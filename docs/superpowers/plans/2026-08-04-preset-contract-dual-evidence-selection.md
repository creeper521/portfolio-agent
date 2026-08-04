# Preset Contract 双取证策略实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将正式 Preset 从主体范围相关性搜索切换为确定性的 Claim Contract 取证，同时保留自由问题的相关性探索、准确失败语义和连续追问能力。

**Architecture:** 保持单一 `PortfolioIntelligence.tryResolve` 深模块和统一 `PortfolioRetriever` Port，只在 Evidence Selection 内部分为 `PRESET_CONTRACT` 与 `RELEVANCE`。Preset Contract 精确绑定 Required/Supporting Claim，Bundle 与 PostgreSQL 复用现有 exact lookup；两条策略汇入不可变 `PortfolioFactBundle`、确定性 Composer、Citation Validator 和统一 `PortfolioDecision`。

**Tech Stack:** Java 21、Spring Boot、Jackson、Maven、JUnit 5、AssertJ、Vue 3、TypeScript、Vite、Vitest、Vue Test Utils、Playwright。

## Global Constraints

- 设计权威：`docs/superpowers/specs/2026-08-04-preset-contract-dual-evidence-selection-design.md`。
- 运行时只能读取 `backend/src/main/resources/public-data/` 下审核过的公开快照；禁止读取私有 Obsidian、候选快照、原始日报、凭据或未审核截图。
- 只有 `publicStatus = APPROVED` 且不暴露 raw content 的 Evidence 可以进入答案。
- Java 生产和测试代码禁止 `var`、`record` 和 Lombok；值对象使用显式不可变类。
- 不新增第二个 Agent、第二套 Retriever、独立 Contract 仓库、Spring AI、SSE、认证或动态外部发布。
- Preset Contract 禁止静默降级到 BM25/向量检索；Composer 禁止访问检索器或内容仓库。
- 不记录或持久化访客问题、答案、原始 Evidence、私有路径和内部环境信息。
- 访客对话继续只存在于标签页内存，刷新或关闭即消失。
- 所有功能与修复执行 TDD：RED -> GREEN -> REFACTOR。
- 仓库存在用户未提交改动；只修改本计划列出的文件，不 reset、restore 或覆盖其他改动。
- 计划中的 Git Commit 步骤仅在用户明确授权暂存和提交后执行；提交信息使用中文。

---

## File map

### Contract 内容与发布边界

- `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractStatus.java`：发布状态枚举。
- `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionEvidenceRequirement.java`：最低 Evidence 要求。
- `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractVersion.java`：确定性版本计算。
- `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java`：公开快照中的 Contract 定义。
- `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`：发布门禁。

### PortfolioIntelligence 内部边界

- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioContractTask.java`：已解析的强契约任务。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/EvidenceSelectionStatus.java`：类型化选择状态。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioEvidenceSelection.java`：统一 Selector 输出。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/ContractEvidenceSelector.java`：确定性取证。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RelevanceEvidenceSelector.java`：自由问题取证和失败语义。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioFactBundle.java`：生成阶段唯一事实输入。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioFactBundleBuilder.java`：Selection 到 FactBundle 的转换。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioCitationValidator.java`：双向引用校验。
- `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java`：只表达已选事实。

### API 与前端

- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/QuestionPresetResponse.java`：暴露 ID、文本、版本和 ACTIVE 状态。
- `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequest.java`：接收 Contract Version。
- `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`：返回已解析 Preset 身份和最新版本。
- `frontend/src/features/public-content/model/publicContentTypes.ts`：Preset Version/Availability 类型。
- `frontend/src/features/agent/api/answerApi.ts`：序列化 Contract Version。
- `frontend/src/features/agent/components/AgentWorkspace.vue`：点击、陈旧版本单次重试和准确失败交互。

---

### Task 1: 建立 Preset Contract 领域模型和确定性版本

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractStatus.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionEvidenceRequirement.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PresetContractVersion.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PresetContractVersionTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java`

**Interfaces:**
- Produces: `PresetContractStatus { DRAFT, ACTIVE, SUSPENDED, RETIRED }`。
- Produces: `QuestionEvidenceRequirement(int minimumApprovedEvidencePerRequiredClaim, boolean publicOnly)`。
- Produces: `PresetContractVersion.calculate(String id, String text, List<String> aliases, List<String> projectIds, List<String> caseIds, List<String> requiredClaimIds, List<String> supportingClaimIds, QuestionEvidenceRequirement requirement) -> pcv1-<16 lowercase hex>`。
- Produces: `QuestionDefinition.getRequiredClaimIds()`, `getSupportingClaimIds()`, `getEvidenceRequirement()`, `getContractStatus()`, `getContractVersion()`。

- [ ] **Step 1: 为稳定版本规则写失败测试**

```java
package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PresetContractVersionTest {

    @Test
    void ignoresAliasOrderButChangesWhenRequiredClaimsChange() {
        QuestionEvidenceRequirement requirement =
                new QuestionEvidenceRequirement(1, true);
        String first = PresetContractVersion.calculate(
                "preset-a", "Canonical?", List.of("Alias B", "Alias A"),
                List.of("project-a"), List.of(),
                List.of("claim-1"), List.of("claim-2"), requirement);
        String reordered = PresetContractVersion.calculate(
                "preset-a", "Canonical?", List.of("Alias A", "Alias B"),
                List.of("project-a"), List.of(),
                List.of("claim-1"), List.of("claim-2"), requirement);
        String changed = PresetContractVersion.calculate(
                "preset-a", "Canonical?", List.of("Alias A", "Alias B"),
                List.of("project-a"), List.of(),
                List.of("claim-3"), List.of("claim-2"), requirement);

        assertThat(first).matches("pcv1-[a-f0-9]{16}");
        assertThat(reordered).isEqualTo(first);
        assertThat(changed).isNotEqualTo(first);
    }
}
```

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PresetContractVersionTest test
```

Expected: FAIL，编译器报告 `QuestionEvidenceRequirement` 或 `PresetContractVersion` 不存在。

- [ ] **Step 3: 实现不可变状态与 Evidence 要求**

```java
public enum PresetContractStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    RETIRED
}
```

```java
public final class QuestionEvidenceRequirement {
    private final int minimumApprovedEvidencePerRequiredClaim;
    private final boolean publicOnly;

