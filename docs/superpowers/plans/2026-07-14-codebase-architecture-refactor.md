# 实习作品集 Agent 代码架构重构实施计划

> **执行状态（2026-07-20）：** 已被 `2026-07-16-modular-monolith-package-refactor.md` 取代，不再作为可执行计划。下列未勾选项保留原始计划历史，不属于当前待办。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 API、UI、公开数据和运行行为的前提下，把后端重构为业务模块优先的分层结构，把 16 个 `record` 转为普通不可变类，清除全部 Java `var` 和 Lombok，并把前端迁移到轻量 feature-first 目录。

**Architecture:** 后端保持一个 Maven 模块，按 `common`、`portfolio`、`answer` 三个顶级包组织；每个业务模块内部使用 `api/application/domain/infrastructure` 分层。前端按 `app/pages/features/shared` 组织，允许 `agent` 功能只读依赖 `portfolio` 的公开类型和 Evidence 组件，禁止反向依赖。

**Tech Stack:** Java 21、Spring Boot 3.5.3、Maven、Jackson、JUnit 5、MockMvc、Vue 3、TypeScript 5.8、Vite 7、Vitest 3、Vue Test Utils、Playwright、PowerShell。

## Global Constraints

- 实际修改目录只能是 `D:\code\agent`；`D:\code\d11_server` 只读。
- 使用 `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` 作为 `JAVA_HOME`。
- 生产和测试 Java 代码都禁止 `var`、`record` 和 Lombok。
- 普通值对象使用 `final class`、`private final` 字段、显式构造器、显式 getter、防御性集合复制以及显式 `equals/hashCode/toString`。
- 保持 `GET /api/v1/portfolio`、`GET /api/v1/projects/{slug}`、`POST /api/v1/answers` 的 URL、HTTP 状态和 JSON 字段不变。
- 保持 Vue 页面 DOM、文案、CSS、交互、路由和响应式效果不变；本次只移动文件并修正 import。
- 公开运行时仍只能读取 `backend/src/main/resources/public-data/public-portfolio.v1.json`。
- 不引入 DeepSeek、Spring AI、SSE、数据库、Lombok、MapStruct、ArchUnit、Pinia、Axios 或新的运行时依赖。
- 保留用户已有 Git 状态；未经明确授权不 stage、commit、push、reset 或 restore。本文计划因此不包含提交步骤。
- `rtk` 已确认在当前环境不可用，执行时使用项目 `AGENTS.md` 允许的原始命令调试例外。
- 设计依据：`docs/superpowers/specs/2026-07-14-codebase-architecture-refactor-design.md`。
- 代码规范依据：`docs/04-项目代码约束.md`。

---

## File Map

### 后端最终文件归属

```text
backend/src/main/java/com/portfolio/agent/
├─ PortfolioAgentApplication.java
├─ common/
│  ├─ exception/{ErrorCode,CommonErrorCode,ApplicationException}.java
│  └─ web/{ApiErrorResponse,GlobalExceptionHandler,SpaForwardController}.java
├─ portfolio/
│  ├─ api/PortfolioController.java
│  ├─ api/dto/{EvidenceResponse,OwnerResponse,PortfolioHomeResponse,ProjectDetailResponse,ProjectSummaryResponse}.java
│  ├─ application/PortfolioQueryService.java
│  ├─ application/exception/{PortfolioErrorCode,ProjectNotFoundException}.java
│  ├─ domain/model/{ContributionType,EvidenceRecord,EvidenceStatus,EvidenceType,OwnerProfile,PortfolioSnapshot,ProjectProfile,ProjectStatus,QuestionDefinition}.java
│  ├─ domain/repository/PublicPortfolioRepository.java
│  └─ infrastructure/json/{InvalidPortfolioSnapshotException,JsonPublicPortfolioRepository,PortfolioSnapshotValidator}.java
└─ answer/
   ├─ api/AnswerController.java
   ├─ api/dto/{AnswerPayload,AnswerRequest,AnswerResponse,AnswerSectionResponse}.java
   ├─ application/{AnswerEngine,AnswerService,QuestionNormalizer}.java
   ├─ domain/model/{AnswerMode,AnswerResult,AnswerSection,AnswerSectionType}.java
   └─ infrastructure/deterministic/DeterministicAnswerEngine.java
```

### 前端最终文件归属

```text
frontend/src/
├─ app/{App.vue,router.ts,router.test.ts}
├─ app/styles/main.css
├─ pages/{HomePage,HomePage.test,ProjectPage,ProjectPage.test,NotFoundPage}.vue/.ts
├─ features/portfolio/api/{portfolioApi,portfolioApi.test}.ts
├─ features/portfolio/model/{portfolioTypes,projectLabels}.ts
├─ features/portfolio/components/{ProjectCard,EvidenceCard}.vue
├─ features/agent/api/{answerApi,answerApi.test}.ts
├─ features/agent/model/answerTypes.ts
├─ features/agent/components/{AgentPanel,AgentPanel.test,AnswerSections}.vue/.ts
├─ shared/components/LoadingBlock.vue
├─ test/setup.ts
└─ main.ts
```

