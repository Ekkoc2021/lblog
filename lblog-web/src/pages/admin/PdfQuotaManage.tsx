import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Switch, InputNumber, message, Statistic, Row, Col, Typography } from 'antd';
import type { PdfUserQuotaItem } from '../../types';
import { getAdminPdfQuotas, setAdminPdfQuota, setAdminPdfAllowUpload } from '../../services/api';

const { Text } = Typography;

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function toMB(bytes: number): number {
  return Math.round(bytes / 1048576);
}

const PdfQuotaManage: React.FC = () => {
  const [data, setData] = useState<PdfUserQuotaItem[]>([]);
  const [loading, setLoading] = useState(false);

  const fetch = useCallback(() => {
    setLoading(true);
    getAdminPdfQuotas().then(res => setData(res.data)).catch(() => message.error('加载失败')).finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetch(); }, [fetch]);

  const handleQuotaChange = async (userId: number, mb: number | null) => {
    if (mb == null) return;
    try {
      await setAdminPdfQuota(userId, mb * 1048576);
      message.success('配额已更新');
      fetch();
    } catch { message.error('更新失败'); }
  };

  const handleAllowUploadChange = async (userId: number, checked: boolean) => {
    try {
      await setAdminPdfAllowUpload(userId, checked ? 1 : 0);
      message.success(checked ? '已允许上传' : '已禁止上传');
      fetch();
    } catch { message.error('更新失败'); }
  };

  const totalSize = data.reduce((s, i) => s + i.totalSize, 0);
  const totalFiles = data.reduce((s, i) => s + i.fileCount, 0);

  const columns = [
    { title: '用户', key: 'user', render: (_: unknown, r: PdfUserQuotaItem) => (
      <span>{r.nickname || r.username} <Text type="secondary" style={{ fontSize: 12 }}>({r.username})</Text></span>
    )},
    { title: 'PDF 数量', dataIndex: 'fileCount', width: 100 },
    { title: '已用空间', key: 'used', render: (_: unknown, r: PdfUserQuotaItem) => formatBytes(r.totalSize) },
    { title: '配额 (MB)', key: 'quota', width: 160, render: (_: unknown, r: PdfUserQuotaItem) => (
      <InputNumber size="small" min={1} max={10240} style={{ width: 100 }}
        value={toMB(r.quotaBytes)}
        onPressEnter={e => handleQuotaChange(r.userId, parseInt((e.target as HTMLInputElement).value))}
        onBlur={e => handleQuotaChange(r.userId, parseInt(e.target.value))} />
    )},
    { title: '使用率', key: 'usage', render: (_: unknown, r: PdfUserQuotaItem) => {
      const pct = r.quotaBytes > 0 ? Math.round(r.totalSize / r.quotaBytes * 100) : 0;
      return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ flex: 1, height: 6, background: '#f0f0f0', borderRadius: 3, overflow: 'hidden' }}>
            <div style={{ height: '100%', width: `${Math.min(pct, 100)}%`,
              background: pct > 80 ? '#ff4d4f' : pct > 60 ? '#faad14' : '#52c41a',
              borderRadius: 3, transition: 'width 0.3s' }} />
          </div>
          <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)', whiteSpace: 'nowrap' }}>{pct}%</span>
        </div>
      );
    }},
    { title: '允许上传', key: 'allow', width: 90, render: (_: unknown, r: PdfUserQuotaItem) => (
      <Switch size="small" checked={r.allowUpload === 1}
        onChange={checked => handleAllowUploadChange(r.userId, checked)} />
    )},
  ];

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card size="small"><Statistic title="用户数" value={data.length} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="PDF 总数" value={totalFiles} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="总占用空间" value={formatBytes(totalSize)} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="默认配额" value="500 MB" /></Card></Col>
      </Row>

      <Card title="PDF 存储配额管理" size="small">
        <Table rowKey="userId" columns={columns} dataSource={data} loading={loading}
          pagination={false} size="small" />
      </Card>
    </div>
  );
};

export default PdfQuotaManage;
