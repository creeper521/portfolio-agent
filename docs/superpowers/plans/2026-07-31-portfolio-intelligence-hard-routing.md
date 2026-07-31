# Agent 作品集智能检索与确定性推荐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将普通作品集 RAG、比较、确定性推荐和推荐调整统一收进 Agent 内部的 `PortfolioIntelligence` 硬路由模块，以 PostgreSQL/pgvector 为主检索 Adapter、Bundle 为降级 Adapter，并删除公开 Selection 接口。

**Architecture:** `/api/v2/answers` 仍是唯一用户入口。规则优先的任务解析器把自然语言转换为受校验的 `PortfolioTask`，模型只在规则不能唯一判断时补充分类；`PortfolioIntelligence.resolve(PortfolioTask)` 通过内部硬路由调用统一 `PortfolioRetriever` seam、证据组装和确定性推荐策略。推荐结果通过可选 `portfolioRecommendation` 返回，前端源码不在本计划修改范围内。

**Tech Stack:** Java 21、Spring Boot、Spring JDBC、PostgreSQL 16、pgvector 0.8.5、JUnit 5、Mockito、MockMvc、Maven。

## Global Constraints

- 只修改后端、测试和交接文档；不得修改 `frontend/src`。
- 唯一公开入口是 `POST /api/v2/answers`；不得新增 Selection 页面、路由或请求端点。
- 删除 `/api/portfolio-selections`，不保留兼容期。
- 模型不得生成或选择 SQL、检索 Adapter、推荐策略及最终作品 ID。
- 硬路由必须强制执行公开 Release、已验证 Claim、已批准 Evidence 和隐私门禁。
- PostgreSQL/pgvector 健康时为主 Adapter；失败时自动降级到 Bundle，并设置 `degraded=true`。
- 作品集任务模型分类默认阈值为 `0.80`；低于阈值进入追问。
- 推荐数量默认 3，有效范围为 2 到 5。
- 推荐上下文使用随机 `rec_<32位十六进制>` 标识，保存 30 分钟，最多 1000 条，仅限单实例。
- 非推荐回答省略 `portfolioRecommendation`，不得序列化为 `null`。
- 所有实现提交使用中文提交信息；每个任务单独提交。
- 保留无关的工作区改动，不执行 `reset --hard`、`checkout --` 或批量清理。

---

## File Structure

### 新建领域与深模块

- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskMode.java`：硬路由枚举。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioConditions.java`：可部分填写、可合并的推荐条件。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/RecommendationRefinement.java`：替换位置和新增偏好。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTask.java`：Agent 传给深模块的唯一任务输入。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskClassification.java`：受约束模型分类结果。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRecommendation.java`：稳定结构化推荐。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRecommendationItem.java`：推荐项。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioClarification.java`：单问题追问。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioIntelligenceResult.java`：深模块唯一结果。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrieval*.java`：统一检索请求、结果、来源、Subject 和 Passage。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetriever.java`：检索 seam。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetrievalException.java`：只表示主检索基础设施不可用。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioTaskClassifierPort.java`：受约束模型分类 seam。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolver.java`：规则优先任务解析。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskValidator.java`：任务条件校验。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskValidation.java`：校验结果和固定原因码。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioIntelligence.java`：唯一外部接口。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`：深模块实现及模式到内部处理器的确定性硬路由。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioRecommendationPolicy.java`：包装现有选择策略。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RecommendationContextRegistry.java`：有界、过期的批次上下文。

### 新建 Adapter

- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetriever.java`：从公开 Bundle 构造降级结果。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresKnowledgeQuery.java`：事实与比较检索 SQL 接口。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQuery.java`：PostgreSQL FTS/向量实现。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresPortfolioRetriever.java`：把 PostgreSQL 查询和现有候选召回映射到统一 seam。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/FailoverPortfolioRetriever.java`：主从 Adapter 切换。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`：Spring 装配。

### 修改现有 Agent 链路

- `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerContextRequest.java`：增加可选 `recommendationBatchId`。
- `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`：保存可选推荐并在 `withGuidance()` 中保留。
- `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`：输出可选推荐。
- `backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationResponse.java`：推荐响应 DTO。
- `backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationItemResponse.java`：推荐项 DTO。
- `backend/src/main/java/com/portfolio/agent/answer/gateway/ConversationalModelPort.java`：不修改；作品集分类使用独立 seam。
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapter.java`：实现 `PortfolioTaskClassifierPort`。
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactory.java`：增加受约束作品集任务分类契约。
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ProviderOperation.java`：增加 `CLASSIFY_PORTFOLIO_TASK`。
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentProperties.java`：增加 `minimumPortfolioIntentConfidence=0.80`。
- `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`：在模型生成前调用硬路由模块。
- `backend/src/main/java/com/portfolio/agent/answer/service/ConversationIntentRouter.java`：拆出不调用模型的安全/时效/寒暄判断。
- `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`：注入新模块。

### 删除公开 Selection 表面

- 删除 `backend/src/main/java/com/portfolio/agent/selection/controller/PortfolioSelectionController.java`。
- 删除 `backend/src/main/java/com/portfolio/agent/selection/dto/` 下仅服务公开 Selection HTTP 的 DTO。
- 删除 `backend/src/main/java/com/portfolio/agent/selection/mapper/PortfolioSelectionResponseMapper.java`。
- 删除对应 Controller、Mapper 和配置测试。
- 保留 `selection/domain`、`selection/service/SelectionStrategy`、`TopKSelectionStrategy`、`ExhaustiveSelectionStrategy`、PostgreSQL 候选算法和 `selection/benchmark`。

### 文档交付

- `docs/handoffs/agent-portfolio-recommendation-frontend.md`：前端字段、示例、交互和联调说明。
- `docs/08-当前实现状态.md`：更新唯一入口和模块状态。
- `docs/11-项目演进日志.md`：记录迁移及验证命令。

---

### Task 1: 固定作品集任务与推荐领域契约

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskMode.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioConditions.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/RecommendationRefinement.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTask.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskClassification.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRecommendationItem.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRecommendation.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioClarification.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioIntelligenceResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerContextRequest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioIntelligenceModelContractTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java`

**Interfaces:**
- Produces: `PortfolioTaskMode`, `PortfolioConditions`, `PortfolioTask`, `PortfolioTaskClassification`, `PortfolioRecommendation`, `PortfolioIntelligenceResult`.
- Consumes: existing `ConversationAnswerBlock` and request `AudienceRole` value through a string mapping.

- [ ] **Step 1: Write failing model contract tests**

```java
@Test
void conditionsNormalizeAndMergeRefinementOverrides() {
    PortfolioConditions base = new PortfolioConditions(
            " BACKEND ", "ENGINEERING_MANAGER", Set.of("java"), "展示工程能力", 3);
    PortfolioConditions override = new PortfolioConditions(
            null, null, Set.of("postgresql"), "更偏后端", null);

    PortfolioConditions merged = base.merge(override);

    assertThat(merged.careerTrack()).isEqualTo("BACKEND");
    assertThat(merged.audienceRole()).isEqualTo("ENGINEERING_MANAGER");
    assertThat(merged.capabilityCodes()).containsExactlyInAnyOrder("java", "postgresql");
    assertThat(merged.goal()).isEqualTo("更偏后端");
    assertThat(merged.requestedSize()).isEqualTo(3);
}

@Test
void requestContextAcceptsOnlyOpaqueRecommendationBatchId() {
    assertThat(validator.validate(context("rec_0123456789abcdef0123456789abcdef")))
            .isEmpty();
    assertThat(validator.validate(context("../internal")))
            .extracting(ConstraintViolation::getMessage)
            .contains("recommendationBatchId format is invalid");
}
```

- [ ] **Step 2: Run tests and verify the new types are missing**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioIntelligenceModelContractTest,ConversationAnswerRequestTest test
```

Expected: compilation fails because `PortfolioConditions` and the five-argument context constructor do not exist.

- [ ] **Step 3: Add the exact task modes and immutable condition merge**

```java
public enum PortfolioTaskMode {
    FACT_LOOKUP,
    COMPARISON,
    RECOMMENDATION,
    REFINE_RECOMMENDATION,
    CLARIFICATION_REQUIRED
}
```

