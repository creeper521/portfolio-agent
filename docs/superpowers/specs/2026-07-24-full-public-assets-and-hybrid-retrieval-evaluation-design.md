# 全量公开资产扩增与混合检索价值评测设计

- 状态：已确认，待实施
- 日期：2026-07-24
- 代码基线：`a2b1c57`
- 内容基线：schema `3.0`，content version `2026-07-23.1`
- 范围：全量复核 68 项资产，治理 29 项公开候选，分波扩充 Project、Case、Claim、Evidence、Preset、Timeline 与 RAG 文档，并对 Keyword、Vector、Hybrid 做可复现离线比较
- 非目标：生产部署、默认启用检索、增加线上 `VECTOR_ONLY`、读取私有知识库作为运行时数据源

## 1. 背景与现状

当前公开 Bundle 严格意义上只有 1 个 Project：`sql-audit`。另外有 3 个独立 Case、16 个 Claim、5 个 `APPROVED` Evidence、16 条 Claim-Evidence Link、5 条 TimelineEvent 和 6 个 QuestionPreset。5 个 Evidence 都是脱敏后的 `COLLECTION`，代表 16 份底层来源，公开运行时不读取原始证据。

私有资产库已登记 68 项：

- 7 条 MAINLINE；
- 19 个 TASK；
- 25 个 INCIDENT；
- 17 项 KNOWLEDGE_ASSET。

按当前治理状态，29 项为 `PUBLIC_REVIEW_REQUIRED`，32 项为 `HOLD`，7 项为 `EXCLUDE`。首批 SQL 主线、多语言图片保留、测试角色重置和 CodeGraph 评测已经发布；剩余公开候选尚未全部形成最终公开决策。

检索底层已经实现 BM25 风格 Keyword、BGE-small-zh-v1.5 本地 Vector 和 RRF Hybrid，但当前运行 Profile 只有 `DISABLED`、`KEYWORD_ONLY` 和 `HYBRID`。`VECTOR_ONLY` 不是线上能力。现有 Benchmark 只有 5 条 SQL 正例和 4 条负例，只运行 Hybrid，并未输出 Hit、MRR 或三路对比。因此现状不能证明混合检索相对单路检索的真实价值。

当前随仓库公开资源仍是四文件 Bundle，没有 `rag-documents.jsonl`、`keyword-index.json` 和 `vector-index.bin`。本设计必须生成并验证真实七文件 Retrieval Bundle，但不部署、不默认启用。

## 2. 决策摘要

采用“全量治理、分波发布、统一评测”方案：

1. 68 项资产全部复核并产生最终路由。
2. 29 项公开候选逐项形成“发布”或“审核后 HOLD”的可审计结论。
3. 公开内容按成果领域分波发布，每一波同时交付内容、Evidence、Preset、RAG 文档和 Benchmark。
4. 先冻结扩充前 baseline，再扩充内容，避免无法判断语料变化和算法变化各自带来的影响。
5. Keyword、Vector、Hybrid 只在同一快照、同一语料、同一模型和同一 Policy 下离线比较。
6. 先测现有算法，再决定是否调整排序或 Policy；校准集与 holdout 分离，最终价值结论只看 holdout。
7. 不以条目数量替代证据质量，不把原型写成上线成果，不把协作或观察学习写成独立交付。

## 3. 目标与非目标

### 3.1 目标

1. 为 68 项资产建立完整、可复核、无悬空状态的决策台账。
2. 将所有通过治理的公开候选转换为真实 Project、Case、Claim、Evidence、Link、Preset、Timeline 和 RAG 文档。
3. 增加至少两个主题差异明显的真实公开 Project，使检索评测不再局限于 SQL 单项目。
4. 建立覆盖所有最终公开 Project/Case 的版本化 Benchmark。
5. 生成 Keyword、Vector、Hybrid 的稳定机器可读报告和人类可读报告。
6. 使用预先声明的判定规则判断 Hybrid 是“明确有价值”“互补但增益有限”“未证明价值”还是“存在退化”。
7. 生成真实七文件 Retrieval Bundle 并通过治理、隐私、Hash、Bundle 和全量发布门禁。
8. 同步更新文档状态、实现盘点和资产库状态，不把本地验证写成线上验收。

### 3.2 非目标

本轮不实现：

