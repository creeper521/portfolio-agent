# 公开作品集 API 单资源收敛 Implementation Plan
<!-- DOCUMENT_STATUS: ACTIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **状态：** 用户已批准执行，作为公开作品集 API Replacement Slice 的活动实施计划
> **设计权威：** [公开作品集 API 单资源收敛设计](../specs/2026-08-21-portfolio-public-api-convergence-design.md)

**Goal:** 将公开作品集读取面破坏式收敛为唯一 `GET /api/portfolio`，将客户端诊断切换为 `/api/client-diagnostics`，删除所有活动 `/api/v1` 路由与无消费者合同，同时保持 14 字段快照、隐私过滤、`agentAvailability` 和 `no-store` 语义不变。

**Architecture:** Backend 继续由 `PortfolioService.getPublicContent()` 读取一个原子公开发布快照，`PortfolioResponseMapper` 将其投影为 `PortfolioSnapshotResponse`，单一 `PortfolioController` 暴露根资源。Frontend 继续通过 `PublicContentRepository` seam 缓存一次聚合请求；Agent 四条资源完全不变。Backend、Frontend、诊断、脚本、E2E 和当前文档作为一个不可拆分部署的 Replacement Slice 完成，不保留兼容路由。

**Tech Stack:** Java 21、Spring Boot、MockMvc、JUnit 5、AssertJ、Vue 3、TypeScript、Vitest、PowerShell、Playwright、Maven。

## Global Constraints

- 最终活动 HTTP 面固定为 `GET /api/portfolio`、`POST /api/client-diagnostics` 与现有四条 `/api/agent` 资源。
- `/api/portfolio` 必须保留 `contentVersion`、`runtimeBundleHash`、`publishedAt`、`owner`、`collections`、`projects`、`cases`、`claims`、`claimEvidenceLinks`、`evidence`、`timeline`、`caseSlugsByEvidenceId`、`questionPresets`、`agentAvailability` 共 14 个字段，字段名、类型和语义不变。
- `/api/portfolio` 无认证且必须返回 `Cache-Control: no-store`；只有 `APPROVED` 且非 raw-public 的 Evidence 可以投影。
- `agentAvailability` 缺失或损坏时 Frontend 继续 fail-closed；不新增第五条 Agent 资源。
- 不增加 `/api/portfolio/projects/*`、`/api/portfolio/cases/*`、分页、缓存协商、CORS、认证或动态发布。
- 不保留 `/api/v1` redirect、转发、别名、feature flag、payload switch 或 fallback；回退只使用 Git/JAR/整体部署版本。
- 四条 `/api/agent` 资源、Command、Goal、Plan、Execution、PublicAgentTurn、State、预算和 Provider 合同不变。
- Java 生产与测试代码禁止 `var` 和 Lombok；保持 Java 21。
- 保留所有用户已有改动；禁止 reset、restore、覆盖、stage、commit 或 push，除非用户另行明确授权。
- 下列 Task 可以形成审查检查点，但中间状态不可部署；只有 Task 1～6 全部 Exit Gate 通过后才完成 Replacement Slice。
- 当前环境未安装 `rtk`，本 Plan 中使用仓库允许的原生命令。

---

## File Structure Map

### Backend 目标权威

- `backend/src/main/java/com/portfolio/agent/portfolio/controller/PortfolioController.java`：唯一公开作品集 HTTP Adapter。
- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PortfolioSnapshotResponse.java`：14 字段公开快照 DTO。
- `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`：`PublicContent` 到公开快照及嵌套 Project/Case DTO 的唯一 Mapper。
- `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`：只保留完整快照构造所需公开读取入口。

### Backend 退休与删除

- `backend/src/main/java/com/portfolio/agent/portfolio/controller/PublicContentController.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PortfolioHomeResponse.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/ProjectSummaryResponse.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PortfolioOverview.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/exception/ProjectNotFoundException.java`
- `backend/src/main/java/com/portfolio/agent/portfolio/exception/CaseNotFoundException.java`
- `backend/src/main/java/com/portfolio/agent/common/exception/PublicResourceErrorCode.java`

`CaseSummaryResponse`、`ProjectDetailResponse`、`CaseDetailResponse`、`ProjectDetails` 与 `CaseDetails` 保留，因为完整快照仍使用它们。

### Frontend

- `frontend/src/features/portfolio/api/portfolioApi.ts`：保留通用 request transport，公开快照方法更名为 `getPortfolioSnapshot()`。
- `frontend/src/features/public-content/repository/apiPublicContentRepository.ts`：生产 Repository Adapter 默认 loader 改为 `getPortfolioSnapshot`。
- `frontend/src/features/portfolio/model/portfolioTypes.ts`：删除只服务退休 HTTP 摘要/详情的 interface，只保留仍被 `projectLabels.ts` 消费的状态类型。
- `frontend/src/shared/diagnostics/diagnosticTransport.ts`：切换 `/api/client-diagnostics`。

### Scripts、治理与文档

- `scripts/start-local.ps1`、`scripts/test-fixtures/start-local-fake-server.ps1`、`scripts/run-jar-e2e.ps1`：切换新路径并从聚合内断言 Case。
- `scripts/public-api-surface-check.ps1` 与 `.test.ps1`：新增活动源码 `/api/v1` 零引用门。
- `scripts/verify-release.ps1`：注册零引用门测试与检查。
- `README.md`、`docs/06-公开内容发布运行手册.md`、`docs/08-当前实现状态.md`、`docs/11-项目演进日志.md`、`docs/agent-architecture-status.json`：同步真实目标状态和证据。

---

### Task 1: 建立唯一 Portfolio 根资源

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/controller/PortfolioController.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/SpaForwardControllerTest.java`

