import { afterEach, describe, expect, it, vi } from 'vitest'

import { getClientSessionId } from './clientCorrelation'
import { frontendDiagnostics, installRuntimeDiagnostics } from './frontendDiagnostics'
import {
  createFirstPartyStackFingerprint,
  serializeFrontendEvent,
  type ReportableFrontendEvent,
} from './frontendDiagnosticTypes'

function createEvent(): ReportableFrontendEvent {
  return {
    schemaVersion: 1,
    eventName: 'frontend.agent.request.failed',
    occurredAt: '2026-07-29T00:00:00.000Z',
    clientSessionId: getClientSessionId(),
    clientRequestId: crypto.randomUUID(),
    errorCode: 'CLIENT_NETWORK_ERROR',
    errorKind: 'NETWORK',
    durationBucket: 'FROM_1000_TO_4999_MS',
  }
}

describe('frontend diagnostic event contract', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('serializes only approved diagnostic fields', () => {
    const event = createEvent()
    const unsafeRuntimeValue = {
      ...event,
      question: 'visitor-question-sentinel',
      message: 'error-message-sentinel',
      stack: 'stack-sentinel',
      url: 'https://example.test/private-url-sentinel',
      headers: { authorization: 'credential-sentinel' },
      requestBody: 'request-body-sentinel',
      responseBody: 'response-body-sentinel',
      body: { message: 'nested-body-message-sentinel' },
      response: { answer: 'nested-response-answer-sentinel' },
      header: { message: 'nested-header-message-sentinel' },
      messages: [{ content: 'messages-content-sentinel' }],
      answer: { message: 'nested-answer-message-sentinel' },
      provider: 'provider-sentinel',
      retrieval: 'retrieval-sentinel',
    } as ReportableFrontendEvent

    const serialized = JSON.stringify(serializeFrontendEvent(unsafeRuntimeValue))

    expect(serialized).toContain('frontend.agent.request.failed')
    expect(serialized).not.toContain('visitor-question-sentinel')
    expect(serialized).not.toContain('error-message-sentinel')
    expect(serialized).not.toContain('stack-sentinel')
    expect(serialized).not.toContain('private-url-sentinel')
    expect(serialized).not.toContain('credential-sentinel')
    expect(serialized).not.toContain('request-body-sentinel')
    expect(serialized).not.toContain('response-body-sentinel')
    expect(serialized).not.toContain('nested-body-message-sentinel')
    expect(serialized).not.toContain('nested-response-answer-sentinel')
    expect(serialized).not.toContain('nested-header-message-sentinel')
    expect(serialized).not.toContain('messages-content-sentinel')
    expect(serialized).not.toContain('nested-answer-message-sentinel')
    expect(serialized).not.toContain('provider-sentinel')
    expect(serialized).not.toContain('retrieval-sentinel')
  })

  it('creates a fresh plain allowlisted object from an untyped runtime value', () => {
    const event = createEvent()
    const runtimeValue = Object.assign(Object.create(null) as Record<string, unknown>, event, {
      messages: [{ message: 'visitor-message-sentinel' }],
      answer: { body: 'visitor-answer-sentinel' },
    })

    const sanitized = serializeFrontendEvent(runtimeValue)

    expect(sanitized).toEqual(event)
    expect(sanitized).not.toBe(runtimeValue)
    expect(Object.getPrototypeOf(sanitized as object)).toBe(Object.prototype)
    expect(JSON.stringify(sanitized)).not.toContain('visitor-message-sentinel')
    expect(JSON.stringify(sanitized)).not.toContain('visitor-answer-sentinel')
  })

  it('keeps the recovery count and guidance stage when both are well formed', () => {
    const event = {
      ...createEvent(),
      eventName: 'frontend.response.invalid' as const,
      errorCode: 'SUGGESTION_CONTRACT_RECOVERED',
      errorKind: 'INVALID_RESPONSE' as const,
      recoveredCount: 2,
      guidanceStage: 'OPENING' as const,
    }

    expect(serializeFrontendEvent(event)).toEqual(event)
  })

  it.each([
    ['event name', { eventName: 'frontend.not-approved' }],
    ['schema version', { schemaVersion: 2 }],
    ['timestamp', { occurredAt: 'not-an-instant' }],
    ['session UUID', { clientSessionId: 'not-a-uuid' }],
    ['request UUID', { clientRequestId: [] }],
    ['server request UUID', { serverRequestId: 'not-a-uuid' }],
    ['turn UUID', { turnId: { message: 'nested-secret' } }],
    ['error code type', { errorCode: { message: 'nested-secret' } }],
    ['error kind enum', { errorKind: 'NOT_APPROVED' }],
    ['fingerprint shape', { errorFingerprint: ['nested-secret'] }],
    ['duration bucket enum', { durationBucket: 'NOT_APPROVED' }],
    ['recovered count type', { recoveredCount: 'two' }],
    ['recovered count negative', { recoveredCount: -1 }],
    ['recovered count fraction', { recoveredCount: 1.5 }],
    ['guidance stage enum', { guidanceStage: 'NOT_A_STAGE' }],
  ])('rejects an invalid runtime %s', (_label, invalidField) => {
    expect(serializeFrontendEvent({ ...createEvent(), ...invalidField } as never)).toBeUndefined()
  })

  it('does not inspect unknown throwing accessors while sanitizing', () => {
    const event = createEvent()
    const runtimeValue = event as ReportableFrontendEvent & { message?: string }
    Object.defineProperty(runtimeValue, 'message', {
      get(): string {
        throw new Error('throwing-message-getter-sentinel')
      },
    })

    expect(serializeFrontendEvent(runtimeValue)).toEqual(event)
  })

  it('silently drops a malformed facade event', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const malformed = Object.create(null) as Record<string, unknown>
    Object.defineProperty(malformed, 'eventName', {
      get(): never {
        throw new Error('throwing-event-name-getter-sentinel')
      },
    })

    expect(() => frontendDiagnostics.report(malformed as never)).not.toThrow()
    await vi.runAllTimersAsync()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects unsafe properties at compile time', () => {
    const event = createEvent()

    const accept = (_event: ReportableFrontendEvent): void => undefined
    accept({
      ...event,
      // @ts-expect-error messages are never diagnostic fields
      message: 'secret',
    })
    accept({
      ...event,
      // @ts-expect-error raw stacks are never diagnostic fields
      stack: 'secret',
    })
    accept({
      ...event,
      // @ts-expect-error URLs are never diagnostic fields
      url: 'secret',
    })
    accept({
      ...event,
      // @ts-expect-error headers are never diagnostic fields
      headers: {},
    })
    accept({
      ...event,
      // @ts-expect-error request bodies are never diagnostic fields
      requestBody: 'secret',
    })
    accept({
      ...event,
      // @ts-expect-error response bodies are never diagnostic fields
      responseBody: 'secret',
    })
  })

  it('hashes only a normalized first-party stack frame', async () => {
    const digest = vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3]).buffer)
    vi.stubGlobal('crypto', { subtle: { digest } })
    const firstPartyUrl = `${window.location.origin}/assets/app.js?question=visitor-question-sentinel#fragment`

    const fingerprint = await createFirstPartyStackFingerprint([
      'Error: visitor-question-sentinel',
      `    at answerHandler (${firstPartyUrl}:12:3)`,
      '    at external (https://provider.example.test/sdk.js:1:1)',
    ].join('\n'))

    expect(fingerprint).toBe('010203')
    const normalizedInput = new TextDecoder().decode(digest.mock.calls[0]?.[1] as ArrayBuffer)
    expect(normalizedInput).not.toContain('visitor-question-sentinel')
    expect(normalizedInput).not.toContain('provider.example.test')
  })

  it('omits a fingerprint when no safe first-party frame can be normalized', async () => {
    await expect(createFirstPartyStackFingerprint('at external (https://provider.example.test/sdk.js:1:1)'))
      .resolves.toBeUndefined()
  })

  it('silently omits a fingerprint for malicious non-string stack values', async () => {
    const maliciousStack = new Proxy({}, {
      get(): never {
        throw new Error('throwing-split-getter-sentinel')
      },
    })

    await expect(createFirstPartyStackFingerprint(maliciousStack as never)).resolves.toBeUndefined()
  })

  it('runtime listeners contain a throwing Error.stack getter without an unhandled rejection', async () => {
    const report = vi.spyOn(frontendDiagnostics, 'report')
    const maliciousError = Object.create(Error.prototype) as Error
    Object.defineProperty(maliciousError, 'stack', {
      get(): never {
        throw new Error('throwing-stack-getter-sentinel')
      },
    })
    installRuntimeDiagnostics()
    const runtimeError = new Event('error')
    Object.defineProperty(runtimeError, 'error', { value: maliciousError })

    window.dispatchEvent(runtimeError)

    await vi.waitFor(() => expect(report).toHaveBeenCalledOnce())
    expect(report).toHaveBeenCalledWith(expect.not.objectContaining({ errorFingerprint: expect.anything() }))
  })
})
