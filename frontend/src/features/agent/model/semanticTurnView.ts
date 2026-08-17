import type {
  AgentTurnClarificationResponse,
  AgentTurnCompletedTaskResponse,
  AgentTurnDisplayPlanResponse,
  AgentTurnClarificationRequiredResponse,
  AgentTurnConfirmationRequiredResponse,
  AgentTurnPlanChangeResponse,
  AgentTurnPayload,
  AgentTurnReadyResponse,
  AgentTurnRecommendationItemResponse,
  AgentTurnRecommendationResultResponse,
  AgentTurnResponse,
  AgentTurnResultPayloadResponse,
  AgentTurnTaskSummaryResponse,
  AnswerBlockSupport,
  AnswerEvidenceState,
  AnswerSupportKind,
  ContinuationContext,
  ConversationContextType,
  ExecutionDisplayPlanResponse,
  ExecutionDisplayStageResponse,
  ExecutionDisplayTaskResponse,
  ExecutionFinalStatus,
  ExecutionStageCode,
  FulfillmentRole,
  InvalidatedPlanReference,
  OrderedResultItem,
  PendingPlanReference,
  AnswerBlock,
  PlanConfirmationSubmission,
  PortfolioRecommendationItem,
  PublicSourceReference,
  SemanticSourceDomain,
  SemanticSubjectReference,
  StatementSupportReference,
  TaskCompositionMode,
  TaskSupportSummary,
  TaskSummaryDisplayMode,
  TaskSummaryStatus,
  TurnDisposition,
} from './answerTypes'
import { knownEnum, safeEnum } from './enumSafety'
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
  // P5 stp-v2（设计 §10.4）：履约角色，只读。
  fulfillmentRole?: FulfillmentRole
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
  // P5 stp-v2（设计 §9.3）：真实来源域（权威）与逐 Block 支持。
  blockId?: string
  sourceDomain?: SemanticSourceDomain
  support?: AnswerBlockSupport
}

export interface SectionResultView {
  kind: 'SECTION_RESULT'
  blocks: CompletedTaskBlockView[]
}

// P5 stp-v2：推荐项视图在 stp-v1 字段之外可选携带有序结果项身份（§12.12 / handoff §2）。
export interface RecommendationItemView extends PortfolioRecommendationItem {
  resultItemId?: string
  position?: number
  subject?: SemanticSubjectReference
}

export interface RecommendationResultView {
  kind: 'RECOMMENDATION_RESULT'
  recommendations: RecommendationItemView[]
  // 2026-08-17 体验闭环（后端闭环设计 §9）：推荐数量完整性；缺省视为后端未提供。
  requestedSize?: number
  actualSize?: number
  reasonCodes?: string[]
  unsatisfiedConstraints?: string[]
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

// P4：任务级表达状态视图（设计 §11.2 / handoff §2.2）。
// 仅供协议状态与测试使用，不在访客主界面渲染徽标（handoff §3）。
export interface TaskCompositionView {
  mode: TaskCompositionMode
  degraded: boolean
}

export interface CompletedTaskView {
  displayIndex: string
  goalLabel: string
  sourceDomain: SemanticSourceDomain
  resultPayload: CompletedTaskResultView
  // P3：仅产生可续接 Context 的任务返回不透明 handle（handoff §6）。
  contextHandle?: string
  // P4：任务级表达状态。缺省（undefined）表示后端未提供，按兼容处理；不渲染差异。
  composition?: TaskCompositionView
  // P5 stp-v2（设计 §10.4/§9.4/§11.14，handoff §2/§4）：履约角色、支持聚合、续接句柄。
  fulfillmentRole?: FulfillmentRole
  supportSummary?: TaskSupportSummary
  continuationContext?: ContinuationContext
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
  evidenceState?: AnswerEvidenceState
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
  // P5 stp-v2：迁移期同时接受 stp-v1/stp-v2（设计 §17.2）。
  contractVersion: 'stp-v1' | 'stp-v2'
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
  if (response.contractVersion !== 'stp-v1' && response.contractVersion !== 'stp-v2') return false
  // P5 stp-v2：Strict Context 失效优先于通用澄清（设计 §13.9 / handoff §3）。
  if (response.disposition === 'CONTEXT_INVALIDATED') return true
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
    contractVersion: response.contractVersion === 'stp-v2' ? 'stp-v2' : 'stp-v1',
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
      ...mapFulfillmentRole(task.fulfillmentRole),
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
    // P3/P5：不透明 ContextHandle（stp-v1 兼容）与续接句柄（stp-v2 权威）；不一致 fail closed（handoff §4/§6）。
    ...mapTaskContextHandle(task),
    // P4：严格闭集映射 composition；非法 composition 只丢 metadata 并上报脱敏诊断，
    // 不丢失已通过既有契约校验的可信正文与 sourceReferences（handoff §3）。
    ...mapTaskComposition(task.composition),
    // P5：履约角色与支持聚合（设计 §10.4/§9.4）。
    ...mapFulfillmentRole(task.fulfillmentRole),
    ...mapTaskSupportSummary(task.supportSummary),
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
        // P5：有序结果项身份（设计 §12.12 / handoff §2）。
        ...mapOrderedResultItem(recommendation),
      })),
      ...mapRecommendationCompletionFields(payload),
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

