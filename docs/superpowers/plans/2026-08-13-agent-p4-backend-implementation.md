# Agent P4.1 Backend Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with explicit RED/GREEN checkpoints. The project owner has requested that Superpowers not be used for implementation; do not invoke Superpowers skills while executing this plan.

**Goal:** 在不改变 P2 语义决策与 P3 证据结论的前提下，实现默认关闭、单次调用、严格校验、原子回退的 P4.1 单主体 Fact 模型表达链，并把任务级 composition 状态安全传播到公共 API。

**Architecture:** 将 `PortfolioAnswerMaterial -> PortfolioCompositionResult` 建成唯一深模块 seam。模块先构造并校验完整确定性 Plan，再依据闭集 Intent、回合 Allowance、配置和 deadline 决定是否调用独立 `PortfolioExpressionPort`；任何 Provider、Codec、Grounding 或 Plan 失败都原子返回预构造 Plan。P2 Synthesis 始终只消费 Material 的模型前 `GroundedAnswerContribution`。

**Tech Stack:** Java 21、Spring Boot 3.5.3、Jackson、JDK `HttpClient`、JUnit 5、AssertJ、Maven 3.9.9、PowerShell 发布门禁。

## Global Constraints

- 权威设计：`docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md`。
- 上游基线：P0/P1/P2/P3 已实现；本计划不重新设计 P3，也不恢复已删除旧链。
- 不修改 `frontend/**`。前端实现交给独立 Agent；后端仅提供公共 DTO、Mapper 与契约测试。
- 不发送原问题、历史、`goalLabel`、Token、Context、内部 ID、route、reference key、Evidence/Chunk 正文或检索分数。
- 不引入第二模型 Judge、Embedding 语义判定、模型自修复、重试、跨 Provider fallback、Streaming 或 Agent loop。
- Java 生产/测试代码禁止 `var`、`record`、Lombok；值对象显式不可变、防御复制、校验构造参数并脱敏 `toString()`。
- 严格 TDD：每个任务先看到目标测试 RED，再实现 GREEN；每个任务只暂存该任务列出的文件。
- 默认仓库配置必须保持 `portfolio.model-expression.enabled=false`；普通测试不得访问网络。
- P4.1 build-supported kind 只有 `FACT`。Comparison/Recommendation 类型或 Codec 存在不等于准入完成。
- 真实 Provider 验收必须单独获得明确授权；未运行时报告 `REAL_PROVIDER_INCOMPLETE`，不能写成 PASS。
- 本计划中的 commit 命令以“P3 基线已单独提交”为前提。若执行开始时 P3 仍未提交，先停止并取得用户对 P3 提交/隔离方式的授权。

---

## 1. Authority, baseline and execution precondition

执行前完整阅读：

1. `AGENTS.md`
2. `docs/04-项目代码约束.md`
3. `docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md`
4. `docs/superpowers/specs/2026-08-11-bounded-tool-orchestration-design.md`
5. `docs/superpowers/specs/2026-08-10-semantic-turn-routing-design.md`
6. `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
7. `docs/handoffs/2026-08-13-agent-p4-frontend-contract-handoff.md`

冲突优先级：P4 Spec → P3/P2 Spec → 本 Plan → 旧状态文档。发现 P4 Spec 无法按当前代码实现时，停止对应任务并先修订 Spec；不得用代码静默改变语义。

### Baseline commands

```powershell
git status --short
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

Expected:

- 执行 P4 前，P3 后端与前端基线已成为可识别 commit；工作树只允许存在已知、与本任务不重叠的用户改动。
- 后端全量测试 PASS。
- 隐私门禁 PASS。
- 若基线失败，先记录失败属于 P3 基线还是环境，不在 P4 commit 中夹带修复。

---

## 2. Target file map

### 2.1 New deep module

根包：`backend/src/main/java/com/portfolio/agent/answer/composition`

```text
domain/
  PortfolioAnswerMaterial.java
  FactAnswerMaterial.java
  ComparisonAnswerMaterial.java
  RecommendationAnswerMaterial.java
  GroundedStatement.java
  ExpressionStatement.java
  PortfolioCompositionContext.java
  ExpressionIntent.java
  ExpressionAllowance.java
  PortfolioCompositionResult.java
  CompositionMode.java
  ExpressionDisposition.java
  ModelExpressionRequest.java
  ModelExpressionResult.java
  ModelExpressionDeadline.java
  ModelExpressionDraft.java
  FactExpressionDraft.java
  ComparisonExpressionDraft.java
  RecommendationExpressionDraft.java

service/
  PortfolioAnswerComposition.java
  DeterministicPortfolioAnswerComposer.java
  PortfolioAnswerPlanValidator.java
  ModelExpressionEligibilityPolicy.java
  ExpressionCircuitBreaker.java

projection/
  PortfolioCompositionContextFactory.java
  ModelExpressionInputProjector.java
  ExpressionAliasRegistry.java
  ExpressionInputDocument.java

codec/
  PortfolioExpressionDraftCodec.java

validation/
  StatementGroundingValidator.java
  ProtectedAtomExtractor.java
  QualifierPreservationValidator.java
  FactDraftValidator.java

assembly/
  ModelDraftPlanAssembler.java

gateway/
  PortfolioExpressionPort.java

adapter/model/
  OpenAiCompatiblePortfolioExpressionAdapter.java
  PortfolioExpressionPromptFactory.java
  PortfolioExpressionProperties.java
  PortfolioExpressionConfiguration.java
  PortfolioExpressionStartupValidator.java

observability/
  ExpressionDiagnostics.java
```

### 2.2 Existing backend files to evolve

- `answer/domain/GroundedAnswerContribution.java`
- `answer/domain/PortfolioAnswerPlan.java`
- `answer/domain/PortfolioAnswerSection.java`
- `answer/domain/GenerationMode.java`
- `answer/domain/AnswerConstructionMode.java`
- `answer/routing/domain/TaskExecutionAllowance.java`
- `answer/routing/domain/SemanticTaskExecutionContext.java`
- `answer/routing/domain/TaskOutcome.java`
- `answer/routing/domain/TaskComposition.java`
- `answer/routing/domain/SemanticTurnOutcome.java`
- `answer/routing/service/SemanticTurnCoordinator.java`
- `answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutor.java`
- `answer/intelligence/execution/resultpolicy/*ResultPolicy.java`
- `answer/intelligence/adapter/PortfolioExecutionConfiguration.java`
- `answer/dto/response/CompletedTaskResponse.java`
- `answer/mapper/ConversationAnswerResponseMapper.java`
- `answer/service/ConversationalAgentRuntime.java` 或当前唯一顶层 mode 聚合器
- `answer/adapter/model/ModelProviderRegistrySnapshot.java`
- `answer/adapter/model/ModelProviderDescriptor.java`
- `answer/adapter/model/ProviderOperation.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application-prod.yml`
- `.env.example`
- `.env.postgres.example`

