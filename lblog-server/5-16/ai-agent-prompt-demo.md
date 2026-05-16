# 新增 AI Agent 模块 — 提示词拼接 Demo

> 以新增一个 `ai/chat/` 模块（AI 对话助手）为例

## 文件结构

```
src/main/resources/prompts/chat/          # ① 新建目录放提示词
  system-default.md                       # 核心角色提示词
  context-rules.md                        # 上下文规则

ai/chat/                                  # ② 新建 Java 模块
  ChatPromptManager.java                  # 提示词组装器（注入 AiPromptService）
  ChatService.java                        # 业务服务
  controller/ChatController.java          # API 端点
```

## 提示词文件

`resources/prompts/chat/system-default.md`:

```markdown
You are a helpful programming assistant.
Answer questions about code, debugging, and software design.
Always respond in the user's language.
```

`resources/prompts/chat/context-rules.md`:

```markdown
## Context Rules
- Maximum response length: 2000 characters
- Include code examples when relevant
- Use markdown formatting for code blocks
```

## 模块的 PromptManager

```java
package com.yang.lblogserver.ai.chat;

import com.yang.lblogserver.ai.prompt.service.AiPromptService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatPromptManager {

    private final AiPromptService promptService;

    public ChatPromptManager(AiPromptService promptService) {
        this.promptService = promptService;
    }

    public String buildSystemPrompt() {
        // 一次取回 chat 模块所有提示词
        Map<String, String> p = promptService.getPromptMap("chat");

        // 按 key 取用，DB → 文件的兜底由 AiPromptService 处理
        String systemDefault = p.getOrDefault("system-default", "");
        String contextRules = p.getOrDefault("context-rules", "");

        return systemDefault + "\n\n" + contextRules;
    }
}
```

## 数据流

```
ChatController
  → ChatService
    → ChatPromptManager.buildSystemPrompt()    ← 模块级组装
      → AiPromptService.getPromptMap("chat")   ← 公共提示词服务
        → DB ai_prompts (where module='chat')
        → classpath:prompts/chat/*.md
        → Caffeine cache
```

## 管理端操作

```bash
# 初始化：将 chat 模块的提示词从文件导入 DB
curl -X POST "http://localhost:8099/iblogserver/api/v1/admin/ai/prompts/seed?module=chat" \
  -H "X-Token: {admin_token}"

# 查看已导入的提示词
curl "http://localhost:8099/iblogserver/api/v1/admin/ai/prompts?module=chat" \
  -H "X-Token: {admin_token}"

# 热修提示词
curl -X PUT "http://localhost:8099/iblogserver/api/v1/admin/ai/prompts/{id}" \
  -H "X-Token: {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{"content": "new prompt text...", "operator": "admin"}'
```

## 总结

每次加新 agent 只需做 3 件事：
1. 在 `prompts/{module}/` 下放 markdown 文件
2. 写一个 PromptManager（注入 AiPromptService，调 `getPromptMap()`）
3. 调用它组装提示词

没有基础设施层面的重复工作。
