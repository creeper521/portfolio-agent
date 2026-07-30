# PostgreSQL / pgvector 资产包组合后端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有不可变文件 Bundle 兼容路径的前提下，引入 PostgreSQL/pgvector 公开快照、可解释的跨 Project/Case 组合推荐、私有 Markdown 增量导入及可量化的迁移验证。

**Architecture:** 使用 Spring JDBC + Flyway 实现两个逻辑安全域；运行时通过 `PublicPortfolioRepository` 继续消费完整快照，并新增独立的 `selection` 深模块。候选召回和组合选择通过小型端口隔离，V1 使用 FTS/vector RRF + 穷举策略，文件实现保留为默认安全回退。

**Tech Stack:** Java 21、Spring Boot 3.5.3、Spring JDBC、Flyway、PostgreSQL、pgvector、JUnit 5、Testcontainers

## Global Constraints

- 生产和测试 Java 禁止 `var`、`record` 和 Lombok。
- 公网运行时不得读取私有 Markdown、治理库、草稿或未批准 Evidence。
- 不记录访客问题正文，不暴露本地路径、凭据、内部主机或原始证据。
- 数据库能力默认关闭；未配置时现有文件 Bundle 行为保持不变。
- Release 查询必须固定单一 `release_id`。
- LLM 不能决定最终选择。
- 不修改 `frontend/`。
- 不清理、暂存或提交用户已有 Git 改动。

---

## File Structure

新增或修改的责任边界：

```text
backend/src/main/resources/db/public/
  V1__public_release_schema.sql         公开 Release、主体、证据和检索表
backend/src/main/resources/db/governance/
  V1__governance_ingestion_schema.sql   私有源文档、revision、chunk 和运行记录

backend/src/main/java/com/portfolio/agent/portfolio/repository/postgres/
  PostgresPublicPortfolioRepository.java  从一个 active release 重建现有快照
  PublicPortfolioDatabaseConfiguration.java 数据源、Flyway、仓储选择

backend/src/main/java/com/portfolio/agent/selection/
  domain/*                               目标、候选、能力、评分和结果
  gateway/CandidateRetrievalPort.java    候选召回端口
  service/SelectionStrategy.java         组合策略接缝
  service/TopKSelectionStrategy.java     基准/降级策略
  service/ExhaustiveSelectionStrategy.java V1 穷举策略
  service/PortfolioSelectionService.java 编排、约束和不足处理
  adapter/postgres/PostgresHybridCandidateRetriever.java FTS/vector/RRF
  controller/PortfolioSelectionController.java 只读公开 API

backend/src/main/java/com/portfolio/agent/ingestion/
  domain/*                               扫描状态和报告
  service/MarkdownScanService.java       哈希差异与 dry-run
  adapter/postgres/PostgresSourceDocumentRepository.java 私有增量状态
  MarkdownImportCli.java                 显式命令入口

backend/src/main/java/com/portfolio/agent/release/
  PublicBundleDatabaseImporter.java      Bundle → 新 Release
  PublicReleaseActivationService.java    校验并原子切换 active_release

backend/src/test/java/...                 对应单元与数据库集成测试
backend/src/test/resources/retrieval-benchmark/
  portfolio-selection-cases.json         组合评测冻结样例
```

---

### Task 1: 固化依赖、配置和数据库迁移边界

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-prod.yml`
- Create: `backend/src/main/resources/db/public/V1__public_release_schema.sql`
- Create: `backend/src/main/resources/db/governance/V1__governance_ingestion_schema.sql`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PublicSchemaMigrationTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/ingestion/adapter/postgres/GovernanceSchemaMigrationTest.java`

**Interfaces:**
- Produces: public datasource properties under `portfolio.database.public`
- Produces: governance datasource properties under `portfolio.database.governance`
- Produces: pgvector column `retrieval_document.embedding vector(512)`

- [ ] **Step 1: 写迁移失败测试**

```java
assertThat(tableNames).contains(
        "content_release",
        "portfolio_subject",
        "claim",
        "evidence",
        "retrieval_document",
        "active_release");
assertThat(governanceTableNames).contains(
        "source_document",
        "source_revision",
        "source_chunk",
        "import_run");
```

