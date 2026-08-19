import { afterEach, describe, expect, it, vi } from 'vitest'

import { loadPublicAgentTurnGoldenFixtures } from '../model/publicAgentTurnFixtureLoader'
import {
  cancelAgentTurn,
  clearConversation,
  fetchCurrentConversation,
  submitAgentTurn,
} from './agentTurnApi'

// 传输层合同测试：请求形状（Bearer、closed command、conversationWindow）、
// 响应分流（200 业务/非 200 错误 envelope/合同破损/网络/中止）、cancel/clear/current。

function goldenTurn(fileName: string): Record<string, unknown> {
  const fixture = loadPublicAgentTurnGoldenFixtures().find(
    (candidate) => candidate.fileName === fileName,
  )
  if (fixture === undefined) throw new Error(`缺少 fixture ${fileName}`)
  return JSON.parse(JSON.stringify(fixture.turn)) as Record<string, unknown>
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

function stubFetch() {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('submitAgentTurn', () => {
  it('发送 closed command envelope，携带 Bearer 与 conversationWindow', async () => {
    const fetch = stubFetch()
    fetch.mockResolvedValue(
      jsonResponse(200, {
        ...goldenTurn('conversational.json'),
        conversation: { conversationId: 'conversation-1', resumeToken: 'token-1' },
      }),
    )
    const result = await submitAgentTurn({
      requestId: '10000000-0000-4000-8000-000000000099',
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍 SQL 审计项目' } },
      surfaceContext: {
        subjectHint: { kind: 'PROJECT', slug: 'sql-audit' },
        audienceRole: 'INTERVIEWER',
        requestSource: 'AGENT_PAGE',
      },
      conversationWindow: [{ role: 'USER', content: '上一个问题' }],
      resumeToken: 'token-1',
    })
    expect(result.ok).toBe(true)
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/api/agent/turns')
    expect(init.method).toBe('POST')
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-1')
    const body = JSON.parse(String(init.body)) as Record<string, unknown>
    expect(body).toEqual({
      requestId: '10000000-0000-4000-8000-000000000099',
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍 SQL 审计项目' } },
      surfaceContext: {
        subjectHint: { kind: 'PROJECT', slug: 'sql-audit' },
        audienceRole: 'INTERVIEWER',
        requestSource: 'AGENT_PAGE',
      },
      conversationWindow: [{ role: 'USER', content: '上一个问题' }],
    })

    if (result.ok) {
      expect(result.turn.kind).toBe('CONVERSATIONAL')
      expect(result.conversation).toEqual({
        conversationId: 'conversation-1',
        resumeToken: 'token-1',
      })
    }
  })

  it('首轮无 Token 不发送 Authorization；metadata 无新 token 时 conversation 为 null', async () => {
    const fetch = stubFetch()
    const payload = goldenTurn('answer-complete.json')
    delete (payload as Record<string, unknown>).conversation
    fetch.mockResolvedValue(jsonResponse(200, payload))
    const result = await submitAgentTurn({
      requestId: '10000000-0000-4000-8000-000000000001',
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍' } },
    })
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined()
    if (result.ok) {
      expect(result.turn.kind).toBe('ANSWER')
      expect(result.conversation).toBeNull()
    }
  })

  it('非 200 响应映射错误 envelope（含 Retry-After 语义字段）', async () => {
    const fetch = stubFetch()
    fetch.mockResolvedValue(
      jsonResponse(409, {
        requestId: '10000000-0000-4000-8000-000000000009',
        error: {
          code: 'TURN_IN_PROGRESS',
          message: '同请求仍在执行',
          retryable: true,
          retryAfterSeconds: 4,
        },
      }),
    )
    const result = await submitAgentTurn({
      requestId: '10000000-0000-4000-8000-000000000009',
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍' } },
    })
    if (result.ok) throw new Error('期望失败')
    expect(result.failure.kind).toBe('API')
    expect(result.failure.status).toBe(409)
    expect(result.failure.code).toBe('TURN_IN_PROGRESS')
    expect(result.failure.retryable).toBe(true)
    expect(result.failure.retryAfterSeconds).toBe(4)
  })

  it('200 但 PublicAgentTurn 破损时 fail-closed 为合同错误，不回退旧合同', async () => {
    const fetch = stubFetch()
    fetch.mockResolvedValue(jsonResponse(200, { requestId: 'x', kind: 'CONFIRMATION' }))
    const result = await submitAgentTurn({
      requestId: '10000000-0000-4000-8000-000000000001',
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍' } },
    })
    if (result.ok) throw new Error('期望失败')
    expect(result.failure.kind).toBe('CONTRACT')
    expect(result.failure.code).toBe('PUBLIC_TURN_CONTRACT_INVALID')
  })

  it('网络异常映射为可重试网络失败；中止映射为 ABORTED', async () => {
    const fetch = stubFetch()
    fetch.mockRejectedValue(new TypeError('failed to fetch'))
    const network = await submitAgentTurn({
      requestId: '10000000-0000-4000-8000-000000000001',
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍' } },
    })
    if (network.ok) throw new Error('期望失败')
    expect(network.failure.kind).toBe('NETWORK')
    expect(network.failure.retryable).toBe(true)

    fetch.mockRejectedValue(new DOMException('aborted', 'AbortError'))
    const aborted = await submitAgentTurn(
      {
        requestId: '10000000-0000-4000-8000-000000000001',
        command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '介绍' } },
      },
      { signal: AbortSignal.abort() },
    )
    if (aborted.ok) throw new Error('期望失败')
    expect(aborted.failure.kind).toBe('ABORTED')
  })
})

