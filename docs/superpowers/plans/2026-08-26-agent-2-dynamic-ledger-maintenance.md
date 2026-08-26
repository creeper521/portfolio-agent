# Agent 2.0 动态缺陷与开发账本重构实施计划

<!-- DOCUMENT_STATUS: ACTIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `docs/15-Agent 2.0真实交互问题清单与修复边界.md` 重写为只维护当前开放事项、具备稳定水位线和可执行 Exit Gate 的唯一账本。

**Architecture:** 先修复 Windows PowerShell 5.1 下不可运行的 replay 文档门，再按批准设计完成 ID 裁决、AGENTS 章程同步和主账本替换。现有开放 A2 保留原 ID，新生产缺陷从 A2-118 起，非产品工作只进入 ARCH/GATE/DOC；历史正文只从 Git 与 docs/11 追溯。

**Tech Stack:** Markdown、PowerShell 5.1/7、Git、仓库 documentation/privacy/architecture checkers。

## Global Constraints

- 不修改生产 Java、TypeScript、公开合同、模型 Prompt、OperationBinding 或 Provider 配置。
- 不运行真实 Provider；本计划的 Provider Gate 为 `NOT_APPLICABLE`。
- 保持 `REPLAY_BODY_NOT_RETAINED`、`关键词/sentinel 检测只属于测试`、`Portfolio continuation handle 原样保留` 三个安全标记。
- 正式 ID 仅允许 `A2`、`ARCH`、`GATE`、`DOC`；评审阶段工作流前缀不得进入账本。
- 删除已关闭正文，不建立历史索引；显式水位线只增不减。
- docs/11 不记录测试数量、哈希、提交号或测试过程。
- 每次提交只承载一个可独立复核的文档责任，提交主题使用中文。

---

### Task 1: 修复 replay 文档门的 Windows PowerShell 5.1 编码

**Files:**
- Modify: `scripts/persistence-safe-replay-docs-check.ps1:1`
- Modify: `scripts/persistence-safe-replay-docs-check.test.ps1:1`

**Interfaces:**
- Consumes: `verify-release.ps1` 通过 `powershell.exe -NoProfile -File` 调用脚本。
- Produces: 两个带 UTF-8 BOM、在 Windows PowerShell 5.1 与 PowerShell 7 均可解析的脚本；检查语义与 token 不变。

- [ ] **Step 1: 记录修复前 canonical 失败证据**

Run:

```powershell
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\persistence-safe-replay-docs-check.test.ps1
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\persistence-safe-replay-docs-check.ps1
```

Expected: 两条命令均因无 BOM UTF-8 中文字面量产生 ParserError。

- [ ] **Step 2: 只给两个脚本添加 UTF-8 BOM**

文件首字节必须为：

```text
EF BB BF
```

除 BOM 外脚本内容不得变化。

- [ ] **Step 3: 验证 BOM 与 canonical 调用**

Run:

```powershell
$files = @('scripts\persistence-safe-replay-docs-check.ps1', 'scripts\persistence-safe-replay-docs-check.test.ps1')
foreach ($file in $files) {
  $bytes = [IO.File]::ReadAllBytes((Resolve-Path $file))
  if (($bytes[0..2] -join ',') -ne '239,187,191') { throw "missing UTF-8 BOM: $file" }
}
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\persistence-safe-replay-docs-check.test.ps1
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\persistence-safe-replay-docs-check.ps1
```

Expected: `PERSISTENCE_SAFE_REPLAY_DOCS_TESTS_OK tests=2` 与 `PERSISTENCE_SAFE_REPLAY_DOCS_OK files=5`。

- [ ] **Step 4: 提交编码修复**

```powershell
git add scripts/persistence-safe-replay-docs-check.ps1 scripts/persistence-safe-replay-docs-check.test.ps1
git commit -m "fix(gate): 修复回放文档门脚本编码"
```

### Task 2: 冻结 ID 裁决与账本章程

**Files:**
- Modify: `AGENTS.md:62-70`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Consumes: 批准设计 §2、§3、§7、§8；当前 docs/15 总览状态。
- Produces: 四命名空间章程、删除裁决记录和新账本可消费的 ID 集合。

- [ ] **Step 1: 更新 AGENTS.md 章程**

动态账本章节必须明确：

```text
docs/15 是开放生产缺陷及已批准 Agent 开发、验证、架构和文档治理工作的唯一账本。
A2 用于生产缺陷；ARCH/GATE/DOC 用于非产品工作。
每个命名空间维护显式、只增不减、永不复用的水位线。
P0-P3 表达影响；NOW/NEXT/LATER 表达唯一执行序。
每项包含现状证据、修复边界、依赖、专属测试/验证缺口和 Exit Gate。
关闭后删除总览与正文，重要行为在 docs/11 留摘要，不在 docs/15 建档案。
```

