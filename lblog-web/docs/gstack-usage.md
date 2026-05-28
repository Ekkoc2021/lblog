# Gstack 开发需求使用指南

## 标准流程

需求 → 规划 → 规格 → 实现 → 测试 → 审查 → 发布

| 步骤 | 阶段 | 技能 | 说明 |
|------|------|------|------|
| 1 | 头脑风暴 | `/office-hours` | 探索想法、明确用户意图、验证是否值得做 |
| 2 | 策略评审 | `/plan-ceo-review` | 确认范围、优先级、业务目标 |
| 3 | 架构评审 | `/plan-eng-review` | 锁定技术方案、数据结构、接口设计 |
| 4 | 编写规格 | `/spec` | 产出可执行的 issue/backlog，含验收条件 |
| 5 | 实现 | 写代码 | 配合 `/careful` 安全模式，或用 TDD 流程 |
| 6 | QA 测试 | `/qa` | 浏览器自动化走查、截图对比、响应式检查 |
| 7 | 代码审查 | `/review` | 检查 diff、验证实现是否符合规格 |
| 8 | 发布 | `/ship` 或 `/land-and-deploy` | 创建 PR → 合并 → 部署 → 验证 |

## 常用组合

| 场景 | 技能 | 说明 |
|------|------|------|
| 快速评审 | `/autoplan` | 一次性跑完 CEO+工程+设计评审 |
| 只测不改 | `/qa-only` | 只报告 bug，不修复 |
| 定位 bug | `/investigate` | 系统性排查问题根因 |
| 保存进度 | `/context-save` | 创建检查点，后续可恢复 |
| 恢复进度 | `/context-restore` | 恢复之前保存的上下文 |
| 安全模式 | `/careful` | 限制编辑范围，防止误操作 |

## 辅助技能

| 场景 | 技能 | 说明 |
|------|------|------|
| 设计评审 | `/plan-design-review` | 评审 UI/UX 设计方案 |
| 设计咨询 | `/design-consultation` | 设计系统、品牌、视觉方向咨询 |
| 视觉走查 | `/design-review` | 上线前视觉还原度检查 |
| 开发者体验 | `/plan-devex-review` | API/CLI/SDK 设计评审 |
| 性能基准 | `/benchmark` | 页面性能回归测试 |
| 安全审查 | `/cso` | OWASP 安全漏洞扫描 |
| 浏览器调试 | `/open-gstack-browser` | 打开可视化浏览器进行调试 |
| Cookie 导入 | `/setup-browser-cookies` | 导入浏览器 cookie 做认证测试 |
| 部署配置 | `/setup-deploy` | 配置项目的部署流程 |
| 发布文档 | `/document-release` | 发布后更新 CHANGELOG 和文档 |
| 生成文档 | `/document-generate` | 从零生成模块/功能文档 |
| 周回顾 | `/retro` | 回顾本周交付内容 |
| 升级 gstack | `/gstack-upgrade` | 升级 gstack 版本 |

## 实际使用建议

- **小需求**（修 bug、小改动）：跳过步骤 1-3，直接从规格或实现开始
- **中等需求**（新增功能）：走步骤 1-4，实现后走 6-8
- **大需求**（新模块、架构变更）：完整走 1-8，必要时穿插设计评审