```java
public record PortfolioConditions(
        String careerTrack,
        String audienceRole,
        Set<String> capabilityCodes,
        String goal,
        Integer requestedSize
) {
    public PortfolioConditions {
        careerTrack = normalize(careerTrack);
        audienceRole = normalize(audienceRole);
        capabilityCodes = capabilityCodes == null
                ? Set.of()
                : capabilityCodes.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        goal = normalize(goal);
        if (requestedSize != null && (requestedSize < 2 || requestedSize > 5)) {
            throw new IllegalArgumentException("requestedSize must be between 2 and 5");
        }
    }

    public PortfolioConditions merge(PortfolioConditions override) {
        Set<String> capabilities = new LinkedHashSet<>(capabilityCodes);
        capabilities.addAll(override.capabilityCodes);
        return new PortfolioConditions(
                override.careerTrack == null ? careerTrack : override.careerTrack,
                override.audienceRole == null ? audienceRole : override.audienceRole,
                capabilities,
                override.goal == null ? goal : override.goal,
                override.requestedSize == null ? requestedSize : override.requestedSize);
    }

    public int resolvedRequestedSize() {
        return requestedSize == null ? 3 : requestedSize;
    }

    public static PortfolioConditions empty() {
        return new PortfolioConditions(null, null, Set.of(), null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
```

- [ ] **Step 4: Add the task, refinement, recommendation and result records**

```java
public record RecommendationRefinement(Integer replaceIndex) {
    public RecommendationRefinement {
        if (replaceIndex != null && replaceIndex < 0) {
            throw new IllegalArgumentException("replaceIndex must be zero-based");
        }
    }
}

public record PortfolioTask(
        String turnId,
        String question,
        PortfolioTaskMode mode,
        double confidence,
        PortfolioConditions conditions,
        String recommendationBatchId,
        RecommendationRefinement refinement
) {
    public PortfolioTask {
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(conditions, "conditions");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }
}

public record PortfolioTaskClassification(
        PortfolioTaskMode mode,
        double confidence,
        PortfolioConditions conditions,
        RecommendationRefinement refinement
) {
    public PortfolioTaskClassification {
        conditions = conditions == null ? PortfolioConditions.empty() : conditions;
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0 and 1");
        }
    }
}

public record PortfolioRecommendationItem(
        String portfolioId,
        String title,
        String route,
        List<String> matchReasons,
        List<String> evidenceIds
) {
    public PortfolioRecommendationItem {
        matchReasons = List.copyOf(matchReasons);
        evidenceIds = List.copyOf(evidenceIds);
    }
}

public record PortfolioRecommendation(
        String recommendationBatchId,
        List<PortfolioRecommendationItem> items,
        List<String> satisfiedConstraints,
        List<String> unsatisfiedConstraints
) {
    public PortfolioRecommendation {
        items = List.copyOf(items);
        satisfiedConstraints = List.copyOf(satisfiedConstraints);
        unsatisfiedConstraints = List.copyOf(unsatisfiedConstraints);
    }
}
```

```java
public record PortfolioClarification(String code, String question) {
    public PortfolioClarification {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(question, "question");
    }
}

public record PortfolioIntelligenceResult(
        PortfolioTaskMode mode,
        String contentVersion,
        String title,
        List<ConversationAnswerBlock> blocks,
        PortfolioRecommendation recommendation,
        PortfolioClarification clarification,
        boolean degraded,
        String noticeCode
) {
    public PortfolioIntelligenceResult {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(title, "title");
        blocks = List.copyOf(blocks);
    }
}
```

- [ ] **Step 5: Extend request context without breaking existing constructors**

```java
@Pattern(
        regexp = "[A-Za-z0-9_-]{1,100}",
        message = "recommendationBatchId format is invalid")
private final String recommendationBatchId;

@JsonCreator
public ConversationAnswerContextRequest(
        @JsonProperty("projectSlug") String projectSlug,
        @JsonProperty("caseSlug") String caseSlug,
        @JsonProperty("audienceRole") AudienceRole audienceRole,
        @JsonProperty("source") AnswerRequestSource source,
        @JsonProperty("coveredTopics") List<ConversationTopic> coveredTopics,
        @JsonProperty("recommendationBatchId") String recommendationBatchId
) {
    this.projectSlug = projectSlug;
    this.caseSlug = caseSlug;
    this.audienceRole = audienceRole;
    this.source = source;
    this.coveredTopics = coveredTopics == null ? List.of() : List.copyOf(coveredTopics);
    this.recommendationBatchId = recommendationBatchId;
}

public String getRecommendationBatchId() {
    return recommendationBatchId;
}
```

Existing four- and five-argument constructors must delegate with `recommendationBatchId=null`.

- [ ] **Step 6: Run focused tests**

Run the command from Step 2.

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/domain `
  backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerContextRequest.java `
  backend/src/test/java/com/portfolio/agent/answer/intelligence/domain `
  backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java
git commit -m "领域模型：增加作品集智能任务与推荐契约"
```

---

### Task 2: 实现规则优先、模型受约束补充的任务解析

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioTaskClassifierPort.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolver.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ProviderOperation.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactory.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentProperties.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactoryTest.java`

**Interfaces:**
- Consumes: Task 1 `PortfolioTask`, `PortfolioTaskClassification`, request context and `ConversationWindow`.
- Produces: `Optional<PortfolioTask> PortfolioTaskResolver.resolve(request, window, allowModel)`.
- Produces: `ConversationModelResult<PortfolioTaskClassification> classifyPortfolioTask(String question, ConversationWindow window, String audienceRole)`.

- [ ] **Step 1: Write failing rule-priority tests**

```java
@Test
void explicitRecommendationUsesRulesWithoutCallingModel() {
    PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
    PortfolioTaskResolver resolver = new PortfolioTaskResolver(classifier, 0.80);

    Optional<PortfolioTask> task = resolver.resolve(
            request("请给面试官推荐 3 个偏后端的项目", null),
            window(),
            true);

    assertThat(task).get().extracting(PortfolioTask::mode)
            .isEqualTo(PortfolioTaskMode.RECOMMENDATION);
    verifyNoInteractions(classifier);
}