- [ ] **Step 2: 运行目标测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PublicSchemaMigrationTest,GovernanceSchemaMigrationTest test`
Expected: FAIL，因为依赖、迁移和表尚不存在。

- [ ] **Step 3: 加入 JDBC/Flyway/PostgreSQL/Testcontainers 并实现迁移**

依赖限定为：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
```

迁移必须启用 `CREATE EXTENSION IF NOT EXISTS vector`，并对所有公开实体建立 `(release_id, stable_id)` 唯一约束。

- [ ] **Step 4: 验证 GREEN 与默认关闭**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PublicSchemaMigrationTest,GovernanceSchemaMigrationTest test`
Expected: PASS。未设置数据库 URL 时应用上下文仍使用文件仓储。

### Task 2: Bundle 到公开数据库的无损导入与激活

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/PublicBundleDatabaseImporter.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/PublicReleaseActivationService.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/PublicBundleDatabaseImportCli.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/PublicBundleDatabaseImporterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/PublicReleaseActivationServiceTest.java`

**Interfaces:**
- Consumes: `RuntimeContentSnapshot`, canonical bundle hash and JDBC transaction manager
- Produces: `ImportReleaseResult importRelease(RuntimeContentSnapshot snapshot)`
- Produces: `void activateVerifiedRelease(String releaseVersion)`

- [ ] **Step 1: 写失败测试覆盖数量、关系、审批门和失败不切换**

```java
assertThat(result.projectCount()).isEqualTo(5);
assertThat(result.caseCount()).isEqualTo(49);
assertThat(result.claimCount()).isEqualTo(79);
assertThat(activeReleaseRepository.currentVersion()).contains(previousVersion);
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PublicBundleDatabaseImporterTest,PublicReleaseActivationServiceTest test`
Expected: FAIL，因为导入器和激活服务不存在。

- [ ] **Step 3: 实现单事务导入和单独原子激活**

导入器只接受已通过现有 `PublicBundleLoader` 校验的快照。若发现未批准 Evidence、悬空关系、重复稳定 ID 或数量不一致，整个新 Release 回滚。激活服务只接受 `VERIFIED` Release。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PublicBundleDatabaseImporterTest,PublicReleaseActivationServiceTest test`
Expected: PASS，失败导入和失败激活均不改变旧活动版本。

### Task 3: PostgreSQL 公开快照仓储与文件兼容回退

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/repository/postgres/PostgresPublicPortfolioRepository.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/repository/postgres/PublicPortfolioDatabaseProperties.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/repository/postgres/PublicPortfolioDatabaseConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PostgresPublicPortfolioRepositoryTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/postgres/PublicPortfolioRepositoryParityTest.java`

**Interfaces:**
- Implements: `PublicPortfolioRepository`
- Produces: one `RuntimeContentSnapshot` reconstructed from exactly one active `release_id`

- [ ] **Step 1: 写仓储奇偶性失败测试**

```java
RuntimeContentSnapshot fileSnapshot = fileRepository.getSnapshot();
RuntimeContentSnapshot databaseSnapshot = databaseRepository.getSnapshot();
assertThat(databaseSnapshot.portfolio()).usingRecursiveComparison()
        .isEqualTo(fileSnapshot.portfolio());
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PostgresPublicPortfolioRepositoryTest,PublicPortfolioRepositoryParityTest test`
Expected: FAIL，因为 PostgreSQL 仓储不存在。

- [ ] **Step 3: 实现显式映射和条件配置**

一次读取首先锁定 active release ID，之后所有 SQL 都带相同 `release_id`。`portfolio.database.public.enabled=false` 时仍装配 `JsonPublicPortfolioRepository`。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PostgresPublicPortfolioRepositoryTest,PublicPortfolioRepositoryParityTest test`
Expected: PASS，数据库与文件快照的公开语义一致。

### Task 4: 组合推荐领域模型与确定性策略

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/selection/domain/PortfolioSubjectKind.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/domain/SelectionTarget.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/domain/SelectionCandidate.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/domain/SelectionScore.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/domain/PortfolioSelection.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/service/SelectionStrategy.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/service/TopKSelectionStrategy.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/service/ExhaustiveSelectionStrategy.java`
- Test: `backend/src/test/java/com/portfolio/agent/selection/service/ExhaustiveSelectionStrategyTest.java`

**Interfaces:**
- Produces: `PortfolioSelection select(SelectionTarget target, List<SelectionCandidate> candidates)`
- Produces: deterministic `policyVersion()`

