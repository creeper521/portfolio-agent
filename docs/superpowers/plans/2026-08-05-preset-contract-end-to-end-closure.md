# Preset Contract 端到端闭环纠偏实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 18 个单主体 QuestionPreset 恢复为可治理、可验证、可公开调用的 Active Contract，隐藏跨主体 Draft，修复结构化主体 suggestion 被误路由到 GENERAL 的问题，并让旧基线或缺字段 Bundle 在发布前失败。

**Architecture:** 在现有公开快照生成链中加入版本化 Contract Policy 与确定性投影；`contractSubjectId` 单独承担执行主体语义，`projectIds/caseIds` 继续承担展示关联。候选生成、评审、发布、Bundle 加载和 PostgreSQL 导入共同校验单项版本与 Active 集合指纹。运行时按 Reference → Preset → canonical/alias → deterministic rule → structured subject → model 的固定顺序解析，公开 DTO 只暴露 ACTIVE preset。

**Tech Stack:** PowerShell 5.1、JSON Schema、Java 21、Spring Boot、Jackson、Maven、Vue 3、TypeScript、Vitest、Playwright。

## Global Constraints

- 设计权威为 `docs/superpowers/specs/2026-08-05-preset-contract-end-to-end-closure-design.md`；旧的 `2026-08-04-preset-contract-dual-evidence-selection-design.md` 保留为历史设计，不回写。
- 目标公开内容版本固定为 `2026-08-05.1`，schema 保持 `4.0`；当前 `2026-08-04.2` Bundle 是唯一增量基线。
- Active 集合必须精确为 18 项；`question-public-assets-overview` 必须为 DRAFT 且不得进入公共 DTO。
- `contractSubjectId` 是执行主体唯一来源；`projectIds/caseIds` 仅用于展示关联。Active Contract 的主体必须存在，并至少出现在一个展示关联数组中。
- Required/Supporting Claim 必须属于 `contractSubjectId`；项目级 Contract 不得直接引用 Case Claim。
- Contract 失败不得降级到 BM25、向量检索、模型分类或 GENERAL。
- 不公开 Claim IDs、Evidence IDs、Evidence Requirement 或 `contractSubjectId`。
- Java 不使用 `var`、`record`、Lombok；所有新领域对象保持不可变并保留必要的兼容构造器。
- 所有数组顺序均参与单项 Contract 版本计算；不得排序 aliases、Required Claims 或 Supporting Claims。Active 集合仅在计算集合指纹时按 `presetId` 排序。
- 治理脚本必须返回设计文档第 11 节定义的类型化失败码；不得自动降级、删除或从旧 Bundle 补字段。
- 现有工作树包含用户的未提交修改；不得 reset、restore、stash 或覆盖无关文件。修改 `docs/11-项目演进日志.md` 前先检查该文件现有差异，只追加本次事实。
- 未获得用户明确 Git 授权前，不执行本计划中的 `git add` / `git commit` 命令。每个 Commit 步骤只是授权后的检查点。

---

## 文件结构

### 新增文件

- `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractSetHash.java`：跨发布阶段复用的 Active Contract 集合指纹算法。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java`：`NONE / MATCHED / INVALID` 结构化主体解析结果。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolutionType.java`：结构化主体解析结果类型。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java`：不依赖问句指代词的 slug 解析器。
- `backend/src/test/java/com/portfolio/agent/portfolio/domain/PresetContractSetHashTest.java`。
- `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java`。
- `backend/src/test/java/com/portfolio/agent/answer/domain/AnswerQuestionTest.java`。
- `backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java`：真实 Bundle 与截图请求回放。
- `governance/portfolio-governance/schemas/preset-contract-policy.schema.json`。
- `governance/portfolio-governance/policies/preset-contract-policy.v1.json`。
- `governance/portfolio-governance/candidates/preset-contract-closure-public-patch.json`：五条 ABTest 项目级 Claim 和 Link 的审核候选。
- `scripts/portfolio-governance.preset-contract-policy.test.ps1`。
- `scripts/portfolio-governance.preset-contract-closure-candidate.test.ps1`。

### 修改文件

- `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractVersion.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/domain/ReleaseManifest.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoader.java`
- `backend/src/main/java/com/portfolio/agent/release/PublicBundleVerificationCli.java`
- `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerQuestion.java`
- `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerResolution.java`
- `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDisposition.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolver.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- `scripts/portfolio-governance.prepare-candidate.test.ps1`
- 对应 Java 单元/集成测试和前端测试文件。
- `frontend/src/features/public-content/model/publicContentTypes.ts`
- `frontend/src/features/agent/model/answerTypes.ts`
- `frontend/src/features/agent/model/answerLabels.ts`
- `frontend/src/features/agent/model/mapAnswerResponse.ts`
- `frontend/src/features/agent/components/AgentWorkspace.vue`
- `backend/src/main/resources/public-data/public-portfolio.v1.json`
- `backend/src/main/resources/public-data/bundle/*`
- `docs/11-项目演进日志.md`

---

### Task 1: 固化 Contract 主体、版本和集合指纹领域模型

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractVersion.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerQuestion.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractSetHash.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PresetContractVersionTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PresetContractSetHashTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/domain/AnswerQuestionTest.java`

**Interfaces:**
- Consumes: policy 投影后的 QuestionPreset 字段。
- Produces: `QuestionDefinition.getContractSubjectId()`、稳定 `pcv1-*` 版本、`sha256:*` Active 集合指纹，以及运行时 `AnswerQuestion.contractSubjectId`。

- [ ] **Step 1: 写 `PresetContractVersion` 的 RED 测试**

测试固定以下行为：

```java
assertThat(version(activeQuestion("subject-a", List.of("alias-a", "alias-b"))))
        .isNotEqualTo(version(activeQuestion("subject-b", List.of("alias-a", "alias-b"))));
assertThat(version(activeQuestion("subject-a", List.of("alias-a", "alias-b"))))
        .isNotEqualTo(version(activeQuestion("subject-a", List.of("alias-b", "alias-a"))));
