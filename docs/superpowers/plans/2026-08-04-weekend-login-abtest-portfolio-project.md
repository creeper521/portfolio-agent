# 周末登录奖励 ABTest 作品集项目实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将周末登录奖励 ABTest 作为一个 `PRIMARY` 核心 Project、三个关联 Case 和完整 Claim/Evidence/检索闭环，经人工审核后发布到作品集运行时 Bundle。

**Architecture:** 保持数据驱动前后端不变，通过 schema 4.0 增量候选把脱敏公开 patch 合并到当前 `2026-07-29.1` Bundle，形成 `2026-08-04.1` 候选。候选依次经过结构校验、隐私扫描、检索基准、review pack、精确 payload 人工 Approval、发布和运行时验证；页面继续使用现有 Project/Case DTO 自动渲染。

**Tech Stack:** PowerShell 5.1、JSON schema 4.0、Java 21、Spring Boot、Maven、Vue 3、TypeScript、Vitest、Playwright、BGE 本地 Embedding。

## Global Constraints

- 设计权威：`docs/superpowers/specs/2026-08-04-weekend-login-abtest-portfolio-project-design.md`。
- 新内容版本固定为 `2026-08-04.1`；schema 保持 `4.0`。
- Project 固定为 `DELIVERED / PRIMARY / JAVA_BACKEND / WORKSTREAM / PRIMARY`。
- 三个 Case 固定为 `DELIVERED / PRIMARY / FEATURE`。
- Project 使用 `P-07`，Case 使用 `CASE-53` 至 `CASE-55`，避开尚未实施的 Tool Docker 计划已预留的 `P-06 / CASE-50~52`。
- Tool Docker 计划后续实施时必须基于本版本重新定版，不得再从 `2026-07-29.1` 发布同名 `2026-08-04.1`。
- 不公开公司或产品名称、内部任务号、活动序列、实验 Key、数据库表名、类名、包名、提交哈希、地区目录、环境路径、原始代码、SQL、日志或截图。
- 不声明未公开的样本量、留存提升、收入提升或其他业务指标。
- “设计/核对/准备回滚方案”不得改写为已发生线上事故或已执行线上回滚。
- 运行时只能读取审核后的 `backend/src/main/resources/public-data/bundle/`，不得读取私有 Obsidian 或业务仓库。
- 私有治理操作只允许通过 `scripts/portfolio-governance.ps1`，不得直接修改治理工作区状态。
- Approval 必须在 validate、benchmark 和 review pack 之后由用户针对精确 payload hash 明确确认，禁止自动批准。
- 现有工作树包含其他任务的未提交改动；不得 reset、restore、暂存、提交或覆盖它们。
- 未获得显式 Git 授权前，不执行 `git add` 或 `git commit`；每个任务以测试与差异检查作为检查点。

---

## 文件结构

### 新增文件

- `governance/portfolio-governance/candidates/weekend-login-abtest-public-patch.json`：1 Project、3 Case、1 TimelineEvent、4 Claim、4 Evidence、4 Link、3 QuestionPreset 的唯一公开内容增量。
- `governance/portfolio-governance/candidates/weekend-login-abtest-public-routes.json`：4 个私有资产到 Project/Case/Evidence 的公开路由。
- `governance/portfolio-governance/benchmark/weekend-login-abtest-benchmarks.v1.json`：现有运行时基准加 ABTest supported/alias/boundary 覆盖。
- `scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1`：增量内容、引用、隐私和确定性回归。

### 修改文件

- `governance/portfolio-governance/scripts/portfolio-governance.ps1`：增加受限的 schema 4.0 增量候选分支和新版本基准路由。
- `governance/portfolio-governance/schemas/asset-publication-decision-ledger.schema.json`：允许 `AB-01` 至 `AB-04`，同时保持旧 68 项精确性。
- `scripts/portfolio-governance.prepare-candidate.test.ps1`：证明旧 Wave 1/2 行为未被新分支改变。
- `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicPortfolioSchemaFourContractTest.java`：锁定版本、数量、Project 和三个 Case 关联。
- `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`：锁定公开 DTO。
- `backend/src/test/resources/retrieval-benchmark/cases-runtime-baseline.json`：增加 ABTest 检索与活动工程主线负向区分。
- `frontend/src/pages/ProjectsPage.test.ts`、`ProjectPage.test.ts`、`CasePage.test.ts`：锁定数据驱动展示。
- `backend/src/main/resources/public-data/bundle/*`：仅用已批准发布产物整体替换七文件 Bundle。
- `docs/00-文档状态索引.md`、`docs/08-当前实现状态.md`、`docs/09-作品集资产库状态.md`、`docs/11-项目演进日志.md`：同步状态、数量与事实边界。

