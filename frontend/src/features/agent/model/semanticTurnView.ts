import type {
  AgentTurnClarificationResponse,
  AgentTurnCompletedTaskResponse,
  AgentTurnDisplayPlanResponse,
  AgentTurnClarificationRequiredResponse,
  AgentTurnConfirmationRequiredResponse,
  AgentTurnPlanChangeResponse,
  AgentTurnPayload,
  AgentTurnReadyResponse,
  AgentTurnResponse,
  AgentTurnResultPayloadResponse,
  AgentTurnTaskSummaryResponse,
  InvalidatedPlanReference,
  AnswerBlock,
  PlanConfirmationSubmission,
  PortfolioRecommendationItem,
  SemanticSourceDomain,
  TaskSummaryDisplayMode,
  TaskSummaryStatus,
  TurnDisposition,
} from './answerTypes'

export interface OpaquePlanConfirmation extends PlanConfirmationSubmission {
  expiresAt: string
}

export interface DisplayPlanTaskView {
  displayIndex: string
  goalLabel: string
  sourceDomain: SemanticSourceDomain
  dependencySummary: string | null
}

export interface DisplayPlanView {
  taskCount: number
  executableTaskCount: number | null
  tasks: DisplayPlanTaskView[]
  constraints: string[]
}

export interface ClarificationOptionView {
  value: string
  label: string
}

export interface ClarificationFieldView {
  fieldKey: string
  inputMode: 'SINGLE_CHOICE'
  options: ClarificationOptionView[]
  required: boolean
  affectedGoalLabels: string[]
}

export interface ClarificationView {
  scope: 'LOCAL' | 'CRITICAL'
  prompt: string
  fields: ClarificationFieldView[]
  blockedTaskCount: number
  continuingTaskCount: number
}

export interface PlanChangeView {
  summary: string
  changeLabels: string[]
  invalidatedPlanReference?: InvalidatedPlanReference
}

export interface TaskSummaryItemView {
  displayIndex: string
  goalLabel: string
  status: TaskSummaryStatus
  sourceDomain: SemanticSourceDomain
}

export interface TaskSummaryView {
  displayMode: TaskSummaryDisplayMode
  totalCount: number
  answeredCount: number
  notSupportedCount: number
  emptyCount: number
  blockedCount: number
  failedCount: number
  cancelledCount: number
  degradedCount: number
  items: TaskSummaryItemView[]
}

export interface CompletedTaskBlockView {
  sourceScope: AnswerBlock['sourceScope']
  sectionType: AnswerBlock['sectionType']
  title: string | null
  content: string
  claimIds: string[]
  evidenceIds: string[]
}

export interface SectionResultView {
  kind: 'SECTION_RESULT'
  blocks: CompletedTaskBlockView[]
}

export interface RecommendationResultView {
  kind: 'RECOMMENDATION_RESULT'
  recommendations: PortfolioRecommendationItem[]
}

export interface SynthesisResultView {
  kind: 'SYNTHESIS_RESULT'
  blocks: CompletedTaskBlockView[]
  originDomains: SemanticSourceDomain[]
}

export type CompletedTaskResultView =
  | SectionResultView
  | RecommendationResultView
  | SynthesisResultView

export interface CompletedTaskView {
  displayIndex: string
  goalLabel: string
  sourceDomain: SemanticSourceDomain
  resultPayload: CompletedTaskResultView
}

export interface SemanticTurnView {
  contractVersion: 'stp-v1'
  disposition: TurnDisposition
  displayPlan?: DisplayPlanView
  clarification?: ClarificationView
  planChange?: PlanChangeView
  planOutcome?: 'SUCCEEDED' | 'PARTIAL' | 'NO_RESULT' | 'FAILED' | 'CANCELLED'
  taskSummary?: TaskSummaryView
  completedTasks: CompletedTaskView[]
}

