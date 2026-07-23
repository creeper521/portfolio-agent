# Portfolio CaseStudy Public Contract Design

> **状态：** 后端纵向切片与首批公开内容已实施并验证
> **确认日期：** 2026-07-23
> **实施范围：** schema 3.0、后端只读 API、首批公开内容与发布工具

> **实施结果（2026-07-23）：** schema 3.0 与 2.0 兼容加载、CaseStudy 领域模型/校验/服务/API、三个公开 Case 和 SQL 审计主线 2026-07 扩展已完成。Case 前端列表/详情与视觉、Agent Case 检索/上下文/可执行预设、生产部署与线上验收尚未完成。

## 1. 目标

现有公开内容模型只支持 `Project`。多语言图片上传修复、测试角色重置工具和 CodeGraph 评测都是可独立说明的成果，但它们没有真实、稳定的父项目。将它们伪装成 Project 会扭曲内容结构，也会让 Claim、Evidence、QuestionPreset 和 TimelineEvent 的引用失真。

本设计新增一等公民 `CaseStudy`，并完成以下纵向切片：

1. 将公开 Bundle 从 schema `2.0` 升级到 `3.0`。
2. 显式保留 schema `2.0` 的读取兼容路径。
3. 新增 CaseStudy 领域模型、校验、只读服务和 API。
4. 在首个 schema `3.0` Bundle 中纳入 SQL 审计主线扩充和三个独立案例。
5. 保持 Claim–Evidence Link 为唯一验证权威。
6. 通过现有治理流程审核、构建、发布和校验公开 Bundle。

本阶段不修改前端。案例页面与 Agent 案例上下文将在 Agent 页面重构完成后单独设计。

## 2. 已确认的产品决策

### 2.1 CaseStudy 是独立聚合

采用独立 `CaseStudy`，不抽象统一 `WorkItem`，也不把案例嵌入或伪装成 Project。

原因：

- 现有 Project API 和前端已经稳定，首期不应为统一抽象进行大范围重构。
- 三个案例都能独立表达问题、行动、验证、结果与局限。
- 可选 `projectId` 可以在未来表达真实父项目，但不得为了满足结构而虚构关联。

### 2.2 首批公开内容

首个 schema `3.0` Bundle 包含：

- 现有 SQL 审计 Project 的七月扩充；
- 多语言图片上传结果保留 Case；
- 测试角色重置工具 Case；
- CodeGraph 辅助代码检索评测 Case。

CodeGraph 只发布定性结论。未经单独数字复核的精确提升百分比、通用效率承诺和项目规模信息不进入公开 Bundle。

### 2.3 前端与 Agent 延期

本阶段不修改：

- `frontend/` 下的类型、Repository、路由、页面、样式和测试；
- Agent ContextEnvelope、QuestionPreset 执行、检索语料和工具协议；
- 首页、项目页、导航或视觉系统。

后端可以提前返回 Case 数据，但当前前端不会消费这些字段。

## 3. schema 3.0 契约

### 3.1 顶层结构

schema `3.0` 在现有 PortfolioSnapshot 中新增：

```json
{
  "schemaVersion": "3.0",
  "projects": [],
  "cases": [],
  "claims": [],
  "claimEvidenceLinks": [],
  "questionPresets": [],
  "evidence": [],
  "timelineEvents": []
}
```

`cases` 在 schema `3.0` 中是必填数组。它可以为空，但不得缺失或为 `null`。

### 3.2 CaseStudy

`CaseStudy` 使用显式不可变 Java 类，字段如下：

```text
id
code
slug
type
title
summary
problem
actions[]
decisions[]
verification[]
outcome
limitations[]
achievementStatus
contributionType
projectId?
claimIds[]
evidenceIds[]
timelineEventIds[]
questionPresetIds[]
```

规则：

- `id`、`code`、`slug` 全局唯一。
- `slug` 使用现有小写字母、数字和连字符约束。
- `actions`、`verification`、`limitations`、`claimIds`、`evidenceIds`、`timelineEventIds` 和 `questionPresetIds` 不得为空。
- `decisions` 可以为空；简单工具类 Case 不强制编造架构决策。
- `projectId` 可选；非空时必须引用真实 Project。
- `achievementStatus` 和 `contributionType` 复用现有枚举。
- 成果状态不得因页面展示、测试通过或 Agent 判断而升级。