**Interfaces:**
- Consumes: `PortfolioService.getPublicContent(): PublicContent`、`PortfolioResponseMapper.toPublicContentResponse(PublicContent, AgentAvailabilityResponse): PublicContentResponse`。
- Produces: `GET /api/portfolio`，HTTP 200、`Cache-Control: no-store`、当前完整聚合 JSON；Task 2 会把临时 DTO/Mapper 名改成最终名。

- [ ] **Step 1: 先把集成合同测试切到目标根资源**

将 `PortfolioControllerTest` 中摘要、独立 Project 和旧聚合测试收敛为一个根资源测试。除现有内容断言外，用 `ObjectMapper` 锁定顶层字段集合：

```java
@Autowired private MockMvc mockMvc;
@Autowired private ObjectMapper objectMapper;

@Test
void returnsAtomicPortfolioSnapshotWithFrozenTopLevelContract() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/portfolio"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.contentVersion").value("2026-08-05.1"))
            .andExpect(jsonPath("$.runtimeBundleHash")
                    .value(org.hamcrest.Matchers.startsWith("sha256:")))
            .andExpect(jsonPath("$.projects.length()").value(6))
            .andExpect(jsonPath("$.cases.length()").value(52))
            .andExpect(jsonPath("$.evidence.length()").value(63))
            .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    java.util.Set<String> names = new java.util.LinkedHashSet<>();
    root.fieldNames().forEachRemaining(names::add);
    assertThat(names).containsExactlyInAnyOrder(
            "contentVersion", "runtimeBundleHash", "publishedAt", "owner",
            "collections", "projects", "cases", "claims", "claimEvidenceLinks",
            "evidence", "timeline", "caseSlugsByEvidenceId", "questionPresets",
            "agentAvailability");
}
```

保留原 `returnsCompleteReviewedPublicContent()` 中对 `questionPresets`、Claim、Evidence、Timeline、featured Case 和反向索引的正面断言，但请求路径改为 `/api/portfolio`。删除只验证退休摘要/Project 详情的测试。

- [ ] **Step 2: 运行测试并确认目标路由尚不存在**

Run:

```powershell
mvn.cmd -f backend/pom.xml "-Dtest=PortfolioControllerTest" test
```

Expected: FAIL，`GET /api/portfolio` 返回 404。

- [ ] **Step 3: 将 `PortfolioController` 改为根资源 Adapter**

将旧四 Handler 替换为以下单 Handler。Task 1 暂时沿用旧 DTO/Mapper 名，避免在同一步混入类型清理：

```java
@RestController
@RequestMapping("/api/portfolio")
public final class PortfolioController {
    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;
    private final AgentAvailabilityResponse agentAvailability;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper,
            @Value("${portfolio.conversation-context.mode:DISABLED}") String contextMode,
            @Value("${portfolio.model-operations.turn-interpretation.mode:DISABLED}")
            String turnInterpretationMode,
            ConversationProviderAccess providerAccess) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
        this.agentAvailability = "DISABLED".equalsIgnoreCase(contextMode)
                ? AgentAvailabilityResponse.unavailable()
                : AgentAvailabilityResponse.available(
                        "ENABLED".equalsIgnoreCase(turnInterpretationMode)
                                && providerAccess.isAllowed()
                                ? AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE
                                : AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
    }

    @GetMapping
    public ResponseEntity<PublicContentResponse> getPortfolioSnapshot() {
        PublicContentResponse response = responseMapper.toPublicContentResponse(
                portfolioService.getPublicContent(), agentAvailability);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
```

将 `SpaForwardControllerTest.doesNotCaptureApiRoutes()` 的请求改为 `/api/portfolio`，继续断言 JSON 而非 `index.html`。

- [ ] **Step 4: 运行目标 Controller 测试**

