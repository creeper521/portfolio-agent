# Tool 服务 Docker 化运维改造核心项目发布实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Tool 服务 Docker 化运维改造作为一个 `COLLABORATIVE` 核心 Project、三个关联技术 Case 和完整 Claim/Evidence/检索闭环，经过人工审核后发布到作品集运行时 Bundle。

**Architecture:** 保持现有数据驱动前后端不变，通过一个仅支持 schema 4.0 增量内容的治理候选通道，将静态脱敏 patch 合并到当前 `2026-07-29.1` Bundle，形成 `2026-08-04.1` 候选。候选依次通过结构、隐私、Claim-Evidence、检索基准、人工 Approval、发布和运行时验证；前端继续通过现有 Project/Case DTO 自动渲染。

**Tech Stack:** PowerShell 5.1、JSON schema 4.0、Java 21、Spring Boot、Maven、Vue 3、TypeScript、Vitest、Playwright、Docker Compose、BGE 本地 Embedding。

## Global Constraints

- 设计权威：`docs/superpowers/specs/2026-08-04-tool-docker-core-project-publication-design.md`。
- 新内容版本固定为 `2026-08-04.1`；schema 保持 `4.0`。
- Project 固定为 `DELIVERED / COLLABORATIVE / JAVA_BACKEND / TOOL / PRIMARY`。
- 三个 Case 固定为 `IMPLEMENTED_TESTED / COLLABORATIVE / FEATURE`。
- 不公开仓库路径、内部主机、部署目录、环境代号、凭据、原始代码、原始日志或内部截图。
- 不声明完整生产验收、自动回滚、生产级鉴权、应用 readiness 或量化收益。
- 运行时只能读取审核后的 `backend/src/main/resources/public-data/bundle/`，不得读取私有 Obsidian 知识库。
- 私有治理操作只允许通过 `scripts/portfolio-governance.ps1`，不得直接修改治理工作区状态。
- `PORTFOLIO_GOVERNANCE_HOME` 与私有资产清单必须位于 Git worktree 外；命令和日志不得输出其绝对路径。
- Approval 必须在 validate、benchmark 和 review pack 之后由用户针对精确 payload hash 明确确认，禁止自动批准。
- 现有工作树包含其他任务的未提交改动；不得 reset、restore、暂存、提交或覆盖它们。
- 未获得显式 Git 授权前，不执行 `git add` 或 `git commit`；每个任务以测试和差异审查代替提交检查点。
- Java 生产与测试代码禁止 `var`、`record` 和 Lombok。

---

## 文件结构

### 新增文件

- `governance/portfolio-governance/candidates/tool-docker-public-patch.json`：1 Project、3 Case、1 TimelineEvent、9 Claim、3 Evidence、9 Link、3 QuestionPreset 的唯一公开内容增量。
- `governance/portfolio-governance/candidates/tool-docker-public-routes.json`：4 个新私有资产到 Project/Case/Evidence 的公开路由。
- `governance/portfolio-governance/benchmark/tool-docker-benchmarks.v1.json`：现有 Wave 2 基准加 3 个新问题入口的 supported/alias/boundary 覆盖。
- `scripts/portfolio-governance.tool-docker-candidate.test.ps1`：schema 4.0 增量候选编译、引用、隐私和确定性回归。

### 修改文件

- `governance/portfolio-governance/scripts/portfolio-governance.ps1`：增加窄范围 schema 4.0 增量候选分支与新版本基准路由。
- `governance/portfolio-governance/schemas/asset-publication-decision-ledger.schema.json`：允许 4 个 tool_docker 新资产进入 ledger，同时保持旧 68 项完整性。
- `scripts/portfolio-governance.prepare-candidate.test.ps1`：证明旧 Wave 1/2 候选行为没有被新分支改变。
- `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicPortfolioSchemaFourContractTest.java`：锁定新版本、实体数量、Project 属性和三个 Case 关联。
- `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`：锁定新 Project/Case/QuestionPreset 的公开 DTO。
- `backend/src/test/resources/retrieval-benchmark/cases-runtime-baseline.json`：增加 tool_docker 检索与学习手册负向区分用例。
- `frontend/src/pages/ProjectsPage.test.ts`：验证新核心 Project 在 Java 后端主线出现并显示协作贡献。
- `frontend/src/pages/ProjectPage.test.ts`：验证 Project 五段叙事、三条关联 Case 和 Agent/证据入口。
- `frontend/src/pages/CasePage.test.ts`：验证三个技术 Case 的详情和 Project 反向链接。
- `backend/src/main/resources/public-data/bundle/*`：仅用已批准发布产物整体替换七文件 Bundle。
- `docs/00-文档状态索引.md`、`docs/08-当前实现状态.md`、`docs/09-作品集资产库状态.md`、`docs/11-项目演进日志.md`：同步设计、数量、审核状态与演进边界。

