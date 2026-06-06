# PDF 阅读器 V2 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 PDF 阅读器剩余功能：PdfStorage、保存按钮、书签修正、侧边栏折叠、分支清理

**Architecture:** PDF.js Viewer (iframe) + bridge 脚本注入 + postMessage 通信 + Spring Boot REST API + MySQL

**Tech Stack:** React + TypeScript + Ant Design + Spring Boot + MyBatis + PDF.js v6.0.227

**参考文档：**
- `docs/superpowers/specs/2026-06-06-pdf-reader-v2-design.md` — 完整设计方案
- `docs/superpowers/specs/2026-06-06-pdf-annotation-rendering-pitfalls.md` — 标注渲染踩坑 + 完整 bridge

---

## 文件结构

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `lblog-server/.../storage/PdfStorage.java` | PDF 存储接口 |
| 新建 | `lblog-server/.../storage/PdfFileStorage.java` | PDF 本地文件存储实现 |
| 修改 | `lblog-server/.../service/PdfService.java` | 注入 PdfStorage 替换 FileStorage |
| 修改 | `lblog-server/.../application.yml` | 新增 `pdf.storage.path` |
| 修改 | `lblog-web/public/pdfjs/web/viewer.html` | bridge 去轮询 + save-annotations + jump-to-page |
| 修改 | `lblog-web/src/components/pdf/PdfViewer.tsx` | 暴露 save() + jumpToPage() |
| 修改 | `lblog-web/src/components/pdf/PdfSidebar.tsx` | 保存按钮 + 折叠按钮 |
| 修改 | `lblog-web/src/components/pdf/BookmarkPanel.tsx` | 修正页码 + 跳转 |
| 修改 | `lblog-web/src/pages/PdfReaderPage.tsx` | 连接所有状态和回调 |

---

### Task 1: PdfStorage 后端

**参考：** V2 设计文档 §9

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/storage/PdfStorage.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/storage/PdfFileStorage.java`
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/service/PdfService.java`
- Modify: `lblog-server/src/main/resources/application.yml`

- [ ] **Step 1: 创建 PdfStorage 接口**

```java
package com.yang.lblogserver.storage;

import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;

public interface PdfStorage {
    StorageResult store(InputStream stream, String filename, long size, String contentType) throws IOException;
    Resource getFile(String path);
    void delete(String path);
}
```

- [ ] **Step 2: 创建 PdfFileStorage 实现**

`StorageResult` 复用现有的：
```java
package com.yang.lblogserver.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;

@Component
public class PdfFileStorage implements PdfStorage {

    private final Path basePath;

    public PdfFileStorage(@Value("${pdf.storage.path}") String basePath) {
        this.basePath = Paths.get(basePath);
        try { Files.createDirectories(this.basePath); } catch (IOException e) {
            throw new RuntimeException("Cannot create PDF storage directory", e);
        }
    }

    @Override
    public StorageResult store(InputStream stream, String filename, long size, String contentType) throws IOException {
        Path filePath = basePath.resolve(filename);
        Files.createDirectories(filePath.getParent());
        Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING);
        StorageResult result = new StorageResult();
        result.setUrl("/api/v1/pdf/files/" + filename + "/download");
        result.setStoragePath(filePath.toString());
        return result;
    }

    @Override
    public Resource getFile(String path) {
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String path) {
        try { Files.deleteIfExists(Paths.get(path)); } catch (IOException ignored) {}
    }
}
```

- [ ] **Step 3: 修改 PdfService — FileStorage → PdfStorage**

```java
// 构造函数注入替换
private final PdfStorage pdfStorage;  // 原来是 FileStorage fileStorage

// upload() 方法中
StorageResult result = pdfStorage.store(file.getInputStream(), storedName, file.getSize(), contentType);

// deleteFile() 方法中
pdfStorage.delete(f.getFilePath());
```

- [ ] **Step 4: 修改 application.yml**

```yaml
pdf:
  storage:
    path: ./uploads/pdf
```

- [ ] **Step 5: 编译验证**

```bash
cd lblog-server && mvn compile -q
```

