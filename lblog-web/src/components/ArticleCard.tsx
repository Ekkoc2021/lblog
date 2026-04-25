import { Card, Tag, Space, Typography, Avatar, Divider } from 'antd';
import { EyeOutlined, LikeOutlined, MessageOutlined } from '@ant-design/icons';
import type { Post, Tag as TagType } from '../types';

const { Text, Paragraph } = Typography;

function escapeRegex(str: string) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function highlightText(text: string, keyword: string) {
  if (!keyword.trim()) return text;
  const escaped = escapeRegex(keyword);
  const regex = new RegExp(`(${escaped})`, 'gi');
  const parts = text.split(regex);
  return parts.map((part, i) =>
    i % 2 === 1
      ? <span key={i} style={{ color: '#1e80ff', background: '#e6f4ff', borderRadius: 2 }}>{part}</span>
      : part
  );
}

interface ArticleCardProps {
  post: Post;
  keyword?: string;
  onClick?: () => void;
}

const ArticleCard: React.FC<ArticleCardProps> = ({ post, keyword, onClick }) => (
  <Card
    hoverable
    style={{ marginBottom: 12, borderRadius: 4 }}
    styles={{ body: { padding: '16px 20px' } }}
    onClick={onClick}
  >
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <Avatar size={22} style={{ background: '#1e80ff', fontSize: 12 }}>
            {post.author?.nickname?.[0] || 'U'}
          </Avatar>
          <Text style={{ fontSize: 13, color: '#8a919f' }}>{post.author?.nickname}</Text>
          <Divider type="vertical" />
          <Text style={{ fontSize: 13, color: '#8a919f' }}>
            {post.publishedAt?.split('T')[0]}
          </Text>
          <Divider type="vertical" />
          <Text style={{ fontSize: 13, color: '#1e80ff' }}>{post.category?.name}</Text>
        </div>
        <Text strong style={{ fontSize: 16, display: 'block', marginBottom: 6, cursor: 'pointer' }}>
          {keyword ? highlightText(post.title, keyword) : post.title}
        </Text>
        <Paragraph
          style={{ fontSize: 13, color: '#8a919f', marginBottom: 8, lineHeight: '22px' }}
          ellipsis={{ rows: 2 }}
        >
          {keyword ? highlightText(post.excerpt, keyword) : post.excerpt}
        </Paragraph>
        <Space size={16}>
          <Space size={4} style={{ color: '#8a919f', fontSize: 13, cursor: 'pointer' }}>
            <LikeOutlined />
            <span>{post.likeCount}</span>
          </Space>
          <Space size={4} style={{ color: '#8a919f', fontSize: 13, cursor: 'pointer' }}>
            <MessageOutlined />
            <span>{post.commentCount}</span>
          </Space>
          <Space size={4} style={{ color: '#8a919f', fontSize: 13 }}>
            <EyeOutlined />
            <span>{post.viewCount}</span>
          </Space>
          {post.tags?.map((tag: TagType) => (
            <Tag key={tag.id} color="blue" style={{ margin: 0, borderRadius: 2 }}>
              {tag.name}
            </Tag>
          ))}
        </Space>
      </div>
    </div>
  </Card>
);

export default ArticleCard;
