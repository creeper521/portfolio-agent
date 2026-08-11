import { RequestOperation, request } from '../../portfolio/api/portfolioApi'
import type {
  AnswerResponse,
  ClarificationResolutionRequest,
  InvalidatedPlanReference,
  PlanAdjustmentRequest,
  PlanConfirmationSubmission,
  PortfolioReferenceContext,
  SemanticContextRequest,
  TurnAction,
  ConversationTopic,
  PortfolioRecommendationContextRequest,
} from '../model/answerTypes'
import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import { createRequestToken } from './createRequestToken'

export interface AnswerApiRequest {
  turnId: string
  action?: TurnAction
  agentTurnContract?: 'stp-v1'
  planConfirmation?: PlanConfirmationSubmission
  semanticContext?: SemanticContextRequest
  invalidatedPlanReference?: InvalidatedPlanReference
  planAdjustment?: PlanAdjustmentRequest
  clarificationResolution?: ClarificationResolutionRequest
  requestToken?: string
  signal?: AbortSignal
  projectSlug?: string | null
  caseSlug?: string | null
  audienceRole: AudienceRole
  source: 'HOME' | 'AGENT_PAGE' | 'PROJECT' | 'CASE' | 'EVIDENCE'
  focusEvidenceIds?: string[]
  questionPresetId?: string
  contractVersion?: string
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
      ...(input.action === undefined ? {} : { action: input.action }),
      ...(input.agentTurnContract === undefined
        ? {}
        : { agentTurnContract: input.agentTurnContract }),
      ...(input.action !== 'CONFIRM_PLAN' || input.planConfirmation === undefined
        ? {}
        : {
            planConfirmation: {
              confirmationId: input.planConfirmation.confirmationId,
              confirmationPlan: input.planConfirmation.confirmationPlan,
              planFingerprint: input.planConfirmation.planFingerprint,
              integrityToken: input.planConfirmation.integrityToken,
            },
          }),
      ...(input.questionPresetId === undefined
        ? {}
        : { questionPresetId: input.questionPresetId }),
      ...(input.contractVersion === undefined
        ? {}
        : { contractVersion: input.contractVersion }),
      ...(input.question === undefined ? {} : { question: input.question }),
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
      ...(input.semanticContext === undefined
        ? {}
        : {
            semanticContext: {
              ...input.semanticContext,
              activeSubjects: input.semanticContext.activeSubjects === undefined
                ? undefined
                : input.semanticContext.activeSubjects.map((subject) => ({ ...subject })),
              resultReferences: input.semanticContext.resultReferences === undefined
                ? undefined
                : input.semanticContext.resultReferences.map((reference) => ({ ...reference })),
              coveredTopics: input.semanticContext.coveredTopics === undefined
                ? undefined
                : [...input.semanticContext.coveredTopics],
            },
          }),
      ...(input.invalidatedPlanReference === undefined
        ? {}
        : { invalidatedPlanReference: { ...input.invalidatedPlanReference } }),
      ...(input.planAdjustment === undefined
        ? {}
        : {
            planAdjustment: {
              instruction: input.planAdjustment.instruction,
              pendingPlanReference: { ...input.planAdjustment.pendingPlanReference },
            },
          }),
      ...(input.clarificationResolution === undefined
        ? {}
        : {
            clarificationResolution: {
              clarificationId: input.clarificationResolution.clarificationId,
              promptCode: input.clarificationResolution.promptCode,
              fieldKey: input.clarificationResolution.fieldKey,
              ...(input.clarificationResolution.selectedOption === undefined
                ? {}
                : {
                    selectedOption: {
                      value: input.clarificationResolution.selectedOption.value,
                      ...(input.clarificationResolution.selectedOption.subjectReference === undefined
                        ? {}
                        : {
                            subjectReference: {
                              ...input.clarificationResolution.selectedOption.subjectReference,
                            },
                          }),
                    },
                  }),
              ...(input.clarificationResolution.textValue === undefined
                ? {}
                : { textValue: input.clarificationResolution.textValue }),
            },
          }),
    }),
  }, {
    operation: RequestOperation.ANSWER,
    signal: options.signal ?? input.signal,
    timeoutMs: 15_000,
  })
}
