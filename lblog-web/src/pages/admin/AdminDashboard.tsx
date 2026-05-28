import { Card, Row, Col, Typography } from 'antd';
import { SettingOutlined, PictureOutlined, UserOutlined, FileTextOutlined, FolderOutlined, TagsOutlined, BookOutlined, MessageOutlined, RobotOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

const { Title, Paragraph } = Typography;

const features = [
  {
    key: 'configs',
    title: '配置管理',
    description: '管理站点配置项，如注册开关、站点标题等',
    icon: <SettingOutlined style={{ fontSize: 32, color: '#1e80ff' }} />,
    path: '/admin/configs',
  },
  {
    key: 'images',
    title: '图片管理',
    description: '管理上传的图片，清理未使用的图片',
    icon: <PictureOutlined style={{ fontSize: 32, color: '#52c41a' }} />,
    path: '/admin/images',
  },
  {
    key: 'users',
    title: '用户管理',
    description: '管理用户账号、角色分配、重置密码',
    icon: <UserOutlined style={{ fontSize: 32, color: '#722ed1' }} />,
    path: '/admin/users',
  },
  {
    key: 'posts',
    title: '文章管理',
    description: '全站文章管理，支持按作者筛选、批量操作',
    icon: <FileTextOutlined style={{ fontSize: 32, color: '#1e80ff' }} />,
    path: '/admin/posts',
  },
  {
    key: 'categories',
    title: '分类管理',
    description: '全站分类管理，查看所有作者创建的分类',
    icon: <FolderOutlined style={{ fontSize: 32, color: '#52c41a' }} />,
    path: '/admin/categories',
  },
  {
    key: 'tags',
    title: '标签管理',
    description: '全站标签管理，查看所有作者创建的标签',
    icon: <TagsOutlined style={{ fontSize: 32, color: '#fa8c16' }} />,
    path: '/admin/tags',
  },
  {
    key: 'series',
    title: '专栏管理',
    description: '全站专栏管理，查看所有作者创建的专栏',
    icon: <BookOutlined style={{ fontSize: 32, color: '#722ed1' }} />,
    path: '/admin/series',
  },
  {
    key: 'comments',
    title: '评论管理',
    description: '全站评论审核，通过、驳回或删除评论',
    icon: <MessageOutlined style={{ fontSize: 32, color: '#eb2f96' }} />,
    path: '/admin/comments',
  },
  {
    key: 'prompts',
    title: '提示词管理',
    description: '管理各 AI 模块的系统提示词，支持版本控制和审计',
    icon: <RobotOutlined style={{ fontSize: 32, color: '#13c2c2' }} />,
    path: '/admin/prompts',
  },
];

const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Title level={4} style={{ marginBottom: 24 }}>社区管理</Title>
      <Row gutter={[16, 16]}>
        {features.map((f) => (
          <Col xs={24} sm={12} md={8} xl={6} key={f.key}>
            <Card
              hoverable
              onClick={() => navigate(f.path)}
              styles={{ body: { padding: 24, height: 120 } }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, height: '100%' }}>
                {f.icon}
                <div>
                  <div style={{ fontSize: 16, fontWeight: 600 }}>{f.title}</div>
                  <Paragraph
                    type="secondary"
                    style={{ fontSize: 13, marginBottom: 0, lineHeight: '20px' }}
                    ellipsis={{ rows: 2 }}
                  >
                    {f.description}
                  </Paragraph>
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
};

export default AdminDashboard;
