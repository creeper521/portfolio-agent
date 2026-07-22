# Portfolio Agent C2a 本地公开检索设计

- 状态：已实现并验证（2026-07-22）
- 日期：2026-07-22
- 范围：C2a——Claim 派生 Chunk、本地关键词/向量混合检索、RetrievalDecision 与 `ANSWERED + RETRIEVAL`
- 前置设计：`2026-07-21-portfolio-agent-content-governance-design.md`
- 前置设计：`2026-07-21-portfolio-agent-future-intelligence-design.md`
- 前置实现：C1 `AnswerPlan`、`ModelExpressionPort`、完整 Draft 校验与同 Plan fallback

## 1. 决策摘要

C2 按 `C2a 本地公开检索 → C2b 封闭只读工具 → C2c 引用式多轮` 实施。本设计只覆盖 C2a，不提前建设 Tool、Registry、通用 Hook、Orchestrator、DurableTask、多 Agent 或服务端会话。

C2a 使用 `BAAI/bge-small-zh-v1.5` 的固定 INT8 ONNX 制品，在 Portfolio Agent 所在机器通过 ONNX Runtime Java CPU 本地生成 512 维归一化查询向量。访客问题、规范化词项、查询向量、相似度和候选排序不得离开服务端、进入普通日志或发送给 DeepSeek、GLM 等外部 Provider。模型表达端仍只接收验证后的白名单 `AnswerPlan`。

QuestionPreset 精确或 alias 命中时继续直接产生 `ANSWERED + PRESET`，不执行 RAG。只有未命中 Preset 的自由文本进入本地检索；只有 `RetrievalDecision.SUFFICIENT` 可以产生 `ANSWERED + RETRIEVAL`。任何不足、歧义、冲突、越界或安全命中均不能让模型猜测事实。

## 2. 目标与非目标

### 2.1 目标

1. 从已批准公开 Claim 派生可审计 Chunk，并将 RAG 文档、关键词索引和向量索引与 ContentBundle 原子发布、激活和回滚。
2. 在本机完成访客问题规范化、关键词召回和向量化，不调用外部 Embedding API。
3. 使用固定 Metadata Filter、混合召回、ClaimEvidenceLink 展开和 Grounding Gate 形成类型化 RetrievalDecision。
4. 保持 `resolution`、`answerSource`、`generationMode`、`verification` 四维正交。
5. 保证索引只负责候选发现，Claim、Evidence 和 ClaimEvidenceLink 继续拥有事实权威。
6. 在 4 核、4GB 内存、约 17GB 可用磁盘的目标机器上有界运行，并提供关键词降级路径。

### 2.2 非目标

C2a 不实现：

- 外部 Embedding Provider 或外部向量数据库；
- Milvus、Pinecone、Elasticsearch、OpenSearch 或独立检索服务；
- 生成式 Query Rewrite、模型 Reranker 或模型直接检索；
- ToolPlan、PublicKnowledgeTools、模型 ToolCall；
- ContextEnvelope、引用式多轮或服务端会话；
- 动态 Registry、通用 Hook、Agent Loop、多 Agent 或 DurableTask；
- 私有知识库、未批准 Evidence、治理目录或任意文件系统搜索；
- 运行时重新生成文档向量或修改 active Bundle。

## 3. 固定数据流

### 3.1 发布侧

```text
canonical portfolio.json
+ canonical presentation.json
+ canonical rag-documents.jsonl
→ Candidate Payload / candidatePayloadHash
→ Human Approval / approvalDigest
→ RagDocumentValidator
→ KeywordIndexBuilder
→ LocalDocumentEmbeddingBuilder
→ vector-index
→ manifest.json / checksums.json
→ runtimeBundleHash
→ atomic active switch
```

`rag-documents.jsonl` 属于 canonical public payload，必须在私人治理环境完成公开事实选择和人工 Approval。服务器发布工具只能逐字节复制已批准 payload、复算 hash、验证引用并确定性构建索引；不能对已批准 Chunk 文本做语义改写、补默认值或重新分段。

关键词索引和向量索引是服务器派生制品，不属于 Approval 语义输入。它们必须能由相同 `rag-documents.jsonl`、固定策略版本和固定模型制品重新生成。