- [ ] **Step 2: 固定旧 A2 裁决**

本批明确删除且不进入新账本的 19 个已关闭 ID：

```text
A2-01—A2-14、A2-16—A2-18、A2-89、A2-90
```

保留所有其他当前可见开放 A2。新生产缺陷分配：

```text
A2-118 冷恢复瞬时失败会清除唯一 ResumeToken
A2-119 General Comparison 容量与 pair 位置绑定不闭合
A2-120 packaged Browser 模型切换门不可达
```

非产品水位及初始工作：

```text
ARCH-01—ARCH-10
GATE-01（replay 脚本 BOM；Task 1 完成后从开放正文删除，水位保留）
DOC-01—DOC-07
```

- [ ] **Step 3: 在 docs/11 写精简裁决记录**

记录必须包含：删除 ID 集合、关闭依据类别（生产修复与原风险门已有证据）、保留原 ID 原则、新增四类水位和新账本链接；不得写测试数量、哈希或提交元数据。

- [ ] **Step 4: 验证章程无旧 bug-only 冲突**

Run:

```powershell
rg -n "single ledger for open Agent 2.0 bugs|Bug IDs increase" AGENTS.md
```

Expected: 零命中；新四命名空间、水位线、执行序和 Exit Gate 字段均可检索。

### Task 3: 重写 docs/15 为开放事项唯一账本

**Files:**
- Modify: `docs/15-Agent 2.0真实交互问题清单与修复边界.md`

**Interfaces:**
- Consumes: Task 2 的 ID 集合与批准设计。
- Produces: `CURRENT_AUTHORITY` 的开放工作账本。

- [ ] **Step 1: 替换文档信息架构**

最终固定章节：

```text
1. 当前结论与证据边界
2. 维护、编号、水位和删除规则
3. 稳态不变量
4. NOW/NEXT/LATER 总览
5. 开放生产问题（A2）
6. 架构工作（ARCH）
7. 验证治理（GATE）
8. 文档治理（DOC）
9. 固定执行批次
10. 全局 Exit Gates
```

- [ ] **Step 2: 写入水位与安全标记**

```text
A2 已用至 A2-120
ARCH 已用至 ARCH-10
GATE 已用至 GATE-01
DOC 已用至 DOC-07
```

正文必须原样包含三个 replay 标记，并说明 Provider 派生正文 replay 为 `REPLAY_BODY_NOT_RETAINED`；关键词/sentinel 检测只属于测试；Portfolio continuation handle 原样保留。

- [ ] **Step 3: 精简继承 A2**

保留 A2-15、A2-20—A2-41、A2-43—A2-88、A2-91—A2-117；删除 Task 2 指定的 19 个已关闭 ID。可以按共同根因分组展示，但每个保留 ID 必须可检索，并拥有严重度、执行序、状态、现状证据、修复边界/依赖和专属验证/Exit Gate。

高优先级条目必须明确：

```text
A2-44：满数量+约束缺口在 Recommendation 构造阶段触发不变量异常，且前端隐藏 unsatisfiedConstraints。
A2-80/A2-87/A2-88/A2-117：Qwen Comparison 被 Goal Draft v1 阻断，Qwen/GLM 完整矩阵仍未关闭。
A2-100/A2-111：Release runner 在 FAILED/NOT_RUN 时可 exit 0，证据门不能据此宣称 PASS。
A2-118：网络/5xx恢复失败后 ensureSession 创建无 token 会话，watchEffect 清除唯一槽位。
A2-119：Goal 合法 subjects×dimensions 可超过 General 20 项上限；扁平数组按位置绑定 pair。
A2-120：PLAYWRIGHT_MODEL_SELECTION 未由 packaged runner 设置，且双模型前置可导致 spec skip。
```

- [ ] **Step 4: 写入 ARCH/DOC 开放项**

`ARCH-01—ARCH-10` 依次覆盖 Gateway ownership、OperationBinding 精确合同对、Runtime Readiness、诊断 pointer/closed reason、Lifecycle 拆分、Workspace 拆分、TurnExecutionStore settlement command、死代码/record 纪律、token/cost canary、Schema/Prompt/Codec wire-shape 单权威。

`DOC-01—DOC-07` 依次覆盖 docs/08 当前口径、docs/11 测试计数、docs/00 与 checker 活动文档一致性、SECURITY 旧 Provider 权威、机器状态新鲜度、token policy 命名、活动 specs/plans 的历史 A2 引用语义。

- [ ] **Step 5: 验证正文只含开放事项**

Run:

```powershell
rg -n "已关闭|tests / 0 failures|SHA-256|旧 JAR|2026-08-24 第" "docs/15-Agent 2.0真实交互问题清单与修复边界.md"
```

