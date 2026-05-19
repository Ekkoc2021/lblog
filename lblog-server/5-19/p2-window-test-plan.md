# P2 上下文窗口管理 — 测试计划

> 日期：2026-05-19
> 目标：验证 token 估算、滑动窗口策略、CompressionAdvisor 压缩链的正确性
> 对应实现：commit `e43ba2e`

---

## 测试结果（2026-05-19 执行）

> **注意：** sessionId 必须为数字，否则 ChatHistoryAdvisor 会抛出 `NumberFormatException` 并跳过历史加载。

### Step 1: 短对话（预算内） — ✅ 通过

```
请求 sessionId=42, "draw a login page"
→ SSE 正常返回 353 个 data 事件
→ 日志无 CompressionAdvisor 输出
```

**结论：** 消息数少、token 未超预算时，压缩不触发。

### Step 2: 多轮对话触发压缩 — ✅ 通过

```
8 轮对话累积到 sessionId=42
```

日志输出：
```
CompressionAdvisor: 4 → 3 messages
```

**分析：** 第 1 轮后消息累积到 4 条，估算 token 4017 > budget 4000，触发压缩，`SlidingWindowStrategy` 正确保留 system + 最近 2 条。

### Step 3: 工具调用循环 — ⚠️ 部分验证

- CompressionAdvisor 在 advisor 链中正确执行（位于 DeepSeekToolCallAdvisor 之后）
- 超预算时日志正常输出
- 工具调用循环中的压缩因 token 未超预算而未被触发（当前场景工具调用轮次较少）

### Step 4: 策略切换 — ❌ 未测试

需要其他 CompressionStrategy 实现，当前只有 SlidingWindowStrategy。

### 发现的问题

| 问题 | 说明 | 影响 |
|------|------|------|
| sessionId 必须为数字 | ChatHistoryAdvisor 用 `Long.parseLong()` | 非数字 sessionId 静默跳过历史加载 |
| shouldCompress 未触发 | 实际触发全是 overBudget，not shouldCompress | 策略的条数判定未被使用 |

---

## 测试层级

```
层级 1: 单元测试（组件级别）
  ├ CharBasedTokenEstimator — token 估算精度
  ├ SlidingWindowStrategy — shouldCompress / compress 逻辑
  └ CompressionAdvisor — 委托策略、prompt 改写

层级 2: 集成测试（advisor 链）
  └ 真实请求走 ChatHistoryAdvisor → DeepSeekToolCallAdvisor → CompressionAdvisor

层级 3: API 测试（端到端）
  └ 画图对话发起请求，验证压缩生效
```

---

## 1. CharBasedTokenEstimator

### 测试数据

| 输入 | 预期结果 | 公式 |
|------|---------|------|
| `null` | 0 | 空值保护 |
| `""`（空字符串） | 1 | `0/2 + 1` |
| `"hello"` | 3 | `5/2 + 1` |
| `"你好世界"` | 5 | `8/2 + 1` |
| `"a".repeat(100)` | 51 | `100/2 + 1` |
| 多条消息列表 | 每条估算之和 | 遍历累加 |

### 验证目标

- 空值/空字符串不抛异常
- 中文场景约 1.5-2 char/token，英文场景 3-4 char/token
- 估算值非负数

---

## 2. SlidingWindowStrategy

### 2.1 shouldCompress

| 场景 | 消息数 | maxMessages | minTrigger | 预期 |
|------|--------|-------------|------------|------|
| 历史在阈值内 | 5 | 50 | 70 | false |
| 历史刚超阈值 | 72 | 50 | 70 | true |
| 历史远超阈值 | 200 | 50 | 70 | true |
| 历史为空 | 0 | 50 | 70 | false |
| 历史刚好阈值 | 70 | 50 | 70 | false（>70，70不触发） |

### 2.2 compress

