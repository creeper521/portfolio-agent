import { describe, expect, test } from 'vitest'
import { BEHAVIOR_SCENARIOS, REQUIRED_CONTEXT_STATES, expandActivePresetScenarios, scenarioById } from './agentBehaviorCorpus'
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
    const required: BehaviorInputClass[] = ['ACTIVE_PRESET','PRESET_VARIANT','NOISE','AMBIGUOUS_REFERENCE','CONTEXT_SWITCH','SECURITY_BOUNDARY','UNKNOWN_SUBJECT','MALFORMED_BOUNDARY','MULTI_TASK_CONTRADICTION','FAILURE_RECOVERY']
    for (const inputClass of required) expect(classes.has(inputClass)).toBe(true)
    const outcomes = new Set(BEHAVIOR_SCENARIOS.flatMap((scenario) => scenario.turns.map((turn) => turn.transportOutcome).filter(Boolean)))
    for (const outcome of ['REJECTED','TIMED_OUT','UNAVAILABLE','CANCELLED']) expect(outcomes.has(outcome)).toBe(true)
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
