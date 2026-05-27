import { useState, useEffect, useRef } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, theme } from 'antd';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { SiteDataProvider } from './contexts/SiteDataContext';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import { DiagramProvider, useDiagram } from './contexts/diagram-context';
import DrawFloatingButton from './components/DrawFloatingButton';
import TodoPanel from './components/TodoPanel';
import DrawPage from './pages/DrawPage';
import MainLayout from './layouts/MainLayout';
import AdminLayout from './layouts/AdminLayout';
import Home from './pages/Home';
import CategoryPosts from './pages/CategoryPosts';
import TagPosts from './pages/TagPosts';
import SeriesPosts from './pages/SeriesPosts';
import SearchResult from './pages/SearchResult';
import PostDetail from './pages/PostDetail';
import PostList from './pages/author/PostList';
import PostEditor from './pages/author/PostEditor';
import CategoryManage from './pages/author/CategoryManage';
import TagManage from './pages/author/TagManage';
import SeriesManage from './pages/author/SeriesManage';
import SeriesPostsManage from './pages/author/SeriesPostsManage';
import Statistics from './pages/author/Statistics';
import AdminDashboard from './pages/admin/AdminDashboard';
import PostManage from './pages/admin/PostManage';
import AdminCategoryManage from './pages/admin/CategoryManage';
import AdminTagManage from './pages/admin/TagManage';
import AdminSeriesManage from './pages/admin/SeriesManage';
import ConfigManage from './pages/author/ConfigManage';
import ImageManage from './pages/admin/ImageManage';
import UserManage from './pages/admin/UserManage';
import CommentManage from './pages/admin/CommentManage';

const AppContent: React.FC = () => {
  const { theme: currentTheme } = useTheme();
  const [showDrawPage, setShowDrawPage] = useState(false);
  const [showTodoPanel, setShowTodoPanel] = useState(false);
  const [toolboxPos, setToolboxPos] = useState({ left: 0, top: 0 });

  const themeConfig = {
    algorithm: currentTheme === 'dark' ? theme.darkAlgorithm : undefined,
    token: currentTheme === 'warm' ? {
      colorBgContainer: '#faf5e8',
      colorBgLayout: '#f4ecd8',
      colorBgElevated: '#faf5e8',
      colorFillAlter: '#efe4cc',
      colorBorder: '#d9ccb4',
      colorText: '#3d3025',
      colorTextSecondary: '#8b7355',
      colorPrimary: '#8b6914',
      colorPrimaryBg: '#efe0c0',
    } : undefined,
  };

return (
    <ConfigProvider theme={themeConfig}>
      <SiteDataProvider>
      <AuthProvider>
        <DiagramProvider>
          {/* 登出时清空画布，防止下一用户看到残留数据 */}
          <DiagramAuthGuard />

          {/* 博客内容 */}
          <MainLayout>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/category/:slug" element={<CategoryPosts />} />
              <Route path="/tag/:slug" element={<TagPosts />} />
              <Route path="/series/:slug" element={<SeriesPosts />} />
              <Route path="/search" element={<SearchResult />} />
              <Route path="/posts/:slug" element={<PostDetail />} />
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/admin/posts" element={<PostManage />} />
              <Route path="/admin/categories" element={<AdminCategoryManage />} />
              <Route path="/admin/tags" element={<AdminTagManage />} />
              <Route path="/admin/series" element={<AdminSeriesManage />} />
              <Route path="/admin/configs" element={<ConfigManage />} />
              <Route path="/admin/images" element={<ImageManage />} />
              <Route path="/admin/users" element={<UserManage />} />
              <Route path="/admin/comments" element={<CommentManage />} />
              <Route path="/author" element={<AdminLayout />}>
                <Route index element={<PostList />} />
                <Route path="posts" element={<PostList />} />
                <Route path="posts/new" element={<PostEditor />} />
                <Route path="posts/:id/edit" element={<PostEditor />} />
                <Route path="categories" element={<CategoryManage />} />
                <Route path="tags" element={<TagManage />} />
                <Route path="series" element={<SeriesManage />} />
                <Route path="series/:seriesId/posts" element={<SeriesPostsManage />} />
                <Route path="statistics" element={<Statistics />} />
              </Route>
            </Routes>
          </MainLayout>

          {/* 写作工具箱：仅登录用户可见 */}
          <ToolboxButton showDrawPage={showDrawPage} onOpenDraw={() => setShowDrawPage(true)} onOpenTodo={() => setShowTodoPanel(v => !v)} onPositionChange={setToolboxPos} />

          {/* AI 绘图面板 —— 从按钮位置缩放展开 */}
          <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100vw',
            height: '100vh',
            zIndex: 999,
            transformOrigin: `${toolboxPos.left + 18}px ${toolboxPos.top + 18}px`,
            transform: showDrawPage ? 'scale(1)' : 'scale(0)',
            opacity: showDrawPage ? 1 : 0,
            transition: 'transform 0.25s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease',
            pointerEvents: showDrawPage ? 'auto' : 'none',
            borderRadius: showDrawPage ? 0 : '50%',
          }}>
            <DrawPage onClose={() => setShowDrawPage(false)} />
          </div>

          {/* Todo 面板 */}
          {showTodoPanel && <TodoPanel onClose={() => setShowTodoPanel(false)} />}
        </DiagramProvider>
      </AuthProvider>
      </SiteDataProvider>
    </ConfigProvider>
  );
};

/** 登出时自动清空画布，防止下一用户看到残留数据 */
function DiagramAuthGuard() {
  const { isAuthenticated, user } = useAuth();
  const { clearDiagram } = useDiagram();
  const prevUserId = useRef<number | undefined>(undefined);

  useEffect(() => {
    const uid = user?.id;
    if (prevUserId.current !== undefined && uid !== prevUserId.current) {
      clearDiagram();
    }
    prevUserId.current = uid;
  }, [user, clearDiagram]);

  // 登出时也清空
  useEffect(() => {
    if (!isAuthenticated) {
      clearDiagram();
    }
  }, [isAuthenticated, clearDiagram]);

  return null;
}

/** 工具箱包装器：仅在登录且角色为 admin/author 时显示 */
function ToolboxButton({ showDrawPage, onOpenDraw, onOpenTodo, onPositionChange }: {
  showDrawPage: boolean; onOpenDraw: () => void; onOpenTodo: () => void; onPositionChange: (pos: { left: number; top: number }) => void
}) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated || !user) return null;
  if (user.role !== 'admin' && user.role !== 'author') return null;
  return <DrawFloatingButton onOpenDraw={onOpenDraw} onOpenTodo={onOpenTodo} onPositionChange={onPositionChange} hidden={showDrawPage} />;
}

function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <AppContent />
      </ThemeProvider>
    </BrowserRouter>
  );
}

export default App;
