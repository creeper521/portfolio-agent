# Portfolio Agent 内容治理与学习闭环设计

- 状态：B 第一版已实现并通过 release verification；C 扩展仍未实现
- 日期：2026-07-21
- 范围：B 阶段——内容治理与学习闭环
- 前置设计：`2026-07-20-portfolio-agent-runtime-trust-design.md`
- 后续设计：C 阶段——未来智能能力

## 1. 背景

A 阶段解决公开运行时的能力真实性、回答状态、结构化呈现和会话隐私。B 阶段进一步解决公开内容如何从私人材料产生、如何建立 Claim 与 Evidence 关系、如何审核、评测、发布、回滚，以及如何从失败案例中受控学习。

当前 classpath 单文件快照适合一个项目和一个问题，但无法长期支撑多项目、多 Claim、多 QuestionPreset 和持续内容更新。主要缺口包括：

1. `supportedClaims` 是 Evidence 内的自由文本，不是独立事实对象。
2. 私人来源和公开 Evidence 缺少物理、模型和发布边界。
3. 内容变化、审核失效、版本升级和回滚没有统一可执行规则。
4. 发布验证主要面向代码和制品，缺少事实、答案和安全 Benchmark。
5. 访客反馈、失败案例、修复和 Playbook 之间没有闭环。
6. 自动化候选生成与人工批准的责任边界尚未固化为工具流程。

本设计采用“离线治理控制面 + 只读公开运行时”，并将治理流程封装成项目内共享的 `portfolio-governance` Skill。

## 2. 设计目标

1. 私人来源永不进入公开运行时或公开版本包。
2. Claim 成为公开事实、回答内容和 Verified 判定的最小单元。
3. Evidence 与 Claim 通过显式关系表达支持强度。
4. 每个公开版本不可变、可验证、可审计、可回滚。
5. 自动化可以提取候选、生成审核包和运行评测，但不能代替人工批准。
6. 访客反馈在不保存原始问题和自由文本的前提下形成改进信号。
7. 失败案例能够沉淀为 Benchmark 和 Playbook。
8. 治理流程可以通过 CLI 和可读审核包稳定复用。

## 3. 非目标

B 阶段不实现：

- 公开运行时直接读取 Obsidian 或私人治理目录；
- 在线 CMS 或远程管理后台；
- 数据库存储；
- 自动公开发布；
- AI 自动批准对象、Bundle、发布或回滚；
- 运行时根据访客反馈自动修改知识；
- 模型回答、RAG、多轮服务端会话或多 Agent 运行时；
- 密码学签名和企业级密钥管理。

## 4. 总体架构

```text
私人资料 / Obsidian
→ 候选事实提取
→ Claim 与 Evidence 关联
→ 对象事实与隐私审核
→ 创建 GovernanceRunSnapshot
→ 显式 Gate 校验
→ Benchmark 验证
→ 生成候选 Bundle 与审核包
→ 人工批准指定 Bundle hash
→ 原子切换 active
→ 公开运行时只读加载
```

治理控制面和公开运行时必须分离：

- 治理控制面可以访问明确授权的私人来源，只能向私人 Sandbox 写候选和审核产物。
- 公开运行时只能读取 active 指向的完整 `PUBLISHED` Bundle。
- 只有通过全部硬门禁并获得人工批准的字段白名单输出，才能进入公开仓库。

## 5. Claim 模型

Claim 是最小公开事实单元：

```json
{
  "id": "claim-sql-audit-delivered",
  "subjectType": "PROJECT",
  "subjectId": "project-sql-audit",
  "category": "OUTCOME",
  "statement": "核心版本已完成部署并形成使用文档。",
  "achievementStatus": "DELIVERED",
  "verificationBasis": "EVIDENCE_SUPPORTED",
  "verificationStatus": "VERIFIED",
  "materiality": "KEY",
  "visibility": "PUBLIC",
  "confidentialityReviewStatus": "APPROVED",
  "reviewStatus": "APPROVED",
  "validFrom": "2026-07-10",
  "reviewedBy": "human-owner",
  "reviewedAt": "2026-07-21T10:00:00+08:00",
  "contentHash": "sha256:..."
}
```

Claim 是以下消费者的共同事实来源：

