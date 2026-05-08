import { useEffect, useState, useMemo } from 'react';
import { Card, Row, Col, Statistic, Typography, Spin, Button, message, Tooltip } from 'antd';
import { FileTextOutlined, EyeOutlined, LikeOutlined, MessageOutlined, ReloadOutlined } from '@ant-design/icons';
import type { AuthorStatistics } from '../../services/api';
import { getAuthorStatistics } from '../../services/api';

const { Title } = Typography;

const STATUS_LABELS: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '私密',
};

const STATUS_COLORS: Record<number, string> = {
  0: '#999',
  1: '#52c41a',
  2: '#faad14',
};

const KPI_CARDS: Array<{
  title: string;
  dataKey: keyof Pick<AuthorStatistics, 'totalPosts' | 'totalViews' | 'totalLikes' | 'totalComments'>;
  icon: React.ReactNode;
}> = [
  { title: '文章总数', dataKey: 'totalPosts', icon: <FileTextOutlined style={{ color: '#1e80ff' }} /> },
  { title: '总浏览量', dataKey: 'totalViews', icon: <EyeOutlined style={{ color: '#52c41a' }} /> },
  { title: '总点赞数', dataKey: 'totalLikes', icon: <LikeOutlined style={{ color: '#ff4d4f' }} /> },
  { title: '总评论数', dataKey: 'totalComments', icon: <MessageOutlined style={{ color: '#faad14' }} /> },
];