Run:

```powershell
mvn.cmd -f backend/pom.xml "-Dtest=PortfolioControllerTest,SpaForwardControllerTest" test
```

Expected: PASS。此检查点仍可能保留 `/api/v1/public-content`，不可部署。

- [ ] **Step 5: 可选提交检查点（仅在用户另行授权时）**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/controller/PortfolioController.java backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java backend/src/test/java/com/portfolio/agent/common/web/SpaForwardControllerTest.java
git commit -m "refactor(portfolio): 建立公开快照根资源"
```

未获得授权时跳过，不 stage。

---

### Task 2: 完成 Backend DTO 命名与退休代码删除

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PortfolioSnapshotResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/controller/PublicContentController.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PortfolioHomeResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/ProjectSummaryResponse.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PortfolioOverview.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/exception/ProjectNotFoundException.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/exception/CaseNotFoundException.java`
- Delete: `backend/src/main/java/com/portfolio/agent/common/exception/PublicResourceErrorCode.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/controller/PortfolioController.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`
- Delete: `backend/src/test/java/com/portfolio/agent/portfolio/controller/CaseControllerTest.java`
- Delete: `backend/src/test/java/com/portfolio/agent/common/exception/ApiErrorCodeContractTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerAvailabilityTest.java`
- Delete: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PublicContentControllerAvailabilityTest.java`

**Interfaces:**
- Consumes: Task 1 的 `GET /api/portfolio` 和 `PortfolioService.getPublicContent()`。
- Produces: `PortfolioSnapshotResponse`；`PortfolioResponseMapper.toPortfolioSnapshotResponse(PublicContent)` 与 `toPortfolioSnapshotResponse(PublicContent, AgentAvailabilityResponse)`；Backend 不再注册任何作品集 `/api/v1` Handler。

- [ ] **Step 1: 先把 Mapper 与 availability 测试改成最终类型名**

在 `PortfolioResponseMapperTest` 中做精确替换：

```java
PortfolioSnapshotResponse response =
        mapper.toPortfolioSnapshotResponse(publicContentWithThreeCases());
```

将 `PublicContentControllerAvailabilityTest` 替换为 `PortfolioControllerAvailabilityTest`，构造 `PortfolioController` 并验证：

```java
PortfolioSnapshotResponse response = mock(PortfolioSnapshotResponse.class);
when(mapper.toPortfolioSnapshotResponse(
        eq(content), any(AgentAvailabilityResponse.class))).thenReturn(response);

new PortfolioController(
        service, mapper, "POSTGRESQL", "ENABLED",
        new ConversationProviderAccess(true)).getPortfolioSnapshot();

verify(mapper).toPortfolioSnapshotResponse(eq(content), availability.capture());
assertThat(availability.getValue().getStatus())
        .isEqualTo(AgentAvailabilityResponse.Status.AVAILABLE);