### 2.3 Old files removed after migration

- `answer/domain/PortfolioAnswerMaterial.java`
- `answer/domain/GroundedStatement.java`
- `answer/service/PortfolioAnswerComposer.java`
- `answer/service/DeterministicPortfolioAnswerComposer.java`

只在所有调用方迁移后删除；不得保留两套公共 composition seam。

---

## 3. Frozen contracts and limits

```text
input schema          portfolio-expression-input.v1
draft schema          portfolio-expression-draft.v1
policy                p4-expression-policy-v1
build-supported kind  FACT
attempts per turn     1
provider timeout      min(4s, execution deadline - now)
minimum window        1500ms
max statements        16
max serialized input  12000 characters
max output tokens     1600
model content         min(2400, task character limit)
summary               max 300 characters
fact sections         max 6
sentences/section     max 4
sentences total       max 18
supports/sentence     1..4
temperature           0.1
streaming             false
thinking              disabled
retry                  0
```

Composition disposition closed set:

```java
public enum ExpressionDisposition {
    NOT_ATTEMPTED_DISABLED,
    NOT_ATTEMPTED_INELIGIBLE,
    NOT_ATTEMPTED_ALLOWANCE,
    NOT_ATTEMPTED_DEADLINE,
    NOT_ATTEMPTED_INPUT_LIMIT,
    ACCEPTED,
    FALLBACK_CIRCUIT_OPEN,
    FALLBACK_PROVIDER_FAILURE,
    FALLBACK_EMPTY_RESPONSE,
    FALLBACK_SCHEMA_INVALID,
    FALLBACK_GROUNDING_INVALID,
    FALLBACK_PLAN_INVALID
}
```

---

## Task 0: Freeze the P3 baseline and record P4 approval

**Files:**

- Modify: `docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md`
- Modify: `docs/00-文档状态索引.md`
- Test: Git status only

**Interfaces:** Consumes the user-approved P4 Spec; produces an unambiguous `APPROVED / PLANNED / NOT_IMPLEMENTED` documentation state.

- [ ] **Step 1: Inspect the exact P3 baseline.**

  Run `git status --short` and `git log -5 --oneline`. Expect P3 changes to be in a distinct commit before P4 code starts.

- [ ] **Step 2: If P3 is still uncommitted, stop.**

  Do not stage or commit the current mixed tree without explicit authorization. Obtain either a P3 baseline commit or an isolated branch containing P3.

- [ ] **Step 3: Update the status index.**

  Record P4 as `DESIGNED / APPROVED / PLAN_READY`; explicitly record `IMPLEMENTED=false`, `REAL_PROVIDER_INCOMPLETE`, `DEPLOYMENT_ENABLED=false`.

- [ ] **Step 4: Verify no production code changed.**

  Run `git diff --name-only -- backend/src frontend/src`. Expect no new P4 production changes from this task.

- [ ] **Step 5: Commit documentation only after the baseline is safe.**

  ```powershell
  git add docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md docs/00-文档状态索引.md docs/superpowers/plans/2026-08-13-agent-p4-backend-implementation.md
  git commit -m "docs: approve and plan agent p4 backend"
  ```

---

## Task 1: Introduce the rich grounded statement contract

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/GroundedStatement.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ExpressionStatement.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/domain/GroundedStatementTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/domain/ExpressionStatementTest.java`

**Interfaces:** Consumes only P3-approved public claim projections and public source values; produces immutable statement entries safe for deterministic composition and Provider projection.

- [ ] **Step 1: Write RED constructor/invariant tests.**

  Cover every enum and field, at least one source, duplicate source rejection, nonblank published text, stable order, defensive copies, and content-free `toString()`.

  ```java
  GroundedStatement statement = new GroundedStatement(
          StatementType.FACT,
          List.of(subjectReference),
          ControlledPredicate.VERIFIED_BY_TEST,
          "通过公开测试验证核心流程。",
          "验证范围为已发布用例。",
          ClaimCategory.VERIFICATION,
          AchievementStatus.DELIVERED,
          ContributionType.COLLABORATIVE,
          VerificationBasis.EVIDENCE_SUPPORTED,
          Materiality.KEY,
          SupportTarget.SUBJECT,
          List.of(sourceReference));
  assertThat(statement.toString()).doesNotContain("公开测试", "referenceKey");
  ```

- [ ] **Step 2: Run RED.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=GroundedStatementTest,ExpressionStatementTest test
  ```

  Expected: compilation failure because the new domain types do not exist.

- [ ] **Step 3: Implement closed enums and immutable values.**

  `ExpressionStatement` must expose only:

  ```java
  public final class ExpressionStatement {
      private final GroundedStatement statement;
      private final PresentationRole presentationRole;
      private final AnswerSectionType allowedSection;
      private final int stableOrder;
  }
  ```

  Roles are exactly `REQUIRED`, `OPTIONAL`, `CONTEXT`. `stableOrder` is nonnegative and unique within its owning section (the owner validates uniqueness later).

- [ ] **Step 4: Run GREEN and commit.**

  Run the focused command again; expect PASS.

  ```powershell
  git add backend/src/main/java/com/portfolio/agent/answer/composition/domain backend/src/test/java/com/portfolio/agent/answer/composition/domain
  git commit -m "feat: add p4 grounded statement contracts"
  ```

---

## Task 2: Replace flat material with the closed strong hierarchy

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/PortfolioAnswerMaterial.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/FactAnswerMaterial.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ComparisonAnswerMaterial.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/RecommendationAnswerMaterial.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/GroundedAnswerContribution.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/domain/PortfolioAnswerMaterialTest.java`

**Interfaces:** Consumes typed P3 result-policy output; produces both composition material and the immutable pre-model contribution for P2 Synthesis.

- [ ] **Step 1: Write RED tests for all three variants.**

  Assert variant-specific shape/order invariants and that `toGroundedContribution()` is value-equal before and after any model branch. Assert Comparison subject/dimension order and Recommendation candidate/tier/order cannot be mutated.

- [ ] **Step 2: Run RED.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=PortfolioAnswerMaterialTest test
  ```

