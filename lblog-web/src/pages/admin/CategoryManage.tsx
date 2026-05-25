import { useState, useEffect } from 'react';
import { Table, Button, Space, Card, Modal, Input, Form, Select, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { AdminCategory } from '../../types';
import { getAdminAllCategories, createAdminCategory, updateAdminCategory, deleteAdminCategory, getAdminUsers } from '../../services/api';
import type { AdminUser } from '../../services/api';

const CategoryManage: React.FC = () => {
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [authorId, setAuthorId] = useState<number | undefined>(undefined);
  const [authors, setAuthors] = useState<AdminUser[]>([]);
  const [editingCat, setEditingCat] = useState<AdminCategory | null>(null);
  const [form] = Form.useForm();

  const loadData = (signal?: AbortSignal) => {
    setLoading(true);
    getAdminAllCategories({ page, pageSize, createdBy: authorId }, signal)
      .then(res => {
        setCategories(res.data.list);
        setTotal(res.data.total);
      })
      .catch((e: Error) => {
        if (e.name !== 'AbortError') message.error(e.message);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    const controller = new AbortController();
    loadData(controller.signal);
    return () => controller.abort();
  }, [page, pageSize, authorId]);

  useEffect(() => {
    getAdminUsers({ page: 1, pageSize: 100 })
      .then(res => setAuthors(res.data.list))
      .catch(() => {});
  }, []);

  const openCreate = () => {
    setEditingCat(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEdit = (cat: AdminCategory) => {
    setEditingCat(cat);
    form.setFieldsValue(cat);
    setModalVisible(true);
  };

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (editingCat) {
        await updateAdminCategory(editingCat.id, values);
        message.success('分类已更新');
      } else {
        await createAdminCategory(values);
        message.success('分类已创建');
      }
      setModalVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleDelete = (cat: AdminCategory) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除「${cat.name}」吗？${cat.postCount ? `该分类下有 ${cat.postCount} 篇文章。` : ''}`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminCategory(cat.id);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          message.error((e as { message?: string })?.message || '删除失败');
        }
      },
    });
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 120 },
    { title: '别名', dataIndex: 'slug', key: 'slug', width: 120 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 140, ellipsis: true },
    { title: '文章数', dataIndex: 'postCount', key: 'postCount', width: 70, align: 'right' as const },
    { title: '创建者', dataIndex: 'creatorName', key: 'creatorName', width: 100 },
    {
      title: '操作', key: 'action', width: 120, fixed: 'right' as const,
      render: (_: unknown, record: AdminCategory) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Card
        title="全站分类管理"
        extra={
          <Space>
            <Select
              placeholder="创建者筛选"
              allowClear showSearch
              style={{ width: 140 }}
              value={authorId}
              onChange={v => { setAuthorId(v); setPage(1); }}
              filterOption={(input, option) => (option?.label as string || '').toLowerCase().includes(input.toLowerCase())}
              options={authors.map(a => ({ value: a.id, label: a.nickname }))}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建分类</Button>
          </Space>
        }
        styles={{ body: { padding: 0 } }}
      >
        <Table
          columns={columns}
          dataSource={categories.map(c => ({ ...c, key: c.id }))}
          loading={loading}
          scroll={{ x: 800 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 条`,
            showSizeChanger: true,
            pageSizeOptions: ['10', '15', '20'],
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
        />
      </Card>

      <Modal
        title={editingCat ? '编辑分类' : '新建分类'}
        open={modalVisible}
        onOk={handleOk}
        onCancel={() => setModalVisible(false)}
        okText={editingCat ? '保存' : '创建'}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="分类名称" />
          </Form.Item>
          <Form.Item name="slug" label="别名" rules={[{ required: true, message: '请输入 URL 别名' }]}>
            <Input placeholder="category-slug" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="分类描述（可选）" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" placeholder="0" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CategoryManage;
