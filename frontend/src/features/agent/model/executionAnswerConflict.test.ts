import { describe, expect, it } from 'vitest'

import { hasExecutionAnswerConflict } from './semanticTurnView'
import type { ExecutionDisplayPlanView } from './semanticTurnView'

function planWithStages(statuses: Array<'COMPLETED' | 'PARTIAL' | 'SKIPPED' | 'FAILED'>): ExecutionDisplayPlanView {
  const allFailed = statuses.every((status) => status === 'FAILED')
  return {
    overallStatus: allFailed ? 'FAILED' : 'COMPLETED',
    tasks: [{
      displayIndex: '01',
      finalStatus: allFailed ? 'FAILED' : 'COMPLETED',
      stages: statuses.map((status, index) => ({
        code: (['SCOPE_CONFIRMED', 'MATERIALS_RETRIEVED', 'EVIDENCE_VALIDATED', 'RESULT_COMPOSED'] as const)[index % 4]!,
        label: `阶段 ${index}`,
        status,
      })),
    }],
  }
}

describe('hasExecutionAnswerConflict', () => {
  it('returns true when answer claims success but every FINAL stage failed', () => {
    expect(hasExecutionAnswerConflict({
      resolution: 'ANSWERED',
      evidenceState: 'VERIFIED',
      semanticTurn: { execution: planWithStages(['FAILED', 'FAILED', 'FAILED', 'FAILED']) },
    })).toBe(true)
  })

  it('returns false when there is no execution snapshot', () => {
    expect(hasExecutionAnswerConflict({
      resolution: 'ANSWERED',
      evidenceState: 'VERIFIED',
    })).toBe(false)
  })

  it('returns false when answer is not ANSWERED', () => {
    expect(hasExecutionAnswerConflict({
      resolution: 'NOT_SUPPORTED',
      evidenceState: 'INSUFFICIENT',
      semanticTurn: { execution: planWithStages(['FAILED', 'FAILED']) },
    })).toBe(false)
  })

  it('returns false when evidence is not VERIFIED', () => {
    expect(hasExecutionAnswerConflict({
      resolution: 'ANSWERED',
      evidenceState: 'INSUFFICIENT',
      semanticTurn: { execution: planWithStages(['FAILED', 'FAILED']) },
    })).toBe(false)
  })

  it('returns false when at least one stage is not FAILED', () => {
    expect(hasExecutionAnswerConflict({
      resolution: 'ANSWERED',
      evidenceState: 'VERIFIED',
      semanticTurn: { execution: {
        overallStatus: 'PARTIAL',
        tasks: [{
          displayIndex: '01',
          finalStatus: 'PARTIAL',
          stages: [
            { code: 'SCOPE_CONFIRMED', label: 'a', status: 'COMPLETED' },
            { code: 'RESULT_COMPOSED', label: 'b', status: 'FAILED' },
          ],
        }],
      } },
    })).toBe(false)
  })

  it('returns false when the snapshot has no stages at all', () => {
    expect(hasExecutionAnswerConflict({
      resolution: 'ANSWERED',
      evidenceState: 'VERIFIED',
      semanticTurn: { execution: { overallStatus: 'FAILED', tasks: [] } },
    })).toBe(false)
  })
})
