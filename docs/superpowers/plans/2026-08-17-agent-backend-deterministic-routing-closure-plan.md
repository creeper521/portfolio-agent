# Agent 后端确定性路由闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Agent 后端对噪声、宽泛项目推荐、可信续接、重复任务、推荐数量不足、正式预设和 packaged JAR 运行版本形成可证明的确定性闭环。

**Architecture:** 在现有 `DefaultTurnRouter → SemanticPlanCompiler → SemanticPlanValidator → P3PortfolioSemanticTaskExecutor → ConversationAnswerResponseMapper` 主链上增加前置输入形成门禁、按意图授权的主体策略、Goal/Task 双层去重和推荐完整性投影。正式预设只保留一份 canonical public projection；packaged JAR 验收复用现有 PowerShell 启动器，并只输出脱敏后的版本、数量和状态摘要。

**Tech Stack:** Java 21、Spring Boot 3.5.3、JUnit 5、AssertJ、MockMvc、Maven 3.9.9、PowerShell。

## Global Constraints

- 只修改后端 Java、后端测试、根目录运行验收脚本和后端文档；不修改 `frontend/` 下任何文件。
- 大模型不得决定 taskType、requestedSize、candidate scope、Evidence 范围、完成状态或是否跳过澄清。
- 推荐候选域保持公开 Project-only，不开放 Case 推荐。
- `null`、空白、纯数字、纯标点、纯表情和不包含自然语言字母/汉字的输入必须澄清，且不生成 Plan、Task、Blocks、Evidence 或 Source。
- 宽泛推荐默认使用 `ALL_PUBLISHED_PROJECTS`；只有当前问题明确点名、受控澄清选择或已授权 Context Handle 才能收窄。
- 推荐数量范围保持 1–5；不得静默缩小 requestedSize。
- 计划校验失败必须 fail closed；不得在校验器中猜测如何合并任务。
- 不在日志或验收输出中打印问题正文、回答正文、ResumeToken、Prompt、Context Handle 或模型原始响应。
- 每项任务执行 TDD：先观察目标测试失败，再做最小实现，再运行局部和相关回归测试。
- 每项任务单独提交，提交前运行 `git diff --check`。

---

## File Structure

### 新建文件

- `backend/src/main/java/com/portfolio/agent/answer/routing/service/InputFormationPolicy.java`：只判断当前输入是否形成，不读取主体、历史或模型。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/RecommendationSubjectPolicy.java`：根据主体来源和当前输入决定推荐候选是否允许收窄。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticGoalDeduplicator.java`：在 task count 和 task 编译前生成稳定 Goal 键并保序去重。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/InputFormationPolicyTest.java`：冻结噪声字符边界。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/RecommendationSubjectPolicyTest.java`：冻结活动主体、显式文本、澄清选择和可信 Context 的授权矩阵。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticGoalDeduplicatorTest.java`：冻结 Goal 键的等价与非等价边界。
- `backend/src/test/java/com/portfolio/agent/answer/routing/domain/SemanticTurnOutcomeTest.java`：冻结 PRIMARY 部分完成时的计划级状态。
- `backend/src/test/java/com/portfolio/agent/answer/controller/AgentBackendClosureIntegrationTest.java`：覆盖最终 HTTP 合同场景。

### 修改文件

- `backend/src/main/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouter.java`：在上下文解析和模型调用前执行输入形成门禁。
- `backend/src/main/java/com/portfolio/agent/answer/mapper/SemanticTurnRequestMapper.java`：不再把 `semanticContext.activeSubjects` 冒充当前问题显式主体。
- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticRoutingTypes.java`：增加可信 Context 主体来源。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/RoutingContextResolver.java`：保留可信 Context 来源，并保持当前问题显式文本优先于页面活动主体。
- `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`：给服务端授权的 Context 选择打可信来源标记，并统一正式预设 material。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java`：按意图应用推荐主体策略，移除无意图的活动主体回退。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanCompiler.java`：在编号和依赖生成前稳定去重 Goal。
- `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidator.java`：拒绝不同 taskId 的语义重复任务。
- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskResultPayload.java`：扩展推荐 canonical projection。
- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskOutcome.java`：支持携带公开安全原因码的部分完成结果。
- `backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticTurnOutcome.java`：计划级状态在 PRIMARY task 部分完成时保持 PARTIAL。
- `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutor.java`：按 requestedSize/actualSize 判断完整、部分或零结果。
- `backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationResponse.java`：公开推荐数量、候选范围和原因码。
- `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java`：把推荐完整性字段和正式预设 canonical blocks 投影到所有兼容位置。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java`：路由和模型边界回归。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/RoutingContextResolverTest.java`：主体来源优先级回归。
- `backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidatorTest.java`：语义重复防线。
- `backend/src/test/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutorTest.java`：推荐数量完整性。
- `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`：推荐响应字段和预设双投影一致性。
- `backend/src/test/java/com/portfolio/agent/answer/controller/NoiseConversationIntegrationTest.java`：带活动 SQL 主体的噪声请求。
- `backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java`：SQL 正式预设单任务和逐字段一致性。
- `scripts/run-jar-e2e.ps1`：增加后端-only 模式、构建身份记录和闭环 API 探针。
- `scripts/run-jar-e2e.test.ps1`：验证新参数、脱敏输出、PID/端口归属和清理行为。

---

### Task 1: 输入形成门禁先于上下文和模型

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/InputFormationPolicy.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/InputFormationPolicyTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouter.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/NoiseConversationIntegrationTest.java`