```

保留原四种 availability 场景：State disabled、持久 State + Provider allowed、自由文本关闭、Provider privacy gate 禁止。

- [ ] **Step 2: 运行测试并确认最终类型尚不存在**

Run:

```powershell
mvn.cmd -f backend/pom.xml "-Dtest=PortfolioResponseMapperTest,PortfolioControllerAvailabilityTest" test
```

Expected: FAIL at test compilation，找不到 `PortfolioSnapshotResponse` 或 `toPortfolioSnapshotResponse`。

- [ ] **Step 3: 将 DTO 和 Mapper 精确重命名**

从 `PublicContentResponse.java` 原样迁移 14 字段实现到 `PortfolioSnapshotResponse.java`，只进行下列标识符替换：

```text
PublicContentResponse                  -> PortfolioSnapshotResponse
public PublicContentResponse(          -> public PortfolioSnapshotResponse(
instanceof PublicContentResponse that  -> instanceof PortfolioSnapshotResponse that
"PublicContentResponse{"              -> "PortfolioSnapshotResponse{"
```

Mapper 最终公开方法固定为：

```java
public PortfolioSnapshotResponse toPortfolioSnapshotResponse(PublicContent content) {
    return toPortfolioSnapshotResponse(content, AgentAvailabilityResponse.available());
}

public PortfolioSnapshotResponse toPortfolioSnapshotResponse(
        PublicContent content,
        AgentAvailabilityResponse agentAvailability) {
    Map<String, String> projectSlugsById = projectSlugsById(content);
    Map<String, String> caseSlugsById = caseSlugsById(content);
    return new PortfolioSnapshotResponse(
            content.getContentVersion(),
            content.getRuntimeBundleHash(),
            content.getPublishedAt(),
            OwnerResponse.from(content.getOwner()),
            content.getCollections().stream()
                    .map(CaseCollectionResponse::from).toList(),
            content.getProjects().stream().map(this::toProjectResponse).toList(),
            content.getCases().stream().map(this::toCaseResponse).toList(),
            content.getClaims().stream().map(ClaimResponse::new).toList(),
            content.getClaimEvidenceLinks().stream()
                    .map(ClaimEvidenceLinkResponse::new).toList(),
            content.getEvidence().stream()
                    .map(item -> EvidenceResponse.from(
                            item,
                            content.getProjectSlugsByEvidenceId()
                                    .getOrDefault(item.getId(), List.of()),
                            content.getClaimIdsByEvidenceId()
                                    .getOrDefault(item.getId(), List.of())))
                    .toList(),
            content.getTimeline().stream()
                    .map(event -> TimelineEventResponse.from(
                            event,
                            resolveSlugs(event.getProjectIds(), projectSlugsById),
                            resolveSlugs(event.getCaseIds(), caseSlugsById)))
                    .toList(),
            content.getCaseSlugsByEvidenceId(),
            content.getQuestionPresets().stream()
                    .filter(question -> question.getContractStatus()
                            == PresetContractStatus.ACTIVE)
                    .map(question -> QuestionPresetResponse.from(
                            question,
                            firstProjectSlug(
                                    question.getProjectIds(),
                                    content.getProjects(),
                                    projectSlugsById),
                            resolveSlugs(question.getCaseIds(), caseSlugsById)))
                    .toList(),
            agentAvailability);
}
```

补充 `PresetContractStatus` import，删除旧类 import。Controller 返回类型同步改成 `ResponseEntity<PortfolioSnapshotResponse>`。

- [ ] **Step 4: 删除退休 Backend 面和仅服务它的代码**

删除 `PublicContentController`、`PortfolioHomeResponse`、`ProjectSummaryResponse`、`PortfolioOverview`、两个 Portfolio not-found Exception 与 `PublicResourceErrorCode`。从 `PortfolioService` 删除：

```text
getPortfolio()
getProject(String)
getCases()
getCase(String)
findProject(RuntimeContentSnapshot, String)
findCase(RuntimeContentSnapshot, String)
```

保留 `getPublicContent()`、`toProjectDetails()` 和 `toCaseDetails()`。从 Mapper 删除 `toPortfolioResponse()` 与 `toCaseResponses()`；保留 `toProjectResponse()`、`toCaseResponse()` 与 private `toCaseSummaryResponse()`。

同步删除 `CaseControllerTest`、`ApiErrorCodeContractTest`，并从 `PortfolioServiceTest` 删除只覆盖退休方法的测试；保留 `getPublicContentReadsOneSnapshotAndBuildsReverseEvidenceLinks`、Evidence 过滤、Case 索引与不可变性测试。

- [ ] **Step 5: 运行 Backend Portfolio 聚焦测试与零引用检查**

Run:

```powershell
mvn.cmd -f backend/pom.xml "-Dtest=PortfolioControllerTest,PortfolioControllerAvailabilityTest,PortfolioResponseMapperTest,PortfolioServiceTest,PublicPortfolioDatabaseConfigurationTest" test
rg -n "PublicContentController|PublicContentResponse|PortfolioHomeResponse|PortfolioOverview|ProjectSummaryResponse|ProjectNotFoundException|CaseNotFoundException|PublicResourceErrorCode|toPortfolioResponse|toCaseResponses" backend/src
```

Expected: Maven PASS；`rg` 无输出。

- [ ] **Step 6: 可选提交检查点（仅在用户另行授权时）**

```powershell
git add backend/src/main backend/src/test
git commit -m "refactor(portfolio): 删除旧版本读取面"
```

未获得授权时跳过，不 stage。

---

### Task 3: 切换 Frontend 单快照 Adapter

**Files:**
- Modify: `frontend/src/features/portfolio/api/portfolioApi.ts`
- Modify: `frontend/src/features/portfolio/api/portfolioApi.test.ts`
- Modify: `frontend/src/features/portfolio/model/portfolioTypes.ts`
- Modify: `frontend/src/features/public-content/repository/apiPublicContentRepository.ts`
- Modify: `frontend/src/features/public-content/repository/apiPublicContentRepository.test.ts`
- Modify: `frontend/src/pages/HomePage.test.ts`

**Interfaces:**
- Consumes: Task 2 的 `GET /api/portfolio` 14 字段 JSON。
- Produces: `getPortfolioSnapshot(): Promise<PublicPortfolio>`；`ApiPublicContentRepository` 继续只请求一次并在内存派生所有页面数据。

- [ ] **Step 1: 先把 API 测试冻结到新路径和新函数名**

删除摘要与 Project 独立请求测试，将 availability 三个测试改为调用：

```typescript
import { getPortfolioSnapshot } from './portfolioApi'

it('loads one fail-closed portfolio snapshot from the unversioned resource', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    contentVersion: 'test-v1',
    agentAvailability: {
      status: 'AVAILABLE',
      freeTextSemanticRouting: 'AVAILABLE',
    },
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', fetchMock)

  await expect(getPortfolioSnapshot()).resolves.toMatchObject({
    agentAvailability: {
      status: 'AVAILABLE',
      freeTextSemanticRouting: 'AVAILABLE',
    },
  })
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/portfolio',
    expect.objectContaining({ method: 'GET' }),
  )
})
```

`apiPublicContentRepository.test.ts` 保留并继续断言五个 selector 并发时 loader 只调用一次。`HomePage.test.ts` 的非泄漏断言改为不渲染 `/api/portfolio` 文本。

- [ ] **Step 2: 运行 Frontend 聚焦测试并确认新导出尚不存在**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/portfolio/api/portfolioApi.test.ts src/features/public-content/repository/apiPublicContentRepository.test.ts src/pages/HomePage.test.ts
```

