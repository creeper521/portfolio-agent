# PostgreSQL / pgvector 资产包治理与组合推荐设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

**日期：** 2026-07-30
**状态：** 已完成产品讨论并获确认
**目标仓库：** `D:\code\agent`

## 1. 背景与问题

当前公开运行时从不可变文件 Bundle 加载 `Project`、`Case`、`Claim`、`Evidence` 和检索索引。现有 RAG 会先解析单个 Project 或 Case，再在该主体内检索多个 Claim，因此适合回答“这个项目做了什么”，但不适合回答：

> 给定求职方向、访客身份或能力目标，从多个 Project/Case 中组合一套互补、可信、可解释的资产包。

单纯把向量索引替换为 pgvector 只能改善候选召回和索引治理，不能自动建立资产间的互补关系。目标系统需要同时解决：

1. PostgreSQL 中的结构化关系、版本与发布治理；
2. 全文检索和 pgvector 的跨主体候选召回；
3. 依据能力覆盖、证据质量、多样性和冗余进行组合选择；
4. 私有 Markdown 增量导入、人工审核与公开发布隔离；
5. 用冻结数据集定量证明每一层演进的价值。

## 2. 设计原则

1. **相似不等于互补。** 向量只负责召回候选，不能决定最终资产包。
2. **证据优先。** 未获批准或缺少 Claim/Evidence 支撑的能力不得参与公开推荐。
3. **发布不可变。** 运行时只读取已批准 Release，不直接读取草稿或治理表。
4. **私有与公开分区。** 原始 Markdown、私有向量和审核过程不得进入公网运行环境。
5. **确定性决策。** V1 由可重放的评分策略决定组合，LLM 只负责解释。
6. **失败不破坏旧版本。** 导入、向量化或发布失败均不得改变当前有效数据。
7. **小步演进。** 保留现有文件 Bundle 兼容能力；数据库能力通过配置显式启用。

## 3. 固定领域语言

### 3.1 ReleaseBundle

经过规范化、批准、编译和校验的不可变发布产物。它代表某一时刻公开数据的完整快照，不是动态推荐结果。

### 3.2 PortfolioSelection

根据目标、访客身份和约束动态计算出的推荐集合，默认包含 3 个、允许 2–5 个 `PortfolioSubject`。

### 3.3 PortfolioSubject

可被推荐的公开主体，类型为 `PROJECT` 或 `CASE`。Project 与 Case 保留各自领域模型，通过该统一抽象进入召回和组合算法。

### 3.4 Capability

受控、可解释的能力词表。embedding 表示语义相似度；Capability 表示资产能够用批准证据证明的能力。模型或规则可以提出标签建议，但只有经人工确认并关联支持 Claim 的能力才可发布。

## 4. 总体架构

```text
Private Markdown
  │ manual scan/import + dry-run
  ▼
portfolio_governance
  source document/revision/chunk/private vector
  draft Project/Case/Claim/Evidence
  capability suggestion/review/approval
  │
  │ APPROVED → canonicalize → hash → compile → verify
  ▼
ReleaseBundle
  │ publish import (one-way)
  ▼
portfolio_public
  approved release snapshot
  public text search + public vector
  capability/constraint projection
  │
  ├─ hybrid candidate retrieval
  ├─ deterministic portfolio selection
  └─ evidence-grounded explanation
```

后端保持模块化单体，新增四个清晰边界：

- `ingestion`：扫描、差异、解析、分块、增量向量化；
- `portfolio`：治理实体、关系、Release 和公开查询；
- `selection`：候选召回、组合评分和解释输入；
- `release`：规范化、哈希、编译、校验、发布和回滚。

## 5. 数据库与安全边界

### 5.1 私有治理库

数据库名建议为 `portfolio_governance`。包含：

- `source_document`
- `source_revision`
- `source_chunk`
- `source_link_suggestion`
- 草稿及审核中的 Project、Case、Claim、Evidence
- `subject_capability`
- 审核、批准和发布编译记录

角色至少分为 `portfolio_importer`、`portfolio_reviewer` 和 `portfolio_release_compiler`。公网运行时不得拥有该数据库凭据。

### 5.2 公开运行库

数据库名建议为 `portfolio_public`。只包含：

