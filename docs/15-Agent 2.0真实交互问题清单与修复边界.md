# Agent 2.0 动态缺陷清单与修复边界
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

> **记录日期：** 2026-08-19
> **适用版本：** Agent 2.0（完成 Agent 架构收敛 Slice 0—6 后的本地版本）
> **验证环境：** 最终 packaged JAR、Frontend closed PublicAgentTurn 消费链、`IN_MEMORY`/PostgreSQL 会话状态、本机 Chromium 与确定性 Provider fixture
> **文档性质：** Agent 2.0 真实交互验证账本；当前未关闭项以问题总览状态为准
> **维护原则：** 发现并确认 Bug 后添加；完成修复与对应 Exit Gate 后删除；已解决历史转记演进日志，不在本文累积
> **当前状态：** 2026-08-24 配置化 GLM/Qwen 模型目录、Turn 显式选择、五种 settled 模型错误与 `modelExecution` 已完成后端离线实现和 Maven 全量验证；旧 global/visitor Provider 门、DeepSeek 与单 Provider 权威已退出生产路径。真实 GLM canary 已到达智谱但被 HTTP 429 / 业务码 1305 平台过载拒绝，尚无成功终局；Qwen 调用配置等待用户提供。本轮不包含前端，整体保持 IN_PROGRESS

## 1. 文档目的

Agent 2.0 已完成 Command、Goal、Plan、Execution、Projection、State、无版本 API 与 Frontend 的整体替换，并通过确定性测试、Testcontainers、packaged-JAR Browser E2E 和单轮真实 Provider canary。但在真实浏览器连续操作中，仍暴露出自动化测试没有覆盖的跨轮状态与超时问题。

本文用于：

1. 统一记录已经实际观察到的用户现象；
2. 区分已复现事实、源码确认根因与仍待验证的推断；
3. 明确修复必须遵守的 Agent 2.0 冻结边界，避免重新引入旧 Router、兼容协议或第二套状态权威；
4. 给后续设计、实施和回归测试提供一份稳定问题清单；
5. 防止修复单个 UI 症状后遗漏同一状态链上的后端语义、取消、幂等与隐私问题。

### 1.1 动态维护规则

本文只描述 Agent 2.0 **当前仍然存在**的 Bug，不保存已关闭事项。

#### 添加 Bug

满足以下条件时，应在处理代码前或完成第一轮诊断后及时更新本文：

1. 用户在真实页面、API、packaged JAR 或真实 Provider 链路中观察到异常；
2. 自动化测试发现生产行为、公开合同、会话状态、安全边界或恢复语义存在缺陷；
3. 问题已经具备最小复现证据，或源码调用链能够确认风险；
4. 问题属于 Agent 2.0 当前生产链，而不是尚未批准的新功能诉求。

新增问题应包含：

- 唯一 ID、严重度、简短标题、证据等级和责任区；
- 用户可见现象或稳定复现方式；
- 已确认事实与待验证推断，二者不得混写；
- 根因、影响范围、修复边界和需要补充的测试；
- 不记录访客原文、模型原始输出、Prompt、Token、凭据或内部敏感数据。

ID 按 `A2-NN` 单调递增。删除已解决问题后允许出现编号空洞，不重排仍开放问题，也不复用旧 ID。

#### 更新 Bug

后续截图、日志、测试或源码分析改变原判断时，应直接修订对应问题，而不是在文末追加互相矛盾的阶段结论。证据不足时标为“待验证”；证据坐实后更新为“已复现”或“源码确认”。

相关症状共享同一根因时，可以合并问题，但必须保留所有用户可见影响与验收场景；不同权威或不同修复边界的问题不得仅因同时出现而强行合并。

#### 删除已解决 Bug

只有同时满足以下条件，才从本文删除该 Bug 的总览行、正文、专属测试缺口和专属 Exit Gate：

1. 生产代码修复已经完成；
2. 针对性单元/集成测试通过；
3. 受影响责任区的全量测试和构建通过；
4. 涉及跨端、浏览器、数据库或 Provider 时，对应 packaged-JAR/Testcontainers/真实 Provider 门已按风险执行；
5. 原始用户操作路径已经重新验证，现象不可复现；
6. 没有通过恢复旧协议、兼容桥、吞错或放宽安全校验掩盖问题。

Bug 删除后不在本文保留“已完成”“已修复”章节。重要行为修复按 `AGENTS.md` 写入 `docs/11-项目演进日志.md`；具体实现和验证证据由 Git 提交、测试及必要的专项报告承载。

如果所有 Bug 均已关闭，保留本文标题、定位、维护规则和一条“当前无未关闭 Agent 2.0 缺陷”，其余问题正文与临时 Exit Gate 删除。

本文不替代：

- `docs/08-当前实现状态.md` 的项目现状描述；
- `docs/13-Agent对话体验与智能编排改造路线图.md` 的历史产品背景（已被当前 Agent 2.0 设计取代，不是待办权威）；
- `docs/14-Agent架构债与防御性设计评审.md` 的历史治理观察（已被当前收敛设计取代，不是实施依据）；
- `docs/superpowers/specs/2026-08-17-agent-architecture-convergence-design.md` 的冻结架构决策；
- 针对本问题另行形成的正式设计与实施计划。

## 2. 证据等级

- **已复现：** 浏览器截图、当前 packaged JAR 安全诊断或稳定操作步骤可以直接证明；
- **源码确认：** 当前调用链能够直接解释现象，不依赖猜测；
- **待验证：** 已有合理机制解释，但仍需针对性测试或运行时观测确认；
- **修复后待验收：** 问题已进入修复范围，但必须通过本文 Exit Gate 才能关闭。

所有日志证据只使用状态码、耗时、operation、稳定错误码和安全枚举，不记录访客原文、模型原始输出、Prompt、ResumeToken 或凭据。

## 3. 问题总览

| ID | 严重度 | 问题 | 当前证据 | 主要责任区 |
|---|---|---|---|---|
| A2-01 | P1 | 明确的项目推荐请求被错误转成主体澄清 | 已关闭（默认数量与确定性边界验收通过） | Goal Interpretation / Goal Policy |
| A2-02 | P0 | 澄清答案消费后丢失原始 Goal 与推荐约束 | 已关闭（两层澄清与 PostgreSQL 恢复通过） | Lifecycle / Clarification State |
| A2-03 | P0 | 前端未把澄清答案记为 USER 轮次，破坏 conversationWindow 交替 | 已关闭 | Frontend Session / Wire Window |
| A2-04 | P1 | 失败请求留下本地 USER 消息，后续窗口持续污染 | 已关闭 | Frontend Turn Lifecycle |
| A2-05 | P1 | 400 合同校验错误被显示成笼统的 Agent 不可用 | 已关闭 | API Error Projection |
| A2-06 | P2 | 澄清/失败期间来源栏仍标记为“当前回答来源” | 已关闭 | Frontend Source Context |
| A2-07 | P0 | 新建或切换会话后旧 failure 继续显示 | 已关闭 | Frontend Session State |
| A2-08 | P0 | pending、retry 等操作可能跨会话错位 | 已关闭 | Frontend Session State |
| A2-09 | P1 | draft、clearNotice、resumeNotice 可能跨会话滞留 | 已关闭 | Frontend Session State |
| A2-10 | P0 | 前端内部超时被当成主动取消，处理中状态直接消失 | 已关闭 | Frontend Transport |
| A2-11 | P0 | 前端超时只 abort fetch，不取消后端 Active Turn | 已关闭（冻结为同 requestId replay，不取消服务端） | Frontend/API Lifecycle |
| A2-12 | P0 | Provider/Goal/Turn deadline 未形成真正的端到端绝对超时 | 已关闭（body-stall 与 active cancel 通过） | Backend Model Transport / Lifecycle |
| A2-13 | P1 | 前后端超时预算相互冲突 | 已关闭（20/25/30/35 秒时间轴冻结） | Cross-end Contract |
| A2-14 | P1 | 后端最终完成的幂等结果无法由前端自动取回 | 已关闭（显式同 requestId 重试取回终局） | Replay / Frontend Recovery |
| A2-15 | P1 | 重新打开后“恢复正常”实际是状态重置加一次新的快速 Provider 调用 | 修复后待真实 Provider LIVE 验收 | Frontend Lifecycle / Provider Variance |
| A2-16 | P1 | 简单问候“你好”被错误升级为必填澄清 | 已关闭 | Goal Interpretation |
| A2-17 | P0 | 澄清可以连续生成新的 Critical Clarification，缺少级联终止规则 | 已关闭（最多两层 typed clarification） | Goal/Lifecycle Policy |
| A2-18 | P0 | 已提交、已一次性消费的历史澄清卡仍可编辑和重复提交 | 已关闭 | Frontend Clarification State |
| A2-20 | P1 | 通用知识生成文案在中文站点发生语言漂移 | 修复后真实 Provider 自动门通过；待浏览器语义验收 | Goal Interpretation / General Knowledge Prompt |
| A2-21 | P1 | EXPLANATION depth 未形成可执行的结构与篇幅差异 | 修复后真实 Provider 自动门通过；待浏览器语义验收 | Goal Interpretation / General Knowledge / Presentation |
| A2-22 | P1 | 同 requestId 重试未冻结完整提交身份 | 源码确认、批准实施 | Frontend Retry / Idempotency |
| A2-23 | P1 | Clarification 消费早于 terminal settlement | 源码确认、批准实施 | Clarification State / Settlement |
| A2-24 | P1 | 单候选 NEEDS_CLARIFICATION 被后端强制进入讨论 | 源码确认、批准实施 | Semantic Routing / Lifecycle |
| A2-25 | P2 | PostgreSQL Session replacement 残留 expired discussion pointer | 源码确认、批准实施 | PostgreSQL Session State |
| A2-26 | P1 | ENTER discussion TTL 被来源 Recommendation 过期时间裁剪 | 源码确认、批准实施 | Discussion Lifecycle |
| A2-27 | P2 | Pending 清理缺少 requestId generation guard | 源码确认、批准实施 | Frontend Turn Lifecycle |
| A2-28 | P1 | Discussion 权威投影、revision、TTL 与恢复动作未闭合 | 源码确认、批准实施 | Public Contract / Frontend State |
| A2-29 | P1 | Provider、Browser、共享合同与隐私门覆盖不足 | 源码确认、批准实施 | Release Verification |

### 3.1 A2-22—A2-29 修复边界

本批次以 [失败恢复与项目讨论补完设计](superpowers/specs/2026-08-21-agent-failure-recovery-and-discussion-completion-design.md) 为唯一实施依据：前端重试必须原样复用内存态提交快照；Clarification 使用 V5 reservation 并在 terminal transaction 消费；Project Discussion 修复单候选、TTL 和 Session replacement parity；V6 提升现有 Session revision 并让成功 Turn 返回当前权威 discussion summary。不得用前端推测状态、模型重试、兼容旧合同或持久化原始输入规避问题。

只有 A2-22—A2-29 的针对性、全量、PostgreSQL、packaged Browser、隐私与获授权 Provider 门全部通过后，才可删除对应条目并恢复架构 COMPLETE。

### 3.2 A2-30—A2-115 全仓库审计待做项

下表是 2026-08-21 对生产 Java、Frontend、共享合同、State Migration、PowerShell 门禁和当前文档进行只读审计后确认的待做队列。`现状` 与 `预期` 是关闭该项必须跨越的最小差距；表中不记录访客原文、模型正文、Prompt、Token 或 handle。

