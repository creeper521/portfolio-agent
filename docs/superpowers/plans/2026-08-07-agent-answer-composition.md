# Agent Answer Composition Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单主体作品集事实回答从 Passage 列表升级为基于完整 Claim Projection、可确定复现、按语义章节组织并聚合引用的回答。

**Architecture:** Retriever 继续拥有事实选择与相关性顺序，`PortfolioIntelligenceResult` 通过 `AnswerFocus` 和结构化 Passage 把最小必要语义交给 `DeterministicPortfolioAnswerComposer`。Composer 产生内部 `PortfolioAnswerPlan`，Assembler 只映射运行元数据与 v2 typed Blocks；前端在 `mapAnswerResponse` 中统一 Blocks/legacy Sections，组件只消费 `AnswerSectionView[]`。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、AssertJ、Flyway/PostgreSQL、Vue 3、TypeScript、Vitest、Playwright、PowerShell。

## Global Constraints

- 只处理 `ANSWERED + FACT_LOOKUP + 单主体`；Comparison、Recommendation、澄清和失败响应保持现有路径。
- Composer 不读取用户原文，不调用模型、网络、数据库或工具。
- 最终正文只来自公开、`VERIFIED` Claim 的 `statement + detail`。
- Evidence 必须为 `APPROVED`，输出 Claim/Evidence ID 必须来自同一输入 Result。
- `OVERVIEW` 生成直接 Summary；`FOCUSED` 不生成 Summary。
- 部分目标章节缺失时允许部分回答；全部缺失时 `NOT_SUPPORTED + INSUFFICIENT`。
- 数据或 Plan 不变量损坏时丢弃整个 Plan，返回 `CAPABILITY_UNAVAILABLE + INSUFFICIENT`、`degraded=true` 和 `ANSWER_COMPOSITION_INVALID`。
- 后端只发布增强后的 v2 Blocks；legacy Sections 只在前端映射层读取。
- Eval、日志和报告不得保存问题、正文、Prompt、稳定 ID hash 或私有路径。
- 普通 CI 不调用真实 Provider、真实 PostgreSQL 服务或 ONNX；真实依赖仍需显式授权。

---

## File Map

### 新增文件

- `backend/src/main/resources/db/public/V2__claim_answer_projection.sql`：为新发布的公共 Claim 保存 Composer 所需语义，不为历史行伪造默认值。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/AnswerFocusMode.java`：`OVERVIEW` / `FOCUSED`。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/AnswerFocus.java`：结构化回答焦点。
- `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerSection.java`：内部不可变章节。
- `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlan.java`：内部不可变回答计划。
- `backend/src/main/java/com/portfolio/agent/answer/exception/PortfolioAnswerCompositionException.java`：类型化编排失败。
- `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java`：唯一 P1 Composer。
- `backend/src/test/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlanTest.java`：Plan 不变量测试。
- `backend/src/test/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposerTest.java`：Composer 单元测试。
- `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java`：P1 路由与降级测试。

### 主要修改文件

- PostgreSQL：`PublicBundleDatabaseImporter.java`、`PostgresKnowledgePassageRow.java`、`JdbcPostgresFactPassageQuery.java`、`PostgresPortfolioRetriever.java` 及其测试。
- Intelligence：`PortfolioRetrievedPassage.java`、`PortfolioIntelligenceResult.java`、`DefaultPortfolioIntelligence.java`、`PortfolioRetrievalPlanner.java` 及其测试。
- HTTP：`ConversationAnswerBlock.java`、`ConversationAnswerResult.java`、`ConversationAnswerBlockResponse.java`、`ConversationAnswerResponse.java` 及其测试。
- 装配：`PortfolioIntelligenceConfiguration.java`、`PortfolioIntelligenceAnswerAssembler.java`、受影响的 runtime/privacy 测试。
- 前端：`answerTypes.ts`、`mapAnswerResponse.ts`、`ConversationThread.vue`、`evidenceDeskModel.ts` 及其测试。
- Eval：`EvalAnswerShape.java`、`JdkEvalAnswerClient.java`、`DeterministicEvalGrader.java`、JSON/Markdown 报告与测试。
- E2E/文档：`frontend/e2e/portfolio.spec.ts`、`publicApiMocks.ts`、状态索引、当前实现状态、演进日志和路线图。

---

### Task 1: 完整保存并读取 PostgreSQL Claim Projection

