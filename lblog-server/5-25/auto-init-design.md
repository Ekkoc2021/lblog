# 懒初始化 — 设计文档

> 状态：设计中 | 日期：2026-05-25

## 一、问题

当前所有初始化数据依赖手动执行 SQL 脚本（`v1.0.sql` 的初始化数据段落 + `v1.1.sql`）。部署步骤多、易遗漏。

## 二、设计思路

**不在启动时主动检查，而是在第一次查询时按需创建。** 

核心模式：

```
查询 → 结果为空 → 查默认值 → INSERT → 返回默认值
查询 → 有结果   → 直接返回
```

好处：
- 零启动开销，不阻塞应用启动
- 自然幂等——第二次查询已经有数据，跳过
- 默认值只定义在 Java 常量中，不散落在 SQL 脚本里
- 新增配置只需在常量类加一行，不需要写 SQL

## 三、三种懒初始化场景

### 3.1 site_config — 改造 `SiteConfigCacheService`

**触发时机**：任何代码调用 `getConfigValue(key)` 时。

**现状**：

```
cache hit? → 返回
cache miss → DB 查询 → 有值 → 放缓存返回
                     → null  → 返回 null
```

**改造后**：

```
cache hit? → 返回
cache miss → DB 查询 → 有值     → 放缓存返回
                     → null      → 查 DefaultConfig 是否有默认值
                                  → 有 → INSERT → 放缓存 → 返回默认值
                                  → 无 → 返回 null
```

`RankConfigService` 天然受益——它调 `getConfigValue`，config 不存在时自动落库。

### 3.2 角色 — 新包装 `RoleService`

**触发时机**：管理后台查询角色列表、分配角色时第一次查到空表。

在 `RolesMapper` 上加一层薄 Service：

```java
public Roles getByName(String name) {
    Roles role = rolesMapper.selectByName(name);
    if (role == null && isDefaultRole(name)) {
        ensureDefaults();  // 插 3 条默认角色
        role = rolesMapper.selectByName(name);
    }
    return role;
}

public List<Roles> getAll() {
    List<Roles> list = rolesMapper.selectAll();
    if (list.isEmpty()) {
        insertDefaults();
        list = rolesMapper.selectAll();
    }
    return list;
}
```

默认角色定义在常量中：

| name | label | sort |
|------|-------|------|
| admin | 管理员 | 0 |
| author | 作者 | 1 |
| user | 用户 | 2 |

### 3.3 管理员用户 — 改造登录流程

**触发时机**：首次使用 `ekko` 登录时，`UsersMapper.findByUsername("ekko")` 返回 null。

在 `AuthController.login()` 或 `AuthService` 中，`findByUsername` 返回 null 之后、返回"用户名或密码错误"之前，加一步检查：

```
findByUsername("ekko") → null
  ↓
检查是否总用户数为 0（首次启动）
  ↓ 是
创建管理员 (username=ekko, password={noop}admin123, ...)
分配 admin 角色
记录日志 "首次启动，已自动创建管理员 ekko"
继续正常登录流程
```

**安全检查**：
- 只在**用户总数为 0**时才自动创建（`SELECT COUNT(*) FROM users`）
- 用户数 > 0 时走正常"用户名或密码错误"逻辑
- 避免在已有用户的环境下误创建 ekko 账号

### 3.4 统一配置入口 — 修复直接 Mapper 调用

**问题**：以下 4 处绕过了 `SiteConfigCacheService`，直接调用 `siteConfigMapper.selectConfigValue()`。首次部署时 site_config 表为空，返回 null，导致行为异常：

| 文件 | 配置键 | null 时的行为 | 后果 |
|------|--------|-------------|------|
| `AuthController.java:149` | `registration_enabled` | `!"true".equals(null)` = true | 巧合正确 |
| `AdminImageController.java:46` | `image_cleanup_days` | 代码 fallback 30 | 有兜底 OK |
| `DrawController.java:55` | `ai_draw_chat_enabled` | `"true".equalsIgnoreCase(null)` = **false** | **AI 绘图被禁用** |
| `ChatHistoryAdvisor.java:279` | `reasoning_inject` | `"true".equals(null)` = **false** | **推理注入被禁用** |

**修复**：全部改为调用 `siteConfigCacheService.getConfigValue()`，这样懒初始化自动生效，且享受缓存加速。

改造示例（AuthController）：

```java
// 改前
String regEnabled = siteConfigMapper.selectConfigValue("registration_enabled");

// 改后
String regEnabled = siteConfigCacheService.getConfigValue("registration_enabled");
```

其他 3 处同理。`AdminImageController` 已有 `siteConfigMapper` 注入，需改为注入 `SiteConfigCacheService`。

## 四、改动清单

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `common/init/DefaultConfig.java` | 新建 | 所有默认值的唯一定义处 |
| 2 | `site/service/SiteConfigCacheService.java` | 修改 | getConfigValue 加懒初始化逻辑 |
| 3 | `site/mapper/SiteConfigMapper.java` | 新增方法 | `insertConfig`（INSERT IGNORE） |
| 4 | `auth/service/RoleService.java` | 新建 | 包装 RolesMapper，getByName/getAll 懒初始化 |
| 5 | `auth/mapper/RolesMapper.java` | 新增方法 | `insert` |
| 6 | `auth/service/impl/AuthServiceImpl.java`（或登录逻辑所在处）| 修改 | 首次 ekko 登录时自动创建管理员 |
| 7 | `auth/controller/AuthController.java` | 修改 | L149 `selectConfigValue` → `getConfigValue` |
| 8 | `image/controller/admin/AdminImageController.java` | 修改 | L46 `selectConfigValue` → `getConfigValue`，注入改为 CacheService |
| 9 | `ai/agent/draw/controller/DrawController.java` | 修改 | L55 `selectConfigValue` → `getConfigValue` |
| 10 | `ai/memory/advisor/ChatHistoryAdvisor.java` | 修改 | L279 `selectConfigValue` → `getConfigValue` |
| 11 | `v1.0.sql` | 修改 | 删除末尾初始化数据段落（L504-L520） |
| 8 | `v1.1.sql` | 删除 | 排序配置由 DefaultConfig 接管 |