export function mapSemanticTurnResponse(response: AgentTurnPayload): SemanticTurnView {
  if (!isKnownAgentTurnResponse(response)) {
    return mapUnknownBoundary(response)
  }

  const readyResponse = isReadyResponse(response) ? response : undefined
  const clarificationResponse = hasClarification(response) ? response.clarification : undefined
  return {
    contractVersion: response.contractVersion,
    disposition: response.disposition,
    ...(response.plan === undefined ? {} : { displayPlan: mapDisplayPlan(response.plan) }),
    ...(clarificationResponse === undefined
      ? {}
      : { clarification: mapClarification(clarificationResponse) }),
    ...(response.planChange === undefined ? {} : { planChange: mapPlanChange(response.planChange) }),
    ...(readyResponse?.outcome.planOutcome === undefined
      ? {}
      : { planOutcome: readyResponse.outcome.planOutcome }),
    ...(readyResponse?.outcome.taskSummary === undefined
      ? {}
      : { taskSummary: mapTaskSummary(readyResponse.outcome.taskSummary) }),
    completedTasks: readyResponse?.completedTasks.map(mapCompletedTask) ?? [],
  }
}

export function extractOpaquePlanConfirmation(
  response: AgentTurnPayload | undefined,
): OpaquePlanConfirmation | undefined {
  const confirmation = response !== undefined && isConfirmationResponse(response)
    ? response.planConfirmation
    : undefined
  if (confirmation === undefined) return undefined
  return {
    confirmationId: confirmation.confirmationId,
    confirmationPlan: confirmation.confirmationPlan,
    planFingerprint: confirmation.planFingerprint,
    integrityToken: confirmation.integrityToken,
    expiresAt: confirmation.expiresAt,
  }
}

function isKnownAgentTurnResponse(response: AgentTurnPayload): response is AgentTurnResponse {
  if (response.contractVersion !== 'stp-v1') return false
  if (response.disposition === 'READY' || response.disposition === 'PARTIAL_READY') {
    return isReadyResponse(response)
  }
  if (response.disposition === 'CONFIRMATION_REQUIRED') {
    return isConfirmationResponse(response)
  }
  if (response.disposition === 'CLARIFICATION_REQUIRED') {
    return isClarificationResponse(response)
  }
  return response.disposition === 'BOUNDARY' || response.disposition === 'REJECTED'
}

function isReadyResponse(response: AgentTurnPayload): response is AgentTurnReadyResponse {
  return (response.disposition === 'READY' || response.disposition === 'PARTIAL_READY')
    && typeof response.outcome === 'object'
    && response.outcome !== null
    && Array.isArray(response.completedTasks)
    && (response.clarification === undefined || (
      isClarificationPayload(response.clarification) && response.clarification.scope === 'LOCAL'))
}

function isConfirmationResponse(
  response: AgentTurnPayload,
): response is AgentTurnConfirmationRequiredResponse {
  return response.disposition === 'CONFIRMATION_REQUIRED'
    && typeof response.planConfirmation === 'object'
    && response.planConfirmation !== null
}

function isClarificationResponse(
  response: AgentTurnPayload,
): response is AgentTurnClarificationRequiredResponse {
  return response.disposition === 'CLARIFICATION_REQUIRED'
    && typeof response.clarification === 'object'
    && response.clarification !== null
    && isClarificationPayload(response.clarification)
}

function hasClarification(
  response: AgentTurnResponse,
): response is AgentTurnReadyResponse | AgentTurnClarificationRequiredResponse {
  return response.clarification !== undefined && isClarificationPayload(response.clarification)
}

function isClarificationPayload(value: unknown): value is AgentTurnClarificationResponse {
  if (typeof value !== 'object' || value === null) return false
  const clarification = value as AgentTurnClarificationResponse
  return (clarification.scope === 'LOCAL' || clarification.scope === 'CRITICAL')
    && typeof clarification.prompt === 'string'
    && Array.isArray(clarification.fields)
    && Number.isInteger(clarification.blockedTaskCount)
    && Number.isInteger(clarification.continuingTaskCount)
}

function mapUnknownBoundary(response: AgentTurnPayload): SemanticTurnView {
  const disposition = response.disposition === 'BOUNDARY' ? 'BOUNDARY' : 'REJECTED'
  return {
    contractVersion: 'stp-v1',
    disposition,
    completedTasks: [],
  }
}

