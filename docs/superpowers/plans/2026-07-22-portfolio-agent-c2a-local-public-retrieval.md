# Portfolio Agent C2a Local Public Retrieval Implementation Plan

## Implementation status (2026-07-22)

C2a is implemented and verified. This plan remains the implementation record; C2b read-only tools/cited multi-turn and C3 Registry/Hook/Orchestrator/multi-Agent work are explicitly not implemented.

- Backend clean package: 175 tests, 0 failures, 0 errors; the optional real-model benchmark/performance tests are run separately with an explicit local-model property.
- Release verification: code quality, architecture, privacy, deterministic bundle governance for both legacy four-file and retrieval seven-file releases, JAR unpack/static checks, and packaged application checks passed.
- Local retrieval benchmark: all fixed public positive/negative cases passed; 100 measured query embeddings after 20 warmups completed with p50 2 ms, p95 3 ms, and 4 MB committed-memory delta.
- Frontend: type/check and lint passed; 75 Vitest tests passed; production build passed; mock Playwright passed 20/20.
- Real Hybrid packaged-JAR acceptance: fixture build passed; Playwright passed 18 tests with 2 expected skips; no application process remained afterward.
- Model contract: pinned local `BAAI/bge-small-zh-v1.5` INT8 ONNX artifact, exact immutable revision/file hashes, 512 dimensions, L2-normalized cosine retrieval. Model binaries remain ignored under `runtime-models/`.
- Governance boundary: approval covers the canonical payload including byte-exact `rag-documents.jsonl`; keyword/vector indexes are deterministic derived runtime artifacts and are validated as part of the closed seven-file release.
- Git boundary: no staging, commit, push, reset, restore, checkout, or deletion was performed as part of C2a completion.

> **执行要求：** 严格按任务顺序做 RED → GREEN → 回归。每完成一个任务记录命令和结果。当前工作区包含用户已有未提交内容；不得 reset、restore、checkout 覆盖或删除，不得自动 `git add`、commit、push 或创建 PR。

**目标：** 在 C1 的 provider-safe `AnswerPlan` 前增加本地公开知识检索：发布时从已批准 Claim 派生并固化 Chunk、关键字索引和文档向量；运行时只在本机对自由问题向量化，经过确定性混合召回和 Grounding Gate 后，只有 `SUFFICIENT` 能产生 `ANSWERED + RETRIEVAL`。

**架构：** `RuntimeContentSnapshot` 是唯一 active 内容快照，增加可选的 retrieval 内容，不创建新的 Snapshot 名称。Preset 精确/别名命中继续直接走 C1；未命中才进入 `LocalRetrievalCoordinator`。索引只负责候选发现，最终事实必须按 Claim ID 回查同一 Snapshot，并通过 `ClaimEvidenceLink` 展开 Evidence。检索成功后，从已选 Project、Claim category/topic 和固定模板派生 canonical intent，外部模型只接收裁剪后的 `AnswerPlan`，绝不接收原问题、词项、向量、分数或候选列表。

**技术栈：** Java 21、Spring Boot 3.5.3、Jackson、ONNX Runtime Java CPU `1.26.0`、DJL HuggingFace Tokenizers `0.36.0`、JUnit 5、AssertJ、Mockito、Vue 3、Vitest、Playwright、PowerShell。

## 全局约束

- 只在 `D:\code\agent` 工作，并保留 C1 和用户现有所有未提交/未跟踪文件。
- 不把模型文件提交 Git；`runtime-models/` 必须被 `.gitignore` 排除。模型只由显式安装脚本下载，应用启动期间禁止联网下载。
- 第一版固定 `BAAI/bge-small-zh-v1.5` 的 INT8 ONNX 制品，512 维、L2、Cosine、query batch 1、最大 256 tokens、intra-op 2、inter-op 1。
- 访客原问题、规范化文本、词项、问题 hash、查询向量、相似度、候选排序和检索上下文只存在于本次请求内存；不得进入 URL、浏览器存储、服务端存储、普通日志或外部 Provider。
- 文档向量在发布构建阶段生成；运行时只生成查询向量。不得引入外部 Embedding API、向量数据库或独立检索服务。
- `resolution`、`answerSource`、`generationMode`、`verification` 保持正交。`ANSWERED + RETRIEVAL` 不等于 `VERIFIED`。
- `SUFFICIENT` 以外的检索判定只返回安全 `BOUNDARY`；安全/隐私拒绝保持 `REJECTED`。向量失败只允许单次关键字降级，不重试、不转发外部服务。
- 旧四文件 Bundle 继续可加载，表现为 retrieval capability 不可用；声明 retrieval 的新 Bundle 必须七文件完整且原子验证，禁止部分激活。
- 不建设 C2b/C2c、Tool、ContextEnvelope、Registry、通用 Hook、Orchestrator、DurableTask、多 Agent、服务端会话或生成式 Query Rewrite/Reranker。
- 不使用 Java record、`var` 或 Lombok；不新增会泄露请求内容的 `toString()`。

