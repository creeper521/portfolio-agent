import type {
  AnswerResponse,
  AnswerSectionView,
  MappedAnswer,
  AnswerBlock,
  AnswerBlockSupport,
  CompletionReceiptResponse,
  ContextInvalidation,
  ContextInvalidationRecoveryAction,
  ContextResolution,
  ContextResolutionMode,
  ConversationContextSummary,
  ConversationContextType,
  ConversationContinuationStatus,
  LegacyAnswerSection,
  MappedAnswerSuccess,
  MappedCompletionReceipt,
  MappedConversation,
  P3AnswerSuccess,
  PublicAnswerCaveat,
  PublicDegradationKind,
  PublicDegradationSummary,
  PublicSourceCatalogEntry,
  PublicSourceReference,
  PublicSourceType,
  RecentTaskType,
  SourceComposition,
} from './answerTypes'
import { resolveAnswerSuccess } from './answerTypes'
import { knownEnum } from './enumSafety'
import {
  mapSemanticTurnResponse,
  mapSourceReferencesField,
  type CompletedTaskBlockView,
  type SemanticTurnView,
} from './semanticTurnView'
import type {
  PortfolioRecommendation,
  PortfolioRecommendationContext,
  PortfolioRecommendationItem,
} from './answerTypes'
import { createFrontendDiagnosticEvent } from '../../../shared/diagnostics/frontendDiagnosticTypes'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

const INVALID_TYPED_BLOCK_ERROR = 'Answer response contains an invalid typed block'

export function mapAnswerResponse(response: AnswerResponse): MappedAnswer {
  let semanticTurn = response.agentTurn === undefined
    ? undefined
    : mapSemanticTurnResponse(response.agentTurn)
  // ── P5 stp-v2 顶层字段（设计 §2.2）──
  const sourceComposition = mapSourceComposition(response.sourceComposition)
  const publicSourceCatalog = mapPublicSourceCatalog(response.publicSourceCatalog)
  const degradationSummary = mapDegradationSummary(response.degradationSummary)
  const caveats = mapCaveats(response.caveats)
  const contextResolution = mapContextResolution(response.contextResolution)
  let contextInvalidation = mapContextInvalidation(response.contextInvalidation)
  // 设计 §13.9 / handoff §3：CONTEXT_INVALIDATED 必须伴随非空 contextInvalidation 且 blocks 为空，否则 fail closed。
  if (semanticTurn !== undefined && semanticTurn.disposition === 'CONTEXT_INVALIDATED') {
    const blocksEmpty = response.blocks === undefined || response.blocks.length === 0
    if (contextInvalidation === undefined || !blocksEmpty) {
      reportInvalidContextInvalidation()
      contextInvalidation = undefined
      semanticTurn = { ...semanticTurn, disposition: 'REJECTED', clarification: undefined, completedTasks: [] }
    }
  }
  const hasV2Blocks = response.blocks !== undefined
  const authoritativeContent = hasV2Blocks ? response.blocks : response.sections
  const isBlank = !response.title?.trim() && !response.summary?.trim() &&
    (!authoritativeContent || authoritativeContent.every(item => !item.content?.trim())) &&
    semanticTurn === undefined

  if (isBlank) {
    throw new Error('Answer response has no content')
  }

  // 结构化作品推荐是可选字段。非法结构不致命：保留可信文本回答，
  // 忽略非法推荐，并通过现有安全诊断入口上报（不含问题/批次 ID/作品内容）。
  const portfolioRecommendation = semanticTurn === undefined
    ? mapPortfolioRecommendation(response)
    : undefined

  const semanticSections = semanticTurn === undefined ? [] : mapSemanticSections(semanticTurn)
  // 正式 preset 的正文由后端按已审核 claim 合同投影；即使同时存在用于执行状态展示的
  // semanticTurn，也必须优先使用该权威正文，不能回退到自由文本路由出的无关章节。
  const hasPresetContractBlocks = response.questionPresetId !== undefined
    && response.blocks !== undefined
    && response.blocks.some((block) => Boolean(block.content?.trim()))
  const sections = semanticTurn === undefined
    ? mapSections(response)
    : hasPresetContractBlocks
      ? mapSections(response)
    : semanticSections.length > 0
      ? semanticSections
      : isSafeSemanticCompatibilityProjection(semanticTurn)
        ? mapSections(response)
        : []

  // 统一视图权威：顶层 Evidence 集合始终从统一 Sections 推导，不信任原始字段。
  const evidenceIds = [...new Set(sections.flatMap((section) => section.evidenceIds))]

  return {
    turnId: response.turnId,
    contentVersion: response.contentVersion,
    contractVersion: response.contractVersion,
    title: response.title || '',
    summary: response.summary || '',
    intent: response.intent,
    answerScope: response.answerScope,
    sections,
    resolution: response.resolution,
    answerSource: response.answerSource ?? null,
    generationMode: response.generationMode,
    constructionMode: response.constructionMode ?? legacyConstructionMode(response),
    intentSource: response.intentSource ?? legacyIntentSource(response),
    evidenceState: response.evidenceState ?? legacyEvidenceState(response, sections),
    verification: response.verification,
    evidenceIds: [...evidenceIds],
    suggestedQuestionPresetIds: [...(response.suggestedQuestionPresetIds || [])],
    suggestedQuestions: (response.suggestedQuestions || []).map((suggestion) =>
      typeof suggestion === 'string'
        ? {
            text: suggestion,
            projectSlug: null,
            caseSlug: null,
            facet: null,
          }
        : { ...suggestion },
    ),
    coveredTopics: [...new Set(response.coveredTopics ?? [])],
    guidanceStage: response.guidanceStage ?? null,
    degraded: degradationSummary !== undefined ? degradationSummary.degraded : response.degraded === true,
    referenceContext: response.referenceContext
      ? {
          ...response.referenceContext,
          projectSlugs: response.referenceContext.projectSlugs ? [...response.referenceContext.projectSlugs] : undefined,
          caseSlugs: response.referenceContext.caseSlugs ? [...response.referenceContext.caseSlugs] : undefined,
          referencedClaimIds: [...response.referenceContext.referencedClaimIds],
        }
      : undefined,
    contextVersionUpdated: response.contextVersionUpdated === true,
    portfolioRecommendation,
    semanticTurn,
    // P3：会话续接状态与 ResumeToken（handoff §5）。
    ...mapConversationField(response.conversation),
    // P5 stp-v2 顶层字段。
    ...(sourceComposition === undefined ? {} : { sourceComposition }),
    ...(publicSourceCatalog === undefined ? {} : { publicSourceCatalog }),
    ...(degradationSummary === undefined ? {} : { degradationSummary }),
    ...(caveats === undefined ? {} : { caveats }),
    ...(contextInvalidation === undefined ? {} : { contextInvalidation }),
    ...(contextResolution === undefined ? {} : { contextResolution }),
  }
}

