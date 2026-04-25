import { Card, Tag, Space, Typography } from 'antd';
import { FireOutlined, TagsOutlined, BookOutlined } from '@ant-design/icons';
import type { Post, Tag as TagType, Series } from '../types';

const { Text } = Typography;

interface SidebarProps {
  posts: Post[];
  tags: TagType[];
  seriesList: Series[];
  onTagClick?: (tag: TagType) => void;
  onSeriesClick?: (series: Series) => void;
  onPostClick?: (post: Post) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ posts, tags, seriesList, onTagClick, onSeriesClick, onPostClick }) => (
  <>
    <Card
      title={
        <Space>
          <FireOutlined style={{ color: '#ff4d4f' }} />
          <span>热门文章</span>
        </Space>
      }
      style={{ borderRadius: 4 }}
      styles={{ body: { padding: '8px 16px' } }}
    >
      {[...posts].sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0)).slice(0, 5).map((post, index) => (
        <div
          key={post.id}
          style={{ padding: '8px 0', borderBottom: index < 4 ? '1px solid #f0f0f0' : 'none', borderRadius: 4, transition: 'background 0.2s', cursor: 'pointer' }}
          onMouseEnter={e => { e.currentTarget.style.background = '#f0f5ff'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          onClick={onPostClick ? () => onPostClick(post) : undefined}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
            <Text strong style={{ fontSize: 14, color: index < 3 ? '#1e80ff' : '#8a919f', minWidth: 20 }}>
              {index + 1}.
            </Text>
            <Text style={{ fontSize: 13, flex: 1 }} ellipsis>{post.title}</Text>
          </div>
        </div>
      ))}
    </Card>
    <Card
      title={
        <Space>
          <TagsOutlined />
          <span>热门标签</span>
        </Space>
      }
      style={{ borderRadius: 4, marginTop: 16 }}
      styles={{ body: { padding: 16 } }}
    >
      <Space wrap size={[8, 8]}>
        {tags.map((tag: TagType) => (
          <Tag
            key={tag.id}
            style={{ cursor: 'pointer', borderRadius: 4, padding: '2px 10px', border: '1px solid #d9d9d9', transition: 'all 0.2s' }}
            onMouseEnter={e => { e.currentTarget.style.color = '#1e80ff'; e.currentTarget.style.borderColor = '#1e80ff'; e.currentTarget.style.background = '#e6f4ff'; }}
            onMouseLeave={e => { e.currentTarget.style.color = ''; e.currentTarget.style.borderColor = '#d9d9d9'; e.currentTarget.style.background = ''; }}
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
          <BookOutlined />
          <span>专栏推荐</span>
        </Space>
      }
      style={{ borderRadius: 4, marginTop: 16 }}
      styles={{ body: { padding: '8px 16px' } }}
    >
      {seriesList.map((series: Series, index: number) => (
        <div
          key={series.id}
          style={{ padding: '10px 8px', borderBottom: index < seriesList.length - 1 ? '1px solid #f0f0f0' : 'none', borderRadius: 4, transition: 'background 0.2s', cursor: 'pointer' }}
          onMouseEnter={e => { e.currentTarget.style.background = '#f0f5ff'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          onClick={onSeriesClick ? () => onSeriesClick(series) : undefined}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text strong style={{ fontSize: 14, transition: 'color 0.2s' }}
              onMouseEnter={e => { e.currentTarget.style.color = '#1e80ff'; }}
              onMouseLeave={e => { e.currentTarget.style.color = ''; }}
            >
              {series.title}
            </Text>
            {series.isCompleted ? (
              <Tag color="green" style={{ margin: 0, fontSize: 11 }}>已完结</Tag>
            ) : (
              <Tag color="orange" style={{ margin: 0, fontSize: 11 }}>连载中</Tag>
            )}
          </div>
          <Text style={{ fontSize: 12, color: '#8a919f' }}>{series.postCount}篇文章</Text>
        </div>
      ))}
    </Card>
  </>
);

export default Sidebar;
