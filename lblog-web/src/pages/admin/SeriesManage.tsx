import { useState, useEffect } from 'react';
import { Table, Button, Space, Card, Tag, Modal, Input, Form, Select, Switch, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { AdminSeries, AdminCategory } from '../../types';
import { getAdminAllSeries, getAdminAllCategories, createAdminSeries, updateAdminSeries, deleteAdminSeries, getAdminUsers } from '../../services/api';
import type { AdminUser } from '../../services/api';

const SeriesManage: React.FC = () => {
  const [seriesList, setSeriesList] = useState<AdminSeries[]>([]);
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [categoryFilter, setCategoryFilter] = useState<number | undefined>(undefined);
  const [authorId, setAuthorId] = useState<number | undefined>(undefined);
  const [authors, setAuthors] = useState<AdminUser[]>([]);
  const [editingSeries, setEditingSeries] = useState<AdminSeries | null>(null);
  const [form] = Form.useForm();

  const loadData = (signal?: AbortSignal) => {
    setLoading(true);
    Promise.all([
      getAdminAllSeries({ page, pageSize, categoryId: categoryFilter, createdBy: authorId }, signal),
      getAdminAllCategories({ page: 1, pageSize: 100 }),
    ]).then(([seriesRes, catRes]) => {
      setSeriesList(seriesRes.data.list);
      setTotal(seriesRes.data.total);
      setCategories(catRes.data.list);
    }).catch((e: Error) => {
      if (e.name !== 'AbortError') message.error(e.message);
    })
    .finally(() => setLoading(false));
  };

  useEffect(() => {
    const controller = new AbortController();
    loadData(controller.signal);
    return () => controller.abort();
  }, [page, pageSize, categoryFilter, authorId]);

  useEffect(() => {
    getAdminUsers({ page: 1, pageSize: 100 })
      .then(res => setAuthors(res.data.list))
      .catch(() => {});
  }, []);

  const openCreate = () => {
    setEditingSeries(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEdit = (series: AdminSeries) => {
    setEditingSeries(series);
    form.setFieldsValue({ ...series, isCompleted: series.isCompleted === 1 });
    setModalVisible(true);
  };

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        isCompleted: values.isCompleted ? 1 : 0,
      };
      if (editingSeries) {
        await updateAdminSeries(editingSeries.id, payload);
        message.success('专栏已更新');
      } else {
        await createAdminSeries(payload);
        message.success('专栏已创建');
      }
      setModalVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleDelete = (series: AdminSeries) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除专栏「${series.title}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminSeries(series.id);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          if (e instanceof Error) message.error(e.message);
        }
      },
    });
  };

  const columns = [
    { title: '标题', dataIndex: 'title', key: 'title', width: 180, ellipsis: true },
    { title: '别名', dataIndex: 'slug', key: 'slug', width: 150, ellipsis: true },
    { title: '文章数', dataIndex: 'postCount', key: 'postCount', width: 70, align: 'right' as const },
    {
      title: '状态', key: 'status', width: 80,
      render: (_: unknown, record: AdminSeries) => (
        <Tag color={record.isCompleted === 1 ? 'green' : 'orange'}>
          {record.isCompleted === 1 ? '已完结' : '连载中'}
        </Tag>
      ),
    },
    { title: '创建者', dataIndex: 'creatorName', key: 'creatorName', width: 100 },
    {
      title: '操作', key: 'action', width: 120, fixed: 'right' as const,
      render: (_: unknown, record: AdminSeries) => (
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
        title="全站专栏管理"
        extra={
          <Space>
            <Select
              placeholder="分类筛选"
              allowClear
              style={{ width: 130 }}
              value={categoryFilter}
              onChange={v => { setCategoryFilter(v); setPage(1); }}
              options={categories.map(c => ({ value: c.id, label: c.name }))}
            />
            <Select
              placeholder="创建者筛选"
              allowClear showSearch
              style={{ width: 140 }}
              value={authorId}
              onChange={v => { setAuthorId(v); setPage(1); }}
              filterOption={(input, option) => (option?.label as string || '').toLowerCase().includes(input.toLowerCase())}
              options={authors.map(a => ({ value: a.id, label: a.nickname }))}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建专栏</Button>
          </Space>
        }
        styles={{ body: { padding: 0 } }}
      >
        <Table
          columns={columns}
          dataSource={seriesList.map(s => ({ ...s, key: s.id }))}
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
        title={editingSeries ? '编辑专栏' : '新建专栏'}
        open={modalVisible}
        onOk={handleOk}
        onCancel={() => setModalVisible(false)}
        okText={editingSeries ? '保存' : '创建'}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入专栏标题' }]}>
            <Input placeholder="专栏标题" />
          </Form.Item>
          <Form.Item name="slug" label="别名" rules={[{ required: true, message: '请输入 URL 别名' }]}>
            <Input placeholder="series-slug" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="专栏简介（可选）" />
          </Form.Item>
          <Form.Item name="categoryId" label="所属分类">
            <Select
              placeholder="选择分类"
              allowClear
              options={categories.map(c => ({ value: c.id, label: c.name }))}
            />
          </Form.Item>
          <Form.Item name="isCompleted" label="状态" valuePropName="checked">
            <Switch checkedChildren="已完结" unCheckedChildren="连载中" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" placeholder="0" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default SeriesManage;
