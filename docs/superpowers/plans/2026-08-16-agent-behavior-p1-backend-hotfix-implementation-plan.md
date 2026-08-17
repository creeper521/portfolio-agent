# Agent 行为 P-1 后端热修 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复已审计的无意义输入路由、非回答来源投影和公开内容缓存缺口，而不改变前端或启用模型 Provider。

**Architecture:** 在现有确定性语义路由器中，以窄范围的“无法形成任务”判定将纯数字/符号输入送入既有 critical clarification 路径。响应 Mapper 在最终 HTTP 投影边界强制非回答结果不含 Evidence 或公开来源；公共内容端点则以 HTTP 缓存头防止浏览器存储快照。

**Tech Stack:** Java 21、Spring Boot、Maven、JUnit 5、AssertJ、MockMvc。

## Global Constraints

- 生产与测试 Java 不得使用 `var`、`record` 或 Lombok；值对象维持显式不可变类。
- 公共 API 只读且只返回 DTO；不得持久化或记录访客问题、回答、Evidence 原文、凭证或私有资料。
- 运行时只读取已审阅 public-data；本热修不得扩大公开事实范围。
- 每项行为严格 RED → GREEN → REFACTOR，先观察到失败测试再改生产代码。
- 不改 `frontend/`；不改变前端交互、视觉或 stp-v2 合同；不启用 `MODEL_LED` 或真实 Provider。
- 不执行 stage、commit 或 push；如需提交，必须由用户另行授权并使用中文提交信息。

---

## File Structure

- `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java`：只负责将可形成任务的输入归为通用解释；移除纯噪声的通用解释兜底。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/ClarificationRequest.java`：提供当前路由器可复用的安全澄清构造入口（仅在现有类型不足时修改）。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java`：以真实 `DefaultTurnRouter` 固化纯噪声路由和自然语言回归。
- `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java`：在最终响应映射处保留非回答所需的安全文案，但清除其 Claim、Evidence、公开来源、sourceComposition 与 publicSourceCatalog 投影。
- `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`：验证澄清/能力不可用不携带来源，ANSWER/PARTIAL 仍保留合法来源。
- `backend/src/main/java/com/portfolio/agent/portfolio/controller/PublicContentController.java`：为公共内容快照返回 `Cache-Control: no-store`。
- `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`：验证 HTTP 缓存头。
- `docs/08-当前实现状态.md`、`docs/11-项目演进日志.md`：记录真实行为变更与当前验证状态。

## Task 1: 无意义输入进入安全澄清