## 文件与契约迁移总览

### Bundle 与模型制品

- 新建 `backend/src/main/java/com/portfolio/agent/portfolio/domain/RagDocument.java`：canonical Chunk 契约。
- 新建 `backend/src/main/java/com/portfolio/agent/portfolio/domain/RetrievalManifest.java`：Manifest 的闭合 retrieval 元数据。
- 新建 `backend/src/main/java/com/portfolio/agent/portfolio/domain/RuntimeRetrievalContent.java`：同一 `RuntimeContentSnapshot` 中的只读 retrieval 内容，不是 Snapshot。
- 修改 `ReleaseManifest.java`、`RuntimeContentSnapshot.java`、`PublicBundleLoader.java`、`BundleHashCalculator.java`：兼容旧四文件包并严格加载新七文件包。
- 新建 `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/KeywordIndexFile.java`、`VectorIndexFile.java`、`VectorIndexCodec.java`：确定性索引格式与校验。
- 新建 `backend/src/main/java/com/portfolio/agent/portfolio/release/RagDocumentValidator.java`、`KeywordIndexBuilder.java`、`LocalDocumentEmbeddingBuilder.java`、`RetrievalBundleCompiler.java`、`RetrievalBundleCompilerCli.java`：发布期验证与派生构建。
- 新建 `backend/src/main/resources/embedding-models/bge-small-zh-v1.5-int8.json`：上游仓库、40 位 immutable revision、所需文件 SHA-256、许可证、维度和推理参数。
- 新建 `scripts/install-local-embedding-model.ps1`、`scripts/build-retrieval-bundle.ps1`、对应脚本测试；修改 `.gitignore`。

### Answer 检索核心

- 新建 `answer/domain` 下的 `RetrievalDecisionType`、`RetrievalMode`、`RetrievalPolicy`、`RetrievalChunk`、`RetrievalCorpus`、`RetrievalCandidate`、`RetrievalDecision`、`EmbeddingVector`。
- 新建 `answer/gateway/LocalEmbeddingPort.java`，只暴露 `EmbeddingVector embedQuery(String localQueryText)`；该端口只能由本地 adapter 实现。
- 新建 `answer/service` 下的 `RetrievalQueryNormalizer`、`KeywordRetriever`、`VectorRetriever`、`ReciprocalRankFusion`、`RetrievalContextValidator`、`LocalRetrievalCoordinator`。
- 新建 `answer/adapter/retrieval/OnnxLocalEmbeddingAdapter.java`、`LocalEmbeddingProperties.java`、`LocalEmbeddingConfiguration.java`；修改 `backend/pom.xml` 和 `application.yml`。
- 修改 `LocalPortfolioKnowledgeAdapter.java` 与 `RuntimeAnswerContent.java`：把同一 active Snapshot 的 retrieval corpus 投影到 Answer 边界。
- 修改 `QuestionResolver.java`、`PortfolioAgentRuntime.java`、`ResolvedAnswerContext.java`、`AnswerTurnSnapshot.java`、`AnswerPlanBuilder.java`、`ExecutionBudgets.java`、`AgentExecutionSnapshot.java`：增加检索分支，但保持 Preset 和 C1 模型表达路径稳定。

### 前端、检查与文档

- 修改 `LightAnswerPanel.vue`、`ConversationThread.vue` 及其测试：对 `RETRIEVAL` 显示“来自公开资料检索”，不改 verification 展示逻辑。
- 修改 `frontend/e2e/support/publicApiMocks.ts` 和 `frontend/e2e/portfolio.spec.ts`：覆盖检索答案、Boundary、Evidence 抽屉和隐私状态。
- 扩展 `architecture-check.ps1`、`privacy-check.ps1`、`verify-static-bundle.ps1` 及各自测试。
- 新建固定公开 benchmark 夹具 `backend/src/test/resources/retrieval-benchmark/cases.json`；不采集真实访客问题。
- 更新 `README.md`、`SECURITY.md`、`docs/00-文档状态索引.md`、`docs/05-public-release-bundle-contract.md`、`docs/06-content-publishing-runbook.md` 和 C 设计实现状态；B、C2b、C2c、C3 不得标成完成。

