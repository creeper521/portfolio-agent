# Agent 2.0 开放缺陷与开发账本
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

> **文档性质：** Agent 2.0 开放生产问题与已批准架构、验证、文档治理工作的唯一账本。
> **总体状态：** `IN_PROGRESS`。
> **本轮边界：** 仅重构治理文档；不改变生产代码、Frontend、Prompt、公开合同、OperationBinding 或 Provider 配置；不调用真实 Provider，Provider Gate 为 `NOT_APPLICABLE`。

## 1. 当前结论与证据边界

当前 Command → Goal → Plan → Execution → Projection → State 单生产链仍成立，幂等、取消、原子结算、公开证据和 typed discussion pointer 继续作为稳态基础。项目不能以字段存在、局部单测、HTTP 成功或脚本成功退出代替完整能力证据；机器状态必须继续保持 `IN_PROGRESS`，直到所有必需验证层各自给出新鲜、匹配风险的结果。

本账本只记录当前开放责任。每条“事实”必须能由当前源码调用链、当前自动化失败/缺口、真实运行或批准设计的未完成 Exit Gate 证明；“待验证”只描述尚需最小复现或外层验收的推断，不得写成线上事故。访客文本、Prompt、模型原始输出、凭据、Token、handle 和私有路径不得进入本文。

## 2. 维护、编号、水位和删除规则

- 正式命名空间只有 `A2`、`ARCH`、`GATE`、`DOC`。`A2` 表示生产行为缺陷；其余三类分别表示架构、验证和文档治理工作。
- 水位只表示编号曾使用到的位置，单调递增、永不复用，不保存事项标题、状态或正文：
  - A2 已用至 A2-120
  - ARCH 已用至 ARCH-10
  - GATE 已用至 GATE-01
  - DOC 已用至 DOC-07
- `P0`—`P3` 只表示影响；`NOW`、`NEXT`、`LATER` 是唯一执行序。同一执行序先服从依赖，再按影响排序。
- 新项先提升对应水位，再分配下一编号。每个开放正式 ID 只能有一行；共同根因或门可以互相引用，但不能建立第二份总览表。
- 条目只有在生产修复、原失败路径、针对性回归、受影响全量门以及与风险匹配的 Browser、PostgreSQL、跨 JVM 或获授权 Provider 门都成立后，才从总览、正文和专属 Exit Gate 中删除。本文不保留完成索引、实施流水或第二账本；必要行为摘要写入项目演进日志，细节由 Git 与测试资产追溯。
- 证据变化时直接更新原行；只有风险路径而机制未闭合时标为“待验证”，并把最小复现放在专属门首位。

## 3. 稳态不变量

1. 每个业务概念只有一个生产权威；不恢复旧 Router、兼容桥、配置式双栈或运行时 fallback，回退只依赖整体版本。
2. Frontend 与 Backend 消费同一公开合同源；模型不得决定 Task、DAG、Provider 或扩大公开主体。
3. 同 requestId replay 只复用现有 TurnExecutionStore 权威，不建立第二结果缓存。Provider 派生正文不持久化，重放终局为 `REPLAY_BODY_NOT_RETAINED`；关键词/sentinel 检测只属于测试；Portfolio continuation handle 原样保留。
4. 加密不能替代“不持久化”。生产只按闭合来源分类与未知来源默认拒绝执行持久化政策。
5. 未进入唯一生产路径的实现不算完成；未运行的验证不得记为 `PASS`；一个验证层不能替代另一层。
6. 上游能力未就绪时，下游门必须报告 `NOT_READY`，不得以 skip、空场景、固定成功字段或脚本退出状态冒充能力通过。

## 4. NOW/NEXT/LATER 总览

**NOW** 先修复证据假绿、replay/Provider 授权与状态真实性，再处理会破坏会话、澄清、恢复、推荐约束投影和 Browser 可达性的现行生产路径。这里的直接风险优先于功能扩展。

**NEXT** 在上述权威和门稳定后，完成页面上下文、多轮语义、Portfolio 表达、General 语言/深度/Comparison、结构化输出边界以及相应 Frontend 正文验收。

**LATER** 只在上游生产链与验证入口可达后执行跨 JVM Browser 恢复、每家 Provider 独立稳定性矩阵、token/cost canary 和退役资产最终零引用清理。严重度不会改变这些依赖顺序。

## 5. 开放生产问题（A2）

