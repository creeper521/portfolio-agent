import { describe, expect, it } from 'vitest'

import {
  loadPublicAgentTurnGoldenFixtures,
  type GoldenFixture,
} from './publicAgentTurnFixtureLoader'
import { parsePublicAgentTurn } from './publicAgentTurnMapper'
import type { PublicAgentTurn } from './publicAgentTurn'

// D-38 冻结合同 mapper 测试：正向用例全部由共享 Golden Fixtures 驱动；
// 负向用例只对 fixture 克隆做变异，不自造 Mock 字段。

const fixtures: readonly GoldenFixture[] = loadPublicAgentTurnGoldenFixtures()

function fixtureByName(fileName: string): GoldenFixture {
  const fixture = fixtures.find((candidate) => candidate.fileName === fileName)
  if (fixture === undefined) {
    throw new Error(`缺少 fixture ${fileName}`)
  }
  return fixture
}

function parseFixture(fileName: string): PublicAgentTurn {
  const parsed = parsePublicAgentTurn(fixtureByName(fileName).turn)
  if (!parsed.ok) {
    throw new Error(`${fileName} 应能解析：${parsed.error.violations.join('；')}`)
  }
  return parsed.turn
}

function mutate(
  fileName: string,
  mutator: (turn: Record<string, unknown>) => void,
): unknown {
  const clone = JSON.parse(JSON.stringify(fixtureByName(fileName).turn)) as Record<string, unknown>
  mutator(clone)
  return clone
}

function expectInvalid(value: unknown, ...markers: string[]): void {
  const parsed = parsePublicAgentTurn(value)
  expect(parsed.ok).toBe(false)
  if (!parsed.ok) {
    expect(parsed.error.reason).toBe('CONTRACT_INVALID')
    const joined = parsed.error.violations.join('\n')
    for (const marker of markers) {
      expect(joined).toContain(marker)
    }
  }
}

function asRecord(value: unknown): Record<string, unknown> {
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  throw new Error('期望 JSON 对象')
}

function asArray(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value
  }
  throw new Error('期望 JSON 数组')
}

