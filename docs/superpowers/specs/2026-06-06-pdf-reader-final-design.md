# PDF 阅读器 — 完整实现文档

> 日期：2026-06-06 | 版本：3（完整可执行）

本文档是 PDF 阅读器标注功能的完整实现指南。按照本文档操作即可完成集成，无需参考任何外部代码。

---

## 1. PDF.js 部署

### 1.1 下载

从 https://github.com/mozilla/pdf.js/releases 下载 `pdfjs-X.X.XXX-dist.zip`，解压。

### 1.2 放入项目

```
lblog-web/public/pdfjs/
  build/
    pdf.mjs
    pdf.worker.mjs
    ...
  web/
    viewer.html
    viewer.mjs
    viewer.css
    images/
    cmaps/
    ...
```

保持 `web/` 和 `build/` 目录结构。viewer.html 内部引用 `../build/pdf.mjs`，不能打乱。

### 1.3 修改 viewer.html

**改动 1：** CSP 加 `'unsafe-inline'`（允许内联 bridge 脚本）。

找到：
```html
content="default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; ...
```

改为：
```html
content="default-src 'none'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; ...
```

**改动 2：** 在 `</head>` 前插入 bridge 脚本。

找到：
```html
  <script src="viewer.mjs" type="module"></script>
  </head>
```

改为：
```html
  <script src="viewer.mjs" type="module"></script>
  <script>
  /* === BRIDGE SCRIPT START === */
  window.parent.postMessage({type:'bridge-loaded'},'*');
  (function(){
    var poll=0;
    function init(){
      var app=window.PDFViewerApplication;
      if(!app||!app.pdfViewer||!app.eventBus){if(++poll<50)setTimeout(init,200);return;}
      console.log('[Bridge] Hooked');
      window.parent.postMessage({type:'bridge-hooked'},'*');

      /* ---- SAVE ---- */
      var lastSize=0;
      setInterval(function(){
        var doc=app.pdfDocument;if(!doc)return;
        var storage=doc.annotationStorage;
        if(storage.size===0){if(lastSize>0){lastSize=0;}return;}
        if(storage.size===lastSize)return;
        lastSize=storage.size;
        var data=[];
        for(var entry of storage){
          try{
            var key=entry[0],val=entry[1];
            if(val&&typeof val.serialize==='function'){
              var s=val.serialize(false);
              if(s){
                // Deep convert TypedArrays to arrays before JSON.stringify
                function toPlain(o){
                  if(o instanceof Float32Array||o instanceof Float64Array||o instanceof Int32Array||o instanceof Uint8Array)return Array.from(o);
                  if(Array.isArray(o))return o.map(toPlain);
                  if(o&&typeof o==='object'&&o.constructor===Object){var r={};for(var k in o)r[k]=toPlain(o[k]);return r;}
                  return o;
                }
                s=toPlain(s);
                data.push({key:key,value:s});
              }
            }
          }catch(e){}
        }
        if(data.length>0){
          console.log('[Bridge] Saving',data.length,'annotations');
          window.parent.postMessage({type:'pdf-annotations',data:JSON.stringify(data)},'*');
        }
      },3000);

      /* ---- RESTORE ---- */
      window.addEventListener('message',function(evt){
        if(evt.data.type!=='restore-annotations'||!app.pdfDocument)return;
        var data=evt.data.data,doc=app.pdfDocument,viewer=app.pdfViewer,restored=0;
        (async function(){
          for(var i=0;i<data.length;i++){
            try{
              var val=data[i].value,key=data[i].key;
              if(!val||typeof val!=='object')continue;
              var pi=val.pageIndex||0;
              var page=viewer._pages[pi];if(!page)continue;
              if(!page.annotationEditorLayer){try{page._renderAnnotationEditorLayer();}catch(e){continue;}}
              var layer=page.annotationEditorLayer.annotationEditorLayer;if(!layer)continue;
              // Restore TypedArrays in paths
              if(val.paths){try{
                if(val.paths.lines&&Array.isArray(val.paths.lines)){
                  val.paths.lines=val.paths.lines.map(function(line){
                    if(line&&typeof line==='object'&&!Array.isArray(line)){
                      if(line.points)line.points=Array.isArray(line.points)?new Float32Array(line.points):line.points;
                      if(line.bezier)line.bezier=Array.isArray(line.bezier)?new Float32Array(line.bezier):line.bezier;
                    }return line;
                  });
                }
                if(val.paths.points&&Array.isArray(val.paths.points)){
                  val.paths.points=val.paths.points.map(function(p){return Array.isArray(p)?new Float32Array(p):p;});
                }
              }catch(e){}}
              var editor=await layer.deserialize(val);
              if(editor){
                if(!editor.div){try{editor.render();}catch(e){}}
                // Fix freetext position and content
                if(val.annotationType===3&&editor.pageDimensions){
                  var pw=editor.pageDimensions[0],ph=editor.pageDimensions[1],r=val.rect;
                  if(r){editor.x=r[0]/pw;editor.y=(ph-r[3])/ph;editor.width=(r[2]-r[0])/pw;editor.height=(r[3]-r[1])/ph;}
                  if(typeof editor.fixAndSetPosition==='function')editor.fixAndSetPosition();
                  if(val.value&&editor.editorDiv){try{editor.editorDiv.textContent=val.value;}catch(e){}}
                  try{editor.show();}catch(e){}
                }
                layer.add(editor);
                doc.annotationStorage.setValue(key,editor);
                restored++;
              }
            }catch(e){console.error('[Bridge] restore error:',e.message||e);}
          }
          console.log('[Bridge] Restored',restored,'/',data.length,'annotations');
          if(restored>0){viewer.update();try{app.pdfViewer.annotationEditorMode={mode:3};setTimeout(function(){app.pdfViewer.annotationEditorMode={mode:0};},100);}catch(e){}}
        })();
      });

      /* ---- PAGE CHANGE ---- */
      app.eventBus.on('pagechanging',function(evt){
        window.parent.postMessage({type:'pdf-page-change',page:evt.pageNumber},'*');
      });
    }
    setTimeout(init,500);
  })();
  /* === BRIDGE SCRIPT END === */
  </script>
  </head>
```