### 3.2 运行侧

```text
AnswerRequest
→ current RuntimeContentSnapshot
→ QuestionPreset exact/alias resolution
├─ matched: ANSWERED + PRESET
└─ unmatched:
   → Local Query Normalization
   → Capability / Safety Scope Check
   → Metadata Filter
   → Keyword Retrieval
   → Local Vector Retrieval
   → Deterministic Fusion and Rerank
   → ClaimEvidenceLink Expansion
   → RetrievalContextValidator
   → RetrievalDecision
   ├─ SUFFICIENT: AnswerPlan → ANSWERED + RETRIEVAL
   └─ other: BOUNDARY or REJECTED, answerSource = null
```

每轮只读取一次 active `RuntimeContentSnapshot`。检索、Claim 回查、Evidence Link 展开、AnswerPlan 和 verification 必须使用同一内容版本，不允许在一次请求中重新读取 active 或混用新旧索引。

## 4. RAG 文档契约

`rag-documents.jsonl` 每行是一个完整 JSON 对象：

```json
{
  "chunkId": "chunk-sql-audit-delivery",
  "contentVersion": "2026-07-21.1",
  "projectSlugs": ["sql-audit"],
  "claimIds": ["claim-sql-audit-delivered"],
  "text": "SQL 审计工具核心版本已完成部署，并形成使用文档。",
  "topics": ["DELIVERY_STATUS", "DOCUMENTATION"],
  "validFrom": "2026-07-10",
  "validUntil": null,
  "contentHash": "sha256:..."
}
```

约束如下：

1. `chunkId`、`contentVersion`、`projectSlugs`、`claimIds`、`text`、`topics`、`validFrom` 和 `contentHash` 必填；`validUntil` 可以为 `null`。
2. 每个 Claim ID 必须存在于同一 canonical payload，属于列出的 Project，且不是撤回、过期或非公开 Claim。
3. Chunk 文本只能由该 Claim 已批准的公开 statement、detail 和必要项目标题组合，不能引入 Evidence raw content、审核说明或推断文本。
4. Evidence 不写入 Chunk；运行时从当前 `ClaimEvidenceLink` 派生。
5. 第一版优先一个 Chunk 表达一项事实。只有不可分割的同义事实才允许一个 Chunk 引用多个 Claim。
6. `contentHash` 使用对象 canonical bytes 计算；重复 `chunkId`、重复 canonical content 或引用不一致均阻断发布。
7. Chunk 文本出现外部 URL、类 ToolCall、模板表达式、Unicode 隐藏控制字符或数据泄露诱导时阻断发布，不做有损自动清洗。

## 5. 索引包与无环 Hash

C2a 运行包：

```text
runtime-bundle/
├─ manifest.json
├─ portfolio.json
├─ presentation.json
├─ rag-documents.jsonl
├─ keyword-index.json
├─ vector-index.bin
└─ checksums.json
```

Manifest 的 `retrieval` 对象固定包含：

- `strategyVersion`
- `normalizationVersion`
- `retrievalPolicyVersion`
- `embeddingModelId`
- `embeddingArtifactSha256`
- `dimension = 512`
- `documentMaxTokens = 256`
- `vectorNormalization = L2`
- `similarity = COSINE`
- `chunkCount`
- `chunkSetHash`
- `keywordIndexFormatVersion`
- `vectorIndexFormatVersion`

Hash 依赖继续遵守 B 的无环契约：

```text
canonical payload files, including rag-documents.jsonl
→ candidatePayloadHash
→ Approval / approvalDigest
→ manifestHash

derived keyword/vector indexes and other runtime files
→ checksumsHash

manifestHash + checksumsHash
→ runtimeBundleHash
```

索引中任一 Chunk ID、contentHash、模型 ID、模型制品 hash、维度、规范化版本、向量数量或文件 hash 不一致时，拒绝激活完整新 Bundle，并保留旧 active Bundle。

## 6. 本地 Embedding 运行规格

第一版固定配置：