---

### Task 1: 固化 ABTest 公开内容增量

**Files:**
- Create: `governance/portfolio-governance/candidates/weekend-login-abtest-public-patch.json`
- Create: `governance/portfolio-governance/candidates/weekend-login-abtest-public-routes.json`
- Create: `scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1`

**Interfaces:**
- Consumes: 当前 schema 4.0 Bundle `2026-07-29.1` 与已确认设计文档。
- Produces: 目标版本 `2026-08-04.1` 的确定性 patch 和四项公开路由。

- [ ] **Step 1: 写缺文件时失败的静态契约测试**

测试必须断言 patch 与 routes 文件存在，并锁定：

```powershell
$patch.schemaVersion -eq '3.0'
$patch.baseContentVersion -eq '2026-07-29.1'
$patch.targetContentVersion -eq '2026-08-04.1'
@($patch.projects).Count -eq 1
@($patch.cases).Count -eq 3
@($patch.timelineEvents).Count -eq 1
@($patch.claims).Count -eq 4
@($patch.evidence).Count -eq 4
@($patch.links).Count -eq 4
@($patch.presets).Count -eq 3
```

- [ ] **Step 2: 运行测试确认 RED**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1
```

Expected: FAIL，消息为 `ABTest patch is required.`。

- [ ] **Step 3: 创建完整 patch**

顶层结构：

```json
{
  "schemaVersion": "3.0",
  "baseContentVersion": "2026-07-29.1",
  "targetContentVersion": "2026-08-04.1",
  "projects": [],
  "cases": [],
  "timelineEvents": [],
  "claims": [],
  "evidence": [],
  "links": [],
  "presets": [],
  "projectUpdates": [],
  "caseUpdates": []
}
```

Project 身份固定为：

```json
{
  "id": "weekend-login-abtest-project",
  "code": "P-07",
  "slug": "weekend-login-abtest",
  "title": "周末登录奖励 ABTest 完整闭环",
  "status": "DELIVERED",
  "contributionType": "PRIMARY",
  "careerTrack": "JAVA_BACKEND",
  "projectNature": "WORKSTREAM",
  "displayTier": "PRIMARY",
  "featuredCaseIds": [
    "case-abtest-experiment-design",
    "case-abtest-service-sql",
    "case-abtest-validation-risk-control"
  ]
}
```

Project 文案逐字使用设计文档第 3 节，`handoff` 固定为“已形成两期活动代码与配置演进记录；公开层只保留脱敏工程摘要，不公开内部实现与业务指标。”。

三个 Case 固定为：

```text
CASE-53 / case-abtest-experiment-design / 实验设计与稳定分流
CASE-54 / case-abtest-service-sql / 服务端能力与配置 SQL
CASE-55 / case-abtest-validation-risk-control / 验证、观测与风险控制
```

每个 Case 的 `projectId` 为 `weekend-login-abtest-project`，`collectionIds=[]`，并包含 problem、actions、decisions、verification、outcome、limitations。

四条 Claim 固定为：

```text
claim-abtest-project-delivered
claim-abtest-experiment-design
claim-abtest-service-sql
claim-abtest-validation-risk-control
```

前三条使用 `EVIDENCE_SUPPORTED / VERIFIED`；风险控制 Claim 使用 `EVIDENCE_SUPPORTED / PARTIALLY_VERIFIED`，detail 明确“支持测试、核对和预案设计，不声明发生过线上事故或执行过线上回滚”。

四条 Evidence 固定为：

```text
evidence-abtest-delivery-history / CODE / sourceCount=8
evidence-abtest-experiment-design-notes / DOCUMENT / sourceCount=3
evidence-abtest-service-sql-evolution / COLLECTION / sourceCount=8
evidence-abtest-validation-risk-notes / COLLECTION / sourceCount=4
```

全部 Evidence 为 `APPROVED`、`rawContentPublic=false`。四条 Link 均为 `DIRECT / APPROVED`，每条 Claim 恰好关联一条 Evidence。

Timeline 固定为 `timeline-weekend-login-abtest-delivery`，日期 `2026.07–08`，关联 Project、三个 Case、四条 Claim 和四组 Evidence。

三个 QuestionPreset 固定为：

```text
question-abtest-overview
question-abtest-stratification-bucketing
question-abtest-stable-assignment-and-rollback
```

问题分别覆盖项目闭环、分层与分桶、稳定归组与验证回滚，不包含内部活动名。

- [ ] **Step 4: 创建公开路由文件**

```json
{
  "schemaVersion": "3.0",
  "targetContentVersion": "2026-08-04.1",
  "publishRoutes": [
    {"assetId":"AB-01","finalRoute":"PROJECT","projectSlugs":["weekend-login-abtest"],"caseSlugs":[],"evidenceIds":["evidence-abtest-delivery-history"]},
    {"assetId":"AB-02","finalRoute":"CASE","projectSlugs":["weekend-login-abtest"],"caseSlugs":["abtest-experiment-design"],"evidenceIds":["evidence-abtest-experiment-design-notes"]},
    {"assetId":"AB-03","finalRoute":"CASE","projectSlugs":["weekend-login-abtest"],"caseSlugs":["abtest-service-sql"],"evidenceIds":["evidence-abtest-service-sql-evolution"]},
    {"assetId":"AB-04","finalRoute":"CASE","projectSlugs":["weekend-login-abtest"],"caseSlugs":["abtest-validation-risk-control"],"evidenceIds":["evidence-abtest-validation-risk-notes"]}
  ]
}
```

- [ ] **Step 5: 运行静态契约测试确认 GREEN**

Expected: PASS，并输出 `ABTest public patch contract passed.`。

- [ ] **Step 6: 差异检查点**

```powershell
git diff --check -- governance/portfolio-governance/candidates scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1
```

未经授权不暂存或提交。

---

### Task 2: 增加受限的 schema 4.0 增量候选通道

**Files:**
- Modify: `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify: `governance/portfolio-governance/schemas/asset-publication-decision-ledger.schema.json`
- Modify: `scripts/portfolio-governance.prepare-candidate.test.ps1`
- Modify: `scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1`