- [ ] **Step 3: Implement the closed hierarchy.**

  ```java
  public abstract sealed class PortfolioAnswerMaterial
          permits FactAnswerMaterial, ComparisonAnswerMaterial, RecommendationAnswerMaterial {
      public abstract MaterialKind getMaterialKind();
      public abstract String getPublicTitle();
      public abstract List<String> getFixedCaveats();
      public abstract List<String> getOmittedTopicLabels();
      public abstract GroundedAnswerContribution toGroundedContribution();
  }
  ```

  Implement final nested value types for Fact sections, Comparison dimensions/cells, and Recommendation candidates/criteria. No raw question, IDs, route, score or evidence body is accepted by constructors.

- [ ] **Step 4: Fix `GroundedAnswerContribution.hashCode()`.**

  Include `sourceReferences`, matching the existing `equals()` contract. Add a regression assertion.

- [ ] **Step 5: Run GREEN and commit.**

  ```powershell
  git add backend/src/main/java/com/portfolio/agent/answer/composition/domain backend/src/main/java/com/portfolio/agent/answer/domain/GroundedAnswerContribution.java backend/src/test/java/com/portfolio/agent/answer/composition/domain
  git commit -m "refactor: make portfolio answer material strongly typed"
  ```

---

## Task 3: Migrate P3 result policies without changing their decisions

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy/PortfolioResultPolicy.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy/FactResultPolicy.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy/ComparisonResultPolicy.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy/RecommendationResultPolicy.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy/RefineResultPolicy.java`
- Test: existing `*ResultPolicyTest.java`; add `PortfolioResultPolicyMaterialContractTest.java`

**Interfaces:** Consumes the same validated evidence/support assessment as P3; produces strong Material with frozen REQUIRED/OPTIONAL/CONTEXT roles.

- [ ] **Step 1: Add characterization tests before modifying policies.**

  For existing Fact/Compare/Recommend/Refine fixtures, snapshot resolution, evidence support state, candidate order, omitted labels and public references. Add expected roles and section authorization without changing business conclusions.

- [ ] **Step 2: Run characterization tests; expect PASS on old assertions and RED on strong-type assertions.**

- [ ] **Step 3: Migrate policies.**

  Fact must set Summary `REQUIRED` for overview and `FORBIDDEN` for focused. Comparison must carry only P3-produced `controlledRelation`. Recommendation/Refine must preserve candidate order/tier/criterion and `refineSource`; no model-oriented inference belongs here.

- [ ] **Step 4: Prove Synthesis consumes pre-model facts.**

  Replace executor-side ad hoc contribution construction with `material.toGroundedContribution()`. No Draft/Plan type may be imported by deterministic Synthesis.

- [ ] **Step 5: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true "-Dtest=*ResultPolicyTest,PortfolioResultPolicyMaterialContractTest,DeterministicSynthesisTaskExecutorTest" test
  git add backend/src/main/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy backend/src/test/java/com/portfolio/agent/answer/intelligence/execution/resultpolicy backend/src/main/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutor.java
  git commit -m "refactor: project p3 results into strong answer material"
  ```

---

## Task 4: Make the deterministic plan public-reference based

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerSection.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlan.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/service/DeterministicPortfolioAnswerComposer.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerPlanValidator.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/service/DeterministicPortfolioAnswerComposerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerPlanValidatorTest.java`

**Interfaces:** Consumes strong Material; produces a complete legal fallback Plan with fixed caveats/omitted topics and public source values, never internal claim/evidence IDs.

- [ ] **Step 1: Write RED tests for Fact, Comparison and Recommendation deterministic output.**

  Assert exact stable sections, server titles, caveats, omitted labels, source-reference order, character limit, and no internal identifiers.

- [ ] **Step 2: Run RED.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=DeterministicPortfolioAnswerComposerTest,PortfolioAnswerPlanValidatorTest test
  ```

- [ ] **Step 3: Replace section provenance.**

  `PortfolioAnswerSection` should carry `List<PublicSourceReferenceValue> sourceReferences`; remove `claimIds/evidenceIds` from the composition Plan seam. Preserve public DTO compatibility through mappers, not through internal IDs.

- [ ] **Step 4: Implement deterministic strategies behind one final composer.**

  The composer must render all Material variants deterministically even though only Fact is model-eligible. Boundary/caveat text remains server-owned.

- [ ] **Step 5: Run GREEN and commit.**

  ```powershell
  git add backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerSection.java backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlan.java backend/src/main/java/com/portfolio/agent/answer/composition/service backend/src/test/java/com/portfolio/agent/answer/composition/service
  git commit -m "refactor: build deterministic plans from public grounding"
  ```

---

## Task 5: Add composition context, result and deterministic-only seam

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ExpressionIntent.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ExpressionAllowance.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/PortfolioCompositionContext.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/CompositionMode.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ExpressionDisposition.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/PortfolioCompositionResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerComposition.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerCompositionDeterministicTest.java`

**Interfaces:** Consumes strong Material and closed context; produces final Plan plus closed expression status. At this task, all paths are deterministic and no Provider port exists.

- [ ] **Step 1: Write RED API and privacy-shape tests.**

  ```java
  PortfolioCompositionResult result = composition.compose(material, context);
  assertThat(result.getCompositionMode()).isEqualTo(CompositionMode.DETERMINISTIC);
  assertThat(result.getExpressionDisposition())
          .isEqualTo(ExpressionDisposition.NOT_ATTEMPTED_DISABLED);
  assertThat(result.isExpressionDegraded()).isFalse();
  ```

  Reflection-test that `ExpressionIntent` contains only task kind, focus mode, closed facet/dimension/output enums, audience, depth, locale, source and public subject labels.

- [ ] **Step 2: Run RED, then implement final module class.**

  ```java
  public final class PortfolioAnswerComposition {
      public PortfolioCompositionResult compose(
              PortfolioAnswerMaterial material,
              PortfolioCompositionContext context) {
          PortfolioAnswerPlan fallback = deterministicComposer.compose(material);
          planValidator.validate(fallback, context.getExpressionAllowance().getCharacterLimit());
          return PortfolioCompositionResult.deterministic(
                  fallback, ExpressionDisposition.NOT_ATTEMPTED_DISABLED);
      }
  }
  ```

- [ ] **Step 3: Run GREEN and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=PortfolioAnswerCompositionDeterministicTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition backend/src/test/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerCompositionDeterministicTest.java
  git commit -m "feat: add deterministic p4 composition seam"
  ```

---