// 唯一协议兼容边界：v2 Blocks 优先，legacy Sections 兜底。
function isSafeSemanticCompatibilityProjection(semanticTurn: SemanticTurnView): boolean {
  return semanticTurn.disposition === 'READY' || semanticTurn.disposition === 'PARTIAL_READY'
}

// ── P3：成功联合响应映射（handoff §4）────────────────────────────────────────
// 200 响应先按 responseKind 分流：ANSWER 正常映射；COMPLETION_RECEIPT 不伪造正文；
// 未知 responseKind 进入契约错误。ResumeToken 绝不进入诊断。
export function mapAnswerSuccess(response: P3AnswerSuccess): MappedAnswerSuccess {
  const resolved = resolveAnswerSuccess(response)
  if (resolved.kind === 'ANSWER') {
    return { kind: 'ANSWER', answer: mapAnswerResponse(resolved.response) }
  }
  if (resolved.kind === 'COMPLETION_RECEIPT') {
    return { kind: 'COMPLETION_RECEIPT', receipt: mapCompletionReceipt(resolved.response) }
  }
  return { kind: 'CONTRACT_ERROR', responseKind: resolved.responseKind }
}

function mapCompletionReceipt(response: CompletionReceiptResponse): MappedCompletionReceipt {
  const validTasks = (Array.isArray(response.completedTasks) ? response.completedTasks : [])
    .filter((task) => PUBLIC_TASK_STATUSES.has(String(task?.status)))
    .map((task) => ({
      displayIndex: typeof task.displayIndex === 'string' ? task.displayIndex : '',
      status: task.status,
      ...(typeof task.contextHandle === 'string' && task.contextHandle
        ? { contextHandle: task.contextHandle } : {}),
    }))
  return {
    turnId: response.turnId,
    requestToken: response.requestToken,
    completedTasks: validTasks,
    conversation: mapConversationField(response.conversation).conversation
      ?? { continuationStatus: 'NOT_APPLICABLE' as const },
  }
}