assertThat(versionWithDisplayAssociations(List.of("project-a"), List.of()))
        .isEqualTo(versionWithDisplayAssociations(
                List.of("project-a", "project-b"), List.of("case-a")));
```

同时断言 `contractStatus` 从 `ACTIVE` 改为 `DRAFT` 会改变版本，Required/Supporting 顺序变化会改变版本。

- [ ] **Step 2: 运行领域测试确认 RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PresetContractVersionTest test
```

Expected: FAIL，原因是现有算法仍从 `projectIds + caseIds` 推断主体、会排序 aliases，且没有 status 输入。

- [ ] **Step 3: 增加 `contractSubjectId` 并改写单项版本算法**

`QuestionDefinition` 的 JSON 构造器新增：

```java
@JsonProperty("contractSubjectId") String contractSubjectId
```

字段规范化为 `null` 或 trim 后字符串；兼容构造器传 `null`。`getContractVersion()` 仅对 ACTIVE 调用：

```java
return PresetContractVersion.calculate(
        id,
        text,
        aliases,
        contractSubjectId,
        requiredClaimIds,
        supportingClaimIds,
        evidenceRequirement,
        contractStatus);
```

`PresetContractVersion.calculate` 的参数和 canonical 字符串精确为：

```java
String canonical = "id=" + normalize(id) + "\n"
        + "text=" + normalize(text) + "\n"
        + "aliases=" + String.join(",", normalized(aliases)) + "\n"
        + "subject=" + normalize(contractSubjectId) + "\n"
        + "requiredClaimIds=" + String.join(",", normalized(requiredClaimIds)) + "\n"
        + "supportingClaimIds=" + String.join(",", normalized(supportingClaimIds)) + "\n"
        + "minimumApprovedEvidencePerRequiredClaim="
        + evidenceRequirement.getMinimumApprovedEvidencePerRequiredClaim() + "\n"
        + "publicOnly=" + evidenceRequirement.isPublicOnly() + "\n"
        + "status=" + contractStatus.name().toLowerCase(Locale.ROOT) + "\n";
```

保留 NFKC、`Locale.ROOT` 小写、trim 和连续空白折叠；删除 alias 排序逻辑。`AnswerQuestion` 的完整构造器、getter、`equals/hashCode` 同步增加 `contractSubjectId`，旧构造器传 `null`。

- [ ] **Step 4: 写集合指纹 RED 测试**

```java
assertThat(PresetContractSetHash.calculate(List.of(questionB, questionA)))
        .isEqualTo(PresetContractSetHash.calculate(List.of(questionA, questionB)));
assertThat(PresetContractSetHash.calculate(List.of(questionA, questionB)))
        .isNotEqualTo(PresetContractSetHash.calculate(List.of(questionAChanged, questionB)));
assertThatThrownBy(() -> PresetContractSetHash.calculate(List.of(questionA, duplicateA)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate active preset id");
```

- [ ] **Step 5: 实现集合指纹**

只接收 ACTIVE Question，按 `id` 排序，使用无空白 ASCII JSON：

```java
String canonical = active.stream()
        .sorted(Comparator.comparing(QuestionDefinition::getId))
        .map(question -> "{\"presetId\":\"" + question.getId()
                + "\",\"contractVersion\":\"" + question.getContractVersion() + "\"}")
        .collect(Collectors.joining(",", "[", "]"));
return "sha256:" + HexFormat.of().formatHex(sha256(canonical));
```

ID 和版本均已被格式约束为 ASCII；实现仍须显式拒绝空 ID、空版本、非 ACTIVE 输入和重复 ID。

- [ ] **Step 6: 运行测试确认 GREEN**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PresetContractVersionTest,PresetContractSetHashTest test
```

Expected: PASS。

- [ ] **Step 7: 授权后提交检查点**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractVersion.java backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractSetHash.java backend/src/main/java/com/portfolio/agent/answer/domain/AnswerQuestion.java backend/src/test/java/com/portfolio/agent/portfolio/domain/PresetContractVersionTest.java backend/src/test/java/com/portfolio/agent/portfolio/domain/PresetContractSetHashTest.java
git commit -m "修复：显式建模预设契约执行主体与版本"
```

---

### Task 2: 将 Active Contract 不变量和集合指纹加入 Bundle 加载门禁

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ReleaseManifest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoader.java`
- Modify: `backend/src/main/java/com/portfolio/agent/release/PublicBundleVerificationCli.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/domain/ReleaseBundleModelContractTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoaderTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/release/PublicBundleVerificationCliTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PublicBundleDatabaseImporterTest.java`

**Interfaces:**
- Consumes: schema 4.0 `portfolio.json` 与 manifest `presetContractSetHash`。
- Produces: JVM 启动、CLI verify 和 PostgreSQL import 之前的同一套 fail-closed 校验。

- [ ] **Step 1: 写校验器 RED 测试**

把 `activeQuestionJson()` 固定为包含：

```json
"contractSubjectId":"sql-audit-project",
"requiredClaimIds":["claim-1"],
"supportingClaimIds":[],
"evidenceRequirement":{"minimumApprovedEvidencePerRequiredClaim":1,"publicOnly":true},
"contractStatus":"ACTIVE"
```

新增测试分别删除主体、改成未知主体、改成未包含于展示关联的主体、引用外部主体 Claim、让 Required/Supporting 重叠、移除 DIRECT APPROVED Evidence；逐项断言加载失败且消息包含对应字段或 Claim ID。

- [ ] **Step 2: 运行校验器测试确认 RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test
```

Expected: 至少“多展示关联 + 单 contractSubjectId”场景 FAIL，因为现有校验仍要求 `projectIds.size + caseIds.size == 1`。

- [ ] **Step 3: 改写 Active Contract 校验**

`validateActiveQuestionContracts` 接收 `projectsById` 和 `casesById`，核心判断改为：