---

## Task 1：锁定 Bundle C2a 契约并保持四文件兼容

**测试：** `ReleaseBundleModelContractTest.java`、`PublicBundleLoaderTest.java`、`BundleHashCalculatorTest.java`（新建）。

- [ ] 先增加失败测试：反序列化完整 `RetrievalManifest`；旧四文件 Bundle 仍成功且 `snapshot.getRetrievalContent().isEmpty()`；新七文件 Bundle 完整成功；缺任一 retrieval 文件、额外文件、未知字段、数量/hash/模型元数据不一致都拒绝整个 Bundle。
- [ ] 增加 hash 测试：`rag-documents.jsonl` 的原始 canonical bytes 进入 `candidatePayloadHash`；`keyword-index.json` 和 `vector-index.bin` 只进入 checksums/runtime hash，不进入 Approval payload。
- [ ] 运行 RED：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ReleaseBundleModelContractTest,PublicBundleLoaderTest,BundleHashCalculatorTest test
```

- [ ] 最小实现：`ReleaseManifest` 增加 nullable `retrieval`；`PublicBundleLoader` 只接受 `{manifest,portfolio,presentation,checksums}` 或设计规定的七文件闭合集；一次读取、一次校验、最后一次构造 `RuntimeContentSnapshot`。
- [ ] 再运行同一命令确认 GREEN，并运行 `JsonPublicPortfolioRepositoryTest` 防止旧包回归。

**兼容/回滚：** 不声明 `manifest.retrieval` 的包完全沿用 A/B 行为；声明后不允许 keyword-only 残包激活。回滚单位始终是完整 Bundle。

## Task 2：实现 RagDocument 治理验证与确定性关键字索引

**测试：** 新建 `RagDocumentValidatorTest.java`、`KeywordIndexBuilderTest.java`、`RetrievalBundleCompilerTest.java`。

- [ ] 先写失败矩阵：重复 `chunkId`/canonical content、错误 `contentVersion`、不存在/撤回/过期/非公开 Claim、Project 不归属、错误 `contentHash`、Evidence 正文复制、URL、模板/ToolCall 文本、Unicode 隐藏控制字符都阻断。
- [ ] 写 tokenizer/BM25 失败测试：NFKC、Locale-independent lowercase、英文数字连续词、中文二元词组、固定停用词、稳定词序、稳定文档频率和相同输入逐字节相同输出。
- [ ] 运行 RED：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RagDocumentValidatorTest,KeywordIndexBuilderTest,RetrievalBundleCompilerTest test
```

- [ ] 实现 `RagDocumentValidator` 与版本化 `KeywordIndexBuilder`。索引文档按 `chunkId` 排序，JSON 使用项目既有 canonical ObjectMapper，不写时间戳或机器路径。
- [ ] 实现 compiler 的“先临时目录构建、全部 hash/校验通过、再发布输出”边界；任何失败不触碰 active Bundle。
- [ ] 运行 GREEN，并补一组不同输入顺序产生相同 bytes/hash 的重复性测试。

## Task 3：定义本地检索领域模型、Policy 和归一化

**测试：** 新建 `RetrievalPolicyTest.java`、`RetrievalQueryNormalizerTest.java`、`RetrievalModelContractTest.java`。

