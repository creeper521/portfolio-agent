import type { LocationQuery } from 'vue-router'

import type { CaseType, PublicCase } from '../../public-content/model/publicContentTypes'

import { caseStatusGroup } from './caseListModel'

/**
 * /cases 的组合筛选模型。
 *
 * 筛选状态与 URL query 双向同步：
 *   - parseCaseQuery 把 LocationQuery 解析成筛选状态，非法值一律回退安全默认；
 *   - buildCaseQueryObject 把筛选状态序列化回 query，默认值不写入（保持 URL 干净）。
 *
 * 归属（project / collection / independent）三者互斥，
 * 优先级 independent > project > collection——同时出现时低优先级丢弃，
 * 避免「既属于某项目又是独立案例」这种自相矛盾的筛选。
 */

export type CaseStatusFilter = 'all' | 'delivered' | 'investigated' | 'prototype' | 'learning'

export type CaseTypeFilter = 'all' | CaseType

export interface CaseFilterState {
  status: CaseStatusFilter
  project: string | null
  collection: string | null
  independent: boolean
  type: CaseTypeFilter
  q: string
}

/** 直接进入 /cases 时的默认筛选：只看已交付。 */
export const DEFAULT_CASE_FILTER: CaseFilterState = {
  status: 'delivered',
  project: null,
  collection: null,
  independent: false,
  type: 'all',
  q: '',
}

const STATUS_FILTERS: readonly CaseStatusFilter[] = [
  'all',
  'delivered',
  'investigated',
  'prototype',
  'learning',
] as const

const TYPE_FILTERS: readonly CaseTypeFilter[] = ['all', 'FEATURE', 'INCIDENT', 'EVALUATION'] as const

/** query 值只接受单个字符串；数组或缺失一律视为未提供。 */
function queryString(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

/** 把 URL query 解析成筛选状态；任何非法值都回退到安全默认，绝不抛错。 */
export function parseCaseQuery(query: LocationQuery): CaseFilterState {
  const rawStatus = queryString(query.status)
  const status: CaseStatusFilter =
    rawStatus !== null && (STATUS_FILTERS as readonly string[]).includes(rawStatus)
      ? (rawStatus as CaseStatusFilter)
      : DEFAULT_CASE_FILTER.status

  const rawType = queryString(query.type)
  const type: CaseTypeFilter =
    rawType !== null && (TYPE_FILTERS as readonly string[]).includes(rawType)
      ? (rawType as CaseTypeFilter)
      : 'all'

  const rawIndependent = queryString(query.independent)
  const independent = rawIndependent === '1' || rawIndependent === 'true'

  const project = queryString(query.project)
  const collection = queryString(query.collection)

  const rawQ = queryString(query.q)
  const q = rawQ === null ? '' : rawQ.trim()

  // 归属互斥：independent > project > collection
  if (independent) {
    return { status, project: null, collection: null, independent: true, type, q }
  }
  if (project !== null) {
    return { status, project, collection: null, independent: false, type, q }
  }
  return { status, project: null, collection, independent: false, type, q }
}

/** 按筛选状态过滤案例；各条件取交集，保持源数据顺序。 */
export function filterCases(cases: ReadonlyArray<PublicCase>, state: CaseFilterState): PublicCase[] {
  const keyword = state.q.toLowerCase()
  return cases.filter((item) => {
    if (state.status !== 'all' && caseStatusGroup(item.achievementStatus) !== state.status) {
      return false
    }
    if (state.independent && item.projectSlug !== null) return false
    if (state.project !== null && item.projectSlug !== state.project) return false
    if (state.collection !== null && !item.collectionSlugs.includes(state.collection)) return false
    if (state.type !== 'all' && item.type !== state.type) return false
    if (keyword.length > 0) {
      const haystack = `${item.title}\n${item.code}\n${item.summary}`.toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    return true
  })
}

/** 把筛选状态序列化为 URL query；等于默认值的字段不写入。 */
export function buildCaseQueryObject(state: CaseFilterState): Record<string, string> {
  const query: Record<string, string> = {}
  if (state.status !== DEFAULT_CASE_FILTER.status) query.status = state.status
  if (state.independent) {
    query.independent = '1'
  } else if (state.project !== null) {
    query.project = state.project
  } else if (state.collection !== null) {
    query.collection = state.collection
  }
  if (state.type !== 'all') query.type = state.type
  if (state.q.length > 0) query.q = state.q
  return query
}