## Task 6: Allocate exactly one expression attempt per turn

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskExecutionAllowance.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticTaskExecutionContext.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticTurnCoordinator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/ExpressionAttemptAllowanceAllocator.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/routing/service/ExpressionAttemptAllowanceAllocatorTest.java`
- Test: modify `SemanticTurnCoordinatorTest.java`

**Interfaces:** Consumes stable topological task order and existing deadline/character allowance; produces per-task `attemptAllowed` and request-local ordinal without Provider or content knowledge.

- [ ] **Step 1: Write RED allocation tests.**

  Cover zero Fact tasks, one Fact, multiple Facts, first Fact preset/ineligible, Compare before Fact, blocked task, and stable repeated allocation. Only the first statically eligible non-preset Portfolio Fact gets ordinal `1`; all others get no attempt.

- [ ] **Step 2: Run RED.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ExpressionAttemptAllowanceAllocatorTest,SemanticTurnCoordinatorTest test
  ```

- [ ] **Step 3: Implement allocation without exposing P4 internals to routing.**

  Extend the existing task allowance with neutral fields `expressionAttemptAllowed` and `requestLocalExpressionOrdinal`. Do not add Provider/schema/breaker types to P2.

- [ ] **Step 4: Run GREEN and commit.**

  ```powershell
  git add backend/src/main/java/com/portfolio/agent/answer/routing backend/src/test/java/com/portfolio/agent/answer/routing
  git commit -m "feat: bound model expression to one turn attempt"
  ```

---

## Task 7: Wire P3 to the deep seam while preserving P3 authority

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutor.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioExecutionConfiguration.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/projection/PortfolioCompositionContextFactory.java`
- Delete after migration: old `answer/service/PortfolioAnswerComposer.java`
- Delete after migration: old `answer/service/DeterministicPortfolioAnswerComposer.java`
- Delete after migration: old flat Material/Statement classes
- Test: modify `P3PortfolioSemanticTaskExecutorTest.java`
- Test: create `PortfolioCompositionContextFactoryTest.java`
- Test: create `P3CompositionBoundaryArchitectureTest.java`

**Interfaces:** Consumes P3 Material and task allowance; calls only `PortfolioAnswerComposition.compose`; produces the same TaskResolution/EvidenceState/order plus composition metadata and pre-model contribution.

- [ ] **Step 1: Write RED seam tests.**

  Assert executor imports/calls no `PortfolioExpressionPort`, Codec, Validator, prompt, Provider registry or breaker. Assert `material.toGroundedContribution()` is used for Synthesis regardless of returned Plan.

  `PortfolioCompositionContextFactoryTest` must prove a deterministic closed projection from typed `SemanticTask` plus allowance: no `questionSpan`, `goalLabel`, history, token, Context or request DTO enters the result.

- [ ] **Step 2: Migrate constructor and compose path.**

  ```java
  PortfolioCompositionResult compositionResult = composition.compose(
          material,
          compositionContextFactory.from(task, context.getTaskExecutionAllowance()));
  GroundedAnswerContribution contribution = material.toGroundedContribution();
  ```

  Preserve P3 resolution/support/degraded values; only OR `compositionResult.isExpressionDegraded()` into degraded.

- [ ] **Step 3: Remove old public seam and run regression.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=P3PortfolioSemanticTaskExecutorTest,P3PortfolioSemanticTaskExecutorTaskTypesTest,P3CompositionBoundaryArchitectureTest,DeterministicSynthesisTaskExecutorTest test
  ```

- [ ] **Step 4: Commit.**

  Stage only the listed backend files and tests; commit `refactor: route p3 output through p4 composition seam`.

---

## Task 8: Project the minimum expression input and request-local aliases

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/projection/ExpressionAliasRegistry.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/projection/ExpressionInputDocument.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/projection/ModelExpressionInputProjector.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/projection/ModelExpressionInputProjectorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/projection/ExpressionInputPrivacyTest.java`

**Interfaces:** Consumes Material plus closed Intent/limits; produces `portfolio-expression-input.v1` and a server-only alias registry.

- [ ] **Step 1: Write RED exact-JSON and privacy tests.**

  Assert stable aliases `P01`, `S001`, `D01`, `C01`; exact field allowlist; maximum 16 statements and 12000 serialized characters; no truncation of REQUIRED.

  Explicitly seed fixtures with sentinel values for question, goal label, token, IDs, route, reference key, evidence text and exception message, then assert none appear in serialized input.

- [ ] **Step 2: Implement projector.**

  Alias Registry retains reverse maps to statement/source values only in request memory. It exposes no serialization getter and has a content-free `toString()`.

- [ ] **Step 3: Return an explicit over-limit result.**

  Input over limit maps later to `NOT_ATTEMPTED_INPUT_LIMIT`; never truncate REQUIRED and never call the port.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ModelExpressionInputProjectorTest,ExpressionInputPrivacyTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/projection backend/src/test/java/com/portfolio/agent/answer/composition/projection
  git commit -m "feat: project privacy-bounded expression input"
  ```

---

## Task 9: Implement closed Draft types and a strict Jackson Codec

**Files:**

- Create: domain Draft files listed in section 2.1
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/codec/PortfolioExpressionDraftCodec.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/codec/PortfolioExpressionDraftCodecTest.java`

**Interfaces:** Consumes a raw Provider response and expected MaterialKind; produces exactly one closed `portfolio-expression-draft.v1` variant or a safe schema failure.

- [ ] **Step 1: Write a parameterized RED adversarial table.**

  Include valid Fact/Comparison/Recommendation examples plus malformed JSON, top-level array, duplicate field, unknown field, unknown enum, wrong schema, cross-kind body, invalid null, newline, Markdown, HTML, URL, control character, duplicate text, >4 supports, >6 sections, >4 sentences/section and >18 sentences.

- [ ] **Step 2: Configure a dedicated strict ObjectMapper/ObjectReader.**

  Enable duplicate detection and unknown-property failure. Do not relax the application-wide mapper.

- [ ] **Step 3: Decode by the expected request kind only.**

  Comparison/Recommendation decode support is contract scaffolding. Eligibility still rejects them in P4.1.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=PortfolioExpressionDraftCodecTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/domain backend/src/main/java/com/portfolio/agent/answer/composition/codec backend/src/test/java/com/portfolio/agent/answer/composition/codec
  git commit -m "feat: decode strict portfolio expression drafts"
  ```

---

