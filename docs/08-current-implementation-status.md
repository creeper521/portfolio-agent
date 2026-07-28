# 当前实现与未实现功能盘点

> **状态：** 当前实现盘点（以生产代码、配置、自动化测试和发布脚本为证据）
> **核对日期：** 2026-07-28
> **代码基线：** 本地 `master` `ba4f59a`
> **维护规则：** 功能合入、默认开关或产品边界变化时，同步更新本文与 `00-文档状态索引.md`。

## 1. 结论

当前项目已经从“一个确定性问答的 V0”扩展为一个可打包交付的公开实习作品集 Agent：前端具备六个正式路由和页面内存工作台，后端具备公开内容 API、严格回答契约、内容治理、可选模型表达、本地混合检索、固定只读工具和引用式多轮，并提供单 JAR、Docker 与完整发布门禁。

Case 后端与发布前五项收尾已经完成：`source=CASE`、Project/Case 互斥、未知公开主体 fail-closed、真实随包 Bundle 集成、Case API/v2 JAR 冒烟，以及显式 `-RequireLiveProvider` 响应门禁均已有实现和自动化测试。普通 CI 强制关闭模型调用；真实 Provider 外部调用仍需在获批生产候选环境单独留证。

但它仍不是完整 V1。产品方向是独立 Case 信息架构，同时复用既有 Dossier 能力；故事页属于后续 selected-case 增强。模型表达与本地检索默认关闭；共享 Case 目录模型和共享详情投影已存在，前端也已调用 `/api/v2/answers`，但独立 `/cases`、`/cases/:slug`、规范重定向、具体 UI 与生产验收仍未完成。动态工具/插件、编排、多 Agent、持久会话、数据库、认证和私有 Copilot 均未实现。

内容准备层已经登记 68 项私有资产。61 项非排除资产已通过精确哈希人工 Approval，本地发布并原子导入为 schema 3.0、内容版本 `2026-07-27.1` 的七文件 Bundle；当前随包运行时包含 7 个 Project、49 个 Case、81 个 Claim、59 个 Evidence、81 条 Claim–Evidence 关联、11 条 TimelineEvent 和 16 个 QuestionPreset。7 项 `EXCLUDE` 继续保持私有。

当前公开版本已完成 Wave 1 的 Keyword、Vector、Hybrid 三路真实本地模型比较：37 个问题覆盖 Holdout、Regression 和 Calibration；策略 v2.1 的三路 false-sufficient 均为 0，Hybrid 在 26 个正例上取得 Hit@1 0.8846、Hit@5 1.0000、MRR@5 0.9359 和 20/26 正向充分判定。完整过程、v2 被拒原因与边界见 `docs/reports/retrieval-wave-1-policy-v2-1-2026-07-27.md`。运行时 Profile 未改变，检索仍默认关闭，也没有部署。

全量内容还完成了 89 例、267 次路由评估的真实比较：Hybrid 在 38 个正例上取得 Hit@1 0.9211、Hit@5 1.0000、MRR@5 0.9561 和 32/38 正向充分判定，明显高于 Keyword 的 13/38 和 Vector 的 17/38；三路 false-sufficient 仍为 0。该结果证明混合检索对扩充内容有价值，但不代表所有决策用例通过，详见 `docs/reports/retrieval-full-public-assets-candidate-2026-07-27.md`。

## 2. 已实现功能

### 2.1 公开作品集前端

- Vue 3 + TypeScript + Vite 单页应用，正式路由为 `/`、`/projects`、`/projects/:slug`、`/timeline`、`/evidence`、`/agent`，并有显式 404 页面。
- 首页由公开数据驱动，包含作品定位、角色化提问、可信度摘要和探索入口；缺少姓名时不生成虚构占位内容。
- 项目目录、项目详情、时间线和证据中心均从 `GET /api/v1/public-content` 聚合接口读取，同一次加载共享请求，并提供 loading、空状态、失败重试和未知资源状态。
- Agent 三栏工作台包含会话栏、对话区和证据区；桌面端支持可拖动/键盘调整分栏，窄屏改为抽屉且避免水平溢出。
- Agent 工作台采用 A「均衡纸阶」：顶部导航保持深墨色，三栏使用分级米白纸色，中间对话区最亮；仅“新对话”和“发送”保留实心深色主按钮。
- 首页到 Agent 使用随机、短时、一次性内存 `handoffId`；问题和回答不进入 URL 或浏览器历史。
- 会话、消息、失败重试状态和引用上下文只存在当前页面内存；刷新或关闭页面后消失，不使用 localStorage、sessionStorage 或服务端持久化。
- 回答 UI 区分 `ANSWERED / BOUNDARY / REJECTED`、`PRESET / RETRIEVAL / TOOL`、`DETERMINISTIC / MODEL / FALLBACK` 和验证状态，并展示结构化 section、证据引用、建议问题与内容版本变化提示。

