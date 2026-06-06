import { useState, useEffect } from 'react';
import { message } from 'antd';
import { FilePdfOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { getPdfFiles, deletePdfFile, updatePdfFile } from '../../services/api';
import type { PdfFile } from '../../types';

interface Props {
  folderId: number | null;
  selectedFile: PdfFile | null;
  onSelectFile: (file: PdfFile) => void;
  refreshKey: number;
}

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(0) + ' MB';
}

const PdfFileList: React.FC<Props> = ({ folderId, selectedFile, onSelectFile, refreshKey }) => {
  const [files, setFiles] = useState<PdfFile[]>([]);

  useEffect(() => {
    getPdfFiles(folderId).then(res => setFiles(res.data)).catch(() => {});
  }, [folderId, refreshKey]);

  const handleDelete = async (e: React.MouseEvent, file: PdfFile) => {
    e.stopPropagation();
    try { await deletePdfFile(file.id); setFiles(f => f.filter(x => x.id !== file.id));
      message.success('已删除'); } catch { message.error('删除失败'); }
  };

  const handleRename = async (file: PdfFile) => {
    const name = prompt('新名称', file.originalName);
    if (name && name !== file.originalName) {
      try { await updatePdfFile(file.id, name, undefined); setFiles(f =>
        f.map(x => x.id === file.id ? { ...x, originalName: name } : x)); } catch { message.error('重命名失败'); }
    }
  };

  return (
    <div style={{ padding: '0 4px' }}>
      {files.length === 0 && (
        <div style={{ padding: 16, color: 'var(--color-text-tertiary)', fontSize: 12, textAlign: 'center' }}>
          暂无 PDF 文件
        </div>
      )}
      {files.map(f => (
        <div key={f.id}
          onClick={() => onSelectFile(f)}
          style={{
            display: 'flex', alignItems: 'center', padding: '8px 12px', cursor: 'pointer', borderRadius: 4, gap: 8,
            background: selectedFile?.id === f.id ? 'var(--color-primary-bg, #e6f7ff)' : 'transparent',
          }}
        >
          <FilePdfOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, color: 'var(--color-text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {f.originalName}
            </div>
            <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}>
              {formatSize(f.fileSize)} · {f.totalPages || '?'} 页
            </div>
          </div>
          <EditOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)', cursor: 'pointer' }}
            onClick={(e) => { e.stopPropagation(); handleRename(f); }} />
          <DeleteOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)', cursor: 'pointer' }}
            onClick={(e) => handleDelete(e, f)} />
        </div>
      ))}
    </div>
  );
};

export default PdfFileList;