@Test
void ambiguousInputUsesModelButLowConfidenceRequiresClarification() {
    PortfolioTaskClassifierPort classifier = (question, window, audienceRole) ->
            ConversationModelResult.success(new PortfolioTaskClassification(
                    PortfolioTaskMode.COMPARISON,
                    0.79,
                    new PortfolioConditions(null, audienceRole, Set.of(), null, null),
                    null));
    PortfolioTask task = new PortfolioTaskResolver(classifier, 0.80)
            .resolve(request("这几个怎么选", null), window(), true)
            .orElseThrow();

    assertThat(task.mode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
}

@Test
void refineRuleRequiresBatchAndExtractsZeroBasedOrdinal() {
    PortfolioTask task = resolver.resolve(
            request("换掉第二个", "rec_0123456789abcdef0123456789abcdef"),
            window(),
            false).orElseThrow();

    assertThat(task.mode()).isEqualTo(PortfolioTaskMode.REFINE_RECOMMENDATION);
    assertThat(task.refinement().replaceIndex()).isEqualTo(1);
}
```

- [ ] **Step 2: Run resolver tests and confirm failure**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioTaskResolverTest test
```

Expected: compilation fails because the resolver and classifier seam do not exist.

- [ ] **Step 3: Add the classifier seam and deterministic rules**

```java
@FunctionalInterface
public interface PortfolioTaskClassifierPort {
    ConversationModelResult<PortfolioTaskClassification> classifyPortfolioTask(
            String question,
            ConversationWindow window,
            String audienceRole);
}
```

`PortfolioTaskResolver` must apply this exact order:

```java
public Optional<PortfolioTask> resolve(
        ConversationAnswerRequest request,
        ConversationWindow window,
        boolean allowModel
) {
    String question = request.getQuestion().strip();
    PortfolioConditions conditions = extractConditions(question, request);
    String batchId = request.getContext().getRecommendationBatchId();

    if (batchId != null && isRefinement(question)) {
        return Optional.of(task(request, PortfolioTaskMode.REFINE_RECOMMENDATION,
                1.0, conditions, extractRefinement(question)));
    }
    if (isRecommendation(question)) {
        return Optional.of(task(request, PortfolioTaskMode.RECOMMENDATION,
                1.0, conditions, null));
    }
    if (isComparison(question)) {
        return Optional.of(task(request, PortfolioTaskMode.COMPARISON,
                1.0, conditions, null));
    }
    if (hasPortfolioSubjectHint(request) || isPortfolioFact(question)) {
        return Optional.of(task(request, PortfolioTaskMode.FACT_LOOKUP,
                1.0, conditions, null));
    }
    if (!allowModel) {
        return Optional.empty();
    }
    ConversationModelResult<PortfolioTaskClassification> classified =
            classifier.classifyPortfolioTask(
                    question, window, request.getContext().getAudienceRole().name());
    if (classified == null || !classified.isSuccessful()
            || classified.getValue().mode() == null) {
        return Optional.empty();
    }
    PortfolioTaskClassification candidate = classified.getValue();
    PortfolioTaskMode mode = candidate.confidence() < minimumConfidence
            ? PortfolioTaskMode.CLARIFICATION_REQUIRED
            : candidate.mode();
    return Optional.of(task(
            request,
            mode,
            candidate.confidence(),
            conditions.merge(candidate.conditions()),
            candidate.refinement()));
}

private PortfolioTask task(
        ConversationAnswerRequest request,
        PortfolioTaskMode mode,
        double confidence,
        PortfolioConditions conditions,
        RecommendationRefinement refinement
) {
    return new PortfolioTask(
            request.getTurnId(),
            request.getQuestion().strip(),
            mode,
            confidence,
            conditions,
            request.getContext().getRecommendationBatchId(),
            refinement);
}
```

规则词表至少覆盖“推荐、筛选、选出、组合”“比较、对比、区别”“换掉、第一个、第二个、再偏、数量改成”，数量只接受 2 到 5。词表必须是不可变常量；规则结果不接受模型覆盖。

- [ ] **Step 4: Add a separate provider operation and strict JSON contract**

```java
CLASSIFY_PORTFOLIO_TASK("portfolio_task"),
```

```java
public String portfolioTaskPrompt(Object conversation, Object approvedContext) {
    return prompt("portfolio_task", conversation, approvedContext);
}
```

`outputContract("portfolio_task")` 必须明确只允许：

```text
mode: FACT_LOOKUP|COMPARISON|RECOMMENDATION|REFINE_RECOMMENDATION|null
confidence: 0 到 1
conditions: careerTrack、audienceRole、capabilityCodes、goal、requestedSize
refinement: null 或只包含 replaceIndex
```

同时明确禁止输出 SQL、检索器、策略名和作品 ID。Adapter 的副本 `ObjectMapper` 已启用 `FAIL_ON_UNKNOWN_PROPERTIES`，新增字段会使分类失败。

- [ ] **Step 5: Implement the model Adapter method at temperature 0**

```java
@Override
public ConversationModelResult<PortfolioTaskClassification> classifyPortfolioTask(
        String question,
        ConversationWindow window,
        String audienceRole
) {
    Map<String, Object> approved = Map.of("audienceRole", audienceRole);
    return post(
            ProviderOperation.CLASSIFY_PORTFOLIO_TASK,
            () -> promptFactory.portfolioTaskPrompt(
                    conversation(question, window),
                    approved),
            objectMapper.constructType(PortfolioTaskClassification.class),
            0.0);
}
```

`OpenAiCompatibleConversationalModelAdapter` 的 implements 列表增加 `PortfolioTaskClassifierPort`。

- [ ] **Step 6: Add the dedicated confidence property**

```java
private double minimumPortfolioIntentConfidence = 0.80;

public double getMinimumPortfolioIntentConfidence() {
    return minimumPortfolioIntentConfidence;
}

public void setMinimumPortfolioIntentConfidence(double value) {
    if (value < 0.0 || value > 1.0) {
        throw new IllegalArgumentException(
                "minimumPortfolioIntentConfidence must be between 0 and 1");
    }
    this.minimumPortfolioIntentConfidence = value;
}
```

- [ ] **Step 7: Run resolver and model Adapter tests**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioTaskResolverTest,OpenAiCompatibleConversationalModelAdapterTest,ConversationalPromptFactoryTest test
```

Expected: `BUILD SUCCESS`; tests verify rule-first no-call, threshold, timeout, illegal field rejection and provider operation diagnostic value.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence `
  backend/src/main/java/com/portfolio/agent/answer/adapter/model `
  backend/src/test/java/com/portfolio/agent/answer/intelligence `
  backend/src/test/java/com/portfolio/agent/answer/adapter/model
git commit -m "智能路由：增加规则优先的作品集任务识别"
```

---

### Task 3: 建立统一检索 seam 和 Bundle Adapter

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalPurpose.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalSource.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievedPassage.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievedSubject.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetriever.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetrievalException.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetriever.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetrieverContractTest.java`

**Interfaces:**
- Produces: one method `PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request)`.
- Consumes: `PublicPortfolioRepository.getSnapshot()` and Task 1 `PortfolioConditions`.

- [ ] **Step 1: Write the shared Adapter contract tests**

```java
static Stream<PortfolioRetriever> adapters() {
    return Stream.of(bundleRetriever(fixtureSnapshot()));
}

@ParameterizedTest
@MethodSource("adapters")
void returnsOnlyVerifiedClaimsWithApprovedEvidence(PortfolioRetriever retriever) {
    PortfolioRetrievalResult result = retriever.retrieve(new PortfolioRetrievalRequest(
            PortfolioRetrievalPurpose.FACT_LOOKUP,
            "PostgreSQL 验证",
            Set.of("portfolio-postgres"),
            new PortfolioConditions(null, "ENGINEERING_MANAGER", Set.of(), null, null),
            8));

    assertThat(result.subjects()).isNotEmpty();
    assertThat(result.subjects())
            .flatExtracting(PortfolioRetrievedSubject::passages)
            .allSatisfy(passage -> {
                assertThat(passage.claimIds()).isNotEmpty();
                assertThat(passage.evidenceIds()).isNotEmpty();
            });
}

@Test
void bundleOrderIsDeterministic() {
    PortfolioRetrievalResult first = retriever.retrieve(request);
    PortfolioRetrievalResult second = retriever.retrieve(request);
    assertThat(second.subjects()).isEqualTo(first.subjects());
}
```

- [ ] **Step 2: Run the contract test and verify missing types**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioRetrieverContractTest,BundlePortfolioRetrieverTest test
```

Expected: compilation fails because the seam and Adapter are absent.

- [ ] **Step 3: Add the exact seam models**

```java
public enum PortfolioRetrievalPurpose {
    FACT_LOOKUP,
    COMPARISON,
    RECOMMENDATION
}

public enum PortfolioRetrievalSource {
    POSTGRES_HYBRID,
    POSTGRES_FTS,
    BUNDLE
}

public record PortfolioRetrievalRequest(
        PortfolioRetrievalPurpose purpose,
        String query,
        Set<String> subjectSlugs,
        PortfolioConditions conditions,
        int limit
) {
    public PortfolioRetrievalRequest {
        subjectSlugs = Set.copyOf(subjectSlugs);
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
    }
}

public record PortfolioRetrievedPassage(
        String passageId,
        String text,
        List<String> claimIds,
        List<String> evidenceIds,
        double score
) {
    public PortfolioRetrievedPassage {
        claimIds = List.copyOf(claimIds);
        evidenceIds = List.copyOf(evidenceIds);
    }
}

public record PortfolioRetrievedSubject(
        String subjectId,
        String subjectKind,
        String slug,
        String title,
        String summary,
        String route,
        String careerTrack,
        Set<String> capabilityCodes,
        List<PortfolioRetrievedPassage> passages,
        double score
) {
    public PortfolioRetrievedSubject {
        capabilityCodes = Set.copyOf(capabilityCodes);
        passages = List.copyOf(passages);
    }
}

public record PortfolioRetrievalResult(
        String contentVersion,
        PortfolioRetrievalSource source,
        boolean degraded,
        List<PortfolioRetrievedSubject> subjects,
        String noticeCode
) {
    public PortfolioRetrievalResult {
        subjects = List.copyOf(subjects);
    }

    public PortfolioRetrievalResult withDegradation(String code) {
        return new PortfolioRetrievalResult(
                contentVersion,
                source,
                true,
                subjects,
                code);
    }
}
```

```java
@FunctionalInterface
public interface PortfolioRetriever {
    PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request);
}