describe('cancel / current / clear', () => {
  it('cancel 映射 204/409/404/异常', async () => {
    const fetch = stubFetch()
    fetch.mockResolvedValue(new Response(null, { status: 204 }))
    expect(await cancelAgentTurn('10000000-0000-4000-8000-000000000001', 'token-1')).toBe('CANCELLED')
    const cancelInit = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect((cancelInit.headers as Record<string, string>).Authorization).toBe('Bearer token-1')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/agent/turns/10000000-0000-4000-8000-000000000001')

    fetch.mockResolvedValue(jsonResponse(409, { error: { code: 'TURN_ALREADY_COMPLETED', message: '', retryable: false } }))
    expect(await cancelAgentTurn('10000000-0000-4000-8000-000000000001')).toBe('ALREADY_COMPLETED')

    fetch.mockResolvedValue(jsonResponse(404, { error: { code: 'TURN_NOT_FOUND', message: '', retryable: false } }))
    expect(await cancelAgentTurn('10000000-0000-4000-8000-000000000001')).toBe('NOT_FOUND')

    fetch.mockRejectedValue(new TypeError('failed'))
    expect(await cancelAgentTurn('10000000-0000-4000-8000-000000000001')).toBe('FAILED')
  })

  it('GET current 解析 {conversationId,status}，401 视为 invalid', async () => {
    const fetch = stubFetch()
    fetch.mockResolvedValue(jsonResponse(200, { conversationId: 'conversation-1', status: 'ACTIVE' }))
    expect(await fetchCurrentConversation('token-1')).toEqual({
      ok: true,
      conversationId: 'conversation-1',
      status: 'ACTIVE',
    })
    fetch.mockResolvedValue(jsonResponse(401, { error: { code: 'RESUME_TOKEN_INVALID', message: '', retryable: false } }))
    expect(await fetchCurrentConversation('token-1')).toEqual({ ok: false, invalid: true })
  })

  it('DELETE current 204 为 CLEARED，其余为 FAILED', async () => {
    const fetch = stubFetch()
    fetch.mockResolvedValue(new Response(null, { status: 204 }))
    expect(await clearConversation('token-1')).toBe('CLEARED')
    fetch.mockResolvedValue(jsonResponse(503, { error: { code: 'AGENT_STATE_UNAVAILABLE', message: '', retryable: false } }))
    expect(await clearConversation('token-1')).toBe('FAILED')
  })
})