---

### Task 1: 建立基线与自动代码约束检查器

**Files:**
- Create: `scripts/code-quality-check.ps1`
- Create: `scripts/code-quality-check.test.ps1`
- Read: `scripts/verify-release.ps1`

**Interfaces:**
- Consumes: 一个包含 Java 文件的 `-Path` 参数。
- Produces: 通过时退出码 0；命中 `var`、`record` 或 Lombok 时打印规则、文件和行号并返回非零退出码。

- [ ] **Step 1: 使用指定 JDK 记录当前完整基线**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1 -SkipInstall -SkipDockerCheck
```

Expected: 输出 `Release verification passed.`；若失败，停止重构并使用 `systematic-debugging` 查明基线失败原因。

- [ ] **Step 2: 先创建检查器测试并验证 RED**

`scripts/code-quality-check.test.ps1` 使用临时目录创建五个独立 fixture：

```powershell
$unsafeCases = [ordered]@{
    'var-local' = 'class Sample { void run() { var value = "x"; } }'
    'record-type' = 'record Sample(String value) {}'
    'lombok-import' = "import lombok.Data;`nclass Sample {}"
    'lombok-data' = '@lombok.Data class Sample {}'
}
$safeSource = @'
import org.springframework.beans.factory.annotation.Value;

final class Sample {
    private final String value;
    @Value("classpath:data.json")
    private String configuredValue;
    Sample(String value) { this.value = value; }
    String getValue() { return value; }
}
'@
```

测试逐个运行检查器，断言每个不安全 fixture 返回非零，安全 fixture 返回 0，并在 `finally` 删除已验证位于系统临时目录下的 fixture。

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\code-quality-check.test.ps1
```

Expected: FAIL，因为 `scripts/code-quality-check.ps1` 尚不存在。

- [ ] **Step 3: 实现最小检查器**

`scripts/code-quality-check.ps1` 使用以下精确规则：

```powershell
param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$rules = @(
    @{ Name = 'var-local'; Pattern = '\bvar\s+[A-Za-z_$][A-Za-z0-9_$]*' },
    @{ Name = 'record-type'; Pattern = '\brecord\s+[A-Za-z_$][A-Za-z0-9_$]*' },
    @{ Name = 'lombok-import'; Pattern = '^\s*import\s+lombok\.' },
    @{ Name = 'lombok-qualified-annotation'; Pattern = '@\s*lombok\.(Data|Getter|Setter|Value|Builder|RequiredArgsConstructor|AllArgsConstructor|NoArgsConstructor|Slf4j)\b' }
)

$violations = New-Object System.Collections.Generic.List[string]
$javaFiles = Get-ChildItem -LiteralPath $resolvedPath -Recurse -File -Filter '*.java'
foreach ($file in $javaFiles) {
    foreach ($rule in $rules) {
        $matches = Select-String -LiteralPath $file.FullName -Pattern $rule.Pattern
        foreach ($match in $matches) {
            $violations.Add("$($rule.Name):$($file.FullName):$($match.LineNumber):$($match.Line.Trim())")
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Code quality check passed for $resolvedPath."
```

- [ ] **Step 4: 验证检查器测试 GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\code-quality-check.test.ps1
```

Expected: 输出 `code-quality-check tests passed`。

- [ ] **Step 5: 对当前代码运行检查器并确认它能阻断现状**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\code-quality-check.ps1 -Path backend\src
```

Expected: 非零退出；至少报告 16 个 `record-type` 命中和生产、测试代码中的 `var-local` 命中。此时不接入发布脚本，避免后续每个中间步骤都被预期失败阻断。

---

