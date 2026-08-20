# Preset Contract 端到端闭环纠偏设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

**日期：** 2026-08-05  
**状态：** 已完成方案讨论，待书面审阅  
**适用范围：** Preset Contract 发布治理、公开 DTO、前端请求身份、自由问题与 suggestion 主体路由、Bundle/PostgreSQL 一致性和真实运行验收  
**前置设计：** `docs/superpowers/specs/2026-08-04-preset-contract-dual-evidence-selection-design.md`

## 1. 背景与已验证问题

2026-08-04 的双取证设计规定：正式推荐问题必须以 `PRESET_CONTRACT` 选择确定性 Claim 集合，普通自由问题和连续追问继续使用相关性选择。实现曾在提交 `53ed85f` 中向运行 bundle 写入 15 个 Active Contract，但当前 `HEAD` 已退化为 0 个 Active Contract。

回归由两条独立链路共同造成。

第一条是发布内容回退：

1. `53ed85f` 在 bundle 中写入 15 个 Active Contract。
2. 后续内容分支 `b86e2c9` 基于更早的 `35c4fb2` 重生成整份 bundle，没有保留 Contract 字段。
3. 合并提交 `7c43f79` 接受了该旧基线生成物。
4. `QuestionDefinition` 将缺失的 Contract 字段兼容为“空 Claim 列表 + DRAFT”，应用没有启动失败。
5. 公共映射没有过滤 DRAFT preset，前端仍把它显示在正式推荐区。
6. 前端只为 Active preset 发送 ID 和版本，因此点击 DRAFT 项时请求退化为自由文本。

第二条是结构化主体失效：

1. 系统生成 suggestion 时携带公开 `projectSlug` 或 `caseSlug`。
2. 运行时仍要求问题字面包含“这个项目 / 该项目 / 本项目”等指代词，才承认已有主体上下文。
3. “测试角色重置工具的背景和目标是什么？”虽然携带 `projectSlug=role-reset-tool`，但不命中字面指代规则。
4. 模型能力关闭时，该请求被判为 `NOT_PORTFOLIO`，最终返回 `GENERAL + CAPABILITY_UNAVAILABLE + TEMPLATE`。

真实运行重放已稳定得到：

```text
SQL 正式问题
  -> NOT_SUPPORTED / EVIDENCE_COMPOSITION / PORTFOLIO

角色重置 suggestion
  -> CAPABILITY_UNAVAILABLE / TEMPLATE / GENERAL
```

问题不只是某个 Selector 的局部实现，而是 Contract 在“治理声明、候选生成、发布验证、公共展示、客户端身份、运行路由、真实验收”之间没有形成闭环。

## 2. 设计目标

本设计实现以下目标：

1. 将 18 个单主体 preset 发布为确定性的 Active Contract。
2. 保持跨主体 `question-public-assets-overview` 为 DRAFT，且不进入公开推荐 DTO。
3. 让 Contract 元数据由治理声明投影生成，不再依赖旧 bundle 中是否碰巧保留字段。
4. 任何 Contract 字段、Active 集合或版本回退都在候选准备或发布前失败。
5. 将 Contract 执行主体从展示关联中显式分离，支持 ABTest preset 保留项目与 Case 导航关联。
6. 正式 preset 点击始终携带 ID、版本和主体提示，并始终走 `PRESET_CONTRACT`。
7. 普通 suggestion 不继承 Preset 身份，但已验证的结构化主体必须进入 `SUBJECT_SCOPED_RELEVANCE`。
8. 模型与 Embedding 默认关闭时，正式 preset 和带公开主体的 suggestion 仍保持可执行。
9. Bundle 与 PostgreSQL 对同一 Contract 返回相同 Required Claim 集合。
10. 使用真实生产 bundle 覆盖截图中的两步交互，避免 synthetic fixture 继续掩盖发布回归。

## 3. 非目标

本设计不做以下事情：