---

## 2. 前端组件

### 2.1 PdfViewer.tsx

`src/components/pdf/PdfViewer.tsx` — iframe 容器 + postMessage 通信。

```tsx
import { useEffect, useRef } from 'react';
import type { PdfFile } from '../../types';
import {
  getPdfDownloadUrl,
  getPdfProgress,
  savePdfProgress,
  savePdfAnnotation,
  getPdfAnnotation,
} from '../../services/api';

interface Props {
  file: PdfFile;
  onToggleSidebar: () => void;
}

const PdfViewer: React.FC<Props> = ({ file }) => {
  const url = getPdfDownloadUrl(file.id);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  // Restore reading progress
  useEffect(() => {
    getPdfProgress(file.id).then(res => {
      if (res.data?.pageNum > 0) {
        sessionStorage.setItem(
          `pdf-progress-${file.id}`,
          String(res.data.pageNum)
        );
      }
    }).catch(() => {});
  }, [file.id]);

  // postMessage handler
  useEffect(() => {
    const iframe = iframeRef.current;

    const handler = (e: MessageEvent) => {
      if (!e.data?.type) return;

      // Bridge ready → fetch and restore saved annotations
      if (e.data.type === 'bridge-hooked') {
        getPdfAnnotation(file.id, 0).then(res => {
          if (res.data) {
            try {
              const data = JSON.parse(res.data);
              if (Array.isArray(data) && data.length > 0) {
                iframe?.contentWindow?.postMessage(
                  { type: 'restore-annotations', data },
                  '*'
                );
              }
            } catch { /* parse error */ }
          }
        }).catch(() => {});
      }

      // Page change → save progress
      if (e.data.type === 'pdf-page-change') {
        savePdfProgress(file.id, e.data.page, 0).catch(() => {});
      }

      // Annotation change → save to backend
      if (e.data.type === 'pdf-annotations') {
        savePdfAnnotation(file.id, 0, e.data.data).catch(() => {});
      }
    };

    window.addEventListener('message', handler);
    return () => window.removeEventListener('message', handler);
  }, [file.id]);

  return (
    <iframe
      ref={iframeRef}
      src={`/pdfjs/web/viewer.html?file=${encodeURIComponent(url)}&disableAutoFetch=true`}
      style={{ width: '100%', height: '100%', border: 'none' }}
      title="PDF Viewer"
    />
  );
};

export default PdfViewer;
```

### 2.2 Types（types/index.ts 新增）

