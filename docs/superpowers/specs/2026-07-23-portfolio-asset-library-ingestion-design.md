# Portfolio Asset Library Ingestion Design

## 1. Goal

将本地 Obsidian 知识库中已经盘点的实习成果纳入本公开项目的内容治理体系，形成可追踪、可审核、可逐批公开的资产库；本阶段不修改公开运行时数据、API、Agent 回答范围、前端页面或部署状态。

## 2. Scope

### 2.1 Full inventory registration

首轮登记覆盖当前盘点中的全部资产类别：

- 7 条长期主线；
- 19 项已交付、原型或文档任务；
- 25 项活动开发、线上问题与故障排查任务；
- 17 项技术方案、评测和知识资产。

登记信息只包含：

- 稳定资产编号；
- 内容类型；
- 完成状态；
- 贡献类型；
- 公开优先级；
- 证据状态；
- 审核状态；
- 脱敏后的简短说明。

全量登记不等于公开批准，也不改变现有项目运行时的事实范围。

### 2.2 First refined batch

首批生成可审核候选包：

1. SQL 审计与故障排查主线；
2. 多语言图片上传结果保留修复；
3. 测试角色重置工具；
4. CodeGraph 工具评测。

SQL 审计作为现有公开 Project 的增量候选；其余三项作为未来 `CaseStudy` 公共契约的输入，不伪装成 Project，也不写入当前 schema 2.0 bundle。

### 2.3 Out of scope

本阶段明确不做：

- 修改 `backend/src/main/resources/public-data/`；
- 新增或变更 Java、TypeScript 运行时模型；
- 新增 CaseStudy API 或页面；
- 扩大 Agent 可回答事实；
- 自动批准、发布或部署；
- 复制原始日报、内部截图、代码、SQL 或日志到公开仓库；
- 提交当前工作区已有的前端视觉改动。

## 3. Storage Architecture

### 3.1 Private governance workspace

私有治理工作区通过 `PORTFOLIO_GOVERNANCE_HOME` 显式配置，物理位置必须在公开项目仓库之外。项目文档只描述逻辑结构，不记录本机绝对路径：

```text
$PORTFOLIO_GOVERNANCE_HOME/
├── candidates/
├── reviews/
└── reports/
```

职责：

- `candidates/`：保存全量资产边界记录和首批结构化候选包；
- `reviews/`：保存贡献、完成状态、公开资格和内容差异的人工审核记录；
- `reports/`：保存敏感信息扫描及结构一致性检查结果。

该目录位于公开项目仓库之外。执行治理命令时仅在当前进程中设置 `PORTFOLIO_GOVERNANCE_HOME`，不要求设置永久系统环境变量，也不向日志打印其真实值。

### 3.2 Public project handoff

公开项目仓库只接收重新撰写且经过最小化处理的公开交接信息：

```text
docs/
├── 00-文档状态索引.md
├── 08-current-implementation-status.md
└── 09-portfolio-asset-library-status.md
```

`09-portfolio-asset-library-status.md` 记录：

- 全量资产数量与分类；
- 首批四项候选的公开准备状态；
- 私有来源与公开项目之间的安全边界；
- 当前阻塞项；
- 后续 CaseStudy 契约和公开 bundle 的实施顺序。

项目文档不得包含私有治理目录的绝对路径、原始文件名、内部标识或未审核内容。

## 4. Data Model

### 4.1 Inventory record

每项资产使用以下字段：

```json
{
  "id": "stable-id",
  "contentType": "MAINLINE | FEATURE | INCIDENT | EVALUATION | KNOWLEDGE_OUTPUT",
  "achievementStatus": "DELIVERED | IMPLEMENTED_TESTED | VALIDATED_PROTOTYPE | INVESTIGATED | OBSERVED_LEARNING | INCOMPLETE",
  "contributionType": "PRIMARY | COLLABORATIVE | ASSISTED | OBSERVED_LEARNING",
  "publicPriority": "P0 | P1 | P2 | EXCLUDE",
  "evidenceStatus": "VERIFIED | PARTIALLY_VERIFIED | OWNER_CONFIRMED | INSUFFICIENT",
  "reviewState": "PUBLIC_REVIEW_REQUIRED | HOLD | EXCLUDE",
  "summary": "脱敏后的最小说明"
}
```

