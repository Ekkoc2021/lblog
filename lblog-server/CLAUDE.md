# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build the project (skip tests for speed during development)
mvn clean install -DskipTests

# Run the application (starts on port 8099, context-path: /iblogserver)
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=LblogServerApplicationTests
```

## Project Overview

Spring Boot 3.5 + Java 17 blog backend API server. Serves the `lblog-web` React frontend (sibling directory). Uses MySQL 8, MyBatis, Druid connection pool, Spring Security, Log4j2.

**API base path:** `/iblogserver/api/v1/`  
**Port:** 8099  
**Swagger UI:** springdoc-openapi (auto-generated)

## Architecture (Layered)

| Layer | Location | Role |
|-------|----------|------|
| Controller | `controller/` | REST endpoints, input validation |
| Service | `service/` + `service/impl/` | Business logic |
| Mapper | `mapper/` | MyBatis interfaces (SQL in `resources/com/yang/lblogserver/mapper/*.xml`) |
| Domain | `domain/` | DB entity classes (Lombok `@Data`) |
| VO | `vo/` | API response DTOs, one per response shape |
| Common | `common/` | `ApiResponse<T>`, `PageResult<T>`, `GlobalExceptionHandler` |

## Key Controllers

- **HomeController** — Public API: posts list (paginated, filterable by category/tag/series/keyword), post detail, categories, tags, series, hot posts, like/unlike (visitor fingerprint via `X-Visitor-Id`)
- **AdminController** — CRUD for posts/categories/tags/series, slug uniqueness check, series post ordering, site statistics. **Note:** currently uses hardcoded `TEST_USER_ID = 1L` — no real auth guard on admin endpoints yet
- **AuthController** — Login (temporary: direct password compare from DB, token format `lblog_{userId}`), `/me`, `/logout`. **Planned:** migrate to bcrypt + JWT
- **UploadController** — Image upload to local filesystem (`uploads/` dir, date-partitioned). **Planned:** migrate to OSS/cloud storage

## Domain Model (6 core tables)

- `posts` — article metadata (title, slug, status, view/like/comment counts, soft delete)
- `post_contents` — article body (1:1 with posts, Markdown)
- `categories` — article categories
- `tags` — article tags (M:N via `post_tags` join table)
- `series` — article series/collections (M:N via `series_posts` join table with `sort_order`)
- `users` — authors
- `like_records` — visitor likes keyed by fingerprint ID

## Database

MySQL 8 on `192.168.1.5:3306/iblog`. Druid connection pool configured in `application.yml`. MyBatis `map-underscore-to-camel-case: true`.

## Notable Config

- **Spring Security:** `ProjectConfig.java` — currently permits all requests (`anyRequest().permitAll()`), form login enabled, CORS allows `localhost:4200`, stateless session, CSRF disabled. `CustomAuthenticationProvider` exists but is not wired to the filter chain yet.
- **Log4j2:** Rolling file appenders in `service-logs/` with daily rotation and size-based triggers. Separate files for all/debug/info/warn/error + JSON error log.
- **Static resources:** Disabled (`add-mappings: false`). Only `/uploads/**` is mapped for file serving.

## Frontend (sibling project)

The `lblog-web` directory contains the React 19 + TypeScript + Vite + Ant Design frontend. Development server proxies `/api` → `localhost:8099/iblogserver`.

## File Modification Boundaries

This project (`lblog-server`) is the primary workspace. **Prefer to implement all changes within `lblog-server`.** When the task involves files in sibling projects (e.g., `lblog-web`), first assess:

1. **Is the change really needed on the frontend side?** Many features (validation, error handling, data transformation) can be handled entirely on this backend.
2. **Could the same goal be achieved by extending a backend API, adding a field, or adjusting a response?** Exhaust backend options before touching the frontend.
3. **If a frontend change is unavoidable**, only modify the minimum necessary files to satisfy the API contract (types, API service functions), prefer mirroring patterns already established in `lblog-server`.

## Helpers & Conventions

- API responses always wrap in `ApiResponse<T>` — code 0 = success, non-zero = error
- Pagination uses `PageHelper` + `PageResult<T>`
- Validation via `jakarta.validation` annotations on controller params and request bodies (`@Validated` at class level)
- Global exception handler normalizes validation/bad request/404/500 errors into `ApiResponse`
- Slug uniqueness is checked before creating/updating posts and series
- Series posts use "full replace" semantics — reorder/link endpoints send the complete ordered post ID list
- Posts use soft delete (`deleted_at` + `is_delelte` flag)