    @JsonCreator
    public QuestionEvidenceRequirement(
            @JsonProperty("minimumApprovedEvidencePerRequiredClaim") int minimum,
            @JsonProperty("publicOnly") boolean publicOnly) {
        if (minimum < 1) {
            throw new IllegalArgumentException(
                    "minimumApprovedEvidencePerRequiredClaim must be at least 1");
        }
        this.minimumApprovedEvidencePerRequiredClaim = minimum;
        this.publicOnly = publicOnly;
    }

    public int getMinimumApprovedEvidencePerRequiredClaim() { return minimumApprovedEvidencePerRequiredClaim; }
    public boolean isPublicOnly() { return publicOnly; }
}
```

- [ ] **Step 4: 实现确定性版本算法**

`PresetContractVersion.calculate` 使用 NFKC、lowercase、trim、连续空白折叠；alias 排序，Required/Supporting Claim 保留声明顺序。规范串按固定字段名和换行拼接，SHA-256 后取前 8 bytes，输出 16 位小写 hex。状态、Evidence ID 和 `preferredClaimCategories` 不进入版本。

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
return "pcv1-" + HexFormat.of().formatHex(Arrays.copyOf(hash, 8));
```

主体必须是 `projectIds + caseIds` 合计一个；否则 `calculate` 抛出 `IllegalArgumentException`，由发布门禁处理。

- [ ] **Step 5: 扩展 QuestionDefinition 并保留旧快照读取兼容**

在 `@JsonCreator` 末尾增加：

```java
@JsonProperty("requiredClaimIds") List<String> requiredClaimIds,
@JsonProperty("supportingClaimIds") List<String> supportingClaimIds,
@JsonProperty("evidenceRequirement") QuestionEvidenceRequirement evidenceRequirement,
@JsonProperty("contractStatus") PresetContractStatus contractStatus
```

缺失字段只用于历史 schema 读取兼容：列表归一为空，状态归一为 `DRAFT`，Evidence 要求归一为 `minimum=1/publicOnly=true`。只有 `ACTIVE` 才允许调用 `getContractVersion()` 并进入运行时。

- [ ] **Step 6: 运行领域测试确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PresetContractVersionTest,PortfolioModelContractTest test
```

Expected: PASS。

- [ ] **Step 7: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/domain backend/src/test/java/com/portfolio/agent/portfolio/domain
git commit -m "feat(portfolio): 增加推荐问题事实契约模型"
```

---

### Task 2: 加强发布门禁并迁移首批公开 Preset

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/text/StableQuestionNormalizer.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/engine/QuestionNormalizer.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Modify: `backend/src/main/resources/public-data/bundle/portfolio.json`
- Modify: `backend/src/main/resources/public-data/public-portfolio.v1.json`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/engine/QuestionNormalizerTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/release/PublicBundleVerificationCliTest.java`

**Interfaces:**
- Consumes: Task 1 Contract 字段和版本算法。
- Produces: `StableQuestionNormalizer.normalize(String)`，供发布校验和运行时 alias 解析使用。
- Produces: 只有通过全部强契约门禁的 `ACTIVE` Preset。

- [ ] **Step 1: 写发布门禁失败测试**

在 `PortfolioSnapshotValidatorTest` 增加四个具体断言：

```java
@Test
void rejectsActivePresetWithoutRequiredClaims() {
    assertInvalid(activeContractJson().replace(
            "\"requiredClaimIds\": [\"claim-sql-audit-background\"]",
            "\"requiredClaimIds\": []"),
            "active question requiredClaimIds");
}

@Test
void rejectsRequiredClaimOwnedByAnotherSubject() {
    assertInvalid(activeContractJson().replace(
            "claim-sql-audit-background", "claim-case-role-reset-controlled-flow"),
            "required claim subject");
}

@Test
void rejectsRequiredClaimWithoutEnoughApprovedDirectEvidence() {
    String json = activeContractJson().replace(
            "\"minimumApprovedEvidencePerRequiredClaim\": 1",
            "\"minimumApprovedEvidencePerRequiredClaim\": 2");
    assertInvalid(json, "required claim approved evidence");
}

@Test
void rejectsActiveCanonicalAliasCollision() {
    assertInvalid(twoActiveContractsWithSameAliasJson(),
            "active question text identity");
}
```

- [ ] **Step 2: 运行校验测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test
```

Expected: FAIL，新 Contract 不变量尚未校验。

- [ ] **Step 3: 抽取唯一文本规范化实现**

`StableQuestionNormalizer` 复用现有 NFKC/lowercase/whitespace/trailing punctuation 规则；中文标点使用 code point，避免源码编码歧义：

```java
private static final Set<Integer> TRAILING_PUNCTUATION = Set.of(
        0x003F, 0x0021, 0x002E, 0x003B, 0x002C,
        0xFF1F, 0xFF01, 0x3002, 0xFF1B, 0xFF0C);
```

现有 Spring `QuestionNormalizer` 只委托这个静态实现，确保发布时唯一性检查与运行时命中规则完全一致。

- [ ] **Step 4: 实现 ACTIVE Contract 门禁**

对每个 Active Preset 严格检查：

```text
deterministicEntry == true
projectIds.size + caseIds.size == 1
requiredClaimIds 非空、唯一
supportingClaimIds 唯一且与 required 不相交
所有 Claim 存在、VERIFIED、subjectType/subjectId 与 Preset 一致
每个 Required Claim 的 APPROVED DIRECT Link 数量 >= minimum
每个 Link 指向 APPROVED 且 rawContentPublic == false 的 Evidence
evidenceRequirement.publicOnly == true
canonical 与 alias 在全部 ACTIVE Preset 中规范化后唯一
```

