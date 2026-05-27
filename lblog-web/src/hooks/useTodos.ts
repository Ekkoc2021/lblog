import { useState, useCallback, useRef } from 'react';
import { message } from 'antd';
import type { Todo, CreateTodoRequest, UpdateTodoRequest } from '../types';
import * as todoApi from '../services/todoApi';

const PAGE_SIZE = 20;

export function useTodos() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tags, setTags] = useState<string[]>([]);
  const pageRef = useRef(1);
  const statusRef = useRef<number | undefined>(undefined);

  const loadingRef = useRef(false);
  const hasMore = todos.length < total;

  const loadTodos = useCallback(async (params?: { status?: number; tag?: string }) => {
    setLoading(true);
    loadingRef.current = true;
    pageRef.current = 1;
    statusRef.current = params?.status;
    try {
      const res = await todoApi.getTodos({ page: 1, pageSize: PAGE_SIZE, ...params });
      setTodos(res.data.list);
      setTotal(res.data.total);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setLoading(false);
      loadingRef.current = false;
    }
  }, []);

  const loadMore = useCallback(async () => {
    if (!hasMore || loadingRef.current) return;
    loadingRef.current = true;
    setLoading(true);
    const nextPage = pageRef.current + 1;
    try {
      const res = await todoApi.getTodos({ page: nextPage, pageSize: PAGE_SIZE, status: statusRef.current });
      setTodos(prev => [...prev, ...res.data.list]);
      setTotal(res.data.total);
      pageRef.current = nextPage;
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setLoading(false);
      loadingRef.current = false;
    }
  }, [hasMore]);

  const loadTags = useCallback(async () => {
    try {
      const res = await todoApi.getTodoTags();
      setTags(res.data);
    } catch { /* tags are non-critical */ }
  }, []);

  const create = useCallback(async (data: CreateTodoRequest) => {
    try {
      await todoApi.createTodo(data);
      message.success('代办已创建');
      await loadTodos({ status: statusRef.current });
      await loadTags();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos, loadTags]);

  const update = useCallback(async (id: number, data: UpdateTodoRequest) => {
    try {
      const res = await todoApi.updateTodo(id, data);
      if (!res.data) return;
      setTodos(prev => {
        // 标记完成时，如果当前筛选进行中，从列表中移除
        if (data.status === 1 && statusRef.current === 0) {
          return prev.filter(t => t.id !== id);
        }
        return prev.map(t => t.id === id ? res.data : t);
      });
      if (data.tags) await loadTags();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTags]);

  const remove = useCallback(async (id: number) => {
    try {
      await todoApi.deleteTodo(id);
      message.success('代办已删除');
      setTodos(prev => prev.filter(t => t.id !== id));
      setTotal(prev => prev - 1);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, []);

  const addItem = useCallback(async (todoId: number, title: string) => {
    try {
      const res = await todoApi.addTodoItem(todoId, title);
      setTodos(prev => prev.map(t => t.id === todoId ? { ...t, items: [...t.items, res.data] } : t));
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, []);

  const updateItem = useCallback(async (itemId: number, data: { title?: string; completed?: boolean }) => {
    try {
      await todoApi.updateTodoItem(itemId, data);
      setTodos(prev => prev.map(t => ({
        ...t,
        items: t.items.map(i => i.id === itemId ? { ...i, ...data } : i)
      })));
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, []);

  const removeItem = useCallback(async (itemId: number) => {
    try {
      await todoApi.deleteTodoItem(itemId);
      setTodos(prev => prev.map(t => ({
        ...t,
        items: t.items.filter(i => i.id !== itemId)
      })));
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, []);

  const reorder = useCallback(async (items: { id: number; sortOrder: number }[]) => {
    const prevTodos = todos;
    const sortMap = new Map(items.map(i => [i.id, i.sortOrder]));
    setTodos(prev => prev.map(t => ({ ...t, sortOrder: sortMap.get(t.id) ?? t.sortOrder })));
    try {
      await todoApi.sortTodos({ items });
    } catch {
      setTodos(prevTodos); // 回滚
      message.error('排序失败，请重试');
    }
  }, [todos]);

  return { todos, loading, total, hasMore, tags, loadTodos, loadMore, loadTags, create, update, remove, addItem, updateItem, removeItem, reorder };
}
