# 模型主导 Agent P2 提议编译与最小降级 Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 P1 的不可信 `TurnProposal` 完整重绑定、编译并校验为现有 `SemanticTurnPlan`，同时建立不依赖关键词语义路由的最小安全降级边界；不把该链路接入公开请求。

**Architecture:** 领域层扩展受限 proposal 字段和主体 basis，`ProposalCompiler` 只读取当前输入、已验证公开目录与结构化上下文，返回闭集编译结果而不是直接执行。`ReferenceMatchPolicy` 统一 reviewed alias 与 PAGE_HINT 的 Unicode 规范化、锚点边界和冲突处理；`MinimalTurnFallback` 只处理已签名合同动作与精确唯一公开别名概览，不能重新猜测自然语言任务。P5 才允许 `DefaultTurnRouter` 调用这条链路。

**Tech Stack:** Java 21、Spring Boot、Jackson、JUnit 5、AssertJ、Maven。

## Global Constraints

- 不修改 `DefaultTurnRouter`、HTTP DTO、stp-v1/v2、Provider 配置、执行器或前端；P2 产物不得改变公开运行时行为。
- 禁止 `var`、`record`、Lombok；合同和结果对象使用显式不可变类与防御性集合复制。
- 运行时仅读取审核后的 public data 与不含事实的 `routing/page-reference-markers.v1.json`；不得读取私有内容或回显模型 payload。
- 不可信提议的任一 task、主体、锚点、依赖、排除项或字段组合非法时，整体拒绝，不删除坏 task 后继续执行。
- 只有服务端生成 taskId、planId、fingerprint、fulfillmentRole、sourceDomain、确认策略与闭集原因码；模型不得取得执行或事实扩张权限。
- 推荐候选域必须固定为全部公开 Project，Case 不可混排；未来 Case 推荐必须另立 `recommendationScope` 契约。
- Provider 仍默认关闭，P2 测试只使用值对象和 Fake/fixture，不调用网络或真实模型。

## File Structure

- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TurnProposal.java`：补齐 subject candidate、basis、任务受限字段、依赖与排除项闭集。
- `backend/src/main/java/com/portfolio/agent/answer/routing/gateway/TurnInterpretationPort.java`：补齐仅含公开目录和结构化上下文的输入投影。
- `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/model/TurnProposalCodec.java`：按新闭集字段严格 JSON 解码。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/ProposalCompiler.java`：不可信提议到未验证 `SemanticTurnPlan` 的唯一编译边界。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/ProposalCompilationResult.java`：可公开、无文本回显的 compiled / clarification / rejected 闭集结果。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/ReferenceMatchPolicy.java`：唯一 alias 与 PAGE_HINT 文本锚点匹配策略。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/PageReferenceMarkerCatalog.java`：版本化 marker 配置加载与 fail-closed 校验。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/MinimalTurnFallback.java`：受限确定性 fallback，尚不接入 Router。
- `backend/src/main/resources/routing/page-reference-markers.v1.json`：不携带作品集事实的审核 marker 配置。
- 对应 `backend/src/test/...`：领域、Codec、编译、目录、匹配和 fallback 测试。

