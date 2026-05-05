import { Card, Row, Col, Statistic, Typography } from 'antd';
import { FileTextOutlined, EyeOutlined, LikeOutlined, MessageOutlined } from '@ant-design/icons';

const { Title } = Typography;

const Statistics: React.FC = () => {
  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>站点统计</Title>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="文章总数" value={23} prefix={<FileTextOutlined style={{ color: '#1e80ff' }} />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="总浏览量" value={12856} prefix={<EyeOutlined style={{ color: '#52c41a' }} />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="总点赞数" value={892} prefix={<LikeOutlined style={{ color: '#ff4d4f' }} />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="总评论数" value={156} prefix={<MessageOutlined style={{ color: '#faad14' }} />} />
          </Card>
        </Col>
      </Row>
      <Card style={{ marginTop: 16 }}>
        <div style={{ color: '#999', textAlign: 'center', padding: '40px 0' }}>
          图表区域（待实现 — 发布趋势 / 分类分布 / 标签分布）
        </div>
      </Card>
    </div>
  );
};

export default Statistics;
