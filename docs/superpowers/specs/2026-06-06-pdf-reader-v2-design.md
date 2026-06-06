# PDF 阅读器 V2 设计文档

> 日期：2026-06-06 | 基于实测验证的完整设计

---

## 1. 概述

### 1.1 功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| PDF 上传 | 已实现 | multipart 上传，最大 500MB |
| 文件夹管理 | 已实现 | 树形结构，增删改 |
| PDF 列表 | 已实现 | 按文件夹筛选 |
| 标注存取 | 核心已通 | 三种工具 + bridge + 后端 |
| 阅读进度 | 已实现 | 自动保存/恢复 |
| 书签 | 待修复 | 页码和跳转有 bug |
| 左侧面板 | 待优化 | 可折叠 |
| 大 PDF 优化 | 已实现 | disableAutoFetch + Range 请求 |

### 1.2 技术选型

- **PDF 渲染：** PDF.js v6.0.227 官方 Viewer，iframe 嵌入
- **标注引擎：** PDF.js 内置 AnnotationEditor（Highlight + FreeText + Ink）
- **标注存取：** bridge 脚本（注入 viewer.html） + postMessage + 后端 JSON
- **前端框架：** React + TypeScript + Ant Design
- **后端：** Spring Boot + MyBatis + MySQL

---

## 2. 架构

```
┌─────────────────────────────────────────────────────────────┐
│  PdfReaderPage.tsx                                           │
│  ┌───────────────────────┐  ┌──────────────────────────────┐│
│  │ PdfSidebar (可折叠)    │  │ PdfViewer.tsx                ││
│  │ ┌───────────────────┐ │  │ ┌──────────────────────────┐ ││
│  │ │ Tab: 书架          │ │  │ │ iframe                    │ ││
│  │ │  FolderTree        │ │  │ │ ┌──────────────────────┐ │ ││
│  │ │  PdfFileList       │ │  │ │ │ viewer.html           │ │ ││
│  │ ├───────────────────┤ │  │ │ │ + bridge 脚本          │ │ ││
│  │ │ Tab: 书签          │ │  │ │ │                       │ │ ││
│  │ │  BookmarkPanel     │ │  │ │ │ 存: serialize(false)  │ │ ││
│  │ └───────────────────┘ │  │ │ │   → toPlain()         │ │ ││
│  └───────────────────────┘  │ │ │   → postMessage       │ │ ││
│                              │ │ │                       │ │ ││
│                              │ │ │ 取: postMessage       │ │ ││
│                              │ │ │   → deserialize()     │ │ ││
│                              │ │ │   → add() + update()  │ │ ││
│                              │ │ └──────────────────────┘ │ ││
│                              │ └──────────────────────────┘ ││
│                              └──────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
          │ postMessage                    │ REST API
          ▼                                ▼
┌─────────────────────┐     ┌─────────────────────────────────┐
│  bridge 通信协议      │     │  后端 (Spring Boot)              │
│                     │     │                                 │
│  父→iframe:         │     │  PdfController                  │
│    restore-annotations│    │  PdfAnnotationController        │
│    save-annotations  │     │  PdfBookmarkController          │
│    jump-to-page      │     │  PdfProgressController          │
│                     │     │                                 │
│  iframe→父:         │     │                                 │
│    bridge-loaded     │     │                                 │
│    bridge-hooked     │     │                                 │
│    pdf-page-change   │     │                                 │
│    pdf-annotations   │     │                                 │
└─────────────────────┘     └─────────────────────────────────┘
└─────────────────────┘
```

---

## 3. 数据库

