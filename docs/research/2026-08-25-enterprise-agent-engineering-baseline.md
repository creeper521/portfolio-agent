# 企业级 Agent 工程基线与 Portfolio Agent 校验

> 研究日期：2026-08-25
> 研究目标：用官方一手资料回答两个问题：当前项目是不是“真正的 Agent”；若把它作为秋招作品集，距离真实企业业务 Agent 还缺什么。
> 资料范围：Anthropic、OpenAI、LangGraph、A2A、MCP、NIST、OWASP、vLLM、Milvus、pgvector 的官方文档、规范或官方源码仓库；项目内 `AGENTS.md`、现行架构状态和主状态文档。
> 结论类型：本文显式区分 **事实**、**推论** 与 **评估标尺**。外部资料链接均为可点击的一手来源。

## 一句话结论

当前项目不是“Prompt + API + 聊天页面”的玩具：它已经具备闭合 Goal、类型化 Plan/Execution、确定性与模型路径、PostgreSQL 短期状态、幂等、取消、澄清恢复、隐私约束和多层验证，因此可以诚实地称为一个**有边界、可审计的作品集领域 Agent**。

但按照严格的 Agent 定义和企业业务验收标准，它尚不能泛称为“企业级自主业务 Agent”或“Agent 平台”：当前公开能力主要是读取公开作品集证据并生成解释/建议，没有外部业务系统写入、按风险分级的人工审批、企业身份与租户权限、可恢复的长任务、已部署 SLO/监控/压测，以及由真实业务终态证明的生产效果。

更准确的技术分类是：**模型参与语义决策、后端负责可靠执行的 bounded hybrid agentic system（有边界的混合式 Agent 系统）**。它的优势正是边界明确，不应为了简历标签强行加入开放式 ReAct、多 Agent、MCP、A2A、Milvus 或 vLLM。

## 1. 先统一“什么才算 Agent”

### 1.1 官方定义并不完全一致

**事实：** Anthropic 把 workflow 与 agent 明确区分：workflow 的模型和工具沿预先定义的代码路径运行；agent 则由模型动态决定过程和工具使用。官方同时建议从简单、可组合的模式开始，只有复杂度确实改善结果时才升级，并提醒框架可能遮蔽底层 prompt、response 与调试过程。[Anthropic：Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)

