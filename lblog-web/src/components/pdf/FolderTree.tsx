import { useState, useEffect } from 'react';
import { Input, Modal, message } from 'antd';
import { FolderOutlined, FolderOpenOutlined, DeleteOutlined, EditOutlined, PlusOutlined, FilePdfOutlined } from '@ant-design/icons';
import { getPdfFolders, createPdfFolder, updatePdfFolder, deletePdfFolder, getPdfFiles, deletePdfFile, updatePdfFile } from '../../services/api';
import type { PdfFolder, PdfFile } from '../../types';

interface Props {
  selectedFile: PdfFile | null;
  onSelectFile: (file: PdfFile) => void;
  refreshKey: number;
}

const FolderTree: React.FC<Props> = ({ selectedFile, onSelectFile, refreshKey }) => {
  const [folders, setFolders] = useState<PdfFolder[]>([]);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [folderFiles, setFolderFiles] = useState<Record<number, PdfFile[]>>({});

  const fetchFolders = () => {
    getPdfFolders().then(res => setFolders(res.data)).catch(() => {});
  };
  useEffect(() => {
    fetchFolders();
    setFolderFiles({});
    // Re-fetch files for currently expanded folder
    if (expandedId !== null) {
      getPdfFiles(expandedId).then(res => {
        setFolderFiles(prev => ({ ...prev, [expandedId]: res.data }));
      }).catch(() => {});
    }
  }, [refreshKey]);

  const toggleFolder = (folderId: number) => {
    if (expandedId === folderId) {
      setExpandedId(null);
    } else {
      setExpandedId(folderId);
      // Fetch files for this folder
      getPdfFiles(folderId).then(res => {
        setFolderFiles(prev => ({ ...prev, [folderId]: res.data }));
      }).catch(() => {});
    }
  };

  const handleCreate = () => {
    Modal.confirm({
      title: '新建文件夹',
      content: <Input placeholder="文件夹名" id="folder-input" />,
      onOk: async () => {
        const input = document.getElementById('folder-input') as HTMLInputElement;
        const name = input?.value?.trim();
        if (!name) { message.warning('请输入文件夹名'); return; }
        try { await createPdfFolder(name); fetchFolders(); } catch { message.error('创建失败'); }
      },
    });
  };

  const handleRename = (id: number, name: string) => {
    let newName = name;
    Modal.confirm({
      title: '重命名文件夹',
      content: <Input defaultValue={name} onChange={e => newName = e.target.value} />,
      onOk: async () => {
        try { await updatePdfFolder(id, newName); fetchFolders(); } catch { message.error('重命名失败'); }
      },
    });
  };

  const handleDelete = async (e: React.MouseEvent, folderId: number, name: string) => {
    e.stopPropagation();
    Modal.confirm({
      title: `删除文件夹 "${name}"？`,
      content: '文件夹内的所有 PDF 也会被删除',
      onOk: async () => {
        try { await deletePdfFolder(folderId); fetchFolders(); setExpandedId(null); }
        catch { message.error('删除失败'); }
      },
    });
  };

  const handleDeleteFile = async (e: React.MouseEvent, fileId: number) => {
    e.stopPropagation();
    try { await deletePdfFile(fileId); fetchFolders(); setExpandedId(null); }
    catch { message.error('删除失败'); }
  };

  const handleRenameFile = async (e: React.MouseEvent, file: PdfFile) => {
    e.stopPropagation();
    const name = prompt('新名称', file.originalName);
    if (name && name !== file.originalName) {
      try { await updatePdfFile(file.id, name, undefined); fetchFolders(); setExpandedId(null); }
      catch { message.error('重命名失败'); }
    }
  };

  function formatSize(bytes: number): string {
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(0) + ' MB';
  }

  return (
    <div style={{ padding: '4px 0' }}>
      <div style={{ display: 'flex', alignItems: 'center', padding: '4px 8px' }}>
        <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)', fontWeight: 500 }}>
          文件夹
        </span>
        <span style={{ flex: 1 }} />
        <PlusOutlined style={{ fontSize: 12, color: 'var(--color-text-secondary)', cursor: 'pointer' }}
          onClick={handleCreate} title="新建文件夹" />
      </div>

      {folders.length === 0 && (
        <div style={{ padding: 12, textAlign: 'center', color: 'var(--color-text-tertiary)', fontSize: 12 }}>
          暂无文件夹，点击右上角 + 创建
        </div>
      )}

      {folders.map(f => {
        const isExpanded = expandedId === f.id;
        const files = folderFiles[f.id] || [];

        return (
          <div key={f.id}>
            <div onClick={() => toggleFolder(f.id)} style={{
              display: 'flex', alignItems: 'center', padding: '6px 8px', cursor: 'pointer',
              borderRadius: 4, gap: 4, fontSize: 13,
              background: isExpanded ? 'var(--color-fill-tertiary)' : 'transparent',
              color: 'var(--color-text)',
            }}>
              <span style={{ flex: 1 }}>
                {isExpanded ? <FolderOpenOutlined /> : <FolderOutlined />} {f.name}
              </span>
              <EditOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}
                onClick={e => { e.stopPropagation(); handleRename(f.id, f.name); }} />
              <DeleteOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}
                onClick={e => handleDelete(e, f.id, f.name)} />
            </div>

            {isExpanded && (
              <div style={{ paddingLeft: 8 }}>
                {files.length === 0 ? (
                  <div style={{ padding: '8px 12px', color: 'var(--color-text-tertiary)', fontSize: 11 }}>
                    暂无 PDF
                  </div>
                ) : (
                  files.map(pf => (
                    <div key={pf.id} onClick={() => onSelectFile(pf)} style={{
                      display: 'flex', alignItems: 'center', padding: '6px 12px', cursor: 'pointer',
                      borderRadius: 4, gap: 8, fontSize: 13,
                      background: selectedFile?.id === pf.id ? 'var(--color-primary-bg, #e6f7ff)' : 'transparent',
                      color: 'var(--color-text)',
                    }}>
                      <FilePdfOutlined style={{ color: '#ff4d4f', fontSize: 14, flexShrink: 0 }} />
                      <div style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {pf.originalName}
                      </div>
                      <span style={{ fontSize: 10, color: 'var(--color-text-tertiary)', flexShrink: 0 }}>
                        {formatSize(pf.fileSize)}
                      </span>
                      <EditOutlined style={{ fontSize: 10, color: 'var(--color-text-tertiary)', flexShrink: 0 }}
                        onClick={e => handleRenameFile(e, pf)} />
                      <DeleteOutlined style={{ fontSize: 10, color: 'var(--color-text-tertiary)', flexShrink: 0 }}
                        onClick={e => handleDeleteFile(e, pf.id)} />
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default FolderTree;
