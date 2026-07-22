# 实习作品集 Agent 最终审查后端整改设计

**日期：** 2026-07-15
**状态：** 部分实施；模块边界已整改，隐私顺序、扫描覆盖和访客问题脱敏尚未全部落实
**适用项目：** `D:\code\agent`

## 0. 2026-07-20 状态核对

已实施：`common / portfolio / answer` 模块边界、Answer-owned Gateway/投影、架构门禁、单快照查询边界和完整发布脚本基础。

仍未按本文完成：`AnswerRequest.toString()` 仍包含问题原文；隐私检查仍可能漏掉带引号 JSON credential 与转义 Windows 路径；`verify-release.ps1` 仍在 Maven 生成 JAR 后执行主要隐私扫描，且未扫描全部 `BOOT-INF/classes` 文本资源；最终验证证据与失败制品隔离也未形成本文要求的闭环。因此本文不能标记为全部完成。

## 1. 背景

现有重构已经完成 `common / portfolio / answer` 后端包拆分、16 个 `record` 的普通不可变类迁移、前端 feature-first 迁移、代码约束门禁、完整发布验证和本地 JAR 启动。

最终全仓审查没有发现 Critical 正确性或现行泄密故障，但确认了以下需要在交付前处理的问题：

1. `portfolio.application` 反向依赖 `portfolio.api.dto`，分层没有真正闭合；
2. `answer.application/domain` 引用 portfolio 的 application 异常和 domain 实体，模块内部类型发生泄漏；
3. 隐私扫描晚于 JAR 打包，失败时风险制品已经生成；
4. 隐私正则和 JAR 扫描范围存在 JSON 凭据与任意文本资源漏检面；
5. Lombok 检查只覆盖枚举出的全限定注解；
6. `AnswerRequest.toString()` 暴露完整访客问题；
7. 一次项目查询读取两次快照；
8. 最终发布原始日志和 JAR 断言规则没有形成持久、可哈希的审计证据。

用户选择严格分层方案，并明确本轮优先优化后端设计与代码，不调整前端。

## 2. 目标与非目标

### 2.1 目标

- 消除 `portfolio.application -> portfolio.api` 依赖；
- 让 answer 的 domain/application 只依赖 answer 自有模型和端口；
- 将 portfolio 领域类型的跨模块读取限制在一个明确的 answer infrastructure adapter 内；
- 保持三个公开 REST 端点的 URL、状态码和 JSON 字段不变；
- 保持确定性回答的匹配算法、section 顺序、Evidence 顺序和公开文案不变；
- 提前执行隐私门禁，并扩大凭据和制品资源扫描覆盖；
- 自动阻断新的分层反向依赖、任意 Lombok 引用和 Lombok Maven 依赖；
- 重新运行完整发布验证，保存原始日志、哈希和机器可读 JAR 断言结果；
- 用新构建的 JAR 替换当前已知的本地运行进程。

### 2.2 非目标

- 不修改 Vue 页面、DOM、文案、CSS、交互、路由或前端目录结构；
- 不接入 DeepSeek、Codex 私有处理、SSE、数据库或新的运行时依赖；
- 不修改公开作品集数据内容；
- 不改变现有 API 的 Evidence JSON 结构；
- 不创建 `AnswerEvidenceResponse`；
- 不修改 `D:\code\d11_server`；
- 不执行 Git stage、commit、push、reset、restore 或其他 Git 命令。

## 3. 最终架构

### 3.1 Portfolio 模块内部依赖

目标方向：

```text
portfolio.api -> portfolio.application -> portfolio.domain
                                      -> portfolio.domain.repository
portfolio.infrastructure -> portfolio.domain.repository/domain.model
```

`portfolio.application` 不得导入 `portfolio.api`。

#### Application-owned 输出模型

新增 `portfolio.application.model`：

- `PortfolioOverview`
  - `contentVersion`
  - `publishedAt`
  - `OwnerProfile owner`
  - `List<ProjectProfile> projects`
- `ProjectDetails`
  - `ProjectProfile project`
  - `List<EvidenceRecord> evidence`
  - `List<String> suggestedQuestions`

两者均为普通不可变 `public final class`：`private final` 字段、显式构造器/getter、集合 `List.copyOf`、完整 `equals/hashCode/toString`。

`PortfolioQueryService` 最终只负责用例逻辑：

```java
PortfolioOverview getPortfolio();
ProjectDetails getProject(String slug);
private ProjectProfile findProject(PortfolioSnapshot snapshot, String slug);
```

`getProject` 在一次调用内只读取一次 `PortfolioSnapshot`，并把同一个 snapshot 传给 `findProject`、Evidence 过滤和推荐问题筛选。

#### API 映射

- `PortfolioHomeResponse.from(PortfolioOverview overview)` 在 API 层完成 Owner 和 ProjectSummary 映射；
- `ProjectDetailResponse.from(ProjectDetails details)` 在 API 层完成 Project、Evidence 和 suggestedQuestions 映射；
- `PortfolioController` 继续调用 `PortfolioQueryService`，返回字段和端点不变；
- 所有 DTO 仍保持普通不可变类和现有 JSON getter。

