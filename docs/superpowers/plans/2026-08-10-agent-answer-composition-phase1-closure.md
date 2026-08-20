# Agent Answer Composition Phase 1 Closure Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复阶段一回答编排与既有设计规格之间的全部阻塞性偏差，补齐测试和真实 PostgreSQL 证据，并让普通工程门禁、`eval validate`、`eval offline`、Mock E2E 与打包 JAR 验收共同达到可复核的 PASS。

**Architecture:** 保留现有 `Retriever -> PortfolioIntelligenceResult -> DeterministicPortfolioAnswerComposer -> PortfolioAnswerPlan -> Assembler -> v2 Blocks -> AnswerSectionView` 主链。后端以 `AnswerSectionMapping` 作为分类、章节顺序、标题和受控缺口文案的唯一权威入口；Intelligence 在进入 Composer 前判定目标证据覆盖，Composer 保留第二层不变量防线；前端以“字段是否存在”而不是“数组是否非空”选择 v2 Blocks。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、AssertJ、Mockito、Flyway、PostgreSQL 16/pgvector、Testcontainers、Vue 3、TypeScript、Vitest、Playwright、PowerShell。

## Global Constraints

- 需求基线是 `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md` 和 `docs/13-Agent对话体验与智能编排改造路线图.md`；本计划不重新定义阶段一产品范围。
- 只处理 `ANSWERED + FACT_LOOKUP + 单主体` 的阶段一 Composer 路径；Comparison、Recommendation、澄清、无效输入和 Contract 专用响应保持现有路径。
- Composer 不读取用户问题，不调用模型、网络、数据库或工具；正文只来自公开、`VERIFIED` Claim 的 `statement + detail`。
- Evidence 必须为 `APPROVED`；输出 Claim/Evidence ID 必须来自同一输入 Result。
- `OVERVIEW` 每章最多 3 条事实，`FOCUSED` 目标章节最多 6 条事实；`BOUNDARY` 事实同样受预算约束，受控缺口句不占事实预算。
- `OVERVIEW` 必须有 Summary；`FOCUSED` 不得有 Summary。
- Focused 覆盖以“请求的精确 Claim Category”筛选事实，再映射为目标 Section Type；不能用同一 Section Type 下未请求的 Category 冒充目标证据。
- 部分目标有证据时输出事实章节和唯一 Boundary；全部目标无证据时在 Intelligence 层返回 `NOT_SUPPORTED + INSUFFICIENT`，Composer 仍必须拒绝无目标事实的直接调用。
- 数据或 Plan 不变量损坏返回 `CAPABILITY_UNAVAILABLE + INSUFFICIENT + degraded=true + ANSWER_COMPOSITION_INVALID`，不得输出部分 Plan。
- v2 `blocks` 字段一旦存在，即使值为 `[]` 也具有权威性；只有字段不存在时才读取 legacy `sections`。
- Eval、日志和报告不得保存问题、回答正文、Prompt、稳定 ID hash 或私有路径。
- 普通 CI 不调用真实 Provider；真实 Provider 未授权时必须如实报告 `INCOMPLETE`，不伪装为 PASS。
- `design-exploration/agent-clarification-comparison/` 属于阶段二/阶段五探索，不进入本计划的提交范围。

## Closure Decisions To Approve With This Plan

1. STATUS Focus 当前请求类别为 `OUTCOME + LIMITATION`：只有 OUTCOME 时输出 STATUS，并在唯一 BOUNDARY 中写“当前公开材料未覆盖限制与边界。”；只有 LIMITATION 时输出 BOUNDARY 事实，并为缺失 STATUS 写“当前公开材料未覆盖最终状态。”。
2. 同一 Section Type 对应多个请求 Category 时，只要其中任一请求 Category 有事实，该 Section 即视为存在；但未请求 Category 永远不能满足覆盖判断。
3. 全目标缺失是业务证据不足，不是 Composer 损坏；生产链在 `DefaultPortfolioIntelligence.decisionFor` 返回 `NOT_SUPPORTED`，Assembler 输出固定无引用模板。直接绕过 Intelligence 调用 Composer 则触发第二层不变量异常。
4. `blocks: []` 不允许回退读取 legacy `sections`。若标题、Summary 与权威 Blocks 均无可展示内容，映射器按现有安全错误路径拒绝响应。
5. Phase 1 的“正式完成”要求路线图启动门槛和 §16 验收同时闭合：后端、前端、Mock E2E、`eval validate`、`eval offline`、PostgreSQL 集成和打包 JAR 门禁均有本次运行证据。

---

## File Map

### Backend domain and composition

- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerSectionMapping.java` — 后端唯一章节策略：映射、顺序、标题、缺口文案。
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java` — 精确 Focus 筛选、全目标缺失防线、统一预算。
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java` — degraded 透传及 NOT_SUPPORTED 安全正文。
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java` — 进入 Composer 前的目标覆盖判定。
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalAnswerShape.java` — 复用后端权威章节顺序。

### Backend tests and evaluation

- Modify: `backend/src/test/java/com/portfolio/agent/answer/domain/AnswerSectionMappingTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlanTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDomainContractTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposerTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQueryIntegrationTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalIntelligenceExecutor.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngine.java`
- Modify: `backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalIntelligenceExecutorTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngineTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/evaluation/application/EvalHarnessTest.java`
- Modify: `governance/portfolio-governance/evaluation/cases/holdout/answer.v1.json`

### Frontend and E2E

- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Modify: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `frontend/e2e/portfolio.spec.ts`

### Documentation and evidence

- Modify: `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/13-Agent对话体验与智能编排改造路线图.md`
- Create: `docs/reports/agent-answer-composition-phase1-closure-2026-08-10.md`

---

### Task 1: Lock Immutable Domain Contracts And The Authoritative Section Policy

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerSectionMapping.java:18-49`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalAnswerShape.java:20-80`
- Test: `backend/src/test/java/com/portfolio/agent/answer/domain/AnswerSectionMappingTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlanTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDomainContractTest.java`

**Interfaces:**
- Produces: `AnswerSectionMapping.authoritativeOrder(): List<AnswerSectionType>`.
- Produces: `AnswerSectionMapping.titleFor(AnswerSectionType): String`.
- Produces: `AnswerSectionMapping.gapMessageFor(AnswerSectionType): String` for BACKGROUND、RESPONSIBILITY、SOLUTION、VERIFICATION、STATUS、BOUNDARY.
- Preserves: `sectionTypeFor` and `preferredCategoriesFor` signatures.

- [ ] **Step 1: Add failing immutability and value-semantics tests**

Add the following test shapes; use mutable input collections and assert both defensive copying and unmodifiable outputs:

```java
@Test
void planAndSectionDefensivelyCopyCollectionsAndHaveValueSemantics() {
    List<String> claimIds = new ArrayList<>(List.of("claim-1"));
    List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
    PortfolioAnswerSection section = new PortfolioAnswerSection(
            AnswerSectionType.SOLUTION, "技术方案与实现", "采用受控路由。",
            claimIds, evidenceIds);
    List<PortfolioAnswerSection> sections = new ArrayList<>(List.of(section));
    PortfolioAnswerPlan plan = new PortfolioAnswerPlan("SQL 审计工具", null, sections);

    claimIds.add("claim-mutated");
    evidenceIds.add("evidence-mutated");
    sections.clear();

    assertThat(section.getClaimIds()).containsExactly("claim-1");
    assertThat(section.getEvidenceIds()).containsExactly("evidence-1");
    assertThat(plan.getSections()).containsExactly(section);
    assertThatThrownBy(() -> section.getClaimIds().add("blocked"))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> plan.getSections().clear())
            .isInstanceOf(UnsupportedOperationException.class);

    PortfolioAnswerSection equalSection = new PortfolioAnswerSection(
            AnswerSectionType.SOLUTION, "技术方案与实现", "采用受控路由。",
            List.of("claim-1"), List.of("evidence-1"));
    PortfolioAnswerPlan equalPlan = new PortfolioAnswerPlan(
            "SQL 审计工具", null, List.of(equalSection));
    assertThat(section).isEqualTo(equalSection).hasSameHashCodeAs(equalSection);
    assertThat(plan).isEqualTo(equalPlan).hasSameHashCodeAs(equalPlan);
}
```

Add an `AnswerFocus` test that mutates the source list, asserts the returned list is unmodifiable, and asserts equal Focus objects have equal hash codes.

- [ ] **Step 2: Add failing policy tests**

```java
@Test
void exposesOneAuthoritativeOrderTitlesAndControlledGapMessages() {
    assertThat(AnswerSectionMapping.authoritativeOrder()).containsExactly(
            AnswerSectionType.BACKGROUND,
            AnswerSectionType.RESPONSIBILITY,
            AnswerSectionType.SOLUTION,
            AnswerSectionType.VERIFICATION,
            AnswerSectionType.STATUS,
            AnswerSectionType.BOUNDARY);
    assertThat(AnswerSectionMapping.titleFor(AnswerSectionType.STATUS))
            .isEqualTo("结果与当前状态");
    assertThat(AnswerSectionMapping.gapMessageFor(AnswerSectionType.STATUS))
            .isEqualTo("当前公开材料未覆盖最终状态。");
    assertThat(AnswerSectionMapping.gapMessageFor(AnswerSectionType.BOUNDARY))
            .isEqualTo("当前公开材料未覆盖限制与边界。");
    assertThatThrownBy(() -> AnswerSectionMapping.titleFor(AnswerSectionType.REJECTED))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=AnswerSectionMappingTest,PortfolioAnswerPlanTest,PortfolioDomainContractTest test
```

Expected: FAIL because the three policy methods do not exist; existing immutable implementations should already satisfy most new contract assertions.

- [ ] **Step 4: Implement the authoritative policy and remove duplicate backend order constants**

Add immutable static maps/lists in `AnswerSectionMapping`, return the list directly because `List.of` is unmodifiable, and reject `REJECTED`. Replace `DeterministicPortfolioAnswerComposer.SECTION_ORDER`, `DEFAULT_TITLES`, and `GAP_MESSAGES` references with these methods. Make `EvalAnswerShape` validate order using `AnswerSectionMapping.authoritativeOrder()` rather than a private duplicate order.

- [ ] **Step 5: Run focused tests and backend policy consumers**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=AnswerSectionMappingTest,PortfolioAnswerPlanTest,PortfolioDomainContractTest,DeterministicPortfolioAnswerComposerTest,EvalAnswerShapeTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/domain/AnswerSectionMapping.java backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalAnswerShape.java backend/src/test/java/com/portfolio/agent/answer/domain/AnswerSectionMappingTest.java backend/src/test/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlanTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDomainContractTest.java
git commit -m "refactor: centralize answer section policy"
```

---

### Task 2: Enforce Exact Focus Coverage Before Composition

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java:287-297`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java:76-112,292-320`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java`

**Interfaces:**
- Produces: focused Result with no evidence in any requested Category maps to `PortfolioDisposition.NOT_SUPPORTED`.
- Produces: NOT_SUPPORTED response uses fixed `当前公开内容中没有足够的已验证材料。`, no Claim/Evidence IDs, `AnswerEvidenceState.INSUFFICIENT`.

- [ ] **Step 1: Add the focused all-target-missing routing test**

Create a retriever result whose Focus requests `VERIFICATION`, but whose only returned passage Category is `BACKGROUND`. Assert:

```java
assertThat(decision.getDisposition()).isEqualTo(PortfolioDisposition.NOT_SUPPORTED);
assertThat(decision.getMaterial()).get().satisfies(result -> {
    assertThat(result.getAnswerFocus()).isEqualTo(
            AnswerFocus.focused(List.of(AnswerClaimCategory.VERIFICATION)));
    assertThat(result.getEvidence()).isNotEmpty();
});
```

The non-empty unrelated evidence intentionally proves the decision is based on target coverage rather than total evidence count.

- [ ] **Step 2: Add the NOT_SUPPORTED no-leak Assembler test**

Pass that Result inside a `PortfolioDecision(NOT_SUPPORTED, result)` and assert:

```java
assertThat(answer.getResolution()).isEqualTo(AnswerResolution.NOT_SUPPORTED);
assertThat(answer.getEvidenceState()).isEqualTo(AnswerEvidenceState.INSUFFICIENT);
assertThat(answer.getBlocks()).singleElement().satisfies(block -> {
    assertThat(block.getContent()).isEqualTo("当前公开内容中没有足够的已验证材料。");
    assertThat(block.getClaimIds()).isEmpty();
    assertThat(block.getEvidenceIds()).isEmpty();
});
```

- [ ] **Step 3: Run the focused tests and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=DefaultPortfolioIntelligenceRoutingTest,PortfolioIntelligenceAnswerAssemblerTest test
```

Expected: routing returns ANSWERED and Assembler exposes the unrelated passage.

- [ ] **Step 4: Implement target coverage and safe NOT_SUPPORTED mapping**

In `decisionFor`, after clarification/contract checks and before total evidence emptiness, calculate:

```java
private boolean hasFocusedTargetEvidence(PortfolioIntelligenceResult result) {
    AnswerFocus focus = result.getAnswerFocus();
    if (focus.getMode() == AnswerFocusMode.OVERVIEW) {
        return !result.getEvidence().isEmpty();
    }
    Set<AnswerClaimCategory> requested = Set.copyOf(
            focus.getRequestedClaimCategories());
    return result.getEvidence().stream()
            .map(passage -> passage.getClaim().getCategory())
            .anyMatch(requested::contains);
}
```

Return NOT_SUPPORTED when this is false. In Assembler, handle `PortfolioDisposition.NOT_SUPPORTED` with a dedicated fixed no-reference block instead of `materialBlocks(result)`.

- [ ] **Step 5: Run routing, Assembler and runtime regression tests**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=DefaultPortfolioIntelligenceRoutingTest,DefaultPortfolioIntelligenceTest,PortfolioIntelligenceAnswerAssemblerTest,ConversationalAgentRuntimeTest test
```

Expected: PASS; Comparison、Recommendation、clarification and Contract assertions remain unchanged.

- [ ] **Step 6: Commit Task 2**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java
git commit -m "fix: fail closed when focused evidence is absent"
```

---

### Task 3: Fix Focused STATUS Gaps And Apply Budgets To Boundary Facts

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java:45-274`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposerTest.java`

**Interfaces:**
- Consumes: exact requested Claim Categories from `AnswerFocus`.
- Produces: grouped sections contain only Overview facts or exact Focus Categories.
- Produces: Boundary facts respect Overview 3 / Focused 6 fact budgets; controlled gap lines are appended after facts.

- [ ] **Step 1: Add a failing STATUS partial-gap test**

```java
@Test
void focusedStatusReportsMissingBoundaryTargetWithoutLosingOutcome() {
    PortfolioIntelligenceResult result = result(
            AnswerFocus.focused(List.of(
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.LIMITATION)),
            "project-1", "SQL 审计与故障排查工具", "公开项目摘要",
            List.of(passage("claim-outcome", AnswerClaimCategory.OUTCOME,
                    "已完成公开范围内的交付。", "evidence-out")));

    PortfolioAnswerPlan plan = composer.compose(result);

    assertThat(plan.getSections())
            .extracting(PortfolioAnswerSection::getSectionType)
            .containsExactly(AnswerSectionType.STATUS, AnswerSectionType.BOUNDARY);
    assertThat(plan.getSections().getLast().getContent())
            .isEqualTo("当前公开材料未覆盖限制与边界。");
    assertThat(plan.getSections().getLast().getEvidenceIds()).isEmpty();
}
```

- [ ] **Step 2: Add a failing exact-category and second-level defense test**

Use Focus `LIMITATION` and only a `LEARNING` passage. Although both map to BOUNDARY, assert `composer.compose(result)` throws `PortfolioAnswerCompositionException` containing `requested focus`. This prevents an unrequested Category from satisfying the target.

- [ ] **Step 3: Replace the unlimited Boundary test with explicit budget tests**

Construct seven distinct LIMITATION facts. Compose once with Overview and once with focused LIMITATION. Assert Overview has exactly 3 Claim IDs and Focused has exactly 6, preserving input order. Also assert the focused content contains fact 6 and not fact 7.

- [ ] **Step 4: Run Composer tests and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=DeterministicPortfolioAnswerComposerTest test
```

Expected: STATUS has no gap, LEARNING incorrectly satisfies LIMITATION, and Boundary retains all seven facts.

- [ ] **Step 5: Implement exact filtering, target defense and Boundary budgeting**

Change `groupBySection` so Focused mode first rejects passages whose exact Category is absent from `requestedClaimCategories`. After grouping, reject a Focused request when every mapped target group is empty. In `boundarySection`, select `mergeByNormalizedBody(boundaryFacts)` with `budget(focus, BOUNDARY)` before appending controlled gap messages. Do not count gap messages against the fact budget.

When generating gaps, iterate every target Section including BOUNDARY; use `AnswerSectionMapping.gapMessageFor(type)`. Preserve one BOUNDARY section by merging fact lines and gap lines in that order.

- [ ] **Step 6: Run Composer and mapping regression tests**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=AnswerSectionMappingTest,DeterministicPortfolioAnswerComposerTest,PortfolioIntelligenceAnswerAssemblerTest test
```

Expected: PASS, including Overview 3 / Focused 6 distinction and deterministic equality.

- [ ] **Step 7: Commit Task 3**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java backend/src/test/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposerTest.java
git commit -m "fix: enforce focused gaps and boundary budgets"
```

---

### Task 4: Preserve Degraded And Focus Metadata Through Every Copy Path

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java:115-145`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDomainContractTest.java`

**Interfaces:**
- Produces: successful composed answer keeps `PortfolioIntelligenceResult.isDegraded()`.
- Preserves: `withDecisionMetadata`, `withContractIdentity`, and `withAnswerFocus` retain Focus, degraded, notice and contract metadata.

- [ ] **Step 1: Add the reachable degraded propagation test**

Build an ANSWERED FACT_LOOKUP Result with `degraded=true`, `noticeCode="POSTGRES_FALLBACK"`, valid subject/evidence, and Overview Focus. Assert assembled output remains ANSWERED/VERIFIED and:

```java
assertThat(answer.isDegraded()).isTrue();
assertThat(answer.getNoticeCode()).isEqualTo("POSTGRES_FALLBACK");
assertThat(answer.getBlocks()).isNotEmpty();
```

- [ ] **Step 2: Add copy-preservation contract assertions**

Start from a Result with Focused VERIFICATION, then call every copy method in different orders:

```java
PortfolioIntelligenceResult copied = original
        .withDecisionMetadata(AnswerIntentSource.RULE, true)
        .withContractIdentity("preset-1", "contract-1")
        .withAnswerFocus(focus);
assertThat(copied.getAnswerFocus()).isEqualTo(focus);
assertThat(copied.isDegraded()).isEqualTo(original.isDegraded());
assertThat(copied.getNoticeCode()).isEqualTo(original.getNoticeCode());
assertThat(copied.getQuestionPresetId()).isEqualTo("preset-1");
assertThat(copied.getContractVersion()).isEqualTo("contract-1");
assertThat(copied.isContextVersionUpdated()).isTrue();
```

- [ ] **Step 3: Run tests and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioIntelligenceAnswerAssemblerTest,PortfolioDomainContractTest test
```

Expected: degraded propagation test fails because `answeredResult` currently hardcodes `false`.

- [ ] **Step 4: Replace the hardcoded flag and keep copy constructors lossless**

Change only the answered Result construction from `false` to `result.isDegraded()`. If copy-preservation assertions reveal a loss, route all copy methods through the full private constructor while retaining every existing field; do not introduce a new public constructor.

- [ ] **Step 5: Run response, runtime and failover regressions**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioIntelligenceAnswerAssemblerTest,PortfolioDomainContractTest,ConversationAnswerResponseTest,ConversationalAgentRuntimeTest,FailoverPortfolioRetrieverTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDomainContractTest.java
git commit -m "fix: preserve answer degradation metadata"
```

---

### Task 5: Make v2 Blocks Presence Authoritative In The Frontend

**Files:**
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts:18-95`
- Test: `frontend/src/features/agent/model/mapAnswerResponse.test.ts:179-253`

**Interfaces:**
- Produces: `response.blocks !== undefined` selects Blocks, including `[]`.
- Produces: blank-response validation inspects the same authoritative source used by `mapSections`.

- [ ] **Step 1: Add an empty-Blocks precedence test**

```ts
it('treats an explicitly empty blocks array as authoritative', () => {
  const mapped = mapAnswerResponse({
    ...response(),
    title: '作品集信息',
    blocks: [],
    sections: [{
      type: 'BACKGROUND',
      title: '旧章节',
      content: '不得回退展示',
      claimIds: ['legacy-claim'],
      evidenceIds: ['legacy-evidence'],
    }],
  })

  expect(mapped.sections).toEqual([])
  expect(mapped.evidenceIds).toEqual([])
})
```

Add a second test with blank title/Summary, `blocks: []`, and non-empty legacy sections; assert `Answer response has no content` because legacy content is not authoritative.

- [ ] **Step 2: Run the mapping test and verify RED**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/model/mapAnswerResponse.test.ts
```

Expected: first test maps legacy sections; second test does not reject the response.

- [ ] **Step 3: Implement one source-selection helper**

Introduce:

```ts
function hasV2Blocks(response: AnswerResponse): boolean {
  return response.blocks !== undefined
}
```

Use it in both blank checking and `mapSections`. For v2, inspect/map only Blocks; otherwise inspect/map legacy Sections. Keep typed-block diagnostics unchanged and sanitized.

- [ ] **Step 4: Run frontend unit and type gates**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/model/mapAnswerResponse.test.ts src/features/agent/components/ConversationThread.test.ts src/features/agent/model/evidenceDeskModel.test.ts
npm.cmd --prefix frontend run check
```

Expected: PASS.

- [ ] **Step 5: Commit Task 5**

```powershell
git add frontend/src/features/agent/model/mapAnswerResponse.ts frontend/src/features/agent/model/mapAnswerResponse.test.ts
git commit -m "fix: honor explicit empty answer blocks"
```

---

### Task 6: Restore Eval Unit Semantics And Make Offline Skip HTTP_E2E Per Layer

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalIntelligenceExecutor.java:64-100`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngine.java:44-65`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalIntelligenceExecutorTest.java:72-111`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngineTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/application/EvalHarnessTest.java:172-225`

**Interfaces:**
- Produces: only ANSWERED decisions are PASS in `EvalIntelligenceExecutor`; handled non-answer decisions are FAIL; execution exceptions remain ERROR.
- Produces: OFFLINE keeps the selected tracked cases but skips each `HTTP_E2E` layer at execution time; a mixed `INTELLIGENCE + HTTP_E2E` case still executes INTELLIGENCE and creates no HTTP observation.

- [ ] **Step 1: Tighten the existing Eval assertions before implementation**

Keep the existing INVALID_INPUT and NEEDS_CLARIFICATION FAIL assertions. Add NOT_SUPPORTED and CAPABILITY_UNAVAILABLE parameter cases, both expected FAIL with their disposition reason code. Keep `intelligenceLoopback...` expected ERROR.

In `EvalExecutionEngineTest`, create one mixed case with `INTELLIGENCE + HTTP_E2E`, register only an INTELLIGENCE recording executor, execute an OFFLINE plan, and assert exactly one INTELLIGENCE observation with no `EXECUTOR_MISSING`. Add a second HTTP_E2E-only case and assert it creates no observation. Keep `EvalRunPlannerTest.offlineModeIncludesAllTrackedCasesExceptExternalChallenge` unchanged: case selection and layer execution are separate responsibilities.

- [ ] **Step 2: Run the three failing Eval test classes**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=EvalIntelligenceExecutorTest,EvalExecutionEngineTest,EvalHarnessTest test
```

Expected: INVALID_INPUT and NEEDS_CLARIFICATION are PASS instead of FAIL; both pure and mixed HTTP_E2E layers produce EXECUTOR_MISSING during OFFLINE.

- [ ] **Step 3: Map disposition to observation status explicitly**

Add:

```java
private EvalObservationStatus status(PortfolioDisposition disposition) {
    return disposition == PortfolioDisposition.ANSWERED
            ? EvalObservationStatus.PASS
            : EvalObservationStatus.FAIL;
}
```

Use this in both material-present and material-empty branches. Preserve `ERROR` only for invalid executor input or caught runtime exceptions.

- [ ] **Step 4: Skip HTTP_E2E at the per-layer execution boundary**

In `EvalExecutionEngine.execute`, immediately after entering the layer loop and before executor lookup, add:

```java
if (layer == EvalLayer.HTTP_E2E && plan.getMode() == EvalRunMode.OFFLINE) {
    continue;
}
```

Do not silently skip HTTP_E2E in modes that are intended to run it. Preserve the existing Provider authorization skip. This layer-level rule is required because most answer cases contain both INTELLIGENCE and HTTP_E2E.

- [ ] **Step 5: Run Eval unit suite**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest='com.portfolio.agent.evaluation.*' test
```

If Maven Surefire does not expand the package pattern on Windows, run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=EvalIntelligenceExecutorTest,EvalRunPlannerTest,EvalHarnessTest,EvalCliTest,EvalExecutionEngineTest,DeterministicEvalGraderTest test
```

Expected: PASS with zero Eval unit failures.

- [ ] **Step 6: Commit Task 6**

```powershell
git add backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalIntelligenceExecutor.java backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngine.java backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalIntelligenceExecutorTest.java backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngineTest.java backend/src/test/java/com/portfolio/agent/evaluation/application/EvalHarnessTest.java
git commit -m "fix: restore offline evaluation semantics"
```

---

### Task 7: Close The Phase 1 Eval Dataset And Product E2E Matrix

**Files:**
- Modify: `governance/portfolio-governance/evaluation/cases/holdout/answer.v1.json`
- Modify: `frontend/e2e/support/publicApiMocks.ts:37-103`
- Modify: `frontend/e2e/portfolio.spec.ts:658-677,711-773`

**Interfaces:**
- Produces: one Phase 1 focused STATUS/BOUNDARY holdout case with valid public Claim/Evidence references.
- Produces: deterministic Mock scenarios for NOT_SUPPORTED, REJECTED and RECOMMENDATION.
- Preserves: Comparison remains an untyped legacy Block and does not enter the Phase 1 Composer shape.

- [ ] **Step 1: Add a focused STATUS/BOUNDARY holdout case**

Add `answer.sql-audit.status-limitations.phase1.001` using the public question “SQL 审计项目的最终状态和已知局限分别是什么？”. Require the delivered OUTCOME Claim and the published limitation Claims already referenced by the existing `status-boundary` and `limitations` cases. Use `INTELLIGENCE` and `HTTP_E2E`, `providerTrials: 3`, graders SUBJECT_MATCH、REQUIRED_CLAIMS、REFERENCE_INTEGRITY、RESOLUTION、ANSWER_QUALITY, and tag `phase-1-answer-composition`. Do not place expected Chinese answer text in the oracle.

- [ ] **Step 2: Fix the unsupported Mock scenario**

Add an `unsupported` predicate for performance-uplift questions. Map it to:

```ts
resolution: 'NOT_SUPPORTED'
constructionMode: 'EVIDENCE_COMPOSITION'
evidenceState: 'INSUFFICIENT'
summary: '当前公开内容中没有足够的已验证材料。'
blocks: [{
  sourceScope: 'PORTFOLIO',
  sectionType: 'BOUNDARY',
  title: '能力说明',
  content: '当前公开内容中没有足够的已验证材料。',
  claimIds: [],
  evidenceIds: [],
}]
```

Keep the separate generic boundary and rejected branches.

- [ ] **Step 3: Add a Recommendation legacy-path Mock and E2E**

Recognize `推荐两个适合后端面试展示的作品`. Return ANSWERED with an untyped Portfolio Block and a valid `portfolioRecommendation` containing two existing public items, stable order, matching `selectedPortfolioIds`, non-empty reasons/evidence, and a 64-hex `rec_` batch ID.

Add an E2E assertion that the recommendation answer displays “作品推荐 · 2 项”, both cards in backend order, and no typed Composer section heading. This is a regression test: it proves Recommendation remains on its established path.

- [ ] **Step 4: Run the targeted desktop and mobile tests**

```powershell
npm.cmd --prefix frontend run test:e2e -- --grep "unsupported and rejected|comparison answer|composed overview|focused verification|partial-gap|recommendation"
```

Expected: all selected tests pass on Chromium and mobile Chromium.

- [ ] **Step 5: Validate the evaluation manifest and run offline**

Use fresh, non-existing output directories:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 validate --manifest governance/portfolio-governance/evaluation/manifest.v1.json --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json --output-dir output/evaluation/phase1-closure-validate
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.ps1 -Manifest governance/portfolio-governance/evaluation/manifest.v1.json -Policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json -OutputDir output/evaluation/phase1-closure-offline -SkipInstall
```

Expected: both commands exit 0 and report PASS; offline contains no `EXECUTOR_MISSING` observations. Real Provider state remains INCOMPLETE unless separately authorized.

- [ ] **Step 6: Commit Task 7**

```powershell
git add governance/portfolio-governance/evaluation/cases/holdout/answer.v1.json frontend/e2e/support/publicApiMocks.ts frontend/e2e/portfolio.spec.ts
git commit -m "test: close phase one answer acceptance matrix"
```

---

### Task 8: Prove The PostgreSQL Claim Projection On A Real Database

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQueryIntegrationTest.java`

**Interfaces:**
- Consumes: actual Flyway `db/public` migrations and the actual public bundle imported by `PublicBundleDatabaseImporter`.
- Produces: a real PostgreSQL assertion that Category、statement、detail、achievement、contribution、verification basis/status、materiality、topics and approved Evidence survive import/query.

- [ ] **Step 1: Write the Testcontainers integration test**

Use the repository's established pattern:

```java
@Testcontainers(disabledWithoutDocker = true)
class JdbcPostgresFactPassageQueryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");
}
```

In `@BeforeEach`, clean/migrate `classpath:db/public`, create JdbcTemplate/TransactionTemplate, load the actual classpath public bundle with `PublicBundleLoader`, import it, and retain the returned release ID. Query `sql-audit` through `JdbcPostgresFactPassageQuery.findPassages`.

Assert the row for `claim-sql-audit-delivered` equals the corresponding bundle Claim projection field-for-field and that every returned evidence reference is APPROVED. Assert no row has a null semantic projection field and no Category was defaulted.

- [ ] **Step 2: Run without Docker and confirm an explicit skip, not a false PASS claim**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=JdbcPostgresFactPassageQueryIntegrationTest test
```

