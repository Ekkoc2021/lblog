# PDF 阅读器设计文档

> 日期：2026-06-06 | 版本：1

## 1. 概述

在工具箱中新增在线 PDF 阅读器，支持高亮、画笔、下划线、便签等批注功能。标注数据保存到服务器，下次打开可继续阅读和批注。支持大文件按需加载（HTTP Range 请求 + 滑动窗口缓存），记录阅读进度。

**核心能力：**
- 用户上传/管理 PDF（私人书架 + 文件夹分类）
- PDF 批注：高亮、画笔、下划线、文字便签
- 书签快速定位
- 大文件按需加载（500MB+ 支持）
- 阅读进度记录，下次自动恢复

## 2. 技术选型

- **PDF 渲染**：PDF.js (pdfjs-dist)，HTTP Range 请求按需加载
- **批注引擎**：DokFlow PDF Editor（开源 MIT，React + Konva + PDF.js）
- **前端**：React + TypeScript + Ant Design
- **后端**：Spring Boot + MyBatis + MySQL
- **标注存储**：MySQL JSON 列，按页存储

## 3. 数据库

### 3.1 pdf_folders — 文件夹

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| user_id | BIGINT NOT NULL | 用户ID |
| parent_id | BIGINT NULL | 父文件夹ID，NULL 为根 |
| name | VARCHAR(100) NOT NULL | 文件夹名 |
| sort_order | INT DEFAULT 0 | 排序 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 3.2 pdf_files — PDF 文件

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| user_id | BIGINT NOT NULL | 用户ID |
| folder_id | BIGINT NULL | 所属文件夹 |
| filename | VARCHAR(255) NOT NULL | 存储文件名(UUID) |
| original_name | VARCHAR(255) NOT NULL | 原始文件名 |
| file_size | BIGINT NOT NULL | 字节数 |
| file_path | VARCHAR(500) NOT NULL | 服务器物理路径 |
| total_pages | INT DEFAULT 0 | PDF 总页数 |
| created_at | DATETIME | 上传时间 |
| updated_at | DATETIME | 更新时间 |

### 3.3 pdf_annotations — 标注（按页）

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| pdf_id | BIGINT FK | 文件ID |
| page_num | INT NOT NULL | 页码(1-based) |
| user_id | BIGINT NOT NULL | 用户ID |
| data | JSON NOT NULL | DokFlow 标注 JSON 数组 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

唯一约束：`(pdf_id, page_num, user_id)`

### 3.4 pdf_bookmarks — 书签

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| pdf_id | BIGINT FK | 文件ID |
| user_id | BIGINT NOT NULL | 用户ID |
| page_num | INT NOT NULL | 页码 |
| label | VARCHAR(100) NOT NULL | 书签名 |
| created_at | DATETIME | 创建时间 |

### 3.5 pdf_progress — 阅读进度

| 列 | 类型 | 说明 |
|----|------|------|
| pdf_id | BIGINT FK | 文件ID |
| user_id | BIGINT NOT NULL | 用户ID |
| page_num | INT DEFAULT 1 | 当前页码 |
| scroll_top | FLOAT DEFAULT 0 | 页内滚动偏移(px) |
| updated_at | DATETIME | 更新时间 |

唯一约束：`(pdf_id, user_id)`

## 4. 后端 API

所有接口前缀 `/api/v1/pdf`，需登录。

### 4.1 文件管理

| 方法 | 路径 | 说明 | 权限校验 |
|------|------|------|---------|
| POST | `/upload` | 上传 PDF，multipart | owner |
| GET | `/files?folderId=` | 按文件夹列文件 | owner |
| GET | `/files/{id}` | 文件详情 | owner |
| PUT | `/files/{id}` | 重命名/移动 | owner |
| DELETE | `/files/{id}` | 删除文件+标注+书签+进度 | owner |
| GET | `/files/{id}/download` | Range 请求读取 PDF | owner |

### 4.2 文件夹

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/folders` | 文件夹树 |
| POST | `/folders` | 创建文件夹 |
| PUT | `/folders/{id}` | 重命名/移动(防循环) |
| DELETE | `/folders/{id}` | 删除(文件移根目录) |

### 4.3 标注

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{pdfId}/annotations?page=50` | 获取某页标注 |
| PUT | `/{pdfId}/annotations/page/{pageNum}` | 保存某页标注(UPSERT) |

### 4.4 书签

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{pdfId}/bookmarks` | 书签列表(按页码排序) |
| POST | `/{pdfId}/bookmarks` | 添加书签 |
| DELETE | `/{pdfId}/bookmarks/{id}` | 删除书签 |

### 4.5 阅读进度

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{pdfId}/progress` | 获取进度 |
| PUT | `/{pdfId}/progress` | 更新进度(UPSERT) |

