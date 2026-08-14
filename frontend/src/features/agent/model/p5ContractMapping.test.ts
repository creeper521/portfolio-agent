import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AnswerBlock, AnswerResponse } from './answerTypes'
import { mapAnswerResponse } from './mapAnswerResponse'
import { mapSemanticTurnResponse } from './semanticTurnView'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

// 构造一个最小 stp-v2 READY 回答，带一个 Portfolio 完成任务与一个 Block。
function stpV2Answer(): AnswerResponse {
  return {
    turnId: 'turn-p5',
    contentVersion: 'public-2026-08-13',
    resolution: 'ANSWERED',
    title: 'P5 answer',
    summary: '',
    blocks: [],
    agentTurn: {
      contractVersion: 'stp-v2',
      disposition: 'READY',
      outcome: { planOutcome: 'SUCCEEDED' },
      completedTasks: [
        {
          displayIndex: '01',
          goalLabel: 'Goal one',
          sourceDomain: 'PORTFOLIO',
          resultPayload: {
            kind: 'SECTION_RESULT',
            blocks: [
              {
                sourceScope: 'PORTFOLIO',
                content: 'Portfolio body.',
                claimIds: [],
                evidenceIds: [],
              } as AnswerBlock,
            ],
          },
        },
      ],
    },
  }
}

// 返回第一个完成任务的可变句柄；绕过 AgentTurnPayload 联合类型上的属性访问。
function firstTask(response: AnswerResponse): Record<string, unknown> {
  return (response.agentTurn as never as { completedTasks: Array<Record<string, unknown>> }).completedTasks[0]!
}

