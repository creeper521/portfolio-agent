# Tool 服务 Docker 化运维改造核心项目公开设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

**日期：** 2026-08-04
**状态：** 已确认设计，待实施计划
**适用范围：** 作品集公开内容、Project/Case 展示、Agent 检索与回答

## 1. 背景

当前公开作品集包含多个核心 Project 和独立 Case，但尚未收录 Tool 服务 Docker 化运维改造。现有“容器化学习手册”仅代表知识整理，明确不代表生产迁移完成，不能替代真实工程改造产出。

本次新增一条独立核心 Project，描述工具端、部署端和服务端协同完成的 Docker 化运维改造。公开内容只表达已经能够由代码、测试、配置和提交历史支持的事实，不公开原始仓库路径、内部主机、环境标识、账号、凭据或业务专有名词。

## 2. 目标

1. 新增一个 `PRIMARY` 展示层级的核心 Project，完整呈现跨三个工程边界的改造闭环。
2. 将个人贡献标记为 `COLLABORATIVE`，准确表达“深度参与、多人协作”。
3. 用三个关联 Case 分别承载运行时路由、批量重启和容器改时三个技术主题。
4. 建立 Project、Case、Claim、Evidence、TimelineEvent、QuestionPreset 和 RAG 文档之间的完整引用关系。
5. 复用现有数据驱动页面、API 和 Agent 能力，不新增专用前端页面或硬编码回答。
6. 通过现有内容治理、隐私、检索和打包门禁后再进入运行时 Bundle。

## 3. 非目标

- 不公开原始代码、提交详情、内部截图、服务器地址、部署目录和环境名称。
- 不把团队共同完成的整体改造描述为个人主导或独立交付。
- 不声明缺少证据支持的效率百分比、故障率、用户规模或长期生产收益。
- 不声明已经具备完整自动回滚、生产级鉴权、应用 readiness 或线上验收闭环。
- 不把“容器化学习手册”改写成工程交付证据；学习 Case 保持原有事实边界。
- 不为该 Project 新增前端特例、独立 API、数据库结构或回答路由。

## 4. 方案选择

采用“核心 Project + 三个技术 Case”方案。

相较于只新增 Project，该方案能把主线叙事和具体技术问题分离；相较于单个综合 Case，三个 Case 的职责边界更清楚，也更适合已有 Case 页面、筛选、证据追溯和 Agent 上下文交接能力。

## 5. 核心 Project 契约

### 5.1 身份与状态

| 字段 | 设计值 |
|---|---|
| `id` | `tool-docker-transformation-project` |
| `code` | `P-06` |
| `slug` | `tool-docker-transformation` |
| `title` | `Tool 服务 Docker 化运维改造` |
| `periodStart` | `2026-07-01` |
| `periodEnd` | `2026-07-30` |
| `status` | `DELIVERED` |
| `contributionType` | `COLLABORATIVE` |
| `careerTrack` | `JAVA_BACKEND` |
| `projectNature` | `TOOL` |
| `displayTier` | `PRIMARY` |

`DELIVERED` 只表示本次公开范围内的核心改造能力已经实现并进入代码主线，不等价于完整生产部署、自动回滚和线上效果验证。

### 5.2 公开摘要

> 围绕服务从裸机脚本向 Docker Compose 运行模式迁移，深度参与工具端、部署端与服务端的协同改造，统一服务启停、批量重启、状态日志与容器改时能力，同时兼容存量 Shell 环境。

### 5.3 背景

原有服务管理能力主要面向裸机脚本。容器化部署引入了运行时识别、多环境精确寻址、Compose 服务生命周期、环境变量重建、容器内日志和虚拟时间等新问题。改造需要在不破坏存量 Shell 环境的前提下，为容器环境提供统一且可核对的运维入口。

### 5.4 我的职责

1. 深度参与改造范围拆解和工具端核心能力实现，协同核对部署端与服务端运行约束。
2. 参与 Shell/Docker Compose 双运行时抽象、多环境精确路由及存量环境兼容设计。
3. 参与单服务启停、四核心服务顺序重启、状态与错误日志查询、容器改时等链路落地。
4. 补充路由、命令编排、超时降级、部分失败和改时校验等测试与异常场景验证。

