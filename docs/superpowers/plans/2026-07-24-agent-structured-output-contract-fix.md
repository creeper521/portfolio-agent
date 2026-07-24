# Agent Structured Output Contract Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 DeepSeek V4 Flash 与 GLM 4.7 按后端 DTO 返回稳定的结构化 JSON，不再因字段结构不匹配进入确定性降级。

**Architecture:** 在 `ConversationalPromptFactory` 中按 operation 追加明确的输出契约，继续使用供应商兼容的 `response_format=json_object` 和后端严格反序列化。`suggestion` 使用对象包装数组，并由适配器解包为现有领域列表，保持外部 API 不变。

**Tech Stack:** Java 21、Spring Boot 3.5、Jackson、JUnit 5、AssertJ、Spring MockRestServiceServer、Maven

## Global Constraints

- 不修改前端或现有 `/api/v2/answers` 响应结构。
- 不兼容供应商自行发明的字段别名。
- 不增加跨 Provider 重试。
- 不在仓库、日志或测试输出中保存模型密钥、Prompt 正文或访客私密内容。
- 保持严格反序列化、事实校验与确定性降级策略。
- 真实冒烟测试只发送不含作品集私密资料的通用技术问题。

---

### Task 1: 为六类 operation 注入严格输出契约

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactoryTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactory.java`

**Interfaces:**
- Consumes: `ConversationalPromptFactory.systemPrompt(String operation)`
- Produces: `private String outputContract(String operation)`，返回与 operation 对应的 JSON 字段、枚举及最小示例

- [ ] **Step 1: 写入 generation 契约失败测试**

```java
@Test
void generationPromptDeclaresExactConversationDraftShape() {
    ConversationalPromptFactory factory =
            new ConversationalPromptFactory(new ObjectMapper(), "base");

    String prompt = factory.systemPrompt("generation");

    assertThat(prompt)
            .contains("title", "resolution", "blocks")
            .contains("sourceScope", "content", "claimIds", "evidenceIds")
            .contains("ANSWERED", "BOUNDARY", "REJECTED")
            .contains("不要输出未声明字段");
}
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=ConversationalPromptFactoryTest test
```

Expected: FAIL，提示 Prompt 不包含 `title`、`resolution` 或 `blocks`。

- [ ] **Step 3: 添加其余 operation 的契约测试**

```java
@ParameterizedTest
@MethodSource("operationContracts")
void declaresContractForEveryOperation(
        String operation,
        List<String> requiredFragments
) {
    ConversationalPromptFactory factory =
            new ConversationalPromptFactory(new ObjectMapper(), "base");

    assertThat(factory.systemPrompt(operation))
            .contains(requiredFragments.toArray(String[]::new));
}

