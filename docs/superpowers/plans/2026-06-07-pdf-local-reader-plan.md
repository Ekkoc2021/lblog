# PDF 本地阅读器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持创建书时不传文件，后续可选择上传或本地打开 PDF，服务器只存元数据+标注

**Architecture:** PdfFile 加 `source_type` 字段区分 UPLOAD/LOCAL，LOCAL 书通过 blob URL 加载本地文件。后端新增一个纯元数据创建接口，前端改造上传弹窗为"新建书籍"弹窗

**Tech Stack:** Spring Boot 3.5.7 + MyBatis, React 19 + TypeScript + Ant Design 6 + PDF.js 5

---

### Task 1: SQL Migration

**Files:**
- Create: `lblog-server/src/main/resources/sql/pdf_local_source_type.sql`

- [ ] **Step 1: Write migration SQL**

```sql
ALTER TABLE pdf_files ADD COLUMN source_type VARCHAR(10) DEFAULT 'UPLOAD' AFTER total_pages;
```

- [ ] **Step 2: Execute migration**

Run in MySQL:
```bash
mysql -u root lblog < lblog-server/src/main/resources/sql/pdf_local_source_type.sql
```

Expected: column `source_type` added to `pdf_files` table.

---

### Task 2: Backend Domain + VO

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/PdfFile.java`
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/PdfFileVO.java`

- [ ] **Step 1: Add sourceType to PdfFile.java**

In `PdfFile.java`, add field after `totalPages`:

```java
private String sourceType;
```

- [ ] **Step 2: Add sourceType to PdfFileVO.java**

In `PdfFileVO.java`, add field after `totalPages`:

```java
@Schema(description = "来源类型: UPLOAD/LOCAL")
private String sourceType;
// getter/setter
public String getSourceType() { return sourceType; }
public void setSourceType(String sourceType) { this.sourceType = sourceType; }
```

- [ ] **Step 3: Verify compile**

Use the IDE build tool to verify `PdfFile.java` and `PdfFileVO.java` compile.

---

### Task 3: Backend Mapper XML

**Files:**
- Modify: `lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/PdfFileMapper.xml`
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/PdfFileMapper.java`

- [ ] **Step 1: Update Base_Column_List**

Append `source_type`:

```xml
<sql id="Base_Column_List">
    id, user_id, folder_id, filename, original_name, file_size, file_path, total_pages, source_type, created_at, updated_at
</sql>
```

- [ ] **Step 2: Add sourceType to BaseResultMap**

After the `totalPages` result mapping:

```xml
<result property="sourceType" column="source_type" />
```

- [ ] **Step 3: Add sourceType to FileVOMap**

After the `totalPages` result mapping:

```xml
<result property="sourceType" column="source_type" />
```

- [ ] **Step 4: Update INSERT statement**

Change the insert to include `source_type`:

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO pdf_files (user_id, folder_id, filename, original_name, file_size, file_path, total_pages, source_type, created_at, updated_at)
    VALUES (#{userId}, #{folderId}, #{filename}, #{originalName}, #{fileSize}, #{filePath}, #{totalPages}, #{sourceType}, NOW(), NOW())
</insert>
```

- [ ] **Step 5: Add updateFileWithSource method to mapper XML**

Add after the existing `<update>` block:

```xml
<update id="updateFileWithSource">
    UPDATE pdf_files
    SET original_name = #{originalName},
        filename = #{filename},
        file_size = #{fileSize},
        file_path = #{filePath},
        source_type = 'UPLOAD'
    WHERE id = #{id}
</update>
```

- [ ] **Step 6: Add method to PdfFileMapper.java interface**

```java
int updateFileWithSource(@Param("id") Long id, @Param("originalName") String originalName,
                         @Param("filename") String filename, @Param("fileSize") Long fileSize,
                         @Param("filePath") String filePath);
```

- [ ] **Step 7: Verify compile**

Use the IDE build tool to verify.

---

