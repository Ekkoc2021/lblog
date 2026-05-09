import type { Post, Category, Tag, Series, PageResult, ApiResponse, PostDetail, LikeResponse, LikeStatus, CreatePostRequest, UpdatePostRequest, CreateCategoryRequest, CreateTagRequest, CreateSeriesRequest, TokenPairVO, ChangePasswordRequest, RegisterRequest, Comment, CreateCommentRequest, SiteConfig, AdminCategory, AdminTag, AdminSeries, AdminComment } from '../types';

function getToken(): string | null {
  return sessionStorage.getItem('lblog_access_token');
}

function getRefreshToken(): string | null {
  return sessionStorage.getItem('lblog_refresh_token');
}

function setTokens(accessToken: string, refreshToken: string): void {
  sessionStorage.setItem('lblog_access_token', accessToken);
  sessionStorage.setItem('lblog_refresh_token', refreshToken);
}

function clearTokens(): void {
  sessionStorage.removeItem('lblog_access_token');
  sessionStorage.removeItem('lblog_refresh_token');
}

// Token 刷新模块级状态
let isRefreshing = false;
let pendingRequests: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = [];

export async function refreshToken(): Promise<ApiResponse<TokenPairVO>> {
  const token = getRefreshToken();
  if (!token) throw new Error('No refresh token');
  return request<TokenPairVO>('/api/v1/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: token }),
  });
}

