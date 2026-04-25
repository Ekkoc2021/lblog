// 文章
export interface Post {
  id: number;
  title: string;
  slug: string;
  excerpt: string;
  featuredImage: string | null;
  status: number;
  authorId: number;
  categoryId: number;
  publishedAt: string;
  createdAt: string;
  updatedAt: string;
  // 关联字段
  author?: User;
  category?: Category;
  tags?: Tag[];
  series?: Series;
  viewCount?: number;
  likeCount?: number;
  commentCount?: number;
  commentEnable?: number;
}

// 文章内容
export interface PostContent {
  id: number;
  postId: number;
  body: string;
  format: string;
}

// 文章详情（含正文）
export interface PostDetail extends Post {
  content?: PostContent;
  body?: string;
  prevPost?: AdjacentPost | null;
  nextPost?: AdjacentPost | null;
}

// 上下篇文章
export interface AdjacentPost {
  id: number;
  title: string;
  slug: string;
}

// 用户
export interface User {
  id: number;
  username: string;
  nickname: string;
  email: string;
  avatar: string | null;
  role: string;
}

// 分类
export interface Category {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  description: string | null;
  sortOrder: number;
  postCount?: number;
}

// 标签
export interface Tag {
  id: number;
  name: string;
  slug: string;
  postCount?: number;
}

// 专栏
export interface Series {
  id: number;
  title: string;
  slug: string;
  description: string | null;
  coverImageUrl: string | null;
  categoryId: number | null;
  isCompleted: number;
  sortOrder: number;
  postCount?: number;
}

// 分页
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

// 点赞响应
export interface LikeResponse {
  liked: boolean;
  likeCount: number;
}

// 点赞状态
export interface LikeStatus {
  liked: boolean;
}

// 通用API响应
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// ---- 管理端请求体 ----

export interface CreatePostRequest {
  title: string;
  slug: string;
  excerpt?: string;
  body: string;
  featuredImage?: string | null;
  status: number;
  categoryId?: number | null;
  tagIds?: number[];
  seriesId?: number | null;
  commentEnable: number;
}

export interface UpdatePostRequest {
  title?: string;
  slug?: string;
  excerpt?: string;
  body?: string;
  featuredImage?: string | null;
  status?: number;
  categoryId?: number | null;
  tagIds?: number[];
  seriesId?: number | null;
  commentEnable?: number;
}

export interface CreateCategoryRequest {
  name: string;
  slug: string;
  description?: string;
  parentId?: number | null;
  sortOrder?: number;
}

export interface CreateTagRequest {
  name: string;
  slug: string;
}

export interface CreateSeriesRequest {
  title: string;
  slug: string;
  description?: string;
  coverImageUrl?: string | null;
  categoryId?: number | null;
  isCompleted?: number;
  sortOrder?: number;
}
