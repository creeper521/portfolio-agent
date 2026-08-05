# Agent 框架与“反框架”设计研究

> 研究日期：2026-08-04  
> 资料范围：只采用厂商官方文档、项目官方仓库、官方架构说明。本文中的“综合判断”是基于这些一手资料作出的推论，不代表某一家厂商的原话。

## 一句话结论

“优秀 Agent 都在反框架”不是一个准确的行业事实。更准确的说法是：**优秀 Agent 越来越反对不必要、不可见、难调试的高层抽象，但并不反对 SDK、运行时、工具协议、持久化、可观测性和安全基础设施。**

真正形成共识的是“从最小可行控制循环开始，复杂度必须由已验证的问题推动”。Anthropic 明确建议优先用简单、可组合模式和直接 API；OpenAI 的官方方案则一边允许直接拥有循环，一边提供“少量原语”的轻量 Agents SDK；LangGraph、Google ADK 和 AutoGen 的官方定位说明，框架在长运行、可恢复、多人协作、HITL、状态持久化和分布式执行时仍然有明确价值。

因此，当前项目没有采用 Spring AI、LangChain4j 或 LangGraph4j，并不天然落后，也不能仅凭“无框架”判断设计先进。关键是看项目是否已经清楚地拥有并验证了：控制循环、工具契约、状态边界、停止条件、错误恢复、安全策略、可观测性和评测。如果这些只是散落在业务代码里的隐式约定，那么它不是“反框架优势”，而是在自行维护一个未命名的内部框架。

## 1. 先拆开四个经常混用的概念

### 1.1 模型/工具 SDK

它解决“怎么调用模型、描述工具、解析 tool call、处理流式响应”等协议和传输问题。它不一定替你决定 Agent 的控制流。

