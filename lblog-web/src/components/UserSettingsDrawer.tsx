import { useState } from 'react';
import { Drawer, Avatar, Descriptions, Tag, Divider, Form, Input, Button, message, Typography } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import { changePassword } from '../services/api';

const { Text } = Typography;

interface UserSettingsDrawerProps {
  open: boolean;
  onClose: () => void;
}

const UserSettingsDrawer: React.FC<UserSettingsDrawerProps> = ({ open, onClose }) => {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

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
        <Avatar size={64} icon={<UserOutlined />} style={{ background: '#1e80ff' }} />
        <div>
          <Text strong style={{ fontSize: 16 }}>{user?.nickname ?? '用户'}</Text>
          <div><Text type="secondary">@{user?.username}</Text></div>
          <Tag color={roleInfo.color} style={{ marginTop: 4 }}>{roleInfo.label}</Tag>
        </div>
      </div>

      <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="邮箱">{user?.email ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="用户名">{user?.username ?? '-'}</Descriptions.Item>
      </Descriptions>

      <Divider>修改密码</Divider>

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
    </Drawer>
  );
};

export default UserSettingsDrawer;
