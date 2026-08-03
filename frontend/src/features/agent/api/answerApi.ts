import { RequestOperation, request } from '../../portfolio/api/portfolioApi'
import type {
  AnswerResponse,
  PortfolioReferenceContext,
  ConversationTopic,
  PortfolioRecommendationContextRequest,
} from '../model/answerTypes'
import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import { createRequestToken } from './createRequestToken'

export interface AnswerApiRequest {
  turnId: string
  requestToken?: string
  signal?: AbortSignal
  projectSlug?: string | null
  caseSlug?: string | null
  audienceRole: AudienceRole
  source: 'HOME' | 'AGENT_PAGE' | 'PROJECT' | 'CASE' | 'EVIDENCE'
  focusEvidenceIds?: string[]
  questionPresetId?: string
  question?: string
  messages?: { role: 'USER' | 'ASSISTANT'; content: string }[]
  coveredTopics?: readonly ConversationTopic[]
  referenceContext?: PortfolioReferenceContext
  recommendationContext?: PortfolioRecommendationContextRequest
}

export interface AnswerRequestOptions {
  signal?: AbortSignal
}

export function askQuestion(
  input: AnswerApiRequest,
  options: AnswerRequestOptions = {},
): Promise<AnswerResponse> {
  return request<AnswerResponse>('/api/v2/answers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      turnId: input.turnId,
      requestToken: input.requestToken ?? createRequestToken(),
      ...(input.questionPresetId === undefined
        ? {}
        : { questionPresetId: input.questionPresetId }),
      question: input.question,
      messages: input.messages?.map((message) => ({
        role: message.role,
        content: message.content,
      })),
      context: {
        projectSlug: input.projectSlug ?? null,
        caseSlug: input.caseSlug ?? null,
        audienceRole: input.audienceRole,
        source: input.source,
        coveredTopics: [...new Set(input.coveredTopics ?? [])],
        ...(input.referenceContext === undefined
          ? {}
          : {
              referenceContext: {
                previousContentVersion: input.referenceContext.previousContentVersion,
                projectSlugs: [...(input.referenceContext.projectSlugs ?? [])],
                caseSlugs: [...(input.referenceContext.caseSlugs ?? [])],
                questionPresetId: input.referenceContext.questionPresetId,
                referencedClaimIds: [...input.referenceContext.referencedClaimIds],
                selectedSectionType: input.referenceContext.selectedSectionType,
                followUpAction: input.referenceContext.followUpAction,
              },
            }),
        ...(input.recommendationContext === undefined
          ? {}
          : {
              recommendationContext: {
                ...input.recommendationContext,
                capabilityCodes: [...input.recommendationContext.capabilityCodes],
                selectedPortfolioIds: [...input.recommendationContext.selectedPortfolioIds],
              },
            }),
      },
    }),
  }, {
    operation: RequestOperation.ANSWER,
    signal: options.signal ?? input.signal,
    timeoutMs: 15_000,
  })
}
