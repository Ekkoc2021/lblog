import { useState, useEffect } from 'react';
import { Button, Input, Modal, message, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined, BookOutlined } from '@ant-design/icons';
import { getPdfBookmarks, addPdfBookmark, deletePdfBookmark } from '../../services/api';
import type { PdfBookmark } from '../../types';

interface Props {
  pdfId: number;
  currentPage: number;
  onJumpToPage: (page: number) => void;
  onEditNote?: (bm: PdfBookmark) => void;
  refreshKey?: number;
}

const BookmarkPanel: React.FC<Props> = ({ pdfId, currentPage, onJumpToPage, onEditNote, refreshKey }) => {
  const [bookmarks, setBookmarks] = useState<PdfBookmark[]>([]);

  const fetchBookmarks = () => {
    getPdfBookmarks(pdfId).then(res => setBookmarks(res.data)).catch(() => {});
  };
  useEffect(() => { fetchBookmarks(); }, [pdfId, refreshKey]);

  const handleAdd = () => {
    Modal.confirm({
      title: '添加书签',
      content: <Input placeholder="书签名称" id="bm-name-input" />,
      onOk: async () => {
        const label = (document.getElementById('bm-name-input') as HTMLInputElement)?.value?.trim();
        if (!label) { message.warning('请输入书签名称'); return; }
        try { await addPdfBookmark(pdfId, currentPage, label); fetchBookmarks(); }
        catch { message.error('添加失败'); }
      },
    });
  };

  const handleDelete = (e: React.MouseEvent, id: number) => {
    e.stopPropagation();
    Modal.confirm({
      title: '确认删除',
      content: '删除后不可恢复，确定要删除这个书签吗？',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try { await deletePdfBookmark(pdfId, id); fetchBookmarks(); }
        catch { message.error('删除失败'); }
      },
    });
  };

  return (
    <div style={{ padding: 8 }}>
      <Button type="dashed" size="small" icon={<PlusOutlined />} block onClick={handleAdd}>
        添加书签
      </Button>

      {bookmarks.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无书签" style={{ marginTop: 24 }} />
      )}

      {bookmarks.map(bm => (
        <div key={bm.id} style={{ marginTop: 6 }}>
          <div onClick={() => onJumpToPage(bm.pageNum)}
            style={{ display: 'flex', alignItems: 'center', padding: '6px 8px', cursor: 'pointer',
              borderRadius: 4, gap: 6, background: 'var(--color-bg)' }}>
            <BookOutlined style={{ color: '#fa8c16', fontSize: 13, flexShrink: 0 }} />
            <span style={{ flex: 1, fontSize: 13, color: 'var(--color-text)', fontWeight: 500,
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {bm.label}
            </span>
            <span style={{ fontSize: 10, color: 'var(--color-text-tertiary)', flexShrink: 0 }}>
              p{bm.pageNum}
            </span>
            <DeleteOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)', cursor: 'pointer', flexShrink: 0 }}
              onClick={e => handleDelete(e, bm.id)} />
          </div>
          <div onClick={() => onEditNote?.(bm)} style={{
            padding: '2px 8px 6px 28px', cursor: 'pointer', fontSize: 12,
            borderRadius: '0 0 4px 4px', lineHeight: '18px', minHeight: 22,
            borderTop: '1px solid var(--color-border, #f0f0f0)',
            color: bm.note ? 'var(--color-text-secondary)' : 'var(--color-text-tertiary)',
            fontStyle: bm.note ? 'normal' : 'italic',
            background: 'var(--color-bg)',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>
            {bm.note ? bm.note : '点击添加笔记...'}
          </div>
        </div>
      ))}
    </div>
  );
};

export default BookmarkPanel;
