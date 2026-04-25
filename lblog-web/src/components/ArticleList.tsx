import { List, Button, Spin } from 'antd';
import ArticleCard from './ArticleCard';
import type { Post } from '../types';

interface ArticleListProps {
  dataSource: Post[];
  loading: boolean;
  loadingMore: boolean;
  total: number;
  keyword?: string;
  onLoadMore: () => void;
  onArticleClick?: (post: Post) => void;
}

const ArticleList: React.FC<ArticleListProps> = ({
  dataSource, loading, loadingMore, total, keyword, onLoadMore, onArticleClick,
}) => (
  <>
    <Spin spinning={loading}>
      <List
        dataSource={dataSource}
        renderItem={(post: Post) => (
          <ArticleCard
            post={post}
            keyword={keyword}
            onClick={onArticleClick ? () => onArticleClick(post) : undefined}
          />
        )}
        split={false}
        style={{ padding: '0 8px 8px' }}
      />
    </Spin>
    {!loading && dataSource.length < total && (
      <div style={{ textAlign: 'center', marginTop: 16 }}>
        <Button type="link" loading={loadingMore} onClick={onLoadMore}>加载更多</Button>
      </div>
    )}
    {!loading && dataSource.length > 0 && dataSource.length >= total && (
      <div style={{ textAlign: 'center', marginTop: 16, color: '#8a919f', fontSize: 13 }}>
        已展示全部
      </div>
    )}
  </>
);

export default ArticleList;
