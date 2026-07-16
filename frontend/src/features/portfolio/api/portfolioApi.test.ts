import { afterEach, describe, expect, it, vi } from 'vitest'

import { getPortfolio, getProject } from './portfolioApi'

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
})
