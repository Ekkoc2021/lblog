# 用户图表存储功能 — 接口设计文档

## 1. 功能概述

让用户可以将 AI 绘图工具（draw.io）中绘制的图标保存到服务器，并随时加载、管理自己的图标库。

### 核心能力

- 保存当前绘制的图表（新建 / 覆盖保存）
- 加载已保存的图表到画布
- 分页浏览自己的图表库（搜索、排序）
- 管理图表（重命名、删除）

---

## 2. 数据模型

### 2.1 数据库表 `user_diagrams`

```sql
CREATE TABLE user_diagrams (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL COMMENT '所属用户 ID',
    title         VARCHAR(200) NOT NULL DEFAULT '未命名图表' COMMENT '图表标题',
    description   VARCHAR(500)          DEFAULT NULL COMMENT '图表描述',
    tags          VARCHAR(500)          DEFAULT NULL COMMENT '标签，JSON 数组字符串，如 ["架构","微服务"]',
    xml_data      MEDIUMTEXT   NOT NULL COMMENT 'draw.io XML 完整内容',
    thumbnail     LONGTEXT              DEFAULT NULL COMMENT '缩略图，Base64 PNG data URL',
    file_size     INT                   DEFAULT 0 COMMENT 'xml_data 内容的字节数',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at    DATETIME              DEFAULT NULL COMMENT '软删除时间戳',

    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at),
    INDEX idx_user_updated (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户绘图存储表';
```

### 2.2 实体对象

**UserDiagram（DO，映射表结构）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| userId | Long | 用户 ID |
| title | String | 图表标题 |
| description | String | 描述 |
| tags | String | 标签（JSON 数组字符串，如 `["架构","微服务"]`） |
| xmlData | String | draw.io XML 完整内容 |
| thumbnail | String | 缩略图（Base64 PNG data URL，可选） |
| fileSize | Integer | XML 字节数 |
| createdAt | Date | 创建时间 |
| updatedAt | Date | 更新时间 |
| deletedAt | Date | 软删除时间 |

### 2.3 重要说明

- **xml_data 用 MEDIUMTEXT**：draw.io XML 通常几 KB ~ 几百 KB，MEDIUMTEXT 上限 16MB，足够
- **列表查询不返回 xml_data**：列表接口排除大字段，只有在打开详情时才返回
- **软删除**：设置 `deleted_at` 而非物理删除
- **标题已内联到 title 字段**：DrawPage 标题栏输入的内容就是 title，不需要额外设计图表名称管理

---

## 3. 接口总览

所有接口路径以 `/api/v1/diagrams` 为前缀，需要 JWT 认证。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/diagrams` | 新建图表 |
| GET | `/api/v1/diagrams` | 查询图表列表（不含 XML） |
| GET | `/api/v1/diagrams/{id}` | 获取图表详情（含 XML） |
| PUT | `/api/v1/diagrams/{id}` | 覆盖保存（更新 XML + 元数据） |
| PATCH | `/api/v1/diagrams/{id}` | 更新图表元数据（标题/描述/标签） |
| DELETE | `/api/v1/diagrams/{id}` | 删除图表（软删除） |

---

## 4. 详细接口定义

### 4.1 新建图表

保存一个新图表到服务器。

```
POST /api/v1/diagrams
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body：**

```json
{
  "title": "微服务架构图",
  "description": "系统整体架构设计",
  "tags": "[\"架构\",\"微服务\"]",
  "xmlData": "<mxfile><diagram ...>...</diagram></mxfile>",
  "thumbnail": "data:image/png;base64,...",
  "fileSize": 12800
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 图表标题，最长 200 字符 |
| description | string | 否 | 图表描述，最长 500 字符 |
| tags | string | 否 | 标签 JSON 数组字符串 |
| xmlData | string | 是 | draw.io XML 内容 |
| thumbnail | string | 否 | Base64 PNG data URL |
| fileSize | int | 否 | XML 字节数 |

**Success Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1
  }
}
```

**Error Response：**

```json
{
  "code": 401,
  "message": "未登录",
  "data": null
}
```

| code | message | 说明 |
|------|---------|------|
| 401 | 未登录 | 无有效 JWT |
| 400 | 标题不能为空 | title 校验失败 |
| 400 | XML 内容不能为空 | xmlData 校验失败 |

---

### 4.2 获取图表列表

分页查询当前用户的图表列表，**不返回 xml_data 大字段**。

