import { useState } from 'react';
import { Modal, Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined, BookOutlined, MailOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';

interface LoginModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

const LoginModal: React.FC<LoginModalProps> = ({ open, onClose, onSuccess }) => {
  const { login, register } = useAuth();
  const [isRegister, setIsRegister] = useState(false);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const ok = await login(values.username, values.password);
      if (ok === true) {
        message.success('登录成功');
        form.resetFields();
        onClose();
        onSuccess?.();
      } else {
        message.error(ok || '登录失败');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: {
    username: string; password: string; confirmPassword: string; nickname?: string; email?: string;
  }) => {
    setLoading(true);
    try {
      const ok = await register({
        username: values.username,
        password: values.password,
        nickname: values.nickname || undefined,
        email: values.email || undefined,
      });
      if (ok === true) {
        message.success('注册成功');
        form.resetFields();
        onClose();
        onSuccess?.();
      } else {
        message.error(ok || '注册失败');
      }
    } finally {
      setLoading(false);
    }
  };

  const toggleMode = () => {
    form.resetFields();
    setIsRegister(r => !r);
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      width={380}
      centered
      destroyOnClose
    >
      <div style={{ textAlign: 'center', marginBottom: 28, marginTop: 8 }}>
        <div style={{
          width: 48,
          height: 48,
          borderRadius: 12,
          background: 'linear-gradient(135deg, #667eea, #764ba2)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 12px',
        }}>
          <BookOutlined style={{ fontSize: 24, color: '#fff' }} />
        </div>
        <div style={{ fontWeight: 600, fontSize: 18 }}>{isRegister ? '注册' : '登录'}</div>
        <div style={{ color: 'var(--color-text-tertiary)', fontSize: 13, marginTop: 2 }}>{isRegister ? '创建你的账号' : '欢迎回来'}</div>
      </div>

      {isRegister ? (
        <Form form={form} onFinish={handleRegister} size="large" autoComplete="off">
          <Form.Item name="username" rules={[
            { required: true, message: '请输入用户名' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字、下划线' },
            { min: 3, max: 20, message: '3-20 位' },
          ]}>
            <Input prefix={<UserOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="用户名" />
          </Form.Item>
          <Form.Item name="nickname">
            <Input prefix={<UserOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="昵称（可选）" />
          </Form.Item>
          <Form.Item name="email" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
            <Input prefix={<MailOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="邮箱（可选）" />
          </Form.Item>
          <Form.Item name="password" rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码至少 6 位' },
          ]}>
            <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="密码" />
          </Form.Item>
          <Form.Item name="confirmPassword" dependencies={['password']} rules={[
            { required: true, message: '请确认密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('password') === value) return Promise.resolve();
                return Promise.reject(new Error('两次输入的密码不一致'));
              },
            }),
          ]}>
            <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="确认密码" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading} size="large">
              注册
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center', fontSize: 13 }}>
            <span style={{ color: 'var(--color-text-tertiary)' }}>已有账号？</span>
            <Button type="link" style={{ padding: 0 }} onClick={toggleMode}>去登录</Button>
          </div>
        </Form>
      ) : (
        <Form form={form} onFinish={handleLogin} size="large" autoComplete="off">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="用户名" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined style={{ color: 'var(--color-text-tertiary)' }} />} placeholder="密码" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading} size="large">
              登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center', fontSize: 13 }}>
            <span style={{ color: 'var(--color-text-tertiary)' }}>没有账号？</span>
            <Button type="link" style={{ padding: 0 }} onClick={toggleMode}>去注册</Button>
          </div>
        </Form>
      )}
    </Modal>
  );
};

export default LoginModal;
