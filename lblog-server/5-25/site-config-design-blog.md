# 一个只有两张表的配置中心——博客 site_config 设计笔记

每当你需要在代码里写 `if (isFeatureEnabled)` 的时候，背后都有一个配置系统。

大厂有配置中心：Apollo、Nacos、Consul，配个 SDK、连个控制台、灰度发布、版本回滚一应俱全。小项目怎么办？

本博客的做法极其简单：**一张 `site_config` 表，一个缓存服务，三层默认值兜底。**

## 数据库设计

```sql
CREATE TABLE site_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key  VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500) NOT NULL DEFAULT ''
);
```

就两个字段：键和值。值的类型靠约定——`"true"` 是布尔，`"2.0"` 是浮点数，`"30"` 是整数，`"My Blog"` 是字符串。没有 type 列，没有 description 列，没有 namespace。

为什么这么简陋？因为这个博客用不到 15 个配置项。过度设计在这里不是美德。

## 缓存层

每次 `SELECT` 查库太重，但配置又不是高频变化的数据。加了一层 Caffeine 进程内缓存，TTL 30 分钟：

```
getConfigValue("site_title")
    → 缓存命中？直接返回
    → 缓存未命中？查库 → 放缓存 → 返回
    → 库里也没有？查默认值 → INSERT 入库 → 放缓存 → 返回默认值
```

最后的"库里也没有"这一步是**懒初始化**——项目首次部署时数据库是空的，所有 site_config 都是第一次被访问时才自动写入。不需要手动执行初始化脚本，不需要在 application.yml 里再写一遍默认值。

对调用方来说，`getConfigValue("site_title")` 永远有返回值。站点的标题不会因为运维忘了导 SQL 而变成 null。

## 类型化的访问层

`site_config` 里存的全是字符串，但业务代码要的是布尔、整数、浮点数。如果每个调用方都自己 `parseInt` + `try-catch`，代码会很快变味。

解决方式：**不让调用方碰字符串解析**。

以排序算法权重为例，业务代码拿到的不是一个 `Map<String, String>`，而是一个类型安全的 POJO：

```
RankConfig:
  weightLike    = 2.0    (double)
  weightComment = 3.0    (double)
  weightView    = 0.05   (double)
  decayBase     = 2      (int)
  decayExponent = 1.2    (double)
```

解析逻辑集中在一处，解析失败就降级到硬编码默认值。调用方不需要知道 `"2.0"` 怎么变成 `double`，也不需要关心这条配置到底是从数据库读出来的还是初始化写入的。

**这是分层的好处：site_config 表是"怎么存"，缓存服务是"怎么取"，RankConfigService 是"怎么理解"。每层只做一件事。**

## 运行时修改

后台管理页面可以直接改任意配置，改完调用 `updateConfigValue(key, value)`：

```
后台点击"保存"
  → SiteConfigCacheService.updateConfigValue("site_title", "新标题")
      → UPDATE site_config SET config_value = '新标题'
      → cache.invalidate("site_title")
  → 下次请求 getConfigValue → 缓存未命中 → 查库 → 返回新值
```

改动即时生效，不需要重启，不需要重新部署。30 分钟是缓存过期时间，如果等不及可以手动清缓存。

## 三层默认值兜底

一套配置在生产环境活得好不好，取决于它能不能在各种异常情况下"优雅地站着，而不是直接躺倒"。这个设计提供了三层防护：

| 层 | 位置 | 触发条件 | 例子 |
|-----|------|---------|------|
| 数据库 | `site_config` 表 | 正常 | `rank.recommend.decay.exponent = 1.2` |
| 初始化 | `DefaultConfig.java` 常量 | 首次部署，表里没有 | 自动 INSERT `"1.2"` 入库 |
| 硬编码 | `RankConfigService` 代码 | 数据库挂了、值乱码 | `getDouble(key, 1.2)` 直接返回 1.2 |

最极端的情况——数据库不可用——排序仍然正常工作，只是用默认参数。不会因为配置读不到而报 500。

## 优点

**1. 零依赖**

没有 Apollo SDK，没有 Nacos 控制台，没有 Consul 集群。一张 MySQL 表能跑就够。对于单人维护的博客，这是压倒性的优势。

**2. 运行时可改**

改配置不需要 git commit → CI → deploy 的链路。后台点一下，30 分钟缓存过期后全站生效。

**3. 代码即文档**

所有的 key、默认值、类型一眼看完。想知道系统有哪些可配置项？打开 `DefaultConfig.java`。

**4. 类型安全**

业务代码不直接消费字符串。每个配置域有一个 POJO 封装解析和默认值。排序算法拿到的是 `double`，不是 `"1.2"`。

**5. 部署友好**

首次部署不需要执行任何初始化 SQL。应用启动后第一次访问站点，配置自动落库。

**6. 可审计**

改配置的操作可以通过接口日志追溯。谁在什么时间把注册开关关了，一目了然。

## 缺点

**1. 没有 Schema 约束**

`config_value` 是 `VARCHAR(500)`，理论上你可以把 `registration_enabled` 设成 `"maybe"`。类型和合法值完全靠代码里的解析逻辑兜底。如果要严格约束，要么加 enum 列，要么在 Admin API 层做校验。但以 15 个配置项的规模来说，不值得。

**2. 没有历史版本**

`UPDATE` 直接覆盖旧值，没有 audit log。如果改了 `decay.exponent = 1.2 → 5.0 → 1.2`，中间切过 5.0 这件事没有任何记录。大项目可能需要 `site_config_history` 表，但个人博客最多几十次修改，不值得。

**3. 没法做灰度**

你想让 10% 用户看到新排序参数、90% 看到旧的？做不到。所有用户共享同一个 `config_value`。但这个缺陷在单人博客场景里根本不是问题——灰度发给谁看呢，自己吗。

**4. 配置项多了会失控**

15 个 key 可以手动管理，150 个就会想加 namespace、分组、描述字段。到 1500 个就真需要 Apollo 了。**每个工具都有它的问题规模上限，知道上限在哪里比什么都重要。**

## 适用场景

| 场景 | 适合？ |
|------|--------|
| 个人博客、小项目（<50 配置项） | 极其适合 |
| 中型项目（50-200 配置项） | 可用，建议加 schema 校验 |
| 多环境、灰度发布 | 不够，上 Apollo/Nacos |
| 多人协作、需要审批流 | 不够，上配置中心 |
| 配置变更频繁且需秒级生效 | 加主动刷新或缩短 TTL |

## 总结

这个 `site_config` 设计解决了一个具体的问题：**在最小复杂度下，让配置运行时可变、部署时零人工、代码中类型安全。**

它不是配置中心，不是特性开关系统，也不是最佳实践。它只是对一个简单问题的简单回答。而这种"问题的规模和解决方案的规模匹配"的感觉，在过分设计的年代反而显得难得。