```sql
-- 文件夹
CREATE TABLE pdf_folders (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    parent_id  BIGINT NULL,
    name       VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    FOREIGN KEY (parent_id) REFERENCES pdf_folders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PDF 文件
CREATE TABLE pdf_files (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    folder_id     BIGINT NULL,
    filename      VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size     BIGINT NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    total_pages   INT DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_folder (user_id, folder_id),
    FOREIGN KEY (folder_id) REFERENCES pdf_folders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 标注（全量存储，page=0）
CREATE TABLE pdf_annotations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id     BIGINT NOT NULL,
    page_num   INT NOT NULL DEFAULT 0,
    user_id    BIGINT NOT NULL,
    data       JSON NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pdf_page_user (pdf_id, page_num, user_id),
    FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 书签
CREATE TABLE pdf_bookmarks (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id     BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    page_num   INT NOT NULL,
    label      VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pdf_user (pdf_id, user_id),
    FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 阅读进度
CREATE TABLE pdf_progress (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id     BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    page_num   INT DEFAULT 1,
    scroll_top FLOAT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pdf_user (pdf_id, user_id),
    FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. 后端 API

所有接口前缀 `/api/v1/pdf`，需登录 `@PreAuthorize("isAuthenticated()")`。

### 4.1 文件管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/upload` | multipart 上传 PDF | owner |
| GET | `/files?folderId=` | 按文件夹列文件 | owner |
| PUT | `/files/{id}` | 重命名/移动 | owner |
| DELETE | `/files/{id}` | 删除（级联 + 物理文件） | owner |
| GET | `/files/{id}/download` | Range 文件流 | owner |

### 4.2 文件夹

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/folders` | 文件夹树 |
| POST | `/folders` | 创建 `{name, parentId?}` |
| PUT | `/folders/{id}` | 重命名/移动（防循环） |
| DELETE | `/folders/{id}` | 删除（子文件夹级联，文件移至根） |

### 4.3 标注

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{pdfId}/annotations?page=0` | 全量获取 JSON |
| PUT | `/{pdfId}/annotations/page/0` | 全量保存（UPSERT） |

### 4.4 书签

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{pdfId}/bookmarks` | 按页码排序的书签列表 |
| POST | `/{pdfId}/bookmarks` | 添加 `{pageNum, label}` |
| DELETE | `/{pdfId}/bookmarks/{id}` | 删除 |

### 4.5 阅读进度

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{pdfId}/progress` | 获取 `{pageNum, scrollTop}` |
| PUT | `/{pdfId}/progress` | 更新（UPSERT） |

---

## 5. 标注数据格式

存储为 `[{key, value}]` 数组，`value` 是 `editor.serialize(false)` 经 `toPlain()` 后的纯 JSON。每个 `value` 包含 `pageIndex` 和 `annotationType`。

### 高亮（annotationType=9）
```json
{"key": "editor_0", "value": {"annotationType":9, "pageIndex":0,
  "rect":[147.6,103.5,293.2,133.3], "color":[255,255,152], "opacity":1,
  "outlines":{"points":[[148.2,126.4,...]]}, "quadPoints":[[148.2,133.3,...]]}}
```

### 文字（annotationType=3）
```json
{"key": "editor_1", "value": {"annotationType":3, "pageIndex":0,
  "rect":[121.4,561.1,301.4,581.1], "color":[0,0,0], "fontSize":10,
  "value":"批注内容"}}
```

### 画笔（annotationType=15）
```json
{"key": "editor_2", "value": {"annotationType":15, "pageIndex":0,
  "rect":[100,200,500,400], "color":[255,0,0], "thickness":3,
  "paths":{"lines":[{"points":[100.5,200.3,...],"bezier":[...]}],"points":[[...]]}}}
```

---

## 6. 书签功能修正

### 6.1 当前问题

| 问题 | 文件:行 | 原因 |
|------|---------|------|
| 添加书签页码总是 1 | BookmarkPanel.tsx:30 | `addPdfBookmark(pdfId, 1, label)` 写死了 1 |
| 点击书签不跳页 | PdfSidebar.tsx:45 | `onJumpToPage={(p) => {}}` 空回调 |

### 6.2 修正方案

**PdfReaderPage：** 维护 `currentPage` 状态，从 `pdf-page-change` postMessage 事件更新。

```tsx
// PdfReaderPage.tsx 新增
const [currentPage, setCurrentPage] = useState(1);

// postMessage handler 中新增
if (e.data.type === 'pdf-page-change') {
    setCurrentPage(e.data.page);
}
```

**PdfSidebar：** 接收 `currentPage` 和 `onJumpToPage` props，传给 BookmarkPanel。