### 2.2 公开内容与 API

- Spring Boot 提供 `GET /api/v1/portfolio`、`GET /api/v1/projects/{slug}`、`GET /api/v1/cases`、`GET /api/v1/cases/{slug}`、`GET /api/v1/public-content` 和 `POST /api/v1/answers`。
- 当前随包公开快照为 schema 3.0、内容版本 `2026-07-27.1`，包含 7 个 Project、49 个 Case、81 个 Claim、59 个 Evidence、81 条 Claim–Evidence 关联、11 条 TimelineEvent 和 16 个 QuestionPreset；61/68 项公开资产，7 项 `EXCLUDE` 保持私有。
- 已实现独立不可变 CaseStudy 领域模型、CaseType、CASE Claim 归属与引用校验、只读服务和公开 DTO；未知 Case slug 返回 404 `CASE_NOT_FOUND`。
- 加载器显式接受 schema 2.0/3.0：2.0 被规范化为空 `cases`/`caseIds`，3.0 严格校验 Case 集合与引用，未知版本和缺失必填集合失败关闭。
- `GET /api/v1/public-content` 新增 `cases`、`caseSlugsByEvidenceId`，QuestionPreset 与 Timeline 投影新增 `caseSlugs`。
- `source=CASE` 时 Project/Case 主体互斥，未知主体 fail-closed，Case 不会隐式扩展为相关 Project；共享目录模型和详情投影已实现。
- 公开 DTO 与内部领域对象分离；启动/加载阶段校验 schema、唯一性、交叉引用、Evidence 审批状态、原始内容暴露标志和 Claim 验证约束。
- SPA 正式路由由单 JAR 回退到 Vue 入口，同时不吞掉 API 和静态资源路径；异常响应不暴露堆栈、本地路径或内部错误信息。

### 2.3 回答运行时与可信边界

- 每次回答基于一次不可变内容快照，生成 `AnswerTurnSnapshot` 与匿名 `requestId`；访客问题和回答不写日志、不持久化。
- 支持规范问题 ID、有限精确别名、自由文本边界判断和敏感请求拒绝；当前规范问题返回背景、职责、方案、验证、状态五段式答案。
- API 强制校验项目 slug、问题长度、请求媒体类型、Evidence 范围以及引用上下文；历史问答正文不能进入 `ContextEnvelope`。
- 最终响应同时表达处理结果、事实来源、生成方式和验证状态，避免把“检索到”“模型生成”误标成“已验证”。
- 匿名观测端口和决策模型已实现，但生产适配器当前为 `NoopAnswerDecisionPublisher`，不会记录访客内容。

### 2.4 A/B：运行时可信度与内容治理

- Claim、Evidence、ClaimEvidenceLink、Project、CaseStudy、QuestionPreset、TimelineEvent 和发布清单均有显式不可变领域模型。
- 支持四文件基础 bundle 与七文件 retrieval bundle 的确定性编译、SHA-256 校验、严格加载、active 版本定位和原子发布边界。
- 提供内容治理、检索 bundle 构建、静态 bundle 校验、架构、代码质量、隐私、JAR E2E 和完整 release verification 脚本。
- 内容发布 runbook 覆盖仓库外候选内容、人工 Approval、dry-run、发布、验证和回滚；bootstrap bundle 仅是开发/首装种子，不等价于生产人工审批。

### 2.5 C1：可选模型表达

