import type { AnswerResponse, MappedAnswer } from './answerTypes'
import type { PortfolioRecommendation, PortfolioRecommendationItem } from './answerTypes'
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
    contextEnvelope: response.contextEnvelope
      ? {
          ...response.contextEnvelope,
          projectSlugs: response.contextEnvelope.projectSlugs ? [...response.contextEnvelope.projectSlugs] : undefined,
          caseSlugs: response.contextEnvelope.caseSlugs ? [...response.contextEnvelope.caseSlugs] : undefined,
          referencedClaimIds: [...response.contextEnvelope.referencedClaimIds],
        }
      : undefined,
    contextVersionUpdated: response.contextVersionUpdated === true,
    portfolioRecommendation,
  }
}

// 小型运行时校验：不引入新 schema 库。
// 校验失败时返回 undefined 并上报脱敏诊断，不抛错——保留可信的 blocks/sections。
function mapPortfolioRecommendation(response: AnswerResponse): PortfolioRecommendation | undefined {
  const raw = response.portfolioRecommendation
  if (raw === undefined) return undefined

  if (!isNonEmptyString(raw.recommendationBatchId) || !Array.isArray(raw.items)) {
    reportInvalidRecommendation(response.turnId)
    return undefined
  }

  const items: PortfolioRecommendationItem[] = []
  for (const candidate of raw.items) {
    if (!isValidRecommendationItem(candidate)) {
      reportInvalidRecommendation(response.turnId)
      return undefined
    }
    items.push({
      portfolioId: candidate.portfolioId,
      title: candidate.title,
      route: candidate.route,
      matchReasons: [...candidate.matchReasons],
      evidenceIds: [...candidate.evidenceIds],
    })
  }

  return {
    recommendationBatchId: raw.recommendationBatchId,
    items,
    satisfiedConstraints: Array.isArray(raw.satisfiedConstraints) ? [...raw.satisfiedConstraints] : [],
    unsatisfiedConstraints: Array.isArray(raw.unsatisfiedConstraints) ? [...raw.unsatisfiedConstraints] : [],
  }
}

function isValidRecommendationItem(value: unknown): value is PortfolioRecommendationItem {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return isNonEmptyString(item.portfolioId)
    && typeof item.title === 'string'
    && typeof item.route === 'string'
    && Array.isArray(item.matchReasons) && item.matchReasons.every(isString)
    && Array.isArray(item.evidenceIds) && item.evidenceIds.every(isString)
}

function isString(value: unknown): value is string {
  return typeof value === 'string'
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