public final class PortfolioRetrievalException extends RuntimeException {
    public PortfolioRetrievalException(String message) {
        super(message);
    }

    public PortfolioRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Implement Bundle filtering and deterministic scoring**

`BundlePortfolioRetriever` must:

1. Read one `RuntimeContentSnapshot`.
2. Build `evidenceById` from `EvidenceStatus.APPROVED` and `rawContentPublic=false`.
3. Accept only `ClaimVerificationStatus.VERIFIED` claims having an approved direct evidence link.
4. Derive capability codes from verified Claim topics, matching the database importer.
5. Tokenize the query with `[\\p{L}\\p{N}_-]+`.
6. Score title match `3.0`、summary match `2.0`、claim text match `1.0`、requested capability match `2.0`.
7. Sort by score descending and then `subjectId` ascending.

The public method must return:

```java
return new PortfolioRetrievalResult(
        snapshot.getContentVersion(),
        PortfolioRetrievalSource.BUNDLE,
        true,
        rankedSubjects.stream().limit(request.limit()).toList(),
        "POSTGRES_RETRIEVAL_UNAVAILABLE");
```

The Adapter must never include raw evidence content; passage text comes from public Claim statement/detail and public subject summary.

- [ ] **Step 5: Run Bundle contract tests**

Run the command from Step 2.

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/domain `
  backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway `
  backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle `
  backend/src/test/java/com/portfolio/agent/answer/intelligence
git commit -m "检索模块：建立作品集统一检索接口与本地降级"
```

---

### Task 4: 增加 PostgreSQL/pgvector 事实检索 Adapter

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresKnowledgeRow.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresKnowledgeQuery.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQuery.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresPortfolioRetriever.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQueryTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresPortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQueryIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 `PortfolioRetriever`.
- Consumes: existing `PostgresHybridCandidateRetriever` for recommendation candidates and `LocalEmbeddingPort`.
- Produces: PostgreSQL implementations for FACT_LOOKUP、COMPARISON and RECOMMENDATION through the same seam.

- [ ] **Step 1: Write failing SQL shape and fusion tests**

```java
@Test
void factLookupUsesActiveReleaseAndPublicEvidenceGate() {
    query.searchFts("release-id", "PostgreSQL 验证", Set.of("portfolio-postgres"), 8);

    String sql = jdbc.lastSql();
    assertThat(sql)
            .contains("JOIN active_release")
            .contains("c.verification_status = 'VERIFIED'")
            .contains("e.public_status = 'APPROVED'")
            .contains("rd.search_vector @@");
}

@Test
void vectorFailureFallsBackToPostgresFtsWithoutUsingBundle() {
    when(embeddingPort.embedQuery(anyString()))
            .thenThrow(new LocalEmbeddingFailureException("MODEL_UNAVAILABLE"));

    PortfolioRetrievalResult result = retriever.retrieve(factRequest());

    assertThat(result.source()).isEqualTo(PortfolioRetrievalSource.POSTGRES_FTS);
    assertThat(result.degraded()).isTrue();
    assertThat(result.noticeCode()).isEqualTo("VECTOR_RETRIEVAL_UNAVAILABLE");
}
```

- [ ] **Step 2: Run focused PostgreSQL tests and verify failure**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=JdbcPostgresKnowledgeQueryTest,PostgresPortfolioRetrieverTest test
```

Expected: compilation fails because the PostgreSQL knowledge query does not exist.

- [ ] **Step 3: Define the query interface**

```java
public interface PostgresKnowledgeQuery {
    ActiveRelease activeRelease();

    List<PostgresKnowledgeRow> searchFts(
            String releaseId,
            String query,
            Set<String> subjectSlugs,
            int limit);