| ID | 严重度 | 执行序 | 状态 | 当前证据 | 修复边界/依赖 | 专属验证 / Exit Gate |
|---|---|---|---|---|---|---|
| A2-15 | P1 | NEXT | OPEN | 事实：页面重新进入只恢复会话身份与状态，不证明旧 requestId 终局被取回；快速新调用可掩盖原请求丢失。 | 复用现有 replay 权威，区分恢复与新执行；依赖恢复链和 Provider LIVE。 | Browser 记录同一 requestId、调用次数与终局；重新进入不得以新 requestId 冒充恢复。 |
| A2-20 | P1 | NEXT | OPEN | 事实：运行时已有中文结构门，自动 Provider 样本取得局部正例；Browser 正文语言语义尚无闭环。 | 保持简体中文正文约束和技术标识例外，不增加重试或正文日志。 | 获授权 Provider 固定样本与 Browser 正文逐项通过中文、结构和隐私门。 |
| A2-21 | P1 | NEXT | OPEN | 事实：depth 已进入 typed Goal 和 General 句数结构，但真实页面的用途、边界、权衡与误区差异未验收。 | 由同一 depth 权威控制结构与篇幅；依赖 General 质量和 Browser 正文门。 | 三档输入在生产入口形成可断言的结构、篇幅和语义差异，Browser 无降档。 |
| A2-22 | P1 | NOW | OPEN | 事实：失败恢复设计要求同 requestId 重试冻结完整提交身份，当前联合发布门仍未给出完整证据。 | 重试原样复用 command、surface、preset/model selection 与 fingerprint；不得重建请求。 | 单元与 Browser 断言重试快照逐字段一致、无 fingerprint 冲突、无额外 Provider 调用。 |
| A2-23 | P1 | NOW | OPEN | 事实：Clarification reservation 与 terminal settlement 的原子消费仍需原失败路径联合验收。 | 消费只能随 terminal transaction 成功；依赖 TurnExecutionStore settlement。 | settlement 失败、取消、竞争和重试矩阵证明 reservation 可恢复且只消费一次。 |
| A2-24 | P1 | NOW | OPEN | 事实：单候选 `NEEDS_CLARIFICATION` 的语义路由与 Discussion 转换仍需生产入口证明。 | 保持单一 Semantic Routing/Lifecycle 权威，不用前端推测状态。 | 单候选路径返回限定澄清，解析后恢复原 Goal；不得被强制改造成 Discussion。 |
| A2-25 | P2 | NOW | OPEN | 事实：PostgreSQL Session replacement 与 expired discussion pointer 的 parity 仍在联合门中开放。 | replacement 必须在同一事务清理旧 pointer；依赖状态 schema 与 store 合同。 | PostgreSQL replacement、重启、过期和并发测试均无残留 pointer。 |
| A2-26 | P1 | NOW | OPEN | 事实：ENTER discussion 的 TTL 可能被来源 Recommendation 的较短过期时间裁剪。 | Discussion TTL 由自身权威计算，来源只提供合法 continuation identity。 | 受控时钟证明 ENTER 后获得完整 discussion TTL，来源过期不提前截断。 |
| A2-27 | P2 | NOW | OPEN | 事实：Frontend pending 清理存在跨 generation 清除新请求状态的风险路径。 | 清理必须同时匹配 sessionId、requestId 与 generation。 | 迟到 settle/cancel/retry 交错测试证明旧 finally 不清除新 pending。 |
| A2-28 | P1 | NOW | OPEN | 事实：Discussion summary、revision、TTL 与恢复动作虽有 typed 投影，完整冷恢复/Browser 行为尚未闭合。 | 服务端 summary 是唯一权威；前端不得推算 revision 或过期动作。 | active/expired/entered 三态经刷新和重启后投影一致，动作只来自当前 summary。 |
| A2-29 | P1 | NOW | OPEN | 事实：Provider、Browser、共享合同、隐私与 persistence-safe replay 尚未形成同一发布证据链。 | 各验证层独立报告，不能由单层绿色替代。 | 专项、合同、PostgreSQL、packaged Browser、隐私及获授权 Provider 门逐层有新鲜结果。 |
| A2-30 | P0 | NOW | OPEN | 事实：Goal label 与 Discussion action 已有安全投影修复，但完整 settlement 外层门仍是关闭条件。 | 禁止 visitor-derived text 进入 replay；加密不构成豁免。 | 固定输入标记贯穿 Goal/Projection/settlement，解密后的 publicTurn、contexts、challenges 均无原文片段。 |
| A2-31 | P0 | NOW | OPEN | 事实：General 与非快速 Conversational 正文来自 Provider；待验证：真实复述风险以 fixture 证明，不写成已发生。 | Provider-derived text 默认 live-only；未知来源默认拒绝持久化。 | 复述 fixture 首次响应可见、持久化正文不可见，同 requestId 重放为固定安全终局。 |
| A2-32 | P0 | NOW | OPEN | 事实：确定性 Portfolio 与 Provider 自由正文不能共享同一种精确 replay 语义。 | Portfolio 可精确 replay；Provider 正文只保存固定不可重放终局，不建第二缓存。 | 五种 PublicTurn variant 的首次、Memory/PostgreSQL replay、明文与调用次数矩阵通过。 |
| A2-33 | P0 | NOW | OPEN | 事实：当前 Turn 已显式冻结 ModelSelection，旧 global/provider-ref 权威已退出；两家真实接收方总门仍未完成。 | Catalog、Provider policy、Operation binding 三层准入后冻结唯一接收方。 | 错配启动失败；显式选择只到指定 Provider fixture；两家获授权 canary 分别证明接收方。 |
| A2-34 | P0 | NOW | OPEN | 事实：Operation schema 与 canonical contract registry 已做启动期校验；双合同与 Codec 的最终一致性仍需联合门。 | Provider/Application contract、fingerprint、compiler 与唯一 Codec 必须原子一致，无兼容 reader。 | 正确/错误/缺失合同启动矩阵和 provider-draft→canonical→Codec 正反例通过。 |
| A2-35 | P1 | NOW | OPEN | 事实：公开 availability 消费共享 readiness，但真实 Provider 与 Frontend 消费总门仍开放。 | 可用性只投影已冻结、可执行的目录与 Operation readiness。 | 配置矩阵中公开目录、Bean wiring、首次请求和错误终局一致。 |
| A2-36 | P0 | NOW | OPEN | 事实：机器状态当前以 complete-settlement 测试记录隐私 `PASS`；整体证据不变量仍为失败。 | 任何 PASS 必须引用新鲜完整数据流门；依赖状态 checker 和 replay 文档一致性。 | 删除或陈旧化完整 settlement 证据的负例必须使 checker 失败；新鲜正例才允许维持 PASS。 |
| A2-37 | P1 | NEXT | OPEN | 事实：零实现表达端口及幽灵预算已移除；最终零引用和风险对应外层门仍未合并。 | 保持物理删除，不重新接入未批准表达模型。 | 生产/测试/配置零引用，Backend、architecture、documentation 与 Browser 受影响门通过。 |
| A2-38 | P1 | NEXT | OPEN | 事实：同类 Claim 已合成为叙述块并聚合来源；真实 Browser 可读性和正文完整性未证明。 | 只组合审核事实，不制造新事实；依赖 depth 与正文门。 | 每段来源闭合、区块数量符合 depth，Browser 正文非机械 Claim 列表。 |
| A2-39 | P1 | NEXT | OPEN | 事实：Portfolio Goal 已携带 depth；真实 Provider 选择与 Browser 差异仍待验收。 | depth 由 typed Goal 单权威持有并贯穿澄清与跨域子任务。 | 三档 Goal 在生产入口、恢复路径与 Browser 结果中保持一致。 |
| A2-40 | P1 | NEXT | OPEN | 事实：depth 已影响检索、候选上限、coverage 和区块；外层差异矩阵未完成。 | 禁止只加装饰字段；依赖 Portfolio retrieval/presentation 联合门。 | 同一主题三档输入产生可断言的召回、覆盖、区块和完成状态差异。 |
| A2-41 | P1 | NEXT | OPEN | 事实：typed parameters 已作为下游权威，requestedOutputs 由其派生并校验；真实合同门未闭合。 | 只保留一个 AnswerIntent 权威，其他表示必须确定性派生。 | 互相矛盾的 outputs/facets 失败关闭，合法路径跨 Codec、Plan、Browser 一致。 |
| A2-43 | P1 | NEXT | OPEN | 事实：闭合 constraints 已进入召回、排序与缺口计算；真实 Provider/Browser 用户可见差异未闭合。 | 约束只能来自公开目录，目录外值失败关闭。 | 固定矩阵证明约束改变候选或排序，缺口经公开合同和 Browser 可见。 |
| A2-44 | P1 | NOW | OPEN | 事实：`PortfolioSemanticResultFactory` 在数量完整但有约束缺口时产出 `PARTIAL` 且 omissions 为空；`PublicAgentTurnProjector` 因 `PARTIAL` 伪造“公开结果数量不足”到 incompleteReasons；`PublicPresentation.Recommendation` 随后因数量完整而抛错，后面的 `gapNotices()` fallback 不可达；Frontend 还把 unsatisfiedConstraints 藏在 count-incomplete 条件内。 | 分离数量缺口与约束缺口；Projector 不得为约束缺口伪造数量理由。依赖 Backend invariant 与 Frontend 展示同时修复。 | Backend 原路径以完整数量+约束缺口返回合法 `PARTIAL` 和明确约束且不抛错；Browser 在数量完整时仍展示 unsatisfiedConstraints。 |
| A2-45 | P1 | NEXT | OPEN | 事实：每项已有闭合 reason code、固定公开说明与 publicSourceKeys；真实 Provider/Browser 总门未完成。 | 理由只能由审核证据和闭合代码产生。 | 每个推荐项理由、来源和候选身份一致，缺来源或未知 reason 失败关闭。 |
| A2-46 | P1 | NEXT | OPEN | 事实：requestedSize、career track、capability 已参与召回与稳定排序；真实目标敏感矩阵仍开放。 | 同一确定性排序权威同时覆盖 Bundle/PostgreSQL。 | 固定目标矩阵在两种数据源产生一致且可解释的候选/排序差异。 |
| A2-47 | P1 | NEXT | OPEN | 事实：Portfolio Comparison 已按 dimension 生成对齐 section；Browser 正文与真实 Provider 门未闭合。 | 比较必须以闭合 dimension 对齐主体证据，不顺序拼接。 | 每个请求 pair 都有差异、取舍、缺口与来源，额外/遗漏 pair 失败。 |
| A2-48 | P1 | NEXT | OPEN | 事实：Codec 与 Invocation 已拒绝未知 comparison dimension；外层真实路径未验收。 | 未知值只允许拒绝或澄清，不降级成其他 facet。 | Provider fixture、API 与 Browser 输入未知值均形成同一安全终局。 |
| A2-49 | P1 | NEXT | OPEN | 事实：Portfolio dimension 已改为后端闭合集合；真实 Provider/Browser 仍需证明消费。 | 一个枚举贯穿 Goal、retrieval、coverage 与 presentation。 | 每个合法维度有生产消费者和反向测试，未知/重复值失败关闭。 |
| A2-50 | P1 | NEXT | OPEN | 事实：Cross-domain 关系段已由 General mechanism 和 Claim category 映射；真实语义质量未证明。 | 只组合验证过的 statement/Claim/caveat，不引入模型表达第二权威。 | 固定证据与真实 Provider 样本均能追溯概念—项目关系和适用边界。 |
| A2-51 | P1 | NEXT | OPEN | 事实：cross-domain 的 General/Portfolio 子任务已共享 depth；Browser 联合门未完成。 | depth 由上游 Goal 一次冻结，fan-out 不得各自改写。 | 三档跨域请求的两路任务、综合结果和 Browser 结构一致。 |
| A2-52 | P1 | NEXT | OPEN | 事实：详细 overview 缺必需 profile 时会 `PARTIAL`；用户可见缺口文案尚未完成 Browser 验收。 | 证据不足不得表现为完整；依赖 coverage 与投影。 | 缺证据 fixture 返回安全缺口和 `PARTIAL`，完整 fixture 才能 `COMPLETE`。 |
| A2-53 | P1 | NEXT | OPEN | 事实：闭合 Audience 已进入全部任务并影响 General/Portfolio；真实角色差异矩阵未闭合。 | 角色只能改变表达/优先级，不扩大主体或证据范围。 | 四种角色在相同证据上有 typed、可见差异，scope 与隐私保持不变。 |
| A2-54 | P1 | NEXT | OPEN | 事实：验证后的 subjectHint 已投影为 defaultSubject；真实页面消费未闭合。 | hint 只能来自公开目录和可信 surface，访客不能注入任意 ID。 | 合法/无效 hint 的 API、Provider 与 Browser 正反例通过。 |
| A2-55 | P1 | NEXT | OPEN | 事实：STANDARD 单主体省略表达可由后端绑定 `SURFACE_HINT`；真实 Browser 指代门未完成。 | 显式其他公开主体可覆盖默认；不得把默认变成锁定主体。 | 项目/案例页省略表达绑定当前主体，显式切换仍准确，非法主体失败。 |
| A2-56 | P1 | NEXT | OPEN | 事实：V7 typed semantic state 已原子加密保存安全短摘要；真实 Provider/Browser 多轮仍未证明。 | 只保存闭合 Goal/section 身份，不保存正文、anchor、Prompt 或 handle。 | PostgreSQL 重启与 Browser 多轮证明状态可用且解密明文无敏感字段。 |
| A2-57 | P1 | NEXT | OPEN | 事实：Provider 必须返回匹配 recentReference.goalId 才注入 `RECENT_TURN`；真实生成稳定性未知。 | 仅显式、精确引用可绑定上一 Goal，不做静默主题继承。 | 正确/错误/遗漏 goalId 的 Provider fixture 与 Browser “进一步展开”路径通过。 |
| A2-58 | P1 | NEXT | OPEN | 事实：V7 保存公开 sectionId/sectionKind 并校验归属；Browser 区块指代未闭合。 | section 引用必须属于所引用 Goal，正文不进入状态。 | “展开第 N 区块”命中正确 section；跨 Goal、过期或未知 ID 失败关闭。 |
| A2-59 | P1 | NOW | OPEN | 事实：Frontend 仍可能在不相关话题后附带旧 Recommendation hint。 | context routing 按最新相关公开结果与会话归属清理。 | 主题切换、非推荐终局、新会话和失败后均不携带陈旧 hint。 |
| A2-60 | P1 | NOW | OPEN | 事实：Discussion 已生成 typed challenge 和 generation guard；真实 Provider→澄清→resolve 原路径未闭合。 | pointer 保持服务端权威；clarification 不重新解释访客文本。 | Browser 完成限定澄清、facet resolve、pointer 不变且返回预期正文。 |
| A2-61 | P1 | NOW | OPEN | 事实：active discussion 已提供闭合 facet choice，expired 仅允许重进；前端消费门仍开放。 | choice、template、binding 必须精确闭合。 | active/expired choice 渲染、提交、只读与恢复动作均来自服务端投影。 |
| A2-62 | P2 | NEXT | OPEN | 事实：Provider-derived Conversational 已有语言、长度、控制字符和连续复述校验；真实固定样本未完成。 | 只做来源/结构验证，不以关键词清洗生产正文。 | 获授权中英文、超长、复述和控制字符样本按 closed reason 通过/拒绝。 |
| A2-63 | P1 | NEXT | OPEN | 事实：General Validator 已按 depth 强制 statement 句数；真实 Provider/Browser 质量门未闭合。 | 句数是不变量，不只写入 Prompt。 | 三档 Provider 样本和 Browser 正文均落入相应句数与结构桶。 |
| A2-64 | P1 | NEXT | OPEN | 事实：Validator 已要求每句含中文并拒绝完整英文句；外层验收仍开放。 | 允许技术标识，拒绝非技术完整英文正文。 | 固定正反例、真实 Provider 样本与 Browser 展示全部满足语言规则。 |
| A2-65 | P1 | NEXT | OPEN | 事实：DETAILED 要求闭合 aspect 精确覆盖，但 aspect 仍由 Provider 自报。 | 结构闭合不能代替正文语义；依赖抽样与人工可复核指标。 | 真实样本逐 aspect 对照正文，缺失、错标或空泛覆盖均失败。 |
| A2-66 | P1 | NEXT | OPEN | 事实：Validator 已要求实际 comparison pair 等于请求笛卡尔积；A2-119 仍暴露容量和身份缺口。 | 先修容量与 pair identity，再维持无额外/遗漏/重复 pair。 | 边界容量、乱序、重复、额外与遗漏 pair 的 Codec/Provider/Browser 矩阵通过。 |
| A2-67 | P2 | NEXT | OPEN | 事实：caveat 已校验 kind、中文、句数、长度与去重；相关性仍待真实正文抽样。 | caveat 必须对应当前主题边界，不得成为通用免责声明。 | 固定错题 caveat 被拒绝，真实样本的 kind/text 与主题可追溯。 |
| A2-68 | P1 | NEXT | OPEN | 事实：语言、句数、aspect、exact pair 核心门已进入运行时；完整 Provider/Browser 质量门未通过。 | 生产 Validator 与验收规则同源，不放宽运行时换取通过率。 | 运行时拒绝分布、真实正文质量和 Browser 终局按相同规则独立报告。 |
| A2-69 | P1 | NOW | OPEN | 事实：`CLARIFICATION_IN_PROGRESS` 可能排除 USER 却保留 busy Assistant，破坏窗口交替。 | 临时终局不得进入可信 conversationWindow。 | busy、取消、重试、最终 settle 的窗口始终从 USER 开始并严格交替。 |
| A2-70 | P1 | NOW | OPEN | 事实：服务端释放 reservation 后，Frontend 澄清卡可能仍永久只读。 | 卡片状态服从当前 reservation/terminal 结果，不由旧 pending 推断。 | 可恢复失败和取消后重新可提交；成功消费后永久只读且不能双击。 |
| A2-71 | P1 | NOW | OPEN | 事实：首页 Round 每轮新建 Conversation、空窗口且无 Token，不构成真实多轮。 | 明确选择单轮预览或复用同一会话，不保留模糊宣称。 | UI 文案与网络 trace 一致；若为多轮则 conversationId/window/token 连续。 |
| A2-72 | P1 | NOW | OPEN | 事实：首页失败重试会生成新 requestId。 | 失败视图保存原提交快照并原样 replay。 | 重试沿用 requestId/fingerprint，原请求只执行一次并得到同一终局。 |
| A2-73 | P1 | NOW | OPEN | 事实：首页 Preset 失败后只保留展示文本，重试可能退化为 FREE_TEXT。 | 保存 presetId、revision、surface 和 requestId，不从文案反推命令。 | Preset 失败/重试网络请求字段逐项一致，错误 revision 失败关闭。 |
| A2-74 | P2 | NOW | OPEN | 事实：缺 `randomUUID` 时 Frontend fallback 可生成不符合后端合同的 ID。 | 使用合规 UUID 实现或明确标记环境不支持。 | 模拟缺 API 环境，所有 conversation/request 标识仍通过后端校验且不碰撞。 |
| A2-75 | P2 | NOW | OPEN | 事实：删除非活跃本地会话不会清理对应服务端状态。 | 任意会话删除均 best-effort 调用精确服务端 clear，不能误清当前会话。 | 多会话删除、网络失败、重试与重启后目标会话状态符合预期。 |
| A2-76 | P2 | NOW | OPEN | 事实：Discussion 倒计时归零只改文案，不一定取得权威 `EXPIRED` summary。 | 到期触发受控冷恢复，动作来自服务端。 | 受控时钟下无需 reload 即取得 EXPIRED 状态和唯一合法动作。 |
| A2-77 | P2 | NOW | OPEN | 事实：过期恢复入口依赖页面 reload 才出现。 | 复用 conversation GET，不新增客户端状态权威。 | active 页面停留至过期后自动进入合法恢复路径，刷新前后一致。 |
| A2-78 | P1 | NOW | OPEN | 事实：传输层可把任意 HTTP 200 当成功，包含 `CAPABILITY_UNAVAILABLE`。 | happy path 必须断言预期 PublicTurn kind/resolution/body。 | 每个 Browser 场景拒绝错误终局，失败 variant 只进入对应错误体验。 |
| A2-79 | P1 | NEXT | OPEN | 事实：GLM/Qwen 已有独立协议 Profile；两家真实 schema canary 未全部闭合。 | Profile 只拥有 Provider envelope/thinking/stream 差异，不能泄漏业务合同。 | 两家 payload fixture 与获授权 canary 分别通过，任一字段变化不扩散另一家。 |
| A2-80 | P1 | NEXT | OPEN | 事实：Qwen Comparison 在进入 General 前被 Goal Draft v1 阻断；Qwen/GLM 全能力独立矩阵均未完成。 | Goal v2 生产绑定是未批准 Level 3 决策，本项不得实施；先取得批准或保持 `NOT_READY`。 | 批准后原 Comparison 路径进入 General；两家分别完成 schema、semantic 和终局 canary。 |
| A2-81 | P1 | LATER | OPEN | 事实：两家成功调用的稳定 P50/P95 与跨端预算基线不足，失败样本不能冻结预算。 | 依赖可达且通过基本 schema/semantic 的独立矩阵。 | 每家分 operation 报告成功延迟分布与超时率，再验证 operation<Turn<client<gateway<lease。 |
| A2-82 | P2 | NEXT | OPEN | 事实：401/403、402、429、5xx、其他 4xx 已闭合分类；真实分布与公开映射仍未完整验证。 | diagnostics 只保留 closed layer/code，不保留 Provider body。 | 各状态 fixture 与获授权失败 canary 映射一致，日志无正文。 |
| A2-83 | P2 | NEXT | OPEN | 事实：Transport、JSON、envelope、schema、semantic 已分层；真实发生率与 pointer 可观测性仍开放。 | 每次拒绝只由责任层发布一个 closed reason。 | 各层正反例和真实样本均产生唯一 layer/code，异常消息和正文零泄漏。 |
| A2-84 | P2 | NEXT | OPEN | 事实：Transport 已在读取期限制响应体并保留 absolute deadline；真实总门未完成。 | 上限在分配完整正文前生效，取消订阅且不 fallback。 | 边界字节、超一字节、body-stall、线程中断和真实大响应门通过。 |
| A2-85 | P1 | NEXT | OPEN | 事实：schema/semantic 拒绝已冻结为单次调用失败，不 repair、不重试。 | 未来改变需独立隐私、成本、deadline 与质量批准。 | Goal/General schema、semantic、HTTP、JSON、envelope 失败均断言调用数严格为一。 |
| A2-86 | P1 | NEXT | OPEN | 事实：同 Turn 已冻结单一显式 ModelSelection；自动跨 Provider 重发被禁止，Frontend 切换体验仍未闭合。 | 用户换模型只能新 requestId；普通 replay 保留原选择。 | 失败不触达第二 Provider；手动切换创建新 Turn，旧 requestId replay 不改模型。 |
| A2-87 | P1 | LATER | OPEN | 事实：Qwen Comparison 被上游 Goal Draft v1 阻断；Qwen/GLM 完整独立矩阵均未完成。 | 依赖 A2-80 的批准决策与各自可用性；不得用一家结果代替另一家。 | runner 对每家独立执行、独立失败、独立报告，任何 `NOT_READY` 不算 PASS。 |
| A2-88 | P1 | LATER | OPEN | 事实：现有局部样本不足以形成两家 General/Comparison 稳定性结论。 | 依赖独立矩阵可达；不以一次通过或混合样本聚合。 | 每家报告成功终局率、语义正确率、schema 拒绝率、超时率及 P50/P95。 |
| A2-91 | P1 | NOW | OPEN | 事实：scenario command 已接入生产 HTTP runner，但 expected、setup 和 hard-error trace 仍有失败/缺口。 | 每个场景必须执行真实 setup、command 和可观测断言。 | 全部 manifest 场景逐项运行并匹配 expected；缺 setup/trace 直接失败。 |
| A2-92 | P1 | NOW | OPEN | 事实：Browser happy path 偏重状态/UI，未稳定拒绝错误 PublicTurn 终局。 | 解析公开 body 并断言 kind、resolution、coverage。 | 每条 happy path 既断言 UI 也断言网络 body，错误 variant 必须使 spec 失败。 |
| A2-93 | P1 | NOW | OPEN | 事实：公开响应不暴露可安全断言的 facet/depth trace，Browser 无法证明语义消费。 | 只增加测试可见、脱敏且闭合的 trace；不得进入公开生产合同。 | packaged 测试 lane 能断言 facet/depth/subject，生产 lane 无 trace 泄漏。 |
| A2-94 | P1 | NOW | OPEN | 事实：空、单句或缺 section 的回答仍可能通过 Browser 状态断言。 | 正文质量门检查 section、来源、数量、coverage 与非空。 | 逐类 fixture 的缺失/空白/数量不足负例失败，合法正文通过。 |
| A2-95 | P1 | NOW | OPEN | 事实：部分 live gate 强字段已移除，但所有输出仍需只来源于实际观测。 | 不输出硬编码 goalKind 或未观测成功字段。 | 元测试注入不一致响应时 runner 报错；报告字段可逐项回溯公开响应或 closed diagnostics。 |
| A2-96 | P1 | LATER | OPEN | 事实：packaged API 已证明跨两个 JVM 恢复，仍缺同一浏览器会话跨真实后端重启。 | 复用同一 PostgreSQL、密钥、ResumeToken 与 Browser context。 | Browser 不重建会话跨后端重启后恢复 Conversation、typed state 和合法 replay。 |
| A2-97 | P1 | NEXT | OPEN | 事实：General 单测已有语言、句数、depth、顺序负例；真实 Provider 质量矩阵仍开放。 | fixture 不能只自造满足规则的正文；依赖外部样本。 | 错误语言/句数/depth/order 正反例与每家真实抽样使用同一判定器。 |
| A2-98 | P1 | NOW | OPEN | 事实：已存在 Lifecycle→PostgreSQL→解密完整 settlement 的运行时隐私门；发布组合尚未固定执行。 | 静态 privacy scan 不能替代运行时数据流。 | canonical 发布门必须执行完整 settlement 标记测试，缺失或 skip 即失败。 |
| A2-99 | P1 | NOW | OPEN | 事实：Codec 测试已扫描解密后的 publicTurn、contexts、challenges；最终五 variant 和回读联合门仍开放。 | 扫描完整 plaintext 对象，不只扫描 resume template。 | 五 variant Memory/PostgreSQL 回读后递归扫描，任一字段含 visitor/provider 标记即失败。 |
| A2-100 | P1 | NOW | OPEN | 事实：release summary 可包含 `FAILED`/`NOT_RUN`，而 runner 因未传 `-RequireComplete` 仍退出 0。 | 发布入口必须把分层状态转成进程失败；禁止用退出 0 代替具体 gate 证据。 | fixture 令任一必需层失败/未运行时 canonical release 非零退出；全部层真实 PASS 才退出 0。 |
| A2-101 | P2 | NOW | OPEN | 事实：测试总量不能表达用户场景和风险覆盖；当前分层报告仍需成为唯一发布口径。 | 只按场景、风险门与执行状态报告覆盖，不搬运总量。 | 报告逐场景列出 matched/setup/trace 与风险层，新增无断言测试不提升完成状态。 |
| A2-102 | P2 | LATER | OPEN | 事实：生产 runner 已迁移到当前 model-runtime/Provider/modelRef 配置，最终全仓零引用门仍需保留。 | 删除退役键，不提供兼容别名。 | 非测试脚本、生产配置与测试资产对退役前缀零引用，当前启动器自测通过。 |
| A2-103 | P2 | LATER | OPEN | 事实：无消费者的 Portfolio expression timeout 已删除；最终配置与文档零引用未统一收口。 | 不在没有生产消费者时恢复预算。 | 配置绑定、环境变量、文档和测试零引用；预算关系只含真实 operation。 |
| A2-104 | P2 | LATER | OPEN | 事实：零入口的 Portfolio expression Port/Compiler 已物理删除；清理总门仍开放。 | 未批准前不重建表达器或可选构造分支。 | 生产/测试/Spring/反射零引用，architecture 与 packaged 回归通过。 |
| A2-105 | P2 | LATER | OPEN | 事实：无消费者 conversation history 配置已删除；最终零引用仍需合并发布门。 | 若未来需要历史预算，先建立唯一消费者与隐私设计。 | properties、env、文档、绑定类和脚本零引用，ConversationWindow 隐私门不回归。 |
| A2-106 | P2 | NEXT | OPEN | 事实：后端 suggested-question 数量伪权威已删除；Frontend 展示数量和交互验收仍开放。 | 数量由唯一前端设计/发布 preset 权威决定，不重建后端无消费者配置。 | 当前权威唯一可检索，桌面/移动 Browser 数量、布局和交互通过。 |
| A2-107 | P2 | LATER | OPEN | 事实：Operation readiness 已收敛为闭合真实枚举；文档和外层投影总门仍需统一。 | readiness 只说明配置可用，不宣称 schema/quality 已验证。 | 枚举零旧名，公开投影和文档不把 configured 推导成质量 PASS。 |
| A2-108 | P2 | LATER | OPEN | 事实：已识别的零消费者类型已删除；缺少持续的 record/dead-code 纪律。 | 只在证明无生产入口、序列化或反射注册后物理删除。 | 零引用扫描、架构依赖测试和 packaged 启动共同证明删除安全。 |
| A2-109 | P2 | LATER | OPEN | 事实：Goal/General Codec 已启用 trailing-token 拒绝；双合同 Parser/Compiler 全链仍需一致门。 | 所有结构策略使用严格 Parser，不允许 Adapter 旁路。 | 合法对象后追加 token、重复键和多对象在 Provider Draft 与 canonical 层均失败。 |
| A2-110 | P0 | NOW | OPEN | 事实：治理文档与 Codec 测试已采用 persistence-safe 分类；本次账本替换及活动文档同步尚未完成。 | AGENTS、SECURITY、当前状态、机器状态和测试使用同一允许/禁止分类。 | 文档正反例与运行时 fixture 同时验证三条 replay 安全标记，任一漂移失败。 |
| A2-111 | P1 | NOW | OPEN | 事实：`verify-release.ps1` 未传 `-RequireComplete`，所以含 `FAILED`/`NOT_RUN` 的汇总仍可随命令退出 0；机器证据不能据此宣称 PASS。 | EVIDENCE hard invariant 只接受五类新鲜具体 gate，不接受汇总命令成功。 | 负例让任一层缺失时 release 与 architecture checker 均失败；机器状态不得升级。 |
| A2-112 | P1 | NOW | OPEN | 事实：Discussion 计划已区分 State/Lifecycle 与 Semantic Quality，但活动文档仍需防止完成表述回流。 | 计划状态按责任层拆分，语义/Browser 未通过不得写 Complete。 | documentation checker 对活动计划的过强完成词有负例，当前状态与账本一致。 |
| A2-113 | P1 | NOW | OPEN | 事实：Configured catalog 已收窄为配置与安全选择元数据；真实 Transport/Schema/Quality 仍由外部门证明。 | 接口不得从 configured 推导 verified。 | Catalog API/测试无 `supports` 或质量强声明；真实层报告独立存在。 |
| A2-114 | P2 | NOW | OPEN | 事实：刷新、PostgreSQL、跨 JVM API、同浏览器跨 JVM 已分层，但最后一层仍未运行。 | 每类恢复独立留证，不互相替代。 | 汇总分别报告四类状态；缺任一必需层不得整体 PASS。 |
| A2-115 | P1 | NOW | OPEN | 事实：字段、接口或配置存在仍可能被误判为能力完成。 | 完成必须同时具备生产消费、用户可见差异、负例和全链门。 | 能力清单逐项链接消费者、Browser 结果、反向测试和风险门，缺项保持开放。 |
| A2-116 | P1 | NEXT | OPEN | 事实：低信息确定性出口与 Provider Draft 已取得局部正例；原始 Qwen 两轮 Browser/packaged 路径仍未完成发布验收。 | 保持窄出口、严格 blockedGoal、无 repair/retry/fallback；不借此启动未批准 Goal v2。 | 首轮模型调用为零，typed recent state/合法澄清不被拦截，原两轮 Qwen 路径通过。 |
| A2-117 | P1 | NEXT | OPEN | 事实：Goal 与 General 双合同编译层已进入生产；Qwen Comparison 仍被上游 Goal Draft v1 拒绝，Qwen/GLM 完整独立矩阵未完成。 | Goal v2 生产绑定是未批准 Level 3 决策；保持 frozen binding、单次调用和无 runtime fallback。 | 获批准后同一合同 fingerprint 驱动请求与本地校验；Comparison 可达；两家独立矩阵及全量/隐私/Browser 门通过。 |
| A2-118 | P0 | NOW | OPEN | 事实：网络/5xx 恢复可返回 `ok=false, invalid=false`，随后落入空 `ensureSession()`；`watchEffect` 会清除唯一 ResumeToken。待验证：仍需聚焦单元和 Browser 复现。 | 瞬时失败必须保留 token 与可重试恢复状态；只有权威 invalid 才清除。 | 单元模拟 network/5xx，Browser 冷启动失败后重试恢复原会话；token 全程不丢失。 |
| A2-119 | P1 | NEXT | OPEN | 事实：合法 Goal 的 subjects×dimensions 可超过 General 的 20 项上限；Provider draft 句子又按排序后的 canonical pair 位置赋值，没有 pair identity。 | 在 Goal 边界对齐容量，并让 draft 携带可校验 pair identity；依赖合同批准与 A2-66。 | 最大/超限组合在 Goal 层确定性处理；乱序/缺失/重复 pair 无法通过编译；Browser Comparison 对齐正确。 |
| A2-120 | P1 | NOW | OPEN | 事实：packaged runner 未启用 `PLAYWRIGHT_MODEL_SELECTION`；公开可选模型不足两项时 spec 还会 skip。 | 增加明确 packaged lane 和双模型 fixture；skip 不能记为覆盖。 | runner 元测试证明 env 设置/恢复和 spec 可达；lane 必须执行两模型切换，前置不足时报 `NOT_READY`/失败。 |