Spring AI 官方把自身能力拆为可移植的 Model API、Vector Store API、Tool Calling、ChatClient、Advisors、MCP 和自动配置；其中还明确提供用户自行控制工具执行生命周期的模式。也就是说，**使用 Spring AI 的低层 API，不等于把 Agent 交给框架编排。**  
来源：[Spring AI API](https://docs.spring.io/spring-ai/reference/api/)；[Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)（访问日期：2026-08-04）。

LangChain4j 官方则把自己定义为 JVM 上构建 LLM 应用的 Java 库，提供模型和向量库统一 API，并覆盖 tool calling、MCP、agents 与 RAG。它同时包含“供应商适配层”和“更高层 Agent 能力”，不能把整个项目简单归类成单一编排框架。  
来源：[LangChain4j 官方仓库](https://github.com/langchain4j/langchain4j)（访问日期：2026-08-04）。

### 1.2 Agent loop（Agent 控制循环）

最小 Agent loop 通常是：把指令、上下文和工具交给模型；模型决定回复或调用工具；系统执行工具并把环境结果返回；重复直到完成、失败、超时、达到轮次/成本上限或等待人工。

OpenAI 官方指南称 while loop 是 Agent 运作的中心，并把单 Agent 定义为“一个模型带工具和指令，在循环中执行工作流”。Anthropic 也把 Agent 的典型实现概括为“LLM 根据环境反馈在循环中使用工具”。  
来源：[OpenAI《A practical guide to building agents》](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents/)；[Anthropic《Building effective agents》](https://www.anthropic.com/engineering/building-effective-agents)（访问日期：2026-08-04）。

**自己写 Agent loop 是无高层框架，不是无架构。** 循环仍然要定义状态、工具分发、重试、超时、取消、幂等性、敏感操作审批、上下文裁剪、模型错误和终止语义。

### 1.3 Workflow（工作流）

Anthropic 与 LangGraph 官方都给出相同的关键区分：workflow 的代码路径预先确定；agent 则由模型动态决定过程和工具使用。Prompt chaining、routing、parallelization、orchestrator-workers、evaluator-optimizer 都可视为常见工作流模式；它们可以只用普通代码实现，也可以由图编排框架承载。  
来源：[Anthropic《Building effective agents》](https://www.anthropic.com/engineering/building-effective-agents)；[LangGraph Workflows and agents](https://docs.langchain.com/oss/python/langgraph/workflows-agents)（访问日期：2026-08-04）。

因此，“用了图”不一定就是 Agent，“没用图”也不表示没有 workflow。决定性的区别是下一步由预定义代码还是模型动态选择。

### 1.4 Orchestration framework/runtime（编排框架/运行时）

这类系统主要解决执行层能力：状态机或图、检查点、暂停/恢复、长运行、并发、HITL、流式事件、持久化、跨进程调度和轨迹调试。

LangGraph 官方把自己定位为长运行、有状态 Agent 的低层编排框架和运行时，并明确说它不抽象 prompt 或具体 Agent 架构；LangChain 则是更高层的模型、工具和 Agent loop 框架。LangGraph 还可以脱离 LangChain 使用。  
来源：[LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)；[Frameworks, runtimes, and harnesses](https://docs.langchain.com/oss/python/concepts/products)（访问日期：2026-08-04）。

LangGraph4j 官方定位则是 Java 的 stateful multi-agent workflow 编排框架，提供 StateGraph、条件边、异步/流式、checkpoint、breakpoint、子图和并行节点等能力。它解决的不是“能否调用一次模型”，而是复杂状态执行。  
来源：[LangGraph4j 官方文档](https://langgraph4j.github.io/langgraph4j/)（访问日期：2026-08-04）。

## 2. “反框架”观点真正反对什么

### 2.1 反对过早把简单循环建模成复杂图

Anthropic 的建议是先找最简单方案，仅在必要时增加复杂度；很多应用甚至单次 LLM 调用加检索和示例就够了。它特别提醒框架容易诱导开发者在简单方案足够时继续增加复杂度。  
来源：[Anthropic《Building effective agents》](https://www.anthropic.com/engineering/building-effective-agents)（访问日期：2026-08-04）。

OpenAI 也建议先最大化单 Agent 能力；多 Agent 虽可分离概念，但会增加复杂度和开销。官方给出的升级信号不是“看起来复杂”，而是单 Agent 已经无法遵循复杂指令，或因为工具相似/重叠而稳定地选错工具。  
来源：[OpenAI《A practical guide to building agents》](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents/)（访问日期：2026-08-04）。

综合判断：反框架首先是反“拓扑先行”。应该由失败数据证明需要图、分支、多 Agent，而不是先选框架再把业务塞进节点和边。

### 2.2 反对隐藏 prompt、响应和真实控制流

Anthropic 明确指出，高层框架的额外抽象会遮蔽底层 prompt 与 response，使调试更困难；如果使用框架，开发者仍应理解底层代码，因为对“黑箱里发生了什么”的错误假设是常见错误来源。  
来源：[Anthropic《Building effective agents》](https://www.anthropic.com/engineering/building-effective-agents)（访问日期：2026-08-04）。

mini-SWE-agent 的官方说明把线性 history 视为关键优点：轨迹就是传给模型的 messages，易调试、易用于微调；它用 bash 作为唯一工具、用独立的 `subprocess.run` 执行动作，以降低脚手架复杂度。  
来源：[mini-SWE-agent 官方仓库](https://github.com/SWE-agent/mini-swe-agent)（访问日期：2026-08-04）。

综合判断：所谓“反框架”很大一部分是**反不可审计**。如果使用一个 SDK 后仍能完整看到每轮输入、模型输出、工具参数、工具结果、状态变化和退出原因，那么它与这条原则并不冲突。

### 2.3 反对通用抽象侵入领域模型

Agent 框架常提供自己的 `Agent`、`Message`、`Memory`、`State`、`Tool`、`Node`、`Edge` 等类型。如果这些类型穿透到领域层，切换模型供应商、改变控制流、写确定性测试或复用业务服务都会变难。

Spring AI 的工具文档其实展示了一个重要反例：它同时支持 framework-controlled、advisor-controlled 和 user-controlled 三种工具执行方式。框架能力可以被限制在适配器层，应用仍可拥有循环。  
来源：[Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)（访问日期：2026-08-04）。

综合判断：需要反对的是“框架类型成为业务事实”，而不是所有第三方接口。健康的依赖方向是领域能力定义端口，供应商 SDK 或框架在基础设施侧实现端口。

### 2.4 反对为当前模型能力保留过时脚手架

SWE-agent 团队解释，2024 年曾强调专用工具和特殊接口，但随着模型能力提升，很多脚手架已非必要；团队现在推荐更简单的 mini-SWE-agent，并称它以约 100 行 Agent 类、bash-only 工具和线性历史取得很强的 SWE-bench Verified 表现。原 SWE-agent 仓库也声明主要开发投入已转向 mini-SWE-agent，并推荐默认使用后者。  
来源：[mini-SWE-agent 官方仓库](https://github.com/SWE-agent/mini-swe-agent)；[SWE-agent 官方仓库](https://github.com/SWE-agent/SWE-agent)（访问日期：2026-08-04）。

这说明“强模型 + 好环境反馈 + 小循环”可能超过精巧脚手架。但这是一类 coding-agent 的重要实例，不足以推出所有业务 Agent 都应当 bash-only，更不能推出持久化、审批和权限系统没有价值。

### 2.5 反对把多 Agent 当作默认答案

AutoGen 是典型多 Agent 框架，但其官方文档同样建议：简单任务从单 Agent 开始，先优化工具和指令；只有单 Agent 被证明不够时才切换到团队，因为团队需要更多 steering 和 scaffolding。  
来源：[AutoGen Teams](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/teams.html)（访问日期：2026-08-04）。

这进一步说明“先简单后复杂”不是反框架阵营独有的口号，而是框架官方自己也承认的工程原则。

## 3. 一手资料实际显示的不是二元对立

| 项目/厂商 | 官方主张 | 它反对或避免的东西 | 它仍然提供/认可的框架价值 |
|---|---|---|---|
| Anthropic | 从直接 API 和简单可组合模式开始 | 复杂框架、不可见 prompt/response、无证据的复杂度 | 框架可简化模型调用、工具解析和链式调用；生产阶段可按需要使用，但应理解底层 |
| OpenAI | 可直接拥有 loop，也可用“少量原语”的 Agents SDK | 过早多 Agent、声明式图在动态流程中的繁琐 | 自动 turns、工具执行、guardrails、handoff、session、HITL、tracing |
| LangGraph | 低层、可独立使用，不抽象 prompt/Agent 架构 | 把所有问题都塞进高层 Agent API | durable execution、checkpoint、streaming、HITL、persistence、debugging |
| Google ADK | code-first、模块化、精确控制 | 与具体模型/部署环境强绑定 | 多 Agent、顺序/并行/循环工作流、动态路由、状态、评测、开发 UI、部署 |
| AutoGen | AgentChat 便于原型，Core 面向事件驱动和分布式系统 | 简单任务直接上团队 | termination、state、HITL、团队模式、事件驱动 runtime、分布式 agent |
| mini-SWE-agent | Agent 类极简、bash-only、线性历史 | 特制工具、复杂 history processor、黑箱脚手架 | 仍使用模型适配、环境隔离、运行脚本、轨迹浏览、配置和测试 |

表中来源：  
[OpenAI Agents SDK](https://openai.github.io/openai-agents-python/)；[OpenAI Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/)；[LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)；[Google ADK 发布说明](https://developers.googleblog.com/agent-development-kit-easy-to-build-multi-agent-applications/)；[AutoGen overview](https://microsoft.github.io/autogen/stable/index.html)；[mini-SWE-agent 官方仓库](https://github.com/SWE-agent/mini-swe-agent)（访问日期：2026-08-04）。

值得注意的是，框架本身也在吸收“反框架”批评：OpenAI Agents SDK 宣称少量原语、Python-first，并明确区分何时直接用 Responses API；LangGraph 提供 Functional API，让现有 `if`、`for` 和函数控制流在不强制 DAG 重构的情况下获得 persistence、HITL 和 streaming；Google ADK 强调 code-first；Spring AI 允许 user-controlled tool execution。  
来源：[OpenAI Agents SDK](https://openai.github.io/openai-agents-python/)；[LangGraph Functional API](https://docs.langchain.com/oss/python/langgraph/functional-api)；[Google ADK 官方仓库](https://github.com/google/adk-python)；[Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)（访问日期：2026-08-04）。

## 4. 为什么一些优秀 coding agent 能极简

coding agent 有几个特殊条件，使“轻脚手架”特别有效：

1. **环境反馈强。** 编译、测试、lint、diff 和 Git 状态为 Agent 提供可验证的 ground truth。Anthropic 将可自动测试、可迭代、问题空间结构化和结果可度量列为 coding agent 特别有效的原因。  
   来源：[Anthropic《Building effective agents》](https://www.anthropic.com/engineering/building-effective-agents)（访问日期：2026-08-04）。
2. **通用工具本身很强。** shell 已经是一个组合语言；文件检索、编辑、测试和版本控制可由成熟命令行工具完成，减少专用 tool schema 的数量。
3. **循环可以线性化。** mini-SWE-agent 将每次动作和结果直接追加到 messages，状态模型很薄。  
   来源：[mini-SWE-agent 官方仓库](https://github.com/SWE-agent/mini-swe-agent)（访问日期：2026-08-04）。
4. **核心差异常在 ACI 和上下文，而不在图。** SWE-agent 架构说明显示，其关键部件是环境、Agent `forward()`、历史压缩、模型输出解释和 shell 中的自定义命令；论文/项目强调 Agent-Computer Interface。  
   来源：[SWE-agent Architecture](https://swe-agent.com/0.7/background/architecture/)；[SWE-agent 官方仓库](https://github.com/SWE-agent/SWE-agent)（访问日期：2026-08-04）。
5. **上下文工程比 Agent 数量更重要。** Aider 使用压缩 repo map，把关键符号和相关代码放入有限 token budget，而不是先构建通用多 Agent 网络。  
   来源：[Aider Repository map](https://aider.chat/docs/repomap.html)（访问日期：2026-08-04）。

综合判断：coding agent 的成功不能简单归因于“没有框架”。更贴切的因果链是：强模型、合适 ACI、可靠沙箱、清晰环境反馈、上下文选择、可审计轨迹和最小循环共同工作；框架只是其中一个可替换层。

## 5. 什么时候框架真正有价值

### 5.1 需要可恢复的长运行任务

如果任务可能跨分钟/小时、进程重启、网络失败或人工等待，checkpoint 和 resume 很难靠一个内存 while loop 可靠完成。LangGraph 把 durable execution 和持久化列为核心能力；checkpoint 可支持故障恢复、HITL、memory 和 time travel。  
来源：[LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)；[LangGraph Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)（访问日期：2026-08-04）。

### 5.2 需要跨轮次人工审批

高风险工具调用需要暂停、持久化状态、等待审批后继续。LangGraph 的 HITL 能在工具调用前 pause，并在 approve/edit/reject 后恢复；AutoGen 也提供 handoff、external、timeout、token usage 等 termination condition，但其文档提醒“run 内阻塞式用户输入”会让团队处于不可保存/恢复的不稳定状态，只适合短交互。  
来源：[LangChain Human-in-the-loop](https://docs.langchain.com/oss/python/langchain/human-in-the-loop)；[AutoGen Human-in-the-Loop](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/human-in-the-loop.html)；[AutoGen Termination](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/termination.html)（访问日期：2026-08-04）。

### 5.3 状态拓扑已真实复杂

当业务有并行 fan-out/fan-in、循环、条件分支、人工节点、失败补偿、多个专门 Agent，显式图可以把原本散落的状态转移变成可检查结构。LangGraph 和 LangGraph4j 都为节点、边、状态、并行、checkpoint 提供直接表达。  
来源：[LangGraph Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api)；[LangGraph4j 官方文档](https://langgraph4j.github.io/langgraph4j/)（访问日期：2026-08-04）。

### 5.4 团队需要统一工程能力

框架可以统一模型适配、工具 schema、重试、追踪、评测和部署。Google ADK 官方列出的价值包括模型/工具生态、可预测 workflow agent、动态路由、调试 UI、逐步轨迹评测和部署；AutoGen Core 的定位则是事件驱动、可扩展的多 Agent 系统。  
来源：[Google ADK 发布说明](https://developers.googleblog.com/agent-development-kit-easy-to-build-multi-agent-applications/)；[AutoGen overview](https://microsoft.github.io/autogen/stable/index.html)（访问日期：2026-08-04）。

### 5.5 可观测性成本已经超过依赖成本

Agent 失败往往发生在轨迹中而非最终答案上。OpenAI Agents SDK 默认记录 model generation、tool call、handoff、guardrail 等 span，并允许配置是否包含敏感数据；这类能力自己实现并不难起步，但要做到完整、关联、并发安全和可运营并不便宜。  
来源：[OpenAI Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/)（访问日期：2026-08-04）。

## 6. 什么时候保持“无 Agent 框架”更合适

以下条件越多，自己拥有控制 loop 越合理：

- 单 Agent、工具数量有限且语义区分清楚；
- 请求短生命周期，不要求中断后跨进程恢复；
- 状态主要是当前请求上下文，无复杂共享图状态；
- 业务已有成熟 Spring 分层、指标、日志、超时和权限体系；
- 核心目标是可解释、可测试、供应商可替换；
- 团队能直接维护模型消息协议与工具循环；
- 产品边界严格，额外框架能力反而扩大默认行为和攻击面；
- 已经有真实 eval/轨迹证明当前 loop 足够，而不是只靠主观感觉。

OpenAI Agents SDK 文档给出了几乎相同的边界：当团队想自己拥有 loop、tool dispatch 和 state handling，且 workflow 短生命周期时，可直接使用 Responses API；当希望 runtime 管理 turns、工具执行、guardrails、handoff 或 sessions 时再用 SDK。  
来源：[OpenAI Agents SDK](https://openai.github.io/openai-agents-python/)（访问日期：2026-08-04）。

## 7. 对 Java 项目的具体含义

### 7.1 “不用三大框架”可以是正确决策

如果当前 Agent 本质上是：少量明确工具、一个受控循环、短请求、无持久化会话、无跨进程恢复、无多 Agent 图，那么引入 LangGraph4j 可能只会增加 State/Node/Edge 映射；引入完整 Spring AI/LangChain4j 高层 Agent 能力也可能重复现有领域边界。

这种情况下，合理路线是：

- 领域层定义自己的 `ModelPort`、`Tool`、`ToolResult`、`AgentRunState`、`StopReason`；
- 基础设施层适配具体模型供应商；
- orchestration 作为普通 Java 代码，控制循环显式可读；
- 对每轮模型输入摘要、tool call、tool result、耗时、token/cost、退出原因做结构化追踪；
- 工具执行统一经过鉴权、参数校验、超时、取消、脱敏和副作用策略；
- 用回归 eval 决定是否增加记忆、规划、多 Agent 或图。

### 7.2 但不要把“零依赖”当目标

完全自行实现供应商协议、SSE/streaming、JSON schema、tool call 兼容、重试语义和观测接入，可能把精力耗在非产品差异上。可以采用“薄 SDK、厚边界”的方案：

- 用官方模型 SDK 或 Spring AI/LangChain4j 的低层模型适配能力；
- 禁止其 `Agent`、`Memory`、`Message` 等类型进入领域层；
- 控制 loop、状态和安全策略仍由应用代码掌握；
- 通过契约测试验证不同供应商适配器行为一致。

这不是向“大框架”妥协，而是区分 commodity integration 与产品核心。

### 7.3 框架升级应由能力缺口触发

建议把以下现象作为重新评估信号：

| 真实问题 | 候选能力 |
|---|---|
| 服务重启后必须继续任务 | checkpoint / durable runtime（LangGraph4j 或通用 workflow runtime） |
| 高风险动作等待数小时审批 | 可持久化 interrupt / resume / HITL |
| 动态并行子任务和汇总越来越多 | graph、fan-out/fan-in、worker orchestration |
| 工具循环、memory、provider 适配重复代码持续增长 | Spring AI 或 LangChain4j 的低层/中层能力 |
| 多团队重复实现 tracing、eval、session | 轻量 Agents SDK/统一平台能力或内部运行时 |
| 单 Agent 因工具重叠持续选错，且优化 schema 后仍无改善 | 专门 Agent / manager / handoff |

没有这些问题时，不应因为框架“主流”而引入；出现这些问题后，也不应因为“反框架”身份而拒绝成熟运行时。

## 8. 判断当前设计是否真的健康：审查清单

### 控制循环

- 模型回复、tool call、失败、取消、超时、最大轮数、最大 token/成本分别如何终止？
- 工具返回可恢复错误时，是让模型修正、代码重试，还是直接失败？
- 是否防止相同工具参数无限循环？
- 并发工具是否有明确上限、顺序语义和取消传播？

### 状态与上下文

- 对话历史、领域状态、工具运行状态是否分开？
- 哪些内容进入模型上下文，谁负责裁剪/摘要，能否复现？
- 服务重启后允许丢失什么，必须恢复什么？
- 状态是否使用项目自己的类型，而非供应商 DTO 贯穿全层？

### 工具与安全

- 工具说明、参数 schema、错误码是否经过针对模型的测试？Anthropic 指出其 SWE-bench Agent 在工具优化上花的时间甚至多于总 prompt，并强调清晰 ACI。  
  来源：[Anthropic《Building effective agents》](https://www.anthropic.com/engineering/building-effective-agents)（访问日期：2026-08-04）。
- 读取工具和有副作用工具是否分级？
- 不可逆或高风险操作是否需要人工审批？
- 每个工具是否有超时、幂等、输入边界、输出大小限制、脱敏规则？
- 环境/工具结果是否被视为不可信输入，防止 prompt injection 通过工具结果回流？

### 可观测性与评测

- 能否按一次 run 还原每轮模型调用、工具调用、状态变化和 stop reason？
- tracing 是否默认避免保存用户敏感正文？OpenAI SDK 的 tracing 文档说明模型和工具输入输出可能含敏感数据，需要显式配置。  
  来源：[OpenAI Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/)（访问日期：2026-08-04）。
- 是否有离线 eval 覆盖成功率、工具选择、事实性、成本、延迟和安全？
- 架构复杂度增加前后，是否有量化收益，而不是只比较代码观感？

如果这些问题都有清楚、可测试的答案，当前无框架设计有充分工程依据。如果答案依赖“模型应该会处理”“以后再补”或分散在多个 service 的隐式分支中，就应先整理内部运行时边界；这一步不必立即引入外部框架。

## 9. 最终观点

1. **“反框架”是对复杂度和不透明性的约束，不是一项技术栈身份。**
2. **Agent 的必要核心很小：模型、工具、指令、环境反馈循环和停止条件；生产系统的必要外围并不小：安全、状态、恢复、观测、评测和人工控制。**
3. **强模型会压缩 Agent scaffold，但不会自动消除分布式系统和安全工程问题。** mini-SWE-agent 证明了简单 loop 的上限很高；LangGraph、ADK、AutoGen 则说明复杂运行问题仍需要基础设施。
4. **无框架最怕演变成隐式框架。** 当自研代码开始普遍处理 checkpoint、节点调度、事件总线、并行汇总、HITL 和 time travel，应重新比较“继续造运行时”与采用成熟低层框架的总成本。
5. **对当前 Java 项目，更稳妥的立场是 framework-optional，而不是 framework-hostile。** 保持领域模型和控制 loop 自有，允许在适配器层使用模型 SDK；只有真实能力缺口和 eval 证据证明需要时，才引入 Spring AI、LangChain4j 或 LangGraph4j 的对应层，而不是整套接管。

## 10. 主要一手资料索引

- Anthropic, [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)（发布：2024-12-19；访问：2026-08-04）
- OpenAI, [A practical guide to building agents](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents/)（访问：2026-08-04）
- OpenAI, [Agents SDK documentation](https://openai.github.io/openai-agents-python/)（访问：2026-08-04）
- OpenAI, [Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/)（访问：2026-08-04）
- LangChain, [LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)（访问：2026-08-04）
- LangChain, [Workflows and agents](https://docs.langchain.com/oss/python/langgraph/workflows-agents)（访问：2026-08-04）
- LangChain, [Frameworks, runtimes, and harnesses](https://docs.langchain.com/oss/python/concepts/products)（访问：2026-08-04）
- LangChain, [LangGraph Functional API](https://docs.langchain.com/oss/python/langgraph/functional-api)（访问：2026-08-04）
- Google, [Agent Development Kit announcement](https://developers.googleblog.com/agent-development-kit-easy-to-build-multi-agent-applications/)（发布：2025-04-09；访问：2026-08-04）
- Google, [ADK Python official repository](https://github.com/google/adk-python)（访问：2026-08-04）
- Microsoft, [AutoGen overview](https://microsoft.github.io/autogen/stable/index.html)（访问：2026-08-04）
- Microsoft, [AutoGen Teams](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/teams.html)（访问：2026-08-04）
- Spring, [Spring AI API](https://docs.spring.io/spring-ai/reference/api/)（访问：2026-08-04）
- Spring, [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)（访问：2026-08-04）
- LangChain4j, [Official repository](https://github.com/langchain4j/langchain4j)（访问：2026-08-04）
- LangGraph4j, [Official documentation](https://langgraph4j.github.io/langgraph4j/)（访问：2026-08-04）
- SWE-agent, [mini-SWE-agent official repository](https://github.com/SWE-agent/mini-swe-agent)（访问：2026-08-04）
- SWE-agent, [SWE-agent architecture](https://swe-agent.com/0.7/background/architecture/)（访问：2026-08-04）
- Aider, [Repository map](https://aider.chat/docs/repomap.html)（访问：2026-08-04）