`DRAFT/SUSPENDED/RETIRED` 允许没有 Required Claim，但仍校验 ID、文本和引用列表格式，且不得由公开 DTO 输出。

- [ ] **Step 5: 按精确清单迁移 15 个单主体 Preset**

每项使用 `minimumApprovedEvidencePerRequiredClaim=1`、`publicOnly=true`、`contractStatus=ACTIVE`：

| Preset | Required Claim IDs | Supporting Claim IDs |
|---|---|---|
| `sql-audit-overview` | `claim-sql-audit-background`, `claim-sql-audit-responsibility`, `claim-sql-audit-technical-decision`, `claim-sql-audit-verification`, `claim-sql-audit-delivered` | `claim-sql-audit-documented-handoff` |
| `question-sql-audit-negative-input` | `claim-sql-audit-fixed-string-search` | `claim-sql-audit-verification` |
| `question-sql-audit-partial-success` | `claim-sql-audit-source-selection`, `claim-sql-audit-partial-success` | `claim-sql-audit-selected-target-check` |
| `question-case-multilingual-overview` | `claim-case-multilingual-replacement-problem`, `claim-case-multilingual-preserve-existing`, `claim-case-multilingual-sequential-verification` | `claim-case-multilingual-no-backfill` |
| `question-case-role-reset-overview` | `claim-case-role-reset-cache-interference-problem`, `claim-case-role-reset-controlled-flow`, `claim-case-role-reset-confirmation-safety`, `claim-case-role-reset-acceptance`, `claim-case-role-reset-documented-delivery` | empty |
| `question-case-codegraph-overview` | `claim-case-codegraph-narrowing`, `claim-case-codegraph-combined-workflow`, `claim-case-codegraph-evaluation-method` | `claim-case-codegraph-failure-boundary`, `claim-case-codegraph-manual-quality-review`, `claim-case-codegraph-qualitative-publication` |
| `question-sql-audit-async-and-recovery` | `claim-sql-audit-async-task-lifecycle`, `claim-sql-audit-progress-fallback` | empty |
| `question-sql-audit-progress-fallback` | `claim-sql-audit-progress-fallback` | `claim-sql-audit-async-task-lifecycle` |
| `question-sql-audit-archive-and-truncation` | `claim-sql-audit-result-lifecycle`, `claim-sql-audit-truncation-disclosure` | empty |
| `question-case-multilingual-verification-sequence` | `claim-case-multilingual-preserve-existing`, `claim-case-multilingual-sequential-verification` | empty |
| `question-case-multilingual-recovery-boundary` | `claim-case-multilingual-no-backfill` | `claim-case-multilingual-preserve-existing` |
| `question-case-role-reset-acceptance-result` | `claim-case-role-reset-controlled-flow`, `claim-case-role-reset-acceptance` | `claim-case-role-reset-documented-delivery` |
| `question-case-role-reset-safety-boundary` | `claim-case-role-reset-confirmation-safety` | `claim-case-role-reset-controlled-flow` |
| `question-case-codegraph-method` | `claim-case-codegraph-evaluation-method` | `claim-case-codegraph-narrowing`, `claim-case-codegraph-combined-workflow` |
| `question-case-codegraph-quality-boundary` | `claim-case-codegraph-manual-quality-review`, `claim-case-codegraph-qualitative-publication` | `claim-case-codegraph-failure-boundary` |

`question-public-assets-overview` 是跨多主体问题，第一版明确设置：

```json
"requiredClaimIds": [],
"supportingClaimIds": [],
"evidenceRequirement": {
  "minimumApprovedEvidencePerRequiredClaim": 1,
  "publicOnly": true
},
"contractStatus": "DRAFT"
```

它不进入推荐区；用户手输该文本时按自由问题处理。

兼容资源 `backend/src/main/resources/public-data/public-portfolio.v1.json` 中的 `sql-audit-overview` 使用与 bundle 相同的 Required/Supporting Claim、Evidence Requirement 和 `ACTIVE` 状态，避免 packaged legacy profile 与 bundle 产生两种 Preset 语义。

- [ ] **Step 6: 运行发布验证确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest,QuestionNormalizerTest,PublicBundleVerificationCliTest test
```

Expected: PASS，发布结果只包含 15 个 Active 单主体 Contract。

- [ ] **Step 7: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/common/text backend/src/main/java/com/portfolio/agent/answer/engine/QuestionNormalizer.java backend/src/main/java/com/portfolio/agent/portfolio/validation backend/src/main/resources/public-data backend/src/test/java/com/portfolio/agent/portfolio/validation backend/src/test/java/com/portfolio/agent/answer/engine backend/src/test/java/com/portfolio/agent/release/PublicBundleVerificationCliTest.java
git commit -m "feat(content): 为正式推荐问题增加发布契约门禁"
```

---

### Task 3: 传递 Contract 身份并解析显式 ContractTask

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerQuestion.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioContractTask.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTurn.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolutionType.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolution.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolver.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolverTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`

**Interfaces:**
- Consumes: Active `QuestionDefinition` Contract。
- Produces: `PortfolioTurn.getContractVersion()`。
- Produces: `PortfolioContractTask(presetId, contractVersion, canonicalQuestion, subjectId, requiredClaimIds, supportingClaimIds, minimumEvidence)`。
- Produces: `PortfolioPresetResolutionType { MATCHED, NO_MATCH, INVALID, STALE, UNAVAILABLE }`。

- [ ] **Step 1: 写身份优先级失败测试**

```java
@Test
void explicitPresetRequiresMatchingContractVersion() {
    PortfolioTurn turn = PortfolioTurn.builder("turn-1", "How is async state restored?")
            .questionPresetId("preset-async")
            .contractVersion("pcv1-0000000000000000")
            .build();

    PortfolioPresetResolution resolution = resolver.resolve(turn, content());

    assertThat(resolution.getType()).isEqualTo(PortfolioPresetResolutionType.STALE);
    assertThat(resolution.getLatestContractVersion())
            .isEqualTo(content().getProjects().getFirst().getQuestions().getFirst()
                    .getContractVersion());
}

