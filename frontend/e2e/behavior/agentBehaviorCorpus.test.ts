import { describe, expect, test } from 'vitest'
import { BEHAVIOR_SCENARIOS, REQUIRED_CONTEXT_STATES, V4_TARGET_SCENARIO_IDS, expandActivePresetScenarios, scenarioById } from './agentBehaviorCorpus'
import type { BehaviorInputClass } from './agentBehaviorTypes'

describe('agent behavior corpus', () => {
  test('has unique IDs and covers every required context state', () => {
    const ids = BEHAVIOR_SCENARIOS.map((scenario) => scenario.id)
    expect(new Set(ids).size).toBe(ids.length)
    for (const state of REQUIRED_CONTEXT_STATES) expect(BEHAVIOR_SCENARIOS.some((scenario) => scenario.initialState === state)).toBe(true)
  })
  test('keeps live provider scenarios isolated', () => {
    expect(BEHAVIOR_SCENARIOS.filter((scenario) => scenario.lane === 'L4_LIVE_PROVIDER').every((scenario) => scenario.requiresExplicitAuthorization)).toBe(true)
  })
  test('covers every input class and important transport outcome', () => {
    const classes = new Set(BEHAVIOR_SCENARIOS.flatMap((scenario) => scenario.turns.map((turn) => turn.inputClass)))
    const required: BehaviorInputClass[] = ['ACTIVE_PRESET','PRESET_VARIANT','NOISE','AMBIGUOUS_REFERENCE','BARE_PRONOUN','RECOMMENDATION_ASK','CONTEXT_SWITCH','SECURITY_BOUNDARY','UNKNOWN_SUBJECT','MALFORMED_BOUNDARY','MULTI_TASK_CONTRADICTION','FAILURE_RECOVERY']
    for (const inputClass of required) expect(classes.has(inputClass)).toBe(true)
    const outcomes = new Set(BEHAVIOR_SCENARIOS.flatMap((scenario) => scenario.turns.map((turn) => turn.transportOutcome).filter(Boolean)))
    for (const outcome of ['REJECTED','TIMED_OUT','UNAVAILABLE','CANCELLED']) expect(outcomes.has(outcome)).toBe(true)
  })
  test('freezes v4 target behaviors: bare pronouns clarify without silent binding; recommendations stay project-only', () => {
    // P0 目标行为冻结（对当前实现为预期失败，待 P4–P7 交付后转绿）：
    // 首轮裸代词只允许澄清，不得静默绑定 pageHint 主体，也不得携带 Evidence。
    expect(V4_TARGET_SCENARIO_IDS.every((id) => BEHAVIOR_SCENARIOS.some((scenario) => scenario.id === id))).toBe(true)
    for (const id of ['project-hint-bare-pronoun', 'case-hint-bare-pronoun']) {
      const scenario = scenarioById(id)
      expect(scenario.expectation.mustClarify).toBe(true)
      expect(scenario.expectation.evidencePolicy).toBe('FORBIDDEN')
      expect(scenario.expectation.expectedSubjects).toEqual([])
      expect(scenario.turns.every((turn) => turn.inputClass === 'BARE_PRONOUN')).toBe(true)
    }
    // 首版推荐目标行为：结果主体域只允许 Project，不得混入 Case。
    const recommendation = scenarioById('project-only-recommendation')
    expect(recommendation.expectation.allowedResolutions).toContain('ANSWERED')
    expect(recommendation.expectation.evidencePolicy).toBe('REQUIRED_PUBLIC')
    expect(recommendation.expectation.forbiddenSubjectTypes).toEqual(['CASE'])
  })
  test('protects noise, boundary, and failed turns from evidence and history', () => {
    for (const scenario of BEHAVIOR_SCENARIOS.filter((candidate) => candidate.id.includes('noise') || candidate.id === 'null-blank-boundary' || candidate.id === 'unicode-boundaries')) {
      expect(scenario.expectation.mustClarify).toBe(true)
      expect(scenario.expectation.evidencePolicy).toBe('FORBIDDEN')
      expect(scenario.expectation.expectedSubjects ?? []).toHaveLength(0)
    }
    for (const scenario of BEHAVIOR_SCENARIOS) for (const turn of scenario.turns.filter((candidate) => candidate.transportOutcome && candidate.transportOutcome !== 'ACCEPTED')) expect(scenario.expectation.mustNotEnterHistory).toBe(true)
  })
  test('contains Unicode boundaries and an explicit out-of-order response sequence', () => {
    const unicode = scenarioById('unicode-boundaries')
    expect(unicode.turns.find((turn) => turn.id === '1999')?.input).toHaveLength(1999)
    expect(unicode.turns.find((turn) => turn.id === '2000')?.input).toHaveLength(2000)
    expect(unicode.turns.find((turn) => turn.id === '2001')?.input).toHaveLength(2001)
    expect(unicode.turns.find((turn) => turn.id === 'surrogate')?.input).toBe('\uD800')
    expect(scenarioById('out-of-order').responseOrder).toEqual(['t2', 't1'])
  })
  test('dynamically expands all 18 active presets and resolves their IDs', () => {
    const presets = Array.from({ length: 18 }, (_, index) => ({ id: `synthetic-preset-${index}`, projectSlug: null, caseSlugs: [], text: `合成预设 ${index}`, audiences: ['GUEST'] as ['GUEST'], placements: ['AGENT'] as ['AGENT'], contractVersion: 'synthetic-v1', availability: 'ACTIVE' as const }))
    const expanded = expandActivePresetScenarios(presets)
    expect(expanded).toHaveLength(18)
    expect(new Set(expanded.map((scenario) => scenario.id)).size).toBe(18)
    for (const preset of presets) expect(scenarioById(`active-preset:${preset.id}`, expanded).turns[0]?.input).toBe(preset.text)
  })
  test('rejects unknown IDs and keeps corpus synthetic', () => {
    expect(() => scenarioById('misspelled-scenario')).toThrow('Unknown behavior scenario: misspelled-scenario')
    expect(JSON.stringify(BEHAVIOR_SCENARIOS)).not.toMatch(/[A-Za-z]:\\Users\\|sk-[A-Za-z0-9]{10,}|-----BEGIN [A-Z ]+-----/)
  })
})
