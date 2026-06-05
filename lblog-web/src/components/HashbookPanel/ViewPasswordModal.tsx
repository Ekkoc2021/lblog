import { useState } from 'react';
import { Modal, Input, Button, message, Space, Typography } from 'antd';
import { CopyOutlined, EyeOutlined } from '@ant-design/icons';
import { decrypt } from '../../utils/crypto';
import type { PasswordEntry } from '../../types';

interface Props {
  entry: PasswordEntry;
  onClose: () => void;
}

const ViewPasswordModal: React.FC<Props> = ({ entry, onClose }) => {
  const [secret, setSecret] = useState('');
  const [decrypted, setDecrypted] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleDecrypt = async () => {
    if (!secret.trim()) return;
    setLoading(true);
    setError('');
    try {
      const plain = await decrypt(entry.encryptedPassword, secret);
      setDecrypted(plain);
    } catch {
      setError('解密失败，请检查密文是否正确');
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => message.success(`${label}已复制`));
  };

  return (
    <Modal
      title="查看密码"
      open
      onCancel={onClose}
      footer={null}
      destroyOnClose
    >
      <div style={{ marginTop: 8 }}>
        <div style={{ marginBottom: 12 }}>
          <div style={{ fontWeight: 600, fontSize: 16 }}>{entry.siteName}</div>
          {entry.siteUrl && (
            <a href={entry.siteUrl} target="_blank" rel="noreferrer" style={{ fontSize: 13 }}>
              {entry.siteUrl}
            </a>
          )}
        </div>

        <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>
            <Typography.Text type="secondary">账号：</Typography.Text>
            <Typography.Text copyable={{ text: entry.username }}>{entry.username}</Typography.Text>
          </span>
          <Button size="small" icon={<CopyOutlined />} onClick={() => copyToClipboard(entry.username, '账号')}>复制</Button>
        </div>

        <div style={{ marginBottom: 8 }}>
          <Typography.Text type="secondary">密码：</Typography.Text>
          {decrypted ? (
            <Typography.Text copyable={{ text: decrypted }} code style={{ marginLeft: 4 }}>
              {decrypted}
            </Typography.Text>
          ) : (
            <span style={{ color: '#999' }}>••••••••••••</span>
          )}
        </div>

        {entry.note && (
          <div style={{ marginBottom: 12 }}>
            <Typography.Text type="secondary">备注：</Typography.Text>
            <span>{entry.note}</span>
          </div>
        )}

        {!decrypted && (
          <Space.Compact style={{ width: '100%', marginTop: 12 }}>
            <Input.Password
              placeholder="输入密文来解密"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              onPressEnter={handleDecrypt}
            />
            <Button type="primary" icon={<EyeOutlined />} loading={loading} onClick={handleDecrypt}>
              解密
            </Button>
          </Space.Compact>
        )}

        {error && <div style={{ color: '#ff4d4f', fontSize: 12, marginTop: 4 }}>{error}</div>}

        {decrypted && (
          <Button type="primary" ghost icon={<CopyOutlined />} style={{ marginTop: 8 }} onClick={() => copyToClipboard(decrypted, '密码')}>
            复制密码
          </Button>
        )}
      </div>
    </Modal>
  );
};

export default ViewPasswordModal;