## 4. Answer 模块边界

目标方向：

```text
answer.api -> answer.application -> answer.domain
answer.infrastructure.deterministic -> answer.domain
answer.infrastructure.portfolio -> answer.application.port
                                -> portfolio.domain.repository/model
```

只有 `answer.infrastructure.portfolio` 可以导入 portfolio domain/repository。`answer.api/application/domain` 不得导入 portfolio 的 application、domain 或 infrastructure。

### 4.1 Answer-owned 端口

新增：

```java
package com.portfolio.agent.answer.application.port;

public interface PortfolioKnowledgePort {
    Optional<AnswerProjectKnowledge> findBySlug(String projectSlug);
}
```

该端口由 answer 模块拥有，表达回答用例真正需要的知识，不暴露 portfolio 的完整快照。

### 4.2 Answer-owned 投影

新增 `answer.domain.model`：

- `AnswerProjectKnowledge`
  - `slug, title, background, responsibilities, solution, keyDecisions, verification, outcome, handoff, status`
  - `List<AnswerQuestion> questions`
  - `List<AnswerEvidence> evidence`
- `AnswerQuestion`
  - `canonicalQuestion, aliases, suggestion`
- `AnswerEvidence`
  - `id, title, String type, LocalDate periodStart, LocalDate periodEnd, sourceCount, summary, supportedClaims, String publicStatus, boolean rawContentPublic`

这些类均为 answer-owned 普通不可变类，不导入 portfolio 类型。集合字段全部防御性复制并实现完整值语义。

为了保持 API JSON 不变，枚举类字段在 adapter 中使用原枚举的 `name()` 映射为字符串；日期在投影中使用 `LocalDate`，由 Jackson 保持现有 ISO JSON；adapter 只允许通过 `Boolean.FALSE.equals(record.getRawContentPublic())` 的 Evidence，因此 answer 投影使用非空 `boolean rawContentPublic=false`。

### 4.3 Portfolio adapter

新增 `answer.infrastructure.portfolio.PortfolioKnowledgeAdapter`：

- 构造器注入 `PublicPortfolioRepository`；
- 一次读取 snapshot；
- 根据 slug 查找项目；
- 只映射项目引用且 `publicStatus == APPROVED`、`Boolean.FALSE.equals(rawContentPublic)` 的 Evidence；
- 只映射项目引用的 Question；
- 保持 snapshot 中原始顺序；
- 返回 `Optional.empty()` 表示项目不存在；
- 不返回任何 portfolio 实体。

该 adapter 是全项目唯一允许 answer 导入 portfolio domain/repository 的位置。

### 4.4 Answer 用例与引擎

`AnswerService`：

1. 通过 `PortfolioKnowledgePort.findBySlug` 一次获取 `AnswerProjectKnowledge`；
2. 不存在时抛出 answer-owned `AnswerProjectNotFoundException`；
3. 调用 `AnswerEngine.answer(AnswerProjectKnowledge project, String question)`。

新增：

- `answer.application.exception.AnswerErrorCode`：只包含与现有公开契约一致的 `PROJECT_NOT_FOUND`；
- `answer.application.exception.AnswerProjectNotFoundException`：公开 code、message、HTTP 404 与现有 API 保持一致。

`AnswerEngine` 签名改为：

```java
AnswerResult answer(AnswerProjectKnowledge project, String question);
```

`DeterministicAnswerEngine` 不再注入 `PublicPortfolioRepository`，只使用传入的 answer-owned projection 和 `QuestionNormalizer`。

`AnswerResult.evidence` 改为 `List<AnswerEvidence>`，不再导入 `EvidenceRecord`。

`EvidenceResponse` 的 API 字段类型调整为 `String type`、`String publicStatus`、`LocalDate` 日期和 `boolean rawContentPublic`；`EvidenceResponse.from(EvidenceRecord)` 在 portfolio API 内部使用枚举 `.name()` 映射。该变化不改变任何 JSON 值，但去除响应 DTO 构造器对 portfolio 枚举的暴露。

`AnswerResponse.from` 继续复用 `portfolio.api.dto.EvidenceResponse`，由 answer API 根据 `AnswerEvidence` 的 API 原生字段直接调用其构造器；不要求 `EvidenceResponse` 反向导入 answer 类型，answer API 也不导入 portfolio domain，并且不创建新的 Evidence 响应 DTO。

## 5. 自动架构门禁

新增 `scripts/architecture-check.ps1` 与 `scripts/architecture-check.test.ps1`，并在发布脚本 Java 代码检查后执行。

检查器至少阻断：

- `common` 导入 `portfolio` 或 `answer`；
- `portfolio` 导入 `answer`；
- `portfolio.application` 导入 `portfolio.api`；
- `answer.api/application/domain` 导入 `portfolio.application/domain/infrastructure`；
- `answer.infrastructure` 中除 `answer.infrastructure.portfolio` 外的包导入 portfolio；
- Controller 直接导入 infrastructure 或 repository；
- frontend `features/portfolio` 导入 `features/agent`；
- frontend `features/shared` 反向导入 pages。

