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
  ExecutionDisplayPlanResponse,
  ExecutionDisplayStageResponse,
  ExecutionDisplayTaskResponse,
  ExecutionFinalStatus,
  ExecutionStageCode,
  InvalidatedPlanReference,
  PendingPlanReference,
  AnswerBlock,
  PlanConfirmationSubmission,
  PortfolioRecommendationItem,
  PublicSourceReference,
  SemanticSourceDomain,
  SemanticSubjectReference,
  TaskSummaryDisplayMode,
  TaskSummaryStatus,
  TurnDisposition,
} from './answerTypes'
import { createFrontendDiagnosticEvent } from '../../../shared/diagnostics/frontendDiagnosticTypes'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

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
  // TRANSITIONAL(p3-e): 旧 P2 引用，P3 删除。
  claimIds: string[]
  evidenceIds: string[]
  // P3：公开来源引用（handoff §8）。
  sourceReferences?: PublicSourceReference[]
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
  // P3：仅产生可续接 Context 的任务返回不透明 handle（handoff §6）。
  contextHandle?: string
}

// ── P3 执行快照视图（FINAL，handoff §7）────────────────────────────────────
export interface ExecutionStageView {
  code: ExecutionStageCode
  label: string
  status: ExecutionFinalStatus
}

export interface ExecutionTaskView {
  displayIndex: string
  finalStatus: ExecutionFinalStatus
  stages: ExecutionStageView[]
}

export interface ExecutionDisplayPlanView {
  overallStatus: ExecutionFinalStatus
  tasks: ExecutionTaskView[]
}

/**
 * P3 防御性检查（不是契约的一部分，仅展示层兜底）：
 * 顶层宣称 ANSWERED 且 evidenceState=VERIFIED，但服务端 FINAL 执行快照全部任务的全部阶段
 * 均为 FAILED 时，意味着后端在某处出现状态矛盾。前端不能把 FAILED 阶段伪造成成功，也
 * 不能把答案静默包装为完整成功；应以非阻断提示本轮执行能力降级，并以服务端快照为准。
 *
 * 真正语义修复在后端；这里只是防御性展示（handoff §7/§9）。
 */
export function hasExecutionAnswerConflict(answer: {
  resolution: 'ANSWERED' | 'PARTIALLY_ANSWERED' | string
  evidenceState?: 'VERIFIED' | 'NOT_REQUIRED' | 'INSUFFICIENT'
  semanticTurn?: { execution?: ExecutionDisplayPlanView }
}): boolean {
  const execution = answer.semanticTurn?.execution
  if (!execution) return false
  if (answer.resolution !== 'ANSWERED') return false
  if (answer.evidenceState !== 'VERIFIED') return false
  if (execution.overallStatus !== 'FAILED') return false
  const stages = execution.tasks.flatMap((task) => task.stages)
  if (stages.length === 0) return false
  return stages.every((stage) => stage.status === 'FAILED')
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
  // P3：与 plan 同级的最终执行快照（handoff §7）。非法 FINAL（含运行中状态）被丢弃。
  execution?: ExecutionDisplayPlanView
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
    ...(response.execution === undefined ? {} : { execution: mapExecution(response.execution) }),
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
    // P3：透传不透明 ContextHandle，前端不得生成/解析/修改（handoff §6）。
    ...(task.contextHandle === undefined || typeof task.contextHandle !== 'string'
      ? {}
      : { contextHandle: task.contextHandle }),
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
        evidenceIds: [...(recommendation.evidenceIds ?? [])],
        ...mapSourceReferencesField(recommendation.sourceReferences),
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
    claimIds: [...(block.claimIds ?? [])],
    evidenceIds: [...(block.evidenceIds ?? [])],
    ...mapSourceReferencesField(block.sourceReferences),
  }
}

// ── P3：公开来源引用校验（handoff §8）────────────────────────────────────────
// 未知 sourceType、缺关键字段或非法路由的引用被丢弃并上报脱敏诊断；
// 不把旧 claimIds/evidenceIds 合成为 PublicSourceReference。
const PUBLIC_SOURCE_TYPES = new Set<string>([
  'COLLECTION', 'DOCUMENT', 'SCREENSHOT', 'CODE', 'TEST_RESULT',
])
// 站内相对公开路由：以单个 / 开头且第二字符不为 /，允许路径段与查询串
// （如 /evidence?evidence=...），但禁止协议、对象存储地址与协议相对 URL。
const RELATIVE_ROUTE_PATTERN = /^\/[^/][A-Za-z0-9._\-/?&=%~]*$/

function isRelativeSiteRoute(value: string): boolean {
  return RELATIVE_ROUTE_PATTERN.test(value) && !value.includes('://')
}

