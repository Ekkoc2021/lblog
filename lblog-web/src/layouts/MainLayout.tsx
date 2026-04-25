import { useState, useEffect } from 'react';
import { Layout, Input, Button, Avatar, Typography } from 'antd';
import { EditOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';

const { Header, Content, Footer } = Layout;
const { Search } = Input;
const { Text } = Typography;

const navItems = [
  { key: '/', label: '首页' },
];

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [searchValue, setSearchValue] = useState('');

  useEffect(() => {
    if (location.pathname === '/search') {
      setSearchValue(searchParams.get('q') || '');
    }
  }, [location.pathname, searchParams]);

  const handleSearch = (value: string) => {
    if (value.trim()) {
      navigate(`/search?q=${encodeURIComponent(value.trim())}`);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#f4f5f5' }}>
      <Header style={{
        background: '#fff',
        borderBottom: '1px solid #e8e8e8',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        boxShadow: '0 1px 2px rgba(0,0,0,0.06)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', height: '100%' }}>
          <div
            style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', marginRight: 32 }}
            onClick={() => navigate('/')}
          >
            <Text strong style={{ fontSize: 20, color: '#1e80ff' }}>LBlog</Text>
          </div>
          {navItems.map(item => {
            const isActive = location.pathname === item.key;
            return (
              <div
                key={item.key}
                onClick={() => navigate(item.key)}
                style={{
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  padding: '0 4px',
                  margin: '0 12px',
                  cursor: 'pointer',
                  position: 'relative',
                  color: isActive ? '#1e80ff' : '#555',
                  fontSize: 15,
                  fontWeight: isActive ? 600 : 400,
                  transition: 'color 0.2s',
                }}
                onMouseEnter={e => { if (!isActive) e.currentTarget.style.color = '#1e80ff'; }}
                onMouseLeave={e => { if (!isActive) e.currentTarget.style.color = '#555'; }}
              >
                {item.label}
                {isActive && (
                  <div style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    height: 3,
                    background: '#1e80ff',
                    borderRadius: '3px 3px 0 0',
                  }} />
                )}
              </div>
            );
          })}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Search
            placeholder="搜索文章"
            allowClear
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onSearch={handleSearch}
            style={{ width: 200 }}
          />
          <Button type="primary" icon={<EditOutlined />}>写文章</Button>
          <Avatar icon={<UserOutlined />} style={{ cursor: 'pointer' }} />
        </div>
      </Header>
      <Content style={{ padding: '20px 0' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px' }}>
          {children}
        </div>
      </Content>
      <Footer style={{ textAlign: 'center', background: '#f4f5f5', color: '#999' }}>
        LBlog ©{new Date().getFullYear()} — Powered by React + Ant Design
      </Footer>
    </Layout>
  );
};

export default MainLayout;
