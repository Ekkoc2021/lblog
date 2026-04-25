这是一个个人博客项目。

lblog-server：博客后台，技术栈主要是Spring boot

- spring boot 3+
- mybatis

lblog-web：博客前台，技术栈主要是React

- React
- anti design

# lblog-server

## 数据库设计

核心内容

- 文章：文章相关元数据，和文章内容分开存放
- 内容：具体的文章内容
- 分类：文章类型 
- 标签：文章标签，方便检索 
- 专栏：某个分类下可以拥有多个专栏 

用户相关（可以先不做！）

- 用户：博客用户 用户管理，鉴权

想做成多用户的博客，但是目前就做成单用户即可。后续再扩展，所以核心内容最好都包含用户相关的数据。现在要做的是做出一个拥有核心功能的项目出来，满足现有需求的同时，保证一定的扩展性，比如后续会扩展评论相关的需求等。

建表语句

```sql
-- 1. 用户表
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录名',
  `password_hash` varchar(255) NOT NULL COMMENT '加密密码',
  `nickname` varchar(100) DEFAULT NULL COMMENT '显示名称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) NOT NULL DEFAULT 'author' COMMENT '角色：admin/author',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-正常，0-禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
   `is_delelte` int DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 2. 分类表（补全 updated_at、deleted_at）
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '分类名',
  `slug` varchar(100) NOT NULL COMMENT 'URL标识',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类ID',
  `description` varchar(255) DEFAULT NULL COMMENT '分类描述',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by` bigint DEFAULT NULL COMMENT '创建者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

-- 3. 标签表（补全 updated_at、deleted_at）
CREATE TABLE `tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '标签名',
  `slug` varchar(100) NOT NULL COMMENT 'URL标识',
  `created_by` bigint DEFAULT NULL COMMENT '创建者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';

-- 4. 专栏表（已有 created_by，补全 deleted_at）
CREATE TABLE `series` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '专栏名称',
  `slug` varchar(255) NOT NULL COMMENT 'URL标识',
  `description` text COMMENT '专栏简介',
  `cover_image_url` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
  `is_completed` tinyint NOT NULL DEFAULT 0 COMMENT '0-未完结，1-已完结',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by` bigint DEFAULT NULL COMMENT '创建者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专栏表';

-- 5. 文章元数据表
CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '文章标题',
  `slug` varchar(255) NOT NULL COMMENT 'URL标识',
  `excerpt` text COMMENT '摘要',
  `featured_image` varchar(500) DEFAULT NULL COMMENT '特色图片',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-草稿，1-已发布，2-私密',
  `author_id` bigint DEFAULT NULL COMMENT '作者用户ID',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览量',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `comment_count` int NOT NULL DEFAULT 0 COMMENT '评论数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `is_delelte` int DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slug` (`slug`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_published` (`status`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章元数据表';

-- 6. 文章内容表（添加时间字段）
CREATE TABLE `post_contents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '关联文章ID',
  `body` longtext NOT NULL COMMENT '文章正文（Markdown/HTML）',
  `format` varchar(20) NOT NULL DEFAULT 'markdown' COMMENT '内容格式',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delelte` int DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章内容表';

-- 7. 文章标签关联表（无需时间字段）
CREATE TABLE `post_tags` (
  `post_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`post_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章标签关联表';

-- 8. 专栏文章关联表（无需时间字段）
CREATE TABLE `series_posts` (
  `series_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '专栏内排序',
  PRIMARY KEY (`series_id`, `post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专栏文章关联表';
```

# lblog-web

进入项目目录

```
  cd E:\workspace\java\lblog\lblog-web
```

安装依赖（首次）

```
  npm install
```

启动开发服务器

```
  npm run dev
```

构建生产版本

```
  npm run build
```

  对接后端接口

  当前前端使用的是 mock 数据，对接后端只需两步：

  1. 配置后端地址

  创建 .env 文件指定后端 API 地址：

```
 VITE_API_BASE_URL=http://localhost:8080/api/v1
```

  2. 修改 services/api.ts

  把 mock 实现替换为真实 HTTP 请求。需要先安装 axios：

  npm install axios

  然后把 src/services/api.ts 改为类似这样：

  import axios from 'axios';

  const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
    timeout: 10000,
  });

  // 响应拦截，提取 data
  request.interceptors.response.use(res => res.data);

  export async function getPosts(params) {
    return request.get('/posts', { params });
  }

  export async function getCategories() {
    return request.get('/categories');
  }

  export async function getTags(params) {
    return request.get('/tags', { params });
  }

  export async function getSeries(params) {
    return request.get('/series', { params });
  }

  接口路径和响应格式按 425/2首页接口文档.md 中定义的来，前后端保持一致即可。核心就是：mock
  函数的入参和返回值不变，只换内部实现，页面代码无需改动。

