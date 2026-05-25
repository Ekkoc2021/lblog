# 文章评论计数器修复设计文档

> 状态：设计中 | 日期：2026-05-25

## 一、问题描述

`posts.comment_count` 字段始终为 0，前端文章详情页和列表页显示的评论数不正确。

### 现象

- 前端 `PostDetail.tsx:157` 渲染 `post.commentCount`，显示始终为 0
- 首页列表、分类/标签/专栏筛选列表中的 `commentCount` 同样为 0

### 根因

`posts` 表有 `comment_count` 冗余计数列，写入 SQL（`PostsMapper.xml:insertPost`）初始化为 0，但此后**没有任何代码更新它**：

- `CommentsServiceImpl.createComment()` — 创建评论后未 +1
- `CommentsServiceImpl.updateStatus()` — 审核通过/驳回后未同步
- `CommentsServiceImpl.deleteComment()` — 删除评论后未 -1

对比同类冗余字段：
- `view_count` → `PostsMapper.incrementViewCount()` ✅ 有维护
- `like_count` → `PostsMapper.incrementLikeCount()` / `decrementLikeCount()` ✅ 有维护
- `comment_count` → ❌ 无任何更新逻辑

---

## 二、状态分析

### 评论状态定义（`comments.status`）

| 值 | 含义 |
|----|------|
| 0 | 待审核 |
| 1 | 审核通过 |
| 2 | 驳回 |

### 前端可见规则

前端查询评论的条件（`CommentsMapper.xml:selectRootByPostId`）：

```sql
WHERE ... AND (status = 1 OR user_id = #{currentUserId})
```

即：普通访客只能看到 status=1 的评论，评论作者自己可以看到自己所有状态的评论。

### 计数语义选择

`comment_count` 应统计该类文章下**所有已通过（status=1）的评论**，包含根评论和子回复。

理由：与前端可见规则一致（访客视角），且 `parent_id` 不影响"这是一条评论"的事实。

---

## 三、设计方案

### 总体思路

在 `CommentsServiceImpl` 中，任何导致评论**进入或离开 status=1** 的操作，都同步更新 `posts.comment_count`。

| 操作 | 状态变化 | comment_count |
|------|---------|---------------|
| 新建评论 | — → 0 | 不变（待审核不计入） |
| 审核通过 | 0 → 1 | **+1** |
| 审核通过（重新） | 2 → 1 | **+1** |
| 驳回 | 1 → 2 | **-1** |
| 重新审核（打回待审） | 1 → 0 | **-1** |
| 删除已通过评论 | 1 → 删除 | **-1** |
| 删除未通过评论 | 0/2 → 删除 | 不变 |
| 重复操作 | 1 → 1, 0 → 0 | 不变（安全忽略） |

### 修改文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|---------|------|
| 1 | `blog/mapper/PostsMapper.java` | 新增方法 | `incrementCommentCount` / `decrementCommentCount` |
| 2 | `blog/service/impl/CommentsServiceImpl.java` | 修改方法 | `updateStatus` / `deleteComment` 增加计数器维护 |
| 3 | SQL 脚本 | 新增 | 历史数据校准 UPDATE |

---

## 四、详细修改

### 4.1 `PostsMapper.java` — 新增两个注解方法

在 `decrementLikeCount` 之后新增：

```java
@Update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = #{postId}")
int incrementCommentCount(@Param("postId") Long postId);

@Update("UPDATE posts SET comment_count = comment_count - 1 WHERE id = #{postId} AND comment_count > 0")
int decrementCommentCount(@Param("postId") Long postId);
```

放在文件末尾即可，与其他计数器方法风格一致（参考已有的 `incrementLikeCount` / `decrementLikeCount`）。

### 4.2 `CommentsServiceImpl.updateStatus()` — 对比新旧状态

**现状：**

```java
@Override
public void updateStatus(Long id, Integer status) {
    commentsMapper.updateStatus(id, status);
}
```

**修改为：**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateStatus(Long id, Integer status) {
    Comments comment = commentsMapper.selectById(id);
    if (comment == null) {
        return;
    }
    Integer oldStatus = comment.getStatus();

    commentsMapper.updateStatus(id, status);

    // 维护 posts.comment_count：只有 status=1（通过）的评论才计入
    boolean wasApproved = oldStatus != null && oldStatus == 1;
    boolean isApproved = status != null && status == 1;

    if (wasApproved && !isApproved) {
        postsMapper.decrementCommentCount(comment.getPostId());
    } else if (!wasApproved && isApproved) {
        postsMapper.incrementCommentCount(comment.getPostId());
    }
    // 其他情况（1→1, 0→0, 0→2, 2→0, 2→2）均不操作
}
```

**说明：**
- 添加 `@Transactional` 保证 `updateStatus` + `posts.comment_count` 变更在同一事务中
- 先查询旧状态再做判断，避免 ABA 问题
- `oldStatus == null` 时 `wasApproved` 为 false，安全降级

### 4.3 `CommentsServiceImpl.deleteComment()` — 删除前判断

**现状：**

```java
@Override
public void deleteComment(Long id) {
    Comments c = commentsMapper.selectById(id);
    if (c != null && c.getRootId() != null) {
        commentsMapper.decrementReplyCount(c.getRootId());
    }
    commentsMapper.softDelete(id);
}
```

**修改为：**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void deleteComment(Long id) {
    Comments c = commentsMapper.selectById(id);
    if (c == null) {
        return;
    }

    if (c.getRootId() != null) {
        commentsMapper.decrementReplyCount(c.getRootId());
    }

    // 只有已通过的评论被删除时才减 post 计数
    if (c.getStatus() != null && c.getStatus() == 1) {
        postsMapper.decrementCommentCount(c.getPostId());
    }

    commentsMapper.softDelete(id);
}
```