测试使用安全与不安全 fixture 验证每条依赖方向，输出规则、文件和行号，命中返回非零。

## 6. Lombok 门禁

保留现有 `var-local`、`record-type`、`lombok-import` 输出契约，同时把全限定注解规则扩展为任意：

```regex
@\s*lombok\.[A-Za-z_$][A-Za-z0-9_$.]*
```

新增 fixture 覆盖 `@lombok.EqualsAndHashCode`、`@lombok.With`。

发布脚本额外扫描 `backend/pom.xml`，阻断 Lombok group/artifact 依赖。该检查不通过字符串拆分或注释绕过。

## 7. 隐私门禁

### 7.1 凭据模式

`credential-assignment` 必须覆盖：

- `password=secret`
- `token: secret`
- `"apiKey": "secret"`
- `{"token":"secret"}`
- YAML、properties、JSON、JavaScript 配置中的引号和空白变体

安全 fixture 继续证明公开文案不会误报。

### 7.2 执行顺序

发布脚本顺序调整为：

1. Java/架构/Lombok 门禁；
2. 前端测试和 build；
3. privacy checker tests；
4. 扫描公开 snapshot、frontend dist、其他待打包资源；
5. Maven clean package；
6. JAR 条目断言；
7. 解包后扫描整个 `BOOT-INF/classes` 中允许扩展名的文本资源；
8. Playwright；
9. Docker check（如未跳过）。

隐私失败必须发生在标准 JAR 生成前。打包后扫描作为第二道防线保留。

### 7.3 JAR 扫描范围

解包后直接把 `BOOT-INF/classes` 交给 privacy checker。checker 只读取其 allowlist 扩展名，不读取 `.class` 二进制文件；这样 `application.yml`、任意 JSON/JS/HTML/CSS/TXT/MD 等资源都会被覆盖。

JAR 条目负向断言继续检查私有知识库、候选快照、原始 Evidence、未审核内容，并保存机器可读断言结果。

## 8. 其他安全与一致性修复

- `AnswerRequest.toString()` 不输出 question 原文，只输出固定 `<redacted>`；`equals/hashCode` 仍保留完整值语义；
- `PortfolioQueryService#getProject` 使用同一个 snapshot 完成整次查询；
- 当前前端不修改，现有 21 个 Vitest 和 4 个 Playwright 测试必须继续通过；
- REST 错误码、公开 message、requestId 和 timestamp 字段不变。

## 9. 测试设计

所有行为变化遵循 RED → GREEN：

1. Application 分层测试：先证明 `PortfolioQueryService` 不再返回 API DTO，并验证 API 映射结果不变；
2. Answer 端口/投影测试：验证 adapter 的项目查找、顺序、Evidence 审核过滤、Question 过滤和不可变性；
3. Deterministic engine 测试：传入 answer-owned knowledge，原有匹配/边界/section/Evidence 结果保持；
4. Controller 契约测试：三个端点原 JSONPath 与状态码不变；
5. 架构检查器 fixture：每条允许/禁止依赖至少一个测试；
6. Lombok fixture：未列举的全限定注解和 Maven 依赖均失败；
7. Privacy fixture：典型与紧凑 JSON 凭据均失败；完整 classes 文本资源扫描失败用例；
8. `AnswerRequest.toString()` 明确不含访客问题；
9. 后端全部测试、前端全部测试/build、完整 release verification；
10. JAR 正负断言、HTTP 四端点冒烟和本地启动。

## 10. 发布证据

最终验证必须保存到 `.codex-agent-sdd/final-verification/`：

- 完整 `verify-release` 原始日志；
- 日志 SHA-256；
- 机器可读 JAR assertion JSON；
- JAR assertion 文件 SHA-256；
- 最终 JAR SHA-256；
- HTTP 冒烟摘要；
- 新运行 PID 和端口。

这些证据目录不进入 JAR，不修改公开数据，也不要求 Git 操作。

## 11. 运行切换

- 仅允许停止已由本任务链路记录并验证命令行指向 `D:\code\agent\backend\target\portfolio-agent.jar` 的旧 PID 36704；
- 若 PID 已不存在，不视为失败；
- 若 8080 被其他未知进程占用，停止并报告，不终止未知进程；
- 新 JAR 使用指定 JDK 21、隐藏窗口启动；
- 条件轮询 readiness，成功后执行四端点硬断言并保留进程运行。

## 12. 完成定义

- `portfolio.application` 对 `portfolio.api` 导入为 0；
- `answer.api/application/domain` 对 portfolio 内部层导入为 0；
- 唯一跨模块 adapter 为 `answer.infrastructure.portfolio`；
- API/HTTP/JSON 和确定性回答行为不变；
- 隐私扫描先于打包，完整 `BOOT-INF/classes` 文本资源被扫描；
- JSON 凭据、任意 Lombok 注解和 Lombok Maven 依赖均会被自动阻断；
- 后端、前端、Playwright、隐私、打包、JAR 和 HTTP 验证全部通过；
- 原始验证日志与机器可读断言持久化并带 SHA-256；
- 新 JAR 在本地 8080 运行；
- 未修改前端源码、`D:\code\d11_server` 或 Git 状态。
