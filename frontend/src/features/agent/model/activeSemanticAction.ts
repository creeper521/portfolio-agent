import type { AgentSession } from './sessionTypes'

/**
 * 唯一未决动作（P2 状态语义收口）：
 * 一个会话同一时刻最多只有一个可操作的语义动作——待确认计划、待澄清或计划失效。
 *
 * 决议规则：
 * - 只有「最后一条带回答的 Agent 消息」可以持有未决动作；
 *   后续出现 READY 正文、新确认、新澄清、新失效或用户取消时，旧动作自动失效。
 * - CONFIRMATION 还需要会话级 pendingConfirmation 仍在 tab 内存中
 *   （取消或确认被消费后指针消失，历史确认卡降级为只读）。
 * - PLAN_INVALIDATION 被「暂不处理」dismiss 后不再构成未决动作。
 *
 * 历史卡片仍然展示，但渲染层据此降级为只读；Workspace 处理事件时据此再次校验身份。
 */
export type ActiveSemanticAction =
  | { kind: 'CONFIRMATION'; turnId: string; confirmationId: string }
  | { kind: 'CLARIFICATION'; turnId: string; clarificationId: string | null }
  | { kind: 'PLAN_INVALIDATION'; turnId: string }
  | null

export function resolveActiveSemanticAction(
  session: Pick<AgentSession, 'messages' | 'pendingConfirmation'>,
  isInvalidationDismissed: (turnId: string) => boolean = () => false,
): ActiveSemanticAction {
  for (let index = session.messages.length - 1; index >= 0; index -= 1) {
    const message = session.messages[index]
    if (message?.role !== 'AGENT' || !message.answer) continue
    const semanticTurn = message.answer.semanticTurn
    if (!semanticTurn) return null
    const turnId = message.answer.turnId

    if (semanticTurn.disposition === 'CONFIRMATION_REQUIRED' && semanticTurn.displayPlan) {
      const pending = session.pendingConfirmation
      return pending === undefined
        ? null
        : { kind: 'CONFIRMATION', turnId, confirmationId: pending.confirmationId }
    }
    if (semanticTurn.clarification) {
      return {
        kind: 'CLARIFICATION',
        turnId,
        clarificationId: semanticTurn.clarification.clarificationId,
      }
    }
    if (semanticTurn.planChange) {
      return isInvalidationDismissed(turnId)
        ? null
        : { kind: 'PLAN_INVALIDATION', turnId }
    }
    return null
  }
  return null
}
