import { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, Card, Input, Select, Modal, Tooltip, message } from 'antd';
import { CheckOutlined, CloseOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { AdminComment } from '../../types';
import { getAdminComments, reviewAdminComment, deleteAdminComment, batchAdminComments } from '../../services/api';

const statusMap: Record<number, { color: string; text: string }> = {
  0: { color: 'blue', text: '待审' },
  1: { color: 'green', text: '通过' },
  2: { color: 'red', text: '驳回' },
};

const CommentManage: React.FC = () => {
  const [comments, setComments] = useState<AdminComment[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [searchValue, setSearchValue] = useState('');
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [batchLoading, setBatchLoading] = useState(false);

  const loadData = (signal?: AbortSignal) => {
    setLoading(true);
    getAdminComments({ page, pageSize, status: statusFilter, keyword }, signal)
      .then(res => {
        setComments(res.data.list);
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
    loadData(controller.signal);
    return () => controller.abort();
  }, [page, pageSize, statusFilter, keyword]);

  const handleSearch = () => {
    setPage(1);
    setKeyword(searchValue);
  };

  const handleReview = async (id: number, status: number) => {
    try {
      await reviewAdminComment(id, status);
      message.success(status === 1 ? '已通过' : '已驳回');
      loadData();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleDelete = (record: AdminComment) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除这条评论吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminComment(record.id);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          if (e instanceof Error) message.error(e.message);
        }
      },
    });
  };

  const handleBatchAction = async (action: 'APPROVE' | 'REJECT' | 'DELETE') => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择评论');
      return;
    }

    const doAction = async () => {
      setBatchLoading(true);
      try {
        const res = await batchAdminComments(selectedRowKeys, action);
        const actionText = action === 'APPROVE' ? '通过' : action === 'REJECT' ? '驳回' : '删除';
        if (res.data && res.data.failedIds && res.data.failedIds.length > 0) {
          message.warning(`批量${actionText}部分成功：成功 ${res.data.successCount} 个，失败 ${res.data.failedIds.length} 个`);
        } else {
          message.success(`批量${actionText}成功`);
        }
        loadData();
      } catch (e: unknown) {
        if (e instanceof Error) message.error(e.message);
      } finally {
        setBatchLoading(false);
      }
    };

    if (action === 'DELETE') {
      Modal.confirm({
        title: '确认批量删除',
        content: `确定要删除选中的 ${selectedRowKeys.length} 条评论吗？`,
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
      title: '评论内容', key: 'content', width: 300,
      render: (_: unknown, record: AdminComment) => (
        <Tooltip title={record.content}>
          <div style={{
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-all',
          }}>{record.content}</div>
        </Tooltip>
      ),
    },
    {
      title: '评论者', key: 'author', width: 100,
      render: (_: unknown, record: AdminComment) => record.author?.nickname || '-',
    },
    {
      title: '回复', key: 'replyTo', width: 80,
      render: (_: unknown, record: AdminComment) => record.replyTo ? `@${record.replyTo.nickname}` : '-',
    },
    {
      title: '所属文章', key: 'post', width: 160, ellipsis: true,
      render: (_: unknown, record: AdminComment) => (
        <a href={`/posts/${record.postSlug}`} target="_blank" rel="noopener noreferrer">{record.postTitle || `#${record.postId}`}</a>
      ),
    },
    {
      title: '状态', key: 'status', width: 70,
      render: (_: unknown, record: AdminComment) => {
        const m = statusMap[record.status] || { color: 'default', text: String(record.status) };
        return <Tag color={m.color}>{m.text}</Tag>;
      },
    },
    { title: 'IP', dataIndex: 'ipAddress', key: 'ipAddress', width: 120 },
    {
      title: '时间', key: 'createdAt', width: 100,
      render: (_: unknown, record: AdminComment) => record.createdAt?.slice(0, 10),
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right' as const,
      render: (_: unknown, record: AdminComment) => (
        <Space size="small">
          {record.status !== 1 && (
            <Button type="link" size="small" icon={<CheckOutlined />} style={{ color: '#52c41a' }} onClick={() => handleReview(record.id, 1)}>通过</Button>
          )}
          {record.status !== 2 && (
            <Button type="link" size="small" icon={<CloseOutlined />} style={{ color: '#ff4d4f' }} onClick={() => handleReview(record.id, 2)}>驳回</Button>
          )}
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1500, margin: '0 auto' }}>
      <Card
        title="评论审核管理"
        extra={
          <Space wrap>
            <Input
              placeholder="搜索评论内容"
              prefix={<SearchOutlined />}
              value={searchValue}
              onChange={e => setSearchValue(e.target.value)}
              onPressEnter={handleSearch}
              style={{ width: 220 }}
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
                { value: 0, label: '待审' },
                { value: 1, label: '通过' },
                { value: 2, label: '驳回' },
              ]}
            />
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
          dataSource={comments.map(c => ({ ...c, key: c.id }))}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 条`,
            showSizeChanger: true,
            pageSizeOptions: ['10', '15', '20'],
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
          title={selectedRowKeys.length > 0 ? () => (
            <Space>
              <span>已选 {selectedRowKeys.length} 项</span>
              <Button size="small" type="primary" loading={batchLoading} onClick={() => handleBatchAction('APPROVE')}>批量通过</Button>
              <Button size="small" style={{ color: '#ff4d4f' }} loading={batchLoading} onClick={() => handleBatchAction('REJECT')}>批量驳回</Button>
              <Button size="small" danger loading={batchLoading} onClick={() => handleBatchAction('DELETE')}>批量删除</Button>
            </Space>
          ) : undefined}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  );
};

export default CommentManage;
