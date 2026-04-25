-- ============================================================
-- LBlog 文章详情接口测试数据
-- 补全 post_contents 正文 + like_records 点赞记录
-- ============================================================

-- 1. 补全文章正文（替换占位符）
UPDATE `post_contents` SET `body` = '# Spring Boot 3.0 新特性全面解析

## 前言

2022 年 11 月，Spring Boot 3.0 正式发布，这是基于 Spring Framework 6 构建的第一个 GA 版本。作为 Spring 生态的一次重大升级，3.0 版本带来了多项突破性变化。本文将全面解析这些新特性，帮助你平滑迁移并充分利用新版本的优势。

## 一、Jakarta EE 9+ 迁移

Spring Boot 3.0 最显著的变化是从 **Java EE** 迁移到了 **Jakarta EE 9+**。这意味着所有 `javax.*` 包名都被替换为 `jakarta.*`。

```java
// 旧写法（Spring Boot 2.x）
import javax.persistence.Entity;
import javax.validation.constraints.NotBlank;

// 新写法（Spring Boot 3.x）
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
```

如果你的项目使用了 Hibernate、Tomcat 等传统 Java EE 组件，升级时需要同步更新依赖版本。大部分主流框架已经在 2022 年底完成了 Jakarta EE 9+ 的适配。

## 二、GraalVM 原生镜像

Spring Boot 3.0 正式支持 **GraalVM 原生镜像（Native Image）**，可以将 Spring 应用编译为独立的可执行文件，实现毫秒级启动和极低内存占用。

### 2.1 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 2.2 使用 AOT 编译

```bash
mvn -Pnative native:compile
```

编译后的原生镜像启动时间通常在 **0.1 秒以内**，内存占用仅为 JVM 模式的 1/5 到 1/10。对于 Serverless、容器化场景特别有利。

### 2.3 注意事项

- 原生镜像**不支持**所有 Java 特性（如反射、动态代理需要配置）
- Spring 提供了 `@NativeHint` 注解来辅助配置
- 第三方库需要兼容 GraalVM，部分库可能需要额外适配

## 三、Observability（可观测性）

Spring Boot 3.0 引入了全新的 **Observability API**，统一了 Micrometer、OpenTelemetry 等可观测性框架的集成方式。

### 3.1 Micrometer  Tracing

```java
@Bean
public ObservationHandler myHandler() {
    return new ObservationHandler() {
        @Override
        public void onStart(Observation.Context context) {
            log.info("Observation started: {}", context.getName());
        }
    };
}
```

### 3.2 自动配置

Spring Boot 3.0 提供了 `management.observations.*` 配置前缀，支持开箱即用的指标收集和链路追踪：

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

## 四、HttpExchange —— 声明式 HTTP 客户端

Spring 6 / Spring Boot 3.0 引入了 `@HttpExchange` 注解，可以用声明式方式定义 HTTP 客户端，类似 Feign 但无需额外依赖：

```java
@HttpExchange("/api/users")
public interface UserClient {

    @GetExchange("/{id}")
    User getUser(@PathVariable Long id);

    @PostExchange
    User createUser(@RequestBody User user);
}
```

## 五、迁移指南

从 Spring Boot 2.x 升级到 3.0 的关键步骤：

1. **升级 JDK**：最低要求 JDK 17，推荐 JDK 21
2. **替换 javax → jakarta**：全局替换所有 `javax.*` 导入
3. **更新第三方依赖**：确保所有依赖兼容 Spring Boot 3.x
4. **更新配置属性**：部分配置属性名有变化（如 `server.error.path` → `server.error.path`）
5. **测试原生镜像**：如有容器化需求，验证 GraalVM 兼容性

## 总结

Spring Boot 3.0 是一次里程碑式的更新。虽然不是所有项目都需要立即升级，但 Jakarta EE 迁移是长期趋势，越早准备越主动。GraalVM 原生镜像和可观测性 API 则为云原生架构提供了更好的基础设施支持。

如果项目正在 Spring Boot 2.x 上运行且升级成本较高，可以考虑渐进式迁移——先升级到 Spring Boot 2.7（3.0 的桥梁版本），再逐步过渡到 3.0。' WHERE `post_id` = 1;


-- 2. 补全 MySQL 索引优化文章正文
UPDATE `post_contents` SET `body` = '# MySQL 索引优化实战：从慢查询到毫秒级响应

## 背景

在生产环境中，慢查询是数据库性能问题的首要原因。本文通过三个真实案例，展示如何通过索引优化将查询时间从秒级降到毫秒级。

## 准备工作

先创建一张示例订单表，用于后续的优化演示：