```ts
export interface PdfFile {
  id: number;
  userId: number;
  folderId: number | null;
  filename: string;
  originalName: string;
  fileSize: number;
  totalPages: number;
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
}

export interface PdfBookmark {
  id: number;
  pdfId: number;
  userId: number;
  pageNum: number;
  label: string;
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
```

### 2.3 API 函数（services/api.ts 新增）

```ts
// PDF 文件
export async function uploadPdf(file: File, folderId?: number | null): Promise<ApiResponse<PdfFile>> {
  const fd = new FormData(); fd.append('file', file);
  if (folderId) fd.append('folderId', String(folderId));
  return request<PdfFile>('/api/v1/pdf/upload', { method: 'POST', body: fd });
}
export async function getPdfFiles(folderId?: number | null): Promise<ApiResponse<PdfFile[]>> {
  return request<PdfFile[]>(`/api/v1/pdf/files${folderId != null ? `?folderId=${folderId}` : ''}`);
}
export async function deletePdfFile(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/pdf/files/${id}`, { method: 'DELETE' });
}
export async function updatePdfFile(id: number, originalName?: string, folderId?: number | null): Promise<ApiResponse<null>> {
  const p = new URLSearchParams(); if (originalName) p.set('originalName', originalName);
  if (folderId !== undefined) p.set('folderId', String(folderId));
  return request<null>(`/api/v1/pdf/files/${id}?${p}`, { method: 'PUT' });
}
export function getPdfDownloadUrl(id: number): string {
  return `/api/v1/pdf/files/${id}/download`;
}

// 文件夹
export async function getPdfFolders(): Promise<ApiResponse<PdfFolder[]>> {
  return request<PdfFolder[]>('/api/v1/pdf/folders');
}
export async function createPdfFolder(name: string, parentId?: number | null): Promise<ApiResponse<PdfFolder>> {
  const p = new URLSearchParams(); p.set('name', name);
  if (parentId) p.set('parentId', String(parentId));
  return request<PdfFolder>(`/api/v1/pdf/folders?${p}`, { method: 'POST' });
}
export async function deletePdfFolder(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/pdf/folders/${id}`, { method: 'DELETE' });
}

// 标注
export async function getPdfAnnotation(pdfId: number, page: number): Promise<ApiResponse<string>> {
  return request<string>(`/api/v1/pdf/${pdfId}/annotations?page=${page}`);
}
export async function savePdfAnnotation(pdfId: number, page: number, data: string): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/pdf/${pdfId}/annotations/page/${page}`, {
    method: 'PUT', body: JSON.stringify({ data }),
  });
}

// 书签
export async function getPdfBookmarks(pdfId: number): Promise<ApiResponse<PdfBookmark[]>> {
  return request<PdfBookmark[]>(`/api/v1/pdf/${pdfId}/bookmarks`);
}
export async function addPdfBookmark(pdfId: number, pageNum: number, label: string): Promise<ApiResponse<PdfBookmark>> {
  return request<PdfBookmark>(`/api/v1/pdf/${pdfId}/bookmarks`, {
    method: 'POST', body: JSON.stringify({ pageNum, label }),
  });
}
export async function deletePdfBookmark(pdfId: number, id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/pdf/${pdfId}/bookmarks/${id}`, { method: 'DELETE' });
}

// 阅读进度
export async function getPdfProgress(pdfId: number): Promise<ApiResponse<PdfProgress>> {
  return request<PdfProgress>(`/api/v1/pdf/${pdfId}/progress`);
}
export async function savePdfProgress(pdfId: number, pageNum: number, scrollTop?: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/pdf/${pdfId}/progress`, {
    method: 'PUT', body: JSON.stringify({ pageNum, scrollTop: scrollTop ?? 0 }),
  });
}
```

---

## 3. 数据库

```sql
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

## 4. 标注数据格式

标注统一存为 `[{key: string, value: object}]` 数组，key 是 PDF.js 内部编辑器 ID，value 是 `editor.serialize(false)` 经 `toPlain()` 处理后的纯 JSON。

### 4.1 高亮（Highlight, annotationType=9）

```json
{
  "key": "pdfjs_internal_editor_0",
  "value": {
    "annotationType": 9,
    "pageIndex": 0,
    "rect": [147.6, 103.5, 293.2, 133.3],
    "rotation": 0,
    "color": [255, 255, 152],
    "opacity": 1,
    "isCopy": true,
    "outlines": {
      "points": [[148.2, 126.4, 158.8, 126.4, ...]]
    },
    "quadPoints": [[148.2, 133.3, 293.2, 133.3, ...]],
    "popupRef": "",
    "structTreeParentId": null,
    "thickness": 0
  }
}
```

