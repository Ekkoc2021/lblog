import { useCallback } from 'react';
import { Spin, Empty, Button, theme } from 'antd';
import type { Todo } from '../types';
import TodoItem from './TodoItem';

interface Props {
  todos: Todo[];
  loading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  onUpdate: (id: number, data: { status?: number }) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onAddItem: (todoId: number, title: string) => Promise<void>;
  onUpdateItem: (itemId: number, data: { title?: string; completed?: boolean }) => Promise<void>;
  onDeleteItem: (itemId: number) => Promise<void>;
  onReorder: (items: { id: number; sortOrder: number }[]) => Promise<void>;
}

const TodoList: React.FC<Props> = ({ todos, loading, hasMore, onLoadMore, onUpdate, onDelete, onAddItem, onUpdateItem, onDeleteItem, onReorder }) => {
  const { token } = theme.useToken();
  const handleDragStart = useCallback((e: React.DragEvent, index: number) => {
    e.dataTransfer.setData('text/plain', String(index));
    (e.currentTarget as HTMLElement).style.opacity = '0.5';
  }, []);

  const handleDragEnd = useCallback((e: React.DragEvent) => {
    (e.currentTarget as HTMLElement).style.opacity = '1';
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
  }, []);

  const handleDrop = useCallback((e: React.DragEvent, dropIndex: number) => {
    e.preventDefault();
    const dragIndex = parseInt(e.dataTransfer.getData('text/plain'), 10);
    if (dragIndex === dropIndex) return;

    const reordered = [...todos];
    const [moved] = reordered.splice(dragIndex, 1);
    reordered.splice(dropIndex, 0, moved);

    const items = reordered.map((t, i) => ({ id: t.id, sortOrder: i }));
    onReorder(items);
  }, [todos, onReorder]);

  if (loading && todos.length === 0) return <Spin style={{ display: 'block', padding: 40, textAlign: 'center' }} />;
  if (!loading && todos.length === 0) return <Empty description="暂无代办" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ padding: 30 }} />;

  return (
    <div>
      <div style={{ maxHeight: '50vh', overflowY: 'auto' }}>
        {todos.map((todo, index) => (
          <div
            key={todo.id}
            draggable
            onDragStart={e => handleDragStart(e, index)}
            onDragEnd={handleDragEnd}
            onDragOver={handleDragOver}
            onDrop={e => handleDrop(e, index)}
          >
            <TodoItem
              todo={todo}
              onUpdate={onUpdate}
              onDelete={onDelete}
              onAddItem={onAddItem}
              onUpdateItem={onUpdateItem}
              onDeleteItem={onDeleteItem}
              dragHandleProps={{}}
            />
          </div>
        ))}
      </div>
      {hasMore && (
        <div style={{ textAlign: 'center', padding: '8px 0', borderTop: `1px solid ${token.colorBorderSecondary}` }}>
          <Button type="link" loading={loading} onClick={onLoadMore} size="small">
            加载更多
          </Button>
        </div>
      )}
    </div>
  );
};

export default TodoList;