- 已实现白名单 `AnswerPlan`、Provider HTTP Adapter、Prompt 构建、结构化 Draft 解析、完整 Draft 校验和同一 Plan 的整轮确定性 fallback。
- 支持 DeepSeek V4 Flash 与 GLM-4.7；每个部署只能显式选择一个 Provider，不重试、不跨 Provider 故障转移。
- 外部 Provider 只接收由已批准公开事实构建的 Plan，不接收访客原问题、历史会话、请求标识、检索词项、向量或工具内部数据。
- 默认 `PORTFOLIO_MODEL_ENABLED=false`；还必须同时具备数据策略批准和所选 Provider 密钥才会启用，否则保持确定性回答。

### 2.6 C2a：本地公开检索

- 已实现 BGE-small-zh-v1.5 INT8 ONNX 本地 Embedding 适配器、固定模型描述与哈希验证；模型文件不进入 Git，也不会在应用启动时下载。
- 发布期生成文档向量；运行期访客查询只在本机向量化。检索链路包含 query normalization、BM25、向量召回、RRF 融合和 Grounding Gate。
- `DISABLED` 为默认配置；支持显式 `KEYWORD_ONLY` 诊断模式和 `HYBRID` 模式。Hybrid 查询向量失败时可降级为关键词候选，但仍必须通过 grounding 校验。
- 自由问题只有在 Claim、Evidence、项目范围、阈值和上下文预算全部满足时才返回 `ANSWERED + RETRIEVAL`，否则安全退回 `BOUNDARY`。
- 已实现独立离线比较器和真实模型 CLI，可在同一内容、Policy、模型与 fixture 上输出 Keyword、Vector、Hybrid 的稳定 JSON/Markdown；Wave 0 基线中三路 false-sufficient 均为 0。该离线 Vector 路线不是生产 `VECTOR_ONLY` Profile。

### 2.7 C2b：固定只读工具与引用式多轮

- 已实现 `c2b-tools-v2` 固定策略和七类封闭工具：`GET_PROJECT`、`GET_CASE`、`GET_CLAIMS`、`GET_EVIDENCE_FOR_CLAIMS`、`GET_TIMELINE`、`SEARCH_PUBLIC_CONTENT`、`COMPARE_PROJECTS`。
- 工具只读取同一个公开快照，最多执行 4 次；未知、跨项目、跨版本、未批准、比较样本不足或超预算结果失败关闭。
- 显式追问只传 content version、bundle hash、Project/Claim/Preset/Section 稳定 ID 和封闭 `FollowUpIntent`，不传历史问答正文。
- 每轮重新验证引用；内容版本更新时基于当前版本回答并提示，引用失效时返回边界结果。

### 2.8 C3：仅内置 Model Provider Registry

- 已实现不可变快照 `c3-model-registry-v1`，内建 DeepSeek V4 Flash 和 GLM-4.7 两个经过固定描述的 Provider。
- Registry 校验 Provider、模型策略版本、回答 schema 和能力；启动时创建且运行中不变。
- 不支持 classpath、文件或网络动态发现，不支持热更新、自动故障转移或跨 Provider 重发。

### 2.9 构建、交付与质量保障

- Java 21、Spring Boot、Maven；Vue 3、TypeScript、Vitest、Vue Test Utils、Playwright。
- 前端产物打入单个可执行 JAR；提供 Dockerfile 和 packaged-JAR 浏览器联调。
- 自动化覆盖领域约束、控制器、公开内容加载、确定性回答、模型 fallback、Provider Registry、混合检索、工具、多轮引用、隐私、无障碍交互和响应式布局。
- `scripts/verify-release.ps1` 组合代码质量、架构、隐私、bundle、前后端测试、构建、JAR 静态资源与端到端检查；直接 `mvn package` 不能替代完整发布门禁。
- 发布门禁支持 `-RequireLiveProvider`：缺少审批、显式 Provider 或密钥时失败；普通模式以高优先级关闭模型能力，避免继承本机环境后误触外部调用。成功摘要只包含 Provider、内容版本、resolution 和 block 数量。

### 2.10 历史内容快照：首批公开发布（schema 3.0 / `2026-07-23.1`）