- [ ] 先写失败测试，固定枚举：`SUFFICIENT | INSUFFICIENT | AMBIGUOUS | CONFLICTING | OUT_OF_SCOPE` 和 `HYBRID_ENABLED | KEYWORD_ONLY | KEYWORD_FALLBACK`。
- [ ] 固定第一版 Policy 字段：keywordTopK=8、vectorTopK=8、maxChunks=12、maxClaims=8、RRF k、context 字符上限、强关键字规则、向量候选阈值、歧义 margin、Grounding Gate 版本；数值只从一个版本化 Policy 对象读取。
- [ ] 以反射测试确保 `RetrievalDecision`、`EmbeddingVector` 及候选模型没有 `question`、`turnId`、`requestId`、`handoffId`、日志文本或外部 provider 字段，没有自定义 `toString()`。
- [ ] 运行 RED：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalPolicyTest,RetrievalQueryNormalizerTest,RetrievalModelContractTest test
```

- [ ] 最小实现上述 immutable 类型和归一化器；query 规范化对象只在调用栈内生存，不挂到 Snapshot、response 或 publisher event。
- [ ] 运行 GREEN。

## Task 4：实现关键字/向量召回、RRF 与 Grounding Gate

**测试：** 新建 `KeywordRetrieverTest.java`、`VectorRetrieverTest.java`、`ReciprocalRankFusionTest.java`、`RetrievalContextValidatorTest.java`、`LocalRetrievalCoordinatorTest.java`。

- [ ] 先写 RED：Preset 路径不在此阶段；Metadata Filter 先于评分；两路各 Top 8；RRF 不直接相加 BM25/cosine；双路命中、KEY Claim、有效完整 Link 优先；并列按 `chunkId`；最终不超过 12 Chunk/8 Claim。
- [ ] 写判定矩阵：相关 KEY Claim + 完整有效 Link 才可 `SUFFICIENT`；弱候选/缺 Link/安全截断为 `INSUFFICIENT`；近分多方向为 `AMBIGUOUS`；生命周期/治理矛盾为 `CONFLICTING`；无关为 `OUT_OF_SCOPE`。
- [ ] 写故障测试：`LocalEmbeddingPort` 抛出受控失败时只执行一次 `KEYWORD_FALLBACK`，不重试；关键字仍不足则 Boundary 判定；两路失败不调用模型表达。
- [ ] 运行 RED：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=KeywordRetrieverTest,VectorRetrieverTest,ReciprocalRankFusionTest,RetrievalContextValidatorTest,LocalRetrievalCoordinatorTest test
```

- [ ] 最小实现。索引命中后只保留 ID，必须从传入的同一 `RuntimeAnswerContent` 回查 Claim/Evidence/Link；索引文本本身不进入最终事实上下文。
- [ ] 运行 GREEN，并用 Mockito 验证失败路径调用次数和禁止的 Model port 交互。

## Task 5：固定并安装本地 INT8 ONNX 制品

**文件：** `backend/pom.xml`、模型 descriptor、安装脚本、`OnnxLocalEmbeddingAdapter` 及其测试。

- [ ] 先为 descriptor 和安装脚本写失败测试：revision 必须是 40 位 commit；每个文件必须有 64 位 SHA-256；只允许 HTTPS 固定 host；目标必须位于仓库内 `runtime-models/bge-small-zh-v1.5/`；hash 失败时删除临时目录且不覆盖已验证模型。
- [ ] 通过上游 API 解析一次 immutable revision，下载 `onnx/model_quantized.onnx`、对应 external-data 文件、`tokenizer.json` 和运行所需 config；本地逐文件计算 SHA-256，将真实 revision/hash 写入受版本控制 descriptor。禁止 `main` 浮动地址进入运行时配置。
- [ ] 修改 `.gitignore` 排除 `/runtime-models/`，但 descriptor、许可证文本和安装脚本继续受控。
- [ ] 在 `pom.xml` 加 `com.microsoft.onnxruntime:onnxruntime:1.26.0` 与 `ai.djl.huggingface:tokenizers:0.36.0`。
- [ ] 写 adapter RED：singleton session/tokenizer、输入截到 256 tokens、attention mask/token type ids 正确、mean pooling（按模型实际输出契约）、512 维、L2 范数约 1、线程参数 2/1、无网络访问与无原文日志。
- [ ] 实现 `LocalEmbeddingConfiguration`：只有 Bundle retrieval 元数据与本地 descriptor/文件 hash 全兼容时启用 hybrid；开发 profile 可显式 keyword-only；生产不得静默伪装 hybrid。
- [ ] 执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/install-local-embedding-model.ps1
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=LocalEmbeddingDescriptorTest,OnnxLocalEmbeddingAdapterTest,LocalEmbeddingConfigurationTest test
```

**回滚：** 删除/停用本地模型只关闭自由文本 hybrid 能力，不影响 Preset。不得在应用启动时自动补下载。

## Task 6：发布期生成文档向量和确定性二进制索引

**测试：** 新建 `VectorIndexCodecTest.java`、`LocalDocumentEmbeddingBuilderTest.java`；扩展 `RetrievalBundleCompilerTest.java`。

- [ ] 先写 RED：固定 magic/version/dimension/count；按 `chunkId` 排序；同输入逐字节稳定；NaN/Infinity、非 512 维、非 L2、重复/未知 Chunk、截断/尾随 bytes 全拒绝。
- [ ] 实现小端 binary codec，头部含 format version/dimension/count，条目含稳定 UTF-8 chunk ID 长度、ID bytes 和 512 个 float；所有 bounds 在分配前验证。
- [ ] `LocalDocumentEmbeddingBuilder` 只嵌入已验证 `rag-documents.jsonl` 的 Chunk 文本，不加 query instruction；模型 ID、artifact hash、维度写入 Manifest retrieval 元数据。
- [ ] compiler 完成后复读输出并用 `PublicBundleLoader` 做最终加载验证，再允许原子发布。
- [ ] 执行 GREEN：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=VectorIndexCodecTest,LocalDocumentEmbeddingBuilderTest,RetrievalBundleCompilerTest,PublicBundleLoaderTest test
```