- 生产环境部署或线上验收；
- 默认开启 `PORTFOLIO_RETRIEVAL_PROFILE`；
- 线上 `VECTOR_ONLY` Profile 或 Mode；
- 外部 Embedding API、外部向量数据库或模型 Reranker；
- 私有知识库运行时搜索；
- 原始截图、内部日志、内部文档或 PDF 的公开直出；
- 动态工具、通用 Registry、Hook、Orchestrator、多 Agent、DurableTask 或持久会话；
- 为获得好看指标而放宽 Grounding、隐私或 Evidence Gate。

## 4. 全量资产决策模型

每项资产生成一条 `AssetPublicationDecision`，至少包含：

- `assetId`
- `contentType`
- `achievementStatus`
- `contributionType`
- `publicPriority`
- `evidenceStatus`
- `originalReviewState`
- `finalRoute`
- `decisionReason`
- `projectSlugs`
- `caseSlugs`
- `evidenceIds`
- `privacyReview`
- `approvalState`
- `contentVersion`

`finalRoute` 固定为：

- `PROJECT`
- `CASE`
- `ENRICH_EXISTING_PROJECT`
- `EVIDENCE_ONLY`
- `TIMELINE_ONLY`
- `HOLD`
- `EXCLUDE`

状态和贡献不能由生成器自动升级。证据不足、归属不清、公开资格不确定或无法安全脱敏时必须选择 `HOLD`。原本为 `EXCLUDE` 的资产只有在内容所有者明确重新决策并补足证据后，才允许重新进入公开审核；本轮默认保持 `EXCLUDE`。

验收时：

- 68 项必须全部有最终路由；
- 29 项 `PUBLIC_REVIEW_REQUIRED` 必须是已发布或审核后 HOLD；
- 32 项原 HOLD 和 7 项 EXCLUDE 必须保留原因，不进入运行 Bundle；
- 台账计数与公开 Bundle 的 Project/Case/Evidence 引用能够双向核对。

## 5. 公开对象归类

### 5.1 现有 SQL 主线加深

L-01 继续作为 `sql-audit` 主 Project。以下资产不拆成多个虚假项目，而是增强该项目：

- T-01 异步审计查询基础能力；
- T-02 结果归档与截断提示；
- T-03 多目标查询路由；
- T-04 负号输入与多来源查询；
- T-17 使用与验收文档。

新增内容必须分别表达背景、个人职责、技术决策、实现、验证、交付状态和限制。任何调用次数、用户数量、长期效率或生产影响，没有可公开直接 Evidence 时不得写入 Claim。

### 5.2 新增真实 Project

L-04 发布为“个人智能平台与 Agent 原型”，状态保持 `VALIDATED_PROTOTYPE`。T-08、T-09、T-10 分别作为流式会话、工具调用和 MCP 双传输 Case 归入该 Project。公开内容必须说明哪些链路已运行验证、哪些工程边界仍缺失。

L-05 发布为“智能开发工具与上下文工程评测”，状态保持 `VALIDATED_PROTOTYPE`。现有 CodeGraph Case 归入该 Project；上下文压缩离线实验形成独立评测 Case。Token 数据只能描述固定输入下的离线估算，不得描述为真实账单节省或已证明的质量收益。

L-02“图片上传与审计能力”单独审核：

- 如果 Project 级 Evidence 足以支持主线范围，则以 `IMPLEMENTED_TESTED` 发布，并明确上传审计未确认上线；
- 如果只能证明多语言修复，则保留现有 Case，审计相关内容继续 HOLD；
- 不允许用已发布的多语言修复 Evidence 推导完整审计主线已经交付。

### 5.3 工程 Case 与知识交付

以下公开候选优先路由为 Case，而不是独立 Project：

- A-01 至 A-06：配置、展示、兼容过滤和环境结构故障处置；
- T-12、T-13：本地持续集成、压力与运行时追踪；
- T-14、T-15：前端构建产物和服务包交付流程；
- T-18：智能工程内部分享与复盘；
- T-19：知识库迁移与增量同步，贡献保持 `COLLABORATIVE`；
- K-14：版本冲突处理。

A-02 等仍为 `PARTIALLY_VERIFIED` 的候选必须经过 Evidence 复核；若不能形成“问题—行动—验证—结果—限制”闭环，则转为审核后 HOLD。

K-17 历史 CSDN 主页优先作为公开写作 Evidence 和探索入口。动态文章数、访问量等数据如需展示，必须标注抓取日期；默认只展示稳定主页链接和写作主题，不固化易变数字。

## 6. 分波交付

### Wave 0：基线与评测工具

1. 冻结 `2026-07-23.1` baseline。
2. 建立三路离线比较器和报告契约。
3. 运行当前 1 Project、3 Case 的 baseline。
4. 修正脚本语义：没有模型时只能声明 unit gate 通过，不能声明真实 retrieval benchmark 通过。
5. 不修改生产 Profile，不启用运行时检索。