describe('parsePublicAgentTurn：共享 Golden Fixtures 正向解析', () => {
  it('answer-complete：COMPLETE、单 FULL Goal、SECTIONED、来源目录与续接动作', () => {
    const turn = parseFixture('answer-complete.json')
    expect(turn.kind).toBe('ANSWER')
    if (turn.kind !== 'ANSWER') {
      return
    }
    expect(turn.answer.resolution).toBe('COMPLETE')
    expect(turn.answer.contentReleaseId).toBe('2026-08-05.1')
    expect(turn.answer.goalResults).toHaveLength(1)
    const goal = turn.answer.goalResults[0]
    expect(goal.coverage).toBe('FULL')
    expect(goal.notices).toEqual([])
    if (goal.presentation === undefined || goal.presentation.kind !== 'SECTIONED') {
      throw new Error('期望 SECTIONED presentation')
    }
    expect(goal.presentation.sections).toHaveLength(2)
    expect(goal.presentation.sections[0].support.kind).toBe('VERIFIED_PUBLIC_EVIDENCE')
    expect(goal.presentation.sections[0].support.publicSourceKeys).toEqual(['source-sql-audit'])
    expect(turn.answer.sourceCatalog.sources).toHaveLength(1)
    expect(turn.answer.sourceCatalog.sources[0].code).toBe('E-01')
    expect(turn.answer.sourceCatalog.sources[0].route).toBe('/evidence')
    expect(turn.answer.sourceComposition).toEqual(['VERIFIED_PUBLIC_EVIDENCE'])
    expect(goal.continuation?.contextHandle).toBe('ctx_fixture_overview')
    const action = turn.answer.suggestedActions?.[0]
    expect(action?.actionId).toBe('continue-verification')
    expect(action?.continuation?.contextHandle).toBe('ctx_fixture_overview')
  })

  it('answer-partial：PARTIAL、FULL+NONE 多 Goal、缺口 notice 挂在对应 Goal', () => {
    const turn = parseFixture('answer-partial.json')
    if (turn.kind !== 'ANSWER') {
      throw new Error('期望 ANSWER')
    }
    expect(turn.answer.resolution).toBe('PARTIAL')
    expect(turn.answer.goalResults).toHaveLength(2)
    expect(turn.answer.goalResults[0].coverage).toBe('FULL')
    const noneGoal = turn.answer.goalResults[1]
    expect(noneGoal.coverage).toBe('NONE')
    expect(noneGoal.presentation).toBeUndefined()
    expect(noneGoal.notices.map((notice) => notice.code)).toEqual(['OUT_OF_SCOPE'])
    expect(turn.answer.sourceComposition).toEqual(['GENERAL_KNOWLEDGE'])
  })

  it('answer-no-result：NO_RESULT、无 Presentation、空来源', () => {
    const turn = parseFixture('answer-no-result.json')
    if (turn.kind !== 'ANSWER') {
      throw new Error('期望 ANSWER')
    }
    expect(turn.answer.resolution).toBe('NO_RESULT')
    expect(turn.answer.goalResults[0].coverage).toBe('NONE')
    expect(turn.answer.goalResults[0].presentation).toBeUndefined()
    expect(turn.answer.goalResults[0].notices.map((notice) => notice.code)).toEqual([
      'NO_SUPPORTED_EVIDENCE',
    ])
    expect(turn.answer.sourceCatalog.sources).toEqual([])
    expect(turn.answer.sourceComposition).toEqual([])
  })

  it('answer-local-clarification：局部澄清引用同 answer 的受影响 Goal', () => {
    const turn = parseFixture('answer-local-clarification.json')
    if (turn.kind !== 'ANSWER') {
      throw new Error('期望 ANSWER')
    }
    const local = turn.answer.localClarification
    expect(local?.affectedGoalIds).toEqual(['goal-ambiguous-project'])
    const field = local?.fields[0]
    if (field?.kind !== 'SINGLE_CHOICE') {
      throw new Error('期望 SINGLE_CHOICE 字段')
    }
    expect(field.choices).toHaveLength(2)
  })

  it('clarification：独立澄清 Turn 无 answer，challenge 字段闭合', () => {
    const turn = parseFixture('clarification.json')
    if (turn.kind !== 'CLARIFICATION') {
      throw new Error('期望 CLARIFICATION')
    }
    expect('answer' in turn).toBe(false)
    expect(turn.clarification.clarificationId).toBe('clarification_fixture_critical')
    expect(turn.clarification.fields[0].kind).toBe('SINGLE_CHOICE')
  })

  it('conversational / boundary / capability-unavailable：message、code 与 retryable', () => {
    const conversational = parseFixture('conversational.json')
    if (conversational.kind !== 'CONVERSATIONAL') {
      throw new Error('期望 CONVERSATIONAL')
    }
    expect(conversational.suggestedActions?.[0].continuation).toBeUndefined()

    const boundary = parseFixture('boundary.json')
    if (boundary.kind !== 'BOUNDARY') {
      throw new Error('期望 BOUNDARY')
    }
    expect(boundary.code).toBe('HIGH_RISK_ADVICE_OUT_OF_SCOPE')

    const capability = parseFixture('capability-unavailable.json')
    if (capability.kind !== 'CAPABILITY_UNAVAILABLE') {
      throw new Error('期望 CAPABILITY_UNAVAILABLE')
    }
    expect(capability.code).toBe('GOAL_INTERPRETATION_UNAVAILABLE')
    expect(capability.retryable).toBe(true)
  })
})