// 2026-08-17 体验闭环：推荐数量完整性字段（可选 metadata）。缺省视为未提供（兼容旧响应，
// 不报诊断）；提供但非法（非正整数/非负整数/非字符串数组）时只丢该 metadata 并保留可信推荐。
function mapRecommendationCompletionFields(payload: AgentTurnRecommendationResultResponse): {
  requestedSize?: number
  actualSize?: number
  reasonCodes?: string[]
  unsatisfiedConstraints?: string[]
} {
  const result: {
    requestedSize?: number
    actualSize?: number
    reasonCodes?: string[]
    unsatisfiedConstraints?: string[]
  } = {}
  if (typeof payload.requestedSize === 'number'
    && Number.isInteger(payload.requestedSize) && payload.requestedSize > 0) {
    result.requestedSize = payload.requestedSize
  }
  if (typeof payload.actualSize === 'number'
    && Number.isInteger(payload.actualSize) && payload.actualSize >= 0) {
    result.actualSize = payload.actualSize
  }
  const reasonCodes = p5StringArray(payload.reasonCodes)
  if (reasonCodes.length) result.reasonCodes = reasonCodes
  const unsatisfied = p5StringArray(payload.unsatisfiedConstraints)
  if (unsatisfied.length) result.unsatisfiedConstraints = unsatisfied
  return result
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
    // P5：逐 Block 来源域（权威）与支持明细（设计 §9.3 / handoff §5）。
    ...mapBlockP5Fields(block),
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

// ── P4：任务级 composition 校验（设计 §11.2 / handoff §2.2/§3）──────────────
// composition 是可选 metadata：缺省视为未提供（兼容旧响应，不报诊断）；
// 提供但非法（非对象、mode 不在闭集、degraded 非布尔）时只丢弃该 metadata，
// 保留已通过既有契约校验的可信正文与 sourceReferences，并上报脱敏诊断——
// 诊断不含正文、reference key、Token、mode 字面值或任何 composition 原文。
const TASK_COMPOSITION_MODES = new Set<string>(['DETERMINISTIC', 'MODEL_GROUNDED', 'FALLBACK'])

/** 严格闭集映射 composition；非法返回空对象（不挂 composition 字段）并上报脱敏诊断。 */
function mapTaskComposition(
  value: unknown,
): { composition?: TaskCompositionView } {
  if (value === undefined) return {}
  if (typeof value !== 'object' || value === null) {
    reportInvalidTaskComposition()
    return {}
  }
  const raw = value as Record<string, unknown>
  const mode = typeof raw.mode === 'string' && TASK_COMPOSITION_MODES.has(raw.mode)
    ? (raw.mode as TaskCompositionMode)
    : null
  if (mode === null || (raw.degraded !== true && raw.degraded !== false)) {
    reportInvalidTaskComposition()
    return {}
  }
  return { composition: { mode, degraded: raw.degraded } }
}

function reportInvalidTaskComposition(): void {
  // 仅上报脱敏 code，不携带正文/reference key/Token/mode 字面值。
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'TASK_COMPOSITION_INVALID',
    errorKind: 'INVALID_RESPONSE',
  }))
}