## Task 10: Validate structure, alias scope and REQUIRED coverage

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/validation/StatementGroundingValidator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/validation/FactDraftValidator.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/validation/StatementGroundingValidatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/validation/FactDraftValidatorTest.java`

**Interfaces:** Consumes decoded Draft, authoritative Material, Alias Registry and limits; produces a validated sentence-to-statement binding or a closed grounding failure.

- [ ] **Step 1: Write RED adversarial tests.**

  Cover fictitious/cross-request alias, empty supports, CONTEXT-only support, missing REQUIRED, summary-only REQUIRED, unauthorized section, Focused summary, Overview missing summary, duplicate use, source-less reverse mapping and exact section order.

- [ ] **Step 2: Implement fixed validation order.**

  ```text
  1 structure and limits
  2 alias/reference scope
  3 protected atoms
  4 qualifier preservation
  5 task-specific rules
  ```

  Stop on first closed failure category; do not expose sentence or aliases in exceptions/logs.

- [ ] **Step 3: Map citations server-side.**

  Draft never carries public reference keys. Each accepted sentence gets stable-deduplicated `PublicSourceReferenceValue` from supports aliases.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=StatementGroundingValidatorTest,FactDraftValidatorTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/validation backend/src/test/java/com/portfolio/agent/answer/composition/validation
  git commit -m "feat: validate draft grounding and coverage"
  ```

---

## Task 11: Protect high-risk atoms and qualifiers deterministically

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/validation/ProtectedAtomExtractor.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/validation/QualifierPreservationValidator.java`
- Create: `backend/src/test/resources/evaluation/p4/p4-grounding-adversarial-v1.json`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/validation/ProtectedAtomExtractorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/validation/QualifierPreservationValidatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/validation/P4GroundingAdversarialDatasetTest.java`

**Interfaces:** Consumes a sentence and its supported GroundedStatements; produces pass/fail for protected fact atoms and qualifier classes without calling a model.

- [ ] **Step 1: Build the RED dataset.**

  Include new number/percentage/amount/unit/date/version/subject/technology/status/contribution/verification/comparison/ranking/negation/modality/causality cases and legitimate paraphrase controls.

- [ ] **Step 2: Encode qualifier classes.**

  Preserve at least: planned, prototype/experimental/observed, partial/staged, uncovered/uncertain, collaborative/supporting, possible/inferred. Block `PLANNED->DELIVERED`, `PROTOTYPE->PRODUCTION`, `COLLABORATIVE->PRIMARY`, `PARTIAL->COMPLETE`, `OBSERVED->PROVEN`.

- [ ] **Step 3: Fail closed on ambiguous protected atoms.**

  Static connectors/general-term allowlist must be explicit and versioned in code. Do not add fuzzy matching or embeddings.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true "-Dtest=ProtectedAtomExtractorTest,QualifierPreservationValidatorTest,P4GroundingAdversarialDatasetTest" test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/validation backend/src/test/java/com/portfolio/agent/answer/composition/validation backend/src/test/resources/evaluation/p4
  git commit -m "feat: protect grounded atoms and qualifiers"
  ```

---

## Task 12: Assemble accepted Fact Drafts into server-owned plans

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/assembly/ModelDraftPlanAssembler.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/assembly/ModelDraftPlanAssemblerTest.java`

**Interfaces:** Consumes only validated bindings and Material; produces a Plan whose structure, title, caveats, omitted topics and citations remain server-owned.

- [ ] **Step 1: Write RED tests.**

  Assert server-localized title/section titles; model content only in validated summary/sentences; sources derived from aliases; caveats/omitted topics exact from Material; final character budget includes fixed caveats; final Plan validator runs again.

- [ ] **Step 2: Implement assembler for Fact only.**

  Reject Comparison/Recommendation with a closed unsupported-kind error. Their Draft types are not P4.1 activation.

- [ ] **Step 3: Prove no partial assembly.**

  A bad section or final budget failure returns no Plan object. The orchestration layer later uses the exact prebuilt fallback.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ModelDraftPlanAssemblerTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/assembly backend/src/test/java/com/portfolio/agent/answer/composition/assembly
  git commit -m "feat: assemble validated fact expression plans"
  ```

---

## Task 13: Add eligibility and the thread-safe expression circuit breaker

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/service/ModelExpressionEligibilityPolicy.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/service/ExpressionCircuitBreaker.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/service/ModelExpressionEligibilityPolicyTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/service/ExpressionCircuitBreakerTest.java`

**Interfaces:** Eligibility consumes config/Material/Intent/Allowance/deadline/input size and returns a closed decision; breaker consumes only eligible attempt outcomes and a `Clock`.

- [ ] **Step 1: Write RED eligibility matrix.**

  Eligible only when enabled, single-subject Fact, non-preset, `zh-CN`, supported evidence state, attempt allowed, >=1500ms remaining and input within limits. Disabled/ineligible/allowance/deadline/input-limit are non-degraded not-attempted states.

- [ ] **Step 2: Write RED breaker transition/concurrency tests.**

  Three consecutive eligible failures open for 30 seconds; OPEN does not permit calls; after 30 seconds exactly one concurrent request enters HALF_OPEN; success closes, failure reopens. Inject `Clock`; never sleep.

- [ ] **Step 3: Implement with explicit atomic state.**

  Count Provider error/timeout/empty/schema/grounding failures. Do not count disabled/ineligible/no allowance/deadline/input limit; do not persist state.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ModelExpressionEligibilityPolicyTest,ExpressionCircuitBreakerTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/service backend/src/test/java/com/portfolio/agent/answer/composition/service
  git commit -m "feat: gate and circuit-break expression attempts"
  ```

---

## Task 14: Define the independent Provider port and OpenAI-compatible adapter

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/gateway/PortfolioExpressionPort.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ModelExpressionRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ModelExpressionResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/domain/ModelExpressionDeadline.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/adapter/model/PortfolioExpressionPromptFactory.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/adapter/model/OpenAiCompatiblePortfolioExpressionAdapter.java`
- Modify: model registry/descriptor/operation files listed in section 2.2
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/adapter/model/OpenAiCompatiblePortfolioExpressionAdapterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/adapter/model/PortfolioExpressionAdapterPrivacyTest.java`

**Interfaces:** Consumes only versioned projected JSON and a bounded deadline; produces raw success/empty/safe transport failure. It is separate from `ConversationalModelPort`.

- [ ] **Step 1: Write RED captured-request tests.**

  Assert one POST, non-streaming, JSON object response format, thinking disabled, temperature `0.1`, <=1600 tokens, timeout `min(4s, remaining)`, no retry and no second Provider.

- [ ] **Step 2: Write RED privacy sentinels.**

  Captured body and prompt must exclude original question/history/goalLabel/token/context/internal IDs/routes/reference keys/evidence body/exception text. Static prompt says statement text is data, not instruction.

- [ ] **Step 3: Implement the exact port.**

  ```java
  public interface PortfolioExpressionPort {
      ModelExpressionResult express(
              ModelExpressionRequest request,
              ModelExpressionDeadline deadline);
  }
  ```

  Reuse the immutable registry and shared transport mechanics only. Do not add express/review logic to `ConversationalModelPort`.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=OpenAiCompatiblePortfolioExpressionAdapterTest,PortfolioExpressionAdapterPrivacyTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition backend/src/main/java/com/portfolio/agent/answer/adapter/model backend/src/test/java/com/portfolio/agent/answer/composition/adapter/model
  git commit -m "feat: add bounded portfolio expression provider adapter"
  ```