private static Stream<Arguments> operationContracts() {
    return Stream.of(
            Arguments.of("intent", List.of(
                    "intent", "answerScope", "confidence", "projectSlug",
                    "caseSlug", "facet", "clarificationRequired")),
            Arguments.of("tool_plan", List.of(
                    "calls", "kind", "projectSlugs", "caseSlugs",
                    "claimIds", "sectionType")),
            Arguments.of("generation", List.of(
                    "title", "resolution", "blocks", "sourceScope",
                    "content", "claimIds", "evidenceIds")),
            Arguments.of("review", List.of(
                    "unsupportedBlockIndexes", "reasonCodes")),
            Arguments.of("suggestion", List.of(
                    "questions", "text", "projectSlug", "caseSlug", "facet")),
            Arguments.of("summary", List.of("summary"))
    );
}
```

- [ ] **Step 4: 实现最小 operation 契约映射**

在 `ConversationalPromptFactory` 中将 `systemPrompt` 改为：

```java
public String systemPrompt(String operation) {
    return systemPrompt
            + "\n\n当前任务：" + operation
            + "\n\n输出契约：\n" + outputContract(operation);
}
```

新增：

```java
private String outputContract(String operation) {
    return switch (operation) {
        case "intent" -> """
                只输出一个 JSON 对象，字段必须且只能是：
                intent: CONVERSATION|GENERAL_KNOWLEDGE|PORTFOLIO_GROUNDED|HYBRID|TIME_SENSITIVE|UNSUPPORTED_OR_UNSAFE
                answerScope: CONVERSATION|GENERAL|PORTFOLIO|HYBRID
                confidence: 0 到 1 的数字
                projectSlug: 字符串或 null
                caseSlug: 字符串或 null
                facet: OVERVIEW|IMPLEMENTATION|DECISION|CHALLENGE|INCIDENT|VERIFICATION|LIMITATION|LEARNING
                clarificationRequired: boolean
                示例：{"intent":"GENERAL_KNOWLEDGE","answerScope":"GENERAL","confidence":0.98,"projectSlug":null,"caseSlug":null,"facet":"OVERVIEW","clarificationRequired":false}
                不要输出未声明字段。
                """;
        case "tool_plan" -> """
                只输出一个 JSON 对象：{"calls":[...]}。
                每个 call 的字段必须且只能是 kind、projectSlugs、caseSlugs、claimIds、sectionType。
                kind 只能是 GET_PROJECT|GET_CASE|GET_CLAIMS|GET_EVIDENCE_FOR_CLAIMS|GET_TIMELINE|SEARCH_PUBLIC_CONTENT|COMPARE_PROJECTS。
                projectSlugs、caseSlugs、claimIds 必须是字符串数组；没有值时使用 []。
                sectionType 只能是 BACKGROUND|RESPONSIBILITY|SOLUTION|VERIFICATION|STATUS|BOUNDARY|REJECTED 或 null。
                不需要工具时输出 {"calls":[]}。不要输出未声明字段。
                """;
        case "generation" -> """
                只输出一个 JSON 对象，字段必须且只能是 title、resolution、blocks。
                resolution 只能是 ANSWERED|BOUNDARY|REJECTED。
                blocks 必须是数组；每个 block 的字段必须且只能是 sourceScope、content、claimIds、evidenceIds。
                sourceScope 只能是 GENERAL|PORTFOLIO。
                claimIds、evidenceIds 必须是字符串数组；通用内容必须使用 []；作品集内容只能使用 approved_portfolio_context 中存在的 ID。
                示例：{"title":"REST API","resolution":"ANSWERED","blocks":[{"sourceScope":"GENERAL","content":"REST API 是一种接口设计风格。","claimIds":[],"evidenceIds":[]}]}
                不要把推荐问题放入本对象。不要输出未声明字段。
                """;
        case "review" -> """
                只输出一个 JSON 对象，字段必须且只能是 unsupportedBlockIndexes、reasonCodes。
                两个字段都必须是数组；没有问题时输出 {"unsupportedBlockIndexes":[],"reasonCodes":[]}。
                unsupportedBlockIndexes 只能包含输入 blocks 的零基索引。不要输出未声明字段。
                """;
        case "suggestion" -> """
                只输出一个 JSON 对象：{"questions":[...]}。
                questions 必须包含 0 到 3 项；每项字段必须且只能是 text、projectSlug、caseSlug、facet。
                projectSlug、caseSlug、facet 没有值时使用 null；不得编造 approved_portfolio_context 中不存在的 slug。
                不要输出顶层数组。不要输出未声明字段。
                """;
        case "summary" -> """
                只输出一个 JSON 对象，字段必须且只能是 summary。
                summary 必须是简洁字符串，只总结对话，不新增作者事实。
                示例：{"summary":"访客询问了 REST API 的基本概念。"}
                不要输出未声明字段。
                """;
        default -> throw new IllegalArgumentException(
                "unsupported conversation operation: " + operation);
    };
}
```

- [ ] **Step 5: 运行 Prompt 测试并确认通过**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=ConversationalPromptFactoryTest test
```

Expected: BUILD SUCCESS，所有 Prompt 契约测试通过。

- [ ] **Step 6: 提交 Task 1**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactory.java backend/src/test/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactoryTest.java
git commit -m "fix: 补全 Agent 模型输出契约"
```

---

### Task 2: 解析 suggestion 对象包装

**Files:**
- Modify: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapterTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapter.java`

**Interfaces:**
- Consumes: Provider JSON `{"questions":[ConversationSuggestedQuestion...]}`
- Produces: `ConversationModelResult<List<ConversationSuggestedQuestion>>`
- Adds: private DTO `SuggestedQuestionsResponse`

- [ ] **Step 1: 写入 suggestion 对象包装失败测试**

