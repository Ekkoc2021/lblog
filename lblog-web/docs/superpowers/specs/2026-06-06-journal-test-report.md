# 日记本后端接口测试文档

## 测试概览

- **测试框架**: JUnit 5 + Mockito + MockitoExtension
- **被测类**: `JournalController`
- **测试文件**: `src/test/java/com/yang/lblogserver/journal/controller/JournalControllerTest.java`
- **测试数量**: **37 个**
- **测试结果**: **37/37 全部通过**

## 端点覆盖

### 一、GET /api/v1/journals/calendar — 日历视图（7 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| C1 | 空月：无日记的月份 | 返回空列表 |
| C2 | 有日记的月份 | 返回日期+emoji 列表 |
| C3 | year=0 边界值 | 正常传递到 service |
| C4 | month=13 越界 | 正常传递到 service |
| C5 | month=0 边界 | 正常传递到 service |
| C6 | 用户A看不到用户B的日历 | service 仅收到用户A的 userId |
| C7 | 跨年月份（2025年12月） | 正常传递到 service |

### 二、GET /api/v1/journals — 时间线列表（4 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| T1 | 空时间线 | 返回空列表 |
| T2 | 有数据列表 | 返回日记列表（含 emoji） |
| T3 | page=1, pageSize=1 最小分页 | 正常传递 |
| T4 | 用户数据隔离 | service 仅收到当前用户 userId |

### 三、GET /api/v1/journals/by-date — 按日期查询（3 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| D1 | 某天有日记 | code=0, data 非 null |
| D2 | 某天无日记 | code=0, data=null |
| D3 | 跨用户查询 | service 仅收到当前用户 userId |

### 四、POST /api/v1/journals — 新建日记（10 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| P1 | 新建日记（该天首次） | code=0，返回完整数据 |
| P2 | 同一天覆盖（upsert） | code=0，返回更新后数据 |
| P3 | 仅填必填字段 journalDate | code=0，可选字段为空 |
| P4 | 缺少 journalDate | 框架层 @NotNull 拦截 |
| P5 | title 恰好 200 字符 | 通过 |
| P6 | title 超 200 字符 | 框架层 @Size 拦截 |
| P7 | mood 超 50 字符 | 框架层 @Size 拦截 |
| P8 | moodEmoji 超 10 字符 | 框架层 @Size 拦截 |
| P9 | weather 超 20 字符 | 框架层 @Size 拦截 |
| P10 | 长文本 content (10000 chars) | 正常通过 |

### 五、PUT /api/v1/journals/{id} — 更新日记（5 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| U1 | 正常更新 | code=0 |
| U2 | 更新不存在的记录 | service 抛 404 |
| U3 | 跨用户更新 | service 抛 404 |
| U4 | 更新已软删除记录 | service 抛 404 |
| U5 | 空 body 更新 | 所有字段 null，正常处理 |

### 六、DELETE /api/v1/journals/{id} — 删除日记（4 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| L1 | 正常软删除 | code=0 |
| L2 | 删除不存在的记录 | service 抛 404 |
| L3 | 跨用户删除 | service 抛 404 |
| L4 | 重复删除 | 第一次成功，第二次 404 |

### 七、认证与授权（4 个）

| # | 场景 | 预期结果 |
|---|------|---------|
| A1 | 未认证 | 抛出 401 |
| A2 | AUTHOR 角色 | 正常返回 |
| A3 | ADMIN 角色 | 正常返回 |
| A4 | 非 LoginUser principal | 抛出 401 |

## 场景覆盖矩阵

```
                    │ 正常 │ 边界值 │ 权限隔离 │ 不存在 │ 已删除 │ 重复 │ 字段校验 │ upsert
────────────────────┼──────┼───────┼─────────┼───────┼───────┼─────┼─────────┼───────
 GET /calendar      │  ✅  │  ✅   │    ✅   │   ✅  │   -   │  -  │    -    │   -
 GET /journals      │  ✅  │  ✅   │    ✅   │   ✅  │   -   │  -  │    -    │   -
 GET /by-date       │  ✅  │  -    │    ✅   │   ✅  │   -   │  -  │    -    │   -
 POST /journals     │  ✅  │  ✅   │    -    │   -   │   -   │  -  │    ✅   │   ✅
 PUT /journals/{id} │  ✅  │  -    │    ✅   │   ✅  │   ✅  │  -  │    -    │   -
 DELETE             │  ✅  │  -    │    ✅   │   ✅  │   -   │  ✅  │    -    │   -
 认证/授权          │  ✅  │  -    │    ✅   │   ✅  │   -   │  -  │    -    │   -
```

## 执行方式

在 IDEA 中打开 `JournalControllerTest`，点击类名旁的绿色运行按钮，或通过 MCP：

```
execute_run_configuration(
  filePath: "src/test/java/com/yang/lblogserver/journal/controller/JournalControllerTest.java",
  line: 1
)
```