### 3.3 CaseType

首期公开值：

```text
FEATURE
EVALUATION
```

枚举同时保留 `INCIDENT`，但首批 Bundle 不发布事故案例。未来启用 `INCIDENT` 必须经过独立的贡献归属与隐私审核。

### 3.4 Claim

`ClaimSubjectType` 新增 `CASE`。

当 `subjectType = CASE` 时：

- `subjectId` 必须引用一个存在的 CaseStudy；
- 该 Claim 只能被对应 Case 的 `claimIds` 引用；
- Achievement Claim 继续要求至少一个 `APPROVED / DIRECT` Claim–Evidence Link；
- Case 不能借用 Project Claim 来提高验证状态。

### 3.5 QuestionPreset

`QuestionDefinition` 新增 `caseIds[]`。

兼容规则：

- schema `2.0` 的 QuestionPreset 被规范化为 `caseIds=[]`；
- schema `3.0` 中 `projectIds` 与 `caseIds` 至少一个非空；
- 一个问题可以同时关联真实 Project 和 Case，但不得通过空壳 Project 建立关联；
- 新增 placement `CASE`；
- Case 专属问题当前只作为公开数据返回，不进入 Agent 可执行上下文。

### 3.6 TimelineEvent

`TimelineEvent` 新增 `caseIds[]`。

规则：

- schema `2.0` 的事件被规范化为 `caseIds=[]`；
- schema `3.0` 中 `projectIds` 与 `caseIds` 至少一个非空；
- 事件引用的 Claim 和 Evidence 必须存在；
- Evidence 必须为 `APPROVED` 且不公开原始内容。

### 3.7 Evidence 与 Link

Evidence 保持全局集合，不嵌入 CaseStudy。

CaseStudy 的验证权威仍来自：

```text
CaseStudy.claimIds
    -> Claim(subjectType = CASE)
    -> ClaimEvidenceLink
    -> Evidence(publicStatus = APPROVED)
```

`CaseStudy.evidenceIds` 只用于公开详情投影和导航，不得替代 Claim–Evidence Link。

## 4. schema 兼容策略

### 4.1 显式双版本读取

加载器只接受：

```text
2.0
3.0
```

行为：

- schema `2.0`：规范化为 `cases=[]`，QuestionPreset 和 TimelineEvent 的 `caseIds=[]`。
- schema `3.0`：严格要求 Case 相关字段满足本设计。
- 未知版本：立即失败。
- schema `3.0` 缺少 `cases`：立即失败。
- 不允许把 `3.0` 静默降级为 `2.0`。

### 4.2 当前发布版本

新的 active Bundle 使用 schema `3.0`。schema `2.0` fixture 只用于兼容和回滚测试，不继续扩充内容。

### 4.3 既有接口兼容

以下接口保持现有语义与响应结构：

```text
GET /api/v1/portfolio
GET /api/v1/projects/{slug}
```

项目消费者不会收到伪装成 Project 的 Case。

## 5. 后端 API

### 5.1 新增接口

```text
GET /api/v1/cases
GET /api/v1/cases/{slug}
```

列表接口返回 `CaseSummaryResponse[]`，包含：

```text
slug, code, type, title, summary, achievementStatus, contributionType
```

详情接口返回 `CaseDetailResponse`，包含：

```text
摘要字段
problem
actions[]
decisions[]
verification[]
outcome
limitations[]
projectSlug?
evidence[]
suggestedQuestions[]
```

DTO 不暴露私有来源、治理状态文件、原始截图、内部路径或审计记录。

### 5.2 聚合接口

`GET /api/v1/public-content` 新增顶层 `cases` 字段，内容为 Case 详情投影。

现有字段保持原义：

```text
projects
claims
claimEvidenceLinks
evidence
timeline
questionPresets
```

`projectSlugsByEvidenceId` 保留。新增 `caseSlugsByEvidenceId`，用于后续前端构建案例证据反向索引。

### 5.3 服务边界

PortfolioService 增加：

```text
getCases()
getCase(slug)
```