### 4.6 上传校验

- 文件类型：MIME `application/pdf`
- 文件大小：最大 500MB
- 上传后：PDF.js 读取总页数存 `total_pages`
- 损坏 PDF：读页数失败则删文件，返回错误提示

## 5. 前端设计

### 5.1 布局

全屏页面 `/reader`，三栏结构：

```
┌──────────────────────────────────────────┐
│  ← 返回  [📁书架][📑书签]   页码 128/680│  顶部栏
├────────┬─────────────────────────────────┤
│ 左侧   │  工具栏: ✏️画笔 🖊高亮 📝便签   │
│ 面板   │  ─────────────────────────────  │
│ 300px  │                                │
│ 可折叠 │      PDF.js 渲染 + Konva 批注   │
│        │                                │
│        │                                │
├────────┴─────────────────────────────────┤
│  ◀ 128 / 680 ▶  [跳转:___]             │  底部栏
└──────────────────────────────────────────┘
```

### 5.2 左侧面板

两种模式切换：

**书架模式：** 文件夹树 + 文件列表，支持搜索、拖拽移动、右键菜单（重命名/移动/删除）

**书签模式：** 当前 PDF 的书签列表，按页码排序，点击跳转。支持添加/删除。

### 5.3 批注工具

- 🖐 拖动模式（默认）
- ✏️ 画笔（自由勾画，支持颜色+粗细）
- 🖊 高亮（划选文字）
- 📝 便签（点击任意位置）
- ⌨ 下划线
- ↩ 撤销
- 🎨 颜色面板
- 🔲 粗细面板

### 5.4 加载策略（大 PDF 优化）

滑动窗口：

```
用户在第 50 页：
  视口: p48-p50（完整渲染）
  预加载: p30-p47, p51-p70（后台静默）
  已清理: p1-p29, p71+（释放内存）
  未加载: 其余页（占位 div）

翻到第 51 页 → 窗口滑动 → 清理 p30，加载 p71
```

- PDF.js 使用 HTTP Range 请求，只下载渲染所需字节
- 页面缓存：默认 10 页，超出窗口即 `cleanup()`
- 标注按页加载：只请求可见页的标注数据

### 5.5 标注保存策略

- 触发时机：翻页、切换工具、关闭阅读器、每 30s 自动保存
- 防抖：同一页 5 秒内不重复保存
- 异常处理：失败标记脏页，下次翻回重试
- 离线兜底：网络断连时写 localStorage，恢复后批量同步

### 5.6 阅读进度

- 翻页时自动保存（防抖 500ms）
- 关闭时保存
- 下次打开：GET progress → 跳转到上次位置 → 加载标注

### 5.7 文件清单

| 层 | 文件 | 说明 |
|----|------|------|
| 后端 domain | PdfFile, PdfFolder, PdfAnnotation, PdfBookmark, PdfProgress | 5 个实体 |
| 后端 VO | 对应 Request/VO 类 | |
| 后端 mapper | 5 个 Mapper + XML | |
| 后端 service | PdfService | 统一业务逻辑 |
| 后端 controller | PdfController, PdfAnnotationController, PdfBookmarkController, PdfProgressController | 4 个 |
| 前端 types | 新增 5 个接口 | |
| 前端 services | api.ts 新增 15 个函数 | |
| 前端页面 | PdfReaderPage.tsx | 全屏页面 |
| 前端组件 | PdfSidebar, FolderTree, PdfFileList, BookmarkPanel, PdfViewer, PdfToolbar, PdfPageNav, PdfUploadModal | 8 个 |
| 前端修改 | App.tsx, DrawFloatingButton.tsx | 路由+入口 |
| SQL | pdf_reader_v1.sql | 5 张表 |
| 配置文件 | application.yml multipart max-file-size=500MB | |

## 6. 边界处理

| 场景 | 处理 |
|------|------|
| 非 PDF 上传 | 前端 MIME + 后端二次校验 |
| 文件 > 500MB | 前端 + 后端双重限制 |
| 上传中断 | 事务回滚 + 物理文件清理 |
| 损坏 PDF | 读页数失败 → 删文件 → 提示"无法识别" |
| 同名文件 | 文件名加 _(1) 后缀 |
| 文件夹循环嵌套 | 移动时校验目标 ≠ 自身/子文件夹 |
| 删除非空文件夹 | 子文件夹级联删除，文件移到根目录 |
| 标注保存失败 | 标记脏页，重试 3 次，失败提示 |
| 网络断连 | localStorage 缓存，恢复后批量同步 |
| 翻页过快 | AbortController 取消旧请求 |
| 内存超限 | 超过 30 页强制清理最远页面 |
| PDF 被删除 | 书架去掉了，已打开的提示"不存在" |