- [ ] 扩展 `verify-static-bundle.ps1`：七文件闭集、checksums、Manifest retrieval 元数据和索引一致性作为 BLOCKER；旧四文件包继续通过兼容验证。

## Task 7：接入 PortfolioAgentRuntime，保持 C1 白名单

**测试：** 扩展 `LocalPortfolioKnowledgeAdapterTest.java`、`AnswerPlanBuilderTest.java`、`PortfolioAgentRuntimeTest.java`、`PortfolioAgentRuntimeModelPrivacyTest.java`、`AgentExecutionSnapshotFactoryTest.java`。

- [ ] 先写 RED：Preset exact/alias 命中时 `LocalEmbeddingPort`/retrievers 零交互且返回 `ANSWERED + PRESET`；未命中且 `SUFFICIENT` 返回 `ANSWERED + RETRIEVAL`；其他检索决定返回 `BOUNDARY` 且 `answerSource=null`、`verification=NOT_APPLICABLE`。
- [ ] 写快照测试：每轮只读取一次 current `RuntimeContentSnapshot`；`AnswerTurnSnapshot` 记录该轮选中的 project、preset nullable、approved Evidence IDs；`AgentExecutionSnapshot` 只组合既有 Turn Snapshot、retrieval/model policy 与预算，不复制问题/索引/内容。
- [ ] 写 C1 出站隐私测试：RETRIEVAL 成功后 `ProviderAnswerPlanPayload` 只含选中 Claim/Evidence 和由 `projectSlug + ClaimCategory/topics + 固定模板` 派生的 canonical intent；原问题、规范化问题、词项、向量、分数、Chunk 文本与未选候选均不存在。
- [ ] 运行 RED：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=LocalPortfolioKnowledgeAdapterTest,AnswerPlanBuilderTest,PortfolioAgentRuntimeTest,PortfolioAgentRuntimeModelPrivacyTest,AgentExecutionSnapshotFactoryTest test
```

- [ ] 修改 Runtime 流程：安全拒绝优先；Preset 优先；自由文本本地检索；`SUFFICIENT` 构造裁剪后的 `ResolvedAnswerContext`；一次构建 `AnswerPlan`；再沿用 C1 deterministic/model/fallback 和 `VerificationPolicy`。
- [ ] `AnswerPlanBuilder` 不再假设 preset 非空；RETRIEVAL 使用内部派生 intent，Preset 仍使用已批准 canonical intent。
- [ ] 运行 GREEN，再执行所有 `answer` 包测试。

## Task 8：前端语义、Evidence 与浏览器隐私验收

**测试：** 组件测试、Vitest、Playwright。

- [ ] 先写失败测试：`RETRIEVAL` 标签为“来自公开资料检索”；verification 独立显示；Boundary 不显示来源/已核验；无效 Evidence ID 不回退第一项。
- [ ] 扩展 API mock：分别返回 `ANSWERED+RETRIEVAL+DETERMINISTIC`、`MODEL`、`FALLBACK`、四种 retrieval Boundary；Evidence ID 都来自当前 public snapshot。
- [ ] 实现最小 UI 文案，不增加第五回答维度，不显示内部 `retrievalMode`、分数或 Query。
- [ ] 执行：

```powershell
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npx.cmd playwright test --config frontend/playwright.config.ts
```

- [ ] 浏览器手工/自动验收：URL/history 无问题；localStorage/sessionStorage/IndexedDB 无会话；刷新会话消失且提示仍在；RETRIEVAL/BOUNDARY/REJECTED 四维显示正确；抽屉键盘/焦点/reduced-motion 不回归。

## Task 9：Benchmark、隐私/架构门禁与故障注入

**测试/脚本：** 新建 `RetrievalBenchmarkTest.java`、`scripts/run-local-retrieval-benchmark.ps1`；扩展检查脚本测试。

- [ ] `cases.json` 覆盖每个 active Project 的自然问法/别名外问法、中文同义表达、英文技术词、数字/标点变化，以及无关、相似但错误、过期、撤回、歧义、冲突和注入文本。
- [ ] 门槛固定：所有正例的预期 KEY Claim 进入 Top 5；所有负例零 false `SUFFICIENT`；所有 vector-failure 用例得到预期 `KEYWORD_FALLBACK` 或安全 Boundary。
- [ ] 性能脚本在实际本地模型上预热 20 次，再运行固定公开查询 100 次；输出只含 p50/p95、内存增量、成功计数，不输出查询、向量或排名。准入为 p95 ≤ 500ms、常驻内存增量 ≤ 350MB。
- [ ] 扩展 privacy scan 到 descriptor、安装/构建脚本、索引生成源码、测试资源、frontend dist、最终 JAR 解包和最终 Bundle；在风险制品最终形成后再扫一次。
- [ ] 扩展 architecture check：Answer core 不得依赖 ONNX/DJL；只有 `answer.adapter.retrieval` 可实现 `LocalEmbeddingPort`；不得出现外部 embedding endpoint/client、vector DB、tool/registry/hook/orchestrator 包。
- [ ] 执行：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalBenchmarkTest test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-retrieval-benchmark.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path .
```