| ID | 严重度 | 待做项目 | 现状 | 预期 | 主要责任区 |
|---|---|---|---|---|---|
| A2-30 | P0 | PublicTurn replay 含访客派生文本 | 已固定 Goal label 并移除原文 retry action；Backend/PostgreSQL sentinel 门通过，待最终总门 | PostgreSQL replay 不含访客问题或其片段 | Projection / State / Privacy |
| A2-31 | P0 | Provider 文本缺少持久化安全证明 | General 与非快速路径 Conversational 正文已改为 live-only；固定复述 fixture 与完整 settlement 门通过，待最终总门 | 完整 settlement sentinel 门证明只有安全 typed 或公开文本可持久化 | Model Output / Replay / Privacy |
| A2-32 | P0 | 精确 replay 与禁止保存原文冲突 | 已冻结 Portfolio 精确 replay 与 Provider 正文固定终局；Memory/PostgreSQL 通过，待最终总门 | 冻结安全 replay 语义并明确不可重放正文的终局 | Lifecycle / Public Contract / State |
| A2-33 | P0 | Operation Provider 声明不控制实际调用 | 旧 global Provider、visitor access 与 Operation `provider-ref` 已删除；当前 Turn 的显式 ModelSelection 是唯一接收方选择，Catalog/Provider policy/Operation 三层准入后冻结执行快照，待真实 GLM/Qwen 总门 | 声明选择与真实数据接收方一致，且无第二 Provider 权威 | Model Catalog / Provider Authority |
| A2-34 | P0 | Operation schemaVersion 不控制 Codec | `goal.proposal.v5`/`general.draft.v2` 继续由独立 Operation 门与唯一生产 Codec 做启动期精确校验；不再与 Provider 选择混合，待总门 | 配置版本必须与唯一生产 Codec 精确一致 | Model Operation / Codec |
| A2-35 | P1 | Agent availability 可能误报 | `/api/portfolio` 已只消费冻结的 Catalog 与 Operation readiness，安全投影目录版本、默认选择和可选条目；离线矩阵通过，待真实 Provider 与前端消费总门 | 只投影经过三层准入的安全目录与 Operation readiness | Portfolio API / Model Catalog |
| A2-36 | P0 | Privacy 架构账本状态失真 | 机器账本已改为只凭新鲜 complete-settlement 证据记 PASS，checker 负例已补；整体仍 IN_PROGRESS | 原始路径和完整 settlement 隐私门通过后才恢复 PASS | Architecture Status / Governance |
| A2-37 | P1 | Portfolio 表达端口零实现 | 零实现端口、编译器、可选构造分支与幽灵预算已物理删除；待最终清理总门 | 明确实现受约束表达器或删除幽灵能力 | Portfolio Presentation / Model |
| A2-38 | P1 | Portfolio 回答只是 Claim 列表 | 同一 Answer section 的事实已合成为服务端叙述块并聚合全部公开来源；depth/intent 确定性门通过，待 Browser 正文门 | section 类型匹配 AnswerIntent、每段有来源且满足闭合 depth 区块门 | Portfolio Presentation |
| A2-39 | P1 | Portfolio Fact 缺少 depth | typed Goal、澄清恢复与 cross-domain 子任务已携带 depth；deterministic/package 门通过，待真实 Provider/Browser | Portfolio Goal 携带并消费闭合 depth | Goal / Portfolio Capability |
| A2-40 | P1 | Portfolio depth 可能成为装饰字段 | depth 已控制检索 profile/候选上限、coverage、区块数与详细内容；待真实 Provider/Browser 差异矩阵 | depth 同时控制检索、覆盖、区块和完成判定 | Goal / Retrieval / Presentation |
| A2-41 | P1 | requestedOutputs 与 facets 双权威 | typed parameters 已成为下游权威，outputs 只作精确一致性校验并由参数重新派生；待真实 Provider 合同门 | 保留一个 AnswerIntent 权威，其他值由后端派生 | Goal Contract / Validator |
| A2-43 | P1 | Recommendation constraints 不参与选择 | 当前公开目录生成闭合约束目录，Codec 拒绝目录外值；Bundle/PostgreSQL 候选元数据进入 typed 执行链并参与排序，待真实 Provider/Browser 总门 | 闭合约束真实影响筛选、排序和覆盖 | Recommendation / Retrieval |
| A2-44 | P1 | 未满足推荐约束不报告 | 后端按每个已选主体计算约束缺口，任一缺口即 `PARTIAL` 并公开 `unsatisfiedConstraints`；前端展示待 Frontend Agent 接入，整体仍 IN_PROGRESS | 无法满足时返回 PARTIAL 和明确缺口 | Recommendation / Projection |
| A2-45 | P1 | 推荐项缺少可验证理由 | 每项携带闭合 reason code，投影为固定公开中文说明并绑定公开 source key；待真实 Provider/Browser 总门 | 每项返回闭合 reason code、公开说明和 publicSourceKeys | Recommendation / Presentation |
| A2-46 | P1 | 推荐对目标不敏感 | requestedSize、career track 与 capability 进入 Invocation；先按约束匹配数、再按证据类别数、最后按稳定 ID 排序，PostgreSQL 不足时扩大召回后由语义层统一判定；待真实 Provider 总门 | 固定输入矩阵按 typed 目标产生可断言的候选或排序差异 | Recommendation / Ranking |
| A2-47 | P1 | Portfolio Comparison 未形成比较 | 后端按请求 dimension 生成对齐 section，每节按主体聚合 Claim 与来源；待 Browser 正文与真实 Provider 总门 | 按 dimension 对齐差异、取舍和缺口 | Comparison / Presentation |
| A2-48 | P1 | 未知 comparison dimension 被当成验证 | Codec 与 Invocation 两层已拒绝未知值；待真实 Provider/Browser 比较门 | 未知值必须拒绝或澄清 | Comparison / Validator |
| A2-49 | P1 | Portfolio dimension 不是闭合集合 | Goal 已改为后端枚举，检索/coverage 只接收五种维度；待真实 Provider/Browser | 使用后端枚举并逐项验证 | Goal Contract / Comparison |
| A2-50 | P1 | Cross-domain 只是三段拼接 | 关系段已由 General mechanism 与闭合 Claim category 逐项映射，General caveat 公开为适用边界；待真实 Provider/Browser 语义门 | 真实解释概念与项目事实的对应关系 | Synthesis / Presentation |
| A2-51 | P1 | Cross-domain depth 固定 STANDARD | depth 已同时传播到 General 与 Portfolio supporting task；待真实 Provider/Browser 综合门 | depth 贯穿 General、Portfolio 和综合结果 | Planning / Synthesis |
| A2-52 | P1 | 证据不足时详细回答仍可能显得完整 | 详细 overview 缺任一闭合 profile 即 PARTIAL；待真实 Provider/Browser 缺口文案门 | depth 不达标时返回 PARTIAL 和安全缺口 | Coverage / Presentation |
| A2-53 | P1 | AudienceRole 不影响回答 | 闭合 Audience 已进入所有 task：General Provider 接收角色，Portfolio 在同一证据范围内按角色调整 facet 优先级；待真实 Provider/Browser typed 差异矩阵 | 闭合 role-to-output 策略被生产消费，并由 typed 差异矩阵断言 | Surface Context / Goal / Presentation |
| A2-54 | P1 | Page subjectHint 不参与模型理解 | subjectHint 经公开目录解析为 `defaultSubject` 并进入 Goal Interpretation；无效 hint 仍失败关闭，待真实 Provider/Browser | 当前页面主体成为可信默认或锁定主体 | Surface Context / Goal Interpretation |
| A2-55 | P1 | 页面省略表达不能稳定绑定主体 | STANDARD 单主体 Portfolio/跨域目标省略 subject 时由后端绑定 `SURFACE_HINT`；显式其他公开主体可覆盖默认，待真实 Browser 省略表达门 | 页面内指代直接绑定 typed subject | Goal Resolution / Frontend Handoff |
| A2-56 | P1 | 多轮只携带薄 Assistant 摘要 | V7 已原子保存上一成功 Portfolio Goal 的短期、无正文 typed summary；PostgreSQL AES-GCM 与重启回读门通过，待真实 Provider/Browser | 保存短期、脱敏的 typed turn summary | Conversation Window / Typed State |
| A2-57 | P1 | 无法可靠理解“进一步展开” | Goal v5 要求 Provider 显式返回匹配 `recentReference.goalId`，后端才注入 `RECENT_TURN`；facet/depth 已进入可信状态，待真实 Provider | 上一成功 Goal 的安全语义可被引用 | Multi-turn Goal State |
| A2-58 | P1 | 无法引用上一回答区块 | V7 保存公开 `sectionId/sectionKind`，Goal v5 的 section 引用必须精确属于所引用 Goal；待 Browser 正文门 | 后续可引用公开回答的 typed section | Public Presentation / Multi-turn |
| A2-59 | P1 | 旧 Recommendation hint 长时间滞留 | 后续不相关话题仍可能附带旧 Context | 新话题或非推荐结果后停止附带 | Frontend Context Routing |
| A2-60 | P1 | Discussion NEEDS_CLARIFICATION 未履约 | 后端已生成短期加密 typed challenge，并以 pointer generation guard 原子结算；待真实 Provider/Browser 原路径 | 产生限定澄清且 pointer 不变 | Discussion / Clarification |
| A2-61 | P1 | Discussion 澄清缺少闭合选择 | active discussion 已固定为五种合法 facet，expired discussion 只允许重进；choice 与恢复模板精确闭合，待 Browser | 后端提供合法 facet、输出或候选选择 | Discussion / Public Contract |
| A2-62 | P2 | 开放社交回复约束不足 | Provider-derived Conversational 已强制 160 字上限、中文主体、英文整句/控制字符与较长访客原文连续复述拒绝；待真实 Provider 固定样本 | 语言、长度和复述风险受运行时校验 | Conversational / Validation |
| A2-63 | P1 | General depth 只依赖 Prompt | 生产 Validator 已按 depth 强制每条 statement 的句数；待真实 Provider/Browser 质量门 | 每档句数成为运行时不变量 | General Validator |
| A2-64 | P1 | General 简体中文不受运行时保证 | 生产 Validator 已要求每句含中文字符并拒绝完整英文句；待真实 Provider/Browser | 非技术标识的完整英文句被拒绝 | General Validator / Language |
| A2-65 | P1 | DETAILED 不保证语义完整 | `general.draft.v2` 已要求闭合 aspect 精确覆盖；aspect 仍是 Provider 自报，待真实正文抽样证明 | DETAILED 覆盖批准语义维度 | General Quality |
| A2-66 | P1 | General Comparison 接受额外 pair | 生产 Validator 已要求实际 pair 与请求笛卡尔积精确相等且无重复；待真实 Provider/Browser | 实际 pair 与请求完全相等且无重复 | General Comparison Validator |
| A2-67 | P2 | General caveat 校验过弱 | caveat 已改为闭合 kind + text，并校验中文、句数、长度、kind/text 去重；相关性仍待真实正文抽样 | 校验语言、重复、相关性和边界 | General Validator |
| A2-68 | P1 | 线上质量与验收门倒挂 | 句数、语言、aspect、exact pair 与 caveat 核心门已进入生产 Validator；真实 Provider/Browser 总门未通过 | 核心质量门同步进入生产校验 | Runtime / Provider Gate |
| A2-69 | P1 | CLARIFICATION_IN_PROGRESS 污染窗口 | USER 被排除而 Assistant busy 保留 | 临时终局不破坏 USER/ASSISTANT 交替 | Frontend Conversation Window |
| A2-70 | P1 | 澄清取消后卡片可能永久只读 | 服务端可释放 reservation，UI 不恢复 | 可恢复失败和取消后卡片可再次提交 | Frontend Clarification State |
| A2-71 | P1 | 首页 Round 不是真多轮 | 每轮新 Conversation、空窗口、无 Token | 改为单轮预览或真正复用会话 | Homepage Dialogue / Session |
| A2-72 | P1 | 首页失败重试不幂等 | 重试生成新 requestId | 原提交快照和 requestId 原样复用 | Homepage Retry / Idempotency |
| A2-73 | P1 | Preset 重试退化为 FREE_TEXT | 失败后只保留展示文本 | 保持 presetId、revision、surface 和 requestId | Homepage Preset Retry |
| A2-74 | P2 | UUID fallback 不符合后端合同 | 缺 randomUUID 时生成非 UUID | 使用合法 UUID fallback 或明确不支持 | Frontend Request Identity |
| A2-75 | P2 | 删除非活跃会话不清服务端 | 本地删除后服务端状态保留到 TTL | 删除任意会话都 best-effort clear | Frontend / State Cleanup |
| A2-76 | P2 | Discussion 到期后动作不自动更新 | 只改倒计时文案，不取得 EXPIRED summary | 到期时冷恢复权威动作 | Frontend Discussion Recovery |
| A2-77 | P2 | 过期恢复依赖页面 reload | E2E 通过重载才显示恢复按钮 | 不刷新也能进入合法恢复路径 | Browser UX / Conversation GET |
| A2-78 | P1 | HTTP 200 被误当成功回答 | CAPABILITY_UNAVAILABLE 也被传输层视为成功 | Happy path 明确要求预期 PublicTurn kind | Frontend / Browser Assertions |
| A2-79 | P1 | Provider 共用固定请求格式 | GLM/Qwen 已分别使用闭合 `ZHIPU_CHAT_COMPLETIONS` / `DASHSCOPE_CHAT_COMPLETIONS` Profile，并离线断言各自完整请求字段；待两家真实 schema canary | 每个 Provider 使用独立协议 Profile | Model Transport |
| A2-80 | P1 | Provider 兼容停留在配置声明 | 配置化目录、两种 Binding/Profile 与显式 modelRef 路由已完成离线门；GLM-4.7-Flash 真实 canary 已到达智谱并得到 HTTP 429 / 业务码 1305，证明路由可达但没有成功 schema/semantic 样本；Qwen3.7-Flash 配置待用户提供 | 每个 Provider 有真实 schema 与语义 canary | Provider Verification |
| A2-81 | P1 | Operation timeout 缺少新目录真实基线 | GLM canary 一次快速返回 1305、后续应用调用贴近 8 秒 Goal deadline 结算为暂时不可用，仍无成功调用 P50/P95；Qwen3.7-Flash 未配置 | 基于两家真实成功调用 P95 冻结跨端预算 | Timeout Policy / Provider |
| A2-82 | P2 | Provider HTTP 错误分类太粗 | 402 已独立为 `TRANSPORT/BILLING_REJECTED`，401/403、429、5xx 与其他 4xx 保持分层；Provider body 不进入诊断 | 分开统计稳定失败类别 | Transport Diagnostics |
| A2-83 | P2 | JSON/schema 失败分类不准 | Transport/JSON/envelope/operation schema/typed semantic 已使用 closed layer/code 分层；sentinel 安全门通过，待真实 Provider 分布门 | Transport、JSON、schema、semantic 分层 | Model Diagnostics |
| A2-84 | P2 | Provider response 无硬字节上限 | 自定义 BodySubscriber 已在读取期强制 256 KiB 上限且保留 absolute deadline；待真实 Provider 总门 | 客户端限制响应体字节数 | Model Transport / Resource Bound |
| A2-85 | P1 | 无同 Provider schema repair 决策 | 已冻结为 schema/semantic 拒绝即本轮失败，不 repair、不重试；显式选择下的单调用门通过 | 明确保持禁止；未来改变需独立隐私与质量审批 | Provider Reliability / Product Decision |
| A2-86 | P1 | 跨 Provider fallback 边界未产品化 | 每个 Turn 已冻结一个显式 ModelSelection；任一失败均不自动跨 Provider 重发，用户换模型必须使用新 requestId；前端交互不在本轮 | 用户明确切换只能创建新 Turn；不得自动 fallback | Provider Selection / Privacy |
| A2-87 | P1 | Provider 矩阵不独立 | runner 与配置面已迁移为 GLM/Qwen 独立选择和报告；两家新目标的真实同构矩阵均未执行 | 每个批准 Provider 独立执行和报告 | Provider Matrix |
| A2-88 | P1 | Provider 样本量不足 | 当前只有离线协议、错误映射和执行一致性证据；GLM/Qwen 尚无新目录下可比较的真实成功率、语义率、P50/P95 与超时率 | 报告成功率、语义率、P50/P95 和超时率 | Provider Quality Metrics |
| A2-91 | P1 | 30 多条 scenario 不执行 | 35 条 command 已接入 production HTTP runner 并逐条比较公开 expected；模型关闭基线仅 4 条匹配，6 条缺 setup，35 条 hardError 均无可观测 trace | 参数化执行 command 并比较 expected | Contract Scenarios / Test Runtime |
| A2-92 | P1 | Browser happy path 内容断言不足 | 状态/UI有覆盖但不拒绝错误终局 | 解析 body 并断言 kind、resolution、coverage | Browser E2E |
| A2-93 | P1 | Browser 无法断言 facet/depth | 公开响应不暴露安全语义 trace | 使用仅测试可见的脱敏 trace | Semantic Trace / E2E |
| A2-94 | P1 | Browser 不检查回答完整性 | 空或单句内容可能通过 | 检查 section、证据、数量和非空门 | Browser Quality Gate |
| A2-95 | P1 | live gate 输出硬编码 goalKind | 硬编码字段已删除；真实矩阵只输出公开终局、比率、P50/P95 与 closed diagnostics，并移除把快速社交探针称为 Provider 成功的过强文案 | 只报告真实采集的 closed 字段 | Live Gate Evidence |
| A2-96 | P1 | 缺少跨 JVM PostgreSQL 恢复 | packaged API 已跨两个真实 JVM 恢复 Conversation 与精确 Portfolio replay；同一浏览器会话跨重启仍为 NOT_RUN | 同一浏览器会话跨真实后端重启恢复 | PostgreSQL / Packaged Browser |
| A2-97 | P1 | General 单测自造正确句数 | 已增加英文、错误句数、错误 depth bucket 与 section 顺序负例；待真实 Provider 质量矩阵 | 增加语言、句数、深度负例 | General Tests |
| A2-98 | P1 | privacy check 看不到运行时数据流 | 已补 Lifecycle → PostgreSQL → 解密完整 settlement sentinel 门，待最终总门 | 解密完整 settlement 扫描 sentinel | Privacy Gate / State Test |
| A2-99 | P1 | State 隐私测试扫描错对象 | Codec 测试已扫描解密后的 publicTurn、contexts、challenges 完整明文，待最终总门 | 扫描 publicTurn、contexts、challenges 和完整明文 | State Codec Tests |
| A2-100 | P1 | 不同验证层被合并为 PASS | release 汇总已拆分 deterministic、scenario runtime、Browser contract/body、PostgreSQL/JVM restart、Provider Quality；未执行层不再被总 PASS 覆盖 | 分开报告确定性、Browser、PostgreSQL、Provider Quality | Release Reporting |
| A2-101 | P2 | 测试数量高估产品覆盖 | 分层汇总与 scenario runner 已按 35 个用户 case 报告 matched/setup/hard-error coverage；当前 scenario runtime 明确为 FAILED | 以用户场景和风险门报告覆盖 | Verification Governance |
| A2-102 | P2 | legacy 模型配置仍被脚本使用 | Eval、packaged JAR、启动器、probe 与 behavior runner 已迁移到 `model-runtime`、GLM/Qwen Provider 变量和显式 modelRef；旧 `conversational-model`、global Provider/env 与 operation provider-ref 已退出生产资产 | 删除全部退役键和脚本引用 | Configuration Cleanup |
| A2-103 | P2 | Portfolio expression timeout 无执行消费者 | 幽灵 expression 能力选择删除，预算字段、环境键和预算关系已同步移除；待最终配置总门 | 随表达器实现接入实际 operation，或删除该预算 | Runtime Configuration |
| A2-104 | P2 | Portfolio expression 编译器未接线 | 零生产入口的 Port/Compiler、可选执行分支及孤立测试已物理删除；待最终清理总门 | 实现并接入或物理删除 | Portfolio Expression |
| A2-105 | P2 | Conversation history 配置无消费者 | `max-history-rounds/recent-raw-rounds/max-input-tokens` 及零消费者属性已删除；待最终配置总门 | 实现唯一消费方或删除 | Conversation Configuration |
| A2-106 | P2 | maxSuggestedQuestions 配置无消费者 | 后端伪权威已删除；前端展示数量与交互验收仍归 Frontend Agent，整体保持 IN_PROGRESS | 建立唯一权威或删除 | Frontend / Configuration |
| A2-107 | P2 | Operation readiness 名称过时 | 已收敛为 `DISABLED/AVAILABLE_WITH_PROVIDER/INCOMPLETE_CONFIGURATION`；待最终配置总门 | 枚举和文档反映当前真实语义 | Model Policy Cleanup |
| A2-108 | P2 | 零消费者生产类型残留 | 已证明并删除 `PortfolioSelectionResult`、`SectionedTaskPresentation`、`QuestionStatus`；待全仓零引用总门 | 证明无入口后删除 | Dead Code / Architecture |
| A2-109 | P2 | 严格 JSON 不拒绝 trailing token | Goal/General Codec 已启用 `FAIL_ON_TRAILING_TOKENS` 并补 `{...} {}` 负例；待真实 Provider 总门 | 启用 FAIL_ON_TRAILING_TOKENS 并补负例 | Goal/General Codec |
| A2-110 | P0 | Privacy hard invariant 文案与代码冲突 | AGENTS、SECURITY、docs/08、本文与机器状态已统一 persistence-safe 分类，待最终总门 | 状态和证据与生产行为一致 | Architecture Status / Privacy |
| A2-111 | P1 | Evidence hard invariant 被污染 | 机器账本已从 PASS 改为 FAILED；checker 要求五类执行证据齐备才能恢复 PASS，live gate 已删除未观测 goalKind；场景 runtime 仍待补 | 未观测事实不得进入 PASS 证据 | Architecture Status / Verification |
| A2-112 | P1 | Discussion Plan 完成表述过强 | 计划头部已拆分 State/Lifecycle Complete 与 Semantic Quality Incomplete，Browser facet/depth/完整性仍明确开放 | 分开记录 State Complete 与 Semantic Quality Incomplete | Plan / Current Status |
| A2-113 | P1 | Provider registry 支持元数据强于真实证据 | built-in Registry 已由 `ConfiguredModelCatalog` 物理替换；Catalog 只表达配置、公开选择和安全执行元数据，不推导真实 Transport、Schema 或 Quality | Configured 不得推导 Transport、Schema 或 Quality | Model Catalog / Documentation |
| A2-114 | P2 | 恢复能力表述混淆 | docs/08 与分层汇总已拆分页面刷新、PostgreSQL、跨 JVM API、同浏览器跨 JVM；前三类有独立状态，最后一类为 NOT_RUN | 三种恢复分别留证 | Recovery Documentation |
| A2-115 | P1 | 字段存在被误判为功能完成 | 参数、接口、配置出现即被计入能力 | 生产消费、用户可见、负例和全链门全部成立才算完成 | Definition of Done |