@Test
void manualTextDoesNotMatchInactiveContract() {
    PortfolioPresetResolution resolution = resolver.resolve(
            PortfolioTurn.builder("turn-1", "Draft canonical").build(), contentWithDraft());

    assertThat(resolution.getType()).isEqualTo(PortfolioPresetResolutionType.NO_MATCH);
}
```

- [ ] **Step 2: 运行 Resolver 与 Request 测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationAnswerRequestTest,PortfolioPresetResolverTest,LocalPortfolioKnowledgeAdapterTest test
```

Expected: FAIL，Contract Version 和 ContractTask 尚不存在。

- [ ] **Step 3: 扩展运行时 AnswerQuestion 和映射**

`AnswerQuestion` 增加不可变字段：

```java
private final String contractVersion;
private final List<String> requiredClaimIds;
private final List<String> supportingClaimIds;
private final int minimumApprovedEvidencePerRequiredClaim;
private final PresetContractStatus contractStatus;
```

`LocalPortfolioKnowledgeAdapter` 映射全部状态，便于显式旧 ID 返回 `UNAVAILABLE`；`PortfolioPresetResolver` 的无 ID 文本匹配只过滤 `ACTIVE`。

- [ ] **Step 4: 新增显式 PortfolioContractTask**

```java
public final class PortfolioContractTask {
    private final String presetId;
    private final String contractVersion;
    private final String canonicalQuestion;
    private final String subjectId;
    private final List<String> requiredClaimIds;
    private final List<String> supportingClaimIds;
    private final int minimumApprovedEvidencePerRequiredClaim;
    // constructor validates nonblank IDs, unique lists and disjoint sets
}
```

不要把 Contract 塞回带大量可选字段的普通 `PortfolioTask`。

- [ ] **Step 5: 扩展 API Request、PortfolioTurn 和 Runtime 映射**

`ConversationAnswerRequest.contractVersion` 使用：

```java
@Pattern(regexp = "pcv1-[a-f0-9]{16}", message = "contractVersion format is invalid")
```

JSON 字段是 `contractVersion`。`ConversationalAgentRuntime.portfolioTurn` 同时传递 `questionPresetId` 和 `contractVersion`。普通自由输入两个字段都为 null。

- [ ] **Step 6: 实现 Resolver 优先级**

```text
显式 ID 不存在或文本/主体不一致 -> INVALID
显式 ID 存在但状态非 ACTIVE -> UNAVAILABLE
显式 ID ACTIVE 但版本不一致 -> STALE，并返回 latestContractVersion
显式 ID/版本/文本/主体一致 -> MATCHED ContractTask
无 ID 且唯一命中 ACTIVE canonical/alias -> MATCHED ContractTask
无 ID 未命中 -> NO_MATCH
```

显式调用不得在 `STALE/UNAVAILABLE/INVALID` 后继续调用自由检索。

- [ ] **Step 7: 运行测试确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationAnswerRequestTest,PortfolioPresetResolverTest,LocalPortfolioKnowledgeAdapterTest test
```

Expected: PASS。

- [ ] **Step 8: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "feat(agent): 解析带版本的推荐问题契约任务"
```

---

### Task 4: 为 Bundle 与 PostgreSQL 增加共享的精确 Contract 取证

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalStrategy.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetriever.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQuery.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalRequestTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetrieverTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQueryTest.java`

**Interfaces:**
- Produces: `PortfolioRetrievalStrategy.PRESET_CONTRACT`。
- Produces: `PortfolioRetrievalRequest.contractScope(query, subjectId, claimIds)`，固定 `exactPortfolioLookup=true`、空 preferred categories。
- Preserves: 现有 `PortfolioRetriever.retrieve(PortfolioRetrievalRequest)` Port。

- [ ] **Step 1: 写 Bundle 不调用相关性引擎的失败测试**

```java
@Test
void presetContractReturnsExactClaimsWithoutCallingCoordinator() {
    LocalRetrievalCoordinator forbiddenCoordinator =
            new LocalRetrievalCoordinator(null, null, null, null, null, null);
    BundlePortfolioRetriever retriever = retriever(forbiddenCoordinator, content());

    PortfolioRetrievalResult result = retriever.retrieve(
            PortfolioRetrievalRequest.contractScope(
                    "canonical", "project-a", List.of("claim-a", "claim-b")));

    assertThat(result.getPassages())
            .extracting(PortfolioRetrievedPassage::getClaimId)
            .containsExactlyInAnyOrder("claim-a", "claim-b");
}
```

`LocalRetrievalCoordinator` 是 final；这里故意传入空依赖。Contract 精确分支不调用 coordinator 时测试正常通过，任何意外调用都会立即产生 `NullPointerException` 并使测试失败。

- [ ] **Step 2: 写 PostgreSQL 精确分支失败测试**

在 `JdbcPostgresKnowledgeQueryTest` 断言：

```java
verify(passageQuery).findPassages("release-1", List.of("project-a"));
verify(passageQuery, never()).findRelevantPassages(
        anyString(), anyList(), anyString(), anyList(), anyInt());
```

并断言结果只保留请求中的 Claim ID。

- [ ] **Step 3: 运行适配器测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioRetrievalRequestTest,BundlePortfolioRetrieverTest,JdbcPostgresKnowledgeQueryTest test
```

Expected: FAIL，`PRESET_CONTRACT` 和 `contractScope` 不存在。

- [ ] **Step 4: 实现 Contract Request 工厂**

