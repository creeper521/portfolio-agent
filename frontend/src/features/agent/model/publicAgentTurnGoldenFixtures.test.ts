import { describe, expect, it } from 'vitest'

import {
  EXPECTED_FIXTURE_FILES,
  loadPublicAgentTurnGoldenFixtures,
  type GoldenFixture,
} from './publicAgentTurnFixtureLoader'
import { parsePublicAgentTurn } from './publicAgentTurnMapper'

// Slice 0 共享合同消费者测试（S0-01 / 前端交接 §9）：
// 直接读取仓库根 contracts/agent-turn/fixtures 的 8 个 Golden Fixtures，不复制数据到 frontend；
// 结构校验委托给唯一消费权威 parsePublicAgentTurn，本文件只保留 fixture 源专属断言。

// 后端合同结构测试冻结的禁止字段闭集：旧协议版本轴、执行/任务轴、raw 内部 ID
// 与公共 degraded 轴不得在任何层级重新出现。
const FORBIDDEN_KEYS: readonly string[] = [
  'interaction',
  'agentTurn',
  'contractVersion',
  'disposition',
  'completedTasks',
  'taskId',
  'sourceTaskId',
  'sourceTaskIds',
  'claimId',
  'claimIds',
  'evidenceId',
  'evidenceIds',
  'degraded',
  'degradationSummary',
  'execution',
  'reasonCodes',
]

const EXPECTED_KIND_BY_FILE: Readonly<Record<string, string>> = {
  'answer-complete.json': 'ANSWER',
  'answer-local-clarification.json': 'ANSWER',
  'answer-no-result.json': 'ANSWER',
  'answer-partial.json': 'ANSWER',
  'boundary.json': 'BOUNDARY',
  'capability-unavailable.json': 'CAPABILITY_UNAVAILABLE',
  'clarification.json': 'CLARIFICATION',
  'conversational.json': 'CONVERSATIONAL',
}

const TURN_KINDS: readonly string[] = [
  'ANSWER',
  'CLARIFICATION',
  'CONVERSATIONAL',
  'BOUNDARY',
  'CAPABILITY_UNAVAILABLE',
]

const fixtures: readonly GoldenFixture[] = loadPublicAgentTurnGoldenFixtures()

function isJsonObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function validateNoForbiddenKeys(value: unknown, fileName: string): void {
  if (isJsonObject(value)) {
    for (const key of Object.keys(value)) {
      if (FORBIDDEN_KEYS.includes(key)) {
        throw new Error(`${fileName}: 出现禁止字段 "${key}"`)
      }
      validateNoForbiddenKeys(value[key], fileName)
    }
    return
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      validateNoForbiddenKeys(item, fileName)
    }
  }
}

function answerOf(fixture: GoldenFixture): Record<string, unknown> {
  const parsed = parsePublicAgentTurn(fixture.turn)
  if (!parsed.ok || parsed.turn.kind !== 'ANSWER') {
    throw new Error(`${fixture.fileName}: 该 fixture 不是可解析的 ANSWER 变体`)
  }
  return parsed.turn.answer as unknown as Record<string, unknown>
}

describe('PublicAgentTurn 共享 Golden Fixtures（contracts/agent-turn/fixtures）', () => {
  it('仓库根提供且仅提供 8 个冻结共享 fixture 文件', () => {
    expect(fixtures.map((fixture) => fixture.fileName)).toEqual(EXPECTED_FIXTURE_FILES)
  })

  it.each(fixtures)('$fileName 通过前端 mapper 且 kind 与冻结文件名一致', (fixture) => {
    const parsed = parsePublicAgentTurn(fixture.turn)
    expect(parsed.ok, JSON.stringify(parsed.ok ? '' : parsed.error.violations)).toBe(true)
    if (parsed.ok) {
      expect(parsed.turn.kind).toBe(EXPECTED_KIND_BY_FILE[fixture.fileName])
    }
  })

  it.each(fixtures)('$fileName 不含禁止字段（旧协议/执行/raw ID/degraded 轴）', (fixture) => {
    validateNoForbiddenKeys(fixture.turn, fixture.fileName)
  })

  it('覆盖全部五种顶层变体，且只有 ANSWER 携带 answer', () => {
    const coveredKinds = new Set<string>(
      fixtures.map((fixture) => {
        const parsed = parsePublicAgentTurn(fixture.turn)
        if (!parsed.ok) {
          throw new Error(`${fixture.fileName}: 解析失败`)
        }
        return parsed.turn.kind
      }),
    )
    for (const kind of TURN_KINDS) {
      expect(coveredKinds.has(kind), `缺少变体 ${kind}`).toBe(true)
    }
    for (const fixture of fixtures) {
      if (EXPECTED_KIND_BY_FILE[fixture.fileName] !== 'ANSWER') {
        expect(
          'answer' in (fixture.turn as Record<string, unknown>),
          `${fixture.fileName} 不得携带 answer`,
        ).toBe(false)
      }
    }
  })

  it('覆盖 Answer 的 COMPLETE / PARTIAL / NO_RESULT 三种 resolution', () => {
    const resolutions = fixtures
      .filter((fixture) => EXPECTED_KIND_BY_FILE[fixture.fileName] === 'ANSWER')
      .map((fixture) => answerOf(fixture).resolution)
    expect(new Set(resolutions)).toEqual(new Set(['COMPLETE', 'PARTIAL', 'NO_RESULT']))
  })

  it('覆盖 SECTIONED 与 RECOMMENDATION presentation 及 ANSWER 内 local clarification', () => {
    const fixturesWithPresentationKind = (kind: string): readonly GoldenFixture[] =>
      fixtures.filter((fixture) => {
        if (EXPECTED_KIND_BY_FILE[fixture.fileName] !== 'ANSWER') {
          return false
        }
        const goalResults = answerOf(fixture).goalResults
        if (!Array.isArray(goalResults)) {
          return false
        }
        return goalResults.some((rawGoal) => {
          const goal = rawGoal as Record<string, unknown>
          return (goal.presentation as Record<string, unknown> | undefined)?.kind === kind
        })
      })
    expect(
      fixturesWithPresentationKind('SECTIONED').length,
      '至少一个 fixture 使用 SECTIONED presentation',
    ).toBeGreaterThan(0)
    expect(
      fixturesWithPresentationKind('RECOMMENDATION').length,
      '至少一个 fixture 使用 RECOMMENDATION presentation',
    ).toBeGreaterThan(0)

    const localClarificationFixture = fixtures.find(
      (fixture) => fixture.fileName === 'answer-local-clarification.json',
    )
    if (localClarificationFixture === undefined) {
      throw new Error('缺少 answer-local-clarification.json')
    }
    expect(
      answerOf(localClarificationFixture).localClarification,
      'answer-local-clarification.json 必须携带 answer.localClarification',
    ).not.toBeUndefined()
  })
})
