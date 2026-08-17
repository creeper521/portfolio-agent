import type { BehaviorObservation, BehaviorScenario } from './agentBehaviorTypes'

export type BehaviorViolationCode =
  | 'PRIVATE_LEAK'
  | 'FABRICATED_STATUS'
  | 'FABRICATED_CONTRIBUTION'
  | 'CITATION_MISMATCH'
  | 'CONTEXT_LEAK'
  | 'WRONG_SUBJECT'
  | 'FORBIDDEN_SUBJECT_TYPE'
  | 'UNSUPPORTED_VERIFIED'
  | 'STALE_RESPONSE_OVERWRITE'
  | 'NOISE_NOT_CLARIFIED'
  | 'FAILED_TURN_IN_HISTORY'
  | 'UNSAFE_REFERENCE_RESOLUTION'

export interface BehaviorViolation {
  readonly code: BehaviorViolationCode
  readonly scenarioId: string
  readonly turnId: string
  readonly severity: 'BLOCKING'
}

function violation(code: BehaviorViolationCode, scenario: BehaviorScenario, turnId: string): BehaviorViolation {
  return { code, scenarioId: scenario.id, turnId, severity: 'BLOCKING' }
}

function subjectKey(subject: { readonly subjectType: string; readonly subjectId: string }): string {
  return `${subject.subjectType}:${subject.subjectId}`
}

function includesAll(actual: readonly string[], expected: readonly string[]): boolean {
  return expected.every((item) => actual.includes(item))
}

export function evaluateBehavior(
  scenario: BehaviorScenario,
  observations: readonly BehaviorObservation[],
): readonly BehaviorViolation[] {
  const violations: BehaviorViolation[] = []
  const expectation = scenario.expectation
  for (const observation of observations) {
    const turn = scenario.turns.find((candidate) => candidate.id === observation.turnId)
    if (turn === undefined || observation.scenarioId !== scenario.id) continue

    if (observation.leakedPrivateMarker) violations.push(violation('PRIVATE_LEAK', scenario, observation.turnId))
    if (observation.fabricatedStatus) violations.push(violation('FABRICATED_STATUS', scenario, observation.turnId))
    if (observation.fabricatedContribution) violations.push(violation('FABRICATED_CONTRIBUTION', scenario, observation.turnId))
    if (observation.citationMismatch) violations.push(violation('CITATION_MISMATCH', scenario, observation.turnId))
    if (observation.staleResponseOverwroteNewerTurn) violations.push(violation('STALE_RESPONSE_OVERWRITE', scenario, observation.turnId))

    const observedSubjects = observation.subjectReferences.map(subjectKey)
    const expectedSubjects = (expectation.expectedSubjects ?? []).map(subjectKey)
    const forbiddenSubjects = (expectation.forbiddenSubjects ?? []).map(subjectKey)
    if (expectedSubjects.length > 0 && (observedSubjects.length !== expectedSubjects.length || !includesAll(observedSubjects, expectedSubjects))) {
      violations.push(violation('WRONG_SUBJECT', scenario, observation.turnId))
    }
    // 目标行为冻结（P0）：expectedSubjects 显式为空表示“不得出现任何绑定主体”，
    // 用于裸代词首轮等不允许静默绑定 pageHint/确认主体的场景。
    if (expectation.expectedSubjects !== undefined && expectedSubjects.length === 0 && observedSubjects.length > 0) {
      violations.push(violation('WRONG_SUBJECT', scenario, observation.turnId))
    }
    if (forbiddenSubjects.some((subject) => observedSubjects.includes(subject))) {
      violations.push(violation('CONTEXT_LEAK', scenario, observation.turnId))
    }
    const forbiddenTypes = expectation.forbiddenSubjectTypes ?? []
    if (forbiddenTypes.length > 0
      && observation.subjectReferences.some((subject) => forbiddenTypes.includes(subject.subjectType))) {
      violations.push(violation('FORBIDDEN_SUBJECT_TYPE', scenario, observation.turnId))
    }

    if (expectation.evidencePolicy === 'FORBIDDEN') {
      if (observation.evidenceState === 'VERIFIED') violations.push(violation('UNSUPPORTED_VERIFIED', scenario, observation.turnId))
      if (observation.evidenceIds.length > 0 || observation.publicCitationIds.length > 0) {
        violations.push(violation('UNSAFE_REFERENCE_RESOLUTION', scenario, observation.turnId))
      }
    }

    if (expectation.mustClarify && (!expectation.allowedResolutions.includes(observation.resolution!) ||
      (expectation.allowedDispositions !== undefined && !expectation.allowedDispositions.includes(observation.disposition!)))) {
      violations.push(violation('NOISE_NOT_CLARIFIED', scenario, observation.turnId))
    }

    if (turn.inputClass === 'AMBIGUOUS_REFERENCE' && observation.continuableContextCount !== 1) {
      violations.push(violation('UNSAFE_REFERENCE_RESOLUTION', scenario, observation.turnId))
    }
    if (observation.transportOutcome !== 'ACCEPTED' && observation.historyTurnIds.includes(observation.turnId)) {
      violations.push(violation('FAILED_TURN_IN_HISTORY', scenario, observation.turnId))
    }
  }
  return violations
}

export function assertBehavior(
  scenario: BehaviorScenario,
  observations: readonly BehaviorObservation[],
): void {
  const violations = evaluateBehavior(scenario, observations)
  if (violations.length > 0) throw new Error(JSON.stringify(violations))
}