Expected: FAIL at TypeScript/Vitest import，`getPortfolioSnapshot` 尚未导出。

- [ ] **Step 3: 实现唯一快照函数并删除旧传输接口**

在 `portfolioApi.ts` 删除 `getPortfolio()`、`getProject()` 和 `RequestOperation.PROJECT`，将聚合方法改为：

```typescript
export async function getPortfolioSnapshot(): Promise<PublicPortfolio> {
  const payload = await request<Record<string, unknown>>(
    '/api/portfolio',
    { method: 'GET' },
    { operation: RequestOperation.PUBLIC_CONTENT },
  )
  const availability = payload.agentAvailability
  const available = typeof availability === 'object'
    && availability !== null
    && !Array.isArray(availability)
    && (availability as Record<string, unknown>).status === 'AVAILABLE'
  const freeTextSemanticRouting = available
    && (availability as Record<string, unknown>).freeTextSemanticRouting === 'AVAILABLE'
      ? 'AVAILABLE'
      : 'DISABLED'
  return {
    ...payload,
    agentAvailability: {
      status: available ? 'AVAILABLE' : 'UNAVAILABLE',
      freeTextSemanticRouting,
    },
  } as unknown as PublicPortfolio
}
```

`diagnosticEventNameFor()` 与 `isRequestOperation()` 删除 `PROJECT` 分支。`apiPublicContentRepository.ts` 改为：

```typescript
import { getPortfolioSnapshot } from '../../portfolio/api/portfolioApi'

constructor(loader: () => Promise<PublicPortfolio> = getPortfolioSnapshot) {
  this.loader = loader
}
```

从 `portfolioTypes.ts` 删除无消费者的 `OwnerProfile`、`ProjectSummary`、`PortfolioHome`、`Evidence`、`ProjectDetail`，保留 `ProjectStatus` 与 `ContributionType`。

- [ ] **Step 4: 运行 Frontend 聚焦测试和类型检查**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/portfolio/api/portfolioApi.test.ts src/features/public-content/repository/apiPublicContentRepository.test.ts src/features/public-content/composables/usePublicContent.test.ts src/pages/HomePage.test.ts
npm.cmd --prefix frontend run check
```

Expected: PASS；`vue-tsc` 无旧类型引用。

- [ ] **Step 5: 可选提交检查点（仅在用户另行授权时）**

```powershell
git add frontend/src/features/portfolio frontend/src/features/public-content frontend/src/pages/HomePage.test.ts
git commit -m "refactor(frontend): 切换公开快照单资源"
```

未获得授权时跳过，不 stage。

---

### Task 4: 切换客户端诊断并锁定退休路由 404

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsController.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsBodyLimitFilter.java`
- Modify: `backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsControllerTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsBodyLimitFilterTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/common/web/RetiredVersionedApiContractTest.java`
- Modify: `frontend/src/shared/diagnostics/diagnosticTransport.ts`
- Modify: `frontend/src/shared/diagnostics/diagnosticTransport.test.ts`

**Interfaces:**
- Consumes: 现有诊断请求体、body-limit Filter 和安全事件白名单。
- Produces: 唯一 `POST /api/client-diagnostics`；六条退休路径稳定 404 且返回 JSON，不进入 SPA。

- [ ] **Step 1: 先把诊断测试切到新路径**

将两个 Backend 诊断测试文件与 Frontend transport test 中所有活动路径精确替换为：

```text
/api/client-diagnostics
```

保持现有 202、400、413、白名单、批量上限、pagehide keepalive 和不持久化断言不变。

- [ ] **Step 2: 新增集中式退休合同回归**

创建唯一允许包含旧路径的 Java 测试：