状态不由脚本或模型自动升级。存在疑义时取更保守值。

### 4.2 Refined candidate packets

SQL 增量候选包复用当前 Project、Claim、Evidence、ClaimEvidenceLink、TimelineEvent 和 QuestionPreset 语义，但仅作为私有候选。

三项 Case 候选包使用独立的预备结构：

```text
id, slug, code, type, title, summary, problem, actions[], decisions[],
verification[], outcome, limitations[], achievementStatus,
contributionType, optionalProjectId, claimIds, evidenceIds,
timelineEventIds, questionPresetIds
```

这些字段用于驱动后续 CaseStudy 公共契约设计，不由当前 schema 2.0 validator 消费。

## 5. Data Flow

```text
Obsidian 原始知识库
  → 本轮全量盘点与证据台账
  → 私有资产边界记录
  → 状态与贡献决策记录
  → 首批四项脱敏候选包
  → 敏感信息与结构检查
  → 项目内公开状态交接文档
  → 后续 CaseStudy 契约设计
  → 人工批准后才可能进入公开 bundle
```

任何阶段发现敏感信息、证据不足或贡献不清，都将对应资产保持为 `HOLD`，不会降级检查或绕过审核。

## 6. Privacy and Safety Rules

公开仓库和候选公开文案不得包含：

- 公司、内部产品、真实人员或团队名称；
- 内部域名、URL、IP、端口、主机和文件系统路径；
- 账号、角色、玩家、活动、渠道、区服等真实标识；
- 凭据、Token、密钥、Cookie 或连接字符串；
- 原始 SQL、表名、日志、堆栈和未脱敏代码；
- 未审核截图及其本地路径；
- 无证据支持的生产规模、性能、使用次数和长期影响。

允许保留的内容仅限于：

- 抽象后的问题机制；
- 本人贡献边界；
- 可复述的技术决策；
- 已验证的行为结果；
- 明确标注的限制条件；
- 人工确认可公开的保守指标。

## 7. Validation

### 7.1 Private workspace checks

- 工作区解析路径必须位于公开项目仓库之外；
- 全量资产编号唯一；
- 每项资产必须具备类型、状态、贡献、优先级和审核状态；
- P0/P1 资产必须存在明确审核记录；
- 扫描 IPv4、URL、Windows 绝对路径、邮箱、凭据关键词、内部标识和 SQL 关键词；
- 扫描结果只报告规则和数量，不输出命中原文。

### 7.2 Candidate checks

- SQL 增量不得覆盖现有已批准事实；
-每个关键 Claim 至少关联一项直接 Evidence 候选；
- Evidence 的 `sourceCount` 只统计人工接受的来源；
- Case 候选必须包含问题、行动、验证、结果和限制；
- CodeGraph 同时保留正向结果和失效边界；
- 未确认发布的功能不能写成已上线。

### 7.3 Public project checks

- 项目状态文档与私有候选数量一致；
- 文档中不出现私有路径和来源文件名；
- `backend/src/main/resources/public-data/` 保持不变；
- 当前 schema、API、前端和 Agent 测试基线不因本阶段发生变化；
- Git 变更只包含本阶段新增或明确更新的文档，不包含用户已有视觉改动。

## 8. Deliverables

私有交付物：

1. 全量资产边界记录；
2. 人工决策记录；
3. SQL 审计增量候选与差异审查；
4. 多语言图片修复 Case 候选；
5. 角色重置 Case 候选；
6. CodeGraph 评测 Case 候选；
7. 敏感信息和结构检查报告。

公开项目交付物：

1. 本设计文档；
2. `docs/09-portfolio-asset-library-status.md`；
3. 更新后的文档状态索引和实现状态；
4. 后续 CaseStudy 公共契约的明确输入边界。

## 9. Success Criteria

满足以下条件即视为本阶段完成：

- 全量资产均进入私有治理索引，没有遗漏已盘点类别；
- 首批四项均形成独立、结构化、脱敏候选包；
- 所有候选通过敏感信息和结构检查；
- 项目能够准确展示资产准备状态，但公开运行时事实没有被擅自扩大；
- 后续 CaseStudy 契约可以直接以候选包为输入，无需重新翻阅全部原始知识库；
- 当前用户拥有的前端视觉改动未被覆盖、暂存或提交。