---

## Task 15: Complete atomic fallback orchestration

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerComposition.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerCompositionFallbackTest.java`

**Interfaces:** Consumes all internal collaborators; produces exactly one accepted model Plan or the exact prebuilt fallback Plan plus correct disposition/degraded state.

- [ ] **Step 1: Write RED decision-table tests.**

  Cover disabled, ineligible kind, preset, no allowance, short deadline, input limit, breaker open, Provider error/timeout, empty response, invalid schema, grounding invalid, assembly invalid and accepted Draft.

- [ ] **Step 2: Assert fallback object/value identity.**

  Prebuild fallback before eligibility/port. For every fallback failure assert returned Plan is the exact prebuilt instance where practical, otherwise value-equal, and contains no model fragment.

- [ ] **Step 3: Implement one straight-line pipeline.**

  ```text
  deterministic compose -> deterministic validate -> eligibility -> breaker permit
  -> project -> one port call -> strict decode -> grounding validate
  -> assemble -> final validate -> accepted OR exact fallback
  ```

  Catch only expected adapter/codec/validation/assembly categories. A deterministic fallback construction failure must not call Provider and maps to existing `PRESENTATION_BLOCKED` handling.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=PortfolioAnswerCompositionDeterministicTest,PortfolioAnswerCompositionFallbackTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/service/PortfolioAnswerComposition.java backend/src/test/java/com/portfolio/agent/answer/composition/service
  git commit -m "feat: atomically fall back from model expression"
  ```

---

## Task 16: Add safe configuration and production wiring

**Files:**

- Create: `PortfolioExpressionProperties.java`, `PortfolioExpressionConfiguration.java`, `PortfolioExpressionStartupValidator.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioExecutionConfiguration.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-prod.yml`
- Modify: `.env.example`
- Modify: `.env.postgres.example`
- Test: `PortfolioExpressionPropertiesTest.java`
- Test: `PortfolioExpressionConfigurationTest.java`
- Test: `PortfolioExpressionProductionStartupTest.java`

**Interfaces:** Consumes startup-only configuration and immutable provider registry; produces one composition bean and either a no-call disabled port or validated production adapter.

- [ ] **Step 1: Write RED binding/default tests.**

  Defaults must be exactly those in the P4 Spec. Request DTOs cannot override Provider/timeout/token/kinds.

- [ ] **Step 2: Write RED startup matrix.**

  `enabled=false` allows missing key/approval. Production `enabled=true` fails startup when approval/key/registry compatibility/HTTPS/schema support is missing or limits exceed caps. Config may narrow kinds/limits but never enable non-build-supported kinds.

- [ ] **Step 3: Implement independent P4 properties.**

  Do not reuse legacy C1 field names (`model-policy-version`, `answer-schema-version`, `maxTokens`) as the P4 contract. If old `ModelExpressionProperties` is unreachable, remove it only after an architecture test proves no production references.

- [ ] **Step 4: Keep repository profiles disabled.**

  ```yaml
  portfolio:
    model-expression:
      enabled: false
      provider: DEEPSEEK_V4_FLASH
      policy-version: p4-expression-policy-v1
      input-schema-version: portfolio-expression-input.v1
      draft-schema-version: portfolio-expression-draft.v1
      allowed-material-kinds: [FACT]
      timeout: 4s
      max-output-tokens: 1600
      external-public-data-policy-approved: false
  ```

- [ ] **Step 5: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=PortfolioExpressionPropertiesTest,PortfolioExpressionConfigurationTest,PortfolioExpressionProductionStartupTest test
  git add backend/src/main/java/com/portfolio/agent/answer/composition/adapter/model backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioExecutionConfiguration.java backend/src/main/resources .env.example .env.postgres.example backend/src/test/java/com/portfolio/agent/answer/composition/adapter/model
  git commit -m "feat: wire p4 expression disabled by default"
  ```

---

## Task 17: Propagate task composition and aggregate MIXED modes

**Files:**

- Modify: `GenerationMode.java`, `AnswerConstructionMode.java`
- Modify: `TaskOutcome.java`, `SemanticTurnOutcome.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskComposition.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/dto/response/TaskCompositionResponse.java`
- Modify: `CompletedTaskResponse.java`
- Modify: `ConversationAnswerResponseMapper.java`
- Modify: the unique top-level mode aggregation code in `ConversationalAgentRuntime.java` or its current replacement
- Test: `TaskOutcomeContractTest.java`
- Test: `SemanticTurnOutcomeCompositionTest.java`
- Test: `ConversationAnswerResponseMapperTest.java`
- Test: `ConversationalAgentRuntimeTest.java`

**Interfaces:** Consumes internal `PortfolioCompositionResult`; produces optional public `composition {mode,degraded}` and correct top-level mode. No internal failure reason is public.

- [ ] **Step 1: Write RED task/public mapping tests.**

  Renderable Portfolio tasks include composition; non-renderable and non-Portfolio tasks omit it. Public DTO includes only mode and degraded.

- [ ] **Step 2: Write RED aggregate truth table.**

  ```text
  all deterministic -> DETERMINISTIC / EVIDENCE_COMPOSITION
  all model         -> MODEL         / MODEL_GROUNDED
  all fallback      -> FALLBACK      / EVIDENCE_COMPOSITION
  any real mixture  -> MIXED         / MIXED_COMPOSITION
  ```

  Top-level degraded is OR(P3 degradation, P4 fallback, existing degradation). Normal not-attempted is false.

- [ ] **Step 3: Implement enum/DTO/mapper changes atomically.**

  Add `GenerationMode.MIXED`, `AnswerConstructionMode.MIXED_COMPOSITION`. Do not change TaskResolution, AnswerResolution or EvidenceState.

  `P3PortfolioSemanticTaskExecutor` maps the result into a small immutable `TaskComposition(mode, degraded)` stored on renderable `TaskOutcome`; `expressionDisposition` and internal safe failure codes are deliberately not copied into the public task model.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=TaskOutcomeContractTest,SemanticTurnOutcomeCompositionTest,ConversationAnswerResponseMapperTest,ConversationalAgentRuntimeTest test
  git add backend/src/main/java/com/portfolio/agent/answer/domain backend/src/main/java/com/portfolio/agent/answer/routing/domain backend/src/main/java/com/portfolio/agent/answer/dto/response backend/src/main/java/com/portfolio/agent/answer/mapper backend/src/main/java/com/portfolio/agent/answer/service backend/src/test/java/com/portfolio/agent/answer
  git commit -m "feat: expose grounded composition status"
  ```

  Note: this commit is the backend half of an atomic public-contract release. Do not deploy it with an old frontend that rejects the new enums.