- `content_release`
- `portfolio_subject`
- `project_profile`
- `case_study`
- `claim`
- `evidence`
- `claim_evidence_link`
- `subject_capability`
- `retrieval_document`
- `active_release`

公开角色 `portfolio_runtime_reader` 只读。生产公网环境只部署该数据库及其备份，不部署私有治理库、原始 Markdown 或私有 embedding。

### 5.3 环境拓扑

本地开发允许两个数据库共用一个 PostgreSQL 实例，但必须使用不同 database 和 role。仅靠同一数据库中的 schema 隔离不作为生产方案。

## 6. Release 版本模型

版本单位是完整 Release，而不是各实体独立的时间版本。

```text
DRAFT
→ VALIDATED
→ CANONICALIZED
→ APPROVED
→ COMPILED
→ VERIFIED
→ PUBLISHED
```

- 每个公开实体和关系都带 `release_id`；
- `APPROVED` 后禁止原位修改，变更必须创建新 Release；
- `active_release` 是运行时唯一活动指针；
- 发布事务先导入并验证完整快照，最后原子切换指针；
- 回滚只切换到一个已经验证过的 Release；
- 任意查询必须固定一个 `release_id`，禁止跨版本拼接。

## 7. Markdown 增量导入

V1 使用显式命令或管理操作，不监听文件系统：

```text
scan --dry-run
→ preview ADDED/CHANGED/UNCHANGED/MISSING/FAILED/BLOCKED
→ import
→ parse
→ chunk
→ embed changed chunks
→ review suggestions
```

规则：

- 源文件以稳定相对路径和内容哈希识别；
- 仅变更文档重新解析，仅变更分块重新生成 embedding；
- `MISSING` 只标记缺失，不立即硬删除；
- 每个文档一个事务，单文档失败不回滚整个批次；
- 解析失败保留上一有效 revision；
- embedding 失败标记 `VECTOR_PENDING`，文本和结构化审核仍可继续；
- 导入运行结果为 `SUCCESS`、`PARTIAL` 或 `FAILED`；
- 原始文档只是私有输入，不自动成为公开 Project/Case/Claim/Evidence；
- 所有公开映射建议都必须人工确认。

## 8. 混合候选召回

召回输入包括目标文本、求职方向、访客身份、能力目标、数量和必要约束。

V1 流程：

1. 用结构化字段过滤不合格主体；
2. PostgreSQL FTS 计算关键词相关性；
3. pgvector 计算语义相似度；
4. 用 RRF 融合两个排名；
5. 返回最多 12 个候选供组合算法使用。

当前数据只有 79 个公开检索向量，默认使用 pgvector 精确搜索。HNSW 或 IVFFlat 只有在基准证明精确搜索不能满足数据规模或 p95 延迟时才引入。

pgvector 不可用时：

- 对具有明确结构化目标的请求，降级为 Capability + FTS；
- 无法安全确认目标时返回暂时不可用；
- 不允许以弱结果伪装成功。

## 9. 组合选择算法

### 9.1 约束

- 默认选择 3 个；
- 调用者可请求 2–5 个；
- 只能选择当前 Release 中已发布且证据合格的主体；
- 相同资产不得重复；
- 不满足硬约束的候选在评分前排除；
- 没有足够合格资产时返回较少结果和 `INSUFFICIENT`，不得凑数。

### 9.2 V1 策略

Top 12 候选在选择 3 个时只有 `C(12,3)=220` 种组合，V1 直接穷举，获得稳定且可解释的最优结果。

组合评分由版本化策略提供：

```text
score =
  targetFit
  + capabilityCoverage
  + evidenceQuality
  + diversity
  - redundancy
  - conflictPenalty
```

必须保留每个分量及选择原因，使用稳定 ID 作为最终 tie-break，确保相同 Release、输入和策略产生相同结果。

### 9.3 演进接缝

定义小型 `SelectionStrategy` 接口，V1 存在两个真实实现：

- `TopKSelectionStrategy`：基准和降级；
- `ExhaustiveSelectionStrategy`：V1 正式策略。

未来的 greedy、beam search、LLM reranker 或 learning-to-rank 只能通过同一接口加入，并携带独立 `policyVersion`。不提前建设通用插件注册中心、知识图谱或在线学习。

