# 图片管理设计

> 社区管理 · 图片管理页面

---

## 目标

在创作中心提供一个图片管理页面，让作者/管理员可以：
1. 查看所有已上传的图片
2. 查看图片被哪些文章引用
3. 删除未引用的图片
4. 了解图片使用率，清理低效图片

---

## 权限说明

图片管理属于 **管理员（admin）** 专属功能，所有接口需要 `admin` 角色权限。
普通作者（author）/ 用户（user）无权限访问，返回 403。

与配置管理一样，入口在导航栏「社区管理」下，不在创作中心侧边栏。

---

## 后端接口

### 图片统计概览

```
GET /api/v1/admin/images/statistics

Response:
{
  "code": 0,
  "message": "success",
  "data": {
    "totalImages": 156,           // 图片总数
    "totalSize": 524288000,       // 所有图片总大小（字节）
    "referencedCount": 120,       // 被引用的图片数
    "unreferencedCount": 36,      // 未引用的图片数
    "utilizationRate": 76.9,      // 利用率（%）
    "oldUnreferencedCount": 12,   // 创建超过30天且未引用的图片数（清理目标）
    "oldUnreferencedSize": 10240000 // 可释放的空间（字节）
  }
}
```

### 获取图片列表（分页）

```
GET /api/v1/admin/images?page=1&pageSize=20&sort=newest&status=all&keyword=

参数:
  sort:   newest / oldest / largest / smallest / most_used / unused
  status: all / referenced / unreferenced
  keyword: 按原始文件名搜索

Response:
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "url": "/uploads/2026/05/abc.jpg",
        "originalName": "screenshot.png",
        "mimeType": "image/png",
        "fileSize": 245760,
        "width": 1920,
        "height": 1080,
        "usageCount": 2,
        "usages": [
          { "refType": "post", "refId": 42, "field": "body", "refTitle": "文章标题" },
          { "refType": "post", "refId": 42, "field": "featured_image", "refTitle": "文章标题" }
        ],
        "createdAt": "2026-05-08T12:00:00"
      }
    ],
    "total": 156,
    "page": 1,
    "pageSize": 20
  }
}
```

### 清理未引用图片

> **物理删除**：清理时直接从磁盘删除文件 + 硬删除 `images` 表记录，不可恢复。

两种清理策略，可单独使用或组合使用：

| 参数 | 说明 | 默认 |
|------|------|------|
| `beforeDays` | 只清理创建超过 N 天的图片 | 30 |
| `targetUtilization` | 清理到利用率达到此百分比为止（如 80） | 不限制 |

**组合逻辑：** 从未引用图片中按创建时间从旧到新依次删除，直到满足两个条件之一：
- `beforeDays`：没有超过 N 天的未引用图片了
- `targetUtilization`：当前利用率 ≥ 目标值

```
DELETE /api/v1/admin/images/cleanup?beforeDays=30&targetUtilization=80&dryRun=true

参数:
  beforeDays:         只清理超过多少天未引用的图片（默认 30）
  targetUtilization:  清理到利用率达到多少百分比为止（可选）
  dryRun:             true=仅预览不执行，false=实际删除

dryRun=true 响应（预览）:
{
  "code": 0,
  "message": "success",
  "data": {
    "dryRun": true,
    "currentUtilization": 65.4,   // 当前利用率
    "targetUtilization": 80,       // 目标利用率（传了才返回）
    "estimatedUtilization": 82.1,  // 清理后的预计利用率
    "count": 12,                   // 将被清理的图片数
    "totalSize": 10240000,         // 可释放的空间
    "images": [
      { "id": 5, "url": "/uploads/2026/03/old.png", "originalName": "old.png", "fileSize": 1024000, "createdAt": "2026-03-01T10:00:00" }
    ]
  }
}

dryRun=false 响应（实际执行）:
{
  "code": 0,
  "message": "success",
  "data": {
    "dryRun": false,
    "beforeUtilization": 65.4,    // 清理前利用率
    "afterUtilization": 82.1,     // 清理后利用率
    "deletedCount": 12,
    "freedSize": 10240000
  }
}
```