```java
String subjectId = question.getContractSubjectId();
require(hasText(subjectId),
        "active question contractSubjectId is required: " + question.getId());
require(projectsById.containsKey(subjectId) || casesById.containsKey(subjectId),
        "active question contractSubjectId is unknown: " + question.getId());
require(question.getProjectIds().contains(subjectId)
                || question.getCaseIds().contains(subjectId),
        "active question contractSubjectId must be a display association: "
                + question.getId());
```

其余 VERIFIED、同主体、Evidence Requirement、canonical/alias 全局唯一校验保持并基于该字段执行。DRAFT 允许 `contractSubjectId == null`，但不得带 Required/Supporting Claims。

- [ ] **Step 4: 写 manifest/set hash RED 测试**

在 loader 与 CLI fixture 中加入合法 `presetContractSetHash`，再分别测试缺失、格式非法、与 `portfolio.json` 重算结果不符。预期异常消息固定为：

```text
presetContractSetHash is invalid
presetContractSetHash mismatch
```

- [ ] **Step 5: 扩展 manifest 与验证入口**

`ReleaseManifest` 增加 JSON 字段、getter 和兼容构造器参数：

```java
@JsonProperty("presetContractSetHash") String presetContractSetHash
```

`PublicBundleVerificationCli.MANIFEST_FIELDS` 加入该字段；`PublicBundleLoader` 对 retrieval Bundle 要求 `sha256:[a-f0-9]{64}`，在 `PortfolioSnapshotValidator` 通过后重算并比较：

```java
String actualContractSetHash = PresetContractSetHash.calculate(
        published.getQuestions().stream()
                .filter(QuestionDefinition::isActiveContract)
                .toList());
require(actualContractSetHash.equals(manifest.getPresetContractSetHash()),
        "presetContractSetHash mismatch");
```

`PublicBundleDatabaseImporterTest` 用带合法 hash 且已由 loader 验证的 `RuntimeContentSnapshot` 导入，并断言篡改 Contract 的 Bundle 在进入 importer 前失败；数据库不新增第二套 Contract 配置表。

- [ ] **Step 6: 运行门禁测试确认 GREEN**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest,ReleaseBundleModelContractTest,PublicBundleLoaderTest,PublicBundleVerificationCliTest,PublicBundleDatabaseImporterTest test
```

Expected: PASS。

- [ ] **Step 7: 授权后提交检查点**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java backend/src/main/java/com/portfolio/agent/portfolio/domain/ReleaseManifest.java backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoader.java backend/src/main/java/com/portfolio/agent/release/PublicBundleVerificationCli.java backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java backend/src/test/java/com/portfolio/agent/portfolio/domain/ReleaseBundleModelContractTest.java backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoaderTest.java backend/src/test/java/com/portfolio/agent/release/PublicBundleVerificationCliTest.java backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PublicBundleDatabaseImporterTest.java
git commit -m "修复：在发布包加载链校验预设契约集合"
```

---

### Task 3: 建立 18 项 Contract Policy 与确定性投影

**Files:**
- Create: `governance/portfolio-governance/schemas/preset-contract-policy.schema.json`
- Create: `governance/portfolio-governance/policies/preset-contract-policy.v1.json`
- Create: `scripts/portfolio-governance.preset-contract-policy.test.ps1`
- Modify: `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify: `scripts/portfolio-governance.prepare-candidate.test.ps1`

**Interfaces:**
- Consumes: 合并完事实 patch 的候选 `questionPresets/claims/evidence/claimEvidenceLinks`。
- Produces: 投影后的 18 个 ACTIVE Contract、一个规范化 DRAFT、与 Java 完全一致的 `contractVersion` 和 `presetContractSetHash`。

- [ ] **Step 1: 写 policy schema 和投影 RED 测试**

schema 精确限制顶层字段：

```json
{
  "schemaVersion": "1.0",
  "activeContracts": [],
  "nonPublicDraftPresetIds": ["question-public-assets-overview"]
}
```

`activeContracts` 每项只允许 `presetId`、`contractSubjectId`、`requiredClaimIds`、`supportingClaimIds`、`evidenceRequirement`、`status`；`status` const `ACTIVE`，`publicOnly` const `true`，minimum 最小为 1。

测试覆盖合法 policy、重复 preset、未知 preset、缺主体、跨主体 Claim、Evidence 不足、未声明 ACTIVE、声明项缺失、旧基线重生成和 hash 篡改，并精确断言七个失败码。

- [ ] **Step 2: 运行脚本测试确认 RED**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.preset-contract-policy.test.ps1
```

Expected: FAIL，消息为 `preset contract policy is required.`。

- [ ] **Step 3: 写入精确 18 项 policy**

每项 `evidenceRequirement` 均为 `minimumApprovedEvidencePerRequiredClaim=1 / publicOnly=true`。以下表格是完整 allowlist，字段顺序按表写入 JSON：