- 在仓库外私有治理区登记 7 条长期主线、19 项任务、25 项事件和 17 项知识资产，共 68 项。
- 每项资产均记录内容类型、完成状态、贡献边界、公开优先级、证据状态和审核状态；不确定贡献或缺少最终验收的内容保持 `HOLD` 或 `EXCLUDE`。
- 在 `2026-07-23.1` 历史快照中，首批 SQL 主线增量和三个 Case 已完成公开文案、Evidence、引用、隐私、精确 diff/hash 与人工 Approval，并进入当时的随包公开 Bundle；当前规模以 `2026-07-27.1` 为准。
- SQL 2026-07 扩展新增负号输入安全、多来源选择、成功结果保留和选中目标检查等保守事实，不覆盖既有公开 Project。
- 三个 Case 分别为多语言图片上传结果保留、测试角色重置工具和 CodeGraph 定性评测；CodeGraph 不公开精确效率指标或内部项目资料。
- 私有治理区与原始知识库仍不由运行时直接读取；后续资产仍需逐批走相同人工审核和发布流程。

### 2.11 全量公开资产发布

- 新增确定性生成器，把私有清单中的 52 项非排除资产转换为 6 条长期主线、46 个独立 Case、52 个 Claim、52 个脱敏 Evidence 摘要、6 条 TimelineEvent 和 1 个共享 QuestionPreset。
- 合并后公开 61/68 项；7 项 `EXCLUDE` 保持私有，原始 Evidence 始终不公开。
- 治理脚本校验 `VERIFIED / PARTIALLY_VERIFIED / OWNER_CONFIRMED / INVESTIGATED / ASSISTED / UNRESOLVED` 到公开验证状态和贡献类型的保守映射。
- 比较 CLI 可直接读取三文件候选，在内存中编译 Keyword/Vector 索引并复核规范 RAG 字节，不需要伪造已发布 Bundle。
- 前端 Case 目录模型已支持“长期主线、单体任务、问题处理、知识与评测”四组；该能力尚未部署。
- 精确候选已通过结构、引用、隐私、冻结基准和人工 Approval，发布到本地发布区并原子导入当前分支随包运行时。

## 3. 部分实现或受运行条件限制

| 能力 | 当前状态 | 还缺什么 |
|---|---|---|
| 公开内容规模 | 当前随包运行时为 `2026-07-27.1`，公开 61/68 项，形成 7 个 Project 和 49 个 Case | 仍未生产部署；7 项排除资产不得进入公开运行时 |
| C1 模型表达 | 代码、双 Provider Adapter、Registry 与 fallback 已实现，默认关闭 | 部署方独立完成数据条款审批、注入密钥并决定是否启用；真实 Provider 可用性属于运行环境状态 |
| 对话式 Agent v2 后端 | `/api/v2/answers`、意图路由、20 轮临时上下文、通用/作品集/混合回答、公开检索、固定工具、事实校验和动态追问已实现，默认关闭；前端已调用该接口 | 生产启用还要求访客数据条款审批、单 Provider 密钥与线上验收 |
| C2a 本地检索 | 全量内容已发布并导入；89 例比较中 Hybrid 为 32/38 正例充分判定，优于 Keyword 13/38 和 Vector 17/38，三路 false-sufficient 为 0 | 生产侧仍需安装固定 revision 的本地 ONNX 模型并显式配置 `HYBRID`，当前 Git 不包含模型二进制 |
| C2b 项目比较 | 工具实现并能读取当前 7 个公开 Project，已具备跨项目比较数据 | 仍需完成 Agent 前端入口、浏览器联调和代表性跨项目问题验收 |
| 内容发布闭环 | CLI、审批契约、发布和回滚工具已实现；`2026-07-23.1` 历史首批三个 Case 及后续 `2026-07-27.1` 全量公开资产均已完成人工批准与本地发布 | 生产部署、线上验收和后续内容批次仍需单独执行与留证 |
| 匿名观测 | 领域事件、耗时桶和 best-effort 发布端口已实现 | 当前生产适配器是 Noop，没有指标后端、告警或运营面板 |
| 角色化体验 | 前端角色选择与 `audienceRole` 请求字段已接入，模型表达有封闭语气策略；当前公开 Bundle 的 16 个 QuestionPreset 均可由 Agent 后端执行 | Case-only preset 的独立 Case 页面入口与完整生产验收仍待完成；角色不会解锁不同事实或未发布问题 |
| 无障碍与视觉收口 | 键盘分栏、抽屉、reduced-motion 用例和主要 loading/error 状态已有覆盖 | 历史设计审核仍记录焦点管理、完整语义和更广 WCAG 人工验收尾项，尚无“全面合规”结论 |

