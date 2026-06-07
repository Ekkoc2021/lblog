# PDF 本地阅读器设计

## 目标

支持不经过服务器上传直接阅读本地 PDF 文件，服务器只存储书元数据 + 标注/书签/进度。

## 核心逻辑

```
创建书 → 填书名 + 选文件夹 → 确定
  ├─ 上传文件 → 现有上传流程 → sourceType=UPLOAD
  └─ 本地打开 → sourceType=LOCAL → 弹出文件选择器 → 阅读

打开书 → 检查 sourceType
  ├─ UPLOAD → 服务器有文件 → 直接加载（现有行为）
  └─ LOCAL  → 服务器无文件 → 弹窗：上传文件 / 打开本地
```

## 数据模型

PdfFile 表新增字段：

```sql
ALTER TABLE pdf_files ADD COLUMN source_type VARCHAR(10) DEFAULT 'UPLOAD';
```

LOCAL 类型：`file_path`、`file_size` 为空，`original_name` 存书名。现有字段全部复用。

## API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/pdf/files/metadata` | **新增**，创建书记录不上传文件，参数: name, folderId |
| GET | `/api/v1/pdf/files/{id}` | 返回加 `sourceType` 字段 |
| PUT | `/api/v1/pdf/files/{id}` | 支持后续上传文件，LOCAL→UPLOAD |
| POST | `/api/v1/pdf/files` | 现有上传接口，加 `sourceType` 参数 |

标注/书签/进度 API 不变。

## 前端改动

### PdfSidebar.tsx — 书架加"新建"按钮
书架标签页顶部增加"新建"按钮。

### PdfUploadModal.tsx — 改为"新建书籍"弹窗
- 输入书名 + 选择文件夹
- 点击确定 → 调用 `POST /api/v1/pdf/files/metadata`
- 创建成功后弹选择：上传文件 / 本地打开
- 选上传 → 走现有上传流程
- 选本地打开 → 调到 PdfViewer 的本地打开流程

### PdfViewer.tsx — 支持本地文件加载
- 打开 LOCAL 书时检查服务器无文件 → 弹窗提示"该书籍尚无文件"，两个按钮：上传 / 打开本地
- 打开本地 → input[type=file] 选择 PDF → 创建 `blob:` URL → 设置 iframe src 为 `viewer.html?file=blob:...`
- UPLOAD 书保持现有行为（下载 URL）

### FolderTree.tsx — 区分显示
- LOCAL 书加本地标记图标区分
- UPLOAD 书保持现有显示

## PDF.js 本地加载

LOCAL 文件通过 `URL.createObjectURL(file)` 创建 blob URL，传给 iframe 的 `file` 参数。与现有下载 URL 路径一致，改动最小。刷新页面后 blob URL 失效需重新选择文件（符合设计预期）。

## 不动的内容

- 标注存取（PdfAnnotationController / 前端 bridge）
- 书签管理（PdfBookmarkController / BookmarkPanel）
- 阅读进度（PdfProgressController）
- 文件夹管理（PdfFolder CRUD）
- 配额管理（AdminPdfController）
- PdfToolbar 组件