Case 详情投影只能返回：

- Case 自身引用的 Evidence；
- `publicStatus = APPROVED`；
- `rawContentPublic = false`；
- 通过 Case QuestionPreset 关联得到的建议问题。

### 5.4 错误处理

未知 Case slug 抛出 `CaseNotFoundException`，映射为：

```text
HTTP 404
errorCode = CASE_NOT_FOUND
```

禁止：

- 回退到列表首项；
- 返回空对象冒充成功；
- 暴露本地路径、stack trace 或内部 ID；
- Bundle 无效时返回部分数据。

## 6. 首批公开内容

### 6.1 SQL 审计 Project 扩充

新增四条窄范围 Claim：

1. 负号开头关键词按固定搜索文本处理，而不是命令选项。
2. 查询界面支持一个服务器目标和多个已允许的日志来源。
3. 多来源查询保留成功结果，失败来源可以独立重试。
4. 连接检查只覆盖当前任务已选择的目标。

配套内容：

- 一个七月迭代 Evidence collection；
- 四个窄范围 DIRECT links；
- 一个七月 TimelineEvent；
- 两个 QuestionPreset。

限制：

- 不公开 shell 命令、主机、端口、路径、内部表名、真实环境或原始截图。
- 不推断生产规模、长期效果、使用人数或性能收益。
- Evidence `sourceCount` 必须来自人工接受的私有来源数，不能按笔记文件数量推断。

### 6.2 多语言图片上传 Case

公开语义：

- 问题：后一次语言上传覆盖了先前可见语言集合。
- 行动：持久化语言集合由已有语言与本次实际上传语言取并集。
- 验证：两次按不同语言顺序上传后，两种语言结果可以同时查询。
- 状态：`DELIVERED / PRIMARY`。

排除：

- 内部模块、表、渠道、区域、存储路径和原始语言标识。

### 6.3 测试角色重置 Case

公开语义：

- 选择允许的环境；
- 使用外部输入标识查询；
- 明确确认破坏性重置；
- 验证旧角色不可用，并在重新登录后创建新角色。

状态：`DELIVERED / PRIMARY`。

排除：

- 删除语句、表名、环境名、服务器信息、示例账号和内部字段。
- 不发布无法证明的具体使用次数；仅保留“用于频繁创建干净测试账号”的定性背景。

### 6.4 CodeGraph 评测 Case

公开语义：

- 方法：符号检索基准与两组 MCP 任务。
- 正向发现：精确与全文符号检索在选定样本中能缩短定位过程。
- 负向发现：方法引用、Lambda、依赖注入、事件分发和重复符号会造成关系缺失或噪声。
- 决策：CodeGraph 用于范围收窄和批量导航；文本搜索与文件阅读用于精确验证。
- 状态：`PROTOTYPE / PRIMARY`。

排除：

- 精确提升百分比、通用生产力承诺、原项目名、仓库规模、内部符号和源代码样本。

## 7. 发布与治理

### 7.1 不直接编辑批准状态

所有 Candidate、Approval、Audit、Publish 状态继续通过 `scripts/portfolio-governance.ps1` 管理。

禁止直接编辑：

- Approval 文件；
- audit 记录；
- active bundle 指针；
- publish state。

### 7.2 发布顺序

实施阶段必须按以下顺序执行：

1. 从私有候选生成最小化公开候选。
2. 运行 schema、引用、隐私和兼容性检查。
3. 生成只包含公开字段的最终 diff。
4. 请求内容所有者核对最终 diff。
5. 收到明确确认后，通过治理 CLI approve。
6. 通过治理 CLI build/publish。
7. 校验 active Bundle、checksums、manifest 和启动加载。

测试通过不构成人工 Approval。

### 7.3 manifest 与 checksum

schema `3.0` manifest 增加 Case 计数，并保持所有文件 hash 校验。

建议计数字段：

```text
projectCount
caseCount
claimCount
claimEvidenceLinkCount
questionPresetCount
evidenceCount
timelineEventCount
```

运行时 Bundle hash 必须覆盖新增 Case 内容，避免 Case 变化不触发版本变化。

## 8. 校验规则

validator 必须检查：

