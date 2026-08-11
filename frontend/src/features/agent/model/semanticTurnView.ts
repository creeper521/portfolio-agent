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
  PendingPlanReference,
  AnswerBlock,
  PlanConfirmationSubmission,
  PortfolioRecommendationItem,
  SemanticSourceDomain,
  SemanticSubjectReference,
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
  summaryLabel: string | null
  pendingPlanReference: PendingPlanReference | null
  tasks: DisplayPlanTaskView[]
  constraints: string[]
}

export type ClarificationInputModeView =
  | 'SINGLE_CHOICE'
  | 'MULTI_CHOICE'
  | 'SHORT_TEXT'
  | 'UNSUPPORTED'

export interface ClarificationOptionView {
  value: string
  label: string
  subjectReference: SemanticSubjectReference | null
}

export interface ClarificationFieldView {
  fieldKey: string
  inputMode: ClarificationInputModeView
  options: ClarificationOptionView[]
  required: boolean
  affectedGoalLabels: string[]
}

export interface ClarificationBlockedGoalView {
  goalLabel: string
  reasonCode: string
}

/** 澄清提交负载：组件层产出，由 Workspace 转换为 clarificationResolution 请求。 */
export type ClarificationSubmission =
  | { kind: 'CHOICE'; fieldKey: string; option: ClarificationOptionView }
  | { kind: 'MULTI_CHOICE'; fieldKey: string; options: ClarificationOptionView[] }
  | { kind: 'TEXT'; fieldKey: string; text: string }

/** 调整模式状态（由 Workspace 持有）：仅含展示所需信息，不含任何协议字段。 */
export interface PlanAdjustmentBarState {
  planTitle: string
}

export interface ClarificationView {
  clarificationId: string | null
  scope: 'LOCAL' | 'CRITICAL'
  promptCode: string | null
  prompt: string
  fields: ClarificationFieldView[]
  blockedTaskCount: number
  continuingTaskCount: number
  continuingGoalLabels: string[]
  blockedGoals: ClarificationBlockedGoalView[]
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
  reasonCodes: string[]
  blockedByDisplayIndexes: string[]
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
  // 待确认计划引用由 planConfirmation 承载（b816bfa 起为必选字段）；
  // 缺失时视图模型为 null，调整提交按此门控。
  const confirmationReference = isConfirmationResponse(response)
    ? mapPendingPlanReference(response.planConfirmation.pendingPlanReference)
    : null
  return {
    contractVersion: response.contractVersion,
    disposition: response.disposition,
    ...(response.plan === undefined
      ? {}
      : { displayPlan: mapDisplayPlan(response.plan, confirmationReference) }),
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

function mapDisplayPlan(
  plan: AgentTurnDisplayPlanResponse,
  confirmationReference?: PendingPlanReference | null,
): DisplayPlanView {
  return {
    taskCount: plan.taskCount,
    executableTaskCount: plan.executableTaskCount ?? null,
    summaryLabel: mapSummaryLabel(plan.summaryLabel),
    pendingPlanReference: confirmationReference ?? null,
    tasks: plan.tasks.map((task) => ({
      displayIndex: task.displayIndex,
      goalLabel: task.goalLabel,
      sourceDomain: task.sourceDomain,
      dependencySummary: task.dependencySummary ?? null,
    })),
    constraints: [...(plan.constraints ?? [])],
  }
}

// displayPlan.summaryLabel 是后端确定性概括。前端不信任无限长文本，
// 超长按缺失处理，避免标题被撑爆（FE-U05 fallback）。
const SUMMARY_LABEL_MAX_LENGTH = 40

function mapSummaryLabel(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  if (!normalized || normalized.length > SUMMARY_LABEL_MAX_LENGTH) return null
  return normalized
}

function mapPendingPlanReference(value: unknown): PendingPlanReference | null {
  if (typeof value !== 'object' || value === null) return null
  const reference = value as PendingPlanReference
  if (typeof reference.planId !== 'string' || !reference.planId.trim()) return null
  if (typeof reference.planFingerprint !== 'string' || !reference.planFingerprint.trim()) return null
  return {
    planId: reference.planId.trim(),
    planFingerprint: reference.planFingerprint.trim(),
  }
}

const PUBLIC_CODE_PATTERN = /^[A-Z]+_[A-Z0-9_]+$/
const CLARIFICATION_ID_PATTERN = /^clarify-[a-f0-9]{32}$/
const CONTROLLED_SUBJECT_TYPES = new Set(['PROJECT', 'CASE', 'RESULT'])

function mapPublicCode(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  return PUBLIC_CODE_PATTERN.test(normalized) ? normalized : null
}

function mapClarification(clarification: AgentTurnClarificationResponse): ClarificationView {
  return {
    clarificationId: typeof clarification.clarificationId === 'string'
      && CLARIFICATION_ID_PATTERN.test(clarification.clarificationId)
      ? clarification.clarificationId
      : null,
    scope: clarification.scope,
    promptCode: mapPublicCode(clarification.promptCode),
    prompt: clarification.prompt,
    fields: clarification.fields.map((field) => ({
      fieldKey: field.fieldKey,
      inputMode: mapInputMode(field.inputMode),
      options: field.options.map((option) => ({
        value: option.value,
        label: option.label,
        subjectReference: mapOptionSubjectReference(option.resolution),
      })),
      required: field.required,
      affectedGoalLabels: [...field.affectedGoalLabels],
    })),
    blockedTaskCount: clarification.blockedTaskCount,
    continuingTaskCount: clarification.continuingTaskCount,
    continuingGoalLabels: (clarification.continuingGoalLabels ?? [])
      .filter((label): label is string => typeof label === 'string' && label.trim().length > 0),
    blockedGoals: (clarification.blockedGoals ?? [])
      .map((goal) => ({
        goalLabel: typeof goal?.goalLabel === 'string' ? goal.goalLabel : '',
        reasonCode: mapPublicCode(goal?.reasonCode) ?? '',
      }))
      .filter((goal) => goal.goalLabel.trim().length > 0),
  }
}

function mapInputMode(value: unknown): ClarificationInputModeView {
  return value === 'SINGLE_CHOICE' || value === 'MULTI_CHOICE' || value === 'SHORT_TEXT'
    ? value
    : 'UNSUPPORTED'
}

// 只接受后端受控 SUBJECT_REFERENCE 且主体类型属于公开闭集；
// 其余一律视为无引用，前端不根据 fieldKey 或文案猜测类型（FE-F03）。
function mapOptionSubjectReference(value: unknown): SemanticSubjectReference | null {
  if (typeof value !== 'object' || value === null) return null
  const resolution = value as { kind?: unknown; subjectType?: unknown; subjectId?: unknown }
  if (resolution.kind !== 'SUBJECT_REFERENCE') return null
  if (typeof resolution.subjectType !== 'string' || typeof resolution.subjectId !== 'string') return null
  const subjectType = resolution.subjectType.trim()
  const subjectId = resolution.subjectId.trim()
  if (!CONTROLLED_SUBJECT_TYPES.has(subjectType) || !subjectId) return null
  return { subjectType, subjectId }
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
      reasonCodes: (item.reasonCodes ?? [])
        .map(mapPublicCode)
        .filter((code): code is string => code !== null),
      blockedByDisplayIndexes: (item.blockedByDisplayIndexes ?? [])
        .filter((index): index is string => typeof index === 'string' && index.trim().length > 0),
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