**Interfaces:**
- Consumes: `SemanticTurnInput.getAction()`、`SemanticTurnInput.getRoutingQuestion()`、`ClarificationRequest.unformedRequest()`。
- Produces: `InputFormationPolicy.Formation evaluate(String question)`；`UNFORMED` 请求在 `RoutingContextResolver` 和 `SemanticClassifierPort` 之前终止。

- [ ] **Step 1: 写输入字符边界失败测试**

```java
@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"   ", "1", "112233", "!!!", "😀", "--__--"})
void rejectsInputsWithoutNaturalLanguageCharacters(String question) {
    assertThat(new InputFormationPolicy().evaluate(question))
            .isEqualTo(InputFormationPolicy.Formation.UNFORMED);
}

@ParameterizedTest
@ValueSource(strings = {"推荐两个项目", "project recommendation", "介绍 SQL 项目"})
void acceptsInputsContainingNaturalLanguageCharacters(String question) {
    assertThat(new InputFormationPolicy().evaluate(question))
            .isEqualTo(InputFormationPolicy.Formation.FORMED);
}
```

- [ ] **Step 2: 运行测试并确认因类型不存在而失败**

Run:

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=InputFormationPolicyTest' test
```

Expected: FAIL，编译器报告 `InputFormationPolicy` 不存在。

- [ ] **Step 3: 实现无状态输入形成策略**

```java
public final class InputFormationPolicy {
    public enum Formation { FORMED, UNFORMED }

    public Formation evaluate(String question) {
        if (question == null || question.isBlank()) {
            return Formation.UNFORMED;
        }
        boolean formed = question.codePoints().anyMatch(Character::isLetter);
        return formed ? Formation.FORMED : Formation.UNFORMED;
    }
}
```

- [ ] **Step 4: 把门禁放到 boundary 之后、上下文解析之前**

在 `DefaultTurnRouter` 中保留现有构造器兼容性，旧构造器委托给注入 `new InputFormationPolicy()` 的完整构造器；`route` 的顺序固定为：

```java
GlobalBoundaryGate.BoundaryDecision boundary = boundaryGate.evaluate(input);
if (boundary.isBoundary()) {
    return SemanticTurnDecision.boundary(new LinkedHashSet<>(boundary.getReasonCodes()));
}
if (input.getAction() != SemanticTurnInput.Action.CONFIRM_PLAN
        && inputFormationPolicy.evaluate(input.getRoutingQuestion())
        == InputFormationPolicy.Formation.UNFORMED) {
    return SemanticTurnDecision.clarificationRequired(
            ClarificationRequest.unformedRequest());
}
```

在路由测试中使用计数型 `SemanticClassifierPort`，断言噪声输入带活动主体时 disposition 为 `CLARIFICATION_REQUIRED`、plan 为空、classifier 调用次数为 0。

- [ ] **Step 5: 扩展 HTTP 噪声测试**

给 `NoiseConversationIntegrationTest` 增加 `"1"`、`"!!!"`、`"😀"` 三组请求，并在请求中携带：

```json
{
  "semanticContext": {
    "activeSubjects": [{"subjectType":"PROJECT","subjectId":"sql-audit"}],
    "audienceRole":"INTERVIEWER",
    "requestSource":"AGENT_PAGE"
  }
}
```

每组断言：`resolution=NEEDS_CLARIFICATION`、`agentTurn.plan` 不存在、`blocks=[]`、`portfolioRecommendation` 不存在、`publicSourceCatalog` 不存在。

- [ ] **Step 6: 运行局部回归**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=InputFormationPolicyTest,DefaultTurnRouterDeterministicTest,NoiseConversationIntegrationTest' test
```

Expected: BUILD SUCCESS，新增用例全部通过。

- [ ] **Step 7: 提交本任务**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/routing/service/InputFormationPolicy.java backend/src/main/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouter.java backend/src/test/java/com/portfolio/agent/answer/routing/service/InputFormationPolicyTest.java backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java backend/src/test/java/com/portfolio/agent/answer/controller/NoiseConversationIntegrationTest.java
git diff --cached --check
git commit -m "fix: 在上下文解析前拒绝噪声输入"
```

---

### Task 2: 按意图授权推荐主体范围

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/RecommendationSubjectPolicy.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/RecommendationSubjectPolicyTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/SemanticTurnRequestMapper.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticRoutingTypes.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/RoutingContextResolver.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/RoutingContextResolverTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java`

