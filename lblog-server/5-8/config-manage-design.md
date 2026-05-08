# 配置管理设计

> 社区管理 · 站点配置管理页面

---

## 数据库

```sql
CREATE TABLE site_config (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  config_key   VARCHAR(100) NOT NULL COMMENT '配置键',
  config_value VARCHAR(500) NOT NULL DEFAULT '' COMMENT '配置值',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '站点配置';
```

数据示例：

| config_key | config_value |
|------------|-------------|
| registration_enabled | true |
| image_max_size | 10485760 |
| site_title | LBlog |

---

## 后端接口

### 获取所有配置

```
GET /api/v1/admin/configs

Response:
{
  "code": 0,
  "message": "success",
  "data": [
    { "configKey": "registration_enabled", "configValue": "true" },
    { "configKey": "image_max_size", "configValue": "10485760" },
    { "configKey": "site_title", "configValue": "LBlog" }
  ]
}
```

### 批量更新配置

```
PUT /api/v1/admin/configs
Content-Type: application/json

Request:
{
  "registration_enabled": "false",
  "site_title": "我的社区"
}

Response:
{
  "code": 0,
  "message": "success",
  "data": null
}
```

只传需要改的项，不传的保持不变。

### 添加配置（可选）

```
POST /api/v1/admin/configs
Content-Type: application/json

Request:
{ "configKey": "new_key", "configValue": "value" }

Response:
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 删除配置（可选）

```
DELETE /api/v1/admin/configs?key=obsolete_key

Response:
{
  "code": 0,
  "message": "success",
  "data": null
}
```

---

## 前端

### 新增路由

`App.tsx` 在 admin 路由下新增：

```tsx
<Route path="configs" element={<ConfigManage />} />
```

### 新增菜单

`AdminLayout.tsx` 侧边栏新增：

```tsx
{ key: '/author/configs', icon: <SettingOutlined />, label: '配置管理' },
```

### 新增页面

`src/pages/author/ConfigManage.tsx`

**布局：**

```
┌─ Card ──────────────────────────────┐
│  标题: 配置管理                       │
│  顶部: [添加配置] 按钮                │
│                                      │
│  ┌─ Table ─────────────────────────┐ │
│  │ 配置键          │ 配置值    │ 操作 │ │
│  │ registration…   │ true  🔘  │ 编辑 │ │
│  │ image_max_size  │ 10MB  📝  │ 编辑 │ │
│  │ site_title      │ LBlog 📝  │ 编辑 │ │
│  └─────────────────────────────────┘ │
└──────────────────────────────────────┘
```

**功能：**

| 功能 | 实现 |
|------|------|
| 列表展示 | Ant Design Table，两列：configKey / configValue |
| 编辑 | 点击编辑 → Modal 弹窗，表单编辑值 |
| 值类型自适应 | `key` 含 `_enabled` / `_switch` → Switch 开关控件；否则文本输入 |
| 添加 | 顶部「添加配置」按钮 → Modal 输入 key + value |
| 删除 | 表格行操作「删除」按钮 → 确认后删除 |
| 保存 | 调用 `PUT` 接口批量保存 |

**状态处理：**

| 状态 | 表现 |
|------|------|
| Loading | Spin 覆盖表格 |
| Error | message.error + 重试 |
| Empty | 表格显示暂无配置 |

### 新增 API

`api.ts` 新增：

```typescript
export interface SiteConfigItem {
  configKey: string;
  configValue: string;
}

export async function getAdminConfigs(): Promise<ApiResponse<SiteConfigItem[]>> {
  return request<SiteConfigItem[]>('/api/v1/admin/configs');
}

export async function updateAdminConfigs(data: Record<string, string>): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/admin/configs', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}
```

---

## 前端文件改动清单

| 文件 | 改动 |
|------|------|
| `api.ts` | 新增 `SiteConfigItem` 类型、`getAdminConfigs`、`updateAdminConfigs` 接口 |
| `App.tsx` | 新增 `Route path="configs" element={<ConfigManage />}` |
| `AdminLayout.tsx` | 侧边栏新增「配置管理」菜单项 |
| 新建 `ConfigManage.tsx` | 配置管理页面 |
