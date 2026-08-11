import type {
  AnswerResponse,
  AnswerSectionView,
  MappedAnswer,
  AnswerBlock,
  LegacyAnswerSection,
} from './answerTypes'
import {
  mapSemanticTurnResponse,
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
  const semanticTurn = response.agentTurn === undefined
    ? undefined
    : mapSemanticTurnResponse(response.agentTurn)
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
  const sections = semanticTurn === undefined
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
    degraded: response.degraded === true,
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
  }
}

// 唯一协议兼容边界：v2 Blocks 优先，legacy Sections 兜底。
function isSafeSemanticCompatibilityProjection(semanticTurn: SemanticTurnView): boolean {
  return semanticTurn.disposition === 'READY' || semanticTurn.disposition === 'PARTIAL_READY'
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
    claimIds: stableDistinct(block.claimIds),
    evidenceIds: stableDistinct(block.evidenceIds),
  }
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
    claimIds: stableDistinct(block.claimIds),
    evidenceIds: stableDistinct(block.evidenceIds),
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
