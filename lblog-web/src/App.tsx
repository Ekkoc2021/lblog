import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, theme } from 'antd';
import { AuthProvider } from './contexts/AuthContext';
import { SiteDataProvider } from './contexts/SiteDataContext';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
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
              <Route path="statistics" element={<Statistics />} />
            </Route>
          </Routes>
        </MainLayout>
      </AuthProvider>
      </SiteDataProvider>
    </ConfigProvider>
  );
};

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