```sql
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL COMMENT ''订单号'',
  `user_id` bigint NOT NULL COMMENT ''用户ID'',
  `status` tinyint NOT NULL COMMENT ''订单状态'',
  `amount` decimal(10,2) NOT NULL COMMENT ''订单金额'',
  `pay_time` datetime DEFAULT NULL COMMENT ''支付时间'',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

表中已插入 **100 万条**模拟数据。

## 案例一：全表扫描

### 慢查询

```sql
SELECT * FROM orders WHERE user_id = 12345;
```

执行耗时：**2.3 秒**

### 分析

使用 `EXPLAIN` 查看执行计划：

```text
id | select_type | table  | type | rows
1  | SIMPLE      | orders | ALL  | 998721
```

`type = ALL` 表示全表扫描，扫描了近 100 万行。

### 优化

添加索引：

```sql
ALTER TABLE orders ADD INDEX idx_user_id (user_id);
```

优化后耗时：**0.003 秒**（3 毫秒），性能提升 **766 倍**。

## 案例二：复合索引最左前缀原则

### 慢查询

```sql
SELECT * FROM orders
WHERE status = 1
ORDER BY created_at DESC
LIMIT 20;
```

现有索引：`idx_user_id(user_id)`，`idx_status(status)`

执行耗时：**0.8 秒**

### 分析

虽然 `status` 有索引，但 `ORDER BY created_at` 需要额外排序（Using filesort），在 status=1 的记录较多时性能下降。

### 优化

建立复合索引，同时覆盖筛选和排序：

```sql
ALTER TABLE orders ADD INDEX idx_status_created (status, created_at);
```

优化后耗时：**0.002 秒**，且 Extra 中消除了 Using filesort。

## 案例三：覆盖索引

### 慢查询

```sql
SELECT user_id, COUNT(*) AS order_count, SUM(amount) AS total_amount
FROM orders
WHERE created_at >= ''2026-01-01'' AND created_at < ''2026-04-01''
GROUP BY user_id;
```

执行耗时：**5.7 秒**

### 分析

需要扫描大量数据行并进行分组聚合，而普通索引需要回表查询完整行数据。

### 优化

建立覆盖索引，包含查询所需的所有列：

```sql
ALTER TABLE orders ADD INDEX idx_created_user_amount (created_at, user_id, amount);
```

此索引可以直接满足查询中的所有列，无需回表。执行计划中的 Extra 变为 `Using index`。

优化后耗时：**0.15 秒**（提升 38 倍）

## 索引设计基本原则

1. **区分度优先**：选择区分度高的列作为索引前缀（如 user_id 比 status 更适合放在复合索引前面）
2. **最左前缀**：复合索引遵循最左前缀原则，查询条件必须从索引最左列开始匹配
3. **覆盖索引**：尽量让索引包含查询所需的所有列，避免回表
4. **避免冗余**：`(a, b)` 复合索引已经涵盖了 `(a)` 单列索引，无需重复建立
5. **控制数量**：单表索引数量建议不超过 5 个，过多会影响写入性能

## 日常慢查询排查工具

| 工具 | 用途 |
|------|------|
| `EXPLAIN` | 分析查询执行计划 |
| `SHOW PROFILE` | 查看查询各阶段耗时 |
| `slow_query_log` | 记录慢查询日志 |
| `performance_schema` | 细粒度性能监控 |
| `pt-query-digest` | Percona Toolkit 查询分析工具 |

## 总结

索引优化是数据库调优中最立竿见影的手段。本次实战的三个案例表明：

- **添加合适的索引**可以将查询性能提升数百倍
- **复合索引**比单列索引更能应对复杂查询
- **覆盖索引**在统计分析类查询中表现优异
- 日常开发中养成用 `EXPLAIN` 分析 SQL 的习惯，可以有效避免慢查询上线' WHERE `post_id` = 10;


-- 3. 点赞记录表（如不存在则创建）
CREATE TABLE IF NOT EXISTS `like_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '文章ID',
  `visitor_id` varchar(64) NOT NULL COMMENT '浏览器指纹',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_visitor` (`post_id`, `visitor_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞记录表';


-- 4. 点赞记录（访客对文章的点赞数据）
INSERT INTO `like_records` (`post_id`, `visitor_id`, `created_at`) VALUES
-- 文章 1（Spring Boot 3.0）: 3 位访客点赞
(1, 'visitor-uuid-a1b2c3d4', '2026-04-24 10:00:00'),
(1, 'visitor-uuid-e5f6g7h8', '2026-04-24 11:30:00'),
(1, 'visitor-uuid-i9j0k1l2', '2026-04-24 14:15:00'),

-- 文章 10（MySQL 索引优化）: 2 位访客点赞
(10, 'visitor-uuid-a1b2c3d4', '2026-04-25 09:00:00'),
(10, 'visitor-uuid-m3n4o5p6', '2026-04-25 10:20:00');
