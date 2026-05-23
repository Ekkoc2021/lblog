import { useState, useEffect, useRef } from 'react';
import { Layout, Input, Typography, Avatar, Popover, Button, Divider, message, Tag, Drawer } from 'antd';
import { UserOutlined, LogoutOutlined, FileTextOutlined, MenuOutlined, BulbOutlined, BulbFilled, BookOutlined } from '@ant-design/icons';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useSearchHistory } from '../hooks/useSearchHistory';
import { useAuth } from '../contexts/AuthContext';
import { useTheme } from '../contexts/ThemeContext';
import LoginModal from '../components/LoginModal';
import UserSettingsDrawer from '../components/UserSettingsDrawer';

const { Header, Content, Footer } = Layout;
const { Search } = Input;
const { Text } = Typography;

const adminNavItem = { key: '/admin', label: '社区管理' };

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { history, addToHistory, removeFromHistory, clearHistory } = useSearchHistory();
  const { isAuthenticated, logout, user } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';
  const isWarm = theme === 'warm';
  const [searchValue, setSearchValue] = useState('');
  const [loginModalVisible, setLoginModalVisible] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [settingsVisible, setSettingsVisible] = useState(false);
  const blurTimerRef = useRef<number | null>(null);
  const afterLoginRef = useRef<string | null>(null);
  const [isMobile, setIsMobile] = useState(window.innerWidth <= 768);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)');
    const handler = (e: MediaQueryListEvent | MediaQueryList) => setIsMobile(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

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

  const isPostDetail = location.pathname.startsWith('/posts/');
  const [readProgress, setReadProgress] = useState(0);

  useEffect(() => {
    if (!isPostDetail) return;
    const handleScroll = () => {
      const h = document.documentElement.scrollHeight - window.innerHeight;
      if (h <= 0) return;
      setReadProgress(Math.min((window.scrollY / h) * 100, 100));
    };
    handleScroll();
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [isPostDetail]);

  return (
    <>
      {isPostDetail && (
        <div style={{ position: 'fixed', top: 0, left: 0, height: 3, background: 'var(--color-primary)', zIndex: 9999, width: `${readProgress}%`, boxShadow: '0 0 8px var(--color-primary)', transition: 'width 0.15s linear' }} />
      )}
    <Layout style={{ minHeight: '100vh', background: 'var(--color-bg)' }}>
      <Header style={{
        background: 'var(--glass-header-bg)',
        backdropFilter: 'saturate(180%) blur(20px)',
        WebkitBackdropFilter: 'saturate(180%) blur(20px)',
        borderBottom: '1px solid var(--glass-header-border)',
        padding: isMobile ? '0 16px' : '0 32px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        position: 'sticky',
        top: 0,
        zIndex: 100,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', height: '100%' }}>
          {isMobile && (
            <Button
              type="text"
              icon={<MenuOutlined style={{ fontSize: 18, color: 'var(--color-text)' }} />}
              onClick={() => setDrawerOpen(true)}
              style={{ marginRight: 8 }}
            />
          )}
          {!isMobile && (
            <div
              style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', marginRight: 40 }}
              onClick={() => navigate('/')}
            >
              <Text strong style={{ fontSize: 22, color: 'var(--color-text)', letterSpacing: '-0.02em' }}>LBlog</Text>
            </div>
          )}
          {!isMobile && [{ key: '/', label: '首页' }, ...(user?.role === 'admin' ? [adminNavItem] : [])].map(item => {
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
                  margin: '0 16px',
                  cursor: 'pointer',
                  position: 'relative',
                  color: isActive ? 'var(--color-text)' : 'var(--color-text-secondary)',
                  fontSize: 15,
                  fontWeight: isActive ? 600 : 400,
                  whiteSpace: 'nowrap',
                  transition: 'color 0.2s ease',
                }}
                onMouseEnter={e => { if (!isActive) e.currentTarget.style.color = 'var(--color-text)'; }}
                onMouseLeave={e => { if (!isActive) e.currentTarget.style.color = 'var(--color-text-secondary)'; }}
              >
                {item.label}
                {isActive && (
                  <div style={{
                    position: 'absolute',
                    bottom: 0,
                    left: '25%',
                    right: '25%',
                    height: 2,
                    background: 'var(--color-text)',
                    borderRadius: 1,
                  }} />
                )}
              </div>
            );
          })}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Button
            type="text"
            icon={isDark ? <BulbFilled /> : isWarm ? <BookOutlined /> : <BulbOutlined />}
            onClick={toggleTheme}
            style={{ color: 'var(--color-text-secondary)' }}
            title={isDark ? '暗色模式' : isWarm ? '书页模式' : '亮色模式'}
          />
          <div style={{ position: 'relative', width: 'clamp(160px, 25vw, 300px)' }}>
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
                marginTop: 4,
                background: 'var(--color-bg-card)',
                border: '1px solid var(--color-border)',
                borderRadius: 12,
                boxShadow: 'var(--shadow-dropdown)',
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
                      padding: '10px 20px',
                      cursor: 'pointer',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      fontSize: 14,
                      color: 'var(--color-text)',
                      transition: 'background 0.15s',
                    }}
                    onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-bg-hover)'; }}
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
                        color: 'var(--color-text-tertiary)',
                        fontSize: 16,
                        lineHeight: 1,
                        cursor: 'pointer',
                      }}
                      onMouseEnter={e => { e.currentTarget.style.color = 'var(--color-text-secondary)'; }}
                      onMouseLeave={e => { e.currentTarget.style.color = 'var(--color-text-tertiary)'; }}
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
                    padding: '10px 20px',
                    textAlign: 'center',
                    fontSize: 13,
                    color: 'var(--color-text-tertiary)',
                    cursor: 'pointer',
                    borderTop: '1px solid var(--color-border)',
                    transition: 'color 0.15s',
                  }}
                  onMouseEnter={e => { e.currentTarget.style.color = 'var(--color-primary)'; }}
                  onMouseLeave={e => { e.currentTarget.style.color = 'var(--color-text-tertiary)'; }}
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
                <div style={{ width: 240, padding: 4 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', gap: 12 }}>
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                        <Avatar src={user?.avatar || undefined} icon={user?.avatar ? undefined : <UserOutlined />} style={{ background: user?.avatar ? undefined : 'var(--color-primary)' }} size={44}>{!user?.avatar && (user?.nickname?.[0] || 'U')}</Avatar>
                        {user?.role && <Tag color={user.role === 'admin' ? 'red' : user.role === 'author' ? 'blue' : 'default'} style={{ fontSize: 10, lineHeight: '16px', padding: '0 6px', margin: 0, borderRadius: 8 }}>{user.role === 'admin' ? '管理员' : user.role === 'author' ? '作者' : '用户'}</Tag>}
                      </div>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--color-text)' }}>{user?.nickname ?? '用户'}</div>
                        <div style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>{user?.username ?? '-'}</div>
                        <div style={{ color: 'var(--color-text-tertiary)', fontSize: 11, marginTop: 2 }}>{user?.email ?? '-'}</div>
                      </div>
                    </div>
                    <Button type="text" size="small" style={{ color: 'var(--color-text-secondary)', marginTop: 2 }} onClick={() => setSettingsVisible(true)}>设置</Button>
                  </div>
                  <Divider style={{ margin: '12px 0' }} />
                  <div style={{ display: 'flex', gap: 8 }}>
                    {!isMobile && (
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
                    )}
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
                  src={user?.avatar || undefined}
                  icon={user?.avatar ? undefined : <UserOutlined />}
                  size={36}
                  style={{ background: user?.avatar ? undefined : 'var(--color-primary)', cursor: 'pointer' }}
                >{!user?.avatar && (user?.nickname?.[0] || 'U')}</Avatar>
              </span>
            </Popover>
          ) : (
            <Button type="text" icon={<UserOutlined />} onClick={() => setLoginModalVisible(true)} style={{ color: 'var(--color-text-secondary)', fontWeight: 500 }}>
              登录
            </Button>
          )}
        </div>
      </Header>
      <Content style={{ padding: location.pathname.startsWith('/author') ? 0 : '32px 0' }}>
        {location.pathname.startsWith('/author') ? (
          children
        ) : (
          <div style={{ maxWidth: 1600, margin: '0 auto', padding: '0 24px' }}>
            {children}
          </div>
        )}
      </Content>
      <Footer style={{ textAlign: 'center', background: 'var(--color-bg)', color: 'var(--color-text-tertiary)', fontSize: 13 }}>
        LBlog &copy;{new Date().getFullYear()} Powered by React + Ant Design
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
        <Drawer
          title={null}
          placement="left"
          closable={false}
          onClose={() => setDrawerOpen(false)}
          open={drawerOpen}
          width={200}
          styles={{ body: { padding: '16px 0' } }}
        >
          {[{ key: '/', label: '首页' }, ...(user?.role === 'admin' ? [adminNavItem] : [])].map(item => {
            const isActive = item.key === '/' ? location.pathname === '/' : location.pathname.startsWith(item.key);
            return (
              <div
                key={item.key}
                onClick={() => {
                  handleNavClick(item.key);
                  setDrawerOpen(false);
                }}
                style={{
                  padding: '14px 24px',
                  cursor: 'pointer',
                  fontSize: 16,
                  color: isActive ? 'var(--color-text)' : 'var(--color-text-secondary)',
                  fontWeight: isActive ? 600 : 400,
                  background: isActive ? 'var(--color-bg)' : 'transparent',
                  borderRight: isActive ? '3px solid var(--color-primary)' : '3px solid transparent',
                  transition: 'all 0.2s ease',
                }}
              >
                {item.label}
              </div>
            );
          })}
        </Drawer>
    </Layout>
    </>
  );
};

export default MainLayout;
