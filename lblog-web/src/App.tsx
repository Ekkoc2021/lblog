import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import Home from './pages/Home';
import SearchResult from './pages/SearchResult';
import PostDetail from './pages/PostDetail';

function App() {
  return (
    <BrowserRouter>
      <MainLayout>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/search" element={<SearchResult />} />
          <Route path="/posts/:slug" element={<PostDetail />} />
        </Routes>
      </MainLayout>
    </BrowserRouter>
  );
}

export default App;
