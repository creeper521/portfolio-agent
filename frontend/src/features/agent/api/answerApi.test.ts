import { afterEach, describe, expect, it, vi } from 'vitest'

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
      caseSlug: 'some-case',
      messages: [{ role: 'USER', content: '之前的问题' }],
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v2/answers',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          turnId: 'turn-1',
          question: '介绍项目',
          messages: [{ role: 'USER', content: '之前的问题' }],
          context: {
            projectSlug: 'sql-audit',
            caseSlug: 'some-case',
            audienceRole: 'INTERVIEWER',
            source: 'AGENT_PAGE',
          },
        }),
      }),
    )
  })

  it('uses the api error message for a non-success response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: '请求参数不符合要求' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(askQuestion(input(''))).rejects.toThrow('请求参数不符合要求')
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

    await vi.advanceTimersByTimeAsync(10_000)
    await rejection
  })

  it('keeps the timeout active while the answer response body is being read', async () => {
    vi.useFakeTimers()
    let requestSignal: AbortSignal | undefined
    const fetchMock = vi.fn((_url: RequestInfo | URL, init?: RequestInit) => {
      requestSignal = init?.signal ?? undefined
      return Promise.resolve({
        ok: true,
        json: () =>
          new Promise((_resolve, reject) => {
            requestSignal?.addEventListener('abort', () =>
              reject(new DOMException('Aborted', 'AbortError')),
            )
          }),
      } as Response)
    })
    vi.stubGlobal('fetch', fetchMock)

    const request = askQuestion(input('介绍项目'))
    await Promise.resolve()
    await Promise.resolve()
    const rejection = expect(request).rejects.toThrow('作品集服务响应超时，请稍后重试')

    await vi.advanceTimersByTimeAsync(10_000)
    expect(requestSignal?.aborted).toBe(true)
    await rejection
  })
})
