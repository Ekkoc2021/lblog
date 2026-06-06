import { useState, useEffect } from 'react';
import { Modal, Input, Button, message, Descriptions, Tag } from 'antd';
import { getMyApplication, submitApplication, resubmitApplication, getCurrentUser } from '../services/api';
import type { AuthorApplication } from '../types';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const { TextArea } = Input;

const statusMap: Record<number, { label: string; color: string }> = {
  0: { label: '审核中', color: 'processing' },
  1: { label: '已通过', color: 'success' },
  2: { label: '已拒绝', color: 'error' },
  3: { label: '需补充材料', color: 'warning' },
};

interface Props {
  open: boolean;
  onClose: () => void;
}

const AuthorApplicationModal: React.FC<Props> = ({ open, onClose }) => {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const [app, setApp] = useState<AuthorApplication | null>(null);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setLoading(true);
      Promise.all([getMyApplication(), getCurrentUser()])
        .then(([appRes, userRes]) => {
          const data = appRes.data;
          const realRole = userRes.data?.role;
          // 已申请通过且服务端角色已是 author → 直接进创作中心
          if (data && data.status === 1 && realRole !== 'user') {
            refreshUser();
            onClose();
            navigate('/author/posts');
            return;
          }
          // 已申请通过但服务端角色被收回 → 允许重新申请
          if (data && data.status === 1 && realRole === 'user') {
            setApp(null);
            setLoading(false);
            return;
          }
          setApp(data);
          if (data && (data.status === 2 || data.status === 3)) {
            setReason(data.reason);
          }
        })
        .catch(() => message.error('获取申请状态失败'))
        .finally(() => setLoading(false));
    }
  }, [open]);

  const handleSubmit = async () => {
    if (!reason.trim()) {
      message.warning('请填写申请理由');
      return;
    }
    setSubmitting(true);
    try {
      if (app && (app.status === 2 || app.status === 3)) {
        await resubmitApplication(reason.trim());
        message.success('已重新提交申请');
      } else {
        await submitApplication(reason.trim());
        message.success('申请已提交');
      }
      const res = await getMyApplication();
      setApp(res.data);
    } catch (e: any) {
      message.error(e.message || '操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const goToAuthorCenter = async () => {
    await refreshUser();
    onClose();
    navigate('/author/posts');
  };

  const renderContent = () => {
    if (loading) return <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>;

    // 无申请记录 → 填写表单
    if (!app) {
      return (
        <div>
          <div style={{ marginBottom: 12, color: 'var(--color-text-secondary)', fontSize: 13 }}>
            请简要介绍您的技术背景和创作方向，管理员审核通过后即可开始创作。
          </div>
          <TextArea
            rows={5}
            placeholder="请填写申请理由..."
            value={reason}
            onChange={e => setReason(e.target.value)}
            maxLength={500}
            showCount
          />
          <Button
            type="primary"
            block
            style={{ marginTop: 16 }}
            loading={submitting}
            onClick={handleSubmit}
          >
            提交申请
          </Button>
        </div>
      );
    }

    // 已通过 → 引导进入创作中心
    if (app.status === 1) {
      return (
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          <Tag color="success" style={{ fontSize: 14, padding: '4px 16px' }}>已通过</Tag>
          <div style={{ marginTop: 16, color: 'var(--color-text-secondary)' }}>
            您已成为作者，可以进入创作中心开始写作
          </div>
          <Button type="primary" style={{ marginTop: 16 }} onClick={goToAuthorCenter}>
            进入创作中心
          </Button>
        </div>
      );
    }

    // 待审核
    if (app.status === 0) {
      return (
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          <Tag color="processing" style={{ fontSize: 14, padding: '4px 16px' }}>审核中</Tag>
          <Descriptions column={1} style={{ marginTop: 16 }} size="small">
            <Descriptions.Item label="申请理由">{app.reason}</Descriptions.Item>
            <Descriptions.Item label="提交时间">{new Date(app.createdAt).toLocaleString()}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 12, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            请耐心等待管理员审核
          </div>
        </div>
      );
    }

    // 拒绝(2) 或 需补充(3) → 可编辑重新提交
    return (
      <div>
        {app.feedback && (
          <div style={{
            background: app.status === 3 ? '#fffbe6' : '#fff2f0',
            border: `1px solid ${app.status === 3 ? '#ffe58f' : '#ffccc7'}`,
            borderRadius: 6,
            padding: '8px 12px',
            marginBottom: 12,
            fontSize: 13,
            color: app.status === 3 ? '#ad6800' : '#a8071a',
          }}>
            <strong>{app.status === 3 ? '管理员要求补充：' : '审核未通过，原因：'}</strong>
            {app.feedback}
          </div>
        )}
        <TextArea
          rows={5}
          placeholder="请修改申请理由..."
          value={reason}
          onChange={e => setReason(e.target.value)}
          maxLength={500}
          showCount
        />
        <Button
          type="primary"
          block
          style={{ marginTop: 16 }}
          loading={submitting}
          onClick={handleSubmit}
        >
          重新提交
        </Button>
      </div>
    );
  };

  const statusTag = app ? statusMap[app.status] : null;

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          作者申请
          {statusTag && <Tag color={statusTag.color}>{statusTag.label}</Tag>}
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={null}
      width={480}
      destroyOnClose
    >
      {renderContent()}
    </Modal>
  );
};

export default AuthorApplicationModal;
