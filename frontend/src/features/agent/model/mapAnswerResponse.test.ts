import { describe, expect, it, vi } from 'vitest'

import type { AnswerResolution, ConversationTopic, PortfolioRecommendation } from './answerTypes'
import { mapAnswerResponse } from './mapAnswerResponse'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

describe('mapAnswerResponse', () => {
  function response(resolution: AnswerResolution = 'ANSWERED') {
    return {
      requestId: 'request-1',
      turnId: 'turn-1',
      contentVersion: '2026-07-21',
      questionPresetId: resolution === 'ANSWERED' ? 'preset-1' : undefined,
      resolution,
      constructionMode: 'EVIDENCE_COMPOSITION' as const,
      intentSource: resolution === 'ANSWERED' ? ('PRESET' as const) : ('RULE' as const),
      evidenceState: resolution === 'ANSWERED' ? ('VERIFIED' as const) : ('INSUFFICIENT' as const),
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
      referenceContext: resolution === 'ANSWERED' ? {
        previousContentVersion: '2026-07-21',
        projectSlugs: ['sql-audit'],
        questionPresetId: 'preset-1',
        referencedClaimIds: ['claim-1'],
        followUpAction: 'RELATED_QUESTION' as const,
      } : undefined,
      contextVersionUpdated: resolution === 'ANSWERED',
    }
  }

  it('preserves structured sections and all four answer dimensions', () => {
    const source = response()
    const mapped = mapAnswerResponse(source)

    expect(mapped.sections).toEqual([{
      key: 'BACKGROUND:0',
      type: 'BACKGROUND',
      title: '背景',
      sourceScope: 'PORTFOLIO',
      content: '结构化内容',
      claimIds: ['claim-1'],
      evidenceIds: ['evidence-1'],
    }])
    expect(mapped).toMatchObject({
      contentVersion: '2026-07-21',
      resolution: 'ANSWERED',
      constructionMode: 'EVIDENCE_COMPOSITION',
      intentSource: 'PRESET',
      evidenceState: 'VERIFIED',
      evidenceIds: ['evidence-1'],
    })
    expect(mapped.sections[0].claimIds).toEqual(['claim-1'])
    expect(mapped.sections[0].claimIds).not.toBe(source.sections[0].claimIds)
    expect(mapped.referenceContext).toEqual(source.referenceContext)
    expect(mapped.referenceContext?.projectSlugs)
      .not.toBe(source.referenceContext?.projectSlugs)
    expect(mapped.referenceContext?.referencedClaimIds)
      .not.toBe(source.referenceContext?.referencedClaimIds)
    expect(mapped.contextVersionUpdated).toBe(true)
    expect(mapped).not.toHaveProperty('blocks')
  })

  it('rejects an answer whose title, summary and sections are all blank', () => {
    const blank = response()
    blank.title = ' '
    blank.summary = ' '
    blank.sections[0].content = ' '

    expect(() => mapAnswerResponse(blank)).toThrowError('Answer response has no content')
  })

  it('keeps a clarification explicitly evidence-insufficient', () => {
    const mapped = mapAnswerResponse(response('NEEDS_CLARIFICATION'))

    expect(mapped.resolution).toBe('NEEDS_CLARIFICATION')
    expect(mapped.intentSource).toBe('RULE')
    expect(mapped.evidenceState).toBe('INSUFFICIENT')
    expect(mapped.evidenceIds).toEqual([])
  })

  it('maps the exact v2 backend contract including contextual suggestions', () => {
    const mapped = mapAnswerResponse({
      turnId: 'turn-v2',
      contentVersion: '2026-07-24',
      intent: 'PORTFOLIO_GROUNDED',
      answerScope: 'PORTFOLIO',
      resolution: 'ANSWERED',
      constructionMode: 'EVIDENCE_COMPOSITION',
      intentSource: 'RULE',
      evidenceState: 'VERIFIED',
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
    // ANSWERED 无类型旧 Block 使用兼容 GENERAL 类型与空标题，不丢失正文或引用，
    // 不误标为边界（BOUNDARY 只用于非 ANSWERED 的失败/边界响应）
    expect(mapped.sections).toEqual([{
      key: 'GENERAL:0',
      type: 'GENERAL',
      title: '',
      sourceScope: 'PORTFOLIO',
      content: 'Implementation details',
      claimIds: ['claim-1'],
      evidenceIds: ['evidence-1'],
    }])
    expect(mapped).not.toHaveProperty('blocks')
  })

  it('maps untyped portfolio blocks of non-answered responses to BOUNDARY', () => {
    const mapped = mapAnswerResponse({
      ...response('NOT_SUPPORTED'),
      blocks: [{
        sourceScope: 'PORTFOLIO',
        content: '当前公开内容中没有足够的已验证材料。',
        claimIds: [],
        evidenceIds: [],
      }],
    })

    expect(mapped.sections).toEqual([{
      key: 'BOUNDARY:0',
      type: 'BOUNDARY',
      title: '',
      sourceScope: 'PORTFOLIO',
      content: '当前公开内容中没有足够的已验证材料。',
      claimIds: [],
      evidenceIds: [],
    }])
  })

  it('derives top-level evidence from unified sections and deduplicates within a section', () => {
    const mapped = mapAnswerResponse({
      ...response(),
      evidenceIds: ['evidence-ghost'],
      blocks: [{
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        title: '技术方案',
        content: '正文',
        claimIds: ['claim-1', 'claim-1'],
        evidenceIds: ['evidence-1', 'evidence-1', 'evidence-2'],
      }],
    })

    expect(mapped.evidenceIds).toEqual(['evidence-1', 'evidence-2'])
    expect(mapped.sections[0].claimIds).toEqual(['claim-1'])
    expect(mapped.sections[0].evidenceIds).toEqual(['evidence-1', 'evidence-2'])
  })

  it('maps typed v2 blocks with priority over legacy sections', () => {
    const mapped = mapAnswerResponse({
      ...response(),
      summary: '公开项目摘要',
      blocks: [{
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        title: '技术方案与实现',
        content: '使用受控路由。',
        claimIds: ['claim-1'],
        evidenceIds: ['evidence-1'],
      }],
      sections: [{
        type: 'BACKGROUND',
        title: '旧章节',
        content: '不得采用',
        claimIds: [],
        evidenceIds: [],
      }],
    })

    expect(mapped.sections).toEqual([{
      key: 'SOLUTION:0',
      type: 'SOLUTION',
      title: '技术方案与实现',
      sourceScope: 'PORTFOLIO',
      content: '使用受控路由。',
      claimIds: ['claim-1'],
      evidenceIds: ['evidence-1'],
    }])
    expect(mapped).not.toHaveProperty('blocks')
  })

  it('treats an explicitly empty blocks array as authoritative', () => {
    const mapped = mapAnswerResponse({
      ...response(),
      title: '作品集信息',
      blocks: [],
      sections: [{
        type: 'BACKGROUND',
        title: '旧章节',
        content: '不得回退展示',
        claimIds: ['legacy-claim'],
        evidenceIds: ['legacy-evidence'],
      }],
    })

    expect(mapped.sections).toEqual([])
    expect(mapped.evidenceIds).toEqual([])
  })

  it('rejects blank content when empty blocks make legacy sections non-authoritative', () => {
    expect(() => mapAnswerResponse({
      ...response(),
      title: '',
      summary: '',
      blocks: [],
      sections: [{
        type: 'BACKGROUND',
        title: '旧章节',
        content: '不得回退展示',
        claimIds: [],
        evidenceIds: [],
      }],
    })).toThrow('Answer response has no content')
  })

  it('maps general untyped blocks and keeps legacy sections as fallback', () => {
    const mapped = mapAnswerResponse({
      ...response(),
      sections: [{
        type: 'BACKGROUND',
        title: '背景',
        content: 'legacy 内容',
        evidenceIds: ['evidence-1'],
        claimIds: ['claim-1'],
      }],
    })

    expect(mapped.sections).toEqual([{
      key: 'BACKGROUND:0',
      type: 'BACKGROUND',
      title: '背景',
      sourceScope: 'PORTFOLIO',
      content: 'legacy 内容',
      claimIds: ['claim-1'],
      evidenceIds: ['evidence-1'],
    }])
    expect(mapped).not.toHaveProperty('blocks')

    const general = mapAnswerResponse({
      ...response(),
      blocks: [{
        sourceScope: 'GENERAL',
        content: '一般内容',
        claimIds: [],
        evidenceIds: [],
      }],
    })
    expect(general.sections).toEqual([{
      key: 'GENERAL:0',
      type: 'GENERAL',
      title: '',
      sourceScope: 'GENERAL',
      content: '一般内容',
      claimIds: [],
      evidenceIds: [],
    }])
  })

  it('rejects a typed block missing title with a sanitized diagnostic', () => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')

    expect(() => mapAnswerResponse({
      ...response(),
      blocks: [{
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        content: '只有 sectionType 没有 title',
        claimIds: ['claim-1'],
        evidenceIds: ['evidence-1'],
      }],
    })).toThrowError('Answer response contains an invalid typed block')

    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.response.invalid',
      errorCode: 'ANSWER_BLOCK_INVALID',
      errorKind: 'INVALID_RESPONSE',
    }))
    const payload = JSON.stringify(reportSpy.mock.calls[0]?.[0])
    expect(payload).not.toContain('只有 sectionType 没有 title')
    expect(payload).not.toContain('claim-1')
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

  // —— P4 顶层聚合 MIXED / MIXED_COMPOSITION ——
  it('accepts top-level MIXED generation and MIXED_COMPOSITION construction (P4)', () => {
    const mapped = mapAnswerResponse({
      ...response(),
      generationMode: 'MIXED',
      constructionMode: 'MIXED_COMPOSITION',
    })

    expect(mapped.generationMode).toBe('MIXED')
    expect(mapped.constructionMode).toBe('MIXED_COMPOSITION')
    // 不丢可信正文
    expect(mapped.sections[0]?.content).toBe('结构化内容')
  })

  it('does not relax P3 source reference validation for a MODEL_GROUNDED task (P4)', () => {
    const mapped = mapAnswerResponse({
      requestId: 'request-p4',
      turnId: 'turn-p4-model',
      contentVersion: 'public-2026-08-13',
      resolution: 'ANSWERED',
      constructionMode: 'MODEL_GROUNDED',
      evidenceState: 'VERIFIED',
      title: '模型接地回答',
      summary: '',
      agentTurn: {
        contractVersion: 'stp-v1',
        disposition: 'READY',
        outcome: { planOutcome: 'SUCCEEDED' },
        completedTasks: [{
          displayIndex: '01',
          goalLabel: '模型任务',
          sourceDomain: 'PORTFOLIO',
          composition: { mode: 'MODEL_GROUNDED', degraded: false },
          resultPayload: {
            kind: 'SECTION_RESULT',
            blocks: [{
              sourceScope: 'PORTFOLIO',
              sectionType: 'SOLUTION',
              title: '方案',
              content: '模型表达正文',
              claimIds: [],
              evidenceIds: [],
              // 非闭集 sourceType：P3 校验应丢弃，不因模型模式放宽
              sourceReferences: [{
                referenceKey: 'SRC_BAD',
                label: '非法来源',
                sourceType: 'PRIVATE_BUCKET',
                subjectRoute: '/projects/sql-audit',
                publishedVersion: 'public-2026-08-13',
              }],
            }],
          },
        }],
      },
    })

    // 正文保留
    expect(mapped.sections[0]?.content).toBe('模型表达正文')
    // MODEL_GROUNDED 不放宽引用校验：非法引用被丢弃
    expect(mapped.sections[0]?.sourceReferences).toBeUndefined()
  })
})