| presetId | contractSubjectId | requiredClaimIds | supportingClaimIds |
|---|---|---|---|
| `sql-audit-overview` | `sql-audit-project` | `claim-sql-audit-background`, `claim-sql-audit-responsibility`, `claim-sql-audit-technical-decision`, `claim-sql-audit-verification`, `claim-sql-audit-delivered` | `claim-sql-audit-documented-handoff` |
| `question-sql-audit-negative-input` | `sql-audit-project` | `claim-sql-audit-fixed-string-search` | `claim-sql-audit-verification` |
| `question-sql-audit-partial-success` | `sql-audit-project` | `claim-sql-audit-source-selection`, `claim-sql-audit-partial-success` | `claim-sql-audit-selected-target-check` |
| `question-case-multilingual-overview` | `case-multilingual-upload` | `claim-case-multilingual-replacement-problem`, `claim-case-multilingual-preserve-existing`, `claim-case-multilingual-sequential-verification` | `claim-case-multilingual-no-backfill` |
| `question-case-role-reset-overview` | `case-role-reset` | `claim-case-role-reset-cache-interference-problem`, `claim-case-role-reset-controlled-flow`, `claim-case-role-reset-confirmation-safety`, `claim-case-role-reset-acceptance`, `claim-case-role-reset-documented-delivery` | empty |
| `question-case-codegraph-overview` | `case-codegraph-evaluation` | `claim-case-codegraph-narrowing`, `claim-case-codegraph-combined-workflow`, `claim-case-codegraph-evaluation-method` | `claim-case-codegraph-failure-boundary`, `claim-case-codegraph-manual-quality-review`, `claim-case-codegraph-qualitative-publication` |
| `question-sql-audit-async-and-recovery` | `sql-audit-project` | `claim-sql-audit-async-task-lifecycle`, `claim-sql-audit-progress-fallback` | empty |
| `question-sql-audit-progress-fallback` | `sql-audit-project` | `claim-sql-audit-progress-fallback` | `claim-sql-audit-async-task-lifecycle` |
| `question-sql-audit-archive-and-truncation` | `sql-audit-project` | `claim-sql-audit-result-lifecycle`, `claim-sql-audit-truncation-disclosure` | empty |
| `question-case-multilingual-verification-sequence` | `case-multilingual-upload` | `claim-case-multilingual-preserve-existing`, `claim-case-multilingual-sequential-verification` | empty |
| `question-case-multilingual-recovery-boundary` | `case-multilingual-upload` | `claim-case-multilingual-no-backfill` | `claim-case-multilingual-preserve-existing` |
| `question-case-role-reset-acceptance-result` | `case-role-reset` | `claim-case-role-reset-controlled-flow`, `claim-case-role-reset-acceptance` | `claim-case-role-reset-documented-delivery` |
| `question-case-role-reset-safety-boundary` | `case-role-reset` | `claim-case-role-reset-confirmation-safety` | `claim-case-role-reset-controlled-flow` |
| `question-case-codegraph-method` | `case-codegraph-evaluation` | `claim-case-codegraph-evaluation-method` | `claim-case-codegraph-narrowing`, `claim-case-codegraph-combined-workflow` |
| `question-case-codegraph-quality-boundary` | `case-codegraph-evaluation` | `claim-case-codegraph-manual-quality-review`, `claim-case-codegraph-qualitative-publication` | `claim-case-codegraph-failure-boundary` |
| `question-abtest-overview` | `weekend-login-abtest-project` | `claim-abtest-project-background`, `claim-abtest-project-responsibility`, `claim-abtest-project-stratification-bucketing`, `claim-abtest-project-stable-assignment`, `claim-abtest-project-validation-rollback`, `claim-abtest-project-delivered` | empty |
| `question-abtest-stratification-bucketing` | `weekend-login-abtest-project` | `claim-abtest-project-stratification-bucketing` | `claim-abtest-project-background` |
| `question-abtest-stable-assignment-and-rollback` | `weekend-login-abtest-project` | `claim-abtest-project-stable-assignment`, `claim-abtest-project-validation-rollback` | `claim-abtest-project-delivered` |

- [ ] **Step 4: 实现 PowerShell 投影器**

在治理脚本加入并只通过以下五个入口操作 Contract：

```text
Read-PresetContractPolicy(PathValue) -> 已完成 schema、字段集和唯一性校验的 policy
Get-PresetContractVersion(Question) -> pcv1-[a-f0-9]{16}
Get-PresetContractSetHash(Questions) -> sha256:[a-f0-9]{64}
Invoke-PresetContractProjection(Portfolio, Policy) -> SetHash
Assert-PresetContractProjection(Portfolio, Policy, ExpectedSetHash) -> 无返回值；不一致时终止
```

实现体不得留空：`Invoke-PresetContractProjection` 按 `presetId` 唯一查找、写入 policy 的四组 Contract 字段和 ACTIVE 状态；把 draft 清单项设为 `contractSubjectId=$null`、空 Required/Supporting、DRAFT；随后检查主体、Claim、DIRECT APPROVED Evidence、canonical/alias 唯一性和 Active 集合。PowerShell 规范化使用 FormKC、`ToLowerInvariant()`、Trim 和 `\s+` 折叠，canonical 字符串与 Task 1 完全一致。

集合指纹的 canonical JSON 精确为：

```text
[{"presetId":"<按 id 升序>","contractVersion":"pcv1-..."},...]
```

函数失败时使用：`PRESET_CONTRACT_POLICY_INVALID`、`PRESET_CONTRACT_ACTIVE_SET_DRIFT`、`PRESET_CONTRACT_SUBJECT_INVALID`、`PRESET_CONTRACT_CLAIM_INVALID`、`PRESET_CONTRACT_EVIDENCE_INSUFFICIENT`、`PRESET_CONTRACT_PROJECTION_MISMATCH`、`PRESET_CONTRACT_SET_HASH_MISMATCH`。

- [ ] **Step 5: 将投影插入治理生命周期**

所有候选分支在事实 patch 合并后、写盘前调用 projector。`validate / benchmark / build-review-pack / approve / publish / verify` 在读取候选后调用 assert，并把 set hash 写入 snapshot、approval request、approval、manifest、publish audit 与 verify 输出。policy schema 文件加入现有 `policyBundleHash` 输入。

旧版本 prepare 测试改为明确期待 `PRESET_CONTRACT_ACTIVE_SET_DRIFT`；不再把能重新生成缺 Contract 的历史版本视为成功。

- [ ] **Step 6: 运行脚本回归确认 GREEN**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.preset-contract-policy.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.prepare-candidate.test.ps1
```

Expected: policy 测试 PASS；历史基线场景以预期失败码 PASS。

- [ ] **Step 7: 授权后提交检查点**

```powershell
git add governance/portfolio-governance/schemas/preset-contract-policy.schema.json governance/portfolio-governance/policies/preset-contract-policy.v1.json governance/portfolio-governance/scripts/portfolio-governance.ps1 scripts/portfolio-governance.preset-contract-policy.test.ps1 scripts/portfolio-governance.prepare-candidate.test.ps1
git commit -m "功能：增加预设契约治理策略与确定性投影"
```

---

### Task 4: 补齐 ABTest 项目级 Claims 并生成 `2026-08-05.1` 候选

**Files:**
- Create: `governance/portfolio-governance/candidates/preset-contract-closure-public-patch.json`
- Create: `scripts/portfolio-governance.preset-contract-closure-candidate.test.ps1`
- Modify: `governance/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify: `governance/portfolio-governance/benchmark/weekend-login-abtest-benchmarks.v1.json`

