import { useState, useEffect } from 'react';
import { Table, Button, Space, Card, Tag, Modal, Input, Form, Select, Switch, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined, OrderedListOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { Series, Category } from '../../types';
import { getAuthorSeries, getAuthorCategories, createSeries, updateSeries, deleteSeries } from '../../services/api';

const SeriesManage: React.FC = () => {
  const navigate = useNavigate();
  const [seriesList, setSeriesList] = useState<Series[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [editingSeries, setEditingSeries] = useState<Series | null>(null);
  const [form] = Form.useForm();

  const loadData = () => {
    setLoading(true);
    Promise.all([
      getAuthorSeries({ page: 1, pageSize: 100 }),
      getAuthorCategories({ page: 1, pageSize: 100 }),
    ]).then(([seriesRes, catRes]) => {
      setSeriesList(seriesRes.data.list);
      setTotal(seriesRes.data.total);
      setCategories(catRes.data.list);
    }).catch((e: Error) => message.error(e.message))
    .finally(() => setLoading(false));
  };

  useEffect(() => { loadData(); }, []);

  const openCreate = () => {
    setEditingSeries(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEdit = (series: Series) => {
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
        await updateSeries(editingSeries.id, payload);
        message.success('专栏已更新');
      } else {
        await createSeries(payload);
        message.success('专栏已创建');
      }
      setModalVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleDelete = (series: Series) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除专栏「${series.title}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSeries(series.id);
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
      render: (_: unknown, record: Series) => (
        <Tag color={record.isCompleted === 1 ? 'green' : 'orange'}>
          {record.isCompleted === 1 ? '已完结' : '连载中'}
        </Tag>
      ),
    },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: Series) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button type="link" size="small" icon={<OrderedListOutlined />} onClick={() => navigate(`/author/series/${record.id}/posts`)}>文章</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card
        title="专栏管理"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建专栏</Button>}
        styles={{ body: { padding: 0 } }}
      >
        <Table
          columns={columns}
          dataSource={seriesList.map(s => ({ ...s, key: s.id }))}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 条`,
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
    </>
  );
};

export default SeriesManage;