**Interfaces:**
- Consumes: Task 1 patch、routes、当前七文件 Bundle和包含 `AB-01~04` 的 72 项私有资产清单。
- Produces: canonical `portfolio.json`、`presentation.json` 和 decision ledger。

- [ ] **Step 1: 添加 prepare-candidate 失败测试**

测试构造旧 68 项加 `AB-01~04` 的临时资产清单并调用 `prepare-candidate`。新增资产固定为 `PRIMARY / VERIFIED / PUBLIC_REVIEW_REQUIRED`，一个 `MAINLINE` 和三个 `TASK`。

断言候选数量：

```text
projects=6, cases=52, collections=3, claims=83,
evidence=63, links=83, timeline=12, presets=19
```

- [ ] **Step 2: 运行测试确认 RED**

Expected: 当前实现因 patch coverage 或版本分支不支持而失败。

- [ ] **Step 3: 扩展 ledger schema 且保持精确集合**

允许 asset ID：

```json
"pattern": "^(L-(0[1-7])|T-(0[1-9]|1[0-9])|A-(0[1-9]|1[0-9]|2[0-5])|K-(0[1-9]|1[0-7])|AB-0[1-4])$"
```

旧 Wave 分支仍精确要求原 68 项；ABTest 分支精确要求原 68 项加 `AB-01~04`。不得放宽为任意 68—72 项。

- [ ] **Step 4: 实现窄范围增量合并**

入口必须同时满足 patch schema `3.0`、base `2026-07-29.1`、target `2026-08-04.1`。固定断言增量数量 `1/3/1/4/4/4/3`，校验 ID 与 code 不冲突、双向引用完整、每条 Claim 恰有一个 DIRECT Link、Project/Case/Claim 状态与贡献口径一致。

- [ ] **Step 5: 运行新旧 prepare 测试**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.prepare-candidate.test.ps1
```

Expected: 两者 PASS；旧 Wave 数量与 hash 行为不变。

---

### Task 3: 冻结检索基准并生成 review pack

**Files:**
- Create: `governance/portfolio-governance/benchmark/weekend-login-abtest-benchmarks.v1.json`
- Modify: `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify: `scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1`
- Private write only through governance command: 外部治理工作区。

**Interfaces:**
- Consumes: Task 2 候选编译器与四项经内容所有者确认的私有资产登记。
- Produces: validate/benchmark 均 PASS 的 review pack，不产生 Approval。

- [ ] **Step 1: 写基准路由失败测试**

断言 `4.0|2026-08-04.1` 选择 `weekend-login-abtest-benchmarks.v1.json`。