---

### Task 1: 固化 tool_docker 公开内容增量

**Files:**
- Create: `governance/portfolio-governance/candidates/tool-docker-public-patch.json`
- Create: `governance/portfolio-governance/candidates/tool-docker-public-routes.json`
- Create: `scripts/portfolio-governance.tool-docker-candidate.test.ps1`

**Interfaces:**
- Consumes: 当前 schema 4.0 Bundle `2026-07-29.1`；设计文档第 5-9 节。
- Produces: 目标版本 `2026-08-04.1` 的确定性 patch 和 4 项公开路由，供 Task 2 候选编译器消费。

- [ ] **Step 1: 先写缺文件时失败的静态契约测试**

在新测试中建立以下硬断言：

```powershell
$patchPath = Join-Path $root 'governance\portfolio-governance\candidates\tool-docker-public-patch.json'
$routesPath = Join-Path $root 'governance\portfolio-governance\candidates\tool-docker-public-routes.json'
Assert-True (Test-Path -LiteralPath $patchPath -PathType Leaf) 'tool_docker patch is required.'
Assert-True (Test-Path -LiteralPath $routesPath -PathType Leaf) 'tool_docker routes are required.'

$patch = Get-Content -Raw -Encoding UTF8 $patchPath | ConvertFrom-Json
Assert-True ($patch.schemaVersion -eq '3.0') 'Incremental patch schema must be 3.0.'
Assert-True ($patch.baseContentVersion -eq '2026-07-29.1') 'Base version mismatch.'
Assert-True ($patch.targetContentVersion -eq '2026-08-04.1') 'Target version mismatch.'
Assert-True (@($patch.projects).Count -eq 1) 'Expected one Project.'
Assert-True (@($patch.cases).Count -eq 3) 'Expected three Cases.'
Assert-True (@($patch.claims).Count -eq 9) 'Expected nine Claims.'
Assert-True (@($patch.evidence).Count -eq 3) 'Expected three Evidence summaries.'
Assert-True (@($patch.links).Count -eq 9) 'Expected nine direct links.'
Assert-True (@($patch.presets).Count -eq 3) 'Expected three presets.'
```

继续断言 `tool-docker-transformation-project`、`CASE-50` 至 `CASE-52`、九个 Claim ID、三个 Evidence ID 和三个 QuestionPreset ID 精确匹配设计文档。