**Interfaces:**
- Consumes: `ResolvedRoutingContext`、`SemanticTurnInput.getClarificationResolution()`、`SubjectResolutionSource`。
- Produces: `List<SubjectReference> candidateSubjects(SemanticTurnInput input, ResolvedRoutingContext context)`；空列表代表全部公开 Project。

- [ ] **Step 1: 写推荐授权矩阵失败测试**

测试必须分别构造以下上下文并断言：

```java
assertThat(policy.candidateSubjects(broadRecommendation, activeSqlContext)).isEmpty();
assertThat(policy.candidateSubjects(namedRecommendation, explicitTextProjects))
        .extracting(SubjectReference::getSubjectId)
        .containsExactly("sql-audit", "activity-engineering");
assertThat(policy.candidateSubjects(controlledSelection, clarificationSelection))
        .extracting(SubjectReference::getSubjectId)
        .containsExactly("sql-audit");
assertThat(policy.candidateSubjects(trustedContinuation, authorizedContext))
        .extracting(SubjectReference::getSubjectId)
        .containsExactly("activity-engineering");
```

同时在 `DefaultTurnRouterDeterministicTest` 中断言：宽泛问题携带 SQL 活动主体后，`PortfolioRecommend.getCandidateSubjects()` 为空且 requestedSize 为 2。

- [ ] **Step 2: 运行测试并确认宽泛推荐仍被活动主体污染**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=RecommendationSubjectPolicyTest,DefaultTurnRouterDeterministicTest,RoutingContextResolverTest' test
```

Expected: FAIL；当前推荐参数仍包含活动 SQL 主体，且可信 Context 没有独立来源。

- [ ] **Step 3: 分离活动主体和当前问题显式主体**

在 `SemanticTurnRequestMapper` 中不再复制 `semanticContext.activeSubjects` 到 `explicitSubjects`：

```java
List<SubjectReference> explicitSubjects = new ArrayList<>();
if (clarificationResolution != null
        && clarificationResolution.getSelectedSubject() != null) {
    explicitSubjects.add(clarificationResolution.getSelectedSubject());
}
```

这样 activeSubjects 仍保留在 `SemanticContext` 中，仅由 `RoutingContextResolver` 在明确指代时使用。

- [ ] **Step 4: 标记并保留服务端授权 Context 来源**

给 `SubjectResolutionSource` 增加 `AUTHORIZED_CONTEXT`。`ConversationalAgentRuntime` 把 `AuthorizedContextReference.selectedSubject` 重新包装后注入：

```java
private SubjectReference authorizedContextSubject(SubjectReference subject) {
    return new SubjectReference(subject.getSubjectType(), subject.getSubjectId(),
            SubjectResolutionSource.AUTHORIZED_CONTEXT,
            subject.getContentVersion());
}
```

`RoutingContextResolver` 对显式引用先筛选 `AUTHORIZED_CONTEXT`，通过 catalog 校验后保持该来源；`roleFor(AUTHORIZED_CONTEXT)` 返回 `RESULT_BOUND`。普通页面 activeSubject 不得进入此分支。

同时更新 `ResolvedRoutingContext.resolved(...)` 的默认 role 映射，使 `AUTHORIZED_CONTEXT` 与 `STRUCTURED_RESULT/PENDING_PLAN` 一样得到 `RESULT_BOUND`，避免两个工厂产生不同授权强度。

- [ ] **Step 5: 实现推荐主体策略并接入 Collector**

```java
public final class RecommendationSubjectPolicy {
    public List<SubjectReference> candidateSubjects(
            SemanticTurnInput input, ResolvedRoutingContext context) {
        if (context.getStatus() != RoutingContextStatus.RESOLVED) {
            return List.of();
        }
        SubjectResolutionSource source = context.getResolutionSource();
        boolean explicitlyNamed = source == SubjectResolutionSource.EXPLICIT_TEXT;
        boolean trustedContinuation = source == SubjectResolutionSource.AUTHORIZED_CONTEXT;
        boolean controlledSelection = input.getClarificationResolution() != null
                && input.getClarificationResolution().getSelectedSubject() != null;
        if (!explicitlyNamed && !trustedContinuation && !controlledSelection) {
            return List.of();
        }
        return context.getSubjects().stream()
                .filter(subject -> subject.getSubjectType() == SubjectType.PROJECT)
                .toList();
    }
}
```

`SemanticSignalCollector` 的普通推荐分支改为调用该策略；refinement 仍只接受 RESULT/可信 recommendation context，不复用页面主体。

在 `DefaultTurnRouterDeterministicTest` 增加模型边界断言：即使 optional classifier 为宽泛推荐提出单个 SQL 主体，最终 `taskType` 和 requestedSize 仍由规则确定，candidateSubjects 仍为空。

- [ ] **Step 6: 运行路由和请求映射回归**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=RecommendationSubjectPolicyTest,RoutingContextResolverTest,DefaultTurnRouterDeterministicTest,ConversationAnswerRequestTest,ConversationAnswerRequestValidationTest' test
```

Expected: BUILD SUCCESS；宽泛推荐为全公开范围，显式文本和可信 Context 可收窄。

