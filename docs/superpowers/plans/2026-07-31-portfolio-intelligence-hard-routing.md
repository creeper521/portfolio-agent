# Agent 作品集智能检索与确定性推荐实施计划
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> 执行要求：使用子代理逐任务开发；实现任务遵循 TDD，每项完成后由独立代理复核。存在依赖的任务按波次执行，同一波内互不修改重叠文件。

**目标：** 将普通作品集 RAG、比较、确定性推荐和推荐调整统一收进 Agent 内部的 `PortfolioIntelligence` 硬路由模块，以 PostgreSQL/pgvector 为主检索 Adapter、Bundle 为降级 Adapter，并删除公开 Selection 接口。

**架构：** `/api/v2/answers` 是唯一用户入口。规则优先的任务解析器将自然语言转换为受校验的 `PortfolioTask`；模型只在规则不能唯一判断时做受约束分类。`PortfolioIntelligence.resolve(PortfolioTask)` 通过硬路由调用统一 `PortfolioRetriever`、证据组装和确定性推荐策略。推荐后续上下文只存在当前标签页内存，由前端原样回传，后端无状态重新校验。

**技术栈：** Java 21、Spring Boot、Spring JDBC、PostgreSQL 16、pgvector 0.8.5、JUnit 5、Mockito、MockMvc、Maven。

## 全局约束

- 只修改后端、测试和交接文档；不得修改 `frontend/src`。
- 唯一公开入口是 `POST /api/v2/answers`；删除 `/api/portfolio-selections`，不保留兼容期。
- 生产和测试 Java 禁止使用 `var`、Java `record`、Lombok。领域值对象使用显式 `final` 类、`private final` 字段、防御性拷贝、构造校验、getter、`equals`、`hashCode` 和 `toString`。
- 模型不得生成或选择 SQL、检索 Adapter、推荐策略及最终作品 ID。
- 硬路由强制执行公开 Release、已验证 Claim、已批准 Evidence 和隐私门禁。
- PostgreSQL/pgvector 健康时为主 Adapter；基础设施失败时自动降级到 Bundle，并设置 `degraded=true`。不得吞掉参数错误和编程错误。
- 作品集任务模型分类默认阈值为 `0.80`；低于阈值进入追问。
- 推荐数量默认 3，有效范围为 2 到 5。
- 推荐上下文只存在当前标签页内存；刷新、关闭标签页或新建标签页会话后消失。
- 浏览器不得持久化推荐上下文；后端不得建立 Registry、Session、缓存或数据库副本。
- 后端把回传上下文视为不可信输入，重新校验批次指纹、内容版本、规范化条件、公开作品 ID 和 Evidence 门禁。
- 原始问题和自由文本 `goal` 不进入推荐上下文或日志。日志不得记录批次标识、上下文内容、问题正文或作品内容。
- 非推荐回答省略 `portfolioRecommendation`，不得序列化为 `null`。
- 所有实现提交使用中文提交信息；每个任务单独提交。
- 保留无关工作区改动，不执行 `reset --hard`、`checkout --` 或批量清理。

## 稳定 HTTP 契约

推荐响应增加可选字段：

```json
{
  "portfolioRecommendation": {
    "recommendationBatchId": "rec_<sha256-hex>",
    "context": {
      "contentVersion": "public-2026-07-31",
      "careerTrack": "BACKEND",
      "audienceRole": "INTERVIEWER",
      "capabilityCodes": ["POSTGRESQL", "RAG"],
      "requestedSize": 2,
      "selectedPortfolioIds": ["project-1", "case-2"]
    },
    "items": [],
    "satisfiedConstraints": [],
    "unsatisfiedConstraints": []
  }
}
```

推荐调整请求在现有 `context` 中回传：

```json
{
  "recommendationContext": {
    "recommendationBatchId": "rec_<sha256-hex>",
    "contentVersion": "public-2026-07-31",
    "careerTrack": "BACKEND",
    "audienceRole": "INTERVIEWER",
    "capabilityCodes": ["POSTGRESQL", "RAG"],
    "requestedSize": 2,
    "selectedPortfolioIds": ["project-1", "case-2"]
  }
}
```

`recommendationBatchId` 是规范化上下文的确定性 SHA-256 指纹，只用于完整性检测，不是认证凭据。指纹输入使用固定字段顺序、排序后的能力代码和保持推荐顺序的作品 ID。任何失配都进入 `CLARIFICATION_REQUIRED`。

## 文件结构

### 新建深模块

- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/`：任务模式、条件、推荐上下文、推荐项、推荐结果、追问和检索值对象。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetriever.java`：统一检索 seam。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioTaskClassifierPort.java`：受约束分类 seam。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolver.java`：规则优先任务解析。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskValidator.java`：条件和推荐上下文校验。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/RecommendationBatchFingerprint.java`：规范化 SHA-256 指纹。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioRecommendationPolicy.java`：包装现有确定性选择策略。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioIntelligence.java`：唯一模块接口。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`：硬路由实现。

### 新建 Adapter

- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetriever.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresKnowledgeQuery.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQuery.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresPortfolioRetriever.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/FailoverPortfolioRetriever.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`

### 修改 Agent 链路

- `ConversationAnswerContextRequest`：增加可选的结构化 `recommendationContext`。
- `ConversationAnswerResult`：保存可选推荐并在所有重建方法中保留。
- `ConversationAnswerResponse` 与 Mapper：输出可选推荐。
- 模型 Adapter 与 Prompt Factory：实现受约束作品集分类。
- `ConversationalAgentRuntime`：在生成回答前调用硬路由模块。
- Spring 配置：装配单一 `PortfolioIntelligence` 与单一外部 `PortfolioRetriever`。

### 删除公开 Selection 表面

- 删除 `PortfolioSelectionController`、仅服务该 Controller 的 HTTP DTO、Mapper 和测试。
- 保留并迁移 Candidate、`TopKSelectionStrategy`、`ExhaustiveSelectionStrategy`、Benchmark fixture/evaluator/CLI。

---

## Task 1：建立显式不可变领域契约与请求上下文

**文件：**

- 新建 `answer/intelligence/domain` 下的任务、条件、推荐上下文、推荐结果和检索值对象。
- 新建 `dto/request/PortfolioRecommendationContextRequest.java`。
- 修改 `ConversationAnswerContextRequest.java`。
- 新建对应领域测试，修改 `ConversationAnswerRequestTest.java`。

**接口：**

- `PortfolioTaskMode`：`FACT_LOOKUP`、`COMPARISON`、`RECOMMENDATION`、`REFINE_RECOMMENDATION`、`CLARIFICATION_REQUIRED`。
- `PortfolioConditions`：`careerTrack`、`audienceRole`、`capabilityCodes`、`goal`、`requestedSize`，支持 `empty()` 与确定性合并。
- `PortfolioRecommendationContext`：批次标识、内容版本、规范化条件和有序作品 ID；不包含原始问题、`goal` 或时间戳。
- `PortfolioTask`：turnId、question、mode、confidence、conditions、可选 recommendationContext、可选 refinement。
- 所有集合在构造时 `List.copyOf` / `Set.copyOf`，公开 getter 不泄漏可变引用。

**TDD：**

1. 先写请求反序列化、非法批次格式、数量越界、空内容版本、集合防御性拷贝测试。
2. 运行：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=ConversationAnswerRequestTest,PortfolioDomainContractTest test
```

3. 确认失败后实现最小代码。
4. 再运行相同命令，期望 `BUILD SUCCESS`。
5. 提交：`领域模型：增加作品集智能任务与无状态推荐上下文`

---

## Task 2：实现规则优先、模型受约束补充的任务解析

**依赖：** Task 1。

**文件：**

- 新建 `PortfolioTaskClassifierPort.java`、`PortfolioTaskResolver.java`。
- 修改 `ProviderOperation.java`、`ConversationalPromptFactory.java`、`OpenAiCompatibleConversationalModelAdapter.java`、`ConversationalAgentProperties.java`。
- 新建/修改对应 resolver、prompt、adapter 测试。

**行为：**

- 明确查询、比较、推荐、推荐调整措辞由规则直接命中，不调用模型。
- 仅在规则无法唯一判断时调用分类 seam。
- 模型只能返回任务枚举、受控条件、refinement 和 `0..1` 置信度。
- 低于 `0.80`、非法枚举、超时或解析失败均进入追问。
- `REFINE_RECOMMENDATION` 必须携带完整 `recommendationContext`；只有批次 ID 不足以恢复状态。
- 原始 `goal` 仅参与当前请求解析；可延续偏好必须转为受控能力代码。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioTaskResolverTest,ConversationalPromptFactoryTest,OpenAiCompatibleConversationalModelAdapterTest test
```

**提交：** `意图路由：增加规则优先与受约束分类`

---

## Task 3：建立统一检索 seam 与 Bundle Adapter

**依赖：** Task 1。

**文件：**

- 新建 `PortfolioRetriever.java`、`PortfolioRetrievalException.java`。
- 新建 `BundlePortfolioRetriever.java`。
- 修改现有 Bundle 检索所需的最小可见性，不复制索引算法。
- 新建 `BundlePortfolioRetrieverTest.java`。