## 4. 尚未实现或未准入

### 4.1 产品与内容

- 独立 `/cases`、`/cases/:slug`、规范重定向、具体 Case UI 与最终视觉设计；故事页作为后续 selected-case 增强。`/api/v2/answers` 已有前端调用，剩余为与 Case 流程的完整联调和生产验收。
- 完整 V1 内容规模、多主题项目库、更多可执行 FAQ 和跨项目真实比较数据。
- 私有 Obsidian/候选材料检索、个人 Copilot、管理后台和未审核内容预览。
- 用户注册、登录、权限、团队协作、收藏、分享链接和跨设备会话。
- 服务端或浏览器持久会话；当前刷新即清空是明确隐私契约，不是遗漏。
- 访客反馈提交 API、反馈数据库、人工标注队列和线上学习流水线。

### 4.2 Agent 与扩展架构

- 动态 Tool Registry、动态插件安装/发现/热更新和第三方工具授权。
- 通用 Hook、Orchestrator、工作流 DSL、DurableTask、任务恢复、调度和队列。
- 多 Agent 协作、委派、共享记忆和 Agent 间通信。
- 自动 Provider 故障转移、负载均衡、多 Provider 并发、自动重试和动态模型发现。
- 长期记忆、向量数据库、知识图谱和对私有知识的 RAG。

### 4.3 平台与运营

- 业务数据库、缓存、消息队列、对象存储、认证授权和租户隔离。
- SSE/WebSocket 流式回答；当前 API 为一次性 JSON 响应。
- 生产级指标存储、Tracing、日志检索、告警、SLO 与运营 Dashboard。
- 自动部署流水线、托管环境、域名/TLS 和正式生产发布证明。
- 当前 schema 3.0 Bundle 尚未生产部署，也没有线上 Case API/页面验收结论。

## 5. 下一步优先级建议

1. **实现独立 Case 信息架构。** 在共享目录模型与详情投影之上实现 `/cases`、`/cases/:slug`、规范重定向、数据映射、错误状态与具体 UI；复用 Dossier 能力，故事页留给后续 selected-case 增强。
2. **完成 Agent Case 流程验收。** 基于现有 `/api/v2/answers` 调用，按 `caseSlug`/`caseSlugs` 契约接入 Case 预设与追问动作，并完成浏览器隐私、刷新清空、错误边界与生产验收。
3. **复验全量内容的前端与 Agent 行为。** 补齐 Case 页面和 Agent 入口，在浏览器中验证 7 个 Project、49 个 Case、跨项目比较、错误状态与引用边界。
4. **完成生产候选与线上验收。** 前端完整后，从仓库外安全注入审批与密钥，执行一次 `-RequireLiveProvider` 并留存安全摘要；随后完成 Docker/部署、API、页面、隐私和回滚证据。
5. **做可访问性人工验收。** 集中关闭焦点、语义、对比度、读屏和 reduced-motion 尾项，再声明可访问性等级。
6. **暂不扩 C3。** 只有出现至少两个真实实现、重复扩展代码、稳定契约和运行证据后，再单独 ADR 评估 Registry/Hook/Orchestrator 等抽象。

本次后端闭环不实现 Vue 页面、路由、组件、CSS 或最终视觉设计；前端 AI 应以 [`2026-07-28-portfolio-v1-case-and-release-closure-design.md`](superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md) 为交接规范。

## 6. 状态判定依据

- “已实现”要求生产代码存在，且至少有自动化测试、发布脚本或随包制品中的一种可验证证据。
- “部分实现”表示核心代码存在，但默认关闭、依赖仓库外制品/审批，或当前数据规模无法触发完整价值。
- “未实现”表示只有路线图/设计描述、只有领域占位类型，或当前没有可调用的生产链路。
- 历史实施计划中的未勾选框不自动构成当前待办；以本文、`00-文档状态索引.md`、当前生产代码和最新验证结果为准。
