import { useState, useEffect, useCallback } from 'react';
import { Card, Avatar, Typography, Button, Input, Divider, message, Spin, Tag, Skeleton } from 'antd';
import { MessageOutlined, UserOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import { getComments, getCommentReplies, createComment } from '../services/api';
import type { Comment } from '../types';

const { Text } = Typography;
const { TextArea } = Input;

interface CommentSectionProps {
  postId: number;
}

const CommentSection: React.FC<CommentSectionProps> = ({ postId }) => {
  const { isAuthenticated, user } = useAuth();
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  // 顶级评论输入
  const [topComment, setTopComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 内联回复 { commentId: { replyToName, parentId, rootId, replyContent, content, submitting } }
  const [inlineReplies, setInlineReplies] = useState<Record<number, { replyToName: string; parentId: number; rootId: number; replyContent: string; content: string; submitting: boolean }>>({});

  // 展开回复
  const [expandedReplies, setExpandedReplies] = useState<Record<number, { list: Comment[]; page: number; total: number; loading: boolean }>>({});

  const loadComments = useCallback((p: number) => {
    setLoading(true);
    getComments(postId, { page: p, pageSize, sort: 'newest' })
      .then(res => {
        setComments(prev => p === 1 ? res.data.list : [...prev, ...res.data.list]);
        setTotal(res.data.total);
        setPage(p);
      })
      .catch((e: Error) => message.error(e.message))
      .finally(() => setLoading(false));
  }, [postId]);

  useEffect(() => { loadComments(1); }, [loadComments]);

  const handleLoadMore = () => loadComments(page + 1);

  const handleSubmitTop = async () => {
    if (!topComment.trim()) return;
    setSubmitting(true);
    try {
      const res = await createComment(postId, { content: topComment.trim() });
      // 乐观更新：拼一条本地评论插入列表顶部
      const local: Comment = {
        id: res.data.id,
        author: { id: user!.id, nickname: user!.nickname, avatar: user!.avatar },
        content: topComment.trim(),
        replyTo: null,
        likeCount: 0,
        replyCount: 0,
        createdAt: new Date().toISOString(),
        status: 0,
      };
      setComments(prev => [local, ...prev]);
      setTotal(prev => prev + 1);
      message.success('评论已提交，等待审核');
      setTopComment('');
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitReply = async (commentId: number) => {
    const reply = inlineReplies[commentId];
    if (!reply || !reply.content.trim()) return;
    setInlineReplies(prev => ({ ...prev, [commentId]: { ...prev[commentId], submitting: true } }));
    try {
      const res = await createComment(postId, { content: reply.content.trim(), parentId: reply.parentId });
      // 乐观更新：拼一条本地回复
      const isRootReply = reply.parentId === reply.rootId;
      const local: Comment = {
        id: res.data.id,
        author: { id: user!.id, nickname: user!.nickname, avatar: user!.avatar },
        content: reply.content.trim(),
        replyTo: isRootReply ? null : { id: reply.parentId, nickname: reply.replyToName },
        likeCount: 0,
        replyCount: 0,
        createdAt: new Date().toISOString(),
        status: 0,
      };
      const rootId = reply.rootId;
      setExpandedReplies(prev => ({
        ...prev,
        [rootId]: {
          list: [local, ...(prev[rootId]?.list ?? [])],
          page: prev[rootId]?.page ?? 1,
          total: (prev[rootId]?.total ?? 0) + 1,
          loading: false,
        },
      }));
      // 更新顶级评论的 replyCount
      setComments(prev => prev.map(c => c.id === rootId ? { ...c, replyCount: c.replyCount + 1 } : c));
      message.success('回复已提交，等待审核');
      setInlineReplies(prev => { const { [commentId]: _, ...rest } = prev; return rest; });
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '提交失败');
      setInlineReplies(prev => ({ ...prev, [commentId]: { ...prev[commentId], submitting: false } }));
    }
  };

  const openInlineReply = (commentId: number, parentId: number, rootId: number, replyToName: string, replyContent?: string) => {
    setInlineReplies(prev => ({
      ...prev,
      [commentId]: { replyToName, parentId, rootId, replyContent: replyContent ?? '', content: '', submitting: false },
    }));
  };

  const closeInlineReply = (commentId: number) => {
    setInlineReplies(prev => { const { [commentId]: _, ...rest } = prev; return rest; });
  };

  const loadReplies = async (rootId: number, force = false) => {
    const state = expandedReplies[rootId];
    const nextPage = state && !force ? state.page + 1 : 1;

    setExpandedReplies(prev => ({
      ...prev,
      [rootId]: { ...prev[rootId], list: prev[rootId]?.list ?? [], page: prev[rootId]?.page ?? 0, total: prev[rootId]?.total ?? 0, loading: true },
    }));

    try {
      const res = await getCommentReplies(postId, rootId, { page: nextPage, pageSize: 10 });
      setExpandedReplies(prev => ({
        ...prev,
        [rootId]: {
          list: nextPage === 1 ? res.data.list : [...(prev[rootId]?.list ?? []), ...res.data.list],
          page: nextPage,
          total: res.data.total,
          loading: false,
        },
      }));
    } catch {
      setExpandedReplies(prev => ({ ...prev, [rootId]: { ...prev[rootId], loading: false } }));
    }
  };

  const toggleReplies = (rootId: number) => {
    if (expandedReplies[rootId]) {
      setExpandedReplies(prev => { const { [rootId]: _, ...rest } = prev; return rest; });
    } else {
      loadReplies(rootId);
    }
  };

  const formatTime = (t: string) => t ? t.replace('T', ' ').slice(0, 19) : '';

  const renderComment = (item: Comment, rootId?: number) => {
    const reply = inlineReplies[item.id];
    return (
      <div key={item.id} style={{ padding: '12px 0', borderBottom: '1px solid var(--color-border)' }}>
        <div style={{ display: 'flex', gap: 10 }}>
          <Avatar size={32} icon={<UserOutlined />} style={{ background: 'var(--color-primary)', flexShrink: 0 }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <Text strong style={{ fontSize: 13, color: 'var(--color-text)' }}>{item.author.nickname}</Text>
              <Text style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>{formatTime(item.createdAt)}</Text>
              {item.status === 0 && <Tag color="orange" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', margin: 0 }}>待审核</Tag>}
              {item.status === 2 && <Tag color="red" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', margin: 0 }}>未通过</Tag>}
            </div>
            <div style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--color-text)', wordBreak: 'break-word', overflowWrap: 'break-word' }}>
              {item.replyTo && <Text style={{ color: 'var(--color-primary)' }}>@{item.replyTo.nickname} </Text>}
              {item.content}
            </div>
            {isAuthenticated && (
              <div style={{ marginTop: 6 }}>
                <Button type="link" size="small" style={{ color: 'var(--color-text-secondary)', padding: 0, fontSize: 12 }}
                  onClick={() => openInlineReply(item.id, item.id, rootId ?? item.id, item.author.nickname, item.content)}>
                  回复
                </Button>
              </div>
            )}
            {/* 内联回复框 */}
            {reply && (
              <div style={{ marginTop: 8, padding: '8px 12px', background: 'var(--color-bg-hover)', borderRadius: 4 }}>
                <div style={{ marginBottom: 6, fontSize: 13, color: 'var(--color-text-secondary)' }}>
                  回复 @{reply.replyToName}
                  {reply.replyContent && <Text style={{ color: 'var(--color-text-tertiary)' }}>：{reply.replyContent.slice(0, 5)}{reply.replyContent.length > 5 ? '...' : ''}</Text>}
                  <Button type="link" size="small" style={{ padding: '0 8px', fontSize: 12 }} onClick={() => closeInlineReply(item.id)}>取消</Button>
                </div>
                <TextArea
                  rows={2}
                  placeholder={`回复 @${reply.replyToName}...`}
                  value={reply.content}
                  onChange={e => setInlineReplies(prev => ({ ...prev, [item.id]: { ...prev[item.id], content: e.target.value } }))}
                />
                <div style={{ textAlign: 'right', marginTop: 6 }}>
                  <Button type="primary" size="small" loading={reply.submitting} disabled={!reply.content.trim()}
                    onClick={() => handleSubmitReply(item.id)}>
                    回复
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  };

  return (
    <Card id="comments" style={{ borderRadius: 4, marginTop: 24 }} title={<span><MessageOutlined /> 评论 ({total})</span>}>
      {/* 顶部输入框 — 只发顶级评论 */}
      {isAuthenticated ? (
        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', gap: 10 }}>
            <Avatar size={32} icon={<UserOutlined />} style={{ background: 'var(--color-primary)', flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <TextArea
                rows={3}
                placeholder="发表评论..."
                value={topComment}
                onChange={e => setTopComment(e.target.value)}
              />
              <div style={{ textAlign: 'right', marginTop: 8 }}>
                <Button type="primary" loading={submitting} disabled={!topComment.trim()} onClick={handleSubmitTop}>
                  发表评论
                </Button>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '16px 0', color: 'var(--color-text-secondary)', fontSize: 13, marginBottom: 20 }}>
          登录后参与评论
        </div>
      )}

      <Divider style={{ margin: '0 0 8px' }} />

      {/* 评论列表 */}
      {comments.map(item => (
        <div key={item.id}>
          {renderComment(item)}
          {item.replyCount > 0 && (
            <div style={{ paddingLeft: 42 }}>
              <Button type="link" size="small" style={{ fontSize: 12, padding: 0 }}
                onClick={() => toggleReplies(item.id)}>
                {expandedReplies[item.id] ? '收起回复' : `查看 ${item.replyCount} 条回复`}
              </Button>
              {expandedReplies[item.id] && (
                <div>
                  {expandedReplies[item.id].list.map(r => renderComment(r, item.id))}
                  {expandedReplies[item.id].loading && (
                    <div style={{ textAlign: 'center', padding: 8 }}><Spin size="small" /></div>
                  )}
                  {expandedReplies[item.id].list.length < expandedReplies[item.id].total && (
                    <Button type="link" size="small" style={{ fontSize: 12, padding: '0 0 4px' }}
                      onClick={() => loadReplies(item.id)}>
                      加载更多回复
                    </Button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      ))}

      {loading && comments.length === 0 && [1,2,3].map(i => (
            <div key={i} style={{ padding: '12px 0', borderBottom: '1px solid var(--color-border)' }}>
              <Skeleton active avatar paragraph={{ rows: 1 }} />
            </div>
          ))}

      {!loading && comments.length === 0 && (
        <div style={{ textAlign: 'center', padding: 20, color: 'var(--color-text-secondary)' }}>暂无评论</div>
      )}

      {comments.length < total && (
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Button onClick={handleLoadMore} loading={loading}>加载更多</Button>
        </div>
      )}
    </Card>
  );
};

export default CommentSection;