**行为：**

- 事实、比较、推荐共享同一 `PortfolioRetriever.retrieve(request)`。
- Bundle 只返回当前公开 Release、已验证 Claim、已批准 Evidence。
- 固定排序和相同输入必须产生相同结果。
- 不得把内部检索器、RRF 分数或未公开正文暴露到 HTTP DTO。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=BundlePortfolioRetrieverTest,LocalKnowledgeRetrieverTest test
```

**提交：** `检索适配：统一作品集检索并接入本地降级`

---

## Task 4：实现 PostgreSQL/pgvector 主检索 Adapter

**依赖：** Task 1；可与 Task 2、Task 3、Task 6 在独立分支并行，集成时再接 seam。

**文件：**

- 新建 `PostgresKnowledgeQuery.java`、`JdbcPostgresKnowledgeQuery.java`、`PostgresPortfolioRetriever.java`。
- 复用现有 Selection 的参数化查询、候选召回和数据库属性。
- 新建 JDBC 查询与映射测试。

**行为：**

- SQL 固定在 Adapter 内，模型和请求参数不能选择 SQL 片段、表、排序或阈值。
- PostgreSQL FTS 与 pgvector 混合召回只读取当前 active public release。
- Evidence 必须为 APPROVED；未验证 Claim、私密字段和未公开 Subject 在 SQL 或映射门禁中排除。
- 返回统一检索领域对象，不返回 JDBC 类型。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=JdbcPostgresKnowledgeQueryTest,PostgresPortfolioRetrieverTest test
```

**提交：** `检索适配：接入PostgreSQL与pgvector主检索`

---

## Task 5：实现主从检索自动降级与 Spring 装配

**依赖：** Task 3、Task 4。

**文件：**

- 新建 `FailoverPortfolioRetriever.java`、`PortfolioIntelligenceConfiguration.java`。
- 修改或移除过时的 `PostgresSelectionConfiguration.java`。
- 新建 failover 与 Spring context 测试。

**行为：**

- 仅捕获表示主检索基础设施不可用的 `PortfolioRetrievalException`。
- PostgreSQL 失败后调用 Bundle，并返回固定 notice code `POSTGRES_RETRIEVAL_UNAVAILABLE` 与 `degraded=true`。
- 参数错误、非法状态和编程错误原样失败，不得静默降级。
- 数据库启用或禁用时，Spring 都只能注入一个外部 `PortfolioRetriever`。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=FailoverPortfolioRetrieverTest,PortfolioIntelligenceConfigurationTest test
```

**提交：** `检索降级：增加数据库主用与本地自动切换`

---

## Task 6：内化确定性推荐策略与无状态上下文指纹

**依赖：** Task 1。

**文件：**

- 新建 `PortfolioRecommendationPolicy.java`、`RecommendationBatchFingerprint.java`、`RecommendationContextValidator.java`。
- 复用现有 `TopKSelectionStrategy` 与 `ExhaustiveSelectionStrategy`。
- 新建 policy、fingerprint、validator 测试。

**行为：**

- 相同候选、条件和排除项产生相同有序推荐。
- 批次指纹使用 UTF-8、SHA-256、固定字段顺序、排序后的能力代码和保持顺序的作品 ID。
- 指纹构造输入不包含原始问题、自由文本 `goal`、时间戳或随机数。
- validator 重新计算指纹并验证当前 `contentVersion`、公开作品 ID、Evidence 门禁、数量和条件。
- 任何失配返回固定校验原因，不查询 Registry；项目中不得出现 `RecommendationContextRegistry`。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioRecommendationPolicyTest,RecommendationBatchFingerprintTest,RecommendationContextValidatorTest,TopKSelectionStrategyTest,ExhaustiveSelectionStrategyTest test
```

**提交：** `推荐策略：内化确定性选择与无状态上下文校验`

---

## Task 7：实现 PortfolioIntelligence 硬路由

**依赖：** Task 2、Task 5、Task 6。

**文件：**

- 新建 `PortfolioTaskValidator.java`、`PortfolioTaskValidation.java`、`PortfolioIntelligence.java`、`DefaultPortfolioIntelligence.java`。
- 新建 `DefaultPortfolioIntelligenceTest.java`、`PortfolioTaskValidatorTest.java`。

**行为：**