```java
@SpringBootTest(classes = PortfolioAgentApplication.class)
@AutoConfigureMockMvc
class RetiredVersionedApiContractTest {
    @Autowired private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/portfolio",
            "/api/v1/projects/sql-audit",
            "/api/v1/cases",
            "/api/v1/cases/multilingual-image-preservation",
            "/api/v1/public-content"
    })
    void retiredGetRoutesReturnJson404InsteadOfSpa(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void retiredDiagnosticsRouteReturnsJson404InsteadOfSpa() throws Exception {
        mockMvc.perform(post("/api/v1/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
```

- [ ] **Step 3: 运行测试并确认诊断新路径尚未注册**

Run:

```powershell
mvn.cmd -f backend/pom.xml "-Dtest=FrontendDiagnosticsControllerTest,FrontendDiagnosticsBodyLimitFilterTest,RetiredVersionedApiContractTest" test
npm.cmd --prefix frontend test -- --run src/shared/diagnostics/diagnosticTransport.test.ts
```

Expected: Backend/Frontend 至少一项 FAIL，因为生产诊断路径仍是 `/api/v1/client-diagnostics`。

- [ ] **Step 4: 原子切换诊断 Controller、Filter 和 Transport**

精确修改：

```java
@RequestMapping("/api/client-diagnostics")
```

```java
private static final String INGEST_PATH = "/api/client-diagnostics";
```

```typescript
const DIAGNOSTIC_ENDPOINT = '/api/client-diagnostics'
```

不得改变 body limit、accepted status、事件清洗、keepalive 或 timeout。

- [ ] **Step 5: 运行诊断、退休路由与 SPA 测试**

Run:

```powershell
mvn.cmd -f backend/pom.xml "-Dtest=FrontendDiagnosticsControllerTest,FrontendDiagnosticsBodyLimitFilterTest,RetiredVersionedApiContractTest,SpaForwardControllerTest" test
npm.cmd --prefix frontend test -- --run src/shared/diagnostics/diagnosticTransport.test.ts
```

Expected: PASS；旧路由均为 JSON 404，新诊断行为不变。

- [ ] **Step 6: 可选提交检查点（仅在用户另行授权时）**

```powershell
git add backend/src/main/java/com/portfolio/agent/common/web backend/src/test/java/com/portfolio/agent/common/web frontend/src/shared/diagnostics
git commit -m "refactor(api): 切换诊断路径并退休版本路由"
```

未获得授权时跳过，不 stage。

---

### Task 5: 迁移启动器、Fake Server 与 Packaged E2E

**Files:**
- Modify: `scripts/start-local.ps1`
- Modify: `scripts/test-fixtures/start-local-fake-server.ps1`
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-agent-behavior-audit.test.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`
- Modify: `backend/src/test/java/com/portfolio/agent/turn/api/AgentTurnClosedContractIntegrationTest.java`

**Interfaces:**
- Consumes: `/api/portfolio`、`/api/client-diagnostics` 与聚合 `cases` 数组。
- Produces: 本地 readiness、Fake Backend、Agent preset 准备和 packaged-JAR Case 正面验收全部使用目标接口。

- [ ] **Step 1: 先给脚本测试增加目标路径源断言**

在 `run-jar-e2e.test.ps1` 增加：

```powershell
$runnerSource = Get-Content -LiteralPath $runner -Raw -Encoding UTF8
Assert-True ($runnerSource -match '/api/portfolio') `
    'Packaged runner must load the unversioned portfolio snapshot.'
Assert-True ($runnerSource -match '/api/client-diagnostics') `
    'Packaged runner must use the unversioned diagnostic endpoint.'
```

在 `run-agent-behavior-audit.test.ps1` 对 Fake Server 增加 `/api/portfolio` 存在断言。不要在这些活动测试中复制 `/api/v1` 字面量；旧路径只由 `RetiredVersionedApiContractTest` 和 Task 6 checker fixture 持有。

- [ ] **Step 2: 运行脚本测试并确认目标路径尚未覆盖完整**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-agent-behavior-audit.test.ps1
```

Expected: FAIL，runner 或 Fake Server 尚未包含全部目标路径。

- [ ] **Step 3: 切换所有活动脚本路径**

进行以下精确迁移：

```text
scripts/start-local.ps1
  /api/v1/public-content -> /api/portfolio

scripts/test-fixtures/start-local-fake-server.ps1
  /api/v1/public-content -> /api/portfolio

scripts/run-jar-e2e.ps1
  /api/v1/public-content    -> /api/portfolio
  /api/v1/client-diagnostics -> /api/client-diagnostics
```

删除独立 Case HTTP 调用，将其替换为聚合内断言：