- 模型：`BAAI/bge-small-zh-v1.5`
- 模型格式：INT8 ONNX
- 执行：ONNX Runtime Java CPU
- 输出：512 维 float 向量
- 规范化：L2
- 相似度：Cosine
- 查询 batch：1
- 最大输入：256 Tokens
- `intraOpNumThreads = 2`
- `interOpNumThreads = 1`
- Session/Tokenizer：进程级单例，只加载一次
- JVM 初始部署建议：`-Xmx768m`

短查询在 tokenizer 前添加模型官方中文 retrieval instruction；文档 Chunk 不添加 instruction。文本截断必须确定性执行并保留前 256 Tokens，不按字符数假装 Token 数。

模型不提交 Git。受版本控制的模型描述文件记录上游仓库、固定 revision、所需文件、每个文件 SHA-256、许可证、维度和推理参数。安装脚本下载到临时目录，逐文件校验成功后原子移动到被 `.gitignore` 排除的 `runtime-models/bge-small-zh-v1.5/`。应用启动时禁止联网下载。

正式环境中，声明 vector 索引的 Bundle 只有在本地模型文件、tokenizer、制品 hash 和 Manifest 全部兼容时才具备 `groundedQuestions=true`。开发环境可以显式使用 keyword-only Profile；正式环境不能静默把已声明 vector 的能力当作完整混合检索。

## 7. 关键词检索与混合排序

查询规范化采用版本化、纯本地、确定性策略：

1. Unicode NFKC；
2. Locale-independent lowercase；
3. 空白与常见标点归一；
4. 英文/数字连续词项；
5. 中文连续文本的二元词组；
6. 固定公开停用词表；
7. 不生成或记录问题 hash、词项日志或搜索分析事件。

Keyword Retrieval 使用 BM25 风格得分并取 Metadata Filter 后 Top 8。Vector Retrieval 使用本地查询向量和预构建向量取 Top 8。两路结果通过固定 Reciprocal Rank Fusion 合并；原始 BM25 和 cosine 分数不直接相加。确定性重排优先：

1. 同时被 keyword/vector 命中的 Chunk；
2. KEY Claim；
3. 当前有效、Evidence Link 完整的 Claim；
4. 排名并列时按 `chunkId` 升序，保证重复执行稳定。

最终最多保留 12 个 Chunk、8 个 Claim 和 RetrievalPolicy 允许的最大上下文字符数。若截断会删除已选 KEY Claim，决策必须降为 `INSUFFICIENT`，不能静默截断后继续回答。

## 8. RetrievalPolicy 与决策语义

`RetrievalPolicy` 独立版本化，固定包含：Metadata Filter、Top-K、RRF 参数、候选/Claim/Context 上限、向量启用条件、关键词强匹配规则、向量候选阈值、歧义 margin 和 Grounding Gate。

数值阈值由固定公开 Benchmark 校准后写入 Policy 制品，不散落在业务代码中。BGE 官方说明相似度分布不能使用通用固定阈值判断相关性，因此某个 cosine 值本身不能产生 `SUFFICIENT`。

决策语义：

- `SUFFICIENT`：至少一个相关 KEY Claim；所有入选 Claim 当前有效且来自同一 Snapshot；无未解决冲突；Evidence Link 完整；上下文保留全部必要 KEY Claim；Benchmark 已批准当前 Policy。
- `INSUFFICIENT`：无相关 KEY Claim、只有弱候选、Link 不完整或 Context 上限无法安全保留关键事实。
- `AMBIGUOUS`：多个不同事实方向排名接近，无法用稳定 ID 和当前上下文唯一解析。
- `CONFLICTING`：入选公开 Claim 存在未解决冲突、互斥生命周期或治理状态矛盾。
- `OUT_OF_SCOPE`：问题与当前公开 Project 能力范围无关。

映射规则：

```text
SUFFICIENT
→ resolution = ANSWERED
→ answerSource = RETRIEVAL
→ generationMode independently selected
→ verification independently computed

INSUFFICIENT / AMBIGUOUS / CONFLICTING / OUT_OF_SCOPE
→ resolution = BOUNDARY
→ answerSource = null
→ verification = NOT_APPLICABLE
→ return safe explanation and real supported presets

security/privacy rejection
→ resolution = REJECTED
→ answerSource = null
→ verification = NOT_APPLICABLE
```

