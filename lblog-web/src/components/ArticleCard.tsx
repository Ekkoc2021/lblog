import { Card, Tag, Space, Typography, Avatar, Divider, Image } from 'antd';
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
      ? <span key={i} style={{ color: 'var(--color-primary)', background: 'var(--color-primary-bg)', borderRadius: 3, padding: '0 1px' }}>{part}</span>
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
    className="article-card"
    style={{ marginBottom: 16, borderRadius: 16, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }}
    styles={{ body: { padding: '20px 24px' } }}
    onClick={onClick}
  >
    <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
          <Avatar size={24} src={post.author?.avatar || undefined} style={{ background: post.author?.avatar ? undefined : 'var(--color-primary)', fontSize: 12 }}>
            {!post.author?.avatar && (post.author?.nickname?.[0] || 'U')}
          </Avatar>
          <Text style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>{post.author?.nickname}</Text>
          <Divider type="vertical" style={{ borderColor: 'var(--color-border)' }} />
          <Text style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
            {post.publishedAt?.split('T')[0]}
          </Text>
          <Divider type="vertical" style={{ borderColor: 'var(--color-border)' }} />
          <Text style={{ fontSize: 13, color: 'var(--color-primary)' }}>{post.category?.name}</Text>
        </div>
        <Text strong style={{ fontSize: 17, display: 'block', marginBottom: 8, cursor: 'pointer', color: 'var(--color-text)', lineHeight: 1.4 }}>
          {keyword ? highlightText(post.title, keyword) : post.title}
        </Text>
        <Paragraph
          style={{ fontSize: 14, color: 'var(--color-text-secondary)', marginBottom: 10, lineHeight: '24px' }}
          ellipsis={{ rows: 2 }}
        >
          {keyword ? highlightText(post.excerpt, keyword) : post.excerpt}
        </Paragraph>
        <Space size={20}>
          <Space size={4} style={{ color: 'var(--color-text-tertiary)', fontSize: 13, cursor: 'pointer' }}>
            <LikeOutlined />
            <span>{post.likeCount}</span>
          </Space>
          <Space size={4} style={{ color: 'var(--color-text-tertiary)', fontSize: 13, cursor: 'pointer' }}>
            <MessageOutlined />
            <span>{post.commentCount}</span>
          </Space>
          <Space size={4} style={{ color: 'var(--color-text-tertiary)', fontSize: 13 }}>
            <EyeOutlined />
            <span>{post.viewCount}</span>
          </Space>
          {post.tags?.map((tag: TagType) => (
            <Tag key={tag.id} style={{ margin: 0, borderRadius: 10, background: 'var(--color-bg-tag)', color: 'var(--color-text-secondary)', border: 'none', fontSize: 12, padding: '1px 10px' }}>
              {tag.name}
            </Tag>
          ))}
        </Space>
      </div>
      {post.featuredImage && (
        <Image
          src={post.featuredImage}
          alt={post.title}
          width={160}
          height={100}
          style={{ objectFit: 'cover', borderRadius: 12, flexShrink: 0 }}
          preview={false}
        />
      )}
    </div>
  </Card>
);

export default ArticleCard;