```powershell
$caseResponse = @($publicContent.cases) | Where-Object {
    [string]$_.slug -eq 'multilingual-image-preservation'
} | Select-Object -First 1
if ($null -eq $caseResponse) {
    throw 'Packaged portfolio snapshot omitted the expected Case.'
}
if (@($caseResponse.evidence).Count -eq 0) {
    throw 'Packaged portfolio snapshot Case returned no public evidence.'
}
Write-Output 'Packaged portfolio snapshot Case smoke passed.'
```

`AgentTurnClosedContractIntegrationTest` 读取 Preset 的准备请求改为 `GET /api/portfolio`。

- [ ] **Step 4: 运行脚本自测和相关 Backend 集成测试**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-agent-behavior-audit.test.ps1
mvn.cmd -f backend/pom.xml "-Dtest=AgentTurnClosedContractIntegrationTest" test
```

Expected: PASS。

- [ ] **Step 5: 可选提交检查点（仅在用户另行授权时）**

```powershell
git add scripts backend/src/test/java/com/portfolio/agent/turn/api/AgentTurnClosedContractIntegrationTest.java
git commit -m "test(release): 迁移公开接口验收链"
```

未获得授权时跳过，不 stage。

---

### Task 6: 建立永久零引用门、同步当前文档并运行完整 Exit Gates

**Files:**
- Create: `scripts/public-api-surface-check.ps1`
- Create: `scripts/public-api-surface-check.test.ps1`
- Modify: `scripts/verify-release.ps1`
- Modify: `README.md`
- Modify: `docs/06-公开内容发布运行手册.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/agent-architecture-status.json`

**Interfaces:**
- Consumes: Tasks 1～5 的最终生产路径与集中退休测试。
- Produces: 可复现的 `/api/v1` 活动引用零门禁、当前文档、机器架构证据和完整发布验证结果。

- [ ] **Step 1: 先写零引用门的正反例测试**

创建临时 fixture 仓库，正例只允许退休合同测试持有旧路径，负例在 Frontend 生产源码写入一个版本化调用。测试核心：

```powershell
$checker = Join-Path $PSScriptRoot 'public-api-surface-check.ps1'

# Positive fixture
Write-Utf8File (Join-Path $fixtureRoot `
    'backend/src/test/java/com/portfolio/agent/common/web/RetiredVersionedApiContractTest.java') `
    'final class RetiredVersionedApiContractTest { String path = "/api/v1/portfolio"; }'
Write-Utf8File (Join-Path $fixtureRoot 'frontend/src/api.ts') `
    "fetch('/api/portfolio')"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -RootPath $fixtureRoot
Assert-True ($LASTEXITCODE -eq 0) 'Approved retirement fixture must pass.'

# Negative fixture
Write-Utf8File (Join-Path $fixtureRoot 'frontend/src/leak.ts') `
    "fetch('/api/v1/public-content')"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -RootPath $fixtureRoot
Assert-True ($LASTEXITCODE -ne 0) 'Active versioned route must fail.'
```

测试必须在 `finally` 删除临时目录。

- [ ] **Step 2: 运行 checker test 并确认脚本尚不存在**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/public-api-surface-check.test.ps1
```

Expected: FAIL，找不到 `public-api-surface-check.ps1`。

- [ ] **Step 3: 实现精确活动引用扫描**

`public-api-surface-check.ps1` 扫描活动代码与当前权威文档：

```text
backend/src/main
backend/src/test
frontend/src
scripts
README.md
AGENTS.md
SECURITY.md
docs/00-文档状态索引.md
docs/04-项目代码约束.md
docs/05-公开发布包契约.md
docs/06-公开内容发布运行手册.md
docs/08-当前实现状态.md
docs/09-作品集资产库状态.md
docs/10-本地PostgreSQL与pgvector运行手册.md
docs/15-Agent 2.0真实交互问题清单与修复边界.md
docs/16-Agent单权威持续收敛范式.md
```

匹配固定模式 `(?i)/api/v1(?:/|\b)`。精确排除：

```text
backend/src/test/java/com/portfolio/agent/common/web/RetiredVersionedApiContractTest.java
scripts/public-api-surface-check.ps1
scripts/public-api-surface-check.test.ps1
```

不存在的可选扫描目录跳过；上述当前权威单文件缺失时检查失败。其他文件每个命中输出：

```text
[public-api-surface-check] RETIRED_PUBLIC_API_REFERENCE <relative-path>:<line>
```

存在命中时 exit 1；否则输出 `Public API surface check passed.` 并 exit 0。不得按目录、扩展名或全局 regex 做更宽豁免。批准 Spec/Plan 和历史目录不在扫描范围；其中的迁移事实由现有文档定位门管理。

- [ ] **Step 4: 注册 release gate 并先运行 checker 自测**

在 `verify-release.ps1` 的 documentation gate 后加入：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root 'scripts/public-api-surface-check.test.ps1')
Assert-ExitCode 'Public API surface checker tests'

& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root 'scripts/public-api-surface-check.ps1')
Assert-ExitCode 'Retired public API zero-reference check'
```

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/public-api-surface-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/public-api-surface-check.ps1
```

Expected: checker test PASS；仓库扫描在文档和脚本尚未全部同步时可以先 FAIL，并打印所有剩余活动引用。

- [ ] **Step 5: 同步当前维护文档与架构状态**

进行以下精确文档更新：

- `README.md`：列出 `GET /api/portfolio`、`POST /api/client-diagnostics` 和四条 Agent 资源；删除“`/api/v1` 下只读资源”当前声明。
- `docs/06-公开内容发布运行手册.md`：发布验收路径改为 `GET /api/portfolio`。
- `docs/08-当前实现状态.md`：记录公开读取面已经收敛为无版本单资源，并保留“尚未生产部署”。
- `docs/11-项目演进日志.md`：按日期正序追加“公开浏览面从摘要/详情/聚合多合同收敛为原子快照单权威”，不写测试数量、hash 或提交元数据。
- `docs/agent-architecture-status.json`：只根据本轮实际验证更新 `updatedAt` 与相关 evidence；所有 hard invariant 真正通过且无 deferred item 后才声明 `COMPLETE`。

历史 specs、plans、reports、handoffs 与本设计中的迁移事实保持不变。

- [ ] **Step 6: 运行静态零引用与定向门**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/public-api-surface-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1
git diff --check
```

Expected: 全部 PASS；零引用 checker 无活动 `/api/v1` 命中。

- [ ] **Step 7: 运行 Backend 与 Frontend 全量门**

Run:

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml clean package
```

Expected: Maven 全量 PASS、Vitest 全量 PASS、Vue type-check PASS、Vite build PASS、clean package PASS。

- [ ] **Step 8: 运行安全、架构与发布联合门**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipDockerCheck
```

Expected: code quality、architecture、privacy 与 release verification 全部 PASS；默认 packaged-JAR Browser lane 使用 PostgreSQL 配置完成桌面/移动矩阵。若环境缺少 PostgreSQL/Playwright 条件，必须在 `docs/agent-architecture-status.json` 记录精确 deferred item，不得把未运行门记为 PASS 或宣称 Slice 完成。

本变更不要求新的真实 Provider 调用；不得使用 `-RequireLiveProvider`，除非用户另行明确授权。

- [ ] **Step 9: 最终替换审计**

Run:

```powershell
rg -n "@RequestMapping|@GetMapping|@PostMapping|@DeleteMapping" backend/src/main/java/com/portfolio/agent/portfolio/controller backend/src/main/java/com/portfolio/agent/turn/api backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsController.java
rg -n "PublicContentController|PublicContentResponse|PortfolioHomeResponse|PortfolioOverview|ProjectSummaryResponse|/api/v1" backend/src/main frontend/src scripts README.md docs/06-公开内容发布运行手册.md docs/08-当前实现状态.md
```

Expected: 第一条只显示六条目标 HTTP 资源及 SPA 映射；第二条仅允许 checker 自身、checker fixture 和集中退休合同测试中的精确豁免，不得出现活动生产或验收调用。

- [ ] **Step 10: 可选最终提交（仅在用户另行授权时）**

```powershell
git add README.md backend frontend scripts docs/00-文档状态索引.md docs/06-公开内容发布运行手册.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/agent-architecture-status.json docs/superpowers/specs/2026-08-21-portfolio-public-api-convergence-design.md docs/superpowers/plans/2026-08-21-portfolio-public-api-convergence.md
git commit -m "refactor(api): 收敛公开作品集接口权威"
```

未获得授权时跳过，不 stage。提交前必须重新确认 staging 中没有用户无关改动。

---

## Plan Completion Review

实施者只有在以下问题全部回答“是”时才能结束：

- `/api/portfolio` 是否是唯一作品集读取 HTTP 资源？
- 14 字段是否逐项存在且语义未变？
- `agentAvailability` 与 `no-store` 是否保留？
- 六条旧路由是否均为 JSON 404 且未进入 SPA？
- Frontend 是否仍只发一次公开快照请求？
- Case packaged 断言是否改为聚合内验证？
- 活动 Backend、Frontend、scripts 和当前文档是否通过 `/api/v1` 零引用门？
- 是否没有兼容桥、旧链 fallback 或第二快照权威？
- Backend/Frontend/full package/PostgreSQL Browser/quality/privacy/documentation/architecture/release 门是否实际通过？
- 是否保留所有用户已有改动且没有未经授权 stage/commit/push？
