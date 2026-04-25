import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Tag, Space, Typography, Avatar, Divider, Spin, Button, message } from 'antd';
import { EyeOutlined, LikeOutlined, LikeFilled, MessageOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import type { PostDetail as PostDetailType } from '../types';
import { getPostBySlug, likePost, unlikePost, getLikeStatus, reportView } from '../services/api';
import MarkdownRenderer from '../components/MarkdownRenderer';
import EmptyState from '../components/EmptyState';
import TableOfContents, { parseHeadings } from '../components/TableOfContents';
import FingerprintJS from '@fingerprintjs/fingerprintjs';

const { Text, Title } = Typography;

const PostDetail: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();

  const [post, setPost] = useState<PostDetailType | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [likeLoading, setLikeLoading] = useState(false);
  const visitorIdRef = useRef<string>('');
  const fpRef = useRef<ReturnType<typeof FingerprintJS.load> | null>(null);

  // 派生变量（必须在所有 hook 之后、early return 之前）
  const body = post?.body || post?.content?.body || '';
  const tocItems = parseHeadings(body);

  // 渲染后给标题元素注入 id
  useEffect(() => {
    if (!body) return;
    const container = document.querySelector('.markdown-body');
    if (!container) return;
    const headings = container.querySelectorAll('h1, h2, h3, h4, h5, h6');
    const idCount = new Map<string, number>();
    headings.forEach(el => {
      if (el.id) return;
      const text = el.textContent || '';
      const baseId = text.toLowerCase().replace(/[^a-z0-9一-鿿]+/g, '-').replace(/(^-|-$)/g, '');
      const count = idCount.get(baseId) || 0;
      const id = count > 0 ? `${baseId}-${count}` : baseId;
      idCount.set(baseId, count + 1);
      el.id = id;
    });
  }, [body]);

  useEffect(() => {
    fpRef.current = FingerprintJS.load({ monitoring: false });
  }, []);

  useEffect(() => {
    if (!slug) return;
    setLoading(true);
    setError(false);
    getPostBySlug(slug)
      .then(res => {
        const p = res.data;
        setPost(p);
        if (!p) return;
        setLikeCount(p.likeCount || 0);
        const viewedKey = `viewed_${p.id}`;
        if (!sessionStorage.getItem(viewedKey)) {
          reportView(p.id);
          sessionStorage.setItem(viewedKey, '1');
        }
        fpRef.current?.then(fp => fp.get()).then(result => {
          visitorIdRef.current = result.visitorId;
          if (p?.id) {
            getLikeStatus(p.id, result.visitorId).then(r => setLiked(r.data.liked));
          }
        });
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [slug]);

  const handleLike = async () => {
    if (!post || likeLoading || !visitorIdRef.current) return;
    setLikeLoading(true);
    try {
      if (liked) {
        const res = await unlikePost(post.id, visitorIdRef.current);
        setLiked(res.data.liked);
        setLikeCount(res.data.likeCount);
      } else {
        const res = await likePost(post.id, visitorIdRef.current);
        setLiked(res.data.liked);
        setLikeCount(res.data.likeCount);
      }
    } catch {
      message.error('操作失败，请重试');
    } finally {
      setLikeLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '120px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error || !post) {
    return (
      <Card style={{ borderRadius: 4 }}>
        <EmptyState
          icon={<div style={{ fontSize: 48 }}>📄</div>}
          description="文章不存在或已被删除"
          actionText="返回首页"
          onAction={() => navigate('/')}
        />
      </Card>
    );
  }

  return (
    <div style={{ maxWidth: 860, margin: '0 auto' }}>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        style={{ marginBottom: 16, padding: 0 }}
        onClick={() => navigate(-1)}
      >
        返回
      </Button>

      <Card id="post-content" style={{ borderRadius: 4 }} styles={{ body: { padding: '32px 40px' } }}>
        <Title level={2} style={{ marginTop: 0, marginBottom: 16, lineHeight: 1.4 }}>
          {post.title}
        </Title>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 20 }}>
          <Avatar size={32} style={{ background: '#1e80ff' }}>
            {post.author?.nickname?.[0] || 'U'}
          </Avatar>
          <Text style={{ color: '#333', fontWeight: 500 }}>{post.author?.nickname}</Text>
          <Divider type="vertical" />
          <Text style={{ color: '#8a919f', fontSize: 13 }}>
            {post.publishedAt?.split('T')[0]}
          </Text>
          <Divider type="vertical" />
          <Text style={{ color: '#1e80ff', fontSize: 13 }}>{post.category?.name}</Text>
        </div>

        <Space size={20} style={{ marginBottom: 24, display: 'flex' }}>
          <Space size={4} style={{ color: '#8a919f', fontSize: 13 }}>
            <EyeOutlined /> <span>{post.viewCount}</span>
          </Space>
          <Space
            size={4}
            style={{
              color: liked ? '#1e80ff' : '#8a919f',
              fontSize: 13,
              cursor: 'pointer',
            }}
            onClick={handleLike}
          >
            {liked ? <LikeFilled /> : <LikeOutlined />} <span>{likeCount}</span>
          </Space>
          <Space size={4} style={{ color: '#8a919f', fontSize: 13 }}>
            <MessageOutlined /> <span>{post.commentCount}</span>
          </Space>
        </Space>

        {post.tags && post.tags.length > 0 && (
          <Space style={{ marginBottom: 24 }}>
            {post.tags.map(tag => (
              <Tag key={tag.id} color="blue" style={{ borderRadius: 2 }}>{tag.name}</Tag>
            ))}
          </Space>
        )}

        <Divider style={{ margin: '0 0 24px' }} />

        {body ? (
          <MarkdownRenderer content={body} />
        ) : (
          <div style={{ textAlign: 'center', padding: 40, color: '#8a919f' }}>
            暂无文章内容
          </div>
        )}
      </Card>

      {/* 上下篇导航 */}
      {(post.prevPost || post.nextPost) && (
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, marginTop: 24 }}>
          <div style={{ flex: 1, textAlign: 'left' }}>
            {post.prevPost ? (
              <Button
                type="link"
                style={{ padding: 0, height: 'auto', textAlign: 'left' }}
                onClick={() => navigate(`/posts/${post.prevPost!.slug}`)}
              >
                <div>
                  <div style={{ fontSize: 12, color: '#8a919f' }}>← 上一篇</div>
                  <div style={{ fontSize: 14, color: '#1e80ff', lineHeight: 1.4, wordBreak: 'break-word' }}>
                    {post.prevPost.title}
                  </div>
                </div>
              </Button>
            ) : null}
          </div>
          <div style={{ flex: 1, textAlign: 'right' }}>
            {post.nextPost ? (
              <Button
                type="link"
                style={{ padding: 0, height: 'auto', textAlign: 'right' }}
                onClick={() => navigate(`/posts/${post.nextPost!.slug}`)}
              >
                <div>
                  <div style={{ fontSize: 12, color: '#8a919f' }}>下一篇 →</div>
                  <div style={{ fontSize: 14, color: '#1e80ff', lineHeight: 1.4, wordBreak: 'break-word' }}>
                    {post.nextPost.title}
                  </div>
                </div>
              </Button>
            ) : null}
          </div>
        </div>
      )}

      <TableOfContents items={tocItems} />
    </div>
  );
};

export default PostDetail;
