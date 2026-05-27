import { useState } from 'react';
import { Checkbox, Tag, Popconfirm, theme } from 'antd';
import { DeleteOutlined, DownOutlined, RightOutlined } from '@ant-design/icons';
import type { Todo } from '../types';
import TodoSubtaskList from './TodoSubtaskList';

interface Props {
  todo: Todo;
  onUpdate: (id: number, data: { status?: number; title?: string; priority?: number; dueDate?: string; tags?: string[]; note?: string }) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onAddItem: (todoId: number, title: string) => Promise<void>;
  onUpdateItem: (itemId: number, data: { title?: string; completed?: boolean }) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
  dragHandleProps?: Record<string, unknown>;
}

const PRIORITY_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '低', color: '#52c41a' },
  1: { label: '中', color: '#faad14' },
  2: { label: '高', color: '#ff4d4f' },
};

function isOverdue(dueDate: string | null): boolean {
  if (!dueDate) return false;
  return new Date(dueDate) < new Date(new Date().toDateString());
}

const TodoItem: React.FC<Props> = ({ todo, onUpdate, onDelete, onAddItem, onUpdateItem, onDeleteItem, dragHandleProps }) => {
  const { token } = theme.useToken();
  const [expanded, setExpanded] = useState(false);
  const items = todo.items ?? [];
  const tags = todo.tags ?? [];
  const hasItems = items.length > 0;
  const p = PRIORITY_MAP[todo.priority ?? 0] ?? PRIORITY_MAP[0];

  return (
    <div
      style={{
        padding: '8px 14px',
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
        background: todo.status === 1 ? token.colorFillAlter : undefined,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
        {/* Drag handle */}
        <span
          {...dragHandleProps}
          style={{ cursor: 'grab', color: token.colorTextQuaternary, fontSize: 14, lineHeight: '22px', userSelect: 'none', flexShrink: 0 }}
        >
          ⠿
        </span>

        {/* Checkbox */}
        <Checkbox
          checked={todo.status === 1}
          onChange={e => onUpdate(todo.id, { status: e.target.checked ? 1 : 0 })}
          style={{ flexShrink: 0 }}
        />

        {/* Content */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span
              title={todo.title}
              style={{
                textDecoration: todo.status === 1 ? 'line-through' : 'none',
                color: todo.status === 1 ? token.colorTextTertiary : token.colorText,
                fontSize: 14,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 200,
                display: 'inline-block',
                verticalAlign: 'bottom',
              }}
            >
              {todo.title}
            </span>
            <Tag color={p.color} style={{ margin: 0, fontSize: 11, lineHeight: '18px' }}>{p.label}</Tag>
            {isOverdue(todo.dueDate) && todo.status === 0 && (
              <Tag color="red" style={{ margin: 0, fontSize: 11, lineHeight: '18px' }}>已过期</Tag>
            )}
          </div>

          {/* Meta row */}
          <div style={{ fontSize: 12, color: token.colorTextTertiary, marginTop: 2, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {todo.dueDate && <span>📅 {todo.dueDate}</span>}
            {todo.status === 1 && todo.updatedAt && <span>✅ {todo.updatedAt.substring(0, 10)}</span>}
            {tags.map(t => (
              <Tag key={t} style={{ margin: 0, fontSize: 11, lineHeight: '18px' }}>{t}</Tag>
            ))}
            {hasItems && (
              <span
                style={{ cursor: 'pointer', color: token.colorPrimary }}
                onClick={() => setExpanded(!expanded)}
              >
                {expanded ? <DownOutlined /> : <RightOutlined />} {items.length} 子任务
                ({items.filter(i => i.completed).length}/{items.length})
              </span>
            )}
            {!hasItems && (
              <span
                style={{ cursor: 'pointer', color: token.colorTextQuaternary }}
                onClick={() => setExpanded(!expanded)}
              >
                {expanded ? <DownOutlined /> : <RightOutlined />} 子任务
              </span>
            )}
          </div>

          {/* Subtasks */}
          {expanded && (
            <TodoSubtaskList
              todoId={todo.id}
              items={items}
              onAdd={onAddItem}
              onUpdate={onUpdateItem}
              onDelete={onDeleteItem}
            />
          )}
        </div>

        {/* Delete */}
        <Popconfirm
          title="确定删除？"
          onConfirm={() => onDelete(todo.id)}
          okText="删除"
          cancelText="取消"
        >
          <DeleteOutlined style={{ color: token.colorTextQuaternary, cursor: 'pointer', flexShrink: 0, marginTop: 2 }} />
        </Popconfirm>
      </div>
    </div>
  );
};

export default TodoItem;