### Task 2: 迁移作品集领域模型和 JSON 基础设施

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/domain/model/PortfolioModelContractTest.java`
- Move/Modify: `backend/src/main/java/com/portfolio/agent/domain/*.java` → `backend/src/main/java/com/portfolio/agent/portfolio/domain/model/*.java`
- Move/Modify: `backend/src/main/java/com/portfolio/agent/content/PublicPortfolioRepository.java` → `backend/src/main/java/com/portfolio/agent/portfolio/domain/repository/PublicPortfolioRepository.java`
- Move/Modify: `backend/src/main/java/com/portfolio/agent/content/{JsonPublicPortfolioRepository,PortfolioSnapshotValidator,InvalidPortfolioSnapshotException}.java` → `backend/src/main/java/com/portfolio/agent/portfolio/infrastructure/json/`
- Move/Modify tests from `backend/src/test/java/com/portfolio/agent/content/` to `backend/src/test/java/com/portfolio/agent/portfolio/infrastructure/json/`
- Modify temporarily: current service, answer engine and API imports so the project compiles after the move.

**Interfaces:**
- Consumes: `public-portfolio.v1.json` and Jackson `ObjectMapper`。
- Produces: `PublicPortfolioRepository#getSnapshot(): PortfolioSnapshot` and immutable portfolio model getters.

- [ ] **Step 1: 写不可变和值语义 RED 测试**

测试必须使用新包和 getter，至少包含：

```java
@Test
void questionDefinitionDefensivelyCopiesAliasesAndKeepsValueSemantics() {
    List<String> aliases = new ArrayList<>(List.of("介绍项目"));
    QuestionDefinition first = new QuestionDefinition(
            "question-1", "project-1", "完整介绍", aliases, "介绍项目"
    );
    QuestionDefinition second = new QuestionDefinition(
            "question-1", "project-1", "完整介绍", List.of("介绍项目"), "介绍项目"
    );

    aliases.add("后来加入的别名");

    assertThat(first.getAliases()).containsExactly("介绍项目");
    assertThatThrownBy(() -> first.getAliases().add("禁止修改"))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first.toString()).contains("question-1", "完整介绍");
}
```

另一个测试对 `ProjectProfile` 的 `responsibilities`、`keyDecisions`、`technologies`、`verification`、`questionIds`、`evidenceIds` 逐个断言外部修改不生效且 getter 返回集合不可修改。

Run:

```powershell
mvn.cmd -f backend\pom.xml -Dtest=PortfolioModelContractTest test
```

Expected: 编译失败，因为新包和普通类 getter 尚不存在。

- [ ] **Step 2: 移动枚举和 Repository 端口**

枚举只改 package，不改常量：

- `ContributionType`
- `EvidenceStatus`
- `EvidenceType`
- `ProjectStatus`

Repository 最终接口：

```java
package com.portfolio.agent.portfolio.domain.repository;

import com.portfolio.agent.portfolio.domain.model.PortfolioSnapshot;

public interface PublicPortfolioRepository {
    PortfolioSnapshot getSnapshot();
}
```

- [ ] **Step 3: 将五个 portfolio record 改为普通不可变类**

构造器参数顺序和 JSON 字段保持如下：

| Class | Constructor fields in order | Collection fields copied with `List.copyOf` |
|---|---|---|
| `OwnerProfile` | `name, role, summary, githubUrl, email, resumeUrl` | none |
| `QuestionDefinition` | `id, projectId, canonicalQuestion, aliases, suggestion` | `aliases` |
| `EvidenceRecord` | `id, title, type, periodStart, periodEnd, sourceCount, summary, supportedClaims, publicStatus, rawContentPublic` | `supportedClaims` |
| `ProjectProfile` | `id, slug, title, summary, background, responsibilities, solution, keyDecisions, technologies, verification, outcome, handoff, status, contributionType, questionIds, evidenceIds` | all six list fields |
| `PortfolioSnapshot` | `schemaVersion, contentVersion, publishedAt, owner, projects, questions, evidence` | all three list fields |

每个类必须是 `public final class`，字段全部 `private final`，getter 使用 `getXxx()`；`Boolean rawContentPublic` 使用 `getRawContentPublic()`，不在本次改变其可空语义。每个类使用 `Objects.equals`、`Objects.hash` 和所有字段实现值语义。

Jackson 构造器以 `@JsonCreator` 标注，每个参数使用与公开 JSON 完全一致的 `@JsonProperty`。`QuestionDefinition` 的完整结构作为其余类实现标准：

```java
public final class QuestionDefinition {
    private final String id;
    private final String projectId;
    private final String canonicalQuestion;
    private final List<String> aliases;
    private final String suggestion;

    @JsonCreator
    public QuestionDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("projectId") String projectId,
            @JsonProperty("canonicalQuestion") String canonicalQuestion,
            @JsonProperty("aliases") List<String> aliases,
            @JsonProperty("suggestion") String suggestion
    ) {
        this.id = id;
        this.projectId = projectId;
        this.canonicalQuestion = canonicalQuestion;
        this.aliases = List.copyOf(aliases);
        this.suggestion = suggestion;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getCanonicalQuestion() { return canonicalQuestion; }
    public List<String> getAliases() { return aliases; }
    public String getSuggestion() { return suggestion; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof QuestionDefinition that)) { return false; }
        return Objects.equals(id, that.id)
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(canonicalQuestion, that.canonicalQuestion)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(suggestion, that.suggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, projectId, canonicalQuestion, aliases, suggestion);
    }

    @Override
    public String toString() {
        return "QuestionDefinition{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", canonicalQuestion='" + canonicalQuestion + '\'' +
                ", aliases=" + aliases +
                ", suggestion='" + suggestion + '\'' +
                '}';
    }
}
```

- [ ] **Step 4: 迁移 JSON Repository 和校验器**

`JsonPublicPortfolioRepository` 使用显式类型：

```java
try (InputStream inputStream = resource.getInputStream()) {
    PortfolioSnapshot loaded = objectMapper.readValue(inputStream, PortfolioSnapshot.class);
    validator.validate(loaded);
    this.snapshot = loaded;
}

@Override
public PortfolioSnapshot getSnapshot() {
    return snapshot;
}
```

`PortfolioSnapshotValidator` 将所有 record accessor 改为 getter，并把每个 `var` 改为精确类型：`List<ProjectProfile>`、`List<QuestionDefinition>`、`List<EvidenceRecord>`、`Map<String, ProjectProfile>`、`Map<String, QuestionDefinition>`、`Map<String, EvidenceRecord>`、`QuestionDefinition`、`String`。

- [ ] **Step 5: 更新临时调用点并迁移测试包**

所有当前调用 `repository.snapshot()` 的位置改为 `repository.getSnapshot()`；所有 portfolio model accessor 改为 getter。迁移后的测试类：

- `portfolio/infrastructure/json/JsonPublicPortfolioRepositoryTest`
- `portfolio/infrastructure/json/PortfolioSnapshotValidatorTest`
- `portfolio/domain/model/PortfolioModelContractTest`

测试中的局部变量明确写为 `JsonPublicPortfolioRepository`、`PortfolioSnapshot`、`ProjectProfile` 和 `String`。

- [ ] **Step 6: 验证 portfolio 切片 GREEN**

Run:

```powershell
mvn.cmd -f backend\pom.xml -Dtest=PortfolioModelContractTest,JsonPublicPortfolioRepositoryTest,PortfolioSnapshotValidatorTest test
mvn.cmd -f backend\pom.xml -DskipTests compile
```

Expected: 全部退出码 0；旧的 `com.portfolio.agent.domain` 和 `com.portfolio.agent.content` Java 文件不存在。

---

### Task 3: 迁移问答领域、应用端口和确定性实现

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/domain/model/AnswerModelContractTest.java`
- Move/Modify: answer enums and models to `answer/domain/model/`
- Move/Modify: `AnswerEngine` and `QuestionNormalizer` to `answer/application/`
- Move/Modify: `DeterministicAnswerEngine` to `answer/infrastructure/deterministic/`
- Move/Modify tests to mirror the new packages.

**Interfaces:**
- Consumes: `PublicPortfolioRepository` and normalized question text.
- Produces: `AnswerEngine#answer(String projectSlug, String question): AnswerResult`.

- [ ] **Step 1: 写 Answer model RED 测试**

```java
@Test
void answerResultDefensivelyCopiesCollectionsAndKeepsValueSemantics() {
    List<AnswerSection> sections = new ArrayList<>(List.of(
            new AnswerSection(AnswerSectionType.BACKGROUND, "背景")
    ));
    AnswerResult first = new AnswerResult(
            AnswerMode.DETERMINISTIC, true, false, "标题",
            sections, List.of(), List.of("推荐问题")
    );
    AnswerResult second = new AnswerResult(
            AnswerMode.DETERMINISTIC, true, false, "标题",
            List.of(new AnswerSection(AnswerSectionType.BACKGROUND, "背景")),
            List.of(), List.of("推荐问题")
    );

    sections.clear();

    assertThat(first.getSections()).hasSize(1);
    assertThatThrownBy(() -> first.getSections().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
}
```

Run:

```powershell
mvn.cmd -f backend\pom.xml -Dtest=AnswerModelContractTest test
```

Expected: 编译失败，因为新包和 getter 尚不存在。

- [ ] **Step 2: 移动枚举并转换两个 answer record**

- `AnswerMode`、`AnswerSectionType` 只改 package。
- `AnswerSection`：字段 `type, content`，普通不可变类、显式 getter、完整值语义。
- `AnswerResult`：字段 `answerMode, matched, fallback, title, sections, evidence, suggestedQuestions`；三个集合全部 `List.copyOf`；布尔 getter 使用 `isMatched()` 和 `isFallback()`；完整值语义。

构造器保持原字段顺序，保证调用点只需改变 accessor。

- [ ] **Step 3: 移动应用端口和规范化器**

`AnswerEngine` 最终签名：

```java
public interface AnswerEngine {
    AnswerResult answer(String projectSlug, String question);
}
```

`QuestionNormalizer` 的 `var normalized` 改为 `String normalized`，其他算法和标点集合保持不变。

- [ ] **Step 4: 迁移确定性引擎并显式化全部类型**

在 `DeterministicAnswerEngine` 中使用以下明确类型：

- `PortfolioSnapshot snapshot`
- `ProjectProfile project`
- `List<QuestionDefinition> projectQuestions`
- `String normalizedQuestion`
- `boolean matched`
- `List<String> suggestions`
- `Set<String> evidenceIds`
- `List<EvidenceRecord> publicEvidence`

所有访问改用 getter；方法引用改为 `QuestionDefinition::getSuggestion`、`AnswerSection::getType` 等。输出顺序和边界文案不变。

- [ ] **Step 5: 迁移并更新问答测试**

最终测试路径：

- `answer/application/QuestionNormalizerTest.java`
- `answer/infrastructure/deterministic/DeterministicAnswerEngineTest.java`
- `answer/domain/model/AnswerModelContractTest.java`

测试变量使用 `JsonPublicPortfolioRepository` 和 `AnswerResult` 明确类型，断言改用 getter。

- [ ] **Step 6: 验证 answer 切片 GREEN**

Run:

```powershell
mvn.cmd -f backend\pom.xml -Dtest=AnswerModelContractTest,QuestionNormalizerTest,DeterministicAnswerEngineTest test
mvn.cmd -f backend\pom.xml -DskipTests compile
```

Expected: 全部退出码 0；旧的 `com.portfolio.agent.answer` 平铺类只剩待迁移的 API 调用 import，不存在重复实现。

---

### Task 4: 建立公共异常机制、拆分 Controller 并转换 API DTO

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/exception/{ErrorCode,CommonErrorCode,ApplicationException}.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/{ApiErrorResponse,GlobalExceptionHandler,SpaForwardController}.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/application/exception/{PortfolioErrorCode,ProjectNotFoundException}.java`
- Move/Modify: portfolio application、API、DTO 到目标包。
- Create: answer application service、API、DTO 到目标包。
- Split tests into portfolio API、answer API and common web packages.
- Delete old `api/`、`service/`、`web/` files after replacement.

**Interfaces:**
- Produces: unchanged REST contract and two controllers with separate responsibilities.
- Consumes: portfolio repository, portfolio application service, answer engine.

- [ ] **Step 1: 先拆分 API 契约测试并验证 RED**

把原 `PortfolioControllerTest` 拆成：

- `portfolio/api/PortfolioControllerTest`：首页、项目详情、未知项目三个测试；
- `answer/api/AnswerControllerTest`：规范问题、边界问题、空问题、非法 slug、未知项目、超长问题、405、415；
- `common/web/GlobalExceptionHandlerTest`：未知 `/api/v1/does-not-exist` 返回 404 和统一错误体。

测试 JSONPath 断言原样保留，包名改为目标包，所有 `var` 改为明确类型。

Run:

```powershell
mvn.cmd -f backend\pom.xml -Dtest=PortfolioControllerTest,AnswerControllerTest,GlobalExceptionHandlerTest test
```

Expected: 编译失败，因为目标 Controller 和异常类尚不存在。

- [ ] **Step 2: 实现公共错误契约**

`ErrorCode` 不依赖 Spring：

```java
public interface ErrorCode {
    String getCode();
    String getDefaultMessage();
    int getHttpStatus();
}
```

`CommonErrorCode` 精确包含：

```java
VALIDATION_ERROR("VALIDATION_ERROR", "请求参数不符合要求", 400),
NOT_FOUND("NOT_FOUND", "请求的资源不存在", 404),
METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不受支持", 405),
UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持", 415),
INTERNAL_ERROR("INTERNAL_ERROR", "服务暂时不可用，请稍后重试", 500)
```

`ApplicationException` 继承 `RuntimeException`，保存非空 `ErrorCode`，构造器接收 `ErrorCode` 和公开 message，提供 `getErrorCode()`。

`PortfolioErrorCode` 只包含：

```java
PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "公开项目不存在", 404)
```

`ProjectNotFoundException` 构造器调用：

```java
super(PortfolioErrorCode.PROJECT_NOT_FOUND, "公开项目不存在: " + slug);
```

- [ ] **Step 3: 实现统一错误响应和全局处理器**

`ApiErrorResponse` 是普通不可变类，字段顺序为 `requestId, code, message, timestamp`，带 getter、完整值语义。

`GlobalExceptionHandlerTest` 还必须直接调用未知异常处理方法，传入消息包含 `secret-local-path` 的 `RuntimeException`，断言响应 message 精确等于 `服务暂时不可用，请稍后重试`，且响应序列化结果不包含原异常消息。

`GlobalExceptionHandler` 使用显式 `Logger`，不使用 Lombok。公共响应方法接收 `HttpStatus`、错误码字符串和公开消息。`ApplicationException` 的 HTTP 状态通过 `HttpStatus.valueOf(exception.getErrorCode().getHttpStatus())` 获得，因此 `common` 不导入任何业务模块。

未知异常记录服务端错误和 requestId，但响应只使用 `CommonErrorCode.INTERNAL_ERROR` 的固定文案。不得记录请求体或访客问题。

- [ ] **Step 4: 迁移 portfolio 应用、DTO 和 Controller**

`PortfolioQueryService` 只保留：

```java
PortfolioHomeResponse getPortfolio();
ProjectDetailResponse getProject(String slug);
private ProjectProfile findProject(String slug);
```

其局部变量使用 `PortfolioSnapshot`、`ProjectProfile`、`Set<String>`、`List<EvidenceResponse>`、`List<String>` 明确类型。

portfolio DTO 的精确字段：

| DTO | Fields in constructor order |
|---|---|
| `OwnerResponse` | `name, role, summary, githubUrl, email, resumeUrl` |
| `ProjectSummaryResponse` | `slug, title, summary, status, contributionType` |
| `PortfolioHomeResponse` | `contentVersion, publishedAt, owner, projects` |
| `EvidenceResponse` | `id, title, type, periodStart, periodEnd, sourceCount, summary, supportedClaims, publicStatus, rawContentPublic` |
| `ProjectDetailResponse` | `slug, title, summary, background, responsibilities, solution, keyDecisions, technologies, verification, outcome, handoff, status, contributionType, evidence, suggestedQuestions` |

全部 DTO 为普通不可变类，集合防御性复制，getter、`equals/hashCode/toString` 完整。`from` 方法保留并改用领域 getter。响应 DTO 通过 getter 序列化，不增加无参构造器或 setter。

`PortfolioController` 只保留两个 GET 端点，构造器注入 `PortfolioQueryService`。

- [ ] **Step 5: 实现 answer 应用服务、DTO 和 Controller**

`AnswerService`：

```java
@Service
public class AnswerService {
    private final PublicPortfolioRepository repository;
    private final AnswerEngine answerEngine;

    public AnswerService(PublicPortfolioRepository repository, AnswerEngine answerEngine) {
        this.repository = repository;
        this.answerEngine = answerEngine;
    }

    public AnswerResult answer(String projectSlug, String question) {
        boolean projectExists = repository.getSnapshot().getProjects().stream()
                .anyMatch(project -> project.getSlug().equals(projectSlug));
        if (!projectExists) {
            throw new ProjectNotFoundException(projectSlug);
        }
        return answerEngine.answer(projectSlug, question);
    }
}
```

answer DTO 的精确字段：

| DTO | Fields in constructor order |
|---|---|
| `AnswerRequest` | `projectSlug, question`，校验注解保持原值 |
| `AnswerSectionResponse` | `type, content` |
| `AnswerPayload` | `title, sections` |
| `AnswerResponse` | `requestId, answerMode, matched, fallback, answer, evidence, suggestedQuestions` |

`AnswerResponse` 的 `evidence` 字段使用 `List<EvidenceResponse>`，复用 `portfolio.api.dto.EvidenceResponse`，形成明确的单向依赖 `answer.api → portfolio.api.dto`。`AnswerResponse.from(String requestId, AnswerResult result)` 显式把 `AnswerSection` 映射为 `AnswerSectionResponse`，把 `EvidenceRecord` 映射为 `EvidenceResponse`；不得直接把领域对象放进 API DTO，也不得创建字段和转换逻辑重复的 `AnswerEvidenceResponse`。

`AnswerRequest` 的两个字段保留原 `@NotBlank`、`@Pattern`、`@Size` 规则；构造器使用 `@JsonCreator`，参数使用 `@JsonProperty("projectSlug")` 和 `@JsonProperty("question")`，不提供无参构造器或 setter。

`AnswerController`：

```java
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {
    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        String requestId = UUID.randomUUID().toString();
        AnswerResult result = answerService.answer(request.getProjectSlug(), request.getQuestion());
        return AnswerResponse.from(requestId, result);
    }
}
```

- [ ] **Step 6: 迁移 SPA Controller 并删除旧实现**

`SpaForwardController` 只改 package 为 `common.web`，路由保持 `"/projects/{slug}"` 和 `"/projects/{slug}/"`。删除已被替代的旧 Controller、Service、异常、DTO 和平铺 API 类，禁止保留兼容壳或重复 Bean。

- [ ] **Step 7: 验证 API 和完整后端 GREEN**

Run:

```powershell
mvn.cmd -f backend\pom.xml -Dtest=PortfolioControllerTest,AnswerControllerTest,GlobalExceptionHandlerTest,SpaForwardControllerTest test
mvn.cmd -f backend\pom.xml test
```

Expected: 所有后端测试通过；三个公开端点的 JSON 契约与重构前一致；Spring 启动时不存在重复映射或重复 Bean。

---

### Task 5: 清除剩余隐式语法并接入发布门禁

**Files:**
- Modify: all remaining `backend/src/main/java/**/*.java`
- Modify: all remaining `backend/src/test/java/**/*.java`
- Modify: `scripts/verify-release.ps1`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: Task 1 的代码检查器和已迁移的后端源码。
- Produces: 自动阻断 `var`、`record`、Lombok 的发布流程。

- [ ] **Step 1: 扫描剩余违规并逐项清零**

Run:

```powershell
rg -n --glob '*.java' '\bvar\b|\brecord\b|lombok' backend\src
```

Expected: 如果仍有输出，逐行替换为精确类型或普通类；禁止通过改注释、字符串拼接或放宽正则绕过。

- [ ] **Step 2: 更新 AGENTS.md**

在 Source of truth 列表加入 `docs/04-项目代码约束.md` 和本次重构设计；在 Workflow/Technology 规则中明确生产与测试 Java 禁止 `var`、`record`、Lombok，值对象使用显式不可变类。

- [ ] **Step 3: 把代码检查接入发布脚本**

在 `scripts/verify-release.ps1` 的 Java 版本校验之后、依赖安装之前加入：

```powershell
$codeChecker = Join-Path $root 'scripts\code-quality-check.ps1'

& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root 'scripts\code-quality-check.test.ps1')
Assert-ExitCode 'Code quality checker tests'

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $codeChecker `
    -Path (Join-Path $root 'backend\src')
Assert-ExitCode 'Java code quality check'
```

- [ ] **Step 4: 验证自动门禁 GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\code-quality-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\code-quality-check.ps1 -Path backend\src
mvn.cmd -f backend\pom.xml test
```

Expected: 三个命令退出码均为 0；`rg` 不再发现 Java `var`、`record` 或 Lombok。

---

### Task 6: 迁移前端到轻量 feature-first 目录

**Files:**
- Move all files listed in the frontend File Map.
- Split: `frontend/src/api/portfolio.ts` into portfolio and answer API files.
- Split: `frontend/src/types/portfolio.ts` into portfolio and answer model files.
- Modify: imports only; preserve templates and CSS content.

**Interfaces:**
- Consumes: unchanged `/api/v1` contract.
- Produces: unchanged Vue behavior with `pages → features → shared` organization and `agent → portfolio` one-way feature dependency.

- [ ] **Step 1: 先移动测试并验证 RED**

移动测试到目标位置并只修正测试自身的相对 import：

- `app/router.test.ts`
- `pages/HomePage.test.ts`
- `pages/ProjectPage.test.ts`
- `features/portfolio/api/portfolioApi.test.ts`
- `features/agent/api/answerApi.test.ts`
- `features/agent/components/AgentPanel.test.ts`

把原 API 测试中 `getPortfolio/getProject` 场景放入 `portfolioApi.test.ts`，把 `askQuestion` payload、错误和超时场景放入 `answerApi.test.ts`。

Run:

```powershell
npm.cmd --prefix frontend test -- --run
```

Expected: FAIL，因为实现文件尚未移动到新路径。

- [ ] **Step 2: 移动 app、pages 和 shared 文件**

- `App.vue` → `app/App.vue`
- `router.ts` → `app/router.ts`
- `styles/main.css` → `app/styles/main.css`
- `HomeView.vue` → `pages/HomePage.vue`
- `ProjectView.vue` → `pages/ProjectPage.vue`
- `NotFoundView.vue` → `pages/NotFoundPage.vue`
- `LoadingBlock.vue` → `shared/components/LoadingBlock.vue`

Router 的组件名改为 `HomePage`、`ProjectPage`、`NotFoundPage`，路由表、props 和 scroll behavior 不变。

- [ ] **Step 3: 拆分 portfolio feature**

`features/portfolio/model/portfolioTypes.ts` 保留：

- `ProjectStatus`
- `ContributionType`
- `OwnerProfile`
- `ProjectSummary`
- `PortfolioHome`
- `Evidence`
- `ProjectDetail`

`projectLabels.ts` 随之移动并从 `./portfolioTypes` 导入。

`features/portfolio/api/portfolioApi.ts` 保留 `PortfolioApiError`、通用 `request<T>`、`getPortfolio` 和 `getProject`，并将 `request<T>` 导出供 answer API 使用。`ProjectCard`、`EvidenceCard` 移入 portfolio components。

- [ ] **Step 4: 拆分 agent feature**

`features/agent/model/answerTypes.ts` 从 portfolio model 导入 `Evidence`，定义：

- `AnswerMode`
- `AnswerSectionType`
- `AnswerSection`
- `AnswerResponse`

`features/agent/api/answerApi.ts`：

```typescript
import { request } from '../../portfolio/api/portfolioApi'
import type { AnswerResponse } from '../model/answerTypes'

