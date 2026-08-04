# Preset Contract 双取证策略设计

**日期：** 2026-08-04
**状态：** 已完成方案讨论，待书面审阅
**适用范围：** `PortfolioIntelligence` 正式推荐问题、自由问题与连续追问的事实选择、回答构造和失败语义

## 1. 背景与问题

现有系统已经将作品集语义收敛到单一 `PortfolioIntelligence` 内核，并支持稳定的 `questionPresetId`、canonical/alias 匹配、主体约束、公开证据检索与引用输出。但当前正式 Preset 在解析成功后仍被转换为 `FACT_LOOKUP + SUBJECT_SCOPED_RELEVANCE`：运行时只保留主体和 `preferredClaimCategories`，再由关键词或向量检索重新决定 Claim。

这形成了语义冲突：正式推荐问题已经声明自己是确定性入口，但其事实集合仍由不确定的相关性搜索决定。Embedding 默认关闭、关键词召回不足、分类范围过宽或多个 Claim 得分接近时，检索会返回空结果或歧义；上层又把所有空 Evidence 统一映射成 `NOT_SUPPORTED`，最终向用户显示“当前公开内容中没有足够的已验证材料”。

问题不在于公开资料实际缺失，而在于任务身份与事实选择方式没有对齐。

## 2. 设计目标

本设计实现以下目标：

1. 正式推荐区中的每个 Preset 都是“发布即承诺可回答”的可执行契约。
2. Preset 的核心事实选择不依赖 BM25、向量检索、Embedding 开关或运行时分数阈值。
3. 自由问题继续保留相关性检索、主体歧义识别和探索能力。
4. 两种策略共享同一个 `PortfolioIntelligence`、公开内容快照、证据政策、答案构造器、引用校验器和 `PortfolioDecision` 出口。
5. Contract 失败归为内容或系统异常，不再伪装成用户问题或公开资料不足。
6. 连续追问能继承上一轮主体和事实引用，同时允许用户询问同一主体的新方面或切换主题。
7. 表达模型只能改写已选择事实，不能新增、替换或重新检索事实。

## 3. 非目标

本设计不做以下事情：

- 不创建第二个 Agent 或第二套作品集业务内核。
- 不创建独立的 Contract 数据仓库；Contract 仍属于公开发布快照。
- 不允许 Preset 失败后静默降级为相关性搜索。
- 不把全部自由问题强行匹配到最相似的 Preset。
- 不允许 Answer Composer 访问 Claim Repository、Evidence Repository、BM25、Embedding 或 Preset Store。
- 不改变“运行时只能读取已审核公开快照”的产品边界。
- 不引入外部动态发布、私有资料搜索或未经审核的事实来源。

## 4. 总体架构

系统继续保持一个深模块和一个稳定入口：

```text
ConversationAnswerRequest
  -> 全局安全与输入校验
  -> PortfolioIntelligence.tryResolve(PortfolioTurn)
  -> Task Resolver
       -> ContractEvidenceSelector
       -> RelevanceEvidenceSelector
  -> Evidence Policy
  -> FactBundle Builder
  -> Answer Composer
  -> Citation Validator
  -> PortfolioDecision
```

分叉只发生在 Evidence Selection：

```text
确定性事实选择
  - PRESET_CONTRACT
  - REFERENCE_SCOPED

相关性事实选择
  - SUBJECT_SCOPED_RELEVANCE
  - GLOBAL_RELEVANCE
```

`REFERENCE_SCOPED` 和 `SUBJECT_SCOPED_RELEVANCE` 是约束方式，不形成新的业务内核。两类 Selector 必须返回统一的成功结构，后续模块不得根据输入来源复制答案逻辑。

## 5. Preset Contract

### 5.1 领域模型

正式 Preset 在公开发布快照中编译为不可变契约：

```text
PresetContract
  presetId
  contractVersion
  canonicalText
  aliases
  subjectId
  requiredClaimIds
  supportingClaimIds
  evidenceRequirement
  status
```

