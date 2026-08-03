import type { AnswerResponse, MappedAnswer } from './answerTypes'
import type {
  PortfolioRecommendation,
  PortfolioRecommendationContext,
  PortfolioRecommendationItem,
} from './answerTypes'
import { createFrontendDiagnosticEvent } from '../../../shared/diagnostics/frontendDiagnosticTypes'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

export function mapAnswerResponse(response: AnswerResponse): MappedAnswer {
  const isBlank = !response.title?.trim() && !response.summary?.trim() &&
    (!response.sections || response.sections.every(s => !s.content?.trim())) &&
    (!response.blocks || response.blocks.every(b => !b.content?.trim()))

  if (isBlank) {
    throw new Error('Answer response has no content')
  }

  // 结构化作品推荐是可选字段。非法结构不致命：保留可信文本回答，
  // 忽略非法推荐，并通过现有安全诊断入口上报（不含问题/批次 ID/作品内容）。
  const portfolioRecommendation = mapPortfolioRecommendation(response)

  const blocks = response.blocks?.map(block => ({
    ...block,
    evidenceIds: [...block.evidenceIds],
    claimIds: [...block.claimIds],
  }))
  const evidenceIds = response.evidenceIds ??
    [...new Set((blocks ?? []).flatMap((block) => block.evidenceIds))]

  return {
    turnId: response.turnId,
    contentVersion: response.contentVersion,
    title: response.title || '',
    summary: response.summary || '',
    intent: response.intent,
    answerScope: response.answerScope,
    sections: response.sections?.map((section) => ({
      ...section,
      evidenceIds: [...section.evidenceIds],
      claimIds: [...(section.claimIds ?? [])],
    })) || [],
    blocks,
    resolution: response.resolution,
    answerSource: response.answerSource ?? null,
    generationMode: response.generationMode,
    constructionMode: response.constructionMode ?? legacyConstructionMode(response),
    intentSource: response.intentSource ?? legacyIntentSource(response),
    evidenceState: response.evidenceState ?? legacyEvidenceState(response),
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
  }
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

function legacyEvidenceState(response: AnswerResponse) {
  if (response.resolution === 'NOT_SUPPORTED') return 'INSUFFICIENT' as const
  const evidenceIds = response.evidenceIds ?? response.blocks?.flatMap((block) => block.evidenceIds) ?? []
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