**Files:**
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java`
- Modify only if needed: `backend/src/main/java/com/portfolio/agent/answer/routing/service/ClarificationRequest.java`

**Interfaces:**
- Consumes: `DefaultTurnRouter.route(SemanticTurnInput)`。
- Produces: 对纯数字、纯符号或 emoji 的 `SemanticTurnDecision.Disposition.CLARIFICATION_REQUIRED`，且 `getValidatedPlan()` 为空；自然语言通用解释保持 `READY`。

- [ ] **Step 1: Write the failing test**

```java
@Test
void meaninglessNumericInputRequiresClarificationInsteadOfGeneralExplanation() {
    SemanticTurnDecision decision = router().route(SemanticTurnInput.ask("112233"));

    assertThat(decision.getDisposition())
            .isEqualTo(SemanticTurnDecision.Disposition.CLARIFICATION_REQUIRED);
    assertThat(decision.getValidatedPlan()).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn.cmd -f backend/pom.xml -Dtest=DefaultTurnRouterDeterministicTest#meaninglessNumericInputRequiresClarificationInsteadOfGeneralExplanation test`

Expected: FAIL because current `SemanticSignalCollector` creates `GENERAL_EXPLANATION` for `112233`.

- [ ] **Step 3: Write minimal implementation**

Add a private, deterministic predicate in `SemanticSignalCollector` which permits the `GENERAL_EXPLANATION` fallback only when the trimmed question contains at least one letter or Han code point. Keep existing explicit task detection unchanged. When no task goal remains, allow the existing router critical clarification logic to own the response. Do not add greeting dictionaries, model calls, or a new disposition.

- [ ] **Step 4: Run the focused route tests**

Run: `mvn.cmd -f backend/pom.xml -Dtest=DefaultTurnRouterDeterministicTest test`

Expected: PASS, including `preservesTheCurrentQuestionAsTheGeneralModelTopic`.

## Task 2: 非回答响应禁止投影来源

**Files:**
- Modify: `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java`

**Interfaces:**
- Consumes: `ConversationAnswerResponseMapper.toResponse(...)` 的既有 `AgentTurnResult` 与 `TaskOutcome` 输入。
- Produces: 澄清、边界、能力不可用和噪声/NOT_SUPPORTED 的响应可保留安全文案，但不含 Claim、Evidence、公开来源、sourceComposition 或 `publicSourceCatalog`；合法 ANSWER/PARTIAL 保留已有来源目录。

- [ ] **Step 1: Write the failing tests**

Use the existing `answerResult()` test fixture to create one stp-v2 non-answer outcome with a legal public source, then assert:

```java
assertThat(response.getBlocks()).allSatisfy(block -> {
    assertThat(block.getClaimIds()).isEmpty();
    assertThat(block.getEvidenceIds()).isEmpty();
    assertThat(block.getSourceReferences()).isEmpty();
});
assertThat(response.getPublicSourceCatalog()).isEmpty();
assertThat(response.getSourceComposition()).isNull();
```

Create a separate ANSWER or PARTIAL fixture containing the same approved source and assert:

```java
assertThat(response.getBlocks()).isNotEmpty();
assertThat(response.getPublicSourceCatalog()).isNotEmpty();
```

- [ ] **Step 2: Run mapper tests to verify they fail**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ConversationAnswerResponseMapperTest test`

Expected: the non-answer case FAILS because current mapper unconditionally projects stp-v2 blocks/catalog.

- [ ] **Step 3: Write minimal implementation**

Derive a single `isAnswerLike` boolean from the final public resolution (`ANSWERED` or `PARTIALLY_ANSWERED`). For all other outcomes, preserve any bounded safe block text but rebuild it without Claim IDs, Evidence IDs, source references or support metadata; return an empty catalog and `null` composition. Do not change DTO shape, accepted source validation, or legacy stp-v1 projection.

- [ ] **Step 4: Run mapper tests**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ConversationAnswerResponseMapperTest test`

Expected: PASS for the new non-answer invariant and retained answer/partial catalogue test.

## Task 3: 公开内容响应禁缓存

**Files:**
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/controller/PublicContentController.java`

**Interfaces:**
- Consumes: `GET /api/v1/public-content`。
- Produces: HTTP 200 DTO body plus exact `Cache-Control: no-store` header.

- [ ] **Step 1: Write the failing test**

Add to `returnsCompleteReviewedPublicContent`:

```java
.andExpect(header().string("Cache-Control", "no-store"))
```

and import `MockMvcResultMatchers.header`.

- [ ] **Step 2: Run controller test to verify it fails**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioControllerTest#returnsCompleteReviewedPublicContent test`

Expected: FAIL because current endpoint sets no cache-control directive.

- [ ] **Step 3: Write minimal implementation**

Return `ResponseEntity<PublicContentResponse>` from `getPublicContent`, using `CacheControl.noStore()` and the unchanged mapper-produced DTO body.

- [ ] **Step 4: Run controller tests**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioControllerTest test`

Expected: PASS, retaining all reviewed-public-content assertions.

## Task 4: 回归与文档状态

**Files:**
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Consumes: Tasks 1–3 verified behavior.
- Produces: 真实的 P-1 行为与验证状态记录；不把 P4/P7/Provider 功能写成已实现。

- [ ] **Step 1: Inspect the complete behavior and documentation context**

Run focused tests for Tasks 1–3, then read the relevant current-state sections before editing so the change is placed with the actual Agent/API capability inventory.

- [ ] **Step 2: Update documentation**

In `docs/08-当前实现状态.md`, state that current deterministic routing sends non-task pure noise to existing clarification rather than general explanation, non-answer HTTP responses do not project sources, and the public-content snapshot is no-store. In `docs/11-项目演进日志.md`, add one dated entry describing this safety/behavior correction and that no Provider/default-mode/frontend scope changed.

- [ ] **Step 3: Run full required backend checks**

Run:

```powershell
mvn.cmd -f backend/pom.xml test
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
mvn.cmd -f backend/pom.xml package
```

Expected: record each command as PASS, FAIL, or BLOCKED with actual output. Environment failures (missing Maven/JDK) must not be reported as product test failures.

## Plan Self-Review

- Spec coverage: Task 1 covers P-1 pure-noise routing; Task 2 covers the final non-answer evidence/source invariant and answer/partial preservation; Task 3 covers `no-store`; Task 4 covers required behavior documentation and complete backend verification.
- Intentional exclusions: front-end semanticContext and Case clearing remain owned by the front-end handoff; conversational recovery, stp-v3, Provider calls, and MODEL_LED remain later phases.
- Placeholder scan: no deferred implementation placeholder is used; each task names files, expected behavior, and executable verification.
- Type consistency: all described types and method names are existing seams, except a private predicate that remains internal to `SemanticSignalCollector`.