#### 3.2.1 证据等级映射

| 证据等级 | 对应条目 | 说明 |
|---|---|---|
| 已复现 + 源码确认 | A2-81 | 已有真实 Provider 超时运行证据，源码预算能解释现象 |
| 待验证（P0 预防性） | A2-31 | Provider 正文复述输入尚无具体回显样本，必须由完整 settlement sentinel 门确认或排除 |
| 待验证 | A2-62 | 开放社交回复的语言与复述风险需要真实 Provider 固定样本 |
| 生产修复已落地、待外层验收 | A2-39—A2-41、A2-48、A2-49、A2-51、A2-52 | deterministic 与 packaged Portfolio 路径已通过；真实 Provider 的 depth/dimension 选择和 Browser 正文差异尚未取得，继续留账 |
| 已冻结（产品决策） | A2-85、A2-86 | 保持无 schema repair、无 Provider retry、无跨 Provider 自动重发；未来改变必须重新审批 |
| 源码确认 | A2-30、A2-32—A2-38、A2-43—A2-47、A2-50、A2-53—A2-61、A2-63—A2-80、A2-82—A2-84、A2-87—A2-115 | 生产调用链、配置消费、前端状态或测试/文档入口可直接证明现状 |

#### 3.2.2 P0 详细条目

##### A2-30：PublicTurn replay 含访客派生文本

- **用户/治理现象：** 系统宣称不持久化问题，但已结算 replay 可以包含 Goal label 和 retry inputText。
- **已确认事实：** input anchor 来自当前输入；Plan 将 anchor 作为 Goal label；Projector 将 label 写入 PublicTurn；State Codec 序列化完整 PublicTurn；Discussion retry 还直接携带本轮文本。
- **根因：** `PublicAgentTurn` 同时承担用户展示与持久化 replay，缺少 persistence-safe projection。
- **修复边界：** 固定安全 Goal label；移除原文 retry action；冻结可持久化文本来源；不得以“已加密”替代“不持久化”。
- **专属测试缺口：** 现有隐私测试只扫描 resume template，没有解密并扫描完整 settlement。
- **专属 Exit Gate：** 使用固定 sentinel 贯穿 input、Goal、Projection 和 settlement；解密后的 publicTurn、contexts、challenges 均不得包含 sentinel 或其片段。

##### A2-31：Provider 文本缺少持久化安全证明

- **用户/治理现象：** 模型可见访客输入，接受后的自由正文可能复述输入并进入 replay。
- **已确认事实：** General 与非快速路径 Conversational 正文来自 Provider。`ClarificationProposal.prompt` 虽由 Provider 输出解码并校验长度，但 `getPrompt()` 在生产代码中零消费者；公开 Challenge prompt/message 由 Lifecycle 固定文案和 typed choices 生成，因此 Clarification 不属于本项的 Provider 正文持久化入口。
- **待验证推断：** 尚未保存一条实际复述样本，不能把“可能复述”写成已经发生。
- **修复边界：** 持久化政策按固定公开文本、审核 Claim、visitor-derived text、provider-derived text 分类；未知来源默认不持久化。生产机制只依赖显式来源分类与默认拒绝，关键词/sentinel 检测只属于测试。
- **专属测试缺口：** 缺少固定 Provider fixture 主动复述 sentinel 的负例。
- **专属 Exit Gate：** fixture 输出包含 sentinel 时，公开响应与持久化策略按批准设计处理，PostgreSQL 明文扫描结果符合零原文边界。

##### A2-32：精确 replay 与禁止保存原文冲突

- **用户/治理现象：** 同 requestId 要求精确重放，但完整模型正文无法证明不含 visitor-derived text。
- **已确认事实：** 当前 replay 权威保存完整 PublicTurn；Portfolio 确定性文本与 Provider 自由文本共用同一 SettlementPayload。
- **根因：** replay 没有区分可安全重放的确定性投影与不可证明安全的自由正文。
- **修复边界：** 确定性 Portfolio Turn 继续精确 replay，并保留公开 continuation action 中的 opaque ContextHandle；Provider 派生的 General/Conversational 正文不持久化，改存 `CAPABILITY_UNAVAILABLE/REPLAY_BODY_NOT_RETAINED`，固定文案“该回答未被保留，请重新提问。”，不建设第二结果缓存。
- **专属测试缺口：** 缺少不同 PublicTurn variant 的 replay 安全矩阵。
- **专属 Exit Gate：** ANSWER、CONVERSATIONAL、CLARIFICATION、BOUNDARY、CAPABILITY_UNAVAILABLE 均有首次响应/同 requestId replay/数据库明文三向断言。

##### A2-33：Operation Provider 声明不控制实际调用

- **用户/治理现象：** 配置可以声明一个数据接收方，唯一 Transport 却由另一全局 Provider 构造。
- **已确认事实：** operation `providerRef` 只参与非空 readiness；Transport 使用 `conversational-model.provider`。
- **根因：** 配置授权权威与执行 Provider 权威分离。
- **修复边界：** 过渡期只增加启动 fail-closed 一致性 guard；未来模型目录 Replacement Slice 再替换单 Transport，不并行建设第二路由。
- **专属测试缺口：** 缺少 operation Provider 与全局 Provider 错配的启动负例。
- **专属 Exit Gate：** 任一 ENABLED operation 错配 Provider 时应用启动失败，匹配时请求只到声明 Provider fixture。

##### A2-34：Operation schemaVersion 不控制 Codec

- **用户/治理现象：** 配置任意非空 schemaVersion 仍可能公开能力可用。
- **已确认事实：** operation schema 只检查非空；GoalProposalCodec 和 GeneralDraftCodec 始终由代码固定创建。
- **根因：** schema 配置没有与唯一生产 Codec 建立版本等式。
- **修复边界：** 启动期将 operation schema 精确绑定当前 Codec 常量；不增加兼容 reader。
- **专属测试缺口：** 缺少正确、错误、空 schema 的启动矩阵。
- **专属 Exit Gate：** 错误版本启动失败；正确版本通过并由对应 Codec 解码；旧版本零生产引用。

##### A2-35：Agent availability 可能误报

- **用户现象：** 页面可显示自由文本可用，但首次请求因配置权威不一致稳定失败。
- **已确认事实：** PortfolioController 读取 mode 字符串和全局 providerAccess，没有消费 operation/Codec 最终一致性结果。
- **根因：** readiness 没有单一深模块，公开投影自行拼接部分条件。
- **修复边界：** availability 只消费启动期冻结的统一 readiness snapshot。
- **专属测试缺口：** 缺少配置错配时公开投影与实际请求一致失败关闭的集成测试。
- **专属 Exit Gate：** 每种配置矩阵中，公开 availability、Bean wiring 和首次请求终局一致。

##### A2-36：Privacy 架构账本状态失真

- **治理现象：** `PRIVACY_BOUNDARY=PASS` 与 A2-30 已确认生产行为冲突。
- **已确认事实：** 机器账本当前证据没有覆盖完整 PublicTurn settlement taint。
- **根因：** 静态隐私扫描和局部 Codec 测试被外推成完整数据流证明。
- **修复边界：** 先将状态与证据改为真实值；只有 A2-30—A2-32 原始路径关闭后才恢复 PASS。
- **专属测试缺口：** 架构状态 checker 不知道隐私高风险门是否真实执行。
- **专属 Exit Gate：** privacy hard invariant 的 PASS 证据必须引用新鲜完整 settlement 门，checker 负例拒绝缺失该证据的 PASS。

##### A2-110：Privacy hard invariant 文案与代码冲突

- **治理现象：** AGENTS、SECURITY、动态账本均禁止问题持久化，但公共 replay 数据模型没有同一文字分类。
- **已确认事实：** 文档边界一致，代码与机器状态不一致；A2-36 负责当前状态纠正，本项负责防止规则再次漂移。
- **根因：** “final public replay”被错误理解为所有 PublicTurn 字段天然脱敏。
- **修复边界：** 在当前权威文档和合同中明确 persistence-safe PublicTurn 字段/variant；不放宽原始访客文本边界。
- **专属测试缺口：** documentation checker 没有检查 replay 允许项与禁止项的闭合说明。
- **专属 Exit Gate：** AGENTS、SECURITY、docs/08、docs/15、State Codec 测试和机器账本使用同一持久化分类，文档正反例通过。

### 3.3 A2-30—A2-115 修复边界

1. **P0 隐私止血：** 在任何新语义能力之前关闭 A2-30—A2-36、A2-110；不得用“已加密”替代“不持久化”，不得为了精确 replay 放宽已冻结隐私边界。
2. **验收真实性：** A2-91—A2-101、A2-111—A2-115 必须先恢复真实可执行证据；场景清单、脚本源码字符串和 HTTP 200 不能作为产品通过证据。
3. **现有行为修复：** A2-53—A2-78 应在不新增第二状态权威的前提下修复；Audience、subjectHint、constraints 必须明确选择“实现”或“删除宣称”。
4. **产品能力升级：** A2-37—A2-41、A2-43—A2-52、A2-63—A2-68 属于 AnswerIntent、Portfolio 表达和 General 质量的同一产品分叉；未经批准不得并行创建多个模型权威。
5. **Provider 收敛：** A2-79—A2-88 使用已批准的配置化 GLM/Qwen 目录作为唯一目标架构；旧单 Provider/global/visitor 权威不得作为兼容层恢复，真实矩阵必须按显式 ModelSelection 独立执行。
6. **隐私实现机制：** sentinel 只用于验收，不得成为生产清洗或判定机制；生产代码必须使用闭合来源分类，未知或 Provider 派生正文默认拒绝持久化。
7. **清理：** A2-102—A2-109 只有在零生产消费者和对应替代门成立后删除，不保留兼容键或幽灵接口。

#### 3.3.1 第一批 P0 当前证据（仍为 IN_PROGRESS）

- `SemanticPlanCompilerTest`：访客 sentinel 不再进入 Goal label；`AgentTurnLifecycleContinuationTest`：Discussion 失败 action 不再携带原始 inputText。
- `AgentTurnLifecycleReplayTest`：Provider 派生 Conversational 与 General 首次响应保留正文，同 requestId replay 固定为 `REPLAY_BODY_NOT_RETAINED`，Provider/Plan 执行次数保持 1。
- `PersistenceSafeReplayPolicyTest`：只有纯 Portfolio Task 可精确 replay；General、Comparison、Cross-domain 默认拒绝正文持久化；Portfolio continuation handle 原样保留。
- `AgentStatePayloadCodecTest`：五种 PublicTurn variant 可回读；完整解密 settlement 同时扫描 `publicTurn + contexts + challenges`，visitor/provider sentinel 为零。
- `JdbcAgentStateStoreIntegrationTest#postgresCompleteSettlementPlaintextExcludesVisitorAndProviderSentinel`：2026-08-24 使用 Testcontainers PostgreSQL 16.14 实际执行，`1 tests / 0 failures / 0 errors / 0 skipped`；解密完整 settlement 无 sentinel，同 requestId 回读固定终局。
- `contracts/agent-turn/scenarios/lifecycle-state.json` 的 `EXACT_PUBLIC_TURN` 场景是确定性 Portfolio ANSWER，按上述分类保留，不做全局替换。Provider 正文场景才应期待固定终局。
- Backend 全量于 2026-08-24 实际执行：`870 tests / 0 failures / 0 errors / 4 skipped`；整体状态仍需等待本批 packaged/Browser 与后续全仓门，不据此提前 COMPLETE。
- clean packaged JAR 的 DEFAULT/IN_MEMORY Browser lane 于 2026-08-24 实际执行，桌面/移动合计 `8 passed / 8 lane-specific skipped`，覆盖五 variant 消费、同 requestId timeout replay、typed continuation 与隐私 smoke；该 lane 明确关闭 Provider，因此不替代最终“真实 Provider × PostgreSQL × JVM 重启”总门。

#### 3.3.2 第二批 Provider 授权当前证据（仍为 IN_PROGRESS）

- `AgentRuntimeReadinessTest`：ENABLED Operation 的 Provider 错配会使 Spring ApplicationContext 启动失败；Provider/schema 正确矩阵通过，错误 schema 被拒绝。
- `GoalProposalCodec.SCHEMA_VERSION=goal.proposal.v5`、`GeneralDraftCodec.SCHEMA_VERSION=general.draft.v2` 是当前唯一生产 Codec 版本；不接受兼容别名或旧版本。Goal v5 在 v4 的 depth、闭合 comparison/constraint 与可信页面/受众输入上增加显式 typed `recentReference`；General v2 包含闭合 aspects 和 typed caveat，不能沿用旧版本名义。
- `AgentCapabilityConfigurationTest`：Goal/General 模型端口只消费统一 readiness；`PortfolioControllerAvailabilityTest`：状态模式、Operation mode、Provider 数据策略组合只经同一 readiness 投影公开 availability。
- `start-local.test.ps1` 与 `run-agent-behavior-audit.test.ps1` 已实际通过；启动脚本把 Operation `providerRef` 绑定到实际选择的 `PORTFOLIO_MODEL_PROVIDER`，不再使用 `conversational-default`。
- Backend clean package 于 2026-08-24 实际执行：`874 tests / 0 failures / 0 errors / 4 skipped`，包含 Testcontainers PostgreSQL 16.14；新 packaged JAR 分别以 Provider 错配和 schema 错配启动，两次均在 ApplicationContext 完成前非零退出并报告对应 authority mismatch。
- 同一新 packaged JAR 的 DEFAULT/IN_MEMORY Browser 回归实际执行：桌面/移动 `8 passed / 8 lane-specific skipped`，公开 Portfolio availability 与既有 PublicAgentTurn 消费链未回归；Provider 明确关闭，因此不冒充真实接收方证据。
- 本批不增加 Provider 路由、兼容 reader 或第二 readiness；真实 Provider 接收方及其 packaged Browser 行为仍须后续总门证明，因此 A2-33—A2-35 不关闭。

#### 3.3.3 第三批证据真实性当前证据（仍为 IN_PROGRESS）

- `assert-live-project-discussion-context.test.ps1` 已执行通过：拒绝 `goalKind=` 等未观测字段，要求输出实际响应的 kind、resolution、recommendation item 数和 discussion 状态；未运行真实 Provider 前 A2-95 不关闭。
- `assert-live-general-answer-quality.test.ps1` 已执行通过：除英文负例外，三句 CONCISE 被归入 `OUTSIDE` 并失败，section 顺序错误也失败；真实 Provider 抽样仍未完成，A2-97 保持 IN_PROGRESS。
- `write-agent-verification-summary.test.ps1` 已执行通过：Browser contract 与 Browser body、PostgreSQL state 与 JVM restart、场景 runtime 与 Provider Quality 分层；任一层 `NOT_RUN`/`IN_PROGRESS` 时 overall 只能是 `IN_PROGRESS`，`-RequireComplete` 非零退出。
- `agent-architecture-status.test.ps1` 已执行通过：`EVIDENCE_BEFORE_COMPLETION=PASS` 若缺少 deterministic、scenario runtime、Browser body、PostgreSQL JVM restart、Provider Quality 五类新鲜标记即失败。当前机器账本据真实缺口记为 `FAILED`，不以测试总数或 HTTP 成功恢复 PASS。
- `run-agent-behavior-audit-assets.test.ps1` 已执行通过：实际确认 `test:e2e`、默认 Playwright spec 与 L0/L3 Java 资产存在；runner 不再引用 `test:e2e:behavior`、`api-l0`、`runtime` project 或已删除的 `AgentBehaviorAdversarialProviderIntegrationTest`。L0/L3 已实跑，分别为 6 tests 与 14 tests、零失败；输出范围明确为 `CONTRACT_MANIFEST_ONLY` 和 `PROVIDER_CODEC_ADVERSARIAL`，不冒充用户场景运行时。
- 本批后端 clean package 已实跑：874 tests、0 failures、0 errors、4 skipped，Testcontainers 使用 PostgreSQL 16.14。该结果仍不替代 scenario runtime、Browser body、JVM restart 或 Provider Quality。
- `run-packaged-jvm-restart-api-gate.ps1` 已对 SHA-256 `cfca74e0cb5048c61c64db58a6ca789ca1106bea3cb9369a5afe082dd24740a0` 的 packaged JAR 实跑：临时 PostgreSQL 16 容器、同一数据库/密钥、两个真实 Java 进程；第二个 JVM 以原 resume token 恢复同一 Conversation，并对同 requestId 返回精确 Portfolio PublicTurn。输出明确为 `browser=NOT_RUN`，因此 A2-96 仍不关闭。
- `run-agent-scenario-runtime.ps1` 已对同一 SHA-256 的 packaged JAR、生产 `/api/agent/turns`、模型关闭且限流提升到 1000 的基线实跑：35/35 command 均实际发出，公开 expected 匹配 4，失败 31；29 条无需额外 setup，6 条的 lifecycle/provider/settlement setup 未执行；35 条 hardError expectation 均无可观测通道。runner 默认只报告，`-RequireComplete` 对任何失败或缺口非零退出；这项证据证明清单已进入运行时，也证明 A2-91 尚未关闭。
- packaged runner 曾把当前 workspace HEAD 打印成 JAR commit，但 JAR manifest 未嵌入 commit，二者无法建立制品绑定；该字段已改为 `Workspace commit (not JAR identity)`，制品身份只使用实际 SHA-256 与 mtime。未在构建阶段嵌入并验证 commit 前，任何证据不得声称 JAR 对应当前 HEAD。
- 同一 JAR 的 DEFAULT/IN_MEMORY packaged lane 已在场景审计接线后实跑：scenario runner 先执行 35 条生产 HTTP command，随后 Playwright 桌面/移动共 `8 passed / 8 lane-specific skipped`；Browser 结果继续只算 contract/lifecycle 层，不覆盖 scenario 的 `FAILED` 或 Browser body 的 `IN_PROGRESS`。
- 本组没有修改 Frontend 代码。A2-89 的空 behavior 目录/失效 Playwright 配置、A2-92—A2-94 的 Browser 内容门属于 Frontend Agent 交接；后端 scenario runtime 与跨 JVM runner 继续在本批后续实现。

