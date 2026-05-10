import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Tag, Space, Typography, Avatar, Divider, Skeleton, Button, message, Image } from 'antd';
import { EyeOutlined, LikeOutlined, LikeFilled, MessageOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import type { PostDetail as PostDetailType } from '../types';
import { getPostBySlug, likePost, unlikePost, getLikeStatus, reportView } from '../services/api';
import MarkdownRenderer from '../components/MarkdownRenderer';
import EmptyState from '../components/EmptyState';
import CommentSection from '../components/CommentSection';
import TableOfContents, { parseHeadings } from '../components/TableOfContents';
import { useSiteData } from '../contexts/SiteDataContext';
import FingerprintJS from '@fingerprintjs/fingerprintjs';

const { Text, Title } = Typography;

const PostDetail: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();

  const [post, setPost] = useState<PostDetailType | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [likeLoading, setLikeLoading] = useState(false);
  const visitorIdRef = useRef<string>('');
  const fpRef = useRef<ReturnType<typeof FingerprintJS.load> | null>(null);
  const { imageBaseUrl } = useSiteData();

  const coverSrc = post?.featuredImage
    ? (post.featuredImage.startsWith('/') ? `${imageBaseUrl.replace(/\/$/, '')}${post.featuredImage}` : post.featuredImage)
    : '';

  const body = post?.body || post?.content?.body || '';
  const tocItems = parseHeadings(body);

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
      <div style={{ maxWidth: 900, margin: '80px auto', padding: '0 24px' }}>
        <Card style={{ borderRadius: 16, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }}>
          <Skeleton active avatar paragraph={{ rows: 8 }} />
        </Card>
      </div>
    );
  }

  if (error || !post) {
    return (
      <Card style={{ borderRadius: 16, background: 'var(--color-bg-card)' }}>
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
    <div className="page-enter" style={{ maxWidth: 1500, margin: '0 auto' }}>

      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        style={{ marginBottom: 16, padding: 0 }}
        onClick={() => navigate(-1)}
      >
        返回
      </Button>

      <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
            <Card id="post-content" style={{ borderRadius: 16, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }} styles={{ body: { padding: '24px 28px', overflow: 'hidden' } }}>
              <Title level={2} style={{ marginTop: 0, marginBottom: 16, lineHeight: 1.4, fontSize: 28, fontWeight: 700, color: 'var(--color-text)', letterSpacing: '-0.02em' }}>
                {post.title}
              </Title>

              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 16 }}>
                <Avatar size={28} src={post.author?.avatar || undefined} style={{ background: post.author?.avatar ? undefined : 'var(--color-primary)' }}>
                  {!post.author?.avatar && (post.author?.nickname?.[0] || 'U')}
                </Avatar>
                <Text style={{ color: 'var(--color-text)', fontWeight: 500, fontSize: 14 }}>{post.author?.nickname}</Text>
                <Text style={{ color: 'var(--color-text-tertiary)' }}>/</Text>
                <Text style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
                  {post.publishedAt?.split('T')[0]}
                </Text>
                <Text style={{ color: 'var(--color-text-tertiary)' }}>/</Text>
                <Text style={{ color: 'var(--color-primary)', fontSize: 13 }}>{post.category?.name}</Text>
                <Text style={{ color: 'var(--color-border)' }}>|</Text>
                <Space size={4} style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
                  <EyeOutlined /> <span>{post.viewCount}</span>
                </Space>
                <Space
                  size={4}
                  style={{
                    color: liked ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                    fontSize: 13,
                    cursor: 'pointer',
                  }}
                  onClick={handleLike}
                >
                  {liked ? <LikeFilled /> : <LikeOutlined />} <span>{likeCount}</span>
                </Space>
                <Space size={4} style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
                  <MessageOutlined /> <span>{post.commentCount}</span>
                </Space>
              </div>

              {post.tags && post.tags.length > 0 && (
                <Space style={{ marginBottom: 16 }}>
                  {post.tags.map(tag => (
                    <Tag key={tag.id} style={{ borderRadius: 10, background: 'var(--color-bg-tag)', color: 'var(--color-text-secondary)', border: 'none', margin: 0 }}>{tag.name}</Tag>
                  ))}
                </Space>
              )}

              {post.featuredImage && (
                <div
                  style={{ textAlign: 'center', marginBottom: 20, padding: 12, background: 'var(--color-bg-hover)', borderRadius: 12, border: '1px solid var(--color-border)', cursor: 'pointer' }}
                  onClick={() => setPreviewVisible(true)}
                >
                  <img
                    src={coverSrc}
                    alt={post.title}
                    style={{ width: '100%', height: 'auto', borderRadius: 8, display: 'block', boxShadow: '0 2px 12px rgba(0,0,0,0.08)' }}
                  />
                </div>
              )}

              <Divider style={{ margin: '0 0 24px' }} />

              {body ? (
                <MarkdownRenderer content={body} imageBaseUrl={imageBaseUrl} />
              ) : (
                <div style={{ textAlign: 'center', padding: 40, color: 'var(--color-text-secondary)' }}>
                  暂无文章内容
                </div>
              )}
            </Card>

            {post.featuredImage && (
              <div style={{ display: 'none' }}>
                <Image
                  src={coverSrc}
                  preview={{ visible: previewVisible, onVisibleChange: setPreviewVisible }}
                />
              </div>
            )}

            {(post.prevPost || post.nextPost) && (
              <Card style={{ marginTop: 16, borderRadius: 16, boxShadow: 'var(--shadow-card)', border: 'none', background: 'var(--color-bg-card)' }} styles={{ body: { padding: '12px 16px' } }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
                  <div style={{ flex: 1, textAlign: 'left' }}>
                    {post.prevPost ? (
                      <Button
                        type="link"
                        style={{ padding: 0, height: 'auto', textAlign: 'left' }}
                        onClick={() => navigate(`/posts/${post.prevPost!.slug}`)}
                      >
                        <div>
                          <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>← 上一篇</div>
                          <div style={{ fontSize: 14, color: 'var(--color-primary)', lineHeight: 1.4, wordBreak: 'break-word' }}>
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
                          <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>下一篇 →</div>
                          <div style={{ fontSize: 14, color: 'var(--color-primary)', lineHeight: 1.4, wordBreak: 'break-word' }}>
                            {post.nextPost.title}
                          </div>
                        </div>
                      </Button>
                    ) : null}
                  </div>
                </div>
              </Card>
            )}

            {post && post.commentEnable !== 0 && <CommentSection postId={post.id} />}
          </div>

          {tocItems.length > 0 && (
            <div className="toc-sidebar" style={{
              width: 200,
              flexShrink: 0,
              position: 'sticky',
              top: 80,
              alignSelf: 'flex-start',
            }}>
              <TableOfContents items={tocItems} />
            </div>
          )}
        </div>
      </div>
  );
};

export default PostDetail;
