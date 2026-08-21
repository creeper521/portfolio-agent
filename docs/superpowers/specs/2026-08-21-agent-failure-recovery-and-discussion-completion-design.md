# Agent 失败恢复与项目讨论补完设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-21
> **状态：** 用户已批准按本设计继续 LEVEL_3 补完
> **前置设计：** `2026-08-20-project-discussion-context-design.md`

## 1. 目标与边界

本设计补齐失败重试、Clarification 一次消费、Session replacement parity、Project Discussion 生命周期、权威前端投影与高风险验收门。它不替换既有 Agent 权威：AI 仍只提出 closed semantic route，后端继续验证候选、权限、状态转换与 pointer generation。

固定禁止项保持不变：不增加自然语言短语表、第三个 Prompt、Assistant 文本解析、旧 CONTINUE 兼容、第二状态权威或长期聊天存储；问题、Prompt、原始模型输出、Token、handle 和聊天消息不得进入新状态、日志或机器账本。

## 2. A2-22：同 requestId 的提交身份

前端新增仅存在于页面内存的 `TurnSubmissionSnapshot`，冻结：requestId、command、surfaceContext、conversationWindow、当时的 resumeToken、displayQuestion 和可空 userMessageId。首次发送、clarification、handoff replay 和失败重试都走同一 snapshot；同 requestId 重试不得重算任何参与 `RequestFingerprintFactory` 的字段。

Snapshot 不进入 URL、localStorage、sessionStorage、日志或后端状态。真正的 `IDEMPOTENCY_KEY_CONFLICT` 保持不可重试，并显示重新发起新请求的文案。

## 3. A2-23：V5 Clarification reservation

`agent_turn_clarification` 增加一组 all-null/all-present 字段：

```text
reserved_by_request_id UUID?
reservation_expires_at TIMESTAMPTZ?
```

准备阶段在单个 State 事务内读取、解密、验证 challenge 与 answer，并用条件 UPDATE 预约：challenge 未消费、未过期，且无活预约、预约属于同 requestId 或旧预约已过期。预约截止为 `min(TurnDeadline.expiresAt, challenge.expiresAt)`；不额外叠加 settlement reserve，也不使用 35 秒 lease 延长访客等待。

不同 requestId 撞到活预约时返回闭合的 `CLARIFICATION_IN_PROGRESS`，携带有界 retryAfter；它不是挑战终局。执行链只携带内存态 `ClarificationSettlementMutation`，不把答案写入数据库。`complete` 的同一 terminal transaction 重新读取 challenge、验证 reservation owner 与 answer binding，然后原子设置 consumed、清预约并结算 PublicTurn；任一步失败整体回滚。cancel 可按 requestId best-effort 释放；进程退出后由 reservation expiry 允许回收。InMemory 与 PostgreSQL 必须通过同一合同。

## 4. A2-24—A2-26：Project Discussion 语义与生命周期

- 只有 `ENTER_RECOMMENDED_RESULT` 可直接进入候选；`NEEDS_CLARIFICATION` 即使只有一个候选也必须产生限定澄清。
- ENTER/SWITCH/REENTER 的 discussion expiry 统一为 `min(startedAt + discussionTtl, sessionExpiresAt)`；来源 Recommendation 过期不裁剪已冻结的 discussion scope。
- `discussionTtl` 是独立配置，默认 20 分钟，必须为正、最多 30 分钟且严格短于 Session absolute TTL，保证 ACTIVE → EXPIRED 在有效 Session 内可达。
- Session replacement 必须在两个 PostgreSQL 入口中同时清除 active pointer 并递增 Session revision；InMemory 同语义。
- 两份 Session upsert 收敛到 PostgreSQL State 模块内的 package-private writer；`complete` 继续留在原事务中，不创建第二 Session 权威。

## 5. A2-27：前端 request generation

pending、failure 与异步回调的删除或覆盖必须同时匹配 sessionId 和 requestId。取消 A 后提交 B，A 的迟到回调不得删除、覆盖或解除 B 的 pending。

## 6. A2-28：V6 Session revision 与权威投影

复用 `conversation_session.revision` 作为 discussion pointer 的单调 revision；不增加第二 revision 列。GUARD 不递增，REPLACE、CLEAR 和 replacement 清 pointer 必须递增。`ConversationSessionStore.Session` 同时携带 pointer 与 revision，InMemory 从 0 开始维护 parity。

每个成功 Turn 响应返回当前 authoritative discussion summary 与 revision。初次 settlement 只有在 `complete=true` 后才投影；REPLAY 必须读取当前 Session 状态，不能重放历史 pointer。Authenticated replay 不假设已持有 Session 行锁，必须通过同一 State authority 取得当前 pointer+revision。GET current 保留为冷恢复入口并返回相同 revision。

前端只应用 revision 不小于当前值的 summary；null pointer 也必须携带 revision，防止晚到 ACTIVE 覆盖 EXIT。热路径移除 post-turn GET。Summary malformed 时显示显式恢复错误并暂停依赖 discussion 的自由文本，不静默退化为普通 ASK。

焦点条显示由服务端 `expiresAt` 计算的真实剩余时间；显示倒计时不改变后端状态。`DISCUSSION_*` 恢复动作由后端投影。无 continuation 的自由文本 action 在 semantic routing DISABLED 时不可发送；Recommendation discussionAction 缺合法 ENTER continuation 时 mapper fail-closed；ACTIVE pointer 下显式 REENTER_SUBJECT 失败关闭。

`DISCUSSION_INTERPRETATION_UNAVAILABLE` 是已结算的 PublicTurn 终局；对同 requestId 的重放必须返回原终局，不得重新调用 Provider。因此其 backend-owned 恢复动作冻结为：以原输入创建**新 requestId** 的重试 action，以及携带 `EXIT_CONTEXT` continuation 的退出 action。这保持幂等终局不变，也不将 Provider 重试隐藏在 replay 内。

## 7. A2-29：验证门

- `RequireLiveProvider` 运行真实 General Quality canary；Baseline 仍只采集，不增加 Provider 重试，不改变批准句数桶。
- General Quality canary 是真实通用回答的唯一权威门；旧 GENERAL one-shot probe 不再重复拦截。失败时可输出语言、结构、句数桶、终局与延迟的聚合指标，但不得输出问题或模型正文。
- packaged runner 所有 HTTP 调用使用 absolute deadline 派生的有界 timeout，finally 必须恢复环境并停止进程。
- privacy checker 增加 typed credential literal 负例，豁免只覆盖无赋值声明或可信派生值。
- 共享 fixtures 覆盖 ACTIVE/EXPIRED Summary、revision、DISCUSSION errors/actions 与 free-text capability。
- Browser 矩阵按批准设计 14 场景在桌面/移动执行；确定性 fixture 验状态，真实 Provider 只验开放语义，PostgreSQL 验重启、replacement、reservation reclaim 与 V5/V6 migration。

## 8. Replacement 与回退

实施顺序固定为：提交身份 → V5 reservation → Discussion 语义/Session writer → V6 revision/投影 → 门禁。每个 Slice 接入唯一生产入口、迁移调用方并删除旧路径，不保留兼容桥。回退只使用 Git/JAR/整体版本；V5/V6 是短期状态，版本回退可清空 Agent State。

## 9. 完成条件

A2-22—A2-29 的原始失败路径、Memory/PostgreSQL parity、全量 Backend/Frontend、privacy/documentation/architecture、Testcontainers、packaged Browser 和获授权 Provider 门全部通过后，才删除动态账本条目并恢复 architecture `COMPLETE`。