- [ ] **Step 7: 提交本任务**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/mapper/SemanticTurnRequestMapper.java backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticRoutingTypes.java backend/src/main/java/com/portfolio/agent/answer/routing/service/RecommendationSubjectPolicy.java backend/src/main/java/com/portfolio/agent/answer/routing/service/RoutingContextResolver.java backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java backend/src/test/java/com/portfolio/agent/answer/routing/service/RecommendationSubjectPolicyTest.java backend/src/test/java/com/portfolio/agent/answer/routing/service/RoutingContextResolverTest.java backend/src/test/java/com/portfolio/agent/answer/routing/service/DefaultTurnRouterDeterministicTest.java
git diff --cached --check
git commit -m "fix: 按意图授权项目推荐范围"
```

---

### Task 3: Goal 去重和 Plan 语义重复防线

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticGoalDeduplicator.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticGoalDeduplicatorTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanCompiler.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidator.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticPlanCompilerFulfillmentRoleTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidatorTest.java`

**Interfaces:**
- Consumes: `SemanticSignals.GoalCandidate` 和 typed `SemanticTaskParameters`。
- Produces: compiler 首次出现保序去重；validator issue `PLAN_SEMANTIC_TASK_DUPLICATE`。

- [ ] **Step 1: 写两层失败测试**

Deduplicator/Compiler 测试构造两个 intent、主体、topic、facet 相同的 Goal，断言 collector task count 和 compiler 都只保留一个 PRIMARY task。Validator 测试构造不同 taskId、相同语义的事实任务：

```java
SemanticTurnPlan plan = plan(
        List.of(fact("task-01", "project-a"), fact("task-02", "project-a")),
        List.of(), List.of(), Set.of(RequestedOutput.SUMMARY));

PlanValidationResult result = validator.validate(plan, "stp-v2");

assertFalse(result.isValid());
assertTrue(result.getIssues().contains("PLAN_SEMANTIC_TASK_DUPLICATE"));
```

再增加反例：相同项目但 facets 不同、fulfillmentRole 不同或 requestedOutputs 不同不得被判为重复。

- [ ] **Step 2: 运行测试并确认当前只检查 taskId**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=SemanticGoalDeduplicatorTest,SemanticPlanCompilerFulfillmentRoleTest,SemanticPlanValidatorTest' test
```

Expected: FAIL；重复 Goal 仍增加 requestedTaskCount/任务数量，或 validator 未返回新原因码。

- [ ] **Step 3: 在任务编号前稳定去重 Goal**

在 `SemanticGoalDeduplicator` 中加入不可变键，主体键忽略 resolutionSource，只使用 type/id/contentVersion：

```java
private record GoalKey(
        SemanticSignals.Intent intent,
        List<String> subjects,
        List<String> topics,
        List<String> facets) { }

static List<SemanticSignals.GoalCandidate> distinctGoals(
        List<SemanticSignals.GoalCandidate> goals) {
    LinkedHashMap<GoalKey, SemanticSignals.GoalCandidate> distinct = new LinkedHashMap<>();
    for (SemanticSignals.GoalCandidate goal : goals) {
        List<String> subjects = goal.getSubjects().stream()
                .map(value -> value.getSubjectType() + ":" + value.getSubjectId()
                        + ":" + value.getContentVersion())
                .sorted().toList();
        List<String> topics = goal.getTopics().stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).sorted().toList();
        List<String> facets = goal.getPortfolioFacets().stream()
                .map(Enum::name).sorted().toList();
        distinct.putIfAbsent(new GoalKey(goal.getIntent(), subjects, topics, facets), goal);
    }
    return List.copyOf(distinct.values());
}
```

`SemanticSignalCollector` 在计算 requestedTaskCount 前调用 `distinctGoals`，避免七个重复 Goal 被误判为需要拆分；`SemanticPlanCompiler` 在编号、dependency 和 synthesis source 生成前再次调用同一函数作为防御。

- [ ] **Step 4: 在 validator 增加语义键**

语义键必须包含 taskType、sourceDomain、规范化 parameters、主体 identity、requestedOutputs 和 fulfillmentRole。为每种 parameter variant 生成 typed canonical list，不使用 `toString()`：

```java
private record SemanticTaskKey(
        SemanticTaskType taskType,
        TaskSourceDomain sourceDomain,
        List<String> parameters,
        List<String> subjects,
        List<String> outputs,
        TaskFulfillmentRole fulfillmentRole) { }
```

在 taskId 检查之后增加：

```java
Set<SemanticTaskKey> semanticKeys = new HashSet<>();
for (SemanticTask task : plan.getTasks()) {
    if (!semanticKeys.add(semanticKey(task))) {
        issues.add("PLAN_SEMANTIC_TASK_DUPLICATE");
    }
}
```

parameters 的 canonical 内容直接读取 getter，并对 Set 排序；SubjectReference 仅投影 type/id/contentVersion。

- [ ] **Step 5: 运行路由验证回归**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=SemanticGoalDeduplicatorTest,SemanticPlanCompilerFulfillmentRoleTest,SemanticPlanValidatorTest,SemanticTurnPlanTest,DefaultTurnRouterDeterministicTest' test
```

