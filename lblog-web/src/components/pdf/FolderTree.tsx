import { useState, useEffect } from 'react';
import { Input, Modal, message } from 'antd';
import { FolderOutlined, FolderOpenOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { getPdfFolders, createPdfFolder, updatePdfFolder, deletePdfFolder } from '../../services/api';
import type { PdfFolder } from '../../types';

interface Props {
  currentFolder: number | null;
  onSelectFolder: (id: number | null) => void;
  refreshKey: number;
}

interface FolderNodeProps {
  folder: PdfFolder;
  currentFolder: number | null;
  onSelectFolder: (id: number | null) => void;
  onRefresh: () => void;
}

const FolderNode: React.FC<FolderNodeProps> = ({ folder, currentFolder, onSelectFolder, onRefresh }) => {
  const [expanded, setExpanded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(folder.name);
  const isActive = currentFolder === folder.id;

  const handleEdit = () => {
    Modal.confirm({
      title: '重命名文件夹',
      content: <Input defaultValue={folder.name} onChange={e => setName(e.target.value)} />,
      onOk: async () => {
        try { await updatePdfFolder(folder.id, name); onRefresh(); } catch { message.error('重命名失败'); }
      },
    });
  };

  const handleDelete = () => {
    Modal.confirm({
      title: `删除文件夹 "${folder.name}"？`,
      content: '文件夹内的文件将移至根目录',
      onOk: async () => {
        try { await deletePdfFolder(folder.id); onRefresh(); } catch { message.error('删除失败'); }
      },
    });
  };

  const handleAddChild = () => {
    Modal.confirm({
      title: '新建子文件夹',
      content: <Input placeholder="文件夹名" onChange={e => setName(e.target.value)} />,
      onOk: async () => {
        try { await createPdfFolder(name, folder.id); onRefresh(); } catch { message.error('创建失败'); }
      },
    });
  };

  return (
    <div>
      <div style={{
        display: 'flex', alignItems: 'center', padding: '4px 8px', cursor: 'pointer',
        background: isActive ? 'var(--color-primary-bg, #e6f7ff)' : 'transparent',
        borderRadius: 4, gap: 4
      }}>
        <span onClick={() => { setExpanded(!expanded); onSelectFolder(isActive ? null : folder.id); }}
          style={{ flex: 1, fontSize: 13, color: 'var(--color-text)' }}>
          {expanded ? <FolderOpenOutlined /> : <FolderOutlined />} {folder.name}
        </span>
        <PlusOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }} onClick={handleAddChild} />
        <EditOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }} onClick={handleEdit} />
        <DeleteOutlined style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }} onClick={handleDelete} />
      </div>
      {expanded && folder.children?.map(child => (
        <div key={child.id} style={{ paddingLeft: 16 }}>
          <FolderNode folder={child} currentFolder={currentFolder} onSelectFolder={onSelectFolder} onRefresh={onRefresh} />
        </div>
      ))}
    </div>
  );
};

const FolderTree: React.FC<Props> = ({ currentFolder, onSelectFolder, refreshKey }) => {
  const [folders, setFolders] = useState<PdfFolder[]>([]);

  const fetchFolders = () => {
    getPdfFolders().then(res => setFolders(res.data)).catch(() => {});
  };

  useEffect(() => { fetchFolders(); }, [refreshKey]);

  const handleCreateRoot = () => {
    Modal.confirm({
      title: '新建文件夹',
      content: <Input placeholder="文件夹名" id="root-folder-input" />,
      onOk: async () => {
        const input = document.getElementById('root-folder-input') as HTMLInputElement;
        const name = input?.value?.trim();
        if (!name) { message.warning('请输入文件夹名'); return; }
        try { await createPdfFolder(name); fetchFolders(); } catch { message.error('创建失败'); }
      },
    });
  };

  return (
    <div style={{ padding: '4px 0' }}>
      <div style={{ display: 'flex', alignItems: 'center', padding: '4px 8px' }}>
        <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)', fontWeight: 500 }}>
          文件夹
        </span>
        <span style={{ flex: 1 }} />
        <PlusOutlined style={{ fontSize: 12, color: 'var(--color-text-secondary)', cursor: 'pointer' }}
          onClick={handleCreateRoot} title="新建文件夹" />
      </div>
      <div onClick={() => onSelectFolder(null)} style={{ padding: '4px 8px', cursor: 'pointer',
        background: currentFolder === null ? 'var(--color-primary-bg, #e6f7ff)' : 'transparent',
        borderRadius: 4, fontSize: 13, color: 'var(--color-text)' }}>
        📁 根目录
      </div>
      {folders.map(f => <FolderNode key={f.id} folder={f} currentFolder={currentFolder}
        onSelectFolder={onSelectFolder} onRefresh={fetchFolders} />)}
    </div>
  );
};

export default FolderTree;
