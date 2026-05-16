# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, Run & Debug

All build/run/debug operations must use IntelliJ IDEA MCP tools (不要用 mvn 命令行):

- **Build project** — `mcp__idea__build_project(rebuild: true)`
- **Run/Debug (Spring Boot)** — Run configuration: `LblogServerApplication` (profile: default, port 8099, context-path: `/iblogserver`)
  - 使用 `mcp__idea__xdebug_start_debugger_session(configurationName: "LblogServerApplication")` 以 debug 模式启动
- **Run tests** — Use `mcp__idea__get_run_configurations(filePath: ...)` 找到测试类后执行

- **禁止使用命令行启动后端进程**，包括 mvn spring-boot:run、java -jar 等任何方式。只能通过 IntelliJ IDEA 启动。

## Git

- **不要自动提交**，除非用户明确给出 `提交` 或 `commit` 命令。
- 代码改动后等待用户指示再提交。

## Project Overview

Spring Boot 3.5 + Java 17 blog backend API server. Serves the `lblog-web` React frontend (sibling directory). Uses MySQL 8, MyBatis, Druid connection pool, Spring Security, Log4j2.

**API base path:** `/iblogserver/api/v1/`  
**Port:** 8099  
**Swagger UI:** springdoc-openapi (auto-generated)

## Architecture (Modular)

按业务领域组织模块，每个模块内聚 domain/mapper/service/controller/vo：

| 模块 | 职责 | URL 前缀 |
|------|------|---------|
| `common/` | 跨层公用：ApiResponse, PageResult, GlobalExceptionHandler, WebConfig | — |
| `blog/` | 博客内容：文章、分类、标签、专栏、评论、点赞 | `/api/v1/posts/**`, `/categories/**` 等 |
| `auth/` | 认证授权：用户、角色、权限、Token、安全配置 | `/api/v1/auth/**` |
| `image/` | 图片管理：上传、引用追踪、清理 | `/api/v1/upload/**`, `/api/v1/*/images/**` |
| `site/` | 站点配置 | `/api/v1/config` |
| `ai/` | AI 能力（按子领域分包） | — |
| `ai/draw/` | AI 绘图 | `/api/v1/draw/chat`, `/api/v1/draw/config` |
| `draw/` | 绘图基础：绘图存储、draw.io XML 工具 | `/api/v1/draw/diagrams/**` |
| `storage/` | 文件存储基础设施 | — |

## Key Controllers (by module)

- **blog/controller/public/HomeController** — Public API: posts list (paginated, filterable by category/tag/series/keyword), post detail, categories, tags, series, hot posts, like/unlike (visitor fingerprint via `X-Visitor-Id`)
- **blog/controller/admin/*** — Admin CRUD for posts/categories/tags/series/comments
- **blog/controller/author/*** — Author workspace for own content management
- **auth/controller/AuthController** — Login, register, logout, refresh, change password
- **auth/controller/admin/AdminUserController** — User management, roles, permissions
- **image/controller/UploadController** — Image upload to local filesystem (`uploads/` dir, date-partitioned)
- **image/controller/admin/AdminImageController** — Image statistics, cleanup
- **draw/controller/UserDiagramController** — User diagram storage CRUD
- **ai/draw/controller/DiagramController** — AI-powered draw.io diagram chat (SSE)

**Planned:** migrate uploads to OSS/cloud storage; migrate auth to bcrypt + JWT.

## Domain Model

| 模块 | 表 | 说明 |
|------|-----|------|
| blog | `posts`, `post_contents` | 文章元数据 + Markdown 正文 |
| blog | `categories`, `tags`, `post_tags` | 分类、标签（M:N） |
| blog | `series`, `series_posts` | 专栏（M:N，含 sort_order） |
| blog | `comments` | 评论（树形结构） |
| blog | `like_records` | 访客点赞（fingerprint ID） |
| auth | `users`, `user_roles`, `user_token` | 用户、角色关联、登录令牌 |
| auth | `roles`, `permissions`, `role_permissions` | RBAC 权限模型 |
| image | `images`, `image_usage` | 图片元数据 + 引用追踪 |
| site | `site_config` | 键值对站点配置 |
| draw | `user_diagrams` | 用户绘图表存储 |

## Database

MySQL 8 on `192.168.1.5:3306/iblog`. Druid connection pool configured in `application.yml`. MyBatis `map-underscore-to-camel-case: true`.

## Notable Config

- **Spring Security:** `auth/security/config/SecurityConfig.java` — form login enabled, CORS allows `localhost:4200`, stateless session, CSRF disabled. Role hierarchy: ADMIN > AUTHOR > USER.
- **MyBatis mappers:** `classpath*:com/yang/lblogserver/*/mapper/*.xml` — 按模块分散在 resources 下对应目录
- **Mapper XML namespace** 必须与 Java 接口全限定名一致
- **Log4j2:** Rolling file appenders in `service-logs/` with daily rotation and size-based triggers. Separate files for all/debug/info/warn/error + JSON error log.
- **Static resources:** Disabled (`add-mappings: false`). Only `/uploads/**` is mapped for file serving.

## Helpers & Conventions

- API responses always wrap in `ApiResponse<T>` — code 0 = success, non-zero = error
- Pagination uses `PageHelper` + `PageResult<T>`
- Validation via `jakarta.validation` annotations on controller params and request bodies (`@Validated` at class level)
- Global exception handler normalizes validation/bad request/404/500 errors into `ApiResponse`
- Slug uniqueness is checked before creating/updating posts and series
- Series posts use "full replace" semantics — reorder/link endpoints send the complete ordered post ID list
- Posts use soft delete (`deleted_at` + `is_delelte` flag)

## Output Documents

设计文档、审查报告、技术债务清单等输出文档统一存放在项目根目录下按日期命名的文件夹中：

- 格式：`M-D/`（如 `5-16/`、`5-8/`）
- 目录不存在则自动创建，已存在则直接补充
- 文件名用英文 kebab-case，清晰表达文档内容