    List<PostgresKnowledgeRow> searchVector(
            String releaseId,
            float[] embedding,
            Set<String> subjectSlugs,
            int limit);
}
```

```java
public record PostgresKnowledgeRow(
        String releaseVersion,
        String documentId,
        String subjectId,
        String subjectKind,
        String slug,
        String title,
        String summary,
        String route,
        String careerTrack,
        Set<String> capabilityCodes,
        String searchText,
        String claimId,
        List<String> evidenceIds,
        double score
) {
    public PostgresKnowledgeRow {
        capabilityCodes = Set.copyOf(capabilityCodes);
        evidenceIds = List.copyOf(evidenceIds);
    }
}
```

- [ ] **Step 4: Implement FTS and vector SQL with identical gates**

Both SQL statements must start from active Release and include:

```sql
FROM active_release ar
JOIN content_release cr
  ON cr.release_id = ar.release_id
 AND cr.status IN ('VERIFIED', 'PUBLISHED')
JOIN retrieval_document rd
  ON rd.release_id = cr.release_id
JOIN portfolio_subject ps
  ON ps.release_id = rd.release_id
 AND ps.stable_id = rd.subject_stable_id
JOIN claim c
  ON c.release_id = rd.release_id
 AND c.stable_id = rd.claim_stable_id
 AND c.verification_status = 'VERIFIED'
JOIN claim_evidence_link cel
  ON cel.release_id = c.release_id
 AND cel.claim_stable_id = c.stable_id
JOIN evidence e
  ON e.release_id = cel.release_id
 AND e.stable_id = cel.evidence_stable_id
 AND e.public_status = 'APPROVED'
WHERE ar.singleton = true
  AND cr.release_id = CAST(? AS uuid)
  AND (CAST(? AS text[]) IS NULL OR ps.slug = ANY(CAST(? AS text[])))
```

FTS adds `rd.search_vector @@ websearch_to_tsquery('simple', ?)` and orders by `ts_rank_cd` descending. Vector adds `rd.embedding IS NOT NULL`, orders by `rd.embedding <=> CAST(? AS vector)`, and returns `1 - distance` as score. Both order ties by `rd.stable_id`.

- [ ] **Step 5: Implement Postgres fusion behind the unified seam**

`PostgresPortfolioRetriever.retrieve()` must:

- use existing `PostgresHybridCandidateRetriever` only for `RECOMMENDATION`;
- use `PostgresKnowledgeQuery` for FACT_LOOKUP and COMPARISON;
- execute FTS first;
- attempt the local query embedding and vector query;
- fuse ranks with `1 / (60 + rank)`;
- keep the highest-score copy per passage ID;
- group passages by subject;
- return `POSTGRES_HYBRID` when both routes succeed;
- return `POSTGRES_FTS` and `degraded=true` when only vector generation/query fails;
- throw a dedicated `PortfolioRetrievalException` only when active Release or FTS fails, allowing outer Bundle fallback.

- [ ] **Step 6: Run unit and local PostgreSQL integration tests**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=JdbcPostgresKnowledgeQueryTest,PostgresPortfolioRetrieverTest,JdbcPostgresKnowledgeQueryIntegrationTest test
```

Expected: `BUILD SUCCESS`; integration test may skip only when its existing database-enabled assumption is false, never because of an assertion failure.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres `
  backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres
git commit -m "数据库检索：接入PostgreSQL全文与pgvector统一召回"
```

---

### Task 5: 实现 PostgreSQL 主用、Bundle 自动降级

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/FailoverPortfolioRetriever.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/selection/adapter/postgres/PostgresSelectionConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/FailoverPortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfigurationTest.java`

**Interfaces:**
- Consumes: Task 3 Bundle Adapter and Task 4 PostgreSQL Adapter.
- Produces: the single Spring bean named `portfolioRetriever`.

- [ ] **Step 1: Write failing failover tests**

```java
@Test
void postgresFailureUsesBundleAndPreservesFallbackNotice() {
    PortfolioRetriever primary = request -> {
        throw new PortfolioRetrievalException("POSTGRES_UNAVAILABLE");
    };
    PortfolioRetriever fallback = request -> bundleResult();

    PortfolioRetrievalResult result =
            new FailoverPortfolioRetriever(primary, fallback).retrieve(request());

    assertThat(result.source()).isEqualTo(PortfolioRetrievalSource.BUNDLE);
    assertThat(result.degraded()).isTrue();
    assertThat(result.noticeCode()).isEqualTo("POSTGRES_RETRIEVAL_UNAVAILABLE");
}

@Test
void emptyPostgresResultIsNotAnInfrastructureFailure() {
    PortfolioRetriever primary = request -> postgresResult(List.of());
    PortfolioRetriever fallback = mock(PortfolioRetriever.class);

    PortfolioRetrievalResult result =
            new FailoverPortfolioRetriever(primary, fallback).retrieve(request());

    assertThat(result.subjects()).isEmpty();
    verifyNoInteractions(fallback);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=FailoverPortfolioRetrieverTest,PortfolioIntelligenceConfigurationTest test
```

Expected: compilation fails because failover and unified configuration are absent.

- [ ] **Step 3: Implement failover with a narrow catch**

```java
public final class FailoverPortfolioRetriever implements PortfolioRetriever {
    private final PortfolioRetriever primary;
    private final PortfolioRetriever fallback;

    public FailoverPortfolioRetriever(
            PortfolioRetriever primary,
            PortfolioRetriever fallback
    ) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request) {
        try {
            return primary.retrieve(request);
        } catch (PortfolioRetrievalException exception) {
            PortfolioRetrievalResult result = fallback.retrieve(request);
            return result.withDegradation("POSTGRES_RETRIEVAL_UNAVAILABLE");
        }
    }
}
```

Do not catch `IllegalArgumentException` or programming errors.

- [ ] **Step 4: Wire exactly one external retrieval bean**

`PortfolioIntelligenceConfiguration` creates:

- always-on `bundlePortfolioRetriever`;
- conditional `postgresPortfolioRetriever` when `portfolio.database.public.enabled=true`;
- `portfolioRetriever` as failover when PostgreSQL exists;
- `portfolioRetriever` as Bundle-only when PostgreSQL is disabled.

Use bean names and `@Qualifier` so Spring never sees multiple ambiguous `PortfolioRetriever` candidates. Remove selection orchestration、策略和 HTTP Mapper beans from `PostgresSelectionConfiguration`; keep only `JdbcPostgresSelectionQuery` and `PostgresHybridCandidateRetriever` low-level beans until Task 9 moves those two methods into the unified configuration and removes the obsolete class.

- [ ] **Step 5: Run failover and Spring configuration tests**

Run the command from Step 2.

Expected: `BUILD SUCCESS` in database-enabled and database-disabled application contexts.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter `
  backend/src/main/java/com/portfolio/agent/selection/adapter/postgres/PostgresSelectionConfiguration.java `
  backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter
git commit -m "检索降级：增加数据库主用与本地自动切换"
```

---

### Task 6: 内化确定性推荐策略与批次上下文

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioRecommendationPolicy.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RecommendationContext.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RecommendationContextRegistry.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RecommendationBatchIdFactory.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioRecommendationPolicyTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/RecommendationContextRegistryTest.java`
- Existing tests retained: `backend/src/test/java/com/portfolio/agent/selection/service/TopKSelectionStrategyTest.java`
- Existing tests retained: `backend/src/test/java/com/portfolio/agent/selection/service/ExhaustiveSelectionStrategyTest.java`

**Interfaces:**
- Consumes: Task 3 retrieved subjects and existing `SelectionStrategy`.
- Produces: `PortfolioRecommendation recommend(PortfolioConditions, List<PortfolioRetrievedSubject>, Set<String> excludedIds)`.
- Produces: `RecommendationContextRegistry.save/find`.

- [ ] **Step 1: Write failing deterministic recommendation tests**

```java
@Test
void sameInputsProduceSameOrderedItems() {
    PortfolioRecommendation first =
            policy.recommend(conditions(), subjects(), Set.of());
    PortfolioRecommendation second =
            policy.recommend(conditions(), subjects(), Set.of());

    assertThat(first.items().stream().map(PortfolioRecommendationItem::portfolioId))
            .containsExactlyElementsOf(second.items().stream()
                    .map(PortfolioRecommendationItem::portfolioId).toList());
}

@Test
void refinementExcludesReplacedSubject() {
    PortfolioRecommendation result =
            policy.recommend(conditions(), subjects(), Set.of("project-1"));
    assertThat(result.items())
            .extracting(PortfolioRecommendationItem::portfolioId)
            .doesNotContain("project-1");
}
```

- [ ] **Step 2: Write failing registry expiry and capacity tests**

```java
@Test
void expiredBatchCannotBeRead() {
    registry.save(context("rec_0123456789abcdef0123456789abcdef"));
    clock.advance(Duration.ofMinutes(31));
    assertThat(registry.find("rec_0123456789abcdef0123456789abcdef"))
            .isEmpty();
}

@Test
void registryEvictsOldestEntryAboveOneThousand() {
    IntStream.rangeClosed(0, 1000)
            .forEach(index -> registry.save(context(batchId(index))));
    assertThat(registry.size()).isEqualTo(1000);
    assertThat(registry.find(batchId(0))).isEmpty();
}
```

- [ ] **Step 3: Run recommendation tests and verify failure**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioRecommendationPolicyTest,RecommendationContextRegistryTest,TopKSelectionStrategyTest,ExhaustiveSelectionStrategyTest test
```

Expected: new test classes fail to compile; existing strategy tests remain green.

- [ ] **Step 4: Map retrieved subjects into the existing deterministic strategy**

`PortfolioRecommendationPolicy` must:

1. Filter `excludedIds`.
2. Convert subjects to `SelectionCandidate`, preserving title、route、capabilities and approved evidence references.
3. Build `SelectionTarget` with default size 3.
4. Invoke the injected `SelectionStrategy`.
5. Map the exact selected order to `PortfolioRecommendationItem`.
6. Use deterministic reasons derived from matched career track and sorted capability codes.
7. Return requested hard constraints under `satisfiedConstraints` or `unsatisfiedConstraints`.

The policy must not generate a batch ID; batch identity belongs to the context registry.

- [ ] **Step 5: Implement bounded context and random batch IDs**

```java
public final class RecommendationBatchIdFactory {
    public String next() {
        return "rec_" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

```java
public final class RecommendationContextRegistry {
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_ENTRIES = 1000;

    private final Clock clock;
    private final LinkedHashMap<String, RecommendationContext> entries =
            new LinkedHashMap<>();

