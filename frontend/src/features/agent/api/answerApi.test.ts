import { afterEach, describe, expect, it, vi } from 'vitest'

import type { ConversationTopic } from '../model/answerTypes'
import type { PendingPlanConfirmation } from '../model/sessionTypes'
import { askQuestion, clearConversationContext, fetchConversationContext } from './answerApi'

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

  it('sends preset identity and explicit semantic context through v2', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'NEEDS_CLARIFICATION' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('查看当前状态'),
      messages: [{ role: 'ASSISTANT', content: 'previous answer' }],
      questionPresetId: 'sql-audit-overview',
      contractVersion: 'pcv1-0123456789abcdef',
      semanticContext: {
        activeSubjects: [{ subjectType: 'PROJECT', subjectId: 'sql-audit' }],
        resultReferences: [],
        audienceRole: 'INTERVIEWER',
        requestSource: 'REFERENCE',
        coveredTopics: [],
      },
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.requestToken).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    )
    expect(body.questionPresetId).toBe('sql-audit-overview')
    expect(body.contractVersion).toBe('pcv1-0123456789abcdef')
    expect(body.semanticContext).toEqual({
      activeSubjects: [{ subjectType: 'PROJECT', subjectId: 'sql-audit' }],
      resultReferences: [],
      audienceRole: 'INTERVIEWER',
      requestSource: 'REFERENCE',
      coveredTopics: [],
    })
    expect(body.context.referenceContext).toBeUndefined()
    expect(body.context.focusEvidenceIds).toBeUndefined()
    expect(body.messages).toEqual([{ role: 'ASSISTANT', content: 'previous answer' }])
  })

  it('submits an opaque confirmation unchanged for confirmation and regeneration actions', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))
    vi.stubGlobal('fetch', fetchMock)
    const confirmation: PendingPlanConfirmation = {
      confirmationId: 'confirmation-01',
      confirmationPlan: 'opaque-envelope',
      planFingerprint: 'sha256:opaque-fingerprint',
      integrityToken: 'opaque-integrity-token',
      expiresAt: '2026-08-10T12:10:00Z',
    }

    await askQuestion({
      ...input(''),
      question: undefined,
      action: 'CONFIRM_PLAN',
      agentTurnContract: 'stp-v1',
      planConfirmation: confirmation,
    })
    await askQuestion({
      ...input('Regenerate this plan'),
      action: 'REGENERATE_PLAN',
      agentTurnContract: 'stp-v1',
      semanticContext: {
        activeSubjects: [
          { subjectType: 'PROJECT', subjectId: 'project-a' },
          { subjectType: 'PROJECT', subjectId: 'project-b' },
        ],
      },
      invalidatedPlanReference: {
        planId: 'plan-01',
        planFingerprint: 'sha256:opaque-fingerprint',
      },
    })

    const confirmBody = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    const regenerateBody = JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))
    expect(confirmBody).toMatchObject({
      action: 'CONFIRM_PLAN',
      agentTurnContract: 'stp-v1',
      planConfirmation: {
        confirmationId: confirmation.confirmationId,
        confirmationPlan: confirmation.confirmationPlan,
        planFingerprint: confirmation.planFingerprint,
        integrityToken: confirmation.integrityToken,
      },
    })
    expect(confirmBody.question).toBeUndefined()
    expect(regenerateBody).toMatchObject({
      action: 'REGENERATE_PLAN',
      agentTurnContract: 'stp-v1',
      question: 'Regenerate this plan',
      semanticContext: {
        activeSubjects: [
          { subjectType: 'PROJECT', subjectId: 'project-a' },
          { subjectType: 'PROJECT', subjectId: 'project-b' },
        ],
      },
      invalidatedPlanReference: {
        planId: 'plan-01',
        planFingerprint: 'sha256:opaque-fingerprint',
      },
    })
    expect(regenerateBody.planConfirmation).toBeUndefined()
  })

  it('serializes semantic clarification continuation as explicit ASK stp-v1', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('比较两个项目'),
      action: 'ASK',
      agentTurnContract: 'stp-v1',
      semanticContext: {
        activeSubjects: [
          { subjectType: 'PROJECT', subjectId: 'project-a' },
          { subjectType: 'PROJECT', subjectId: 'project-b' },
        ],
      },
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body).toMatchObject({
      action: 'ASK',
      agentTurnContract: 'stp-v1',
      question: '比较两个项目',
      semanticContext: {
        activeSubjects: [
          { subjectType: 'PROJECT', subjectId: 'project-a' },
          { subjectType: 'PROJECT', subjectId: 'project-b' },
        ],
      },
    })
    expect(body.planConfirmation).toBeUndefined()
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

  // ── P3：ResumeToken 只通过 X-Conversation-Resume-Token Header 携带（handoff §3.1, §10.2）──

  it('does not send a resume token header on the first question of a new conversation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion(input('首问'))

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers)
    expect(headers.has('X-Conversation-Resume-Token')).toBe(false)
  })

  it('sends the resume token only via header and never in body, url, or cookie', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({ ...input('续问'), resumeToken: 'opaque-resume-token-abc' })

    const [url, requestInit] = fetchMock.mock.calls[0] ?? []
    const headers = new Headers(requestInit?.headers)
    expect(headers.get('X-Conversation-Resume-Token')).toBe('opaque-resume-token-abc')
    // Token 不得进入请求体或 URL。
    const body = String(requestInit?.body ?? '')
    expect(body).not.toContain('opaque-resume-token-abc')
    expect(String(url)).not.toContain('opaque-resume-token-abc')
  })

  it('serializes contextReference as a top-level field with handle and expected type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('从该推荐继续'),
      resumeToken: 'opaque-resume-token-abc',
      contextReference: {
        contextHandle: 'opaque-context-handle',
        expectedContextType: 'RECOMMENDATION',
      },
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.contextReference).toEqual({
      contextHandle: 'opaque-context-handle',
      expectedContextType: 'RECOMMENDATION',
    })
    // contextReference 必须是顶层字段，不落入 context 对象。
    expect(body.context.contextReference).toBeUndefined()
  })

  it('does not emit contextReference when omitted', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({ ...input('普通追问'), resumeToken: 'opaque-resume-token-abc' })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.contextReference).toBeUndefined()
  })

  // ── P5 stp-v2：contextReference.resultItemId 显式结果项（设计 §12.12 / handoff §2）──

  it('serializes contextReference.resultItemId when selecting an explicit result item', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({
      ...input('第二个推荐的架构'),
      resumeToken: 'opaque-resume-token-abc',
      contextReference: {
        contextHandle: 'opaque-context-handle',
        expectedContextType: 'RECOMMENDATION',
        resultItemId: 'item-2-opaque',
      },
    })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.contextReference).toEqual({
      contextHandle: 'opaque-context-handle',
      expectedContextType: 'RECOMMENDATION',
      resultItemId: 'item-2-opaque',
    })
  })

  it('accepts and forwards an stp-v2 agent turn contract', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ resolution: 'ANSWERED' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await askQuestion({ ...input('介绍项目'), agentTurnContract: 'stp-v2' })

    const body = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))
    expect(body.agentTurnContract).toBe('stp-v2')
  })

  // ── P3：GET /api/v2/conversation-context 刷新恢复（handoff §11）──

  it('fetches the conversation context summary with the resume token header', async () => {
    const summary = {
      contractVersion: 'p3-context-summary-v1',
      continuationStatus: 'AVAILABLE',
      summary: {
        recentTaskType: 'RECOMMENDATION',
        subjectLabels: ['SQL 审计'],
        facetLabels: ['VERIFICATION'],
        comparisonDimensionLabels: [],
        preferenceLabels: ['优先有验证'],
        canRefine: true,
      },
    }
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(summary), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchConversationContext('opaque-resume-token-abc')).resolves.toEqual(summary)

    const [url, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/v2/conversation-context')
    expect(requestInit?.method).toBe('GET')
    expect(new Headers(requestInit?.headers).get('X-Conversation-Resume-Token'))
      .toBe('opaque-resume-token-abc')
  })

  // ── P3：DELETE /api/v2/conversation-context 幂等清除（handoff §12）──

  it('clears the conversation context via DELETE and resolves on 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(clearConversationContext('opaque-resume-token-abc')).resolves.toBeUndefined()

    const [url, requestInit] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/v2/conversation-context')
    expect(requestInit?.method).toBe('DELETE')
    expect(new Headers(requestInit?.headers).get('X-Conversation-Resume-Token'))
      .toBe('opaque-resume-token-abc')
  })
})