字段语义如下：

- `presetId`：跨版本稳定且不可复用的身份。
- `contractVersion`：由会影响执行语义的规范化字段确定性生成；相同输入必须生成相同版本。
- `canonicalText`：正式展示与 canonical 匹配文本。
- `aliases`：唯一登记的等价表达；仅命中 canonical 或 alias 的手输文本才升级为 Contract。
- `subjectId`：Contract 唯一主体；第一版不支持跨主体 Preset Contract。
- `requiredClaimIds`：回答必须覆盖的核心事实，不能为空。
- `supportingClaimIds`：允许用于增强叙述的可选事实。
- `evidenceRequirement`：必要 Claim 的最低公开证据要求。
- `status`：`DRAFT | ACTIVE | SUSPENDED | RETIRED`。

第一版 `evidenceRequirement` 至少包含：

```text
minimumApprovedEvidencePerRequiredClaim >= 1
publicOnly = true
```

Contract 精确绑定 Claim ID，但默认不绑定 Evidence ID。Claim 表达稳定事实，Evidence 是证明该事实的当前公开材料；允许在不改变事实语义时替换、补充或重新审核 Evidence。第一版不提供 `pinnedEvidenceIds`。

`preferredClaimCategories` 可以保留为内容编写或自由检索提示，但不得参与 Contract 核心事实选择和充分性判断。

### 5.2 强契约含义

`ACTIVE` 表示系统已经承诺：

- 主体身份唯一且公开；
- 所有必要 Claim 存在、已验证并属于该主体；
- 每个必要 Claim 都满足最低 `APPROVED + PUBLIC` Evidence 要求；
- canonical/alias 不与其他 Active Contract 冲突；
- Bundle 和 PostgreSQL 两种公开数据适配器能够得到一致的必要 Claim 集合；
- Embedding 是否可用不影响 Contract 可答性。

只要任何条件失效，Contract 必须离开 `ACTIVE`，不能继续出现在正式推荐区。

## 6. 请求身份与任务解析

### 6.1 客户端请求

点击正式推荐问题时，客户端发送：

```text
questionPresetId
contractVersion
question
```

`questionPresetId + contractVersion` 是执行身份；`question` 只用于展示、运行时一致性校验和防止陈旧 UI 误调用，不作为事实选择依据，也不得因该校验被服务端持久化或写入日志。客户端不得发送 `requiredClaimIds` 或 Evidence ID 来决定 Contract 内容。

### 6.2 解析优先级

任务解析严格采用以下顺序：

1. 存在 `questionPresetId`：加载对应 Contract，校验版本、文本、主体提示和 `ACTIVE` 状态。
2. 不存在 `questionPresetId`：仅当规范化文本唯一命中某个 `ACTIVE` Contract 的 canonical 或已登记 alias 时，解析为 Contract；非 Active Contract 不参与文本匹配。
3. 未可靠命中 Contract：形成自由问题任务，进入 Relevance 策略。
4. ID 存在但版本、文本、主体或状态冲突：返回 Contract 异常，不允许静默降级为搜索。

客户端遇到陈旧版本时自动刷新公开内容并无感重试一次。重试后仍失败，向用户显示推荐问题正在更新，并停止本次调用。

## 7. 双取证策略

### 7.1 统一接口

两种策略消费不同的任务类型，但成功时必须返回统一的 Evidence Selection：

```text
EvidenceSelection
  taskIdentity
  contentSnapshotVersion
  subjectIds
  selectedClaimIds
  selectedEvidenceIds
  requiredClaimIds
  selectionPolicy
  selectionReasons
```

上层不能通过可选字段随意拼装非法状态。Contract 与 Relevance 任务使用显式构造入口，分别保证各自不变量。

### 7.2 ContractEvidenceSelector

Contract Selector 的确定性执行顺序是：

