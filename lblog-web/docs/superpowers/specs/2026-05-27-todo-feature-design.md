# 代办功能设计文档

## 概述

在博客工具箱中增加个人代办功能，用于规划要做的事情。以可拖拽浮动面板形式呈现，支持优先级、截止日期、标签分类、子任务和拖拽排序。

## 需求摘要

- **存储**: 后端 MySQL 持久化，数据跟随账号
- **入口**: 右下角工具箱浮动按钮（与 AI 绘图并列）
- **形式**: 可拖拽浮动面板
- **功能**: 优先级 + 截止日期 + 标签 + 子任务 + 拖拽排序
- **权限**: 每人只看自己的代办

## 数据模型 (MySQL)

```sql
CREATE TABLE todos (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    title         VARCHAR(500) NOT NULL,
    note          TEXT,
    priority      TINYINT DEFAULT 0,       -- 0=低 1=中 2=高
    status        TINYINT DEFAULT 0,       -- 0=待办 1=已完成
    due_date      DATE,
    sort_order    INT DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_todos_user (user_id)
);

CREATE TABLE todo_tags (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    name          VARCHAR(50) NOT NULL,
    UNIQUE KEY uk_user_tag (user_id, name)
);

CREATE TABLE todo_tag_relations (
    todo_id       BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (todo_id, tag_id),
    FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES todo_tags(id) ON DELETE CASCADE
);

CREATE TABLE todo_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    todo_id       BIGINT NOT NULL,
    title         VARCHAR(500) NOT NULL,
    completed     TINYINT(1) DEFAULT 0,
    sort_order    INT DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE
);
```

## API 设计

所有接口自动取当前用户 ID（`SecurityContextHolder` → `LoginUser.getUserId()`），仅操作自己的数据。

```
GET    /api/v1/todos              # 列表 (?status=0&priority=2&tag=blog)
POST   /api/v1/todos              # 创建
PUT    /api/v1/todos/:id          # 更新
DELETE /api/v1/todos/:id          # 删除
PUT    /api/v1/todos/sort         # 批量排序 { items: [{id, sortOrder}] }

GET    /api/v1/todos/:id/items           # 子任务列表
POST   /api/v1/todos/:id/items           # 添加子任务
PUT    /api/v1/todos/items/:id           # 更新子任务
DELETE /api/v1/todos/items/:id           # 删除子任务
PUT    /api/v1/todos/:id/items/sort      # 子任务排序

GET    /api/v1/todo-tags           # 当前用户标签列表（输入补全用）
```

## 后端架构

基于项目现有框架：**Spring Boot 3.5.7 + MyBatis + PageHelper**。遵循已有代码风格。

### 新增文件

```
src/main/java/com/yang/lblogserver/todo/
├── domain/
│   ├── Todo.java                 # @Data POJO, 字段匹配 DB 列
│   ├── TodoItem.java             # 子任务实体
│   └── TodoTag.java              # 标签实体
├── mapper/
│   ├── TodoMapper.java           # @Mapper 接口
│   ├── TodoItemMapper.java
│   └── TodoTagMapper.java
├── service/
│   ├── TodoService.java          # Service 接口
│   └── impl/
│       └── TodoServiceImpl.java  # @Service 实现
├── vo/
│   ├── TodoVO.java               # 返回对象（含 @Schema）
│   ├── CreateTodoRequest.java    # 创建请求（含 Jakarta 校验）
│   ├── UpdateTodoRequest.java    # 更新请求
│   └── SortRequest.java          # 排序请求
└── controller/
    └── TodoController.java       # @RestController, @PreAuthorize

src/main/resources/com/yang/lblogserver/todo/mapper/
├── TodoMapper.xml
├── TodoItemMapper.xml
└── TodoTagMapper.xml
```

### Controller 模式

```java
@Tag(name = "代办", description = "个人代办管理")
@Validated
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user.getUserId();
        }
        return null;
    }

    @GetMapping("/todos")
    @Operation(summary = "获取代办列表")
    public ApiResponse<PageResult<TodoVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String tag) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(todoService.listTodos(userId, page, pageSize, status, tag));
    }
    // ...
}
```

