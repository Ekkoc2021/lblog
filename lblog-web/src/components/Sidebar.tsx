import { Card, Tag, Space, Typography } from 'antd';
import { FireOutlined, TagsOutlined, BookOutlined, AppstoreOutlined } from '@ant-design/icons';
import type { Post, Tag as TagType, Series, Category } from '../types';

const { Text } = Typography;

interface SidebarProps {
  hotPosts?: Post[];
  posts: Post[];
  tags: TagType[];
  categories: Category[];
  seriesList: Series[];
  onCategoryClick?: (category: Category) => void;
  onTagClick?: (tag: TagType) => void;
  onSeriesClick?: (series: Series) => void;
  onPostClick?: (post: Post) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ hotPosts, posts, tags, categories, seriesList, onCategoryClick, onTagClick, onSeriesClick, onPostClick }) => {
  const hotList = hotPosts || [...posts].sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0)).slice(0, 5);
  return (<>
    <Card
      title={
        <Space>
          <FireOutlined style={{ color: '#ff3b30' }} />
          <span style={{ fontWeight: 600 }}>热门文章</span>
        </Space>
      }
      className="sidebar-card"
      style={{ borderRadius: 16, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }}
      styles={{ body: { padding: '4px 16px 12px' } }}
    >
      {hotList.map((post, index) => (
        <div
          key={post.id}
          style={{
            padding: '10px 8px',
            borderBottom: index < 4 ? '1px solid var(--color-border)' : 'none',
            borderRadius: 8,
            transition: 'background 0.2s ease',
            cursor: 'pointer',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-bg-hover)'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          onClick={onPostClick ? () => onPostClick(post) : undefined}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
            <Text strong style={{ fontSize: 15, color: index < 3 ? 'var(--color-primary)' : 'var(--color-text-tertiary)', minWidth: 22, letterSpacing: '-0.02em' }}>
              {index + 1}.
            </Text>
            <Text style={{ fontSize: 14, flex: 1, color: 'var(--color-text)', lineHeight: 1.5 }} ellipsis>{post.title}</Text>
          </div>
        </div>
      ))}
    </Card>
    <Card
      title={
        <Space>
          <AppstoreOutlined style={{ color: '#34c759' }} />
          <span style={{ fontWeight: 600 }}>热门分类</span>
        </Space>
      }
      className="sidebar-card"
      style={{ borderRadius: 16, marginTop: 20, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }}
      styles={{ body: { padding: '12px 16px 16px' } }}
    >
      <Space wrap size={[8, 8]}>
        {categories.map((cat: Category) => (
          <Tag
            key={cat.id}
            style={{ cursor: 'pointer', borderRadius: 12, padding: '3px 14px', background: 'var(--color-bg-tag)', color: 'var(--color-text)', border: 'none', fontSize: 13, transition: 'all 0.2s ease' }}
            onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-primary-bg)'; e.currentTarget.style.color = 'var(--color-primary)'; }}
            onMouseLeave={e => { e.currentTarget.style.background = 'var(--color-bg-tag)'; e.currentTarget.style.color = 'var(--color-text)'; }}
            onClick={onCategoryClick ? () => onCategoryClick(cat) : undefined}
          >
            {cat.name}{cat.postCount !== undefined ? ` (${cat.postCount})` : ''}
          </Tag>
        ))}
      </Space>
    </Card>
    <Card
      title={
        <Space>
          <TagsOutlined style={{ color: 'var(--color-primary)' }} />
          <span style={{ fontWeight: 600 }}>热门标签</span>
        </Space>
      }
      className="sidebar-card"
      style={{ borderRadius: 16, marginTop: 20, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }}
      styles={{ body: { padding: '12px 16px 16px' } }}
    >
      <Space wrap size={[8, 8]}>
        {tags.map((tag: TagType) => (
          <Tag
            key={tag.id}
            style={{ cursor: 'pointer', borderRadius: 12, padding: '3px 14px', background: 'var(--color-bg-tag)', color: 'var(--color-text)', border: 'none', fontSize: 13, transition: 'all 0.2s ease' }}
            onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-primary-bg)'; e.currentTarget.style.color = 'var(--color-primary)'; }}
            onMouseLeave={e => { e.currentTarget.style.background = 'var(--color-bg-tag)'; e.currentTarget.style.color = 'var(--color-text)'; }}
            onClick={onTagClick ? () => onTagClick(tag) : undefined}
          >
            {tag.name} ({tag.postCount})
          </Tag>
        ))}
      </Space>
    </Card>
    <Card
      title={
        <Space>
          <BookOutlined style={{ color: '#ff9500' }} />
          <span style={{ fontWeight: 600 }}>专栏推荐</span>
        </Space>
      }
      className="sidebar-card"
      style={{ borderRadius: 16, marginTop: 20, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }}
      styles={{ body: { padding: '4px 16px 12px' } }}
    >
      {seriesList.map((series: Series, index: number) => (
        <div
          key={series.id}
          style={{
            padding: '10px 8px',
            borderBottom: index < seriesList.length - 1 ? '1px solid var(--color-border)' : 'none',
            borderRadius: 8,
            transition: 'background 0.2s ease',
            cursor: 'pointer',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = 'var(--color-bg-hover)'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          onClick={onSeriesClick ? () => onSeriesClick(series) : undefined}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text strong style={{ fontSize: 14, color: 'var(--color-text)', transition: 'color 0.2s ease' }}
              onMouseEnter={e => { e.currentTarget.style.color = 'var(--color-primary)'; }}
              onMouseLeave={e => { e.currentTarget.style.color = 'var(--color-text)'; }}
            >
              {series.title}
            </Text>
            {series.isCompleted ? (
              <Tag color="green" style={{ margin: 0, fontSize: 11, borderRadius: 8 }}>已完结</Tag>
            ) : (
              <Tag color="orange" style={{ margin: 0, fontSize: 11, borderRadius: 8 }}>连载中</Tag>
            )}
          </div>
          <Text style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{series.postCount}篇文章</Text>
        </div>
      ))}
    </Card>
  </>
  );
};

export default Sidebar;