- 每种 `PortfolioTaskMode` 只有唯一处理路径。
- 事实查询和比较通过统一 retriever + evidence assembler 返回素材。
- 首次推荐调用检索与 policy，返回结构化 recommendation 和可回传 context。
- 推荐调整先验证回传 context，再根据 refinement 排除/变更条件并重新计算，不读取任何服务端会话。
- 缺少关键条件、上下文失配、版本变化或指代不清时只追问一个最关键问题。
- 模型不得增删、替换或重排 policy 的作品集合。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioTaskValidatorTest,DefaultPortfolioIntelligenceTest test
```

**提交：** `智能编排：增加作品集任务校验与确定性硬路由`

---

## Task 8：接入 Agent 回答契约和运行时

**依赖：** Task 7。

**文件：**

- 修改 `ConversationAnswerResult.java`、`ConversationAnswerResponse.java`、Mapper。
- 新建推荐 response DTO，包括 `context`。
- 修改 `ConversationalAgentRuntime.java` 与 Spring 配置。
- 新建/修改 result、mapper、runtime、MockMvc 测试。

**行为：**

- 非作品集问题保持现有链路。
- 作品集任务在模型生成前进入 `PortfolioTaskResolver` 和 `PortfolioIntelligence`。
- 确定性推荐项直接映射到响应；模型只能解释。
- 所有 `withGuidance()`、降级和重建结果的方法保留 recommendation。
- 非推荐响应省略字段；推荐与推荐调整即使空列表也返回字段。
- 日志只记录路由、数量、耗时、是否携带上下文及校验结果，不记录正文、批次 ID 或上下文内容。

**TDD 命令：**

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=ConversationAnswerResultTest,ConversationAnswerResponseMapperTest,ConversationalAgentRuntimeTest,ConversationAnswerControllerTest test
```

**提交：** `Agent集成：统一作品集问答与结构化推荐`

---

## Task 9：删除公开 Selection HTTP 表面并迁移文档

**依赖：** Task 8。

**文件：**

- 删除 Selection Controller、专用 HTTP DTO、Mapper 及其测试。
- 更新 `docs/08-当前实现状态.md`、`docs/11-运行维护手册.md` 和相关 API 文档。
- 新建架构约束测试，确保不存在公开 Selection mapping。
- 不修改 `frontend/src`。

**行为：**

- `/api/portfolio-selections` 返回 404。
- Selection 算法和 benchmark 仍可在内部运行。
- 文档明确：单一 Agent 入口、PostgreSQL 主检索、Bundle 降级、当前标签页内存上下文、刷新清空、后端无状态。
- 删除所有“服务端保存 30 分钟推荐上下文”的现行描述。

**验证：**

```powershell
rg -n "/api/portfolio-selections|RecommendationContextRegistry|保存 30 分钟|30分钟" backend/src docs
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml `
  -Dtest=PortfolioSelectionSurfaceRemovalTest,SelectionBenchmarkSmokeTest test
```

**提交：** `接口收口：移除公开Selection并更新运行文档`

---

## Task 10：全量回归、隐私审计与前端交接

**依赖：** Task 9。

1. 扫描禁止项：

```powershell
rg -n "\bvar\b|\brecord\b|lombok|RecommendationContextRegistry" backend/src/main backend/src/test
rg -n "localStorage|sessionStorage|indexedDB|recommendationBatchId" frontend/src
```

对现存非本次代码逐项判断；本次新增 Java 不得命中禁止项。本任务不修改前端。

2. 运行后端全量测试：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test
```

3. 在本地 PostgreSQL/pgvector 启用和禁用两种配置下运行应用上下文与集成测试。
4. 运行 Selection benchmark smoke，确认算法迁移未改变固定 fixture 结果。
5. 更新 `docs/handoffs/agent-portfolio-recommendation-frontend-ai-prompt.md` 的最终示例。
6. 生成后端到前端交接文档，明确字段、空值、省略、失配追问和刷新清空行为。
7. 由独立代码审查代理审查完整分支；修复后重新运行相关测试与全量测试。
8. 最终提交：`验证：补全作品集智能回归与前端交接`

## 完成定义

- 所有作品集查询、比较、推荐和调整只通过 `/api/v2/answers`。
- PostgreSQL/pgvector 是健康状态下的主检索，Bundle 是受控降级。
- 推荐集合由确定性策略产生，模型不能修改。
- 推荐上下文只在当前标签页内存；刷新即清空；后端无 Registry、无 Session、无推荐上下文持久化。
- 回传上下文每次根据当前公开数据和 Evidence 门禁重新验证。
- `/api/portfolio-selections` 不再公开，内部选择策略和 benchmark 保留。
- 没有修改前端源码。
- 新增 Java 无 `var`、无 `record`、无 Lombok。
- 聚焦测试、全量测试、数据库双配置测试和最终代码审查全部通过。