export function askQuestion(projectSlug: string, question: string): Promise<AnswerResponse> {
  return request<AnswerResponse>('/api/v1/answers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectSlug, question }),
  })
}
```

`AgentPanel` 和 `AnswerSections` 移入 agent components；`AgentPanel` 从 portfolio components 导入 `EvidenceCard`。依赖方向允许 `agent → portfolio`，禁止 portfolio 反向导入 agent。

- [ ] **Step 5: 修正 pages 和 main.ts import**

`main.ts` 最终只改三个本地路径：

```typescript
import App from './app/App.vue'
import { createAppRouter } from './app/router'
import './app/styles/main.css'
```

`HomePage` 导入 portfolio API、model、ProjectCard 和 shared LoadingBlock；`ProjectPage` 导入 portfolio API/model/components、agent AgentPanel 和 shared LoadingBlock。两个页面的 `<template>` 内容保持逐字符一致。

- [ ] **Step 6: 验证前端 GREEN 和旧目录清理**

Run:

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Expected: 21 个现有 Vitest 测试及拆分后的同等测试全部通过，构建退出码 0。旧的 `src/api`、`src/components`、`src/styles`、`src/types`、`src/views` 中不保留重复实现。

---

### Task 7: 全量发布验证、结构审查和启动验收

**Files:**
- Verify only: whole repository.
- Modify only if a verification failure identifies a root cause within this refactor's scope.

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: fresh release evidence and a running final JAR for user review.

- [ ] **Step 1: 静态结构与禁止项检查**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\code-quality-check.ps1 -Path backend\src
rg -n --glob '*.java' 'com\.portfolio\.agent\.(api|service|content|domain)(\.|;)' backend\src
git diff --check
git status --short
```