    public synchronized void save(RecommendationContext context) {
        purgeExpired();
        entries.put(context.recommendationBatchId(), context);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    public synchronized Optional<RecommendationContext> find(String batchId) {
        purgeExpired();
        return Optional.ofNullable(entries.get(batchId));
    }

    public synchronized int size() {
        purgeExpired();
        return entries.size();
    }

    private void purgeExpired() {
        Instant cutoff = clock.instant().minus(TTL);
        entries.entrySet().removeIf(entry ->
                entry.getValue().createdAt().isBefore(cutoff));
    }
}
```

`RecommendationContext` contains batch ID、conditions、selected IDs、createdAt and contentVersion only；不得保存原始用户消息、IP 或模型提示。

- [ ] **Step 6: Run recommendation and benchmark unit tests**

Run the command from Step 3.

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/service `
  backend/src/test/java/com/portfolio/agent/answer/intelligence/service
git commit -m "推荐引擎：内化确定性选择策略与批次上下文"
```

---

### Task 7: 实现 `PortfolioIntelligence` 硬路由深模块

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskValidator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskValidation.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioIntelligence.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioEvidenceAssembler.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioIntelligenceRoutingTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceTest.java`

**Interfaces:**
- Consumes: Task 1 task/result, Task 5 `portfolioRetriever`, Task 6 recommendation policy and registry.
- Produces: `PortfolioIntelligenceResult resolve(PortfolioTask task, RuntimeAnswerContent content)`.

- [ ] **Step 1: Write failing hard-route tests**

```java
@ParameterizedTest
@EnumSource(value = PortfolioTaskMode.class, names = {
        "FACT_LOOKUP", "COMPARISON", "RECOMMENDATION", "REFINE_RECOMMENDATION"})
void sameTaskAlwaysExecutesSameRetrievalPurpose(PortfolioTaskMode mode) {
    intelligence.resolve(task(mode), content());
    intelligence.resolve(task(mode), content());

    verify(retriever, times(2)).retrieve(
            argThat(request -> expectedPurpose(mode) == request.purpose()));
}

@Test
void recommendationWithoutAudienceRequiresOneClarification() {
    PortfolioIntelligenceResult result =
            intelligence.resolve(taskWithoutAudience(), content());

    assertThat(result.mode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
    assertThat(result.clarification().question()).isEqualTo(
            "这组作品主要准备给哪类受众看？");
    verifyNoInteractions(retriever, recommendationPolicy);
}

@Test
void invalidBatchDoesNotGuessRefinement() {
    PortfolioIntelligenceResult result =
            intelligence.resolve(refinementTask("rec_missing"), content());
    assertThat(result.mode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
    assertThat(result.noticeCode()).isEqualTo("RECOMMENDATION_CONTEXT_REQUIRED");
}
```

- [ ] **Step 2: Run tests and verify missing module**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioIntelligenceRoutingTest,DefaultPortfolioIntelligenceTest test
```

Expected: compilation fails because the deep module does not exist.

- [ ] **Step 3: Add the one-method interface and validator**

```java
@FunctionalInterface
public interface PortfolioIntelligence {
    PortfolioIntelligenceResult resolve(
            PortfolioTask task,
            RuntimeAnswerContent content);
}
```

`PortfolioTaskValidator` returns exactly one of:

- valid task;
- `AUDIENCE_ROLE_REQUIRED`;
- `RECOMMENDATION_CONTEXT_REQUIRED`;
- `RECOMMENDATION_CONTEXT_EXPIRED`;
- `REFINEMENT_TARGET_INVALID`;
- `CONDITION_CONFLICT`.

It must validate before retrieval or policy execution.

```java
public record PortfolioTaskValidation(
        boolean accepted,
        String reasonCode,
        String clarificationQuestion
) {
    public static PortfolioTaskValidation valid() {
        return new PortfolioTaskValidation(true, null, null);
    }

    public static PortfolioTaskValidation rejected(
            String code,
            String question
    ) {
        return new PortfolioTaskValidation(false, code, question);
    }

    public static PortfolioTaskValidation ambiguous() {
        return rejected(
                "PORTFOLIO_TASK_AMBIGUOUS",
                "你希望了解某个作品、比较多个作品，还是让我推荐一组作品？");
    }
}
```

- [ ] **Step 4: Implement the exhaustive hard router**

```java
public PortfolioIntelligenceResult resolve(
        PortfolioTask task,
        RuntimeAnswerContent content
) {
    PortfolioTaskValidation validation = validator.validate(task, content);
    if (!validation.accepted()) {
        return clarification(task, content, validation);
    }
    return switch (task.mode()) {
        case FACT_LOOKUP -> factLookup(task, content);
        case COMPARISON -> comparison(task, content);
        case RECOMMENDATION -> recommendation(task, content, Set.of());
        case REFINE_RECOMMENDATION -> refineRecommendation(task, content);
        case CLARIFICATION_REQUIRED -> clarification(
                task, content, PortfolioTaskValidation.ambiguous());
    };
}
```

No default branch is allowed; adding a future enum value must fail compilation until routed.

- [ ] **Step 5: Implement deterministic handlers**

- FACT_LOOKUP retrieves at most 8 passages and returns evidence-bound `ConversationAnswerBlock` values.
- COMPARISON requires at least two subjects; otherwise asks which works to compare.
- RECOMMENDATION retrieves at most 12 subjects, calls `PortfolioRecommendationPolicy`, generates a batch ID, stores context, and returns recommendation even when items are empty.
- REFINE_RECOMMENDATION loads batch context, merges conditions, excludes the requested old subject, runs policy, and stores a new batch; old context is never overwritten.
- Empty hard-constraint results use `NO_ELIGIBLE_PORTFOLIO` and list unsatisfied constraints.

`PortfolioEvidenceAssembler` must reject passages with empty claim IDs or evidence IDs and must never invent an ID from title or slug.

- [ ] **Step 6: Add deterministic explanatory templates**

The module returns factual blocks even when no model provider is available:

```java
private ConversationAnswerBlock recommendationBlock(
        PortfolioRecommendation recommendation
) {
    String content = recommendation.items().isEmpty()
            ? "当前公开作品中没有同时满足这些条件的组合。"
            : "我按公开证据和你给出的条件选出了 "
                    + recommendation.items().size() + " 个作品。";
    List<String> evidenceIds = recommendation.items().stream()
            .flatMap(item -> item.evidenceIds().stream())
            .distinct()
            .toList();
    return new ConversationAnswerBlock(
            ConversationSourceScope.PORTFOLIO,
            content,
            List.of(),
            evidenceIds);
}
```

- [ ] **Step 7: Run hard-route tests**

Run the command from Step 2.

Expected: `BUILD SUCCESS`; tests cover every enum, validation short-circuit, empty result, database degradation and refinement replacement.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/service `
  backend/src/test/java/com/portfolio/agent/answer/intelligence/service
git commit -m "智能引擎：实现作品集任务校验与确定性硬路由"
```

---

### Task 8: 扩展 Agent 统一回答契约

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationItemResponse.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/domain/AnswerModelContractTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponseTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`

**Interfaces:**
- Consumes: Task 1 `PortfolioRecommendation`.
- Produces: optional JSON property `portfolioRecommendation`.

- [ ] **Step 1: Write failing preservation and serialization tests**

```java
@Test
void withGuidancePreservesRecommendation() {
    ConversationAnswerResult guided = recommendationResult()
            .withGuidance(List.of(question()), progress());
    assertThat(guided.getPortfolioRecommendation())
            .isEqualTo(recommendationResult().getPortfolioRecommendation());
}

@Test
void ordinaryAnswerOmitsRecommendationProperty() throws Exception {
    String json = objectMapper.writeValueAsString(
            new ConversationAnswerResponse(ordinaryResult()));
    assertThat(json).doesNotContain("portfolioRecommendation");
}

@Test
void recommendationKeepsBackendOrder() {
    ConversationAnswerResponse response =
            new ConversationAnswerResponse(recommendationResult());
    assertThat(response.getPortfolioRecommendation().getItems())
            .extracting(PortfolioRecommendationItemResponse::getPortfolioId)
            .containsExactly("project-2", "project-1");
}
```

- [ ] **Step 2: Run response tests and verify failure**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=AnswerModelContractTest,ConversationAnswerResponseTest,ConversationAnswerResponseMapperTest test
```

Expected: compilation fails because recommendation accessors and DTOs are absent.

- [ ] **Step 3: Add recommendation to the domain result**

Add `PortfolioRecommendation portfolioRecommendation` to the canonical constructor and getter:

```java
public PortfolioRecommendation getPortfolioRecommendation() {
    return portfolioRecommendation;
}
```

Every convenience constructor delegates with `null`. `withGuidance()` passes the existing `portfolioRecommendation` unchanged. Update all eight constructor call sites at compile time; do not add an overloaded constructor that can silently drop the field after rebuilding a result.

- [ ] **Step 4: Add response DTOs**

```java
public final class PortfolioRecommendationResponse {
    private final String recommendationBatchId;
    private final List<PortfolioRecommendationItemResponse> items;
    private final List<String> satisfiedConstraints;
    private final List<String> unsatisfiedConstraints;