#### 3.3.4 Portfolio AnswerIntent 第一批当前证据（仍为 IN_PROGRESS）

- `PortfolioReviewedGoalSourceTest` 与 packaged PRESET 路径证明 reviewed facet 会确定性派生同名 output，不再固定伪报 `OVERVIEW`。
- `GoalProposalCodecTest`、`SemanticPlanCompilerTest` 与 `BlockedGoalTemplateTest` 证明 Portfolio Fact/cross-domain/澄清恢复携带 `CONCISE/STANDARD/DETAILED`，outputs 与 typed parameters 不一致时失败关闭；澄清不再把 `OUTPUT` 当作可询问字段，未知 comparison dimension 不再降级为 `VERIFICATION`。
- `PortfolioInvocationFactoryTest`、`PortfolioSupportEvaluatorTest`、`PortfolioPresentationComposerTest` 与 Bundle/PostgreSQL adapter 回归证明 depth 改变 overview 检索 profile、每主体候选上限、必需 coverage profile、公开区块数与 DETAILED detail；缺必需 profile 形成 `PARTIAL` 与闭合 omission，不把存在任意 Claim 当作完整回答。
- cross-domain supporting tasks 现在从同一个 `ApplyConceptParameters.depth` 派生 General 与 Portfolio depth，不再硬编码 `STANDARD`。
- model-disabled packaged JAR SHA-256 `2d5a3e18c87adf6ce827069b9c23f382060c84c6db90db3484d87be31fdcad2c` 的 PRESET Agent 终局与隐私 smoke 已通过；35 条 runtime 场景仍为 `FAILED`（0 PASS、4 IN_PROGRESS、31 FAILED），因此不据 packaged HTTP 成功关闭真实语义项。
- packaged runner 原先在 Provider 明确关闭时仍要求自由文本推荐成功，现已按 lane 分离：disabled 必须返回 `CAPABILITY_UNAVAILABLE/SEMANTIC_ROUTING_UNAVAILABLE`，LIVE 才验推荐正文。该修订防止把不可能的配置当作产品失败，也不把 fail-closed 当作推荐成功。
- 变更后的 Backend 全量于 2026-08-24 实际执行：`881 tests / 0 failures / 0 errors / 4 skipped`，包含 Testcontainers PostgreSQL 16.14；`privacy-check` 扫描 496 个生产文件通过，`start-local.test.ps1`、`run-jar-e2e.test.ps1` 与当前权威文档检查通过。该 deterministic 证据不替代真实 Provider/Browser 语义门。
- 本批没有修改 Frontend。A2-39—A2-41、A2-48、A2-49、A2-51、A2-52 仍等待真实 Provider 的 typed 选择与 Browser 正文/coverage 观测后才能移除。

#### 3.3.5 Recommendation constraints 与 Comparison 表达当前证据（仍为 IN_PROGRESS）

- `GoalProposalCodecTest` 与 `GoalInterpretationAdapterTest` 证明模型只可从当前公开目录投影的 `CAREER_TRACK_*` / `CAPABILITY_*` 中选择约束；目录外值失败关闭，不能复制访客短语建立开放约束。当前不兼容输入/输出语义由 `goal.proposal.v4` 明确承载。
- `PortfolioInvocationFactoryTest`、`PortfolioSemanticResultFactoryTest` 与 `JdbcPostgresKnowledgeQueryTest` 证明 requestedSize 和闭合约束进入执行：PostgreSQL 先做 typed 目标召回，候选不足或不完全匹配时扩大召回；Bundle/PostgreSQL 都由同一语义层按约束匹配数、证据类别数和稳定 ID 排序，并对缺口形成 `PARTIAL + unsatisfiedConstraints`。
- `PublicAgentTurnProjectorTest` 与 `PublicAgentTurnInvariantTest` 证明每个推荐项以闭合 reason code 产生固定公开说明和公开 source key；`actualSize == requestedSize` 仍允许报告约束缺口，数量闭合不再冒充目标满足。`PortfolioPresentationComposerTest` 证明 comparison 按请求 dimension 对齐主体 Claim 与来源，不再顺序堆叠。
- 本批 Backend clean package 于 2026-08-24 实际执行：`886 tests / 0 failures / 0 errors / 4 skipped`，包含 Testcontainers PostgreSQL 16.14；全仓 `privacy-check` 扫描 916 个文件通过，相关脚本自测与当前权威文档检查通过。
- model-disabled packaged JAR SHA-256 `c7af8e3506bdb6f7291cb8b59778d23405f32510de93d4902773aff82224a7fc` 的 PRESET/隐私 smoke 已通过；35 条 runtime 场景仍为 `FAILED`（0 PASS、4 IN_PROGRESS、31 FAILED），因此 A2-43—A2-47 仍不移除。
- 本批未修改 Frontend。后端公开合同现在允许“推荐数量满足但存在 `unsatisfiedConstraints`”；Frontend Agent 需要让 mapper 接受该组合，并在推荐卡明确展示未满足约束。UI 文案、布局与交互由 Frontend Agent 负责，未取得 Browser 正文证据前不得把 A2-44/A2-47 标为完成。

#### 3.3.6 Audience 与页面主体后端证据（仍为 IN_PROGRESS）

- `GoalInterpretationInputFactoryTest` 与 `GoalInterpretationAdapterTest` 证明已验证的 page `subjectHint` 以 `defaultSubject` 进入模型输入，Audience 以闭合 `INTERVIEWER/MENTOR/HR/GUEST` profile 进入同一可信投影；访客不能提供任意 subject ID 或 audience 文本。
- `SemanticRouteValidatorTest` 证明 STANDARD 模式下，模型对单主体 Portfolio Fact/跨域目标省略 subject 时，后端注入目录验证过的 `SURFACE_HINT` 且不制造输入 anchor；显式选择另一个公开主体仍由原公开目录校验，不把页面默认误作强制锁定。Discussion 继续只使用原有 `CONTINUATION` locked subject。
- `SemanticPlanCompilerTest`、`GeneralTaskExecutorTest` 与 `PortfolioInvocationFactoryTest` 证明 Audience 传播到跨域的 General/Portfolio/Synthesis 全部 task；General Provider 请求不再硬编码 GUEST，Portfolio 在不扩大 subject/evidence scope 的前提下按角色改变 facet 优先级。
- 该输入语义变化由 `goal.proposal.v4` 承载。Backend clean package 于 2026-08-24 实际执行：`889 tests / 0 failures / 0 errors / 4 skipped`，包含 Testcontainers PostgreSQL 16.14；全仓 `privacy-check` 扫描 916 个文件通过，启动器与 packaged runner 自测通过。
- model-disabled packaged JAR SHA-256 `71b8efc92889ffc9ddd630b96f15e5b3c8308806436aa63bc420483919e67911` 的 PRESET/隐私 smoke 已通过；35 条 runtime 场景仍为 `FAILED`（0 PASS、4 IN_PROGRESS、31 FAILED），因此 A2-53—A2-55 仍不移除。
- 本批未修改 Frontend。现有 API 字段未改变；Frontend Agent 负责确保 PROJECT/CASE 页面持续发送正确 `subjectHint + requestSource`，并完成角色差异、页面省略表达的 Browser 交互与正文验收。UI 文案与交互设计仍归 Frontend Agent。

#### 3.3.7 General 生产质量门当前证据（仍为 IN_PROGRESS）

- `general.draft.v2` 把 explanation aspect 与 caveat kind 变成闭合字段；生产 Codec 拒绝未知/重复枚举、未知字段及超长 caveat。`GeneralDraftValidator` 按 depth 强制每条 statement 句数及逐句中文字符，并要求 explanation 的闭合 aspect 集合精确等于该 depth 需求。
- Comparison 实际 `(subject, dimension)` 集合必须与请求笛卡尔积精确相等，重复、遗漏或额外 pair 均失败关闭；caveat 要求一至两句中文、kind/text 分别去重。上述门只证明 Provider 声明和结构闭合，不把自报 aspect 当作正文语义正确。
- `GeneralDraftValidatorTest`、`GeneralDraftCodecAdversarialTest`、`GeneralKnowledgeGeneratorTest`、`GeneralTaskExecutorTest`、`OpenAiCompatibleGeneralKnowledgeAdapterTest` 与 `AgentRuntimeReadinessTest` 的正反例通过；Backend clean package 于 2026-08-24 实际执行：`895 tests / 0 failures / 0 errors / 4 skipped`，包含 Testcontainers PostgreSQL 16.14。
- 全仓 `privacy-check` 扫描 916 个文件通过，`start-local.test.ps1`、`run-jar-e2e.test.ps1` 与当前权威文档检查通过。model-disabled packaged JAR SHA-256 `500813176cfb553073c3b927a59fc10682c5685235e66b0b245af6794a469c1a` 的 API/隐私 smoke 已通过；35 条 runtime 场景仍为 `FAILED`（0 PASS、4 IN_PROGRESS、31 FAILED），未取得真实 Provider 正文或 Browser 证据，A2-63—A2-68 不移除。
- 本批未修改 Frontend，公开渲染合同未变化；真实正文的语言、深度、Comparison coverage 与 caveat 相关性 Browser 验收仍交由 Frontend Agent 执行，UI/交互设计不在本批范围。

#### 3.3.8 Typed 多轮语义状态后端证据（仍为 IN_PROGRESS）

- Session V7 新增 `semantic_state_key_id/nonce/ciphertext/updated_at` 四列并以 all-or-nothing constraint 失败关闭；状态与成功终局在同一 settlement 事务中原子替换。Memory 与 PostgreSQL 使用同一 `ConversationSemanticState`，数据库 payload 由独立 AAD 的 AES-GCM envelope 保护，密钥覆盖与 previous-key 检查包含该 payload。
- 状态只投影成功 Portfolio Fact/Compare/Recommend 的闭合 GoalKind、公开 subject ID、requested outputs、facet/depth、comparison dimension、requested size、闭合 constraints 与公开 `sectionId/sectionKind`；不保存 Goal label、访客 anchor/topic/concept、client conversationWindow、section title/content、Provider 正文、Prompt、Token 或 handle。General 与跨域 concept 不伪装为可恢复语义。
- Goal JSON 合同升级为 `goal.proposal.v5`。Provider 必须在明确引用上一回答时返回精确 `recentReference.goalId` 与可选 `sectionId`；Codec 校验二者属于 V7 typed state，Validator 仅在显式引用成功后注入 `RECENT_TURN`。仅有旧状态或 Provider 漏 subject 不会静默绑定上一主题；Provider 输出 `SURFACE_HINT`、`CONTINUATION`、`RECENT_TURN` 或无 anchor 的 subject 均失败关闭，这些 basis 只允许后端注入。
- `ConversationSemanticStateProjectorTest` 以 visitor/provider sentinel 证明投影 JSON 不含原文、模型标题或正文；`GoalProposalCodecTest`、`SemanticRouteValidatorTest`、`GoalInterpretationInputFactoryTest`、`GoalResolverTest` 证明显式 Goal/section 引用、遗漏不误绑、来源权威与 client messages 分离；`AgentStatePayloadCodecTest` 证明独立 AAD 回读及跨 Conversation 拒绝。
- `JdbcAgentStateStoreIntegrationTest#typedSemanticStateIsAtomicallyEncryptedAndSurvivesStoreRestart` 使用 Testcontainers PostgreSQL 16.14，证明 typed state 与终局同事务写入、数据库明文不含状态 token，并由新建 JDBC store 实例解密恢复；`ConversationSemanticStateMigrationTest` 证明 V7 没有原文列。Backend clean package 于 2026-08-24 实际执行：`905 tests / 0 failures / 0 errors / 4 skipped`。
- `postgres-local.test.ps1`、`start-local.test.ps1` 与当前权威 `run-agent-behavior-audit-assets.test.ps1` 已通过。旧 `run-agent-behavior-audit.test.ps1` 在当前 HEAD 与资产门互相矛盾：它要求已删除的 `test:e2e:behavior/api-l0/runtime`；当前工作树另有 Frontend Agent 修改，故本批不覆盖或暂存该文件，也不把该僵尸测试记为通过。
- 全仓 `privacy-check` 扫描 918 个文件通过；model-disabled packaged JAR SHA-256 `524ba7550bd3a603afd5c1572c66b2f01e819ef44f05c292243fc39e3859cb5f` 的 IN_MEMORY API/隐私 smoke 通过。35 条 runtime 场景仍为 `FAILED`（0 PASS、4 IN_PROGRESS、31 FAILED），该结果不冒充真实语义成功。
- 本批没有修改 Frontend 或公开 API。真实 Provider 是否稳定产生正确 `recentReference`、Browser 是否能以“进一步展开/第 N 个区块”得到正确正文，仍由 Frontend Agent 的 UI/交互与 Browser 语义门验收；A2-56—A2-58 和整体 Agent 2.0 保持 `IN_PROGRESS`。

#### 3.3.9 Discussion 澄清与 Conversational 校验后端证据（仍为 IN_PROGRESS）

- Discussion 的 `NEEDS_CLARIFICATION` 已使用现有 `CLARIFICATION` 公开 variant：active pointer 精确提供 OVERVIEW、RESPONSIBILITY、SOLUTION、VERIFICATION、STATUS 五种服务端 choice；expired pointer 只提供重进 choice。恢复模板与 choice bindings 必须精确闭合，typed template 随 challenge 短期加密保存。
- 澄清首次结算使用当前 pointer handle 作为 generation guard，不改变 pointer；facet resolve 不再次解释访客文本，而是由后端构造 `CONTINUATION` basis 的锁定项目 Fact Goal。Lifecycle 包装器同步保留成功 Turn 的 V7 semantic state，避免 Discussion 路径丢失短期 typed summary。
- `ConversationalMessageValidator` 只处理 Provider-derived 开放社交正文：限制 160 字、至少两个中文字符且拉丁字母不超过中文字符两倍，拒绝完整英文句、控制字符，以及不少于 8 字的当前访客输入被连续复述。固定快速问候继续走服务端文案；这里是确定性来源/结构校验，不是关键词清洗或 sentinel 生产机制。
- `AgentTurnLifecycleContinuationTest`、`DiscussionClarificationTemplateTest`、`AgentStatePayloadCodecTest` 与 `GoalProposalCodecTest` 的定向组合实际执行：`34 tests / 0 failures / 0 errors / 0 skipped`。Backend clean package 实际执行：`912 tests / 0 failures / 0 errors / 4 skipped`，包含 Testcontainers PostgreSQL 16.14。
- `code-quality-check`、当前权威文档检查、`run-agent-behavior-audit-assets.test.ps1` 通过；按仓库命令对 `backend/src/main` 扫描 500 个生产文件的 privacy gate 通过。model-disabled packaged JAR SHA-256 `397b1dbe1edfc17cc69e7614a799264d8949db8eb0a344982bff93ef7261a328` 的 IN_MEMORY API/日志隐私 smoke 通过；35 条 runtime 场景仍为 `FAILED`（0 PASS、4 IN_PROGRESS、31 FAILED）。
- 本批没有修改 Frontend 或扩展公开 API。Frontend Agent 仍需处理 A2-59，并执行 active/expired clarification 的 choice 渲染、提交后只读、facet resolve 正文和 pointer revision Browser 门；UI 文案、布局与交互设计由 Frontend 责任区决定。真实 Provider 固定样本与 Browser 原路径未取得，A2-60—A2-62 不删除。

