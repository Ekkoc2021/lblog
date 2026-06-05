import { useState, useEffect, useRef, useCallback } from 'react';
import { List, Input, Button, message, Pagination, theme, Space, Popconfirm } from 'antd';
import { PlusOutlined, CloseOutlined, KeyOutlined, SearchOutlined, CopyOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { getPasswords, deletePassword } from '../../services/passwordApi';
import PasswordModal from './PasswordModal';
import ViewPasswordModal from './ViewPasswordModal';
import type { PasswordEntry } from '../../types';

interface Props {
  onClose: () => void;
}

const PAGE_SIZE = 20;

const HashbookPanel: React.FC<Props> = ({ onClose }) => {
  const { token } = theme.useToken();
  const [entries, setEntries] = useState<PasswordEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<PasswordEntry | null>(null);
  const [viewingEntry, setViewingEntry] = useState<PasswordEntry | null>(null);

  const [pos, setPos] = useState(() => {
    try {
      const saved = localStorage.getItem('hashbookPanelPos');
      if (saved) { const p = JSON.parse(saved); if (p && typeof p.left === 'number') return p; }
    } catch { /* ignore */ }
    return { left: Math.round((window.innerWidth - 400) / 2) + 60, top: Math.round(window.innerHeight / 5) + 40 };
  });
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0, left: 0, top: 0 });

  const fetchData = useCallback(async (p: number, kw: string) => {
    setLoading(true);
    try {
      const res = await getPasswords({ page: p, pageSize: PAGE_SIZE, keyword: kw || undefined });
      setEntries(res.data!.list);
      setTotal(res.data!.total);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
      setEntries([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(page, keyword); }, [page, keyword, fetchData]);

  const handleSearch = (value: string) => {
    setKeyword(value);
    setPage(1);
  };

  const handleDelete = async (id: number) => {
    try {
      await deletePassword(id);
      message.success('已删除');
      fetchData(page, keyword);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleSaveSuccess = () => {
    setModalOpen(false);
    setEditingEntry(null);
    fetchData(page, keyword);
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => message.success(`${label}已复制`));
  };

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    dragging.current = true;
    dragStart.current = { x: e.clientX, y: e.clientY, left: pos.left, top: pos.top };
    let lastClientX = e.clientX;
    let lastClientY = e.clientY;
    const onMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      lastClientX = ev.clientX;
      lastClientY = ev.clientY;
      setPos({
        left: Math.max(0, Math.min(window.innerWidth - 400, dragStart.current.left + ev.clientX - dragStart.current.x)),
        top: Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + ev.clientY - dragStart.current.y)),
      });
    };
    const onUp = () => {
      dragging.current = false;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      const newLeft = Math.max(0, Math.min(window.innerWidth - 400, dragStart.current.left + lastClientX - dragStart.current.x));
      const newTop = Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + lastClientY - dragStart.current.y));
      try { localStorage.setItem('hashbookPanelPos', JSON.stringify({ left: newLeft, top: newTop })); } catch { /* ignore */ }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [pos]);

  return (
    <div style={{
      position: 'fixed',
      left: pos.left,
      top: pos.top,
      width: 400,
      maxHeight: '75vh',
      zIndex: 1000,
      background: token.colorBgElevated,
      borderRadius: 12,
      boxShadow: token.boxShadowSecondary,
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Header — draggable */}
      <div onMouseDown={handleMouseDown} style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '10px 16px', cursor: 'grab', userSelect: 'none',
        borderBottom: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0,
      }}>
        <span style={{ fontWeight: 600, fontSize: 15, color: token.colorText }}>
          <KeyOutlined style={{ marginRight: 8 }} />
          密码管家
          <span style={{ marginLeft: 8, fontSize: 12, color: token.colorTextTertiary, fontWeight: 400 }}>
            {total}
          </span>
        </span>
        <CloseOutlined style={{ cursor: 'pointer', color: token.colorTextTertiary }} onClick={onClose} />
      </div>

      {/* Search + Add bar */}
      <div style={{ padding: '8px 16px', flexShrink: 0, display: 'flex', gap: 8 }}>
        <Input
          prefix={<SearchOutlined />}
          placeholder="搜索站点或账号..."
          allowClear
          onPressEnter={(e) => handleSearch((e.target as HTMLInputElement).value)}
          onClear={() => handleSearch('')}
          style={{ flex: 1 }}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingEntry(null); setModalOpen(true); }}>新增</Button>
      </div>
      <div style={{ fontSize: 11, color: token.colorTextTertiary, padding: '0 16px 4px', flexShrink: 0 }}>
        密文不上传后端，请自行妥善保管
      </div>

      {/* List */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0 16px' }}>
        <List
          loading={loading}
          dataSource={entries}
          locale={{ emptyText: '暂无记录' }}
          renderItem={(item) => (
            <List.Item
              style={{ padding: '10px 0', cursor: 'pointer' }}
              onClick={() => setViewingEntry(item)}
              actions={[
                <Button key="edit" type="link" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); setEditingEntry(item); setModalOpen(true); }} />,
                <Popconfirm key="del" title="确定删除？" onConfirm={(e) => { e?.stopPropagation(); handleDelete(item.id); }} onCancel={(e) => e?.stopPropagation()}>
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space size={4}>
                    <span style={{ fontWeight: 500 }}>{item.siteName}</span>
                    <span style={{ fontSize: 12, color: token.colorTextTertiary }}>
                      {item.username}
                    </span>
                  </Space>
                }
                description={
                  <Space size={8} style={{ fontSize: 12 }}>
                    {item.siteUrl && <a href={item.siteUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()} style={{ color: token.colorPrimary }}>{item.siteUrl}</a>}
                    {!item.siteUrl && <span style={{ color: token.colorTextTertiary }}>—</span>}
                    <CopyOutlined style={{ cursor: 'pointer', color: token.colorTextTertiary }} onClick={(e) => { e.stopPropagation(); copyToClipboard(item.username, '账号'); }} />
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </div>

      {/* Pagination */}
      {total > PAGE_SIZE && (
        <div style={{ padding: '8px 16px', borderTop: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0, textAlign: 'center' }}>
          <Pagination
            size="small"
            current={page}
            pageSize={PAGE_SIZE}
            total={total}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      )}

      {/* Create/Edit Modal */}
      <PasswordModal
        open={modalOpen}
        entry={editingEntry}
        onClose={() => { setModalOpen(false); setEditingEntry(null); }}
        onSuccess={handleSaveSuccess}
      />

      {/* View Modal */}
      {viewingEntry && (
        <ViewPasswordModal
          entry={viewingEntry}
          onClose={() => setViewingEntry(null)}
        />
      )}
    </div>
  );
};

export default HashbookPanel;
