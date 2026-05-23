import { useState, useEffect } from 'react';
import { Table, Button, Card, message, Popconfirm, Space, Typography } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, DeleteOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import type { Series } from '../../types';
import { getSeriesPosts, sortSeriesPosts, removeSeriesPost, getAuthorSeries } from '../../services/api';
import type { SeriesPostItem } from '../../services/api';

const { Title } = Typography;

const SeriesPostsManage: React.FC = () => {
  const { seriesId } = useParams<{ seriesId: string }>();
  const navigate = useNavigate();
  const [posts, setPosts] = useState<SeriesPostItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [series, setSeries] = useState<Series | null>(null);

  const loadData = async () => {
    if (!seriesId) return;
    setLoading(true);
    try {
      const [postsRes, seriesRes] = await Promise.all([
        getSeriesPosts(Number(seriesId)),
        getAuthorSeries({ page: 1, pageSize: 100 }),
      ]);
      setPosts(postsRes.data);
      const found = seriesRes.data.list.find(s => s.id === Number(seriesId));
      setSeries(found || null);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [seriesId]);

  const handleMove = async (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= posts.length) return;
    const newPosts = [...posts];
    [newPosts[index], newPosts[targetIndex]] = [newPosts[targetIndex], newPosts[index]];
    const postIds = newPosts.map(p => p.postId);
    try {
      await sortSeriesPosts(Number(seriesId), postIds);
      setPosts(newPosts);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleRemove = async (postId: number) => {
    try {
      await removeSeriesPost(Number(seriesId), postId);
      message.success('已从专栏移除');
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const columns = [
    { title: '#', key: 'order', width: 60, render: (_: unknown, __: unknown, index: number) => index + 1 },
    { title: '文章标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '操作', key: 'action', width: 200,
      render: (_: unknown, record: SeriesPostItem, index: number) => (
        <Space size="small">
          <Button
            size="small"
            icon={<ArrowUpOutlined />}
            disabled={index === 0}
            onClick={() => handleMove(index, 'up')}
          />
          <Button
            size="small"
            icon={<ArrowDownOutlined />}
            disabled={index === posts.length - 1}
            onClick={() => handleMove(index, 'down')}
          />
          <Popconfirm
            title="从专栏移除"
            description={`确定要移除「${record.title}」吗？`}
            onConfirm={() => handleRemove(record.postId)}
            okText="移除"
            okType="danger"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card
        styles={{ body: { padding: '0 16px 16px' } }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '16px 0' }}>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/author/series')} />
          <Title level={5} style={{ margin: 0 }}>{series ? `专栏文章 · ${series.title}` : '专栏文章'}</Title>
        </div>
        <Table
          columns={columns}
          dataSource={posts.map(p => ({ ...p, key: p.postId }))}
          loading={loading}
          pagination={false}
          locale={{ emptyText: '专栏暂无文章' }}
        />
      </Card>
    </>
  );
};

export default SeriesPostsManage;