- 项目详情；
- Timeline；
- QuestionPreset 回答；
- Evidence 支持关系；
- Verification 判定；
- Benchmark 预期结果。

`subjectType + subjectId` 是 Claim 的统一身份；`category` 表示背景、职责、技术决策、实现、验证、结果等业务分类；`achievementStatus` 表示事实所处生命周期；`verificationBasis` 表示主张如何形成。四者不能合并，也不得使用含义不明的 `type` 或 `claimType` 作为兼容别名。Claim 与 Evidence 的关系不存放在 Claim 内。

形成依据与回答可信度上限：

| `verificationBasis` | 含义 | 回答可信度上限 |
| --- | --- | --- |
| `EVIDENCE_SUPPORTED` | 可由已批准 Link 和 Evidence 直接证明 | `VERIFIED` |
| `SELF_DECLARED` | 作者对经历、职责或判断的陈述 | `PARTIALLY_VERIFIED`，并标注“个人陈述” |
| `INFERRED` | 根据已知材料推导的结论 | `PARTIALLY_VERIFIED`，并展示推断边界 |
| `UNSUPPORTED` | 暂无合格 Evidence | `UNVERIFIED` |

`verificationStatus` 是根据当前 Bundle 中实际有效的 Link 与 Evidence 计算的结果，不能由候选生成器直接授予。`materiality = KEY | SUPPORTING` 用于回答级聚合：全部 KEY Claim 都被直接支持时才可为 `VERIFIED`；任一 KEY Claim 仅为自述、推断或部分支持时降为 `PARTIALLY_VERIFIED`；任一 KEY Claim 无合格支持时为 `UNVERIFIED`。辅助 Claim 不得掩盖关键 Claim 的缺口。

只有满足以下条件的 Claim 可以进入公开版本：

```text
visibility = PUBLIC
confidentialityReviewStatus = APPROVED
reviewStatus = APPROVED
```

`verificationStatus` 只使用 `VERIFIED / PARTIALLY_VERIFIED / UNVERIFIED`，不承载审核拒绝状态。未审核、已拒绝、已过期或存在未解决冲突的 Claim 不得进入正式回答。Evidence 只能证明 `ClaimEvidenceLink.scope` 声明的范围，不能因为存在一份 Evidence 就验证整段回答。

## 6. PrivateSource 与 PublicEvidence

### 6.1 PrivateSource

PrivateSource 只存在于仓库外的私人治理工作区：

```json
{
  "id": "source-2026-07-10-work-log",
  "sourceType": "WORK_LOG",
  "privateLocation": "obsidian://...",
  "contentHash": "sha256:...",
  "capturedAt": "2026-07-10T18:00:00+08:00",
  "sensitivity": "INTERNAL",
  "reviewStatus": "REVIEWED"
}
```

它可以引用原始日报、内部截图、私有提交记录、内部路径和审核备注。这些字段永远不得进入公开 bundle。

### 6.2 PublicEvidence

PublicEvidence 通过字段白名单重新组装：

```json
{
  "id": "sql-audit-delivery-set",
  "code": "E-01",
  "title": "SQL 审计工具交付证据集",
  "type": "COLLECTION",
  "periodStart": "2026-06-02",
  "periodEnd": "2026-07-10",
  "sourceCount": 7,
  "summary": "经过脱敏审核的公开摘要",
  "publicStatus": "APPROVED",
  "rawContentPublic": false,
  "contentHash": "sha256:..."
}
```

禁止通过“从私人对象删除敏感字段”的方式构建 PublicEvidence。

### 6.3 ClaimEvidenceLink

```json
{
  "id": "link-delivery-e01",
  "claimId": "claim-sql-audit-delivered",
  "evidenceId": "sql-audit-delivery-set",
  "supportType": "DIRECT",
  "scope": "证明核心版本已经交付；不证明长期生产效果。",
  "reviewStatus": "APPROVED",
  "reviewedBy": "human-owner",
  "reviewedAt": "2026-07-21T10:00:00+08:00",
  "sourceLocator": "public-summary:E-01",
  "notes": null
}
```

支持关系：

- `DIRECT`：可直接支持 Claim。
- `CORROBORATING`：辅助佐证，不能单独产生 Verified。
- `CONTEXTUAL`：只提供背景。

