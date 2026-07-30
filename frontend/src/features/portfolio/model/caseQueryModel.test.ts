import { describe, expect, it } from 'vitest'

import type { PublicCase } from '../../public-content/model/publicContentTypes'

import {
  DEFAULT_CASE_FILTER,
  buildCaseQueryObject,
  filterCases,
  parseCaseQuery,
} from './caseQueryModel'

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
    collectionSlugs: [],
    evidence: [],
    suggestedQuestions: [],
    ...overrides,
  }
}

describe('caseQueryModel · parseCaseQuery', () => {
  it('无 query 时回退到默认筛选（已交付）', () => {
    expect(parseCaseQuery({})).toEqual(DEFAULT_CASE_FILTER)
    expect(DEFAULT_CASE_FILTER.status).toBe('delivered')
  })

  it('status=all 显式保留', () => {
    expect(parseCaseQuery({ status: 'all' }).status).toBe('all')
  })

  it('合法 status 值全部保留', () => {
    for (const s of ['delivered', 'investigated', 'prototype', 'learning']) {
      expect(parseCaseQuery({ status: s }).status).toBe(s)
    }
  })

  it('非法 status 回退到 delivered', () => {
    expect(parseCaseQuery({ status: 'hacked' }).status).toBe('delivered')
  })

  it('数组形式的 query 值回退到安全默认', () => {
    const state = parseCaseQuery({ status: ['all', 'delivered'], type: ['FEATURE'] })
    expect(state.status).toBe('delivered')
    expect(state.type).toBe('all')
  })

  it('解析 project / type / q，q 去掉首尾空白', () => {
    const state = parseCaseQuery({ project: 'sql-audit', type: 'INCIDENT', q: '  审计  ' })
    expect(state.project).toBe('sql-audit')
    expect(state.type).toBe('INCIDENT')
    expect(state.q).toBe('审计')
  })

  it('非法 type 回退到 all', () => {
    expect(parseCaseQuery({ type: 'BUG' }).type).toBe('all')
  })

  it('independent=1 或 true 解析为独立案例筛选', () => {
    expect(parseCaseQuery({ independent: '1' }).independent).toBe(true)
    expect(parseCaseQuery({ independent: 'true' }).independent).toBe(true)
    expect(parseCaseQuery({ independent: '0' }).independent).toBe(false)
  })

  it('归属互斥：independent 优先于 project，project 优先于 collection', () => {
    const a = parseCaseQuery({ independent: '1', project: 'sql-audit', collection: 'tech-writing' })
    expect(a.independent).toBe(true)
    expect(a.project).toBeNull()
    expect(a.collection).toBeNull()

    const b = parseCaseQuery({ project: 'sql-audit', collection: 'tech-writing' })
    expect(b.project).toBe('sql-audit')
    expect(b.collection).toBeNull()
  })
})

describe('caseQueryModel · filterCases', () => {
  const cases = [
    makeCase({ slug: 'c-delivered', code: 'CASE-01', achievementStatus: 'DELIVERED', projectSlug: 'sql-audit' }),
    makeCase({ slug: 'c-tested', code: 'CASE-02', achievementStatus: 'IMPLEMENTED_TESTED', projectSlug: 'sql-audit', type: 'INCIDENT' }),
    makeCase({ slug: 'c-investigated', code: 'CASE-03', achievementStatus: 'INVESTIGATED', projectSlug: 'activity-engineering' }),
    makeCase({ slug: 'c-proto', code: 'CASE-04', achievementStatus: 'PROTOTYPE', collectionSlugs: ['open-source-evaluation'] }),
    makeCase({ slug: 'c-learning', code: 'CASE-77', achievementStatus: 'LEARNING', title: '检索笔记整理' }),
  ]

  it('默认筛选只返回已交付组', () => {
    const result = filterCases(cases, DEFAULT_CASE_FILTER)
    expect(result.map((c) => c.slug)).toEqual(['c-delivered', 'c-tested'])
  })

  it('status=all 返回全部', () => {
    const result = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'all' })
    expect(result).toHaveLength(5)
  })

  it('status=investigated 只返回已排查组', () => {
    const result = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'investigated' })
    expect(result.map((c) => c.slug)).toEqual(['c-investigated'])
  })

  it('按 project 归属筛选', () => {
    const result = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'all', project: 'sql-audit' })
    expect(result.map((c) => c.slug)).toEqual(['c-delivered', 'c-tested'])
  })

  it('按 collection 归属筛选', () => {
    const result = filterCases(cases, {
      ...DEFAULT_CASE_FILTER,
      status: 'all',
      collection: 'open-source-evaluation',
    })
    expect(result.map((c) => c.slug)).toEqual(['c-proto'])
  })

  it('independent 只返回无所属 Project 的案例', () => {
    const result = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'all', independent: true })
    expect(result.map((c) => c.slug)).toEqual(['c-proto', 'c-learning'])
  })

  it('按类型筛选', () => {
    const result = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'all', type: 'INCIDENT' })
    expect(result.map((c) => c.slug)).toEqual(['c-tested'])
  })

  it('关键词不区分大小写匹配标题、编号与摘要', () => {
    const byTitle = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'all', q: '检索笔记' })
    expect(byTitle.map((c) => c.slug)).toEqual(['c-learning'])
    const byCode = filterCases(cases, { ...DEFAULT_CASE_FILTER, status: 'all', q: 'case-77' })
    expect(byCode.map((c) => c.slug)).toEqual(['c-learning'])
  })

  it('多个条件取交集', () => {
    const result = filterCases(cases, {
      ...DEFAULT_CASE_FILTER,
      status: 'all',
      project: 'sql-audit',
      type: 'INCIDENT',
    })
    expect(result.map((c) => c.slug)).toEqual(['c-tested'])
  })
})

describe('caseQueryModel · buildCaseQueryObject', () => {
  it('默认筛选不产生任何 query 参数', () => {
    expect(buildCaseQueryObject(DEFAULT_CASE_FILTER)).toEqual({})
  })

  it('显式 status=all 写入 query', () => {
    expect(buildCaseQueryObject({ ...DEFAULT_CASE_FILTER, status: 'all' })).toEqual({ status: 'all' })
  })

  it('三种归属各写入对应参数', () => {
    expect(buildCaseQueryObject({ ...DEFAULT_CASE_FILTER, project: 'sql-audit' })).toEqual({
      project: 'sql-audit',
    })
    expect(buildCaseQueryObject({ ...DEFAULT_CASE_FILTER, collection: 'tech-writing' })).toEqual({
      collection: 'tech-writing',
    })
    expect(buildCaseQueryObject({ ...DEFAULT_CASE_FILTER, independent: true })).toEqual({
      independent: '1',
    })
  })

  it('非默认 type 与 q 写入 query', () => {
    const query = buildCaseQueryObject({ ...DEFAULT_CASE_FILTER, type: 'FEATURE', q: '审计' })
    expect(query).toEqual({ type: 'FEATURE', q: '审计' })
  })

  it('parse 与 build 往返后状态一致', () => {
    const state = {
      status: 'all' as const,
      project: 'sql-audit',
      collection: null,
      independent: false,
      type: 'INCIDENT' as const,
      q: '审计',
    }
    expect(parseCaseQuery(buildCaseQueryObject(state))).toEqual(state)
  })
})