- [ ] **Step 1: 写失败测试覆盖互补、冗余、稳定 tie-break 和不足**

```java
PortfolioSelection result = strategy.select(target, candidates);
assertThat(result.subjectIds()).containsExactly("CASE-02", "PROJECT-01", "PROJECT-04");
assertThat(result.score().capabilityCoverage()).isGreaterThan(result.score().redundancyPenalty());
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ExhaustiveSelectionStrategyTest test`
Expected: FAIL，因为策略尚不存在。

- [ ] **Step 3: 实现组合枚举和版本化评分**

枚举候选的 2–5 项组合，按目标匹配、能力覆盖、证据质量、多样性、冗余和冲突计算不可变 `SelectionScore`；总分相同按排序后的稳定 ID 串升序决定。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ExhaustiveSelectionStrategyTest test`
Expected: PASS，相同输入重复执行返回字节级一致的主体顺序和评分。

### Task 5: PostgreSQL FTS/pgvector 混合候选召回

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/selection/gateway/CandidateRetrievalPort.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/adapter/postgres/PostgresHybridCandidateRetriever.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/service/SelectionReciprocalRankFusion.java`
- Test: `backend/src/test/java/com/portfolio/agent/selection/adapter/postgres/PostgresHybridCandidateRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/selection/service/SelectionReciprocalRankFusionTest.java`

**Interfaces:**
- Consumes: `SelectionTarget`, active `release_id`, query embedding
- Produces: `List<SelectionCandidate> retrieve(SelectionTarget target, int limit)`

- [ ] **Step 1: 写失败测试覆盖 Top 12、Release 隔离和 FTS-only 降级**

```java
List<SelectionCandidate> candidates = retriever.retrieve(target, 12);
assertThat(candidates).hasSizeLessThanOrEqualTo(12);
assertThat(candidates).allMatch(candidate -> candidate.releaseId().equals(activeReleaseId));
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PostgresHybridCandidateRetrieverTest,SelectionReciprocalRankFusionTest test`
Expected: FAIL，因为召回器不存在。

- [ ] **Step 3: 实现结构化过滤、FTS、vector 和 RRF**

向量使用余弦距离精确搜索，不建立近似索引。vector SQL 失败时仅在目标具有结构化能力约束时执行 FTS-only，并在结果中记录实际模式。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PostgresHybridCandidateRetrieverTest,SelectionReciprocalRankFusionTest test`
Expected: PASS，无跨 Release 结果，降级模式显式可见。

### Task 6: 公开组合服务和只读 API

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/selection/service/PortfolioSelectionService.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/controller/PortfolioSelectionController.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/dto/PortfolioSelectionRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/dto/PortfolioSelectionResponse.java`
- Create: `backend/src/main/java/com/portfolio/agent/selection/mapper/PortfolioSelectionResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/selection/controller/PortfolioSelectionControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/selection/service/PortfolioSelectionServiceTest.java`

**Interfaces:**
- Produces: `POST /api/portfolio-selections`
- Accepts: career track、audience role、capability codes、2–5 数量和可选目标
- Returns: `READY`、`INSUFFICIENT` 或 `TEMPORARILY_UNAVAILABLE`

- [ ] **Step 1: 写 API 失败测试**

```java
mockMvc.perform(post("/api/portfolio-selections")
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.releaseVersion").isNotEmpty())
        .andExpect(jsonPath("$.items.length()").value(3));
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioSelectionControllerTest,PortfolioSelectionServiceTest test`
Expected: FAIL，因为公开 API 尚不存在。

- [ ] **Step 3: 实现校验、服务编排、DTO 映射和确定性说明**

自然语言目标只在内存中处理，不写数据库、不写日志。证据 DTO 只允许已批准 Evidence。请求数量超出 2–5 返回现有统一错误结构。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioSelectionControllerTest,PortfolioSelectionServiceTest test`
Expected: PASS，包括不足、降级、未知能力和证据过滤场景。

### Task 7: 私有 Markdown 扫描与增量导入 CLI

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/ingestion/domain/SourceDocumentStatus.java`
- Create: `backend/src/main/java/com/portfolio/agent/ingestion/domain/MarkdownScanEntry.java`
- Create: `backend/src/main/java/com/portfolio/agent/ingestion/domain/MarkdownScanReport.java`
- Create: `backend/src/main/java/com/portfolio/agent/ingestion/service/MarkdownScanService.java`
- Create: `backend/src/main/java/com/portfolio/agent/ingestion/adapter/postgres/PostgresSourceDocumentRepository.java`
- Create: `backend/src/main/java/com/portfolio/agent/ingestion/MarkdownImportCli.java`
- Test: `backend/src/test/java/com/portfolio/agent/ingestion/service/MarkdownScanServiceTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/ingestion/MarkdownImportCliTest.java`

