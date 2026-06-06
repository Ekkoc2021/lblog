import { useState, useCallback, useRef } from 'react';
import { Layout, message, Input, Button, theme } from 'antd';
import { MinusOutlined, CloseOutlined, BookOutlined } from '@ant-design/icons';
import type { PdfFile, PdfBookmark } from '../types';
import { updatePdfBookmark } from '../services/api';
import PdfSidebar from '../components/pdf/PdfSidebar';
import PdfViewer, { type PdfViewerHandle } from '../components/pdf/PdfViewer';
import PdfUploadModal from '../components/pdf/PdfUploadModal';

const { TextArea } = Input;

const { Sider, Content } = Layout;

const PdfReaderPage: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const viewerRef = useRef<PdfViewerHandle>(null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [selectedFile, setSelectedFile] = useState<PdfFile | null>(null);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const { token: themeToken } = theme.useToken();

  // Floating note editor
  const [noteEditor, setNoteEditor] = useState<{bm: PdfBookmark; label: string; note: string} | null>(null);
  const [notePos, setNotePos] = useState({ x: 0, y: 0 });
  const draggingRef = useRef(false);
  const dragStartRef = useRef({ x: 0, y: 0, left: 0, top: 0 });

  const handleNoteDragStart = useCallback((e: React.MouseEvent) => {
    draggingRef.current = true;
    const el = (e.target as HTMLElement).closest('[data-note-editor]') as HTMLElement;
    const rect = el?.getBoundingClientRect();
    dragStartRef.current = {
      x: e.clientX, y: e.clientY,
      left: rect ? rect.left : (window.innerWidth - 620) / 2,
      top: rect ? rect.top : (window.innerHeight - 520) / 2,
    };
    document.body.style.userSelect = 'none';
    const move = (ev: MouseEvent) => {
      if (!draggingRef.current) return;
      setNotePos({
        x: dragStartRef.current.left + (ev.clientX - dragStartRef.current.x),
        y: dragStartRef.current.top + (ev.clientY - dragStartRef.current.y),
      });
    };
    const up = () => { draggingRef.current = false; document.body.style.userSelect = ''; document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); };
    document.addEventListener('mousemove', move);
    document.addEventListener('mouseup', up);
  }, []);

  const handleSelectFile = useCallback((file: PdfFile) => {
    setSelectedFile(file);
  }, []);

  const handleUploadSuccess = useCallback(() => {
    setUploadVisible(false);
    setRefreshKey(k => k + 1);
  }, []);

  const handleSave = useCallback(() => {
    viewerRef.current?.save();
  }, []);

  const handleSaveComplete = useCallback(() => {
    message.success('标注已保存');
  }, []);

  const handleEditNote = useCallback((bm: PdfBookmark) => {
    setNoteEditor({ bm, label: bm.label, note: bm.note || '' });
  }, []);

  const handleNoteSave = useCallback(async () => {
    if (!noteEditor || !selectedFile) return;
    try {
      await updatePdfBookmark(selectedFile.id, noteEditor.bm.id, noteEditor.label.trim(), noteEditor.note.trim() || undefined);
      setNoteEditor(null);
      setRefreshKey(k => k + 1);
    } catch { message.error('保存失败'); }
  }, [noteEditor, selectedFile]);

  const handleJumpToPage = useCallback((page: number) => {
    viewerRef.current?.jumpToPage(page);
  }, []);

  return (
    <Layout style={{ height: '100vh', background: 'var(--color-bg)' }}>
      {/* Top Bar */}
      <div style={{
        height: 48, display: 'flex', alignItems: 'center', padding: '0 16px',
        borderBottom: '1px solid var(--color-border, #e8e8e8)',
        background: 'var(--color-bg-card)', gap: 12, flexShrink: 0
      }}>
        <MinusOutlined style={{ cursor: 'pointer', fontSize: 16, color: 'var(--color-text-secondary)' }}
          onClick={onClose} />
        <span style={{ fontWeight: 600, color: 'var(--color-text)' }}>PDF 阅读</span>
        {selectedFile && (
          <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
            {selectedFile.originalName}
          </span>
        )}
        <span style={{ flex: 1 }} />
      </div>

      <Layout style={{ flex: 1, overflow: 'hidden', background: 'var(--color-bg)' }}>
        <Sider width={sidebarCollapsed ? 0 : 300} collapsedWidth={0} collapsed={sidebarCollapsed}
          style={{ background: 'var(--color-bg-card)', borderRight: '1px solid var(--color-border, #e8e8e8)', overflow: 'auto' }}>
          <PdfSidebar
            selectedFile={selectedFile}
            onSelectFile={handleSelectFile}
            onUploadClick={() => setUploadVisible(true)}
            onSaveAnnotations={handleSave}
            currentPage={currentPage}
            onJumpToPage={handleJumpToPage}
            onEditNote={handleEditNote}
            refreshKey={refreshKey}
          />
        </Sider>

        {/* 分割线——照抄 DrawPage：收起/展开 */}
        <div
          onClick={() => setSidebarCollapsed(v => !v)}
          onMouseEnter={e => { e.currentTarget.style.background = sidebarCollapsed ? '#d6e4ff' : '#e5e5ea' }}
          onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          style={{
            width: 14,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            alignSelf: 'stretch',
            transition: 'background 0.15s',
            position: 'relative',
            userSelect: 'none',
          }}
          title={sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'}
        >
          <div style={{
            width: 3,
            height: sidebarCollapsed ? 28 : 40,
            borderRadius: 2,
            background: sidebarCollapsed ? '#1677ff' : '#d9d9d9',
            transition: 'background 0.2s, height 0.2s',
          }} />
          {sidebarCollapsed && (
            <span style={{ position: 'absolute', color: '#1677ff', fontSize: 10 }}>▶</span>
          )}
        </div>

        <Content style={{ position: 'relative', overflow: 'hidden', flex: 1 }}>
          {selectedFile ? (
            <PdfViewer
              ref={viewerRef}
              file={selectedFile}
              onPageChange={setCurrentPage}
              onSaveComplete={handleSaveComplete}
            />
          ) : (
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%',
              color: 'var(--color-text-tertiary)', fontSize: 16
            }}>
              从左侧书架选择 PDF 开始阅读
            </div>
          )}
        </Content>
      </Layout>

      <PdfUploadModal
        open={uploadVisible}
        onClose={() => setUploadVisible(false)}
        onSuccess={handleUploadSuccess}
      />

      {/* Floating Note Editor */}
      {noteEditor && (
        <div data-note-editor style={{
          position: 'fixed', zIndex: 1000,
          width: 620, height: 520,
          left: notePos.x || '50%', top: notePos.y || '50%',
          transform: notePos.x ? undefined : 'translate(-50%, -50%)',
          background: 'var(--color-bg-card)', borderRadius: 12,
          boxShadow: '0 8px 40px rgba(0,0,0,0.25)', overflow: 'hidden',
          display: 'flex', flexDirection: 'column',
        }}>
          {/* Header — draggable */}
          <div onMouseDown={handleNoteDragStart}
            style={{ display: 'flex', alignItems: 'center', padding: '12px 16px',
              borderBottom: '1px solid var(--color-border, #e8e8e8)', gap: 8,
              cursor: 'grab', userSelect: 'none',
              background: 'var(--color-bg-card)' }}>
            <BookOutlined style={{ color: '#fa8c16', flexShrink: 0 }} />
            <Input
              value={noteEditor.label}
              onChange={e => setNoteEditor(prev => prev ? { ...prev, label: e.target.value } : null)}
              variant="borderless"
              style={{ fontWeight: 600, fontSize: 14, padding: 0, flex: 1, color: 'var(--color-text)' }}
            />
            <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)', flexShrink: 0 }}>
              p{noteEditor.bm.pageNum}
            </span>
            <Button type="primary" size="small" onClick={handleNoteSave}>保存</Button>
            <Button type="text" size="small" icon={<CloseOutlined />}
              onClick={() => setNoteEditor(null)} />
          </div>

          {/* Body */}
          <div style={{ flex: 1, overflow: 'auto', padding: '12px 16px',
            background: 'var(--color-bg-card)', display: 'flex', flexDirection: 'column' }}>
            <TextArea
              value={noteEditor.note}
              onChange={e => setNoteEditor(prev => prev ? { ...prev, note: e.target.value } : null)}
              placeholder="读书笔记..."
              style={{
                flex: 1,
                fontFamily: '"Ma Shan Zheng", "STKaiti", "KaiTi", serif',
                fontSize: 16, lineHeight: '28px', resize: 'none',
                backgroundColor: 'var(--color-bg-card)',
                backgroundImage: `repeating-linear-gradient(transparent, transparent 27px, ${themeToken.colorBorderSecondary} 27px, ${themeToken.colorBorderSecondary} 28px)`,
                backgroundPosition: '0 14px', backgroundAttachment: 'local',
                borderRadius: 8, border: `1px solid var(--color-border, #d9d9d9)`,
                padding: '14px 20px 14px 28px',
                color: 'var(--color-text)',
              }}
            />
          </div>
        </div>
      )}
    </Layout>
  );
};

export default PdfReaderPage;