**Interfaces:**
- Consumes: 已审核的 `2026-08-04.2` Bundle 与四条既有 ABTest Evidence。
- Produces: 五条 VERIFIED 项目级 Claim、五条 APPROVED DIRECT Link、Project `claimIds` 更新和经 policy 投影的 `2026-08-05.1` 候选。

- [ ] **Step 1: 写增量候选 RED 测试**

测试 patch 精确约束：

```powershell
$patch.baseContentVersion -eq '2026-08-04.2'
$patch.targetContentVersion -eq '2026-08-05.1'
@($patch.claims).Count -eq 5
@($patch.links).Count -eq 5
@($patch.projectUpdates).Count -eq 1
@($patch.projectUpdates[0].addClaimIds).Count -eq 5
```

并断言每条 Claim 均为 `PROJECT / weekend-login-abtest-project / EVIDENCE_SUPPORTED / VERIFIED / DELIVERED / PRIMARY / KEY`，每条 Link 均为 `DIRECT / APPROVED`。

- [ ] **Step 2: 运行候选测试确认 RED**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.preset-contract-closure-candidate.test.ps1
```

Expected: FAIL，消息为 `Preset contract closure patch is required.`。

- [ ] **Step 3: 创建五条精确 Claim/Link**

| Claim | Category | Statement | Evidence |
|---|---|---|---|
| `claim-abtest-project-background` | `BACKGROUND` | `项目目标是把周末登录奖励从一次性配置扩展为可比较、稳定归组、可验证且可停止的 A/B 实验闭环。` | `evidence-abtest-delivery-history` |
| `claim-abtest-project-responsibility` | `RESPONSIBILITY` | `本人负责实验建模、分层分桶、服务端与配置演进、测试验收、埋点核对以及停止和回滚边界设计。` | `evidence-abtest-delivery-history` |
| `claim-abtest-project-stratification-bucketing` | `TECHNICAL_DECISION` | `先按实验前历史登录天数分层，再在层内稳定分桶，以降低历史活跃度差异并保持组间可比。` | `evidence-abtest-experiment-design-notes` |
| `claim-abtest-project-stable-assignment` | `IMPLEMENTATION` | `实验标签通过服务端统一规则和持久化结果保持跨请求、跨实例的稳定归组，并随配置版本演进。` | `evidence-abtest-service-sql-evolution` |
| `claim-abtest-project-validation-rollback` | `VERIFICATION` | `验收覆盖配置、归组、激活、曝光与结果事件，并明确异常停止和回滚边界；不声称发生过线上事故。` | `evidence-abtest-validation-risk-notes` |

每条 Claim 的 `detail` 明确只陈述公开工程事实、不推断业务指标；topics 分别使用现有枚举词汇。Link ID 使用 `link-` 加对应 Claim 后缀，`scope` 与 Statement 一一对应。

- [ ] **Step 4: 增加 closure prepare 分支**

`Invoke-PreparePresetContractClosureCandidate` 只接受：

```text
TargetVersion = 2026-08-05.1
RuntimeBundle = backend/src/main/resources/public-data/bundle
PatchManifest = governance/.../preset-contract-closure-public-patch.json
```

它验证七文件闭集和 `2026-08-04.2` checksums/manifest/payload hash，合并五条 Claim/Link，将五个 ID 追加到 `weekend-login-abtest-project.claimIds`，执行 policy 投影，构建 canonical RAG 文档并原子写入 prepared candidate。最终数量固定为：projects 6、cases 52、claims 88、evidence 63、links 88、timelineEvents 12、questionPresets 19、collections 3。

- [ ] **Step 5: 修正 benchmark 只检查 Active**

治理 benchmark 循环从所有 preset 改为 policy Active allowlist。现有 `weekend-login-abtest-benchmarks.v1.json` 保留 18 个 Active preset 的每项五类覆盖；删除 `question-public-assets-overview` 的五条 Contract case，最终为 90 条。

- [ ] **Step 6: 运行候选测试确认 GREEN**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.preset-contract-closure-candidate.test.ps1
```

Expected: PASS，并输出 `2026-08-05.1`、18 个 ACTIVE、一个 DRAFT 和非空 set hash。

- [ ] **Step 7: 授权后提交检查点**

```powershell
git add governance/portfolio-governance/candidates/preset-contract-closure-public-patch.json governance/portfolio-governance/benchmark/weekend-login-abtest-benchmarks.v1.json governance/portfolio-governance/scripts/portfolio-governance.ps1 scripts/portfolio-governance.preset-contract-closure-candidate.test.ps1
git commit -m "内容：补齐ABTest项目级契约声明"
```

---

### Task 5: 让运行时按 `contractSubjectId` 装配和解析 Preset

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolver.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolverTest.java`

**Interfaces:**
- Consumes: 含多展示关联、单执行主体的 Active QuestionDefinition。
- Produces: 每个 Active preset 恰好挂载到一个 AnswerKnowledge，ContractTask 的 subjectId 与 `contractSubjectId` 一致。

- [ ] **Step 1: 写多展示关联 RED 测试**

创建 ABTest 问题：`projectIds=[weekend-login-abtest-project]`、三个 `caseIds`、`contractSubjectId=weekend-login-abtest-project`。断言项目 AnswerKnowledge 包含一次该问题，三个 Case AnswerKnowledge 都不包含；解析显式 ID 后 task subject 为项目 ID。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=LocalPortfolioKnowledgeAdapterTest,PortfolioPresetResolverTest test
```

Expected: FAIL，因为现有 adapter 排除同时带 projectIds/caseIds 的问题。

- [ ] **Step 3: 改写 adapter 归属判断**