1. 按 `presetId + contractVersion` 读取 `ACTIVE` Contract。
2. 按 `subjectId` 精确限定主体。
3. 按 `requiredClaimIds + supportingClaimIds` 获取已验证 Claim。
4. 为每个 Claim 解析当前 `APPROVED + PUBLIC` Evidence。
5. 验证所有必要 Claim 均存在且满足最低 Evidence 数量。
6. 仅把 Contract 中声明的 Claim 写入 Evidence Selection。

该流程不得调用 BM25、向量召回或相关性分数门槛。Supporting Claim 缺失时允许缩短回答，但不得以 Supporting Claim 替代 Required Claim。

第一版 Contract 使用严格 `CONTRACT_ONLY` 模式，不提供运行时相关性补充。未来如需扩展，必须形成新的已批准设计；补充事实也不能改变 Contract 是否成立或替换 Required Claim。

### 7.3 RelevanceEvidenceSelector

自由问题的执行顺序是：

1. 解析意图、主体提示和问题条件。
2. 应用已验证的主体范围；主体范围只缩小候选，不替代相关性判断。
3. 根据运行能力选择关键词或混合召回。
4. 进行相关性、主体一致性、Evidence Policy 和歧义判断。
5. 返回 `SUFFICIENT | AMBIGUOUS | INSUFFICIENT | OUT_OF_SCOPE | INVALID_INPUT`。

自由检索允许因运行能力不同而改变召回质量，但必须保留准确的失败原因，不得把数据源异常映射成证据不足。

## 8. 连续追问

每次回答保存一个只含公开稳定标识的 `AnswerContext`：

```text
answerId
sourceTaskKind
questionPresetId?
contractVersion?
contentSnapshotVersion
subjectIds
selectedClaimIds
selectedEvidenceIds
```

追问先经过 Reference Resolver：

- 指向上一轮已有事实的解释、展开或重写：使用 `REFERENCE_SCOPED`，只读取已有 Claim 和 Evidence。
- 在同一主体下询问新的方面：继承 `subjectId`，转为 `SUBJECT_SCOPED_RELEVANCE`。
- 明确切换主体：重新执行 Preset/自由问题解析。

第一轮来自 Preset 不会把整场对话永久锁定在原 Contract。Contract 只约束该次正式问题的核心事实集合。

## 9. FactBundle、答案构造与引用验证

Evidence Policy 通过后生成不可变 `FactBundle`，至少包含：

```text
taskIdentity
contentSnapshotVersion
subjectIds
claims[claimId, assertion, importance]
evidence[evidenceId, claimId, publicExcerpt, sourceLabel, approvalStatus]
disposition
```

Answer Composer 只能：

- 调整已选事实的顺序、详略和自然语言表达；
- 合并重复陈述；
- 将 FactBundle 中的 Evidence 引用绑定到对应陈述。

Answer Composer 不能：

- 新增或替换 Claim、Evidence、主体或 disposition；
- 删除 Contract Required Claim；
- 使用模型常识补充作品集事实；
- 访问检索器、内容仓库或 Preset Store。

Citation Validator 必须正向验证每个答案事实的引用，也必须反向验证每个 Required Claim 是否被答案覆盖且至少有一个合格引用。

首次校验失败时，允许 Composer 基于原 FactBundle 修复一次。第二次仍失败时按生成系统异常处理；不得通过删除全部引用或改写为“公开资料不足”强行完成。

## 10. 失败语义与用户交互

### 10.1 Contract 结果

Contract 只允许：

- `CONTRACT_SUFFICIENT`：正常回答。
- `CONTRACT_STALE`：客户端刷新内容并自动重试一次。
- `CONTRACT_UNAVAILABLE`：显示“这个推荐问题正在更新，暂时无法回答”。
- `CONTRACT_INVALID`：记录内容运营告警并使推荐项下线。