- [ ] **Step 2: 运行静态契约测试确认 RED**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.tool-docker-candidate.test.ps1
```

Expected: FAIL，消息为 `tool_docker patch is required.`。

- [ ] **Step 3: 创建完整 patch**

顶层结构固定为：

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

Project 使用以下身份字段，并将设计文档第 5.2 至 5.9 节文案逐字填入对应字段：

```json
{
  "id": "tool-docker-transformation-project",
  "code": "P-06",
  "slug": "tool-docker-transformation",
  "title": "Tool 服务 Docker 化运维改造",
  "status": "DELIVERED",
  "contributionType": "COLLABORATIVE",
  "careerTrack": "JAVA_BACKEND",
  "projectNature": "TOOL",
  "displayTier": "PRIMARY",
  "featuredCaseIds": [
    "case-tool-docker-runtime-routing",
    "case-tool-docker-sequential-restart",
    "case-tool-docker-virtual-time"
  ]
}
```

三个 Case 固定使用 `CASE-50`、`CASE-51`、`CASE-52`，`projectId` 均为 `tool-docker-transformation-project`，`collectionIds` 均为空数组。每个 Case 具备 problem、actions、decisions、verification、outcome、limitations，且不出现内部名称。

九条 Claim 使用设计第 7 节 ID。背景 Claim 为 `UNKNOWN`；职责 Claim 为 `DELIVERED`；六条实现/验证 Claim 为 `IMPLEMENTED_TESTED`；限制 Claim 为 `INVESTIGATED / SELF_DECLARED / PARTIALLY_VERIFIED`。所有 Claim 的 `contributionType` 为 `COLLABORATIVE`。

三条 Evidence 使用 `COLLECTION`、`CODE`、`COLLECTION`，`rawContentPublic=false`、`publicStatus=APPROVED`。九条 Link 均为 `DIRECT / APPROVED`；每条 Claim 恰好关联一条 Evidence。

- [ ] **Step 4: 创建公开路由文件**

```json
{
  "schemaVersion": "3.0",
  "targetContentVersion": "2026-08-04.1",
  "publishRoutes": [
    {"assetId":"TD-01","finalRoute":"PROJECT","projectSlugs":["tool-docker-transformation"],"caseSlugs":[],"evidenceIds":["evidence-tool-docker-cross-repository-review"]},
    {"assetId":"TD-02","finalRoute":"CASE","projectSlugs":["tool-docker-transformation"],"caseSlugs":["tool-docker-runtime-routing"],"evidenceIds":["evidence-tool-docker-tool-implementation-tests"]},
    {"assetId":"TD-03","finalRoute":"CASE","projectSlugs":["tool-docker-transformation"],"caseSlugs":["tool-docker-sequential-restart"],"evidenceIds":["evidence-tool-docker-tool-implementation-tests"]},
    {"assetId":"TD-04","finalRoute":"CASE","projectSlugs":["tool-docker-transformation"],"caseSlugs":["tool-docker-virtual-time"],"evidenceIds":["evidence-tool-docker-deployment-contract"]}
  ]
}
```

- [ ] **Step 5: 运行静态契约测试确认 GREEN**

Run the Task 1 command again。
Expected: PASS，并输出 `tool_docker public patch contract passed.`。

- [ ] **Step 6: 差异检查点**

```powershell
git diff --check -- governance/portfolio-governance/candidates scripts/portfolio-governance.tool-docker-candidate.test.ps1
```

Expected: exit 0，无空白错误。未经授权不暂存或提交。

---

### Task 2: 增加 schema 4.0 增量候选编译通道

**Files:**
- Modify: `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify: `governance/portfolio-governance/schemas/asset-publication-decision-ledger.schema.json`
- Modify: `scripts/portfolio-governance.prepare-candidate.test.ps1`
- Modify: `scripts/portfolio-governance.tool-docker-candidate.test.ps1`

**Interfaces:**
- Consumes: Task 1 patch、routes、当前七文件 Bundle、私有 72 项资产清单。
- Produces: `$PORTFOLIO_GOVERNANCE_HOME/prepared-candidates/2026-08-04.1/candidate/` 下的 canonical `portfolio.json`、`presentation.json` 和 decision ledger。

- [ ] **Step 1: 添加会调用 `prepare-candidate` 的失败测试**

测试创建 72 项临时资产清单：保留原 68 项，再加入 `TD-01` 至 `TD-04`。新增项固定为 `COLLABORATIVE`、`VERIFIED`、`PUBLIC_REVIEW_REQUIRED`，类型为一个 `MAINLINE` 和三个 `TASK`。调用：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
  -Command prepare-candidate -Workspace $workspace -RuntimeBundle $runtime `
  -PatchManifest $patchPath -RouteManifest $routesPath `
  -AssetInventory $inventoryPath -TargetVersion '2026-08-04.1'