### 3.4 本轮审计证据边界

- 已扫描 1310 个仓库文件，其中生产 Java 479、Java 测试 221、Frontend 源码 135、E2E 6、脚本 66、共享合同 23、State Migration 6；
- 本轮新鲜通过 Frontend 470 tests、类型检查、构建、privacy、code-quality、documentation、architecture、public API、runner/checker 自测；这些绿色结果同时证明当前门无法发现本表中的语义与数据流问题；
- 2026-08-24 第二批已取得 clean package 的 874 tests、0 failures、0 errors、4 skipped 新鲜证据；该数量只证明对应测试集执行，不替代尚未运行的 35 条用户场景、Browser 正文语义、跨 JVM 恢复或 Provider Quality；
- 本轮没有执行新的真实 Provider 调用，也没有把任何访客或模型正文写入本文。

## 4. 问题簇一：推荐与澄清语义断裂

### 4.1 A2-01：推荐请求被错误转成主体澄清

#### 用户现象

用户输入“给我推荐两个项目”后，系统没有直接从已审核公开项目中选择两个候选，而是要求用户先从项目列表中选择项目。

#### 判断

该请求已经包含：

- 明确动作：推荐；
- 明确数量：两个；
- 明确候选域：当前公开作品集项目。

在 Agent 2.0 的职责划分中，Goal Model 应表达 `PORTFOLIO_RECOMMENDATION` Goal，确定性 Portfolio Capability 再完成候选选择。让用户先选择候选，相当于把推荐模块本应完成的工作退回给用户。

#### 当前结论

Goal Interpretation Prompt 与后续确定性边界缺少一条稳定规则：当公开候选域明确且请求给出推荐数量时，不得仅因未点名具体项目而要求主体澄清。

### 4.2 A2-02：澄清恢复丢失原始 Goal

#### 用户现象

用户为“推荐两个项目”补充一个项目名称后，系统进一步询问“想了解这个项目的哪些方面”，原始推荐目标已经消失。

#### 源码根因

冻结设计要求 Clarification Challenge 在服务端保存短期的 field、subject binding 与 blocked Goals，并在 `RESOLVE_CLARIFICATION` 后恢复原 Goal。

当前 `ClarificationStore.Record` 只保存：

- conversationId；
- resumeTokenHash；
- contentReleaseId；
- challenge fields；
- choice/text binding。

`AgentTurnLifecycleService.resolveInput()` 成功消费文本澄清答案后，没有使用 `consumed.record()` 与 `consumed.answer()` 合并原 Goal，而是把答案文本单独包装成新的 `ASK(FREE_TEXT)`，再次调用 `GoalResolver`。

实际语义因此变为：

```text
原始 Goal：推荐两个项目
澄清答案：周末登录奖励 ABTest 完整闭环
当前恢复方式：把“周末登录奖励 ABTest 完整闭环”当成全新问题重新理解
```

丢失的信息包括：

- recommendation goalKind；
- requestedSize=2；
- 原 Goal 的语义身份（不持久化原始输入锚点或访客问题）；
- 推荐约束；
- blocked Goal 身份；
- 澄清字段与原 Goal 参数的绑定关系。

这与 Agent 2.0 冻结设计 D-30/D-39 的澄清恢复语义不一致。

### 4.3 A2-03：澄清答案没有进入本地 USER 消息序列

#### 源码根因

普通 FREE_TEXT、PRESET 和 SuggestedAction 在请求前都会追加 USER 消息；`handleClarification()` 直接调用 `runTurn()`，没有把提交的 choice/text 记录为 USER 轮次。

一次澄清后，本地消息可能成为：

```text
USER       给我推荐两个项目
ASSISTANT  请选择项目
ASSISTANT  您想了解该项目的哪些方面
```

后端请求合同要求 conversationWindow 从 USER 开始并严格 `USER/ASSISTANT` 交替。当前 `conversationWindowOf()` 只会截取最后 12 条，并在第一条不是 USER 时丢弃首条；它不会发现或修复内部连续的两个 ASSISTANT。

真实运行已经记录到连续 HTTP 400：

```text
http.status_code=400
error.code=VALIDATION_ERROR
```

错误发生在 Provider 调用之前，因此不是模型失败。

### 4.4 A2-04：失败请求会继续污染本地窗口

FREE_TEXT 和 SuggestedAction 会在请求成功前先追加 USER 消息。如果请求随后失败，该消息不会回滚，也没有标记为 failed/not-delivered。

再次操作时，conversationWindow 会包含失败轮次，可能产生连续 USER、连续 ASSISTANT 或与服务端已结算会话不一致的历史，导致后续请求继续失败。

修复不能只在发送前“尽量修剪”数组；必须先定义什么是已提交、已结算、失败和取消的会话轮次，再从可信轮次生成窗口。

### 4.5 A2-16：简单问候被错误升级为必填澄清

#### 用户现象

用户只输入“你好”，系统没有返回 `CONVERSATIONAL` Turn，而是返回一张必填 TEXT 的 Critical Clarification，要求用户补充想了解的项目、案例或概念。

#### 判断

PublicAgentTurn 已有独立 `CONVERSATIONAL` variant，Goal Interpretation closed schema 也允许输出 `CONVERSATIONAL`。纯问候不需要形成 Portfolio/General Goal，更不应强迫用户填写目标后才能继续。

当前真实 Provider 输出说明 Prompt/Goal Policy 没有稳定保护这一最小 conversational 边界。后续应以确定性输入边界或可验证的 Goal policy 保证问候、致谢等安全社交输入不会进入 Critical Clarification；不能依赖 Provider 每次自行判断正确。

### 4.6 A2-17：澄清级联没有终止规则

#### 用户现象

当前页面连续出现两张 Critical Clarification：

1. “你好”后要求补充目标；
2. 用户填写“给我推荐一个项目”后，又要求补充推荐领域或目标；
3. 用户继续填写项目名称后，请求最终进入错误状态。

#### 源码根因

澄清答案当前被重新包装为全新 FREE_TEXT，因此 Provider 可以再次返回 CLARIFICATION。Lifecycle 没有保存原 blocked Goal，也没有澄清深度、同字段重复、无信息增益或最大轮次规则。

这会形成：

```text
CLARIFICATION
→ RESOLVE（答案被当作新问题）
→ CLARIFICATION
→ RESOLVE
→ CLARIFICATION / 400 / CAPABILITY_UNAVAILABLE
```

修复目标不是简单设置一个任意循环次数，而是首先恢复原 Goal；在此基础上，再对重复字段、无信息增益和不可恢复状态给出明确终局，避免无限 Critical Clarification。

### 4.7 A2-18：历史澄清卡没有 consumed/submitted UI 状态

#### 用户现象

第一张澄清卡提交后仍保留已填写文本、可编辑 textarea 和可点击“提交补充”；第二张澄清卡同时处于可提交状态。用户无法判断哪张 Challenge 仍有效。

#### 源码根因

`ClarificationChallengeForm` 只有当前表单本地的 selected/text values 和一个外部 `disabled` prop。ConversationThread 渲染历史 PublicAgentTurn 时：

- AgentMessage 不保存 clarification submitted/consumed 状态；
- PublicAgentTurnMessage 不接收 disabled/activeClarificationId；
- ClarificationTurnView 没有被传入 disabled；
- 新请求 pending 时，历史澄清表单也不会统一禁用；
- 请求成功后，旧表单不会转成只读的“已提交”摘要。

服务端 ClarificationStore 是一次消费权威；重复提交旧 clarificationId 只能得到 already-consumed/unavailable 终局。前端却继续把它展示为有效操作，制造了必然失败的入口。

后续 UI 必须只有当前会话中最新、未提交、仍有效的一张澄清卡可操作；历史卡应显示安全的已提交/已失效状态，不能再次发出 RESOLVE。

### 4.9 A2-20：通用知识生成文案发生语言漂移

#### 用户现象与证据

真实页面反馈指出通用概念回答夹杂英文；原截图识别未取得可用结果，因此此前只保留为待验证假设。2026-08-20 在明确授权下，对修改前 HEAD 使用固定合成矩阵运行真实 Provider 基线：三个 EXPLANATION 档位各三次均未通过简体中文判定；CONVERSATIONAL 三次均通过。验收只记录语言、结构、句数桶、公开终局和耗时聚合，没有记录问题、回答、Prompt 或原始模型输出。

#### 源码根因

修改前 Goal Interpretation 与 General Knowledge 的 system prompt 只描述 JSON shape，没有约束 CONVERSATIONAL message、clarification prompt、statement text 和 caveats 的生成语言。确定性展示标题虽为中文，但正文完全由 Provider 自由生成。

#### 修复边界

- 生成文案固定使用简体中文，允许 JWT、PostgreSQL 等技术标识符；
- topic、subject、dimension、anchor、ID 和闭合枚举仍按请求精确回显，不翻译；
- 不新增模型调用、重试、日志正文或运行时 prompt 覆盖；
- 修复后真实 Provider 使用相同固定矩阵，每个 EXPLANATION 档位至少三次，语言门必须全部通过。

2026-08-20 修复后自动行为门使用相同 Provider 与固定矩阵通过：CONCISE、STANDARD、DETAILED 各三次，CONVERSATIONAL 三次，COMPARISON 一次；语言、结构、句数桶与公开终局全部通过。正文未输出或持久化。独立浏览器语义覆盖尚未确认，因此本项暂不删除。

### 4.10 A2-21：depth 未形成可执行的结构与篇幅差异

#### 运行证据

修改前真实 Provider 基线中，CONCISE、STANDARD、DETAILED 各三次均未落入目标结构与句数桶；部分 STANDARD/DETAILED 请求还未形成完整 ANSWER。聚合证据证明 `depth` 字段虽然存在于 typed request，但没有稳定控制最终可见回答。

#### 源码根因

- Goal prompt 只有固定 `STANDARD` 示例，没有从“简要/默认/详细”语义选择 depth 的规则；
- General prompt 只要求至少一条 DEFINITION 和一条 MECHANISM，没有句数与语义覆盖范围；
- Validator 不限制同角色重复和 DEFINITION/MECHANISM 顺序，展示层可能产生重复标题。

#### 修复边界

- Goal Interpretation 负责从开放表达提出 closed depth；后端继续验证闭合枚举；
- General prompt 把 CONCISE/STANDARD/DETAILED 冻结为 2、4—6、8—12 个主句；
- EXPLANATION 草稿只接受按顺序出现的一条 DEFINITION 和一条 MECHANISM；
- 修复后相同真实 Provider 矩阵必须同时通过公开终局、固定结构、目标句数桶与简体中文门。

2026-08-20 修复后矩阵中三个 EXPLANATION 档位均为三次 `ANSWER:COMPLETE`，观察到的输出桶分别稳定为 CONCISE、STANDARD、DETAILED。独立浏览器对典型用途、边界、权衡与误区的语义覆盖仍待确认，因此本项暂不删除。

## 5. 问题簇二：错误表达与来源上下文

### 5.1 A2-05：合同错误被显示为通用不可用

截图中后端明确返回 400 `VALIDATION_ERROR`，前端显示：

> Agent 暂时无法处理这条请求

该文案会让用户误判为 Provider、网络或整个 Agent 服务不可用。当前错误投影至少没有让用户区分：

- 请求/会话合同错误；
- 澄清已过期或重复消费；
- Provider 不可用；
- 网络异常；
- 系统内部错误。

公开错误仍须避免泄露内部字段与校验细节，但应保留稳定错误类别和可行动建议。

### 5.2 A2-06：来源栏的“当前”语义不准确

`activeSources` 会向后寻找当前会话最近一条 `ANSWER`，因此当前 Turn 是澄清、失败或 pending 时，右侧仍可能显示旧回答来源，并标为“当前回答来源”。

这不会改变事实安全性，但容易使用户认为旧来源支持当前澄清或失败轮次。后续需要在以下语义中明确选择一种：

- 明确写为“最近回答来源”；
- 当前 Turn 非 ANSWER 时置灰并说明来源属于上一回答；
- 切换 Turn 焦点时按被选中的 Answer 展示来源。

## 6. 问题簇三：多会话状态未隔离

### 6.1 A2-07：新对话后旧错误滞留

#### 用户现象

旧会话出现“Agent 暂时无法处理这条请求”后，点击“新对话”，中间消息区已经为空，但底部错误仍继续显示。

#### 源码根因

`AgentWorkspace` 中以下值是 Workspace 全局 `ref`，不是 session state：

- failure；
- pending；
- clearNotice；
- resumeNotice；
- questionDraft。

新建会话目前只创建 `AgentSession` 并清理活跃 ResumeToken，没有清理或重新绑定这些状态。模板又无条件渲染 `failure !== null`，没有检查 `failure.sessionId === activeSession.id`。

### 6.2 A2-08：pending 与 retry 可能跨会话操作

`PendingTurn` 和 `FailureView` 已经携带 sessionId，但 UI 渲染没有按当前活跃会话过滤。

由此产生的风险包括：

- 旧会话请求仍在执行时，新会话显示旧 pending 与“取消回答”；
- 在新会话点击取消，实际取消旧会话 requestId；
- 在新会话点击重试，实际使用旧 sessionId、requestId 和 command 重放旧请求；
- 任意会话的新请求会清空全局 failure，连带影响其他会话的错误状态。

这是跨会话行为错位，不只是视觉残留。

### 6.3 A2-09：草稿与通知可能跨会话

`questionDraft`、`clearNotice`、`resumeNotice` 同样不具备 session 归属。当前可能出现：

- 未发送草稿带入另一会话；
- 旧会话的 clear 失败通知显示在新会话；
- 恢复会话通知在切换后继续显示。

右侧来源已经按 `activeSession.messages` 计算，截图中能够在新会话正确清空；ResumeToken 也通过 watchEffect 跟随活跃会话。这两部分可以作为后续状态归属设计的参考。

## 7. 问题簇四：超时、取消与结果回收失配

### 7.1 A2-10：处理中状态直接消失

#### 用户现象

用户发送“你好”后，界面先显示“正在处理”，随后 pending 和取消按钮直接消失，只留下 USER 消息；没有回答，也没有错误。

#### 运行证据

同一时段后端三个真实 Goal Interpretation 请求最终均成功返回 HTTP 200，但耗时分别为：

- 215,972 ms；
- 183,324 ms；
- 172,687 ms。

安全诊断均记录：

```text
provider.call.completed
provider.operation=GOAL_INTERPRETATION
event.outcome=SUCCESS
```

前端固定 20 秒后 abort，因此在后端完成前已经停止等待。

#### 源码根因

`fetchWithTimeout()` 把两种来源合并到同一个 composite AbortController：

1. 用户主动点击取消；
2. 前端内部 20 秒计时器到期。

两者最终都表现为 DOM `AbortError`，并统一映射成 `AgentTurnFailure.kind = ABORTED`。`runTurn()` 对所有 ABORTED 都静默处理：清除 pending，不追加消息，也不显示 failure。

静默语义本应只属于用户主动取消，却同时吞掉了系统超时。

### 7.2 A2-11：前端超时没有取消后端 Active Turn

用户主动取消会先调用：

```text
DELETE /api/agent/turns/{requestId}
```

然后 abort 浏览器 fetch。

内部计时器超时只 abort fetch，不发送 DELETE。后端因此继续：

- 占用 Active Turn；
- 占用并发槽；
- 等待 Provider；
- 产生外部调用费用；
- 最终完成并保存一个前端不会接收的结果。

### 7.3 A2-12：后端绝对 deadline 没有覆盖完整模型调用

本地实例显式配置了：

- Goal timeout 12 秒；
- Model timeout 15 秒；
- Answer request timeout 30 秒；
- Semantic executionDuration 10 秒。

实际 Provider 请求却运行近三分钟。

当前 `StructuredModelTransport` 在 `HttpRequest` 上设置 timeout，并同步调用 `HttpClient.send(...BodyHandlers.ofString())`。现有运行证据表明该超时没有可靠覆盖完整响应体读取。

同时，Lifecycle 的 `TurnDeadline.after(executionDuration)` 只在 Goal 已解析、进入 `SemanticTurnEngine` 后创建；Goal Interpretation 位于该 Turn execution deadline 之前，因此不受这条绝对期限约束。

后续必须验证并覆盖：

- 建连超时；
- 等待响应头超时；
- 响应体读取停滞；
- Goal decode/validation 时间；
- 用户取消信号；
- 整个 Turn 的 absolute deadline。

### 7.4 A2-13：跨端预算顺序冲突

当前主要预算为：

```text
Frontend request timeout：20 秒
Backend answer request timeout：30 秒
Model timeout：15 秒
Goal timeout：12 秒
```

这些值没有形成明确的单调关系。即使单次 Provider timeout 正常生效，需要 Goal + General 两次模型操作的 Turn 也可能超过前端 20 秒，导致前端先放弃而后端仍认为请求合法运行。

