import { useState } from 'react';
import { Tabs, Button } from 'antd';
import { FolderOutlined, BookOutlined, UploadOutlined, SaveOutlined, MenuFoldOutlined } from '@ant-design/icons';
import type { PdfFile } from '../../types';
import FolderTree from './FolderTree';
import PdfFileList from './PdfFileList';
import BookmarkPanel from './BookmarkPanel';

interface Props {
  selectedFile: PdfFile | null;
  onSelectFile: (file: PdfFile) => void;
  onUploadClick: () => void;
  onSaveAnnotations?: () => void;
  currentPage: number;
  onJumpToPage: (page: number) => void;
  onCollapse?: () => void;
  refreshKey: number;
}

const PdfSidebar: React.FC<Props> = ({ selectedFile, onSelectFile, onUploadClick, onSaveAnnotations, currentPage, onJumpToPage, onCollapse, refreshKey }) => {
  const [activeTab, setActiveTab] = useState<string>('files');
  const [currentFolder, setCurrentFolder] = useState<number | null>(null);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '8px 12px', display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button type="primary" size="small" icon={<UploadOutlined />} onClick={onUploadClick}>上传</Button>
        <Button size="small" icon={<SaveOutlined />} onClick={onSaveAnnotations}>保存</Button>
        <span style={{ flex: 1 }} />
        <Button type="text" size="small" icon={<MenuFoldOutlined />} onClick={onCollapse}
          style={{ color: 'var(--color-text-secondary)' }} />
      </div>
      <Tabs activeKey={activeTab} onChange={setActiveTab} size="small"
        style={{ flex: 1, overflow: 'auto' }}
        items={[
          {
            key: 'files',
            label: <span><FolderOutlined /> 书架</span>,
            children: (
              <div>
                <FolderTree currentFolder={currentFolder} onSelectFolder={setCurrentFolder} refreshKey={refreshKey} />
                <PdfFileList folderId={currentFolder} selectedFile={selectedFile}
                  onSelectFile={onSelectFile} refreshKey={refreshKey} />
              </div>
            ),
          },
          {
            key: 'bookmarks',
            label: <span><BookOutlined /> 书签</span>,
            children: selectedFile ? (
              <BookmarkPanel pdfId={selectedFile.id} currentPage={currentPage} onJumpToPage={onJumpToPage} />
            ) : (
              <div style={{ padding: 16, color: 'var(--color-text-tertiary)', fontSize: 13 }}>请先选择 PDF</div>
            ),
          },
        ]}
      />
    </div>
  );
};

export default PdfSidebar;
