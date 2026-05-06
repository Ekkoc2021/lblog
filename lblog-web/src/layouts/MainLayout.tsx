import { useState, useEffect, useRef } from 'react';
import { Layout, Input, Typography, Avatar, Popover, Button, Divider, message, Tag } from 'antd';
import { UserOutlined, LogoutOutlined, FileTextOutlined } from '@ant-design/icons';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useSearchHistory } from '../hooks/useSearchHistory';
import { useAuth } from '../contexts/AuthContext';
import LoginModal from '../components/LoginModal';
import UserSettingsDrawer from '../components/UserSettingsDrawer';

const { Header, Content, Footer } = Layout;
const { Search } = Input;
const { Text } = Typography;

const adminNavItem = { key: '/admin', label: '博客管理' };

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { history, addToHistory, removeFromHistory, clearHistory } = useSearchHistory();
  const { isAuthenticated, logout, user } = useAuth();
  const [searchValue, setSearchValue] = useState('');
  const [loginModalVisible, setLoginModalVisible] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [settingsVisible, setSettingsVisible] = useState(false);
  const blurTimerRef = useRef<number | null>(null);
  const afterLoginRef = useRef<string | null>(null);

  useEffect(() => {
    if (location.pathname === '/search') {
      setSearchValue(searchParams.get('q') || '');
    }
  }, [location.pathname, searchParams]);

  const handleSearch = (value: string) => {
    if (value.trim()) {
      addToHistory(value.trim());
      navigate(`/search?q=${encodeURIComponent(value.trim())}`);
    }
  };

  const handleSearchFocus = () => {
    if (blurTimerRef.current) {
      clearTimeout(blurTimerRef.current);
      blurTimerRef.current = null;
    }
    setShowHistory(true);
  };

  const handleSearchBlur = () => {
    blurTimerRef.current = window.setTimeout(() => setShowHistory(false), 150);
  };

  const handleNavClick = (key: string) => {
    navigate(key);
  };

  const handleLogout = () => {
    logout();
    message.success('已退出登录');
    navigate('/');
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
          {[{ key: '/', label: '首页' }, ...(user?.role === 'admin' ? [adminNavItem] : [])].map(item => {
            const isActive = item.key === '/' ? location.pathname === '/' : location.pathname.startsWith(item.key);
            return (
              <div
                key={item.key}
                onClick={() => handleNavClick(item.key)}
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
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginRight: 8 }}>
          <div style={{ position: 'relative', width: 'clamp(160px, 25vw, 320px)' }}>
            <Search
              placeholder="搜索文章"
              allowClear
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onSearch={handleSearch}
              onFocus={handleSearchFocus}
              onBlur={handleSearchBlur}
              style={{ width: '100%' }}
            />
            {showHistory && history.length > 0 && (
              <div style={{
                position: 'absolute',
                top: '100%',
                left: 0,
                right: 0,
                background: '#fff',
                border: '1px solid #e8e8e8',
                borderTop: 'none',
                borderRadius: '0 0 6px 6px',
                boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                zIndex: 200,
                maxHeight: 320,
                overflow: 'auto',
              }}>
                {history.map((keyword) => (
                  <div
                    key={keyword}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      setSearchValue(keyword);
                      navigate(`/search?q=${encodeURIComponent(keyword)}`);
                    }}
                    style={{
                      padding: '2px 16px',
                      lineHeight: 1.6,
                      cursor: 'pointer',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      fontSize: 13,
                      color: '#333',
                      transition: 'background 0.15s',
                    }}
                    onMouseEnter={e => { e.currentTarget.style.background = '#f5f5f5'; }}
                    onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
                  >
                    <span>{keyword}</span>
                    <span
                      onMouseDown={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        removeFromHistory(keyword);
                      }}
                      style={{
                        color: '#ccc',
                        fontSize: 14,
                        padding: '0 4px',
                        lineHeight: 1,
                        cursor: 'pointer',
                      }}
                      onMouseEnter={e => { e.currentTarget.style.color = '#999'; }}
                      onMouseLeave={e => { e.currentTarget.style.color = '#ccc'; }}
                    >×</span>
                  </div>
                ))}
                <div
                  onMouseDown={(e) => {
                    e.preventDefault();
                    clearHistory();
                    setShowHistory(false);
                  }}
                  style={{
                    padding: '3px 16px',
                    textAlign: 'center',
                    fontSize: 12,
                    color: '#999',
                    cursor: 'pointer',
                    borderTop: '1px solid #f0f0f0',
                    transition: 'color 0.15s',
                  }}
                  onMouseEnter={e => { e.currentTarget.style.color = '#1e80ff'; }}
                  onMouseLeave={e => { e.currentTarget.style.color = '#999'; }}
                >
                  清空搜索历史
                </div>
              </div>
            )}
          </div>
          {isAuthenticated ? (
            <Popover
              trigger="hover"
              placement="bottomRight"
              arrow={false}
              content={
                <div style={{ width: 220, padding: 4 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', gap: 12 }}>
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                        <Avatar icon={<UserOutlined />} style={{ background: '#1e80ff' }} />
                        {user?.role && <Tag color={user.role === 'admin' ? 'red' : user.role === 'author' ? 'blue' : 'default'} style={{ fontSize: 10, lineHeight: '16px', padding: '0 5px', margin: 0 }}>{user.role === 'admin' ? '管理员' : user.role === 'author' ? '作者' : '用户'}</Tag>}
                      </div>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 14 }}>{user?.nickname ?? '用户'}</div>
                        <div style={{ color: '#999', fontSize: 12 }}>{user?.username ?? '-'}</div>
                        <div style={{ color: '#bbb', fontSize: 11, marginTop: 2 }}>{user?.email ?? '-'}</div>
                      </div>
                    </div>
                    <Button type="text" size="small" style={{ color: '#999', marginTop: 2 }} onClick={() => setSettingsVisible(true)}>设置</Button>
                  </div>
                  <Divider style={{ margin: '10px 0' }} />
                  <div style={{ display: 'flex', gap: 8 }}>
                    <Button
                      type="text"
                      icon={<FileTextOutlined />}
                      style={{ flex: 1, paddingLeft: 8 }}
                      onClick={() => {
                        setLoginModalVisible(false);
                        if (user?.role === 'user') {
                          message.info('申请成为作者后才能使用创作中心');
                          return;
                        }
                        navigate('/author/posts');
                      }}
                    >
                      创作中心
                    </Button>
                    <Button
                      type="text"
                      icon={<LogoutOutlined />}
                      danger
                      style={{ flex: 1, paddingLeft: 8 }}
                      onClick={handleLogout}
                    >
                      退出登录
                    </Button>
                  </div>
                </div>
              }
            >
              <span style={{ display: 'inline-block' }}>
                <Avatar
                  icon={<UserOutlined />}
                  style={{ background: '#1e80ff', cursor: 'pointer' }}
                />
              </span>
            </Popover>
          ) : (
            <Button type="text" icon={<UserOutlined />} onClick={() => setLoginModalVisible(true)} style={{ color: '#555' }}>
              登录
            </Button>
          )}
        </div>
      </Header>
      <Content style={{ padding: location.pathname.startsWith('/author') ? 0 : '20px 0' }}>
        {location.pathname.startsWith('/author') ? (
          children
        ) : (
          <div style={{ maxWidth: 1600, margin: '0 auto', padding: '0 24px' }}>
            {children}
          </div>
        )}
      </Content>
      <Footer style={{ textAlign: 'center', background: '#f4f5f5', color: '#999' }}>
        LBlog ©{new Date().getFullYear()} — Powered by React + Ant Design
      </Footer>
        <UserSettingsDrawer
          open={settingsVisible}
          onClose={() => setSettingsVisible(false)}
        />
        <LoginModal
          open={loginModalVisible}
          onClose={() => setLoginModalVisible(false)}
          onSuccess={() => {
            const target = afterLoginRef.current;
            afterLoginRef.current = null;
            if (target) navigate(target);
          }}
        />
    </Layout>
  );
};

export default MainLayout;