Contract 不产生 `AMBIGUOUS`、`OUT_OF_SCOPE` 或面向用户的普通 `INSUFFICIENT`。这些条件在发布时已经被 Contract 消除；运行时出现说明内容或系统状态异常。

### 10.2 Relevance 结果

- `SUFFICIENT`：正常回答并展示引用。
- `AMBIGUOUS`：展示具体候选主体或解释方向，让用户选择。
- `INSUFFICIENT`：说明已经确认的部分以及缺少的公开证据。
- `OUT_OF_SCOPE`：说明作品集未覆盖该信息范围。
- `INVALID_INPUT`：说明输入问题，不伪装成资料不足。

只有同时满足“自由问题、主体明确、范围合法、检索正常、相关 Claim 可确定、公开 Evidence 确实不足”时，才允许使用证据不足语义。

### 10.3 最终 Decision

不得继续使用 `evidence.isEmpty()` 推导全部业务状态。Selector 和 Validator 先产生类型化结果，再映射到 `PortfolioDecision`。`PortfolioDecision` 继续是 `PortfolioIntelligence` 对外唯一业务出口；`ConversationalAgentRuntime` 不得重新解释作品集任务和失败原因。

## 11. 发布生命周期

Preset 发布流程如下：

```text
DRAFT
  -> canonical/alias 唯一性
  -> subjectId 存在且唯一
  -> requiredClaimIds 非空且全部属于主体
  -> supportingClaimIds 合法且不与 required 重复
  -> Required Claim 均为 VERIFIED
  -> Required Claim 均满足 APPROVED + PUBLIC Evidence 最低数量
  -> Bundle/PostgreSQL 契约一致性检查
  -> 生成 contractVersion
  -> ACTIVE
```

Evidence 被撤销、Claim 状态改变、主体下线或适配器一致性检查失败时，对应 Contract 转为 `SUSPENDED`，并从正式推荐区移除。`RETIRED` 表示内容所有者明确终止该入口，Preset ID 不得复用。

当前公开 bundle 中的现有 Preset 分批迁移：

- 已补齐 Contract 并通过门禁的 Preset 标记为 `ACTIVE`。
- 未迁移或校验失败的 Preset 保留在内容仓库，但不出现在正式推荐区。
- 用户手动输入未激活 Preset 的旧文本时，按普通自由问题处理，不赋予 Contract 承诺。

## 12. 前端影响

公开 `QuestionPreset` DTO 只需新增：

```text
contractVersion
availability
```

后端不向客户端暴露 Required Claim 和内部 Evidence 策略。前端行为如下：

- 点击 Active Preset 时发送 ID、版本和展示文本。
- 普通自由输入不携带上一轮 `questionPresetId`。
- 陈旧版本自动同步并重试一次。
- Suspended/Retired Preset 不出现在推荐区。
- Contract 异常、自由问题歧义、真正证据不足和越界采用不同文案。
- 对已有答案的追问发送公开 Reference Context，不持久化访客问题或答案。

## 13. 可观测性与隐私

诊断事件至少记录以下非敏感维度：

```text
taskKind
selectionPolicy
presetId?
contractVersion?
contentSnapshotVersion
retrievalSource
failureStage
failureCode
requiredClaimCount
selectedClaimCount
citationValidationResult
```

不得记录用户原始问题、完整答案、原始 Evidence 内容、私有路径或未公开资料。运营指标必须区分用户输入问题、内容契约问题、检索基础设施问题和生成/引用问题。

## 14. 代码影响边界

本设计预计影响：