### Wave 1：现有成果加深

1. 扩充 SQL 主线 Claim、Evidence、Preset、Timeline 和 RAG 文档。
2. 保持现有角色重置、多语言图片和 CodeGraph Case 的真实状态。
3. 为已有成果增加自然改写、技术词和负例 Benchmark。
4. 验证新增语料没有破坏既有 SQL 检索。

### Wave 2：新增真实项目

1. 发布 L-04 与关联 Case。
2. 发布 L-05，关联 CodeGraph 和上下文压缩 Case。
3. 审核 L-02 的 Project 资格。
4. 引入 AI 平台运行截图、CodeGraph 报告、压缩实验和限制说明的脱敏 Evidence。
5. 建立跨项目相似概念与语义改写 Benchmark。

### Wave 3：工程案例与知识交付

1. 审核并发布 A-01 至 A-06。
2. 审核并发布 T-12 至 T-15、T-18、T-19。
3. 审核并发布 K-14、K-17。
4. 对无法满足直接 Evidence、贡献或隐私要求的候选形成 HOLD 决策。
5. 运行最终全量三路比较和发布门禁。

每一波使用独立 content version、候选 Hash、Approval、Bundle 和评测报告，允许独立定位问题和回滚。

## 7. Evidence 与隐私契约

1. 每个 KEY Claim 至少绑定一个 `APPROVED + DIRECT` Evidence Link。
2. 第一阶段所有 Evidence 保持 `rawContentPublic=false`。
3. 公开 Evidence 只包含脱敏标题、摘要、证据类型、来源数量、验证方式、时间范围和限制。
4. 私有截图、PDF、提交记录和内部报告可以在仓库外治理层作为审核依据，但不能原样复制进运行 Bundle。
5. 内部域名、IP、端口、本地路径、环境名、账号、角色、渠道、区域、真实业务标识和凭据必须移除。
6. Case 需要具备 problem、actions、verification、outcome 和 limitations。
7. Project/Case 的状态和贡献类型是权威字段，展示层不得重新解释。
8. Evidence 集合可聚合多份底层来源，但必须保留来源数量和覆盖范围，不能用集合标题替代具体支持关系。
9. 公共 Claim 的 `verificationStatus` 只能由有效 Link 与 Evidence 状态计算，候选生成器不能自行授予 `VERIFIED`。

## 8. Retrieval Bundle

最终每一波产生真实七文件 Bundle：

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

RAG 文档必须从已批准公开 Claim 确定性派生或由已批准 canonical payload 提供。Evidence 原始内容不能进入 Chunk。每个 Chunk 必须绑定当前 Project/Case、Claim、topics、content hash 和有效期，并能由 Claim ID 回查当前 Snapshot。

Bundle 必须绑定：

- content version；
- candidate payload hash；
- approval digest；
- retrieval strategy、normalization 和 policy version；
- embedding model ID、descriptor hash、维度和制品 hash；
- chunk set hash；
- 每个文件 checksum；
- runtime bundle hash。

任何引用、Hash、模型身份或索引数量不一致时拒绝新 Bundle，并保留上一有效版本。

## 9. Benchmark 数据契约

Benchmark 用例至少包含：

```json
{
  "caseId": "agent-mcp-transport-paraphrase-01",
  "split": "HOLDOUT",
  "category": "SEMANTIC_PARAPHRASE",
  "subjectType": "PROJECT",
  "subjectSlug": "personal-agent-platform",
  "query": "这个原型用哪些方式连接本地工具？",
  "expectedClaimIds": ["claim-agent-mcp-transports"],
  "expectedChunkIds": ["chunk-agent-mcp-transports"],
  "expectedDecision": "SUFFICIENT"
}
```

固定类别：

- `EXACT_TERM`
- `SEMANTIC_PARAPHRASE`
- `ENGLISH_OR_ACRONYM`
- `NUMBER_OR_PUNCTUATION`
- `CROSS_SUBJECT_CONFUSION`
- `SIMILAR_BUT_FALSE`
- `AMBIGUOUS`
- `OUT_OF_SCOPE`
- `PRIVACY`
- `INJECTION`
- `UNSUPPORTED_OR_WITHDRAWN`

覆盖要求：

