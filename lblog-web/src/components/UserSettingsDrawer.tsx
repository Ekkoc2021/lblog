import { useState, useRef } from 'react';
import { Drawer, Avatar, Descriptions, Tag, Form, Input, Button, message, Typography, Collapse } from 'antd';
import { UserOutlined, LockOutlined, CameraOutlined, LoadingOutlined, KeyOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import { changePassword, updateAvatar, deleteAvatar } from '../services/api';

const { Text } = Typography;

interface UserSettingsDrawerProps {
  open: boolean;
  onClose: () => void;
}

const UserSettingsDrawer: React.FC<UserSettingsDrawerProps> = ({ open, onClose }) => {
  const { user, refreshUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const [avatarLoading, setAvatarLoading] = useState(false);
  const [form] = Form.useForm();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleChangePassword = async (values: { oldPassword: string; newPassword: string; confirmPassword: string }) => {
    setLoading(true);
    try {
      await changePassword({ oldPassword: values.oldPassword, newPassword: values.newPassword });
      message.success('密码修改成功');
      form.resetFields();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '修改失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAvatarUpload = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.warning('请选择图片文件');
      return;
    }
    setAvatarLoading(true);
    try {
      await updateAvatar(file);
      message.success('头像已更新');
      await refreshUser();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '上传失败');
    } finally {
      setAvatarLoading(false);
    }
  };

  const handleAvatarDelete = async () => {
    setAvatarLoading(true);
    try {
      await deleteAvatar();
      message.success('头像已删除');
      await refreshUser();
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '删除失败');
    } finally {
      setAvatarLoading(false);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleAvatarUpload(file);
    e.target.value = '';
  };

  const roleLabel: Record<string, { color: string; label: string }> = {
    admin: { color: 'red', label: '管理员' },
    author: { color: 'blue', label: '作者' },
    user: { color: 'default', label: '用户' },
  };

  const roleInfo = roleLabel[user?.role ?? ''] ?? { color: 'default', label: user?.role ?? '' };

  return (
    <Drawer
      title="用户设置"
      open={open}
      onClose={onClose}
      width={400}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16 }}>
        <div style={{ position: 'relative' }}>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={handleFileSelect}
          />
          <Avatar
            size={64}
            src={user?.avatar || undefined}
            icon={user?.avatar ? undefined : <UserOutlined />}
            style={{ background: user?.avatar ? undefined : '#1e80ff', cursor: 'pointer' }}
            onClick={() => fileInputRef.current?.click()}
          >
            {!user?.avatar && (user?.nickname?.[0] || 'U')}
          </Avatar>
          <div
            onClick={() => fileInputRef.current?.click()}
            style={{
              position: 'absolute', bottom: 0, right: 0,
              width: 22, height: 22, borderRadius: '50%',
              background: '#1e80ff', color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer', fontSize: 12, border: '2px solid #fff',
            }}
          >
            {avatarLoading ? <LoadingOutlined /> : <CameraOutlined />}
          </div>
        </div>
        <div>
          <Text strong style={{ fontSize: 16 }}>{user?.nickname ?? '用户'}</Text>
          <div><Text type="secondary">@{user?.username}</Text></div>
          <Tag color={roleInfo.color} style={{ marginTop: 4 }}>{roleInfo.label}</Tag>
        </div>
      </div>

      {user?.avatar && (
        <div style={{ textAlign: 'right', marginBottom: 8 }}>
          <Button type="link" size="small" danger onClick={handleAvatarDelete} loading={avatarLoading}>
            删除头像
          </Button>
        </div>
      )}

      <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="邮箱">{user?.email ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="用户名">{user?.username ?? '-'}</Descriptions.Item>
      </Descriptions>

      <Collapse
        ghost
        expandIconPosition="end"
        items={[{
          key: 'password',
          label: <span><KeyOutlined style={{ marginRight: 6 }} />修改密码</span>,
          children: (
            <Form form={form} layout="vertical" onFinish={handleChangePassword}>
              <Form.Item name="oldPassword" label="旧密码" rules={[{ required: true, message: '请输入旧密码' }]}>
                <Input.Password prefix={<LockOutlined />} placeholder="旧密码" />
              </Form.Item>
              <Form.Item name="newPassword" label="新密码" rules={[
                { required: true, message: '请输入新密码' },
                { min: 6, message: '密码至少6位' },
              ]}>
                <Input.Password prefix={<LockOutlined />} placeholder="新密码" />
              </Form.Item>
              <Form.Item name="confirmPassword" label="确认新密码" dependencies={['newPassword']} rules={[
                { required: true, message: '请确认新密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) return Promise.resolve();
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}>
                <Input.Password prefix={<LockOutlined />} placeholder="确认新密码" />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} block>
                  保存修改
                </Button>
              </Form.Item>
            </Form>
          ),
        }]}
      />
    </Drawer>
  );
};

export default UserSettingsDrawer;
