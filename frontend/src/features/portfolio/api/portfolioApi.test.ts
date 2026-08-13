import { afterEach, describe, expect, it, vi } from 'vitest'

import { getPortfolio, getProject } from './portfolioApi'
import { PortfolioApiError, RequestOperation, request } from './portfolioApi'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

describe('portfolio api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
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
    const requestInit = fetchMock.mock.calls[0]?.[1] as RequestInit
    const headers = new Headers(requestInit.headers)
    expect(headers.get('X-Client-Session-Id')).toMatch(/^[0-9a-f-]{36}$/)
    expect(headers.get('X-Client-Request-Id')).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('returns a stable message when the project request cannot reach the network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')))

    await expect(getProject('sql-audit')).rejects.toThrow('暂时无法连接作品集服务，请稍后重试')
  })

  it('keeps structured error code and retry delay from a safe error response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 'ANSWER_RATE_LIMITED',
        message: 'server-controlled-message',
        retryAfterSeconds: 17,
      }), { status: 429, headers: { 'Content-Type': 'application/json' } }),
    ))

    const failure = await request('/api/v2/answers', { method: 'POST' }, { operation: RequestOperation.ANSWER })
      .catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(PortfolioApiError)
    if (!(failure instanceof PortfolioApiError)) {
      throw new Error('Expected PortfolioApiError')
    }
    expect(failure).toMatchObject({
      kind: 'HTTP',
      status: 429,
      code: 'ANSWER_RATE_LIMITED',
      retryAfterSeconds: 17,
      action: 'RETRY_AFTER',
    })
    expect(failure.message).toBe('作品集服务暂时无法处理这个请求')
    expect(failure.message).not.toContain('server-controlled-message')
  })

  it('normalizes an unknown response code before storing or reporting it', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        code: 'VISITOR_SECRET_TOKEN',
      }), { status: 500, headers: { 'Content-Type': 'application/json' } }),
    ))
    const report = vi.spyOn(frontendDiagnostics, 'report')

    const failure = await request(
      '/api/v2/answers',
      { method: 'POST' },
      { operation: RequestOperation.ANSWER },
    ).catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(PortfolioApiError)
    expect(failure).toMatchObject({
      code: 'UNKNOWN',
      action: 'RETRY',
    })
    expect(report).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'UNKNOWN',
    }))
    expect(JSON.stringify(report.mock.calls)).not.toContain('VISITOR_SECRET_TOKEN')
  })

  it('derives a safe retry action when a caller supplies an unknown code and dangerous action', () => {
    const failure = new PortfolioApiError('safe fixture', {
      kind: 'HTTP',
      code: 'VISITOR_SECRET_TOKEN' as never,
      action: 'NAVIGATE_BACK',
      clientRequestId: crypto.randomUUID(),
    })

    expect(failure.code).toBe('UNKNOWN')
    expect(failure.action).toBe('RETRY')
  })

  it('refuses a missing or unknown request operation before fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/api/v2/answers', { method: 'POST' }, {} as never))
      .rejects.toThrow('Request operation is required')
    await expect(request('/api/v2/answers', { method: 'POST' }, { operation: 'UNKNOWN' } as never))
      .rejects.toThrow('Request operation is required')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('does not report a successful request that finishes under five seconds', async () => {
    const payload = { owner: { role: 'Java 后端开发实习生' }, projects: [] }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const now = vi.spyOn(performance, 'now')
      .mockReturnValueOnce(100)
      .mockReturnValueOnce(4_999)
    const report = vi.spyOn(frontendDiagnostics, 'report')

    await expect(getPortfolio()).resolves.toEqual(payload)

    expect(now).toHaveBeenCalledTimes(2)
    expect(report).not.toHaveBeenCalled()
  })

  it('reports an answer network failure through the closed diagnostics facade', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network unavailable')))
    const report = vi.spyOn(frontendDiagnostics, 'report')

    await request('/api/v2/answers', { method: 'POST' }, { operation: RequestOperation.ANSWER }).catch(() => undefined)

    expect(report).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.agent.request.failed',
      errorCode: 'CLIENT_NETWORK_ERROR',
      errorKind: 'NETWORK',
    }))
    expect(JSON.stringify(report.mock.calls)).not.toContain('/api/v2/answers')
  })

  it('reports a slow completed answer request after five seconds', async () => {
    const payload = { answer: 'safe answer' }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(performance, 'now')
      .mockReturnValueOnce(100)
      .mockReturnValueOnce(5_100)
    const report = vi.spyOn(frontendDiagnostics, 'report')

    await expect(request('/api/v2/answers', { method: 'POST' }, { operation: RequestOperation.ANSWER })).resolves.toEqual(payload)

    expect(report).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.agent.request.slow',
      durationBucket: 'GE_5000_MS',
      clientRequestId: new Headers((fetchMock.mock.calls[0]?.[1] as RequestInit).headers).get('X-Client-Request-Id'),
    }))
  })

  it('classifies caller cancellation while reading an HTTP error response', async () => {
    const controller = new AbortController()
    let rejectJson: (reason?: unknown) => void = () => undefined
    const json = vi.fn().mockImplementation(() => new Promise((_resolve, reject) => {
      rejectJson = reject
    }))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 429,
      headers: new Headers({ 'X-Request-Id': 'server-request-id' }),
      json,
    }))

    const failure = request('/api/v2/answers', { method: 'POST' }, {
      operation: RequestOperation.ANSWER,
      signal: controller.signal,
    })
      .catch((error: unknown) => error)
    await vi.waitFor(() => expect(json).toHaveBeenCalledOnce())
    controller.abort()
    rejectJson(new DOMException('Aborted', 'AbortError'))

    await expect(failure).resolves.toMatchObject({
      kind: 'CANCELLED',
      code: 'REQUEST_CANCELLED',
      action: 'NONE',
      requestId: 'server-request-id',
    })
  })

  it('classifies a local timeout while reading an HTTP error response', async () => {
    vi.useFakeTimers()
    let responseSignal: AbortSignal | null = null
    const json = vi.fn().mockImplementation(() => new Promise((_resolve, reject) => {
      responseSignal?.addEventListener(
        'abort',
        () => reject(new DOMException('Aborted', 'AbortError')),
        { once: true },
      )
    }))
    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url: string, init: RequestInit) => {
      responseSignal = init.signal as AbortSignal
      return Promise.resolve({
        ok: false,
        status: 504,
        headers: new Headers({ 'X-Request-Id': 'server-request-id' }),
        json,
      })
    }))

    const failure = request('/api/v2/answers', { method: 'POST' }, {
      operation: RequestOperation.ANSWER,
      timeoutMs: 1,
    })
      .catch((error: unknown) => error)
    await vi.advanceTimersByTimeAsync(1)

    await expect(failure).resolves.toMatchObject({
      kind: 'TIMEOUT',
      code: 'CLIENT_REQUEST_TIMEOUT',
      action: 'RETRY',
      requestId: 'server-request-id',
    })
  })

  it('keeps the server request id and client request id on a failed response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'VALIDATION_ERROR' }), {
        status: 400,
        headers: { 'X-Request-Id': 'server-request-id' },
      }),
    ))

    const failure = await request('/api/v2/answers', { method: 'POST' }, { operation: RequestOperation.ANSWER })
      .catch((error: unknown) => error)

    expect(failure).toMatchObject({
      kind: 'HTTP',
      code: 'VALIDATION_ERROR',
      requestId: 'server-request-id',
      action: 'CORRECT_INPUT',
      clientRequestId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    })
  })

  it('classifies caller cancellation without a retry action', async () => {
    const controller = new AbortController()
    controller.abort()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Aborted', 'AbortError')))

    const failure = await request('/api/v2/answers', { method: 'POST' }, {
      operation: RequestOperation.ANSWER,
      signal: controller.signal,
    })
      .catch((error: unknown) => error)

    expect(failure).toMatchObject({
      kind: 'CANCELLED',
      code: 'REQUEST_CANCELLED',
      action: 'NONE',
      clientRequestId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    })
  })

  it('classifies a rejected fetch as a retryable network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')))

    const failure = await request('/api/v2/answers', { method: 'POST' }, { operation: RequestOperation.ANSWER })
      .catch((error: unknown) => error)

    expect(failure).toMatchObject({
      kind: 'NETWORK',
      code: 'CLIENT_NETWORK_ERROR',
      action: 'RETRY',
    })
  })

  it('classifies a local timeout as retryable', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url: string, init: RequestInit) => (
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      })
    )))

    const pending = request('/api/v2/answers', { method: 'POST' }, {
      operation: RequestOperation.ANSWER,
      timeoutMs: 1,
    })
    const failure = pending.catch((error: unknown) => error)
    await vi.advanceTimersByTimeAsync(1)

    await expect(failure).resolves.toMatchObject({
      kind: 'TIMEOUT',
      code: 'CLIENT_REQUEST_TIMEOUT',
      action: 'RETRY',
    })
  })

  it('classifies invalid JSON from a successful response as retryable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('{', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    ))

    const failure = await request('/api/v2/answers', { method: 'POST' }, { operation: RequestOperation.ANSWER })
      .catch((error: unknown) => error)

    expect(failure).toMatchObject({
      kind: 'INVALID_RESPONSE',
      code: 'CLIENT_INVALID_RESPONSE',
      action: 'RETRY',
    })
  })

  // P3：DELETE /api/v2/conversation-context 幂等返回 204 No Content。
  // 不带 expectNoContent 时，空体会被误判为 INVALID_RESPONSE；该选项让 204 直接成功。
  it('resolves without parsing JSON when expectNoContent receives a 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(null, { status: 204 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/api/v2/conversation-context', { method: 'DELETE' }, {
      operation: RequestOperation.ANSWER,
      expectNoContent: true,
    })).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v2/conversation-context',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('still surfaces HTTP errors even when expectNoContent is set', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'INTERNAL_ERROR' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))

    const failure = await request('/api/v2/conversation-context', { method: 'DELETE' }, {
      operation: RequestOperation.ANSWER,
      expectNoContent: true,
    }).catch((error: unknown) => error)

    expect(failure).toMatchObject({
      kind: 'HTTP',
      status: 500,
      code: 'INTERNAL_ERROR',
      action: 'RETRY',
    })
  })

  describe('single request failure publishes exactly one closed diagnostic', () => {
    it.each([
      {
        name: 'HTTP 429',
        fetchResult: new Response(JSON.stringify({ code: 'ANSWER_RATE_LIMITED' }), {
          status: 429,
          headers: { 'Content-Type': 'application/json' },
        }),
        expectedEvent: 'frontend.agent.request.failed',
        expectedErrorCode: 'ANSWER_RATE_LIMITED',
      },
      {
        name: 'HTTP 503',
        fetchResult: new Response(JSON.stringify({ code: 'ANSWER_REQUEST_TIMEOUT' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
        expectedEvent: 'frontend.agent.request.failed',
        expectedErrorCode: 'ANSWER_REQUEST_TIMEOUT',
      },
      {
        name: 'business 404',
        fetchResult: new Response(JSON.stringify({ code: 'PROJECT_NOT_FOUND' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        }),
        expectedEvent: 'frontend.agent.request.failed',
        expectedErrorCode: 'PROJECT_NOT_FOUND',
      },
      {
        name: 'caller cancellation',
        fetchResult: new DOMException('Aborted', 'AbortError'),
        expectedEvent: 'frontend.agent.request.cancelled',
        expectedErrorCode: 'REQUEST_CANCELLED',
      },
      {
        name: 'pre-response network failure',
        fetchResult: new TypeError('network unavailable'),
        expectedEvent: 'frontend.agent.request.failed',
        expectedErrorCode: 'CLIENT_NETWORK_ERROR',
      },
    ])('$name publishes exactly one report without sensitive payload', async ({
      fetchResult,
      expectedEvent,
      expectedErrorCode,
    }) => {
      const controller = new AbortController()
      if (fetchResult instanceof DOMException) {
        controller.abort()
      }
      const isHttpResponse = typeof Response !== 'undefined' && fetchResult instanceof Response
      vi.stubGlobal('fetch', vi.fn().mockImplementation(() => (
        isHttpResponse ? Promise.resolve(fetchResult) : Promise.reject(fetchResult)
      )))
      const report = vi.spyOn(frontendDiagnostics, 'report')

      await request('/api/v2/answers', { method: 'POST' }, {
        operation: RequestOperation.ANSWER,
        signal: controller.signal,
      }).catch(() => undefined)

      expect(report).toHaveBeenCalledTimes(1)
      expect(report.mock.calls[0]?.[0]).toMatchObject({
        eventName: expectedEvent,
        errorCode: expectedErrorCode,
      })
      const serialized = JSON.stringify(report.mock.calls)
      expect(serialized).not.toContain('/api/v2/answers')
      expect(serialized).not.toContain('VISITOR_SECRET_TOKEN')
      expect(serialized).not.toContain('visitor question')
    })
  })
})