```java
public static PortfolioRetrievalRequest contractScope(
        String query, String subjectId, List<String> claimIds) {
    validateUniqueNonBlank(List.of(subjectId), "subjectIds");
    validateUniqueNonBlank(claimIds, "claimIds");
    if (claimIds.isEmpty()) {
        throw new IllegalArgumentException("claimIds are required");
    }
    return new PortfolioRetrievalRequest(
            query, PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty(),
            MAX_LIMIT, List.of(subjectId), claimIds, true,
            PortfolioRetrievalStrategy.PRESET_CONTRACT, List.of());
}
```

Limit 必须至少容纳 Contract 中全部 Required + Supporting Claim；若现有 `MAX_LIMIT` 太小，将 Contract 请求的 limit 定义为 claimIds 数量并把通用上限调整为公开 Claim 总量的安全上限，不截断 Contract。

- [ ] **Step 5: 复用 exactPassages/retrieveExact**

Bundle 精确条件改为：

```java
if (request.getStrategy() == PortfolioRetrievalStrategy.CONTEXT_VALIDATION
        || request.getStrategy() == PortfolioRetrievalStrategy.REFERENCE_SCOPED
        || request.getStrategy() == PortfolioRetrievalStrategy.PRESET_CONTRACT) {
    // existing exactPassages path
}
```

PostgreSQL `retrieveExact` 对 `PRESET_CONTRACT` 使用 `findPassages` 后按请求 Claim ID 过滤；不调用 `findRelevantPassages`，不读取 Embedding。

- [ ] **Step 6: 运行适配器测试确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioRetrievalRequestTest,BundlePortfolioRetrieverTest,JdbcPostgresKnowledgeQueryTest test
```

Expected: PASS；Contract 模式下两个适配器都返回相同 Claim 集合且零相关性调用。

- [ ] **Step 7: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence backend/src/test/java/com/portfolio/agent/answer/intelligence
git commit -m "feat(retrieval): 增加推荐契约精确取证策略"
```

---

### Task 5: 引入类型化 Evidence Selection 并修正 Decision 语义

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/EvidenceSelectionStatus.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioEvidenceSelection.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/ContractEvidenceSelector.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RelevanceEvidenceSelector.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDisposition.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioIntelligenceResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/ContractEvidenceSelectorTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/RelevanceEvidenceSelectorTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java`

**Interfaces:**
- Produces: `EvidenceSelectionStatus { SUFFICIENT, AMBIGUOUS, INSUFFICIENT, OUT_OF_SCOPE, INVALID_INPUT, CONTRACT_STALE, CONTRACT_UNAVAILABLE, CONTRACT_INVALID }`。
- Produces: `PortfolioEvidenceSelection`，成功时携带统一 Retrieval material，失败时携带非敏感 failure code。
- Preserves: `PortfolioIntelligence.tryResolve(PortfolioTurn) -> PortfolioDecision` 是唯一外部业务接口。

- [ ] **Step 1: 写 Contract 完整覆盖和失败归属测试**

```java
@Test
void contractIsSufficientOnlyWhenEveryRequiredClaimHasApprovedEvidence() {
    PortfolioContractTask task = contractTask(
            List.of("claim-a", "claim-b"), List.of("claim-c"));
    when(retriever.retrieve(any())).thenReturn(retrievalWithClaims("claim-a", "claim-c"));

    PortfolioEvidenceSelection selection = selector.select(task);

    assertThat(selection.getStatus())
            .isEqualTo(EvidenceSelectionStatus.CONTRACT_UNAVAILABLE);
    assertThat(selection.getFailureCode()).isEqualTo("CONTRACT_REQUIRED_CLAIM_MISSING");
}
```

再增加：Supporting Claim 缺失仍 `SUFFICIENT`；Required Claim Evidence 非 APPROVED 时 `CONTRACT_UNAVAILABLE`；全部覆盖时 `SUFFICIENT`。

- [ ] **Step 2: 写自由检索失败语义测试**

```java
@Test
void preservesAmbiguousInsteadOfCollapsingToNotSupported() {
    PortfolioEvidenceSelection selection = selector.fromRetrieval(
            retrievalWithStatus(RetrievalDecisionType.AMBIGUOUS));

    assertThat(selection.getStatus()).isEqualTo(EvidenceSelectionStatus.AMBIGUOUS);
}
```

覆盖 `INSUFFICIENT`、`OUT_OF_SCOPE` 和 Retriever 异常；基础设施异常继续抛出 `PortfolioRetrievalFailedException`，不能变成证据不足。

- [ ] **Step 3: 运行 Selector 测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ContractEvidenceSelectorTest,RelevanceEvidenceSelectorTest,DefaultPortfolioIntelligenceRoutingTest test
```

Expected: FAIL，Selector 类型不存在且 Preset 仍走普通 `execute`。

- [ ] **Step 4: 实现统一 Selection 值对象**

`PortfolioEvidenceSelection` 必须验证：

```text
SUFFICIENT -> retrieval material 非空
非 SUFFICIENT -> failureCode 非空
Contract status 不能携带 relevance ambiguity candidates
selected Claim/Evidence IDs 从 Retrieval material 派生，调用方不能自行伪造
```

不要通过 `evidence.isEmpty()` 在 Decision 层重新推断状态。

- [ ] **Step 5: 实现 ContractEvidenceSelector**

```java
List<String> requestedClaimIds = Stream.concat(
        task.getRequiredClaimIds().stream(),
        task.getSupportingClaimIds().stream()).distinct().toList();
PortfolioRetrievalResult retrieval = retriever.retrieve(
        PortfolioRetrievalRequest.contractScope(
                task.getCanonicalQuestion(), task.getSubjectId(), requestedClaimIds));
Set<String> returnedRequired = retrieval.getPassages().stream()
        .map(PortfolioRetrievedPassage::getClaimId)
        .filter(task.getRequiredClaimIds()::contains)
        .collect(Collectors.toUnmodifiableSet());
```

然后逐 Claim 统计 `APPROVED` Evidence Reference；任何 Required Claim 不足返回 `CONTRACT_UNAVAILABLE`，不调用 Relevance Selector。

- [ ] **Step 6: 让 DefaultPortfolioIntelligence 显式分流**

