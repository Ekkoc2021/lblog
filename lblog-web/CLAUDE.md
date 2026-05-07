# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目边界

**本仓库是 lblog-web（前端）。** Claude Code 的操作范围应当限制在 `lblog-web` 目录内。

- **禁止创建/写入 `lblog-server/` 目录下的任何文件。** 输出产物（文档、计划、脚本等）一律放在 `lblog-web` 目录内，不可写到后端项目路径。
- 修改 `lblog-server/` 或其他非 `lblog-web` 项目的文件前，必须**先向用户说明理由**并等待确认。
- 除非任务明确要求改动后端代码且用户已确认，否则应优先在 `lblog-web` 范围内寻找方案（如 mock 数据、代理配置、前端适配等）。

## Commands

```bash
npm run dev      # Start Vite dev server with HMR (proxy backend at localhost:8099)
npm run build    # Run tsc -b && vite build (typecheck then bundle)
npm run lint     # ESLint (typescript-eslint + react-hooks rules)
npm run preview  # Preview production build
```

## Tech Stack

- **React 19** + TypeScript 6, **Vite 8**, **Ant Design 6**, **React Router 7**
- **react-markdown** with remark-gfm for Markdown rendering
- **@fingerprintjs/fingerprintjs** for visitor-based like/view tracking

## Project Structure

```
src/
├── main.tsx                  # Entry point, renders App
├── App.tsx                   # BrowserRouter, context providers, route definitions
├── pages/                    # Route-level page components
│   ├── Home.tsx              # / — article list with recommend/newest/hot tabs
│   ├── PostDetail.tsx        # /posts/:slug — full article, TOC, like, prev/next
│   ├── SearchResult.tsx      # /search?q= — full-text search results
│   ├── CategoryPosts.tsx     # /category/:slug — filtered article list
│   ├── TagPosts.tsx          # /tag/:slug — filtered article list
│   ├── SeriesPosts.tsx       # /series/:slug — filtered article list
│   └── admin/                # All /admin/* routes
│       ├── PostList.tsx      # Article table with status filter, search, pagination
│       ├── PostEditor.tsx    # Markdown editor with live preview, meta modal
│       ├── CategoryManage.tsx # CRUD table + modal form
│       ├── TagManage.tsx     # CRUD table + modal form
│       ├── SeriesManage.tsx  # CRUD table + modal form
│       └── Statistics.tsx    # Stats cards (static for now)
├── components/               # Reusable UI components
│   ├── ArticleCard.tsx       # Article card with keyword highlight
│   ├── ArticleList.tsx       # Article list with load-more
│   ├── Sidebar.tsx           # Hot posts, categories, tags, series
│   ├── MarkdownRenderer.tsx  # react-markdown wrapper
│   ├── TableOfContents.tsx   # Auto-generated TOC with IntersectionObserver
│   ├── EmptyState.tsx        # Generic empty state
│   └── LoginModal.tsx        # Login form modal
├── contexts/
│   ├── AuthContext.tsx       # Auth state (sessionStorage token)
│   └── SiteDataContext.tsx   # Global site data (categories, tags, series, hot posts)
├── hooks/
│   └── useSearchHistory.ts   # localStorage-backed search history (max 10)
├── services/
│   └── api.ts               # REST client with JWT Bearer auth, 401 handling
├── types/
│   └── index.ts             # All TypeScript interfaces (Post, Category, Tag, Series, etc.)
```

## Key Architecture Notes

- **Routing**: Public routes render inside `MainLayout`; admin routes nest under `AdminLayout` (which requires auth).
- **Auth**: Token stored in `sessionStorage` under `lblog_token`; `AuthContext` provides `login`/`logout`/`isAuthenticated`. Admin pages redirect to a login modal when unauthenticated.
- **Global data**: `SiteDataContext` fetches categories, tags, series, and hot posts once on mount and shares them via context. All filter pages (category/tag/series) match their param against this context.
- **API layer**: `api.ts` wraps `fetch` with JWT auth, 401 auto-logout, and structured `ApiResponse<T>` parsing. The dev server proxies `/api` to `http://localhost:8099/iblogserver` (configured in `vite.config.ts`).
- **Page pattern**: CategoryPosts/TagPosts/SeriesPosts are nearly identical — each resolves its entity from SiteDataContext, then calls `getPosts` with the corresponding filter param (`categoryId`/`tagId`/`seriesId`), plus sort/load-more logic.
- **Article list**: Uses "load more" (not pagination) — `ArticleList` renders all items and shows a "加载更多" button until `dataSource.length >= total`.
- **Post editor**: Drafts auto-saved to localStorage (300ms debounce); slug auto-generated from title unless manually edited.
- **Mock data**: `services/mock.ts` contains realistic sample posts/bodies used for development.

## Dev Server

The Vite dev server proxies `/api/*` to `http://localhost:8099/iblogserver`. The Java backend at `../lblog-server/` provides the actual API. No separate mock server is needed.

## Browser MCP (Chrome 调试)

Chrome 浏览器 MCP 用于调试前端页面。Chrome 安装路径：
```
D:\ProgramFile\other\Google\Chrome\Application\chrome.exe
```

**启动命令：**
```bash
# 必须先关闭所有已运行的 Chrome 进程，再以调试模式启动
powershell "Get-Process chrome | Stop-Process -Force"
"D:/ProgramFile/other/Google/Chrome/Application/chrome.exe" \
  --remote-debugging-port=9222 \
  --no-first-run \
  --no-default-browser-check \
  --user-data-dir="D:/ProgramFile/other/Google/Chrome/UserData"
```

> **注意**：必须同时指定 `--remote-debugging-port` 和 `--user-data-dir` 参数，缺一不可。否则 Chrome 无法开启 DevTools 调试端口。

启动后通过 `mcp__browser-connect__browser_list_tabs` 查看标签页，用 `mcp__browser-connect__browser_connect` 连接指定标签页。

## TypeScript Conventions

- `verbatimModuleSyntax` enabled — use `import type` for type-only imports
- `noUnusedLocals` and `noUnusedParameters` are errors
- `erasableSyntaxOnly` means no enums, no `namespace`, no `constructor parameter properties`