```

断言输出数量为 Project 6、Case 52、Claim 88、Evidence 62、Link 88、Timeline 12、QuestionPreset 19，Collection 仍为 3。

- [ ] **Step 2: 运行测试确认 RED**

Run Task 1 command。
Expected: FAIL，当前实现返回旧 Wave 基线或 patch coverage 错误。

- [ ] **Step 3: 扩展 ledger schema**

将 `minItems` 保持 68、`maxItems` 调整为 72，并扩展 asset ID pattern：

```json
"pattern": "^(L-(0[1-7])|T-(0[1-9]|1[0-9])|A-(0[1-9]|1[0-9]|2[0-5])|K-(0[1-9]|1[0-7])|TD-0[1-4])$"
```

脚本 expected IDs 必须按候选分支选择：旧 Wave 仍精确要求原 68 项；新 schema 4.0 增量分支精确要求原 68 项加 `TD-01` 至 `TD-04`。不能降级为“任意 68-72 项”。

- [ ] **Step 4: 实现窄范围增量分支**

以 `patch.schemaVersion == '3.0'`、base `2026-07-29.1`、target `2026-08-04.1` 作为唯一入口。新增辅助函数：

```powershell
function Assert-Count([object[]]$Items, [int]$Expected, [string]$Name) {
    if (@($Items).Count -ne $Expected) {
        Write-Failure 'PREPARE_PATCH_COVERAGE_INVALID' "$Name count is invalid."
    }
}
```

新分支固定断言 `1/3/1/9/3/9/3` 的 Project/Case/Timeline/Claim/Evidence/Link/Preset 数量，使用 schema 4.0 完整 Project 字段和带 `collectionIds` 的 Case 字段，并校验：ID 不冲突、双向引用完整、每条 Claim 恰有一个 DIRECT Link、成果 Claim 为 `EVIDENCE_SUPPORTED / VERIFIED`、限制 Claim 不得为 VERIFIED。

- [ ] **Step 5: 运行新旧 prepare 测试**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.tool-docker-candidate.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.prepare-candidate.test.ps1
```

Expected: 两个脚本均 PASS；旧 Wave 1/2 数量与 hash 断言保持不变。

- [ ] **Step 6: 差异检查点**

运行 `git diff --check`，人工确认新分支没有放宽旧 schema、隐私或引用 Gate。未经授权不提交。

---

### Task 3: 冻结检索基准并生成可审核候选

**Files:**
- Create: `governance/portfolio-governance/benchmark/tool-docker-benchmarks.v1.json`
- Modify: `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify: `scripts/portfolio-governance.tool-docker-candidate.test.ps1`
- Private modify through governance workflow: `$PORTFOLIO_GOVERNANCE_HOME` 下的资产清单与候选目录。

**Interfaces:**
- Consumes: Task 2 候选编译器、4 个经内容所有者确认的私有资产登记。
- Produces: validate/benchmark 均 PASS 的 review pack；不产生 Approval。

- [ ] **Step 1: 写基准选择失败测试**

断言 `4.0|2026-08-04.1` 选择 `tool-docker-benchmarks.v1.json`；当前实现应以 `BENCHMARK_VERSION_UNSUPPORTED` 失败。

- [ ] **Step 2: 创建冻结基准**

复制 Wave 2 全部既有用例，为以下三个 preset 各增加 `SUPPORTED_QUESTION`、`ALIAS`、`BOUNDARY` 三个 `ERROR` 用例：

```text
question-tool-docker-overview
question-tool-docker-runtime-routing
question-tool-docker-restart-and-time
```

增加检索区分用例：工程改造问题必须包含 `tool-docker-transformation`；“容器化学习手册”问题仍匹配 `k-06-knowledge`，两者不得互换。

- [ ] **Step 3: 添加版本映射并运行测试**

```powershell
'4.0|2026-08-04.1' { 'tool-docker-benchmarks.v1.json'; break }
```

Run Task 2 的两个测试脚本。
Expected: PASS。

- [ ] **Step 4: 检查私有治理前置条件**

```powershell
if ([string]::IsNullOrWhiteSpace($env:PORTFOLIO_GOVERNANCE_HOME)) { throw 'PORTFOLIO_GOVERNANCE_HOME is required.' }
if ([string]::IsNullOrWhiteSpace($env:PORTFOLIO_ASSET_INVENTORY)) { throw 'PORTFOLIO_ASSET_INVENTORY is required.' }
```

Expected: 两个变量已在当前进程设置，且不打印值。当前环境尚未设置第一个变量，执行到本步时需要用户提供私有治理工作区授权。

- [ ] **Step 5: 准备候选**

```powershell
$prepareJson = powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
  -Command prepare-candidate -Workspace $env:PORTFOLIO_GOVERNANCE_HOME `
  -RuntimeBundle backend/src/main/resources/public-data/bundle `
  -PatchManifest governance/portfolio-governance/candidates/tool-docker-public-patch.json `
  -RouteManifest governance/portfolio-governance/candidates/tool-docker-public-routes.json `
  -AssetInventory $env:PORTFOLIO_ASSET_INVENTORY `
  -TargetVersion '2026-08-04.1' | Select-Object -Last 1 | ConvertFrom-Json
if ($prepareJson.status -ne 'PASS') { throw 'prepare-candidate failed.' }
```