### 4.2 文字（FreeText, annotationType=3）

```json
{
  "key": "pdfjs_internal_editor_1",
  "value": {
    "annotationType": 3,
    "pageIndex": 0,
    "rect": [121.4, 561.1, 301.4, 581.1],
    "rotation": 0,
    "color": [0, 0, 0],
    "fontSize": 10,
    "value": "这里写了一段批注",
    "isCopy": true
  }
}
```

### 4.3 画笔（Ink, annotationType=15）

```json
{
  "key": "pdfjs_internal_editor_2",
  "value": {
    "annotationType": 15,
    "pageIndex": 0,
    "rect": [100, 200, 500, 400],
    "rotation": 0,
    "color": [255, 0, 0],
    "opacity": 1,
    "thickness": 3,
    "isCopy": true,
    "paths": {
      "lines": [
        {
          "points": [100.5, 200.3, 101.2, 201.1, ...],
          "bezier": [100.5, 200.3, 101.2, 201.1, ...]
        }
      ],
      "points": [[100.5, 200.3, 101.2, 201.1, ...]]
    }
  }
}
```

> **注意：** paths 中的 `points` 和 `bezier` 在 JS 端是 Float32Array，存储时通过 `toPlain()` 转成了普通数组，恢复时需转回 `new Float32Array(arr)`。

---

## 5. 数据流完整时序

```
用户打开 PDF
  │
  ├─ 1. PdfViewer 渲染 <iframe src="viewer.html?file=...">
  │
  ├─ 2. viewer.html 加载 → bridge 脚本执行
  │      → postMessage {type:'bridge-loaded'}
  │
  ├─ 3. PDF.js 初始化 → PDFViewerApplication 就绪
  │      → bridge init() 成功
  │      → postMessage {type:'bridge-hooked'}
  │
  ├─ 4. PdfViewer 收到 bridge-hooked
  │      → GET /api/v1/pdf/{id}/annotations?page=0
  │      → postMessage {type:'restore-annotations', data:[...]}
  │
  ├─ 5. bridge 收到 restore-annotations
  │      → 遍历 data
  │      → layer.deserialize(val)
  │      → layer.add(editor)
  │      → annotationStorage.setValue(key, editor)
  │      → viewer.update()
  │      → 【标注渲染到页面上】
  │
  └─ 6. 每 3 秒轮询
         → annotationStorage 条目 .serialize(false)
         → toPlain() 转 TypedArray
         → postMessage {type:'pdf-annotations', data:JSON}
         → PdfViewer 收到 → PUT /api/v1/pdf/{id}/annotations/page/0
```

---

## 6. 关键技术坑点

| # | 问题 | 错误做法 | 正确做法 |
|---|------|---------|---------|
| 1 | 高亮序列化 | `editor.serialize(true)` 返回 null | `editor.serialize(false)` |
| 2 | 反序列化添加 | `layer.addOrRebuild(editor)` 不渲染 | `layer.add(editor)` + `viewer.update()` |
| 3 | 存储格式 | `annotationStorage.serializable` 不可反序列化 | 遍历 storage 条目逐个 `.serialize(false)` |
| 4 | TypedArray | 直接 `JSON.stringify` 丢失数据 | `toPlain()` 递归转普通数组，恢复时转回 Float32Array |
| 5 | annotationType | 字符串 `"highlight"` | 数字 `9`（deserialize 用数字做 Map 查找） |
| 6 | 文字位置 | 恢复后位置在页面外 | 手动修正 x/y/width/height + `fixAndSetPosition()` |
| 7 | deserialize 返回值 | 返回 null 不报错 | 检查返回值，null 说明数据格式不匹配 |
| 8 | viewer 渲染 | 恢复后不显示 | `viewer.update()` + 短暂激活文字模式 |
| 9 | 画笔 paths | `lines[i].map is not a function` | lines 元素是对象不是数组，只转内部 points/bezier |
| 10 | 高亮 outlines | `outlines` 是对象但 deserialize 期望数组 | 将 `{0: [...], 1: [...]}` 转为 `[[...], [...]]` |
| 11 | 跨域 | iframe 同源，可以访问 contentWindow | 直接访问 `iframe.contentWindow.PDFViewerApplication` |
| 12 | 大 PDF | 全量下载 25MB | `disableAutoFetch=true` + Spring Boot Range 请求 |