### Service 模式

- `TodoService` 接口 + `TodoServiceImpl` 实现
- 列表用 `PageHelper.startPage()` + `PageInfo` → `PageResult.of()`
- 写操作加 `@Transactional(rollbackFor = Exception.class)`
- 删除用物理删除（个人轻量数据，无需软删除）
- 标签自动创建：创建/更新待办时，不存在的标签名自动 INSERT 到 `todo_tags`

### 权限

- `@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")` — admin 和 author 都可用
- 数据隔离在 SQL 层通过 `WHERE user_id = #{userId}` 保证
- 不提供 admin 看所有用户数据的功能

## 前端架构

### 技术栈对齐

基于项目现有框架：**React 19 + Ant Design 6 + TypeScript**。优先使用 Ant Design 组件，遵循项目已有代码风格。

### 新增文件

```
src/
├── components/
│   └── TodoPanel.tsx              # 浮动面板主组件（position:fixed, 可拖拽）
│   └── TodoList.tsx               # 列表 + 拖拽排序
│   └── TodoItem.tsx               # 单条代办行（展开/收起子任务）
│   └── TodoSubtaskList.tsx        # 子任务列表
├── hooks/
│   └── useTodos.ts                # 代办数据 + CRUD 操作封装
├── services/
│   └── todoApi.ts                 # 基于 request<T>() 的 API 调用
├── types/
│   └── index.ts                   # 新增 Todo, TodoItem, TodoTag 类型
```

### 组件树与 Ant Design 组件映射

```
App.tsx
├── DrawFloatingButton.tsx (修改已有)
│   └── tools[] 新增 { id:'todo', label:'代办', icon:<CheckSquareOutlined />, action }
└── TodoPanel (position:fixed div, 拖拽同 DrawFloatingButton 模式)
    ├── 标题栏 (mousedown 拖拽) + 关闭按钮
    ├── <Tabs> (Ant Design) — 全部 / 进行中 / 已完成
    ├── 新建代办 → <Modal> + <Form> :
    │   ├── <Input>           标题 (必填)
    │   ├── <Input.TextArea>  备注
    │   ├── <Select>          优先级 (高/中/低)
    │   ├── <DatePicker>      截止日期
    │   └── <Select mode="tags">  标签 (自动补全已有标签)
    └── TodoList
        └── TodoItem × N
            ├── <Checkbox>       标记完成
            ├── 标题文本
            ├── <Tag>            优先级 (红/橙/绿)
            ├── 截止日期文本 (临近高亮)
            ├── <Tag>            分类标签
            └── TodoSubtaskList (内联展开，不额外弹窗)
                ├── 子任务 × N (<Checkbox> + 标题)
                └── + 添加子任务 (<Input> 内联)
```

### 拖拽排序

使用 HTML5 原生 drag-and-drop，排序结束后调用 `PUT /api/v1/todos/sort` 批量更新 sort_order。

### 面板行为

- 默认宽 380px，高自适应（最大 70vh），`<body>` 直接 render `position:fixed` div
- 标题栏拖拽移动逻辑复用 `DrawFloatingButton` 的 `mousedown` + `mousemove` 模式
- 关闭时 DOM 卸载，下次打开回到默认位置
- 仅对登录用户 (admin/author) 显示，从 `useAuth()` 取用户信息

### 修改已有文件

| 文件 | 改动 |
|------|------|
| `src/components/DrawFloatingButton.tsx` | tools 数组新增 todo 条目，action 回调通知 App.tsx |
| `src/App.tsx` | 新增 showTodoPanel 状态，toggle 逻辑，渲染 `<TodoPanel>` |
| `src/types/index.ts` | 追加 Todo 相关接口 |
| `src/services/api.ts` | 追加 todo API 函数（或放独立 `todoApi.ts`） |

## 实现分工

| 层 | 工作 |
|----|------|
| 后端 | 建表 DDL、Entity/Mapper/Service/Controller、API 鉴权 |
| 前端 | 类型定义、API 层、useTodos Hook、TodoPanel 组件族、工具箱集成 |
