import type { AgentTurnFailure } from '../api/agentTurnApi'

// 把传输失败投影为稳定、可行动的公开错误类别（领域模型层）。
// 只使用后端冻结错误码 / HTTP 状态与前端本地码，不泄露内部字段、堆栈或 Provider 细节；
// 同 requestId 幂等重试仅对可恢复类别开放，不可恢复类别引导用户换新提问或新会话。（A2-05）

/** 面向界面的失败类别闭合集：每类对应固定文案与行动建议。 */
export type TurnFailureCategory =
  | 'SESSION_EXPIRED'
  | 'CONVERSATION_MISMATCH'
  | 'TURN_CONFLICT'
  | 'SERVICE_UNAVAILABLE'
  | 'RATE_LIMITED'
  | 'CONTRACT_INVALID'
  | 'TIMEOUT'
  | 'NETWORK'
  | 'UNKNOWN'

/** 投影后的失败视图：category 决定文案与提示，retryable 决定是否允许同 requestId 重试。 */
export interface TurnFailureView {
  readonly category: TurnFailureCategory
  readonly message: string
  /** 补充行动建议；与 message 分行渲染。 */
  readonly hint?: string
  readonly retryable: boolean
  readonly retryAfterSeconds?: number
}

/** 优先按服务端稳定码映射，未命中再按 HTTP 状态兜底；两者都无法识别时落到保守可重试的 UNKNOWN。 */
function byServerCode(code: string | undefined, status: number | undefined): {
  category: TurnFailureCategory
  message: string
  hint?: string
  retryable: boolean
} {
  switch (code) {
    case 'RESUME_TOKEN_INVALID':
      return {
        category: 'SESSION_EXPIRED',
        message: '当前会话已过期或失效。',
        hint: '请新建会话后重新提问。',
        retryable: false,
      }
    case 'TURN_IN_PROGRESS':
      return {
        category: 'TURN_CONFLICT',
        message: '当前会话已有请求正在处理。',
        hint: '请稍候片刻再重试这条请求。',
        retryable: true,
      }
    case 'IDEMPOTENCY_KEY_CONFLICT':
      return {
        category: 'CONVERSATION_MISMATCH',
        message: '这条请求标识已用于不同内容。',
        hint: '请重新发起一个新请求；重复当前 requestId 不会改变结果。',
        retryable: false,
      }
    case 'TURN_CANCELLED':
      return {
        category: 'CONVERSATION_MISMATCH',
        message: '这条请求已被取消，无法继续取回结果。',
        hint: '请换个说法重新提问。',
        retryable: false,
      }
    case 'AGENT_STATE_UNAVAILABLE':
      return {
        category: 'SERVICE_UNAVAILABLE',
        message: 'Agent 服务暂时不可用。',
        hint: '可重试这条请求；若持续出现请稍后再来。',
        retryable: true,
      }
    case 'VALIDATION_ERROR':
      return {
        category: 'CONVERSATION_MISMATCH',
        message: '这条请求与当前会话状态不匹配。',
        hint: '请换个说法重新提问；重试同一请求不会改变结果。',
        retryable: false,
      }
    case 'PUBLIC_TURN_CONTRACT_INVALID':
      return {
        category: 'CONTRACT_INVALID',
        message: '回答结构不符合公开合同。',
        hint: '请重新提问；若持续出现请联系站点维护者。',
        retryable: false,
      }
    case 'NETWORK_UNAVAILABLE':
      return {
        category: 'NETWORK',
        message: '网络异常，未能连接 Agent 服务。',
        hint: '请检查网络后重试这条请求。',
        retryable: true,
      }
    case 'REQUEST_TIMEOUT':
      return {
        category: 'TIMEOUT',
        message: '等待超时：回答可能仍在生成。',
        hint: '可重试这条请求取回结果；也可以先做别的，稍后再来。',
        retryable: true,
      }
    default:
      break
  }
  if (status === 429) {
    return {
      category: 'RATE_LIMITED',
      message: '请求过于频繁。',
      retryable: true,
    }
  }
  if (status !== undefined && status >= 500) {
    return {
      category: 'SERVICE_UNAVAILABLE',
      message: 'Agent 服务暂时不可用。',
      hint: '可重试这条请求；若持续出现请稍后再来。',
      retryable: true,
    }
  }
  if (status === 404) {
    return {
      category: 'CONVERSATION_MISMATCH',
      message: '这条请求已失效，无法继续处理。',
      hint: '请换个说法重新提问。',
      retryable: false,
    }
  }
  if (status === 400) {
    return {
      category: 'CONVERSATION_MISMATCH',
      message: '这条请求与当前会话状态不匹配。',
      hint: '请换个说法重新提问；重试同一请求不会改变结果。',
      retryable: false,
    }
  }
  return {
    category: 'UNKNOWN',
    message: 'Agent 暂时无法处理这条请求。',
    hint: '可重试这条请求；若持续出现请换个说法提问。',
    retryable: true,
  }
}

/**
 * 把 AgentTurnFailure 投影为可展示的 TurnFailureView。
 * 本地失败（TIMEOUT/NETWORK/CONTRACT）无 HTTP 状态，只按本地码映射；
 * API 失败最终可重试性取「类别语义」与「服务端 retryable 标志」的交集。
 */
export function projectTurnFailure(failure: AgentTurnFailure): TurnFailureView {
  if (failure.kind === 'TIMEOUT' || failure.kind === 'NETWORK' || failure.kind === 'CONTRACT') {
    const base = byServerCode(failure.code, undefined)
    return {
      category: base.category,
      message: base.message,
      ...(base.hint === undefined ? {} : { hint: base.hint }),
      retryable: base.retryable,
      ...(failure.retryAfterSeconds === undefined
        ? {}
        : { retryAfterSeconds: failure.retryAfterSeconds }),
    }
  }
  const base = byServerCode(failure.code, failure.status)
  const serverAllowsRetry =
    failure.retryable || (failure.status !== undefined && failure.status >= 500)
  return {
    category: base.category,
    message: base.message,
    ...(base.hint === undefined ? {} : { hint: base.hint }),
    // 5xx envelope 缺省（retryable=false）不代表显式否决；其余以类别语义 + 服务端标志取交集。
    retryable: base.retryable && serverAllowsRetry,
    ...(failure.retryAfterSeconds === undefined
      ? {}
      : { retryAfterSeconds: failure.retryAfterSeconds }),
  }
}
