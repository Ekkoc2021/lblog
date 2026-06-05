# 日记本功能设计

## 概述

在博客工具箱中新增日记本功能，支持日历视图和时间线视图双模式，带心情标签和天气标记，专注个人生活记录。

## 数据模型

### journals 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT PK | 主键 |
| user_id | BIGINT NOT NULL | 归属用户 |
| title | VARCHAR(200) DEFAULT '' | 标题（可空） |
| content | TEXT | 正文（纯文本） |
| mood | VARCHAR(50) DEFAULT '' | 心情标签 |
| mood_emoji | VARCHAR(10) DEFAULT '' | 心情对应 emoji |
| weather | VARCHAR(20) DEFAULT '' | 天气 |
| journal_date | DATE NOT NULL | 日记日期 |
| is_deleted | TINYINT(1) DEFAULT 0 | 软删除标记 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

UNIQUE KEY: `(user_id, journal_date)` — 每天每个用户只有一篇日记

## 心情预设

| emoji | 标签 | 颜色 |
|-------|------|------|
| 😊 | 开心 | #52c41a |
| 😢 | 难过 | #1677ff |
| 😡 | 生气 | #ff4d4f |
| 😴 | 疲惫 | #8c8c8c |
| 🎉 | 兴奋 | #fa8c16 |
| 💪 | 充实 | #722ed1 |
| 😰 | 焦虑 | #faad14 |
| 😌 | 平静 | #13c2c2 |
| 📝 | 记录 | #666 |
| ➕ | 自定义 | — |

## 天气预设

| emoji | 标签 |
|-------|------|
| ☀️ | 晴 |
| ⛅ | 多云 |
| ☁️ | 阴 |
| 🌧️ | 雨 |
| ⛈️ | 暴雨 |
| ❄️ | 雪 |
| 🌬️ | 大风 |
| 🌫️ | 雾 |

## API 设计

所有接口需要认证，仅 admin/author 可访问。每个用户只能操作自己的数据。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/journals | 列表：按年月查日历（year/month），或时间线分页（page/pageSize） |
| GET | /api/v1/journals/by-date | 查某天日记（参数：date=2026-06-06） |
| POST | /api/v1/journals | 新建日记（同一天已存在则更新） |
| PUT | /api/v1/journals/:id | 更新 |
| DELETE | /api/v1/journals/:id | 软删除 |

## 前端设计

### 组件结构

```
JournalPanel/
  index.tsx             - 面板主组件（拖拽+Tabs切换日历/时间线）
  CalendarView.tsx      - 日历视图
  TimelineView.tsx      - 时间线视图
  JournalEditor.tsx     - 日记编辑弹窗（新建/编辑）
  JournalDetail.tsx     - 日记详情弹窗（只读+心情天气展示）
services/journalApi.ts  - API 调用
```

### 面板布局

- 可拖拽浮动面板，位置持久化到 localStorage（参考 HashbookPanel）
- Tab 切换：「日历」和「时间线」两种视图
- 日历视图：月份导航 + 日历网格，有日记的日期显示心情 emoji
- 时间线视图：卡片流，按时间倒序，每条显示日期、emoji、正文前 100 字预览

### 交互流程

- **新建日记**：日历视图中点击某天→弹出编辑弹窗→选择心情、天气→填写正文→保存
- **编辑日记**：点击已有日记→弹窗预填内容→修改→保存
- **同一天覆盖**：POST 同一天日记时自动更新已存在的记录
- **删除**：确认后软删除

### 工具箱集成

- DrawFloatingButton 新增 `{ id: 'journal', label: '日记本', icon: <BookOpen /> }`
- App.tsx 新增 showJournalPanel 状态 + JournalPanel 渲染

## 后端设计

### 模块结构（参考 password 模块）

```
journal/
  controller/
    JournalController.java   - @PreAuthorize hasAnyRole ADMIN,AUTHOR
  domain/
    Journal.java             - 实体
  mapper/
    JournalMapper.java/xml   - MyBatis
  service/
    JournalService.java/impl - 业务逻辑
  vo/
    CreateJournalRequest.java
    UpdateJournalRequest.java
    JournalVO.java
```

### 关键逻辑

- 每天每用户只有一篇日记：INSERT 前检查 unique(user_id, journal_date)，存在则 UPDATE
- 日历查询：SELECT user_id, journal_date, mood_emoji WHERE YEAR=... AND MONTH=...
- 时间线查询：分页 + ORDER BY journal_date DESC

## 权限

- 仅 admin/author 可使用
- 每个用户只能操作自己的日记
- 所有查询/更新带 user_id 过滤
