import { describe, expect, it, vi } from 'vitest'
import { PortfolioApiError } from '../../portfolio/api/portfolioApi'
import type { AnswerApiRequest } from './answerApi'
import { askWithV3ContractFallback } from './v3ContractFallback'

const base: AnswerApiRequest = {
  turnId: 'turn-a',
  question: '介绍项目',
  audienceRole: 'GUEST',
  source: 'AGENT_PAGE',
  agentTurnContract: 'stp-v3',
}

describe('v3 contract fallback', () => {
  it('retries a fresh read-only ask once as v1 after an explicit unsupported response', async () => {
    const send = vi.fn()
      .mockRejectedValueOnce(new PortfolioApiError(
        'unsupported', 409, 'AGENT_TURN_CONTRACT_UNSUPPORTED'))
      .mockResolvedValueOnce({ responseKind: 'ANSWER' })

    await askWithV3ContractFallback(base, send)

    expect(send).toHaveBeenCalledTimes(2)
    expect(send.mock.calls[1]?.[0]).toEqual(expect.objectContaining({
      agentTurnContract: 'stp-v1',
      requestToken: expect.any(String),
    }))
  })

  it('does not replay a continuation across contracts', async () => {
    const error = new PortfolioApiError(
      'unsupported', 409, 'AGENT_TURN_CONTRACT_UNSUPPORTED')
    const send = vi.fn().mockRejectedValue(error)

    await expect(askWithV3ContractFallback({
      ...base,
      contextReference: { contextHandle: 'opaque', expectedContextType: 'RECOMMENDATION' },
    }, send)).rejects.toBe(error)
    expect(send).toHaveBeenCalledTimes(1)
  })
})
