import type { Post, Category, Tag, Series, PageResult, ApiResponse, PostDetail, LikeResponse, LikeStatus, CreatePostRequest, UpdatePostRequest, CreateCategoryRequest, CreateTagRequest, CreateSeriesRequest } from '../types';

function getToken(): string | null {
  return sessionStorage.getItem('lblog_token');
}

async function request<T>(path: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options?.headers as Record<string, string>),
  };

  const res = await fetch(path, { ...options, headers });

  // 401 统一处理
  if (res.status === 401) {
    sessionStorage.removeItem('lblog_token');
    throw new Error('未登录或 Token 已过期');
  }

  const json: ApiResponse<T> = await res.json();

  if (json.code !== 0) {
    throw new Error(json.message || '请求失败');
  }
  return json;
}

function buildQuery(params?: Record<string, string | number | boolean | undefined | null>): string {
  if (!params) return '';
  const q = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');
  return q ? `?${q}` : '';
}

// ---- Auth ----

interface LoginResponseData {
  token: string;
  user: {
    id: number;
    username: string;
    nickname: string;
    avatar: string | null;
    email: string;
    role: string;
  };
}

export async function login(username: string, password: string): Promise<ApiResponse<LoginResponseData>> {
  return request<LoginResponseData>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export async function getCurrentUser(): Promise<ApiResponse<{ id: number; username: string; nickname: string; avatar: string | null; email: string; role: string }>> {
  return request('/api/v1/auth/me');
}

export async function logout(): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/auth/logout', { method: 'POST' });
}

// ---- 前台公共接口 ----

export async function getPosts(params?: {
  page?: number;
  pageSize?: number;
  categoryId?: number;
  tagId?: number;
  seriesId?: number;
  sort?: string;
  keyword?: string;
}): Promise<ApiResponse<PageResult<Post>>> {
  return request<PageResult<Post>>(`/api/v1/posts${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function getHotPosts(limit = 5): Promise<ApiResponse<Post[]>> {
  return request<Post[]>(`/api/v1/posts/hot${buildQuery({ limit })}`);
}

export async function getCategories(limit = 10): Promise<ApiResponse<Category[]>> {
  return request<Category[]>(`/api/v1/categories${buildQuery({ limit })}`);
}

export async function getTags(limit = 20): Promise<ApiResponse<Tag[]>> {
  return request<Tag[]>(`/api/v1/tags${buildQuery({ limit })}`);
}

export async function getSeries(limit = 5, categoryId?: number): Promise<ApiResponse<Series[]>> {
  return request<Series[]>(`/api/v1/series${buildQuery({ limit, categoryId })}`);
}

export async function getPostBySlug(slug: string): Promise<ApiResponse<PostDetail>> {
  return request<PostDetail>(`/api/v1/posts/${slug}`);
}

export async function reportView(postId: number): Promise<void> {
  await request(`/api/v1/posts/${postId}/view`, { method: 'POST' });
}

export async function likePost(postId: number, visitorId: string): Promise<ApiResponse<LikeResponse>> {
  return request<LikeResponse>(`/api/v1/posts/${postId}/like`, {
    method: 'POST',
    headers: { 'X-Visitor-Id': visitorId } as Record<string, string>,
    body: '{}',
  });
}

export async function unlikePost(postId: number, visitorId: string): Promise<ApiResponse<LikeResponse>> {
  return request<LikeResponse>(`/api/v1/posts/${postId}/like`, {
    method: 'DELETE',
    headers: { 'X-Visitor-Id': visitorId } as Record<string, string>,
  });
}

export async function getLikeStatus(postId: number, visitorId: string): Promise<ApiResponse<LikeStatus>> {
  return request<LikeStatus>(`/api/v1/posts/${postId}/like/status`, {
    headers: { 'X-Visitor-Id': visitorId } as Record<string, string>,
  });
}

// ---- 管理端 Admin APIs ----

// 管理端文章列表
export async function getAdminPosts(params?: {
  page?: number;
  pageSize?: number;
  status?: number;
  keyword?: string;
}): Promise<ApiResponse<PageResult<Post>>> {
  return request<PageResult<Post>>(`/api/v1/admin/posts${buildQuery(params as Record<string, string | number | undefined>)}`);
}

// 获取单篇文章（编辑用）
export async function getAdminPostById(id: number): Promise<ApiResponse<PostDetail>> {
  return request<PostDetail>(`/api/v1/admin/posts/${id}`);
}

// 创建文章
export async function createPost(data: CreatePostRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/posts', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// 更新文章
export async function updatePost(id: number, data: UpdatePostRequest): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/posts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

// 删除文章
export async function deletePost(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/posts/${id}`, { method: 'DELETE' });
}

// 校验 Slug
export async function checkSlug(slug: string, excludeId?: number): Promise<ApiResponse<{ available: boolean }>> {
  return request<{ available: boolean }>(`/api/v1/admin/posts/check-slug${buildQuery({ slug, excludeId })}`);
}

// ---- 管理端 Category CRUD ----

export async function createCategory(data: CreateCategoryRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/categories', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateCategory(id: number, data: Partial<CreateCategoryRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteCategory(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/categories/${id}`, { method: 'DELETE' });
}

// ---- 管理端 Tag CRUD ----

export async function createTag(data: CreateTagRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/tags', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateTag(id: number, data: Partial<CreateTagRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/tags/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteTag(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/tags/${id}`, { method: 'DELETE' });
}

// ---- 管理端 Series CRUD ----

export async function createSeries(data: CreateSeriesRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/series', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateSeries(id: number, data: Partial<CreateSeriesRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/series/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteSeries(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/series/${id}`, { method: 'DELETE' });
}
