# 用户图存储 API 接口文档

> 所有接口需要登录（`Authorization: Bearer {token}`），用户只能操作自己的图表。

## 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/draw/diagrams` | 新建图表 |
| GET | `/api/v1/draw/diagrams` | 查询图表列表（不含 xmlData） |
| GET | `/api/v1/draw/diagrams/{id}` | 获取图表详情（含 xmlData） |
| PUT | `/api/v1/draw/diagrams/{id}` | 覆盖保存（更新 XML + 元数据） |
| PATCH | `/api/v1/draw/diagrams/{id}` | 更新图表元数据（标题/描述/标签） |
| DELETE | `/api/v1/draw/diagrams/{id}` | 删除图（软删除） |

---

## 1. 新建图表

```
POST /api/v1/draw/diagrams
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体：**

```json
{
  "title": "微服务架构图",
  "description": "系统整体架构设计",
  "tags": "[\"架构\",\"微服务\"]",
  "xmlData": "<mxfile><diagram>...</diagram></mxfile>",
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

**成功响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": { "id": 1 }
}
```

---

## 2. 获取图表列表

不返回 xmlData 大字段。

```
GET /api/v1/draw/diagrams?page=1&pageSize=20&keyword=微服务
Authorization: Bearer {token}
```

**查询参数：**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| page | int | 否 | 1 | 页码，从 1 开始 |
| pageSize | int | 否 | 20 | 每页条数，最大 50 |
| keyword | string | 否 | - | 搜索关键词，模匹配 title/description/tags |

**成功响应：**

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

**注意：** 列表返回字段不含 `xmlData`；按 `updatedAt DESC` 排序。

---

## 3. 获取图表详情

返回完整数据，含 xmlData。

```
GET /api/v1/draw/diagrams/{id}
Authorization: Bearer {token}
```

**成功响应：**

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
    "xmlData": "<mxfile><diagram>...</diagram></mxfile>",
    "thumbnail": "data:image/png;base64,...",
    "fileSize": 12800,
    "createdAt": "2026-05-15T10:30:00",
    "updatedAt": "2026-05-15T14:20:00"
  }
}
```

**错误：** 404 — 图不存在或已删除；401 — 未登录

---

## 4. 覆盖保存

同时更新 XML 和元数据。

```
PUT /api/v1/draw/diagrams/{id}
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体与新建相同**（title + xmlData 必填）。

**成功响应：** `{ "code": 0, "message": "success", "data": null }`

---

## 5. 更新图元数据

仅更新标题/描述/标签，不影响 xmlData 和 thumbnail。

```
PATCH /api/v1/draw/diagrams/{id}
Content-Type: application/json
Authorization: Bearer {token}
```

**请求体：**

```json
{
  "title": "重命名标题",
  "description": "新描述",
  "tags": "[\"标签1\"]"
}
```

**成功响应：** `{ "code": 0, "message": "success", "data": null }`

---

## 6. 删除图

软删除，设置 deletedAt 时间。

```
DELETE /api/v1/draw/diagrams/{id}
Authorization: Bearer {token}
```

**成功响应：** `{ "code": 0, "message": "success", "data": null }`

---

## 通用说明

- **Base URL:** `http://localhost:8099/iblogserver/api/v1`
- **认证方式:** `Authorization: Bearer {accessToken}`
- **响应格式:** 统一 `ApiResponse<T>`，`code === 0` 表示成功
- **分页格式:** `data` 为 `PageResult<T>`，含 `list`/`total`/`page`/`pageSize`
- **权限:** 用户只能操作自己的图表（基于 token 中的 userId）