**Interfaces:**
- Produces: `MarkdownScanReport scan(Path root, boolean dryRun)`
- CLI: `scan --root <path> --dry-run`
- CLI: `import --root <path> --run-id <scanRunId>`

- [ ] **Step 1: 写失败测试覆盖哈希状态和路径保护**

```java
assertThat(report.entries()).extracting(MarkdownScanEntry::status)
        .contains(SourceDocumentStatus.ADDED, SourceDocumentStatus.CHANGED);
assertThat(report.entries()).noneMatch(entry -> entry.relativePath().startsWith(".."));
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=MarkdownScanServiceTest,MarkdownImportCliTest test`
Expected: FAIL，因为扫描与 CLI 尚不存在。

- [ ] **Step 3: 实现显式扫描、预览和文档级事务**

只处理 `.md` 普通文件，解析规范化相对路径，拒绝符号链接逃逸；保存 SHA-256、revision 和分块哈希。`MISSING` 只标记，解析失败保留旧 revision。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn.cmd -f backend/pom.xml -Dtest=MarkdownScanServiceTest,MarkdownImportCliTest test`
Expected: PASS，重复扫描为 UNCHANGED，单文档失败不影响其他文档。

### Task 8: 组合评测和迁移安全门

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/PortfolioSelectionBenchmarkCase.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/PortfolioSelectionBenchmarkEvaluator.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/PortfolioSelectionBenchmarkCli.java`
- Create: `backend/src/test/resources/retrieval-benchmark/portfolio-selection-cases.json`
- Test: `backend/src/test/java/com/portfolio/agent/release/benchmark/PortfolioSelectionBenchmarkEvaluatorTest.java`
- Modify: `scripts/privacy-check.ps1`
- Modify: `docs/08-current-implementation-status.md`
- Modify: `docs/09-portfolio-asset-library-status.md`

**Interfaces:**
- Produces: R0–R4 可比较报告
- Enforces: migration integrity and zero-leak gates

- [ ] **Step 1: 写评测失败测试**

```java
assertThat(report.migrationIntegrity()).isEqualByComparingTo("1.0000");
assertThat(report.unsupportedRecommendationCount()).isZero();
assertThat(report.crossReleaseMixCount()).isZero();
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=PortfolioSelectionBenchmarkEvaluatorTest test`
Expected: FAIL，因为评测器和冻结样例不存在。

- [ ] **Step 3: 实现指标、消融路由和文档基线修正**

样例允许多个可接受集合并携带能力约束。报告输出 Recall@12、MRR、nDCG、覆盖、冗余、证据有效率和 p50/p95。状态文档同步到实际 schema `4.0`、5 Project、49 Case、79 Claim 和当前 content version。

- [ ] **Step 4: 运行完整后端验证**

Run: `mvn.cmd -f backend/pom.xml test`
Expected: BUILD SUCCESS。

- [ ] **Step 5: 运行隐私和打包验证**

Run: `powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1`
Expected: PASS。

Run: `mvn.cmd -f backend/pom.xml -DskipFrontend=true package`
Expected: BUILD SUCCESS，公开产物不包含私有 Markdown、治理库配置或私有向量。

## Self-Review

- Spec coverage：数据库安全域、Release、增量导入、混合召回、穷举选择、降级、评测和前端边界均有对应任务。
- Placeholder scan：无 `TBD`、`TODO` 或未定义“适当处理”步骤。
- Type consistency：`SelectionTarget` → `CandidateRetrievalPort` → `SelectionStrategy` → `PortfolioSelectionService` → API 的命名在所有任务中一致。
- Scope：前端文件不进入实施；管理 UI 延后；近似向量索引、知识图谱和 LLM 排名不在 V1。
- Execution choice：用户已明确由当前 AI 完成后端，因此采用 Inline Execution，并在每个任务执行 RED/GREEN/REFACTOR。