```text
MATCHED -> ContractEvidenceSelector -> PortfolioDecision
STALE -> CONTRACT_STALE，携带 latestContractVersion
UNAVAILABLE -> CONTRACT_UNAVAILABLE
INVALID -> CONTRACT_INVALID
NO_MATCH -> 原规则/模型 Task Resolver -> RelevanceEvidenceSelector
REFERENCE -> 原精确 reference 约束；已有事实不重新全局召回
```

`AMBIGUOUS` 映射 `NEEDS_CLARIFICATION`；`INSUFFICIENT/OUT_OF_SCOPE` 分别携带 `RELEVANCE_INSUFFICIENT/RELEVANCE_OUT_OF_SCOPE` notice code；Contract 三种失败使用独立 notice code。

- [ ] **Step 7: 运行 Selector 与 PI 路由测试确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ContractEvidenceSelectorTest,RelevanceEvidenceSelectorTest,DefaultPortfolioIntelligenceRoutingTest,DefaultPortfolioIntelligenceTest test
```

Expected: PASS；Preset 命中时 Mockito 验证 Relevance Selector 零调用。

- [ ] **Step 8: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence backend/src/test/java/com/portfolio/agent/answer/intelligence
git commit -m "refactor(agent): 用类型化取证结果区分失败语义"
```

---

### Task 6: 用不可变 FactBundle 收紧 Composer 和引用边界

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioFact.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioFactEvidence.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioFactBundle.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioFactBundleBuilder.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioCitationValidation.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioCitationValidator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioAnswerComposer.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/exception/PortfolioAnswerCompositionException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioFactBundleBuilderTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioCitationValidatorTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java`

**Interfaces:**
- Consumes: `PortfolioFactBundleBuilder.build(PortfolioDecision)`；Decision material 内携带 `PortfolioEvidenceSelection` 和 Contract Required IDs。
- Produces: `PortfolioFactBundle`，包含 task identity、snapshot version、subjects、facts、evidence、required IDs 和 selection status。
- Produces: `PortfolioAnswerComposer.compose(bundle)` 与 `repair(bundle, rejectedBlocks, validation)`；Composer 不依赖任何 Repository/Retriever。

- [ ] **Step 1: 写 Citation 双向校验失败测试**

```java
@Test
void rejectsUnknownEvidenceAndMissingRequiredClaim() {
    PortfolioFactBundle bundle = bundleWithRequiredClaims("claim-a", "claim-b");
    List<ConversationAnswerBlock> blocks = List.of(new ConversationAnswerBlock(
            ConversationSourceScope.PORTFOLIO,
            "A",
            List.of("claim-a"),
            List.of("evidence-not-in-bundle")));

    PortfolioCitationValidation validation = validator.validate(bundle, blocks);

    assertThat(validation.isValid()).isFalse();
    assertThat(validation.getCodes()).containsExactlyInAnyOrder(
            "UNKNOWN_EVIDENCE", "REQUIRED_CLAIM_NOT_COVERED");
}
```

- [ ] **Step 2: 写 Assembler 单次修复测试**

使用 fake Composer：第一次返回缺引用 block，`repair` 返回完整 block；断言 `compose` 和 `repair` 各一次。再写第二次仍无效时抛出 `PortfolioAnswerCompositionException`，不返回 `NOT_SUPPORTED`。

- [ ] **Step 3: 运行 FactBundle/Assembler 测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioFactBundleBuilderTest,PortfolioCitationValidatorTest,PortfolioIntelligenceAnswerAssemblerTest test
```

Expected: FAIL，新边界不存在。

- [ ] **Step 4: 实现不可变 FactBundle**

```java
public final class PortfolioFactBundle {
    private final String taskKind;
    private final String questionPresetId;
    private final String contractVersion;
    private final String contentSnapshotVersion;
    private final List<String> subjectIds;
    private final List<String> requiredClaimIds;
    private final List<PortfolioFact> facts;
    private final List<PortfolioFactEvidence> evidence;
    // defensive copies; no repository/retriever references
}
```

`PortfolioFactEvidence` 必须明确 `claimId`，让 Validator 能验证 Evidence 与 Claim 的关系，而不是只验证 Evidence ID 存在。

- [ ] **Step 5: 实现确定性 Composer**

`DeterministicPortfolioAnswerComposer` 按 Required Claim 声明顺序输出，再输出可用 Supporting Claim；每个 block 只使用该 Claim 在 FactBundle 中登记的 Evidence IDs。`repair` 丢弃 rejected blocks，从原 FactBundle 重新构造，不新增任何事实。

- [ ] **Step 6: 重构 Assembler 编排**

```java
PortfolioFactBundle bundle = factBundleBuilder.build(decision);
List<ConversationAnswerBlock> blocks = composer.compose(bundle);
PortfolioCitationValidation validation = citationValidator.validate(bundle, blocks);
if (!validation.isValid()) {
    blocks = composer.repair(bundle, blocks, validation);
    validation = citationValidator.validate(bundle, blocks);
}
if (!validation.isValid()) {
    throw new PortfolioAnswerCompositionException(
            "portfolio citation validation failed after one repair");
}
```

失败异常由现有全局安全异常边界转换为通用能力暂不可用；不得输出 Contract/Claim/Evidence 内部细节。

- [ ] **Step 7: 运行测试确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioFactBundleBuilderTest,PortfolioCitationValidatorTest,PortfolioIntelligenceAnswerAssemblerTest,ConversationalAgentRuntimeTest test
```

Expected: PASS；测试同时验证 Composer 构造函数没有 Retriever/Repository 依赖。

- [ ] **Step 8: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "refactor(answer): 用事实包和引用校验约束答案构造"
```

---