### Task 4: Backend Service — createMetadata + upload updates

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/service/PdfService.java`

- [ ] **Step 1: Add createMetadata method**

Add after the `upload` method:

```java
/** Create a book entry without uploading a file */
public PdfFile createMetadata(Long userId, String name, Long folderId) {
    PdfFile pdfFile = new PdfFile();
    pdfFile.setUserId(userId);
    pdfFile.setFolderId(folderId);
    pdfFile.setOriginalName(name);
    pdfFile.setTotalPages(0);
    pdfFile.setSourceType("LOCAL");
    fileMapper.insert(pdfFile);
    return pdfFile;
}
```

- [ ] **Step 2: Update upload method to accept sourceType**

Add `String sourceType` parameter to upload method signature, defaulting behavior:

```java
public PdfFile upload(Long userId, MultipartFile file, Long folderId, String sourceType) throws IOException {
```

Set sourceType on the PdfFile before insert:

```java
pdfFile.setSourceType(sourceType != null ? sourceType : "UPLOAD");
```

- [ ] **Step 3: Add updateFileWithSource method**

Add after `createMetadata`:

```java
/** Upload file to existing LOCAL book, converting it to UPLOAD */
@Transactional
public PdfFile uploadToExisting(Long id, Long userId, MultipartFile file) throws IOException {
    PdfFile f = fileMapper.selectById(id);
    if (f == null || !f.getUserId().equals(userId)) throw new IllegalArgumentException("文件不存在");

    // Quota check (same as upload)
    PdfUserQuota quota = quotaMapper.selectByUserId(userId);
    if (quota == null) {
        quotaHelper.ensureDefaultQuota(userId);
        throw new IllegalArgumentException("请先申请 PDF 上传权限");
    }
    if (quota.getAllowUpload() == null || quota.getAllowUpload() == 0) {
        throw new IllegalArgumentException("PDF 上传权限未开启，请联系管理员");
    }
    long currentTotal = fileMapper.sumSizeByUser(userId);
    if (currentTotal + file.getSize() > quota.getQuotaBytes()) {
        throw new IllegalArgumentException(String.format(
            "PDF 存储空间已满 (已用 %.1f MB / 配额 %.1f MB)",
            currentTotal / 1048576.0, quota.getQuotaBytes() / 1048576.0));
    }

    String storedName = UUID.randomUUID().toString() + ".pdf";
    StorageResult result = pdfStorage.store(file.getInputStream(), storedName, file.getSize(), "application/pdf");

    fileMapper.updateFileWithSource(id, file.getOriginalFilename(), storedName, file.getSize(), result.getStoragePath());
    f.setOriginalName(file.getOriginalFilename());
    f.setFilename(storedName);
    f.setFileSize(file.getSize());
    f.setFilePath(result.getStoragePath());
    f.setSourceType("UPLOAD");
    return f;
}
```

- [ ] **Step 4: Verify compile**

Use the IDE build tool to verify.

---

### Task 5: Backend Controller — new endpoint + upload update

**Files:**
- Modify: `lblog-server/src/main/java/com/yang/lblogserver/auth/controller/PdfController.java`

- [ ] **Step 1: Add POST /files/metadata endpoint**

Add after the `upload` method:

```java
@Operation(summary = "创建书记录（不上传文件）")
@PostMapping("/files/metadata")
public ApiResponse<PdfFile> createMetadata(@RequestParam String name,
                                            @RequestParam(required = false) Long folderId) {
    Long userId = getCurrentUserId();
    return ApiResponse.success(pdfService.createMetadata(userId, name, folderId));
}
```

- [ ] **Step 2: Update upload endpoint to accept sourceType**

Modify the `upload` method signature to add `sourceType` param, and pass to service:

```java
@Operation(summary = "上传 PDF")
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<PdfFile> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) Long folderId,
                                    @RequestParam(required = false) String sourceType) {
    Long userId = getCurrentUserId();
    try {
        return ApiResponse.success(pdfService.upload(userId, file, folderId, sourceType));
    } catch (IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    } catch (Exception e) {
        return ApiResponse.error(500, "上传失败");
    }
}
```

- [ ] **Step 3: Add POST /files/{id}/upload endpoint for LOCAL→UPLOAD conversion**

```java
@Operation(summary = "为已有书籍上传文件（LOCAL→UPLOAD）")
@PostMapping(value = "/files/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<PdfFile> uploadToExisting(@PathVariable Long id,
                                              @RequestParam("file") MultipartFile file) {
    Long userId = getCurrentUserId();
    try {
        return ApiResponse.success(pdfService.uploadToExisting(id, userId, file));
    } catch (IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    } catch (Exception e) {
        return ApiResponse.error(500, "上传失败");
    }
}
```

- [ ] **Step 4: Verify compile and build**

Use the IDE build tool to verify the full backend compiles.

---

### Task 6: Frontend Types + API

**Files:**
- Modify: `lblog-web/src/types/index.ts`
- Modify: `lblog-web/src/services/api.ts`

- [ ] **Step 1: Add sourceType to PdfFile type**

In `lblog-web/src/types/index.ts`, add after `totalPages` field:

```typescript
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
```

- [ ] **Step 2: Add createPdfMetadata API function**

In `lblog-web/src/services/api.ts`, add after `uploadPdf`:

```typescript
// 创建书记录（不上传文件）
export async function createPdfMetadata(name: string, folderId?: number | null): Promise<ApiResponse<PdfFile>> {
  const params = new URLSearchParams();
  params.append('name', name);
  if (folderId) params.append('folderId', String(folderId));
  return request<PdfFile>(`/api/v1/pdf/files/metadata?${params.toString()}`, { method: 'POST' });
}
```

- [ ] **Step 3: Add uploadPdfToExisting API function**

```typescript
// 为已有书籍上传文件
export async function uploadPdfToExisting(id: number, file: File): Promise<ApiResponse<PdfFile>> {
  const formData = new FormData();
  formData.append('file', file);
  return request<PdfFile>(`/api/v1/pdf/files/${id}/upload`, {
    method: 'POST',
    body: formData,
  });
}
```

- [ ] **Step 4: Verify TypeScript compile**

```bash
cd lblog-web && npx tsc --noEmit
```

Expected: no new errors.

---

### Task 7: Frontend — NewBookModal component

**Files:**
- Create: `lblog-web/src/components/pdf/NewBookModal.tsx`

- [ ] **Step 1: Create NewBookModal component**

```tsx
import { useState } from 'react';
import { Modal, Input, Select, Button, Space, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { createPdfMetadata, getPdfFolders, uploadPdfToExisting } from '../../services/api';
import type { PdfFolder, PdfFile } from '../../types';
import { useEffect } from 'react';

const { Dragger } = Upload;

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (file: PdfFile, action: 'upload' | 'local') => void;
}