**说明：**
- 早期返回 `c == null` 改为独立判断，避免原代码 `c != null &&` 嵌套
- `@Transactional` 新增，保证 `reply_count`、`comment_count`、软删除在同一事务

### 4.4 `CommentsService.java` — 接口无需改动

两个方法 `updateStatus` / `deleteComment` 签名不变，调用方（AdminCommentController）无需任何修改。`batchComments` 循环调用这两个方法时会自动获得事务保护。

---

## 五、历史数据校准

上线后执行一次，将已有评论数写入 `posts.comment_count`：

```sql
UPDATE posts p
SET comment_count = (
    SELECT COUNT(1)
    FROM comments c
    WHERE c.post_id = p.id
      AND c.status = 1
      AND c.is_delelte = 0
      AND c.deleted_at IS NULL
)
WHERE p.is_delelte = 0 AND p.deleted_at IS NULL;
```

> 执行前先 `SELECT` 预览影响行数，确认无误再 UPDATE。

---

## 六、测试要点

### 功能测试

| # | 场景 | 预期 |
|---|------|------|
| 1 | 发表评论 → 审核通过 | post.comment_count +1 |
| 2 | 发表评论 → 驳回 | 不变 |
| 3 | 已通过评论 → 驳回 | post.comment_count -1 |
| 4 | 驳回评论 → 重新通过 | post.comment_count +1 |
| 5 | 删除已通过评论 | post.comment_count -1 |
| 6 | 删除待审核评论 | 不变 |
| 7 | 删除驳回评论 | 不变 |
| 8 | 对已通过评论重复"通过" | 不变（幂等） |
| 9 | 批量通过 | 每条 +1，累加正确 |

### 边界测试

| # | 场景 | 预期 |
|---|------|------|
| 10 | comment_count 为 0 时递减 | SQL 有 `> 0` 守卫，不变成负数 |
| 11 | 并发审核同一评论 | `@Transactional` + InnoDB 行锁保证一致性 |
| 12 | 审核不存在的评论 ID | `selectById` 返回 null，直接 return |
| 13 | 批量操作事务独立性：审核一批评论 | 每条独立事务，互不影响 |
| 14 | 前后端联查：审核通过后刷新文章列表 | `commentCount` 显示正确值 |
| 15 | 历史校准 SQL：先 SELECT COUNT 再执行 UPDATE | 预览数与实际更新数一致 |

---

## 七、已知局限

### 7.1 `@Transactional` 自调用风险

如果 `batchComments` 等方法在 `CommentsServiceImpl` 内部通过 `this.updateStatus()` 调用，Spring AOP 不会拦截，`@Transactional` 失效。当前 `AdminCommentController` 在 Controller 层循环调用 Service 方法，每次调用走代理，事务正常工作。后续如需在 Service 内部批量调用，应使用自注入或提取到独立的批量方法。

### 7.2 并发重复更新

两个线程同时对同一条评论执行"审核通过"——都读到 `oldStatus=0`，都执行 +1。当前通过 `@Transactional` + InnoDB 行锁降低风险，但 REPEATABLE READ 隔离级别下仍可能发生。低并发博客场景影响极小，后续可通过 `SELECT ... FOR UPDATE` 或乐观锁加固。

---

## 八、不涉及的改动

- **前端**：无需修改，`post.commentCount` 字段不变
- **API 响应结构**：不变
- **缓存模块**：`comment_count` 直接从 `posts` 表读取，缓存层不受影响
- **`createComment()`**：新建评论默认 status=0，不计入计数，无需改动

---

## 八、实施检查清单

- [x] `PostsMapper.java` 新增 `incrementCommentCount` / `decrementCommentCount`
- [x] `CommentsServiceImpl.updateStatus()` 增加旧状态对比 + 计数器维护 + `@Transactional`
- [x] `CommentsServiceImpl.deleteComment()` 增加计数器维护 + `@Transactional`
- [x] 编译通过
- [ ] 执行历史数据校准 SQL
- [ ] 按测试要点逐条验证
