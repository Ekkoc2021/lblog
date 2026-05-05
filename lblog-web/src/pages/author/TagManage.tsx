import { useState, useEffect } from 'react';
import { Table, Button, Space, Card, Modal, Input, Form, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { Tag } from '../../types';
import { getAuthorTags, createTag, updateTag, deleteTag } from '../../services/api';

const TagManage: React.FC = () => {
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTag, setEditingTag] = useState<Tag | null>(null);
  const [form] = Form.useForm();

  const loadData = () => {
    setLoading(true);
    getAuthorTags({ page: 1, pageSize: 100 })
      .then(res => {
        setTags(res.data.list);
        setTotal(res.data.total);
      })
      .catch((e: Error) => message.error(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadData(); }, []);

  const openCreate = () => {
    setEditingTag(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEdit = (tag: Tag) => {
    setEditingTag(tag);
    form.setFieldsValue(tag);
    setModalVisible(true);
  };

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (editingTag) {
        await updateTag(editingTag.id, values);
        message.success('标签已更新');
      } else {
        await createTag(values);
        message.success('标签已创建');
      }
      setModalVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleDelete = (tag: Tag) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除「${tag.name}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteTag(tag.id);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          if (e instanceof Error) message.error(e.message);
        }
      },
    });
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    { title: '别名', dataIndex: 'slug', key: 'slug', width: 150 },
    { title: '文章数', dataIndex: 'postCount', key: 'postCount', width: 70, align: 'right' as const },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: unknown, record: Tag) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card
        title="标签管理"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建标签</Button>}
        styles={{ body: { padding: 0 } }}
      >
        <Table
          columns={columns}
          dataSource={tags.map(t => ({ ...t, key: t.id }))}
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
        title={editingTag ? '编辑标签' : '新建标签'}
        open={modalVisible}
        onOk={handleOk}
        onCancel={() => setModalVisible(false)}
        okText={editingTag ? '保存' : '创建'}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入标签名称' }]}>
            <Input placeholder="标签名称" />
          </Form.Item>
          <Form.Item name="slug" label="别名" rules={[{ required: true, message: '请输入 URL 别名' }]}>
            <Input placeholder="tag-slug" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default TagManage;