```
GET /api/v1/diagrams?page=1&pageSize=20&keyword=微服务
Authorization: Bearer {token}
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| page | int | 否 | 1 | 页码，从 1 开始 |
| pageSize | int | 否 | 20 | 每页条数，最大 50 |
| keyword | string | 否 | - | 搜索关键词，模糊匹配 title/description/tags |

**Success Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "微服务架构图",
        "description": "系统整体架构设计",
        "tags": "[\"架构\",\"微服务\"]",
        "thumbnail": "data:image/png;base64,...",
        "fileSize": 12800,
        "createdAt": "2026-05-15T10:30:00",
        "updatedAt": "2026-05-15T14:20:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

**注意事项：**

- 返回字段**不包含 `xmlData`**
- `thumbnail` 可能为 null，前端需要处理
- 按 `updated_at DESC` 排序，最新修改的排最前

---

### 4.3 获取图表详情

获取单个图表的完整数据，含 `xmlData` 用于加载到画布。

```
GET /api/v1/diagrams/{id}
Authorization: Bearer {token}
```

**Path Parameters：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 图表 ID |

**Success Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "userId": 1,
    "title": "微服务架构图",
    "description": "系统整体架构设计",
    "tags": "[\"架构\",\"微服务\"]",
    "xmlData": "<mxfile><diagram ...>...</diagram></mxfile>",
    "thumbnail": "data:image/png;base64,...",
    "fileSize": 12800,
    "createdAt": "2026-05-15T10:30:00",
    "updatedAt": "2026-05-15T14:20:00"
  }
}
```

**Error Response：**

| code | message | 说明 |
|------|---------|------|
| 401 | 未登录 | JWT 无效 |
| 404 | 图表不存在 | id 不存在或已删除 |
| 403 | 无权限访问该图表 | 图表不属于当前用户 |

---

### 4.4 覆盖保存

更新已有图表的 XML 内容和元数据。相当于"保存"操作（覆盖原有版本）。

```
PUT /api/v1/diagrams/{id}
Authorization: Bearer {token}
Content-Type: application/json
```

**Path Parameters：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 要更新的图表 ID |

**Request Body：**

```json
{
  "title": "微服务架构图 v2",
  "description": "增加了日志模块",
  "tags": "[\"架构\",\"微服务\",\"日志\"]",
  "xmlData": "<mxfile><diagram ...>...</diagram></mxfile>",
  "thumbnail": "data:image/png;base64,...",
  "fileSize": 13500
}
```

**Success Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

**说明：**

- 此接口**同时更新 XML 和元数据**，因为用户保存时可能同时改了标题
- 需要校验 `userId` 防止越权

---

### 4.5 更新图表元数据

仅更新标题/描述/标签，不涉及 XML 内容。用于"重命名"等轻量操作。

```
PATCH /api/v1/diagrams/{id}
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body：**

```json
{
  "title": "微服务架构图（最终版）",
  "description": "最终确定的设计",
  "tags": "[\"架构\",\"微服务\",\"生产\"]"
}
```

**Success Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

**说明：**

- 只更新传入的字段（但当前设计三个字段都要求）
- 不会改变 `xml_data`、`thumbnail` 等内容

---

### 4.6 删除图表

软删除，设置 `deleted_at` 时间戳。

```
DELETE /api/v1/diagrams/{id}
Authorization: Bearer {token}
```

**Success Response (200)：**

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

**说明：**

- 实际执行 `UPDATE user_diagrams SET deleted_at = NOW() WHERE id = ? AND user_id = ?`
- 所有列表和详情查询都需要加 `AND deleted_at IS NULL` 条件

---

## 5. 安全与权限

- **所有接口都需要 JWT 认证**，从 `SecurityContextHolder` 获取当前用户 ID
- **所有操作都校验 `userId`**，用户只能操作自己的图表
- **软删除**保留数据完整性，后续可以扩展回收站
- 新建图表时不校验同名校验（允许同名）

## 6. 通用响应格式

当前项目统一的 `ApiResponse<T>` 格式：

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

- `code === 0` 表示成功
- `code !== 0` 表示错误，`message` 为错误描述
- 分页接口的 `data` 为 `PageResult<T>`，包含 `list`、`total`、`page`、`pageSize`

---

## 7. 前端对接说明

接口设计好后，前端只需：

1. 在 `src/services/api.ts` 中**将 `request` 函数和 `buildQuery` 导出**（加 `export`）
2. 新建 `src/services/diagramStorage.ts`，基于 `request` 封装以上 6 个 API 调用
3. 在 `diagram-context.tsx` 中新增 `saveDiagram()` / `openDiagram()` 等方法

---

## 8. 实现建议

### 后端实现顺序

1. 执行建表 DDL
2. 创建 `UserDiagram` Entity
3. 创建 `UserDiagramsMapper` + Mapper XML
4. 创建 `UserDiagramsService` 接口 + 实现
5. 创建 `UserDiagramController`
6. 创建 VO 类（CreateDiagramRequest, UpdateDiagramRequest, DiagramListVO, DiagramDetailVO）
7. 启动后端验证接口可用性
