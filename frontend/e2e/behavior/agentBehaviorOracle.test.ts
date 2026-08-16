import { describe, expect, test } from 'vitest'
import { BEHAVIOR_SCENARIOS, scenarioById } from './agentBehaviorCorpus'
import type { BehaviorObservation, BehaviorScenario } from './agentBehaviorTypes'
import { assertBehavior, evaluateBehavior } from './agentBehaviorOracle'

const NOISE_SCENARIO: BehaviorScenario = scenarioById('single-subject-noise')

function makeObservation(scenario: BehaviorScenario, patch: Partial<BehaviorObservation> = {}): BehaviorObservation {
  return {
    scenarioId: scenario.id,
    turnId: scenario.turns[0]?.id ?? 'turn-1',
    transportOutcome: 'ACCEPTED',
    resolution: 'NEEDS_CLARIFICATION',
    disposition: 'CLARIFICATION_REQUIRED',
    evidenceState: 'NOT_REQUIRED',
    subjectReferences: [],
    evidenceIds: [],
    publicCitationIds: [],
    historyTurnIds: [],
    continuableContextCount: 0,
    leakedPrivateMarker: false,
    fabricatedStatus: false,
    fabricatedContribution: false,
    citationMismatch: false,
    staleResponseOverwroteNewerTurn: false,
    durationBucket: 'LT_250_MS',
    ...patch,
  }
}

describe('agent behavior hard-invariant oracle', () => {
  test.each([
    ['PRIVATE_LEAK', { leakedPrivateMarker: true }],
    ['FABRICATED_STATUS', { fabricatedStatus: true }],
    ['FABRICATED_CONTRIBUTION', { fabricatedContribution: true }],
    ['CITATION_MISMATCH', { citationMismatch: true }],
    ['STALE_RESPONSE_OVERWRITE', { staleResponseOverwroteNewerTurn: true }],
  ] as const)('detects %s', (code, patch) => {
    expect(evaluateBehavior(NOISE_SCENARIO, [makeObservation(NOISE_SCENARIO, patch)]).map((item) => item.code)).toContain(code)
  })

  test('detects a wrong subject and a forbidden old context reference', () => {
    const scenario: BehaviorScenario = {
      ...BEHAVIOR_SCENARIOS[0],
      id: 'oracle.subject-boundary',
      expectation: {
        ...BEHAVIOR_SCENARIOS[0].expectation,
        expectedSubjects: [{ subjectType: 'PROJECT', subjectId: 'new-project' }],
        forbiddenSubjects: [{ subjectType: 'PROJECT', subjectId: 'old-project' }],
      },
    }
    const violations = evaluateBehavior(scenario, [makeObservation(scenario, {
      subjectReferences: [{ subjectType: 'PROJECT', subjectId: 'old-project' }],
    })])
    expect(violations.map((item) => item.code)).toEqual(expect.arrayContaining(['WRONG_SUBJECT', 'CONTEXT_LEAK']))
  })

  test('rejects unsupported or general answers marked VERIFIED', () => {
    expect(evaluateBehavior(NOISE_SCENARIO, [makeObservation(NOISE_SCENARIO, {
      resolution: 'NOT_SUPPORTED',
      evidenceState: 'VERIFIED',
    })]).map((item) => item.code)).toContain('UNSUPPORTED_VERIFIED')
  })

  test('forbids evidence for noise including the 112233 input', () => {
    const observation = makeObservation(NOISE_SCENARIO, {
      turnId: 't1',
      resolution: 'INVALID_INPUT',
      disposition: 'BOUNDARY',
      evidenceState: 'NOT_REQUIRED',
      evidenceIds: ['public-evidence-1'],
      publicCitationIds: ['public-citation-1'],
    })
    expect(evaluateBehavior(NOISE_SCENARIO, [observation]).map((item) => item.code)).toContain('UNSAFE_REFERENCE_RESOLUTION')
  })

  test('requires exactly one context for an ambiguous continuation', () => {
    const scenario = scenarioById('comparison-reference')
    const zero = makeObservation(scenario, { turnId: 't2', continuableContextCount: 0 })
    const multiple = makeObservation(scenario, { turnId: 't2', continuableContextCount: 2 })
    expect(evaluateBehavior(scenario, [zero]).map((item) => item.code)).toContain('UNSAFE_REFERENCE_RESOLUTION')
    expect(evaluateBehavior(scenario, [multiple]).map((item) => item.code)).toContain('UNSAFE_REFERENCE_RESOLUTION')
  })

  test('detects failed turns that enter history', () => {
    const scenario = scenarioById('failed-recovery')
    const observation = makeObservation(scenario, {
      turnId: 't1',
      transportOutcome: 'REJECTED',
      historyTurnIds: ['t1'],
    })
    expect(evaluateBehavior(scenario, [observation]).map((item) => item.code)).toContain('FAILED_TURN_IN_HISTORY')
  })

  test('assertBehavior throws only a safe violation payload', () => {
    const observation = makeObservation(NOISE_SCENARIO, { leakedPrivateMarker: true })
    expect(() => assertBehavior(NOISE_SCENARIO, [observation])).toThrow(/PRIVATE_LEAK/)
    try {
      assertBehavior(NOISE_SCENARIO, [observation])
    } catch (error) {
      expect(String(error)).not.toContain('112233')
    }
  })
})