`ClaimEvidenceLink` 是唯一权威关系。Claim 不保存 `evidenceIds`，Evidence 不保存 `claimIds`；两个方向的查询索引均由 Link 派生，禁止人工维护冗余反向引用。

### 6.4 来源追踪与冲突

每个私人治理对象都记录规范来源：

```json
{
  "sourceType": "REPOSITORY_FILE",
  "sourceId": "portfolio-private",
  "scope": "PRIVATE_GOVERNANCE",
  "path": "evidence/sql-audit/delivery.json",
  "revision": "git-sha-or-version",
  "contentHash": "sha256:..."
}
```

公开 Bundle 只保留允许公开的来源摘要，不能包含私人绝对路径。冲突处理不采用 first-wins 或 last-wins：同 ID、同规范内容和 hash 可以去重并记录多个来源；同 ID 异内容、悬空引用或私人来源进入公开字段均为 `BLOCKER`；不同 ID 同 hash 产生人工合并提示。审核后来源内容变化会使原批准失效，不得自动改名或按文件加载顺序决定权威对象。

## 7. 不可变发布包

本设计增强而不替代 `docs/05-public-release-bundle-contract.md` 与 `docs/06-content-publishing-runbook.md`。B 第一版沿用 Manifest、checksums、应用兼容、原子切换、健康检查和回滚语义，但只发布确定性运行所需内容：

```text
releases/
└── 2026-07-21.1/
    ├── manifest.json
    ├── portfolio.json
    ├── presentation.json
    └── checksums.json
```

`portfolio.json` 包含 Owner、Project、Claim、Evidence、ClaimEvidenceLink、TimelineEvent、QuestionPreset 和确定性回答定义。`presentation.json` 只包含公开展示配置。RAG 文档、关键词索引、向量索引、Provider 和检索策略属于 C2；B 第一版不得生成这些文件，也不得用空字段假装支持。

manifest 示例：

```json
{
  "schemaVersion": "2.0",
  "contentVersion": "2026-07-21.1",
  "previousVersion": "2026-07-14.1",
  "builtAt": "2026-07-21T10:30:00+08:00",
  "publishedAt": "2026-07-21T10:35:00+08:00",
  "minimumApplicationVersion": "0.1.0",
  "factsFile": "portfolio.json",
  "presentationFile": "presentation.json",
  "approvedAt": "2026-07-21T10:30:00+08:00",
  "approvedBy": "human-owner",
  "approvalId": "APR-2026-07-21-001",
  "approvalDigest": "sha256:...",
  "policyBundleHash": "sha256:...",
  "benchmarkDefinitionHash": "sha256:...",
  "candidatePayloadHash": "sha256:...",
  "checksumsFile": "checksums.json",
  "counts": {
    "projects": 1,
    "claims": 1,
    "evidence": 1,
    "claimEvidenceLinks": 1,
    "timelineEvents": 1,
    "questionPresets": 1
  }
}
```

规则：

1. 任何公开正文、Claim、Evidence 关系、项目状态、QuestionPreset 或 Timeline 变化都生成新版本。
2. 已批准版本永不原地修改。
3. 发布前验证 Schema、引用、隐私、Benchmark 和文件哈希。
4. 验证通过后原子切换 active。
5. 回滚只能指向历史 APPROVED 版本。
6. 失败候选保留治理记录，但不能成为 active。
7. `ClaimEvidenceLink` 是包内唯一关系；反向索引由加载器构造。
8. 禁止未知 JSON 字段、跨版本引用、悬空引用、Manifest counts 不一致以及 `null` 代替必填集合。
9. Schema 主版本和 `minimumApplicationVersion` 不兼容时拒绝加载，不在运行时自动迁移。
10. 私人审核报告只存在于治理工作区；公开 Manifest 仅保存其公开摘要与 hash。

`candidatePayloadHash` 只覆盖审核包中已规范化的公开 payload：`portfolio.json`、`presentation.json`，以及 C2 启用时的 `rag-documents.jsonl`。规范化必须在 `build-review-pack` 之前完成；人工审核后，服务器只能逐字节复制这些文件并复算 hash，不得排序字段、补默认值、迁移 Schema 或做其他语义规范化。

