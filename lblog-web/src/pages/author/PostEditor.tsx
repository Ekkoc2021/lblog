import { useState, useEffect, useCallback, useRef } from 'react';
import { Card, Input, Select, Switch, Button, Space, Typography, message, Modal, Segmented, Tooltip, Image } from 'antd';
import { SaveOutlined, SendOutlined, ArrowLeftOutlined, PictureOutlined, SettingOutlined, LoadingOutlined, EditOutlined, EyeOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { BlockNoteViewRaw, useCreateBlockNote } from '@blocknote/react';
import '@blocknote/react/style.css';
import MarkdownRenderer from '../../components/MarkdownRenderer';
import { useSiteData } from '../../contexts/SiteDataContext';
import type { Category, Tag, Series } from '../../types';
import { getAuthorCategories, getAuthorTags, getAuthorSeries, getAdminPostById, createPost, updatePost, uploadImage } from '../../services/api';

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
  const [uploading, setUploading] = useState(false);
  const [coverUploading, setCoverUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const coverFileInputRef = useRef<HTMLInputElement>(null);
  const splitContainerRef = useRef<HTMLDivElement>(null);
  const [leftRatio, setLeftRatio] = useState(0.5);
  const [dragging, setDragging] = useState(false);
  const { imageBaseUrl, imageMaxSize } = useSiteData();
  const [mode, setMode] = useState<'split' | 'wysiwyg'>('split');
  const [bnHasContent, setBnHasContent] = useState(false);
  const modeLocked = body.trim().length > 0 || bnHasContent;

  const bnEditor = useCreateBlockNote({
    uploadFile: async (file) => {
      if (file.size > imageMaxSize) {
        message.warning(`图片大小超过限制（最大 ${Math.round(imageMaxSize / 1048576)}MB）`);
        throw new Error('File too large');
      }
      const res = await uploadImage(file);
      return res.data.url;
    },
  });

  const prevModeRef = useRef(mode);
  useEffect(() => {
    if (mode === 'wysiwyg' && prevModeRef.current === 'split' && body) {
      try { bnEditor.replaceBlocks(bnEditor.document, bnEditor.tryParseMarkdownToBlocks(body)); } catch { /* ignore */ }
    }
    if (mode === 'split' && prevModeRef.current === 'wysiwyg') {
      setBody(bnEditor.blocksToMarkdownLossy());
    }
    prevModeRef.current = mode;
  }, [mode]);

  // 返回前同步 BlockNote 内容 → body，直接写入 localStorage 草稿
  const handleBack = useCallback(() => {
    const finalBody = mode === 'wysiwyg' ? bnEditor.blocksToMarkdownLossy() : body;
    if (mode === 'wysiwyg') setBody(finalBody);
    // setBody 是异步的，直接手动保存草稿到 localStorage
    if (!isEdit) {
      try { localStorage.setItem(DRAFT_KEY, JSON.stringify({ title, body: finalBody, meta, slugEdited })); } catch { /* */ }
    }
    navigate('/author/posts');
  }, [mode, bnEditor, body, isEdit, title, meta, slugEdited, navigate]);

  // 下拉选项数据
  const [categories, setCategories] = useState<Category[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [seriesList, setSeriesList] = useState<Series[]>([]);

  // 加载下拉选项
  useEffect(() => {
    getAuthorCategories({ page: 1, pageSize: 100 }).then(res => setCategories(res.data.list)).catch((e: Error) => message.error(e.message));
    getAuthorTags({ page: 1, pageSize: 100 }).then(res => setTags(res.data.list)).catch((e: Error) => message.error(e.message));
    getAuthorSeries({ page: 1, pageSize: 100 }).then(res => setSeriesList(res.data.list)).catch((e: Error) => message.error(e.message));
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
      try { localStorage.setItem(DRAFT_KEY, JSON.stringify({ title, body, meta, slugEdited })); } catch { /* */ }
    }, 300);
    return () => clearTimeout(timer);
  }, [title, body, meta, slugEdited, isEdit, draftRestored]);

  const clearDraft = useCallback(() => {
    try { localStorage.removeItem(DRAFT_KEY); } catch { /* */ }
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
    // 富文本模式下，保存前先同步内容
    const finalBody = mode === 'wysiwyg' ? bnEditor.blocksToMarkdownLossy() : body;
    if (mode === 'wysiwyg') setBody(finalBody);
    if (!meta.slug.trim()) {
      message.warning('请输入 URL 别名');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        title,
        slug: meta.slug,
        excerpt: meta.excerpt || extractExcerpt(finalBody),
        body: finalBody,
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

  const insertAtCursor = (text: string) => {
    const textarea = document.getElementById('post-editor-textarea') as HTMLTextAreaElement | null;
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    setBody(body.slice(0, start) + text + body.slice(end));
    requestAnimationFrame(() => {
      textarea.focus();
      const pos = start + text.length;
      textarea.setSelectionRange(pos, pos);
    });
  };

  const handleUploadImage = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.warning('请选择图片文件');
      return;
    }
    if (file.size > imageMaxSize) {
      message.warning(`图片大小超过限制（最大 ${Math.round(imageMaxSize / 1048576)}MB）`);
      return;
    }
    setUploading(true);
    try {
      const res = await uploadImage(file);
      const markdown = `![${file.name}](${res.data.url})`;
      insertAtCursor(markdown);
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    for (let i = 0; i < e.clipboardData.items.length; i++) {
      const item = e.clipboardData.items[i];
      if (item.type.startsWith('image/')) {
        e.preventDefault();
        const file = item.getAsFile();
        if (file) handleUploadImage(file);
        return;
      }
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleUploadImage(file);
    e.target.value = '';
  };

  const handleCoverUpload = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      message.warning('请选择图片文件');
      return;
    }
    if (file.size > imageMaxSize) {
      message.warning(`图片大小超过限制（最大 ${Math.round(imageMaxSize / 1048576)}MB）`);
      return;
    }
    setCoverUploading(true);
    try {
      const res = await uploadImage(file);
      updateMeta('featuredImage', res.data.url);
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '上传失败');
    } finally {
      setCoverUploading(false);
    }
  };

  const handleCoverSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleCoverUpload(file);
    e.target.value = '';
  };

  const handleCoverRemove = () => {
    updateMeta('featuredImage', '');
  };

  const handleCoverPaste = (e: React.ClipboardEvent) => {
    for (let i = 0; i < e.clipboardData.items.length; i++) {
      const item = e.clipboardData.items[i];
      if (item.type.startsWith('image/')) {
        e.preventDefault();
        const file = item.getAsFile();
        if (file) handleCoverUpload(file);
        return;
      }
    }
  };

  const getImageUrl = (url: string) => {
    return url && imageBaseUrl && url.startsWith('/')
      ? `${imageBaseUrl.replace(/\/$/, '')}${url}`
      : url;
  };

  const handleImageButtonClick = () => {
    fileInputRef.current?.click();
  };

  // 可拖拽分隔条
  const handleMouseDown = () => setDragging(true);

  useEffect(() => {
    if (!dragging) return;
    const handleMouseMove = (e: MouseEvent) => {
      const el = splitContainerRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      setLeftRatio(Math.min(Math.max((e.clientX - rect.left) / rect.width, 0.25), 0.75));
    };
    const handleMouseUp = () => setDragging(false);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [dragging]);

  const updateMeta = <K extends keyof PostMeta>(key: K, value: PostMeta[K]) => {
    setMeta(prev => ({ ...prev, [key]: value }));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 'calc(100vh - 112px)' }}>
      {/* 顶部操作栏 */}
      <Card styles={{ body: { padding: '12px 24px' } }} style={{ marginBottom: 16, borderRadius: 16, boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回</Button>
          <Input
            placeholder="输入文章标题..."
            value={title}
            onChange={e => handleTitleChange(e.target.value)}
            variant="borderless"
            style={{ fontSize: 20, fontWeight: 600, flex: 1 }}
          />
          <Space>
            <Tooltip
              title={modeLocked ? "已有内容时不可切换模式，避免格式丢失" : undefined}
            >
              <Segmented
                value={mode}
                onChange={(val) => setMode(val as 'split' | 'wysiwyg')}
                disabled={modeLocked}
                options={[
                  { value: 'split', label: <><EditOutlined /> Markdown</> },
                  { value: 'wysiwyg', label: <><EyeOutlined /> 富文本</> },
                ]}
                size="small"
              />
            </Tooltip>
            <Button icon={<SaveOutlined />} onClick={() => openMetaModal('draft')}>保存草稿</Button>
            <Button type="primary" icon={<SendOutlined />} onClick={() => openMetaModal('published')}>发布</Button>
          </Space>
        </div>
      </Card>

      {/* 编辑器主体 */}
      {mode === 'split' ? (
        <div
          ref={splitContainerRef}
          style={{ display: 'flex', gap: 0, flex: 1, minHeight: 400, position: 'relative' }}>
          <Card
            title="Markdown 编辑器"
            style={{ width: `${leftRatio * 100}%`, borderRadius: 16, minWidth: 0, overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}
            styles={{ body: { padding: 0, height: 'calc(100% - 57px)' } }}
          >
            <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <TextArea
                id="post-editor-textarea"
                placeholder="在此输入 Markdown 内容..."
                value={body}
                onChange={e => setBody(e.target.value)}
                onPaste={handlePaste}
                style={{ flex: 1, border: 'none', resize: 'none', padding: 16, fontSize: 14 }}
              />
              <div style={{ padding: '4px 16px', borderTop: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  style={{ display: 'none' }}
                  onChange={handleFileSelect}
                />
                <Button
                  type="link"
                  size="small"
                  icon={uploading ? <LoadingOutlined /> : <PictureOutlined />}
                  onClick={handleImageButtonClick}
                  disabled={uploading}
                  style={{ color: '#999' }}
                >
                  {uploading ? '上传中...' : '插入图片'}
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

          {/* 拖拽分隔条 */}
          <div
            onMouseDown={handleMouseDown}
            style={{
              width: 6,
              cursor: "col-resize",
              background: dragging ? "#0071e3" : "transparent",
              flexShrink: 0,
              transition: dragging ? "none" : "background 0.2s",
              borderRadius: 3,
              margin: "0 5px",
            }}
            onMouseEnter={e => { if (!dragging) e.currentTarget.style.background = "#e5e5ea"; }}
            onMouseLeave={e => { if (!dragging) e.currentTarget.style.background = "transparent"; }}
          />

          <Card title="预览" style={{ width: `${(1 - leftRatio) * 100}%`, borderRadius: 16, minWidth: 0, overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }} styles={{ body: { padding: 16, height: 'calc(100% - 57px)', overflow: 'auto' } }}>
            {body ? (
              <MarkdownRenderer content={body} imageBaseUrl={imageBaseUrl} />
            ) : (
              <div style={{ color: '#ccc', textAlign: 'center', marginTop: 40 }}>开始在左侧编写，实时预览</div>
            )}
          </Card>
        </div>
      ) : (
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderRadius: 8, overflow: 'hidden', background: '#fff' }}>
          <div className="bn-editor-wysiwyg" style={{ flex: 1, minHeight: 0 }}>
            <BlockNoteViewRaw
              editor={bnEditor}
              theme="light"
              onChange={() => { if (!bnHasContent) setBnHasContent(true); }}
            />
          </div>
          <div style={{ padding: '4px 16px', borderTop: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={handleFileSelect}
            />
            <Button
              type="link"
              size="small"
              icon={uploading ? <LoadingOutlined /> : <PictureOutlined />}
              onClick={handleImageButtonClick}
              disabled={uploading}
              style={{ color: '#999' }}
            >
              {uploading ? '上传中...' : '插入图片'}
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
      )}      {/* 文章设置弹窗 */}
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

          <div>
            <Text type="secondary" style={{ fontSize: 13, marginBottom: 4, display: 'block' }}>封面图</Text>
            <input
              ref={coverFileInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={handleCoverSelect}
            />
            <div style={{ display: 'flex', alignItems: 'start', gap: 12 }}>
            {meta.featuredImage ? (
              <div style={{ position: 'relative', display: 'inline-block', borderRadius: 6, overflow: 'hidden' }}>
                <Image
                  src={getImageUrl(meta.featuredImage)}
                  alt="封面图"
                  style={{ maxWidth: 240, maxHeight: 135, objectFit: 'cover', borderRadius: 6, cursor: 'pointer' }}
                  preview={{ mask: null }}
                  onClick={() => coverFileInputRef.current?.click()}
                />
                <Button
                  type="text"
                  size="small"
                  icon={<DeleteOutlined />}
                  danger
                  style={{ position: 'absolute', top: 4, right: 4, background: 'rgba(0,0,0,0.45)', color: '#fff', border: 'none' }}
                  onClick={(e) => { e.stopPropagation(); handleCoverRemove(); }}
                />
              </div>
            ) : (
              <Button
                icon={coverUploading ? <LoadingOutlined /> : <PlusOutlined />}
                onClick={() => coverFileInputRef.current?.click()}
                loading={coverUploading}
                style={{ height: 80, width: 160, borderRadius: 6, border: '1px dashed #d9d9d9' }}
              >
                {coverUploading ? '上传中...' : '上传封面'}
              </Button>
            )}
              <div
                onPaste={handleCoverPaste}
                tabIndex={0}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  height: meta.featuredImage ? 135 : 80,
                  width: 120,
                  border: '1px dashed #d9d9d9',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 12,
                  color: '#999',
                  outline: 'none',
                  transition: 'border-color 0.2s',
                }}
                onFocus={e => e.currentTarget.style.borderColor = 'var(--color-primary)'}
                onBlur={e => e.currentTarget.style.borderColor = '#d9d9d9'}
              >
                点击此处后<br />Ctrl+V 粘贴
              </div>
            </div>
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