- 不创建第二个运行时 Contract 仓库。
- 不允许 Contract 失败后降级到 BM25、向量检索或模型分类。
- 不把普通 suggestion 冒充为正式 preset。
- 不允许结构化主体绕过相关性、Evidence Policy、歧义或公开状态校验。
- 不把项目及其全部 Case 自动视为一个可任意取证的主体闭包。
- 不允许 Answer Composer 重新检索或修改已选择的 Claim/Evidence。
- 不引入新的外部模型、数据库、认证或动态发布能力。
- 不修改现有公开事实的语义边界或夸大工作状态。

## 4. 核心原则

### 4.1 每类信息只有一个权威来源

- Claim、Evidence、主体和展示关系由公开事实候选及项目 patch 管理。
- Contract 的执行声明由治理 policy 管理。
- 候选 `portfolio.json` 是二者的确定性投影。
- 运行时只读取已审核、签名和发布的 bundle。

治理 policy 不是第二个运行时仓库。它不能保存断言正文、Evidence 摘要或私有来源，只声明公开快照中已有稳定 ID 之间的执行关系。

### 4.2 展示关联不等于执行主体

`projectIds`、`caseIds` 和 `placements` 用于导航、展示和入口关联；`contractSubjectId` 唯一决定 Contract 的执行主体。一个 preset 可以展示在项目与相关 Case 页面，但第一版 Contract 仍只允许一个执行主体。

### 4.3 生成物不能成为隐式配置

Active 状态、Required Claims 和 Supporting Claims 不能只存在于手工修改的 bundle 中。每次 `prepare-candidate` 都必须从 policy 重新投影 Contract 字段，并在投影后重新验证。

### 4.4 结构化主体是约束，不是答案授权

已验证的 `projectSlug/caseSlug` 足以进入主体约束相关性路径，但只缩小候选范围。Selector 仍必须完成相关性、公开状态、证据充分性和歧义判断。

## 5. 总体架构

```text
公开事实候选 + wave/项目 patch
                 +
preset-contract-policy.v1.json
                 |
                 v
portfolio-governance prepare-candidate
                 |
                 v
PresetContractProjector
                 |
                 v
候选 portfolio.json
                 |
                 +--> Schema / Privacy / Claim / Evidence 门禁
                 +--> Active Contract allowlist 门禁
                 +--> presetContractSetHash 门禁
                 |
                 v
检索编译 -> 评审 -> 签名 -> 发布
                 |
                 v
运行时 Bundle / PostgreSQL
                 |
                 +--> 公开 DTO：只输出 ACTIVE
                 +--> PRESET_CONTRACT
                 +--> SUBJECT_SCOPED_RELEVANCE
```

## 6. 治理 Contract Policy

新增：

```text
governance/portfolio-governance/policies/preset-contract-policy.v1.json
```

第一版结构：

```json
{
  "schemaVersion": "1.0",
  "activeContracts": [
    {
      "presetId": "sql-audit-overview",
      "contractSubjectId": "sql-audit-project",
      "requiredClaimIds": [
        "claim-sql-audit-background",
        "claim-sql-audit-responsibility",
        "claim-sql-audit-technical-decision",
        "claim-sql-audit-verification",
        "claim-sql-audit-delivered"
      ],
      "supportingClaimIds": [
        "claim-sql-audit-documented-handoff"
      ],
      "evidenceRequirement": {
        "minimumApprovedEvidencePerRequiredClaim": 1,
        "publicOnly": true
      },
      "status": "ACTIVE"
    }
  ],
  "nonPublicDraftPresetIds": [
    "question-public-assets-overview"
  ]
}
```

Policy 必须满足：

- `schemaVersion` 精确为 `1.0`。
- `activeContracts[].presetId` 全局唯一。
- `contractSubjectId` 非空且指向一个公开 Project 或 Case。
- `requiredClaimIds` 非空、唯一且保持声明顺序。
- `supportingClaimIds` 唯一且与 Required 集合不相交。
- `minimumApprovedEvidencePerRequiredClaim >= 1`。
- `publicOnly` 第一版必须为 `true`。
- `status` 第一版只允许 `ACTIVE`；Draft 清单单独声明。
- Active 与 Draft ID 集合不相交。
- Policy 不包含 canonical 文本、alias、Claim assertion 或 Evidence 摘要；这些继续来自公开候选。

