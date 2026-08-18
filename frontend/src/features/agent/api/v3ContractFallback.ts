import { PortfolioApiError } from '../../portfolio/api/portfolioApi'
import type { P3AnswerSuccess } from '../model/answerTypes'
import type { AnswerApiRequest } from './answerApi'
import { createRequestToken } from './createRequestToken'

/** One-shot v3 to v1 fallback for a fresh, read-only ASK only. */
export async function askWithV3ContractFallback(
  input: AnswerApiRequest,
  send: (request: AnswerApiRequest) => Promise<P3AnswerSuccess>,
): Promise<P3AnswerSuccess> {
  try {
    return await send(input)
  } catch (error) {
    if (!isEligible(input) || !(error instanceof PortfolioApiError)
      || error.code !== 'AGENT_TURN_CONTRACT_UNSUPPORTED') {
      throw error
    }
    return send({ ...input, agentTurnContract: 'stp-v1', requestToken: createRequestToken() })
  }
}

function isEligible(input: AnswerApiRequest): boolean {
  return input.agentTurnContract === 'stp-v3'
    && (input.action === undefined || input.action === 'ASK')
    && input.planConfirmation === undefined
    && input.planAdjustment === undefined
    && input.clarificationResolution === undefined
    && input.invalidatedPlanReference === undefined
    && input.contextReference === undefined
}