Expected with Docker unavailable: test is explicitly SKIPPED by Testcontainers. This result is not sufficient for Phase 1 completion.

- [ ] **Step 3: Run with Docker available and retain the PASS result**

Start Docker Desktop outside the test process, rerun the same command, and require `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 4: Run PostgreSQL unit regressions**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=JdbcPostgresFactPassageQueryTest,JdbcPostgresKnowledgeQueryTest,PostgresPortfolioRetrieverTest,PublicBundleDatabaseImporterTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 8**

```powershell
git add backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQueryIntegrationTest.java
git commit -m "test: verify answer projection on postgres"
```

---

### Task 9: Run Full Gates, Record Evidence And Correct Authoritative Status

**Files:**
- Modify: `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/13-Agent对话体验与智能编排改造路线图.md`
- Create: `docs/reports/agent-answer-composition-phase1-closure-2026-08-10.md`

**Interfaces:**
- Consumes: fresh outputs from all gates in this task; no historical PASS may substitute.
- Produces: one evidence report with command, date, exit code, test counts, skips and limitations.

- [ ] **Step 1: Run all backend and frontend deterministic gates**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
```

Expected: every command exits 0; backend has zero failures/errors; frontend has zero failed tests.

- [ ] **Step 2: Run full Mock Playwright and both Eval modes**