const CONVERSATION_CONTINUATION_STATUSES: ReadonlySet<ConversationContinuationStatus> = new Set([
  'AVAILABLE', 'PERSISTENCE_UNAVAILABLE', 'CONTEXT_EXPIRED', 'CONTEXT_CLEARED', 'NOT_APPLICABLE',
])

const RECENT_TASK_TYPES: ReadonlySet<RecentTaskType> = new Set([
  'FACT', 'COMPARE', 'RECOMMENDATION', 'REFINE',
])

const PUBLIC_TASK_STATUSES = new Set<string>([
  // P5 stp-v2 公共闭集（handoff §1）
  'COMPLETED', 'PARTIAL', 'EMPTY', 'NOT_SUPPORTED', 'NOT_APPLICABLE', 'BLOCKED',
  'UNAVAILABLE', 'STALE', 'FAILED', 'REJECTED', 'NOT_EXECUTED',
  // 迁移期旧值
  'PRESENTATION_BLOCKED', 'DEPENDENCY_UNAVAILABLE', 'NOT_EXECUTED_BUDGET', 'CANCELLED',
])

function isConversationContinuationStatus(value: string): value is ConversationContinuationStatus {
  return CONVERSATION_CONTINUATION_STATUSES.has(value as ConversationContinuationStatus)
}

function isRecentTaskType(value: string): value is RecentTaskType {
  return RECENT_TASK_TYPES.has(value as RecentTaskType)
}

/** 校验会话续接信息（handoff §5）；非法 continuationStatus 一律丢弃，不更新本地 Token。 */
export function mapConversationField(value: unknown): { conversation?: MappedConversation } {
  if (typeof value !== 'object' || value === null) return {}
  const raw = value as Record<string, unknown>
  if (typeof raw.continuationStatus !== 'string'
    || !isConversationContinuationStatus(raw.continuationStatus)) {
    return {}
  }
  const conversation: MappedConversation = { continuationStatus: raw.continuationStatus }
  if (typeof raw.resumeToken === 'string' && raw.resumeToken.trim()) {
    conversation.resumeToken = raw.resumeToken.trim()
  }
  const summary = mapContextSummary(raw.activeContextSummary)
  if (summary) conversation.activeContextSummary = summary
  return { conversation }
}

/** 校验安全 Context Summary（handoff §11）；用于恢复卡和 activeContextSummary。 */
export function mapContextSummary(value: unknown): ConversationContextSummary | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const raw = value as Record<string, unknown>
  if (raw.canRefine !== true && raw.canRefine !== false) return undefined
  const summary: ConversationContextSummary = {
    subjectLabels: stringLabelArray(raw.subjectLabels),
    facetLabels: stringLabelArray(raw.facetLabels),
    comparisonDimensionLabels: stringLabelArray(raw.comparisonDimensionLabels),
    preferenceLabels: stringLabelArray(raw.preferenceLabels),
    canRefine: raw.canRefine,
  }
  if (typeof raw.recentTaskType === 'string' && isRecentTaskType(raw.recentTaskType)) {
    summary.recentTaskType = raw.recentTaskType
  }
  return summary
}

function stringLabelArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0)
    .map((entry) => entry.trim())
}

function copySourceReference(reference: PublicSourceReference): PublicSourceReference {
  const copy: PublicSourceReference = {
    referenceKey: reference.referenceKey,
    label: reference.label,
    sourceType: reference.sourceType,
    subjectRoute: reference.subjectRoute,
    publishedVersion: reference.publishedVersion,
  }
  if (reference.evidenceRoute !== undefined) copy.evidenceRoute = reference.evidenceRoute
  return copy
}

function mapSemanticSections(semanticTurn: SemanticTurnView): AnswerSectionView[] {
  return semanticTurn.completedTasks.flatMap((task) => {
    if (task.resultPayload.kind === 'RECOMMENDATION_RESULT') return []
    return task.resultPayload.blocks.map((block, index) =>
      mapSemanticBlock(block, task.displayIndex, index))
  })
}