const NewBookModal: React.FC<Props> = ({ open, onClose, onCreated }) => {
  const [bookName, setBookName] = useState('');
  const [selectedFolder, setSelectedFolder] = useState<number | null>(null);
  const [folders, setFolders] = useState<PdfFolder[]>([]);
  const [step, setStep] = useState<'create' | 'choose' | 'upload'>('create');
  const [createdFile, setCreatedFile] = useState<PdfFile | null>(null);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (open) {
      getPdfFolders().then(res => setFolders(res.data)).catch(() => {});
      setStep('create');
      setBookName('');
      setSelectedFolder(null);
      setCreatedFile(null);
    }
  }, [open]);

  const handleCreate = async () => {
    if (!bookName.trim()) { message.warning('请输入书名'); return; }
    try {
      const res = await createPdfMetadata(bookName.trim(), selectedFolder);
      setCreatedFile(res.data);
      setStep('choose');
    } catch (e: any) {
      message.error(e.message || '创建失败');
    }
  };

  const handleUpload = async (file: File) => {
    if (!createdFile) return false;
    if (file.type !== 'application/pdf') {
      message.error('仅支持 PDF 格式');
      return false;
    }
    setUploading(true);
    try {
      await uploadPdfToExisting(createdFile.id, file);
      message.success('上传成功');
      onCreated(createdFile, 'upload');
    } catch (e: any) {
      message.error(e.message || '上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  return (
    <Modal
      title={step === 'create' ? '新建书籍' : step === 'choose' ? '选择来源' : '上传 PDF'}
      open={open}
      onCancel={onClose}
      footer={null}
      width={480}
    >
      {step === 'create' && (
        <>
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, fontWeight: 500 }}>书名</div>
            <Input
              placeholder="输入书名"
              value={bookName}
              onChange={e => setBookName(e.target.value)}
              onPressEnter={handleCreate}
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, fontWeight: 500 }}>文件夹</div>
            <Select
              style={{ width: '100%' }}
              placeholder="选择文件夹（可选）"
              value={selectedFolder}
              onChange={setSelectedFolder}
              allowClear
              options={folders.map(f => ({ value: f.id, label: f.name }))}
            />
          </div>
          <div style={{ textAlign: 'right' }}>
            <Button onClick={onClose} style={{ marginRight: 8 }}>取消</Button>
            <Button type="primary" onClick={handleCreate}>创建</Button>
          </div>
        </>
      )}

      {step === 'choose' && (
        <div style={{ textAlign: 'center', padding: '24px 0' }}>
          <p style={{ marginBottom: 24, fontSize: 14, color: 'var(--color-text-secondary)' }}>
            书籍 "{createdFile?.originalName}" 已创建，请选择文件来源
          </p>
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Button type="primary" block onClick={() => setStep('upload')}>
              上传 PDF 文件到服务器
            </Button>
            <Button block onClick={() => { onCreated(createdFile!, 'local'); }}>
              从本地打开（不上传）
            </Button>
          </Space>
        </div>
      )}

      {step === 'upload' && (
        <>
          <Dragger accept=".pdf" maxCount={1} showUploadList={false}
            beforeUpload={handleUpload} disabled={uploading}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">点击或拖拽 PDF 文件到此处</p>
          </Dragger>
          <div style={{ textAlign: 'right', marginTop: 16 }}>
            <Button onClick={() => setStep('choose')}>返回</Button>
          </div>
        </>
      )}
    </Modal>
  );
};

