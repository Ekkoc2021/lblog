# P0 Test Plan — 对话持久化

> Date: 2026-05-17
> Scope: Steps 1-7 (对话持久化后端实现)

## 1. Unit Test Cases

### 1.1 ChatSessionMapper

| # | Case | Input | Expected |
|---|------|-------|----------|
| 1.1.1 | Insert session | userId=1, agentType="draw", modelName="deepseek-chat" | Returns auto-generated id, row inserted |
| 1.1.2 | Select by ID | Existing session id | Returns correct ChatSession |
| 1.1.3 | List by user+agent | userId=1, agentType="draw", page=1, size=20 | Ordered by updated_at DESC |
| 1.1.4 | Count by user+agent | userId=1, agentType="draw" | Correct count |
| 1.1.5 | Update title | sessionId, "New Title" | Title updated |
| 1.1.6 | Update stats | sessionId, delta=1, tokens=100 | message_count += 1, total_tokens += 100 |
| 1.1.7 | Update status (archive) | sessionId, status=0 | Status changed to 0 |
| 1.1.8 | Update status (delete) | sessionId, status=-1 | Status changed to -1 |

### 1.2 ChatMessageMapper

| # | Case | Input | Expected |
|---|------|-------|----------|
| 1.2.1 | Insert user message | sessionId, role="user", content="hello" | Row inserted |
| 1.2.2 | Insert assistant message with reasoning | sessionId, content="Hi", reasoningContent="thinking..." | Both content and reasoning stored |
| 1.2.3 | Batch insert | 3 messages | All 3 inserted |
| 1.2.4 | Select by session | sessionId | Ordered by msg_index ASC |
| 1.2.5 | Get max msg index | sessionId (has 3 messages) | Returns 2 (0-indexed) |
| 1.2.6 | Get max msg index (empty) | sessionId (no messages) | Returns -1 |
| 1.2.7 | Delete by session | sessionId | All messages removed |

### 1.3 ChatSessionService

| # | Case | Input | Expected |
|---|------|-------|----------|
| 1.3.1 | Create session | userId=1, agentType="draw", modelName="deepseek" | New ChatSession with default title, status=1 |
| 1.3.2 | List sessions | userId=1, agentType="draw" | Paginated list, newest first |
| 1.3.3 | Update title | sessionId, "My Chat" | Title changed |
| 1.3.4 | Delete session | sessionId | Status set to -1 (soft delete) |
| 1.3.5 | Check ownership (match) | session belongs to userId=1 | Returns true |
| 1.3.6 | Check ownership (mismatch) | session belongs to userId=2, check with userId=1 | Returns false |
| 1.3.7 | Check ownership (null user) | session has userId=null, check with userId=null | Returns true |

### 1.4 ChatMessageService

| # | Case | Input | Expected |
|---|------|-------|----------|
| 1.4.1 | Save user message | sessionId, "hello", msgIndex=0 | ChatMessage with role="user" |
| 1.4.2 | Save assistant message | sessionId, "Hi", "thinking...", null, msgIndex=1 | Contains reasoningContent |
| 1.4.3 | Save tool message | sessionId, "call_1", "display_diagram", result, msgIndex=2 | role="tool", toolCallId set |
| 1.4.4 | Batch save | 3 messages | All saved with sessionId set |
| 1.4.5 | Load messages | sessionId | Ordered by msg_index ASC |
| 1.4.6 | Max msg index | sessionId with 5 messages | Returns 4 |

### 1.5 DeepSeekMessageConverter

| # | Case | Input | Expected |
|---|------|-------|----------|
| 1.5.1 | supports deepseek | "deepseek-v4-flash" | true |
| 1.5.2 | supports non-deepseek | "gpt-4o" | false |
| 1.5.3 | toContextMessage assistant | ChatMessage role=assistant, content="Hi" | AssistantMessage("Hi") |
| 1.5.4 | toContextMessage non-assistant | ChatMessage role=user | null (falls through to generic handling) |
| 1.5.5 | toContextMessage with reasoning | includeReasoning=true, has reasoningContent | DeepSeekAssistantMessage with reasoningContent |
| 1.5.6 | toContextMessage reasoning disabled | includeReasoning=false, has reasoningContent | Plain AssistantMessage (no reasoning) |
| 1.5.7 | toStorageMessage | DeepSeekAssistantMessage with reasoningContent | ChatMessage with role=assistant and reasoningContent |
| 1.5.8 | toStorageMessageFromChunks | List of ChatClientResponse chunks | Aggregated ChatMessage |

