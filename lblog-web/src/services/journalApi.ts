import { request, buildQuery } from './api';
import type { ApiResponse, JournalEntry, CalendarDay, CreateJournalRequest, UpdateJournalRequest } from '../types';

export function getCalendar(year: number, month: number): Promise<ApiResponse<CalendarDay[]>> {
  return request<CalendarDay[]>(`/api/v1/journals/calendar${buildQuery({ year, month })}`);
}

export function getJournals(page = 1, pageSize = 20): Promise<ApiResponse<JournalEntry[]>> {
  return request<JournalEntry[]>(`/api/v1/journals${buildQuery({ page, pageSize })}`);
}

export function getJournalByDate(date: string): Promise<ApiResponse<JournalEntry | null>> {
  return request<JournalEntry | null>(`/api/v1/journals/by-date${buildQuery({ date })}`);
}

export function createJournal(data: CreateJournalRequest): Promise<ApiResponse<JournalEntry>> {
  return request<JournalEntry>('/api/v1/journals', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function updateJournal(id: number, data: UpdateJournalRequest): Promise<ApiResponse<JournalEntry>> {
  return request<JournalEntry>(`/api/v1/journals/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deleteJournal(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/journals/${id}`, { method: 'DELETE' });
}
