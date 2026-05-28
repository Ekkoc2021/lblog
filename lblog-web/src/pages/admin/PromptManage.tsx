import { useState, useEffect, useMemo } from 'react';
import { Table, Button, Space, Card, Segmented, Tag, message, Modal, Form, Input, InputNumber, Drawer, Timeline, Typography, Descriptions } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, HistoryOutlined, AuditOutlined, ReloadOutlined, ImportOutlined, EyeOutlined } from '@ant-design/icons';
import type { AdminPrompt, AdminPromptAudit } from '../../types';
import {
  getAdminPrompts, createAdminPrompt, updateAdminPromptContent, updateAdminPromptMeta,
  deleteAdminPrompt, getAdminPromptVersions, getAdminPromptAudit,
  reloadPromptCache, seedPrompts
} from '../../services/api';

const { Text, Paragraph } = Typography;

const PAGE_SIZE = 10;

const PromptManage: React.FC = () => {
  const [prompts, setPrompts] = useState<AdminPrompt[]>([]);
  const [loading, setLoading] = useState(false);
  const [module, setModule] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);

  // Modals & Drawers
  const [createVisible, setCreateVisible] = useState(false);
  const [editContentVisible, setEditContentVisible] = useState(false);
  const [editMetaVisible, setEditMetaVisible] = useState(false);
  const [versionDrawerVisible, setVersionDrawerVisible] = useState(false);
  const [auditDrawerVisible, setAuditDrawerVisible] = useState(false);
  const [seedVisible, setSeedVisible] = useState(false);
  const [selectedPrompt, setSelectedPrompt] = useState<AdminPrompt | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [createForm] = Form.useForm();
  const [editContentForm] = Form.useForm();
  const [editMetaForm] = Form.useForm();
  const [seedForm] = Form.useForm();

  const loadData = () => {
    setLoading(true);
    getAdminPrompts({ module, isActive: true })
      .then(res => setPrompts(res.data))
      .catch((e: Error) => message.error(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, [module]);

  // 提取所有不重复的 module 作为 tabs
  const modules = useMemo(() => {
    const set = new Set(prompts.map(p => p.module));
    return ['全部', ...Array.from(set).sort()];
  }, [prompts]);

  // 客户端分页
  const paginatedData = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE;
    return prompts.slice(start, start + PAGE_SIZE);
  }, [prompts, page]);

  // 切换模块时重置页码
  const handleModuleChange = (val: string) => {
    setModule(val === '全部' ? undefined : val);
    setPage(1);
  };

  // ---- 创建 ----
  const openCreate = () => {
    createForm.resetFields();
    setCreateVisible(true);
  };

  const handleCreate = async () => {
    setSubmitting(true);
    try {
      const values = await createForm.validateFields();
      await createAdminPrompt(values);
      message.success('提示词已创建');
      setCreateVisible(false);
      setPage(1);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  // ---- 编辑内容 ----
  const openEditContent = (record: AdminPrompt) => {
    setSelectedPrompt(record);
    editContentForm.setFieldsValue({ content: record.content });
    setEditContentVisible(true);
  };

  const handleEditContent = () => {
    if (!selectedPrompt) return;
    Modal.confirm({
      title: '确认修改内容',
      content: `修改后将生成新版本 v${selectedPrompt.version + 1}，旧版本 v${selectedPrompt.version} 自动失效。确认？`,
      okText: '确认修改',
      onOk: async () => {
        try {
          const values = await editContentForm.validateFields();
          await updateAdminPromptContent(selectedPrompt.id, values.content, 'admin');
          message.success(`已更新至 v${selectedPrompt.version + 1}`);
          setEditContentVisible(false);
          loadData();
        } catch (e: unknown) {
          if (e instanceof Error) message.error(e.message);
        }
      },
    });
  };

  // ---- 编辑元信息 ----
  const openEditMeta = (record: AdminPrompt) => {
    setSelectedPrompt(record);
    editMetaForm.setFieldsValue({
      description: record.description,
      sortOrder: record.sortOrder,
    });
    setEditMetaVisible(true);
  };

  const handleEditMeta = async () => {
    if (!selectedPrompt) return;
    setSubmitting(true);
    try {
      const values = await editMetaForm.validateFields();
      await updateAdminPromptMeta(selectedPrompt.id, { ...values, operator: 'admin' });
      message.success('元信息已更新');
      setEditMetaVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  // ---- 删除 ----
  const handleDelete = (record: AdminPrompt) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定删除提示词「${record.module}/${record.promptKey}」吗？将设为失效。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminPrompt(record.id, 'admin');
          message.success('已删除');
          setPage(1);
          loadData();
        } catch (e: unknown) {
          message.error((e as { message?: string })?.message || '删除失败');
        }
      },
    });
  };

  // ---- 版本历史 drawer ----
  const [versions, setVersions] = useState<AdminPrompt[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [viewingVersionContent, setViewingVersionContent] = useState<AdminPrompt | null>(null);

  const openVersions = (record: AdminPrompt) => {
    setSelectedPrompt(record);
    setViewingVersionContent(null);
    setVersionsLoading(true);
    setVersionDrawerVisible(true);
    getAdminPromptVersions(record.id)
      .then(res => setVersions(res.data))
      .catch((e: Error) => message.error(e.message))
      .finally(() => setVersionsLoading(false));
  };

  // ---- 审计日志 drawer ----
  const [audits, setAudits] = useState<AdminPromptAudit[]>([]);
  const [auditsLoading, setAuditsLoading] = useState(false);

  const openAudit = (record: AdminPrompt) => {
    setSelectedPrompt(record);
    setAuditsLoading(true);
    setAuditDrawerVisible(true);
    getAdminPromptAudit(record.id)
      .then(res => setAudits(res.data))
      .catch((e: Error) => message.error(e.message))
      .finally(() => setAuditsLoading(false));
  };

  // ---- 缓存 & 导入 ----
  const handleReload = () => {
    reloadPromptCache()
      .then(() => message.success('缓存已重载'))
      .catch((e: Error) => message.error(e.message));
  };

  const handleSeed = async () => {
    setSubmitting(true);
    try {
      const values = await seedForm.validateFields();
      const res = await seedPrompts(values.module);
      message.success(typeof res.data === 'string' ? res.data : '导入完成');
      setSeedVisible(false);
      setPage(1);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  // ---- 表格列 ----
  const columns = [
    {
      title: 'Module', dataIndex: 'module', key: 'module', width: 100,
      render: (m: string) => <Tag color="blue">{m}</Tag>,
    },
    { title: 'Key', dataIndex: 'promptKey', key: 'promptKey', width: 130 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 160, ellipsis: true },
    {
      title: '版本', dataIndex: 'version', key: 'version', width: 60, align: 'center' as const,
      render: (v: number) => <Tag>v{v}</Tag>,
    },
    {
      title: '状态', dataIndex: 'isActive', key: 'isActive', width: 60, align: 'center' as const,
      render: (v: boolean) => v ? <Tag color="green">生效</Tag> : <Tag color="default">失效</Tag>,
    },
    {
      title: '操作', key: 'action', width: 280, fixed: 'right' as const,
      render: (_: unknown, record: AdminPrompt) => (
        <Space size="small" wrap>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditContent(record)}>内容</Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditMeta(record)}>信息</Button>
          <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => openVersions(record)}>版本</Button>
          <Button type="link" size="small" icon={<AuditOutlined />} onClick={() => openAudit(record)}>审计</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1400, margin: '0 auto' }}>
      {/* ---- 工具栏 ---- */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Segmented
          options={modules}
          value={module || '全部'}
          onChange={handleModuleChange}
        />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={handleReload}>重载缓存</Button>
          <Button icon={<ImportOutlined />} onClick={() => { seedForm.resetFields(); setSeedVisible(true); }}>导入文件</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建提示词</Button>
        </Space>
      </div>

      {/* ---- 表格 ---- */}
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={paginatedData.map(p => ({ ...p, key: p.id }))}
          loading={loading}
          scroll={{ x: 1000 }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: prompts.length,
            showTotal: (t) => `共 ${t} 条`,
            showSizeChanger: false,
            onChange: (p) => setPage(p),
          }}
        />
      </Card>

      {/* ---- 新建提示词 Modal ---- */}
      <Modal
        title="新建提示词"
        open={createVisible}
        onOk={handleCreate}
        onCancel={() => setCreateVisible(false)}
        confirmLoading={submitting}
        okText="创建"
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="module" label="模块" rules={[{ required: true, message: '请输入模块名' }]}>
            <Input placeholder="draw / chat / codegen" />
          </Form.Item>
          <Form.Item name="promptKey" label="Key" rules={[{ required: true, message: '请输入 prompt key' }]}>
            <Input placeholder="system-default" />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入提示词内容' }]}>
            <Input.TextArea rows={10} placeholder="Markdown 格式的提示词内容" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="简要说明此提示词的作用" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber placeholder="0" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ---- 编辑内容 Modal ---- */}
      <Modal
        title={selectedPrompt ? `编辑内容 — ${selectedPrompt.module}/${selectedPrompt.promptKey} (v${selectedPrompt.version})` : '编辑内容'}
        open={editContentVisible}
        onOk={handleEditContent}
        onCancel={() => setEditContentVisible(false)}
        okText="保存为新版本"
        width={800}
      >
        <div style={{ marginBottom: 12 }}>
          <Descriptions size="small" column={3}>
            <Descriptions.Item label="模块"><Tag color="blue">{selectedPrompt?.module}</Tag></Descriptions.Item>
            <Descriptions.Item label="Key">{selectedPrompt?.promptKey}</Descriptions.Item>
            <Descriptions.Item label="当前版本">v{selectedPrompt?.version}</Descriptions.Item>
          </Descriptions>
        </div>
        <Form form={editContentForm} layout="vertical">
          <Form.Item name="content" rules={[{ required: true, message: '请输入内容' }]}>
            <Input.TextArea rows={18} placeholder="Markdown 格式的提示词内容" style={{ fontFamily: 'monospace' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ---- 编辑元信息 Modal ---- */}
      <Modal
        title={selectedPrompt ? `编辑信息 — ${selectedPrompt.module}/${selectedPrompt.promptKey}` : '编辑信息'}
        open={editMetaVisible}
        onOk={handleEditMeta}
        onCancel={() => setEditMetaVisible(false)}
        confirmLoading={submitting}
        okText="保存"
      >
        <Form form={editMetaForm} layout="vertical">
          <Form.Item name="description" label="描述">
            <Input placeholder="提示词说明" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber placeholder="0" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ---- 版本历史 Drawer ---- */}
      <Drawer
        title={selectedPrompt ? `版本历史 — ${selectedPrompt.module}/${selectedPrompt.promptKey}` : '版本历史'}
        open={versionDrawerVisible}
        onClose={() => setVersionDrawerVisible(false)}
        width={700}
        loading={versionsLoading}
      >
        {viewingVersionContent ? (
          <div>
            <Button type="link" onClick={() => setViewingVersionContent(null)} style={{ padding: 0, marginBottom: 12 }}>
              ← 返回版本列表
            </Button>
            <Descriptions size="small" column={2} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="版本">v{viewingVersionContent.version}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {viewingVersionContent.isActive ? <Tag color="green">生效中</Tag> : <Tag color="default">已失效</Tag>}
              </Descriptions.Item>
            </Descriptions>
            <pre style={{
              background: '#f5f5f5', padding: 16, borderRadius: 8, maxHeight: '60vh',
              overflow: 'auto', whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6,
            }}>
              {viewingVersionContent.content}
            </pre>
          </div>
        ) : versions.length === 0 ? (
          <Text type="secondary">暂无历史版本</Text>
        ) : (
          <Timeline
            items={versions.map(v => ({
              color: v.isActive ? 'green' : 'gray',
              children: (
                <div key={v.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Tag color={v.isActive ? 'green' : 'default'}>v{v.version}</Tag>
                    <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                      {v.createdAt}
                    </Text>
                  </div>
                  <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setViewingVersionContent(v)}>
                    查看内容
                  </Button>
                </div>
              ),
            }))}
          />
        )}
      </Drawer>

      {/* ---- 审计日志 Drawer ---- */}
      <Drawer
        title={selectedPrompt ? `审计日志 — ${selectedPrompt.module}/${selectedPrompt.promptKey}` : '审计日志'}
        open={auditDrawerVisible}
        onClose={() => setAuditDrawerVisible(false)}
        width={600}
        loading={auditsLoading}
      >
        {audits.length === 0 ? (
          <Text type="secondary">暂无审计记录</Text>
        ) : (
          <Timeline
            items={audits.map(a => ({
              color: a.action === 'CREATE' ? 'green' : a.action === 'DEACTIVATE' ? 'red' : 'blue',
              children: (
                <div key={a.id}>
                  <div style={{ marginBottom: 4 }}>
                    <Tag color={a.action === 'CREATE' ? 'green' : a.action === 'DEACTIVATE' ? 'red' : 'blue'}>
                      {a.action}
                    </Tag>
                    <Text style={{ fontSize: 12 }}>{a.operator}</Text>
                    <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>{a.createdAt}</Text>
                  </div>
                  {a.remark && <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 4 }}>{a.remark}</Paragraph>}
                  {(a.oldVersion && a.newVersion) && (
                    <Text type="secondary" style={{ fontSize: 12 }}>v{a.oldVersion} → v{a.newVersion}</Text>
                  )}
                </div>
              ),
            }))}
          />
        )}
      </Drawer>

      {/* ---- 导入文件 Modal ---- */}
      <Modal
        title="从文件导入提示词"
        open={seedVisible}
        onOk={handleSeed}
        onCancel={() => setSeedVisible(false)}
        confirmLoading={submitting}
        okText="导入"
      >
        <Form form={seedForm} layout="vertical">
          <Form.Item name="module" label="模块名" rules={[{ required: true, message: '请输入模块名' }]}>
            <Input placeholder="draw" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PromptManage;
