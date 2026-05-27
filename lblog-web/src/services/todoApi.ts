import type { ApiResponse, PageResult, Todo, CreateTodoRequest, UpdateTodoRequest, SortRequest, TodoItem } from '../types';
import { request, buildQuery } from './api';

export function getTodos(params?: {
  page?: number;
  pageSize?: number;
  status?: number;
  tag?: string;
}): Promise<ApiResponse<PageResult<Todo>>> {
  return request<PageResult<Todo>>(`/api/v1/todos${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export function createTodo(data: CreateTodoRequest): Promise<ApiResponse<Todo>> {
  return request<Todo>('/api/v1/todos', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function updateTodo(id: number, data: UpdateTodoRequest): Promise<ApiResponse<Todo>> {
  return request<Todo>(`/api/v1/todos/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deleteTodo(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/todos/${id}`, { method: 'DELETE' });
}

export function sortTodos(data: SortRequest): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/todos/sort', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function addTodoItem(todoId: number, title: string): Promise<ApiResponse<TodoItem>> {
  return request<TodoItem>(`/api/v1/todos/${todoId}/items`, {
    method: 'POST',
    body: JSON.stringify({ title }),
  });
}

export function updateTodoItem(itemId: number, data: { title?: string; completed?: boolean }): Promise<ApiResponse<TodoItem>> {
  return request<TodoItem>(`/api/v1/todos/items/${itemId}?${buildQuery(data as Record<string, string | number | undefined>)}`, {
    method: 'PUT',
  });
}

export function deleteTodoItem(itemId: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/todos/items/${itemId}`, { method: 'DELETE' });
}

export function sortTodoItems(todoId: number, data: SortRequest): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/todos/${todoId}/items/sort`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function getTodoTags(): Promise<ApiResponse<string[]>> {
  return request<string[]>('/api/v1/todo-tags');
}