```tsx
// PdfSidebar.tsx 改动
interface Props {
    // ...existing
    currentPage: number;
    onJumpToPage: (page: number) => void;
}

<BookmarkPanel
    pdfId={selectedFile.id}
    currentPage={currentPage}
    onJumpToPage={onJumpToPage}
/>
```

**BookmarkPanel：** 添加时用 `currentPage`，点击调 `onJumpToPage`。

```tsx
// BookmarkPanel.tsx 改动
interface Props {
    pdfId: number;
    currentPage: number;        // 新增
    onJumpToPage: (page: number) => void;
}

// 添加书签时
await addPdfBookmark(pdfId, currentPage, label);  // 不是 1

// 点击书签时
onClick={() => onJumpToPage(b.pageNum)}
```

**PdfViewer：** 接收 `onPageChange` callback，通过 postMessage 告知父页面。需要新增 `jump-to-page` 消息监听。

bridge 中新增：
```js
window.addEventListener('message', function(evt) {
    if (evt.data.type === 'jump-to-page' && app.pdfViewer) {
        app.pdfViewer.currentPageNumber = evt.data.page;
    }
});
```

父页面跳页时：
```tsx
const jumpToPage = (page: number) => {
    iframeRef.current?.contentWindow?.postMessage(
        { type: 'jump-to-page', page }, '*'
    );
};
```

---

## 7. 保存按钮

**位置：** 左侧 PdfSidebar 顶部，上传按钮旁边。

**流程：**
```
用户点"保存" → PdfSidebar.onSaveAnnotations()
  → postMessage({type:'save-annotations'}) 到 iframe
  → bridge: 遍历 annotationStorage, serialize(false), toPlain(), 发送 pdf-annotations
  → PdfViewer: PUT /pdf/{id}/annotations/page/0
```

**bridge 改动：** 去掉 `setInterval` 轮询，改用消息触发。完整 bridge 脚本（含 `toPlain()`、`restore-annotations`、TypedArray 还原）见《标注渲染踩坑记录》第 11 节。

```js
// 新增消息处理（和其他 handler 并列在 window.addEventListener('message', ...) 中）
if (evt.data.type === 'save-annotations' && app.pdfDocument) {
    var storage = app.pdfDocument.annotationStorage;
    var data = [];
    for (var entry of storage) {
        var val = entry[1];
        if (val && typeof val.serialize === 'function') {
            var s = toPlain(val.serialize(false));  // toPlain 定义在完整 bridge 中
            s.pageIndex = val.pageIndex;
            data.push({key: entry[0], value: s});
        }
    }
    window.parent.postMessage({
        type: 'pdf-annotations',
        data: JSON.stringify(data)
    }, '*');
}
```

**PdfSidebar 改动：** `onSaveAnnotations` prop，触发时通知 PdfReaderPage。

```tsx
interface Props {
    onSaveAnnotations?: () => void;
    // ...
}

// 上传按钮旁边加保存按钮
<Button size="small" icon={<SaveOutlined />} onClick={onSaveAnnotations}>
    保存
</Button>
```

---

## 8. postMessage 协议（完整）

### 8.1 iframe → 父页面

| type | 触发时机 | data |
|------|---------|------|
| `bridge-loaded` | bridge 脚本执行 | `{}` |
| `bridge-hooked` | PDFViewerApplication 就绪 | `{}` |
| `pdf-page-change` | 用户翻页 | `{page: number}` |
| `pdf-annotations` | 用户点保存按钮 | `{data: string}`（JSON 数组） |

### 8.2 父页面 → iframe

| type | 触发时机 | data |
|------|---------|------|
| `restore-annotations` | bridge-hooked 后 | `{data: [{key, value}]}` |
| `save-annotations` | 用户点保存按钮 | `{}` |
| `jump-to-page` | 点击书签 | `{page: number}` |

---

## 9. 后端方案

### 9.1 PdfStorage

独立存储接口，basePath 通过配置指定。和现有 FileStorage 同接口，额外加 `getFile()`。

