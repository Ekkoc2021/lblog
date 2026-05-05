import { useState, useEffect } from 'react';
import { Layout, Menu, Button, Spin } from 'antd';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import {
  FileTextOutlined,
  FolderOutlined,
  TagsOutlined,
  BookOutlined,
  BarChartOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import LoginModal from '../components/LoginModal';

const { Sider, Content } = Layout;

const menuItems = [
  { key: '/author/posts', icon: <FileTextOutlined />, label: '文章管理' },
  { key: '/author/categories', icon: <FolderOutlined />, label: '分类管理' },
  { key: '/author/tags', icon: <TagsOutlined />, label: '标签管理' },
  { key: '/author/series', icon: <BookOutlined />, label: '专栏管理' },
  { key: '/author/statistics', icon: <BarChartOutlined />, label: '站点统计' },
];

const AdminLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [loginVisible, setLoginVisible] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      setLoginVisible(true);
    }
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    return (
      <>
        {!loginVisible && (
          <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>
        )}
        <LoginModal
          open={loginVisible}
          onClose={() => { setLoginVisible(false); navigate('/'); }}
          onSuccess={() => setLoginVisible(false)}
        />
      </>
    );
  }

  const selectedKey = '/' + location.pathname.split('/').slice(1, 3).join('/');

  return (
    <Layout style={{ minHeight: 'calc(100vh - 64px)', background: '#f0f2f5' }}>
      <Sider
        theme="light"
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={200}
        style={{ borderRight: '1px solid #e8e8e8' }}
      >
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 'none', marginTop: 8 }}
        />
        <div style={{ position: 'absolute', bottom: 16, left: 0, right: 0, textAlign: 'center' }}>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/')}
            size="small"
            style={{ color: '#999' }}
          >
            {collapsed ? '' : '返回前台'}
          </Button>
        </div>
      </Sider>
      <Layout style={{ background: '#f0f2f5' }}>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default AdminLayout;
