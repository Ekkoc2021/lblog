import { useState, useEffect, useCallback } from 'react';
import {
  Card, Input, Button, Table, Tag, message, Modal, Select,
  Typography, Space, Pagination, InputNumber, Row, Col
} from 'antd';
import {
  ReloadOutlined, LogoutOutlined, UserDeleteOutlined, DeleteOutlined
} from '@ant-design/icons';
import type { SessionInfo, TokenConfig } from '../../types';
import {
  getSessions, revokeSession, kickUser, cleanupTokens,
  getTokenConfig, updateTokenConfig,
} from '../../services/api';

const { Title, Text } = Typography;

const statusOptions = [
  { value: 'active', label: '活跃会话' },
  { value: 'revoked', label: '已吊销' },
  { value: 'expired', label: '已过期' },
];

const SessionManage: React.FC = () => {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('active');

  const [config, setConfig] = useState<TokenConfig | null>(null);
  const [configLoading, setConfigLoading] = useState(false);
  const [accessHours, setAccessHours] = useState<number | null>(2);
  const [refreshDays, setRefreshDays] = useState<number | null>(7);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getSessions({ page, pageSize, keyword: keyword || undefined, status });
      setSessions(res.data.list);
      setTotal(res.data.total);
    } catch (e: unknown) {
      message.error((e as Error).message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword, status]);

  const loadConfig = useCallback(async () => {
    try {
      const res = await getTokenConfig();
      setConfig(res.data);
      setAccessHours(Math.round(res.data.accessTtl / 3600));
      setRefreshDays(Math.round(res.data.refreshTtl / 86400));
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { loadSessions(); }, [loadSessions]);
  useEffect(() => { loadConfig(); }, [loadConfig]);

  const handleRevoke = async (id: number, name: string) => {
    Modal.confirm({
      title: '确认吊销',
      content: `确定要吊销「${name}」的这条 token 吗？`,
      okText: '吊销',
      okType: 'danger',
      onOk: async () => {
        try {
          await revokeSession(id);
          message.success('已吊销');
          loadSessions();
        } catch (e: unknown) {
          message.error((e as Error).message || '吊销失败');
        }
      },
    });
  };

  const handleKick = async (userId: number, name: string) => {
    Modal.confirm({
      title: '确认踢下线',
      content: `确定要将用户「${name}」踢下线吗？会吊销该用户所有有效 token。`,
      okText: '踢下线',
      okType: 'danger',
      onOk: async () => {
        try {
          const res = await kickUser(userId);
          message.success(`已踢下线，吊销了 ${res.data.count} 条 token`);
          loadSessions();
        } catch (e: unknown) {
          message.error((e as Error).message || '操作失败');
        }
      },
    });
  };

  const handleCleanup = async () => {
    Modal.confirm({
      title: '确认清理',
      content: '确定要清理所有已过期的 token 记录吗？',
      okText: '清理',
      okType: 'danger',
      onOk: async () => {
        try {
          const res = await cleanupTokens();
          message.success(`已清理 ${res.data.count} 条过期 token`);
        } catch (e: unknown) {
          message.error((e as Error).message || '清理失败');
        }
      },
    });
  };

  const handleSaveConfig = async () => {
    if (accessHours == null || refreshDays == null) return;
    setConfigLoading(true);
    try {
      const newConfig: TokenConfig = {
        accessTtl: accessHours * 3600,
        refreshTtl: refreshDays * 86400,
      };
      await updateTokenConfig(newConfig);
      setConfig(newConfig);
      message.success('配置已更新，新签发的 token 将使用新的过期时间');
    } catch (e: unknown) {
      message.error((e as Error).message || '保存失败');
    } finally {
      setConfigLoading(false);
    }
  };

  const columns = [
    {
      title: '用户', key: 'user', width: 160,
      render: (_: unknown, r: SessionInfo) => (
        <div>
          <Text strong>{r.username}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>{r.nickname}</Text>
        </div>
      ),
    },
    {
      title: '类型', dataIndex: 'tokenType', key: 'tokenType', width: 90,
      render: (t: string) => <Tag color={t === 'ACCESS' ? 'blue' : 'green'}>{t}</Tag>,
    },
    {
      title: 'Token', dataIndex: 'tokenPreview', key: 'tokenPreview', width: 160,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: '登录时间', dataIndex: 'createdAt', key: 'createdAt', width: 160,
    },
    {
      title: '过期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 160,
      render: (v: string, r: SessionInfo) => (
        <Text type={r.expiringSoon ? 'danger' : undefined}>{v}</Text>
      ),
    },
    {
      title: '状态', key: 'status', width: 100,
      render: (_: unknown, r: SessionInfo) => {
        if (r.revoked) return <Tag color="red">已吊销</Tag>;
        if (r.expiringSoon) return <Tag color="orange">即将过期</Tag>;
        return <Tag color="green">正常</Tag>;
      },
    },
    {
      title: '操作', key: 'action', width: 180,
      render: (_: unknown, r: SessionInfo) => (
        <Space>
          {!r.revoked && (
            <>
              <Button type="link" size="small" danger icon={<LogoutOutlined />}
                onClick={() => handleRevoke(r.id, r.username)}>
                吊销
              </Button>
              <Button type="link" size="small" danger icon={<UserDeleteOutlined />}
                onClick={() => handleKick(r.userId, r.username)}>
                踢下线
              </Button>
            </>
          )}
          {r.revoked && <Text type="secondary">—</Text>}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 16 }}>会话管理</Title>

      <Card title="Token 过期时间配置" style={{ marginBottom: 24 }}>
        <Row gutter={24} align="middle">
          <Col>
            <Text>ACCESS 过期：</Text>
            <InputNumber min={0.08} max={24} step={0.5} value={accessHours}
              onChange={v => setAccessHours(v as number | null)}
              style={{ width: 100, marginLeft: 8 }} /> 小时
          </Col>
          <Col>
            <Text>REFRESH 过期：</Text>
            <InputNumber min={0.5} max={30} step={1} value={refreshDays}
              onChange={v => setRefreshDays(v as number | null)}
              style={{ width: 100, marginLeft: 8 }} /> 天
          </Col>
          <Col>
            <Button type="primary" loading={configLoading} onClick={handleSaveConfig}>
              保存配置
            </Button>
          </Col>
        </Row>
        {config && (
          <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            当前：ACCESS {Math.round(config.accessTtl / 3600)}h / REFRESH {Math.round(config.refreshTtl / 86400)}d
          </Text>
        )}
      </Card>

      <Card
        title={`会话列表（${total}）`}
        extra={
          <Space>
            <Select
              value={status}
              onChange={v => { setStatus(v); setPage(1); }}
              options={statusOptions}
              style={{ width: 130 }}
            />
            <Input.Search placeholder="搜索用户名" allowClear style={{ width: 200 }}
              onSearch={v => { setKeyword(v); setPage(1); }} />
            <Button icon={<DeleteOutlined />} danger onClick={handleCleanup}>
              清理过期 Token
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadSessions}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table
          dataSource={sessions}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: '暂无活跃会话' }}
        />
        {total > pageSize && (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Pagination
              current={page} pageSize={pageSize} total={total}
              onChange={p => setPage(p)}
              showTotal={t => `共 ${t} 条`}
            />
          </div>
        )}
      </Card>
    </div>
  );
};

export default SessionManage;