hash 依赖必须保持单向、无环：

```text
各对象 canonical bytes → contentHash
canonical public payload files → candidatePayloadHash
candidatePayloadHash + 审核元数据 → Approval → approvalDigest
candidatePayloadHash + approvalDigest + 发布元数据 → manifest.json → manifestHash
运行包其余文件 → checksums.json → checksumsHash
manifestHash + checksumsHash → runtimeBundleHash
```

Approval 批准的是 `candidatePayloadHash`，不是服务器之后生成的 Manifest、checksums、索引或最终运行目录字节。派生产物必须由已批准 payload 确定性生成并单独校验。Manifest 不保存包含自身在内的自引用 hash；`manifestHash` 写入不可变发布审计，A 的 `RuntimeContentSnapshot.runtimeBundleHash` 由加载器根据已验证的 `manifestHash + checksumsHash` 计算。

发布器先在旁路完整校验 Bundle 并构造不可变 `RuntimeContentSnapshot`，完成全部索引和不变量检查后才原子替换 active 引用。新请求读取新 Snapshot，进行中的请求继续持有旧 Snapshot；构造或切换失败时旧 Snapshot 不变。文件指针切换、应用重启、readiness 和 HTTP 冒烟继续遵循既有 runbook。

## 8. 对象审核与 Bundle 发布状态

对象审核状态：

```text
DRAFT → IN_REVIEW → APPROVED | REJECTED
                       ↓
                    RETIRED
```

它适用于 Claim、Evidence、ClaimEvidenceLink、QuestionPreset、Case 和 Benchmark，回答“对象本身是否可信、合规、可公开”。对象 `APPROVED` 不代表已经上线；对象内容或公开关系变化时产生新修订并重新审核，不能原地修改已发布修订。

Bundle 发布状态：

```text
CANDIDATE → VALIDATING → READY → PUBLISHED
                ↓          ↓          ↓
              FAILED     STALE     SUPERSEDED
```

它回答“这一组已审核对象能否作为完整版本原子发布”。只有 `PUBLISHED` Bundle 中的对象才可被 A 运行时读取。发布失败不改变 active；回滚重新激活完整历史 Bundle，不逐条回退对象。

对象批准和 Bundle 发布是两次独立权限动作。第一版可由同一名明确的人类 Owner 执行，但审计记录必须分开。涉及公司内部信息、客户信息、凭据或原始截图时建议第二人复核，但第一版不强制所有版本双人审批。

AI、脚本和自动评测不能产生对象 `APPROVED`、Bundle 人工 Approval 或 `PUBLISHED`。

## 9. 审核失效传播

每个受审对象具有 `contentHash`，审核记录绑定：

```text
objectId + contentHash + ruleVersion
```

依赖变化按以下规则传播：

```text
PrivateSource 变化
→ Candidate / Claim 事实审核失效

Claim 变化
→ ClaimEvidenceLink、QuestionPreset、Benchmark 失效

Evidence 变化
→ 关联 Claim verification 失效

QuestionPreset 或 Claim 引用变化
→ 回答评测和人工批准失效

隐私规则或 Playbook 变化
→ 受影响对象重新检查

任何公开对象变化
→ 候选 Bundle Approval 失效
```

自动评测可以恢复机器校验结果，不能自动恢复对象批准或 Bundle Approval。历史审核记录保留且不可覆盖。

## 10. GovernanceRunSnapshot

每次治理运行开始时固定不可变输入：

```json
{
  "runId": "random-id",
  "startedAt": "2026-07-21T10:00:00+08:00",
  "inputFingerprint": "sha256:...",
  "schemaVersion": "2.0",
  "policyBundleHash": "sha256:...",
  "benchmarkDefinitionHash": "sha256:...",
  "toolVersion": "1.0.0",
  "candidatePayloadHash": "sha256:..."
}
```

`inputFingerprint` 覆盖所有治理输入及来源元数据；Benchmark hash 绑定测试定义而非仅绑定结果。固定阶段为：

```text
SNAPSHOT
→ VALIDATE
→ BENCHMARK
→ BUILD_REVIEW_PACK
→ HUMAN_APPROVE
→ PUBLISH
```