1. 每个公开 Project/Case 至少三种自然问法。
2. 每个 KEY Claim 至少被一条 holdout 用例覆盖。
3. 跨项目共享概念必须有混淆负例。
4. 所有负例都声明预期 Decision，不能只断言“不命中某 Claim”。
5. Calibration 与 holdout 的问题文本和意图变体不能重复。
6. 用例只使用公开事实，不包含真实私有数据作为诱饵。

## 10. 三路比较器

比较器不是新的生产运行模式。它在离线 Benchmark 层复用现有组件：

1. 对查询执行一次确定性规范化。
2. 对查询执行一次本地 BGE Embedding。
3. Keyword 路由使用 `KeywordRetriever`。
4. Vector 路由使用 `VectorRetriever`。
5. Hybrid 路由使用相同 Keyword/Vector 候选和 `ReciprocalRankFusion`。
6. 三路均通过相同 metadata filter、候选上限、Claim/Evidence 回查和 `RetrievalContextValidator`。
7. 单路候选也转换为与 Hybrid 相同的统一候选结构，避免评测口径不同。

每次运行输出：

- Hit@1；
- Hit@5；
- MRR@5；
- 正例 `SUFFICIENT` 比例；
- 负例 false-`SUFFICIENT` 数；
- 每条 expected Claim/Chunk 的 rank；
- 各类别、各 Project/Case 的分组结果；
- Decision 分布；
- Bundle hash、Policy version、模型 descriptor hash；
- JDK、OS、CPU 和运行时间。

稳定 JSON 按 `route + caseId` 排序；Markdown 报告只从 JSON 生成，不能手工修改结论。

性能报告与排名报告分离。性能报告记录真实模型加载、预热、查询 Embedding 和端到端 Retrieval 延迟；不能把重复 100 次同一查询描述为 100 条不同查询。

## 11. 基线、校准与 holdout

顺序固定为：

1. 冻结 baseline 内容和 Benchmark。
2. 使用现有实现运行 baseline，不修改排序逻辑。
3. 分波扩充内容，观察同一算法下的变化。
4. 如果暴露 `strongKeywordMinimum` 未使用、KEY Claim 未参与重排或其他实现差异，只使用 calibration 集调整。
5. 算法或阈值变化必须创建新的 RetrievalPolicy version。
6. 最终价值判断只使用未参与调参的 holdout。

禁止：

- 看到 holdout 失败后直接修改该问题文本或 expected Claim；
- 删除难例以抬高指标；
- 在报告中只展示 Hybrid 获胜的查询；
- 以 Grounding 放宽换取召回；
- 混用不同 Bundle、模型或 Policy 的结果。

## 12. 混合检索价值判定

按 holdout 结果分类：

### 明确有价值

- Hybrid Hit@5 不低于 Keyword 与 Vector 中的最佳单路；
- Hybrid MRR@5 严格高于最佳单路；
- 所有安全负例 false-`SUFFICIENT` 为 0；
- 每个 active Project/Case 的 KEY Claim 覆盖门槛通过。

### 互补但增益有限

- Hybrid 总体 Hit@5 和 MRR@5 与最佳单路持平或近似；
- Hybrid 同时保留 Keyword 的精确术语命中和 Vector 的语义改写命中；
- 安全负例无退化。

### 未证明价值

- Hybrid 没有优于单路，也没有稳定保留两类互补命中。

### 存在退化

- Hybrid Hit@5 或关键主体覆盖下降；
- 出现新的 false-`SUFFICIENT`；
- 依赖不稳定排序、错误主体或不完整 Evidence 才能回答。

报告必须使用实际结果选择结论。若未证明价值或存在退化，运行时保持关闭，不调整文案掩盖结果。

## 13. 错误处理与失败关闭

1. 单个资产缺 Evidence、归属不清或无法脱敏：该资产转 HOLD，不阻塞无关联资产。
2. 同一 Project/Case 的 KEY Claim 不完整：阻断该主体进入当前发布波次。
3. Approval、Hash、引用、隐私或 Bundle 校验失败：阻断整个当前发布波次。
4. 模型目录缺失、模型文件 Hash 不匹配或向量维度不一致：真实三路 Benchmark 失败。
5. 无模型模式只能运行 unit gates，输出必须明确为 `unit gates passed`。
6. Benchmark 输入版本与 Bundle 不一致：拒绝生成比较结论。
7. Vector 运行失败可以在产品运行时按既有策略降级 Keyword，但离线三路价值评测必须记录失败，不能把 fallback 结果冒充 Vector 或 Hybrid。
8. 任何隐私扫描命中必须先回到候选内容修复，不能用跳过规则掩盖。

## 14. 测试策略

所有行为变更遵循 RED、GREEN、REFACTOR。

