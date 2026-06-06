import { useState, useEffect, useCallback } from 'react';
import { Card, Select, Input, Button, Table, Tag, message, Modal, Typography } from 'antd';
import { ReloadOutlined, CheckOutlined, CloseOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { getApplications, reviewApplication } from '../../services/api';
import type { AuthorApplication } from '../../types';

const { TextArea } = Input;
const { Text, Title } = Typography;

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: '0', label: '待审核' },
  { value: '1', label: '已通过' },
  { value: '2', label: '已拒绝' },
  { value: '3', label: '需补充' },
];

const statusMap: Record<number, { label: string; color: string }> = {
  0: { label: '待审核', color: 'processing' },
  1: { label: '已通过', color: 'success' },
  2: { label: '已拒绝', color: 'error' },
  3: { label: '需补充', color: 'warning' },
};

const ApplicationManage: React.FC = () => {
  const [data, setData] = useState<AuthorApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [searchValue, setSearchValue] = useState('');
  const [keyword, setKeyword] = useState('');

  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [reviewTarget, setReviewTarget] = useState<AuthorApplication | null>(null);
  const [reviewAction, setReviewAction] = useState<number | null>(null);
  const [reviewFeedback, setReviewFeedback] = useState('');
  const [reviewing, setReviewing] = useState(false);

  const fetchData = useCallback(() => {
    setLoading(true);
    getApplications({ page, pageSize, status, keyword: keyword || undefined })
      .then(res => {
        setData(res.data.list);
        setTotal(res.data.total);
      })
      .catch(() => message.error('获取申请列表失败'))
      .finally(() => setLoading(false));
  }, [page, pageSize, status, keyword]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleReview = (record: AuthorApplication, action: number) => {
    setReviewTarget(record);
    setReviewAction(action);
    setReviewFeedback('');
    setReviewModalOpen(true);
  };

  const confirmReview = async () => {
    if (!reviewTarget || reviewAction === null) return;
    if ((reviewAction === 2 || reviewAction === 3) && !reviewFeedback.trim()) {
      message.warning('请填写审核意见');
      return;
    }
    setReviewing(true);
    try {
      await reviewApplication(reviewTarget.id, reviewAction, reviewFeedback.trim() || undefined);
      message.success('审核完成');
      setReviewModalOpen(false);
      fetchData();
    } catch (e: any) {
      message.error(e.message || '审核失败');
    } finally {
      setReviewing(false);
    }
  };

  const actionLabel = reviewAction === 1 ? '通过申请' : reviewAction === 2 ? '拒绝申请' : '要求补充材料';

  const columns = [
    {
      title: '申请人',
      key: 'user',
      width: 160,
      render: (_: any, r: AuthorApplication) => (
        <div>
          <div style={{ fontWeight: 500 }}>{r.nickname}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.username}</Text>
        </div>
      ),
    },
    {
      title: '申请理由',
      dataIndex: 'reason',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: number) => {
        const info = statusMap[s];
        return <Tag color={info?.color}>{info?.label || s}</Tag>;
      },
    },
    {
      title: '申请时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (t: string) => new Date(t).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_: any, r: AuthorApplication) => {
        if (r.status !== 0) {
          return r.feedback ? (
            <Text type="secondary" style={{ fontSize: 12 }}>反馈：{r.feedback}</Text>
          ) : (
            <Text type="secondary">--</Text>
          );
        }
        return (
          <div style={{ display: 'flex', gap: 4 }}>
            <Button type="link" size="small" icon={<CheckOutlined />} onClick={() => handleReview(r, 1)}>
              通过
            </Button>
            <Button type="link" size="small" danger icon={<CloseOutlined />} onClick={() => handleReview(r, 2)}>
              拒绝
            </Button>
            <Button type="link" size="small" icon={<ExclamationCircleOutlined />} onClick={() => handleReview(r, 3)}>
              需补充
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Title level={4} style={{ marginBottom: 16 }}>作者申请审核</Title>

      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Select
            style={{ width: 120 }}
            placeholder="状态"
            allowClear
            value={status}
            onChange={(v) => { setStatus(v); setPage(1); }}
            options={statusOptions}
          />
          <Input.Search
            style={{ width: 240 }}
            placeholder="搜索用户名/昵称"
            allowClear
            value={searchValue}
            onChange={e => setSearchValue(e.target.value)}
            onSearch={(v) => { setKeyword(v); setPage(1); }}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
        </div>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />

      <Modal
        title={actionLabel}
        open={reviewModalOpen}
        onOk={confirmReview}
        onCancel={() => setReviewModalOpen(false)}
        confirmLoading={reviewing}
        okText="确认"
        cancelText="取消"
      >
        {(reviewAction === 2 || reviewAction === 3) && (
          <div style={{ marginTop: 8 }}>
            <div style={{ marginBottom: 6, color: 'var(--color-text-secondary)', fontSize: 13 }}>
              {reviewAction === 3 ? '请说明需要补充什么材料：' : '请说明拒绝原因：'}
            </div>
            <TextArea
              rows={4}
              value={reviewFeedback}
              onChange={e => setReviewFeedback(e.target.value)}
              placeholder="填写审核意见..."
            />
          </div>
        )}
        {reviewAction === 1 && (
          <div style={{ padding: '12px 0', color: 'var(--color-text-secondary)' }}>
            确认通过该用户的作者申请？通过后用户将获得作者权限。
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ApplicationManage;