const Statistics: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AuthorStatistics | null>(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getAuthorStatistics();
      if (res.code === 0) {
        setData(res.data);
      } else {
        throw new Error(res.message || '获取统计数据失败');
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : '获取统计数据失败';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const statusMax = useMemo(() => {
    if (!data?.statusDistribution?.length) return 1;
    return Math.max(...data.statusDistribution.map((s) => s.count), 1);
  }, [data]);

  const STATUS_ORDER: Record<number, number> = { 1: 0, 0: 1, 2: 2 };

  const statusSorted = useMemo(() => {
    if (!data?.statusDistribution?.length) return [];
    return [...data.statusDistribution].sort((a, b) => (STATUS_ORDER[a.status] ?? 99) - (STATUS_ORDER[b.status] ?? 99));
  }, [data]);

  const categoryDistribution = useMemo(() => {
    if (!data?.categoryDistribution?.length) return [];
    return [...data.categoryDistribution].sort((a, b) => b.postCount - a.postCount);
  }, [data]);

  const categoryMax = useMemo(() => {
    if (!categoryDistribution.length) return 1;
    return Math.max(...categoryDistribution.map((c) => c.postCount), 1);
  }, [categoryDistribution]);

  const monthlyMax = useMemo(() => {
    if (!data?.monthlyTrend?.length) return 1;
    return Math.max(...data.monthlyTrend.map((m) => m.count), 1);
  }, [data]);

  const isEmpty = data !== null && data.totalPosts === 0;

  // Initial loading state
  if (loading && !data) {
    return (
      <div>
        <Title level={4} style={{ marginBottom: 16 }}>创作中心 · 站点统计</Title>
        <div style={{ textAlign: 'center', padding: '80px 0' }}>
          <Spin size="large" />
        </div>
      </div>
    );
  }

  // Error state (no data available)
  if (!data) {
    return (
      <div>
        <Title level={4} style={{ marginBottom: 16 }}>创作中心 · 站点统计</Title>
        <div style={{ textAlign: 'center', padding: '80px 0' }}>
          <div style={{ color: '#ff4d4f', marginBottom: 16, fontSize: 15 }}>{error}</div>
          <Button type="primary" icon={<ReloadOutlined />} onClick={fetchData}>
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  // Normal render
  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>创作中心 · 站点统计</Title>

      {isEmpty && (
        <div
          style={{
            marginBottom: 16,
            padding: '8px 16px',
            background: '#fff7e6',
            borderRadius: 6,
            color: '#d46b08',
            fontSize: 14,
          }}
        >
          还没有发表过文章，去写一篇吧！
        </div>
      )}

      <Spin spinning={loading}>
        {/* Row 1: KPI Cards */}
        <Row gutter={16}>
          {KPI_CARDS.map((card) => (
            <Col span={6} key={card.dataKey}>
              <Card hoverable>
                <Statistic
                  title={card.title}
                  value={data[card.dataKey] ?? 0}
                  prefix={card.icon}
                />
              </Card>
            </Col>
          ))}
        </Row>

        {/* Row 2: Status & Category Distribution */}
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={12}>
            <Card title="文章状态分布">
              {statusSorted.length > 0 ? (
                statusSorted.map((item) => (
                  <div
                    key={item.status}
                    style={{ display: 'flex', alignItems: 'center', marginBottom: 10 }}
                  >
                    <span
                      style={{
                        width: 60,
                        fontSize: 13,
                        color: '#666',
                        flexShrink: 0,
                      }}
                    >
                      {STATUS_LABELS[item.status] ?? `状态${item.status}`}
                    </span>
                    <Tooltip title={`${STATUS_LABELS[item.status] ?? `状态${item.status}`}：${item.count} 篇`}>
                      <div
                        style={{
                          width: `${(item.count / statusMax) * 100}%`,
                          height: 24,
                          background: STATUS_COLORS[item.status] ?? '#999',
                          borderRadius: 4,
                          display: 'flex',
                          alignItems: 'center',
                          paddingLeft: 8,
                          color: '#fff',
                          fontSize: 13,
                          fontWeight: 500,
                          minWidth: item.count > 0 ? 30 : 0,
                          transition: 'width 0.3s ease, filter 0.2s',
                          cursor: 'pointer',
                        }}
                        onMouseEnter={e => { e.currentTarget.style.filter = 'brightness(0.85)'; }}
                        onMouseLeave={e => { e.currentTarget.style.filter = ''; }}
                      >
                        {item.count > 0 ? item.count : ''}
                      </div>
                    </Tooltip>
                  </div>
                ))
              ) : (
                <div style={{ color: '#999', textAlign: 'center', padding: '32px 0' }}>
                  暂无数据
                </div>
              )}
            </Card>
          </Col>
          <Col span={12}>
            <Card title="分类分布">
              <div style={{ maxHeight: 105, overflowY: 'auto' }}>
              {categoryDistribution.length > 0 ? (
                categoryDistribution.map((item) => (
                  <div
                    key={item.categorySlug || item.categoryName}
                    style={{ display: 'flex', alignItems: 'center', marginBottom: 10 }}
                  >
                    <span
                      style={{
                        width: 80,
                        fontSize: 13,
                        color: '#666',
                        flexShrink: 0,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {item.categoryName}
                    </span>
                    <Tooltip title={`${item.categoryName}：${item.postCount} 篇`}>
                      <div
                        style={{
                          width: `${(item.postCount / categoryMax) * 100}%`,
                          height: 24,
                          background: '#1e80ff',
                          borderRadius: 4,
                          display: 'flex',
                          alignItems: 'center',
                          paddingLeft: 8,
                          color: '#fff',
                          fontSize: 13,
                          fontWeight: 500,
                          minWidth: item.postCount > 0 ? 30 : 0,
                          transition: 'width 0.3s ease, filter 0.2s',
                          cursor: 'pointer',
                        }}
                        onMouseEnter={e => { e.currentTarget.style.filter = 'brightness(0.85)'; }}
                        onMouseLeave={e => { e.currentTarget.style.filter = ''; }}
                      >
                        {item.postCount > 0 ? item.postCount : ''}
                      </div>
                    </Tooltip>
                  </div>
                ))
              ) : (
                <div style={{ color: '#999', textAlign: 'center', padding: '32px 0' }}>
                  暂无数据
                </div>
              )}
              </div>
            </Card>
          </Col>
        </Row>

        {/* Row 3: Monthly Trend */}
        <Card title="每月发文趋势" style={{ marginTop: 16 }}>
          {data.monthlyTrend.length > 0 ? (
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                gap: 4,
                height: 200,
                padding: '0 8px',
              }}
            >
              {data.monthlyTrend.map((m) => (
                <div
                  key={m.month}
                  style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    height: '100%',
                    justifyContent: 'flex-end',
                  }}
                >
                  <div
                    style={{
                      fontSize: 12,
                      marginBottom: 4,
                      color: '#666',
                      fontWeight: 500,
                    }}
                  >
                    {m.count > 0 ? m.count : ''}
                  </div>
                  <Tooltip title={`${m.month}：${m.count} 篇`}>
                    <div
                      style={{
                        width: '70%',
                        height: `${(m.count / monthlyMax) * 170}px`,
                        background: '#1e80ff',
                        borderRadius: '4px 4px 0 0',
                        minHeight: m.count > 0 ? 4 : 0,
                        transition: 'height 0.3s ease, filter 0.2s',
                        cursor: m.count > 0 ? 'pointer' : 'default',
                      }}
                      onMouseEnter={e => { e.currentTarget.style.filter = 'brightness(0.8)'; }}
                      onMouseLeave={e => { e.currentTarget.style.filter = ''; }}
                    />
                  </Tooltip>
                  <div
                    style={{
                      fontSize: 11,
                      marginTop: 4,
                      color: '#999',
                    }}
                  >
                    {m.month}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div style={{ color: '#999', textAlign: 'center', padding: '40px 0' }}>
              暂无数据
            </div>
          )}
        </Card>

        {/* Error retry banner */}
        {error && (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button type="primary" icon={<ReloadOutlined />} onClick={fetchData}>
              重新加载
            </Button>
          </div>
        )}
      </Spin>
    </div>
  );
};

export default Statistics;