// ── P5 stp-v2：任务/Block 级新字段校验（设计 §9.3/§9.4/§10.4/§11.14/§12.12，handoff §2/§4/§5）──
// 均为可选 metadata：缺省视为未提供（兼容旧响应，不报诊断）；提供但非法时只丢该 metadata，
// 保留已通过既有契约校验的可信正文与 sourceReferences，并上报脱敏诊断。
const SOURCE_DOMAINS = new Set<SemanticSourceDomain>(['GENERAL', 'PORTFOLIO', 'SYNTHESIS'])
const FULFILLMENT_ROLES = new Set<FulfillmentRole>(['PRIMARY', 'SUPPORTING', 'OPTIONAL'])
const SUPPORT_KINDS = new Set<AnswerSupportKind>([
  'VERIFIED_PUBLIC_EVIDENCE', 'GENERAL_KNOWLEDGE', 'DERIVED_FROM_TASKS',
])
const CONTEXT_TYPES = new Set<ConversationContextType>(['RECENT_SEMANTIC_TASK', 'RECOMMENDATION'])

function p5StringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0)
    .map((entry) => entry.trim())
}

function mapFulfillmentRole(value: unknown): { fulfillmentRole?: FulfillmentRole } {
  const role = knownEnum(value, FULFILLMENT_ROLES)
  return role === undefined ? {} : { fulfillmentRole: role }
}

function mapSupportKind(value: unknown): AnswerSupportKind | undefined {
  return knownEnum(value, SUPPORT_KINDS)
}

function mapStatementReference(value: unknown): StatementSupportReference | null {
  if (typeof value !== 'object' || value === null) return null
  const raw = value as Record<string, unknown>
  if (typeof raw.statementId !== 'string' || !raw.statementId.trim()) return null
  const ref: StatementSupportReference = { statementId: raw.statementId.trim() }
  if (typeof raw.sourceTaskId === 'string' && raw.sourceTaskId.trim()) {
    ref.sourceTaskId = raw.sourceTaskId.trim()
  }
  if (Array.isArray(raw.publicSourceKeys)) ref.publicSourceKeys = p5StringArray(raw.publicSourceKeys)
  return ref
}

function mapBlockSupport(value: unknown): AnswerBlockSupport | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const raw = value as Record<string, unknown>
  const kind = mapSupportKind(raw.kind)
  if (kind === undefined) return undefined
  const statementReferences = Array.isArray(raw.statementReferences)
    ? raw.statementReferences
      .map(mapStatementReference)
      .filter((ref): ref is StatementSupportReference => ref !== null)
    : []
  const support: AnswerBlockSupport = {
    kind,
    statementReferences,
    sourceTaskIds: p5StringArray(raw.sourceTaskIds),
    publicSourceKeys: p5StringArray(raw.publicSourceKeys),
  }
  if (typeof raw.contentVersion === 'string' && raw.contentVersion.trim()) {
    support.contentVersion = raw.contentVersion.trim()
  }
  return support
}

// sourceDomain 权威；与旧 sourceScope 不一致（含 SYNTHESIS 携带非空 scope）即 fail closed（handoff §5）。
function mapBlockSourceDomain(block: AnswerBlock): SemanticSourceDomain | undefined {
  const domain = knownEnum(block.sourceDomain, SOURCE_DOMAINS)
  if (domain === undefined) return undefined
  const rawScope = (block as { sourceScope?: AnswerBlock['sourceScope'] | null }).sourceScope
  if (domain === 'SYNTHESIS') {
    if (rawScope !== undefined && rawScope !== null) {
      reportInvalidBlockSourceDomain()
      return undefined
    }
  } else if (rawScope !== undefined && rawScope !== null && rawScope !== domain) {
    reportInvalidBlockSourceDomain()
    return undefined
  }
  return domain
}

function mapBlockP5Fields(block: AnswerBlock): {
  blockId?: string
  sourceDomain?: SemanticSourceDomain
  support?: AnswerBlockSupport
} {
  const result: {
    blockId?: string
    sourceDomain?: SemanticSourceDomain
    support?: AnswerBlockSupport
  } = {}
  if (typeof block.blockId === 'string' && block.blockId.trim()) result.blockId = block.blockId.trim()
  const sourceDomain = mapBlockSourceDomain(block)
  if (sourceDomain !== undefined) result.sourceDomain = sourceDomain
  const support = mapBlockSupport(block.support)
  if (support !== undefined) result.support = support
  return result
}