---

## Task 18: Add privacy-safe diagnostics

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/composition/observability/ExpressionDiagnostics.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java` only if enum/allowlist registration is required
- Test: `backend/src/test/java/com/portfolio/agent/answer/composition/observability/ExpressionDiagnosticsTest.java`
- Test: modify `RuntimeCompositePrivacyTest.java`
- Modify: `scripts/privacy-check.ps1` and its test only if new static patterns are required

**Interfaces:** Consumes closed status/count/size/duration values; produces only the five approved event names and content-free fields.

- [ ] **Step 1: Write RED event-schema tests.**

  Events are exactly `expression.eligibility`, `expression.provider.completed`, `expression.provider.failed`, `expression.validation.completed`, `expression.fallback.used`.

- [ ] **Step 2: Enforce field allowlist.**

  Allow only task/material kind, disposition/failure code, count/size/duration buckets, breaker state, operation enum and booleans. Reject/free-text fields rather than sanitizing late.

- [ ] **Step 3: Capture content sentinels.**

  Assert logs/events contain no question, statement, Draft, alias, subject label, reference key, prompt, response body, URL, endpoint or exception message.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ExpressionDiagnosticsTest,RuntimeCompositePrivacyTest test
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
  git add backend/src/main/java/com/portfolio/agent/answer/composition/observability backend/src/test/java/com/portfolio/agent/answer/composition/observability backend/src/test/java/com/portfolio/agent/answer/adapter/model/RuntimeCompositePrivacyTest.java scripts/privacy-check.ps1 scripts/privacy-check.test.ps1
  git commit -m "feat: observe expression without content leakage"
  ```

---

## Task 19: Add production-chain integration tests

**Files:**

- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/P4FactCompositionIntegrationTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/P4CompositionFallbackIntegrationTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/P4MixedCompositionIntegrationTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/database/P3PostgresPortfolioExecutionIntegrationTest.java`
- Modify: `scripts/run-jar-e2e.ps1` and its test if a P4 mock-provider scenario is added

**Interfaces:** Exercises `HTTP -> P2 -> P3 -> P4 -> TaskOutcome -> Mapper -> JSON` with a local fake Provider and both Bundle/PostgreSQL evidence paths.

- [ ] **Step 1: Write disabled-path integration test.**

  Default config returns deterministic content and performs zero Provider calls.

- [ ] **Step 2: Write accepted Fact test.**

  With test-only enabled config and Fake Port, a single-subject non-preset Fact returns `MODEL_GROUNDED`, unchanged resolution/evidence state and valid source references.

- [ ] **Step 3: Write fallback matrix test.**

  Provider/Codec/Validator failures return the full deterministic content, `FALLBACK`, degraded true, no error response and no second call.

- [ ] **Step 4: Write multi-task MIXED test.**

  Exactly one eligible Fact gets an attempt; other renderable tasks remain deterministic. Top-level mode is `MIXED/MIXED_COMPOSITION`.

- [ ] **Step 5: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true "-Dtest=P4FactCompositionIntegrationTest,P4CompositionFallbackIntegrationTest,P4MixedCompositionIntegrationTest,P3PostgresPortfolioExecutionIntegrationTest" test
  git add backend/src/test/java/com/portfolio/agent/answer/controller backend/src/test/java/com/portfolio/agent/database scripts/run-jar-e2e.ps1 scripts/run-jar-e2e.test.ps1
  git commit -m "test: verify p4 production composition chain"
  ```

---

## Task 20: Extend Eval without persisting content

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/evaluation/domain/P4EvaluationDimension.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/P4EvalExecutor.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/grading/P4EvalVerdictPolicy.java`
- Create: `backend/src/test/java/com/portfolio/agent/evaluation/execution/P4EvalExecutorTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/evaluation/grading/P4EvalVerdictPolicyTest.java`
- Modify: Eval report DTO/renderer/CLI files discovered from the current P0 implementation

**Interfaces:** Reuses the production composition seam; produces dimension verdicts and safe aggregate counts/buckets only.

- [ ] **Step 1: Write RED verdict tests for six dimensions.**

  `OFFLINE_VALIDATION`, `MOCK_PROVIDER_INTEGRATION`, `PRIVACY_CAPTURE`, `MODEL_CONFORMANCE`, `REAL_PROVIDER_ACCEPTANCE`, `ANSWER_QUALITY_COMPARISON`.

- [ ] **Step 2: Encode ordinary CI behavior.**

  First four must PASS. Real Provider and quality comparison are `INCOMPLETE` when not explicitly run. Any structure/fact/reference/privacy/fallback failure is BLOCKER.

- [ ] **Step 3: Prove report privacy.**

  Persist only enums, counts, buckets and verdicts—no question, answer, statement, alias, subject label or reference key.

- [ ] **Step 4: Run and commit.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=P4EvalExecutorTest,P4EvalVerdictPolicyTest,EvalReportJsonWriterTest,EvalReportMarkdownRendererTest test
  git add backend/src/main/java/com/portfolio/agent/evaluation backend/src/test/java/com/portfolio/agent/evaluation
  git commit -m "feat: evaluate p4 grounded expression safely"
  ```

---

## Task 21: Remove obsolete expression islands and enforce architecture

**Files:**

- Delete unreachable legacy C1 expression properties/config/domain files only after reference proof
- Modify: `backend/src/test/java/com/portfolio/agent/answer/architecture/ProductionAnswerArchitectureTest.java` or create it
- Modify: `backend/src/test/java/com/portfolio/agent/evaluation/execution/OracleIsolationTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/selection/PortfolioSelectionSurfaceRemovalTest.java` only if its surface list must be updated

