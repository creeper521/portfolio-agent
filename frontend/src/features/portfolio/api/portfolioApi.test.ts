import { afterEach, describe, expect, it, vi } from 'vitest'

import { getPortfolio, getProject } from './portfolioApi'
import { PortfolioApiError, request } from './portfolioApi'

describe('portfolio api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('loads the public portfolio summary', async () => {
    const payload = { owner: { role: 'Java 后端开发实习生' }, projects: [] }
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(getPortfolio()).resolves.toEqual(payload)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/portfolio', expect.objectContaining({ method: 'GET' }))
  })

  it('returns a stable message when the project request cannot reach the network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')))

    await expect(getProject('sql-audit')).rejects.toThrow('暂时无法连接作品集服务，请稍后重试')
  })

  it('keeps structured error code and retry delay from a safe error response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 'ANSWER_RATE_LIMITED',
        message: '请求过于频繁',
        retryAfterSeconds: 17,
      }), { status: 429, headers: { 'Content-Type': 'application/json' } }),
    ))

    const failure = await request('/api/v2/answers', { method: 'POST' })
      .catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(PortfolioApiError)
    expect(failure).toMatchObject({
      status: 429,
      code: 'ANSWER_RATE_LIMITED',
      retryAfterSeconds: 17,
    })
  })
})
