import { useState, useEffect } from 'react';
import { Button, Input, Modal, message, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined, BookOutlined } from '@ant-design/icons';
import { getPdfBookmarks, addPdfBookmark, deletePdfBookmark } from '../../services/api';
import type { PdfBookmark } from '../../types';

interface Props {
  pdfId: number;
  currentPage: number;
  onJumpToPage: (page: number) => void;
}

const BookmarkPanel: React.FC<Props> = ({ pdfId, currentPage, onJumpToPage }) => {
  const [bookmarks, setBookmarks] = useState<PdfBookmark[]>([]);

  const fetchBookmarks = () => {
    getPdfBookmarks(pdfId).then(res => setBookmarks(res.data)).catch(() => {});
  };
  useEffect(() => { fetchBookmarks(); }, [pdfId]);

  const handleAdd = () => {
    Modal.confirm({
      title: '添加书签',
      content: (
        <Input placeholder="书签名称" id="bookmark-input" />
      ),
      onOk: async () => {
        const input = document.getElementById('bookmark-input') as HTMLInputElement;
        const label = input?.value || '';
        if (!label.trim()) { message.warning('请输入书签名称'); return; }
        try { await addPdfBookmark(pdfId, currentPage, label); fetchBookmarks(); message.success('已添加'); }
        catch { message.error('添加失败'); }
      },
    });
  };

  const handleDelete = async (id: number) => {
    try { await deletePdfBookmark(pdfId, id); fetchBookmarks(); } catch { message.error('删除失败'); }
  };

  return (
    <div style={{ padding: 8 }}>
      <Button type="dashed" size="small" icon={<PlusOutlined />} block onClick={handleAdd}>
        添加当前页书签
      </Button>
      {bookmarks.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无书签"
        style={{ marginTop: 24 }} />}
      {bookmarks.map(b => (
        <div key={b.id} onClick={() => onJumpToPage(b.pageNum)}
          style={{ display: 'flex', alignItems: 'center', padding: '8px 12px', cursor: 'pointer',
            borderRadius: 4, gap: 8, marginTop: 4, background: 'var(--color-bg)' }}
        >
          <BookOutlined style={{ color: '#fa8c16', fontSize: 13 }} />
          <span style={{ flex: 1, fontSize: 13, color: 'var(--color-text)' }}>{b.label}</span>
          <span style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>p{b.pageNum}</span>
          <DeleteOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)', cursor: 'pointer' }}
            onClick={(e) => { e.stopPropagation(); handleDelete(b.id); }} />
        </div>
      ))}
    </div>
  );
};

export default BookmarkPanel;