- [ ] **Step 2: 创建冻结基准**

复制当前运行时基准并增加：

- 三个正式 preset 的 supported 与 alias 用例；
- “为什么先分层再分桶”“为什么标签必须持久化”“怎样区分分配和曝光”“如何停止与回滚”的自由问题；
- “活动系统工程实践”必须继续命中旧 Project；
- “周末登录奖励 ABTest”必须命中新 Project；
- 未公开业务提升数字的问题必须保持边界回答，不能生成指标。

- [ ] **Step 3: 增加版本映射并运行测试**

```powershell
'4.0|2026-08-04.1' { 'weekend-login-abtest-benchmarks.v1.json'; break }
```

- [ ] **Step 4: 配置外部治理工作区**

在外部 Obsidian staging 中创建 `asset-library-2026-08-04-abtest.json`：完整复制既有 68 项资产，追加 `AB-01~04`，并把 `counts` 更新为 72。随后只在当前 PowerShell 进程设置变量，不输出变量值：

```powershell
$documentsRoot = [Environment]::GetFolderPath('MyDocuments')
$privateGovernanceRoot = Join-Path $documentsRoot `
    '杂项\实习学习-Obsidian\agent_docs_staging\portfolio-governance'
$env:PORTFOLIO_GOVERNANCE_HOME = $privateGovernanceRoot
$env:PORTFOLIO_ASSET_INVENTORY = Join-Path $privateGovernanceRoot `
    'candidates\asset-library-2026-08-04-abtest.json'
$env:PORTFOLIO_RELEASE_ROOT = Join-Path $privateGovernanceRoot `
    'abtest-public-releases'
```

资产清单新增 `AB-01~04`，只记录私有来源与审核状态，不复制原始代码或笔记到仓库。

- [ ] **Step 5: 按顺序运行 inspect、prepare、retrieval build、validate、benchmark**

通过 `scripts/portfolio-governance.ps1` 运行，不直接编辑治理状态。全部命令必须 PASS 且无 BLOCKER/ERROR。

- [ ] **Step 6: 构建 review pack 并暂停**

记录 reviewRunId、privacyReviewId、benchmarkRunId 和 candidate payload hash。向用户展示脱敏文案、实体数量、限制与精确 hash，等待明确批准；不得自动 approve。

---

### Task 4: 人工批准、发布并替换随包 Bundle

**Files:**
- Generated/replace: `backend/src/main/resources/public-data/bundle/checksums.json`
- Generated/replace: `backend/src/main/resources/public-data/bundle/keyword-index.json`
- Generated/replace: `backend/src/main/resources/public-data/bundle/manifest.json`
- Generated/replace: `backend/src/main/resources/public-data/bundle/portfolio.json`
- Generated/replace: `backend/src/main/resources/public-data/bundle/presentation.json`
- Generated/replace: `backend/src/main/resources/public-data/bundle/rag-documents.jsonl`
- Generated/replace: `backend/src/main/resources/public-data/bundle/vector-index.bin`

**Interfaces:**
- Consumes: 用户针对 Task 3 精确 hash 的明确批准。
- Produces: 已验证、可回滚的 `2026-08-04.1` 七文件 Bundle。

- [ ] **Step 1: 调用显式 Approval**

从 review pack 读取真实 run IDs，调用 `approve -Confirm`；不得复用旧 Approval 或手工构造 hash。

- [ ] **Step 2: dry-run 发布，再确认发布**

先运行无 `-Confirm` 的 publish 并断言 `dryRun=true`，再用本次 approvalId 确认发布。

- [ ] **Step 3: verify 发布版本**

Expected: version、Approval、ledger、payload、索引与 checksums 全部一致。

- [ ] **Step 4: 替换仓库 Bundle**

源目录必须恰好包含七个允许文件。复制到临时目录并运行静态 Bundle 校验，通过后逐文件替换仓库 Bundle；不得复制 Approval、ledger、review pack 或私有路径信息。

- [ ] **Step 5: 检查数量与隐私**

```text
projects=6, cases=52, collections=3, claims=83,
evidence=63, links=83, timeline=12, presets=19,
rag documents=83
```

运行 `privacy-check.ps1` 和 `verify-release.ps1`，Expected: PASS。

---

### Task 5: 锁定后端与检索契约

**Files:**
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicPortfolioSchemaFourContractTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`
- Modify: `backend/src/test/resources/retrieval-benchmark/cases-runtime-baseline.json`

