import { useState } from 'react';
import { Input, Checkbox } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { TodoItem } from '../types';

interface Props {
  todoId: number;
  items: TodoItem[];
  onAdd: (todoId: number, title: string) => Promise<void>;
  onUpdate: (itemId: number, data: { title?: string; completed?: boolean }) => Promise<void>;
  onDelete: (itemId: number) => Promise<void>;
}

const TodoSubtaskList: React.FC<Props> = ({ todoId, items, onAdd, onUpdate, onDelete }) => {
  const [newTitle, setNewTitle] = useState('');
  const [adding, setAdding] = useState(false);

  const handleAdd = async () => {
    const t = newTitle.trim();
    if (!t) return;
    setAdding(true);
    await onAdd(todoId, t);
    setNewTitle('');
    setAdding(false);
  };

  return (
    <div style={{ paddingLeft: 20, marginTop: 6 }}>
      {items.map(item => (
        <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '2px 0' }}>
          <Checkbox
            checked={item.completed}
            onChange={e => onUpdate(item.id, { completed: e.target.checked })}
          />
          <span style={{
            flex: 1,
            fontSize: 13,
            textDecoration: item.completed ? 'line-through' : 'none',
            color: item.completed ? '#999' : undefined,
          }}>
            {item.title}
          </span>
          <DeleteOutlined
            style={{ fontSize: 12, color: '#bbb', cursor: 'pointer', flexShrink: 0 }}
            onClick={() => onDelete(item.id)}
          />
        </div>
      ))}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
        <PlusOutlined style={{ fontSize: 12, color: '#bbb' }} />
        <Input
          size="small"
          variant="borderless"
          placeholder="添加子任务"
          value={newTitle}
          onChange={e => setNewTitle(e.target.value)}
          onPressEnter={handleAdd}
          disabled={adding}
          style={{ fontSize: 13, padding: 0, height: 24 }}
        />
      </div>
    </div>
  );
};

export default TodoSubtaskList;
