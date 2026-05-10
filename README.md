# LBlog

个人博客系统，前后端分离架构。

## 项目结构

```
lblog/
├── lblog-server/   Spring Boot 3.5 + Java 17 + MyBatis + MySQL 8
└── lblog-web/      React 19 + TypeScript + Vite + Ant Design 6
```

### 技术栈

| 层 | 后端 | 前端 |
|---|------|------|
| 框架 | Spring Boot 3.5 | React 19 |
| 语言 | Java 17 | TypeScript 6 |
| 数据 | MyBatis 3 + MySQL 8 | — |
| 连接池 | Druid | — |
| 安全 | Spring Security | JWT Bearer Token |
| UI | — | Ant Design 6 |
| 构建 | Maven | Vite 8 |
| 文档 | springdoc-openapi (Swagger) | — |

## 功能概览

### 前台

- 文章列表（推荐/最新/最热），分类/标签/专栏筛选，关键词搜索
- 文章详情页 — Markdown 渲染、自动目录、阅读进度条、上下篇导航
- 访客点赞（浏览器指纹去重）、文章浏览量统计
- 评论系统 — 嵌套回复、审核机制

### 后台管理

- 文章 CRUD — Markdown 编辑器、草稿/发布/私密、slug 自动生成
- 分类/标签/专栏管理 — 树形分类、专栏文章排序
- 全站内容管理 — 文章/评论批量审核
- 用户管理 — 角色分配（admin/author/user）、重置密码、登录禁用
- 图片管理 — 上传、引用追踪、未使用清理
- 站点统计 — 文章/浏览/点赞/评论概览
- 站点配置 — 注册开关等动态配置

### 安全

- JWT 双令牌认证（Access + Refresh Token），refresh rotation
- 基于 SHA-256 的数据库令牌持久化，支持吊销
- RBAC 角色权限控制（admin / author / user）
- 登录尝试限制、注册保护

### 亮点

- **三主题切换** — Apple 极简风（亮色）、暗色护眼、书页暖色，通过 CSS 变量 + Ant Design token 统一驱动
- **阅读进度条** — 文章页顶部 2px 蓝色进度条，跟随滚动实时更新
- **骨架屏加载** — 首页和详情页首次加载使用 Skeleton 占位，体验流畅
- **页面过渡动画** — 路由切换 fade-in + slide-up
- **包结构重构** — controller 按角色分组（admin/author/public/auth），vo 按类型分组（request/response/admin）
- **MyBatis 全量替换语义** — 专栏文章排序通过发送完整 ID 列表完成，避免逐条更新

## 快速启动

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8
- Maven 3.9+

### 1. 数据库

在 MySQL 中创建数据库并执行建表语句（见 `lblog-server/src/main/resources/sql/` 或联系项目维护者获取最新 DDL）。

默认连接：`192.168.1.5:3306/iblog`（可在 `application.yml` 中修改）。

### 2. 启动后端

```bash
cd lblog-server
mvn spring-boot:run
```

或通过 IntelliJ IDEA 运行 `LblogServerApplication`。

后端启动后访问：
- API 基础路径：`http://localhost:8099/iblogserver/api/v1/`
- Swagger UI：`http://localhost:8099/iblogserver/swagger-ui.html`

### 3. 启动前端

```bash
cd lblog-web
npm install
npm run dev
```

前端 dev server 默认 `http://localhost:5173`，API 请求自动代理到后端 `localhost:8099`。

## 构建打包

### 后端

```bash
cd lblog-server
mvn clean package -DskipTests
```

产物：`target/lblog-server-*.jar`

### 前端

```bash
cd lblog-web
npm run build
```

产物：`dist/` 目录，可直接由 Nginx 托管或放入后端 `src/main/resources/static/`。

## 部署

### 后端部署

```bash
java -jar lblog-server-*.jar --spring.profiles.active=prod
```

可通过环境变量或外部配置文件覆盖数据库连接等参数：

```bash
java -jar lblog-server.jar \
  --spring.datasource.url=jdbc:mysql://your-host:3306/iblog \
  --spring.datasource.username=your-user \
  --spring.datasource.password=your-password
```

### 前端部署