`ANSWERED + RETRIEVAL` 不自动等于 VERIFIED。只有最终 AnswerSection 引用的 KEY Claim 均满足现有 VerificationPolicy，才能得到对应 verification。

## 9. AnswerPlan 与外部模型边界

RetrievalContext 只包含当前 Snapshot 中重新解析的 Project、Claim、Evidence 和允许的 section 规划。`AnswerPlanBuilder` 从该上下文生成与 C1 相同的白名单 `AnswerPlan`。外部 ModelExpressionPort 不接收：

- 访客原问题或规范化问题；
- 关键词、query instruction 或查询向量；
- BM25/cosine/RRF 分数；
- 完整候选列表或未入选 Chunk；
- RetrievalPolicy 内部阈值；
- ContextEnvelope、会话、turnId、requestId 或 handoffId。

模型 Draft 继续通过 C1 完整 Validator。模型失败时，使用同一 Retrieval AnswerPlan 确定性 fallback；不能回退到未验证候选或让模型重新检索。

## 10. Capability、降级与失败

能力状态：

```text
valid bundle + compatible local model
→ HYBRID_ENABLED

explicit development keyword-only profile
→ KEYWORD_ONLY

runtime vector failure + sufficient keyword result
→ KEYWORD_FALLBACK

runtime vector failure + insufficient keyword result
→ BOUNDARY

invalid index or bundle/model incompatibility at activation
→ reject new bundle, retain old active
```

Preset 不依赖 Retrieval Capability。可选向量能力不可让整个应用失去 readiness，但正式环境不能把缺失模型伪装成完整 hybrid capability。

内部失败使用类型化 code，不携带原问题、文件路径、向量或异常正文。向量运行失败不重试、不跨 Provider、不调用外部 Embedding。关键词检索也失败时返回安全边界，不调用生成模型猜测。

## 11. API 与前端

现有四维 API 字段保持不变。前端对 `answerSource=RETRIEVAL` 显示“来自公开资料检索”，不能显示为 Preset 或自动显示已核验。`KEYWORD_FALLBACK` 是内部 retrievalMode，不新增第五个回答维度；必要时通过安全能力说明告知语义检索暂不可用。

`AMBIGUOUS` 返回可选择的公开方向或 QuestionPreset；`CONFLICTING` 返回公开资料待确认说明；`INSUFFICIENT` 和 `OUT_OF_SCOPE` 返回真实支持问题。Evidence 抽屉只解析响应中存在于当前公开 Snapshot 的 Evidence ID，无效 ID 不回退第一项。

页面内存会话、刷新即失效、URL/History/Storage 禁止、一次性 handoff 和抽屉焦点规则不变。

## 12. 注入与隐私防护

信任顺序固定为：

```text
Runtime Policy
> current Claim / Evidence / Link
> RetrievalPolicy / AnswerPlan
> visitor input and public reference text
```

访客输入和 Chunk 文本都是数据，不能修改 Policy、权限、section、verification 或输出 Schema。索引命中后必须使用 Claim ID 回查当前 Snapshot，不能直接把索引文本当作事实权威。

禁止记录或长期保存：问题、规范化问题、词项、问题 hash、查询向量、相似度、完整候选排序、Chunk 正文、RetrievalContext、AnswerPlan、Prompt 或 Draft。匿名观测只允许 contentVersion、retrievalPolicyVersion、retrievalMode、RetrievalDecision、候选/Claim 数量桶、延迟桶和标准 fallbackReason。

## 13. Benchmark 与测试门禁

### 13.1 Retrieval Benchmark

覆盖：

- 每个 active Project 的正例问法和别名外自然问法；
- 预期 Project、Claim 和 Chunk 排名；
- 无关问题、相似但错误事实、过期/撤回 Claim；
- 中文同义表达、英文技术词、数字和标点变化；
- vector failure 后的 keyword fallback。

### 13.2 Grounding Benchmark

覆盖：

- KEY Claim 保留；
- ClaimEvidenceLink 展开；
- Evidence 缺失与无效 Link；
- 多方向歧义；
- 冲突 Claim；
- Context 上限和不可安全截断；
- Chunk 中的伪指令、URL、ToolCall、模板和 Unicode 隐藏字符。

