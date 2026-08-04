import { describe, expect, it, vi } from 'vitest'

import {
  askWithPresetContractRetry,
  isPresetContractUnavailable,
} from './presetContractRetry'

describe('askWithPresetContractRetry', () => {
  it('retries a stale preset exactly once with the server version', async () => {
    const send = vi.fn()
      .mockResolvedValueOnce({
        resolution: 'CAPABILITY_UNAVAILABLE',
        noticeCode: 'PRESET_CONTRACT_STALE',
        questionPresetId: 'preset-a',
        contractVersion: 'pcv1-1111111111111111',
      })
      .mockResolvedValueOnce({ resolution: 'ANSWERED' })

    await askWithPresetContractRetry({
      questionPresetId: 'preset-a',
      contractVersion: 'pcv1-0000000000000000',
    }, send)

    expect(send).toHaveBeenCalledTimes(2)
    expect(send.mock.calls[1]?.[0]).toMatchObject({
      questionPresetId: 'preset-a',
      contractVersion: 'pcv1-1111111111111111',
    })
  })

  it('returns the second stale response without a third request', async () => {
    const stale = {
      resolution: 'CAPABILITY_UNAVAILABLE',
      noticeCode: 'PRESET_CONTRACT_STALE',
      questionPresetId: 'preset-a',
      contractVersion: 'pcv1-1111111111111111',
    }
    const send = vi.fn().mockResolvedValue(stale)

    await expect(askWithPresetContractRetry({
      questionPresetId: 'preset-a',
      contractVersion: 'pcv1-0000000000000000',
    }, send)).resolves.toEqual(stale)

    expect(send).toHaveBeenCalledTimes(2)
  })

  it('does not retry a contract-unavailable response', async () => {
    const unavailable = {
      resolution: 'CAPABILITY_UNAVAILABLE',
      noticeCode: 'PRESET_CONTRACT_UNAVAILABLE',
    }
    const send = vi.fn().mockResolvedValue(unavailable)

    const response = await askWithPresetContractRetry({}, send)

    expect(isPresetContractUnavailable(response)).toBe(true)
    expect(send).toHaveBeenCalledOnce()
  })

  it('never turns a free question into a preset retry', async () => {
    const stale = {
      resolution: 'CAPABILITY_UNAVAILABLE',
      noticeCode: 'PRESET_CONTRACT_STALE',
      questionPresetId: 'preset-a',
      contractVersion: 'pcv1-1111111111111111',
    }
    const send = vi.fn().mockResolvedValue(stale)

    await expect(askWithPresetContractRetry({}, send)).resolves.toEqual(stale)

    expect(send).toHaveBeenCalledOnce()
  })
})