Expected: BUILD SUCCESS；正常多任务仍保序，语义重复 fail closed。

- [ ] **Step 6: 提交本任务**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticGoalDeduplicator.java backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticSignalCollector.java backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanCompiler.java backend/src/main/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidator.java backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticGoalDeduplicatorTest.java backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticPlanCompilerFulfillmentRoleTest.java backend/src/test/java/com/portfolio/agent/answer/routing/service/SemanticPlanValidatorTest.java
git diff --cached --check
git commit -m "fix: 阻止语义重复的 Agent 任务"
```

---

### Task 4: 推荐数量完整性和部分完成协议

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskResultPayload.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskOutcome.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticTurnOutcome.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutor.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutorTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/routing/domain/SemanticTurnOutcomeTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponseTest.java`

**Interfaces:**
- Produces in recommendation projection/response: `requestedSize`、`actualSize`、`candidateScope`、`selectedPortfolioIds`、`unsatisfiedConstraints`、`reasonCodes`。
- Produces reason codes: `INSUFFICIENT_ELIGIBLE_PROJECTS`、`INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS`、`CAPABILITY_COVERAGE_INCOMPLETE`。

- [ ] **Step 1: 写 2/2、1/3、0/3 三类执行失败测试**

测试 fixture 给 executor 返回确定的 candidateSet/evidence bundle，并断言：

```java
assertEquals(TaskResolution.ANSWERED, exact.getResolution());
assertEquals(TaskResolution.PARTIALLY_ANSWERED, partial.getResolution());
assertEquals(Set.of("INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS"), partial.getReasonCodes());
assertEquals(TaskResolution.NOT_SUPPORTED, empty.getResolution());
```

对 partial payload 断言：requestedSize=3、actualSize=1、candidateScope=`ALL_PUBLISHED_PROJECTS`、selectedPortfolioIds 只有一个且不重复。

- [ ] **Step 2: 运行测试并确认 1/3 当前仍被标记 ANSWERED**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=P3PortfolioSemanticTaskExecutorTest,ConversationAnswerResponseMapperTest' test
```

Expected: FAIL；现有逻辑只看 EvidenceSupportAssessment 状态，没有比较 requestedSize 和 actualSize。

- [ ] **Step 3: 扩展 canonical recommendation projection**

在 `RecommendationProjection` 中加入受控枚举和字段：

```java
public enum CandidateScope {
    ALL_PUBLISHED_PROJECTS,
    EXPLICIT_PROJECT_SET
}

private final int actualSize;
private final CandidateScope candidateScope;
private final List<String> reasonCodes;
```

构造器强制：`actualSize == items.size()`、`actualSize == selectedPortfolioIds.size()`、selectedPortfolioIds 去重、exact 成功时 reasonCodes 为空、partial/zero 时 reasonCodes 非空。

- [ ] **Step 4: 在 executor 形成单一完整性判定**

增加纯函数并在 `recommendationPayload` 后使用：

```java
private RecommendationCompletion completion(
        SemanticTaskParameters.PortfolioRecommend parameters,
        PortfolioRetrievalCandidateSet candidateSet,
        List<RecommendationItem> items,
        EvidenceSupportAssessment assessment) {
    int requested = parameters.getRequestedSize().getValue();
    int actual = items.size();
    LinkedHashSet<String> reasons = new LinkedHashSet<>();
    if (actual < requested) {
        if (candidateSet.getCandidateSubjects().size() < requested) {
            reasons.add("INSUFFICIENT_ELIGIBLE_PROJECTS");
        } else {
            reasons.add("INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS");
        }
    }
    if (!assessment.getOmittedLabels().isEmpty()) {
        reasons.add("CAPABILITY_COVERAGE_INCOMPLETE");
    }
    return new RecommendationCompletion(requested, actual, List.copyOf(reasons));
}
```

结果规则固定为：actual==requested → ANSWERED；0&lt;actual&lt;requested → PARTIALLY_ANSWERED；actual==0 → NOT_SUPPORTED。部分完成的 `TaskOutcome` 使用新增 overload 接收 `Set<String> reasonCodes`；零结果在构造禁止空 items 的 payload 之前返回 `INSUFFICIENT_ELIGIBLE_PROJECTS` 或 `INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS`。

`SemanticTurnOutcome.derivePlanOutcome` 不能把“所有 PRIMARY 都是 PARTIALLY_ANSWERED”归为 SUCCEEDED：

```java
boolean containsPartial = considered.stream().anyMatch(outcome ->
        outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED);