ACTIVE 问题只按执行主体挂载：

```java
private boolean belongsToExecutionSubject(
        QuestionDefinition question,
        String subjectId
) {
    return question.isActiveContract()
            && subjectId.equals(question.getContractSubjectId());
}
```

DRAFT 不进入 runtime questions。`toQuestion` 传递 `contractSubjectId`。`PortfolioPresetResolver.matched` 创建 ContractTask 前同时校验：

```java
if (!match.subject.getStableId().equals(match.question.getContractSubjectId())) {
    return PortfolioPresetResolution.invalid();
}
```

ContractTask 的 subject 参数使用 `match.question.getContractSubjectId()`，不再使用展示容器推断。

- [ ] **Step 4: 运行测试确认 GREEN**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=LocalPortfolioKnowledgeAdapterTest,PortfolioPresetResolverTest test
```

Expected: PASS。

- [ ] **Step 5: 授权后提交检查点**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolver.java backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolverTest.java
git commit -m "修复：按契约主体装配预设问题"
```

---

### Task 6: 在模型分类前解析结构化主体并返回 INVALID_INPUT

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolutionType.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDisposition.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerResolution.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java`

**Interfaces:**
- Consumes: `PortfolioTurn.question/projectSlug/caseSlug` 和当前公开 `RuntimeAnswerContent`。
- Produces: `NONE`、`MATCHED(FACT_LOOKUP + subjectId)` 或 `INVALID`；INVALID 映射为公开 `AnswerResolution.INVALID_INPUT`。

- [ ] **Step 1: 写 resolver 和路由 RED 测试**

覆盖：无 slug → NONE；已知 project slug → MATCHED；已知 case slug → MATCHED；未知 slug → INVALID；同名/重复 slug → INVALID；模型关闭时 `测试角色重置工具的背景和目标是什么？ + projectSlug=role-reset-tool` 仍进入 RULE 的 SUBJECT_SCOPED_RELEVANCE。

更新现有 unknown-case 集成断言：

```java
.andExpect(jsonPath("$.resolution").value("INVALID_INPUT"))
.andExpect(jsonPath("$.noticeCode").value("STRUCTURED_SUBJECT_INVALID"));
```

- [ ] **Step 2: 运行路由测试确认 RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=StructuredSubjectTaskResolverTest,DefaultPortfolioIntelligenceRoutingTest,CaseConversationBundleIntegrationTest test
```

Expected: FAIL；已知结构化 slug 仍受 `referencesExplicitSubject()` 文本词表控制，未知 slug 仍返回 NOT_SUPPORTED。

- [ ] **Step 3: 实现三态 resolver**

核心入口：

```java
public StructuredSubjectResolution resolve(
        PortfolioTurn turn,
        RuntimeAnswerContent content
) {
    if (turn.getProjectSlug() == null && turn.getCaseSlug() == null) {
        return StructuredSubjectResolution.none();
    }
    List<AnswerKnowledge> matches = turn.getProjectSlug() != null
            ? matchingProjects(turn.getProjectSlug(), content)
            : matchingCases(turn.getCaseSlug(), content);
    if (matches.size() != 1) {
        return StructuredSubjectResolution.invalid();
    }
    return StructuredSubjectResolution.matched(factLookup(turn, matches.getFirst()));
}
```

`PortfolioTurn` 已阻止 project/case 同时存在；resolver 不返回伪 ID。

- [ ] **Step 4: 调整固定解析优先级**

在 `DefaultPortfolioIntelligence.resolveTurn` 的 deterministic rule 后、provider 判断前调用：

```java
StructuredSubjectResolution structured = structuredSubjectResolver.resolve(turn, content);
if (structured.getType() == StructuredSubjectResolutionType.INVALID) {
    return invalidInput(content, "STRUCTURED_SUBJECT_INVALID");
}
if (structured.getType() == StructuredSubjectResolutionType.MATCHED) {
    return execute(structured.getTask(), AnswerIntentSource.RULE, false);
}
```

删除 `explicitSubjectTask`；`referencesExplicitSubject()` 只保留给没有结构化 slug 的自然语言规则，不得成为 slug 是否生效的门槛。配置类注入新 resolver。

增加 `PortfolioDisposition.INVALID_INPUT` 和 `AnswerResolution.INVALID_INPUT`；assembler 映射到一个安全的单块响应，`claimIds/evidenceIds` 为空，不进入 GENERAL fallback。

- [ ] **Step 5: 运行测试确认 GREEN**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=StructuredSubjectTaskResolverTest,DefaultPortfolioIntelligenceRoutingTest,CaseConversationBundleIntegrationTest test
```

Expected: PASS。

- [ ] **Step 6: 授权后提交检查点**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolutionType.java backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDisposition.java backend/src/main/java/com/portfolio/agent/answer/domain/AnswerResolution.java backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java
git commit -m "修复：优先解析结构化作品主体"
```

---

### Task 7: 公共 DTO 只输出 ACTIVE，前端强制携带 Contract 身份

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`
- Modify: `frontend/src/features/public-content/model/publicContentTypes.ts`
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/answerLabels.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Modify: `frontend/src/features/agent/model/answerLabels.test.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`

**Interfaces:**
- Consumes: 19 个内部 preset，其中 18 ACTIVE、1 DRAFT。
- Produces: 公共 API 精确 18 项；正式 preset 点击请求始终带 `questionPresetId + contractVersion + question + subject slug`，普通 suggestion 不带 Contract 身份。

- [ ] **Step 1: 写 DTO 和前端 RED 测试**

后端断言 `questionPresets.length()==18`、所有 availability 为 ACTIVE、所有 version 匹配 `pcv1-[a-f0-9]{16}`、找不到 `question-public-assets-overview`。前端断言：缺 version 的 preset 被过滤；正式 preset 请求同时带 ID/version；普通 suggestion 只有 text 和 XOR slug；INVALID_INPUT 显示“请求的作品范围无效”。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioResponseMapperTest,PortfolioControllerTest test
npm.cmd --prefix frontend test -- --run src/features/agent/components/AgentWorkspace.test.ts src/features/agent/model/answerLabels.test.ts src/features/agent/model/mapAnswerResponse.test.ts
```

