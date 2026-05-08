import { useState, useEffect } from 'react';
import { Table, Button, Space, Card, Modal, Input, Form, Switch, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { SiteConfigItem } from '../../services/api';
import { getAdminConfigs, updateAdminConfigs, addAdminConfig, deleteAdminConfig } from '../../services/api';

const isSwitchKey = (key: string) => key.includes('_enabled') || key.includes('_switch');

const ConfigManage: React.FC = () => {
  const [configs, setConfigs] = useState<SiteConfigItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<SiteConfigItem | null>(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getAdminConfigs();
      setConfigs(res.data);
    } catch (e: unknown) {
      const msg = (e as Error).message || '加载失败';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  const openAdd = () => {
    setEditingItem(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEdit = (item: SiteConfigItem) => {
    setEditingItem(item);
    const switchKey = isSwitchKey(item.configKey);
    form.setFieldsValue({
      configValue: switchKey ? item.configValue === 'true' : item.configValue,
    });
    setModalVisible(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      if (editingItem) {
        const configValue = isSwitchKey(editingItem.configKey)
          ? String(values.configValue)
          : values.configValue;
        await updateAdminConfigs({ [editingItem.configKey]: configValue });
        message.success('配置已更新');
      } else {
        await addAdminConfig(values.configKey, values.configValue);
        message.success('配置已添加');
      }
      setModalVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleDelete = (item: SiteConfigItem) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除配置「${item.configKey}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminConfig(item.configKey);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          message.error((e as { message?: string })?.message || '删除失败');
        }
      },
    });
  };

  const columns = [
    {
      title: '配置键',
      dataIndex: 'configKey',
      key: 'configKey',
      width: 240,
    },
    {
      title: '配置值',
      dataIndex: 'configValue',
      key: 'configValue',
      render: (value: string, record: SiteConfigItem) => {
        if (isSwitchKey(record.configKey)) {
          return <Switch checked={value === 'true'} disabled />;
        }
        return <span style={{ wordBreak: 'break-all' }}>{value}</span>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: unknown, record: SiteConfigItem) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card
        title="配置管理"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
            添加配置
          </Button>
        }
        styles={{ body: { padding: 0 } }}
      >
        <Table
          columns={columns}
          dataSource={configs.map((c) => ({ ...c, key: c.configKey }))}
          loading={loading}
          pagination={false}
          locale={
            error
              ? {
                  emptyText: (
                    <div style={{ padding: 16 }}>
                      <p style={{ color: '#ff4d4f', marginBottom: 12 }}>加载失败：{error}</p>
                      <Button type="primary" onClick={loadData}>
                        重新加载
                      </Button>
                    </div>
                  ),
                }
              : undefined
          }
        />
      </Card>

      <Modal
        title={editingItem ? '编辑配置' : '添加配置'}
        open={modalVisible}
        onOk={handleSave}
        onCancel={() => setModalVisible(false)}
        okText={editingItem ? '保存' : '添加'}
      >
        <Form form={form} layout="vertical">
          {editingItem ? (
            <>
              <Form.Item label="配置键">
                <Input value={editingItem.configKey} disabled />
              </Form.Item>
              {isSwitchKey(editingItem.configKey) ? (
                <Form.Item name="configValue" label="配置值" valuePropName="checked">
                  <Switch />
                </Form.Item>
              ) : (
                <Form.Item
                  name="configValue"
                  label="配置值"
                  rules={[{ required: true, message: '请输入配置值' }]}
                >
                  <Input placeholder="配置值" />
                </Form.Item>
              )}
            </>
          ) : (
            <>
              <Form.Item
                name="configKey"
                label="配置键"
                rules={[{ required: true, message: '请输入配置键' }]}
              >
                <Input placeholder="如：site_title" />
              </Form.Item>
              <Form.Item
                name="configValue"
                label="配置值"
                rules={[{ required: true, message: '请输入配置值' }]}
              >
                <Input placeholder="配置值" />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </>
  );
};

export default ConfigManage;