### 13.3 End-to-End

覆盖：

- Preset 命中跳过 Retrieval；
- `ANSWERED + RETRIEVAL` 的四维契约；
- `DETERMINISTIC / MODEL / FALLBACK` 独立语义；
- verification 只由核心 Policy 计算；
- Provider 请求不含问题、关键词、向量或候选；
- 页面内存隐私、Evidence 展示和 packaged-JAR 浏览器验收。

Bundle/结构/隐私/Grounding/fallback 为发布 BLOCKER。表达质量继续属于独立 ModelPolicy 门禁。

首份 RetrievalPolicy 的准入门槛固定为：全部 active Project 正例在 Top 5 命中预期 KEY Claim；全部越界、冲突、歧义、过期和撤回负例不得产生 `SUFFICIENT`；全部 vector-failure 用例产生预期 `KEYWORD_FALLBACK` 或安全 `BOUNDARY`。在目标 4 核、4GB 机器完成 20 次预热后，100 次固定公开查询的本地 Embedding p95 必须不超过 500ms，且加载模型后的进程增量常驻内存不得超过 350MB。未达到任一门槛时保持 Retrieval 关闭并调整 Policy 或制品，不放宽事实 Gate。

## 14. 上线顺序与回滚

```text
1. 生成并验证 rag-documents.jsonl
2. 启用 keyword retrieval benchmark
3. 使用固定公开 Benchmark 运行 local vector shadow comparison
4. 批准 RetrievalPolicy
5. 启用 ANSWERED + RETRIEVAL
```

Shadow 阶段只运行固定公开 Benchmark，不采集真实访客问题。C2a 回滚通过切换到上一完整 ContentBundle 和上一 RetrievalPolicy；不能单独回滚向量索引。紧急关闭自由文本 Retrieval 时，Preset 和 C1 确定性回答继续工作。

## 15. 验收标准

1. Preset 命中不执行关键词或向量检索。
2. 查询文本、词项和向量只存在于本次请求内存，不出站、不落盘、不进日志。
3. 文档与查询使用相同模型 ID、制品 hash、维度和规范化版本。
4. Chunk 全部关联当前公开 Claim，Evidence 全部从当前 Link 派生。
5. 索引不一致拒绝激活完整新 Bundle，并保留旧 active。
6. 向量运行失败可靠降级关键词且不重试外部服务。
7. 只有 `SUFFICIENT` 产生 `ANSWERED + RETRIEVAL`。
8. `ANSWERED + RETRIEVAL` 独立计算 generationMode 和 verification。
9. KEY Claim、冲突、歧义、Context 上限和注入用例全部通过 Benchmark。
10. 模型、Tokenizer 和索引在 4 核、4GB 目标环境满足有界内存/线程配置。
11. 前端准确展示 RETRIEVAL，不把 BOUNDARY 或 RETRIEVAL 自动显示为 VERIFIED。
12. C2a 没有 Tool、ContextEnvelope、Registry、Hook、Orchestrator、DurableTask、多 Agent 或服务端持久会话。

## 16. 后续边界

C2a 完成并验证后，C2b 才讨论封闭 `ToolKind`、确定性 ToolPlan 和公开只读工具；C2c 最后增加只含稳定 ID 的浏览器内存 ContextEnvelope。两者不得反向改变 C2a 的本地 Embedding、事实权威、Bundle 原子性或 RetrievalDecision Gate。

## 17. 选型依据

- BGE Small 中文模型卡、检索 instruction、维度、评测与 MIT 许可：`https://huggingface.co/BAAI/bge-small-zh-v1.5`
- ONNX Runtime Java CPU 集成：`https://onnxruntime.ai/docs/get-started/with-java.html`
- DJL HuggingFace Tokenizer 本地 `tokenizer.json` 加载：`https://docs.djl.ai/v0.29.0/extensions/tokenizers/index.html`
- 智谱 Embedding-3 外部 API（仅作为被拒绝的替代方案记录）：`https://docs.bigmodel.cn/cn/guide/models/embedding/embedding-3`