Expected: 后端仍输出 19 项；前端类型仍允许 DRAFT/null version 且无 INVALID_INPUT。

- [ ] **Step 3: 收紧后端 mapper 和前端类型**

Mapper：

```java
content.getQuestionPresets().stream()
        .filter(QuestionDefinition::isActiveContract)
        .map(question -> toQuestionPreset(question, projectsById, casesById))
        .toList();
```

前端类型：

```ts
export interface QuestionPreset {
  id: string
  projectSlug: string | null
  caseSlugs: string[]
  text: string
  audiences: AudienceRole[]
  placements: Array<'HOME' | 'AGENT' | 'PROJECT'>
  contractVersion: string
  availability: 'ACTIVE'
}
```

`AnswerResolution` 增加 `'INVALID_INPUT'`。加载公共内容时拒绝/过滤 version 为空或不匹配格式的 preset，并记录已有的安全诊断，不记录问题正文。

- [ ] **Step 4: 简化正式 preset 提交**

正式列表已经只含 ACTIVE，因此 `submit` 只在同时找到 preset 和非空 version 时发送：

```ts
questionPresetId: preset?.id,
contractVersion: preset
  ? resolvedContractVersions.get(preset.id) ?? preset.contractVersion
  : undefined,
```

`submitSuggestion` 保持不发送这两个字段。Case 入口传 `caseSlug` 且 `projectSlug=null`；Project 入口反之。

- [ ] **Step 5: 运行测试确认 GREEN**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioResponseMapperTest,PortfolioControllerTest test
npm.cmd --prefix frontend test -- --run src/features/agent/components/AgentWorkspace.test.ts src/features/agent/model/answerLabels.test.ts src/features/agent/model/mapAnswerResponse.test.ts
npm.cmd --prefix frontend run build
```

Expected: PASS。

- [ ] **Step 6: 授权后提交检查点**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java frontend/src/features/public-content/model/publicContentTypes.ts frontend/src/features/agent/model/answerTypes.ts frontend/src/features/agent/model/answerLabels.ts frontend/src/features/agent/model/mapAnswerResponse.ts frontend/src/features/agent/components/AgentWorkspace.vue frontend/src/features/agent/components/AgentWorkspace.test.ts frontend/src/features/agent/model/answerLabels.test.ts frontend/src/features/agent/model/mapAnswerResponse.test.ts
git commit -m "修复：仅公开可执行预设并回传契约身份"
```

---

### Task 8: 发布治理候选并替换完整运行 Bundle

**Files:**
- Modify: `backend/src/main/resources/public-data/bundle/manifest.json`
- Modify: `backend/src/main/resources/public-data/bundle/portfolio.json`
- Modify: `backend/src/main/resources/public-data/bundle/presentation.json`
- Modify: `backend/src/main/resources/public-data/bundle/rag-documents.jsonl`
- Modify: `backend/src/main/resources/public-data/bundle/keyword-index.json`
- Modify: `backend/src/main/resources/public-data/bundle/vector-index.bin`
- Modify: `backend/src/main/resources/public-data/bundle/checksums.json`
- Modify: `backend/src/main/resources/public-data/public-portfolio.v1.json`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicPortfolioSchemaFourContractTest.java`

**Interfaces:**
- Consumes: 人工审核后的精确 `2026-08-05.1` candidate payload/hash。
- Produces: 七文件原子 Bundle、兼容资源中的同版 `sql-audit-overview`、18 ACTIVE + 1 DRAFT 内部数据。

- [ ] **Step 1: 写真实资源 RED 测试**

`PublicPortfolioSchemaFourContractTest` 读取 classpath Bundle 并断言：版本 `2026-08-05.1`；Active ID 集合精确等于 policy 18 项；每项有主体/Required/Evidence/版本；Draft 仅一项；ABTest 五条新 Claim/Link 存在；manifest set hash 等于 Java 重算值；兼容资源与 Bundle 的 `sql-audit-overview` 主体、Claims、Evidence Requirement、版本一致。

- [ ] **Step 2: 运行测试确认 RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PublicPortfolioSchemaFourContractTest test
```

Expected: FAIL，当前 Bundle 为 `2026-08-04.2` 且 Active 数为 0。

- [ ] **Step 3: 准备候选并执行自动门禁**

先要求环境变量指向仓库外的已审核资产清单：

```powershell
if ([string]::IsNullOrWhiteSpace($env:PORTFOLIO_ASSET_INVENTORY)) { throw 'PORTFOLIO_ASSET_INVENTORY is required' }
powershell -NoProfile -ExecutionPolicy Bypass -File governance/portfolio-governance/scripts/portfolio-governance.ps1 -Command prepare-candidate -Workspace $env:TEMP\portfolio-contract-closure -RuntimeBundle backend/src/main/resources/public-data/bundle -PatchManifest governance/portfolio-governance/candidates/preset-contract-closure-public-patch.json -AssetInventory $env:PORTFOLIO_ASSET_INVENTORY -TargetVersion 2026-08-05.1
```

随后依次执行 `validate`、`benchmark`、`build-review-pack`。记录命令输出的 `candidatePayloadHash`、`ledgerHash`、`presetContractSetHash` 和 review run ID；三者必须在后续阶段完全一致。

- [ ] **Step 4: 人工审核与发布**

暂停自动执行，由用户对精确 review pack 明确批准后，使用治理脚本 `approve`；再使用该 approval ID 执行 `publish`。不得生成自批准文件，不得复用旧 approval。

发布完成后，只把 release 目录的完整七文件复制到 `backend/src/main/resources/public-data/bundle/`；禁止单独编辑 `portfolio.json` 或 manifest。兼容资源从发布候选投影生成，不手工维护 Contract 字段。