### Task 7: 扩展公开 API 和前端 Contract Version 交互

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/QuestionPresetResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/AnswerControllerTest.java`
- Modify: `frontend/src/features/public-content/model/publicContentTypes.ts`
- Modify: `frontend/src/features/public-content/data/previewPublicContent.ts`
- Modify: `frontend/src/features/agent/api/answerApi.ts`
- Modify: `frontend/src/features/agent/api/answerApi.test.ts`
- Create: `frontend/src/features/agent/api/presetContractRetry.ts`
- Create: `frontend/src/features/agent/api/presetContractRetry.test.ts`
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Public Preset DTO: `id`, `text`, existing scopes, `contractVersion`, `availability: 'ACTIVE'`。
- Answer Request: `contractVersion?: string`。
- Answer Response: `questionPresetId?: string`, `contractVersion?: string`, existing `noticeCode`。
- Stale protocol: `resolution=CAPABILITY_UNAVAILABLE`, `noticeCode=PRESET_CONTRACT_STALE`, response carries latest ID/version。

- [ ] **Step 1: 写后端 DTO/API 失败测试**

```java
assertThat(response.getQuestionPresets())
        .extracting(QuestionPresetResponse::getId)
        .doesNotContain("question-public-assets-overview");
assertThat(response.getQuestionPresets().getFirst().getContractVersion())
        .matches("pcv1-[a-f0-9]{16}");
assertThat(response.getQuestionPresets().getFirst().getAvailability())
        .isEqualTo("ACTIVE");
```

在 `AnswerControllerTest` 增加 stale 请求，断言 response JSON 包含最新 `questionPresetId`、`contractVersion` 和 `PRESET_CONTRACT_STALE`。

- [ ] **Step 2: 写前端序列化和单次重试失败测试**

```ts
expect(JSON.parse(fetchMock.mock.calls[0][1]!.body as string)).toMatchObject({
  questionPresetId: 'sql-audit-overview',
  contractVersion: 'pcv1-0123456789abcdef',
})
```

```ts
it('retries a stale preset exactly once with the server version', async () => {
  const send = vi.fn()
    .mockResolvedValueOnce({
      resolution: 'CAPABILITY_UNAVAILABLE',
      noticeCode: 'PRESET_CONTRACT_STALE',
      questionPresetId: 'preset-a',
      contractVersion: 'pcv1-1111111111111111',
    })
    .mockResolvedValueOnce({ resolution: 'ANSWERED' })

  await askWithPresetContractRetry(request, send)

  expect(send).toHaveBeenCalledTimes(2)
  expect(send.mock.calls[1][0].contractVersion)
    .toBe('pcv1-1111111111111111')
})
```

- [ ] **Step 3: 运行后端与前端测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioResponseMapperTest,AnswerControllerTest test
npm.cmd --prefix frontend test -- --run src/features/agent/api/answerApi.test.ts src/features/agent/api/presetContractRetry.test.ts src/features/agent/components/AgentWorkspace.test.ts
```

Expected: FAIL，版本字段、过滤和重试 helper 尚不存在。

- [ ] **Step 4: 只公开 ACTIVE Preset 和安全元数据**

`PortfolioResponseMapper` 在映射前过滤 `contractStatus == ACTIVE`。`QuestionPresetResponse` 只新增：

```java
private final String contractVersion;
private final String availability;
```

不暴露 Required Claim、Supporting Claim、Evidence Requirement 或 Evidence ID。

- [ ] **Step 5: 扩展请求和响应身份**

前端：

```ts
export interface QuestionPreset {
  id: string
  projectSlug: string
  text: string
  audiences: AudienceRole[]
  placements: Array<'HOME' | 'AGENT' | 'PROJECT'>
  contractVersion: string
  availability: 'ACTIVE'
}
```

`AnswerApiRequest` 和 `AnswerRequestContext` 增加 `contractVersion`；只有点击 Active Preset 时发送。自由输入和普通 suggestion 不继承旧 Preset 身份。

- [ ] **Step 6: 实现一次陈旧版本重试和内存版本覆盖**

`askWithPresetContractRetry` 最多调用 transport 两次；第二次仍 stale 时直接返回 stale response。`AgentWorkspace` 使用标签页内存 Map：

```ts
const resolvedContractVersions = new Map<string, string>()
```

服务器返回新版本后更新 Map，后续点击同一 Preset 使用新版本；不写 localStorage/sessionStorage/URL/history。

- [ ] **Step 7: 实现准确用户文案**

```text
PRESET_CONTRACT_STALE（二次失败） -> 这个推荐问题正在更新，请刷新后重试。
PRESET_CONTRACT_UNAVAILABLE -> 这个推荐问题暂时无法回答，内容正在更新。
RELEVANCE_AMBIGUOUS -> 展示后端给出的具体澄清问题。
RELEVANCE_INSUFFICIENT -> 说明已确认部分和缺失证据。
RELEVANCE_OUT_OF_SCOPE -> 当前作品集没有覆盖该信息范围。
```

Contract 错误不得使用“公开资料不足”。

- [ ] **Step 8: 运行后端和前端测试确认 GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioResponseMapperTest,AnswerControllerTest test
npm.cmd --prefix frontend test -- --run src/features/agent/api/answerApi.test.ts src/features/agent/api/presetContractRetry.test.ts src/features/agent/components/AgentWorkspace.test.ts
```

Expected: PASS；测试断言 stale 最多重试一次，自由问题请求无 ID/version。

- [ ] **Step 9: 经授权后提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/portfolio backend/src/test/java/com/portfolio/agent/answer frontend/src
git commit -m "feat(frontend): 支持推荐契约版本和准确失败交互"
```

---

### Task 8: 增加隐私安全诊断、端到端矩阵和权威文档

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioIntelligenceResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioContractAdapterParityTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/release/RetrievalBenchmarkTest.java`
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/09-作品集资产库状态.md`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Consumes: `PortfolioIntelligenceResult` 的 task kind、selection policy、failure code、required/selected counts。
- Produces: `portfolio.intelligence.completed` 的非敏感诊断维度。
- Produces: Active Contract 跨 Bundle/PostgreSQL/Embedding 模式验收矩阵。

