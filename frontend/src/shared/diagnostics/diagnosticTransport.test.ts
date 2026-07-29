import { afterEach, describe, expect, it, vi } from 'vitest'

import { DiagnosticTransport } from './diagnosticTransport'
import type { ReportableFrontendEvent } from './frontendDiagnosticTypes'

function createEvent(index: number): ReportableFrontendEvent {
  return {
    schemaVersion: 1,
    eventName: 'frontend.agent.request.failed',
    occurredAt: '2026-07-29T00:00:00.000Z',
    clientSessionId: '1e4f8588-1d2a-4cf5-a1c3-1bc6e07b3b73',
    clientRequestId: `1e4f8588-1d2a-4cf5-a1c3-1bc6e07b3b${String(index).padStart(2, '0')}`,
    errorCode: `SAFE_CODE_${index}`,
  }
}

function uploadedEvents(fetchMock: ReturnType<typeof vi.fn>, callIndex = 0): unknown[] {
  const init = fetchMock.mock.calls[callIndex]?.[1] as RequestInit
  return JSON.parse(init.body as string).events as unknown[]
}

describe('diagnostic transport', () => {
  const transports: DiagnosticTransport[] = []

  afterEach(() => {
    for (const transport of transports) {
      transport.dispose()
    }
    transports.length = 0
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('keeps at most twenty in-memory events', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)

    for (let index = 0; index < 21; index += 1) {
      transport.report(createEvent(index))
    }
    await transport.flush()
    await transport.flush()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(uploadedEvents(fetchMock, 0)).toHaveLength(10)
    expect(uploadedEvents(fetchMock, 1)).toHaveLength(10)
    expect(JSON.stringify(fetchMock.mock.calls)).not.toContain('SAFE_CODE_20')
  })

  it('sends no more than ten events in one upload', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)

    for (let index = 0; index < 11; index += 1) {
      transport.report(createEvent(index))
    }
    await transport.flush()

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(uploadedEvents(fetchMock)).toHaveLength(10)
  })

  it('aborts a best-effort upload after two seconds without retrying', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn().mockImplementation((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true })
    }))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)
    transport.report(createEvent(1))

    const pendingFlush = transport.flush()
    await vi.advanceTimersByTimeAsync(2_000)
    await pendingFlush

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.signal?.aborted).toBe(true)
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('silently discards a failed upload instead of recursively reporting it', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('network unavailable'))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)
    const reportSpy = vi.spyOn(transport, 'report')

    transport.report(createEvent(1))
    await transport.flush()

    expect(reportSpy).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('does not use browser persistence for queued diagnostics', async () => {
    const storageGet = vi.spyOn(Storage.prototype, 'getItem')
    const storageSet = vi.spyOn(Storage.prototype, 'setItem')
    const indexedDbOpen = vi.fn()
    vi.stubGlobal('indexedDB', { open: indexedDbOpen })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 202 })))
    const transport = new DiagnosticTransport()
    transports.push(transport)

    transport.report(createEvent(1))
    await transport.flush()

    expect(storageGet).not.toHaveBeenCalled()
    expect(storageSet).not.toHaveBeenCalled()
    expect(indexedDbOpen).not.toHaveBeenCalled()
  })

  it('uses a keepalive raw fetch on pagehide', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)
    transport.installPagehideListener()
    transport.report(createEvent(1))

    window.dispatchEvent(new Event('pagehide'))
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/client-diagnostics', expect.objectContaining({
      method: 'POST',
      keepalive: true,
    }))
  })

  it('uses keepalive to cover both an in-flight batch and queued events on pagehide', async () => {
    let finishNormalUpload: ((response: Response) => void) | undefined
    const normalUpload = new Promise<Response>((resolve) => {
      finishNormalUpload = resolve
    })
    const fetchMock = vi.fn()
      .mockReturnValueOnce(normalUpload)
      .mockRejectedValueOnce(new TypeError('pagehide upload unavailable'))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)
    transport.installPagehideListener()
    transport.report(createEvent(1))
    const pendingNormalFlush = transport.flush()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())
    transport.report(createEvent(2))

    window.dispatchEvent(new Event('pagehide'))
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))

    const pagehideInit = fetchMock.mock.calls[1]?.[1] as RequestInit
    expect(pagehideInit.keepalive).toBe(true)
    expect(uploadedEvents(fetchMock, 1)).toEqual([
      expect.objectContaining({ errorCode: 'SAFE_CODE_1' }),
      expect.objectContaining({ errorCode: 'SAFE_CODE_2' }),
    ])
    finishNormalUpload?.(new Response(null, { status: 202 }))
    await pendingNormalFlush
  })

  it('keeps pagehide batches at ten while covering a full in-flight batch plus queued events', async () => {
    let finishNormalUpload: ((response: Response) => void) | undefined
    const normalUpload = new Promise<Response>((resolve) => {
      finishNormalUpload = resolve
    })
    const fetchMock = vi.fn()
      .mockReturnValueOnce(normalUpload)
      .mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)
    transport.installPagehideListener()
    for (let index = 0; index < 10; index += 1) {
      transport.report(createEvent(index))
    }
    const pendingNormalFlush = transport.flush()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())
    transport.report(createEvent(10))

    window.dispatchEvent(new Event('pagehide'))
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))

    expect(uploadedEvents(fetchMock, 1)).toHaveLength(10)
    expect(uploadedEvents(fetchMock, 2)).toEqual([
      expect.objectContaining({ errorCode: 'SAFE_CODE_10' }),
    ])
    expect((fetchMock.mock.calls[1]?.[1] as RequestInit).keepalive).toBe(true)
    expect((fetchMock.mock.calls[2]?.[1] as RequestInit).keepalive).toBe(true)
    finishNormalUpload?.(new Response(null, { status: 202 }))
    await pendingNormalFlush
  })

  it('drops malformed nested runtime fields without serializing them', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const transport = new DiagnosticTransport()
    transports.push(transport)

    transport.report({
      ...createEvent(1),
      errorCode: { messages: [{ answer: 'nested-answer-sentinel' }] },
    } as never)
    await transport.flush()

    expect(fetchMock).not.toHaveBeenCalled()
  })
})