## 6. 架构工作（ARCH）

| ID | 严重度 | 执行序 | 状态 | 当前证据 | 修复边界/依赖 | 专属验证 / Exit Gate |
|---|---|---|---|---|---|---|
| ARCH-01 | P1 | NOW | OPEN | 事实：批准设计要求 Domain Adapter 只依赖 `StructuredOutputGateway`，而 compiler 参数仍可由调用方传入。 | Gateway 原子拥有 compile→单次 transport→extract→parse→schema validate；内部成员默认 package-private。 | 生产调用方只见一个 Gateway 方法且不能传 contract/compiler；架构依赖测试和零引用门通过。 |
| ARCH-02 | P0 | NOW | OPEN | 事实：`OperationBinding` 已冻结 Provider/Application 合同及 fingerprint，但精确 pair 与 consumer 一致性仍需总门。 | 两份合同、compiler、strategy、token policy、extractor 与 binding fingerprint 原子不可自由组合。 | 错配/缺失/未知组合启动前失败；Snapshot、请求、Gateway 与 canonical Codec fingerprint 一致。 |
| ARCH-03 | P1 | NOW | OPEN | 事实：`AgentRuntimeReadiness` 主要校验 operation policy/canonical contract，可执行 binding、目录和公开 availability 仍分层。 | 建立单一启动期 Runtime Readiness 快照，configured 不外推 schema/quality。 | 配置矩阵覆盖 agent、provider、binding、contract 与公开投影，任一缺口 fail-closed。 |
| ARCH-04 | P2 | NOW | OPEN | 事实：diagnostics 已有 closed layer/code，但 pointer、reason 所有权和重复发布边界仍需冻结。 | 每次失败只由责任层产生一个 closed reason 与安全 pointer，不暴露异常/正文。 | transport/gateway/domain/lifecycle 全链正反例证明唯一 reason、可定位且无重复计数。 |
| ARCH-05 | P1 | NEXT | OPEN | 事实：`AgentTurnLifecycleService` 同时编排 claim、恢复、Goal、执行、结算与异常映射，扩展成本持续上升。 | 保留唯一 lifecycle façade，内部按 claim/resolve/execute/settle 深模块拆分，不增加第二权威。 | 公共入口不变；模块依赖门无反向依赖；所有取消、幂等、原子结算合同通过。 |
| ARCH-06 | P1 | NEXT | OPEN | 事实：Frontend Workspace 聚合会话、pending、恢复、草稿、通知、来源和动作，多个现行缺陷共享此状态面。 | 按 session store、turn coordinator、recovery、presentation 拆分，唯一 session/request 归属不变。 | 跨会话并发、取消、重试、恢复和删除测试无需组件私有状态互相修补。 |
| ARCH-07 | P0 | NEXT | OPEN | 事实：`TurnExecutionStore` 是终局权威，但 complete 参数和多种 mutation 仍可被调用方自由组合。 | 引入闭合 settlement command，原子携带 PublicTurn、context/challenge/discussion/semantic-state mutation 与 guard。 | Memory/PostgreSQL 合同对所有合法/非法组合一致，部分提交、迟到完成和重复结算归零。 |
| ARCH-08 | P2 | NEXT | OPEN | 事实：多轮清理持续发现零消费者类型、配置和浅 record，缺少可执行纪律。 | 新 record 必须有生产消费者和不变量；删除前证明无反射/序列化/注册入口。 | 架构检查阻止零消费者生产 record 与未消费配置；删除清单有零引用和 packaged 启动证据。 |
| ARCH-09 | P1 | LATER | OPEN | 事实：token field policy 已冻结在 binding，但缺真实 usage/cost canary，无法证明省略/上限策略的成本边界。 | canary 只记录聚合 token/成本桶，不记录输入、输出或 Prompt；依赖真实 Provider 授权。 | 每模型/operation 验证请求 token 字段、响应 usage 与预算上限，超界阻止目录准入。 |
| ARCH-10 | P0 | NOW | OPEN | 事实：Schema、Prompt、Provider Draft compiler 与 canonical Codec 仍可能分别携带 wire-shape 知识，Comparison 已暴露漂移。 | 每个 operation 建立一个 wire-shape 权威，Prompt/request schema/compiler/Codec 只做派生或引用。 | 变更字段只改一处即驱动生成/校验；故意漂移任一消费者时编译或合同门失败。 |

