import { afterEach, describe, expect, it, vi } from 'vitest'

import type { ConversationTopic } from '../model/answerTypes'
import { askQuestion } from './answerApi'

function input(question: string) {
  return {
    turnId: 'turn-1',
    projectSlug: 'sql-audit',
    audienceRole: 'INTERVIEWER' as const,
    source: 'AGENT_PAGE' as const,
    question,
  }
}

describe('answer api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('posts a project question as json', async () => {
    const response = { resolution: 'ANSWERED', generationMode: 'MODEL' }
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('介绍项目'),
      requestToken: '63f63c75-16e8-49e7-864d-dcd0fe100d50',
      caseSlug: 'some-case',
      messages: [{ role: 'USER', content: '之前的问题' }],
    })

    expect(fetchMock).toHaveBeenCalledOnce()
    const requestInit = fetchMock.mock.calls[0]?.[1]
    const headers = new Headers(requestInit?.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-Client-Session-Id')).toMatch(/^[0-9a-f-]{36}$/)
    expect(headers.get('X-Client-Request-Id')).toMatch(/^[0-9a-f-]{36}$/)
    expect(requestInit).toMatchObject({
      method: 'POST',
      signal: expect.any(AbortSignal),
      body: JSON.stringify({
        turnId: 'turn-1',
        requestToken: '63f63c75-16e8-49e7-864d-dcd0fe100d50',
        question: '介绍项目',
        messages: [{ role: 'USER', content: '之前的问题' }],
        context: {
          projectSlug: 'sql-audit',
          caseSlug: 'some-case',
          audienceRole: 'INTERVIEWER',
          source: 'AGENT_PAGE',
          coveredTopics: [],
        },
      }),
    })
  })

  it('sends deduplicated coveredTopics inside the request context only', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('继续深入'),
      coveredTopics: ['BACKGROUND', 'SOLUTION', 'BACKGROUND'] as ConversationTopic[],
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.context.coveredTopics).toEqual(['BACKGROUND', 'SOLUTION'])
    expect(body.coveredTopics).toBeUndefined()
  })

  it('sends the complete recommendation context only for a refinement request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const recommendationContext = {
      recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      contentVersion: 'public-2026-07-31',
      careerTrack: null,
      audienceRole: 'INTERVIEWER',
      capabilityCodes: ['POSTGRESQL', 'RAG'],
      requestedSize: 2,
      selectedPortfolioIds: ['project-1', 'case-2'],
    }

    await askQuestion({ ...input('refine recommendation'), recommendationContext })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.context.recommendationContext).toEqual(recommendationContext)
    expect(body.context.recommendationContext).not.toBe(recommendationContext)
  })

  it('strips extra fields from forwarded messages', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('继续深入'),
      messages: [
        { role: 'USER', content: '之前的问题', id: 'message-1', answer: { title: 'x' } } as never,
      ],
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.messages).toEqual([{ role: 'USER', content: '之前的问题' }])
  })

  it('uses a stable local message for a non-success response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: '请求参数不符合要求' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(askQuestion(input(''))).rejects.toMatchObject({
      name: 'PortfolioApiError',
      kind: 'HTTP',
      status: 400,
      message: '作品集服务暂时无法处理这个请求',
    })
  })

  it('keeps frontend-only referential context out of the strict v2 payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'BOUNDARY' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('查看当前状态'),
      messages: [{ role: 'ASSISTANT', content: 'previous answer' }],
      contextEnvelope: {
        previousContentVersion: '2026-07-21.1',
        projectSlugs: ['sql-audit'],
        questionPresetId: 'sql-audit-overview',
        referencedClaimIds: ['claim-sql-audit-delivered'],
        selectedSectionType: 'STATUS',
        followUpIntent: 'CURRENT_STATUS',
      },
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.requestToken).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    )
    expect(body.contextEnvelope).toBeUndefined()
    expect(body.questionPresetId).toBeUndefined()
    expect(body.context.focusEvidenceIds).toBeUndefined()
    expect(body.messages).toEqual([{ role: 'ASSISTANT', content: 'previous answer' }])
  })

  it('aborts a stalled request and returns a stable timeout message', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn((_url: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const request = askQuestion(input('介绍项目'))
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBeInstanceOf(AbortSignal)
    const rejection = expect(request).rejects.toThrow('作品集服务响应超时，请稍后重试')

    await vi.advanceTimersByTimeAsync(15_000)
    await rejection
  })

  it('keeps the timeout active while the answer response body is being read', async () => {
    vi.useFakeTimers()
    let requestSignal: AbortSignal | undefined
    const fetchMock = vi.fn((_url: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal ?? undefined
      return Promise.resolve({
        ok: true,
        headers: new Headers(),
        json: () =>
          new Promise((_resolve, reject) => {
            requestSignal?.addEventListener('abort', () =>
              reject(new DOMException('Aborted', 'AbortError')),
            )
          }),
      } as Response)
    })
    vi.stubGlobal('fetch', fetchMock)

    const failure = askQuestion(input('介绍项目')).catch((error: unknown) => error)
    await Promise.resolve()
    await Promise.resolve()

    await vi.advanceTimersByTimeAsync(15_000)
    expect(requestSignal?.aborted).toBe(true)
    await expect(failure).resolves.toMatchObject({
      name: 'PortfolioApiError',
      kind: 'TIMEOUT',
      code: 'CLIENT_REQUEST_TIMEOUT',
      action: 'RETRY',
      message: '作品集服务响应超时，请稍后重试',
    })
  })

  it('forwards an explicit request token and external cancellation signal', async () => {
    const controller = new AbortController()
    const fetchMock = vi.fn((_url: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () =>
          reject(new DOMException('Aborted', 'AbortError')),
        )
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const pending = askQuestion(
      { ...input('介绍项目'), requestToken: '63f63c75-16e8-49e7-864d-dcd0fe100d50' },
      { signal: controller.signal },
    )
    controller.abort()

    await expect(pending).rejects.toMatchObject({
      name: 'PortfolioApiError',
      code: 'REQUEST_CANCELLED',
    })
    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.requestToken).toBe('63f63c75-16e8-49e7-864d-dcd0fe100d50')
  })
})