所有职责使用“深度参与”“参与”“协同”表述，不使用“主导”“独立完成”或“全权负责”。

### 5.5 解决方案

工具端以统一服务操作策略隔离裸机 Shell 与 Docker Compose 两种运行时，并根据受控环境注册信息选择具体实现。Docker 路径通过主机和部署身份精确定位 Compose 环境，在同一入口下提供服务生命周期、批量重启、状态、错误日志和虚拟时间操作。部署端负责镜像、Compose 配置和环境注册信息，服务端配合服务注销、进程信号和虚拟时间运行约束，三者共同形成改造闭环。

### 5.6 关键决策

1. 保留 Shell 适配器并新增 Docker Compose 适配器，避免容器化改造破坏存量环境。
2. Docker 环境不只按主机匹配，而是结合部署身份精确寻址，降低同机多环境误操作风险。
3. 根据服务当前状态及环境变量是否变化，在 `start`、`restart` 和 `force-recreate` 之间动态选择，不把三种操作错误地视为等价。
4. 批量重启采用异步顺序状态机，显式表达成功、部分成功和失败，并阻止同环境重复任务。
5. 容器时间采用进程视角虚拟时间，不修改宿主机时间；规则热更新后执行误差校验并保留回拨边界。

### 5.7 技术标签

- Java
- Spring Boot
- Strategy Pattern
- Docker Compose
- Jenkins
- Harbor
- Ansible
- Eureka
- libfaketime

### 5.8 验证

1. 核对运行时策略选择和受控环境路由，覆盖 Shell/Docker 分流及同机多环境场景。
2. 验证单服务命令编排、环境变化后的容器重建、四核心服务顺序重启和部分失败结果聚合。
3. 验证状态查询超时降级、错误日志读取边界和并发任务去重。
4. 验证虚拟时间热更新、时间回拨识别、操作互斥和改时后允许误差。
5. 对照工具端、部署端和服务端代码及配置检查跨工程约束是否一致。

### 5.9 结果与边界

公开结果文案：

> 核心改造能力已实现并进入代码主线，Tool 可以在兼容存量 Shell 环境的同时管理 Docker Compose 服务，并覆盖环境路由、服务生命周期、批量重启、状态日志和容器改时等主要运维场景。

公开边界文案：

> 本项目为多人协作成果；公开证据支持核心实现与测试状态，不宣称完整生产验收、自动回滚闭环、生产级鉴权、应用 readiness 或量化业务收益。

### 5.10 简历式核心产出

> 深度参与 Tool 服务管理 Docker 化改造，基于策略模式抽象 Shell 与 Docker Compose 双运行时，打通环境路由、服务启停、批量顺序重启、状态与日志查询、容器改时等核心链路，并完善超时降级、部分成功及多环境防串机制，实现容器环境统一运维并兼容原有裸机部署。

该文案用于 Project 的责任和结果表达，不单独作为无证据 Claim 发布。

## 6. 关联 Case

### 6.1 CASE-50：Shell 与 Docker Compose 双运行时路由

| 字段 | 设计值 |
|---|---|
| `id` | `case-tool-docker-runtime-routing` |
| `slug` | `tool-docker-runtime-routing` |
| `type` | `FEATURE` |
| `achievementStatus` | `IMPLEMENTED_TESTED` |
| `contributionType` | `COLLABORATIVE` |

内容边界：解释策略接口、双适配器、受控环境注册和精确寻址，不公开主机、路径、环境代号或内部配置值。

### 6.2 CASE-51：四核心服务异步顺序重启

| 字段 | 设计值 |
|---|---|
| `id` | `case-tool-docker-sequential-restart` |
| `slug` | `tool-docker-sequential-restart` |
| `type` | `FEATURE` |
| `achievementStatus` | `IMPLEMENTED_TESTED` |
| `contributionType` | `COLLABORATIVE` |

内容边界：解释固定顺序、任务去重、阶段结果和超时降级。服务名公开时使用通用角色名称，不公开业务集群标识。

### 6.3 CASE-52：容器时间热更新