Expected: 零命中。

### Task 4: 清理当前权威与活动文档的死引用

**Files:**
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md`
- Modify: `docs/superpowers/specs/2026-08-20-general-answer-language-and-depth-prompt-design.md`
- Modify: `docs/superpowers/specs/2026-08-24-agent-model-selection-frontend-ui-design.md`
- Modify: `docs/superpowers/specs/2026-08-26-agent-2-dynamic-ledger-maintenance-design.md`

**Interfaces:**
- Consumes: Task 2 删除的 19 个 ID。
- Produces: 当前权威与活动文档不把已删除 ID 当成当前账本入口。

- [ ] **Step 1: 清理 docs/08**

删除旧测试数量和 Qwen v3 历史并列口径；对已关闭清理项只描述当前生产事实，不再引用不存在的 A2 行。

- [ ] **Step 2: 标注活动设计中的历史引用**

仅对实际命中的已删除 A2-02、A2-16 增加“历史缺陷 ID，已从动态账本移除”语义；A2-86 未删除，保持当前开放引用，不加历史标签。

- [ ] **Step 3: 执行全活动文档扫描**

Run:

```powershell
$deleted = 'A2-(?:0?[1-9]|1[0-4]|1[6-8]|89|90)(?![0-9])'
rg --pcre2 -n $deleted AGENTS.md SECURITY.md docs/00-文档状态索引.md docs/08-当前实现状态.md docs/16-Agent单权威持续收敛范式.md docs/agent-architecture-status.json docs/superpowers/specs docs/superpowers/plans
```

Expected: 命中只能位于明确标注为历史引用的活动设计/计划；当前权威状态文档零死引用。

### Task 5: 全量文档治理验证与提交

**Files:**
- Verify all files modified by Tasks 1—4.

**Interfaces:**
- Consumes: 新账本、同步章程、活动引用和 replay 门。
- Produces: 可复核的治理提交，不改变生产行为。

- [ ] **Step 1: 验证 ID 与必填字段**

```powershell
$ledgerPath = 'docs\15-Agent 2.0真实交互问题清单与修复边界.md'
$ledger = Get-Content -LiteralPath $ledgerPath -Raw
$ids = Select-String -Path $ledgerPath -Pattern '^\|\s*((?:A2|ARCH|GATE|DOC)-\d+)\s*\|' | ForEach-Object { $_.Matches[0].Groups[1].Value }
$duplicates = $ids | Group-Object | Where-Object Count -gt 1
if ($duplicates) { throw "duplicate ledger ids: $($duplicates.Name -join ',')" }
foreach ($marker in @('A2 已用至 A2-120', 'ARCH 已用至 ARCH-10', 'GATE 已用至 GATE-01', 'DOC 已用至 DOC-07')) {
  if (-not $ledger.Contains($marker)) { throw "missing watermark: $marker" }
}
foreach ($closed in @('A2-01', 'A2-16', 'A2-89')) {
  if ($ledger -match "(?<![0-9])$closed(?![0-9])") { throw "closed id remains: $closed" }
}
```

Expected: 无输出、退出码 0。

- [ ] **Step 2: 运行专项与治理门**

```powershell
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\persistence-safe-replay-docs-check.test.ps1
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\persistence-safe-replay-docs-check.ps1
.\scripts\documentation-check.ps1
.\scripts\agent-architecture-status.ps1
.\scripts\privacy-check.ps1 -Path backend\src\main
git diff --check
```

Expected: replay tests/checker、documentation、architecture、privacy 均成功；架构状态仍诚实报告 `overall=IN_PROGRESS`。

- [ ] **Step 3: 检查变更范围**

```powershell
git status --short
git diff --stat
git diff -- AGENTS.md docs/08-当前实现状态.md docs/11-项目演进日志.md "docs/15-Agent 2.0真实交互问题清单与修复边界.md" scripts/persistence-safe-replay-docs-check.ps1 scripts/persistence-safe-replay-docs-check.test.ps1
```

Expected: 只有本计划列出的治理文件变化，无生产代码、Prompt、合同或配置变化。

- [ ] **Step 4: 提交账本重构**

```powershell
git add -- AGENTS.md docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md "docs/15-Agent 2.0真实交互问题清单与修复边界.md" docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md docs/superpowers/specs/2026-08-20-general-answer-language-and-depth-prompt-design.md docs/superpowers/specs/2026-08-24-agent-model-selection-frontend-ui-design.md docs/superpowers/specs/2026-08-26-agent-2-dynamic-ledger-maintenance-design.md scripts/persistence-safe-replay-docs-check.ps1 scripts/persistence-safe-replay-docs-check.test.ps1
git commit -m "docs(agent): 重构动态缺陷与开发账本"
```