export default NewBookModal;
```

- [ ] **Step 2: Verify TypeScript compile**

---

### Task 8: Frontend — PdfSidebar add "新建" button

**Files:**
- Modify: `lblog-web/src/components/pdf/PdfSidebar.tsx`

- [ ] **Step 1: Add onNewBook prop and "新建" button**

Change the Props interface:

```tsx
interface Props {
  selectedFile: PdfFile | null;
  onSelectFile: (file: PdfFile) => void;
  onUploadClick: () => void;
  onNewBookClick: () => void;
  onSaveAnnotations?: () => void;
  currentPage: number;
  onJumpToPage: (page: number) => void;
  onEditNote?: (bm: PdfBookmark) => void;
  refreshKey: number;
}
```

Update the component destructuring to include `onNewBookClick`.

Change the button row to:

```tsx
<div style={{ padding: '8px 12px', display: 'flex', gap: 8, alignItems: 'center' }}>
  <Button type="primary" size="small" onClick={onNewBookClick}>新建</Button>
  <Button size="small" icon={<UploadOutlined />} onClick={onUploadClick}>上传</Button>
  <Button size="small" icon={<SaveOutlined />} onClick={onSaveAnnotations}>保存</Button>
</div>
```

- [ ] **Step 2: Verify TypeScript compile**

---

### Task 9: Frontend — PdfViewer support LOCAL files

**Files:**
- Modify: `lblog-web/src/components/pdf/PdfViewer.tsx`

- [ ] **Step 1: Rewrite PdfViewer to support LOCAL files**

```tsx
import { useEffect, useRef, forwardRef, useImperativeHandle, useState, useCallback } from 'react';
import { Button, Space } from 'antd';
import { UploadOutlined, FolderOpenOutlined } from '@ant-design/icons';
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
  onSaveComplete?: () => void;
  onUploadRequest?: () => void;
}