| 字段 | 设计值 |
|---|---|
| `id` | `case-tool-docker-virtual-time` |
| `slug` | `tool-docker-virtual-time` |
| `type` | `FEATURE` |
| `achievementStatus` | `IMPLEMENTED_TESTED` |
| `contributionType` | `COLLABORATIVE` |

内容边界：解释进程视角虚拟时间、共享规则、热更新、回拨识别和误差校验，不把测试能力描述为生产时间治理能力。

三个 Case 均关联 `projectSlug=tool-docker-transformation`，并进入 Project 的 `featuredCaseIds`。

## 7. Claim 设计

发布以下九条 Claim；具体 statement 和 detail 在候选包阶段保持短事实与解释分离。

| ID | Category | 核心事实 | 状态 | 依据 |
|---|---|---|---|---|
| `claim-tool-docker-background` | `BACKGROUND` | 裸机脚本无法直接覆盖容器运行时语义 | `DELIVERED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-responsibility` | `RESPONSIBILITY` | 深度参与跨工具、部署、服务三个边界的改造 | `DELIVERED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-dual-runtime` | `TECHNICAL_DECISION` | 采用 Shell/Docker Compose 双策略保持兼容 | `IMPLEMENTED_TESTED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-exact-routing` | `IMPLEMENTATION` | 通过受控部署身份精确定位容器环境 | `IMPLEMENTED_TESTED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-lifecycle` | `IMPLEMENTATION` | 按状态和配置变化选择启动、重启或重建 | `IMPLEMENTED_TESTED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-restart-state-machine` | `IMPLEMENTATION` | 批量重启具备顺序、去重和分级结果 | `IMPLEMENTED_TESTED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-virtual-time` | `IMPLEMENTATION` | 容器时间可在不修改宿主机时钟的前提下热更新 | `IMPLEMENTED_TESTED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-verification` | `VERIFICATION` | 关键路由、命令、失败和改时场景有测试或代码核对 | `IMPLEMENTED_TESTED` | `EVIDENCE_SUPPORTED` |
| `claim-tool-docker-boundary` | `LIMITATION` | 尚无公开证据支持完整生产验收和量化收益 | `INVESTIGATED` | `SELF_DECLARED` |

成果性 Claim 必须拥有已批准的 `DIRECT` ClaimEvidenceLink。限制 Claim 不得被检索或模型改写为正向交付结论。

## 8. Evidence 设计

新增三组脱敏证据摘要，全部设置 `rawContentPublic=false`。

1. `evidence-tool-docker-tool-implementation-tests`，类型 `COLLECTION`：汇总生产代码与自动化测试的脱敏核对结果，支持策略路由、生命周期、异步重启、状态日志与改时实现。
2. `evidence-tool-docker-deployment-contract`，类型 `CODE`：支持镜像、Compose、环境注册和共享时间规则等部署契约。
3. `evidence-tool-docker-cross-repository-review`，类型 `COLLECTION`：支持工具端、部署端与服务端约束已经进行交叉核对。

Evidence 摘要只描述可公开机制和验证范围。`sourceCount` 由候选包实际纳入并经过人工复核的来源数量决定，不在设计阶段虚构。

## 9. Timeline 与问题入口

新增一条 TimelineEvent：

- ID：`timeline-tool-docker-transformation`
- 时间：2026-07-01 至 2026-07-30
- 类型：`ITERATION`
- 状态：`DELIVERED`
- 标题：`Tool 服务 Docker 化运维改造形成闭环`
- 关联 Project、三个 Case、九条 Claim 和三组 Evidence。

新增三个 QuestionPreset：

1. `question-tool-docker-overview`：详细介绍改造背景、职责、方案、验证和边界。
2. `question-tool-docker-runtime-routing`：为什么保留 Shell/Docker 双运行时，如何避免多环境串操作。
3. `question-tool-docker-restart-and-time`：批量重启与容器改时如何处理失败、并发和一致性。

问题入口面向 `INTERVIEWER` 和 `MENTOR` 优先展示，并关联对应 Project/Case 和 Claim category。

## 10. 数据与运行链路