升级条件至少满足一项：

- 穷举触及已定义的 p95 延迟或候选规模上限；
- 新策略在冻结 Holdout 上无质量回退；
- 已积累足够的人工成对偏好数据；
- 隐私、证据和跨 Release 安全门保持零失败。

## 10. 输出契约

后端组合推荐响应至少包括：

- `selectionId`
- `releaseVersion`
- `policyVersion`
- `retrievalMode`
- `selectionMode`
- `status`
- 选中的 2–5 个 PortfolioSubject
- 每项的入选原因和证据引用
- 整体能力覆盖
- 资产之间的互补说明
- 可选替代项
- 降级或不足原因代码

LLM 只接收已经确定的选择及获准公开的证据。LLM 失败时由确定性模板生成说明，不能改变入选资产。

## 11. 故障与降级

- 数据库或 pgvector 连接失败不得影响现有公开作品浏览；
- 单文档导入失败保留旧 revision；
- 发布编译或校验失败不切换 `active_release`；
- 任一未批准 Evidence、私有路径或原始文档进入公开快照时发布必须失败；
- 召回可以从 hybrid 降级到 FTS，但输出必须暴露实际模式；
- LLM 失败只影响语言表达；
- 推荐不足时返回 `INSUFFICIENT`，不放宽证据门；
- 所有错误日志必须遵守现有脱敏约束，不记录访客问题正文。

## 12. 评测设计

使用同一 Release、同一 embedding 模型和同一冻结测试集比较：

- `R0`：当前单主体内存 RAG，无组合；
- `R1`：结构化过滤 + FTS；
- `R2`：结构化过滤 + pgvector；
- `R3`：FTS + pgvector + RRF，TopK 无组合；
- `R4`：混合召回 + 穷举组合。

指标：

- 迁移：实体数、关系数、字段和哈希一致性；
- 召回：Recall@12、Hit@1/5、MRR、nDCG、false-sufficient rate；
- 组合：能力覆盖、冗余、证据有效率、盲测成对偏好；
- 工程：p50/p95、增量导入耗时、重算向量数、发布和回滚时间。

测试集分为 Calibration、Holdout 和 Regression。组合样例记录约束与多个可接受集合，不把单一精确组合当作唯一真值。

不可妥协的安全门：

- 实体和关系迁移完整率 100%；
- 未批准或私有内容泄漏为 0；
- 无证据推荐为 0；
- 跨 Release 混合为 0；
- 发布失败后活动版本变化为 0。

质量阈值应先测 R0–R4 基线，再根据结果确定，不能预先猜测一个漂亮数字。

## 13. 兼容迁移顺序

1. 固化当前 Bundle 和基准数据；
2. 引入数据库依赖、迁移脚本和端口，但默认仍使用文件仓储；
3. 将 Bundle 导入 `portfolio_public`，验证行数、关系和哈希；
4. 增加数据库公开仓储，在测试环境双读比较；
5. 接入 FTS/pgvector 召回和 R0–R3 消融；
6. 接入 SelectionStrategy 与 R4；
7. 增加治理库 scan/import 和 Release 发布；
8. 基准、安全门和回滚通过后才允许切换默认运行仓储。

## 14. 明确不在本次后端范围

- 前端视觉、交互和组件开发；
- 文件系统自动监听；
- 自动批准或自动发布；
- 让公网运行时查询私有知识库；
- HNSW/IVFFlat 的无基准启用；
- 知识图谱、在线学习和通用算法插件平台；
- 让 LLM 直接决定最终资产包。

前端潜在变更点由独立 handoff 文档定义，交由其他 AI 设计和开发。

## 15. 当前仓库基线提醒

实际 Bundle 已是 schema `4.0`、content version `2026-07-29.1`，包含 5 个 Project、49 个 Case、3 个 Collection、79 个 Claim、59 个 Evidence、79 条 Claim-Evidence 关系及 79 个 RAG chunk。部分状态文档仍写 schema `3.0`、7 个 Project、81 个 Claim 和旧版本号；迁移验证必须以实际经过校验的 Bundle 为基线，并同步修正文档漂移。
