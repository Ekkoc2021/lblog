# P2 上下文健康检查 — 设计草案

> 状态：📄 待实现
> 备注：当前阶段不做，记录设计供后续参考

---

## 一、概念

健康检查是在上下文注入 LLM 前，对消息列表做的**内容质量检测**，相当于"安检"——在发给模型之前先扫一遍有没有问题。

当前 `CompressionAdvisor` 只做了**量的控制**（token 多了就丢），健康检查做的是**质的控制**（内容有问题就修）。

---

## 二、检查项

### 1. 重复信息检测

**问题：** 多轮对话中，同一信息被反复提及，浪费 token。

```
用户第 2 轮: "用 MySQL 8.0"
用户第 5 轮: "数据库是 MySQL 8.0"
→ 上下文里两条消息都带了"MySQL 8.0"，后一条就够了
```

**检测方案：**

| 方式 | 精度 | 成本 | 说明 |
|------|------|------|------|
| 关键词匹配 | 低 | 0 | 同一个词出现多次，保留最新 |
| embedding 相似度 | 中 | 向量库依赖 | 语义相似的消息去重 |
| LLM 判断 | 高 | 高（又调一次模型） | 调 LLM 判断是否重复，成本太高不推荐 |

**建议方案：** 基于消息内容的简单去重——用 `CharBasedTokenEstimator` 估算后比较相似度（字符重叠率），或直接按"同一 role + 近似内容"丢弃。

### 2. 矛盾信息标记

**问题：** 用户在不同轮次说了相反的要求，模型可能搞混。

```
用户第 3 轮: "用暗色主题"
用户第 10 轮: "改成亮色主题"
→ 两个指令都在上下文中，模型可能矛盾
```

**检测方案：**

| 方式 | 精度 | 成本 |
|------|------|------|
| 规则匹配 | 低 | 0 |
| LLM 提取+对比 | 高 | 高 |

**标记方式：** 在注入前检测到矛盾时，追加一条 system 提示：

```
[Note: User preference for "theme" changed from "dark" to "light" at turn 10.
 Please follow the latest instruction.]
```

### 3. 过期信息标记

**问题：** 上下文中有时间敏感的旧信息，模型不知道它过期了。

```
用户第 1 轮: "现在帮我查一下 2026-05-19 的天气"
→ 过了一小时还在上下文里，模型可能误以为是当前时间
```

**检测方案：** 检测消息中的时间相关关键词（如日期、时间、最新等），与当前时间对比。

---

## 三、架构定位

```
ChatHistoryAdvisor → DeepSeekToolCallAdvisor → CompressionAdvisor → HealthCheckAdvisor → Model
                                                        ↓
                                                   量控制             质控制
```

健康检查可以作为一个**独立 advisor** 放在 CompressionAdvisor 之后，或者**内嵌到 CompressionAdvisor** 中作为策略的一部分。

### 方案对比

| 方案 | 复杂度 | 说明 |
|------|--------|------|
| A. 独立 HealthCheckAdvisor | 低 | 新增 advisor，放入 advisor 链，职责单一 |
| B. 扩展 CompressionAdvisor | 中 | 在 compressIfNeeded 中追加检测逻辑 |
| C. 扩展 CompressionStrategy | 中 | 健康检测作为策略的一部分 |

**推荐方案 A**——保持与 CompressionAdvisor 同样的模式，职责单一。

---

## 四、接口设计

```java
public interface HealthCheckService {
    /** 对消息列表执行健康检查，返回检查结果（包含修复建议） */
    HealthCheckResult check(List<Message> messages);
}

public class HealthCheckResult {
    private boolean hasIssues;            // 是否有问题
    private List<String> repairs;         // 自动修复操作描述
    private List<String> contradictions;  // 检测到的矛盾列表
    private String systemHint;            // 拼入 system prompt 的提示文本
}
```

### HealthCheckAdvisor

```java
public class HealthCheckAdvisor implements BaseAdvisor {
    private final HealthCheckService healthCheck;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        List<Message> messages = request.prompt().getInstructions();
        HealthCheckResult result = healthCheck.check(messages);

        if (result.hasIssues()) {
            // 有矛盾/过期时，追加 system hint
            if (result.systemHint() != null) {
                messages.add(new SystemMessage(result.systemHint()));
            }
            // 有重复时，去重
            if (!result.repairs().isEmpty()) {
                messages = applyRepairs(messages, result);
            }
        }

        return chain.next(request.mutate()
                .prompt(new Prompt(messages, request.prompt().getOptions()))
                .build());
    }
}
```

---

## 五、实现优先级

| 实现项 | 优先级 | 工作量 | 独立价值 |
|--------|--------|--------|---------|
| 重复检测（关键词去重） | 低 | 0.5 天 | 节省 token，减少干扰 |
| 矛盾标记 | 低 | 1 天 | 提高指令遵从 |
| 过期标记 | 低 | 0.5 天 | 减少时间混淆 |

全部优先级不高——因为当前画图对话的场景决定了：
- 对话轮次不多（5-10 轮）
- 用户不会频繁改需求方向
- 没有时间敏感的内容

**建议：** 等对话场景多了（如通用聊天、代码生成）再实现。
