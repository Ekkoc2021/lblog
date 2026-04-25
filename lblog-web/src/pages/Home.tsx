import { useState, useEffect } from 'react';
import { Card, Tabs, Row, Col } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { Post, Tag as TagType, Series } from '../types';
import { getPosts, getTags, getSeries } from '../services/api';
import ArticleList from '../components/ArticleList';
import Sidebar from '../components/Sidebar';

const Home: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('recommend');
  const [posts, setPosts] = useState<Post[]>([]);
  const [tags, setTags] = useState<TagType[]>([]);
  const [seriesList, setSeriesList] = useState<Series[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  useEffect(() => {
    getTags().then(res => setTags(res.data));
    getSeries().then(res => setSeriesList(res.data));
  }, []);

  const loadPosts = (pageNum: number, append: boolean) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    getPosts({ page: pageNum, pageSize, sort: activeTab as 'recommend' | 'newest' | 'hot' })
      .then(res => {
        setPosts(prev => append ? [...prev, ...res.data.list] : res.data.list);
        setTotal(res.data.total);
        setPage(pageNum);
      })
      .finally(() => {
        setLoading(false);
        setLoadingMore(false);
      });
  };

  useEffect(() => {
    loadPosts(1, false);
  }, [activeTab]);

  const tabItems = [
    { key: 'recommend', label: '推荐' },
    { key: 'newest', label: '最新' },
    { key: 'hot', label: '最热' },
  ];

  return (
    <Row gutter={20}>
      <Col xs={24} sm={24} md={17}>
        <Card style={{ borderRadius: 4 }} styles={{ body: { padding: 0 } }}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={tabItems}
            style={{ padding: '0 16px' }}
          />
          <ArticleList
            dataSource={posts}
            loading={loading}
            loadingMore={loadingMore}
            total={total}
            onLoadMore={() => loadPosts(page + 1, true)}
            onArticleClick={(post) => navigate(`/posts/${post.slug}`)}
          />
        </Card>
      </Col>
      <Col xs={0} sm={0} md={7}>
        <Sidebar
          posts={posts}
          tags={tags}
          seriesList={seriesList}
        />
      </Col>
    </Row>
  );
};

export default Home;