Expected: 代码检查通过；旧平铺包检索无输出；`git diff --check` 无空白错误；Git 状态只包含用户原有文件和本次授权范围内的变更。

- [ ] **Step 2: 执行原子发布验证**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1 -SkipInstall -SkipDockerCheck
```

Expected: 后端测试、前端测试、前端构建、代码约束检查、隐私检查、Maven 打包和 Playwright 桌面/移动端全部通过，最后输出 `Release verification passed.`。

- [ ] **Step 3: 检查 JAR 内容**

Run:

```powershell
jar.exe tf backend\target\portfolio-agent.jar
```

Expected: 包含 `BOOT-INF/classes/static/index.html`、公开快照和新的 `common/portfolio/answer` 类；不包含旧平铺包、私有知识库、候选快照或原始证据。

- [ ] **Step 4: 启动最终 JAR**

使用隐藏后台进程启动，保存进程对象，不使用固定长等待；循环请求 `/api/v1/portfolio`，直到成功或达到 30 秒截止时间。先检查端口，若已经存在监听者则报告 PID 并停止，不终止未知进程：

```powershell
$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    throw "Port 8080 is already used by PID $($listener.OwningProcess)."
}

$process = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
    -ArgumentList '-jar', 'backend\target\portfolio-agent.jar' `
    -WorkingDirectory (Get-Location) -WindowStyle Hidden -PassThru

$deadline = (Get-Date).AddSeconds(30)
$ready = $false
do {
    if ($process.HasExited) {
        throw "portfolio-agent exited with code $($process.ExitCode)."
    }
    try {
        $response = Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/api/v1/portfolio'
        $ready = $response.StatusCode -eq 200
    }
    catch {
        Start-Sleep -Milliseconds 500
    }
} while (-not $ready -and (Get-Date) -lt $deadline)

if (-not $ready) {
    Stop-Process -Id $process.Id
    throw 'portfolio-agent did not become ready within 30 seconds.'
}
```

