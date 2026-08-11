import { describe, expect, it } from 'vitest'

import { mapAnswerResponse } from './mapAnswerResponse'
import { mapSemanticTurnResponse } from './semanticTurnView'
import {
  confirmationRequiredResponse,
  legacyOnlyResponse,
  localPartialReadyResponse,
  partialSuccessResponse,
} from './semanticTurnFixtures'

describe('semantic turn response mapping', () => {
  it('maps agentTurn before legacy answer fields', () => {
    const mapped = mapAnswerResponse(partialSuccessResponse())

    expect(mapped.semanticTurn?.displayPlan?.tasks[0]?.goalLabel)
      .toBe('Review the SQL project')
    expect(mapped.semanticTurn?.taskSummary?.displayMode).toBe('EXPANDED')
  })

  it('derives rendered sections from completed semantic tasks instead of legacy blocks', () => {
    const mapped = mapAnswerResponse(partialSuccessResponse())

    expect(mapped.sections).toEqual([expect.objectContaining({
      title: 'SQL project review',
      content: 'Only completed-task content appears here.',
    })])
    expect(mapped.sections.map((section) => section.content))
      .not.toContain('Legacy fallback must not define the semantic rendering.')
  })

  it('maps completed tasks without exposing internal graph fields', () => {
    const view = mapSemanticTurnResponse(partialSuccessResponse().agentTurn!)

    expect(view.completedTasks).toHaveLength(1)
    expect(view.taskSummary?.displayMode).toBe('EXPANDED')
    expect(JSON.stringify(view)).not.toContain('REQUIRES_SUCCESS')
    expect(JSON.stringify(view)).not.toContain('task-01')
    expect(JSON.stringify(view)).not.toContain('modelConfidence')
  })

  it('keeps the legacy answer mapping when agentTurn is absent', () => {
    const mapped = mapAnswerResponse(legacyOnlyResponse())

    expect(mapped.semanticTurn).toBeUndefined()
    expect(mapped.sections[0]?.content).toBe('Legacy content')
  })

  it('keeps confirmation transport data out of the display view', () => {
    const view = mapSemanticTurnResponse(confirmationRequiredResponse().agentTurn!)

    expect(JSON.stringify(view)).not.toContain('opaque-envelope')
    expect(JSON.stringify(view)).not.toContain('opaque-integrity-token')
  })

  it('maps a PARTIAL_READY response with LOCAL clarification and safe completed work', () => {
    const view = mapSemanticTurnResponse(localPartialReadyResponse().agentTurn!)

    expect(view.disposition).toBe('PARTIAL_READY')
    expect(view.clarification?.scope).toBe('LOCAL')
    expect(view.completedTasks).toHaveLength(1)
  })

  it('keeps the invalidated plan reference opaque to the display view', () => {
    const response = partialSuccessResponse()
    response.agentTurn = {
      contractVersion: 'stp-v1',
      disposition: 'REJECTED',
      planChange: {
        summary: '内容已更新',
        changeLabels: ['版本变化'],
        invalidatedPlanReference: { planId: 'plan-opaque', planFingerprint: 'sha256:opaque' },
      },
    }

    const view = mapSemanticTurnResponse(response.agentTurn)
    expect(view.planChange?.invalidatedPlanReference).toEqual({
      planId: 'plan-opaque',
      planFingerprint: 'sha256:opaque',
    })
  })

  it('does not project a legacy recommendation when agentTurn is authoritative', () => {
    const legacy = legacyOnlyResponse()
    legacy.portfolioRecommendation = recommendation()
    expect(mapAnswerResponse(legacy).portfolioRecommendation).toBeDefined()

    const semantic = partialSuccessResponse()
    semantic.portfolioRecommendation = recommendation()
    expect(mapAnswerResponse(semantic).portfolioRecommendation).toBeUndefined()
  })

  it('fails closed for an unknown raw disposition', () => {
    const response = partialSuccessResponse()
    response.agentTurn = {
      contractVersion: 'stp-v1',
      disposition: 'UNKNOWN_DISPOSITION',
    } as never

    expect(mapAnswerResponse(response).semanticTurn?.disposition).toBe('REJECTED')
  })
})

function recommendation() {
  return {
    recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    context: {
      recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      contentVersion: 'public-2026-08-10',
      careerTrack: null,
      audienceRole: 'INTERVIEWER',
      capabilityCodes: ['POSTGRESQL'],
      requestedSize: 2,
      selectedPortfolioIds: ['project-1', 'case-2'],
    },
    items: [
      {
        portfolioId: 'project-1',
        title: 'Project one',
        route: '/projects/project-one',
        matchReasons: ['PostgreSQL'],
        evidenceIds: ['evidence-1'],
      },
      {
        portfolioId: 'case-2',
        title: 'Case two',
        route: '/cases/case-two',
        matchReasons: ['Verification'],
        evidenceIds: ['evidence-2'],
      },
    ],
    satisfiedConstraints: ['backend'],
    unsatisfiedConstraints: [],
  }
}