function mapSemanticBlock(
  block: CompletedTaskBlockView,
  displayIndex: string,
  index: number,
): AnswerSectionView {
  return {
    key: `semantic:${displayIndex}:${index}`,
    type: block.sectionType ?? 'GENERAL',
    title: block.title ?? '',
    sourceScope: block.sourceScope,
    content: block.content,
    claimIds: stableDistinct(block.claimIds ?? []),
    evidenceIds: stableDistinct(block.evidenceIds ?? []),
    blockId: block.blockId,
    sourceDomain: block.sourceDomain,
    support: block.support === undefined ? undefined : copyBlockSupport(block.support),
    // 已在语义映射层校验过，这里仅复制（保持不可变）。
    ...(block.sourceReferences === undefined ? {} : { sourceReferences: block.sourceReferences.map(copySourceReference) }),
    // P5 stp-v2（设计 §9.3 / handoff §5）：逐 Block 来源域（权威）、支持明细与 blockId，
    // 已在 semanticTurnView 校验通过后透传到统一章节视图供渲染层消费。
    ...(block.blockId === undefined ? {} : { blockId: block.blockId }),
    ...(block.sourceDomain === undefined ? {} : { sourceDomain: block.sourceDomain }),
    ...(block.support === undefined ? {} : { support: copyBlockSupport(block.support) }),
  }
}

// P5：Block 支持明细是不可变投影；这里深拷贝 statementReferences 与数组，保持视图不可变。
function copyBlockSupport(support: AnswerBlockSupport): AnswerBlockSupport {
  const result: AnswerBlockSupport = {
    kind: support.kind,
    statementReferences: support.statementReferences.map((reference) => ({ ...reference })),
    sourceTaskIds: [...support.sourceTaskIds],
    publicSourceKeys: [...support.publicSourceKeys],
  }
  if (support.contentVersion !== undefined) result.contentVersion = support.contentVersion
  return result
}

function mapSections(response: AnswerResponse): AnswerSectionView[] {
  if (response.blocks !== undefined) {
    return response.blocks.map((block, index) => mapBlock(block, index, response))
  }
  return (response.sections ?? []).map((section, index) => ({
    key: `${section.type}:${index}`,
    type: section.type,
    title: section.title || '',
    sourceScope: 'PORTFOLIO' as const,
    content: section.content,
    claimIds: [...(section.claimIds ?? [])],
    evidenceIds: stableDistinct(section.evidenceIds),
  }))
}

function mapBlock(block: AnswerBlock, index: number, response: AnswerResponse): AnswerSectionView {
  const sectionType = block.sectionType
  const title = block.title
  const isTyped = sectionType !== undefined || title !== undefined
  if (isTyped && (!sectionType || !title?.trim())) {
    reportInvalidTypedBlock(response.turnId)
    throw new Error(INVALID_TYPED_BLOCK_ERROR)
  }
  return {
    key: `${(sectionType ?? legacyType(block, response))}:${index}`,
    type: sectionType ?? legacyType(block, response),
    title: title ?? '',
    sourceScope: block.sourceScope,
    content: block.content,
    claimIds: stableDistinct(block.claimIds ?? []),
    evidenceIds: stableDistinct(block.evidenceIds ?? []),
    blockId: block.blockId,
    sourceDomain: block.sourceDomain,
    support: block.support === undefined ? undefined : copyBlockSupport(block.support),
    // P3：校验原始 sourceReferences（handoff §8）；非法引用被丢弃。
    ...mapSourceReferencesField(block.sourceReferences),
  }
}

// 兼容类型只对未类型化旧 Block 生效：
// - GENERAL 来源保持 GENERAL；
// - 非 ANSWERED 的 Portfolio Block（失败/边界响应）映射为 BOUNDARY；
// - ANSWERED 的 Portfolio Block（Comparison/Recommendation 等未迁移回答）
//   保留其正文与引用，用 GENERAL 兼容类型，避免误标为边界。
function legacyType(block: AnswerBlock, response: AnswerResponse): 'GENERAL' | 'BOUNDARY' {
  if (block.sourceScope === 'GENERAL') return 'GENERAL'
  return response.resolution === 'ANSWERED' ? 'GENERAL' : 'BOUNDARY'
}