| 场景 | 消息序列 | maxMessages | 预期行为 |
|------|---------|-------------|---------|
| 正常压缩 | [S, A, B, C, D, E, F] 共7条 | 3 | 保留 [S, E, F]（system + 最近2条） |
| 无需压缩 | [S, A] 共2条 | 3 | 不变 |
| 空列表 | [] | 3 | 不变 |
| null | null | 3 | 不变 |
| 刚好 maxMessages | [S, A, B] 共3条 | 3 | 不变 |
| system 保护 | [S, A, B, C, D] 共5条 | 1 | 只保留 [S] |

**注意：** compress 保留 messages[0]（假设为 system message）+ 最近 N-1 条。

---

## 3. CompressionAdvisor

### 3.1 决策逻辑

| 场景 | token 是否超预 | shouldCompress | 预期行为 |
|------|---------------|---------------|---------|
| 预算充足、消息少 | false | false | 不压缩 |
| 预算超了 | true | false | 压缩（不问策略） |
| 预算充足、消息过多 | false | true | 压缩 |
| 预算超、消息也多 | true | true | 压缩 |

### 3.2 集成验证

通过实际 API 请求，验证 advisor 链是否按预期工作：

```
请求 → ChatHistoryAdvisor → DeepSeekToolCallAdvisor → CompressionAdvisor
                                                           ↓
                                                    检查日志输出
```

---

## 4. API 集成测试（端到端）

### 前置条件

- 服务运行在 `localhost:8099`，context-path `/iblogserver`
- DeepSeek API key 有效

### 测试步骤

#### Step 1: 短对话（预算内，不触发压缩）

```
POST /api/v1/draw/chat
{
  "sessionId": "test-p2-001",
  "messages": [{"role": "user", "content": "画一个登录页面流程图"}],
  "message": "画一个登录页面流程图"
}
```

**预期：**
- ✅ SSE 正常返回
- ✅ 日志无 `CompressionAdvisor` 输出
- ✅ 消息数未减少

#### Step 2: 构造超长对话触发压缩

```
POST /api/v1/draw/chat
{
  "sessionId": "test-p2-002",
  "messages": [{"role": "user", "content": "第1轮对话..."}, ... 多轮历史],
  "message": "第N轮对话"
}
```

通过多次调用同一 sessionId 累积历史，直到超过 `maxMessages` (50) 或 token 预算 (4000)。

**验证方法：**
- 检查日志 `CompressionAdvisor: N → M messages`
- 确认压缩后 M < N

#### Step 3: 验证工具调用循环中的压缩

在对话中要求 AI 多次调工具，观察工具调用循环中 CompressionAdvisor 是否被执行。

**预期：**
- 每次工具调用后，`CompressionAdvisor` 的 `after`（实际是 `adviseStream` 的 `chain.nextStream`）被触发
- 超预算时日志输出

#### Step 4: 配置切换压缩策略

修改 `AiConfig` 中的 `CompressionStrategy` bean 为其他实现（如后续新增的策略），验证：

- 服务重启后新策略生效
- `CompressionAdvisor` 未修改
- 压缩行为按新策略执行

---

## 5. 验收标准

| # | 标准 | 验证方式 |
|---|------|---------|
| 1 | Token 估算不抛异常、返回非负数 | 单元测试 |
| 2 | shouldCompress 在阈值以下返回 false | 单元测试 |
| 3 | compress 保留 system message + 最近 N 条 | 单元测试 |
| 4 | CompressionAdvisor 不压缩预算内的请求 | 集成（API + 日志） |
| 5 | CompressionAdvisor 压缩超预算的请求 | 集成（API + 日志） |
| 6 | 工具调用循环中压缩仍生效 | 集成（API + 日志） |
| 7 | 换策略只需改 AiConfig，不动 advisor | 代码检视 |

---

## 6. 测试环境

| 项目 | 配置 |
|------|------|
| Spring Boot profile | default |
| Port | 8099 |
| Context path | /iblogserver |
| maxHistoryTokens | 4000（默认） |
| SlidingWindowStrategy | maxMessages=20, minTrigger=30 |

**日志筛选：**
```
grep "CompressionAdvisor\|SlidingWindow\|CharBasedEstimator" service-logs/*.log
```
