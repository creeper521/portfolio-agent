# 设计稿目录收敛设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

## 背景

仓库根目录同时存在 `design/`、`design-demos/` 与 `design-exploration/`，用途相近但入口分散。此次调整只整理设计资产的位置，不改变生产功能、页面行为或公开内容边界。

## 方案比较

1. **统一到 `design/` 并按用途保留子目录（采用）**：把 `design-demos/` 移为 `design/demos/`，把 `design-exploration/` 移为 `design/exploration/`。结构清晰，文件原有相对依赖不变。
2. **全部平铺到 `design/`**：顶层文件过多，命名碰撞和误删风险较高。
3. **只新增索引、不移动目录**：改动最少，但无法消除三个并列顶层目录，未满足本次目标。

## 目标结构

```text
design/
├─ demos/
├─ exploration/
├─ huashu-portfolio/
├─ portfolio-design-v2/
├─ v0设计/
└─ Portfolio-Dossier.html
```

## 调整规则

- 完整移动两个目录中的全部文件，包括 `design-exploration/` 内目前被 `.gitignore` 忽略的本地探索稿。
- 保留 `design-demos/` 内 HTML 与共享 CSS/JavaScript 的同级关系，避免改变页面内部引用。
- 将仓库内有效文档、计划、脚本和测试中的旧路径统一替换为新路径。
- 将 `.gitignore` 改为默认忽略 `design/exploration/` 的新探索产物，但显式保留已受版本控制的设计文件；这样目录迁移不会被提交为“仅删除旧目录”。
- 不修改 `.zcode/` 等被仓库忽略的本地工具历史记录；它们是历史会话产物，不是当前项目文档。
- 不触碰当前工作区内与本任务无关的改动或合并冲突。

## 验证

- 根目录不再存在 `design-demos/` 与 `design-exploration/`。
- 新目录文件数量与移动前一致：`demos/` 21 个，`exploration/` 10 个。
- 搜索受版本控制的项目文件，不再出现有效的旧目录引用。
- 检查共享资源引用和 `shoot.mjs` 的相对路径在移动后仍可解析。
- 查看 Git 状态，确保差异仅包含目录迁移、必要路径更新和本设计说明。