```powershell
npm.cmd --prefix frontend run test:e2e
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 validate --manifest governance/portfolio-governance/evaluation/manifest.v1.json --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json --output-dir output/evaluation/phase1-final-validate
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.ps1 -Manifest governance/portfolio-governance/evaluation/manifest.v1.json -Policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json -OutputDir output/evaluation/phase1-final-offline -SkipInstall
```

Expected: Playwright passes desktop and mobile projects; validate/offline both PASS; offline has zero hard errors and all blocking gates pass.

- [ ] **Step 3: Run the packaged-JAR release gate**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall -SkipDockerCheck
```

Expected: exit 0, including packaged JAR startup, static assets and JAR E2E. `-SkipDockerCheck` is permitted only because Task 8 separately supplied a real PostgreSQL PASS; if Task 8 did not run with Docker, remove `-SkipDockerCheck` and require the integrated Docker check.

- [ ] **Step 4: Write the closure report from actual outputs**

Create `docs/reports/agent-answer-composition-phase1-closure-2026-08-10.md` with a table containing each command, exit code, tests run/failed/skipped, verdict and evidence path. Record Provider as `INCOMPLETE (not authorized)` unless a separately authorized live probe was actually executed. Record Docker/PostgreSQL as PASS only when Task 8 had zero skips.

- [ ] **Step 5: Add the closure clarification to the existing Spec**

Append a short “Phase 1 closure clarification” section that records the five decisions at the top of this plan: exact Category filtering, STATUS/BOUNDARY gap wording, Boundary fact budgets, all-target-missing responsibility split, and explicit empty Blocks precedence. Do not rewrite the original design history.

- [ ] **Step 6: Correct status documents using evidence, not intent**

Before all gates pass, use “核心链路已实现，收口验证中”. Only after Steps 1–4 pass may the authoritative documents say “阶段 1 完成”. Include exact remaining limitations: real Provider INCOMPLETE is allowed for deterministic Phase 1 but is not a formal production-provider PASS.

- [ ] **Step 7: Verify scope and document consistency**

```powershell
git status --short
git diff --check
rg -n "阶段 1.*完成|阶段一.*完成|收口验证中|providerRealState|INCOMPLETE" docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/13-Agent对话体验与智能编排改造路线图.md docs/reports/agent-answer-composition-phase1-closure-2026-08-10.md
```

Expected: no whitespace errors; status statements agree with the report; `design-exploration/` remains outside the staged Phase 1 file set.

- [ ] **Step 8: Commit Task 9**

```powershell
git add docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/13-Agent对话体验与智能编排改造路线图.md docs/reports/agent-answer-composition-phase1-closure-2026-08-10.md
git commit -m "docs: close phase one answer composition"
```

---

## Deferred Non-Blocking Debt

The following review findings are intentionally not part of the Phase 1 completion gate:

1. `ConversationAnswerResult -> ConversationAnswerResponse -> Mapper` repeats roughly twenty metadata fields. Replacing them with a shared metadata value object changes broad public DTO construction and should be a dedicated compatibility refactor after Phase 1 is frozen.
2. Frontend must mirror backend section enums/order because the HTTP contract crosses languages. This plan removes duplicate backend order/title/gap definitions; generating TypeScript contract types from a schema is a separate tooling project.
3. `design-exploration/agent-clarification-comparison/` explores clarification/comparison work for later roadmap stages and must be reviewed or committed separately.

These items must be entered in the next maintenance backlog, but they do not justify keeping a functionally and evidentially complete Phase 1 open.

## Final Review Checklist

- [ ] ANSWERED single-subject FACT_LOOKUP reaches Composer in production runtime.
- [ ] PostgreSQL fallback preserves `degraded=true` through the successful composed response.
- [ ] Focused STATUS with missing LIMITATION creates one evidence-free controlled gap.
- [ ] Unrequested LEARNING/REFLECTION facts cannot satisfy a requested LIMITATION target.
- [ ] All requested targets missing returns NOT_SUPPORTED/INSUFFICIENT without unrelated citations.
- [ ] Overview facts, including Boundary, are capped at 3; Focused target facts are capped at 6.
- [ ] Overview has Summary; Focused has none.
- [ ] Every new immutable collection is defensively copied and unmodifiable; value equality/hashCode are tested.
- [ ] Every `PortfolioIntelligenceResult` copy method retains Focus and runtime metadata.
- [ ] `blocks: []` is authoritative and never falls back to legacy Sections.
- [ ] Comparison and Recommendation remain on their legacy paths and have E2E regressions.
- [ ] Eval non-answer decisions are FAIL, executor failures are ERROR, and offline skips HTTP_E2E-only work.
- [ ] Phase 1 holdout case is valid and contains no expected full answer text.
- [ ] Real PostgreSQL integration executes with zero skips.
- [ ] Backend, frontend, check, lint, build, code quality, architecture, privacy and desktop/mobile Mock E2E pass.
- [ ] `eval validate` and `eval offline` both PASS with fresh output directories.
- [ ] Packaged-JAR release verification passes.
- [ ] Status docs state exactly what current evidence proves; Provider not run remains INCOMPLETE.
- [ ] `design-exploration/` is not included in the Phase 1 commits.

## Plan Self-Review

- Spec coverage: Tasks 1–5 cover §8–§12; Tasks 6–7 cover §13–§14; Task 8 covers PostgreSQL parity in §14.1/§16.6; Task 9 closes §16 and roadmap gates.
- Placeholder scan: no implementation step depends on an unspecified handler, type, command or expected behavior.
- Type consistency: `AnswerFocus` retains Claim Categories; mapping to Section Types remains in `AnswerSectionMapping`; no task changes the public v2 Blocks or `AnswerSectionView` shapes.
- Scope consistency: Phase 1 code correctness and Phase 0/evaluation gate repair are separate tasks and meet only in Task 9's completion decision.