/** 校验并投影公开来源引用数组；非法引用被丢弃。供 mapAnswerResponse 复用。 */
export function mapSourceReferencesField(
  value: PublicSourceReference[] | undefined,
): { sourceReferences?: PublicSourceReference[] } {
  if (!Array.isArray(value) || value.length === 0) return {}
  const valid = value
    .map((entry) => mapPublicSourceReference(entry))
    .filter((entry): entry is PublicSourceReference => entry !== null)
  if (valid.length === 0) return {}
  return { sourceReferences: valid }
}

function mapPublicSourceReference(value: unknown): PublicSourceReference | null {
  if (typeof value !== 'object' || value === null) return null
  const entry = value as Record<string, unknown>
  const referenceKey = typeof entry.referenceKey === 'string' ? entry.referenceKey.trim() : ''
  const label = typeof entry.label === 'string' ? entry.label.trim() : ''
  const sourceType = typeof entry.sourceType === 'string' ? entry.sourceType : ''
  const subjectRoute = typeof entry.subjectRoute === 'string' ? entry.subjectRoute.trim() : ''
  const publishedVersion = typeof entry.publishedVersion === 'string'
    ? entry.publishedVersion.trim() : ''
  if (!referenceKey || !label || !subjectRoute || !publishedVersion) return null
  if (!PUBLIC_SOURCE_TYPES.has(sourceType)) return null
  if (!isRelativeSiteRoute(subjectRoute)) return null
  const evidenceRoute = typeof entry.evidenceRoute === 'string'
    ? entry.evidenceRoute.trim() : ''
  const reference: PublicSourceReference = {
    referenceKey,
    label,
    sourceType: sourceType as PublicSourceReference['sourceType'],
    subjectRoute,
    publishedVersion,
  }
  if (evidenceRoute && isRelativeSiteRoute(evidenceRoute)) {
    reference.evidenceRoute = evidenceRoute
  }
  return reference
}

// ── P3：执行快照（FINAL）校验与投影（handoff §7）─────────────────────────────
const EXECUTION_FINAL_STATUSES = new Set<string>(['COMPLETED', 'PARTIAL', 'SKIPPED', 'FAILED'])
const EXECUTION_STAGE_CODES = new Set<string>([
  'SCOPE_CONFIRMED', 'MATERIALS_RETRIEVED', 'EVIDENCE_VALIDATED', 'RESULT_COMPOSED',
])

function mapExecution(value: ExecutionDisplayPlanResponse | undefined): ExecutionDisplayPlanView | undefined {
  if (value === undefined || typeof value !== 'object') return undefined
  const plan = value as ExecutionDisplayPlanResponse
  if (plan.contractVersion !== 'p3-display-v1' || plan.snapshotType !== 'FINAL') {
    reportInvalidExecution()
    return undefined
  }
  if (!EXECUTION_FINAL_STATUSES.has(String(plan.overallStatus))) {
    reportInvalidExecution()
    return undefined
  }
  if (!Array.isArray(plan.tasks)) {
    reportInvalidExecution()
    return undefined
  }
  const tasks = plan.tasks.map(mapExecutionTask).filter((task): task is ExecutionTaskView => task !== null)
  if (tasks.length !== plan.tasks.length) {
    // 任一 task/stage 非法（含运行中状态）→ 整个快照丢弃（handoff §7）。
    reportInvalidExecution()
    return undefined
  }
  return {
    overallStatus: plan.overallStatus,
    tasks,
  }
}

function mapExecutionTask(task: ExecutionDisplayTaskResponse): ExecutionTaskView | null {
  if (typeof task !== 'object' || task === null) return null
  if (!EXECUTION_FINAL_STATUSES.has(String(task.finalStatus))) return null
  if (!Array.isArray(task.stages) || task.stages.length === 0) return null
  const stages = task.stages.map(mapExecutionStage).filter((s): s is ExecutionStageView => s !== null)
  if (stages.length !== task.stages.length) return null
  return {
    displayIndex: typeof task.displayIndex === 'string' ? task.displayIndex : '',
    finalStatus: task.finalStatus,
    stages,
  }
}

function mapExecutionStage(stage: ExecutionDisplayStageResponse): ExecutionStageView | null {
  if (typeof stage !== 'object' || stage === null) return null
  if (!EXECUTION_STAGE_CODES.has(String(stage.code))) return null
  if (!EXECUTION_FINAL_STATUSES.has(String(stage.status))) return null
  // label 是后端安全文案；前端直接展示，但不信任超长文本。
  const label = typeof stage.label === 'string' ? stage.label.trim().slice(0, 60) : ''
  if (!label) return null
  return {
    code: stage.code,
    label,
    status: stage.status,
  }
}

function reportInvalidExecution(): void {
  // 仅上报脱敏 code，不携带正文/handle/version/异常。
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'EXECUTION_SNAPSHOT_INVALID',
    errorKind: 'INVALID_RESPONSE',
  }))
}