```java
@Test
void unwrapsSuggestedQuestionsFromJsonObject() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
    server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
            .andRespond(withSuccess("""
                    {"choices":[{"message":{"content":"{\\"questions\\":[{\\"text\\":\\"如何验证实现？\\",\\"projectSlug\\":\\"sql-audit\\",\\"caseSlug\\":null,\\"facet\\":\\"VERIFICATION\\"}]}"}}]}
                    """, MediaType.APPLICATION_JSON));

    ConversationModelResult<List<ConversationSuggestedQuestion>> result =
            adapter.suggest(route(), window(), List.of(), List.of());

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getValue()).singleElement().satisfies(question -> {
        assertThat(question.getText()).isEqualTo("如何验证实现？");
        assertThat(question.getProjectSlug()).isEqualTo("sql-audit");
        assertThat(question.getFacet())
                .isEqualTo(PortfolioKnowledgeFacet.VERIFICATION);
    });
    server.verify();
}
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=OpenAiCompatibleConversationalModelAdapterTest#unwrapsSuggestedQuestionsFromJsonObject test
```

Expected: FAIL，现有代码尝试把顶层对象反序列化为 `List<ConversationSuggestedQuestion>`。

- [ ] **Step 3: 实现 suggestion 包装 DTO 与解包**

将 `suggest` 的响应类型改为 `SuggestedQuestionsResponse`：

```java
ConversationModelResult<SuggestedQuestionsResponse> result = post(
        "suggestion",
        promptFactory.suggestionPrompt(conversation, approved),
        objectMapper.constructType(SuggestedQuestionsResponse.class),
        0.3);
if (!result.isSuccessful()) {
    return ConversationModelResult.failure(result.getFailureCode());
}
return ConversationModelResult.success(result.getValue().getQuestions());
```

在适配器底部新增：

```java
@JsonIgnoreProperties(ignoreUnknown = false)
private static final class SuggestedQuestionsResponse {
    private List<ConversationSuggestedQuestion> questions;

    public List<ConversationSuggestedQuestion> getQuestions() {
        return questions == null ? List.of() : List.copyOf(questions);
    }

    public void setQuestions(List<ConversationSuggestedQuestion> questions) {
        this.questions = questions;
    }
}
```

- [ ] **Step 4: 运行适配器测试并确认通过**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=OpenAiCompatibleConversationalModelAdapterTest test
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交 Task 2**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapter.java backend/src/test/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapterTest.java
git commit -m "fix: 解析动态推荐问题对象"
```

---

### Task 3: 全量验证与真实 Provider 验收

**Files:**
- No production file changes expected

**Interfaces:**
- Consumes: packaged Spring Boot JAR and process-injected provider keys
- Produces: test evidence for automated and real end-to-end behavior

- [ ] **Step 1: 运行后端全量测试**

Run:

```powershell
Set-Location backend
C:\tools\apache-maven-3.9.9\bin\mvn.cmd test
```

Expected: BUILD SUCCESS，0 failures，0 errors。

- [ ] **Step 2: 构建可执行 JAR**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -DskipTests package
```

Expected: BUILD SUCCESS，生成 `backend/target/portfolio-agent.jar`。

- [ ] **Step 3: DeepSeek 真实端到端冒烟测试**

临时启用以下非密钥配置，并继承进程中的 `PORTFOLIO_AGENT_DEEPSEEK_API_KEY`：

```text
portfolio.model-expression.enabled=true
portfolio.model-expression.provider=DEEPSEEK_V4_FLASH
portfolio.model-expression.external-data-policy-approved=true
portfolio.conversational-agent.enabled=true
portfolio.conversational-agent.visitor-data-policy-approved=true
```

向 `/api/v2/answers` 发送“什么是 REST API？”。Expected：

```text
intent=GENERAL_KNOWLEDGE
answerScope=GENERAL
resolution=ANSWERED
degraded=false
blocks>=1
```

- [ ] **Step 4: GLM 真实端到端冒烟测试**

使用同一请求，将 Provider 改为 `GLM_4_7`。Expected：

```text
intent=GENERAL_KNOWLEDGE
answerScope=GENERAL
resolution=ANSWERED
degraded=false
blocks>=1
```

- [ ] **Step 5: 清理进程并检查工作区**

确认测试端口没有监听进程，删除测试日志，并运行：

```powershell
git status --short
git diff --check
```

Expected: 仅保留用户原有的无关修改，没有密钥文件或测试日志进入 Git。