- [ ] **Step 5: 验证已发布文件**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipTests package
java -cp backend/target/portfolio-agent.jar com.portfolio.agent.release.PublicBundleVerificationCli backend/src/main/resources/public-data/bundle
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PublicPortfolioSchemaFourContractTest,PublicBundleLoaderTest test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

Expected: 全部 PASS；verification JSON 同时输出 `candidatePayloadHash`、`ledgerHash`、`presetContractSetHash`，Active 为 18。

- [ ] **Step 6: 授权后提交检查点**

```powershell
git add backend/src/main/resources/public-data/bundle backend/src/main/resources/public-data/public-portfolio.v1.json backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicPortfolioSchemaFourContractTest.java
git commit -m "发布：恢复18项预设契约运行包"
```

---

### Task 9: 用真实 Bundle 回放截图请求并锁死失败回归

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: classpath `2026-08-05.1` Bundle，模型访问关闭。
- Produces: 对原截图两条请求和关键失败语义的端到端锁定。

- [ ] **Step 1: 写真实 Bundle 集成场景**

测试类使用：

```java
@SpringBootTest(
        classes = PortfolioAgentApplication.class,
        properties = {
                "portfolio.model-expression.enabled=false",
                "portfolio.conversational-agent.enabled=false"
        })
@AutoConfigureMockMvc
```

先 GET `/api/v1/public-content` 取得 `sql-audit-overview` 的真实版本，再 POST `/api/v2/answers`：

```json
{
  "turnId":"preset-sql-audit",
  "requestToken":"由 turnId 确定性生成的 UUID",
  "question":"请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？",
  "questionPresetId":"sql-audit-overview",
  "contractVersion":"GET 返回的版本",
  "messages":[],
  "context":{"projectSlug":"sql-audit","audienceRole":"INTERVIEWER","source":"AGENT_PAGE"}
}
```

断言 `ANSWERED / EVIDENCE_COMPOSITION / PRESET / VERIFIED`，响应回显同一 preset/version，blocks 非空且 required claim/evidence 均被覆盖。

第二条 POST 使用：

```json
{
  "turnId":"structured-role-reset",
  "requestToken":"由 turnId 确定性生成的 UUID",
  "question":"测试角色重置工具的背景和目标是什么？",
  "messages":[],
  "context":{"projectSlug":"role-reset-tool","audienceRole":"INTERVIEWER","source":"AGENT_PAGE"}
}
```

断言 `ANSWERED / EVIDENCE_COMPOSITION / RULE / VERIFIED`，且不是 `CAPABILITY_UNAVAILABLE / TEMPLATE / GENERAL`。

- [ ] **Step 2: 增加负向场景**

同一测试类增加：陈旧 Contract version → `CAPABILITY_UNAVAILABLE + PRESET_CONTRACT_STALE`；未知 preset/主体不匹配 → Contract unavailable；未知 slug → `INVALID_INPUT`；删除一条 Required Evidence 的临时 Bundle → loader 失败而非 NOT_SUPPORTED。

- [ ] **Step 3: 运行集成测试**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PresetContractBundleIntegrationTest,CaseConversationBundleIntegrationTest test
```

Expected: PASS，且日志中没有对两条主场景触发 GENERAL fallback。

- [ ] **Step 4: 运行前端点击链回归**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/components/AgentWorkspace.test.ts
```

Expected: 正式 preset request 带 ID/version；suggestion request 不带；主体字段保持 XOR。

- [ ] **Step 5: 授权后提交检查点**

```powershell
git add backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java frontend/src/features/agent/components/AgentWorkspace.test.ts
git commit -m "测试：锁定预设契约与结构化主体回放"
```

---

### Task 10: 全量验证、文档收口与工作树审计

**Files:**
- Modify: `docs/11-项目演进日志.md`
- Verify only: all files changed by Tasks 1–9.

**Interfaces:**
- Consumes: 已发布 Bundle 和全部实现差异。
- Produces: 可复核的验证证据、事实边界记录和无占位符的最终变更集。

- [ ] **Step 1: 运行后端全量测试**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行前端全量测试与构建**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Expected: 全部 PASS，构建成功。

- [ ] **Step 3: 运行治理与隐私门禁**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.prepare-candidate.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.preset-contract-policy.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.preset-contract-closure-candidate.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

Expected: 全部 PASS；旧基线缺 Contract 的用例以预期 fail-closed 断言通过。

- [ ] **Step 4: 追加演进日志**

先执行：

```powershell
git diff -- docs/11-项目演进日志.md
```

保留用户现有改动，只追加一条 `2026-08-05` 记录，写明：根因是分支基线回退导致 Contract 字段丢失与 slug 路由受问句词表控制；修复为 18 项 policy 投影、单主体字段、set hash、Active-only DTO、structured subject resolver；验证包含两条截图请求回放。不得写入未执行的发布、测试或审批结论。

- [ ] **Step 5: 扫描占位符与范围外差异**

```powershell
rg -n "TODO|TBD|FIXME|placeholder|待补|稍后补充" backend/src governance/portfolio-governance scripts frontend/src docs/11-项目演进日志.md
git status --short
git diff --check
```

Expected: 本次文件无占位符、`git diff --check` 无输出；原有 design 文件删除和其他用户改动保持原状且未被纳入本次提交。

- [ ] **Step 6: 最终验收清单**

逐项确认：policy=18；Bundle ACTIVE=18；公共 DTO=18；Draft 不公开；五条 ABTest Project Claim/Link 存在；所有 ACTIVE 有主体/Required/version；manifest set hash 重算一致；PostgreSQL import 只接受已验证快照；SQL Audit preset 回放为 ANSWERED/PRESET；角色重置 suggestion 回放为 ANSWERED/RULE；未知 slug 为 INVALID_INPUT；缺字段/旧基线/hash 漂移均阻断。

- [ ] **Step 7: 授权后提交最终检查点**

```powershell
git add docs/11-项目演进日志.md
git commit -m "文档：记录预设契约端到端闭环纠偏"
```

最终交付前再次运行 `git status --short`，只报告本次实际修改和验证结果，不声称未执行的审批、发布或测试。