每个产物都绑定同一 `runId`。进入人工批准和发布前重新计算关键 hash；输入、Schema、Policy、Benchmark 定义、工具版本或 Candidate 任一变化，运行进入 `STALE`，不得复用旧报告或批准。发布器必须证明获批的 `candidatePayloadHash` 与即将复制的 canonical payload 字节完全一致；运行 Manifest、checksums 和索引属于可复算的派生产物，不进入 Approval 的循环依赖。

## 11. Feedback Bus 与 Case Registry

### 11.1 结构化匿名 Feedback

第一版只接受固定字段：

```json
{
  "contentVersion": "2026-07-21.1",
  "questionPresetId": "sql-audit-full-introduction",
  "resolution": "ANSWERED",
  "answerSource": "PRESET",
  "helpful": false,
  "reason": "EVIDENCE_INSUFFICIENT"
}
```

原因枚举：

- `INCORRECT`
- `OUTDATED`
- `UNCLEAR`
- `INCOMPLETE`
- `EVIDENCE_INSUFFICIENT`
- `WRONG_BOUNDARY`
- `UI_CONFUSING`
- `OTHER_STRUCTURED`

不得采集原始问题、回答全文、自由文本评价、用户身份、浏览器指纹和可关联个人的信息。

### 11.2 Case Registry

```json
{
  "caseId": "CASE-001",
  "source": "STRUCTURED_FEEDBACK",
  "contentVersion": "2026-07-21.1",
  "questionPresetId": "sql-audit-full-introduction",
  "failureType": "EVIDENCE_INSUFFICIENT",
  "sanitizedObservation": "最终状态段缺少直接 Evidence。",
  "expectedBehavior": "交付状态必须关联 DIRECT Evidence。",
  "rootCause": "QuestionPreset 引用了无 DIRECT 关系的 Claim。",
  "resolution": "补充 ClaimEvidenceLink 并增加回归测试。",
  "status": "RESOLVED"
}
```

Case 可以来自结构化反馈、Benchmark、人工审查、发布验证和生产错误码聚合。关闭 Case 必须记录根因、关联修复版本、增加回归测试，并判断是否提炼为 Playbook。

## 12. Benchmark Registry

Benchmark 用结构化行为和事实断言，不依赖整段字符串：

```json
{
  "caseId": "FACT-001",
  "category": "CONTRACT",
  "caseType": "SUPPORTED_QUESTION",
  "projectSlug": "sql-audit",
  "questionPresetId": "sql-audit-full-introduction",
  "expected": {
    "resolution": "ANSWERED",
    "answerSource": "PRESET",
    "generationMode": "DETERMINISTIC",
    "verification": "VERIFIED",
    "requiredClaimIds": ["claim-sql-audit-delivered"],
    "requiredEvidenceIds": ["sql-audit-delivery-set"],
    "forbiddenTerms": []
  }
}
```

Benchmark 套件分为：

- `CONTRACT`：响应结构、resolution、alias、边界和引用完整性；
- `CONTENT`：关键 Claim、Evidence 关系和预期可信度；
- `SAFETY`：隐私诱导、未批准内容泄露和非法输入。

严重级别：

- `BLOCKER`：Schema、引用、隐私、安全失败，阻止发布。
- `ERROR`：支持问题、Claim、Evidence、Verified 错误，阻止发布。
- `WARNING`：表达和展示问题，只能人工确认放行。
- `INFO`：覆盖率和趋势，不影响发布。

模型评审只能提供建议，不能覆盖 BLOCKER 或 ERROR。

发布硬门槛按当前 active QuestionPreset 计算：

- `activePresetCoverage = 100%`；
- `criticalBenchmarkPassRate = 100%`；
- 每个 active Preset 必须同时具备回答定义、正向回归、alias、边界、Claim/Evidence 和安全用例；
- 任一 active Preset 缺少 Benchmark 或任一 BLOCKER/ERROR 失败，Bundle 不得进入 `READY`。

产品路线目标不写入架构契约，也不作为发布分母。发布工具只报告无固定上限的 `supportedPresetCount`，并始终要求当前 active Preset 的 `activePresetCoverage = 100%`。新增 Preset 必须将定义、结构化回答、Claim/Evidence 关系和完整 Benchmark 作为同一次 Candidate 交付。

