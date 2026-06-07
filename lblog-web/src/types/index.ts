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

// ---- 评论 ----

export interface CommentAuthor {
  id: number;
  nickname: string;
  avatar: string | null;
}

export interface CommentReplyTo {
  id: number;
  nickname: string;
}

export interface Comment {
  id: number;
  author: CommentAuthor;
  content: string;
  replyTo: CommentReplyTo | null;
  likeCount: number;
  replyCount: number;
  createdAt: string;
  status?: number;  // 0=待审核 1=通过 2=驳回（仅本地评论有）
}

export interface CreateCommentRequest {
  content: string;
  parentId?: number | null;
}

// ---- 认证相关 ----

export interface TokenPairVO {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface RefreshRequest {
  refreshToken: string;
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

// 修改密码
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

// 注册
export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
}

// 站点配置
export interface SiteConfig {
  imageBaseUrl: string;
  imageMaxSize: number;
  aiDrawChatEnabled?: boolean;
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

// ---- Admin 全站管理类型 ----

export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  description: string | null;
  sortOrder: number;
  postCount: number;
  createdBy: number;
  creatorName: string;
}

export interface AdminTag {
  id: number;
  name: string;
  slug: string;
  postCount: number;
  createdBy: number;
  creatorName: string;
}

export interface AdminSeries {
  id: number;
  title: string;
  slug: string;
  description: string | null;
  coverImageUrl: string | null;
  categoryId: number | null;
  isCompleted: number;
  sortOrder: number;
  postCount: number;
  createdBy: number;
  creatorName: string;
}

export interface AdminComment {
  id: number;
  author: CommentAuthor;
  replyTo: CommentReplyTo | null;
  content: string;
  likeCount: number;
  replyCount: number;
  createdAt: string;
  postId: number;
  status: number;
  ipAddress: string;
  postTitle: string;
  postSlug: string;
}

// ---- 代办 ----

export interface TodoItem {
  id: number;
  title: string;
  completed: boolean;
  sortOrder: number;
}

export interface Todo {
  id: number;
  title: string;
  note: string | null;
  priority: number;       // 0=低 1=中 2=高
  status: number;         // 0=待办 1=已完成
  dueDate: string | null;
  sortOrder: number;
  tags: string[];
  items: TodoItem[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateTodoRequest {
  title: string;
  note?: string;
  priority?: number;
  dueDate?: string;
  tags?: string[];
}

export interface UpdateTodoRequest {
  title?: string;
  note?: string;
  priority?: number;
  status?: number;
  dueDate?: string;
  sortOrder?: number;
  tags?: string[];
}

export interface SortRequest {
  items: { id: number; sortOrder: number }[];
}

// ---- AI Prompt 管理 ----

export interface AdminPrompt {
  id: number;
  module: string;
  promptKey: string;
  content: string;
  version: number;
  sortOrder: number;
  description: string | null;
  isActive: boolean;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPromptAudit {
  id: number;
  promptId: number;
  module: string;
  promptKey: string;
  oldContent: string | null;
  newContent: string | null;
  oldVersion: number | null;
  newVersion: number | null;
  action: string;
  operator: string;
  remark: string | null;
  createdAt: string;
}

// 密码本
export interface PasswordEntry {
  id: number;
  siteName: string;
  siteUrl: string;
  username: string;
  encryptedPassword: string;
  note: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePasswordEntryRequest {
  siteName: string;
  siteUrl?: string;
  username: string;
  encryptedPassword: string;
  note?: string;
}

export interface UpdatePasswordEntryRequest {
  siteName?: string;
  siteUrl?: string;
  username?: string;
  encryptedPassword?: string;
  note?: string;
}

// 日记本
export interface JournalEntry {
  id: number;
  title: string;
  content: string;
  mood: string;
  moodEmoji: string;
  weather: string;
  journalDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface CalendarDay {
  journalDate: string;
  moodEmoji: string;
}

export interface CreateJournalRequest {
  title?: string;
  content?: string;
  mood?: string;
  moodEmoji?: string;
  weather?: string;
  journalDate: string;
}

export interface UpdateJournalRequest {
  title?: string;
  content?: string;
  mood?: string;
  moodEmoji?: string;
  weather?: string;
}

export const MOOD_OPTIONS = [
  { emoji: '😊', label: '开心', color: '#52c41a' },
  { emoji: '😢', label: '难过', color: '#1677ff' },
  { emoji: '😡', label: '生气', color: '#ff4d4f' },
  { emoji: '😴', label: '疲惫', color: '#8c8c8c' },
  { emoji: '🎉', label: '兴奋', color: '#fa8c16' },
  { emoji: '💪', label: '充实', color: '#722ed1' },
  { emoji: '😰', label: '焦虑', color: '#faad14' },
  { emoji: '😌', label: '平静', color: '#13c2c2' },
  { emoji: '📝', label: '记录', color: '#666' },
] as const;

// Token 管理
export interface SessionInfo {
  id: number;
  userId: number;
  username: string;
  nickname: string;
  tokenType: string;
  tokenPreview: string;
  createdAt: string;
  expiresAt: string;
  revoked: boolean;
  expiringSoon: boolean;
}

export interface BatchOpResult {
  count: number;
}

export interface TokenConfig {
  accessTtl: number;
  refreshTtl: number;
}

export const WEATHER_OPTIONS = [
  { emoji: '☀️', label: '晴' },
  { emoji: '⛅', label: '多云' },
  { emoji: '☁️', label: '阴' },
  { emoji: '🌧️', label: '雨' },
  { emoji: '⛈️', label: '暴雨' },
  { emoji: '❄️', label: '雪' },
  { emoji: '🌬️', label: '大风' },
  { emoji: '🌫️', label: '雾' },
] as const;

// ---- 作者申请 ----

export interface AuthorApplication {
  id: number;
  userId: number;
  username: string;
  nickname: string;
  reason: string;
  status: number;  // 0=待审核 1=通过 2=拒绝 3=需补充
  feedback: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// ---- PDF 阅读器 ----

export interface PdfFile {
  id: number;
  userId: number;
  folderId: number | null;
  filename: string;
  originalName: string;
  fileSize: number;
  totalPages: number;
  sourceType?: string;  // 'UPLOAD' | 'LOCAL'
  createdAt: string;
  updatedAt: string;
}

export interface PdfFolder {
  id: number;
  userId: number;
  parentId: number | null;
  name: string;
  sortOrder: number;
  children: PdfFolder[];
  createdAt: string;
  updatedAt: string;
}

export interface PdfAnnotation {
  id: number;
  pdfId: number;
  pageNum: number;
  userId: number;
  data: string;          // JSON string from DokFlow
  createdAt: string;
  updatedAt: string;
}

export interface PdfBookmark {
  id: number;
  pdfId: number;
  userId: number;
  pageNum: number;
  label: string;
  note?: string;
  createdAt: string;
}

export interface PdfProgress {
  id: number;
  pdfId: number;
  userId: number;
  pageNum: number;
  scrollTop: number;
  updatedAt: string;
}

export interface PdfUserStats {
  totalSize: number;
  quotaBytes: number;
  allowUpload: number;
}

export interface PdfUserQuotaItem {
  userId: number;
  username: string;
  nickname: string;
  quotaBytes: number;
  allowUpload: number;
  fileCount: number;
  totalSize: number;
}
