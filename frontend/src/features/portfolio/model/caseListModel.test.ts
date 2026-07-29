import { describe, expect, it } from 'vitest'

import type { PublicCase } from '../../public-content/model/publicContentTypes'
import { previewPublicContent } from '../../public-content/data/previewPublicContent'

import {
  CASE_STATUS_GROUP_INFO,
  CASE_STATUS_GROUP_ORDER,
  CASE_TYPE_LABEL,
  ACHIEVEMENT_STATUS_LABEL,
  CONTRIBUTION_LABEL,
  buildCaseStatusGroups,
  caseStatusGroup,
} from './caseListModel'

/** 造一个最小可用的 case，字段默认值可被覆盖。 */
function makeCase(overrides: Partial<PublicCase> = {}): PublicCase {
  return {
    id: 'case-x',
    slug: 'case-x',
    code: 'CASE-X',
    type: 'FEATURE',
    title: '占位案例',
    summary: '占位摘要',
    problem: '占位问题',
    actions: [],
    decisions: [],
    verification: [],
    outcome: '',
    limitations: [],
    achievementStatus: 'DELIVERED',
    contributionType: 'PRIMARY',
    projectSlug: null,
    evidence: [],
    suggestedQuestions: [],
    ...overrides,
  }
}

describe('caseListModel · caseStatusGroup', () => {
  it('把 DELIVERED 归入 delivered 组', () => {
    expect(caseStatusGroup('DELIVERED')).toBe('delivered')
  })

  it('把 IMPLEMENTED_TESTED 也归入 delivered 组', () => {
    expect(caseStatusGroup('IMPLEMENTED_TESTED')).toBe('delivered')
  })

  it('把 PROTOTYPE 归入 prototype 组', () => {
    expect(caseStatusGroup('PROTOTYPE')).toBe('prototype')
  })

  it('把 LEARNING 归入 learning 组', () => {
    expect(caseStatusGroup('LEARNING')).toBe('learning')
  })

  it('把 DESIGNED / PLANNED / UNKNOWN 归入 learning 组（保守降级，不夸大成熟度）', () => {
    expect(caseStatusGroup('DESIGNED')).toBe('learning')
    expect(caseStatusGroup('PLANNED')).toBe('learning')
    expect(caseStatusGroup('UNKNOWN')).toBe('learning')
  })
})

describe('caseListModel · buildCaseStatusGroups', () => {
  it('按 delivered → prototype → learning 固定顺序返回非空组', () => {
    const cases = [
      makeCase({ slug: 'c-learning', achievementStatus: 'LEARNING' }),
      makeCase({ slug: 'c-proto', achievementStatus: 'PROTOTYPE' }),
      makeCase({ slug: 'c-delivered', achievementStatus: 'DELIVERED' }),
    ]
    const groups = buildCaseStatusGroups(cases)
    expect(groups.map((g) => g.key)).toEqual(['delivered', 'prototype', 'learning'])
  })

  it('把 DELIVERED 与 IMPLEMENTED_TESTED 都收进 delivered 组', () => {
    const cases = [
      makeCase({ slug: 'a', achievementStatus: 'DELIVERED' }),
      makeCase({ slug: 'b', achievementStatus: 'IMPLEMENTED_TESTED' }),
      makeCase({ slug: 'c', achievementStatus: 'LEARNING' }),
    ]
    const groups = buildCaseStatusGroups(cases)
    const delivered = groups.find((g) => g.key === 'delivered')!
    expect(delivered.cases.map((c) => c.slug)).toEqual(['a', 'b'])
    expect(groups).toHaveLength(2)
  })

  it('空组不返回（某状态没有 case 时该 tab 不出现）', () => {
    const cases = [makeCase({ slug: 'a', achievementStatus: 'DELIVERED' })]
    const groups = buildCaseStatusGroups(cases)
    expect(groups).toHaveLength(1)
    expect(groups[0].key).toBe('delivered')
  })

  it('组内保持源数据顺序', () => {
    const cases = [
      makeCase({ slug: 'first', achievementStatus: 'DELIVERED' }),
      makeCase({ slug: 'second', achievementStatus: 'DELIVERED' }),
      makeCase({ slug: 'third', achievementStatus: 'DELIVERED' }),
    ]
    const groups = buildCaseStatusGroups(cases)
    expect(groups[0].cases.map((c) => c.slug)).toEqual(['first', 'second', 'third'])
  })

  it('空数组返回空数组（不产生空 tab）', () => {
    expect(buildCaseStatusGroups([])).toEqual([])
  })

  it('对 previewPublicContent 的真实 case 产生稳定分组', () => {
    const groups = buildCaseStatusGroups(previewPublicContent.cases)
    // preview 数据里 multilingual=DELIVERED、codegraph=PROTOTYPE
    const keys = groups.map((g) => g.key)
    expect(keys).toContain('delivered')
    expect(keys).toContain('prototype')
    expect(groups.find((g) => g.key === 'delivered')!.cases.map((c) => c.slug)).toContain(
      'multilingual-image-preservation',
    )
  })
})

describe('caseListModel · 文案映射', () => {
  it('CASE_STATUS_GROUP_INFO 覆盖三个状态组且带 code/label/note', () => {
    for (const key of CASE_STATUS_GROUP_ORDER) {
      const info = CASE_STATUS_GROUP_INFO[key]
      expect(typeof info.code).toBe('string')
      expect(info.code.length).toBeGreaterThan(0)
      expect(typeof info.label).toBe('string')
      expect(info.label.length).toBeGreaterThan(0)
      expect(typeof info.note).toBe('string')
      expect(info.note.length).toBeGreaterThan(0)
    }
  })

  it('CASE_TYPE_LABEL 覆盖三种 CaseType', () => {
    expect(CASE_TYPE_LABEL.FEATURE).toBeTruthy()
    expect(CASE_TYPE_LABEL.INCIDENT).toBeTruthy()
    expect(CASE_TYPE_LABEL.EVALUATION).toBeTruthy()
  })

  it('ACHIEVEMENT_STATUS_LABEL 覆盖全部 AchievementStatus', () => {
    const all: Array<keyof typeof ACHIEVEMENT_STATUS_LABEL> = [
      'DELIVERED',
      'IMPLEMENTED_TESTED',
      'PROTOTYPE',
      'DESIGNED',
      'LEARNING',
      'PLANNED',
      'UNKNOWN',
    ]
    for (const s of all) {
      expect(ACHIEVEMENT_STATUS_LABEL[s]).toBeTruthy()
    }
  })

  it('CONTRIBUTION_LABEL 覆盖全部 ContributionType', () => {
    expect(CONTRIBUTION_LABEL.PRIMARY).toBeTruthy()
    expect(CONTRIBUTION_LABEL.COLLABORATIVE).toBeTruthy()
    expect(CONTRIBUTION_LABEL.INDEPENDENT).toBeTruthy()
    expect(CONTRIBUTION_LABEL.OBSERVED_LEARNING).toBeTruthy()
  })
})
