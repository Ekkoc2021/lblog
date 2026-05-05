import { useState, useEffect } from 'react';
import { Card, Tabs, Row, Col, Typography, message } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import type { Post, Category } from '../types';
import { getPosts } from '../services/api';
import { useSiteData } from '../contexts/SiteDataContext';
import ArticleList from '../components/ArticleList';
import Sidebar from '../components/Sidebar';

const { Title } = Typography;

const CategoryPosts: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const [category, setCategory] = useState<Category | null>(null);
  const [activeTab, setActiveTab] = useState('recommend');
  const { tags, categories, seriesList, hotPosts } = useSiteData();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  useEffect(() => {
    const found = categories.find(c => c.slug === slug);
    setCategory(found || null);
  }, [slug, categories]);

  const loadPosts = (pageNum: number, append: boolean) => {
    if (!category) return;
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    getPosts({ page: pageNum, pageSize, sort: activeTab as 'recommend' | 'newest' | 'hot', categoryId: category.id })
      .then(res => {
        setPosts(prev => append ? [...prev, ...res.data.list] : res.data.list);
        setTotal(res.data.total);
        setPage(pageNum);
      })
      .catch((e: Error) => message.error(e.message))
      .finally(() => {
        setLoading(false);
        setLoadingMore(false);
      });
  };

  useEffect(() => {
    if (category) {
      loadPosts(1, false);
    }
  }, [activeTab, category?.id]);

  const tabItems = [
    { key: 'recommend', label: '推荐' },
    { key: 'newest', label: '最新' },
    { key: 'hot', label: '最热' },
  ];

  return (
    <Row gutter={20}>
      <Col xs={24} sm={24} md={17}>
        <Card style={{ borderRadius: 4 }} styles={{ body: { padding: 0 } }}>
          <div style={{ padding: '16px 16px 0' }}>
            <Title level={4} style={{ margin: 0 }}>{category?.name || slug}</Title>
          </div>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={tabItems}
            style={{ padding: '0 16px' }}
          />
          <ArticleList
            dataSource={posts}
            loading={loading && !loadingMore}
            loadingMore={loadingMore}
            total={total}
            onLoadMore={() => loadPosts(page + 1, true)}
            onArticleClick={(post) => navigate(`/posts/${post.slug}`)}
          />
        </Card>
      </Col>
      <Col xs={0} sm={0} md={7}>
        <Sidebar
          hotPosts={hotPosts}
          posts={posts}
          tags={tags}
          categories={categories}
          seriesList={seriesList}
          onCategoryClick={(cat) => navigate(`/category/${cat.slug}`)}
          onTagClick={(tag) => navigate(`/tag/${tag.slug}`)}
          onSeriesClick={(s) => navigate(`/series/${s.slug}`)}
          onPostClick={(post) => navigate(`/posts/${post.slug}`)}
        />
      </Col>
    </Row>
  );
};

export default CategoryPosts;
