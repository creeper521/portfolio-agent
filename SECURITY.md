# Security Policy
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

## 公开数据边界

公网运行时只能读取已审核、已发布的公开 Bundle 或其受控公开数据库投影。私有 Obsidian 知识库、原始日报、候选审核包、未批准 Evidence、内部截图、凭据和隐私报告不得进入运行制品。

只有标记为 `APPROVED` 的 Evidence 可以公开投影。Project 与 Case 主体必须显式且互斥；未知主体失败关闭，不能隐式扩大为相关 Project，也不能混合不相容的 Claim。

## Agent State

标准本地环境和生产环境使用独立 PostgreSQL Agent State。它只保存完成恢复所需的最小短期状态：

- request claim、fingerprint 与 persistence-safe `PublicAgentTurn` replay；
- Conversation 与 ResumeToken 的单向绑定；
- typed Continuation Context；
- typed Clarification Challenge 与消费状态。

不得保存访客原始问题、完整 `ConversationWindow`、Prompt、模型原始响应、私有 Evidence、原始来源地址或浏览器聊天记录。Clarification 的自由文本只在当前请求内归一化；持久化层只接收闭合、强类型结果。确定性 Portfolio Turn 可以精确保存并重放其公开文本、typed Context 与不透明 ContextHandle；Provider 派生的 General/Conversational 正文只在首次响应返回，settlement 必须改存固定 `CAPABILITY_UNAVAILABLE/REPLAY_BODY_NOT_RETAINED` 终局，文案为“该回答未被保留，请重新提问。”。加密不改变这条不持久化边界。

Challenge 使用 5 分钟 absolute TTL；Conversation、Continuation Context、已完成 replay 和终局记录统一使用 30 分钟 absolute TTL。读取、刷新、重放或 Token 轮换都不得延长原始过期时间。旧加密密钥的保留窗口必须覆盖 30 分钟 TTL 与清理延迟。

Token 与 payload 使用不同的 32 字节密钥加密，密钥不得相同；生产密钥不得进入仓库、日志、命令参数或发布包。解密失败、绑定不符、版本漂移或过期记录均失败关闭。

## 浏览器状态

浏览器只允许在当前标签页的 `sessionStorage` 保存一个短期 ResumeToken。关闭标签页后自然清除；服务端返回凭证失效时必须立即删除。

以下内容不得进入 `sessionStorage`、`localStorage`、IndexedDB、URL 或浏览器历史：问题、回答、ConversationWindow、ContextHandle、Clarification 内容、requestId 历史、PublicAgentTurn、Evidence、Prompt 与模型输出。ResumeToken 不得进入 URL、请求正文、日志或诊断事件，只能通过 `Authorization: Bearer` 发送。

## 模型与检索

模型能力默认关闭。只有在数据策略已审批、Provider 配置完整且操作显式启用时，才可发送本轮允许的最小载荷。每个已启用 Operation 声明的 `providerRef` 必须精确等于唯一 Transport 的 `ModelProviderKind`，`schemaVersion` 必须精确等于对应生产 Codec 的常量；任一错配都必须在应用启动期失败关闭。模型端口和公开 availability 只能消费同一个冻结 readiness，不得各自推测配置。模型不得接收凭据、Cookie、Header、内部标识、私有资料或持久化会话；没有自动跨 Provider 重发。

公开检索使用审核后的 Bundle 或其公开数据库投影。本地向量、查询词项、候选、分数与检索上下文不得进入日志或外部 Provider。模型表达不能扩大项目状态、个人贡献或生产效果声明，校验失败时丢弃草稿并安全降级。

## 准入与诊断

公开 Turn 受来源速率、来源并发、单实例 Active Turn 和单 Turn 任务并行上限保护。来源地址只在进程内做 HMAC 匿名化，不记录原始地址。公开限流错误使用统一 `RATE_LIMITED`；内部诊断可区分限制来源，但不得泄露地址或凭据。

前端诊断入口默认关闭，只接受封闭、限流、不持久化的结构化事件。它不得接收访客内容、任意元数据、原始堆栈、URL、Header、请求体或响应体。

API 错误不得包含堆栈、路径、内部主机、数据库信息或 Secret。成功与错误响应均应使用 `Cache-Control: no-store`。

## 报告与发布检查

不要在公开 Issue 中提交私有实习信息。疑似数据泄露应通过私密渠道报告给仓库所有者。

发布候选至少运行：

- `scripts/privacy-check.ps1`；
- `scripts/privacy-check.ps1 -Path backend/src/main`；
- `scripts/documentation-check.ps1`；
- `scripts/code-quality-check.ps1`；
- `scripts/architecture-check.ps1`；
- `scripts/verify-release.ps1`。

真实 Provider、生产数据库和外部环境验收必须显式授权，并单独保存新鲜证据。