if (answeredCount == considered.size()) {
    return containsPartial ? PlanOutcome.PARTIAL : PlanOutcome.SUCCEEDED;
}
```

- [ ] **Step 5: 扩展公共 DTO 和 Mapper**

`PortfolioRecommendationResponse` 构造器和 getter 增加：

```java
int requestedSize,
int actualSize,
String candidateScope,
List<String> selectedPortfolioIds,
List<String> reasonCodes
```

所有 mapper 分支都从同一个 `RecommendationProjection` 复制字段；legacy domain recommendation 无这些信息时，使用 `items.size()` 作为 requested/actual、`EXPLICIT_PROJECT_SET` 作为兼容 scope、空 reasonCodes，禁止返回 null 数量。

- [ ] **Step 6: 运行合同和执行回归**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=P3PortfolioSemanticTaskExecutorTest,TaskOutcomeContractTest,SemanticTurnOutcomeTest,ConversationAnswerResponseMapperTest,ConversationAnswerResponseTest,P5PublicContractSerializationTest' test
```

Expected: BUILD SUCCESS；1/3 在 task、top-level resolution 和 recommendation DTO 中都为 partial。

- [ ] **Step 7: 提交本任务**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskResultPayload.java backend/src/main/java/com/portfolio/agent/answer/routing/domain/TaskOutcome.java backend/src/main/java/com/portfolio/agent/answer/routing/domain/SemanticTurnOutcome.java backend/src/main/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutor.java backend/src/main/java/com/portfolio/agent/answer/dto/response/PortfolioRecommendationResponse.java backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java backend/src/test/java/com/portfolio/agent/answer/routing/adapter/execution/P3PortfolioSemanticTaskExecutorTest.java backend/src/test/java/com/portfolio/agent/answer/routing/domain/SemanticTurnOutcomeTest.java backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java backend/src/test/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponseTest.java
git diff --cached --check
git commit -m "feat: 公开项目推荐数量完整性"
```

---

### Task 5: 正式预设 canonical public projection

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`

**Interfaces:**
- Consumes: runtime 已审核 `AnswerQuestion` required/supporting claims 和 `ConversationAnswerResult.blocks`。
- Produces: top-level blocks 与单一 PRIMARY completed task 的 Section Result 从同一组 domain blocks 投影。

- [ ] **Step 1: 写 SQL 预设单任务和逐字段一致性失败测试**

扩展 `sqlAuditOverviewPresetReplaysTheScreenshotRequestWithoutModelCapabilities`：

```java
.andExpect(jsonPath("$.agentTurn.plan.taskCount").value(1))
.andExpect(jsonPath("$.agentTurn.completedTasks.length()").value(1))
.andExpect(jsonPath("$.agentTurn.completedTasks[0].fulfillmentRole").value("PRIMARY"));
```

读取 JSON 后逐块比较 top-level 与 completed task：sectionType、title、content、claimIds、evidenceIds、sourceReferences；比较前按 blockId 之外的业务字段保持原顺序。

- [ ] **Step 2: 运行测试并确认当前两条投影路径不完全一致**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=PresetContractBundleIntegrationTest,ConversationAnswerResponseMapperTest' test
```

Expected: FAIL；至少 task 数量或 task blocks 的公开字段与 top-level preset blocks 不一致。

- [ ] **Step 3: 把 preset blocks 收敛为 runtime 唯一 material**

保留 `presetBlocks(request, content)` 作为唯一已审核 material 构建入口，删除任何再次按自由文本检索或重组正式预设正文的分支。`projectedBlocks` 对合法 preset 只返回该 material；空 material 视为合同不可用，不退化到自由文本事实回答。

```java
List<ConversationAnswerBlock> canonicalPresetBlocks = hasValidPreset(request, content)
        ? presetBlocks(request, content)
        : List.of();
```

- [ ] **Step 4: Completed Task 复用同一 public block mapper**

在 `ConversationAnswerResponseMapper.toResultPayload` 的 Section Result 分支中，合法 preset 不再读取独立的 `TaskResultPayload.SectionResultPayload`，而是复用与 top-level 相同的 domain blocks：

```java
if (result.getQuestionPresetId() != null && !result.getBlocks().isEmpty()) {
    List<ConversationAnswerBlockResponse> canonical = result.getBlocks().stream()
            .map(block -> toBlockResponse(block, false, true))
            .filter(Objects::nonNull)
            .toList();
    return new CompletedTaskResponse.ResultPayload(
            "SECTION_RESULT", canonical, null, null);
}
```

该分支和 `topLevelBlocks` 必须调用同一个 `toBlockResponse(block, false, true)`；不得复制一套 source-reference 逻辑。

- [ ] **Step 5: 对所有正式预设运行合同回归**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=PresetContractBundleIntegrationTest,ConversationAnswerResponseMapperTest,PresetContractCrossCheckTest,PresetContractVersionTest' test
```

Expected: BUILD SUCCESS；SQL overview 恰好一个 PRIMARY task，所有 active preset 均保持 verified public evidence。

- [ ] **Step 6: 提交本任务**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java
git diff --cached --check
git commit -m "fix: 统一正式预设公开投影"
```

---

### Task 6: 后端 HTTP 闭环集成测试

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/controller/AgentBackendClosureIntegrationTest.java`

**Interfaces:**
- Consumes: `/api/v1/public-content`、`POST /api/v2/answers`。
- Produces: 不依赖前端的六场景 HTTP 回归门禁。

- [ ] **Step 1: 建立 Spring Boot/MockMvc fixture**