## 7. Contract 领域模型纠偏

`QuestionDefinition` 增加：

```text
contractSubjectId?
```

字段语义：

- Active Contract 必须非空。
- DRAFT/SUSPENDED/RETIRED 可以为空。
- 必须命中 `projectIds` 或 `caseIds` 所关联的公开主体之一。
- Contract Version 使用该字段，不再通过“projectIds + caseIds 恰好只有一个”推断主体。
- `projectIds/caseIds` 继续保留展示关系，不参与执行主体唯一性判定。

Active Contract 的不变量：

```text
contractSubjectId exists
requiredClaimIds not empty
every required/supporting claim exists
every required/supporting claim.subjectId == contractSubjectId
every required claim satisfies evidenceRequirement
canonical/alias unique among Active Contracts
```

该规则不允许项目 Contract 直接引用子 Case Claim。若一个项目级问题需要表达 Case 中已验证的事实，必须创建经过审核的项目级稳定 Claim，并以新的 ClaimEvidenceLink 关联现有公开 Evidence。

## 8. 18 个 Active Contract 迁移清单

### 8.1 原 15 项

| Preset | Contract Subject | Required Claims | Supporting Claims |
|---|---|---|---|
| `sql-audit-overview` | `sql-audit-project` | `claim-sql-audit-background`, `claim-sql-audit-responsibility`, `claim-sql-audit-technical-decision`, `claim-sql-audit-verification`, `claim-sql-audit-delivered` | `claim-sql-audit-documented-handoff` |
| `question-sql-audit-negative-input` | `sql-audit-project` | `claim-sql-audit-fixed-string-search` | `claim-sql-audit-verification` |
| `question-sql-audit-partial-success` | `sql-audit-project` | `claim-sql-audit-source-selection`, `claim-sql-audit-partial-success` | `claim-sql-audit-selected-target-check` |
| `question-case-multilingual-overview` | `case-multilingual-upload` | `claim-case-multilingual-replacement-problem`, `claim-case-multilingual-preserve-existing`, `claim-case-multilingual-sequential-verification` | `claim-case-multilingual-no-backfill` |
| `question-case-role-reset-overview` | `case-role-reset` | `claim-case-role-reset-cache-interference-problem`, `claim-case-role-reset-controlled-flow`, `claim-case-role-reset-confirmation-safety`, `claim-case-role-reset-acceptance`, `claim-case-role-reset-documented-delivery` | empty |
| `question-case-codegraph-overview` | `case-codegraph-evaluation` | `claim-case-codegraph-narrowing`, `claim-case-codegraph-combined-workflow`, `claim-case-codegraph-evaluation-method` | `claim-case-codegraph-failure-boundary`, `claim-case-codegraph-manual-quality-review`, `claim-case-codegraph-qualitative-publication` |
| `question-sql-audit-async-and-recovery` | `sql-audit-project` | `claim-sql-audit-async-task-lifecycle`, `claim-sql-audit-progress-fallback` | empty |
| `question-sql-audit-progress-fallback` | `sql-audit-project` | `claim-sql-audit-progress-fallback` | `claim-sql-audit-async-task-lifecycle` |
| `question-sql-audit-archive-and-truncation` | `sql-audit-project` | `claim-sql-audit-result-lifecycle`, `claim-sql-audit-truncation-disclosure` | empty |
| `question-case-multilingual-verification-sequence` | `case-multilingual-upload` | `claim-case-multilingual-preserve-existing`, `claim-case-multilingual-sequential-verification` | empty |
| `question-case-multilingual-recovery-boundary` | `case-multilingual-upload` | `claim-case-multilingual-no-backfill` | `claim-case-multilingual-preserve-existing` |
| `question-case-role-reset-acceptance-result` | `case-role-reset` | `claim-case-role-reset-controlled-flow`, `claim-case-role-reset-acceptance` | `claim-case-role-reset-documented-delivery` |
| `question-case-role-reset-safety-boundary` | `case-role-reset` | `claim-case-role-reset-confirmation-safety` | `claim-case-role-reset-controlled-flow` |
| `question-case-codegraph-method` | `case-codegraph-evaluation` | `claim-case-codegraph-evaluation-method` | `claim-case-codegraph-narrowing`, `claim-case-codegraph-combined-workflow` |
| `question-case-codegraph-quality-boundary` | `case-codegraph-evaluation` | `claim-case-codegraph-manual-quality-review`, `claim-case-codegraph-qualitative-publication` | `claim-case-codegraph-failure-boundary` |

