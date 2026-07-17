import { describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../data/previewPublicContent'
import type { PublicPortfolio } from '../model/publicContentTypes'
import { ApiPublicContentRepository } from '../repository/apiPublicContentRepository'
import { createPublicContentState } from './usePublicContent'

function createDeferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('createPublicContentState', () => {
  it('moves from loading to ready', async () => {
    const repository = new ApiPublicContentRepository(
      vi.fn().mockResolvedValue(previewPublicContent),
    )
    const state = createPublicContentState(repository)

    const request = state.load()

    expect(state.status.value).toBe('loading')
    await request
    expect(state.status.value).toBe('ready')
    expect(state.portfolio.value?.projects[0]?.slug).toBe('sql-audit')
  })

  it('clears a failed cache before retrying', async () => {
    const loader = vi.fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(previewPublicContent)
    const state = createPublicContentState(new ApiPublicContentRepository(loader))

    await state.load()

    expect(state.status.value).toBe('error')
    await state.retry()
    expect(loader).toHaveBeenCalledTimes(2)
    expect(state.status.value).toBe('ready')
  })

  it('replaces internal failure details with a fixed public message', async () => {
    const internalDetail = 'GET http://127.0.0.1:8080/api failed at C:\\private\\snapshot.json'
    const repository = new ApiPublicContentRepository(
      vi.fn().mockRejectedValue(new Error(internalDetail)),
    )
    const state = createPublicContentState(repository)

    await state.load()

    expect(state.error.value).toBe('公开内容暂时无法加载，请稍后重试')
    expect(state.error.value).not.toContain('127.0.0.1')
    expect(state.error.value).not.toContain('C:\\private')
  })

  it('returns the same state-level promise for concurrent loads', async () => {
    const deferred = createDeferred<PublicPortfolio>()
    const loader = vi.fn().mockReturnValue(deferred.promise)
    const state = createPublicContentState(new ApiPublicContentRepository(loader))

    const firstLoad = state.load()
    const secondLoad = state.load()

    expect(secondLoad).toBe(firstLoad)
    expect(loader).toHaveBeenCalledTimes(1)

    deferred.resolve(previewPublicContent)
    await Promise.all([firstLoad, secondLoad])
    expect(state.status.value).toBe('ready')
  })

  it('invalidates once and shares one request across concurrent retries', async () => {
    const deferred = createDeferred<PublicPortfolio>()
    const loader = vi.fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockReturnValue(deferred.promise)
    const repository = new ApiPublicContentRepository(loader)
    const invalidateSpy = vi.spyOn(repository, 'invalidate')
    const state = createPublicContentState(repository)
    await state.load()

    const firstRetry = state.retry()
    const secondRetry = state.retry()

    expect.soft(secondRetry).toBe(firstRetry)
    expect.soft(invalidateSpy).toHaveBeenCalledTimes(1)
    expect.soft(loader).toHaveBeenCalledTimes(2)

    deferred.resolve(previewPublicContent)
    await Promise.all([firstRetry, secondRetry])
    expect(state.status.value).toBe('ready')
  })
})
