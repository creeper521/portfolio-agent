import { describe, expect, it } from 'vitest'

import type { AgentTurnFailure } from '../api/agentTurnApi'
import { projectTurnFailure } from './turnFailureProjection'

// A2-05：错误投影只依赖冻结错误码 / HTTP 状态 / 前端本地码；
// 同 requestId 重试仅对可恢复类别开放，不可恢复类别给出换法提问或新会话指引。

function apiFailure(code: string | undefined, status: number, retryable = false): AgentTurnFailure {
  return { kind: 'API', status, code, message: '服务端消息', retryable }
}

describe('projectTurnFailure', () => {
  it('RESUME_TOKEN_INVALID 归类会话过期，不可同请求重试', () => {
    const view = projectTurnFailure(apiFailure('RESUME_TOKEN_INVALID', 401))
    expect(view.category).toBe('SESSION_EXPIRED')
    expect(view.retryable).toBe(false)
    expect(view.hint).toContain('新建会话')
  })

  it('VALIDATION_ERROR 归类会话不匹配，明确重试同一请求不会改变结果', () => {
    const view = projectTurnFailure(apiFailure('VALIDATION_ERROR', 400))
    expect(view.category).toBe('CONVERSATION_MISMATCH')
    expect(view.retryable).toBe(false)
  })

  it('TURN_IN_PROGRESS / TURN_CANCELLED 分别给出可重试与不可重试终局', () => {
    expect(projectTurnFailure(apiFailure('TURN_IN_PROGRESS', 409, true)).category).toBe('TURN_CONFLICT')
    expect(projectTurnFailure(apiFailure('TURN_IN_PROGRESS', 409, true)).retryable).toBe(true)
    const cancelled = projectTurnFailure(apiFailure('TURN_CANCELLED', 409))
    expect(cancelled.category).toBe('CONVERSATION_MISMATCH')
    expect(cancelled.retryable).toBe(false)
  })

  it('AGENT_STATE_UNAVAILABLE 与裸 5xx 归类服务不可用且可重试', () => {
    const view = projectTurnFailure(apiFailure('AGENT_STATE_UNAVAILABLE', 503, true))
    expect(view.category).toBe('SERVICE_UNAVAILABLE')
    expect(view.retryable).toBe(true)
    // envelope 缺省 retryable=false 不否决 5xx 的可重试语义。
    const bare = projectTurnFailure(apiFailure(undefined, 502))
    expect(bare.category).toBe('SERVICE_UNAVAILABLE')
    expect(bare.retryable).toBe(true)
  })

  it('429 归类限流并携带 retryAfterSeconds', () => {
    const view = projectTurnFailure({
      kind: 'API',
      status: 429,
      code: 'RATE_LIMITED',
      message: '限流',
      retryable: true,
      retryAfterSeconds: 30,
    })
    expect(view.category).toBe('RATE_LIMITED')
    expect(view.retryable).toBe(true)
    expect(view.retryAfterSeconds).toBe(30)
  })

  it('前端本地 TIMEOUT / NETWORK / CONTRACT 各自归类并给出行动建议', () => {
    const timeout = projectTurnFailure({
      kind: 'TIMEOUT', code: 'REQUEST_TIMEOUT', message: '等待超时', retryable: true,
    })
    expect(timeout.category).toBe('TIMEOUT')
    expect(timeout.retryable).toBe(true)
    expect(timeout.hint).toContain('重试')

    const network = projectTurnFailure({
      kind: 'NETWORK', code: 'NETWORK_UNAVAILABLE', message: '网络不可用', retryable: true,
    })
    expect(network.category).toBe('NETWORK')

    const contract = projectTurnFailure({
      kind: 'CONTRACT', code: 'PUBLIC_TURN_CONTRACT_INVALID', message: '合同破损', retryable: false,
    })
    expect(contract.category).toBe('CONTRACT_INVALID')
    expect(contract.retryable).toBe(false)
  })

  it('未知码回落状态桶：404 引导重新提问，其余保持通用可重试类别', () => {
    const notFound = projectTurnFailure(apiFailure(undefined, 404))
    expect(notFound.category).toBe('CONVERSATION_MISMATCH')
    expect(notFound.retryable).toBe(false)

    const unknown = projectTurnFailure(apiFailure(undefined, 418))
    expect(unknown.category).toBe('UNKNOWN')
    // envelope retryable 缺省为 false：未知类别保守不提供同请求重试入口。
    expect(unknown.retryable).toBe(false)
    expect(unknown.message).toContain('Agent')
  })
})