    public static PortfolioRecommendationResponse from(
            PortfolioRecommendation source
    ) {
        return new PortfolioRecommendationResponse(
                source.recommendationBatchId(),
                source.items().stream()
                        .map(PortfolioRecommendationItemResponse::from)
                        .toList(),
                source.satisfiedConstraints(),
                source.unsatisfiedConstraints());
    }
}
```

`PortfolioRecommendationItemResponse` exposes exactly `portfolioId`、`title`、`route`、`matchReasons`、`evidenceIds` and copies all lists.

- [ ] **Step 5: Omit the field for non-recommendation answers**

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
private final PortfolioRecommendationResponse portfolioRecommendation;
```

In `ConversationAnswerResponse(ConversationAnswerResult result)`:

```java
this.portfolioRecommendation = result.getPortfolioRecommendation() == null
        ? null
        : PortfolioRecommendationResponse.from(
                result.getPortfolioRecommendation());
```

- [ ] **Step 6: Run contract tests**

Run the command from Step 2.

Expected: `BUILD SUCCESS`; ordinary JSON omits the field and recommendation JSON matches the approved field names.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java `
  backend/src/main/java/com/portfolio/agent/answer/dto/response `
  backend/src/test/java/com/portfolio/agent/answer/domain/AnswerModelContractTest.java `
  backend/src/test/java/com/portfolio/agent/answer/dto/response `
  backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java
git commit -m "回答契约：增加可选的作品集结构化推荐"
```

---

### Task 9: 接入 Agent 运行时并删除公开 Selection 接口

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationIntentRouter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/controller/PortfolioSelectionController.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/mapper/PortfolioSelectionResponseMapper.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/AudienceRole.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/CapabilityCoverageResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/ComplementarityResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/EvidenceReferenceResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/PortfolioSelectionAlternativeResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/PortfolioSelectionItemResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/PortfolioSelectionRequest.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/PortfolioSelectionResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/dto/SelectionDegradationResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/service/PortfolioSelectionService.java`
- Delete: `backend/src/main/java/com/portfolio/agent/selection/adapter/postgres/PostgresSelectionConfiguration.java`
- Delete: corresponding Controller、Mapper、configuration and service tests.
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/controller/ConversationAnswerControllerTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/RemovedPortfolioSelectionEndpointTest.java`

**Interfaces:**
- Consumes: Task 2 resolver, Task 7 intelligence, Task 8 response result.
- Produces: all portfolio paths through `/api/v2/answers`.

- [ ] **Step 1: Write failing provider-independent runtime tests**

```java
@Test
void explicitRecommendationWorksWhenExternalProviderIsDisabled() {
    when(providerAccess.isAllowed()).thenReturn(false);
    when(taskResolver.resolve(any(), any(), eq(false)))
            .thenReturn(Optional.of(recommendationTask()));
    when(intelligence.resolve(any(), any()))
            .thenReturn(recommendationIntelligenceResult());

    ConversationAnswerResult result = runtime.answer(request("推荐三个后端项目"));

    assertThat(result.getPortfolioRecommendation()).isNotNull();
    verifyNoInteractions(modelPort);
}

@Test
void modelCannotReplaceHardRouterRecommendation() {
    when(intelligence.resolve(any(), any()))
            .thenReturn(recommendationIntelligenceResult("project-1", "project-2"));

    ConversationAnswerResult result = runtime.answer(request("推荐两个项目"));

    assertThat(result.getPortfolioRecommendation().items())
            .extracting(PortfolioRecommendationItem::portfolioId)
            .containsExactly("project-1", "project-2");
}
```

- [ ] **Step 2: Write the removed endpoint test**

```java
@Test
void publicSelectionEndpointDoesNotExist() throws Exception {
    mockMvc.perform(post("/api/portfolio-selections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 3: Run focused tests and verify expected failures**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=ConversationalAgentRuntimeTest,ConversationAnswerControllerTest,RemovedPortfolioSelectionEndpointTest test
```

Expected: runtime tests fail because intelligence is not injected; endpoint test fails while the old Controller exists.

- [ ] **Step 4: Split deterministic guard routing from model routing**

`ConversationIntentRouter` adds:

```java
public Optional<ConversationRoute> deterministicRoute(
        RuntimeAnswerContent content,
        ConversationAnswerRequest request
) {
    String normalized = request.getQuestion().strip().toLowerCase(Locale.ROOT);
    if (isUnsafe(normalized)) {
        return Optional.of(deterministic(
                ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                ConversationAnswerScope.CONVERSATION));
    }
    if (isTimeSensitive(normalized)) {
        return Optional.of(deterministic(
                ConversationIntent.TIME_SENSITIVE,
                ConversationAnswerScope.GENERAL));
    }
    if (isConversation(normalized)) {
        return Optional.of(deterministic(
                ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION));
    }
    return Optional.ofNullable(routeHint(content, request.getContext()));
}
```

Existing `route()` first calls this method and only then invokes model classification. This preserves safety priority while allowing provider-disabled portfolio rules to execute.

- [ ] **Step 5: Integrate intelligence before free-form generation**

In `ConversationalAgentRuntime.answerInternal()` use this sequence:

```java
RuntimeAnswerContent content = knowledgeGateway.getContent();
ConversationWindow window = windowManager.prepare(
        request.getMessages(), request.getQuestion());

Optional<ConversationRoute> deterministic =
        intentRouter.deterministicRoute(content, request);
if (deterministic.filter(this::mustUseBoundaryFallback).isPresent()) {
    return finalizeTurn(
            fallback.answer(request, content, deterministic.orElseThrow()),
            content,
            deterministic.orElseThrow(),
            window,
            request,
            false);
}

Optional<PortfolioTask> portfolioTask = taskResolver.resolve(
        request, window, providerAccess.isAllowed());
if (portfolioTask.isPresent()) {
    PortfolioIntelligenceResult intelligenceResult =
            portfolioIntelligence.resolve(portfolioTask.orElseThrow(), content);
    return finalizeTurn(
            toConversationAnswerResult(request, intelligenceResult),
            content,
            portfolioRoute(request),
            window,
            request,
            false);
}

if (!providerAccess.isAllowed()) {
    return finalizeTurn(
            fallback.answer(request, content),
            content,
            safeRoute(request, false),
            window,
            request,
            false);
}
```

`toConversationAnswerResult` copies recommendation exactly once; it may map title/blocks/degraded/notice but must not invoke a model or reorder items.

Before `deterministicRoute`, preserve the existing subject privacy guard:

```java
if (!subjectGuard.accepts(request.getContext(), content)) {
    return finalizeTurn(
            fallback.unknownSubject(request, content),
            content,
            safeRoute(request, true),
            window,
            request,
            true);
}
```

Add these exact helpers:

```java
private boolean mustUseBoundaryFallback(ConversationRoute route) {
    return route.getIntent() == ConversationIntent.TIME_SENSITIVE
            || route.getIntent() == ConversationIntent.UNSUPPORTED_OR_UNSAFE
            || route.getIntent() == ConversationIntent.CONVERSATION;
}

private ConversationAnswerResult toConversationAnswerResult(
        ConversationAnswerRequest request,
        PortfolioIntelligenceResult source
) {
    return new ConversationAnswerResult(
            request.getTurnId(),
            source.contentVersion(),
            ConversationIntent.PORTFOLIO_GROUNDED,
            ConversationAnswerScope.PORTFOLIO,
            source.clarification() == null
                    ? AnswerResolution.ANSWERED
                    : AnswerResolution.BOUNDARY,
            source.title(),
            source.blocks(),
            List.of(),
            source.degraded(),
            GenerationMode.DETERMINISTIC,
            AnswerSource.RETRIEVAL,
            source.noticeCode(),
            new ConversationProgress(
                    List.of(),
                    ConversationGuidanceStage.OPENING),
            source.recommendation());
}
```

- [ ] **Step 6: Wire the new dependencies**

The `conversationalAgentRuntime` bean method in `ConversationalAgentConfiguration` injects `PortfolioTaskResolver` and `PortfolioIntelligence`. `PortfolioIntelligenceConfiguration` constructs resolver with `properties.getMinimumPortfolioIntentConfidence()` and the model Adapter as `PortfolioTaskClassifierPort`.

- [ ] **Step 7: Delete the public Selection surface**

Delete the listed Controller、HTTP DTO、Mapper、orchestration service and configuration files. Do not delete:

- `SelectionStrategy`
- `TopKSelectionStrategy`
- `ExhaustiveSelectionStrategy`
- selection domain values used by `PortfolioRecommendationPolicy`
- `PostgresHybridCandidateRetriever`
- benchmark package and fixtures

Before deleting `PostgresSelectionConfiguration`, move its `JdbcPostgresSelectionQuery` and `PostgresHybridCandidateRetriever` bean methods into `PortfolioIntelligenceConfiguration` under the same database-enabled condition. Run the database-enabled configuration test to prove no bean disappears and no duplicate bean remains.

Update imports and Spring wiring until no production reference points to `/api/portfolio-selections`.

- [ ] **Step 8: Run runtime, endpoint and retained strategy tests**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=ConversationalAgentRuntimeTest,ConversationAnswerControllerTest,RemovedPortfolioSelectionEndpointTest,TopKSelectionStrategyTest,ExhaustiveSelectionStrategyTest test
```

Expected: `BUILD SUCCESS`; old endpoint is 404 and retained algorithms stay green.

- [ ] **Step 9: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer `
  backend/src/main/java/com/portfolio/agent/selection `
  backend/src/test/java/com/portfolio/agent/answer `
  backend/src/test/java/com/portfolio/agent/selection
git commit -m "Agent集成：统一作品集问答推荐并移除独立接口"
```

---

### Task 10: 增加诊断、后端端到端验证和前端交接文档

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioIntelligenceDiagnosticsTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/PortfolioIntelligenceConversationIntegrationTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/selection/benchmark/PortfolioSelectionBenchmarkCliTest.java`
- Create: `docs/handoffs/agent-portfolio-recommendation-frontend.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Consumes: completed backend response contract.
- Produces: safe diagnostics、end-to-end evidence and frontend-only implementation handoff.

- [ ] **Step 1: Write failing safe-diagnostic tests**

```java
@Test
void diagnosticContainsRouteAndCountsButNoQuestionOrCredentials() {
    intelligence.resolve(recommendationTask("我的秘密目标"), content());

    DiagnosticEvent event = events.single("portfolio.intelligence.completed");
    assertThat(event.getFields())
            .containsEntry("portfolio.mode", "RECOMMENDATION")
            .containsKeys(
                    "route.source",
                    "retrieval.source",
                    "retrieval.candidate_count",
                    "recommendation.item_count",
                    "duration.bucket")
            .doesNotContainKeys("question", "apiKey", "databaseUrl");
    assertThat(event.toString()).doesNotContain("我的秘密目标");
}
```

- [ ] **Step 2: Publish one best-effort event per resolve**

Event names:

- success: `portfolio.intelligence.completed`
- clarification: `portfolio.intelligence.clarification`
- degraded: `portfolio.intelligence.degraded`

Required fields:

```text
portfolio.mode
route.source
classification.confidence_bucket
retrieval.source
retrieval.candidate_count
recommendation.item_count
recommendation.batch_present
content.version
notice.code
duration.bucket
```

Publishing must be inside `try/catch RuntimeException`; diagnostics never alter results.

- [ ] **Step 3: Write Agent integration scenarios**

`PortfolioIntelligenceConversationIntegrationTest` covers:

```java
@Test
void recommendationThenRefinementUsesBatchContext() {
    ConversationAnswerResponse first = answer(
            request("推荐三个后端项目", null));
    assertThat(first.getPortfolioRecommendation()).isNotNull();

    String batchId = first.getPortfolioRecommendation()
            .getRecommendationBatchId();
    ConversationAnswerResponse refined = answer(
            request("换掉第一个", batchId));

    assertThat(refined.getPortfolioRecommendation()
            .getRecommendationBatchId()).isNotEqualTo(batchId);
    assertThat(refined.getPortfolioRecommendation().getItems())
            .extracting(PortfolioRecommendationItemResponse::getPortfolioId)
            .doesNotContain(first.getPortfolioRecommendation()
                    .getItems().getFirst().getPortfolioId());
}
```

Additional cases:

- ordinary fact lookup returns evidence-bound blocks and omits recommendation;
- comparison returns no recommendation;
- database unavailable uses Bundle and `degraded=true`;
- provider disabled still executes explicit recommendation;
- missing batch returns one clarification;
- no eligible result returns empty items plus unsatisfied constraints;
- old Selection endpoint returns 404.

- [ ] **Step 4: Run diagnostics, integration and benchmark tests**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioIntelligenceDiagnosticsTest,PortfolioIntelligenceConversationIntegrationTest,CaseConversationBundleIntegrationTest,PortfolioSelectionBenchmarkCliTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Write the frontend handoff without modifying frontend code**

`docs/handoffs/agent-portfolio-recommendation-frontend.md` must include this exact response example:

```json
{
  "turnId": "turn-100",
  "contentVersion": "public-2026-07-31",
  "intent": "PORTFOLIO_GROUNDED",
  "answerScope": "PORTFOLIO",
  "resolution": "ANSWERED",
  "title": "推荐结果",
  "blocks": [
    {
      "sourceScope": "PORTFOLIO",
      "content": "我按公开证据和你给出的条件选出了 2 个作品。",
      "claimIds": [],
      "evidenceIds": ["evidence-1"]
    }
  ],
  "degraded": false,
  "portfolioRecommendation": {
    "recommendationBatchId": "rec_0123456789abcdef0123456789abcdef",
    "items": [
      {
        "portfolioId": "project-1",
        "title": "项目一",
        "route": "/projects/project-one",
        "matchReasons": ["匹配后端能力要求"],
        "evidenceIds": ["evidence-1"]
      }
    ],
    "satisfiedConstraints": ["audienceRole", "requestedSize"],
    "unsatisfiedConstraints": []
  }
}
```

It must also state:

- non-recommendation responses omit `portfolioRecommendation`;
- the frontend echoes the batch ID as `context.recommendationBatchId`;
- card actions send natural language only to `/api/v2/answers`;
- backend item order is authoritative;
- no Selection route or client-side algorithm is allowed;
- the backend implementation did not modify `frontend/src`.

- [ ] **Step 6: Update status and evolution docs**

`docs/08-当前实现状态.md` records the unified module、primary/fallback Adapter and removed endpoint. `docs/11-项目演进日志.md` records every focused and full verification command with its result; do not state that frontend rendering is implemented.

- [ ] **Step 7: Run the full backend test suite**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test
```

Expected: `BUILD SUCCESS` with no failing tests.

- [ ] **Step 8: Verify no frontend code or public Selection surface changed**

Run:

```powershell
git diff master...HEAD --name-only
rg -n "/api/portfolio-selections|PortfolioSelectionController" backend/src/main docs README.md
git diff master...HEAD -- frontend/src
```

Expected:

- the first command lists backend、tests、docs only;
- the second command has no production endpoint or Controller match; historical migration text may be explicitly annotated as removed;
- the third command prints no diff.

- [ ] **Step 9: Commit**

```powershell
git add backend/src/main backend/src/test docs/handoffs `
  docs/08-当前实现状态.md docs/11-项目演进日志.md
git commit -m "验证文档：补充智能检索回归与前端交接"
```

---

### Task 11: 最终回归、提交审计与集成准备

**Files:**
- Verify only; no planned source modifications.

**Interfaces:**
- Consumes: Tasks 1 through 10.
- Produces: a verified implementation branch ready for review and eventual fast-forward integration to `master`.

- [ ] **Step 1: Confirm the worktree contains no unrelated changes**

Run:

```powershell
git status --short
git log --oneline master..HEAD
```

Expected: status is clean; log contains the approved design/plan commits followed by the ten Chinese implementation commits in task order.

- [ ] **Step 2: Run full backend verification again from clean state**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml clean test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Verify forbidden surfaces and sensitive output**

Run:

```powershell
rg -n "/api/portfolio-selections|PortfolioSelectionController|selection 页面|Selection 页面" `
  backend/src/main frontend/src docs README.md
rg -n "apiKey|databaseUrl|jdbc:postgresql|rawContent" `
  backend/src/main/java/com/portfolio/agent/answer/intelligence
```

Expected:

- no live endpoint、Controller or independent page remains;
- any diagnostic-related match is a field-denial assertion, not an emitted secret.

- [ ] **Step 4: Review the diff scope**

Run:

```powershell
git diff --stat master...HEAD
git diff --check master...HEAD
git diff master...HEAD -- frontend/src
```

Expected: backend/tests/docs only, no whitespace errors, no frontend diff.

- [ ] **Step 5: Hand off for code review**

Report:

- branch and commit list;
- full test count and duration;
- database integration test status;
- exact removed endpoint;
- frontend handoff document path;
- any pre-existing dirty state in the main `D:\code\agent` worktree.

Do not merge or push until the reviewer accepts the implementation and the main worktree can be integrated without overwriting unrelated user changes.
