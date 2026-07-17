import { describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../data/previewPublicContent'
import { ApiPublicContentRepository } from '../repository/apiPublicContentRepository'
import { createPublicContentState } from './usePublicContent'

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
})
