import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { SiteDataProvider } from './contexts/SiteDataContext';
import MainLayout from './layouts/MainLayout';
import Home from './pages/Home';
import CategoryPosts from './pages/CategoryPosts';
import TagPosts from './pages/TagPosts';
import SeriesPosts from './pages/SeriesPosts';
import SearchResult from './pages/SearchResult';
import PostDetail from './pages/PostDetail';
import Editor from './pages/Editor';

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
            <Route path="/editor" element={<Editor />} />
            <Route path="/editor/:id" element={<Editor />} />
          </Routes>
        </MainLayout>
      </AuthProvider>
      </SiteDataProvider>
    </BrowserRouter>
  );
}

export default App;