修复前需要冻结一条跨端预算原则，例如：内部 operation deadline < Turn absolute deadline < 客户端等待上限，并为网络余量保留明确空间。具体数值必须通过真实 Provider 延迟分布决定，不能只调整一个常量。

### 7.5 A2-14：已完成结果无法自动回收

后端在前端断开后仍可能完成 requestId，并写入幂等 replay 快照。冻结修订后，确定性 Portfolio Turn 可取回原终局；Provider 派生的 General/Conversational 正文不保存，同 requestId 只能取回 `REPLAY_BODY_NOT_RETAINED`，用户必须重新提问。当前前端超时后：

- 不显示超时；
- 不保留重试入口；
- 不使用相同 requestId 重放；
- 不查询该 requestId 是否已完成；
- 用户再次输入会产生新的 requestId。

因此原行为形成“后端已有答案、用户界面永久丢失”的状态。修复必须复用现有 requestId/Replay 权威，不能新增第二套结果查询状态机；隐私边界优先于 Provider 正文恢复，不得用进程内暂存或加密正文建立第二结果权威。

### 7.6 A2-15：重新打开页面后的“正常”不是旧结果恢复

#### 用户现象

同一个“你好”请求在当前页面先经历 pending 消失；重新打开 Agent 页面并再次操作后，很快出现正常的 Clarification Turn，因此看起来像刷新恢复了此前答案。

#### 运行证据

重新打开页面时，日志先记录一组静态资源与公开内容请求；随后出现一条新的 Goal Interpretation Provider 调用：

```text
provider.operation=GOAL_INTERPRETATION
event.outcome=SUCCESS
http.status_code=200
duration.ms=1881
```

它与此前 172—216 秒后才完成的三条调用不是同一次执行。

#### 源码判断

当前刷新恢复路径只从 sessionStorage 读取 ResumeToken，并通过 `GET /api/agent/conversations/current` 恢复 conversationId/status。该接口不返回历史消息或 Completed PublicAgentTurn；`useLocalSessions()` 也不会跨完整页面重载持久化消息。

因此现有证据不支持“页面重新打开后自动取回旧 requestId 结果”。更准确的解释是：

1. 页面重新加载清除了旧 Workspace 内存中的 pending/failure/损坏窗口；
2. 用户再次提交后产生了新的 Provider 调用；
3. 新调用只耗时 1.881 秒，落在前端 20 秒窗口内；
4. 新结果正常显示，从体验上掩盖了上一请求仍在后端长时间运行且无法回收的问题。

这说明故障具有明显的时序和 Provider 延迟波动特征。重新打开页面只是偶然绕过状态污染和慢调用，不是可靠恢复策略，也不能作为问题关闭依据。

## 8. 自动化为何没有发现

现有门禁覆盖了大量单点合同，但没有覆盖这些真实跨轮组合。

### 8.1 Frontend 单元测试缺口

当前测试覆盖：

- FREE_TEXT 提交与正常两轮 window；
- RESOLVE_CLARIFICATION command 的字段形状；
- API failure 与相同 requestId 重试；
- 主动取消；
- clear 与 ResumeToken。

缺少：

- 澄清答案写入 USER 轮次；
- `CLARIFICATION -> RESOLVE -> CLARIFICATION/ANSWER -> 下一轮` 的 window；
- 请求失败后本地消息是否进入下一窗口；
- failure 后新建/切换会话；
- pending 时新建/切换会话；
- 新会话不得取消或重试旧会话请求；
- timeout 与 user cancel 的不同 UI 语义；
- timeout 后使用同 requestId 恢复结果。
- 页面重新打开后不得通过新 requestId 假装恢复旧请求；测试必须区分 replay 与重新执行。

### 8.2 Backend 单元/集成测试缺口

ClarificationStore 测试证明了短 TTL、一次消费与 binding 校验，但没有证明消费后能够恢复原始 blocked Goal。Lifecycle 也缺少“原推荐 Goal + 澄清答案 -> 同一推荐 Goal Proposal”的完整断言。

模型 Transport 测试没有覆盖 Provider 已返回响应头但响应体长期不完成、外层 deadline 触发和取消传播。

### 8.3 Browser E2E 与真实 Provider canary 缺口

修复前的 packaged-JAR E2E 只覆盖 preset/replay/Bearer/clear、closed Turn UI 与 cancel requestId 目标隔离；当时的取消用例分别发生在 POST 尚未进入后端、以及后端已结算之后，不能证明 active cancel、cancel-wins 或迟到结果抑制。补充的慢 Provider active-cancel 证据见 §10.6；真实 Provider 仍未执行。

此前真实 Provider canary 是单轮、快速的稳定通用问题；它证明真实 Provider 可连接和结构输出可解析，但不能证明长延迟、跨轮澄清和多会话状态正确。

## 9. 修复边界

后续修复必须遵守已经批准的 Agent 2.0 架构，不重新讨论或扩张系统：

1. 保持唯一 `AgentTurnLifecycleService`、GoalResolver、SemanticPlanCompiler、SemanticTurnEngine 与 PublicAgentTurn Projector；
2. 保持无版本 `/api/agent/turns` 与 `/api/agent/conversations/current` 四条资源；
3. 保持 `ASK | CONTINUE | RESOLVE_CLARIFICATION` closed commands；
<!-- RETIRED_CONTRACT_REFERENCES:BEGIN -->
4. 不恢复旧 Router、Confirmation、stp-v1/v2/v3、ConversationAnswer DTO 或兼容桥；
<!-- RETIRED_CONTRACT_REFERENCES:END -->
5. 澄清状态继续短 TTL、一次消费、绑定 Conversation/ResumeToken/ContentReleaseId，并受加密 Agent State 管理；
6. 不保存完整访客问题、Prompt、模型原始输出或长期聊天记录；
7. 超时恢复复用 requestId 与现有 replay authority，不建设第二个结果缓存；
8. 模型不能决定 Task、DAG、Provider 或扩大公开主体；
9. 错误分类可以更准确，但不得向前端泄露内部字段、栈、Provider 响应或安全绑定。

## 10. 建议修复批次

本节只定义问题边界和依赖顺序，不代表具体实现已经批准。

### Batch A：澄清权威闭环

- Clarification State 保存恢复 blocked Goal 所需的最小、typed、加密绑定；
- RESOLVE 合并答案到原 Goal，而不是把答案文本重新当作独立问题；
- 推荐数量、goalKind、公开主体、输出与闭合约束在澄清前后保持不变；
- 本设计显式取代“恢复原始 input anchor”的字面要求：状态只保存隐私安全的 typed Goal，恢复时由服务端生成固定语义锚点与 goalKey；
- 明确 recommendation 在公开候选域下何时允许直接执行、何时必须澄清。

### Batch B：Frontend 轮次与会话状态隔离

- 澄清答案形成明确 USER 轮次；
- conversationWindow 只由合法、已提交的轮次生成；
- failure、pending、draft、notice 明确归属 session 或 workspace；
- 所有取消、重试和渲染按 active sessionId 校验；
- 定义 pending 时切换会话的产品行为。

### Batch C：端到端 deadline、取消与回收

- 区分 user cancel、client timeout、network abort；
- client timeout 必须显示可理解且可恢复的状态；
- 冻结跨端 deadline 顺序；
- Provider 调用增加覆盖完整响应体的 absolute deadline；
- Goal Interpretation 纳入 Turn 总预算和取消传播；
- timeout 后按相同 requestId 恢复或重放最终结果。

### Batch D：错误与来源体验

- 将公开错误收敛为稳定、可行动的类别；
- 修正“当前回答来源”的时间语义；
- 确保错误、来源和 pending 均与当前 Turn/Session 对齐。

### 10.5 修复进展（2026-08-19 前端批次）

前端责任区的生产修复已完成本地验证。以上条目在通过 §12 Exit Gate 前保持「修复后待验收」，不从本文删除。已实现内容：

1. **Batch B 前端（A2-03/04/07/08/09）：** pending、failure、draft、notice 全部归属 session；取消/重试/渲染按活跃会话过滤；pending 允许跨会话并存，结果回流原会话；澄清答案记为 USER 轮次（CHOICE 显示公开选项标签、TEXT 显示原文）；失败/取消 USER 轮次标记 `failed`、排除出 conversationWindow，同 requestId 重试成功后解除标记。
2. **Batch D（A2-05/06）：** 新增 `turnFailureProjection`，按冻结错误码/HTTP 状态把失败投影为 SESSION_EXPIRED、CONVERSATION_MISMATCH、TURN_CONFLICT、SERVICE_UNAVAILABLE、RATE_LIMITED、CONTRACT_INVALID、TIMEOUT、NETWORK、UNKNOWN 稳定类别，各配行动建议，仅可恢复类别提供同 requestId 重试；来源栏按最近 Turn 语义显示“当前回答来源/最近回答来源”并在 stale 时弱化。
3. **A2-10 前端部分：** 传输层区分内部计时器超时（`TIMEOUT`）与用户主动取消（`ABORTED`）；前端等待上限已从临时 20 秒切换为冻结的 25 秒，超时显示明确状态与同 requestId 重试入口，用户取消保持静默。后端绝对 deadline（A2-12/13）已实现，仍待完整 Exit Gate 验收。
4. **A2-18：** 澄清挑战卡引入 ACTIVE/CONSUMED/SUPERSEDED 生命周期；提交即把原卡转只读摘要，历史卡一律只读，仅最新未消费卡可操作，pending 期间全部禁用。
5. **交互恢复（重构中被简化、按现行合同重建）：** 滚动纪律（上滑停止自动跟随 + “回到最新回答”）；来源面板“定位”入口跳转并高亮回答内引用该来源的 section（`sectionId` + `publicSourceKeys` 推导，纯前端）；澄清卡在无后端建议时以已发布 QuestionPreset 作为脱困入口——叶子组件只渲染上层传入的已发布预设（presetId 走 PRESET 命令），前端不自造业务问题（§11 第 6 项确认后修订）。
6. **§11 第 1 项冻结后的补充实现：** 同一标签页合计 pending 上限 2（与后端来源级最大并发 2 对齐），超出时不发请求、仅输入区提示；上限作用于 FREE_TEXT、Preset、SuggestedAction、澄清提交与失败重试全部新轮次入口。

### 10.6 跨端验收进展（2026-08-19）

已将 packaged-JAR 和 Live Provider 脚本从旧版本化 Agent 合同迁移到四条最终无版本资源，请求使用闭合 Command，响应按根级 `PublicAgentTurn` 断言。当前进展：

- DEFAULT（显式 IN_MEMORY）的桌面/移动端最终合同、会话隔离、取消 requestId 目标隔离、澄清恢复、同 requestId replay 和来源语义已通过 packaged 验收；该结果尚不等于 active cancel 已验收；
- ADMISSION 使用独立低 RPM JVM 配置，双通道 429 与前端倒计时已通过；
- DEPTH_TWO 使用独立 JVM/浏览器项目，验证“产生 Challenge → 提交答案 → 恢复原 Goal”两阶段链路；当前生产模型仍是单轮澄清，不把未实现的第二轮伪装为已验收；
- CONTENT_ONLY 通过公开 `agentAvailability` 中性投影隐藏提交界面，公开内容仍可浏览，直接 POST 继续以 `AGENT_STATE_UNAVAILABLE` 失败关闭；
- BODY_STALL 在不增加任意生产 endpoint override 的前提下已通过 packaged 验收：测试 JVM 使用临时 hosts/truststore 将固定审核 Provider 主机映射到只接收固定假凭据的本地 HTTPS fixture；fixture 返回响应头和部分正文后停滞。浏览器等到 `ACTIVE:1` 后对精确 requestId 发送 DELETE 并得到 204，fixture 随后观测到 `CLOSED:1`，页面无迟到 PublicTurn 且 Provider 请求计数仍为 1；临时证书、hosts、truststore、端口与进程均已清理；
- 标准 PostgreSQL packaged lane 已使用仓库外一次性 EnvFile 和独立验证库完成 context V3 迁移；临时文件已在 `finally` 删除，未改写用户的 `.env.postgres.local`。首轮验收暴露的 `requestedSize: null` 解码问题已修复，最终桌面/移动端矩阵通过；
- LIVE 脚本已迁移最终合同并通过 Fake Backend 自测，本轮未获得真实 Provider 执行授权，因此未运行。
- 旧 model-led canary 与旧 Provider response checker 已在旧 Answer 表面清理中删除或退出最终验收路径；

真实 Provider 仍未获得本轮执行授权；依赖该证据的问题继续保持「修复后待验收」，不提前删除。

### 10.7 Provider Transport 收敛进展（2026-08-24）

- **A2-79：** DeepSeek 与 GLM 不再由 Transport 内的一份固定模板隐式共享协议；两者拥有独立、带版本的 `ModelProviderProtocolProfile`。按 2026-08-24 官方协议，两家当前批准字段都包含 `response_format={type:json_object}`、`thinking={type:disabled}` 与 `stream=false`，因此 Profile 当前可生成相同字段，但任何后续变化必须各自修改并通过各自 payload 门，不能扩散到另一 Provider。本批没有引入多模型路由。
- **A2-82：** 非 2xx 已稳定区分为 `AUTHENTICATION_REJECTED`（401/403）、`RATE_LIMITED`（429）、`PROVIDER_UNAVAILABLE`（5xx）和 `PROVIDER_REJECTED`（其他 4xx）；诊断只保留 closed code/layer 和耗时桶，不记录 Provider response body。
- **A2-83：** HTTP/连接失败属于 `TRANSPORT`，非法 JSON 属于 `JSON`，Chat Completions 外层形状错误属于 `ENVELOPE`。后续批次已补齐 operation 边界：Goal/General Codec 拒绝记为 `SCHEMA/OUTPUT_SCHEMA_REJECTED`，General Validator 与 Goal typed scope 拒绝记为 `SEMANTIC/OUTPUT_SEMANTIC_REJECTED`。Provider call 成功与 output reject 分为两个事件，不把结构闭合作为回答成功。
- **A2-84：** `BodyHandlers.ofString()` 已替换为读取期有界 subscriber，UTF-8 响应体超过 256 KiB 即取消订阅并返回 `RESPONSE_TOO_LARGE`；原 absolute deadline 仍覆盖等待响应头和完整响应体，body-stall 与线程中断取消回归继续通过。
- **专属门：** `OpenAiCompatibleStructuredModelTransportProtocolTest` 覆盖两个 Provider 的完整字段集合、六类 HTTP fixture、JSON/envelope diagnostics、response body sentinel 不进入 diagnostics，以及 256 KiB + 1 拒绝；`OpenAiCompatibleStructuredModelTransportDeadlineTest` 覆盖 body stall 与中断取消。本地门只证明 Transport 行为，不替代 A2-80/81/87/88 的真实 Provider schema、P95、独立矩阵和样本量。
- **本批验证证据：** 2026-08-24 最终源码的后端 `clean package -DskipFrontend=true` 为 916 tests、0 failures、0 errors、4 skipped；code-quality、architecture、documentation 通过，privacy 扫描 501 个生产文件通过；最终 packaged JAR SHA-256 为 `7bba720235e76e44e1403aae04c7331a574db4c05c2208d10c31fe9e43b6a67d`。本批未运行真实 Provider 或 Browser，因此整体和相关条目继续保持 `IN_PROGRESS`。
- **范围：** 本批没有修改公开 API、公开错误 variant、Provider 选择或 Frontend。A2-80/81/85—88 未取得新的真实样本或产品冻结决定，状态不变。

### 10.8 后端幽灵能力与配置清理（2026-08-24）

- **A2-37/103/104：** 生产 `PortfolioTaskExecutor` 始终使用四参数构造，`PortfolioFactExpressionPort` 没有实现，Compiler 只被孤立单测调用。选择冻结边界允许的“物理删除”：移除端口、编译器、可选执行分支、孤立测试、`portfolio-expression-timeout` 配置和预算校验；不借清理接入新的模型表达调用。
- **A2-105/106：** `max-history-rounds`、`recent-raw-rounds`、`max-input-tokens` 与 `max-suggested-questions` 在后端只有绑定字段和 getter/setter，生产零读取；后端伪权威全部删除。suggested question 的页面数量、布局与交互仍由 Frontend Agent 负责，本批不修改 Frontend，也不据此关闭 A2-106 的前端验收。
- **A2-107：** Operation readiness 删除未使用且过强的 deterministic/fallback/provider-unavailable 名称，只保留 `DISABLED`、`AVAILABLE_WITH_PROVIDER` 与 `INCOMPLETE_CONFIGURATION`；该状态仍只描述配置 readiness，不声称回答成功。
- **A2-108：** 全生产源码逐类引用核实后，`PortfolioSelectionResult`、`SectionedTaskPresentation`、`QuestionStatus` 只有定义自身，无 Spring/Jackson/反射注册，也无生产消费者，已物理删除；活跃的 Selection、Presentation 与 Question 类型不做名称驱动清理。
- **A2-109：** Goal 与 General 两个 Provider 输出 Codec 都启用 `FAIL_ON_TRAILING_TOKENS`，并以合法首个对象后追加第二个 `{}` 的负例证明失败关闭。
- **本批验证证据：** 2026-08-24 后端 `clean package -DskipFrontend=true` 为 916 tests、0 failures、0 errors、4 skipped；code-quality、architecture、documentation 通过，privacy 扫描 496 个生产文件通过；删除符号与配置键在 `backend/src/main + backend/src/test` 为零引用；packaged JAR SHA-256 为 `619dced1bee3a3834c2bc77aa22dcabe4ee6da4bf07e05cab63c1669d0306f20`。本批未运行 Frontend、Browser 或真实 Provider，整体保持 `IN_PROGRESS`。