- `portfolio.domain.QuestionDefinition` 与公开 bundle JSON 契约；
- `PortfolioSnapshotValidator` 和发布验证 CLI；
- `answer.domain.AnswerQuestion` 的运行时映射；
- `PortfolioPresetResolver`、`PortfolioTask` 或新的显式 `ContractTask`；
- `PortfolioRetrievalStrategy` 与 `PortfolioRetrievalRequest` 工厂；
- Bundle/PostgreSQL Retriever 的精确 Contract 分支；
- `DefaultPortfolioIntelligence` 的 Selector 编排与失败映射；
- `PortfolioIntelligenceAnswerAssembler` 或其后继 FactBundle/Composer 边界；
- `ConversationAnswerRequest`、公开 Preset DTO、前端 `answerApi` 和交互状态；
- 对应单元测试、适配器契约测试、发布门禁测试和 Playwright 验收测试。

现有 `requiredClaimIds`、Bundle `exactPassages`、PostgreSQL `retrieveExact`、Reference Context、Preset canonical/alias 解析与 `PortfolioDecision` 出口应优先复用。不得为 Contract 复制第二套存储、检索适配器或 Answer Runtime。

## 15. 测试与验收

### 15.1 发布门禁

每个 `ACTIVE` Preset 必须验证：

- ID、版本、canonical 和 alias 唯一；
- 主体唯一且公开；
- Required Claim 非空、存在、已验证且主体一致；
- 每个 Required Claim 满足 Evidence 最低数量；
- Supporting Claim 合法；
- Contract Version 确定性生成；
- 无效 Contract 无法进入公开推荐列表。

### 15.2 双适配器一致性

同一个 Active Contract 通过 Bundle 与 PostgreSQL 时必须得到相同的主体、Required Claim 集合、充分性结果和最终 disposition。Passage 排序或 Evidence 展示顺序可以不同，但不能改变事实覆盖。

### 15.3 运行模式矩阵

所有 Active Preset 必须在以下条件下保持相同的 Required Claim 集合并成功回答：

- Embedding Disabled；
- Keyword Only；
- Hybrid；
- PostgreSQL 不可用并安全降级到 Bundle。

自由问题分别验证 `SUFFICIENT`、`AMBIGUOUS`、`INSUFFICIENT`、`OUT_OF_SCOPE` 和检索基础设施异常。

### 15.4 连续对话

验证已有事实展开、同主体新问题、显式切换主体、陈旧 Reference Context 更新和非法 Reference Context 拒绝。

### 15.5 前端验收

验证 Preset 请求携带 ID 和版本、canonical/alias 行为一致、陈旧版本只重试一次、暂停项隐藏、自由输入不携带 Preset 身份、追问携带 Reference Context，以及不同失败类型显示准确文案。

## 16. 风险与控制

- **迁移工作量：** 通过分批激活控制；未迁移项不得继续享受正式推荐身份。
- **Contract 漂移：** 通过确定性版本、发布门禁和运行时状态校验控制。
- **适配器结果不一致：** 通过 Bundle/PostgreSQL 契约测试阻断发布。
- **别名冲突：** canonical/alias 全局唯一校验，冲突时两个 Contract 都不得激活。
- **Composer 越权：** 通过不可变 FactBundle、依赖边界和双向 Citation Validator 控制。
- **错误语义再次合并：** Selector 使用类型化结果，禁止从空列表反推失败原因。

## 17. 完成标准

本设计完成实现后，必须满足：

1. 正式推荐问题始终走 `PRESET_CONTRACT`，不调用相关性检索。
2. 手输 canonical/alias 与点击同一 Preset 得到相同 Contract 身份和必要 Claim 集合。
3. 所有 Active Preset 在运行模式矩阵下保持确定性可答。
4. Contract 失效不会向用户显示公开资料不足，也不会静默降级搜索。
5. 自由问题保留相关性探索和准确失败语义。
6. 追问能够正确选择 Reference Scoped 或 Subject Scoped Relevance。
7. Composer 不能改变 FactBundle 的 Claim、Evidence、主体或 disposition。
8. Bundle 和 PostgreSQL 共享统一接口并通过一致性测试。
9. 未契约化 Preset 不出现在正式推荐区。
10. 运行时继续只读取审核过的公开快照，并保持现有隐私约束。
