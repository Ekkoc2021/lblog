import { useState, useCallback } from 'react';
import { message } from 'antd';
import type { Todo, CreateTodoRequest, UpdateTodoRequest } from '../types';
import * as todoApi from '../services/todoApi';

export function useTodos() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [tags, setTags] = useState<string[]>([]);

  const loadTodos = useCallback(async (params?: { page?: number; pageSize?: number; status?: number; tag?: string }) => {
    setLoading(true);
    try {
      const res = await todoApi.getTodos({ pageSize: 100, ...params });
      setTodos(res.data.list);
      setTotal(res.data.total);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

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
      await loadTodos();
      await loadTags();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos, loadTags]);

  const update = useCallback(async (id: number, data: UpdateTodoRequest) => {
    try {
      await todoApi.updateTodo(id, data);
      await loadTodos();
      await loadTags();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos, loadTags]);

  const remove = useCallback(async (id: number) => {
    try {
      await todoApi.deleteTodo(id);
      message.success('代办已删除');
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const addItem = useCallback(async (todoId: number, title: string) => {
    try {
      await todoApi.addTodoItem(todoId, title);
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const updateItem = useCallback(async (itemId: number, data: { title?: string; completed?: boolean }) => {
    try {
      await todoApi.updateTodoItem(itemId, data);
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const removeItem = useCallback(async (itemId: number) => {
    try {
      await todoApi.deleteTodoItem(itemId);
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  const reorder = useCallback(async (items: { id: number; sortOrder: number }[]) => {
    try {
      await todoApi.sortTodos({ items });
      await loadTodos();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  }, [loadTodos]);

  return { todos, loading, total, tags, loadTodos, loadTags, create, update, remove, addItem, updateItem, removeItem, reorder };
}