### 10.9 Portfolio 与 Cross-domain 表达收敛（2026-08-24）

- **A2-38：** Portfolio Fact 不再按每个 Claim 机械生成一张 section。同一 `AnswerSectionType` 的事实按公开证据顺序合并为一个叙述块，section 同时聚合所有实际使用的 `publicSourceKeys`；CONCISE/STANDARD/DETAILED 的 section 上限和 detail 消费保持由后端 depth 控制。单一事实仍原样投影，避免服务端连接词制造额外事实。
- **A2-50：** Cross-domain 保持一个 General 与一个 Portfolio typed 结果的严格 fan-in，但关系段不再使用固定“上述事实说明应用”句。后端选取 General `MECHANISM`，再按每条公开 Claim 的闭合 category 映射为背景条件、职责、方案选择、实现、验证、结果或边界关系；关系句只组合已验证 statement，不引入新事实。General caveat 同时进入“适用边界”，不再在综合回答中静默丢失。
- **专属门：** `PortfolioPresentationComposerTest` 证明同 section 两条事实只形成一个叙述块且保留两个来源；`CrossDomainTaskExecutorTest` 证明 mechanism、IMPLEMENTATION 对应关系与 caveat 均出现在公开 presentation，并拒绝旧固定关系句。Provenance 与 support isolation 回归继续通过。
- **范围：** 本批没有模型表达调用、公开 DTO 或 Frontend 改动。确定性测试不能证明真实 Provider 生成的概念机制质量或 Browser 正文可读性，A2-38/A2-50 和整体继续保持 `IN_PROGRESS`。
- **本批验证证据：** 2026-08-24 后端 `clean package -DskipFrontend=true` 为 917 tests、0 failures、0 errors、4 skipped；code-quality、architecture、documentation 通过，privacy 扫描 496 个生产文件通过；packaged JAR SHA-256 为 `1331454b9d479b26945dcddc5ebc04f82f4c958535c57e7ae028864b9c63433b`。未运行真实 Provider 或 Browser。

### 10.10 Provider output diagnostics 分层（2026-08-24）

- `provider.call.completed` 只说明 HTTP/envelope transport 成功，不再吞并后续回答合同结论；Codec 或 Validator 拒绝另发 `provider.output.rejected`。
- Goal/General Codec 的 JSON 后操作 schema 拒绝固定为 `SCHEMA/OUTPUT_SCHEMA_REJECTED`；General 内容/请求一致性 Validator 与 Goal `SemanticRouteValidator` 的 typed scope 拒绝固定为 `SEMANTIC/OUTPUT_SEMANTIC_REJECTED`。Transport 自身的 `TRANSPORT/JSON/ENVELOPE` 层保持不变。
- 新 diagnostics 只允许 `provider.operation`、`failure.layer`、`failure.code` 三个 closed 字段；不记录异常类型/消息、Provider body、Prompt、访客输入或模型正文，且 publisher 失败不改变业务行为。
- `GeneralModelOutputDiagnosticsTest` 分别驱动 schema/semantic 拒绝；`GoalInterpretationAdapterTest` 与 `GoalResolverTest` 分别驱动 Goal schema 与 typed semantic 拒绝，并用 Provider body/访客 sentinel 证明事件无原文。真实 Provider 的各层发生率仍需 A2-80/87/88 矩阵取得，A2-83 和整体保持 `IN_PROGRESS`。
- **本批验证证据：** 2026-08-24 最终源码的后端 `clean package -DskipFrontend=true` 为 920 tests、0 failures、0 errors、4 skipped；code-quality、architecture、documentation 通过，privacy 扫描 497 个生产文件通过；最终 packaged JAR SHA-256 为 `faf356244c75974f46fd2c2fb2fe2d7f6b61728503ab651f61ccccf589894e89`。实现初稿把 diagnostics helper 放入 execution 包时被现有模块依赖门拒绝，移至 `common.observability` 后最终 architecture 门通过；未放宽架构规则。本批未运行真实 Provider 或 Browser，故不形成发生率或终端可用性结论。

### 10.11 Provider repair 与 fallback 产品边界（2026-08-24）

- **A2-85：** 选择保持当前单次调用合同。Goal/General 的 codec 或 validator 拒绝是本轮终局，不把被拒正文再次放入 Prompt，不发送 repair 请求，也不以同一 Provider 重试；配置 `maxModelAttempts != 1` 继续失败关闭。
- **A2-86：** 运行实例只持有启动时选择的一个 Provider descriptor 和一个 Transport。HTTP、JSON、envelope、schema、semantic 或 timeout 失败不自动发送给另一 Provider。未来用户明确切换 Provider 属于新 Turn、新 requestId 和独立产品能力，不在本批建设。
- **专属门：** `GeneralKnowledgeGeneratorTest` 与 `GoalInterpretationAdapterTest` 对 schema 拒绝断言 Provider 调用数严格为 1；`OpenAiCompatibleStructuredModelTransportProtocolTest` 对 HTTP/JSON/envelope 失败断言 HTTP 请求数严格为 1，并证明 payload model 始终等于启动选定 Provider。以上关闭产品决策缺口，不替代 A2-80/81/87/88 的真实 Provider 独立质量矩阵。
- **本批验证证据：** 2026-08-24 最终源码的后端 `clean package -DskipFrontend=true` 为 920 tests、0 failures、0 errors、4 skipped；code-quality、architecture、documentation 通过，privacy 扫描 497 个生产文件通过；最终 packaged JAR SHA-256 为 `677fc3d9aba3201290315ef1ec4ff7883d07153672bcf61572ed712f84bde8ee`。本批未运行真实 Provider 或 Browser，整体继续保持 `IN_PROGRESS`。

### 10.12 Provider Registry 证据语义收窄（2026-08-24）

- **A2-113：** Registry 只负责启动配置目录，不再用 `supports`、`supported*` 或 `ModelProviderCapability.STRUCTURED_JSON_OUTPUT` 表述外部系统事实。生产 API 收窄为 `isApprovedConfiguration`、`approved*Versions` 和 `ModelProviderRequestFeature`；三个 feature 只描述 Adapter 将发送 JSON-object、thinking-disabled、non-streaming 请求。
- Registry 通过只形成 `CONFIGURED` 级结论。Transport fixture、真实 schema canary 和质量矩阵分别形成 Transport、Schema、Quality 证据，前一层不得推导后一层；当前 DeepSeek/GLM 的真实结论不因本次重命名改变。
- **专属门：** Descriptor/Registry tests 断言批准配置正反例、不可变 request features，以及生产接口不存在 `supports`、`isSchemaVerified` 或 `isQualityVerified` 强声明。真实 Provider A2-80/81/87/88 继续保持 `IN_PROGRESS`。
- **本批验证证据：** 2026-08-24 最终源码的后端 `clean package -DskipFrontend=true` 为 920 tests、0 failures、0 errors、4 skipped；code-quality、architecture、documentation 通过，privacy 扫描 497 个生产文件通过；最终 packaged JAR SHA-256 为 `765377d375b78b961eb1f918727d2700ec85fa06778f891652a6f3d41eb1a370`。本批未运行真实 Provider 或 Browser，故不提升任何外部验证层状态。

### 10.13 真实 Provider 独立失败矩阵与 runner 修复（2026-08-24）

- **证据门修复：** General quality runner 不再在首个错误终局后中止；每个 scenario 现在始终报告 trials、language/structure/depth/terminal 比率、timeout 比率、公开终局分布和 P50/P95。LIVE packaged runner 跳过随机的单次推荐前置断言，先收集完整矩阵，再统一失败；应用停止后只从 ECS 日志聚合 closed `event/operation/layer/code/duration/count`，正文和输入不进入输出。原“Live Provider verification passed”已收窄为“social public-turn probe completed”，因为快速社交路径不证明 Provider 被调用。
- **DeepSeek 独立运行：** packaged JAR SHA-256 `765377d375b78b961eb1f918727d2700ec85fa06778f891652a6f3d41eb1a370`、独立 JVM/端口、`IN_MEMORY`。CONCISE/STANDARD/DETAILED 各 3 次、COMPARISON 1 次，共 10 个实际 Goal Provider 调用，公开终局均为 `CAPABILITY_UNAVAILABLE/SEMANTIC_ROUTING_UNAVAILABLE`；closed diagnostics 为 `provider.call.failed/GOAL_INTERPRETATION/TRANSPORT/PROVIDER_REJECTED`，耗时桶 100–499ms 8 次、500–1999ms 2 次。timeout 为 0/10，但没有成功路径样本，不能据此冻结 A2-81。CONVERSATIONAL 3/3 成功且 P95 22ms，但没有对应 Provider diagnostics，属于服务端快速路径，明确不计入 Provider 成功率。
- **GLM 独立运行：** 使用同一 JAR 在独立 JVM/端口启动；仓库外 Secret 文件声明了 GLM key 变量但值为空，生产 readiness 失败关闭，social probe 报 `PROVIDER_RESPONSE_INVALID`，quality gate 报 `GENERAL_QUALITY_CONFIG_INVALID`，没有发送真实 GLM 请求。该结果只证明配置阻塞被诚实报告，不构成 GLM Transport/schema/quality 证据。
- **状态：** A2-82 获得真实失败类别分布；A2-80/81/87/88 仍为 `IN_PROGRESS`。DeepSeek 成功率为 0、GLM 未获可用凭据时，禁止把矩阵或整体标为 PASS。
- **本批验证证据：** `assert-live-general-answer-quality.test.ps1`、`run-jar-e2e.test.ps1`、`run-agent-behavior-audit-assets.test.ps1` 通过；code-quality、architecture、documentation 通过，privacy 扫描 497 个生产文件通过。真实矩阵使用本批最终 Backend 源码生成且已通过 920 tests、0 failures、0 errors、4 skipped 的同一 packaged JAR；本批只修改验证脚本与文档，JAR SHA-256 保持 `765377d375b78b961eb1f918727d2700ec85fa06778f891652a6f3d41eb1a370`。

### 10.14 退役模型配置键清理（2026-08-24）

- **A2-102：** `run-eval.ps1`、`run-eval-offline.ps1` 与 `run-jar-e2e.ps1` 不再写入无消费者的 `portfolio.model-expression.*`，统一改为 `portfolio.conversational-model.*`。`run-agent-behavior-audit.ps1` 不再写入 `PORTFOLIO_MODEL_EXPRESSION_ENABLED` 或错误的 `PORTFOLIO_AGENT_MODEL_PROVIDER`，统一消费 `PORTFOLIO_MODEL_ENABLED` 与 `PORTFOLIO_MODEL_PROVIDER`。
- 新 `current-model-config-surface.test.ps1` 扫描所有非测试 PowerShell 资产，禁止退役 property/env 前缀，并要求四个 runner 显式使用当前权威；仓库 `scripts + backend/src/main + backend/src/test` 对退役字面量零命中。Eval、offline Eval、packaged JAR、start-local、privacy 与 behavior assets 专属测试通过。
- 原 `run-agent-behavior-audit.test.ps1` 仍要求不存在的 Frontend `test:e2e:behavior`/Playwright projects，与已通过的 `run-agent-behavior-audit-assets.test.ps1`（要求现存 `test:e2e` 且禁止这些死亡资产）互相冲突；该文件已有 Frontend Agent 工作区修改，本批不覆盖、不暂存。此冲突继续归 A2-89/A2-90 前端验收资产清理，不否定 A2-102 的配置零残留证据，也不得被隐藏为全 runner PASS。
- （2026-08-24 闭环）Frontend 工作区的旧 runner 测试已同步为禁止死亡资产并真实通过；空 behavior 目录、`vitest.behavior.config.ts` 与 Playwright `**/behavior/**` testIgnore 已删除，assets 元测试新增"退役资产保持删除"守卫。A2-89/A2-90 关闭，完成行为记入演进日志当日条目。

### 10.15 真实 Provider 诊断与 GLM 基线校正（2026-08-24）

- **A2-82：** 官方协议核验确认 `deepseek-v4-flash`、`/chat/completions` 和 non-thinking JSON request 均有效；仓库外 DeepSeek 凭据的最小安全探针实际返回 HTTP 402。Transport 新增 `BILLING_REJECTED`，不再把账单状态混入 `PROVIDER_REJECTED`；测试继续用 response-body sentinel 证明错误正文不进入异常或 diagnostics。
- **A2-80/87：** 仓库外 GLM 凭据对与生产相同的 `glm-4.7` structured request 返回 200 和单一 choice/content envelope。LIVE runner 不再依赖调用者另行设置六个 operation env，而是在已授权 LIVE lane 内把 Goal/General operation 与 schema 显式绑定到启动时选定 Provider；第一次 GLM 运行因 operation 默认 `DISABLED` 形成的 0 调用结果已作废，不计入任何 Provider 证据。
- **A2-81/88：** paced GLM packaged 基线（CONCISE/STANDARD/DETAILED 各 3、COMPARISON 1，另有 3 个不调用 Provider 的社交快速路径）记录 Goal completed 9、Goal deadline 1、General completed 7、General deadline 2、General semantic reject 3。CONCISE 3/3 通过，公开 P95 11635ms；STANDARD 0/3，P95 12328ms；DETAILED 1/3，P95 15594ms；COMPARISON 在 Goal 8029ms 处 deadline。成功样本证明 GLM Transport/envelope 可用，但 timeout 与质量均未稳定，不能冻结 operation 预算或关闭总门。
- **A2-83：** `provider.call.completed` 从 prod 不可见的 DEBUG 调整为 INFO，但仍只含 closed operation/outcome/duration/response-present。General Validator 使用 typed rejection reason；`provider.output.rejected` 可增加 `TOPIC_MISMATCH`、`EXPLANATION_ROLE_ASPECTS_INVALID` 等固定枚举，不包含正文或异常消息。单个 packaged STANDARD Turn 已实际观测到 `EXPLANATION_ROLE_ASPECTS_INVALID`，定位出 Prompt/Validator 合同漂移。
- **General 合同修复：** Prompt 现在明确要求 DEFINITION statement 自身包含 `DEFINITION` aspect、MECHANISM statement 自身包含 `MECHANISM` aspect，并继续要求按 depth 的联合集合精确闭合。更新后的生产 Prompt 通过一次真实 GLM 不落盘安全探针：topic、角色、每项句数、逐角色 aspect 与联合 aspect 均满足；该单次探针不替代多样本 packaged/browser 门。
- **验收节奏：** quality runner 只在不同 requestId 的独立测试 Turn 之间等待 10 秒，避免 Provider 429 污染基线；单个 Turn 内仍严格一次 Goal、一次 General，不 repair、不重试、不 fallback。Baseline 继续完整收集后失败，不把部分成功、HTTP 200 或结构闭合升级为总 PASS。
- **本批可复验证据：** 最终源码执行 `mvn clean package -DskipFrontend=true` 为 920 tests、0 failures、0 errors、4 skipped；`run-jar-e2e.test.ps1` 与 `assert-live-general-answer-quality.test.ps1` 均通过；code-quality、architecture、documentation 与 privacy 门均通过（privacy 扫描 498 files、0 archives）。最终 packaged JAR SHA-256 为 `1bc4564b6a90cee164f281e67a14546e7856519dda7341523d37498840167f6b`。本批未执行浏览器门，不能据此升级完整验收状态。
- **范围与状态：** 本批不修改公开 API、公开错误 variant、Provider 路由或 Frontend。DeepSeek 当前受外部 402 阻塞；GLM 的 STANDARD/DETAILED/COMPARISON 与 deadline 未全部通过，所以 A2-80/81/87/88 和整体继续 `IN_PROGRESS`。

### 10.16 配置化 GLM/Qwen 目录后端 Replacement Slice（2026-08-24）

本节是当前生产模型权威；本节之前关于 DeepSeek、`conversational-model`、built-in Registry、部署级单 Provider 与旧 GLM model ID 的阶段证据，只能说明替换前行为，不再构成当前目录的 Provider 通过证据。

