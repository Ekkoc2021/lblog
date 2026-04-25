import type { Post, Category, Tag, Series, PageResult, ApiResponse, PostDetail, LikeResponse, LikeStatus } from '../types';
import { mockPosts, mockCategories, mockTags, mockSeries, mockPostBodies, mockSeriesPosts } from './mock';

function getToken(): string | null {
  return sessionStorage.getItem('lblog_token');
}

function authHeader(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function delay<T>(data: T, ms = 300): Promise<T> {
  return new Promise(resolve => setTimeout(() => resolve(data), ms));
}

function apiOk<T>(data: T): ApiResponse<T> {
  return { code: 0, message: 'success', data };
}

// ---- Auth ----

export async function login(username: string, password: string): Promise<ApiResponse<{ token: string }>> {
  if (username === 'admin' && password === 'admin123') {
    return delay(apiOk({ token: 'mock-jwt-token-' + Date.now() }));
  }
  return delay({ code: 401, message: '用户名或密码错误', data: null as unknown as { token: string } });
}

export function getAuthHeaders(): Record<string, string> {
  return authHeader();
}

const likeRecords = new Map<string, true>();

export async function reportView(postId: number): Promise<void> {
  const post = mockPosts.find(p => p.id === postId);
  if (post) post.viewCount = (post.viewCount || 0) + 1;
  return delay(undefined, 100);
}

export async function likePost(postId: number, visitorId: string): Promise<ApiResponse<LikeResponse>> {
  const key = `${postId}:${visitorId}`;
  const alreadyLiked = likeRecords.has(key);
  if (!alreadyLiked) {
    likeRecords.set(key, true);
    const post = mockPosts.find(p => p.id === postId);
    if (post) post.likeCount = (post.likeCount || 0) + 1;
  }
  const post = mockPosts.find(p => p.id === postId);
  return delay(apiOk({ liked: true, likeCount: post?.likeCount || 0 }));
}

export async function unlikePost(postId: number, visitorId: string): Promise<ApiResponse<LikeResponse>> {
  const key = `${postId}:${visitorId}`;
  const alreadyLiked = likeRecords.has(key);
  if (alreadyLiked) {
    likeRecords.delete(key);
    const post = mockPosts.find(p => p.id === postId);
    if (post && post.likeCount && post.likeCount > 0) post.likeCount -= 1;
  }
  const post = mockPosts.find(p => p.id === postId);
  return delay(apiOk({ liked: false, likeCount: post?.likeCount || 0 }));
}

export async function getLikeStatus(postId: number, visitorId: string): Promise<ApiResponse<LikeStatus>> {
  const key = `${postId}:${visitorId}`;
  return delay(apiOk({ liked: likeRecords.has(key) }));
}

export async function getPosts(params?: {
  page?: number;
  pageSize?: number;
  categoryId?: number;
  tagId?: number;
  seriesId?: number;
  sort?: string;
  keyword?: string;
}): Promise<ApiResponse<PageResult<Post>>> {
  let filtered = [...mockPosts];

  if (params?.categoryId) {
    filtered = filtered.filter(p => p.categoryId === params.categoryId);
  }
  if (params?.tagId) {
    filtered = filtered.filter(p => p.tags?.some(t => t.id === params.tagId));
  }
  if (params?.seriesId) {
    const seriesPostIds = mockSeriesPosts
      .filter(sp => sp.seriesId === params.seriesId)
      .map(sp => sp.postId);
    filtered = filtered.filter(p => seriesPostIds.includes(p.id));
  }
  if (params?.keyword) {
    const kw = params.keyword.toLowerCase();
    filtered = filtered.filter(p =>
      p.title.toLowerCase().includes(kw) ||
      p.excerpt.toLowerCase().includes(kw)
    );
  }

  const sort = params?.sort || 'recommend';
  if (sort === 'newest') {
    filtered.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());
  } else if (sort === 'hot') {
    filtered.sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0));
  }

  const page = params?.page || 1;
  const pageSize = params?.pageSize || 10;
  const start = (page - 1) * pageSize;
  const list = filtered.slice(start, start + pageSize);

  return delay(apiOk({ list, total: filtered.length, page, pageSize }));
}

export async function getHotPosts(limit = 5): Promise<ApiResponse<Post[]>> {
  const sorted = [...mockPosts].sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0));
  return delay(apiOk(sorted.slice(0, limit)));
}

export async function getCategories(limit = 10): Promise<ApiResponse<Category[]>> {
  return delay(apiOk(mockCategories.slice(0, limit)));
}

export async function getTags(limit = 20): Promise<ApiResponse<Tag[]>> {
  return delay(apiOk(mockTags.slice(0, limit)));
}

export async function getSeries(limit = 5): Promise<ApiResponse<Series[]>> {
  return delay(apiOk(mockSeries.slice(0, limit)));
}

export async function getPostBySlug(slug: string): Promise<ApiResponse<PostDetail>> {
  const post = mockPosts.find(p => p.slug === slug);
  if (!post) {
    return delay(apiOk(null as unknown as PostDetail));
  }

  let prevPost: PostDetail['prevPost'] = null;
  let nextPost: PostDetail['nextPost'] = null;

  const sp = mockSeriesPosts.find(p => p.postId === post.id);
  if (sp) {
    const seriesPosts = mockSeriesPosts
      .filter(p => p.seriesId === sp.seriesId)
      .sort((a, b) => a.sortOrder - b.sortOrder);
    const idx = seriesPosts.findIndex(p => p.postId === post.id);
    if (idx > 0) {
      const prev = mockPosts.find(p => p.id === seriesPosts[idx - 1].postId);
      if (prev) prevPost = { id: prev.id, title: prev.title, slug: prev.slug };
    }
    if (idx < seriesPosts.length - 1) {
      const next = mockPosts.find(p => p.id === seriesPosts[idx + 1].postId);
      if (next) nextPost = { id: next.id, title: next.title, slug: next.slug };
    }
  }

  const detail: PostDetail = {
    ...post,
    body: mockPostBodies[post.id] || '',
    prevPost,
    nextPost,
  };
  return delay(apiOk(detail));
}