---

## 7. 后端 API 汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/pdf/upload` | 上传 PDF |
| GET | `/api/v1/pdf/files?folderId=` | 文件列表 |
| GET | `/api/v1/pdf/files/{id}` | 文件详情 |
| PUT | `/api/v1/pdf/files/{id}` | 重命名/移动 |
| DELETE | `/api/v1/pdf/files/{id}` | 删除文件 |
| GET | `/api/v1/pdf/files/{id}/download` | Range 请求文件流 |
| PUT | `/api/v1/pdf/files/{id}/total-pages` | 更新页数 |
| GET | `/api/v1/pdf/folders` | 文件夹树 |
| POST | `/api/v1/pdf/folders` | 创建文件夹 |
| PUT | `/api/v1/pdf/folders/{id}` | 重命名/移动文件夹 |
| DELETE | `/api/v1/pdf/folders/{id}` | 删除文件夹 |
| GET | `/api/v1/pdf/{id}/annotations?page=0` | 获取标注 |
| PUT | `/api/v1/pdf/{id}/annotations/page/0` | 保存标注 |
| GET | `/api/v1/pdf/{id}/bookmarks` | 书签列表 |
| POST | `/api/v1/pdf/{id}/bookmarks` | 添加书签 |
| DELETE | `/api/v1/pdf/{id}/bookmarks/{id}` | 删除书签 |
| GET | `/api/v1/pdf/{id}/progress` | 阅读进度 |
| PUT | `/api/v1/pdf/{id}/progress` | 更新进度 |

---

## 8. 配置

`application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
```

---

## 9. 后端文件清单

| 文件 | 职责 |
|------|------|
| `domain/PdfFile.java` | 文件实体 |
| `domain/PdfFolder.java` | 文件夹实体（含 children 列表） |
| `domain/PdfAnnotation.java` | 标注实体 |
| `domain/PdfBookmark.java` | 书签实体 |
| `domain/PdfProgress.java` | 进度实体 |
| `vo/PdfFileVO.java` | 文件列表响应 |
| `vo/PdfFolderVO.java` | 文件夹树响应 |
| `vo/PdfAnnotationRequest.java` | 标注保存请求 |
| `vo/PdfBookmarkRequest.java` | 书签请求 |
| `vo/PdfProgressRequest.java` | 进度请求 |
| `mapper/PdfFileMapper.java + XML` | 文件 Mapper |
| `mapper/PdfFolderMapper.java + XML` | 文件夹 Mapper |
| `mapper/PdfAnnotationMapper.java + XML` | 标注 Mapper（含 UPSERT） |
| `mapper/PdfBookmarkMapper.java + XML` | 书签 Mapper |
| `mapper/PdfProgressMapper.java + XML` | 进度 Mapper（含 UPSERT） |
| `service/PdfService.java` | 核心业务逻辑 |
| `controller/PdfController.java` | 文件 + 文件夹 API |
| `controller/PdfAnnotationController.java` | 标注 API |
| `controller/PdfBookmarkController.java` | 书签 API |
| `controller/PdfProgressController.java` | 进度 API |
| `resources/sql/pdf_reader_v1.sql` | 建表 SQL |

## 10. 前端文件清单

| 文件 | 职责 |
|------|------|
| `public/pdfjs/` | PDF.js 官方发布包 |
| `public/pdfjs/web/viewer.html` | 已注入 bridge 脚本 |
| `src/components/pdf/PdfViewer.tsx` | iframe + postMessage |
| `src/components/pdf/PdfSidebar.tsx` | 左侧面板 |
| `src/components/pdf/FolderTree.tsx` | 文件夹树 |
| `src/components/pdf/PdfFileList.tsx` | 文件列表 |
| `src/components/pdf/BookmarkPanel.tsx` | 书签列表 |
| `src/components/pdf/PdfUploadModal.tsx` | 上传弹窗 |
| `src/pages/PdfReaderPage.tsx` | 全屏页面 |
| `src/types/index.ts` | PdfFile 等 5 个接口 |
| `src/services/api.ts` | 18 个 API 函数 |
| `src/App.tsx` | `/reader` 路由 |
