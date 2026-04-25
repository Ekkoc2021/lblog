import type { Post, Category, Tag, Series, PageResult, ApiResponse, PostDetail, LikeResponse, LikeStatus } from '../types';

const BASE = '/api/v1';

async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    return { code: res.status, message: `HTTP ${res.status} ${res.statusText}`, data: null as unknown as T };
  }
  return res.json();
}

export async function getPosts(params?: {
  page?: number;
  pageSize?: number;
  categoryId?: number;
  tagId?: number;
  sort?: string;
  keyword?: string;
}): Promise<ApiResponse<PageResult<Post>>> {
  const qs = new URLSearchParams();
  if (params?.page) qs.set('page', String(params.page));
  if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
  if (params?.categoryId) qs.set('categoryId', String(params.categoryId));
  if (params?.tagId) qs.set('tagId', String(params.tagId));
  if (params?.sort) qs.set('sort', params.sort);
  if (params?.keyword) qs.set('keyword', params.keyword);
  const query = qs.toString();
  return request(`/posts${query ? `?${query}` : ''}`);
}

export async function getPostBySlug(slug: string): Promise<ApiResponse<PostDetail>> {
  return request(`/posts/${encodeURIComponent(slug)}`);
}

export async function getCategories(): Promise<ApiResponse<Category[]>> {
  return request('/categories');
}

export async function getTags(limit = 20): Promise<ApiResponse<Tag[]>> {
  return request(`/tags?limit=${limit}`);
}

export async function getSeries(limit = 5): Promise<ApiResponse<Series[]>> {
  return request(`/series?limit=${limit}`);
}

export async function reportView(postId: number): Promise<void> {
  await fetch(`${BASE}/posts/${postId}/view`, { method: 'POST' });
}

export async function likePost(postId: number, visitorId: string): Promise<ApiResponse<LikeResponse>> {
  return request(`/posts/${postId}/like`, {
    method: 'POST',
    headers: { 'X-Visitor-Id': visitorId, 'Content-Type': 'application/json' },
  });
}

export async function unlikePost(postId: number, visitorId: string): Promise<ApiResponse<LikeResponse>> {
  return request(`/posts/${postId}/like`, {
    method: 'DELETE',
    headers: { 'X-Visitor-Id': visitorId, 'Content-Type': 'application/json' },
  });
}

export async function getLikeStatus(postId: number, visitorId: string): Promise<ApiResponse<LikeStatus>> {
  return request(`/posts/${postId}/like/status`, {
    headers: { 'X-Visitor-Id': visitorId },
  });
}
