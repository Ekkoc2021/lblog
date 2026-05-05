---
name: delegate
description: Invoked via /delegate command only. Launches a sub-agent to implement
  a coding task, then runs an automated quality review on the result.
disable-model-invocation: true
---

# /delegate — Sub-Agent Task Executor

Launch a sub-agent to implement a coding task, then review the result.

## How to use

```
/delegate 在 AdminController 中增加批量删除文章接口
/delegate 读取 5-5/agent-c-prompt.md 完成任务
/delegate 完成任务 9、10、11
```

## Core Principle

**Main Claude only does lightweight orchestration — all substantive work is done by sub-agents.**

- The main agent NEVER reads and copies file contents into prompts
- If the task references a doc file, tell the sub-agent to read it directly
- The sub-agent is self-sufficient: it reads files, writes code, compiles

## Flow

### Step 1: Clarify & Confirm

If the user's task description is ambiguous, first clarify.

When the user references task numbers or vague descriptions:

```
User: "完成任务 9、10、11"

→ 主 Claude 查计划文档，输出概要确认：
Task 9  — TokenAuthenticationFilter (新建)
Task 10 — CustomAuthEntryPoint + CustomAccessDeniedHandler (新建)
Task 11 — 修改 AuthController + AdminController
依赖: 需 SecurityConfig 已就绪

是否启动 agent 执行这 3 个任务？(Y/n)
```

When the user references a specific doc:

```
User: "/delegate 读取 5-5/agent-c-prompt.md 完成任务"

→ 主 Claude 只确认范围和依赖：
即将启动 agent 按 5-5/agent-c-prompt.md 完成。
目标: 6 个文件（3 新建 + 3 修改）
验证: mvn compile

是否启动？(Y/n)
```

If the task is self-explanatory, output a brief plan for confirmation:

```
即将启动 agent:
- 新建: XXX.java
- 修改: YYY.java
验证: mvn compile
是否启动？(Y/n)
```

Wait for user confirmation. If cancelled, stop.

### Step 2: Launch sub-agent

Launch the Agent tool (general-purpose, `run_in_background: true`).

**DO NOT read task files and copy their content into the prompt.**
Instead, instruct the sub-agent to read them itself.

**When task references a doc file** (e.g. "读取 5-5/agent-c-prompt.md"):

```
minimal prompt to sub-agent:
<project-root>
<build-command>
<reference to the doc file: "Read 5-5/agent-c-prompt.md and follow its instructions">
<"Update 2今日开发计划.md when done">
```

**When task is self-contained** (e.g. "实现批量删除文章接口"):

```
The agent's prompt must be self-contained with exact file paths, class names,
and method signatures. But the CONTENT of existing reference files should NOT
be inlined — tell the agent to read them:
  "Read AdminController.java for existing patterns, then add a batchDelete method"
```

### Step 3: Wait for sub-agent completion

Wait for the background agent to finish. If the notification indicates errors:
1. Diagnose the issue
2. Fix it directly (not via a new agent)
3. Re-verify compilation
4. Only then proceed to review

### Step 4: Launch review sub-agent

Launch another Agent (general-purpose, background) to review:

```
Review files: <list>
Check: design compliance, code quality, security, compilation
Fix issues directly.
Output brief summary.
```

### Step 5: Report

Report results to the user: what was done, review outcome, next steps.

## Notes

- This skill only activates via `/delegate` — never auto-trigger.
- Always use `run_in_background: true` for sub-agents.
- Main Claude NEVER reads file contents to forward them — sub-agents read files directly.
- Confirmation step is mandatory.
- If sub-agent fails, fix and verify before proceeding to review.
