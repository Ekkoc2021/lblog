import { useState, useEffect, useRef, useCallback } from 'react';
import { Tabs, Button, Modal, Form, Input, Select, DatePicker, message, theme } from 'antd';
import { PlusOutlined, CloseOutlined, CheckSquareOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useTodos } from '../hooks/useTodos';
import TodoList from './TodoList';
import type { CreateTodoRequest } from '../types';

const PRIORITY_OPTIONS = [
  { value: 0, label: '低' },
  { value: 1, label: '中' },
  { value: 2, label: '高' },
];

interface Props {
  onClose: () => void;
}

const TodoPanel: React.FC<Props> = ({ onClose }) => {
  const { token } = theme.useToken();
  const { todos, loading, total, hasMore, tags, loadTodos, loadMore, loadTags, create, update, remove, addItem, updateItem, removeItem, reorder } = useTodos();
  const [pos, setPos] = useState(() => {
    try {
      const saved = localStorage.getItem('todoPanelPos');
      if (saved) { const p = JSON.parse(saved); if (p && typeof p.left === 'number') return p; }
    } catch { /* ignore */ }
    return { left: Math.round((window.innerWidth - 380) / 2), top: Math.round(window.innerHeight / 5) };
  });
  const [tab, setTab] = useState('active');
  const [formVisible, setFormVisible] = useState(false);
  const [form] = Form.useForm();
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0, left: 0, top: 0 });

  useEffect(() => {
    const s = tab === 'active' ? 0 : tab === 'done' ? 1 : undefined;
    loadTodos(s !== undefined ? { status: s } : {});
  }, [loadTodos, tab]);
  useEffect(() => { loadTags(); }, [loadTags]);

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
      try { localStorage.setItem('todoPanelPos', JSON.stringify({ left: newLeft, top: newTop })); } catch { /* ignore */ }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [pos]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      const data: CreateTodoRequest = {
        title: values.title,
        note: values.note,
        priority: values.priority ?? 0,
        dueDate: values.dueDate ? dayjs(values.dueDate).format('YYYY-MM-DD') : undefined,
        tags: values.tags ?? [],
      };
      await create(data);
      form.resetFields();
      setFormVisible(false);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleUpdate = async (id: number, data: { status?: number }) => {
    await update(id, data);
  };

  const filteredTodos = tab === 'all' ? todos : todos.filter(t => tab === 'active' ? t.status === 0 : t.status === 1);

  return (
    <div style={{
      position: 'fixed',
      left: pos.left,
      top: pos.top,
      width: 380,
      minHeight: 280,
      maxHeight: '70vh',
      zIndex: 1000,
      background: token.colorBgElevated,
      borderRadius: 12,
      boxShadow: token.boxShadowSecondary,
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Header — draggable */}
      <div
        onMouseDown={handleMouseDown}
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '10px 16px', cursor: 'grab', userSelect: 'none',
          borderBottom: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0,
        }}
      >
        <span style={{ fontWeight: 600, fontSize: 15, color: token.colorText }}>
          <CheckSquareOutlined style={{ marginRight: 8 }} />
          我的代办
          <span style={{ marginLeft: 8, fontSize: 12, color: token.colorTextTertiary, fontWeight: 400 }}>
            {total}
          </span>
        </span>
        <CloseOutlined style={{ cursor: 'pointer', color: token.colorTextTertiary }} onClick={onClose} />
      </div>

      {/* Tabs + Add */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px', borderBottom: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0 }}>
        <Tabs
          activeKey={tab}
          onChange={setTab}
          size="small"
          style={{ marginBottom: 0 }}
          items={[
            { key: 'active', label: `进行中${tab === 'active' ? ` (${total})` : ''}` },
            { key: 'done', label: `已完成${tab === 'done' ? ` (${total})` : ''}` },
            { key: 'all', label: `全部${tab === 'all' ? ` (${total})` : ''}` },
          ]}
        />
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => setFormVisible(true)}>新建</Button>
      </div>

      {/* List */}
      <div style={{ flex: 1, overflow: 'auto' }}>
        <TodoList
          todos={filteredTodos}
          loading={loading}
          hasMore={hasMore}
          onLoadMore={loadMore}
          onUpdate={handleUpdate}
          onDelete={remove}
          onAddItem={addItem}
          onUpdateItem={updateItem}
          onDeleteItem={removeItem}
          onReorder={reorder}
        />
      </div>

      {/* Create Modal */}
      <Modal
        title="新建代办"
        open={formVisible}
        onOk={handleCreate}
        onCancel={() => setFormVisible(false)}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="要做什么？" maxLength={500} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea placeholder="详细描述（可选）" rows={2} />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Select options={PRIORITY_OPTIONS} placeholder="选择优先级" />
          </Form.Item>
          <Form.Item name="dueDate" label="截止日期">
            <DatePicker style={{ width: '100%' }} placeholder="选择日期（可选）" />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入标签，回车添加" options={tags.map(t => ({ value: t, label: t }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TodoPanel;
