import { useState, useEffect } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { createPassword, updatePassword } from '../../services/passwordApi';
import { encrypt } from '../../utils/crypto';
import type { PasswordEntry } from '../../types';

interface Props {
  open: boolean;
  entry: PasswordEntry | null;
  onClose: () => void;
  onSuccess: () => void;
}

const PasswordModal: React.FC<Props> = ({ open, entry, onClose, onSuccess }) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const isEdit = !!entry;

  useEffect(() => {
    if (open) {
      if (entry) {
        form.setFieldsValue({
          siteName: entry.siteName,
          siteUrl: entry.siteUrl,
          username: entry.username,
          note: entry.note,
          secret: '',
          password: '',
        });
      } else {
        form.resetFields();
      }
    }
  }, [open, entry, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      // 用密文加密密码
      const encrypted = await encrypt(values.password, values.secret);

      if (isEdit) {
        await updatePassword(entry!.id, {
          siteName: values.siteName,
          siteUrl: values.siteUrl || '',
          username: values.username,
          encryptedPassword: encrypted,
          note: values.note || '',
        });
        message.success('已更新');
      } else {
        await createPassword({
          siteName: values.siteName,
          siteUrl: values.siteUrl || '',
          username: values.username,
          encryptedPassword: encrypted,
          note: values.note || '',
        });
        message.success('已添加');
      }
      form.resetFields();
      onSuccess();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑密码' : '新增密码'}
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      confirmLoading={submitting}
      okText={isEdit ? '保存' : '添加'}
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item name="siteName" label="网站名称" rules={[{ required: true, message: '请输入网站名称' }]}>
          <Input placeholder="如 GitHub、QQ" maxLength={100} />
        </Form.Item>
        <Form.Item name="siteUrl" label="网址">
          <Input placeholder="https://example.com" maxLength={500} />
        </Form.Item>
        <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
          <Input placeholder="用户名/邮箱/手机号" maxLength={200} />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password placeholder="要保存的明文密码" />
        </Form.Item>
        <Form.Item name="secret" label="密文（保险库密码）" rules={[{ required: true, message: '请输入密文' }]}
          extra="用于加密本条密码。不会上传到后端，请自行保管。不同账户可使用不同密文。">
          <Input.Password placeholder="输入密文来加密本条密码" />
        </Form.Item>
        <Form.Item name="note" label="备注">
          <Input.TextArea placeholder="额外信息（可选）" rows={2} maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default PasswordModal;