**Interfaces:** Produces one production composition seam and one expression port; removes dual-decision and unreachable prompt contracts.

- [ ] **Step 1: Prove production references before deletion.**

  ```powershell
  rg -n "PortfolioAnswerComposer|ModelExpressionProperties|ModelExpressionConfiguration|generate\(|review\(" backend/src/main/java
  ```

  Classify every hit. Do not delete shared registry/transport used by general conversation.

- [ ] **Step 2: Add architecture tests.**

  Assert P3 executor cannot depend on `composition.adapter`, `composition.codec`, `composition.validation` or Provider classes; Synthesis cannot depend on Draft/Plan; only the composition module depends on `PortfolioExpressionPort`.

- [ ] **Step 3: Delete only proven unreachable legacy code and run focused regression.**

- [ ] **Step 4: Commit.**

  Commit `refactor: remove obsolete model expression paths` with an explicit file list; never use broad `git add .`.

---

## Task 22: Run ordinary release gates and update authoritative status

**Files:**

- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/13-Agent对话体验与智能编排改造路线图.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: P4 backend/frontend handoff docs if public names changed

**Interfaces:** Consumes verified build/test evidence; produces truthful `IMPLEMENTED_DISABLED / MOCK_VERIFIED / REAL_PROVIDER_INCOMPLETE` status.

- [ ] **Step 1: Run all focused P4 tests together.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true "-Dtest=*PortfolioAnswerComposition*Test,*Expression*Test,*P4*Test,P3PortfolioSemanticTaskExecutorTest,ConversationAnswerResponseMapperTest,ConversationalAgentRuntimeTest" test
  ```

- [ ] **Step 2: Run full backend and quality gates fresh.**

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true test
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.ps1
  ```

- [ ] **Step 3: Build packaged JAR with the already-built frontend artifact.**

  Coordinate with the frontend Agent for the matching enum/DTO version before the atomic package gate. Do not edit frontend files here.

  ```powershell
  C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=false clean package
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1
  ```

- [ ] **Step 4: Inspect diff and status truthfully.**

  ```powershell
  git diff --check
  git status --short
  ```

  Document `IMPLEMENTED_DISABLED`, `MOCK_VERIFIED`, `REAL_PROVIDER_INCOMPLETE`, `DEPLOYMENT_ENABLED=false`. Do not claim P4.1 fully admitted yet.

- [ ] **Step 5: Commit documentation.**

  Stage only the listed docs and commit `docs: record p4 mock-verified backend status`.

---

## Task 23: Run the explicitly authorized real-Provider acceptance gate

**Precondition:** 用户在执行时明确授权真实外发，并提供受控凭据/环境。没有授权就跳过本任务，并保持 `REAL_PROVIDER_INCOMPLETE`。

**Files:**

- Create/update safe Eval report artifacts that contain only enums/counts/buckets/verdicts
- Modify authoritative status docs only after verified evidence

**Interfaces:** Consumes approved public-only P4 input through the production adapter; produces conformance, latency, failure and answer-quality verdicts without persisting content.

- [ ] **Step 1: Reconfirm capture boundary before enabling.**

  Run privacy capture with a local endpoint first. Verify exact outbound JSON and HTTPS registry compatibility.

- [ ] **Step 2: Run a bounded conformance suite.**

  Cover valid Fact Draft rate, strict-schema failure rate, grounding acceptance, qualifier preservation, timeout, empty response, rate limit and breaker behavior. No retries.

- [ ] **Step 3: Compare answer quality against deterministic baseline.**

  Score directness, coherence, repetition, qualifier preservation, section coverage and citation completeness. Structural/factual/privacy failures are blockers regardless of style score.

- [ ] **Step 4: Decide admission.**

  - If conformance and quality thresholds pass: record `REAL_PROVIDER_VERIFIED`; deployment config may separately enable `FACT`.
  - If they fail: keep default/production config disabled; deterministic path remains the formal capability.

- [ ] **Step 5: Keep repository defaults disabled and commit only safe reports/docs.**

  Never commit credentials, raw request/response bodies, question text, answer text, aliases or reference keys.

---

## 4. Final acceptance checklist

- [ ] `PortfolioAnswerMaterial -> PortfolioCompositionResult` is the only P4 seam visible to P3.
- [ ] P3 executor has no Provider/Prompt/Codec/Validator/Breaker imports.
- [ ] P2 Synthesis consumes only pre-model `GroundedAnswerContribution`.
- [ ] Only single-subject non-preset `zh-CN` Fact is model-eligible.
- [ ] Exactly one expression attempt can occur per turn.
- [ ] Provider input contains only published labels/statements, closed Intent, aliases and shape limits.
- [ ] REQUIRED is covered in body; OPTIONAL may be omitted; CONTEXT cannot stand alone.
- [ ] High-risk atoms and qualifier strengthening fail closed.
- [ ] Caveats, omitted topics, title, section titles and citations remain server-owned.
- [ ] Fallback is prebuilt and atomically returned on every attempted-expression failure.
- [ ] Normal not-attempted paths are not degraded; true fallback is degraded.
- [ ] Circuit breaker is thread-safe and tested with injected Clock.
- [ ] Default/local/prod repository configuration remains disabled.
- [ ] Public task composition contains only mode/degraded.
- [ ] `MIXED/MIXED_COMPOSITION` truth table is verified.
- [ ] No P4 change alters P2 task semantics, P3 resolution/evidence state, comparison relation or recommendation order.
- [ ] Ordinary CI performs no network call and reports real Provider dimensions INCOMPLETE.
- [ ] Backend full tests, privacy, architecture, packaged-JAR and integration gates are fresh PASS before completion claims.
- [ ] Frontend source files were not modified by the backend worker.

## 5. Plan self-review

- Spec coverage: P4-A through P4-E are mapped to Tasks 1–23; P4.2/P4.3 activation is explicitly excluded.
- Placeholder audit: no implementation behavior is left as TBD/TODO. The only discovery instruction is locating the already-existing unique top-level aggregator/Eval report classes before modifying them; their required behavior and tests are fixed here.
- Type consistency: Material, Context, Result, Port, Draft, public DTO and aggregate enums use the same names as the approved Spec.
- Privacy consistency: Provider, logs, reports and public DTO each have explicit allowlists and negative sentinels.
- Failure consistency: not-attempted and fallback are separate; deterministic construction failure is not hidden by model invocation.
- Release consistency: backend public contract is implemented here, frontend behavior is delegated, and packaged release requires the matching frontend contract atomically.