**Interfaces:**
- Consumes: Task 4 Bundle。
- Produces: Project/Case/Preset/检索边界回归保护。

- [ ] **Step 1: 更新 schema 契约测试**

```java
assertThat(snapshot.getContentVersion()).isEqualTo("2026-08-04.1");
assertThat(snapshot.getProjects()).extracting(ProjectProfile::getId)
        .contains("weekend-login-abtest-project");
assertProject(projects.get("weekend-login-abtest-project"),
        CareerTrack.JAVA_BACKEND, ProjectNature.WORKSTREAM,
        ProjectDisplayTier.PRIMARY, 3);
assertThat(cases.values()).filteredOn(item ->
        "weekend-login-abtest-project".equals(item.getProjectId()))
        .extracting(CaseStudy::getCode)
        .containsExactly("CASE-53", "CASE-54", "CASE-55");
```

- [ ] **Step 2: 锁定公开 API**

断言聚合接口返回新版本、6 个 Project、19 个 preset；新 Project 为 `PRIMARY`，关联三个 Case。

- [ ] **Step 3: 增加运行时检索基准**

覆盖项目总览、分层分桶、日期范围、持久化、分配与曝光、测试矩阵和回滚边界；旧活动工程问题不得误命中新 Project。

- [ ] **Step 4: 运行后端定向测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PublicPortfolioSchemaFourContractTest,PortfolioControllerTest,RetrievalBenchmarkTest test
```

Expected: BUILD SUCCESS。

---

### Task 6: 锁定前端数据驱动展示

**Files:**
- Modify: `frontend/src/pages/ProjectsPage.test.ts`
- Modify: `frontend/src/pages/ProjectPage.test.ts`
- Modify: `frontend/src/pages/CasePage.test.ts`

**Interfaces:**
- Consumes: 现有 PublicProject/PublicCase DTO。
- Produces: 无 ABTest 前端特例的展示回归保护。

- [ ] **Step 1: 项目列表测试**

断言标题、Java 后端、工程主线、核心版本已交付、主导贡献、三个案例和 A/B Testing 技术标签。

- [ ] **Step 2: Project 详情测试**

断言主叙事、关键决策、验证结果、三个 Case 链接、证据入口和 Agent 入口。

- [ ] **Step 3: 三个 Case 详情测试**

分别断言 Case 标题、结果、限制、Project 反向链接和 Agent 交接；断言中不得出现内部标识。

- [ ] **Step 4: 运行定向测试**

```powershell
npm.cmd --prefix frontend test -- --run src/pages/ProjectsPage.test.ts src/pages/ProjectPage.test.ts src/pages/CasePage.test.ts
```

Expected: PASS；页面组件无需新增 ABTest 条件分支。

---

### Task 7: 同步文档并执行完整门禁

**Files:**
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/09-作品集资产库状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/superpowers/specs/2026-08-04-weekend-login-abtest-portfolio-project-design.md`

**Interfaces:**
- Consumes: Task 1—6 的最终事实和真实验证结果。
- Produces: 与运行时一致的状态文档和交接说明。

- [ ] **Step 1: 更新状态与资产数量**

记录 6 Project、52 Case、3 Collection、83 Claim、63 Evidence、83 Link、12 TimelineEvent、19 QuestionPreset 和 83 retrieval chunk。

- [ ] **Step 2: 更新事实边界**

明确 Project 为 `PRIMARY`；两期代码与配置演进有证据；测试、埋点和回滚公开内容按实际证据状态表达；未公开业务指标继续为空。

- [ ] **Step 3: 运行完整验证**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.weekend-login-abtest-candidate.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.prepare-candidate.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main/resources/public-data/bundle
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src/main/java
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml test
mvn.cmd -f backend/pom.xml package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall -SkipDockerCheck -BundleDirectory backend/src/main/resources/public-data/bundle -RetrievalCasesPath backend/src/test/resources/retrieval-benchmark/cases-runtime-baseline.json
```

Expected: 全部 exit 0，Maven BUILD SUCCESS，Vitest 全绿，Vite build 成功，隐私、架构、质量与 Bundle 检查 PASS。

- [ ] **Step 4: 最终工作树检查**

```powershell
git status --short
git diff --check
```

已有 observability、logging、local startup、Preset Contract 和 Tool Docker 设计/计划等用户改动保持原样。

- [ ] **Step 5: Git 门禁**

向用户汇报验证结果和未提交文件。只有用户明确要求后才暂存并使用中文提交信息；否则保持未暂存状态。