### 1.6 ChatHistoryAdvisor

| # | Case | Input | Expected |
|---|------|-------|----------|
| 1.6.1 | before: no sessionId | request without sessionId | Returns original request unchanged |
| 1.6.2 | before: empty history | request with sessionId, empty DB | Injects only current user message |
| 1.6.3 | before: with history | request with sessionId, 3 existing messages | History injected before current message |
| 1.6.4 | before: system messages preserved | request with system + user messages | System messages remain at beginning |
| 1.6.5 | after: non-streaming response | ChatClientResponse with output | Saves assistant message to DB |
| 1.6.6 | after: no sessionId | no thread-local sessionId | Returns response unchanged |

## 2. Integration Test Cases

| # | Case | Steps | Expected |
|---|------|-------|----------|
| 2.1 | Full conversation save + load | 1. Create session 2. Save user msg 3. Save assistant msg 4. Load messages | All messages returned in order |
| 2.2 | DeepSeek reasoning preservation | Save assistant msg with reasoningContent, load it | reasoningContent preserved |
| 2.3 | Multiple sessions isolation | Create 2 sessions, add messages to each | Each session loads only its own messages |
| 2.4 | Session CRUD | Create → list → update title → delete → list again | Session appears in list after create, disappears after delete |
| 2.5 | Message ordering | Save 5 messages with consecutive msg_index | Load returns them in order |
| 2.6 | Session stats update | Save 3 messages, update stats with delta=3 | message_count increases by 3 |

## 3. Manual Test Scenarios

### 3.1 Basic Flow

**Setup**: Start server, ensure draw endpoint is open (permitAll).

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 3.1.1 | First message creates session | POST /api/v1/draw/chat without sessionId | 201 response, sessionId in done event |
| 3.1.2 | Conversation continues with sessionId | POST /api/v1/draw/chat with existing sessionId | Assistant responds with context from history |
| 3.1.3 | Reload conversation | GET /api/v1/ai/chat/sessions/{id}/messages | All previous messages returned |

### 3.2 Session Management

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 3.2.1 | List sessions | GET /api/v1/ai/chat/sessions?agentType=draw | Session list ordered by updated_at |
| 3.2.2 | Rename session | PUT /api/v1/ai/chat/sessions/{id}/title | Title updated |

### 3.3 Data Verification

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 3.3.1 | Check DB after chat | Query ai_chat_messages table | msg_index sequential, no gaps |
| 3.3.2 | Check reasoning content | Query assistant messages | reasoning_content populated for DeepSeek |
| 3.3.3 | Multiple conversations | Create 3 sessions, send messages to each | Each session has separate coherent history |

## 4. Test Data Preparation

```sql
-- Test session
INSERT INTO ai_chat_sessions (user_id, agent_type, title, model_name, message_count, total_tokens, status)
VALUES (NULL, 'draw', 'Test Session', 'deepseek-v4-flash', 0, 0, 1);

-- Test messages (assumes session id = 1)
INSERT INTO ai_chat_messages (session_id, role, content, reasoning_content, msg_index)
VALUES (1, 'user', 'Hello', NULL, 0);

INSERT INTO ai_chat_messages (session_id, role, content, reasoning_content, msg_index)
VALUES (1, 'assistant', 'Hi there!', 'User is greeting, respond politely', 1);

INSERT INTO ai_chat_messages (session_id, role, content, reasoning_content, msg_index)
VALUES (1, 'user', 'Draw a flow chart', NULL, 2);

INSERT INTO ai_chat_messages (session_id, role, content, msg_index, tool_calls)
VALUES (1, 'assistant', 'I will create a flow chart', 3, '[{"id":"call_1","type":"function","name":"display_diagram","arguments":"{}"}]');
```

## 5. Verification Checklist

- [ ] Tables `ai_chat_sessions` and `ai_chat_messages` exist in MySQL
- [ ] Build succeeds with zero errors
- [ ] All new files are free of IDE-reported problems
- [ ] No regression in existing functionality (build passes)
- [ ] Session auto-creation works when sessionId is null
- [ ] Messages are persisted after each assistant response
- [ ] Loaded history includes all previous messages in correct order
- [ ] Sessions can be listed, renamed, and soft-deleted
- [ ] Multiple agents sharing same userId have isolated conversations (by agent_type)