- [ ] **Step 1: 写诊断隐私失败测试**

```java
assertThat(event.getFields()).containsEntry("selection.policy", "PRESET_CONTRACT");
assertThat(event.getFields()).containsEntry("required.claim.count", 2);
assertThat(event.getFields()).containsEntry("selected.claim.count", 2);
assertThat(event.getFields()).doesNotContainKeys(
        "question", "answer", "evidence.content", "local.path");
```

- [ ] **Step 2: 写适配器一致性和模式矩阵失败测试**

`PortfolioContractAdapterParityTest` 对每个 Active Preset：

```java
assertThat(bundleResult.getPassages())
        .extracting(PortfolioRetrievedPassage::getClaimId)
        .containsAll(preset.getRequiredClaimIds());
assertThat(postgresResult.getPassages())
        .extracting(PortfolioRetrievedPassage::getClaimId)
        .containsExactlyInAnyOrderElementsOf(
                bundleResult.getPassages().stream()
                        .map(PortfolioRetrievedPassage::getClaimId)
                        .toList());
```

`RetrievalBenchmarkTest` 分别在 Embedding Disabled、Keyword Only、Hybrid 和 PostgreSQL failover 情况下断言 Required Claim 集合相同。

- [ ] **Step 3: 写 Playwright 用户旅程失败测试**

覆盖以下真实请求：

```text
点击 sql-audit-overview -> request 有 ID/version -> ANSWERED 且有 Required Claim 引用
手输 canonical/alias -> 同一 Preset 身份和 Claim 集合
旧 version -> 自动重试一次 -> ANSWERED
连续 stale -> 显示“正在更新”，不显示“公开资料不足”
自由歧义问题 -> 显示具体澄清候选
自由证据不足 -> 显示缺少的证据范围
已有事实追问 -> referenceContext 带 selected Claim IDs
同主体新问题 -> 不携带旧 presetId，主体 context 保留
```

- [ ] **Step 4: 运行新增测试确认 RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationalAgentRuntimeTest,PortfolioContractAdapterParityTest,RetrievalBenchmarkTest test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run test:e2e -- portfolio.spec.ts
```

Expected: 新诊断字段、模式矩阵或用户旅程至少一项失败。

- [ ] **Step 5: 扩展非敏感诊断字段**

在现有 `publishPortfolioIntelligence` 追加：

```java
.field("task.kind", result.getTaskKind())
.field("selection.policy", result.getSelectionPolicy())
.field("preset.id", result.getQuestionPresetId())
.field("contract.version", result.getContractVersion())
.field("failure.stage", result.getFailureStage())
.field("failure.code", result.getFailureCode())
.field("required.claim.count", result.getRequiredClaimCount())
.field("selected.claim.count", result.getSelectedClaimCount())
.field("citation.validation", result.getCitationValidationResult())
```

Builder 对 null 字段沿用现有安全策略；禁止加入原始 question、answer 或 Evidence 内容。

- [ ] **Step 6: 完成 E2E mocks 和全矩阵验收**

Mock 返回 Active Preset version、stale latest version 和独立 notice codes。确保 E2E 断言请求体中自由输入没有 `questionPresetId`/`contractVersion`，Preset 请求二者齐全。

- [ ] **Step 7: 更新权威状态与演进文档**

文档只记录：

```text
正式推荐问题已升级为强 Contract；
15 个单主体 Preset Active，跨主体 public-assets Preset 保持 Draft；
Preset 使用确定性 Claim 取证，自由问题使用 Relevance；
Contract 失败不再映射为公开资料不足；
当前实现已通过 Bundle/PostgreSQL 与运行模式矩阵。
```

不要在演进日志记录逐步实现过程、测试清单或 commit metadata。

- [ ] **Step 8: 运行完整后端、前端、构建和隐私验证**

Run:

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml package
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

Expected:

```text
Backend: BUILD SUCCESS, 0 failures
Frontend Vitest: all tests passed
Frontend build: exit code 0
Package: BUILD SUCCESS
Privacy check: exit code 0, no private-path/raw-content findings
```

- [ ] **Step 9: 经授权后提交**

```powershell
git add backend/src frontend/e2e "docs/08-当前实现状态.md" "docs/09-作品集资产库状态.md" "docs/11-项目演进日志.md"
git commit -m "test(agent): 完成推荐契约全链路验收"
```

---

## Final acceptance checklist

- [ ] 所有公开推荐项都是 `ACTIVE` Contract，并携带稳定 `contractVersion`。
- [ ] `question-public-assets-overview` 保持 Draft 且不出现在正式推荐区。
- [ ] 点击 Preset 和手输 Active canonical/alias 得到相同 Preset 身份、主体和 Required Claim 集合。
- [ ] Preset 在 Embedding Disabled、Keyword Only、Hybrid 和 PostgreSQL failover 下事实集合一致。
- [ ] Bundle 与 PostgreSQL 的 Contract 结果 Required Claim 集合一致。
- [ ] Preset 路径没有调用 BM25、向量召回或相关性阈值。
- [ ] Contract Required Claim/Evidence 缺失时返回内容/系统异常，不返回 `AMBIGUOUS` 或普通 `INSUFFICIENT`。
- [ ] 自由问题保留 `AMBIGUOUS/INSUFFICIENT/OUT_OF_SCOPE/INVALID_INPUT` 的准确语义。
- [ ] 已有事实追问使用 Reference Scope；同主体新问题使用 Subject Scoped Relevance。
- [ ] Composer 只消费不可变 FactBundle，不能访问 Repository/Retriever。
- [ ] Citation Validator 正向校验引用、反向校验 Required Claim 覆盖；最多修复一次。
- [ ] 陈旧 Contract 自动重试一次，连续失败显示更新提示。
- [ ] 自由问题请求不继承上一次 Preset ID/version。
- [ ] 诊断不包含访客问题、答案、Evidence 原文、私有路径或内部环境。
- [ ] 后端、前端、Playwright、构建、打包和隐私检查全部通过。