function mapTaskSupportSummary(value: unknown): { supportSummary?: TaskSupportSummary } {
  if (value === undefined) return {}
  if (typeof value !== 'object' || value === null) {
    reportInvalidSupportSummary()
    return {}
  }
  const raw = value as Record<string, unknown>
  const kind = mapSupportKind(raw.kind)
  if (kind === undefined
    || !Number.isInteger(raw.statementCount) || (raw.statementCount as number) < 0
    || !Number.isInteger(raw.publicSourceCount) || (raw.publicSourceCount as number) < 0) {
    reportInvalidSupportSummary()
    return {}
  }
  const summary: TaskSupportSummary = {
    kind,
    statementCount: raw.statementCount as number,
    publicSourceCount: raw.publicSourceCount as number,
  }
  if (typeof raw.sourceTaskCount === 'number') summary.sourceTaskCount = raw.sourceTaskCount
  if (typeof raw.contentVersion === 'string' && raw.contentVersion.trim()) {
    summary.contentVersion = raw.contentVersion.trim()
  }
  return { supportSummary: summary }
}

function mapContinuationContext(value: unknown): ContinuationContext | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const raw = value as Record<string, unknown>
  const contextHandle = typeof raw.contextHandle === 'string' ? raw.contextHandle.trim() : ''
  const contextType = knownEnum(raw.contextType, CONTEXT_TYPES)
  const sourceTaskId = typeof raw.sourceTaskId === 'string' ? raw.sourceTaskId.trim() : ''
  if (!contextHandle || contextType === undefined || !sourceTaskId) return undefined
  return { contextHandle, contextType, sourceTaskId }
}

// 新旧 context handle 同时存在必须一致，否则 fail closed：丢弃 stp-v2 续接能力，保留可信正文（handoff §4）。
function mapTaskContextHandle(task: AgentTurnCompletedTaskResponse): {
  contextHandle?: string
  continuationContext?: ContinuationContext
} {
  const handle = typeof task.contextHandle === 'string' && task.contextHandle.trim()
    ? task.contextHandle.trim() : undefined
  const continuation = mapContinuationContext(task.continuationContext)
  if (handle !== undefined && continuation !== undefined && handle !== continuation.contextHandle) {
    reportInvalidContextHandle()
    return handle !== undefined ? { contextHandle: handle } : {}
  }
  const result: { contextHandle?: string; continuationContext?: ContinuationContext } = {}
  if (handle !== undefined) result.contextHandle = handle
  if (continuation !== undefined) result.continuationContext = continuation
  return result
}

// 有序结果项身份：以 resultItemId 为键，附带 position/subject（设计 §12.12 / handoff §2）。
function mapOrderedResultItem(item: AgentTurnRecommendationItemResponse): {
  resultItemId?: string
  position?: number
  subject?: SemanticSubjectReference
} {
  if (typeof item.resultItemId !== 'string' || !item.resultItemId.trim()) return {}
  const result: {
    resultItemId?: string
    position?: number
    subject?: SemanticSubjectReference
  } = { resultItemId: item.resultItemId.trim() }
  if (Number.isInteger(item.position) && (item.position as number) > 0) result.position = item.position
  if (item.subject !== undefined && typeof item.subject === 'object' && item.subject !== null) {
    const s = item.subject as { subjectType?: unknown; subjectId?: unknown }
    if (typeof s.subjectType === 'string' && s.subjectType.trim()
      && typeof s.subjectId === 'string' && s.subjectId.trim()) {
      result.subject = { subjectType: s.subjectType.trim(), subjectId: s.subjectId.trim() }
    }
  }
  return result
}

function reportInvalidBlockSourceDomain(): void {
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'BLOCK_SOURCE_DOMAIN_INVALID',
    errorKind: 'INVALID_RESPONSE',
  }))
}

function reportInvalidSupportSummary(): void {
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'TASK_SUPPORT_SUMMARY_INVALID',
    errorKind: 'INVALID_RESPONSE',
  }))
}

function reportInvalidContextHandle(): void {
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'CONTINUATION_CONTEXT_INVALID',
    errorKind: 'INVALID_RESPONSE',
  }))
}