轮询成功后保留该进程运行，让用户访问 `http://localhost:8080/`。若端口已占用，先识别占用进程，不得盲目终止未知进程。

- [ ] **Step 5: 执行 HTTP 冒烟检查**

Run:

```powershell
Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/'
Invoke-RestMethod 'http://localhost:8080/api/v1/portfolio'
Invoke-RestMethod 'http://localhost:8080/api/v1/projects/sql-audit'
Invoke-RestMethod -Method Post -ContentType 'application/json' `
    -Body '{"projectSlug":"sql-audit","question":"请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？"}' `
    'http://localhost:8080/api/v1/answers'
```

Expected: 首页 200；作品集和项目接口返回原有字段；回答接口返回 `DETERMINISTIC`、`matched=true`、五个 section 和已审核 Evidence。

- [ ] **Step 6: 最终人工审查**

逐项对照设计文档完成定义，确认：

1. `d11_server` 没有任何变更；
2. 16 个 record 已全部转为普通不可变类；
3. 生产和测试 Java 没有 `var` 或 Lombok；
4. Controller、Service、Repository 和异常边界符合设计；
5. 前端 UI、文案、CSS 和交互没有变化；
6. 未操作 Git 暂存、提交或远端；
7. 最终应用仍在本地运行，用户可以直接查看。
