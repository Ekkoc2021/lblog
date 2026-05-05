import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { SiteDataProvider } from './contexts/SiteDataContext';
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

function App() {
  return (
    <BrowserRouter>
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
            <Route path="/admin" element={<div style={{ padding: 48, textAlign: 'center', color: '#999' }}>博客管理 · 开发中</div>} />
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
    </BrowserRouter>
  );
}

export default App;
