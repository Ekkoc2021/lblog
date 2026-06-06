import { useState, useEffect } from 'react';
import { Tabs, Button, Progress } from 'antd';
import { FolderOutlined, BookOutlined, UploadOutlined, SaveOutlined } from '@ant-design/icons';
import type { PdfFile, PdfBookmark, PdfUserStats } from '../../types';
import { getPdfStats } from '../../services/api';
import FolderTree from './FolderTree';
import BookmarkPanel from './BookmarkPanel';

interface Props {
  selectedFile: PdfFile | null;
  onSelectFile: (file: PdfFile) => void;
  onUploadClick: () => void;
  onSaveAnnotations?: () => void;
  currentPage: number;
  onJumpToPage: (page: number) => void;
  onEditNote?: (bm: PdfBookmark) => void;
  refreshKey: number;
}

function formatBytes(bytes: number): string {
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(0)} KB`;
  if (bytes < 1073741824) return `${(bytes / 1048576).toFixed(1)} MB`;
  return `${(bytes / 1073741824).toFixed(2)} GB`;
}

const PdfSidebar: React.FC<Props> = ({ selectedFile, onSelectFile, onUploadClick, onSaveAnnotations, currentPage, onJumpToPage, onEditNote, refreshKey }) => {
  const [activeTab, setActiveTab] = useState<string>('files');
  const [stats, setStats] = useState<PdfUserStats | null>(null);

  useEffect(() => {
    getPdfStats().then(res => setStats(res.data)).catch(() => {});
  }, [refreshKey]);

  const usagePct = stats && stats.quotaBytes > 0 ? Math.round(stats.totalSize / stats.quotaBytes * 100) : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '8px 12px', display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button type="primary" size="small" icon={<UploadOutlined />} onClick={onUploadClick}>上传</Button>
        <Button size="small" icon={<SaveOutlined />} onClick={onSaveAnnotations}>保存</Button>
      </div>
      <Tabs activeKey={activeTab} onChange={setActiveTab} size="small"
        tabBarStyle={{ paddingLeft: 12 }}
        style={{ flex: 1, overflow: 'auto' }}
        items={[
          {
            key: 'files',
            label: <span><FolderOutlined /> 书架</span>,
            children: (
              <FolderTree selectedFile={selectedFile} onSelectFile={onSelectFile} refreshKey={refreshKey} />
            ),
          },
          {
            key: 'bookmarks',
            label: <span><BookOutlined /> 书签</span>,
            children: selectedFile ? (
              <BookmarkPanel pdfId={selectedFile.id} currentPage={currentPage} onJumpToPage={onJumpToPage} onEditNote={onEditNote} refreshKey={refreshKey} />
            ) : (
              <div style={{ padding: 16, color: 'var(--color-text-tertiary)', fontSize: 13 }}>请先选择 PDF</div>
            ),
          },
        ]}
      />
      {stats && (
        <div style={{
          padding: '8px 12px', borderTop: '1px solid var(--color-border, #e8e8e8)',
          background: 'var(--color-bg)', flexShrink: 0
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, marginBottom: 3 }}>
            <span style={{ color: 'var(--color-text-tertiary)' }}>已用 {formatBytes(stats.totalSize)}</span>
            <span style={{ color: 'var(--color-text-tertiary)' }}>{usagePct}%</span>
          </div>
          <Progress percent={usagePct} size="small" showInfo={false}
            strokeColor={usagePct > 80 ? '#ff4d4f' : usagePct > 60 ? '#faad14' : '#1677ff'}
            trailColor="var(--color-bg-tag)" />
        </div>
      )}
    </div>
  );
};

export default PdfSidebar;