function stableDistinct(values: string[]): string[] {
  return [...new Set(values)]
}

function reportInvalidTypedBlock(turnId: string): void {
  // 只上报脱敏分类，不携带正文、Claim ID、Evidence ID 或标题。
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'ANSWER_BLOCK_INVALID',
    errorKind: 'INVALID_RESPONSE',
    turnId: turnId,
  }))
}

function legacyConstructionMode(response: AnswerResponse) {
  if (response.generationMode === 'MODEL') {
    return response.answerSource === undefined ? 'GENERAL_MODEL' as const : 'MODEL_GROUNDED' as const
  }
  return response.answerSource === undefined ? 'TEMPLATE' as const : 'EVIDENCE_COMPOSITION' as const
}

function legacyIntentSource(response: AnswerResponse) {
  if (response.answerSource === 'PRESET') return 'PRESET' as const
  if (response.answerSource !== undefined) return 'RULE' as const
  return 'GLOBAL' as const
}

function legacyEvidenceState(
  response: AnswerResponse,
  sections: AnswerSectionView[],
) {
  if (response.resolution === 'NOT_SUPPORTED') return 'INSUFFICIENT' as const
  const evidenceIds = sections.flatMap((section) => section.evidenceIds)
  if (evidenceIds.length > 0) return 'VERIFIED' as const
  if (response.answerScope === 'PORTFOLIO'
    || response.answerScope === 'MIXED'
    || response.answerScope === 'HYBRID') return 'INSUFFICIENT' as const
  return 'NOT_REQUIRED' as const
}

// 小型运行时校验：不引入新 schema 库。
// 校验失败时返回 undefined 并上报脱敏诊断，不抛错——保留可信的 blocks/sections。
function mapPortfolioRecommendation(response: AnswerResponse): PortfolioRecommendation | undefined {
  const raw = response.portfolioRecommendation
  if (raw === undefined) return undefined

  if (!isValidPortfolioRecommendation(raw)) {
    reportInvalidRecommendation(response.turnId)
    return undefined
  }

  return {
    recommendationBatchId: raw.recommendationBatchId,
    context: {
      recommendationBatchId: raw.context.recommendationBatchId,
      contentVersion: raw.context.contentVersion,
      careerTrack: raw.context.careerTrack,
      audienceRole: raw.context.audienceRole,
      capabilityCodes: [...raw.context.capabilityCodes],
      requestedSize: raw.context.requestedSize,
      selectedPortfolioIds: [...raw.context.selectedPortfolioIds],
    },
    items: raw.items.map((candidate) => ({
      portfolioId: candidate.portfolioId,
      title: candidate.title,
      route: candidate.route,
      matchReasons: [...candidate.matchReasons],
      evidenceIds: [...candidate.evidenceIds],
    })),
    satisfiedConstraints: [...raw.satisfiedConstraints],
    unsatisfiedConstraints: [...raw.unsatisfiedConstraints],
  }
}

function isValidPortfolioRecommendation(value: unknown): value is PortfolioRecommendation {
  if (typeof value !== 'object' || value === null) return false
  const recommendation = value as Record<string, unknown>
  return isBatchId(recommendation.recommendationBatchId)
    && isValidRecommendationContext(recommendation.context)
    && recommendation.recommendationBatchId === recommendation.context.recommendationBatchId
    && Array.isArray(recommendation.items) && recommendation.items.every(isValidRecommendationItem)
    && recommendationMatchesContext(recommendation.items, recommendation.context)
    && isNonBlankStringArray(recommendation.satisfiedConstraints)
    && isNonBlankStringArray(recommendation.unsatisfiedConstraints)
}

function recommendationMatchesContext(
  items: PortfolioRecommendationItem[],
  context: PortfolioRecommendationContext,
): boolean {
  const selectedIds = context.selectedPortfolioIds
  return selectedIds.length <= context.requestedSize
    && new Set(selectedIds).size === selectedIds.length
    && selectedIds.length === items.length
    && selectedIds.every((portfolioId, index) => portfolioId === items[index]?.portfolioId)
}