- [ ] **Step 6: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/storage/PdfStorage.java \
        lblog-server/src/main/java/com/yang/lblogserver/storage/PdfFileStorage.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/service/PdfService.java \
        lblog-server/src/main/resources/application.yml
git commit -m "feat: PdfStorage 独立存储接口 — pdf/{userId}/{uuid}"
```

---

### Task 2: Bridge 脚本 — 去轮询 + save-annotations + jump-to-page

**参考：** V2 设计文档 §7 + §8，踩坑记录 §11（完整 bridge）

**Files:**
- Modify: `lblog-web/public/pdfjs/web/viewer.html` — 替换 bridge 脚本

- [ ] **Step 1: 修改 fix_bridge.py 中的 BRIDGE 变量，然后运行 `python fix_bridge.py`**

替换要点：
1. 去掉 `setInterval` 轮询代码块
2. 在 `window.addEventListener('message', ...)` 中新增：
   - `save-annotations` — 序列化全量标注，发送 pdf-annotations
   - `jump-to-page` — `app.pdfViewer.currentPageNumber = page`
3. 保留原有的 `restore-annotations` 和 `pagechanging`

完整 BRIDGE 代码见《标注渲染踩坑记录》§11，改动部分如下：

```js
// 替换原来的 setInterval 轮询为以下消息处理
window.addEventListener('message', function(evt) {
    // === 恢复标注（已有，保留） ===
    if (evt.data.type === 'restore-annotations' && app.pdfDocument) {
        // ... 完整恢复逻辑（踩坑记录 §11）...
    }
    // === 保存标注（新增） ===
    if (evt.data.type === 'save-annotations' && app.pdfDocument) {
        var storage = app.pdfDocument.annotationStorage;
        var data = [];
        for (var entry of storage) {
            var val = entry[1];
            if (val && typeof val.serialize === 'function') {
                var s = toPlain(val.serialize(false));
                s.pageIndex = val.pageIndex;
                data.push({key: entry[0], value: s});
            }
        }
        window.parent.postMessage({type: 'pdf-annotations', data: JSON.stringify(data)}, '*');
    }
    // === 跳页（新增） ===
    if (evt.data.type === 'jump-to-page' && app.pdfViewer) {
        app.pdfViewer.currentPageNumber = evt.data.page;
    }
});
```

- [ ] **Step 2: 运行 fix_bridge.py 更新 viewer.html**

```bash
cd "E:\workspace\java\lblog" && python fix_bridge.py
```

- [ ] **Step 3: Commit**

```bash
git add lblog-web/public/pdfjs/web/viewer.html fix_bridge.py
git commit -m "fix: bridge 去轮询 + save-annotations + jump-to-page 消息"
```

---

### Task 3: PdfViewer — 暴露 save() 和 jumpToPage()

**Files:**
- Modify: `lblog-web/src/components/pdf/PdfViewer.tsx`

- [ ] **Step 1: PdfViewer 添加 forwardRef 和 useImperativeHandle**

```tsx
import { useEffect, useRef, forwardRef, useImperativeHandle } from 'react';
import type { PdfFile } from '../../types';
import {
  getPdfDownloadUrl, getPdfProgress, savePdfProgress,
  savePdfAnnotation, getPdfAnnotation,
} from '../../services/api';

export interface PdfViewerHandle {
  save: () => void;
  jumpToPage: (page: number) => void;
}

interface Props {
  file: PdfFile;
  onPageChange?: (page: number) => void;
}