describe('P5 stp-v2 contract mapping', () => {
  let reportSpy: ReturnType<typeof vi.spyOn>
  beforeEach(() => {
    reportSpy = vi.spyOn(frontendDiagnostics, 'report')
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('maps a stp-v2 READY turn and preserves the contract version', () => {
    const view = mapSemanticTurnResponse(stpV2Answer().agentTurn!)
    expect(view.contractVersion).toBe('stp-v2')
    expect(view.disposition).toBe('READY')
    expect(view.completedTasks).toHaveLength(1)
  })

  // ── CONTEXT_INVALIDATED 优先 + 跨字段不变量（handoff §3）──
  it('routes CONTEXT_INVALIDATED over NEEDS_CLARIFICATION to a recovery view, not clarification', () => {
    const response = stpV2Answer()
    response.resolution = 'NEEDS_CLARIFICATION'
    response.title = ''
    response.blocks = []
    response.agentTurn = { contractVersion: 'stp-v2', disposition: 'CONTEXT_INVALIDATED' }
    response.contextInvalidation = {
      reasonCode: 'CONTEXT_RESULT_STALE',
      recoveryAction: 'RESTART_FROM_CURRENT_CONTENT',
      contextType: 'RECOMMENDATION',
      currentContentVersion: 'public-2026-08-13',
    }

    const mapped = mapAnswerResponse(response)
    expect(mapped.semanticTurn?.disposition).toBe('CONTEXT_INVALIDATED')
    expect(mapped.semanticTurn?.clarification).toBeUndefined()
    expect(mapped.contextInvalidation).toMatchObject({
      reasonCode: 'CONTEXT_RESULT_STALE',
      recoveryAction: 'RESTART_FROM_CURRENT_CONTENT',
    })
  })

  it('fails closed when CONTEXT_INVALIDATED lacks a valid contextInvalidation', () => {
    const response = stpV2Answer()
    response.title = ''
    response.blocks = []
    response.agentTurn = { contractVersion: 'stp-v2', disposition: 'CONTEXT_INVALIDATED' }
    // contextInvalidation 缺失

    const mapped = mapAnswerResponse(response)
    expect(mapped.semanticTurn?.disposition).toBe('REJECTED')
    expect(mapped.contextInvalidation).toBeUndefined()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'CONTEXT_INVALIDATION_INVALID',
    }))
  })

  it('fails closed when CONTEXT_INVALIDATED carries non-empty blocks', () => {
    const response = stpV2Answer()
    response.blocks = [{ sourceScope: 'PORTFOLIO', content: 'leaked body', claimIds: [], evidenceIds: [] }]
    response.agentTurn = { contractVersion: 'stp-v2', disposition: 'CONTEXT_INVALIDATED' }
    response.contextInvalidation = {
      reasonCode: 'CONTEXT_RESULT_STALE',
      recoveryAction: 'RESTART_FROM_CURRENT_CONTENT',
      contextType: 'RECOMMENDATION',
      currentContentVersion: 'public-2026-08-13',
    }

    const mapped = mapAnswerResponse(response)
    expect(mapped.semanticTurn?.disposition).toBe('REJECTED')
    expect(mapped.contextInvalidation).toBeUndefined()
  })

  // ── sourceDomain 权威 + SYNTHESIS sourceScope fail-closed（handoff §5）──
  it('keeps sourceDomain when consistent with sourceScope', () => {
    const response = stpV2Answer()
    ;(response.agentTurn as never as { completedTasks: Array<{ resultPayload: { blocks: AnswerBlock[] } }> })
      .completedTasks[0].resultPayload.blocks[0]!.sourceDomain = 'PORTFOLIO'
    const block = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(block.kind).toBe('SECTION_RESULT')
    if (block.kind === 'SECTION_RESULT') expect(block.blocks[0]!.sourceDomain).toBe('PORTFOLIO')
  })

  it('drops sourceDomain (fail closed) when SYNTHESIS carries a non-null legacy scope', () => {
    const response = stpV2Answer()
    const block = (response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: { blocks: AnswerBlock[] } }>
    }).completedTasks[0]!.resultPayload.blocks[0]!
    block.sourceDomain = 'SYNTHESIS'
    block.sourceScope = 'GENERAL' // 违规：SYNTHESIS 不得伪装 GENERAL

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('SECTION_RESULT')
    if (result.kind === 'SECTION_RESULT') expect(result.blocks[0]!.sourceDomain).toBeUndefined()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'BLOCK_SOURCE_DOMAIN_INVALID',
    }))
  })

  it('keeps SYNTHESIS sourceDomain when legacy scope is omitted', () => {
    const response = stpV2Answer()
    ;(response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: { blocks: AnswerBlock[] } }>
    }).completedTasks[0]!.resultPayload.blocks[0] = {
      sourceDomain: 'SYNTHESIS',
      content: 'Synthesis body.',
      claimIds: [],
      evidenceIds: [],
    } as unknown as AnswerBlock

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('SECTION_RESULT')
    if (result.kind === 'SECTION_RESULT') expect(result.blocks[0]!.sourceDomain).toBe('SYNTHESIS')
  })

  it('maps block support with a closed support kind and drops invalid kinds', () => {
    const response = stpV2Answer()
    const block = (response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: { blocks: AnswerBlock[] } }>
    }).completedTasks[0]!.resultPayload.blocks[0]!
    block.sourceDomain = 'PORTFOLIO'
    block.support = {
      kind: 'VERIFIED_PUBLIC_EVIDENCE',
      statementReferences: [{ statementId: 'stmt-1', publicSourceKeys: ['src-a'] }],
      sourceTaskIds: ['01'],
      publicSourceKeys: ['src-a'],
    }

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('SECTION_RESULT')
    if (result.kind === 'SECTION_RESULT') {
      expect(result.blocks[0]!.support).toMatchObject({
        kind: 'VERIFIED_PUBLIC_EVIDENCE',
        publicSourceKeys: ['src-a'],
      })
    }

    // 非法 kind → support 丢弃（不报诊断，静默降级为未提供）。
    block.support = { ...block.support!, kind: 'BOGUS_KIND' as never }
    const result2 = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    if (result2.kind === 'SECTION_RESULT') expect(result2.blocks[0]!.support).toBeUndefined()
  })

  // ── 履约角色 / 支持聚合 / 续接句柄（§10.4/§9.4/§11.14）──
  it('maps fulfillmentRole on display plan and completed tasks', () => {
    const response = stpV2Answer()
    response.agentTurn!.plan = {
      taskCount: 1,
      tasks: [{
        displayIndex: '01', goalLabel: 'Goal one', sourceDomain: 'PORTFOLIO', fulfillmentRole: 'PRIMARY',
      }],
    }
    ;(response.agentTurn as never as { completedTasks: Array<{ fulfillmentRole?: string }> })
      .completedTasks[0]!.fulfillmentRole = 'SUPPORTING'

    const view = mapSemanticTurnResponse(response.agentTurn!)
    expect(view.displayPlan?.tasks[0]?.fulfillmentRole).toBe('PRIMARY')
    expect(view.completedTasks[0]?.fulfillmentRole).toBe('SUPPORTING')
  })

  it('maps task support summary and drops invalid ones with a diagnostic', () => {
    const response = stpV2Answer()
    const task = firstTask(response)
    task.supportSummary = {
      kind: 'DERIVED_FROM_TASKS', statementCount: 2, publicSourceCount: 3, sourceTaskCount: 2,
    }
    expect(mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]?.supportSummary)
      .toMatchObject({ kind: 'DERIVED_FROM_TASKS', sourceTaskCount: 2 })

    task.supportSummary = { kind: 'DERIVED_FROM_TASKS', statementCount: -1 }
    expect(mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]?.supportSummary).toBeUndefined()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'TASK_SUPPORT_SUMMARY_INVALID',
    }))
  })

  it('keeps continuationContext when it matches the legacy handle, fails closed on mismatch', () => {
    const response = stpV2Answer()
    const task = firstTask(response)
    task.contextHandle = 'handle-1'
    task.continuationContext = {
      contextHandle: 'handle-1', contextType: 'RECENT_SEMANTIC_TASK', sourceTaskId: '01',
    }
    const view = mapSemanticTurnResponse(response.agentTurn!)
    expect(view.completedTasks[0]?.contextHandle).toBe('handle-1')
    expect(view.completedTasks[0]?.continuationContext?.contextHandle).toBe('handle-1')

    task.continuationContext = {
      contextHandle: 'handle-different', contextType: 'RECENT_SEMANTIC_TASK', sourceTaskId: '01',
    }
    const view2 = mapSemanticTurnResponse(response.agentTurn!)
    expect(view2.completedTasks[0]?.continuationContext).toBeUndefined()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'CONTINUATION_CONTEXT_INVALID',
    }))
  })

  it('maps ordered result item identity on recommendation items', () => {
    const response = stpV2Answer()
    ;(response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: unknown }>
    }).completedTasks[0]!.resultPayload = {
      kind: 'RECOMMENDATION_RESULT',
      recommendations: [{
        portfolioId: 'project-1',
        title: 'Project one',
        route: '/projects/project-one',
        matchReasons: ['PostgreSQL'],
        evidenceIds: ['evidence-1'],
        resultItemId: 'item-2-opaque',
        position: 2,
        subject: { subjectType: 'PROJECT', subjectId: 'project-1' },
      }],
    }

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('RECOMMENDATION_RESULT')
    if (result.kind === 'RECOMMENDATION_RESULT') {
      expect(result.recommendations[0]).toMatchObject({
        resultItemId: 'item-2-opaque',
        position: 2,
        subject: { subjectType: 'PROJECT', subjectId: 'project-1' },
      })
    }
  })

  // ── 顶层字段（§9.5/§9.7/§9.9/§10.8/§13.14）──
  it('maps top-level sourceComposition, catalog (deduped), caveats and contextResolution', () => {
    const response = stpV2Answer()
    response.sourceComposition = 'CROSS_DOMAIN_DERIVED'
    response.publicSourceCatalog = [
      { referenceKey: 'src-a', label: 'Source A', sourceType: 'DOCUMENT', subjectRoute: '/projects/p', publishedVersion: 'v2' },
      { referenceKey: 'src-a', label: 'Source A dup', sourceType: 'DOCUMENT', subjectRoute: '/projects/p', publishedVersion: 'v2' },
      { referenceKey: 'bad', label: '', sourceType: 'DOCUMENT', subjectRoute: '/x', publishedVersion: 'v2' },
    ]
    response.caveats = [{
      code: 'ILLUSTRATES_NOT_FULL_IMPLEMENTATION',
      message: '体现不等于完整实现',
      appliesToBlockIds: ['block-3'],
      sourceTaskIds: ['03'],
    }]
    response.contextResolution = {
      mode: 'REVALIDATED_TO_CURRENT', contextType: 'RECENT_SEMANTIC_TASK', currentContentVersion: 'v2',
    }

    const mapped = mapAnswerResponse(response)
    expect(mapped.sourceComposition).toBe('CROSS_DOMAIN_DERIVED')
    expect(mapped.publicSourceCatalog).toEqual([
      { referenceKey: 'src-a', label: 'Source A', sourceType: 'DOCUMENT', subjectRoute: '/projects/p', publishedVersion: 'v2' },
    ])
    expect(mapped.caveats?.[0]?.code).toBe('ILLUSTRATES_NOT_FULL_IMPLEMENTATION')
    expect(mapped.contextResolution?.mode).toBe('REVALIDATED_TO_CURRENT')
  })

  it('maps degradation summary and lets it override the legacy degraded flag', () => {
    const response = stpV2Answer()
    response.degraded = false
    response.degradationSummary = {
      degraded: true,
      kinds: ['RETRIEVAL_FALLBACK', 'BOGUS_KIND'],
      affectedTaskIds: ['03'],
    } as never
    const mapped = mapAnswerResponse(response)
    expect(mapped.degraded).toBe(true)
    expect(mapped.degradationSummary?.kinds).toEqual(['RETRIEVAL_FALLBACK'])
    expect(mapped.degradationSummary?.affectedTaskIds).toEqual(['03'])
  })

  it('drops an unknown sourceComposition value (fail closed)', () => {
    const response = stpV2Answer()
    response.sourceComposition = 'NOT_A_REAL_COMPOSITION' as never
    expect(mapAnswerResponse(response).sourceComposition).toBeUndefined()
  })

  // ── fail-closed 边缘分支补全（handoff §2/§3/§5）──
  it('preserves the stp-v2 contract version when an unknown disposition fails closed', () => {
    const view = mapSemanticTurnResponse({
      contractVersion: 'stp-v2',
      disposition: 'SOME_FUTURE_DISPOSITION',
    } as never)
    expect(view.contractVersion).toBe('stp-v2')
    expect(view.disposition).toBe('REJECTED')
    expect(view.completedTasks).toEqual([])
  })

  it('drops sourceDomain (fail closed) when a non-SYNTHESIS domain conflicts with the legacy scope', () => {
    const response = stpV2Answer()
    const block = (response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: { blocks: AnswerBlock[] } }>
    }).completedTasks[0]!.resultPayload.blocks[0]!
    block.sourceDomain = 'PORTFOLIO'
    block.sourceScope = 'GENERAL' // 违规：声明 PORTFOLIO 域却携带 GENERAL scope

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('SECTION_RESULT')
    if (result.kind === 'SECTION_RESULT') expect(result.blocks[0]!.sourceDomain).toBeUndefined()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'BLOCK_SOURCE_DOMAIN_INVALID',
    }))
  })

  it('maps a SYNTHESIS result preserving origin domains and the SYNTHESIS block domain', () => {
    const response = stpV2Answer()
    ;(response.agentTurn as never as {
      completedTasks: Array<{ sourceDomain?: string; resultPayload: unknown }>
    }).completedTasks[0]!.sourceDomain = 'SYNTHESIS'
    ;(response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: unknown }>
    }).completedTasks[0]!.resultPayload = {
      kind: 'SYNTHESIS_RESULT',
      blocks: [{ sourceDomain: 'SYNTHESIS', content: '合成正文', claimIds: [], evidenceIds: [] }],
      originDomains: ['PORTFOLIO', 'GENERAL'],
    }

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('SYNTHESIS_RESULT')
    if (result.kind === 'SYNTHESIS_RESULT') {
      expect(result.originDomains).toEqual(['PORTFOLIO', 'GENERAL'])
      expect(result.blocks[0]?.sourceDomain).toBe('SYNTHESIS')
    }
  })

  it('drops ordered result item identity when resultItemId is absent', () => {
    const response = stpV2Answer()
    ;(response.agentTurn as never as {
      completedTasks: Array<{ resultPayload: unknown }>
    }).completedTasks[0]!.resultPayload = {
      kind: 'RECOMMENDATION_RESULT',
      recommendations: [{
        portfolioId: 'project-1',
        title: 'Project one',
        route: '/projects/project-one',
        matchReasons: ['PostgreSQL'],
        evidenceIds: ['evidence-1'],
        // resultItemId 缺失 → position/subject 一并丢弃
        position: 2,
        subject: { subjectType: 'PROJECT', subjectId: 'project-1' },
      }],
    }

    const result = mapSemanticTurnResponse(response.agentTurn!).completedTasks[0]!.resultPayload
    expect(result.kind).toBe('RECOMMENDATION_RESULT')
    if (result.kind === 'RECOMMENDATION_RESULT') {
      expect(result.recommendations[0].resultItemId).toBeUndefined()
      expect(result.recommendations[0].position).toBeUndefined()
      expect(result.recommendations[0].subject).toBeUndefined()
    }
  })

  it('keeps continuationContext alone when no legacy contextHandle is present', () => {
    const response = stpV2Answer()
    const task = firstTask(response)
    task.continuationContext = {
      contextHandle: 'handle-solo', contextType: 'RECOMMENDATION', sourceTaskId: '01',
    }
    const view = mapSemanticTurnResponse(response.agentTurn!)
    expect(view.completedTasks[0]?.contextHandle).toBeUndefined()
    expect(view.completedTasks[0]?.continuationContext).toEqual({
      contextHandle: 'handle-solo', contextType: 'RECOMMENDATION', sourceTaskId: '01',
    })
  })

  it('drops continuationContext with an unknown context type without reporting a diagnostic', () => {
    const response = stpV2Answer()
    const task = firstTask(response)
    task.continuationContext = {
      contextHandle: 'handle-x', contextType: 'UNKNOWN_CONTEXT', sourceTaskId: '01',
    }
    const view = mapSemanticTurnResponse(response.agentTurn!)
    expect(view.completedTasks[0]?.continuationContext).toBeUndefined()
    expect(reportSpy).not.toHaveBeenCalled()
  })

  it('drops catalog entries with non-relative routes (absolute or protocol-relative)', () => {
    const response = stpV2Answer()
    response.publicSourceCatalog = [
      { referenceKey: 'ok', label: 'Valid', sourceType: 'DOCUMENT', subjectRoute: '/projects/p', publishedVersion: 'v2' },
      { referenceKey: 'abs', label: 'Absolute', sourceType: 'DOCUMENT', subjectRoute: 'https://evil.example/p', publishedVersion: 'v2' },
      { referenceKey: 'rel', label: 'Protocol relative', sourceType: 'DOCUMENT', subjectRoute: '//evil.example/p', publishedVersion: 'v2' },
    ]
    const mapped = mapAnswerResponse(response)
    expect(mapped.publicSourceCatalog?.map((entry) => entry.referenceKey)).toEqual(['ok'])
  })

  it('drops an invalid degradation summary and falls back to the legacy degraded flag', () => {
    const response = stpV2Answer()
    response.degraded = true
    response.degradationSummary = { degraded: 'maybe', kinds: [], affectedTaskIds: [] } as never
    const mapped = mapAnswerResponse(response)
    expect(mapped.degradationSummary).toBeUndefined()
    // degradationSummary 缺失 → 退化到 response.degraded
    expect(mapped.degraded).toBe(true)
  })
})
