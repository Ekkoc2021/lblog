# LBlog 版本发布流程

## 分支策略

```
master  → 发版分支（只接受 dev 合并，不直接 commit）
dev     → 日常开发分支
feature/* → 功能分支（从 dev 切出，合回 dev）
```

## 发版流程

### 1. 准备（在 dev 上）

```bash
git checkout dev
git pull origin dev --rebase
```

### 2. 更新版本号（在 dev 上）

修改以下两个文件，去掉 `-SNAPSHOT` 后缀：

| 文件 | 示例 |
|------|------|
| `lblog-server/pom.xml` | `<version>0.0.1-SNAPSHOT</version>` → `<version>1.0.0</version>` |
| `lblog-web/package.json` | `"version": "0.0.0"` → `"version": "1.0.0"` |

### 3. 提交版本号改动

```bash
git add lblog-server/pom.xml lblog-web/package.json
git commit -m "chore: 发版 v1.0.0"
```

### 4. 合并到 master 并打 tag

```bash
git checkout master
git merge dev                        # master 只接受合并，不直接 commit
git tag -a v1.0.0 -m "v1.0.0: <简要描述>"   # annotated tag，记录署名和时间
```

### 5. 恢复开发版本号（在 dev 上）

```bash
git checkout dev
# 将版本号设为下一个 SNAPSHOT 版本
# pom.xml:     1.0.0 → 1.0.1-SNAPSHOT
# package.json: 1.0.0 → 1.0.1-SNAPSHOT
git add lblog-server/pom.xml lblog-web/package.json
git commit -m "chore: 下个版本 1.0.1-SNAPSHOT"
```

### 6. 推送

```bash
git push origin dev
git push origin master
git push origin v1.0.0               # tag 需要显式推送
```

## 版本号规则（SemVer）

```
v<MAJOR>.<MINOR>.<PATCH>

MAJOR  — 不兼容的大改动（架构重写、API 破坏性变更）
MINOR  — 向后兼容的新功能
PATCH  — 向后兼容的 bug 修复
```

**个人博客可适当宽松**：
- 新功能模块 → MINOR（如 `1.0.0` → `1.1.0`）
- bug 修复、体验优化 → PATCH（如 `1.1.0` → `1.1.1`）
- 大规模 rewrite → MAJOR（如 `1.x` → `2.0.0`）

## Tag 规范

- 命名：`v<版本号>`，如 `v1.0.0`
- 类型：**annotated tag**（`git tag -a`），不要用 lightweight tag
- 内容：一句话说明此次发布要点

```bash
git tag -a v1.0.0 -m "v1.0.0: 首版发布 — 博客前后台 + AI 绘图 + RBAC 权限"
```

## 发布检查清单

- [ ] 所有功能已在 dev 测试通过
- [ ] 前端 build 无报错（`npm run build`）
- [ ] 后端编译通过（IDEA Build）
- [ ] 数据库迁移脚本就绪（如有新增表/字段）
- [ ] 版本号已去 `-SNAPSHOT`
- [ ] annotated tag 已打
- [ ] dev 版本号已恢复为下一版 SNAPSHOT
