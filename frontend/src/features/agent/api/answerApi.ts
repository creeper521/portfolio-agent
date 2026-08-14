import { RequestOperation, request } from '../../portfolio/api/portfolioApi'
import type {
  AnswerResponse,
  ClarificationResolutionRequest,
  ContextReferenceRequest,
  ConversationContextSummaryResponse,
  InvalidatedPlanReference,
  P3AnswerSuccess,
  PlanAdjustmentRequest,
  PlanConfirmationSubmission,
  PortfolioReferenceContext,
  SemanticContextRequest,
  SemanticTurnContract,
  TurnAction,
  ConversationTopic,
  PortfolioRecommendationContextRequest,
} from '../model/answerTypes'
import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import { createRequestToken } from './createRequestToken'

export interface AnswerApiRequest {
  turnId: string
  action?: TurnAction
  // P5 stp-v2：调用方显式声明；生产工作区默认请求 stp-v2，409 仅允许用户主动以 stp-v1 重试。
  agentTurnContract?: SemanticTurnContract
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
  // TRANSITIONAL(p3-e): 旧 P2 完整 Context 回传，P3 最终改用 contextReference。
  referenceContext?: PortfolioReferenceContext
  recommendationContext?: PortfolioRecommendationContextRequest
  // P3：会话级不透明 ResumeToken（handoff §3.1, §10）。仅通过 Header 携带。
  resumeToken?: string
  // P3：从结果继续时发送的强类型 Context 引用（handoff §3.2）。
  contextReference?: ContextReferenceRequest
}

export interface AnswerRequestOptions {
  signal?: AbortSignal
}

export function askQuestion(
  input: AnswerApiRequest,
  options: AnswerRequestOptions = {},
): Promise<P3AnswerSuccess> {
  return request<P3AnswerSuccess>('/api/v2/answers', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // P3：已有会话才发送 ResumeToken；首问不发送（handoff §3.1）。
      ...(input.resumeToken === undefined ? {} : { 'X-Conversation-Resume-Token': input.resumeToken }),
    },
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
      // P3：contextReference 是顶层强类型字段，不放入 context（handoff §3.2）。
      ...(input.contextReference === undefined
        ? {}
        : {
            contextReference: {
              contextHandle: input.contextReference.contextHandle,
              expectedContextType: input.contextReference.expectedContextType,
              // P5 stp-v2（设计 §12.12 / handoff §2）：显式结果项选择；缺省不写入。
              ...(input.contextReference.resultItemId === undefined
                ? {}
                : { resultItemId: input.contextReference.resultItemId }),
            },
          }),
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

// ── P3：会话业务上下文 API（handoff §11 刷新恢复 / §12 主动清除）──
//
// ResumeToken 只通过 X-Conversation-Resume-Token Header 携带，绝不进入 URL/body/Cookie。
// request() 的诊断管道只写 X-Client-Session-Id/X-Client-Request-Id（诊断用 UUID，与会话 Token
// 无关），且诊断事件为固定白名单 schema，结构上无法携带 Token。

/** GET /api/v2/conversation-context：用 ResumeToken 取安全 Context Summary（刷新恢复）。 */
export function fetchConversationContext(
  resumeToken: string,
  options: AnswerRequestOptions = {},
): Promise<ConversationContextSummaryResponse> {
  return request<ConversationContextSummaryResponse>('/api/v2/conversation-context', {
    method: 'GET',
    headers: { 'X-Conversation-Resume-Token': resumeToken },
    signal: options.signal,
  }, {
    operation: RequestOperation.ANSWER,
    signal: options.signal,
    timeoutMs: 10_000,
  })
}

/** DELETE /api/v2/conversation-context：幂等清除当前 Token 的服务端 Context（204 即成功）。 */
export function clearConversationContext(
  resumeToken: string,
  options: AnswerRequestOptions = {},
): Promise<void> {
  return request<void>('/api/v2/conversation-context', {
    method: 'DELETE',
    headers: { 'X-Conversation-Resume-Token': resumeToken },
    signal: options.signal,
  }, {
    operation: RequestOperation.ANSWER,
    signal: options.signal,
    timeoutMs: 10_000,
    expectNoContent: true,
  })
}