**准入失败：** 保持自由文本 retrieval 关闭，调整公开 benchmark、Policy 或模型制品；不得放宽 Grounding Gate，也不得采集真实访客问题调参。

## Task 10：完整发布验证、packaged JAR 联调和文档收口

- [ ] 生成一份经过测试 Approval fixture 的七文件 C2a Bundle；用 compiler 重建两次，确认派生索引与所有 hash 完全一致。
- [ ] 更新权威文档的实现状态、安装/回滚/故障诊断命令与资源要求；明确 C2b/C2c/C3 未实现。
- [ ] 运行后端要求：

```powershell
mvn.cmd -f backend/pom.xml test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path .
mvn.cmd -f backend/pom.xml package
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall
```

- [ ] 运行前端要求：

```powershell
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npx.cmd playwright test --config frontend/playwright.config.ts
```

- [ ] 在 packaged JAR 下真实联调：Preset 跳过检索；自由问题本地 hybrid 命中；vector 故障关键字降级；不足/歧义/冲突/越界安全 Boundary；DeepSeek/GLM fake provider 只收到裁剪 `AnswerPlan`；模型关闭/失败使用同 Plan deterministic/fallback。
- [ ] 最终记录：修改文件、测试数量/结果、benchmark p95/内存、模型 revision/hash、剩余风险和 `git status --short --branch`。不得暂存、提交或推送。

## 风险与回滚边界

- **模型供应链：** revision 和逐文件 SHA-256 任一变化即安装/启动失败；只能通过评审后的 descriptor 变更升级。
- **低内存：** 默认 `-Xmx768m`、单 session、线程 2/1；超过 350MB 增量或出现系统换页即不启用 vector retrieval。
- **阈值漂移：** Policy 与 benchmark 绑定版本，不能在线自学习或根据访客查询动态调阈值。
- **Bundle 不一致：** 拒绝整个新 Bundle，保留旧 active；不允许单独回滚/替换向量索引。
- **运行故障：** 单次请求内从 hybrid 降级 keyword；若 Grounding Gate 不足则 Boundary。Preset 与 C1 deterministic 路径始终可用。
- **表达模型故障：** 继续使用 C1 同一 `AnswerPlan` 全量 fallback；检索结果不重新计算、不扩大候选。
- **隐私异常：** 发现原问题/向量/候选进入日志、外部请求或持久化即为 release blocker，关闭 retrieval 并修复后重新执行全门禁。
