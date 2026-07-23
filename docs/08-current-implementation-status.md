# 当前实现与未实现功能盘点

> **状态：** 当前实现盘点（以生产代码、配置、自动化测试和发布脚本为证据）
> **核对日期：** 2026-07-23
> **代码基线：** `c5cde9b`
> **维护规则：** 功能合入、默认开关或产品边界变化时，同步更新本文与 `00-文档状态索引.md`。

## 1. 结论

当前项目已经从“一个确定性问答的 V0”扩展为一个可打包交付的公开实习作品集 Agent：前端具备六个正式路由和页面内存工作台，后端具备公开内容 API、严格回答契约、内容治理、可选模型表达、本地混合检索、固定只读工具和引用式多轮，并提供单 JAR、Docker 与完整发布门禁。

但它仍不是完整 V1。当前公开内容只有一个已交付项目、一个可执行问题、一个 Evidence 和一条 TimelineEvent；模型表达与本地检索默认关闭；真实生产内容尚需在仓库外完成一次人工 Approval；动态工具/插件、编排、多 Agent、持久会话、数据库、认证和私有 Copilot 均未实现。

内容准备层已经登记 68 项私有候选，并为 SQL 主线扩充、多语言图片修复、角色重置工具和 CodeGraph 评测形成首批四项脱敏候选包。这里的“候选已准备”不等于“公开内容已扩充”：当前公开 schema、bundle、API、Agent 回答事实和前端页面均未变化。

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

- Spring Boot 提供 `GET /api/v1/portfolio`、`GET /api/v1/projects/{slug}`、`GET /api/v1/public-content` 和 `POST /api/v1/answers`。
- 当前随包公开快照为 schema 2.0、内容版本 `2026-07-21.1`，包含 1 个 `DELIVERED + PRIMARY` SQL 审计项目、5 个 Claim、1 个 APPROVED Evidence、5 条 Claim-Evidence 关联、1 条 TimelineEvent 和 1 个 QuestionPreset。
- 公开 DTO 与内部领域对象分离；启动/加载阶段校验 schema、唯一性、交叉引用、Evidence 审批状态、原始内容暴露标志和 Claim 验证约束。
- SPA 正式路由由单 JAR 回退到 Vue 入口，同时不吞掉 API 和静态资源路径；异常响应不暴露堆栈、本地路径或内部错误信息。

### 2.3 回答运行时与可信边界

- 每次回答基于一次不可变内容快照，生成 `AnswerTurnSnapshot` 与匿名 `requestId`；访客问题和回答不写日志、不持久化。
- 支持规范问题 ID、有限精确别名、自由文本边界判断和敏感请求拒绝；当前规范问题返回背景、职责、方案、验证、状态五段式答案。
- API 强制校验项目 slug、问题长度、请求媒体类型、Evidence 范围以及引用上下文；历史问答正文不能进入 `ContextEnvelope`。
- 最终响应同时表达处理结果、事实来源、生成方式和验证状态，避免把“检索到”“模型生成”误标成“已验证”。
- 匿名观测端口和决策模型已实现，但生产适配器当前为 `NoopAnswerDecisionPublisher`，不会记录访客内容。

### 2.4 A/B：运行时可信度与内容治理

- Claim、Evidence、ClaimEvidenceLink、Project、QuestionPreset、TimelineEvent 和发布清单均有显式不可变领域模型。
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

### 2.7 C2b：固定只读工具与引用式多轮

- 已实现 `c2b-tools-v1` 固定策略和六类封闭工具：`GET_PROJECT`、`GET_CLAIMS`、`GET_EVIDENCE_FOR_CLAIMS`、`GET_TIMELINE`、`SEARCH_PUBLIC_CONTENT`、`COMPARE_PROJECTS`。
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

### 2.10 内容资产准备