function isValidRecommendationContext(value: unknown): value is PortfolioRecommendationContext {
  if (typeof value !== 'object' || value === null) return false
  const context = value as Record<string, unknown>
  return isBatchId(context.recommendationBatchId)
    && isNonEmptyString(context.contentVersion)
    && (context.careerTrack === null || isNonEmptyString(context.careerTrack))
    && isNonEmptyString(context.audienceRole)
    && isNonBlankStringArray(context.capabilityCodes)
    && Number.isInteger(context.requestedSize)
    && Number(context.requestedSize) >= 2
    && Number(context.requestedSize) <= 5
    && isNonBlankStringArray(context.selectedPortfolioIds)
}

function isValidRecommendationItem(value: unknown): value is PortfolioRecommendationItem {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return isNonEmptyString(item.portfolioId)
    && isNonEmptyString(item.title)
    && isNonEmptyString(item.route)
    && isNonBlankStringArray(item.matchReasons)
    && isNonEmptyNonBlankStringArray(item.evidenceIds)
}

const BATCH_ID_PATTERN = /^rec_[0-9a-f]{64}$/

function isBatchId(value: unknown): value is string {
  return typeof value === 'string' && BATCH_ID_PATTERN.test(value)
}

function isNonBlankStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(isNonEmptyString)
}

function isNonEmptyNonBlankStringArray(value: unknown): value is string[] {
  return isNonBlankStringArray(value) && value.length > 0
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function reportInvalidRecommendation(turnId: string): void {
  // turnId 可能不是 UUID 形态；createFrontendDiagnosticEvent 会按需附加，
  // 这里只传它，不传问题/批次 ID/作品内容，满足隐私契约。
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'PORTFOLIO_RECOMMENDATION_INVALID',
    errorKind: 'INVALID_RESPONSE',
    turnId: turnId,
  }))
}

// ── P5 stp-v2 顶层字段校验（设计 §2.2/§9.5/§9.7/§9.9/§10.8/§13.14，handoff §3/§6）──
const SOURCE_COMPOSITIONS = new Set<SourceComposition>([
  'GENERAL_ONLY', 'PORTFOLIO_ONLY', 'MULTI_SOURCE', 'CROSS_DOMAIN_DERIVED',
])
const DEGRADATION_KINDS = new Set<PublicDegradationKind>([
  'RETRIEVAL_FALLBACK', 'EXPRESSION_FALLBACK', 'CROSS_DOMAIN_EXPRESSION_FALLBACK', 'CONTENT_BACKEND_FALLBACK',
])
const CONTEXT_INVALIDATION_RECOVERY_ACTIONS = new Set<ContextInvalidationRecoveryAction>([
  'RESTART_FROM_CURRENT_CONTENT', 'RESELECT_RESULTS', 'REASK_WITHOUT_CONTEXT',
])
const CONTEXT_RESOLUTION_MODES = new Set<ContextResolutionMode>(['REVALIDATED_TO_CURRENT'])
const P5_CONTEXT_TYPES = new Set<ConversationContextType>(['RECENT_SEMANTIC_TASK', 'RECOMMENDATION'])
const CATALOG_SOURCE_TYPES = new Set<PublicSourceType>([
  'COLLECTION', 'DOCUMENT', 'SCREENSHOT', 'CODE', 'TEST_RESULT',
])
const RELATIVE_ROUTE_PATTERN = /^\/[^/][A-Za-z0-9._\-/?&=%~]*$/

function isRelativeSiteRoute(value: string): boolean {
  return RELATIVE_ROUTE_PATTERN.test(value) && !value.includes('://')
}

function mapSourceComposition(value: unknown): SourceComposition | undefined {
  return knownEnum(value, SOURCE_COMPOSITIONS)
}

function mapCatalogEntry(value: unknown): PublicSourceCatalogEntry | null {
  if (typeof value !== 'object' || value === null) return null
  const entry = value as Record<string, unknown>
  const referenceKey = typeof entry.referenceKey === 'string' ? entry.referenceKey.trim() : ''
  const label = typeof entry.label === 'string' ? entry.label.trim() : ''
  const sourceType = typeof entry.sourceType === 'string' ? entry.sourceType : ''
  const subjectRoute = typeof entry.subjectRoute === 'string' ? entry.subjectRoute.trim() : ''
  const publishedVersion = typeof entry.publishedVersion === 'string' ? entry.publishedVersion.trim() : ''
  if (!referenceKey || !label || !subjectRoute || !publishedVersion) return null
  if (!CATALOG_SOURCE_TYPES.has(sourceType as PublicSourceType)) return null
  if (!isRelativeSiteRoute(subjectRoute)) return null
  const result: PublicSourceCatalogEntry = {
    referenceKey,
    label,
    sourceType: sourceType as PublicSourceType,
    subjectRoute,
    publishedVersion,
  }
  if (typeof entry.evidenceRoute === 'string' && isRelativeSiteRoute(entry.evidenceRoute.trim())) {
    result.evidenceRoute = entry.evidenceRoute.trim()
  }
  return result
}

