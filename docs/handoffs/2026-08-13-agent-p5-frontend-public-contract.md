# Agent P5 stp-v2 前端公共契约定稿

> 日期：2026-08-13
> 状态：前后端实施依据
> 权威设计：`../superpowers/specs/2026-08-13-agent-context-and-runtime-modes-design.md`
> 范围：只确定公共 DTO、枚举、兼容与 fail-closed 规则；视觉和交互细节由前端 Agent 设计

## 1. Task Status

`stp-v2` 公共闭集固定为：

```text
COMPLETED | PARTIAL | EMPTY | NOT_SUPPORTED | NOT_APPLICABLE | BLOCKED
| UNAVAILABLE | STALE | FAILED | REJECTED | NOT_EXECUTED
```

补充映射：

- 内部 `PRESENTATION_BLOCKED` → 公共 `BLOCKED`。
- 内部 `executionStatus=CANCELLED` → 公共 `NOT_EXECUTED`。当前 CANCELLED 只表示路由延后或未选择，不表示用户取消整轮请求。
- 已有映射保持：`DEPENDENCY_UNAVAILABLE` → `BLOCKED`，`NOT_EXECUTED_BUDGET` → `NOT_EXECUTED`。

迁移期可以在不同响应中看到 `stp-v1` 旧值或 `stp-v2` 新值，但同一个响应不得
混用。前端按响应实际协商版本选择解析器；`stp-v2` 出现旧值、未知值或混合值时
fail closed，不猜测映射。

## 2. Ordered Result Item

P5 第一版的权威响应路径是：

```text
completedTasks[]
  .resultPayload(kind=RECOMMENDATION_RESULT)
  .recommendations[]
    .resultItemId
    .position
    .subject{subjectType,subjectId}
```

这些字段属于每个可选择的推荐项，不属于 `continuationContext`，也不属于 Recent
Context 的公共投影。选择继续时，前端组合该项的 `resultItemId` 与同一完成任务的
`continuationContext.contextHandle/contextType`。旧顶层
`portfolioRecommendation.items[]` 不是新授权路径；不得从下标、排序、
`portfolioId` 或 `subjectId` 构造 `resultItemId`。

## 3. Context Invalidated 路由

当以下字段并存：

```text
responseKind=ANSWER
answerResolution=NEEDS_CLARIFICATION
agentTurn.disposition=CONTEXT_INVALIDATED
```

前端以 `agentTurn.disposition` 优先，进入“上下文失效恢复卡”，不能进入通用澄清卡。
`contextInvalidation` 是响应顶层字段，与 `agentTurn` 同级。该 disposition 必须伴随
非空 `contextInvalidation` 和空 `blocks`；任一不满足都 fail closed。

## 4. Continuation Context 权威关系

结构化字段放在各自完成任务：

```text
completedTasks[].continuationContext{
  contextHandle,
  contextType,
  sourceTaskId
}
```

它是 `stp-v2` “继续” affordance 的权威来源。旧
`completedTasks[].contextHandle` 仅作 `stp-v1` 兼容回退；两者同时存在时 Handle
必须一致，不一致 fail closed。`stp-v2` 不提供单一顶层 `continuationContext`，
因为同一响应可以有多个可继续任务。前端不能跨任务拼接 Handle 和 Result Item。

## 5. sourceDomain 与 sourceScope

`sourceDomain` 始终是新契约权威字段：

- `GENERAL`：旧 `sourceScope` 若存在，必须为 `GENERAL`。
- `PORTFOLIO`：旧 `sourceScope` 若存在，必须为 `PORTFOLIO`。
- `SYNTHESIS`：旧 `sourceScope` 必须省略或为 `null`，不得伪装成 `GENERAL`。

前端读取到 `sourceDomain` 后不得让旧 `sourceScope` 覆盖它；对于前两类的不一致，
以及 SYNTHESIS 携带非空旧 scope，均按契约违规 fail closed。

## 6. Context 枚举闭集

```text
ContextInvalidationRecoveryAction
  RESTART_FROM_CURRENT_CONTENT
  RESELECT_RESULTS
  REASK_WITHOUT_CONTEXT

ContextResolutionMode
  REVALIDATED_TO_CURRENT
```

固定映射：

| reasonCode | recoveryAction |
|---|---|
| `CONTEXT_RESULT_STALE` | `RESTART_FROM_CURRENT_CONTENT` |
| `REFERENCED_PUBLIC_SOURCE_CHANGED` | `RESTART_FROM_CURRENT_CONTENT` |
| `REFERENCED_SUBJECT_UNAVAILABLE` | `RESELECT_RESULTS` |
| `CONTEXT_REFERENCE_INVALID` | `REASK_WITHOUT_CONTEXT` |
| `CONTEXT_REFERENCE_EXPIRED` | `REASK_WITHOUT_CONTEXT` |

`CONTEXT_RESOLUTION_UNAVAILABLE` 属于能力不可用，不返回 Context Invalidation
恢复动作。没有重新验证时省略 `contextResolution`；当前公共 `mode` 没有其它值。
未知 action/mode 不自动发请求，显示非破坏性的通用恢复出口并记录脱敏诊断。

## 7. 已批准的 409 回退

`HTTP 409 + AGENT_TURN_CONTRACT_UNSUPPORTED` 只允许展示“以基础模式继续”的用户
主动操作。确认后才以 `stp-v1` 新请求重试；禁止静默降级、自动重试或把失败的
`stp-v2` 响应按 `stp-v1` 解析。

## 8. 最小一致性门禁

1. 单响应状态词汇不混版。
2. Result Item 与 Continuation Context 必须来自同一 completed task。
3. `CONTEXT_INVALIDATED` 优先于通用 clarification。
4. 新旧 context handle 同时存在时完全一致。
5. SYNTHESIS 不投影成 GENERAL。
6. 未知公共枚举不触发自动恢复或静默降级。
