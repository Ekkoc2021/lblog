# P2 摘要压缩 — 设计草案

> 状态：📄 待实现（当前不做）
> 前置依赖：P0（对话持久化）、P2 Token 预算计算

---

## 一、概念

当历史对话超过预算时，不直接丢弃旧消息，而是**用 LLM 将旧对话压缩成一段摘要**，用摘要替代原文。

```
Before:
  [User] 帮我画一个登录页面
  [Assistant] 好的，这是一个带用户名密码的登录页面... [draw.io XML]
  [User】 加上记住我复选框
  [Assistant] 已添加... [draw.io XML]
  [User] 背景色改成蓝色
  [Assistant] 已完成... [draw.io XML]

After:
  [Summary] 用户画了一个登录页面：包含用户名密码、记住我复选框、蓝色背景。已生成对应 XML。
  [User] 帮我加一个注册按钮
  [Assistant] ...（继续当前对话）
```

---

## 二、实现方案

### 2.1 架构

摘要压缩比较重（要调 LLM），不适合放在 CompressionAdvisor 的同步路径中。建议作为独立组件：

```
CompressionAdvisor (同步，每次 LLM 调用前)
  └ 先试丢旧消息，预算还不够
       └ 触发异步摘要 → 下次请求时摘要已就绪，直接替换
```

### 2.2 流程

```
第 N 次请求：预算超了
  → 标记"需要摘要" → 异步调 LLM 压缩旧对话
  → 本次先用滑动窗口兜底

第 N+1 次请求：
  → 检查是否有就绪的摘要 → 有则替换历史最旧的部分
```

### 2.3 存储

```sql
CREATE TABLE ai_conversation_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    summary_text TEXT NOT NULL,            -- 摘要内容
    msg_range_start INT NOT NULL,          -- 摘要覆盖的消息起始 msg_index
    msg_range_end INT NOT NULL,            -- 摘要覆盖的消息结束 msg_index
    created_at DATETIME
);
```

### 2.4 摘要触发条件

- 历史消息超过预算，且滑动窗口已经执行过
- 历史消息数超过一定阈值（如 30 轮）
- 摘要生成使用独立 LLM 调用，不阻塞主流程

---

## 三、实现步骤

1. 创建 `ai_conversation_summaries` 表
2. 实现 `SummaryGenerator` 组件——调 LLM 压缩旧对话为摘要
3. 在 `CompressionAdvisor` 中集成：压缩时检查是否有可用摘要，有则替换
4. 添加异步触发的调度逻辑

---

## 四、评估

| 维度 | 评估 |
|------|------|
| 复杂度 | 高 — 需要异步调度 + LLM 调用 + 存储 |
| Token 节省 | 高 — 几十轮对话可压缩为一段摘要 |
| 信息损失 | 有 — 摘要可能遗漏细节 |
| 适用场景 | 超长对话（50+ 轮） |

**结论：当前画图对话轮次少，收益低，建议后续需要时再做。**