### 删除单张图片

```
DELETE /api/v1/admin/images/{id}

Response:
{
  "code": 0,
  "message": "success",
  "data": null
}
```

- 被引用的图片不可删除，返回 400 + 提示信息
- 后端做软删除（`deleted_at`）

---

## 前端页面

### 路由

在社区管理下新增：

```tsx
<Route path="/admin/images" element={<ImageManage />} />
```

### 入口

在 `AdminDashboard.tsx` 的 `features` 数组中新增卡片：

```tsx
{
  key: 'images',
  title: '图片管理',
  description: '管理上传的图片，查看引用详情，清理未使用的图片',
  icon: <PictureOutlined style={{ fontSize: 32, color: '#52c41a' }} />,
  path: '/admin/images',
},
```

### 页面布局

```
┌─ Card: 图片管理 ───────────────────────────────────┐
│                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ 图片总数  │ │ 已使用    │ │ 未使用    │ │ 可清理  │ │
│  │   156    │ │   120    │ │   36     │ │ 12张   │ │
│  │          │ │          │ │          │ │ 10MB   │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│                                                     │
│  筛选栏: [搜索] [排序↓] [全部/已引用/未引用]          │
│                                                     │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐               │
│  │ img  │ │ img  │ │ img  │ │ img  │               │
│  │ 文件名│ │ 文件名│ │ 文件名│ │ 文件名│               │
│  │ 引用数│ │ 引用数│ │ 未引用│ │ 引用数│               │
│  └──────┘ └──────┘ └──────┘ └──────┘               │
│                                                     │
│  分页: < 1 2 3 ... 10 >  共 156 张                  │
│                                                     │
│  [清理未引用图片] ← 按钮，点击弹出确认 Modal          │
└─────────────────────────────────────────────────────┘
```

### 顶部统计卡片

| 卡片 | 数据 | 颜色 |
|------|------|------|
| 图片总数 | totalImages | 蓝 |
| 已使用 | referencedCount | 绿 |
| 未使用 | unreferencedCount | 橙 |
| 可清理 | oldUnreferencedCount + oldUnreferencedSize 格式化显示 | 红 |

### 清理流程

```
点击 [清理未引用图片]
  → 调 cleanup?dryRun=true 预览
  → Modal 显示将要删除的图片列表 + 可释放空间
  → 用户确认
  → 调 cleanup?dryRun=false 实际删除
  → 刷新列表 + 统计
```

### 图片卡片

```
┌──────────┐
│  图片预览  │  点击放大预览
│  缩略图    │
├──────────┤
│ 文件名    │
│ 1200×800  │
│ 240KB     │
│ 引用: 2处  │  ← 无引用时红色「未引用」
│ 2026-05-08│
│ [删除]    │  ← 有引用时置灰 + tooltip
└──────────┘
```

引用数可点击 → Popover 显示详情：

```
📎 引用详情
───────────────
📝 文章「MySQL 索引优化」(封面)
📝 文章「MySQL 索引优化」(正文)
```

### 状态处理

| 状态 | 表现 |
|------|------|
| Loading | Skeleton 卡片占位 |
| Error | message.error + 重试按钮 |
| Empty | 暂无上传的图片 |
| 筛选结果为空 | 未找到匹配的图片 |

---

## 前端改动清单

| 文件 | 改动 |
|------|------|
| `api.ts` | 新增 `AdminImage`、`ImageStatistics` 类型 + `getAdminImages`、`deleteAdminImage`、`getImageStatistics`、`cleanupImages` 接口 |
| `App.tsx` | 新增路由 `/admin/images` |
| `AdminDashboard.tsx` | 新增图片管理卡片 |
| 新建 `ImageManage.tsx` | 图片管理页面（放在 `src/pages/admin/` 下） |

---

## 后续扩展

- 批量选择 + 批量删除
- 拖拽上传新图片
- 图片标签/分组
- 相册功能（`image_usages` 的 `ref_type='album'` 已预留）
