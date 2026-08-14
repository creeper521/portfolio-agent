// 任务级 reason code 前端白名单文案映射（FE-F05 / FE-F07 / FE-U02 方案 C）。
// 只映射后端公开闭集码；未知码一律回落到克制通用句，永不把原始码或异常文本展示给用户。
// 码集合对齐 backend routing 域实际产出（TaskOutcome.reasonCodes / ClarificationRequest.BlockedGoal）。

const EVIDENCE_INSUFFICIENT_TEXT = '公开证据不足，无法生成可信结论'
const CAPABILITY_UNAVAILABLE_TEXT = '当前公开能力无法完成此任务'
const EXECUTION_FAILED_TEXT = '任务未安全完成，请稍后重试'
const DEPENDENCY_UNMET_TEXT = '依赖任务未完成，因此暂不执行'
const SUBJECT_UNRESOLVED_TEXT = '需要先确认任务主体'

const TASK_REASON_TEXT: Readonly<Record<string, string>> = {
  // 证据不足
  PORTFOLIO_EVIDENCE_INSUFFICIENT: EVIDENCE_INSUFFICIENT_TEXT,
  EVIDENCE_NOT_SUFFICIENT: EVIDENCE_INSUFFICIENT_TEXT,
  // 能力不可用 / 任务类型不支持
  PORTFOLIO_CAPABILITY_UNAVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  PORTFOLIO_COMPOSITION_UNAVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  PORTFOLIO_RECOMMENDATION_UNAVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  PORTFOLIO_TASK_NOT_SUPPORTED: CAPABILITY_UNAVAILABLE_TEXT,
  PORTFOLIO_TASK_UNSUPPORTED: CAPABILITY_UNAVAILABLE_TEXT,
  GENERAL_PROVIDER_UNAVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  GENERAL_TASK_UNSUPPORTED: CAPABILITY_UNAVAILABLE_TEXT,
  SYNTHESIS_TASK_UNSUPPORTED: CAPABILITY_UNAVAILABLE_TEXT,
  CAPABILITY_UNAVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  CAPABILITY_EXECUTOR_UNAVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  CAPABILITY_NOT_AVAILABLE: CAPABILITY_UNAVAILABLE_TEXT,
  // 安全失败
  PORTFOLIO_CAPABILITY_FAILURE: EXECUTION_FAILED_TEXT,
  GENERAL_DRAFT_REJECTED: EXECUTION_FAILED_TEXT,
  EXECUTION_UNEXPECTED_FAILURE: EXECUTION_FAILED_TEXT,
  EXECUTION_INVALID_OUTCOME: EXECUTION_FAILED_TEXT,
  EXECUTION_FAILED_SAFE: EXECUTION_FAILED_TEXT,
  // 依赖未满足（有 blockedByDisplayIndexes 时优先展示更具体的依赖句）
  EXECUTION_DEPENDENCY_BLOCKED: DEPENDENCY_UNMET_TEXT,
  EXECUTION_DEPENDENCY_UNAVAILABLE: DEPENDENCY_UNMET_TEXT,
  SYNTHESIS_INPUT_INSUFFICIENT: DEPENDENCY_UNMET_TEXT,
  UPSTREAM_TASK_NOT_ANSWERED: DEPENDENCY_UNMET_TEXT,
  // 主体未确定
  SUBJECT_NOT_RESOLVED: SUBJECT_UNRESOLVED_TEXT,
  ROUTING_SUBJECT_UNRESOLVED: SUBJECT_UNRESOLVED_TEXT,
  ROUTING_SUBJECT_AMBIGUOUS: SUBJECT_UNRESOLVED_TEXT,
}

const UNKNOWN_TASK_REASON_TEXT = '该任务未能安全完成'

const BLOCKED_GOAL_REASON_TEXT: Readonly<Record<string, string>> = {
  WAITING_FOR_COMPARISON_SUBJECT: '等待你确认比较对象',
  WAITING_FOR_SUBJECT: '等待你确认主体',
}

const UNKNOWN_BLOCKED_GOAL_REASON_TEXT = '等待你补充信息'

/**
 * 任务状态行的原因短句：
 * - 有被阻塞上游时优先点名依赖（「依赖任务 02 未完成，因此暂不执行」）；
 * - 否则取第一个已知白名单码的文案；
 * - 全部未知时给出克制通用句；无任何原因信息时返回 null（调用方不渲染原因行）。
 */
export function taskReasonText(item: {
  reasonCodes: readonly string[]
  blockedByDisplayIndexes: readonly string[]
}): string | null {
  if (item.blockedByDisplayIndexes.length > 0) {
    return `依赖任务 ${item.blockedByDisplayIndexes.join('、')} 未完成，因此暂不执行`
  }
  if (item.reasonCodes.length === 0) return null
  for (const code of item.reasonCodes) {
    const text = TASK_REASON_TEXT[code]
    if (text !== undefined) return text
  }
  return UNKNOWN_TASK_REASON_TEXT
}

/** 关键澄清下游目标的原因短句（FE-F07），同样只走白名单。 */
export function blockedGoalReasonText(reasonCode: string): string {
  return BLOCKED_GOAL_REASON_TEXT[reasonCode] ?? UNKNOWN_BLOCKED_GOAL_REASON_TEXT
}

// P5 Context 失效 reasonCode 白名单文案（设计 §2.5/§4.5）。未知码 → 克制通用句，不暴露原始码。
const CONTEXT_REASON_TEXT: Readonly<Record<string, string>> = {
  CONTEXT_REFERENCE_INVALID: '引用的对话上下文已失效',
  CONTEXT_REFERENCE_EXPIRED: '引用的对话上下文已过期',
  CONTEXT_RESULT_STALE: '该上下文已与最新内容不兼容',
  REFERENCED_SUBJECT_UNAVAILABLE: '引用的主体已不可用',
  REFERENCED_PUBLIC_SOURCE_CHANGED: '引用的公开来源已更新',
  CONTEXT_RESOLUTION_UNAVAILABLE: '当前无法解析该上下文',
  ROUTING_CONTEXT_CONFLICT: '该上下文与当前请求冲突',
  CONTINUATION_GOAL_UNRESOLVED: '该上下文的任务目标尚未完成',
  CONTEXT_SUBJECT_REQUIRED: '需要先明确上下文中的主体',
  RESULT_POSITION_OUT_OF_RANGE: '引用的结果序号超出范围',
  RESULT_CONTEXT_AMBIGUITY: '存在多个可指代结果，请明确所指',
}
const UNKNOWN_CONTEXT_REASON_TEXT = '该对话上下文已不可用，请重新提问'

/** P5 Strict Context 失效原因短句（设计 §13.9/§4.5）。未知码 fail-closed 通用句。 */
export function contextReasonText(reasonCode: string): string {
  return CONTEXT_REASON_TEXT[reasonCode] ?? UNKNOWN_CONTEXT_REASON_TEXT
}

/** P5 Context 失效恢复动作按钮文案（设计 §2.5/§4.4）。未知动作 → 安全「重新提问」。 */
export function recoveryActionLabel(action: string): string {
  if (action === 'RESTART_FROM_CURRENT_CONTENT') return '基于最新内容重新开始'
  if (action === 'RESELECT_RESULTS') return '重新选择结果'
  if (action === 'REASK_WITHOUT_CONTEXT') return '不带上下文重新提问'
  return '重新提问'
}