- Case ID、code、slug 唯一；
- CaseType、AchievementStatus、ContributionType 非空；
- 必填文本与列表非空；
- 可选 projectId 在非空时存在；
- Case Claim 的 subjectId 存在且归属一致；
- Case 引用的 Claim、Evidence、TimelineEvent、QuestionPreset 存在；
- Achievement Claim 拥有 APPROVED DIRECT Evidence；
- QuestionPreset 与 TimelineEvent 至少关联一个 Project 或 Case；
- 未批准 Evidence 不进入运行时；
- 未知 Case ID 和 slug 失败关闭；
- schema 版本与字段要求一致。

## 9. 测试策略

### 9.1 领域与迁移

- CaseStudy 不可变模型契约测试；
- CaseType 枚举测试；
- schema `2.0` 规范化为 `cases=[]`；
- schema `3.0` 正常加载；
- schema `3.0` 缺失 cases 失败；
- 未知 schema 失败。

### 9.2 validator

- 重复 Case ID/code/slug；
- 非法 slug；
- 空必填字段；
- 错误 projectId；
- CASE Claim 指向不存在或不同 Case；
- 无 DIRECT Evidence 的成果 Claim；
- Case 引用不存在的 Evidence、TimelineEvent 或 QuestionPreset；
- QuestionPreset 和 TimelineEvent 同时缺少 Project/Case 关联。

### 9.3 service 与 controller

- Case 列表只返回摘要；
- Case 详情只返回自己的已批准 Evidence；
- 建议问题按 caseIds 关联；
- 未知 slug 返回 404 `CASE_NOT_FOUND`；
- `/api/v1/public-content` 同时返回 Project 和 Case；
- 现有 Project API 回归测试保持通过。

### 9.4 发布与隐私

- manifest 计数包含 Case；
- checksum 和 runtime Bundle hash 覆盖 Case 内容；
- schema `2.0` fixture 兼容；
- schema `3.0` active Bundle 启动加载；
- 公开内容扫描 IP、URL、Windows 路径、邮箱、凭据词、原始 SQL、内部标识和私有来源名；
- `frontend/` 最终 diff 为空。

### 9.5 完成门槛

必须通过：

```powershell
mvn.cmd -f backend/pom.xml test
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

若发布工具或公共 Bundle 发生变化，还必须执行完整发布 dry-run、checksum 校验和 packaged-JAR 启动验证。

## 10. 明确不在本阶段

- `/cases`、`/cases/:slug` 前端路由与页面；
- 前端 Case 类型和 Repository 映射；
- 首页或导航调整；
- Agent Case 上下文；
- Case 问题的 Agent 执行；
- Case 检索语料、Embedding 或只读工具；
- 数据库、认证、SSE、Spring AI 或新增模型能力；
- 私有知识库运行时读取；
- 部署。

## 11. 后续顺序

1. 完成并合并当前 Agent 页面重构。
2. 单独设计案例列表与详情页。
3. 实现前端 Case 类型、Repository、路由和页面。
4. 单独扩展 Agent ContextEnvelope、检索和建议问题执行。
5. 统一首页、项目、案例与 Agent 的视觉和导航。
6. 功能完整后再进入部署阶段。

## 12. 验收结论

本设计对应的后端阶段已完成并具备：

- 可显式读取 schema `2.0/3.0` 的加载器；
- schema `3.0` CaseStudy 正式契约；
- 三个公开 Case 的只读列表和详情 API；
- SQL 主线扩充和三个 Case 的人工审核公开内容；
- 可验证的 Claim–Evidence 关系；
- 失败关闭的引用、版本与路由行为；
- 无任何前端源文件变更；
- 无部署行为。

当前随包 Bundle 为 schema `3.0`、内容版本 `2026-07-23.1`。三个公开 Case 分别为多语言图片上传结果保留、测试角色重置工具和 CodeGraph 定性评测；SQL 审计 Project 已加入 2026-07 能力扩展。CodeGraph 不包含精确效率指标或内部项目信息。

尚未完成：

- CaseStudy 前端列表/详情页与视觉设计；
- Agent 对 CaseStudy 的检索、上下文组装与可执行问题预设；
- 生产部署与线上验收。
