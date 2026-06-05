# 密码本功能设计

## 概述

在博客工具箱中新增密码本功能，用于安全存储个人网站密码（QQ、GitHub 等）。密码在前端通过用户自定义密文 AES-256-GCM 加密后传输到后端存储，后端不接触明文和密钥。

## 架构

```
┌──────────────────────────────┐     ┌────────────────────────────┐
│  密码本面板 (HashbookPanel)    │────▶│  POST /api/v1/passwords     │
│                              │     │  GET  /api/v1/passwords     │
│  加密：用户密文 + PBKDF2 派生  │     │  PUT  /api/v1/passwords/:id │
│  密钥 → AES-256-GCM 加密密码   │     │  DEL  /api/v1/passwords/:id │
│  解密：逆向过程                │     └────────────────────────────┘
│                              │                │
│  密文永远不上传不存储          │                ▼
└──────────────────────────────┘     ┌────────────────────────┐
                                     │  MySQL passwords 表     │
                                     │  encrypted_password    │
                                     │  只存密文，不存密钥     │
                                     └────────────────────────┘
```

## 数据模型

### passwords 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT PK | 主键 |
| user_id | BIGINT NOT NULL | 归属用户 |
| site_name | VARCHAR(100) NOT NULL | 网站名称 |
| site_url | VARCHAR(500) DEFAULT '' | 网址 |
| username | VARCHAR(200) NOT NULL | 账号 |
| encrypted_password | TEXT NOT NULL | AES-256-GCM 加密后的密码 |
| note | VARCHAR(500) DEFAULT '' | 备注 |
| is_deleted | TINYINT(1) DEFAULT 0 | 软删除标记 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## API 设计

所有接口需要认证，仅 author/admin 可访问。每个用户只能操作自己的数据。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/passwords | 列表，参数：keyword（搜索站点名/用户名）、page、pageSize |
| POST | /api/v1/passwords | 新增 |
| PUT | /api/v1/passwords/:id | 更新 |
| DELETE | /api/v1/passwords/:id | 软删除（设置 is_deleted=1） |

## 加密方案

- **算法**：AES-256-GCM（浏览器 Web Crypto API）
- **密钥派生**：PBKDF2，从用户密文派生 256 位密钥，salt 随机生成并拼接在密文前面
- **密文格式**：`<base64_salt>:<base64_iv>:<base64_ciphertext>`
- **密文存储**：密文仅用户自己知道，不上传到后端，不存储在数据库
- **每个账户的密文可以不同**，加密后的结果也不同

### 保存流程
1. 用户在表单填入明文密码
2. 输入密文（该账户的保险库密码）
3. 前端：PBKDF2(密文, salt) → 256位密钥 → AES-256-GCM 加密
4. 将 `<salt>:<iv>:<ciphertext>` 作为 encrypted_password 传给后端
5. 后端直接存储，不做任何加解密

### 查看流程
1. 后端返回 encrypted_password
2. 用户点击某条记录，在弹窗中输入密文
3. 前端：从 encrypted_password 分离 salt、iv、ciphertext → PBKDF2 派生密钥 → AES-256-GCM 解密 → 显示明文
4. 关闭弹窗后明文从内存丢弃

## 前端设计

### 面板布局

- 可拖拽浮动面板，位置持久化到 localStorage（参考 TodoPanel）
- 列表模式：顶部搜索框 + 分页列表 + 底部新增按钮
- 每条记录显示：网站名称、网址、账号（密码用星号隐藏）

### 交互流程

- **新增**：点击"新增"→ Modal 表单（网站名称、网址、账号、密码、密文、备注）→ 提交时前端用密文加密 → 保存到后端
- **查看密码**：点击某条记录 → Modal 显示详情（账号可复制）→ 密码显示为星号 → 输入密文 → 点击"解密"→ 显示明文密码（可复制）
- **编辑**：Modal 同新增，预填已有数据
- **删除**：确认后软删除

### 组件结构

```
HashbookPanel/          (新建)
  index.tsx             - 面板主组件（列表 + 搜索 + 分页 + 拖拽）
  PasswordModal.tsx     - 新增/编辑弹窗
  ViewPasswordModal.tsx - 查看密码弹窗
services/passwordApi.ts - API 调用
```

### 工具箱集成

- `DrawFloatingButton.tsx`：新增 `{ id: 'hashbook', label: '密码本', icon: <Key />, action: ... }`
- `App.tsx`：新增 `showHashbookPanel` 状态 + HashbookPanel 渲染 + ToolboxButton 传参

## 后端设计

### 模块结构（参考 todo 模块）

```
password/
  controller/
    PasswordController.java   - REST 控制器 (@PreAuthorize hasAnyRole ADMIN,AUTHOR)
  domain/
    Password.java             - 实体
  mapper/
    PasswordMapper.java       - MyBatis 接口
  service/
    PasswordService.java      - 接口
    impl/
      PasswordServiceImpl.java - 实现
  vo/
    CreatePasswordRequest.java
    UpdatePasswordRequest.java
    PasswordVO.java
```

### 安全

- 控制器级别 `@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")`
- 所有查询/更新带 `user_id` 过滤，确保数据隔离

## 边界考虑

- 密文遗忘：后端不存密文，遗忘后密码无法解密。UI 给予提示"请妥善保管密文"
- 剪贴板安全：复制密码后建议清除剪贴板（不强制）
- 移动端：≤768px 隐藏工具箱入口，与现有逻辑一致