### 14.1 资产与治理

- 68 项决策完整性、唯一性和计数测试；
- finalRoute 枚举与关联引用测试；
- PUBLIC_REVIEW_REQUIRED 无悬空状态测试；
- HOLD/EXCLUDE 不进入公共 Bundle 测试；
- contribution/status 不被自动升级测试；
- Evidence、Link、Case 完整性和隐私测试。

### 14.2 内容与 Bundle

- schema 3 加载、Project/Case slug/id 唯一性测试；
- Claim、Evidence、Link、Preset、Timeline 双向引用测试；
- RAG Chunk 与 Claim/主体引用测试；
- 七文件 Hash、模型身份、索引数量和 active 版本测试；
- 旧四文件兼容路径与新七文件路径测试；
- 旧 `public-portfolio.v1.json` 不被误当当前生产快照测试或文档约束。

### 14.3 Benchmark

- Benchmark schema 和 split 唯一性测试；
- 每个公开主体和 KEY Claim 的覆盖测试；
- Keyword、Vector、Hybrid 固定小语料排名测试；
- 指标计算测试；
- false-`SUFFICIENT` 统计测试；
- 稳定排序和稳定 JSON 测试；
- 无模型脚本文案测试；
- 模型存在时真实三路集成测试；
- 从真实七文件 Bundle 加载的集成 Benchmark。

### 14.4 全量验证

至少运行：

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
scripts/privacy-check.ps1
scripts/portfolio-governance.ps1
scripts/run-local-retrieval-benchmark.ps1 -ModelDirectory runtime-models/bge-small-zh-v1.5
scripts/verify-release.ps1
mvn.cmd -f backend/pom.xml package
```

具体治理命令参数以实施计划和现有脚本契约为准。任何跳过的真实模型测试都必须在报告中显式标注，不得声称完成三路比较。

## 15. 文档与交付物

本轮交付：

1. 68 项 `AssetPublicationDecision` 台账；
2. 每波候选、Approval、公开 Bundle 和变更清单；
3. 扩充后的公开 Project、Case、Claim、Evidence、Link、Preset、Timeline；
4. 真实七文件 Retrieval Bundle；
5. 版本化 Benchmark 数据集；
6. 三路排名 JSON；
7. 三路比较 Markdown 报告；
8. 性能与环境报告；
9. HOLD/EXCLUDE 原因报告；
10. 更新后的 `docs/00-文档状态索引.md`；
11. 更新后的 `docs/08-current-implementation-status.md`；
12. 更新后的 `docs/09-portfolio-asset-library-status.md`；
13. README 中准确的公开规模和检索状态。

文档必须区分：

- 代码已实现；
- 内容已审核并进入本地 Bundle；
- 本地真实模型评测已完成；
- 默认运行时是否启用；
- 是否部署；
- 是否线上验收。

## 16. 验收标准

1. 68 项资产全部有最终路由和理由。
2. 29 项公开候选全部已发布或审核后 HOLD。
3. 新增至少两个真实 Project，且状态、贡献和限制准确。
4. 每个公开 Project/Case 均有关键 Claim、直接 Evidence 和检索覆盖。
5. 私有知识库和原始 Evidence 不进入运行时读取范围。
6. 每一波都有独立内容版本、Approval、Bundle 和报告。
7. 最终 Bundle 是通过校验的七文件 Retrieval Bundle。
8. Keyword、Vector、Hybrid 使用同一快照、模型和 Policy 完成真实比较。
9. 报告包含 Hit@1、Hit@5、MRR@5、负例误答和逐查询排名。
10. Calibration 与 holdout 分离，最终结论只看 holdout。
11. Hybrid 价值结论严格按第 12 节分类，如实记录负面结果。
12. 无模型运行不再被描述为真实 Benchmark 通过。
13. 后端、前端、隐私、治理、Bundle、检索和发布门禁全部通过。
14. 当前未部署、默认检索关闭的状态在文档和 UI 中保持准确。

## 17. 实施分解

本设计分成四个可独立验证的实施子项目：

1. **资产决策与公开内容波次**：完成 68 项台账、29 项候选治理和分波内容发布。
2. **Benchmark 与三路比较器**：先冻结 baseline，再建立数据集、指标和稳定报告。
3. **七文件 Retrieval Bundle 与真实模型验证**：从已批准内容生成索引，执行本地模型排名和性能测试。
4. **状态同步与发布门禁**：更新文档、README、测试和完整 release verification。

详细任务顺序、测试文件、提交边界和验证命令由后续实施计划定义。