- [ ] **Step 6: 构建 retrieval 候选并执行门禁**

```powershell
$candidate = Join-Path $env:PORTFOLIO_GOVERNANCE_HOME 'prepared-candidates\2026-08-04.1\candidate'
$ledger = Join-Path $env:PORTFOLIO_GOVERNANCE_HOME 'prepared-candidates\2026-08-04.1\asset-publication-decisions.json'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-retrieval-bundle.ps1 -CandidateDirectory $candidate
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 -Command validate -Workspace $env:PORTFOLIO_GOVERNANCE_HOME -Candidate $candidate -DecisionLedger $ledger
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 -Command benchmark -Workspace $env:PORTFOLIO_GOVERNANCE_HOME -Candidate $candidate -DecisionLedger $ledger
```

Expected: 三条命令均 PASS，无 BLOCKER/ERROR。

- [ ] **Step 7: 构建 review pack 并暂停**

运行 `build-review-pack`，记录输出的 `reviewRunId`、`privacyReviewId`、`benchmarkRunId` 和 candidate payload hash。向用户展示脱敏文案、数量、限制和 hash，等待明确批准；不得自动调用 `approve`。

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
- Produces: 已验证、可回滚的 `2026-08-04.1` 七文件运行时 Bundle。

- [ ] **Step 1: 调用显式 Approval**

从 review pack 读取真实 run IDs，不手工构造：

```powershell
$approval = powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
  -Command approve -Workspace $env:PORTFOLIO_GOVERNANCE_HOME `
  -Candidate $candidate -DecisionLedger $ledger `
  -ReviewRunId $reviewRunId -ApprovedBy $approvedBy `
  -PrivacyReviewId $privacyReviewId -BenchmarkRunId $benchmarkRunId `
  -Confirm | Select-Object -Last 1 | ConvertFrom-Json
if ($approval.status -ne 'PASS') { throw 'approval failed.' }
```

- [ ] **Step 2: dry-run 发布，再确认发布**

先不带 `-Confirm` 运行 `publish` 并断言 `dryRun=true`；再带 `-Confirm` 发布到 `$env:PORTFOLIO_RELEASE_ROOT`。发布绑定 `$approval.approvalId`，不能使用旧 Approval。

- [ ] **Step 3: verify 发布版本**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
  -Command verify -Workspace $env:PORTFOLIO_GOVERNANCE_HOME `
  -ReleaseRoot $env:PORTFOLIO_RELEASE_ROOT -TargetVersion '2026-08-04.1' `
  -DecisionLedger $ledger
```

Expected: PASS，版本、Approval、ledger、payload、索引和 checksums 一致。

- [ ] **Step 4: 原子替换仓库 Bundle**

验证源目录恰好包含七个允许文件；复制到临时目录并运行 `scripts/verify-static-bundle.ps1`。验证通过后逐文件替换仓库 Bundle。不得复制 Approval、ledger、review pack 或私有路径信息。

- [ ] **Step 5: 检查运行时数量与隐私**

```text
projects=6
cases=52
collections=3
claims=88
evidence=62
claimEvidenceLinks=88
timelineEvents=12
questionPresets=19
rag documents=88
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main/resources/public-data/bundle
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall -SkipDockerCheck -BundleDirectory backend/src/main/resources/public-data/bundle -RetrievalCasesPath backend/src/test/resources/retrieval-benchmark/cases-runtime-baseline.json
```

Expected: PASS。

---

### Task 5: 锁定后端公开契约与检索行为

**Files:**
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicPortfolioSchemaFourContractTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`
- Modify: `backend/src/test/resources/retrieval-benchmark/cases-runtime-baseline.json`

**Interfaces:**
- Consumes: Task 4 运行时 Bundle。
- Produces: 新 Project/Case/QuestionPreset/检索边界的后端回归保护。

- [ ] **Step 1: 更新契约测试并确认旧 Bundle 会失败**

```java
assertThat(snapshot.getContentVersion()).isEqualTo("2026-08-04.1");
assertThat(snapshot.getProjects()).extracting(ProjectProfile::getId)
        .contains("tool-docker-transformation-project");
assertProject(projects.get("tool-docker-transformation-project"),
        CareerTrack.JAVA_BACKEND, ProjectNature.TOOL,
        ProjectDisplayTier.PRIMARY, 3);
assertThat(cases.values()).filteredOn(item ->
        "tool-docker-transformation-project".equals(item.getProjectId()))
        .extracting(CaseStudy::getCode)
        .containsExactly("CASE-50", "CASE-51", "CASE-52");
```

