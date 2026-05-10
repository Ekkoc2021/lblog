import { useState, useEffect, useCallback } from 'react';
import {
  Card, Input, Select, Button, Table, Tag, message, Modal,
  Form, Typography, Tooltip, Space, Pagination,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, KeyOutlined,
} from '@ant-design/icons';
import type { AdminUser, RoleInfo } from '../../services/api';
import {
  getAdminUsers, getRoles, createAdminUser, updateAdminUser,
  deleteAdminUser, resetUserPassword,
} from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';

const { Title, Text } = Typography;

function relativeTime(dateStr: string | null): string {
  if (!dateStr) return '从未登录';
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) return `${days}天前`;
  if (hours > 0) return `${hours}小时前`;
  if (minutes > 0) return `${minutes}分钟前`;
  return '刚刚';
}

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: '1', label: '正常' },
  { value: '0', label: '禁用' },
];

const activityOptions = [
  { value: '', label: '全部' },
  { value: '7d', label: '近7天' },
  { value: '30d', label: '近30天' },
  { value: 'inactive_30', label: '超过30天未登录' },
  { value: 'inactive_90', label: '超过90天未登录' },
  { value: 'never', label: '从未登录' },
];

const UserManage: React.FC = () => {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [activity, setActivity] = useState('');

  // Modal states
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [modalLoading, setModalLoading] = useState(false);

  // Password reset modal
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [resetUserId, setResetUserId] = useState<number | null>(null);
  const [resetUsername, setResetUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordLoading, setPasswordLoading] = useState(false);

  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const inactiveDays = activity === 'inactive_30' ? 30
        : activity === 'inactive_90' ? 90
        : undefined;

      const res = await getAdminUsers({
        page,
        pageSize,
        keyword: keyword || undefined,
        role: roleFilter || undefined,
        status: statusFilter ? Number(statusFilter) : undefined,
        inactiveDays,
      });
      setUsers(res.data.list);
      setTotal(res.data.total);
    } catch (e: unknown) {
      const msg = (e as Error).message || '加载失败';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword, roleFilter, statusFilter, activity]);

  const loadRoles = useCallback(async () => {
    try {
      const res = await getRoles();
      setRoles(res.data);
    } catch {
      // ignore — roles are loaded best-effort for filter dropdown usage
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);
  useEffect(() => { loadRoles(); }, [loadRoles]);

  const handleSearch = (value: string) => {
    setKeyword(value);
    setPage(1);
  };

  const openCreateModal = () => {
    setEditingUser(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEditModal = (user: AdminUser) => {
    setEditingUser(user);
    form.setFieldsValue({
      nickname: user.nickname,
      email: user.email,
      roleIds: roles.filter((r) => user.roles.includes(r.name)).map((r) => r.id),
      status: user.status,
    });
    setModalVisible(true);
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      setModalLoading(true);

      if (editingUser) {
        await updateAdminUser(editingUser.id, {
          nickname: values.nickname,
          email: values.email,
          roleIds: values.roleIds,
          status: values.status,
        });
        message.success('用户已更新');
      } else {
        await createAdminUser({
          username: values.username,
          password: values.password,
          nickname: values.nickname,
          email: values.email,
          roleIds: values.roleIds,
        });
        message.success('用户已创建');
      }
      setModalVisible(false);
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error && !e.message.includes('Validation')) {
        message.error(e.message);
      }
    } finally {
      setModalLoading(false);
    }
  };

  const handleDelete = (user: AdminUser) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除用户「${user.username}」吗？此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminUser(user.id);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          message.error((e as Error).message || '删除失败');
        }
      },
    });
  };

  const openResetPasswordModal = (userId: number, username: string) => {
    setResetUserId(userId);
    setResetUsername(username);
    setNewPassword('');
    setConfirmPassword('');
    setPasswordVisible(true);
  };

  const handleResetPassword = async () => {
    if (!newPassword) {
      message.error('请输入新密码');
      return;
    }
    if (newPassword !== confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }
    if (newPassword.length < 6) {
      message.error('密码长度不能少于6位');
      return;
    }
    if (resetUserId === null) return;

    setPasswordLoading(true);
    try {
      await resetUserPassword(resetUserId, newPassword);
      message.success('密码已重置');
      setPasswordVisible(false);
    } catch (e: unknown) {
      message.error((e as Error).message || '重置密码失败');
    } finally {
      setPasswordLoading(false);
    }
  };

  const columns = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 120,
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 120,
    },
    {
      title: '角色',
      key: 'roles',
      width: 200,
      render: (_: unknown, record: AdminUser) => (
        <>
          {(record.roleLabels ?? []).map((label, i) => (
            <Tag key={i} color={(record.roles ?? [])[i] === 'admin' ? 'red' : 'blue'}>
              {label}
            </Tag>
          ))}
        </>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: number) => (
        <Tag color={status === 1 ? 'green' : 'red'}>
          {status === 1 ? '正常' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '文章数',
      dataIndex: 'postCount',
      key: 'postCount',
      width: 80,
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      width: 120,
      render: (val: string | null) => (
        <Text type={val ? undefined : 'secondary'}>
          {relativeTime(val)}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_: unknown, record: AdminUser) => {
        const isSelf = currentUser?.id === record.id;
        return (
          <Space>
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEditModal(record)}
            >
              编辑
            </Button>
            <Tooltip title={isSelf ? '不能删除自己' : undefined}>
              <Button
                type="link"
                danger
                size="small"
                icon={<DeleteOutlined />}
                disabled={isSelf}
                onClick={() => !isSelf && handleDelete(record)}
              >
                删除
              </Button>
            </Tooltip>
          </Space>
        );
      },
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 16 }}>用户管理</Title>

      {/* Filter Bar */}
      <Card style={{ marginBottom: 24 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Input.Search
            placeholder="搜索用户名/昵称"
            onSearch={handleSearch}
            allowClear
            style={{ width: 200 }}
          />
          <Select
            style={{ width: 130 }}
            value={roleFilter}
            onChange={(v) => { setRoleFilter(v); setPage(1); }}
          >
            <Select.Option value="">全部角色</Select.Option>
            {roles.map((r) => (
              <Select.Option key={r.id} value={r.name}>{r.label}</Select.Option>
            ))}
          </Select>
          <Select
            style={{ width: 130 }}
            value={statusFilter}
            onChange={(v) => { setStatusFilter(v); setPage(1); }}
            options={statusOptions}
          />
          <Select
            style={{ width: 170 }}
            value={activity}
            onChange={(v) => { setActivity(v); setPage(1); }}
            options={activityOptions}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            新建用户
          </Button>
        </Space>
      </Card>

      {/* Table */}
      <Card>
        {error ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <p style={{ color: '#ff4d4f', marginBottom: 12 }}>加载失败：{error}</p>
            <Button type="primary" icon={<ReloadOutlined />} onClick={loadData}>
              重新加载
            </Button>
          </div>
        ) : (
          <>
            <Table
              dataSource={users}
              columns={columns}
              rowKey="id"
              loading={loading}
              pagination={false}
              locale={{ emptyText: '暂无用户' }}
              onRow={(record) => ({
                style: record.status === 0 ? { color: 'rgba(0, 0, 0, 0.45)' } : {},
              })}
            />
            {total > pageSize && (
              <div style={{ textAlign: 'center', marginTop: 16 }}>
                <Pagination
                  current={page}
                  pageSize={pageSize}
                  total={total}
                  onChange={(p) => setPage(p)}
                  showTotal={(t) => `共 ${t} 人`}
                />
              </div>
            )}
          </>
        )}
      </Card>

      {/* Create/Edit Modal */}
      <Modal
        title={editingUser ? '编辑用户' : '新建用户'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        confirmLoading={modalLoading}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ status: 1 }}
        >
          {editingUser ? (
            <>
              <Form.Item label="用户名">
                <Text strong>{editingUser.username}</Text>
              </Form.Item>
              <Form.Item label="密码">
                <Button
                  icon={<KeyOutlined />}
                  onClick={() => {
                    setModalVisible(false);
                    openResetPasswordModal(editingUser.id, editingUser.username);
                  }}
                >
                  重置密码
                </Button>
              </Form.Item>
            </>
          ) : (
            <>
              <Form.Item
                name="username"
                label="用户名"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input placeholder="请输入用户名" />
              </Form.Item>
              <Form.Item
                name="password"
                label="密码"
                rules={[
                  { required: true, message: '请输入密码' },
                  { min: 6, message: '密码长度不能少于6位' },
                ]}
              >
                <Input.Password placeholder="请输入密码" />
              </Form.Item>
            </>
          )}
          <Form.Item name="nickname" label="昵称">
            <Input placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
          >
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roles.map((r) => ({ value: r.id, label: r.label }))}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 1, label: '正常' },
                { value: 0, label: '禁用' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Reset Password Modal */}
      <Modal
        title={`重置密码 - ${resetUsername}`}
        open={passwordVisible}
        onOk={handleResetPassword}
        onCancel={() => setPasswordVisible(false)}
        confirmLoading={passwordLoading}
        okText="确认重置"
        destroyOnClose
      >
        <div style={{ padding: '8px 0' }}>
          <div style={{ marginBottom: 16 }}>
            <Text type="secondary">新密码</Text>
            <Input.Password
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="请输入新密码"
              style={{ marginTop: 4 }}
            />
          </div>
          <div>
            <Text type="secondary">确认密码</Text>
            <Input.Password
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="请再次输入新密码"
              style={{ marginTop: 4 }}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default UserManage;