**Files:**
- Create: `backend/src/main/resources/db/public/V2__claim_answer_projection.sql`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/postgres/PublicBundleDatabaseImporter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresKnowledgePassageRow.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQuery.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PublicBundleDatabaseImporterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQueryTest.java`

**Interfaces:**
- Consumes: `portfolio.domain.Claim` 的 category、statement、detail、achievementStatus、contributionType、verificationBasis、verificationStatus、materiality、topics。
- Produces: `PostgresKnowledgePassageRow.getClaim(): AnswerClaimProjection`，其语义与 Bundle 中的 Projection 一致。

- [ ] **Step 1: 写失败的数据库投影测试**

在 Importer 测试中断言发布后 Claim 行保存真实语义；在 JDBC 查询测试中断言查询返回完整 Projection：

```java
assertThat(row.getClaim()).satisfies(claim -> {
    assertThat(claim.getCategory()).isEqualTo(AnswerClaimCategory.VERIFICATION);
    assertThat(claim.getStatement()).isEqualTo("已验证主要功能流程。");
    assertThat(claim.getDetail()).isEqualTo("验证范围以公开证据为限。");
    assertThat(claim.getAchievementStatus()).isEqualTo(AnswerAchievementStatus.IMPLEMENTED_TESTED);
    assertThat(claim.getContributionType()).isEqualTo(AnswerContributionType.PRIMARY);
    assertThat(claim.getVerificationBasis()).isEqualTo(AnswerVerificationBasis.EVIDENCE_SUPPORTED);
    assertThat(claim.getVerificationStatus()).isEqualTo(AnswerClaimVerificationStatus.VERIFIED);
    assertThat(claim.getMateriality()).isEqualTo(AnswerMateriality.KEY);
    assertThat(claim.getTopics()).containsExactly("POSTGRESQL");
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PublicBundleDatabaseImporterTest,JdbcPostgresFactPassageQueryTest test
```

Expected: FAIL，因为 V1 `claim` 表和 Row 尚不保存完整 Projection。

- [ ] **Step 3: 增加失败关闭的 V2 schema**

创建迁移；历史行保持 `NULL`，查询层不会把它们伪装成完整 Claim，新 Release 由 Importer 写入全部字段：

```sql
ALTER TABLE claim
    ADD COLUMN detail text,
    ADD COLUMN achievement_status varchar(40),
    ADD COLUMN contribution_type varchar(40),
    ADD COLUMN verification_basis varchar(40),
    ADD COLUMN materiality varchar(40),
    ADD COLUMN topics jsonb;
```

- [ ] **Step 4: 扩展 Importer 与查询映射**

将 `INSERT_CLAIM_SQL` 扩展为：

```java
private static final String INSERT_CLAIM_SQL = """
        INSERT INTO claim
            (release_id, stable_id, subject_stable_id, subject_kind, category,
             statement, detail, achievement_status, contribution_type,
             verification_basis, verification_status, materiality, topics, display_order)
        VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
        """;
```

Importer 参数严格来自 `Claim`；`FACT_PASSAGE_SQL` 选择相同字段并要求所有新增列非空。`PostgresKnowledgePassageRow` 只保留一个完整 `AnswerClaimProjection claim`，删除默认 `IMPLEMENTATION` 构造路径；`getClaimId()` 与 `getClaimCategory()` 委托给该 Projection，保持现有查询调用方兼容。

- [ ] **Step 5: 运行 PostgreSQL 投影测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PublicBundleDatabaseImporterTest,JdbcPostgresFactPassageQueryTest,JdbcPostgresKnowledgeQueryTest test
```

Expected: PASS；历史不完整行不会进入 Fact Passage 查询。

- [ ] **Step 6: 提交 Task 1**

```powershell
git add backend/src/main/resources/db/public/V2__claim_answer_projection.sql backend/src/main/java/com/portfolio/agent/portfolio/repository/postgres/PublicBundleDatabaseImporter.java backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresKnowledgePassageRow.java backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQuery.java backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PublicBundleDatabaseImporterTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresFactPassageQueryTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQueryTest.java
git commit -m "feat: preserve claim semantics in public postgres projection"
```

---

### Task 2: 让 Retriever 输出完整 Claim，并贯穿 AnswerFocus

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/AnswerFocusMode.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/AnswerFocus.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievedPassage.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioIntelligenceResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetriever.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresPortfolioRetriever.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioRetrievalPlanner.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDomainContractTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/PostgresPortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceTest.java`

**Interfaces:**
- Consumes: `AnswerClaimProjection`、Retriever 顺序、`PortfolioTask.getPreferredClaimCategories()`。
- Produces: `PortfolioRetrievedPassage.getClaim()` 与 `PortfolioIntelligenceResult.getAnswerFocus()`。

- [ ] **Step 1: 写 Passage 与 Focus 不变量测试**

```java
assertThat(AnswerFocus.overview().getMode()).isEqualTo(AnswerFocusMode.OVERVIEW);
assertThat(AnswerFocus.focused(List.of(
        AnswerClaimCategory.VERIFICATION,
        AnswerClaimCategory.VERIFICATION)).getRequestedClaimCategories())
        .containsExactly(AnswerClaimCategory.VERIFICATION);

assertThatThrownBy(() -> AnswerFocus.focused(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("focused answer requires claim categories");
```

Passage 测试覆盖 Claim 非 VERIFIED、Reference 非 APPROVED、直接 Evidence 集合不一致三种拒绝。

- [ ] **Step 2: 运行领域与 Retriever 测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioDomainContractTest,BundlePortfolioRetrieverTest,PostgresPortfolioRetrieverTest,DefaultPortfolioIntelligenceTest test
```

Expected: FAIL，因为 Focus 与 `getClaim()` 尚不存在。

- [ ] **Step 3: 实现 AnswerFocus**

```java
public static AnswerFocus overview() {
    return new AnswerFocus(AnswerFocusMode.OVERVIEW, List.of());
}

public static AnswerFocus focused(List<AnswerClaimCategory> categories) {
    Objects.requireNonNull(categories, "categories");
    List<AnswerClaimCategory> distinct = categories.stream().distinct().toList();
    if (distinct.isEmpty()) {
        throw new IllegalArgumentException("focused answer requires claim categories");
    }
    return new AnswerFocus(AnswerFocusMode.FOCUSED, distinct);
}
```

- [ ] **Step 4: 升级 PortfolioRetrievedPassage**

构造器保留检索文本兼容字段，但正文权威来自 Claim：

```java
public PortfolioRetrievedPassage(
        String passageId,
        String subjectId,
        String retrievalContent,
        AnswerClaimProjection claim,
        List<PortfolioRetrievedEvidenceReference> evidenceReferences) {
    this.passageId = requireText(passageId, "passageId");
    this.subjectId = requireText(subjectId, "subjectId");
    this.content = requireText(retrievalContent, "retrievalContent");
    this.claim = validateClaim(claim, evidenceReferences);
    this.evidenceReferences = List.copyOf(evidenceReferences);
}

public String getClaimId() { return claim.getId(); }
public AnswerClaimProjection getClaim() { return claim; }
```

`validateClaim` 检查 VERIFIED、APPROVED 和直接 Evidence 集合完全相等，不允许补默认 Category。

- [ ] **Step 5: 让双 Retriever 传递同一 Projection**

Bundle 构造 Passage 时直接传已有 `claim`；PostgreSQL 使用 `row.getClaim()`：

```java
return new PortfolioRetrievedPassage(
        row.getSubjectId() + "#" + row.getClaim().getId(),
        row.getSubjectId(),
        row.getContent(),
        row.getClaim(),
        row.getEvidenceReferences().stream()
                .map(reference -> new PortfolioRetrievedEvidenceReference(
                        reference.getEvidenceId(), reference.getLabel(),
                        reference.getPublicStatus()))
                .toList());
```

- [ ] **Step 6: 将 Focus 写入 Intelligence Result**

`PortfolioIntelligenceResult` 的主构造器新增 `AnswerFocus`，现有便利构造器默认 `overview()`，新增不可变复制方法 `withAnswerFocus(AnswerFocus)`；所有 `withDecisionMetadata` / `withContractIdentity` 复制路径保留 Focus。`DefaultPortfolioIntelligence` 使用：

```java
private AnswerFocus focusFor(List<AnswerClaimCategory> categories) {
    return categories.isEmpty()
            ? AnswerFocus.overview()
            : AnswerFocus.focused(categories);
}
```

`retrieveMaterial`、Preset 与 Reference 对结果调用 `withAnswerFocus(focusFor(actualCategories))`；Comparison、Recommendation 与专用失败结果使用 `overview()`，但不会进入 P1 Composer。

- [ ] **Step 7: 运行 Task 2 测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioDomainContractTest,BundlePortfolioRetrieverTest,PostgresPortfolioRetrieverTest,DefaultPortfolioIntelligenceTest,DefaultPortfolioIntelligenceRoutingTest test
```

Expected: PASS，且 Bundle/PostgreSQL 断言同一 Projection 字段。

- [ ] **Step 8: 提交 Task 2**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence backend/src/test/java/com/portfolio/agent/answer/intelligence
git commit -m "feat: carry answer focus and verified claims through intelligence"
```

---

### Task 3: 建立不可变 PortfolioAnswerPlan

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerSection.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlan.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/exception/PortfolioAnswerCompositionException.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlanTest.java`

**Interfaces:**
- Consumes: `AnswerSectionType`、正文与稳定 Claim/Evidence ID。
- Produces: `PortfolioAnswerPlan.getTitle()`、`getSummary()`、`getSections()`。

- [ ] **Step 1: 写 Plan 不变量测试**

```java
PortfolioAnswerPlan plan = new PortfolioAnswerPlan(
        "SQL 审计工具",
        "公开项目摘要",
        List.of(new PortfolioAnswerSection(
                AnswerSectionType.SOLUTION,
                "技术方案与实现",
                "使用受控路由替代硬编码。",
                List.of("claim-1", "claim-1"),
                List.of("evidence-1", "evidence-1"))));

assertThat(plan.getSections()).singleElement().satisfies(section -> {
    assertThat(section.getClaimIds()).containsExactly("claim-1");
    assertThat(section.getEvidenceIds()).containsExactly("evidence-1");
});
```

同时断言空标题、空章节和重复 Section Type 被拒绝。

- [ ] **Step 2: 运行测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioAnswerPlanTest test
```

Expected: FAIL，因为 Plan 类型尚不存在。

- [ ] **Step 3: 实现值对象和类型化异常**

`PortfolioAnswerSection` trim 文本、稳定去重 ID；`PortfolioAnswerPlan` 验证 Section Type 唯一：

```java
Set<AnswerSectionType> types = new HashSet<>();
for (PortfolioAnswerSection section : sections) {
    if (!types.add(section.getSectionType())) {
        throw new PortfolioAnswerCompositionException(
                "answer plan contains duplicate section type");
    }
}
```

异常消息只用于内部测试和脱敏分类，不进入 HTTP 文案。

- [ ] **Step 4: 运行 Plan 测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioAnswerPlanTest test
```

Expected: PASS。

- [ ] **Step 5: 提交 Task 3**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlan.java backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioAnswerSection.java backend/src/main/java/com/portfolio/agent/answer/exception/PortfolioAnswerCompositionException.java backend/src/test/java/com/portfolio/agent/answer/domain/PortfolioAnswerPlanTest.java
git commit -m "feat: define immutable portfolio answer plan"
```

---

### Task 4: 实现确定性 Composer

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposerTest.java`

**Interfaces:**
- Consumes: `compose(PortfolioIntelligenceResult result)`。
- Produces: `PortfolioAnswerPlan`；非法输入抛 `PortfolioAnswerCompositionException`。

- [ ] **Step 1: 写章节映射、顺序和摘要失败测试**

```java
PortfolioAnswerPlan plan = composer.compose(result);

assertThat(plan.getTitle()).isEqualTo("SQL 审计与故障排查工具");
assertThat(plan.getSummary()).isEqualTo("公开项目摘要");
assertThat(plan.getSections())
        .extracting(PortfolioAnswerSection::getSectionType)
        .containsExactly(
                AnswerSectionType.BACKGROUND,
                AnswerSectionType.RESPONSIBILITY,
                AnswerSectionType.SOLUTION,
                AnswerSectionType.VERIFICATION,
                AnswerSectionType.STATUS,
                AnswerSectionType.BOUNDARY);
```

- [ ] **Step 2: 写去重、预算与缺口失败测试**

覆盖同 Claim、同 `statement + detail`、FOCUSED 无 Summary、多个目标中缺一类，以及限制词不被改写：

```java
assertThat(plan.getSummary()).isNull();
assertThat(plan.getSections()).extracting(PortfolioAnswerSection::getSectionType)
        .containsExactly(AnswerSectionType.VERIFICATION, AnswerSectionType.BOUNDARY);
assertThat(plan.getSections().getLast().getContent())
        .contains("当前公开材料未覆盖最终状态。")
        .doesNotContain("已上线", "长期有效");
```

- [ ] **Step 3: 写非法输入失败测试**

```java
assertThatThrownBy(() -> composer.compose(invalidResult))
        .isInstanceOf(PortfolioAnswerCompositionException.class);
```

多主体、非 FACT_LOOKUP、Evidence 越界和 Claim/Subject 不一致均整轮失败。

- [ ] **Step 4: 运行 Composer 测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DeterministicPortfolioAnswerComposerTest test
```

Expected: FAIL，因为 Composer 尚不存在。

- [ ] **Step 5: 实现集中映射与预算**

```java
private static final List<AnswerSectionType> SECTION_ORDER = List.of(
        AnswerSectionType.BACKGROUND,
        AnswerSectionType.RESPONSIBILITY,
        AnswerSectionType.SOLUTION,
        AnswerSectionType.VERIFICATION,
        AnswerSectionType.STATUS,
        AnswerSectionType.BOUNDARY);

private AnswerSectionType sectionType(AnswerClaimCategory category) {
    return switch (category) {
        case BACKGROUND -> AnswerSectionType.BACKGROUND;
        case RESPONSIBILITY -> AnswerSectionType.RESPONSIBILITY;
        case TECHNICAL_DECISION, IMPLEMENTATION -> AnswerSectionType.SOLUTION;
        case VERIFICATION -> AnswerSectionType.VERIFICATION;
        case OUTCOME -> AnswerSectionType.STATUS;
        case LIMITATION, LEARNING, REFLECTION -> AnswerSectionType.BOUNDARY;
    };
}
```

`OVERVIEW_FACTS_PER_SECTION = 3`、`FOCUSED_FACTS_PER_SECTION = 6` 定义在该类；正文以第一次检索出现顺序组织，只做精确规范化去重和标点连接。

- [ ] **Step 6: 实现 Summary 和唯一 Boundary**

`OVERVIEW` 使用非空主体 Summary，否则使用首条已选 Claim 的 `statement`。`FOCUSED` 返回 `null` Summary。缺失 Section 标题通过固定映射生成受控文案，并与真实边界事实合并到同一个 Boundary。

- [ ] **Step 7: 运行 Composer 与领域测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DeterministicPortfolioAnswerComposerTest,PortfolioAnswerPlanTest,PortfolioDomainContractTest test
```

Expected: PASS；重复执行同一输入得到相等的标题、Summary、章节内容与 ID 顺序。

- [ ] **Step 8: 提交 Task 4**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposer.java backend/src/test/java/com/portfolio/agent/answer/service/DeterministicPortfolioAnswerComposerTest.java
git commit -m "feat: compose deterministic semantic portfolio sections"
```

---

### Task 5: 接入 Assembler、Summary 与 typed v2 Blocks

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerBlock.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerBlockResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssemblerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponseTest.java`
- Modify test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`
- Modify test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/RuntimeCompositePrivacyTest.java`

**Interfaces:**
- Consumes: `DeterministicPortfolioAnswerComposer.compose(result)`。
- Produces: Block 的 `getSectionType()/getTitle()`、Result/Response 的 `getSummary()`。

- [ ] **Step 1: 写 Assembler 选择路径失败测试**

```java
ConversationAnswerResult answer = assembler.assemble(request, content, decision);

assertThat(answer.getSummary()).isEqualTo("公开项目摘要");
assertThat(answer.getBlocks()).extracting(ConversationAnswerBlock::getSectionType)
        .containsExactly(AnswerSectionType.BACKGROUND, AnswerSectionType.SOLUTION);
assertThat(answer.getBlocks()).allSatisfy(block -> {
    assertThat(block.getTitle()).isNotBlank();
    assertThat(block.getEvidenceIds()).isNotEmpty();
});
```

另用 Comparison 与 Recommendation Result 断言未类型化现有路径保持不变。

- [ ] **Step 2: 写 Composer 失败关闭测试**

```java
assertThat(answer.getResolution()).isEqualTo(AnswerResolution.CAPABILITY_UNAVAILABLE);
assertThat(answer.getEvidenceState()).isEqualTo(AnswerEvidenceState.INSUFFICIENT);
assertThat(answer.getConstructionMode()).isEqualTo(AnswerConstructionMode.TEMPLATE);
assertThat(answer.isDegraded()).isTrue();
assertThat(answer.getNoticeCode()).isEqualTo("ANSWER_COMPOSITION_INVALID");
assertThat(answer.getBlocks()).singleElement().satisfies(block -> {
    assertThat(block.getContent()).isEqualTo("当前公开材料暂时无法形成可靠回答。");
    assertThat(block.getClaimIds()).isEmpty();
    assertThat(block.getEvidenceIds()).isEmpty();
});
```

- [ ] **Step 3: 运行 Assembler/DTO 测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioIntelligenceAnswerAssemblerTest,ConversationAnswerResponseTest test
```

Expected: FAIL，因为 Summary 和 typed Block 字段尚不存在。

- [ ] **Step 4: 加法式扩展 Block 与 Result**

```java
public ConversationAnswerBlock(
        ConversationSourceScope sourceScope,
        String content,
        List<String> claimIds,
        List<String> evidenceIds) {
    this(sourceScope, null, null, content, claimIds, evidenceIds);
}

public ConversationAnswerBlock(
        ConversationSourceScope sourceScope,
        AnswerSectionType sectionType,
        String title,
        String content,
        List<String> claimIds,
        List<String> evidenceIds) {
    this.sourceScope = Objects.requireNonNull(sourceScope, "sourceScope");
    this.sectionType = sectionType;
    this.title = normalizeNullable(title);
    this.content = requireText(content, "content");
    this.claimIds = stableDistinct(claimIds);
    this.evidenceIds = stableDistinct(evidenceIds);
}
```

六字段构造器承担 `@JsonCreator`，旧四字段构造器继续供现有调用方使用。`ConversationAnswerResult` 增加不可变 `summary` 和 `withSummary(String)`；所有复制方法保留 Summary。Response 对 Summary、sectionType、title 使用 `NON_NULL`。

- [ ] **Step 5: 注入 Composer 并收窄 Assembler**

```java
@Bean
DeterministicPortfolioAnswerComposer deterministicPortfolioAnswerComposer() {
    return new DeterministicPortfolioAnswerComposer();
}

@Bean
PortfolioIntelligenceAnswerAssembler portfolioIntelligenceAnswerAssembler(
        DeterministicPortfolioAnswerComposer composer) {
    return new PortfolioIntelligenceAnswerAssembler(composer);
}
```

Assembler 只在 disposition、mode、subject count 全部符合时调用 Composer；捕获 `PortfolioAnswerCompositionException` 并构造固定安全响应，不记录异常正文。

- [ ] **Step 6: 运行后端回答测试**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioIntelligenceAnswerAssemblerTest,ConversationAnswerResponseTest,ConversationalAgentRuntimeTest,RuntimeCompositePrivacyTest test
```

Expected: PASS；JSON 新字段出现，旧响应字段不变。

- [ ] **Step 7: 提交 Task 5**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "feat: expose typed answer blocks from portfolio composition"
```

---

### Task 6: 将前端协议统一为 AnswerSectionView

**Files:**
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`

**Interfaces:**
- Consumes: raw `AnswerResponse.blocks?: AnswerBlock[]` 与 `sections?: LegacyAnswerSection[]`。
- Produces: `MappedAnswer.sections: AnswerSectionView[]`；MappedAnswer 不再暴露 raw Blocks。

- [ ] **Step 1: 写 Blocks 优先和 legacy fallback 失败测试**

```ts
const mapped = mapAnswerResponse({
  ...baseResponse,
  summary: '公开项目摘要',
  blocks: [{
    sourceScope: 'PORTFOLIO',
    sectionType: 'SOLUTION',
    title: '技术方案与实现',
    content: '使用受控路由。',
    claimIds: ['claim-1'],
    evidenceIds: ['evidence-1'],
  }],
  sections: [{
    type: 'BACKGROUND',
    title: '旧章节',
    content: '不得采用',
    claimIds: [],
    evidenceIds: [],
  }],
})

expect(mapped.sections).toEqual([{
  key: 'SOLUTION:0',
  type: 'SOLUTION',
  title: '技术方案与实现',
  sourceScope: 'PORTFOLIO',
  content: '使用受控路由。',
  claimIds: ['claim-1'],
  evidenceIds: ['evidence-1'],
}])
expect(mapped).not.toHaveProperty('blocks')
```

- [ ] **Step 2: 写旧无类型 Block 兼容测试**

断言 GENERAL Block 映射为 `GENERAL`，非 ANSWERED Portfolio Block 映射为 `BOUNDARY`，标题为空但正文和引用保留。

同时构造只有 `sectionType`、没有 `title` 的 typed Block，断言映射抛出 `Answer response contains an invalid typed block`，并通过 `frontendDiagnostics` 只上报 `ANSWER_BLOCK_INVALID`、不携带正文或 ID。

- [ ] **Step 3: 运行映射测试并确认失败**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/model/mapAnswerResponse.test.ts
```

Expected: FAIL，因为当前 MappedAnswer 仍暴露 Blocks，Sections 不来自 v2 typed Blocks。

- [ ] **Step 4: 定义 raw 与 view 类型边界**

```ts
export interface AnswerBlock {
  sourceScope: BlockSourceScope
  sectionType?: AnswerSectionType
  title?: string
  content: string
  claimIds: string[]
  evidenceIds: string[]
}

export interface AnswerSectionView {
  key: string
  type: AnswerSectionType
  title: string
  sourceScope: BlockSourceScope
  content: string
  claimIds: string[]
  evidenceIds: string[]
}
```

`AnswerResponse.sections` 使用 `LegacyAnswerSection[]`；`MappedAnswer.sections` 使用 `AnswerSectionView[]` 并删除 `blocks`。

- [ ] **Step 5: 实现唯一映射入口**

Blocks 非空时只映射 Blocks，否则读取 legacy Sections。typed Block 的 `sectionType/title` 必须同时存在且非空；违反契约时先发布脱敏诊断再拒绝整条响应。数组全部深拷贝，Evidence 顶层集合从统一 Sections 推导。

- [ ] **Step 6: 运行映射测试和类型检查**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/model/mapAnswerResponse.test.ts
npm.cmd --prefix frontend run check
```

Expected: 两条命令 PASS。

- [ ] **Step 7: 提交 Task 6**

```powershell
git add frontend/src/features/agent/model/answerTypes.ts frontend/src/features/agent/model/mapAnswerResponse.ts frontend/src/features/agent/model/mapAnswerResponse.test.ts
git commit -m "refactor: unify answer response into section views"
```

---

### Task 7: 让 ConversationThread 与 Evidence Desk 只渲染统一 Sections

**Files:**
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.test.ts`
- Modify: `frontend/src/features/agent/model/evidenceDeskModel.ts`
- Modify: `frontend/src/features/agent/model/evidenceDeskModel.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: `MappedAnswer.sections: AnswerSectionView[]`。
- Produces: 回答级范围标签一次、章节标题/正文/引用、Evidence Desk 定位。

- [ ] **Step 1: 写单一范围标签和章节渲染失败测试**

```ts
expect(wrapper.findAll('[data-scope="PORTFOLIO"]')).toHaveLength(1)
expect(wrapper.findAll('[data-section-type]')).toHaveLength(2)
expect(wrapper.get('[data-section-type="BACKGROUND"]').text()).toContain('项目背景')
expect(wrapper.get('[data-section-type="SOLUTION"]').text()).toContain('技术方案与实现')
expect(wrapper.findAll('[data-block-scope]')).toHaveLength(0)
```

- [ ] **Step 2: 写 Evidence Desk 聚合失败测试**

```ts
const context = buildEvidenceDeskContext(messages)
expect(context.citations.map(item => item.evidenceId))
  .toEqual(['evidence-background', 'evidence-solution'])
expect(context.citations.map(item => item.sectionType))
  .toEqual(['BACKGROUND', 'SOLUTION'])
```

- [ ] **Step 3: 运行组件测试并确认失败**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/components/ConversationThread.test.ts src/features/agent/model/evidenceDeskModel.test.ts src/features/agent/components/AgentWorkspace.test.ts
```

Expected: FAIL，因为 ConversationThread 仍有 v2 Blocks 与 legacy Sections 双分支。

- [ ] **Step 4: 删除组件内协议分支**

删除 `v2Blocks()` 与 `isV2Answer()`。模板始终遍历 `message.answer.sections`，标题为空时不渲染 `<h4>`；key 使用 `section.key`，Focus/Follow-up 继续使用 `section.type`。Summary 元素增加 `data-answer-summary`，供 Focused/Overview 契约测试定位。

- [ ] **Step 5: 保留未来 MIXED seam**

Section View 保留 `sourceScope`，但 P1 不在每章渲染来源。只有回答同时包含 GENERAL 与 PORTFOLIO 时才允许增加来源分区；本任务不实现 MIXED UI。

- [ ] **Step 6: 运行组件、类型和 lint**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/components/ConversationThread.test.ts src/features/agent/model/evidenceDeskModel.test.ts src/features/agent/components/AgentWorkspace.test.ts
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run lint
```

Expected: 全部 PASS。

- [ ] **Step 7: 提交 Task 7**

```powershell
git add frontend/src/features/agent/components/ConversationThread.vue frontend/src/features/agent/components/ConversationThread.test.ts frontend/src/features/agent/model/evidenceDeskModel.ts frontend/src/features/agent/model/evidenceDeskModel.test.ts frontend/src/features/agent/components/AgentWorkspace.test.ts
git commit -m "feat: render semantic answer sections once"
```

---

### Task 8: 让 Eval 测量真实 typed sections

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalAnswerShape.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/execution/JdkEvalAnswerClient.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/grading/DeterministicEvalGrader.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalReportJsonWriter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalReportMarkdownRenderer.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/domain/EvalAnswerShapeTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/JdkEvalAnswerClientTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/grading/DeterministicEvalGraderTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalReportJsonWriterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalReportMarkdownRendererTest.java`

**Interfaces:**
- Consumes: Block `sectionType`、顶层 Summary、正文临时内存。
- Produces: `typedSectionCount`、`untypedBlockCount`、`sectionOrderValid`、`summaryPresent`；不保留正文或 ID。

- [ ] **Step 1: 写 Eval Shape 失败测试**

```java
EvalAnswerShape shape = EvalAnswerShape.from(
        List.of(
                typed(AnswerSectionType.BACKGROUND, "背景"),
                typed(AnswerSectionType.SOLUTION, "方案")),
        "直接摘要");

assertThat(shape.getSemanticSectionCount()).isEqualTo(2);
assertThat(shape.getTypedSectionCount()).isEqualTo(2);
assertThat(shape.getUntypedBlockCount()).isZero();
assertThat(shape.isSectionOrderValid()).isTrue();
assertThat(shape.isSummaryPresent()).isTrue();
```

再用 `SOLUTION → BACKGROUND` 断言顺序为 false；无类型 Block 不增加 semantic section。

- [ ] **Step 2: 写 HTTP 解析与 Grader 失败测试**

HTTP fixture 包含 Summary、sectionType、title；断言 Client 只输出 Shape。`ANSWER_QUALITY` 对乱序 typed sections 失败，对合法 typed sections 通过。

- [ ] **Step 3: 运行 Eval 测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalAnswerShapeTest,JdkEvalAnswerClientTest,DeterministicEvalGraderTest,EvalReportJsonWriterTest,EvalReportMarkdownRendererTest test
```

Expected: FAIL，因为 Shape 尚不解析 typed section 或 Summary。

- [ ] **Step 4: 实现纯结构 Shape**

```java
public static EvalAnswerShape from(List<ConversationAnswerBlock> blocks) {
    return from(blocks, null);
}

public static EvalAnswerShape from(
        List<ConversationAnswerBlock> blocks,
        String summary) {
    return inspectWithoutRetainingContent(blocks, summary);
}
```

`semanticSectionCount` 等于 typed section 数；`directAnswerPresent` 在 Summary 非空或首个 Block 非空时为 true。固定章节顺序使用 `AnswerSectionType`，无类型 Block 单独计数。

- [ ] **Step 5: 更新 HTTP Client、Grader 与报告**

Client 解析 Block 的可选 `sectionType/title`，调用 `EvalAnswerShape.from(blocks, text(root, "summary"))`。Grader 要求正文存在、无重复正文；当回答存在 typed sections 时还要求顺序合法。旧无类型兼容 case 不因缺少 typed section 成为 Blocking，P1 是否真正输出 typed sections 由契约测试和 E2E 锁定。

- [ ] **Step 6: 运行 Eval 测试与离线校验**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalAnswerShapeTest,JdkEvalAnswerClientTest,DeterministicEvalGraderTest,EvalReportJsonWriterTest,EvalReportMarkdownRendererTest test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 validate
```

Expected: 单测 PASS；`eval validate` 返回 PASS，且报告不包含问题或正文。

- [ ] **Step 7: 提交 Task 8**

```powershell
git add backend/src/main/java/com/portfolio/agent/evaluation backend/src/test/java/com/portfolio/agent/evaluation
git commit -m "feat: evaluate typed answer structure"
```

---

### Task 9: 增加目标 E2E、同步文档并运行全门禁

**Files:**
- Modify: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `backend/src/test/resources/evaluation/cases/holdout/answer.v1.json`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/13-Agent对话体验与智能编排改造路线图.md`

**Interfaces:**
- Consumes: 完成后的 v2 typed Block 与统一 Section View。
- Produces: 桌面/移动产品验收、阶段 1 真实状态记录。

- [ ] **Step 1: 增加三种回答 fixture**

`publicApiMocks.ts` 增加完整 Overview、Focused Verification、部分缺口。Overview fixture 包含 Summary 和至少四种 typed Blocks；部分缺口包含有 Evidence 的事实章节与无伪引用的 Boundary。

- [ ] **Step 2: 增加 E2E 断言**

```ts
await expect(answer.locator('[data-section-type="BACKGROUND"]')).toBeVisible()
await expect(answer.locator('[data-section-type="SOLUTION"]')).toBeVisible()
await expect(answer.locator('[data-scope="PORTFOLIO"]')).toHaveCount(1)
await expect(answer.locator('[data-block-scope]')).toHaveCount(0)
await answer.locator('[data-section-type="SOLUTION"] [data-section-evidence]').click()
await expect(page.locator('#agent-evidence-desk')).toContainText('evidence-solution')
```

Focused 场景断言无 Summary；部分缺口断言 Boundary 文案存在且没有虚构 Evidence 按钮。保留 Comparison/Recommendation 既有用例。

- [ ] **Step 3: 增加 Eval holdout 目标 case**

使用现有 Schema 添加单主体详细介绍 case，要求 `ANSWERED`、`PORTFOLIO`、必要 Claim/Evidence 和 `ANSWER_QUALITY`；不把完整中文措辞放入 oracle。

- [ ] **Step 4: 运行目标测试**

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
npm.cmd --prefix frontend run test:e2e
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 validate
```

Expected: 后端、前端、类型、lint、构建、架构、隐私、桌面/移动 E2E 与 eval validate 全部 PASS；条件性真实依赖测试保持显式跳过说明。

- [ ] **Step 5: 同步权威文档**

准确记录：

- 阶段 1 首批只覆盖单主体 Fact Lookup；
- Comparison/Recommendation 尚未迁移；
- Bundle/PostgreSQL Claim Projection 已对齐；
- typed v2 Block 和统一 Section View 已生产可达；
- Eval 已测量真实 typed section；
- 未运行的真实 Provider/数据库/ONNX 不写成已验证。

- [ ] **Step 6: 检查工作区只包含本计划改动**

```powershell
git status --short
git diff --check
```

Expected: 无空白错误；没有生成物、Secret、测试报告正文或无关文件。

- [ ] **Step 7: 提交 Task 9**

```powershell
git add frontend/e2e/support/publicApiMocks.ts frontend/e2e/portfolio.spec.ts backend/src/test/resources/evaluation/cases/holdout/answer.v1.json docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/13-Agent对话体验与智能编排改造路线图.md
git commit -m "test: verify semantic portfolio answer composition"
```

---

## Final Review Checklist

- [ ] 单主体 Fact Lookup 使用 Composer；其他 TaskMode 未进入 Composer。
- [ ] Composer 的正文没有读取 Passage 检索文本或用户原文。
- [ ] PostgreSQL 未用默认值伪造历史 Claim 语义。
- [ ] 一个 Plan 中每个 Section Type 最多一次。
- [ ] Summary 只在 Overview 出现。
- [ ] 缺口 Boundary 不带虚构 Evidence。
- [ ] typed v2 Block 为唯一生产正文契约。
- [ ] ConversationThread 与 Evidence Desk 不再读取 raw Blocks。
- [ ] Eval Shape 不保存正文、问题或 ID。
- [ ] Comparison、Recommendation、澄清、Contract 失败回归测试通过。
- [ ] 所有门禁结果与文档声明一致。