const PdfViewer = forwardRef<PdfViewerHandle, Props>(({ file, onPageChange }, ref) => {
  const url = getPdfDownloadUrl(file.id);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  useImperativeHandle(ref, () => ({
    save: () => {
      iframeRef.current?.contentWindow?.postMessage(
        { type: 'save-annotations' }, '*'
      );
    },
    jumpToPage: (page: number) => {
      iframeRef.current?.contentWindow?.postMessage(
        { type: 'jump-to-page', page }, '*'
      );
    },
  }));

  // Restore progress (保留)
  useEffect(() => {
    getPdfProgress(file.id).then(res => {
      if (res.data?.pageNum > 0) {
        sessionStorage.setItem(`pdf-progress-${file.id}`, String(res.data.pageNum));
      }
    }).catch(() => {});
  }, [file.id]);

  // postMessage handler (保留，新增 onPageChange 回调)
  useEffect(() => {
    const iframe = iframeRef.current;
    const handler = (e: MessageEvent) => {
      if (!e.data?.type) return;

      if (e.data.type === 'bridge-hooked') {
        getPdfAnnotation(file.id, 0).then(res => {
          if (res.data) {
            try {
              const data = JSON.parse(res.data);
              if (Array.isArray(data) && data.length > 0) {
                iframe?.contentWindow?.postMessage(
                  { type: 'restore-annotations', data }, '*'
                );
              }
            } catch { /* parse error */ }
          }
        }).catch(() => {});
      }

      if (e.data.type === 'pdf-page-change') {
        savePdfProgress(file.id, e.data.page, 0).catch(() => {});
        onPageChange?.(e.data.page);  // 新增：通知父组件
      }

      if (e.data.type === 'pdf-annotations') {
        savePdfAnnotation(file.id, 0, e.data.data).catch(() => {});
      }
    };
    window.addEventListener('message', handler);
    return () => window.removeEventListener('message', handler);
  }, [file.id]);

  return (
    <iframe ref={iframeRef}
      src={`/pdfjs/web/viewer.html?file=${encodeURIComponent(url)}&disableAutoFetch=true`}
      style={{ width: '100%', height: '100%', border: 'none' }}
      title="PDF Viewer" />
  );
});

export default PdfViewer;
```

- [ ] **Step 2: TypeScript 编译**

```bash
cd lblog-web && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add lblog-web/src/components/pdf/PdfViewer.tsx
git commit -m "feat: PdfViewer 暴露 save() + jumpToPage() + onPageChange"
```

---

### Task 4: PdfSidebar + BookmarkPanel — 保存按钮 + 书签修正

**Files:**
- Modify: `lblog-web/src/components/pdf/PdfSidebar.tsx`
- Modify: `lblog-web/src/components/pdf/BookmarkPanel.tsx`

- [ ] **Step 1: BookmarkPanel — 用 currentPage 代替硬编码 1**

```tsx
// BookmarkPanel.tsx 改动
import { SaveOutlined } from '@ant-design/icons';  // 不需要，这是 PdfSidebar 的

interface Props {
  pdfId: number;
  currentPage: number;         // 新增：从父组件获取当前页码
  onJumpToPage: (page: number) => void;
}

// handleAdd 中
await addPdfBookmark(pdfId, currentPage, label);  // 不是 1
```

- [ ] **Step 2: PdfSidebar — 加保存按钮 + 传 currentPage/onJumpToPage**

```tsx
import { SaveOutlined } from '@ant-design/icons';

interface Props {
  selectedFile: PdfFile | null;
  onSelectFile: (file: PdfFile) => void;
  onUploadClick: () => void;
  onSaveAnnotations?: () => void;     // 新增
  currentPage: number;                // 新增
  onJumpToPage: (page: number) => void;  // 新增
  refreshKey: number;
}

// 上传按钮旁边加保存按钮
<div style={{ padding: '8px 12px', display: 'flex', gap: 8 }}>
  <Button type="primary" size="small" icon={<UploadOutlined />} onClick={onUploadClick}>
    上传
  </Button>
  <Button size="small" icon={<SaveOutlined />} onClick={onSaveAnnotations}>
    保存
  </Button>
</div>

// BookmarkPanel 传入 currentPage 和 onJumpToPage
<BookmarkPanel
  pdfId={selectedFile.id}
  currentPage={currentPage}
  onJumpToPage={onJumpToPage}
/>
```

- [ ] **Step 3: TypeScript 编译**

```bash
cd lblog-web && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add lblog-web/src/components/pdf/PdfSidebar.tsx \
        lblog-web/src/components/pdf/BookmarkPanel.tsx