```text
已脱敏候选内容
  -> Project / Case / Claim / Evidence / Timeline / QuestionPreset
  -> 结构、引用、状态与隐私校验
  -> 人工审核并绑定候选 payload hash
  -> 构建 RAG 文档与本地索引
  -> 发布运行时 Bundle
  -> 后端公开 API
  -> Project / Case / Evidence / Timeline 页面
  -> Agent 检索、引用与回答
```

现有页面已经按 Project 和 Case 数据契约渲染。本次不修改 `ProjectPage.vue`、`CasePage.vue` 或路由结构；新增实体进入 Bundle 后由现有 Repository、DTO 映射和页面自动展示。

## 11. 失败处理与一致性

- 任一 ID、slug、反向引用、状态或 Evidence 关系不合法时，候选验证失败，不得进入发布。
- 任一成果性 Claim 缺少已批准的直接证据时，保持未发布或降低事实状态，禁止伪造 VERIFIED。
- 隐私检查发现内部地址、路径、凭据、业务标识或不允许 URL 时，阻断候选。
- 检索基准无法稳定召回新 Project/Case，或错误召回“容器化学习手册”替代工程项目时，阻断发布。
- 发布前后 Bundle hash、实体计数或引用不一致时，保持当前已激活版本，不切换运行时内容。
- 现有未提交代码修改属于其他任务，本次实施不得覆盖、重置、暂存或提交这些改动。

## 12. 验证策略

### 12.1 内容契约

- 验证新 Project 使用 `P-06`、唯一 ID 和唯一 slug。
- 验证三个 Case 均反向关联新 Project，且 Project 精选 Case 顺序稳定。
- 验证 `COLLABORATIVE` 在 Project、Case 和相关 Claim 上保持一致。
- 验证 `DELIVERED` 与 `IMPLEMENTED_TESTED` 不被错误升级为生产可用声明。

### 12.2 隐私与事实

- 运行仓库隐私检查和治理校验。
- 搜索候选与 Bundle，确认不存在本地绝对路径、内部主机、凭据和原始日志。
- 检查所有量化表述；没有直接证据的数字不得发布。
- 检查所有第一人称职责表述，不得出现与 `COLLABORATIVE` 冲突的“主导”或“独立完成”。

### 12.3 检索与回答

- 新项目总览问题能够召回 Project 的背景、职责、方案、验证、结果和限制。
- 双运行时问题优先召回 CASE-50，而不是学习手册。
- 顺序重启问题优先召回 CASE-51，并保留部分成功和超时边界。
- 容器改时问题优先召回 CASE-52，并明确“不修改宿主机时间”。
- Agent 回答必须返回有效 Claim/Evidence 引用，不能从代码外推完整生产效果。

### 12.4 应用回归

- 后端内容加载、校验、API、检索与确定性回答测试。
- 前端 Project 列表、详情、关联 Case、证据、时间线和 Agent 交接测试。
- 前端测试、构建、后端测试、隐私检查、静态 Bundle 校验和发布门禁。

## 13. 文档同步

实施完成后更新：

- `docs/08-当前实现状态.md`：记录 Project/Case/Claim/Evidence 和问题入口的新运行时数量及能力状态。
- `docs/09-作品集资产库状态.md`：记录 tool_docker 公开资产的审核与发布状态。
- `docs/11-项目演进日志.md`：记录新增核心 Project 及其相对既有“容器化学习手册”的边界变化。
- `docs/00-文档状态索引.md`：登记本设计与后续实施计划状态。

## 14. 完成标准

只有同时满足以下条件，才可声明本次任务完成：

1. 新 Project、三个 Case 及全部关联实体通过结构和隐私校验。
2. 人工审核确认公开文案、贡献边界和证据摘要准确。
3. 新内容进入批准后的运行时 Bundle，索引和校验和重新生成。
4. 项目页、三个 Case 页、证据页、时间线和 Agent 问答均能访问并保持引用闭环。
5. 检索不会把“容器化学习手册”误当成工程改造成果。
6. 后端、前端、隐私和发布验证全部通过。
7. 文档状态、资产状态和项目演进日志同步完成。
