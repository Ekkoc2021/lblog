# P0 Code Review: 对话持久化 (Conversation Persistence)

**Reviewer:** code-reviewer
**Date:** 2026-05-17
**Scope:** 22 files (15 new, 3 modified, 4 XML/domain)

---

## Summary

| Severity | Count | Key Areas |
|----------|-------|-----------|
| CRITICAL | 1 | Reactor threading: ThreadLocal used across threads in reactive pipeline |
| HIGH | 3 | Tool-call history lost; session stats never updated; history context broken for tool-calling agents |
| MEDIUM | 6 | Concurrency, null-user edge cases, empty touchSession(), Lombok inconsistency, owner-check info leak |
| LOW | 5 | SQL portability, missing constructors, hardcoded empty modelName, style nits |

---

## CRITICAL

### C1. ChatHistoryAdvisor: ThreadLocal used in Reactor operators (wrong thread)

**File:** `ChatHistoryAdvisor.java:57-58, 165-184`
**Severity:** CRITICAL

The `adviseStream()` method sets `SESSION_ID_HOLDER` and `MODEL_NAME_HOLDER` (ThreadLocal) on line 131-132 (the Tomcat request thread), then reads them inside `doOnComplete` (line 169) and `doOnError` (line 182-183). Reactor operators execute on scheduler threads, NOT the original caller thread. This means:

1. `MODEL_NAME_HOLDER.get()` on line 169 returns `null` at runtime, causing `findConverter(null)` to always use the fallback converter.
2. `SESSION_ID_HOLDER.remove()` / `MODEL_NAME_HOLDER.remove()` on lines 177-178, 183 never clean up the original thread's ThreadLocal, causing memory leaks in long-lived Tomcat threads.
3. In the outer `catch` (lines 188-189), cleanup runs on the original thread -- but this only fires if the initial setup (lines 135-159) throws synchronously, not if the Flux errors later.

**Fix:** Capture values in local finals before entering the reactive chain:

```java
final String capturedSessionId = sessionId;
final String capturedModelName = modelName;
SESSION_ID_HOLDER.set(sessionId);  // only if needed for before() path
MODEL_NAME_HOLDER.set(modelName);

// In doOnComplete/doOnError, reference capturedSessionId/capturedModelName instead.
// Remove cleanup from doOnComplete/doOnError entirely -- clean up in the outer try-catch-finally
// by wrapping the returned Flux:
return stream
    .doOnNext(chunks::add)
    .doOnComplete(() -> {
        // use capturedSessionId, capturedModelName -- NOT ThreadLocal
        ...
    })
    .doOnError(e -> { /* log only */ });
    // NO ThreadLocal cleanup in callbacks
```

Then ensure cleanup is done once the Flux terminates (e.g., wrap it or move cleanup after `chain.nextStream` somehow -- this is inherently tricky with reactive). Consider restructuring to avoid ThreadLocal in the reactive path entirely.

---

## HIGH

### H1. Tool-call history lost on context reconstruction

**File:** `DeepSeekMessageConverter.java:37-48` + `ChatHistoryAdvisor.java:229-245`
**Severity:** HIGH