describe('parsePublicAgentTurn：合同破损 fail-closed', () => {
  it('根节点不是对象、kind 未知、requestId 非 UUID 均拒绝', () => {
    expectInvalid(null)
    expectInvalid('ANSWER')
    expectInvalid(mutate('conversational.json', (turn) => {
      turn.kind = 'CONFIRMATION'
    }), 'kind 必须是')
    expectInvalid(mutate('conversational.json', (turn) => {
      turn.requestId = 'not-a-uuid'
    }), 'requestId 必须是 UUID')
  })

  it('ANSWER 缺 answer、非 ANSWER 携带 answer 均拒绝', () => {
    expectInvalid(mutate('answer-complete.json', (turn) => {
      delete turn.answer
    }), 'ANSWER 变体必须携带 answer')
    expectInvalid(mutate('conversational.json', (turn) => {
      turn.answer = { resolution: 'COMPLETE' }
    }), '非 ANSWER 变体不得携带 answer')
  })

  it('闭合枚举外的 resolution / coverage / support 拒绝', () => {
    expectInvalid(mutate('answer-complete.json', (turn) => {
      asRecord(turn.answer).resolution = 'PARTIALLY_ANSWERED'
    }), 'resolution 必须是')
    expectInvalid(mutate('answer-complete.json', (turn) => {
      asRecord(asArray(asRecord(turn.answer).goalResults)[0]).coverage = 'MOSTLY'
    }), 'coverage 必须是')
    expectInvalid(mutate('answer-complete.json', (turn) => {
      const goal = asRecord(asArray(asRecord(turn.answer).goalResults)[0])
      const section = asRecord(asArray(asRecord(goal.presentation).sections)[0])
      asRecord(section.support).kind = 'MODEL_GENERATED'
    }), 'kind 必须是')
  })

  it('FULL/PARTIAL/NONE 与 resolution 不变量破坏时拒绝', () => {
    expectInvalid(mutate('answer-complete.json', (turn) => {
      delete asRecord(asArray(asRecord(turn.answer).goalResults)[0]).presentation
    }), 'FULL Goal 必须携带 presentation')
    expectInvalid(mutate('answer-partial.json', (turn) => {
      asRecord(turn.answer).resolution = 'COMPLETE'
    }), 'COMPLETE 要求全部 Goal 为 FULL')
    expectInvalid(mutate('answer-partial.json', (turn) => {
      const goals = asArray(asRecord(turn.answer).goalResults)
      asRecord(goals[1]).presentation = asRecord(asRecord(goals[0]).presentation)
    }), 'NONE Goal 不得携带 presentation')
    expectInvalid(mutate('answer-partial.json', (turn) => {
      asRecord(asArray(asRecord(turn.answer).goalResults)[0]).coverage = 'PARTIAL'
    }), 'PARTIAL Goal 至少需要一个覆盖缺口 notice')
    expectInvalid(mutate('answer-no-result.json', (turn) => {
      asRecord(turn.answer).resolution = 'PARTIAL'
    }), 'PARTIAL 要求至少一个 Goal 有产出')
    expectInvalid(mutate('answer-complete.json', (turn) => {
      asRecord(turn.answer).resolution = 'NO_RESULT'
    }), 'NO_RESULT 要求全部 Goal 为 NONE')
  })

  it('来源引用无法在唯一 SourceCatalog 解析时拒绝', () => {
    expectInvalid(mutate('answer-complete.json', (turn) => {
      const goal = asRecord(asArray(asRecord(turn.answer).goalResults)[0])
      const section = asRecord(asArray(asRecord(goal.presentation).sections)[0])
      asRecord(section.support).publicSourceKeys = ['source-missing']
    }), '无法在 answer.sourceCatalog 中解析')
  })

  it('goalId / source key 重复与非法 route 拒绝', () => {
    expectInvalid(mutate('answer-partial.json', (turn) => {
      const goals = asArray(asRecord(turn.answer).goalResults)
      asRecord(goals[1]).goalId = asRecord(goals[0]).goalId
    }), 'goalId', '重复')
    expectInvalid(mutate('answer-complete.json', (turn) => {
      const sources = asArray(asRecord(asRecord(turn.answer).sourceCatalog).sources)
      const first = asRecord(sources[0])
      const second = JSON.parse(JSON.stringify(first)) as Record<string, unknown>
      second.label = '重复来源'
      sources.push(second)
    }), 'key', '重复')
    expectInvalid(mutate('answer-complete.json', (turn) => {
      const sources = asArray(asRecord(asRecord(turn.answer).sourceCatalog).sources)
      asRecord(sources[0]).route = 'https://example.com/evidence'
    }), '必须是站内相对路径')
  })

  it('局部澄清引用未知 Goal、SINGLE_CHOICE 缺 choices 拒绝', () => {
    expectInvalid(mutate('answer-local-clarification.json', (turn) => {
      asRecord(asRecord(asRecord(turn.answer).localClarification)).affectedGoalIds = [
        'goal-unknown',
      ]
    }), '必须引用同一 answer 的 goalResult')
    expectInvalid(mutate('answer-local-clarification.json', (turn) => {
      const local = asRecord(asRecord(turn.answer).localClarification)
      asRecord(asArray(local.fields)[0]).choices = []
    }), 'SINGLE_CHOICE 至少需要一个 choice')
    expectInvalid(mutate('clarification.json', (turn) => {
      asRecord(asArray(asRecord(turn.clarification).fields)[0]).kind = 'MULTI_SELECT'
    }), 'kind 必须是')
  })

  it('非 ANSWER 缺 message 拒绝', () => {
    expectInvalid(mutate('conversational.json', (turn) => {
      delete turn.message
    }), 'message 必须是非空字符串')
  })

  it('未知附加字段按 additive evolution 忽略，不回退旧合同', () => {
    const value = mutate('conversational.json', (turn) => {
      turn.conversation = { conversationId: 'conv-1', resumeToken: 'opaque' }
      turn.futureField = { nested: true }
    })
    const parsed = parsePublicAgentTurn(value)
    expect(parsed.ok).toBe(true)
  })
})