将 `dist/` 目录内容部署到 Nginx 或其他静态服务器，配置反向代理：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /path/to/lblog-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /iblogserver/ {
        proxy_pass http://127.0.0.1:8099;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 一体化部署

也可将前端构建产物放入后端 `src/main/resources/static/` 目录后再打包，通过 Spring Boot 直接托管前端静态资源 + API。

```bash
cd lblog-web && npm run build
cp -r dist/* ../lblog-server/src/main/resources/static/
cd ../lblog-server && mvn clean package -DskipTests
```

## 数据库设计

共 18 张表，默认数据库 `iblog`（MySQL 8），字符集 `utf8mb4`。

### 核心内容

```sql
-- 文章元数据表
CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '文章标题',
  `slug` varchar(255) NOT NULL COMMENT 'URL标识',
  `excerpt` text COMMENT '摘要',
  `featured_image` varchar(500) DEFAULT NULL COMMENT '特色图片',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-草稿，1-已发布，2-私密',
  `author_id` bigint DEFAULT NULL COMMENT '作者用户ID',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT '评论数',
  `comment_enable` int NOT NULL DEFAULT '0' COMMENT '是否允许评论',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_published` (`status`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章元数据表';

-- 文章内容表
CREATE TABLE `post_contents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '关联文章ID',
  `body` longtext NOT NULL COMMENT '文章正文（Markdown/HTML）',
  `format` varchar(20) NOT NULL DEFAULT 'markdown' COMMENT '内容格式',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delelte` int DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章内容表';

-- 分类表
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '分类名',
  `slug` varchar(100) NOT NULL COMMENT 'URL标识',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类ID',
  `description` varchar(255) DEFAULT NULL COMMENT '分类描述',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_by` bigint DEFAULT NULL COMMENT '创建者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

-- 标签表
CREATE TABLE `tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '标签名',
  `slug` varchar(100) NOT NULL COMMENT 'URL标识',
  `created_by` bigint DEFAULT NULL COMMENT '创建者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';

-- 专栏表
CREATE TABLE `series` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '专栏名称',
  `slug` varchar(255) NOT NULL COMMENT 'URL标识',
  `description` text COMMENT '专栏简介',
  `cover_image_url` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
  `is_completed` tinyint NOT NULL DEFAULT '0' COMMENT '0-未完结，1-已完结',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_by` bigint DEFAULT NULL COMMENT '创建者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专栏表';

-- 文章标签关联表
CREATE TABLE `post_tags` (
  `post_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`post_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章标签关联表';

-- 专栏文章关联表
CREATE TABLE `series_posts` (
  `series_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '专栏内排序',
  PRIMARY KEY (`series_id`,`post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专栏文章关联表';
```

### 用户与权限

```sql
-- 用户表
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录名',
  `password_hash` varchar(255) NOT NULL COMMENT '加密密码',
  `nickname` varchar(100) DEFAULT NULL COMMENT '显示名称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) NOT NULL DEFAULT 'author' COMMENT '角色：admin/author',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-正常，0-禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT '0' COMMENT '是否删除',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `login_count` int DEFAULT '0' COMMENT '登录次数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 用户令牌表
CREATE TABLE `user_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `token_hash` varchar(64) NOT NULL COMMENT 'SHA-256(token)',
  `token_type` varchar(10) NOT NULL COMMENT 'ACCESS / REFRESH',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `revoked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否吊销',
  `replaced_by` varchar(64) DEFAULT NULL COMMENT 'rotation: 被哪个新 token_hash 替换',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expires` (`expires_at`),
  CONSTRAINT `fk_token_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户令牌表';

-- 角色表
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '角色名称：admin/author/user',
  `label` varchar(50) NOT NULL COMMENT '显示名',
  `description` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- 权限表
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL COMMENT '权限编码',
  `label` varchar(50) NOT NULL COMMENT '显示名',
  `module` varchar(50) NOT NULL COMMENT '所属模块',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- 用户角色关联表
CREATE TABLE `user_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE `role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
```

### 评论与互动

```sql
-- 评论表
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `root_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `author_name` varchar(50) NOT NULL,
  `author_avatar` varchar(500) DEFAULT NULL,
  `reply_to_uid` bigint DEFAULT NULL,
  `reply_to_name` varchar(50) DEFAULT NULL,
  `content` text NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `like_count` int NOT NULL DEFAULT '0',
  `reply_count` int NOT NULL DEFAULT '0',
  `ip_address` varchar(45) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `is_delelte` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_post_status` (`post_id`,`status`,`created_at`),
  KEY `idx_root` (`root_id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 点赞记录表
CREATE TABLE `like_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '文章ID',
  `visitor_id` varchar(64) NOT NULL COMMENT '浏览器指纹',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_visitor` (`post_id`,`visitor_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞记录表';
```

### 图片与配置

```sql
-- 图片库
CREATE TABLE `images` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `url` varchar(500) NOT NULL COMMENT '访问URL',
  `storage_path` varchar(500) NOT NULL COMMENT '存储路径',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `mime_type` varchar(50) NOT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小',
  `width` int DEFAULT NULL COMMENT '图片宽度',
  `height` int DEFAULT NULL COMMENT '图片高度',
  `md5` varchar(32) DEFAULT NULL COMMENT '文件MD5',
  `created_by` bigint DEFAULT NULL COMMENT '上传者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_md5` (`md5`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_url` (`url`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片库';

-- 图片引用关系
CREATE TABLE `image_usages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `image_id` bigint NOT NULL COMMENT '图片ID',
  `ref_type` varchar(20) NOT NULL COMMENT '引用类型：post / user / album / ...',
  `ref_id` bigint NOT NULL COMMENT '引用对象ID',
  `field` varchar(20) NOT NULL COMMENT '引用字段：body / featured_image / avatar / cover / ...',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usage` (`image_id`,`ref_type`,`ref_id`,`field`),
  KEY `idx_image_id` (`image_id`),
  KEY `idx_ref` (`ref_type`,`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片引用关系';

-- 站点配置
CREATE TABLE `site_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(500) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点配置';
```

## 项目端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 8099 | Spring Boot，context-path: `/iblogserver` |
| 前端 Dev | 5173 | Vite HMR，代理 `/api` → 后端 |

## 开发约定

- API 响应统一包装：`ApiResponse<T>`，code=0 成功，非零错误
- 分页使用 PageHelper + `PageResult<T>`
- 前端 TypeScript 严格模式，`verbatimModuleSyntax` 启用
- 后端分层：controller → service/impl → mapper → domain
- 数据库表使用软删除（`deleted_at` + `is_delelte`）