## 13. Playbook 与受控演进

```json
{
  "playbookId": "PB-EVIDENCE-001",
  "title": "交付状态必须有直接证据",
  "trigger": "Claim.achievementStatus in [DELIVERED, IMPLEMENTED_TESTED, PROTOTYPE, DESIGNED]",
  "rule": "至少存在一个 reviewStatus=APPROVED 的 DIRECT Link，且目标 Evidence 为 APPROVED",
  "sourceCaseIds": ["CASE-001"],
  "status": "ACTIVE",
  "approvedBy": "human-owner"
}
```

Agent 可以自动：

- 聚类 Case；
- 建议失败类型和根因；
- 建议 Claim、QuestionPreset、alias 和关系变更；
- 生成候选摘要和回答 section；
- 生成 Benchmark；
- 在 Sandbox 组装候选 bundle；
- 输出差异和风险说明。

Agent 不能自动：

- 将 PrivateSource 标记为公开；
- 将 Claim 标记为 `EVIDENCE_SUPPORTED`；
- 批准事实或隐私；
- 修改硬门禁；
- 删除失败 Case；
- 产生对象 `APPROVED` 或 Bundle Approval；
- 切换 active；
- 修改历史发布版本。

## 14. 存储边界

### 14.1 私人治理工作区

位于公开仓库之外：

```text
private-governance/
├── sources/
├── candidates/
├── claims/
├── reviews/
├── cases/
├── benchmarks/
├── playbooks/
├── review-packets/
└── sandbox-releases/
```

### 14.2 公开仓库

只保存：

- `PUBLISHED` Bundle；
- 公共 Schema；
- 不含私人内容的 Benchmark；
- 发布工具和验证规则；
- manifest、checksums 和 active 指针；
- 已批准 Playbook 的公开规则部分。

治理逻辑依赖 Repository 接口，第一版使用文件实现，未来可替换为 SQLite 或 PostgreSQL。

## 15. portfolio-governance Skill

Skill 位于项目内共享目录：

```text
.agents/skills/portfolio-governance/
├── SKILL.md
├── policies/
├── schemas/
├── benchmark/
├── templates/
└── scripts/
    └── governance-cli
```

Skill 本身只包含公开规则、Schema、模板和脚本，不包含私人资料。`SKILL.md` 设置 `disable-model-invocation: true`，必须由用户明确调用。Skill 负责说明流程和组织命令，不是治理权威；CLI 是治理状态的唯一写入口，Agent 和辅助脚本不能直接编辑状态字段绕过校验。

### 15.1 私人工作区配置

项目配置通过环境变量定位私人目录：

```yaml
privateWorkspace:
  environmentVariable: PORTFOLIO_GOVERNANCE_HOME
  requireOutsideRepository: true
```

规则：

1. 未设置路径时停止。
2. 解析路径必须位于 Git 工作树之外。
3. `..`、符号链接和 junction 绕回仓库时停止。
4. 审核包和 Sandbox 默认写入私人工作区。
5. 日志不输出私人目录绝对路径。
6. CLI `--workspace` 覆盖同样执行边界校验。

### 15.2 命令

```text
portfolio-governance inspect
portfolio-governance validate
portfolio-governance benchmark
portfolio-governance build-review-pack
portfolio-governance approve
portfolio-governance publish
portfolio-governance rollback
portfolio-governance case
```

标准顺序为 `inspect → validate → benchmark → build-review-pack → approve → publish`：

1. `inspect`、`validate`、`benchmark` 只读治理输入，只向私人 Sandbox 写报告。
2. `build-review-pack` 在私人 Sandbox 中先完成规范化，再确定性组装 canonical public payload、计算 `candidatePayloadHash` 并生成可读审核包；它不写公开仓库。
3. Agent 可以执行上述只读/候选命令；`approve`、`publish`、`rollback` 只能由用户显式发起或逐次确认。
4. `approve` 只批准指定 `candidatePayloadHash`；它不能因为测试通过而自动执行。
5. `publish` 只能逐字节复制与 Approval hash 完全一致的 canonical payload，再确定性构建运行派生产物；payload 重新构建后 hash 变化，必须重新审核和批准。
6. 所有状态转换均通过 CLI；不存在直接编辑 JSON 后继续发布的旁路。