const PdfViewer = forwardRef<PdfViewerHandle, Props>(
  ({ file, onPageChange, onSaveComplete, onUploadRequest }, ref) => {
    const [localBlobUrl, setLocalBlobUrl] = useState<string | null>(null);
    const iframeRef = useRef<HTMLIFrameElement>(null);

    const needsFile = file.sourceType === 'LOCAL' || (!file.fileSize && !file.filePath);

    // For UPLOAD files (or legacy files with no sourceType), use the download URL
    // For LOCAL with blob selected, use blob URL
    const url = localBlobUrl || (needsFile ? null : getPdfDownloadUrl(file.id));

    const handleLocalOpen = useCallback(() => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = '.pdf';
      input.onchange = (e) => {
        const selectedFile = (e.target as HTMLInputElement).files?.[0];
        if (selectedFile) {
          const blobUrl = URL.createObjectURL(selectedFile);
          setLocalBlobUrl(blobUrl);
        }
      };
      input.click();
    }, []);

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

    useEffect(() => {
      getPdfProgress(file.id).then(res => {
        if (res.data?.pageNum > 0) {
          sessionStorage.setItem(`pdf-progress-${file.id}`, String(res.data.pageNum));
        }
      }).catch(() => {});
    }, [file.id]);

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
          onPageChange?.(e.data.page);
        }

        if (e.data.type === 'pdf-annotations') {
          savePdfAnnotation(file.id, 0, e.data.data)
            .then(() => onSaveComplete?.())
            .catch(() => {});
        }
      };
      window.addEventListener('message', handler);
      return () => window.removeEventListener('message', handler);
    }, [file.id]);

    // Cleanup blob URL on unmount
    useEffect(() => {
      return () => {
        if (localBlobUrl) URL.revokeObjectURL(localBlobUrl);
      };
    }, [localBlobUrl]);

    // When file changes, reset blob URL
    useEffect(() => {
      setLocalBlobUrl(null);
    }, [file.id]);

    if (needsFile && !localBlobUrl) {
      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          justifyContent: 'center', height: '100%', gap: 16
        }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 8 }}>
            该书籍尚无文件
          </div>
          <Space>
            <Button type="primary" icon={<UploadOutlined />} onClick={onUploadRequest}>
              上传文件
            </Button>
            <Button icon={<FolderOpenOutlined />} onClick={handleLocalOpen}>
              打开本地文件
            </Button>
          </Space>
        </div>
      );
    }

    if (!url) return null;

    return (
      <iframe ref={iframeRef}
        src={`${import.meta.env.BASE_URL}pdfjs/web/viewer.html?file=${encodeURIComponent(url)}&disableAutoFetch=true`}
        style={{ width: '100%', height: '100%', border: 'none' }}
        title="PDF Viewer" />
    );
  });

export default PdfViewer;
```

- [ ] **Step 2: Verify TypeScript compile**

---

### Task 10: Frontend — FolderTree show LOCAL indicator

**Files:**
- Modify: `lblog-web/src/components/pdf/FolderTree.tsx`

- [ ] **Step 1: Add LOCAL indicator icon in file list**

In the file list rendering, add a small indicator next to LOCAL files. Update the file item row to show `sourceType`:

```tsx
<FilePdfOutlined style={{ color: pf.sourceType === 'LOCAL' ? '#faad14' : '#ff4d4f', fontSize: 14, flexShrink: 0 }} />
```

And replace the `formatSize` column to show "本地" for LOCAL files:

```tsx
<span style={{ fontSize: 10, color: 'var(--color-text-tertiary)', flexShrink: 0 }}>
  {pf.sourceType === 'LOCAL' ? '本地' : formatSize(pf.fileSize)}