```java
@SpringBootTest(classes = PortfolioAgentApplication.class, properties = {
        "portfolio.model-expression.enabled=false",
        "portfolio.conversational-agent.enabled=false",
        "portfolio.answer-production.requests-per-minute=1000"
})
@AutoConfigureMockMvc
class AgentBackendClosureIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
}
```

- [ ] **Step 2: 写六个完整 HTTP 场景**

每个场景使用唯一 requestToken，并断言：

1. `1` + active SQL → NEEDS_CLARIFICATION、无 plan/blocks/evidence；
2. `给我推荐两个项目` + active SQL → candidateScope=ALL_PUBLISHED_PROJECTS、requestedSize=2、actualSize=2，两个 portfolioId 不同；
3. `给我推荐三个项目` → actualSize=3，或 PARTIALLY_ANSWERED 且 actualSize&lt;3、reasonCodes 非空；
4. 明确点名 SQL → 单一 PORTFOLIO_FACT 主任务且 subjectId=sql-audit；
5. 推荐后使用服务端返回的 Context Handle + resultItemId → 只允许选择该结果中的项目，不接受客户端自报陌生 subject；
6. SQL overview preset → taskCount=1、completedTasks=1、top-level/task blocks 公共字段一致。

推荐数量断言使用条件分支，但禁止接受“一项 + ANSWERED”：

```java
if (root.path("resolution").asText().equals("ANSWERED")) {
    assertThat(recommendation.path("actualSize").asInt())
            .isEqualTo(recommendation.path("requestedSize").asInt());
} else {
    assertThat(root.path("resolution").asText()).isEqualTo("PARTIALLY_ANSWERED");
    assertThat(recommendation.path("actualSize").asInt())
            .isLessThan(recommendation.path("requestedSize").asInt());
    assertThat(recommendation.path("reasonCodes").isEmpty()).isFalse();
}
```

- [ ] **Step 3: 运行集成测试并修正仅由新增合同暴露的映射遗漏**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=AgentBackendClosureIntegrationTest' test
```

Expected: BUILD SUCCESS，六个场景通过；若失败，只修改前五项任务范围内的后端文件，不扩展产品需求。

- [ ] **Step 4: 运行后端 Agent 闭环回归集合**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=AgentBackendClosureIntegrationTest,NoiseConversationIntegrationTest,PresetContractBundleIntegrationTest,DefaultTurnRouterDeterministicTest,SemanticPlanValidatorTest,P3PortfolioSemanticTaskExecutorTest,ConversationAnswerResponseMapperTest' test
```

Expected: BUILD SUCCESS，无现有 answer/routing/mapper/controller 合同回归。

- [ ] **Step 5: 提交本任务**

```powershell
git add backend/src/test/java/com/portfolio/agent/answer/controller/AgentBackendClosureIntegrationTest.java
git diff --cached --check
git commit -m "test: 覆盖 Agent 后端闭环场景"
```

---

### Task 7: Packaged JAR 构建身份和真实 API 验收

**Files:**
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`

**Interfaces:**
- Produces: `-SkipPlaywright` 后端-only 模式；脱敏的 commit、JAR SHA-256、PID、port、contentVersion 和场景状态摘要。

- [ ] **Step 1: 先写 runner 合同失败测试**

在 `run-jar-e2e.test.ps1` 断言 runner 暴露 `SkipPlaywright`，并检查输出包含：

```powershell
'Build identity: commit='
'JAR SHA-256: '
'Agent backend closure smoke passed.'
```

同时断言输出不包含 privacy sentinel、问题正文、回答正文、contextHandle 和 requestToken。

- [ ] **Step 2: 运行脚本测试并确认新参数不存在**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-jar-e2e.test.ps1
```

Expected: 非零退出，报告缺少 `SkipPlaywright` 或闭环 smoke 证据。

- [ ] **Step 3: 在启动前记录可验证构建身份**

```powershell
$commit = (& git -C $root rev-parse HEAD).Trim()
$jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$builtAt = (Get-Item -LiteralPath $jar).LastWriteTimeUtc.ToString('O')
if ($commit -notmatch '^[a-f0-9]{40}$' -or $jarHash -notmatch '^[a-f0-9]{64}$') {
    throw 'Packaged build identity is invalid.'
}
Write-Output "Build identity: commit=$commit"
Write-Output "JAR SHA-256: $jarHash"
Write-Output "JAR builtAt UTC: $builtAt"
```

继续使用既有 `Start-Process -WindowStyle Hidden`、PID 端口归属检查和 finally 清理。

- [ ] **Step 4: 增加后端-only API 探针**

增加 `[switch]$SkipPlaywright`。readiness 后发送噪声、两项目推荐、三项目推荐和 SQL preset 请求；只输出脱敏摘要：

```powershell
$summary = [ordered]@{
    httpStatus = [int]$httpResponse.StatusCode
    resolution = [string]$response.resolution
    disposition = [string]$response.agentTurn.disposition
    taskCount = @($response.agentTurn.completedTasks).Count
    requestedSize = [int]$response.portfolioRecommendation.requestedSize
    actualSize = [int]$response.portfolioRecommendation.actualSize
    reasonCodes = @($response.portfolioRecommendation.reasonCodes)
}
```