async function request<T>(path: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options?.headers as Record<string, string>),
  };

  // FormData 由浏览器自己设置 Content-Type（含 boundary）
  if (options?.body instanceof FormData) {
    delete headers['Content-Type'];
  }

  const res = await fetch(path, { ...options, headers });

  // 401 统一处理——自动续期
  if (res.status === 401) {
    // 如果本身就是刷新请求，不触发刷新流程，直接清 token 抛异常
    if (path.includes('/auth/refresh')) {
      clearTokens();
      throw new Error('Refresh token expired');
    }

    // 没有 token 说明是登录等公开接口的 401（密码错误），直接返回错误
    if (!token) {
      const json: ApiResponse<T> = await res.json();
      throw new Error(json.message || '未登录或 Token 已过期');
    }

    // 并发控制：若正在刷新，排队等待
    if (isRefreshing) {
      return new Promise<ApiResponse<T>>((resolve, reject) => {
        pendingRequests.push({
          resolve: (newToken: string) => {
            const newHeaders: Record<string, string> = {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${newToken}`,
              ...(options?.headers as Record<string, string>),
            };
            fetch(path, { ...options, headers: newHeaders })
              .then(async (r) => {
                const j: ApiResponse<T> = await r.json();
                if (j.code !== 0) throw new Error(j.message || '请求失败');
                resolve(j);
              })
              .catch(reject);
          },
          reject,
        });
      });
    }

    // 开始刷新流程
    isRefreshing = true;
    try {
      const refreshRes = await refreshToken();
      const newToken = refreshRes.data.accessToken;
      setTokens(newToken, refreshRes.data.refreshToken);

      // 重放等待队列
      const pending = [...pendingRequests];
      pendingRequests = [];
      pending.forEach((p) => p.resolve(newToken));

      // 用新 token 重发当前请求
      const newHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${newToken}`,
        ...(options?.headers as Record<string, string>),
      };
      const retryRes = await fetch(path, { ...options, headers: newHeaders });
      const json: ApiResponse<T> = await retryRes.json();
      if (json.code !== 0) throw new Error(json.message || '请求失败');
      return json;
    } catch (err) {
      clearTokens();
      const pending = [...pendingRequests];
      pendingRequests = [];
      pending.forEach((p) => p.reject(err));
      throw err;
    } finally {
      isRefreshing = false;
    }
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

export async function login(username: string, password: string): Promise<ApiResponse<TokenPairVO>> {
  return request<TokenPairVO>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export async function register(data: RegisterRequest): Promise<ApiResponse<TokenPairVO>> {
  return request<TokenPairVO>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function getCurrentUser(): Promise<ApiResponse<{ id: number; username: string; nickname: string; avatar: string | null; email: string; role: string }>> {
  return request('/api/v1/auth/me');
}

export async function logout(): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/auth/logout', { method: 'POST' });
}

export async function changePassword(data: ChangePasswordRequest): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(data),
  });
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

// ---- 评论 ----

export async function getComments(postId: number, params?: {
  page?: number;
  pageSize?: number;
  sort?: string;
}): Promise<ApiResponse<PageResult<Comment>>> {
  return request<PageResult<Comment>>(`/api/v1/posts/${postId}/comments${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function getCommentReplies(postId: number, rootId: number, params?: {
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<PageResult<Comment>>> {
  return request<PageResult<Comment>>(`/api/v1/posts/${postId}/comments/${rootId}/replies${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function createComment(postId: number, data: CreateCommentRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>(`/api/v1/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify(data),
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
}, signal?: AbortSignal): Promise<ApiResponse<PageResult<Post>>> {
  return request<PageResult<Post>>(`/api/v1/author/posts${buildQuery(params as Record<string, string | number | undefined>)}`, { signal });
}

// 获取单篇文章（编辑用）
export async function getAdminPostById(id: number): Promise<ApiResponse<PostDetail>> {
  return request<PostDetail>(`/api/v1/author/posts/${id}`);
}

// 创建文章
export async function createPost(data: CreatePostRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/author/posts', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// 更新文章
export async function updatePost(id: number, data: UpdatePostRequest): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/posts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

// 删除文章
export async function deletePost(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/posts/${id}`, { method: 'DELETE' });
}

// 校验 Slug
export async function checkSlug(slug: string, excludeId?: number): Promise<ApiResponse<{ available: boolean }>> {
  return request<{ available: boolean }>(`/api/v1/author/posts/check-slug${buildQuery({ slug, excludeId })}`);
}

// ---- 站点配置 & 图片上传 ----

export async function getSiteConfig(): Promise<ApiResponse<SiteConfig>> {
  return request<SiteConfig>('/api/v1/config');
}

export async function uploadImage(file: File): Promise<ApiResponse<{ url: string; filename: string; size: number; mimeType: string }>> {
  const formData = new FormData();
  formData.append('file', file);
  return request('/api/v1/upload/image', {
    method: 'POST',
    body: formData,
  });
}

// ---- 管理端 Category CRUD ----

// 作者管理：分类列表
export async function getAuthorCategories(params?: {
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<PageResult<Category>>> {
  return request<PageResult<Category>>(`/api/v1/author/categories${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function createCategory(data: CreateCategoryRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/author/categories', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateCategory(id: number, data: Partial<CreateCategoryRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteCategory(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/categories/${id}`, { method: 'DELETE' });
}

// ---- 管理端 Tag CRUD ----

// 作者管理：标签列表
export async function getAuthorTags(params?: {
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<PageResult<Tag>>> {
  return request<PageResult<Tag>>(`/api/v1/author/tags${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function createTag(data: CreateTagRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/author/tags', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateTag(id: number, data: Partial<CreateTagRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/tags/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteTag(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/tags/${id}`, { method: 'DELETE' });
}

// ---- 管理端 Series CRUD ----

// 作者管理：专栏列表
export async function getAuthorSeries(params?: {
  page?: number;
  pageSize?: number;
  categoryId?: number;
}): Promise<ApiResponse<PageResult<Series>>> {
  return request<PageResult<Series>>(`/api/v1/author/series${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function createSeries(data: CreateSeriesRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/author/series', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateSeries(id: number, data: Partial<CreateSeriesRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/series/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteSeries(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/author/series/${id}`, { method: 'DELETE' });
}

// ---- 作者统计 ----

export interface AuthorStatistics {
  totalPosts: number;
  totalViews: number;
  totalLikes: number;
  totalComments: number;
  statusDistribution: Array<{ status: number; count: number }>;
  categoryDistribution: Array<{ categoryName: string; categorySlug: string; postCount: number }>;
  monthlyTrend: Array<{ month: string; count: number }>;
}

export async function getAuthorStatistics(): Promise<ApiResponse<AuthorStatistics>> {
  return request<AuthorStatistics>('/api/v1/author/statistics');
}

// ---- 用户头像 ----

export async function updateAvatar(file: File): Promise<ApiResponse<{ id: number; url: string }>> {
  const formData = new FormData();
  formData.append('file', file);
  return request<{ id: number; url: string }>('/api/v1/user/avatar', {
    method: 'PUT',
    body: formData,
  });
}

export async function deleteAvatar(): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/user/avatar', { method: 'DELETE' });
}

// ---- 管理端站点配置 ----

export interface SiteConfigItem {
  configKey: string;
  configValue: string;
}

export async function getAdminConfigs(): Promise<ApiResponse<SiteConfigItem[]>> {
  return request<SiteConfigItem[]>('/api/v1/admin/configs');
}

export async function updateAdminConfigs(data: Record<string, string>): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/admin/configs', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function addAdminConfig(key: string, value: string): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/admin/configs', {
    method: 'POST',
    body: JSON.stringify({ configKey: key, configValue: value }),
  });
}

export async function deleteAdminConfig(key: string): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/configs?key=${encodeURIComponent(key)}`, {
    method: 'DELETE',
  });
}

// ---- 管理端图片管理 ----

export interface AdminImage {
  id: number;
  url: string;
  originalName: string;
  mimeType: string;
  fileSize: number;
  width: number;
  height: number;
  usageCount: number;
  usages: Array<{ refType: string; refId: number; field: string; refTitle: string }>;
  createdAt: string;
}

export interface ImageStatistics {
  totalImages: number;
  totalSize: number;
  referencedCount: number;
  unreferencedCount: number;
  utilizationRate: number;
  oldUnreferencedCount: number;
  oldUnreferencedSize: number;
}

export async function getAdminImages(params?: {
  page?: number;
  pageSize?: number;
  sort?: string;
  status?: string;
  keyword?: string;
}): Promise<ApiResponse<PageResult<AdminImage>>> {
  return request<PageResult<AdminImage>>(`/api/v1/admin/images${buildQuery(params as any)}`);
}

export async function getImageStatistics(): Promise<ApiResponse<ImageStatistics>> {
  return request<ImageStatistics>('/api/v1/admin/images/statistics');
}

export async function deleteAdminImage(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/images/${id}`, { method: 'DELETE' });
}

export async function cleanupImages(params?: { beforeDays?: number; dryRun?: boolean }): Promise<ApiResponse<any>> {
  return request<any>(`/api/v1/admin/images/cleanup${buildQuery(params as any)}`, { method: 'DELETE' });
}

// ---- 管理端用户管理 ----

export interface AdminUser {
  id: number;
  username: string;
  nickname: string;
  email: string;
  avatar: string | null;
  roles: string[];
  roleLabels: string[];
  status: number;
  postCount: number;
  lastLoginAt: string | null;
  loginCount: number;
  createdAt: string;
}

export interface RoleInfo {
  id: number;
  name: string;
  label: string;
}

export async function getAdminUsers(params?: {
  page?: number;
  pageSize?: number;
  keyword?: string;
  role?: string;
  status?: number;
  inactiveDays?: number;
}): Promise<ApiResponse<PageResult<AdminUser>>> {
  return request<PageResult<AdminUser>>(`/api/v1/admin/users${buildQuery(params as any)}`);
}

export async function getAdminUser(id: number): Promise<ApiResponse<AdminUser>> {
  return request<AdminUser>(`/api/v1/admin/users/${id}`);
}

export async function createAdminUser(data: {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  roleIds?: number[];
}): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/users', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateAdminUser(id: number, data: {
  nickname?: string;
  email?: string;
  roleIds?: number[];
  status?: number;
}): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function resetUserPassword(id: number, newPassword: string): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/users/${id}/reset-password`, {
    method: 'PUT',
    body: JSON.stringify({ newPassword }),
  });
}

export async function deleteAdminUser(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/users/${id}`, { method: 'DELETE' });
}

export async function getRoles(): Promise<ApiResponse<RoleInfo[]>> {
  return request<RoleInfo[]>('/api/v1/admin/roles');
}

// ---- 全站内容管理 Admin APIs ----

// Posts
export async function getAdminAllPosts(params?: {
  page?: number;
  pageSize?: number;
  status?: number;
  keyword?: string;
  authorId?: number;
}, signal?: AbortSignal): Promise<ApiResponse<PageResult<Post>>> {
  return request<PageResult<Post>>(`/api/v1/admin/posts${buildQuery(params as Record<string, string | number | undefined>)}`, { signal });
}

export async function updateAdminPost(id: number, data: Record<string, unknown>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/posts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function updateAdminPostStatus(id: number, status: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/posts/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  });
}

export async function batchAdminPosts(ids: number[], action: 'PUBLISH' | 'DRAFT' | 'DELETE'): Promise<ApiResponse<{ successCount: number; failedIds: number[] } | null>> {
  return request<{ successCount: number; failedIds: number[] } | null>('/api/v1/admin/posts/batch', {
    method: 'POST',
    body: JSON.stringify({ ids, action }),
  });
}

// Categories
export async function getAdminAllCategories(params?: {
  page?: number;
  pageSize?: number;
  createdBy?: number;
}, signal?: AbortSignal): Promise<ApiResponse<PageResult<AdminCategory>>> {
  return request<PageResult<AdminCategory>>(`/api/v1/admin/categories${buildQuery(params as Record<string, string | number | undefined>)}`, { signal });
}

export async function createAdminCategory(data: CreateCategoryRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/categories', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateAdminCategory(id: number, data: Partial<CreateCategoryRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteAdminCategory(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/categories/${id}`, { method: 'DELETE' });
}

// Tags
export async function getAdminAllTags(params?: {
  page?: number;
  pageSize?: number;
  createdBy?: number;
}, signal?: AbortSignal): Promise<ApiResponse<PageResult<AdminTag>>> {
  return request<PageResult<AdminTag>>(`/api/v1/admin/tags${buildQuery(params as Record<string, string | number | undefined>)}`, { signal });
}

export async function createAdminTag(data: CreateTagRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/tags', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateAdminTag(id: number, data: Partial<CreateTagRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/tags/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteAdminTag(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/tags/${id}`, { method: 'DELETE' });
}

// Series
export async function getAdminAllSeries(params?: {
  page?: number;
  pageSize?: number;
  categoryId?: number;
  createdBy?: number;
}, signal?: AbortSignal): Promise<ApiResponse<PageResult<AdminSeries>>> {
  return request<PageResult<AdminSeries>>(`/api/v1/admin/series${buildQuery(params as Record<string, string | number | undefined>)}`, { signal });
}

export async function createAdminSeries(data: CreateSeriesRequest): Promise<ApiResponse<{ id: number }>> {
  return request<{ id: number }>('/api/v1/admin/series', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateAdminSeries(id: number, data: Partial<CreateSeriesRequest>): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/series/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteAdminSeries(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/series/${id}`, { method: 'DELETE' });
}

export async function linkAdminSeriesPosts(seriesId: number, postIds: number[]): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/series/${seriesId}/posts`, {
    method: 'POST',
    body: JSON.stringify({ postIds }),
  });
}

export async function sortAdminSeriesPosts(seriesId: number, postIds: number[]): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/series/${seriesId}/posts/sort`, {
    method: 'PUT',
    body: JSON.stringify({ postIds }),
  });
}

// Admin Comments
export async function getAdminComments(params?: {
  page?: number;
  pageSize?: number;
  status?: number;
  keyword?: string;
  postId?: number;
}, signal?: AbortSignal): Promise<ApiResponse<PageResult<AdminComment>>> {
  return request<PageResult<AdminComment>>(`/api/v1/admin/comments${buildQuery(params as Record<string, string | number | undefined>)}`, { signal });
}

export async function reviewAdminComment(id: number, status: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/comments/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  });
}

export async function deleteAdminComment(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/comments/${id}`, { method: 'DELETE' });
}

export async function batchAdminComments(ids: number[], action: 'APPROVE' | 'REJECT' | 'DELETE'): Promise<ApiResponse<{ successCount: number; failedIds: number[] } | null>> {
  return request<{ successCount: number; failedIds: number[] } | null>('/api/v1/admin/comments/batch', {
    method: 'POST',
    body: JSON.stringify({ ids, action }),
  });
}