- 在仓库外私有治理区登记 7 条长期主线、19 项任务、25 项事件和 17 项知识资产，共 68 项。
- 每项资产均记录内容类型、完成状态、贡献边界、公开优先级、证据状态和审核状态；不确定贡献或缺少最终验收的内容保持 `HOLD` 或 `EXCLUDE`。
- 首批准备 SQL 主线增量候选，以及多语言图片修复、角色重置工具和 CodeGraph 评测三个 Case 候选。
- SQL 增量候选只新增输入安全、多来源选择、成功结果保留和选中目标检查等保守事实，不覆盖既有公开 Project。
- 三个 Case 候选均标记为 `AWAITING_CASESTUDY_CONTRACT`，当前 schema 2.0 不读取这些数据，也没有伪造为 Project。
- 私有候选已通过结构、敏感信息和跨文件一致性检查；检查通过不能代替人工公开文案审核或 Bundle Approval。

## 3. 部分实现或受运行条件限制

| 能力 | 当前状态 | 还缺什么 |
|---|---|---|
| 公开内容规模 | 已完成 68 项私有登记和首批四项候选；随包数据仍只有 1 个项目、1 个问题、1 个 Evidence、1 条时间线 | 先完成 CaseStudy 公共契约和公开文案审核，再按 runbook 审批并发布新 bundle |
| C1 模型表达 | 代码、双 Provider Adapter、Registry 与 fallback 已实现，默认关闭 | 部署方独立完成数据条款审批、注入密钥并决定是否启用；真实 Provider 可用性属于运行环境状态 |
| C2a 本地检索 | 检索、索引、模型校验与 benchmark 已实现，默认关闭 | 运维侧安装固定 revision 的本地 ONNX 模型并显式配置 `HYBRID`；当前 Git 不包含模型二进制 |
| C2b 项目比较 | 工具实现并能在多项目时比较 | 当前只有 1 个公开项目，因此真实比较请求会安全返回信息不足 |
| 内容发布闭环 | CLI、审批契约、发布和回滚工具已实现 | bootstrap 数据不是生产人工 Approval；真实发布需明确的人类审核者在外部私有工作区完成 |
| 匿名观测 | 领域事件、耗时桶和 best-effort 发布端口已实现 | 当前生产适配器是 Noop，没有指标后端、告警或运营面板 |
| 角色化体验 | 前端角色选择与 `audienceRole` 请求字段已接入，模型表达有封闭语气策略 | 公开问题仍只有一个；角色不会解锁不同事实或未发布问题 |
| 无障碍与视觉收口 | 键盘分栏、抽屉、reduced-motion 用例和主要 loading/error 状态已有覆盖 | 历史设计审核仍记录焦点管理、完整语义和更广 WCAG 人工验收尾项，尚无“全面合规”结论 |

## 4. 尚未实现或未准入

### 4.1 产品与内容

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

## 5. 下一步优先级建议

1. **先扩真实公开内容。** 在不扩大隐私边界的前提下新增第二个已交付项目、更多 APPROVED Evidence 和可执行 QuestionPreset，才能真正验证动态页面、检索与比较工具的价值。
2. **完成一次真实发布演练。** 在仓库外走完人工 Approval、七文件 bundle、发布、验证和回滚，补齐 bootstrap 与生产流程之间的证据缺口。
3. **做可访问性人工验收。** 集中关闭焦点、语义、对比度、读屏和 reduced-motion 尾项，再声明可访问性等级。
4. **按部署需求启用可选能力。** 先决定是否允许 C1 外部模型和 C2a 本地模型，再分别准备数据策略审批、密钥或固定 ONNX 制品；默认关闭是安全基线。
5. **暂不扩 C3。** 只有出现至少两个真实实现、重复扩展代码、稳定契约和运行证据后，再单独 ADR 评估 Registry/Hook/Orchestrator 等抽象。

## 6. 状态判定依据

- “已实现”要求生产代码存在，且至少有自动化测试、发布脚本或随包制品中的一种可验证证据。
- “部分实现”表示核心代码存在，但默认关闭、依赖仓库外制品/审批，或当前数据规模无法触发完整价值。
- “未实现”表示只有路线图/设计描述、只有领域占位类型，或当前没有可调用的生产链路。
- 历史实施计划中的未勾选框不自动构成当前待办；以本文、`00-文档状态索引.md`、当前生产代码和最新验证结果为准。