每次执行输出机器可读结果：

```json
{
  "runId": "...",
  "command": "validate",
  "inputFingerprint": "sha256:...",
  "status": "PASSED",
  "artifacts": [],
  "blockingFindings": [],
  "warnings": []
}
```

命令必须幂等，默认 dry-run。公开写入前列出完整目标路径和差异，并要求二次确认。

## 16. Approval

```json
{
  "approvalId": "APR-2026-07-21-001",
  "runId": "random-id",
  "inputFingerprint": "sha256:...",
  "candidatePayloadHash": "sha256:...",
  "decision": "APPROVED",
  "approvedBy": "human-owner",
  "approvedAt": "2026-07-21T10:30:00+08:00",
  "schemaVersion": "2.0",
  "policyBundleHash": "sha256:...",
  "benchmarkDefinitionHash": "sha256:...",
  "toolVersion": "1.0.0",
  "privacyReviewId": "PRIV-001",
  "benchmarkRunId": "BENCH-001",
  "expiresWhenCandidateChanges": true
}
```

Approval 保存在私人工作区，公开 manifest 只包含 ID、公开审核者别名、时间和 digest。`candidatePayloadHash` 变化后 Approval 自动失效。

第一版不引入数字签名，使用 SHA-256、不可变 Approval、Git 历史和发布审计记录。

## 17. 发布、失败与回滚

发布使用：

```text
写入 active.next
→ 验证 bundle 与 hash
→ 原子替换 active
```

规则：

1. 发布前失败不影响当前 active。
2. 切换失败保留旧 active，不产生半成品状态。
3. active 切换后 readiness 或核心冒烟失败时，由发布工具验证旧版本并原子恢复旧 active；运行时本身不选择历史版本。
4. 进程内已有旧 Snapshot 时，在失败切换期间继续服务该已验证版本，不能加载半成品。
5. 冷启动时 active 无效必须 fail-closed，由显式 rollback 恢复；应用不能静默选择 `previousVersion` 或任意历史版本。
6. 自动或人工 rollback 的目标都必须完整、兼容且未进入安全阻断清单，并产生 Release Case 和不可变审计记录。
7. rollback 只切换完整版本指针，不逐条回退对象，也不删除版本。

## 18. 审核包

第一版不建设治理后台 UI。Skill 生成：

```text
review-packet/
├── summary.md
├── changes.md
├── claims.md
├── evidence-links.md
├── privacy-findings.md
├── benchmark-results.md
├── release-manifest.json
├── checksums.txt
├── approval-request.json
└── approval.json              # 明确批准后才生成
```

Markdown 面向人工审阅，JSON 和 checksums 保存机器状态；它们必须由同一个 `GovernanceRunSnapshot` 生成。`approval-request.json` 描述待批准的候选 hash；`approval.json` 只能由明确批准动作生成。私人路径和未脱敏内容只能出现在私人审核包，不得复制到公开仓库或公开日志。

## 19. 显式 Gate、审计与被动观测

控制门禁使用固定顺序的显式接口：

```text
SchemaGate
→ ReferenceIntegrityGate
→ PrivacyGate
→ ClaimEvidenceGate
→ BenchmarkGate
→ CompatibilityGate
→ HumanApprovalGate
```

Gate 决定能否继续，具有类型化输入、输出和失败码；任一 `BLOCKER` 阻止下一阶段。Gate 不允许被插件动态跳过、重排或降级，`--force` 也不能绕过隐私、引用完整性和人工批准。紧急撤回使用独立安全流程。

批准、发布和回滚必须写入本地不可变安全审计记录；审计写入失败时对应高权限动作不得完成。普通运行指标由 `GovernanceEventPublisher` 在阶段结果确定后尽力发布，例如 `RUN_STARTED`、`VALIDATION_COMPLETED`、`PUBLISH_SUCCEEDED` 和 `ROLLBACK_COMPLETED`。它只能接收类型化白名单 DTO，不能访问私人原文或完整审核包，也不能修改阶段结果、自动重试发布或触发批准。遥测失败不改变业务结果，但 CLI 要明确报告“动作成功、观测投递失败”。

