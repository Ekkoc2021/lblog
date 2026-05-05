import { useState, useEffect, useCallback } from 'react';
import { Card, Input, Select, Switch, Button, Space, Typography, message, Modal } from 'antd';
import { SaveOutlined, SendOutlined, ArrowLeftOutlined, PictureOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import MarkdownRenderer from '../../components/MarkdownRenderer';
import type { Category, Tag, Series } from '../../types';
import { getCategories, getTags, getSeries, getAdminPostById, createPost, updatePost } from '../../services/api';

const { TextArea } = Input;
const { Text } = Typography;

const DRAFT_KEY = 'lblog_editor_draft';

interface PostMeta {
  slug: string;
  excerpt: string;
  categoryId: number | undefined;
  tagIds: number[];
  seriesId: number | undefined;
  commentEnable: boolean;
  isPrivate: boolean;
  featuredImage: string;
}

function toSlug(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^\w一-龥]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80);
}

function extractExcerpt(body: string): string {
  const cleaned = body
    .replace(/^#+\s+.*$/gm, '')
    .replace(/\n{2,}/g, '\n')
    .replace(/```[\s\S]*?```/g, '')
    .trim();
  const firstLine = cleaned.split('\n').find(l => l.trim().length > 0);
  return (firstLine || '').slice(0, 200);
}

const PostEditor: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;

  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [meta, setMeta] = useState<PostMeta>({
    slug: '',
    excerpt: '',
    categoryId: undefined,
    tagIds: [],
    seriesId: undefined,
    commentEnable: true,
    isPrivate: false,
    featuredImage: '',
  });
  const [slugEdited, setSlugEdited] = useState(false);
  const [saving, setSaving] = useState(false);
  const [metaModalVisible, setMetaModalVisible] = useState(false);
  const [pendingAction, setPendingAction] = useState<'draft' | 'published' | null>(null);
  const [draftRestored, setDraftRestored] = useState(false);

  // 下拉选项数据
  const [categories, setCategories] = useState<Category[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [seriesList, setSeriesList] = useState<Series[]>([]);

  // 加载下拉选项
  useEffect(() => {
    getCategories(100).then(res => setCategories(res.data)).catch((e: Error) => message.error(e.message));
    getTags(100).then(res => setTags(res.data)).catch((e: Error) => message.error(e.message));
    getSeries(100).then(res => setSeriesList(res.data)).catch((e: Error) => message.error(e.message));
  }, []);

  // 编辑模式：从 API 加载已有文章
  useEffect(() => {
    if (isEdit) {
      getAdminPostById(Number(id)).then(res => {
        const p = res.data;
        if (p) {
          setTitle(p.title);
          setBody(p.body || '');
          setMeta({
            slug: p.slug,
            excerpt: p.excerpt,
            categoryId: p.categoryId || undefined,
            tagIds: p.tags?.map(t => t.id) || [],
            seriesId: p.series?.id || undefined,
            commentEnable: p.commentEnable !== 0,
            isPrivate: p.status === 2,
            featuredImage: p.featuredImage || '',
          });
          setSlugEdited(true);
        }
      }).catch((e: Error) => message.error(e.message));
      return;
    }
    // 新建文章：尝试恢复本地草稿
    if (!draftRestored) {
      try {
        const saved = localStorage.getItem(DRAFT_KEY);
        if (saved) {
          const draft = JSON.parse(saved);
          setTitle(draft.title || '');
          setBody(draft.body || '');
          setMeta(draft.meta || {
            slug: '', excerpt: '', categoryId: undefined, tagIds: [],
            seriesId: undefined, commentEnable: true, isPrivate: false, featuredImage: '',
          });
          setSlugEdited(draft.slugEdited || false);
        }
      } catch { /* ignore */ }
      setDraftRestored(true);
    }
  }, [id, isEdit, draftRestored]);

  // 新建文章时，自动保存草稿到本地
  useEffect(() => {
    if (isEdit || !draftRestored) return;
    const timer = setTimeout(() => {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ title, body, meta, slugEdited }));
    }, 300);
    return () => clearTimeout(timer);
  }, [title, body, meta, slugEdited, isEdit, draftRestored]);

  const clearDraft = useCallback(() => {
    localStorage.removeItem(DRAFT_KEY);
  }, []);

  const handleTitleChange = (val: string) => {
    setTitle(val);
    if (!slugEdited) {
      setMeta(prev => ({ ...prev, slug: toSlug(val) }));
    }
  };

  const openMetaModal = (action: 'draft' | 'published' | null) => {
    if (action && !title.trim()) {
      message.warning('请先输入文章标题');
      return;
    }
    if (action && !meta.excerpt && body) {
      setMeta(prev => ({ ...prev, excerpt: extractExcerpt(body) }));
    }
    setPendingAction(action);
    setMetaModalVisible(true);
  };

  const handleSave = async () => {
    if (!meta.slug.trim()) {
      message.warning('请输入 URL 别名');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        title,
        slug: meta.slug,
        excerpt: meta.excerpt || extractExcerpt(body),
        body,
        featuredImage: meta.featuredImage || null,
        status: meta.isPrivate ? 2 : (pendingAction === 'published' ? 1 : 0),
        categoryId: meta.categoryId || null,
        tagIds: meta.tagIds,
        seriesId: meta.seriesId || null,
        commentEnable: meta.commentEnable ? 1 : 0,
      };

      if (isEdit) {
        await updatePost(Number(id), payload);
      } else {
        await createPost(payload);
        clearDraft();
      }
      message.success(pendingAction === 'published' ? '文章已发布' : '设置已保存');
      setMetaModalVisible(false);
      navigate('/author/posts');
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleImageUpload = () => {
    message.info('图片上传功能待对接后端');
  };

  const updateMeta = <K extends keyof PostMeta>(key: K, value: PostMeta[K]) => {
    setMeta(prev => ({ ...prev, [key]: value }));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 'calc(100vh - 112px)' }}>
      {/* 顶部操作栏 */}
      <Card styles={{ body: { padding: '12px 24px' } }} style={{ marginBottom: 16, borderRadius: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/author/posts')}>返回</Button>
          <Input
            placeholder="输入文章标题..."
            value={title}
            onChange={e => handleTitleChange(e.target.value)}
            variant="borderless"
            style={{ fontSize: 20, fontWeight: 600, flex: 1 }}
          />
          <Space>
            <Button icon={<SaveOutlined />} onClick={() => openMetaModal('draft')}>保存草稿</Button>
            <Button type="primary" icon={<SendOutlined />} onClick={() => openMetaModal('published')}>发布</Button>
          </Space>
        </div>
      </Card>

      {/* 编辑器主体 */}
      <div style={{ display: 'flex', gap: 16, flex: 1, minHeight: 400 }}>
        <Card
          title="Markdown 编辑器"
          style={{ flex: 1, borderRadius: 8 }}
          styles={{ body: { padding: 0, height: 'calc(100% - 57px)' } }}
        >
          <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <TextArea
              placeholder="在此输入 Markdown 内容..."
              value={body}
              onChange={e => setBody(e.target.value)}
              style={{ flex: 1, border: 'none', resize: 'none', padding: 16, fontSize: 14 }}
            />
            <div style={{ padding: '4px 16px', borderTop: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Button type="link" size="small" icon={<PictureOutlined />} onClick={handleImageUpload} style={{ color: '#999' }}>
                插入图片
              </Button>
              <Space size="middle">
                <Text type="secondary" style={{ fontSize: 12 }}>{body.length} 字</Text>
                <Button
                  type="link"
                  size="small"
                  icon={<SettingOutlined />}
                  onClick={() => openMetaModal(null)}
                  style={{ color: '#999' }}
                >
                  文章设置
                </Button>
              </Space>
            </div>
          </div>
        </Card>

        <Card title="预览" style={{ flex: 1, borderRadius: 8 }} styles={{ body: { padding: 16, height: 'calc(100% - 57px)', overflow: 'auto' } }}>
          {body ? (
            <MarkdownRenderer content={body} />
          ) : (
            <div style={{ color: '#ccc', textAlign: 'center', marginTop: 40 }}>开始在左侧编写，实时预览</div>
          )}
        </Card>
      </div>

      {/* 文章设置弹窗 */}
      <Modal
        title="文章设置"
        open={metaModalVisible}
        onCancel={() => setMetaModalVisible(false)}
        footer={null}
        width={520}
        destroyOnClose={false}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 4, display: 'block' }}>URL 别名</Text>
            <Input
              value={meta.slug}
              onChange={e => { updateMeta('slug', e.target.value); setSlugEdited(true); }}
              placeholder="url-identifier"
            />
          </div>

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 4, display: 'block' }}>分类</Text>
            <Select
              placeholder="选择分类"
              style={{ width: '100%' }}
              value={meta.categoryId}
              onChange={v => updateMeta('categoryId', v)}
              allowClear
              options={categories.map(c => ({ value: c.id, label: c.name }))}
            />
          </div>

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 4, display: 'block' }}>标签</Text>
            <Select
              mode="multiple"
              placeholder="选择标签"
              style={{ width: '100%' }}
              value={meta.tagIds}
              onChange={v => updateMeta('tagIds', v)}
              options={tags.map(t => ({ value: t.id, label: t.name }))}
            />
          </div>

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 4, display: 'block' }}>专栏</Text>
            <Select
              placeholder="关联专栏"
              style={{ width: '100%' }}
              value={meta.seriesId}
              onChange={v => updateMeta('seriesId', v)}
              allowClear
              options={seriesList.map(s => ({ value: s.id, label: s.title }))}
            />
          </div>

          <div style={{ display: 'flex', gap: 24 }}>
            <Space>
              <Text type="secondary">允许评论</Text>
              <Switch checked={meta.commentEnable} onChange={v => updateMeta('commentEnable', v)} />
            </Space>
            <Space>
              <Text type="secondary">私密</Text>
              <Switch checked={meta.isPrivate} onChange={v => updateMeta('isPrivate', v)} />
            </Space>
          </div>

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 4, display: 'block' }}>摘要</Text>
            <TextArea
              rows={3}
              value={meta.excerpt}
              onChange={e => updateMeta('excerpt', e.target.value)}
              placeholder="自动从正文生成，也可手动修改"
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, paddingTop: 8 }}>
            <Button onClick={() => setMetaModalVisible(false)}>取消</Button>
            <Button
              type="primary"
              icon={pendingAction === 'published' ? <SendOutlined /> : <SaveOutlined />}
              loading={saving}
              onClick={handleSave}
            >
              {pendingAction === 'published' ? '发布' : pendingAction === 'draft' ? '保存草稿' : '保存'}
            </Button>
          </div>
        </Space>
      </Modal>
    </div>
  );
};

export default PostEditor;
