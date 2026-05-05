import { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, Card, Input, Select, Modal, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { EditOutlined, DeleteOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { Post } from '../../types';
import { getAdminPosts, deletePost } from '../../services/api';

const statusMap: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  1: { color: 'green', text: '已发布' },
  2: { color: 'orange', text: '私密' },
};

const PostList: React.FC = () => {
  const navigate = useNavigate();

  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [searchValue, setSearchValue] = useState('');

  const loadPosts = (signal?: AbortSignal) => {
    setLoading(true);
    getAdminPosts({ page, pageSize, status: statusFilter, keyword }, signal)
      .then(res => {
        setPosts(res.data.list);
        setTotal(res.data.total);
      })
      .catch((e: Error) => {
        if (e.name !== 'AbortError') message.error(e.message);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    const controller = new AbortController();
    loadPosts(controller.signal);
    return () => controller.abort();
  }, [page, pageSize, statusFilter, keyword]);

  const handleSearch = () => {
    setPage(1);
    setKeyword(searchValue);
  };

  const handleDelete = (record: Post) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除「${record.title}」吗？删除后不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deletePost(record.id);
          message.success('已删除');
          loadPosts();
        } catch (e: unknown) {
          if (e instanceof Error) message.error(e.message);
        }
      },
    });
  };

  const columns = [
    { title: '标题', dataIndex: 'title', key: 'title', width: 280, ellipsis: true },
    {
      title: '分类', key: 'category', width: 80,
      render: (_: unknown, record: Post) => record.category?.name || '-',
    },
    {
      title: '专栏', key: 'series', width: 100, ellipsis: true,
      render: (_: unknown, record: Post) => record.series?.title || '-',
    },
    {
      title: '标签', key: 'tags', width: 180,
      render: (_: unknown, record: Post) => record.tags?.map(t => <Tag key={t.id}>{t.name}</Tag>),
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (s: number) => {
        const m = statusMap[s] || { color: 'default', text: String(s) };
        return <Tag color={m.color}>{m.text}</Tag>;
      },
    },
    { title: '浏览', dataIndex: 'viewCount', key: 'viewCount', width: 60, align: 'right' as const },
    {
      title: '更新于', key: 'updatedAt', width: 100,
      render: (_: unknown, record: Post) => record.updatedAt?.slice(0, 10),
    },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: Post) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => navigate(`/author/posts/${record.id}/edit`)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="文章管理"
      extra={
        <Space>
          <Input
            placeholder="搜索标题"
            prefix={<SearchOutlined />}
            value={searchValue}
            onChange={e => setSearchValue(e.target.value)}
            onPressEnter={handleSearch}
            style={{ width: 200 }}
            allowClear
            onClear={() => { setSearchValue(''); setKeyword(''); setPage(1); }}
          />
          <Select
            placeholder="状态筛选"
            allowClear
            style={{ width: 100 }}
            value={statusFilter}
            onChange={v => { setStatusFilter(v); setPage(1); }}
            options={[
              { value: 0, label: '草稿' },
              { value: 1, label: '已发布' },
              { value: 2, label: '私密' },
            ]}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/author/posts/new')}>
            写文章
          </Button>
        </Space>
      }
      styles={{ body: { padding: 0 } }}
    >
      <Table
        columns={columns}
        dataSource={posts.map(p => ({ ...p, key: p.id }))}
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showTotal: (t) => `共 ${t} 篇`,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default PostList;