### 8.2 三个 ABTest Contract

三个 ABTest preset 的展示关系覆盖项目及相关 Case，但 `contractSubjectId` 统一为：

```text
weekend-login-abtest-project
```

新增项目级 Claims：

| Claim ID | 语义 | 公开 Evidence |
|---|---|---|
| `claim-abtest-project-background` | 项目背景和实验目标 | `evidence-abtest-delivery-history` |
| `claim-abtest-project-responsibility` | 本人职责与公开贡献边界 | `evidence-abtest-delivery-history` |
| `claim-abtest-project-stratification-bucketing` | 历史登录天数分层与层内稳定分桶 | `evidence-abtest-experiment-design-notes` |
| `claim-abtest-project-stable-assignment` | 服务端稳定归组、标签持久化与配置演进 | `evidence-abtest-service-sql-evolution` |
| `claim-abtest-project-validation-rollback` | 埋点校验、风险控制、停止与回滚边界 | `evidence-abtest-validation-risk-notes` |

现有 `claim-abtest-project-delivered` 继续作为最终产出 Claim。现有 Case Claims 保留，不被项目级 Claim 替换。

Contract 清单：

| Preset | Required Claims | Supporting Claims |
|---|---|---|
| `question-abtest-overview` | `claim-abtest-project-background`, `claim-abtest-project-responsibility`, `claim-abtest-project-stratification-bucketing`, `claim-abtest-project-stable-assignment`, `claim-abtest-project-validation-rollback`, `claim-abtest-project-delivered` | empty |
| `question-abtest-stratification-bucketing` | `claim-abtest-project-stratification-bucketing` | `claim-abtest-project-background` |
| `question-abtest-stable-assignment-and-rollback` | `claim-abtest-project-stable-assignment`, `claim-abtest-project-validation-rollback` | `claim-abtest-project-delivered` |

所有新增 Claim 和 ClaimEvidenceLink 必须经过现有公开内容审核，不因本设计自动获得 `VERIFIED/APPROVED` 状态。

### 8.3 保持 Draft 的跨主体项

```text
question-public-assets-overview
```

该项：

- `contractSubjectId = null`
- `requiredClaimIds = []`
- `supportingClaimIds = []`
- `contractStatus = DRAFT`
- 不进入公共 DTO
- 用户手输其 canonical/alias 时按普通自由问题处理

## 9. Contract 投影与候选生成

新增 `PresetContractProjector`，由 `prepare-candidate` 在事实 patch 合并完成后、候选写盘前调用。

处理顺序：

1. 读取合并后的候选 `questionPresets`。
2. 读取并校验 Contract Policy。
3. 按 `presetId` 唯一定位 18 个 Active preset。
4. 写入 `contractSubjectId`、Required/Supporting Claims、Evidence Requirement 和 `ACTIVE` 状态。
5. 将 Draft 清单中的 preset 规范化为 DRAFT，并清空执行字段。
6. 验证主体、Claim 和 Evidence 不变量。
7. 计算每项 `contractVersion`。
8. 计算整个 Active 集合的 `presetContractSetHash`。
9. 写出候选 `portfolio.json` 和 manifest。