断言推荐 exact/partial 规则、噪声无 blocks/evidence、preset 单任务；不得把完整 response 序列化到 stdout。`SkipPlaywright` 为真时跳过 npm 命令，但仍执行日志隐私检查、进程清理和环境恢复。

- [ ] **Step 5: 运行 runner 自测试**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-jar-e2e.test.ps1
```

Expected: 输出 `run-jar-e2e tests passed`，fixture 端口和进程均已清理。

- [ ] **Step 6: 从当前工作树干净构建后端 JAR**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f .\backend\pom.xml clean package '-DskipFrontend=true'
Get-FileHash -LiteralPath .\backend\target\portfolio-agent.jar -Algorithm SHA256
```

Expected: BUILD SUCCESS；JAR 存在并输出 64 位 SHA-256。

- [ ] **Step 7: 对新 JAR 执行真实后端 API 验收**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-jar-e2e.ps1 -JarPath .\backend\target\portfolio-agent.jar -ContextMode DISABLED -SkipPlaywright -Port 43174
```

Expected: 输出 commit、JAR SHA-256、JAR builtAt、PID/port ownership、contentVersion、HTTP 状态、`Agent backend closure smoke passed.` 和进程停止证据；输出中没有问题/回答正文。

- [ ] **Step 8: 提交本任务**

```powershell
git add scripts/run-jar-e2e.ps1 scripts/run-jar-e2e.test.ps1
git diff --cached --check
git commit -m "test: 验证 packaged Agent 后端闭环"
```

---

### Task 8: 全量后端回归和交付记录

**Files:**
- Modify: `docs/superpowers/specs/2026-08-17-agent-backend-deterministic-routing-closure-design.md`

**Interfaces:**
- Produces: 规格末尾追加实际提交、测试命令、JAR hash、contentVersion 和验收状态；不记录请求或回答正文。

- [ ] **Step 1: 运行全量后端测试**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f .\backend\pom.xml test
```

Expected: BUILD SUCCESS，无失败或错误测试。

- [ ] **Step 2: 运行后端打包和 runner 测试**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f .\backend\pom.xml clean package '-DskipFrontend=true'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-jar-e2e.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-jar-e2e.ps1 -JarPath .\backend\target\portfolio-agent.jar -ContextMode DISABLED -SkipPlaywright -Port 43174
```

Expected: 三条命令均为零退出；packaged JAR 闭环通过。

- [ ] **Step 3: 记录脱敏验收结果**

在后端规格追加 `实施验收`，只记录：

```markdown
- Git commit: the exact 40-character value returned by `git rev-parse HEAD`
- JAR SHA-256: the exact 64-character value returned by `Get-FileHash`
- contentVersion: the exact value returned by `/api/v1/public-content`
- Unit/integration tests: PASS
- Packaged-JAR closure smoke: PASS
- Verified fields: HTTP status, resolution, disposition, taskCount,
  requestedSize, actualSize, reasonCodes
```

记录时复制命令的实际值，不粘贴 response body。

- [ ] **Step 4: 检查范围和工作树**

```powershell
git diff --check
git status --short
git diff --stat 012b124..HEAD
```

Expected: 没有 `frontend/` 修改；只有计划列出的后端、测试、脚本和规格文件。

- [ ] **Step 5: 提交验收记录**

```powershell
git add docs/superpowers/specs/2026-08-17-agent-backend-deterministic-routing-closure-design.md
git diff --cached --check
git commit -m "docs: 记录 Agent 后端闭环验收"
```

---

## Final Acceptance Matrix

| 场景 | 必须结果 | 禁止结果 |
|---|---|---|
| `1` + active SQL | NEEDS_CLARIFICATION；无 plan/blocks/evidence | SQL 事实回答、执行完成 |
| `给我推荐两个项目` + active SQL | ALL_PUBLISHED_PROJECTS；requested=2；actual=2 或结构化 partial | SQL-only candidate、1 项 COMPLETED |
| `给我推荐三个项目` | actual=3，或 actual&lt;3 + PARTIALLY_ANSWERED + reasonCodes | 静默裁剪 requestedSize |
| 明确点名 SQL | 单一 SQL FACT 主任务 | 全作品集事实混入 |
| 可信推荐续接 | 只在已授权结果集合/结果项内收窄 | 客户端自报主体扩大范围 |
| SQL overview preset | 一个 PRIMARY task；两处 blocks 公共字段一致 | 重复 task/stage、双正文 |
| packaged JAR | commit/hash/PID/port/contentVersion 可证明 | 复用来源不明进程、输出正文或令牌 |

## Out of Scope During Execution

- 前端组件、CSS、Vue 状态管理和前端 E2E 用例；
- Case 推荐；
- 大模型任务类型分类；
- `stp-v3` 或模型主导计划；
- 删除 stp-v1/顶层兼容投影；
- 新增公开管理或调试 API。
