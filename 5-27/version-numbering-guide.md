# 版本号命名规范

## 1. 语义化版本 (Semantic Versioning, SemVer)

目前业界最主流的版本规范是 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/)，格式为：

```
MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
```

| 段 | 含义 | 递增时机 |
|---|---|---|
| **MAJOR** (主版本) | 不兼容的 API 变更 | 公共 API 以不向后兼容的方式改变时 |
| **MINOR** (次版本) | 向后兼容的新功能 | 新增了向后兼容的功能；或将任意功能标记为弃用时 |
| **PATCH** (修订版本) | 向后兼容的 bug 修复 | 只做了向后兼容的缺陷修复时 |
| **PRERELEASE** | 预发布标识 | alpha、beta、rc、SNAPSHOT 等 |

### 示例

```
1.0.0          → 首个稳定版
1.0.1          → 修了 1.0.0 的一个 bug（PATCH +1）
1.1.0          → 加了一个向后兼容的新功能（MINOR +1，PATCH 归零）
2.0.0          → 破坏性变更（MAJOR +1，MINOR/PATCH 归零）
1.0.0-alpha.1  → 第一个 alpha 预发布
1.0.0-rc.1     → 第一个候选发布版
```

### 判定优先级

当出现多个有意义的改动时，取最高优先级的版本号变化，低段位归零：

```
优先级: MAJOR > MINOR > PATCH

举例: 既修复了 bug，又加了新功能，又有破坏性变更
→ 只递增 MAJOR: 1.0.0 → 2.0.0
```

---

## 2. Maven SNAPSHOT 机制

### 2.1 概念

Maven 的快照版本是一种**持续开发中的版本标识**，表示为 `x.y.z-SNAPSHOT`。它不是一个固定的发布点，而是一个"当前最新开发状态"的占位符。

### 2.2 行为差异

| | RELEASE (1.0.0) | SNAPSHOT (1.0.1-SNAPSHOT) |
|---|---|---|
| **仓库策略** | 发布到 release 仓库，不可覆盖 | 发布到 snapshot 仓库，可重复覆盖 |
| **缓存策略** | 本地缓存后不再更新 | 定期检查远程是否有新版本（默认每天一次） |
| **含义** | 一个确定的、不可变的版本 | 一个"移动靶"，代表最新开发进展 |
| **依赖解析** | `1.0.0` 始终解析为同一个 jar | `1.0.1-SNAPSHOT` 解析为当时最新的构建 |
| **时间戳** | 无 | jar 包名会自动追加时间戳，如 `1.0.1-20260527.143000-1.jar` |

### 2.3 命名约定

快照版本号应指向**下一个计划的发布版本**：

```
当前发布版: 1.0.0
下一个版本:
  - 如果计划修 bug   → 1.0.1-SNAPSHOT
  - 如果计划加功能   → 1.1.0-SNAPSHOT
  - 如果确定要做大改 → 2.0.0-SNAPSHOT
```

**原则：快照版本 = 下一个目标的 RELEASE 版本 + `-SNAPSHOT`。**

当开发完成正式发布时，去掉 `-SNAPSHOT` 后缀即为发布版本：

```
1.0.1-SNAPSHOT  →  (开发 & 测试)  →  1.0.1 (发布)
```

---

## 3. 常见预发布后缀一览

| 后缀 | 含义 | 典型用途 |
|---|---|---|
| `-SNAPSHOT` | Maven 快照 | 日常开发中的中间构建 |
| `-alpha.N` | 内部测试版 | 功能未完整，仅供团队内部验证 |
| `-beta.N` | 公开测试版 | 功能基本完整，对外部测试者开放 |
| `-rc.N` | 候选发布版 | 如无重大问题，此版即为正式版 |
| `-milestone.N` / `-M.N` | 里程碑版 | Spring 生态常见，比 snapshot 稳定但非最终版 |
| `-preview` / `-pre` | 预览版 | 技术与市场预览 |
| `-SNAPSHOT` | Maven 专属快照 | 等同于"当前开发分支最新构建" |

### 生命周期示意

```
1.0.0-SNAPSHOT (日常开发)
  → 1.0.0-alpha.1 (功能冻结，内部测试)
  → 1.0.0-beta.1  (外部测试)
  → 1.0.0-rc.1    (候选发布)
  → 1.0.0-rc.2    (修复 rc.1 发现的问题)
  → 1.0.0         (正式发布)
  → 1.0.1-SNAPSHOT (下一个小版本开发开始)
```

---

## 4. 何时递增哪个版本段

判断标准来自 SemVer 的定义，总结为一个决策表：

| 场景 | 版本变化 | 示例 |
|---|---|---|
| 修了一个 bug，对外 API 无变化 | PATCH +1 | 1.0.0 → 1.0.1 |
| 加了新接口、新功能，旧接口正常工作 | MINOR +1, PATCH 归零 | 1.0.1 → 1.1.0 |
| 改了已有接口的签名/返回值/行为 | MAJOR +1, 其余归零 | 1.1.0 → 2.0.0 |
| 弃用了一个 API 但没删 | MINOR +1 | 1.0.0 → 1.1.0 |
| 重构内部实现，API 和行为完全一致 | PATCH +1 | — |
| 安全漏洞修复 | PATCH +1 | — |
| 新增了一个有默认值的配置项 | MINOR +1 | — |
| 删除了一个公共方法 | MAJOR +1 | — |

### 关键区分："公共 API"

只有**对使用者可见的**才计入版本判断：
- REST API 的路径、参数、返回值
- 对外暴露的 Java 公共类/方法签名
- 配置文件 key 名、数据库表结构
- SDK / CLII 的命令行参数

**内部重构、私有方法改动 → 不影响版本号判断（归为 PATCH）。**

---

## 5. 常见反模式

| 反模式 | 为什么不对 |
|---|---|
| `1.0.0.1` (四段式) | 混淆了构建号与语义版本，除非是特定平台约定 |
| 不加 SNAPSHOT 直接覆盖 | 本地缓存不更新，团队成员拿到的是旧 jar |
| SNAPSHOT 版本已发布但不改名 | 发布到 release 仓库后被缓存，后续改不动 |
| 跳过版本号 | `1.0.0` 直接跳到 `3.0.0`，让人误以为有两次大重构 |
| 日期版本替代语义版本 | `26.5.27` 看不出兼容性信息 |
| MAJOR = 0 时不敢升 | `0.x.y` 阶段的规则和 `1.0.0+` 一样，0 不代表可以随便破坏兼容性 |

---

## 6. 本项目建议

当前项目 `lblog-server` 是一个后端 API 服务：

- **版本号记录在** `pom.xml` 的 `<version>` 标签
- **开发期间**：使用 SNAPSHOT，如 `1.0.1-SNAPSHOT`
- **发布时**：去掉 `-SNAPSHOT`，打 tag，部署
- **判断标准**：以 REST API 兼容性为准
  - 只增新接口，不改旧的 → MINOR
  - 改了已有接口的字段/路径 → MAJOR
  - 纯 bug 修复、性能优化 → PATCH

### 日常操作

```bash
# 开发期
<version>1.0.1-SNAPSHOT</version>

# 发布时
<version>1.0.1</version>
git tag v1.0.1

# 发布后切回开发
<version>1.0.2-SNAPSHOT</version>
```

---

## 参考

- [Semantic Versioning 2.0.0](https://semver.org/)
- [Maven: Guide to SNAPSHOT versions](https://maven.apache.org/guides/getting-started/index.html#what-is-a-snapshot-version)
- [Spring Boot Versioning](https://github.com/spring-projects/spring-boot#versioning)