## Task 1: 扩展 P1 受限提议与输入投影

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TurnProposal.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/gateway/TurnInterpretationPort.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/model/TurnProposalCodec.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/domain/TurnProposalTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/adapter/model/TurnProposalCodecTest.java`

**Interfaces:**
- Produces `SubjectCandidate(subjectType, subjectId, basis, evidenceAnchor?, resultSetId?, resultPosition?)`, local `ProposalDependency`, controlled exclusions, facets, dimensions, topic anchors, career track, capability filters, requested size, response mode and synthesis source task keys.
- Every `SubjectCandidate` must use a closed basis; `UNKNOWN` cannot be constructed or decoded. `TextAnchor` remains the sole model-supplied textual locator.

- [ ] **Step 1: Write failing proposal invariant tests**

```java
assertThatThrownBy(() -> new TurnProposal.SubjectCandidate(
        SubjectType.PROJECT, "project-a", SubjectBasis.UNKNOWN, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new TurnProposal.ProposalDependency(
        "task-a", "task-a", TaskDependencyType.REQUIRES_SUCCESS))
        .isInstanceOf(IllegalArgumentException.class);
```

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TurnProposalTest,TurnProposalCodecTest test`

- [ ] **Step 3: Add only closed, immutable fields and codec wire classes**

```java
public static final class SubjectCandidate {
    public SubjectCandidate(SubjectType subjectType, String subjectId, SubjectBasis basis,
            TextAnchor evidenceAnchor, String resultSetId, Integer resultPosition) {
        // basis matrix: explicit/page bases require an evidenceAnchor;
        // RECENT_RESULT requires positive resultPosition; no free evidence text.
    }
}
```

Reject unknown JSON, duplicate keys, unknown enums, basis-field mismatch, unsupported task-type fields and all execution identifiers.

- [ ] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=TurnProposalTest,TurnProposalCodecTest test`

## Task 2: ProposalCompiler 与整体拒绝结果

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/ProposalCompiler.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/ProposalCompilationResult.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/ProposalCompilerTest.java`

**Interfaces:**
- Consumes `TurnProposal`, `TurnInterpretationInput`, current public content version and `ReferenceMatchPolicy`.
- Produces `ProposalCompilationResult.compiled(SemanticTurnPlan)`, `.clarification(ProposalRejectionCode)` or `.rejected(ProposalRejectionCode)`; none retains Provider JSON or arbitrary user text.

- [ ] **Step 1: Write failing compilation tests**

```java
ProposalCompilationResult result = compiler.compile(validFactProposal(), input());
assertThat(result.getPlan()).isPresent();
assertThat(result.getPlan().orElseThrow().getSource()).isEqualTo(SemanticTurnPlan.PlanSource.MODEL);
assertThat(compiler.compile(proposalWithUnknownSubject(), input()).getPlan()).isEmpty();
assertThat(compiler.compile(proposalWithOneBadTask(), input()).getReasonCode())
        .isEqualTo(ProposalRejectionCode.SUBJECT_NOT_PUBLIC);
```

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ProposalCompilerTest test`

- [ ] **Step 3: Compile all seven task kinds and DAG edges without rescanning intent keywords**

```java
public ProposalCompilationResult compile(
        TurnProposal proposal, TurnInterpretationPort.TurnInterpretationInput input) {
    // validate every anchor and subject basis first; compile all tasks or return one closed failure.
    // create task-01... and a fresh server plan id only after validation succeeds.
}
```

Map `CONCISE` to `ExplanationDepth.BRIEF`; derive recommendation goal and synthesis goal server-side; derive fulfillment roles from synthesis dependencies. Bind subjects only to the supplied public catalog and create `VALIDATED_MODEL_CANDIDATE` references.

- [ ] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ProposalCompilerTest test`

## Task 3: 版本化 PAGE_HINT 目录与统一匹配策略

**Files:**
- Create: `backend/src/main/resources/routing/page-reference-markers.v1.json`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/PageReferenceMarkerCatalog.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/ReferenceMatchPolicy.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/PageReferenceMarkerCatalogTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/ReferenceMatchPolicyTest.java`

**Interfaces:**
- `ReferenceMatchPolicy.matches(TextAnchor, String currentInput, String alias)` normalizes NFKC/trim/`Locale.ROOT`, then verifies exact-anchor range and neighboring code-point boundaries.
- `PageReferenceMarkerCatalog.load(InputStream)` returns only complete configured markers with their `SubjectType`, or throws before any binding can occur.

- [ ] **Step 1: Write failing marker and alias boundary tests**

```java
assertThat(policy.matches(new TextAnchor("SQL", 1), "MySQL", "SQL")).isFalse();
assertThat(catalog.supports(new TextAnchor("这个项目", 1), SubjectType.PROJECT)).isTrue();
assertThat(catalog.supports(new TextAnchor("这个", 1), SubjectType.PROJECT)).isFalse();
```

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PageReferenceMarkerCatalogTest,ReferenceMatchPolicyTest test`

- [ ] **Step 3: Implement fail-closed loading and one matching implementation**

Use only configured complete phrases: Project `这个项目/该项目/当前项目/this project/the project`; Case `这个案例/该案例/当前案例/this case/the case`. Reject bare pronouns, malformed schema, duplicate normalized markers, wrong subject type, unsupported configuration version and unapproved short aliases. Same-type alias conflict fails catalog creation; cross-type ambiguity returns no binding for the compiler to turn into clarification.

- [ ] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PageReferenceMarkerCatalogTest,ReferenceMatchPolicyTest test`

## Task 4: Validator integration, Project-only recommendation and minimal fallback

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/MinimalTurnFallback.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/MinimalTurnFallbackTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidatorTest.java`

**Interfaces:**
- `MinimalTurnFallback` accepts only verified contract actions and a current input that is an exact unique reviewed alias; all other ordinary natural language returns `NOT_APPLICABLE`.
- Candidate recommendations are created from verified public Project references only; no Case reference can enter a `PortfolioRecommend` candidate set.

- [ ] **Step 1: Write failing safety tests**

```java
assertThat(fallback.resolve("介绍 project-a", publicCatalog()).getDisposition())
        .isEqualTo(MinimalTurnFallback.Disposition.NOT_APPLICABLE);
assertThat(fallback.resolve("project-a", publicCatalog()).getDisposition())
        .isEqualTo(MinimalTurnFallback.Disposition.EXACT_ALIAS_OVERVIEW);
assertThat(recommendationPlan().getTasks().getFirst().getSubjectReferences())
        .allMatch(subject -> subject.getSubjectType() == SubjectType.PROJECT);
```

- [ ] **Step 2: Run RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=MinimalTurnFallbackTest,SemanticPlanValidatorTest test`

- [ ] **Step 3: Implement only contract actions and exact alias fallback**

Do not add a natural-language keyword dictionary. Preserve validator DAG, fingerprint and exclusion checks; additionally require all compiled public subjects to exist at the input content version and reject Case recommendation candidates.

- [ ] **Step 4: Run GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=MinimalTurnFallbackTest,SemanticPlanValidatorTest test`

## Task 5: Contract regression and documentation

**Files:**
- Modify: `docs/00-文档状态索引.md`
- Modify after any actual runtime behavior change only: `docs/08-当前实现状态.md`, `docs/11-项目演进日志.md`

- [ ] **Step 1: Run P1/P2 contracts and full backend verification**

Run: `mvn.cmd -f backend/pom.xml test`, `scripts/code-quality-check.ps1 -Path backend`, `scripts/architecture-check.ps1 -Path backend/src/main/java`, `scripts/privacy-check.ps1 -Path backend`.

- [ ] **Step 2: Register actual isolated state**

Mark P2 as implemented-but-unwired only when all P2 tests pass. Do not claim MODEL_LED, Provider execution, stp-v3 or new browser behavior; do not update runtime status or evolution log unless a public behavior is actually wired.

## Plan Self-Review

- Proposal fields, subject binding, marker parsing, fallback and plan validation each have one bounded responsibility and their own RED/GREEN cycle.
- The plan creates no second keyword router: `MinimalTurnFallback` is exact alias/contract action only, and `ProposalCompiler` never scans input to infer task type.
- P3 Provider calls, P5 router wiring, P6 result sets and P7 transport remain outside this plan.