// 顶层目录按 referenceKey 去重（设计 §9.7）；同一 key 可被多个 Block 的 publicSourceKeys 引用。
function mapPublicSourceCatalog(value: unknown): PublicSourceCatalogEntry[] | undefined {
  if (!Array.isArray(value) || value.length === 0) return undefined
  const seen = new Set<string>()
  const entries: PublicSourceCatalogEntry[] = []
  for (const raw of value) {
    const entry = mapCatalogEntry(raw)
    if (entry === null || seen.has(entry.referenceKey)) continue
    seen.add(entry.referenceKey)
    entries.push(entry)
  }
  return entries.length > 0 ? entries : undefined
}

function mapDegradationSummary(value: unknown): PublicDegradationSummary | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const raw = value as Record<string, unknown>
  if (raw.degraded !== true && raw.degraded !== false) return undefined
  const kinds = Array.isArray(raw.kinds)
    ? raw.kinds
      .map((kind) => knownEnum(kind, DEGRADATION_KINDS))
      .filter((kind): kind is PublicDegradationKind => kind !== undefined)
    : []
  return {
    degraded: raw.degraded,
    kinds,
    affectedTaskIds: stringLabelArray(raw.affectedTaskIds),
  }
}

function mapCaveat(value: unknown): PublicAnswerCaveat | null {
  if (typeof value !== 'object' || value === null) return null
  const raw = value as Record<string, unknown>
  const code = typeof raw.code === 'string' ? raw.code.trim() : ''
  const message = typeof raw.message === 'string' ? raw.message.trim() : ''
  if (!code || !message) return null
  return {
    code,
    message,
    appliesToBlockIds: stringLabelArray(raw.appliesToBlockIds),
    sourceTaskIds: stringLabelArray(raw.sourceTaskIds),
  }
}

function mapCaveats(value: unknown): PublicAnswerCaveat[] | undefined {
  if (!Array.isArray(value) || value.length === 0) return undefined
  const caveats = value
    .map(mapCaveat)
    .filter((caveat): caveat is PublicAnswerCaveat => caveat !== null)
  return caveats.length > 0 ? caveats : undefined
}

function mapContextInvalidation(value: unknown): ContextInvalidation | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const raw = value as Record<string, unknown>
  const reasonCode = typeof raw.reasonCode === 'string' ? raw.reasonCode.trim() : ''
  const recoveryAction = knownEnum(raw.recoveryAction, CONTEXT_INVALIDATION_RECOVERY_ACTIONS)
  const contextType = knownEnum(raw.contextType, P5_CONTEXT_TYPES)
  const currentContentVersion = typeof raw.currentContentVersion === 'string' ? raw.currentContentVersion.trim() : ''
  if (!reasonCode || recoveryAction === undefined || contextType === undefined || !currentContentVersion) {
    return undefined
  }
  return { reasonCode, recoveryAction, contextType, currentContentVersion }
}

function mapContextResolution(value: unknown): ContextResolution | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const raw = value as Record<string, unknown>
  const mode = knownEnum(raw.mode, CONTEXT_RESOLUTION_MODES)
  const contextType = knownEnum(raw.contextType, P5_CONTEXT_TYPES)
  const currentContentVersion = typeof raw.currentContentVersion === 'string' ? raw.currentContentVersion.trim() : ''
  if (mode === undefined || contextType === undefined || !currentContentVersion) return undefined
  return { mode, contextType, currentContentVersion }
}

function reportInvalidContextInvalidation(): void {
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.response.invalid',
    errorCode: 'CONTEXT_INVALIDATION_INVALID',
    errorKind: 'INVALID_RESPONSE',
  }))
}