```java
public interface PdfStorage {
    StorageResult store(InputStream stream, String filename, long size, String contentType) throws IOException;
    Resource getFile(String path);  // 给 Range 下载用
    void delete(String path);
}

// 实现：本地文件系统
public class PdfFileStorage implements PdfStorage {
    private final String basePath;  // 来自配置 pdf.storage.path
    // store → basePath/{userId}/{uuid}.pdf
    // getFile → 返回文件 Resource（Spring 原生支持 Range）
    // delete → 物理删除
}
```

### 9.2 配置

```yaml
pdf:
  storage:
    path: ./uploads/pdf
```

### 9.3 PdfService 改动

将原来的 `FileStorage` 注入换成 `PdfStorage`。其他方法签名不变。

### 9.4 与现有 PdfController 的关系

```java
// PdfController.upload():
//   pdfStorage.store(inputStream, storedName, size, contentType)  ← 文件落盘
//   pdfService.upload(userId, file, folderId)                      ← 写 DB

// PdfController.download():
//   Resource resource = pdfStorage.getFile(pdfFile.getFilePath())  ← 读文件
//   return ResponseEntity.ok().body(resource)                      ← Spring 自动处理 Range
```

---

## 10. 左侧面板可折叠

PdfReaderPage 已有 `sidebarCollapsed` 状态和 Sider 的 `collapsedWidth={0}`。需要在侧边栏边缘加一个拖拽手柄或折叠按钮。

在 PdfSidebar 右侧边缘加折叠按钮：

```tsx
// PdfReaderPage 工具栏新增
<button onClick={() => setSidebarCollapsed(v => !v)}>
    {sidebarCollapsed ? '☰' : '✕'}
</button>
```

Sider 已有 `collapsedWidth={0}`，折叠时宽度为 0。

---

## 11. 文件清单

### 后端（22 文件）

| 层 | 文件 |
|----|------|
| domain | PdfFile.java, PdfFolder.java, PdfAnnotation.java, PdfBookmark.java, PdfProgress.java |
| vo | PdfFileVO.java, PdfFolderVO.java, PdfAnnotationRequest.java, PdfBookmarkRequest.java, PdfProgressRequest.java |
| mapper | PdfFileMapper.java + XML, PdfFolderMapper.java + XML, PdfAnnotationMapper.java + XML, PdfBookmarkMapper.java + XML, PdfProgressMapper.java + XML |
| service | PdfService.java |
| controller | PdfController.java, PdfAnnotationController.java, PdfBookmarkController.java, PdfProgressController.java |
| sql | pdf_reader_v1.sql |

### 前端（12 文件）

| 文件 | 职责 |
|------|------|
| `public/pdfjs/` | PDF.js v6.0.227 发布包 |
| `public/pdfjs/web/viewer.html` | 已注入 bridge + CSP 改动 |
| `src/pages/PdfReaderPage.tsx` | 全屏页面，状态中心，postMessage 处理 |
| `src/components/pdf/PdfViewer.tsx` | iframe + 通信 |
| `src/components/pdf/PdfSidebar.tsx` | 左侧面板（书架/书签） |
| `src/components/pdf/FolderTree.tsx` | 递归文件夹树 |
| `src/components/pdf/PdfFileList.tsx` | 文件列表 |
| `src/components/pdf/BookmarkPanel.tsx` | 书签面板（待修正） |
| `src/components/pdf/PdfUploadModal.tsx` | 上传弹窗 |
| `src/types/index.ts` | PdfFile 等类型 |
| `src/services/api.ts` | API 函数 |
| `src/App.tsx` | `/reader` 路由 + Toolbox 入口 |

---

## 12. 实现顺序

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | PDF.js 部署 + viewer.html 改造 + bridge 脚本 | ✅ 已通 |
| 2 | 标注存取全链路 | ✅ 已通 |
| 3 | 阅读进度 | ✅ 已通 |
| 4 | PdfStorage 后端（独立存储接口） | ❌ 待做 |
| 5 | 保存按钮（替换轮询） | ❌ 待做 |
| 6 | 书签修正（页码 + 跳转） | ❌ 待修 |
| 7 | 文件夹管理 | ✅ 基本可用 |
| 8 | PDF 上传 | ✅ 基本可用 |
| 9 | 左侧面板可折叠 | ❌ 待做 |
| 10 | 清理提交历史 | ❌ 待做 |
