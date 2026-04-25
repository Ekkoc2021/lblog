import { useState, useEffect } from 'react';
import { Card, Row, Col, Spin, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useSearchParams, useNavigate } from 'react-router-dom';
import type { Post, Tag as TagType, Series } from '../types';
import { getPosts, getTags, getSeries } from '../services/api';
import ArticleList from '../components/ArticleList';
import Sidebar from '../components/Sidebar';
import EmptyState from '../components/EmptyState';

const { Text } = Typography;

function truncate(text: string, maxLen: number): string {
  if (text.length <= maxLen) return text;
  return text.slice(0, maxLen) + '...';
}

const SearchResult: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const keyword = searchParams.get('q') || '';

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
    if (!keyword.trim()) return;
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    getPosts({ page: pageNum, pageSize, keyword, sort: 'newest' })
      .then(res => {
        const list = res.data.list.map(p => ({
          ...p,
          excerpt: truncate(p.excerpt, 200),
        }));
        setPosts(prev => append ? [...prev, ...list] : list);
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
  }, [keyword]);

  const renderContent = () => {
    if (!keyword.trim()) {
      return (
        <Card style={{ borderRadius: 4 }}>
          <EmptyState
            icon={<SearchOutlined style={{ fontSize: 64, color: '#d9d9d9' }} />}
            description="请输入关键词搜索文章"
          />
        </Card>
      );
    }

    if (loading) {
      return (
        <div style={{ textAlign: 'center', padding: '80px 0' }}>
          <Spin size="large" />
        </div>
      );
    }

    if (!loading && posts.length === 0) {
      return (
        <Card style={{ borderRadius: 4 }}>
          <EmptyState
            icon={<SearchOutlined style={{ fontSize: 64, color: '#d9d9d9' }} />}
            description={
              <span>
                未找到与 "<Text strong>{keyword}</Text>" 相关的文章
              </span>
            }
            actionText="返回首页"
            onAction={() => navigate('/')}
          />
        </Card>
      );
    }

    return (
      <>
        <Card style={{ borderRadius: 4, marginBottom: 16 }}>
          <Text style={{ fontSize: 14, color: '#8a919f' }}>
            搜索 "<Text strong style={{ color: '#333' }}>{keyword}</Text>"
            ，共找到 <Text strong style={{ color: '#1e80ff' }}>{total}</Text> 篇文章
          </Text>
        </Card>
        <ArticleList
          dataSource={posts}
          loading={false}
          loadingMore={loadingMore}
          total={total}
          keyword={keyword}
          onLoadMore={() => loadPosts(page + 1, true)}
          onArticleClick={(post) => navigate(`/posts/${post.slug}`)}
        />
      </>
    );
  };

  return (
    <Row gutter={20}>
      <Col xs={24} sm={24} md={17}>
        {renderContent()}
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

export default SearchResult;
