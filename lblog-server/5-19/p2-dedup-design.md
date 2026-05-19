# P2 结构化去重 — 设计草案

> 状态：📄 待实现（当前不做）
> 前置依赖：P0（对话持久化）

---

## 一、概念

多轮工具调用中，同一工具可能多次被调用并返回相似结果，这些相似的工具响应消息在上下文中重复占用窗口。

```
场景：display_diagram 被调了 3 次
  [Assistant] display_diagram(xml="<mxGraphModel>...</mxGraphModel>")
  [Tool] Diagram generated successfully
  [Assistant] display_diagram(xml="<mxGraphModel>...</mxGraphModel>")
  [Tool] Diagram generated successfully
  [Assistant] display_diagram(xml="<mxGraphModel>...</mxGraphModel>")
  [Tool] Diagram generated successfully

→ 3 次相同的 tool 结果，保留最新 1 次就够了
```

---

## 二、检测方案

### 2.1 按工具名 + 结果去重

最简单的方案：同一工具名，返回相同或相似的结果文本，只保留最新一次。

```java
public class DedupStrategy implements CompressionStrategy {
    @Override
    public List<Message> compress(List<Message> messages) {
        // 从后往前遍历，记录已见过的 (toolName, result) 组合
        // 遇到重复的旧组合就丢弃
    }
}
```

### 2.2 相似度比较

如果工具返回结果不完全相同但高度相似（如 `display_diagram` 每次返回 "Diagram generated successfully"），可以用简单的文本相似度判断：

| 方案 | 精度 | 成本 |
|------|------|------|
| 精确匹配 | 高 | 低 |
| 字符重叠率 | 中 | 低 |
| embedding 相似度 | 高 | 高 |

---

## 三、实现

### 3.1 作为 CompressionStrategy 实现

```java
public class DedupStrategy implements CompressionStrategy {

    @Override
    public boolean shouldCompress(List<Message> messages) {
        return countToolPairs(messages) > 0;
    }

    @Override
    public List<Message> compress(List<Message> messages) {
        List<Message> result = new ArrayList<>(messages);
        // 从尾部往前保留最新的工具调用对
        Set<String> seenTools = new HashSet<>();
        for (int i = result.size() - 1; i >= 0; i--) {
            if (result.get(i) instanceof AssistantMessage am && am.hasToolCalls()) {
                for (ToolCall tc : am.getToolCalls()) {
                    String key = tc.name() + ":" + getToolResult(result, i);
                    if (!seenTools.add(key)) {
                        // 重复的工具对，标记删除
                    }
                }
            }
        }
        return result;
    }
}
```

### 3.2 直接在 AiConfig 替换

```java
@Bean
public CompressionStrategy compressionStrategy() {
    return new DedupStrategy();
}
```

---

## 四、限制

| 限制 | 说明 |
|------|------|
| 仅对工具结果有效 | 不处理用户消息或普通 assistant 消息的重复 |
| 需要工具结果可比较 | 如果每次结果都不同，去重无效 |
| 可能丢失中间状态 | 同一个工具的连续调用可能是递进的，不能简单去重 |

**结论：当前只有一个 `display_diagram` tool，每次返回固定字符串，去重收益几乎为零。等工具数量多了再实现。**