如果候选缺少 policy 中的 preset，或出现未声明却为 Active 的 preset，必须失败，不得自动新增、删除或降级。

## 10. 确定性版本与集合指纹

单项 `contractVersion` 继续使用 `pcv1-` 前缀，并将以下规范化字段作为输入：

```text
presetId
canonicalText
aliases
contractSubjectId
requiredClaimIds
supportingClaimIds
minimumApprovedEvidencePerRequiredClaim
publicOnly
status
```

数组顺序是语义的一部分；生成器不得自行排序 Required/Supporting Claims 或 aliases。

Active 集合按 `presetId` 升序排列，每项写成包含 `presetId + contractVersion` 的规范化 JSON，再计算：

```text
presetContractSetHash = "sha256:" + lowercaseHex(SHA-256(UTF-8(canonicalJson)))
```

该字段写入运行 manifest。以下阶段必须重新计算并比对：

- 候选准备完成后
- 评审包生成前
- 签名/发布前
- 发布包落盘后
- PostgreSQL 导入验证后

任一阶段不一致都必须阻断流程。

## 11. 发布门禁与失败码

发布门禁必须返回类型化失败码：

| 失败码 | 条件 |
|---|---|
| `PRESET_CONTRACT_POLICY_INVALID` | Policy schema、唯一性、状态或集合关系非法 |
| `PRESET_CONTRACT_ACTIVE_SET_DRIFT` | Active ID 集合不等于 policy 的 18 项 allowlist |
| `PRESET_CONTRACT_SUBJECT_INVALID` | `contractSubjectId` 缺失、未知或不在展示关联中 |
| `PRESET_CONTRACT_CLAIM_INVALID` | Claim 缺失、未验证、重复、跨主体或 Required/Supporting 重叠 |
| `PRESET_CONTRACT_EVIDENCE_INSUFFICIENT` | Required Claim 未满足最低公开证据要求 |
| `PRESET_CONTRACT_PROJECTION_MISMATCH` | 候选字段与 policy 投影不一致 |
| `PRESET_CONTRACT_SET_HASH_MISMATCH` | Contract 集合指纹在流水线阶段间不一致 |

这些错误必须阻断候选准备、评审或发布。禁止通过以下方式继续：

- 自动将失败的 Active 项改成 DRAFT
- 删除失败项后继续发布
- 从旧 bundle 复制缺失字段
- 忽略 hash 差异并重新签名

## 12. 兼容资源

`backend/src/main/resources/public-data/public-portfolio.v1.json` 不再手工维护 Contract 状态。它是兼容生成物，并必须满足：

- `sql-audit-overview` 与 bundle 使用相同 `contractSubjectId`、Claims、Evidence Requirement 和版本。
- 兼容资源必须包含该 Contract 所需的公开 Claims 和 Evidence Links。
- 若兼容资源无法满足 Active Contract 门禁，则对应运行 profile 不得公开该 preset。
- CI 必须比较兼容资源与 bundle 中 `sql-audit-overview` 的 `contractVersion`。

## 13. 公共 DTO 与前端请求身份

`PortfolioResponseMapper` 在创建 `QuestionPresetResponse` 前过滤：

```text
question.contractStatus == ACTIVE
```

公共 DTO 只输出安全元数据：

```text
id
text
projectSlug
caseSlugs
audiences
placements
contractVersion
availability = ACTIVE
```

不输出 `contractSubjectId`、Claim IDs、Evidence Requirement 或 Evidence IDs。

前端 `QuestionPreset` 类型收紧为：

```text
contractVersion: string
availability: ACTIVE
```

点击正式 preset 时，以下字段缺一不可：

```text
questionPresetId
contractVersion
question
projectSlug XOR caseSlug（按当前入口）
```

若公共数据缺少版本，前端不得把该项渲染为正式推荐问题。

普通自由输入和普通 suggestion 不携带 `questionPresetId/contractVersion`。

