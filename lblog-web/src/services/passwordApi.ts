import { request, buildQuery } from './api';
import type { ApiResponse, PageResult, PasswordEntry, CreatePasswordEntryRequest, UpdatePasswordEntryRequest } from '../types';

export function getPasswords(params?: {
  page?: number;
  pageSize?: number;
  keyword?: string;
}): Promise<ApiResponse<PageResult<PasswordEntry>>> {
  return request<PageResult<PasswordEntry>>(`/api/v1/passwords${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export function createPassword(data: CreatePasswordEntryRequest): Promise<ApiResponse<PasswordEntry>> {
  return request<PasswordEntry>('/api/v1/passwords', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function updatePassword(id: number, data: UpdatePasswordEntryRequest): Promise<ApiResponse<PasswordEntry>> {
  return request<PasswordEntry>(`/api/v1/passwords/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deletePassword(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/passwords/${id}`, { method: 'DELETE' });
}