- **三层准入：** 旧 global Provider、visitor access 与 Operation `provider-ref` 门已删除。模型执行只经过 `model-runtime` 总开关、每个 Provider 的 `enabled + credential + data-policy-approved`、以及 Goal/General 各自的 Operation mode/schema/max-output/timeout。`selectable=false` 且通过 Provider 准入的条目只保留在服务端内部 canary 索引，不进入公开 Snapshot，也不能由访客 Turn 解析。
- **目录与协议：** `ConfiguredModelCatalog` 从启动配置冻结 GLM `glm-4-7-flash -> glm-4.7-flash` 和 Qwen `qwen-3-7-flash -> qwen3.7-flash`；两个闭合 Profile 分别控制 thinking-disabled 请求形状，共享 Transport 只接受服务端 Binding。DeepSeek、厂商枚举目录、built-in Registry、旧 namespace/env 和全局 Provider 选择已退出生产路径。
- **Turn 权威：** 所有 Command 根级显式携带 `MODEL(modelRef + selectionVersion)` 或 `NONE`，并进入请求指纹。生命周期先 Claim/Replay，只有新 Claim 才校验当前 Catalog 并冻结一个执行快照；Goal Interpretation 与 General Knowledge 显式使用同一选择，不读取全局默认，不 repair、不自动重试或跨 Provider 重发。
- **公开与失败合同：** `/api/portfolio` 只公开安全目录版本、默认选择和可选条目；每个 `PublicAgentTurn` 原子携带 `modelExecution`。`MODEL_SELECTION_STALE`、`SELECTED_MODEL_UNAVAILABLE`、`SELECTED_MODEL_TEMPORARILY_UNAVAILABLE`、`SELECTED_MODEL_RATE_LIMITED`、`SELECTED_MODEL_INVALID_RESPONSE` 均形成可回放的 HTTP 200 settled `CAPABILITY_UNAVAILABLE`；结构、认证、指纹冲突与入口限流继续使用非 settled HTTP 错误。
- **离线证据：** 后端 Slice A 已完成离线实现，Backend clean package 的 Surefire 结果为 `963 tests / 0 failures / 0 errors / 4 skipped`；生产源码 privacy check 通过，架构 checker 明确报告 `overall=IN_PROGRESS` 且无 deferred item，Eval、offline Eval 与 packaged-JAR 三个生产 runner 自测均通过。该证据覆盖目录、协议、显式选择、单 Turn 单模型、五错误码、Settlement/replay、`modelExecution` 与新配置脚本面，不替代真实 Provider 或前端合同消费。
- **真实 GLM canary：** 使用仓库外 Secret 启动 `glm-4-7-flash` 配置化目录，公开 Catalog 正确投影所选模型，真实请求进入 Provider 后结算为 `SELECTED_MODEL_RATE_LIMITED`。固定无用户数据直连 canary 返回 HTTP 429、业务码 1305“该模型当前访问量过大”，对应智谱平台服务过载而非余额不足；后续一次应用调用在 Goal 的 8 秒预算附近结算为 `SELECTED_MODEL_TEMPORARILY_UNAVAILABLE`。这些证据证明真实路由与错误映射，不构成成功 schema/semantic 或质量 PASS。
- **仍开放：** GLM-4.7-Flash 与 Qwen3.7-Flash 的真实同构矩阵均未完成，Qwen 调用配置等待用户提供；A2-80/81/87/88 与 Slice A A9 外部门保持 `IN_PROGRESS`。本轮不处理前端模型选择 UI、会话偏好、pending 锁定或换模型交互，也不据后端离线结果关闭前端条目。当前全仓 documentation gate 仍被本轮范围外的前端 UI 设计文档状态标记阻塞，不得记为通过。

## 11. 修复前需要冻结的选择

以下选择会影响具体代码，但不改变 Agent 2.0 总架构。状态标注为「已冻结」的选择已于 2026-08-19 随前端修复批次确定：

1. **已冻结**：pending 时允许切换/新建会话；旧请求后台继续执行，结果与取消入口都归属原会话，不自动取消；每个会话最多一个 pending，同一标签页合计最多两个（与后端来源级最大并发 2 对齐），超出时其他会话仍可浏览但输入区提示“已有两个请求正在处理”并暂停一切新轮次提交；
2. **已冻结**：澄清答案在页面内存消息中展示公开安全摘要——CHOICE 显示选项标签，TEXT 显示原文；
3. **已冻结（2026-08-24 修订）**：client timeout 后采用同 requestId 显式重试（复用现有 replay 权威，不自动轮询、不新建结果查询状态机）；超时不取消服务端 Active Turn。确定性 Portfolio Turn 重放原终局；Provider 派生的 General/Conversational 正文不持久化，重放固定返回 `CAPABILITY_UNAVAILABLE/REPLAY_BODY_NOT_RETAINED` 与“该回答未被保留，请重新提问。”，Provider 调用数不得增加；
4. **已冻结（2026-08-24 清理修订）**：同一 absolute timeline 上，Goal/General/DB 单次上限为 8/10/3 秒，18 秒后不再启动新 Task，服务端 Turn 20 秒、前端等待 25 秒、网关至少 30 秒、lease 35 秒；子操作使用 `min(自身上限, Turn 剩余时间)`，不得独立延长 Turn。原 4 秒 Portfolio expression 预算因对应零实现幽灵能力物理删除而退役；确定性 Portfolio 执行继续受 Turn 剩余预算约束，其 PostgreSQL 调用受 DB 3 秒上限约束；
5. **已冻结**：当前 Turn 非 ANSWER 时来源栏显示“最近回答来源”并整体弱化，不隐藏；
6. **已冻结**：澄清卡脱困入口只消费已发布 QuestionPreset 或后端 `suggestedActions`，前端叶子组件不自造业务问题（2026-08-19 确认第 6 项后由硬编码入口修订为预设驱动）。
7. **已冻结（2026-08-24）**：Goal/General 的 schema 或 semantic 拒绝直接结束本轮 Provider 能力，不发送 repair Prompt、不以同 Provider 重试；`maxModelAttempts` 只允许 1，其他值失败关闭。未来若改变，必须作为独立产品决策重新完成隐私、成本、deadline 与质量审批；
8. **已冻结（2026-08-24 目录替换修订）**：每个 Turn 只向请求中显式选择并在 Claim 后冻结的单一 Provider 发请求；鉴权、限流、超时、Transport、schema 或 semantic 失败均不得自动跨 Provider 重发。用户明确切换模型必须使用新 requestId 创建新 Turn；普通同 requestId replay 必须保留原 ModelSelection。

在这些选择冻结前，不应通过零散条件分支修补 UI。

## 12. 最终 Exit Gate

### 12.1 Backend

- 原推荐 Goal 经 TEXT/CHOICE 澄清后保持同一 goalKind、requestedSize 与约束；
- Clarification 一次消费、过期、重复、错误 Token、内容版本变化继续 fail-closed；
- Goal、General 与完整 Turn absolute deadline 均有受控超时测试；
- 响应头已返回但响应体不结束时，Provider 调用仍在 deadline 内终止；
- user cancel 能传播到仍在进行的 Goal Provider 调用；
- timeout/cancel 后只允许一次终局结算；
- timeout 后同 requestId 重试 Provider 正文 Turn，必须得到 `REPLAY_BODY_NOT_RETAINED` 且 Provider 调用数仍为 1；
- Maven 全量与 Testcontainers PostgreSQL 通过；独立 PostgreSQL migration/verify 与 packaged Browser E2E 进一步覆盖真实本地数据库路径。

### 12.2 Frontend

- 澄清提交后 conversationWindow 始终从 USER 开始并严格交替；
- 纯问候稳定返回 CONVERSATIONAL，不创建必填澄清；
- 一次澄清提交后原卡立即变为只读，历史 clarificationId 无重复提交入口；
- 连续澄清必须证明字段与原 blocked Goal 的信息增益，重复/不可恢复时进入明确终局；
- failed/cancelled/timeout 轮次是否进入窗口有明确且一致的断言；
- 新建/切换会话不显示旧 failure、pending、draft 或 notice；
- 新会话不能取消或重试旧会话 requestId；
- internal timeout 有明确错误和恢复入口，user cancel 继续静默；
- 全量 Vitest 与 vue-tsc/Vite build 通过，Vue warning 为零。

### 12.3 Packaged-JAR Browser E2E

至少覆盖桌面与移动端：

1. `推荐两个项目 -> 必要澄清 -> RESOLVE -> 两项推荐 ANSWER`；
2. `CLARIFICATION -> RESOLVE -> 下一轮 SuggestedAction`，无 400；
3. `你好 -> CONVERSATIONAL`，不得出现必填表单；
4. 澄清提交后旧表单只读，重复点击、双击或返回历史位置都不能重复 RESOLVE；
5. failure 后新建会话，旧错误不出现；
6. pending 时切换会话，状态和取消目标不串线；
7. 模拟慢 Provider 超过客户端预算，界面不得直接消失；
8. timeout 后以同 requestId 取回最终答案或得到明确终局；
9. 来源栏与当前 Turn/最近 Answer 的标签语义一致；
10. 同一输入在首次超时、重新进入页面后，能够证明是原 requestId replay/recovery，或明确提示这是一次新请求。

### 12.4 真实 Provider 验收

在普通 CI 之外显式授权运行：

- 快速 conversational；
- 稳定 general explanation；
- 两项目 recommendation；
- 至少一条真实 clarification/resolve；
- 一条受控慢响应或等价的 Fake Provider body-stall 测试。

验收只输出 requestId 是否一致、PublicAgentTurn kind/resolution、Goal 数量、Provider operation、耗时桶、cancel/replay 终局，不输出问题、回答或原始模型内容。

### 12.5 A2-30—A2-115 全量审计 Exit Gate

关闭本轮新增条目必须同时满足：

1. **隐私：** 对完整 settlement 解密后的 `publicTurn + contexts + challenges` 扫描固定 sentinel；visitor-derived text 为零，Provider 派生文本的持久化政策有确定性实现和正反例。sentinel 只属于测试，生产只使用显式来源分类与未知来源默认拒绝。
2. **Provider 授权：** model-runtime、所选 Provider 的 credential/data-policy 与对应 Operation schema/预算形成三层独立准入；旧 global/visitor/provider-ref 权威不得恢复，公开 Catalog 与 availability 不得误报。
3. **语义消费：** AudienceRole、subjectHint、constraints、depth、dimension 等每个保留字段都有生产消费者、反向测试和用户可见差异；不实施的字段、配置和文案同期删除。
4. **回答质量：** Recommendation、Comparison、Cross-domain 和 General depth 分别有确定性行为门；Portfolio 详细回答只在证据满足时 COMPLETE，否则显式 PARTIAL。
5. **多轮与前端：** Discussion clarification、reservation busy、cancel、首页 retry、Preset identity、expiry 和会话删除不破坏 requestId、窗口交替、pointer 或服务端清理语义。
6. **真实场景：** `contracts/agent-turn/scenarios` 每一项由当前生产入口参数化执行；死亡 runner 和硬编码成功字段归零。
7. **Browser/PostgreSQL：** 一条真实浏览器场景覆盖推荐、进入、SOLUTION DETAILED、VERIFICATION 指代、刷新、跨 JVM 重启、切换和退出；全程无错误终局和隐私残留。
8. **Provider Matrix：** 每个批准 Provider 独立报告成功终局率、subject/facet/depth 正确率、schema 拒绝率、超时率和 P50/P95；一次通过不构成稳定结论。
9. **清理与文档：** 旧配置、死类型、幽灵端口和过强完成文案删除；`docs/08`、`docs/11` 和架构机器账本只记录新鲜证据。
10. **完成状态：** A2-30—A2-115 逐项满足原始失败路径、目标回归、受影响全量门和风险对应的集成门后才删除；不得整批凭单层测试关闭。

#### 12.5.1 条目到专属门映射

| Exit Gate | 对应条目 | 专属关闭证据 |
|---|---|---|
| Goal/action 原文入口 | A2-30 | Goal label sentinel、Discussion action inputText 负例、PostgreSQL 完整 settlement 解密扫描 |
| Provider 正文入口 | A2-31 | General 与非快速 Conversational 固定复述 fixture；首次响应有正文、持久化无正文 |
| 安全 replay 合同 | A2-32 | 五 variant 首次/replay/明文矩阵；timeout 同 requestId 固定终局；Provider 调用数 1；Portfolio handle 精确保留 |
| Privacy 机器状态 | A2-36 | `PRIVACY_BOUNDARY=PASS` 引用新鲜 Codec/PostgreSQL complete-settlement 门；缺证据负例失败 |
| 运行时隐私门 | A2-98 | Lifecycle → State → PostgreSQL → 解密 settlement 的 sentinel 数据流测试，不以静态扫描代替 |
| Codec 扫描对象 | A2-99 | 解密后递归扫描 publicTurn、contexts、challenges 的完整 plaintext，五 variant 可回读 |
| Privacy 规则同源 | A2-110 | AGENTS、SECURITY、docs/08、docs/15、机器状态与 Codec 测试使用同一允许/禁止分类 |
| Provider 启动授权 | A2-33—A2-35 | model-runtime、每 Provider 数据策略/凭据、Operation 三层准入矩阵；旧 global/visitor/provider-ref 零生产权威；安全 Catalog/availability 投影 |
| Provider Transport | A2-79、A2-82—A2-84 | GLM/Qwen Profile 独立 payload；401/403/429/5xx 分类；JSON/envelope 分层；256 KiB + 1 拒绝；body-stall deadline 不回退 |
| Provider 调用决策 | A2-85—A2-86 | Goal/General schema 拒绝调用数 1；HTTP/JSON/envelope 失败请求数 1 且 payload model 始终为本 Turn 显式选择；无 repair、retry 或自动 fallback |
| Provider Catalog 证据语义 | A2-113 | Catalog 仅暴露配置与安全选择事实；接口不得声明 schema verified/quality verified；真实层由独立执行证据报告 |
| Provider 真实质量 | A2-80—A2-81、A2-87—A2-88 | 每家真实 schema/semantic canary、P50/P95、成功率与超时率；一次通过不构成稳定结论 |
| Portfolio AnswerIntent 与表达 | A2-37—A2-41、A2-43—A2-52 | outputs/facets 单权威、constraints/dimension 消费、typed reason、depth/coverage 门 |
| 页面上下文与多轮语义 | A2-53—A2-62 | audience/subject typed 差异矩阵、turn summary、section reference；A2-60/61 还需 Provider→限定澄清→facet resolve→pointer 不变的 Browser 原路径；A2-62 需真实 Provider 中英文、长度与复述固定样本 |
| General 运行时质量 | A2-63—A2-68、A2-97 | 语言、句数、深度、exact comparison pair 正反例和真实 Provider 抽样 |
| Frontend lifecycle | A2-69—A2-78 | reservation/cancel 窗口、首页 snapshot、合法 UUID、expiry、所有会话 clear |
| 行为与证据真实性 | A2-91—A2-101、A2-111—A2-115 | scenario 参数化执行、Browser body/trace、跨 JVM、只报告观测字段 |
| 后端退役结构清理 | A2-103—A2-105、A2-107—A2-109 | expression/config/type 零引用；readiness closed enum；Goal/General trailing-token 负例 |
| 脚本与前端配置清理 | A2-102、A2-106 | 退役脚本键零引用；Frontend Agent 冻结 suggested question 唯一权威与 Browser 门 |

#### 12.5.2 固定依赖顺序

1. 先关闭 P0 replay、Provider 授权和机器状态失真；
2. 再恢复真实可执行的 scenario、Browser、semantic trace 和元测试资产存在性门；
3. 再修复 Discussion、Frontend、Audience、subjectHint 和 constraints 等现有行为；
4. 再冻结 AnswerIntent、Portfolio depth/表达和 General 质量；
5. 最后按已批准的 GLM-4.7-Flash/Qwen3.7-Flash 配置目录执行 Provider Matrix、跨 JVM 恢复和清理门。

上游能力尚未进入生产路径时，下游 Gate 必须明确报告 `NOT_READY`，不得用 skip、空场景或固定成功字符串记为 PASS。所有 runner 元测试必须解析并验证其引用的 npm script、Playwright project、Java 测试类、fixture 和脚本文件真实存在。

## 13. 当前结论

Agent 2.0 的单一 Command → Goal → Plan → Execution → Projection → State 生产链仍然成立，幂等、取消、原子结算、公开证据和 typed discussion pointer 仍有真实基础设施价值；本轮审计不要求恢复旧 Router、兼容协议或第二状态权威。

但当前产品不能再用“合同闭合”代替“能力完成”。A2-30—A2-115 证明多个输入字段、配置、UI 宣称和场景清单没有进入真实执行或没有被对应质量门验证，同时 settlement replay 与 Provider 配置权威触及隐私硬边界。当前项目更准确的定位是：**安全状态内核较成熟、公开证据投影较可靠，但语义消费、回答表达、多轮理解和验收真实性仍处于系统性补完阶段。**

修复顺序固定为：P0 隐私与 Provider 授权止血 → 证据和行为门真实性 → 现有 Discussion/Frontend 行为 → AnswerIntent 与 Portfolio/General 产品能力 → 已批准目录的真实 GLM/Qwen 稳定性矩阵 → 清理与联合门。配置化目录的后端离线实现已经完成，但真实 Provider、前端和外层发布门尚未全部通过，整体状态继续保持 `IN_PROGRESS`。
