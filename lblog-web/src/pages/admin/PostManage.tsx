import { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, Card, Input, Select, Modal, Form, message, Avatar } from 'antd';
import { useNavigate } from 'react-router-dom';
import { EditOutlined, DeleteOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { Post } from '../../types';
import { getAdminAllPosts, batchAdminPosts, getAdminUsers, getAdminAllCategories, getAdminAllTags, updateAdminPost } from '../../services/api';
import type { AdminUser } from '../../services/api';

const statusMap: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  1: { color: 'green', text: '已发布' },
  2: { color: 'orange', text: '私密' },
};

const PostManage: React.FC = () => {
  const navigate = useNavigate();

  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [searchValue, setSearchValue] = useState('');
  const [authorId, setAuthorId] = useState<number | undefined>(undefined);
  const [authors, setAuthors] = useState<AdminUser[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [batchLoading, setBatchLoading] = useState(false);
  const [metaModalOpen, setMetaModalOpen] = useState(false);
  const [editingPost, setEditingPost] = useState<Post | null>(null);
  const [categories, setCategories] = useState<{ id: number; name: string }[]>([]);
  const [tags, setTags] = useState<{ id: number; name: string }[]>([]);
  const [metaForm] = Form.useForm();
  const [metaSaving, setMetaSaving] = useState(false);

  const loadPosts = (signal?: AbortSignal) => {
    setLoading(true);
    getAdminAllPosts({ page, pageSize, status: statusFilter, keyword, authorId }, signal)
      .then(res => {
        setPosts(res.data.list);
        setTotal(res.data.total);
        setSelectedRowKeys([]);
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
  }, [page, pageSize, statusFilter, keyword, authorId]);

  useEffect(() => {
    getAdminUsers({ page: 1, pageSize: 100 })
      .then(res => setAuthors(res.data.list))
      .catch(() => {});
  }, []);

  useEffect(() => {
    getAdminAllCategories({ page: 1, pageSize: 100 })
      .then(res => setCategories(res.data.list.map(c => ({ id: c.id, name: c.name }))))
      .catch(() => {});
    getAdminAllTags({ page: 1, pageSize: 100 })
      .then(res => setTags(res.data.list.map(t => ({ id: t.id, name: t.name }))))
      .catch(() => {});
  }, []);

  const handleEditMeta = async () => {
    try {
      const values = await metaForm.validateFields();
      if (!editingPost) return;
      setMetaSaving(true);
      await updateAdminPost(editingPost.id, values);
      message.success('元数据已更新');
      setMetaModalOpen(false);
      loadPosts();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setMetaSaving(false);
    }
  };

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
          await batchAdminPosts([record.id], 'DELETE');
          message.success('已删除');
          loadPosts();
        } catch (e: unknown) {
          if (e instanceof Error) message.error(e.message);
        }
      },
    });
  };

  const handleBatchAction = async (action: 'PUBLISH' | 'DRAFT' | 'DELETE') => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择文章');
      return;
    }

    const doAction = async () => {
      setBatchLoading(true);
      try {
        const res = await batchAdminPosts(selectedRowKeys, action);
        const actionText = action === 'PUBLISH' ? '发布' : action === 'DRAFT' ? '转草稿' : '删除';
        if (res.data && res.data.failedIds && res.data.failedIds.length > 0) {
          message.warning(`批量${actionText}部分成功：成功 ${res.data.successCount} 个，失败 ${res.data.failedIds.length} 个`);
        } else {
          message.success(`批量${actionText}成功`);
        }
        loadPosts();
      } catch (e: unknown) {
        if (e instanceof Error) message.error(e.message);
      } finally {
        setBatchLoading(false);
      }
    };

    if (action === 'DELETE') {
      Modal.confirm({
        title: '确认批量删除',
        content: `确定要删除选中的 ${selectedRowKeys.length} 篇文章吗？删除后不可恢复。`,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: doAction,
      });
    } else {
      doAction();
    }
  };

  const columns = [
    {
      title: '标题', dataIndex: 'title', key: 'title', width: 240, ellipsis: true,
    },
    {
      title: '作者', key: 'author', width: 120,
      render: (_: unknown, record: Post) => (
        <Space size="small">
          <Avatar src={record.author?.avatar} size={20} />
          <span>{record.author?.nickname || '-'}</span>
        </Space>
      ),
    },
    {
      title: '分类', key: 'category', width: 80,
      render: (_: unknown, record: Post) => record.category?.name || '-',
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
    { title: '评论', dataIndex: 'commentCount', key: 'commentCount', width: 60, align: 'right' as const },
    {
      title: '更新于', key: 'updatedAt', width: 100,
      render: (_: unknown, record: Post) => record.updatedAt?.slice(0, 10),
    },
    {
      title: '操作', key: 'action', width: 140, fixed: 'right' as const,
      render: (_: unknown, record: Post) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => { setEditingPost(record); metaForm.setFieldsValue({ title: record.title, slug: record.slug, status: record.status, categoryId: record.categoryId, tagIds: record.tags?.map(t => t.id) }); setMetaModalOpen(true); }}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1400, margin: '0 auto' }}>
      <Card
        title="全站文章管理"
        extra={
          <Space wrap>
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
            <Select
              placeholder="作者筛选"
              allowClear
              showSearch
              style={{ width: 140 }}
              value={authorId}
              onChange={v => { setAuthorId(v); setPage(1); }}
              filterOption={(input, option) => (option?.label as string || '').toLowerCase().includes(input.toLowerCase())}
              options={authors.map(a => ({ value: a.id, label: a.nickname }))}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/author/posts/new')}>
              写文章
            </Button>
          </Space>
        }
        styles={{ body: { padding: 0 } }}
      >
        <Table
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys as number[]),
          }}
          columns={columns}
          dataSource={posts.map(p => ({ ...p, key: p.id }))}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 篇`,
            showSizeChanger: true,
            pageSizeOptions: ['10', '15', '20'],
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
          title={selectedRowKeys.length > 0 ? () => (
            <Space>
              <span>已选 {selectedRowKeys.length} 项</span>
              <Button size="small" type="primary" loading={batchLoading} onClick={() => handleBatchAction('PUBLISH')}>批量发布</Button>
              <Button size="small" loading={batchLoading} onClick={() => handleBatchAction('DRAFT')}>批量转草稿</Button>
              <Button size="small" danger loading={batchLoading} onClick={() => handleBatchAction('DELETE')}>批量删除</Button>
            </Space>
          ) : undefined}
          scroll={{ x: 1100 }}
        />
      </Card>

      <Modal
        title="编辑文章元数据"
        open={metaModalOpen}
        onOk={handleEditMeta}
        onCancel={() => setMetaModalOpen(false)}
        okText="保存"
        cancelText="取消"
        confirmLoading={metaSaving}
        width={520}
      >
        <Form form={metaForm} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="slug" label="别名" rules={[{ required: true, message: '请输入 URL 别名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select options={[
              { value: 0, label: '草稿' },
              { value: 1, label: '已发布' },
              { value: 2, label: '私密' },
            ]} />
          </Form.Item>
          <Form.Item name="categoryId" label="分类">
            <Select allowClear placeholder="选择分类" options={categories.map(c => ({ value: c.id, label: c.name }))} />
          </Form.Item>
          <Form.Item name="tagIds" label="标签">
            <Select mode="multiple" allowClear placeholder="选择标签" options={tags.map(t => ({ value: t.id, label: t.name }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PostManage;