## 7. 验证治理（GATE）

| ID | 严重度 | 执行序 | 状态 | 当前证据 | 修复边界/依赖 | 专属验证 / Exit Gate |
|---|---|---|---|---|---|---|
| GATE-01 | P1 | NOW | OPEN | 事实：两个 replay 检查脚本已有 UTF-8 BOM，ParserError 已消除；但 Windows PowerShell 5.1 的 `Get-Content -Raw` 仍误解码无 BOM UTF-8 目标文档，canonical checker 与 test 均退出 1。 | checker 必须显式 UTF-8 解码，或把全部目标文档统一为与 PS5.1 一致的编码；不得改弱三个安全 token。 | 在 Windows PowerShell 5.1 与 PowerShell 7 分别运行 canonical 正例和缺 token 负例；正例退出 0，负例稳定指向缺失标记。 |

## 8. 文档治理（DOC）

| ID | 严重度 | 执行序 | 状态 | 当前证据 | 修复边界/依赖 | 专属验证 / Exit Gate |
|---|---|---|---|---|---|---|
| DOC-01 | P1 | NOW | OPEN | 事实：`docs/08` 仍需与开放账本、当前模型目录、恢复层级和真实证据口径同步。 | 只写当前实现与新鲜证据，不复制实施流水。 | documentation checker 与人工 diff 证明状态、权威、恢复层级和开放边界一致。 |
| DOC-02 | P2 | NOW | OPEN | 事实：项目演进日志存在以测试总量作为成果口径的漂移风险。 | 演进日志记录行为、边界与治理裁决，不记录测试总量、制品哈希或提交元数据。 | 扫描禁止测试计数/哈希模式；本次治理记录只保留责任摘要和账本链接。 |
| DOC-03 | P1 | NOW | OPEN | 事实：`docs/00` 与 documentation checker 注册的活动文档集合可能不一致。 | 状态索引与 checker 共用明确活动集合；CURRENT_AUTHORITY/APPROVED/ACTIVE 语义一致。 | 双向检查发现索引缺项、checker 缺项、路径失效或状态冲突并非零退出。 |
| DOC-04 | P0 | NOW | OPEN | 事实：`SECURITY.md` 仍可能保留退役 Provider/global authority 叙事并与当前 ModelSelection/binding 冲突。 | 安全文档只描述当前目录、显式选择、单 Provider、无 retry/fallback 与 persistence-safe replay。 | 退役权威词扫描零命中；安全 token 与代码/机器状态正反例一致。 |
| DOC-05 | P1 | NOW | OPEN | 事实：机器状态的 PASS 可能在对应证据陈旧或发布层缺失后继续存在。 | 每个 hard invariant 绑定新鲜、具体、风险匹配的证据；整体保持 `IN_PROGRESS`。 | checker 对缺失/陈旧/不匹配证据失败，当前状态文件不含无法复核的完成声明。 |
| DOC-06 | P2 | NEXT | OPEN | 事实：token policy 在配置、binding、文档和 Provider 字段之间存在命名漂移风险。 | 一个闭合 `TokenFieldPolicy` 名称贯穿代码和文档，Provider 字段由策略派生。 | 全仓命名/退役别名扫描和两家 payload fixture 通过。 |
| DOC-07 | P1 | NOW | OPEN | 事实：活动 specs/plans 仍可能把已从开放账本删除的引用写成当前入口。 | 活动文档中的旧引用必须删除、映射到当前开放项或明确标为历史语义；非活动材料不改写。 | 扫描全部活动 specs/plans，命中均可分类；CURRENT_AUTHORITY 文件无死引用。 |