git commit -m "fix: 书签页码 + 保存按钮 + 跳页回调"
```

---

### Task 5: PdfReaderPage — 连接所有状态

**Files:**
- Modify: `lblog-web/src/pages/PdfReaderPage.tsx`

- [ ] **Step 1: PdfReaderPage 新增状态和回调**

```tsx
import { useState, useCallback, useRef } from 'react';
import PdfViewer, { type PdfViewerHandle } from '../components/pdf/PdfViewer';

const PdfReaderPage: React.FC = () => {
  const navigate = useNavigate();
  const viewerRef = useRef<PdfViewerHandle>(null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [selectedFile, setSelectedFile] = useState<PdfFile | null>(null);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);  // 新增

  const handleSave = useCallback(() => {
    viewerRef.current?.save();
    message.success('标注已保存');
  }, []);

  const handleJumpToPage = useCallback((page: number) => {
    viewerRef.current?.jumpToPage(page);
  }, []);

  // ...

  return (
    <Layout style={{ height: '100vh', background: 'var(--color-bg)' }}>
      <div style={{ /* top bar */ }}>
        <ArrowLeftOutlined onClick={() => navigate('/')} />
        <span>PDF 阅读</span>
        {selectedFile && <span>{selectedFile.originalName}</span>}
        <button onClick={() => setSidebarCollapsed(v => !v)}>
          {sidebarCollapsed ? '☰' : '✕'}
        </button>
      </div>

      <Layout>
        <Sider width={300} collapsedWidth={0} collapsed={sidebarCollapsed}>
          <PdfSidebar
            selectedFile={selectedFile}
            onSelectFile={setSelectedFile}
            onUploadClick={() => setUploadVisible(true)}
            onSaveAnnotations={handleSave}         // 新增
            currentPage={currentPage}              // 新增
            onJumpToPage={handleJumpToPage}        // 新增
            refreshKey={refreshKey}
          />
        </Sider>

        <Content>
          {selectedFile ? (
            <PdfViewer
              ref={viewerRef}                     // 新增 ref
              file={selectedFile}
              onPageChange={setCurrentPage}       // 新增
              onToggleSidebar={() => setSidebarCollapsed(v => !v)}
            />
          ) : (
            <div>从左侧书架选择 PDF 开始阅读</div>
          )}
        </Content>
      </Layout>
    </Layout>
  );
};
```

- [ ] **Step 2: TypeScript 编译**

```bash
cd lblog-web && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add lblog-web/src/pages/PdfReaderPage.tsx
git commit -m "feat: PdfReaderPage 连接保存/跳页/当前页/折叠"
```

---

### Task 6: 集成验证

- [ ] **Step 1: 编译后端**

```bash
cd lblog-server && mvn compile
```

- [ ] **Step 2: 编译前端**

```bash
cd lblog-web && npx tsc --noEmit
```

- [ ] **Step 3: 重启后端，验证 API**

```bash
curl http://localhost:8099/iblogserver/api/v1/pdf/files
```

- [ ] **Step 4: 手动测试**

1. 打开 PDF → 荧光笔/画笔/文字标注 → 点保存按钮 → 刷新 → 标注仍在
2. 添加书签 → 书签显示正确页码 → 点击书签 → 跳转到对应页
3. 翻页 → 刷新 → 回到上次阅读位置
4. 点击折叠按钮 → 左侧面板收起/展开

- [ ] **Step 5: Commit**

```bash
git commit -m "chore: 集成验证通过"
```

---

### Task 7: 分支清理

当前 `feature/pdf阅读器` 有 ~30 个调试提交，需要 squash。

**注意：** 这是一个破坏性操作，需要确认。

```bash
# 方法：切出新分支，cherry-pick 有效提交
git checkout dev
git checkout -b feature/pdf-reader-v2

# Cherry-pick 设计文档和正式功能提交，跳过调试提交
git cherry-pick <design-doc-commits>
git cherry-pick <feature-commits>

# 验证后删除旧分支
git branch -D feature/pdf阅读器
```

或者直接 squash merge 到 dev，不保留调试历史。

- [ ] **Step 1: 确认清理策略**

- [ ] **Step 2: 执行清理**

- [ ] **Step 3: 最终 Commit**