```text
Gate 决定结果
Audit 证明高权限动作
Telemetry 观察运行情况
```

三者不是通用 Hook 或同一个 Event Bus；任何观测消费者都不能获得治理写权限。

## 20. 验收标准

### 20.1 路径与隔离

1. 私人工作区位于仓库内、通过 `..`、符号链接或 junction 绕回仓库时必须拒绝。
2. 未设置私人工作区时 fail-closed。
3. 私人文件不能进入 public bundle、公开日志和公开审核产物。

### 20.2 字段白名单

4. PublicEvidence 必须由允许字段重建。
5. PrivateSource 未知字段不能自动进入公开输出。
6. 内部路径、主机、IP、凭据、邮箱和原始截图引用必须被阻断。
7. 隐私测试覆盖 JSON 引号、双反斜杠路径和编码变体。

### 20.3 审核与 hash

8. candidatePayloadHash、对象 hash、Policy、Benchmark 定义或工具版本变化后，相关审核和 Approval 自动失效。
9. 缺少事实、隐私、Benchmark 或人工批准时不能发布。
10. Agent 不能伪造对象批准、Bundle Approval 或发布结果。
11. Approval 不能用于另一个 bundle。

### 20.4 发布与回滚

12. publish dry-run 不写公开目录。
13. 中途失败不产生半成品 active。
14. 重复 publish 同一版本保持幂等。
15. rollback 只能选择完整、兼容、已发布且未被安全阻断的版本。
16. active 切换后验证失败时，发布工具原子恢复已验证旧版本；应用自身不选择历史版本。
17. 冷启动 active 无效时 fail-closed，不能静默回退。

### 20.5 Benchmark、Case 与 Skill

18. BLOCKER 或 ERROR 阻止发布。
19. WARNING 只能人工确认放行。
20. 失败生成 Case 候选，关闭 Case 必须关联修复版本和回归测试。
21. 结构化 Feedback 不包含自由文本。
22. 每个命令具有明确前置状态、runId 和机器可读结果。
23. `approve`、`publish`、`rollback` 暂停等待明确用户授权。
24. 模糊表达不能视为批准。
25. 测试只使用人工 fixture，不使用真实私人资料。

### 20.6 关系、版本与运行边界

26. 公开源数据只保存 `ClaimEvidenceLink`，任何双向索引都由 Link 派生。
27. 对象 `APPROVED` 不会自动公开；只有完整 `PUBLISHED` Bundle 可进入运行时。
28. `SELF_DECLARED` 与 `INFERRED` Claim 不能使回答达到 `VERIFIED`，未支持的 KEY Claim 必须使回答降级。
29. 所有 active QuestionPreset 的关键 Benchmark 覆盖率和通过率均为 100%；问题数量路线目标不阻断当前可信发布。
30. 治理运行中任一 Snapshot hash 变化都会进入 `STALE`，旧 Approval 不能用于新 Candidate。
31. B 第一版 Bundle 不包含 RAG、关键词/向量索引或 Provider 配置。
32. 完整 Bundle 验证并构造完成后才原子替换 A 的 `RuntimeContentSnapshot`，进行中请求不跨版本。
33. Gate 失败阻止流程；安全审计失败阻止高权限动作；普通遥测失败不改变已经确定的业务结果。
34. Skill 禁止模型自行调用，CLI 是治理状态唯一写入口。

## 21. 与 A、C 的边界

A 阶段定义运行时如何解析 QuestionPreset、构建 AnswerContext、展示可信状态和保护临时会话。B 阶段负责这些公开对象如何被治理、审核和发布。

C 阶段负责身份化回答、多轮上下文、模型、RAG、工具调用和是否需要多 Agent。无论 C 如何扩展，都不能绕过 B 的 Claim、Evidence、Benchmark、隐私和人工批准门禁。

B 不建设运行时通用 Hook：治理控制使用固定显式 Gate，观测使用只读 Publisher。C1 的模型能力使用显式端口，C2 的检索与工具使用显式 Pipeline；只有 C3 在出现多种真实 Provider、工具或独立消费者后，才按 A 设计中的触发条件评估通用 Hook。