## 9. 固定执行批次

1. **证据与账本先行：** 修复 canonical replay 文档门、release 非零退出、机器状态新鲜度、活动文档引用和 Browser/spec 可达性。此批不改变生产能力。
2. **隐私、授权与结算权威：** 收口 persistence-safe replay、Provider/Binding/Readiness、Clarification reservation 和 settlement command；任何隐私或接收方错配先 fail-closed。
3. **现行交互链：** 修复 Recommendation 约束投影、会话/澄清/pending/retry/expiry、恢复 Token 与真实 scenario/Browser body。
4. **语义与表达：** 完成 Audience、subjectHint、多轮引用、Portfolio depth/Comparison/Cross-domain、General 语言/深度/caveat/pair identity。
5. **外层稳定性与清理：** 获得必要批准后执行 Qwen/GLM 独立矩阵、跨 JVM Browser、token/cost canary，最后完成退役资产零引用。Goal v2 未获 Level 3 批准前，其下游明确报告 `NOT_READY`。

## 10. 全局 Exit Gates

1. **ID 与文档结构：** 四个水位不倒退；每个开放 ID 恰有一行且字段齐全；无完成索引、实施流水、制品哈希、测试总量或第二账本；`CURRENT_AUTHORITY` 标记保留。
2. **隐私与 replay：** 首次响应、Memory/PostgreSQL settlement、同 requestId replay 覆盖全部 PublicTurn variant；visitor/provider-derived text 不落盘；三个安全标记由 Windows PowerShell 5.1 与 PowerShell 7 的 canonical 正反例保护。
3. **单权威：** Command、Goal、Plan、Execution、Projection、TurnExecutionStore、ModelSelection、OperationBinding、Runtime Readiness 和公开合同各只有一个生产权威；退役路径与兼容桥零引用。
4. **Backend：** 原失败路径、针对性单元/集成、模块架构、全量、Testcontainers、privacy、deadline/cancel/settlement 竞态门分别通过。
5. **Frontend：** 会话归属、pending、澄清、重试、恢复、来源、推荐缺口、正文质量和模型切换均有单元、类型/build 与无 warning 证据。
6. **Scenario 与 packaged Browser：** manifest 的 setup/command/expected/hard-error trace 实际执行；桌面与移动断言公开 body；同一浏览器跨真实后端重启恢复；skip 或 `NOT_READY` 不算 PASS。
7. **Provider：** 仅在明确授权后，每个批准 Provider 独立执行 Goal、General、Comparison、Clarification 与受控慢响应；报告 schema/semantic/终局/超时/延迟与安全聚合成本，不记录正文。一次成功或另一家的结果不能替代。
8. **发布与状态：** canonical release 对任一 `FAILED`、`NOT_RUN`、`IN_PROGRESS` 或必需层缺失均非零退出；architecture、documentation、privacy、replay 专项和 `git diff --check` 通过；机器状态仍为 `IN_PROGRESS`，直至所有硬不变量及开放责任真正完成。