</span>
```

- [ ] **Step 2: Verify TypeScript compile**

---

### Task 11: Frontend — PdfReaderPage wire up new flow

**Files:**
- Modify: `lblog-web/src/pages/PdfReaderPage.tsx`

- [ ] **Step 1: Import NewBookModal and update state**

Add import:
```tsx
import NewBookModal from '../components/pdf/NewBookModal';
```

Add state:
```tsx
const [newBookVisible, setNewBookVisible] = useState(false);
```

- [ ] **Step 2: Handle book creation callback**

Add handler:
```tsx
const handleBookCreated = useCallback((file: PdfFile, action: 'upload' | 'local') => {
  setNewBookVisible(false);
  setRefreshKey(k => k + 1);
  if (action === 'local') {
    setSelectedFile(file);
  } else {
    // After upload complete, select the file
    setSelectedFile(file);
  }
}, []);
```

- [ ] **Step 3: Update PdfSidebar props**

Add `onNewBookClick`:
```tsx
<PdfSidebar
  selectedFile={selectedFile}
  onSelectFile={handleSelectFile}
  onUploadClick={() => setUploadVisible(true)}
  onNewBookClick={() => setNewBookVisible(true)}
  onSaveAnnotations={handleSave}
  currentPage={currentPage}
  onJumpToPage={handleJumpToPage}
  onEditNote={handleEditNote}
  refreshKey={refreshKey}
/>
```

- [ ] **Step 4: Update PdfViewer props to add onUploadRequest**

```tsx
<PdfViewer
  ref={viewerRef}
  file={selectedFile}
  onPageChange={setCurrentPage}
  onSaveComplete={handleSaveComplete}
  onUploadRequest={() => setUploadVisible(true)}
/>
```

- [ ] **Step 5: Add NewBookModal to render tree**

After the `PdfUploadModal`:
```tsx
<NewBookModal
  open={newBookVisible}
  onClose={() => setNewBookVisible(false)}
  onCreated={handleBookCreated}
/>
```

- [ ] **Step 6: Verify TypeScript compile**

```bash
cd lblog-web && npx tsc --noEmit
```

---

### Task 12: End-to-end verification

- [ ] **Step 1: Start backend and frontend dev servers**

- [ ] **Step 2: Test flow: Create LOCAL book → open local PDF**

1. Click "新建" in sidebar
2. Enter book name, select folder, click "创建"
3. Click "从本地打开"
4. File picker opens → select a PDF
5. Verify PDF renders in viewer
6. Add some annotations → save → refresh → verify annotations persist

- [ ] **Step 3: Test flow: Create LOCAL book → later upload file**

1. Create LOCAL book
2. Click the book → "该书籍尚无文件" prompt shows
3. Click "上传文件" → select PDF → upload
4. Verify book now loads from server

- [ ] **Step 4: Test flow: Direct upload (existing behavior unchanged)**

1. Click "上传" in sidebar
2. Drag PDF file → select folder → upload
3. Verify book created and PDF loads from server

- [ ] **Step 5: Test LOCAL indicator in FolderTree**

1. Verify LOCAL books show yellow icon and "本地" label
2. Verify UPLOAD books show red icon and file size

- [ ] **Step 6: Verify backend API**

```bash
# Create metadata (LOCAL)
curl -X POST "http://localhost:8099/iblogserver/api/v1/pdf/files/metadata?name=测试书" -H "Cookie: ..."

# Verify sourceType in response
curl "http://localhost:8099/iblogserver/api/v1/pdf/files/{id}" -H "Cookie: ..."
# Expected: sourceType = "LOCAL", filePath = null, fileSize = null
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: PDF 本地阅读器 — 支持创建书不上传，本地打开 PDF"
```
