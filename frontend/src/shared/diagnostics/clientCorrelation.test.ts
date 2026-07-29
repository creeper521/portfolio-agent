import { afterEach, describe, expect, it, vi } from 'vitest'

describe('client correlation', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('keeps one session id in module memory and creates a request id per call', async () => {
    const storageGetItem = vi.spyOn(Storage.prototype, 'getItem')
    const storageSetItem = vi.spyOn(Storage.prototype, 'setItem')
    const storageRemoveItem = vi.spyOn(Storage.prototype, 'removeItem')
    const storageClear = vi.spyOn(Storage.prototype, 'clear')
    const storageKey = vi.spyOn(Storage.prototype, 'key')
    const indexedDbOpen = vi.fn()
    const indexedDbDeleteDatabase = vi.fn()
    vi.stubGlobal('indexedDB', {
      open: indexedDbOpen,
      deleteDatabase: indexedDbDeleteDatabase,
    })
    const cookieGet = vi.spyOn(Document.prototype, 'cookie', 'get')
    const cookieSet = vi.spyOn(Document.prototype, 'cookie', 'set')
    const historyPushState = vi.spyOn(window.history, 'pushState')
    const historyReplaceState = vi.spyOn(window.history, 'replaceState')
    const { createClientRequestId, getClientSessionId } = await import('./clientCorrelation')

    const sessionA = getClientSessionId()
    const sessionB = getClientSessionId()
    const requestA = createClientRequestId()
    const requestB = createClientRequestId()
    const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

    expect(sessionA).toBe(sessionB)
    expect(requestA).not.toBe(requestB)
    expect(sessionA).toMatch(UUID)
    expect(requestA).toMatch(UUID)
    expect(storageGetItem).not.toHaveBeenCalled()
    expect(storageSetItem).not.toHaveBeenCalled()
    expect(storageRemoveItem).not.toHaveBeenCalled()
    expect(storageClear).not.toHaveBeenCalled()
    expect(storageKey).not.toHaveBeenCalled()
    expect(indexedDbOpen).not.toHaveBeenCalled()
    expect(indexedDbDeleteDatabase).not.toHaveBeenCalled()
    expect(cookieGet).not.toHaveBeenCalled()
    expect(cookieSet).not.toHaveBeenCalled()
    expect(historyPushState).not.toHaveBeenCalled()
    expect(historyReplaceState).not.toHaveBeenCalled()
  })

  it('does not change the current URL or browser history state', async () => {
    const hrefBefore = window.location.href
    const historyStateBefore = window.history.state
    const { createClientRequestId, getClientSessionId } = await import('./clientCorrelation')

    getClientSessionId()
    createClientRequestId()

    expect(window.location.href).toBe(hrefBefore)
    expect(window.history.state).toEqual(historyStateBefore)
  })
})
