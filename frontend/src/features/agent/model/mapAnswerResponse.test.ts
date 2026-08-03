import { describe, expect, it, vi } from 'vitest'

import type { ConversationTopic, PortfolioRecommendation } from './answerTypes'
import { mapAnswerResponse } from './mapAnswerResponse'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

describe('mapAnswerResponse', () => {
  function response(resolution: 'ANSWERED' | 'BOUNDARY' = 'ANSWERED') {
    return {
      requestId: 'request-1',
      turnId: 'turn-1',
      contentVersion: '2026-07-21',
      questionPresetId: resolution === 'ANSWERED' ? 'preset-1' : undefined,
      resolution,
      answerSource: resolution === 'ANSWERED' ? ('PRESET' as const) : undefined,
      generationMode: 'DETERMINISTIC' as const,
      verification: resolution === 'ANSWERED' ? ('VERIFIED' as const) : ('NOT_APPLICABLE' as const),
      title: '项目说明',
      summary: '公开摘要',
      sections: [{
        type: resolution === 'ANSWERED' ? ('BACKGROUND' as const) : ('BOUNDARY' as const),
        title: '背景',
        content: '结构化内容',
        evidenceIds: resolution === 'ANSWERED' ? ['evidence-1'] : [],
        claimIds: resolution === 'ANSWERED' ? ['claim-1'] : [],
      }],
      evidenceIds: resolution === 'ANSWERED' ? ['evidence-1'] : [],
      suggestedQuestionPresetIds: ['preset-1'],
      contextEnvelope: resolution === 'ANSWERED' ? {
        previousContentVersion: '2026-07-21',
        projectSlugs: ['sql-audit'],
        questionPresetId: 'preset-1',
        referencedClaimIds: ['claim-1'],
      } : undefined,
      contextVersionUpdated: resolution === 'ANSWERED',
    }
  }

  it('preserves structured sections and all four answer dimensions', () => {
    const source = response()
    const mapped = mapAnswerResponse(source)

    expect(mapped.sections).toEqual(source.sections)
    expect(mapped).toMatchObject({
      resolution: 'ANSWERED',
      answerSource: 'PRESET',
      generationMode: 'DETERMINISTIC',
      verification: 'VERIFIED',
      evidenceIds: ['evidence-1'],
    })
    expect(mapped.sections[0].claimIds).toEqual(['claim-1'])
    expect(mapped.sections[0].claimIds).not.toBe(source.sections[0].claimIds)
    expect(mapped.contextEnvelope).toEqual(source.contextEnvelope)
    expect(mapped.contextEnvelope?.projectSlugs)
      .not.toBe(source.contextEnvelope?.projectSlugs)
    expect(mapped.contextEnvelope?.referencedClaimIds)
      .not.toBe(source.contextEnvelope?.referencedClaimIds)
    expect(mapped.contextVersionUpdated).toBe(true)
  })

  it('rejects an answer whose title, summary and sections are all blank', () => {
    const blank = response()
    blank.title = ' '
    blank.summary = ' '
    blank.sections[0].content = ' '

    expect(() => mapAnswerResponse(blank)).toThrowError('Answer response has no content')
  })

  it('keeps a boundary unverified and without a fact source', () => {
    const mapped = mapAnswerResponse(response('BOUNDARY'))

    expect(mapped.resolution).toBe('BOUNDARY')
    expect(mapped.answerSource).toBeNull()
    expect(mapped.verification).toBe('NOT_APPLICABLE')
    expect(mapped.evidenceIds).toEqual([])
  })

  it('maps the exact v2 backend contract including contextual suggestions', () => {
    const mapped = mapAnswerResponse({
      turnId: 'turn-v2',
      contentVersion: '2026-07-24',
      intent: 'PORTFOLIO_GROUNDED',
      answerScope: 'PORTFOLIO',
      resolution: 'ANSWERED',
      title: 'SQL audit tool',
      blocks: [{
        sourceScope: 'PORTFOLIO',
        content: 'Implementation details',
        claimIds: ['claim-1'],
        evidenceIds: ['evidence-1'],
      }],
      suggestedQuestions: [{
        text: 'What challenges came up?',
        projectSlug: 'sql-audit',
        caseSlug: 'sql-audit-delivery',
        facet: 'CHALLENGE',
      }],
      degraded: true,
    })

    expect(mapped.suggestedQuestions).toEqual([{
      text: 'What challenges came up?',
      projectSlug: 'sql-audit',
      caseSlug: 'sql-audit-delivery',
      facet: 'CHALLENGE',
    }])
    expect(mapped.evidenceIds).toEqual(['evidence-1'])
    expect(mapped.degraded).toBe(true)
  })

  it('carries conversation progress fields with a defensive copy', () => {
    const source = {
      ...response(),
      coveredTopics: ['BACKGROUND', 'SOLUTION'] as ConversationTopic[],
      guidanceStage: 'DEEPENING' as const,
    }

    const mapped = mapAnswerResponse(source)

    expect(mapped.turnId).toBe('turn-1')
    expect(mapped.coveredTopics).toEqual(['BACKGROUND', 'SOLUTION'])
    expect(mapped.coveredTopics).not.toBe(source.coveredTopics)
    expect(mapped.guidanceStage).toBe('DEEPENING')
  })

  it('defaults missing progress fields to an empty topic list and no stage', () => {
    const mapped = mapAnswerResponse(response())

    expect(mapped.coveredTopics).toEqual([])
    expect(mapped.guidanceStage).toBeNull()
  })

  // —— 结构化作品推荐映射 ——
  function portfolioRecommendation() {
    return {
      recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      context: {
        recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        contentVersion: 'public-2026-07-31',
        careerTrack: null,
        audienceRole: 'INTERVIEWER',
        capabilityCodes: ['POSTGRESQL', 'RAG'],
        requestedSize: 2,
        selectedPortfolioIds: ['project-1', 'case-2'],
      },
      items: [
        {
          portfolioId: 'project-1',
          title: '项目一',
          route: '/projects/project-one',
          matchReasons: ['匹配后端能力要求'],
          evidenceIds: ['evidence-1'],
        },
        {
          portfolioId: 'case-2',
          title: '案例二',
          route: '/cases/case-two',
          matchReasons: ['补充 PostgreSQL 与验证能力'],
          evidenceIds: ['evidence-2'],
        },
      ],
      satisfiedConstraints: ['audienceRole', 'requestedSize'],
      unsatisfiedConstraints: [],
    }
  }

  it('leaves portfolioRecommendation undefined for a plain answer', () => {
    const mapped = mapAnswerResponse(response())

    expect(mapped.portfolioRecommendation).toBeUndefined()
  })

  it('deep-copies a complete portfolioRecommendation and preserves backend order', () => {
    const source = { ...response(), portfolioRecommendation: portfolioRecommendation() }
    const mapped = mapAnswerResponse(source)

    expect(mapped.portfolioRecommendation).toEqual(source.portfolioRecommendation)
    // 顺序必须保持后端权威顺序，不重排
    expect(mapped.portfolioRecommendation?.items.map((item) => item.portfolioId))
      .toEqual(['project-1', 'case-2'])
  })

  it('does not share nested array references with the source response', () => {
    const source = { ...response(), portfolioRecommendation: portfolioRecommendation() }
    const mapped = mapAnswerResponse(source)
    const rec = mapped.portfolioRecommendation!

    expect(rec.items).not.toBe(source.portfolioRecommendation!.items)
    expect(rec.items[0]!.matchReasons).not.toBe(source.portfolioRecommendation!.items[0]!.matchReasons)
    expect(rec.items[0]!.evidenceIds).not.toBe(source.portfolioRecommendation!.items[0]!.evidenceIds)
    expect(rec.satisfiedConstraints).not.toBe(source.portfolioRecommendation!.satisfiedConstraints)
    expect(rec.unsatisfiedConstraints).not.toBe(source.portfolioRecommendation!.unsatisfiedConstraints)

    // 修改映射结果不应回写源响应
    rec.items[0]!.matchReasons.push('篡改理由')
    expect(source.portfolioRecommendation!.items[0]!.matchReasons)
      .not.toContain('篡改理由')
  })

  it('deep-copies the recommendation context arrays', () => {
    const source = { ...response(), portfolioRecommendation: portfolioRecommendation() }
    const mapped = mapAnswerResponse(source)
    const mappedContext = mapped.portfolioRecommendation as unknown as {
      context: typeof source.portfolioRecommendation.context
    }

    expect(mappedContext.context).toEqual(source.portfolioRecommendation.context)
    expect(mappedContext.context).not.toBe(source.portfolioRecommendation.context)
    expect(mappedContext.context.capabilityCodes).not.toBe(source.portfolioRecommendation.context.capabilityCodes)
    expect(mappedContext.context.selectedPortfolioIds).not.toBe(source.portfolioRecommendation.context.selectedPortfolioIds)
  })

  it.each([
    ['a 32-hex batch id', { recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef' }],
    ['mismatched nested batch id', {
      context: { recommendationBatchId: 'rec_ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff' },
    }],
    ['an out-of-range requested size', { context: { requestedSize: 1 } }],
    ['a non-string constraint', { satisfiedConstraints: ['audienceRole', 1] }],
    ['items that disagree with selected portfolio order', {
      context: { selectedPortfolioIds: ['case-2', 'project-1'] },
    }],
    ['duplicate selected portfolio ids', {
      context: { selectedPortfolioIds: ['project-1', 'project-1'] },
    }],
    ['more selected ids than requested', {
      context: {
        requestedSize: 2,
        selectedPortfolioIds: ['project-1', 'case-2', 'project-3'],
      },
    }],
    ['an item without approved evidence ids', {
      items: [
        { ...portfolioRecommendation().items[0], evidenceIds: [] },
        portfolioRecommendation().items[1],
      ],
    }],
    ['a blank match reason', {
      items: [
        { ...portfolioRecommendation().items[0], matchReasons: [' '] },
        portfolioRecommendation().items[1],
      ],
    }],
  ])('drops a recommendation with %s', (_reason, override) => {
    const recommendation = portfolioRecommendation()
    const source = {
      ...response(),
      portfolioRecommendation: {
        ...recommendation,
        ...override,
        context: {
          ...recommendation.context,
          ...((override as { context?: object }).context ?? {}),
        },
      } as unknown as PortfolioRecommendation,
    }

    expect(mapAnswerResponse(source).portfolioRecommendation).toBeUndefined()
  })

  it('drops an invalid portfolioRecommendation without breaking the trusted text answer', () => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')
    const source = {
      ...response(),
      // 故意构造非法结构测试校验：用 as unknown as 绕过类型检查
      portfolioRecommendation: {
        recommendationBatchId: '', // 非法：缺批次 ID
        items: '不是数组',
      } as unknown as PortfolioRecommendation,
    }

    const mapped = mapAnswerResponse(source)

    // 可信文本回答仍保留
    expect(mapped.sections[0]?.content).toBe('结构化内容')
    // 非法推荐结构被忽略，不留半成品
    expect(mapped.portfolioRecommendation).toBeUndefined()
    // 调用现有安全诊断入口，且不泄露问题/批次 ID/作品内容
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.response.invalid',
      errorKind: 'INVALID_RESPONSE',
    }))
    const payload = JSON.stringify(reportSpy.mock.calls[0]?.[0])
    expect(payload).not.toContain('rec_')
    expect(payload).not.toContain('项目一')
  })
})