function mapDisplayPlan(plan: AgentTurnDisplayPlanResponse): DisplayPlanView {
  return {
    taskCount: plan.taskCount,
    executableTaskCount: plan.executableTaskCount ?? null,
    tasks: plan.tasks.map((task) => ({
      displayIndex: task.displayIndex,
      goalLabel: task.goalLabel,
      sourceDomain: task.sourceDomain,
      dependencySummary: task.dependencySummary ?? null,
    })),
    constraints: [...(plan.constraints ?? [])],
  }
}

function mapClarification(clarification: AgentTurnClarificationResponse): ClarificationView {
  return {
    scope: clarification.scope,
    prompt: clarification.prompt,
    fields: clarification.fields.map((field) => ({
      fieldKey: field.fieldKey,
      inputMode: field.inputMode,
      options: field.options.map((option) => ({
        value: option.value,
        label: option.label,
      })),
      required: field.required,
      affectedGoalLabels: [...field.affectedGoalLabels],
    })),
    blockedTaskCount: clarification.blockedTaskCount,
    continuingTaskCount: clarification.continuingTaskCount,
  }
}

function mapPlanChange(planChange: AgentTurnPlanChangeResponse): PlanChangeView {
  return {
    summary: planChange.summary,
    changeLabels: [...planChange.changeLabels],
    ...(isInvalidatedPlanReference(planChange.invalidatedPlanReference)
      ? { invalidatedPlanReference: { ...planChange.invalidatedPlanReference } }
      : {}),
  }
}

function isInvalidatedPlanReference(value: unknown): value is InvalidatedPlanReference {
  if (typeof value !== 'object' || value === null) return false
  const reference = value as InvalidatedPlanReference
  return typeof reference.planId === 'string'
    && reference.planId.trim().length > 0
    && typeof reference.planFingerprint === 'string'
    && reference.planFingerprint.trim().length > 0
}

function mapTaskSummary(summary: AgentTurnTaskSummaryResponse): TaskSummaryView {
  return {
    displayMode: summary.displayMode,
    totalCount: summary.totalCount,
    answeredCount: summary.answeredCount,
    notSupportedCount: summary.notSupportedCount,
    emptyCount: summary.emptyCount,
    blockedCount: summary.blockedCount,
    failedCount: summary.failedCount,
    cancelledCount: summary.cancelledCount,
    degradedCount: summary.degradedCount,
    items: summary.items.map((item) => ({
      displayIndex: item.displayIndex,
      goalLabel: item.goalLabel,
      status: item.status,
      sourceDomain: item.sourceDomain,
    })),
  }
}

function mapCompletedTask(task: AgentTurnCompletedTaskResponse): CompletedTaskView {
  return {
    displayIndex: task.displayIndex,
    goalLabel: task.goalLabel,
    sourceDomain: task.sourceDomain,
    resultPayload: mapResultPayload(task.resultPayload),
  }
}

function mapResultPayload(payload: AgentTurnResultPayloadResponse): CompletedTaskResultView {
  if (payload.kind === 'RECOMMENDATION_RESULT') {
    return {
      kind: payload.kind,
      recommendations: payload.recommendations.map((recommendation) => ({
        portfolioId: recommendation.portfolioId,
        title: recommendation.title,
        route: recommendation.route,
        matchReasons: [...recommendation.matchReasons],
        evidenceIds: [...recommendation.evidenceIds],
      })),
    }
  }
  if (payload.kind === 'SYNTHESIS_RESULT') {
    return {
      kind: payload.kind,
      blocks: payload.blocks.map(mapCompletedTaskBlock),
      originDomains: [...payload.originDomains],
    }
  }
  return {
    kind: payload.kind,
    blocks: payload.blocks.map(mapCompletedTaskBlock),
  }
}

function mapCompletedTaskBlock(block: AnswerBlock): CompletedTaskBlockView {
  return {
    sourceScope: block.sourceScope,
    sectionType: block.sectionType,
    title: block.title ?? null,
    content: block.content,
    claimIds: [...block.claimIds],
    evidenceIds: [...block.evidenceIds],
  }
}