**事实：** OpenAI 的宽口径定义强调 agent 能代表用户完成任务，包含规划、工具调用和多步状态；真实运行时是“模型输出—工具/交接—再回到模型—最终结果”的循环。[OpenAI：Build agents](https://developers.openai.com/api/docs/guides/agents)；[OpenAI：Run agents](https://developers.openai.com/api/docs/guides/agents/running-agents)

**事实：** Anthropic 2026 年关于可信 Agent 的研究进一步采用较严格的自导循环定义：系统能规划、行动、观察结果并调整，工具、执行环境与分层防护都是系统的一部分。[Anthropic：Trustworthy agents](https://www.anthropic.com/research/trustworthy-agents)

**推论：** “是不是 Agent”不是一个只看是否调用 LLM 的二元问题，至少要区分三类：

1. **LLM 功能/聊天壳**：一次生成或固定问答，无持续任务状态和环境反馈。
2. **Agentic workflow / bounded agent**：模型参与意图、目标或局部决策，系统以预定义的类型化步骤、状态机和工具完成任务。
3. **Autonomous tool-using agent**：模型在约束范围内动态选择工具、观察环境结果、修订计划并循环，直到达到终态或停止条件。

**评估标尺：** 简历不必抢占最宽泛的标签。能准确说明“模型控制什么、代码控制什么、工具拥有什么权限、何时停止、如何验证终态”，比笼统写“基于 ReAct 的企业级 Agent”更可信。

### 1.2 当前项目属于哪一类

**项目事实：** 现行文档规定唯一执行链为 `Command → Goal → Plan → Execution → PublicAgentTurn → Settlement`；模型负责把自由输入收敛成闭合 Goal/语义路由，后端以类型化合同和状态机执行；公开运行时只消费审查后的公开投影，不读取私人 Obsidian；持久状态使用 PostgreSQL，受短期保留、加密、重放和敏感字段禁存约束。依据：[AGENTS.md](../../AGENTS.md)、[当前实现状态](../08-%E5%BD%93%E5%89%8D%E5%AE%9E%E7%8E%B0%E7%8A%B6%E6%80%81.md)、[Agent 2.0 主状态文档](../15-Agent%202.0%E7%9C%9F%E5%AE%9E%E4%BA%A4%E4%BA%92%E9%97%AE%E9%A2%98%E6%B8%85%E5%8D%95%E4%B8%8E%E4%BF%AE%E5%A4%8D%E8%BE%B9%E7%95%8C.md)、[架构状态](../agent-architecture-status.json)。

**推论：** 它已经越过“聊天壳”门槛，但模型并不拥有开放工具注册表，也没有动态工具循环和外部业务写入。因此按 Anthropic 的严格区分，它更接近第 2 类；按 OpenAI 的宽口径，它可称为 bounded agent。两种判断并不矛盾。

## 2. 企业级 Agent 的十项工程基线

这里的“企业级”不是框架或数据库品牌，而是可被验收的系统性质。

| 维度 | 官方事实 | 可执行的评估标尺 |
| --- | --- | --- |
| 1. 业务终态 | Anthropic 建议 Agent 用在既有开放性、又有明确成功标准、反馈循环和人工监督的任务；客服 Agent 的价值来自同时理解对话、调用数据/动作并以解决结果衡量，而非仅生成回复。[Building effective agents](https://www.anthropic.com/engineering/building-effective-agents) | 明确谁触发、读什么、写什么、成功后哪个业务对象发生了什么变化；用数据库/API/工单终态验证，而非让模型自报“已完成”。 |
| 2. Agent 必要性与停止条件 | Agent 用灵活性换取成本、延迟和累积错误；应从最简单方案开始。[Building effective agents](https://www.anthropic.com/engineering/building-effective-agents) | 与规则、单次 LLM、固定 workflow 做质量/成本/延迟对照；设置最大步数、时间、token/费用、重试和失败收敛条件。 |
| 3. 持久状态与恢复 | LangGraph 将 durable execution、persistence 和 HITL 作为长时有状态 Agent 的核心；interrupt 恢复时节点会从头执行，所以中断前副作用必须幂等、后移或拆分。[LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)；[Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)；[Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts) | 杀进程、网络超时、重复消息、重复恢复、跨天审批都能从持久检查点安全继续；写操作使用幂等键、outbox/事务或补偿机制。 |
| 4. 人工控制 | OpenAI 把敏感工具的人工审查定义为可暂停、批准或拒绝的运行状态，典型对象包括取消、编辑、shell 与有副作用的 MCP 调用。[Guardrails and approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals) | 不只做“确认弹窗”：审批必须绑定工具名、完整参数、业务对象、风险原因、call ID、版本和过期时间；拒绝/修改/超时后路径可恢复且不会重复执行。 |
| 5. 身份、权限与凭证 | MCP HTTP 授权要求 OAuth 2.1、resource/audience 绑定，服务端必须验证 token 目标且禁止 token passthrough。[MCP Authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization) | 每个用户、Agent 和工具采用最小权限、短期凭证与明确 audience；测试跨租户、越权、混淆代理和凭证泄漏；模型上下文、日志与 sandbox 不出现长期密钥。 |
| 6. Prompt injection 与工具安全 | OpenAI 建议把不可信数据与高优先级指令隔离、在节点间使用结构化输出，并组合 approvals、guardrails、trace graders 与 evals；该页当前已标注 Agent Builder deprecated，因此这里只引用安全原则，不把它作为新产品选型建议。[Agent safety](https://developers.openai.com/api/docs/guides/agent-builder-safety)；OWASP 2026 将目标劫持、工具误用、身份权限滥用、供应链、意外代码执行、记忆污染、Agent 间通信、级联失败、信任利用和 rogue agents 列为主要风险族。[OWASP Agentic Top 10](https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/) | 为模型、memory、tools、身份、通信、执行环境和人工界面画威胁模型；逐项记录攻击路径、预防/检测控制、残余风险和红队证据；高影响动作 fail closed。 |
| 7. 轨迹观测与评估 | OpenAI tracing 覆盖模型、工具、handoff、guardrail 和自定义 span；trace eval 可评估工具选择、参数、顺序、路由和策略，而不仅是最终文本。[Observability](https://developers.openai.com/api/docs/guides/agents/integrations-observability)；[Agent evals](https://developers.openai.com/api/docs/guides/agent-evals)；Anthropic 也区分 task、attempt、grader、trace、outcome 和 harness，并强调实际环境终态才是 outcome。[Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents) | 每次运行能关联 goal、plan、模型/工具版本、输入摘要、工具参数与结果、重试、审批、终态和耗时成本；评估集版本化，覆盖正常、边界、对抗、故障和回归样本。 |
| 8. 治理与生命周期 | NIST AI RMF 以 Govern、Map、Measure、Manage 管理全生命周期风险；NIST 2026 Agent Standards Initiative 正在推进 Agent 身份、授权、互操作与安全评测，说明行业仍处于标准形成期。[NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework)；[NIST AI Agent Standards Initiative](https://www.nist.gov/artificial-intelligence/ai-agent-standards-initiative) | 有用途/禁用边界、资产清单、风险 owner、上线门、持续监测、事件响应、供应商依赖和退役流程。不要声称通过一个尚不存在的统一“企业 Agent 认证”。 |
| 9. 数据检索与租户隔离 | pgvector 支持 exact/ANN、PostgreSQL 的 ACID/PITR/JOIN/WAL；官方提醒近似索引过滤可能结果不足，共享多租户索引会让租户数据相互影响 recall/速度，可用 iterative scan、分区或独立表。[pgvector 官方仓库](https://github.com/pgvector/pgvector)；Milvus 则提供 standalone/distributed 形态、RBAC、TLS 和多种租户隔离模式。[Milvus install overview](https://milvus.io/docs/install-overview.md)；[Milvus RBAC](https://milvus.io/docs/rbac.md) | 选型由数据量、QPS、Recall@K/MRR、metadata filter、租户隔离、HA、备份恢复和运维能力决定；以 exact search 为质量基线测 ANN，而不是按一个网络流传条数阈值换库。 |
| 10. 推理与互操作基础设施 | vLLM 提供健康检查、负载和 Prometheus 指标，其生产扩展涉及 data/tensor/pipeline parallel、KV cache、队列和跨节点故障。[vLLM Metrics](https://docs.vllm.ai/en/latest/usage/metrics/)；[Parallelism and scaling](https://docs.vllm.ai/en/latest/serving/parallelism_scaling/)；MCP 标准化模型与工具上下文，A2A 标准化不同 Agent 的能力发现、任务生命周期、流式/长任务和认证。[MCP Tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)；[A2A v1.0 Specification](https://a2a-protocol.org/v1.0.0/specification) | 自托管模型要交付 TTFT、吞吐、P95/P99、排队、KV cache、GPU 利用率、扩缩容和故障证据；只有确有跨工具/跨 Agent 互操作需求时才采用 MCP/A2A，并测试协议版本、schema、认证和错误语义。 |

> 时间说明：Anthropic《Building effective agents》发布于 2024-12-19，NIST AI RMF 1.0 发布于 2023；它们是 2025–2026 官方工程实践仍在引用的基础资料，不应误标为 2025–2026 新标准。2026 年的新变化包括 NIST Agent Standards Initiative、OWASP Agentic Top 10 与更加明确的 session/harness/sandbox 解耦实践。[Anthropic：Scaling Managed Agents](https://www.anthropic.com/engineering/managed-agents)

## 3. 对当前项目逐项校验

评分只表示**现有仓库可证明的成熟度**，不评价未来设计：`0=缺失`，`1=有边界的本地实现/部分满足`，`2=可信工程实现且有本地证据`，`3=有真实部署与持续生产证据`，`N/A=当前产品边界不需要`。不建议把分数相加制造伪精确总分。

| 维度 | 当前评分 | 仓库事实 | 结论 |
| --- | ---: | --- | --- |
| Agent 必要性与业务终态 | 1 | 业务耦合是公开作品集证据解释、案例检查和建议生成；公开写操作仅 cancel/clear，没有 CRM、工单、合同或报表等业务对象终态。 | 对作品集成立；对企业业务闭环不足。 |
| 模型决策与环境反馈闭环 | 1 | 模型参与 Goal/语义路由，后端确定性编译 Plan；没有开放 Tool Registry、动态工具选择、自主观察—修订循环。 | 是 bounded agentic workflow / hybrid agent，不是开放式 autonomous agent。 |
| 状态、幂等、取消与恢复 | 2 | PostgreSQL Agent State、typed clarification、idempotency replay、deadline、cancel、rate limit、加密短期状态和 version rollback 均已有明确合同与验证。 | 当前最强的工程亮点；仍缺跨进程长任务/长期审批和生产故障演练。 |
| 人工控制与高风险写入 | 1 | 有澄清、取消和 fail-closed；当前没有高风险业务写工具，所以不存在按工具参数审批的完整链路。 | 不是现有隐患，但若扩为企业动作 Agent，必须先设计 approval/policy/idempotency。 |
| 隐私与数据边界 | 2 | runtime 只消费 reviewed public projection，不读取私人 Obsidian；禁止保存访客问题、prompt 与 raw model output；状态加密且短期保留。 | 对公开作品集场景很有说服力。 |
| 企业身份、RBAC 与租户 | 0 | 现行边界明确没有账号、权限、多租户和私有 copilot。 | 不能据此声称企业 SaaS、企业知识 Agent 或生产 MCP 工具平台。 |
| 可观测与审计 | 1 | 有诊断、状态机、settlement 与多层 gate，但现有资料没有证明一套部署中的端到端 trace、敏感数据治理、告警和事件响应。 | 需要把“能排查”升级为“每次决策和副作用都有可关联证据”。 |
| Eval 与回归 | 2 | 已有 Provider、Browser、PostgreSQL、scenario 等验证资产；但架构状态仍为 `IN_PROGRESS`，`evidenceBeforeCompletion` 为 `FAILED`，且主状态文档记录真实 Provider、Browser 语义和 runtime scenario 仍未全部闭环。 | 资产丰富，不能把存在测试文件等同于完成验证；先关闭现有红项。 |
| 检索与数据层 | 2 | 使用 PostgreSQL 16/pgvector，公开数据规模小且需与关系数据、治理投影共同维护。 | 当前选型比例合适；缺的是 recall/filter/latency/故障基准，不是换成 Milvus。 |
| 部署、容量与生产 SLO | 1 | 有本地 JAR/前后端/数据库启动与容器化验证资料，但文档明确没有生产部署。 | 不能写“支撑高并发生产流量、线上 SLA、生产降本”等无证据表述。 |
| MCP/A2A/多 Agent | N/A | 现行边界明确不做 MCP、开放式 ReAct、多 Agent 和 durable tasks。 | 对单体公开 Portfolio Agent 不是缺陷；只有新增企业互操作需求后才进入评分。 |

**总评推论：**

- 作为“个人作品集 Agent”，真实性较强，核心差异点是**类型化权威、可靠状态与隐私边界**，不是 UI 或 prompt。
- 作为“企业级 Agent 平台”，证据不足，最大缺口不是向量库/框架品牌，而是**真实业务动作闭环、身份权限、审批、可恢复副作用、轨迹评估与生产运行证据**。
- 当前架构状态主动标记 `IN_PROGRESS` 是诚实的；秋招前最有价值的工作是收敛红项并新增一个可验证的企业业务切片，而不是铺更多名词。

## 4. 对常见“面试雷点”的逐条复核

### 4.1 “Chroma/Faiss 一定不企业级，百万以下 pgvector、百万以上 Milvus”

**判断：前半句有场景意义，固定阈值没有官方依据。**

pgvector 官方提供 exact/ANN、过滤、分区、ACID、PITR、复制和多租户建议；Milvus 官方把 Lite、Standalone、Distributed 分别定位到从本地/小规模到大规模分布式场景，并提供独立扩展、RBAC/TLS/租户隔离。品牌不能替代真实 workload benchmark。当前项目的公开证据集规模很小，且需要 PostgreSQL 事务与治理投影，pgvector 是更容易讲清的比例化选择。

面试应回答：数据规模、维度、QPS、过滤选择性、exact baseline、Recall@K/MRR、P95、索引构建/内存、租户隔离、备份恢复；不要背“百万条分界线”。

### 4.2 “不用 vLLM，自接 API 就不企业级”

**判断：错误。**

企业会在托管 API、专属云、自托管之间按数据合规、延迟、吞吐、成本、模型能力和运维能力选择。当前项目选择 GLM/Qwen API 并在 configured-model failure 时 fail closed，是合理的应用层设计。只有确实自托管时，vLLM 才要求额外证明 TTFT、吞吐、尾延迟、KV cache、排队、GPU 容量、扩缩容和故障恢复。没有这些证据，写“vLLM 私有化部署”反而会引出无法回答的追问。

### 4.3 “调用 API、写 Prompt 可以当核心亮点”

**判断：确实不够。**

当前项目应把亮点放在自由输入如何收敛为闭合 Goal、单权威合同如何跨前后端、确定性与模型路径如何分工、如何做幂等 settlement、加密 replay、隐私治理、失败收敛和 scenario/eval。Prompt 和 provider adapter 是必要实现细节，不是项目壁垒。

### 4.4 “聊天界面就是聊天机器人，所以项目是玩具”

**判断：界面形态不是决定因素，但当前业务闭环确实偏弱。**

客服 Agent 同样可以是对话界面，关键是它是否读取权限内的业务数据、执行受控动作并以真实解决结果验收。当前项目虽然有作品集领域证据与状态机，不是通用闲聊机器人，但仍以解释/建议为主，没有企业业务对象写入。这是最值得补的一刀。

### 4.5 “加 LangChain/Haystack/ReAct 就会更真实”

**判断：错误。**

Anthropic 官方明确建议从简单模式开始，并警告框架会增加抽象、遮蔽底层行为。LangGraph 的价值是 durable execution、checkpoint 和 HITL 等实际语义，不是 import 名字；ReAct 的价值是动态工具反馈循环，不是 prompt 模板；MCP/A2A 的价值是互操作协议，不是 Agent 身份证。没有业务必要性和验收证据的框架只会增加面试攻击面。

## 5. 推荐的企业业务切片：受控工单处置 Agent

如果要让作品集显著接近真实企业场景，建议新增**独立的企业案例/演示边界**，而不是把现有公开 Portfolio Agent 直接扩成无限权限平台。一个足够小但有技术含量的切片是“IT/客户工单分诊与受控处置 Agent”。

### 5.1 最小业务闭环

```text
工单事件/用户请求
    ↓
身份、租户、用途与策略校验
    ↓
类型化 Goal + 风险等级 + 成功终态
    ↓
读取工具：工单、CMDB/客户档案、知识库、日志/指标
    ↓
模型提出 Plan；策略层裁剪为允许的 Tool Calls
    ↓
低风险只读分析 ───────────────┐
高风险写操作 → 参数级人工审批 ├→ 幂等 Command / Outbox → 下游系统
需补信息 → 持久 interrupt        │
    ↑                             ↓
    └──────── resume ← 验证真实下游终态
                                  ↓
                     Settlement + 审计 + 业务指标
```

可演示动作不要贪多：读取一条工单与相关资产；给出有证据的分类和优先级；建议负责人；经人工批准后更新工单标签/状态；随后重新读取工单，验证更新真实存在。拒绝、超时、重复提交、下游 5xx、模型输出越权、prompt injection 和审批后版本变化都必须有明确结果。

### 5.2 需要新增的工程合同

1. `TenantPrincipal / Actor`：谁以哪个租户、角色和 scope 发起。
2. `ToolDescriptor / ToolCall`：固定 schema、风险等级、幂等语义、timeout、数据分类和所需权限。
3. `PolicyDecision`：allow / deny / require_approval，且不由模型自行决定最终权限。
4. `ApprovalRequest`：绑定完整参数、call ID、plan/tool/model/policy 版本、过期时间和审批人。
5. `DurableCheckpoint`：能在进程重启和跨天等待后恢复；副作用节点可安全重放。
6. `BusinessOutcome`：由下游系统读取或事件确认，不接受模型文本作为完成证明。
7. `AgentTrace`：关联 goal、plan、tool、approval、retry、settlement、耗时、token/费用和敏感字段处理。
8. `EvalCase`：输入、环境 fixture、允许轨迹、禁止动作、预期终态和 grader。

### 5.3 可在面试中量化的指标

- 任务终态成功率、错误写入率（高风险动作目标应为 0）、人工驳回/改写率。
- 分诊准确率、证据引用覆盖、升级/转人工 precision 与 recall。
- 直通率、平均处理时间、P95 总延迟、审批等待时间、单任务 token/费用。
- 工具选择/参数/顺序正确率，故障恢复率，重复副作用数。
- prompt injection 越权成功率、跨租户泄漏数、敏感数据进入 trace 的违规数。

## 6. 秋招前的优先级

### P0：先把已经承诺的证据闭环

- 关闭架构状态中的 `evidenceBeforeCompletion=FAILED`。
- 跑通并保存真实 Provider、Browser 语义、PostgreSQL 与 runtime scenario 的同轮证据。
- 建立 20–50 条版本化任务集，至少覆盖正常、澄清、无证据、取消、超时、provider failure、注入和重放；同时评最终 outcome 与 trajectory。

### P1：补一个“真实业务动作”垂直切片

- 只做 2–4 个有 schema 的工单工具，其中至少一个是受控写操作。
- 同时交付身份/RBAC、参数级审批、幂等/outbox、恢复、终态验证和审计；否则宁可不做写入。
- 让它作为独立 case study 或模块存在，先走 Level 3 设计批准，不破坏现有公开单权威与隐私边界。

### P2：补容量、观测和安全证据

- 给 API/数据库/检索建立 P50/P95/P99、错误率、cost/task、Recall@K 与过滤正确率基线。
- 让每次 agent turn 具备端到端 trace，并定义脱敏、保留、访问和事件响应。
- 用 OWASP Agentic Top 10 做一份小型威胁模型与红队矩阵。

### P3：仅在需求出现时升级基础设施

- 跨第三方工具互操作再加 MCP；跨独立 Agent/厂商协作再加 A2A。
- 需要长时任务/HITL 再评估 LangGraph 或自研 durable runtime。
- 数据与 QPS 达到 pgvector 实测瓶颈再评估 Milvus。
- 合规、成本或吞吐要求支持自托管时再评估 vLLM。

## 7. 简历与面试表述

### 可以写

> 设计并实现面向公开作品集证据的有边界 Agent：将自然语言请求收敛为类型化 Goal，由后端确定性编译 Plan/Execution；以 PostgreSQL + AES-GCM 短期状态实现幂等 replay、取消、澄清恢复与隐私安全，并通过共享合同、Provider/Browser/PostgreSQL gates 和 scenario runner 验证生命周期行为。

如果完成推荐的工单切片，可再增加：

> 为受控工单写操作设计最小权限 Tool Contract、参数级人工审批、幂等 outbox、崩溃恢复与下游终态验证；以轨迹评估和业务 outcome 同时衡量工具选择与实际处置结果。

### 暂时不要写

- “企业级通用 Agent 平台”或“自主规划并调用任意工具”。
- “生产级高并发、线上 SLA、显著降本增效”。
- “大规模 RAG / 百万级向量检索”。
- “vLLM 私有化集群、Milvus 分布式、多租户 Agent SaaS”。
- “多 Agent 协作、MCP/A2A 生产互操作”。

这些都不是方向错误，而是当前仓库没有相应的业务、容量、权限、故障和生产证据。面试中主动说明边界，比用技术名词把项目包装得更大更专业。

## 8. 面试官最可能继续追问的十个问题

1. 为什么这是 Agent，而不是分类器加固定 workflow？模型实际拥有哪一部分决策权？
2. 如果模型目标错了、工具参数越权或一直循环，在哪里被阻止？
3. 你如何证明任务完成：看最终文本，还是读真实业务终态？
4. 服务在写操作前/后崩溃，重试会不会重复执行？
5. 审批等待一天后，prompt、tool 或 policy 版本已变化，如何安全恢复？
6. prompt injection 能否通过检索内容进入高优先级指令或工具参数？
7. 为什么选择 pgvector；真实 Recall@K、metadata filter P95 和数据规模是多少？
8. 为什么没有 LangGraph/MCP/A2A/vLLM；什么条件出现时你才会引入？
9. eval 测的是最终文案，还是工具选择、参数、顺序、停止行为与业务 outcome？
10. 哪些是本地验证，哪些是线上数据；没有生产部署时你如何避免夸大？

如果能用代码位置、状态合同、失败用例和同轮验证结果回答这十个问题，这个项目作为秋招 Agent 作品集会比单纯堆框架名更可信。

## 9. 主要一手来源索引

- Anthropic：[Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)、[Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)、[Scaling Managed Agents](https://www.anthropic.com/engineering/managed-agents)、[Trustworthy agents](https://www.anthropic.com/research/trustworthy-agents)
- OpenAI：[Agents guide](https://developers.openai.com/api/docs/guides/agents)、[Running agents](https://developers.openai.com/api/docs/guides/agents/running-agents)、[Guardrails and approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)、[Observability](https://developers.openai.com/api/docs/guides/agents/integrations-observability)、[Agent evals](https://developers.openai.com/api/docs/guides/agent-evals)、[Agent safety](https://developers.openai.com/api/docs/guides/agent-builder-safety)
- LangGraph：[Overview](https://docs.langchain.com/oss/python/langgraph/overview)、[Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)、[Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)
- Open protocols：[MCP Specification](https://modelcontextprotocol.io/specification/2025-11-25)、[A2A v1.0 Specification](https://a2a-protocol.org/v1.0.0/specification)
- Risk and security：[NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework)、[NIST AI Agent Standards Initiative](https://www.nist.gov/artificial-intelligence/ai-agent-standards-initiative)、[OWASP Agentic Top 10](https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/)
- Infrastructure：[vLLM docs](https://docs.vllm.ai/)、[pgvector](https://github.com/pgvector/pgvector)、[Milvus docs](https://milvus.io/docs/)