## 14. 运行时解析优先级

固定顺序：

```text
Reference Context
-> Preset ID + Contract Version
-> Active canonical/alias
-> 受控确定性规则
-> 已验证结构化主体
-> 模型任务分类
-> 非作品集/通用能力
```

### 14.1 Preset

显式 Preset 请求必须校验：

- ID 唯一存在
- 状态为 ACTIVE
- 版本一致
- 文本命中 canonical/alias
- `contractSubjectId` 与当前主体提示一致

失败时返回 Contract unavailable/stale，禁止降级搜索。

### 14.2 Structured Subject

新增 `StructuredSubjectTaskResolver`，消费：

```text
question
projectSlug?
caseSlug?
```

不变量：

- Project 与 Case slug 不能同时存在。
- slug 必须唯一命中当前公开快照。
- 成功时生成 `FACT_LOOKUP + subjectId`。
- 后续进入 `SUBJECT_SCOPED_RELEVANCE`。

`referencesExplicitSubject()` 仅负责识别自然语言指代，不再决定结构化 slug 是否生效。

### 14.3 Suggestion

普通 suggestion 至少包含：

```text
text
projectSlug XOR caseSlug
facet
```

服务端返回 suggestion 前验证主体仍存在且公开。客户端点击时原样发送主体字段，但不发送 Preset 身份。

结构化主体只缩小候选，不能绕过：

- 相关性
- 主体一致性
- Evidence Policy
- 歧义判断
- 公开状态

## 15. 失败语义

| 场景 | 结果 |
|---|---|
| Preset ID/状态/主体非法 | Contract unavailable |
| Preset version 陈旧 | Contract stale，最多自动刷新重试一次 |
| Active Contract Required Evidence 运行时缺失 | Contract failure，不返回 `NOT_SUPPORTED` |
| 结构化 slug 不存在或不唯一 | `INVALID_INPUT`，不进入 GENERAL |
| 主体存在但相关证据不足 | `NOT_SUPPORTED` |
| 相关候选歧义 | `NEEDS_CLARIFICATION` |
| 检索基础设施失败 | 标准技术失败，不伪装成证据不足 |
| 真正通用问题且 Provider 关闭 | `GENERAL + CAPABILITY_UNAVAILABLE` |

通用模型关闭只能影响最后一行，不能影响 Active Preset 或带有效公开主体的相关性问题。

## 16. 测试策略

### 16.1 Policy 与投影单元测试

覆盖：

- 合法 18 项 policy
- 重复 preset ID
- Active/Draft 重叠
- 缺失 preset
- 未声明 Active preset
- `contractSubjectId` 非法
- Required/Supporting 重叠
- 跨主体 Claim
- 未验证 Claim
- Required Evidence 不足
- 投影幂等
- Contract Version 确定性
- Contract Set Hash 确定性

### 16.2 真实 bundle 契约测试

必须直接读取：

```text
backend/src/main/resources/public-data/bundle/portfolio.json
```

断言：

- Active ID 集合精确等于 policy 的 18 项。
- 18 项均有 `contractSubjectId`、Required Claims、Evidence Requirement 和非空版本。
- `question-public-assets-overview` 为 DRAFT。
- 公共 DTO 不包含 Draft 项。
- 删除任一 Contract 字段后验证失败。
- 使用旧基线重新生成 bundle 后 allowlist/hash 验证失败。

### 16.3 Bundle/PostgreSQL 一致性

对每个 Active Contract 分别通过 Bundle 和 PostgreSQL 适配器执行，比较：

```text
contractSubjectId
requiredClaimIds
supportingClaimIds
selectedClaimIds
selectedEvidenceIds（允许顺序规范化）
contractVersion
```

Embedding 开关不得影响结果。

### 16.4 后端真实交互验收

运行条件：

```text
model expression disabled
conversational model disabled
embedding disabled
production bundle
```

场景一：