Expected before Task 4 replacement: FAIL；after replacement: PASS。

- [ ] **Step 2: 锁定公开 API**

断言 `/api/v1/portfolio` 返回 `2026-08-04.1`、6 个 Project、19 个 QuestionPreset；按 slug 获取 `tool-docker-transformation` 时贡献为 `COLLABORATIVE`、关联 Case 为 3。

- [ ] **Step 3: 增加检索基准用例**

加入项目总览、双运行时、多环境防串、顺序重启、容器改时五组问题，以及“介绍容器化学习手册”的负向区分。工程问题 expected subject 为新 Project/Case；学习问题保持 `k-06-knowledge`。

- [ ] **Step 4: 运行后端定向测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PublicPortfolioSchemaFourContractTest,PortfolioControllerTest,RetrievalBenchmarkTest test
```

Expected: BUILD SUCCESS。

---

### Task 6: 锁定前端展示与交接

**Files:**
- Modify: `frontend/src/pages/ProjectsPage.test.ts`
- Modify: `frontend/src/pages/ProjectPage.test.ts`
- Modify: `frontend/src/pages/CasePage.test.ts`

**Interfaces:**
- Consumes: 现有 `PublicProject`、`PublicCaseSummary` 和数据驱动页面。
- Produces: 无前端特例的页面回归保护。

- [ ] **Step 1: 为 Project 列表写失败测试**

构造 `tool-docker-transformation` fixture，断言标题、`Java 后端`、`工具`、`核心版本已交付`、`协作参与`、`3 个案例`、`Docker Compose`。

- [ ] **Step 2: 为 Project 详情写失败测试**

构造含三条精选 Case 的 Project，断言五个正文段、`COLLABORATIVE` 状态标记、三条 `/cases/...` 链接、证据入口和 `/agent?project=tool-docker-transformation` 入口。

- [ ] **Step 3: 为三个 Case 写失败测试**

分别断言 runtime routing、sequential restart、virtual time 的标题、限制、Project 反向链接和 Agent 交接；断言中不出现内部环境名或路径。

- [ ] **Step 4: 运行前端定向测试**

```powershell
npm.cmd --prefix frontend test -- --run src/pages/ProjectsPage.test.ts src/pages/ProjectPage.test.ts src/pages/CasePage.test.ts
```

Expected: PASS。页面组件无需修改；如果测试迫使新增 tool_docker 条件分支，应回退并修正数据或 fixture。

---

### Task 7: 同步文档并执行完整发布门禁

**Files:**
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/09-作品集资产库状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/superpowers/specs/2026-08-04-tool-docker-core-project-publication-design.md`

**Interfaces:**
- Consumes: Task 1-6 最终事实和真实测试结果。
- Produces: 与运行时一致的状态说明和完成验证记录。

- [ ] **Step 1: 更新文档状态**

将设计状态更新为“已实施并验证”，登记实施计划；运行时数量更新为 6 Project、52 Case、3 Collection、88 Claim、62 Evidence、88 Link、12 TimelineEvent、19 QuestionPreset、88 retrieval chunk。

- [ ] **Step 2: 更新资产与演进边界**

明确新增内容来自 4 个经审核资产，Project 为 `COLLABORATIVE`；“容器化学习手册”继续是独立学习 Case。记录尚未宣称生产部署、自动回滚和量化收益。

- [ ] **Step 3: 运行完整验证**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.tool-docker-candidate.test.ps1
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

Expected: 所有命令 exit 0；Maven BUILD SUCCESS；Vitest 全绿；Vite build 成功；隐私、架构、质量与静态 Bundle 检查 PASS。

- [ ] **Step 4: 检查最终工作树**

```powershell
git status --short
git diff --check
```

确认只新增或修改本计划列出的任务文件；已有 observability、logging、local startup 等用户改动保持原样。

- [ ] **Step 5: Git 操作门禁**

向用户汇报验证结果和未提交文件。只有用户明确要求后，才按任务边界暂存并使用中文提交信息；否则保持未暂存状态。
