import { useState, useEffect, useCallback } from 'react';
import {
  Card, Row, Col, Input, Select, Button, Spin, Tag, Popover,
  Image, message, Modal, Statistic, Typography, Empty, Tooltip, Pagination,
} from 'antd';
import {
  DeleteOutlined, ReloadOutlined,
} from '@ant-design/icons';
import type { AdminImage, ImageStatistics } from '../../services/api';
import {
  getAdminImages, getImageStatistics, deleteAdminImage, cleanupImages,
} from '../../services/api';

const { Title, Text } = Typography;

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(dateStr: string): string {
  return dateStr.slice(0, 10);
}

const sortOptions = [
  { value: 'newest', label: '最新上传' },
  { value: 'oldest', label: '最早上传' },
  { value: 'largest', label: '最大体积' },
  { value: 'smallest', label: '最小体积' },
  { value: 'most_used', label: '最多引用' },
];

const statusOptions = [
  { value: 'all', label: '全部' },
  { value: 'referenced', label: '已引用' },
  { value: 'unreferenced', label: '未引用' },
];

const ImageManage: React.FC = () => {
  const [images, setImages] = useState<AdminImage[]>([]);
  const [statistics, setStatistics] = useState<ImageStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [sort, setSort] = useState('newest');
  const [status, setStatus] = useState('all');
  const [keyword, setKeyword] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [imgRes, statRes] = await Promise.all([
        getAdminImages({
          page,
          pageSize,
          sort,
          status,
          keyword: keyword || undefined,
        }),
        getImageStatistics(),
      ]);
      setImages(imgRes.data.list);
      setTotal(imgRes.data.total);
      setStatistics(statRes.data);
    } catch (e: unknown) {
      const msg = (e as Error).message || '加载失败';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, sort, status, keyword]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleDelete = (image: AdminImage) => {
    if (image.usageCount > 0) return;
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除图片「${image.originalName}」吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminImage(image.id);
          message.success('已删除');
          loadData();
        } catch (e: unknown) {
          message.error((e as Error).message || '删除失败');
        }
      },
    });
  };

  const handleCleanup = async () => {
    try {
      const previewRes = await cleanupImages({ dryRun: true });
      const previewData = previewRes.data as {
        dryRun: boolean;
        count: number;
        totalSize: number;
        images: Array<{
          id: number;
          url: string;
          originalName: string;
          fileSize: number;
          createdAt: string;
        }>;
      };

      Modal.confirm({
        title: '清理未引用图片',
        width: 600,
        content: (
          <div>
            <p>
              将删除 <Text strong>{previewData.count}</Text> 张未引用的图片，
              释放 <Text strong>{formatFileSize(previewData.totalSize)}</Text> 空间。
            </p>
            {previewData.images && previewData.images.length > 0 && (
              <div style={{ maxHeight: 300, overflow: 'auto', marginTop: 12 }}>
                {previewData.images.map((img) => (
                  <div
                    key={img.id}
                    style={{
                      padding: '4px 0',
                      display: 'flex',
                      justifyContent: 'space-between',
                      borderBottom: '1px solid #f0f0f0',
                    }}
                  >
                    <Text ellipsis style={{ maxWidth: 300 }}>
                      {img.originalName}
                    </Text>
                    <Text type="secondary">{formatFileSize(img.fileSize)}</Text>
                  </div>
                ))}
              </div>
            )}
          </div>
        ),
        okText: '确认清理',
        okType: 'danger',
        cancelText: '取消',
        onOk: async () => {
          try {
            await cleanupImages({ dryRun: false });
            message.success('清理完成');
            loadData();
          } catch (e: unknown) {
            message.error((e as Error).message || '清理失败');
          }
        },
      });
    } catch (e: unknown) {
      message.error((e as Error).message || '预览失败');
    }
  };

  const handleSearch = (value: string) => {
    setKeyword(value);
    setPage(1);
  };

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 16 }}>图片管理</Title>

      {/* Statistics Cards */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="图片总数" value={statistics?.totalImages ?? 0} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="已使用" value={statistics?.referencedCount ?? 0} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="未使用" value={statistics?.unreferencedCount ?? 0} valueStyle={{ color: '#faad14' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic
              title="可清理"
              value={statistics?.oldUnreferencedCount ?? 0}
              suffix={`/ ${formatFileSize(statistics?.oldUnreferencedSize ?? 0)}`}
              valueStyle={{ color: '#ff4d4f' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Filter Bar */}
      <Card style={{ marginBottom: 24 }} styles={{ body: { padding: 16 } }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input.Search
              placeholder="搜索文件名"
              onSearch={handleSearch}
              allowClear
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              style={{ width: '100%' }}
              value={sort}
              onChange={(v) => { setSort(v); setPage(1); }}
              options={sortOptions}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              style={{ width: '100%' }}
              value={status}
              onChange={(v) => { setStatus(v); setPage(1); }}
              options={statusOptions}
            />
          </Col>
          <Col xs={24} sm={4} md={10} style={{ textAlign: 'right' }}>
            <Button type="primary" danger icon={<DeleteOutlined />} onClick={handleCleanup}>
              清理未引用图片
            </Button>
          </Col>
        </Row>
      </Card>

      {/* Content Area */}
      {error ? (
        <Card>
          <div style={{ textAlign: 'center', padding: 40 }}>
            <p style={{ color: '#ff4d4f', marginBottom: 12 }}>加载失败：{error}</p>
            <Button type="primary" icon={<ReloadOutlined />} onClick={loadData}>
              重新加载
            </Button>
          </div>
        </Card>
      ) : loading && images.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Spin size="large" />
        </div>
      ) : images.length === 0 ? (
        <Card>
          <Empty description="暂无图片" />
        </Card>
      ) : (
        <>
          <Row gutter={[16, 16]}>
            {images.map((img) => (
              <Col xs={12} sm={8} md={6} key={img.id}>
                <Card
                  hoverable
                  styles={{ body: { padding: 12 } }}
                  cover={
                    <div
                      style={{
                        height: 160,
                        overflow: 'hidden',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: '#f5f5f5',
                      }}
                    >
                      <Image
                        src={img.url}
                        alt={img.originalName}
                        style={{ maxWidth: '100%', maxHeight: 160, objectFit: 'contain' }}
                        preview={{ mask: '点击预览' }}
                      />
                    </div>
                  }
                >
                  <div style={{ fontSize: 13, lineHeight: '1.8' }}>
                    <div
                      style={{
                        fontWeight: 500,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {img.originalName}
                    </div>
                    <Text type="secondary">
                      {img.width} x {img.height}
                    </Text>
                    <br />
                    <Text type="secondary">{formatFileSize(img.fileSize)}</Text>
                    <br />
                    {img.usageCount > 0 ? (
                      <Popover
                        title="引用详情"
                        content={
                          <div style={{ maxWidth: 280 }}>
                            {img.usages.map((u, i) => (
                              <div key={i} style={{ padding: '2px 0', fontSize: 13 }}>
                                文章「{u.refTitle}」({u.field === 'featured_image' ? '封面' : u.field === 'body' ? '正文' : u.field})
                              </div>
                            ))}
                          </div>
                        }
                        trigger="click"
                      >
                        <Tag color="blue" style={{ cursor: 'pointer' }}>
                          引用: {img.usageCount}处
                        </Tag>
                      </Popover>
                    ) : (
                      <Tag color="red">未引用</Tag>
                    )}
                    <br />
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatDate(img.createdAt)}
                    </Text>
                    <br />
                    <Tooltip title={img.usageCount > 0 ? '该图片被引用中' : undefined}>
                      <Button
                        type="link"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        disabled={img.usageCount > 0}
                        onClick={() => handleDelete(img)}
                        style={{ padding: 0 }}
                      >
                        删除
                      </Button>
                    </Tooltip>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>

          {total > pageSize && (
            <div style={{ textAlign: 'center', marginTop: 24 }}>
              <Pagination
                current={page}
                pageSize={pageSize}
                total={total}
                onChange={(p) => setPage(p)}
                showTotal={(t) => `共 ${t} 张`}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default ImageManage;