```text
questionPresetId = sql-audit-overview
contractVersion = 当前公开版本
projectSlug = sql-audit
question = 请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？
```

断言：

```text
ANSWERED
PORTFOLIO
EVIDENCE_COMPOSITION
VERIFIED
回显 questionPresetId + contractVersion
覆盖五个 Required Claims
不调用相关性检索或模型分类
```

场景二：

```text
questionPresetId = null
contractVersion = null
projectSlug = role-reset-tool
question = 测试角色重置工具的背景和目标是什么？
```

断言：

```text
进入 SUBJECT_SCOPED_RELEVANCE
不得返回 GENERAL
不得返回 CAPABILITY_UNAVAILABLE
所有 Claim 均属于 role-reset-tool 对应公开主体
```

### 16.5 前端验收

覆盖：

- DRAFT preset 不渲染。
- Active preset 请求携带 ID/version。
- 普通 suggestion 不携带 ID/version。
- 普通 suggestion 携带 project/case slug 和 facet。
- stale version 只重试一次。
- 内容刷新后使用新版本。
- 截图两步操作的浏览器测试通过。

### 16.6 合并后门禁

Contract 验证必须在合并后的 HEAD 上重新执行。分支上已通过、生成物已有签名或 checksums 正确，都不能替代合并后验证。

## 17. 迁移顺序

1. 建立 policy schema、loader、projector 和 fingerprint。
2. 增加 `contractSubjectId` 并迁移原 15 个 Contract。
3. 审核并发布五个 ABTest 项目级 Claims 及其 Evidence Links。
4. 激活三个 ABTest Contract。
5. 将跨主体 preset 固定为 Draft 并从公共 DTO 过滤。
6. 重新生成兼容资源、候选 bundle、检索索引、manifest 和 checksums。
7. 执行 Bundle/PostgreSQL 一致性验证。
8. 修复结构化主体路由和 suggestion 请求契约。
9. 增加真实 bundle 与浏览器端到端验收。
10. 在合并后的 HEAD 上重新验证并发布。

迁移过程中不得出现“公开推荐项仍可见但状态为 DRAFT”的中间发布状态。

## 18. 风险与控制

- **治理 policy 与事实候选漂移：** 投影前后做精确 ID、主体、Claim 和 Evidence 校验。
- **新项目 Claims 重复 Case 事实：** 项目 Claims 只形成稳定项目级摘要，保留原 Case Claims 和贡献边界。
- **公开 Evidence 被多 Claim 引用：** 每条 ClaimEvidenceLink 独立审核，不因 Evidence 已公开而自动批准新 Claim。
- **客户端伪造 slug：** slug 只约束公开候选，不绕过相关性与证据政策。
- **旧分支覆盖生成物：** Active allowlist、set hash 和合并后真实 bundle 测试共同阻断。
- **兼容资源形成第二套语义：** 从同一 policy 投影并比较 Contract Version。
- **硬编码数量掩盖 ID 替换：** 门禁比较精确 ID 集合，不只比较数量。

## 19. 完成标准

本设计完成实现后，必须同时满足：

1. Policy 精确声明 18 个 Active Contract。
2. 当前生产 bundle 精确包含相同 18 个 Active Contract。
3. 跨主体 Draft preset 不出现在公共 DTO。
4. 18 项均有唯一 `contractSubjectId` 和非空 `contractVersion`。
5. 每个 Required Claim 均满足最低公开证据要求。
6. SQL 正式问题在默认关闭模型能力时返回确定性 Contract 回答。
7. 角色重置 suggestion 在默认关闭模型能力时进入主体约束相关性路径。
8. 两个截图请求均不再出现原失败结果。
9. Bundle 与 PostgreSQL 的 Contract 选择一致。
10. 旧基线生成物、缺字段、Active 集合漂移和 set hash 漂移均能使发布失败。
11. 兼容资源与 bundle 的 SQL Contract 身份一致。
12. 所有既有隐私、安全、stale retry、canonical/alias 和引用验证测试继续通过。