## 五、DefaultConfig.java 设计

```java
package com.yang.lblogserver.common.init;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultConfig {
    private DefaultConfig() {}

    /** 所有 site_config 默认键值对 */
    private static final Map<String, String> ALL = new LinkedHashMap<>();
    static {
        // 基础配置
        ALL.put("registration_enabled", "true");
        ALL.put("site_title", "My Blog");
        ALL.put("ai_draw_chat_enabled", "true");
        ALL.put("image_cleanup_days", "0");
        ALL.put("reasoning_inject", "true");
        // 推荐排序
        ALL.put("rank.recommend.weight.like", "2.0");
        ALL.put("rank.recommend.weight.comment", "3.0");
        ALL.put("rank.recommend.weight.view", "0.05");
        ALL.put("rank.recommend.decay.base", "2");
        ALL.put("rank.recommend.decay.exponent", "1.2");
        // 最热排序
        ALL.put("rank.hot.weight.view", "0.1");
        ALL.put("rank.hot.weight.like", "1.0");
        ALL.put("rank.hot.weight.comment", "2.0");
        ALL.put("rank.hot.decay.base", "1");
        ALL.put("rank.hot.decay.exponent", "1.5");
    }

    /** 查某个 key 的默认值，没有则返回 null */
    public static String getDefault(String key) {
        return ALL.get(key);
    }
}
```

> 新增配置只需在 `ALL` 里加一行 `put`，不需要改任何其他文件。

## 六、触发链路图

```
首次部署，空数据库：

1. 用户打开首页
   └─ HomeController → SiteConfigCacheService.getConfigValue("site_title")
      └─ DB 无 → DefaultConfig 有 → INSERT "site_title"="My Blog" → 返回 "My Blog"

2. 管理员打开后台
   └─ CategoryManage → 查角色列表
      └─ RoleService.getAll() → roles 表空 → INSERT 3 条角色 → 返回列表

3. 管理员登录 ekko / admin123
   └─ AuthService.login("ekko", "admin123")
      └─ findByUsername → null → SELECT COUNT(*) users = 0
         → INSERT ekko + 分配 admin 角色 → 继续登录流程 → 登录成功
```

## 七、幂等性保障

每次操作都有天然的幂等检查：

| 数据类型 | 检查方式 | 第二次触发的行为 |
|----------|---------|----------------|
| site_config | `selectConfigValue(key) != null` | 跳过 |
| 角色 | `selectAll()` 非空 / `selectByName()` 非 null | 跳过 |
| 管理员 | `findByUsername("ekko") != null` 或 用户总数 > 0 | 跳过 |

> site_config 的 `insertConfig` 额外使用 `INSERT IGNORE`，防止极端并发场景下的 duplicate key。

## 八、优点

1. **零启动开销**：不阻塞应用启动，不跑任何 SQL 直到真正需要
2. **自然幂等**：第一次查询触发创建，之后的查询直接命中已有数据
3. **单一定义点**：所有默认值在 `DefaultConfig.ALL` 中，增删改一处搞定
4. **代码即文档**：看 `DefaultConfig` 就知道系统有哪些配置项和默认值
5. **无侵入**：`SiteConfigCacheService` 外部接口不变，调用方无感知

## 九、注意事项

1. **管理员创建的安全边界**：只在整个 `users` 表为空时才自动创建 ekko。如果用户手动删了 ekko 但表里还有其他用户，再次登录 ekko 不会自动重建——走正常"用户名不存在"逻辑
2. **`DefaultConfig.getDefault()` 返回 null**：如果 key 不在 ALL 中，返回 null，`SiteConfigCacheService` 的调用方自行处理（如 `RankConfigService` 已有默认值兜底）
3. **角色 INSERT 需要 `useGeneratedKeys`**：插入后需要拿到自增 ID，用于后续 `user_roles` 关联

## 十、实施检查清单

- [ ] `DefaultConfig.java` — 集中管理所有 15 个默认配置键值对
- [ ] `SiteConfigCacheService.getConfigValue()` — 加懒初始化逻辑
- [ ] `SiteConfigMapper.insertConfig()` — INSERT IGNORE
- [ ] `RoleService.java` — getByName + getAll 懒初始化
- [ ] `RolesMapper.insert()` — INSERT with generated keys
- [ ] 登录流程 — 首次 ekko 登录自动创建管理员
- [ ] `AuthController` L149 — `selectConfigValue` → `getConfigValue`
- [ ] `AdminImageController` L46 — `selectConfigValue` → `getConfigValue`
- [ ] `DrawController` L55 — `selectConfigValue` → `getConfigValue`
- [ ] `ChatHistoryAdvisor` L279 — `selectConfigValue` → `getConfigValue`
- [ ] `v1.0.sql` — 删除末尾初始化数据段落
- [ ] 删除 `v1.1.sql`
- [ ] 后端编译通过
- [ ] 空库验证：首次启动 → 各触发点依次访问 → 数据自动落库，AI 绘图可用
- [ ] 幂等验证：二次启动 → 无重复数据，日志全部"已存在，跳过"