`toContextMessage()` creates `AssistantMessage` for "assistant" roles but **never copies tool calls** (the `AssistantMessage` constructor doesn't take tool calls, and there is no `setToolCalls()` on the built object). Simultaneously, `buildContextMessages()` skips "tool" role messages entirely (line 240 comment says "handled internally by tool call advisor").

**Impact:** When loading conversation history for a tool-calling agent (e.g. draw agent), the assistant's tool-call messages are converted to plain `AssistantMessage` with no tool calls, and the subsequent tool result messages are dropped. On the next turn, the LLM sees orphaned assistant responses with no context of past tool usage. This can cause:
- Re-execution of the same tool calls
- Confused/erroneous responses due to missing context
- The tool-calling loop breaking entirely in multi-turn conversations

**Fix:** Either:
1. In `toContextMessage()`: when `stored.getToolCalls()` is non-null, deserialize the tool calls JSON and attach them to the `AssistantMessage`. Add the corresponding tool results as `ToolResponseMessage` in `buildContextMessages()`.
2. Or: for V0, document that history-based context only works for non-tool-calling flows. This is a design limitation.

### H2. Session stats never updated after message persistence

**Files:** `ChatHistoryAdvisor.java:99-110, 166-173`, `ChatMemoryStoreImpl.java:42-44`
**Severity:** HIGH

The `ChatHistoryAdvisor` saves assistant messages in `after()` and `doOnComplete()` but never calls `sessionService.updateStats()` or `chatMemoryStore.touchSession()`. The `ChatMemoryStoreImpl.touchSession()` is a no-op (line 43). This means:
- `ai_chat_sessions.message_count` is always 0
- `ai_chat_sessions.total_tokens` is always 0
- `ai_chat_sessions.updated_at` does NOT update on message (relies on `ON UPDATE CURRENT_TIMESTAMP` but no UPDATE runs)

**Impact:** The session list shows `messageCount: 0` for all sessions, making the UI misleading. The `updated_at` column never refreshes after creation.

**Fix:**
```java
// In ChatHistoryAdvisor.after(), after saving the message:
sessionService.updateStats(sid, 1, stored.getTokens() != null ? stored.getTokens() : 0);

// In ChatHistoryAdvisor.adviseStream() doOnComplete, after saving:
sessionService.updateStats(sid, 1, stored.getTokens() != null ? stored.getTokens() : 0);
```

Or wire this into `ChatMemoryStore.saveMessages()`.

### H3. User message saved before LLM call, no rollback if call fails (both paths)

**File:** `ChatHistoryAdvisor.java:60-63` (before), `ChatHistoryAdvisor.java:137` (adviseStream)
**Severity:** HIGH

`saveCurrentUserMessage()` persists the user message to the database **before** the LLM call starts (lines 63, 137). If the LLM call fails (network error, API timeout, rate limit), the user message is permanently stored without a corresponding assistant response. This leaves orphaned user messages in the database.

**Impact:** Conversation history becomes inconsistent -- user messages without replies. On reload, the last user message appears unanswered.

**Fix:** Either:
1. Save the user message in a transaction that also records the assistant response (harder with streaming + async)
2. Move user message save to the `doOnComplete` callback alongside the assistant save (add a user message flag to the batch save)
3. For V0, accept this as known behavior and document it. If acceptable, lower to MEDIUM.

---

## MEDIUM

### M1. `selectByUserAndAgent` with null userId returns empty results

**File:** `ChatSessionMapper.xml:16-22`
**Severity:** MEDIUM

When `userId` is `null` (anonymous user), the generated SQL is `WHERE user_id = NULL` which in MySQL is always false (NULL is not equal to NULL in SQL). Anonymous users can never list their sessions.

The controller's `listSessions` endpoint (ChatSessionController.java:38-41) passes `getCurrentUserId()` which can return null, and the service method passes it directly to the mapper.

**Fix:** Use `<if test="userId != null">` conditional in the XML, adding `OR user_id IS NULL` when userId is null:

```xml
<where>
    <if test="userId != null">user_id = #{userId}</if>
    <if test="userId == null">user_id IS NULL</if>
    AND agent_type = #{agentType}
    AND status >= 0
</where>
```

### M2. `ChatMemoryStoreImpl.touchSession()` is a no-op

**File:** `ChatMemoryStoreImpl.java:43-44`
**Severity:** MEDIUM

The interface defines `touchSession()` and the implementation body is empty. The DDL has `ON UPDATE CURRENT_TIMESTAMP` on `updated_at`, but since no UPDATE query runs, the timestamp never refreshes. This is related to H2.

**Fix:** Either implement with `sessionMapper.updateStats(sessionId, 0, 0)` (which triggers an UPDATE) or remove the unused method.

### M3. `checkOwnership` leaks session existence information

**File:** `ChatSessionController.java:83-88`
**Severity:** MEDIUM

```java
private void checkOwnership(Long sessionId) {
    Long userId = getCurrentUserId();
    if (!sessionService.checkOwnership(sessionId, userId)) {
        throw new IllegalArgumentException("Session not found or access denied");
    }
}
```

When a session does not exist AND when a session exists but belongs to another user, the same error message is returned. However, `selectById` is called before the error, allowing timing-based enumeration. More importantly, ownership check is done **in the controller** using an unchecked exception rather than at the service boundary.

**Fix:** Move ownership check into the service layer. Consider a dedicated `AccessDeniedException` (which the `GlobalExceptionHandler` already handles with 403 on line 62-66) rather than `IllegalArgumentException` (which returns 400).

### M4. `ChatHistoryAdvisor` ordering allows concurrent session writes

**File:** `ChatHistoryAdvisor.java:31-32` (ThreadLocal), `ChatHistoryAdvisor.java:199-217` (saveCurrentUserMessage)
**Severity:** MEDIUM

Two concurrent requests for the same `sessionId` can:
1. Both load history before either saves (no locking)
2. Both call `getMaxMsgIndex()` at the same time, getting the same `maxIdx`
3. Both save with `msgIndex = maxIdx + 1` -- **duplicate index values**

The `msgIndex` column is not UNIQUE constrained per session, so duplicates won't cause a DB error. This leads to message ordering corruption.

**Fix:** Add a `UNIQUE KEY (session_id, msg_index)` to `ai_chat_messages` DDL. Add retry logic or use SELECT ... FOR UPDATE on the session row to serialize writes.

### M5. Controller inner classes avoid Lombok convention

**File:** `ChatSessionController.java:118-133`
**Severity:** MEDIUM

`CreateSessionRequest` and `UpdateTitleRequest` use manual getter/setter. The rest of the project uses Lombok `@Data`. This is inconsistent and adds boilerplate. These classes are also `public static` inner classes -- they should be `private` or package-private since they're only used as request bodies for this controller.

**Fix:**
```java
@Data
private static class CreateSessionRequest {
    @NotBlank
    private String agentType;
    private String modelName;
}

@Data
private static class UpdateTitleRequest {
    @NotBlank
    private String title;
}
```

Add `@Valid` to the method parameter and `@NotBlank` validation annotations.

### M6. `DeepSeekMessageConverter.toStorageMessageFromChunks` loses tool calls

**File:** `DeepSeekMessageConverter.java:70-97`
**Severity:** MEDIUM

The chunk-based aggregation for streaming discards tool calls from individual chunks. For tool-calling agents using streaming, tool calls from the `AssistantMessage` branch (lines 83-85) are ignored -- only text is captured. The resulting stored `ChatMessage` has no `toolCalls` field set.

**Fix:** Extract tool calls from the final aggregated chunk or from the `AssistantMessage` output in the last response. The last chunk typically contains the complete tool call data.

---

## LOW

### L1. MySQL-specific LIMIT syntax

**File:** `ChatSessionMapper.xml:21`, `ChatMessageMapper.xml:35`
**Severity:** LOW

Uses `LIMIT #{offset}, #{size}` which is MySQL-specific syntax. Project uses MySQL 8 exclusively so this works, but the standard SQL syntax `LIMIT #{size} OFFSET #{offset}` is preferred for portability.

### L2. `ChatSessionVO` / `ChatMessageVO` missing explicit constructors

**Files:** `ChatSessionVO.java`, `ChatMessageVO.java`
**Severity:** LOW

Both VOs use only `@Data` without `@NoArgsConstructor` or `@AllArgsConstructor`. `@Data` generates a constructor only if fields are `final`. Spring can still deserialize via the default no-arg constructor (generated because there's no explicit constructor). No functional issue, but inconsistent with the domain classes which have `@NoArgsConstructor @AllArgsConstructor`.

### L3. `DiagramService` hardcodes `modelName` as empty string

**File:** `DiagramService.java:78, 158`
**Severity:** LOW

```java
spec.param("modelName", "");
```

This forces the ChatHistoryAdvisor to use the fallback converter (first in the list). If multiple converters are registered and the first doesn't match deepseek, the wrong converter could be used. The injected `modelName` from application config should be passed instead.

**Fix:** Inject `@Value("${spring.ai.deepseek.chat.options.model}") String modelName` into DiagramService and pass it here.

### L4. No `@Validated` / `@Valid` on controller request parameters

**File:** `ChatSessionController.java:35, 45, 53`
**Severity:** LOW

The project convention is `@Validated` at class level with `jakarta.validation` annotations. The `ChatSessionController` lacks validation annotations on request bodies and params. Parameter errors will not produce user-friendly messages.

### L5. `selectRecentBySessionId` returns DESC order

**File:** `ChatMessageMapper.java:18-20`, `ChatMessageMapper.xml:32-37`
**Severity:** LOW

The method returns messages in `msg_index DESC` order. Callers expecting chronological order (ascending) must reverse the list. Not currently an issue since the method is unused outside the mapper interface, but worth documenting.

---

## Per-File Assessment

| # | File | Verdict | Key Issues |
|---|------|---------|------------|
| 1 | ChatSession.java | PASS | Clean domain entity. No issues. |
| 2 | ChatMessage.java | PASS | Clean domain entity. No issues. |
| 3 | ChatSessionVO.java | PASS | Minor: missing @NoArgsConstructor (L2). |
| 4 | ChatMessageVO.java | PASS | Minor: missing @NoArgsConstructor (L2). ToolCallVO inner class is clean. |
| 5 | ChatSessionMapper.java | PASS | Interface correctly matches XML. |
| 6 | ChatMessageMapper.java | PASS | Interface correctly matches XML. |
| 7 | ChatSessionService.java | PASS | Well-factored interface. |
| 8 | ChatMessageService.java | PASS | Clean interface, covers all CRUD operations. |
| 9 | ChatSessionServiceImpl.java | WARN | `checkOwnership` works at Java level but `selectByUserAndAgent` fails for null userId (M1). |
| 10 | ChatMessageServiceImpl.java | PASS | Clean delegation to mapper. |
| 11 | ChatSessionController.java | WARN | No validation (L4), no Lombok on inner classes (M5), ownership info leak (M3). |
| 12 | ChatMemoryStore.java | PASS | Clean interface. |
| 13 | ChatMemoryStoreImpl.java | FAIL | `touchSession()` is empty (M2); no stats propagation (H2). |
| 14 | ModelMessageConverter.java | PASS | Well-designed converter interface. |
| 15 | ContextPolicy.java | PASS | Simple POJO, all getters/setters present. |
| 16 | DeepSeekMessageConverter.java | FAIL | Tool calls not preserved in history context (H1); chunk tool calls lost (M6). |
| 17 | ChatHistoryAdvisor.java | FAIL | ThreadLocal + Reactor threading race (C1); stats never updated (H2); user msg orphaned on error (H3); concurrent write race (M4). |
| 18 | AiConfig.java | PASS | Advisor ordering is correct -- ChatHistoryAdvisor before DeepSeekToolCallAdvisor. |
| 19 | DiagramService.java | WARN | `modelName` hardcoded as `""` (L3). |
| 20 | DiagramController.java | PASS | Session creation for anonymous users is correct. Rate limiting + enabled check present. |
| 21 | ChatSessionMapper.xml | WARN | MySQL-specific LIMIT (L1), null userId handling missing (M1). |
| 22 | ChatMessageMapper.xml | WARN | MySQL-specific LIMIT (L1). |

---

## Recommendations by Priority

### Must-fix before merge
1. **C1** -- ThreadLocal in reactive operators. Restructure ChatHistoryAdvisor.adviseStream() to capture values in local finals before the Flux chain.
2. **H1** -- Tool-call history preservation. Either implement serialization/deserialization of tool calls in `toContextMessage/buildContextMessages`, or document as known limitation for v0.

### Should-fix before merge
3. **H2** -- Wire `sessionService.updateStats()` into the message persistence flow.
4. **H3** -- Decide on user-message persistence strategy (accept orphaned user messages or defer save to after LLM call completes).
5. **M1** -- Fix null userId handling in `selectByUserAndAgent` XML.

### Fix in next iteration
6. **M3** -- Move ownership check to service layer, use proper `AccessDeniedException`.
7. **M4** -- Add unique constraint on `(session_id, msg_index)` and concurrency handling.
8. **M5** -- Convert inner classes to Lombok `@Data`.
9. **M6** -- Implement tool call capture in streaming chunk aggregation.
10. **L1-L5** -- Style and minor correctness fixes.

---

## Architectural Observation

The `ChatMemoryStore` abstraction duplicates much of `ChatMessageService`/`ChatSessionService` without adding value. `ChatMemoryStoreImpl` is a thin pass-through (with `touchSession()` being empty). Consider removing `ChatMemoryStore` and having `ChatHistoryAdvisor` inject `ChatSessionService` / `ChatMessageService` directly, reducing indirection.
