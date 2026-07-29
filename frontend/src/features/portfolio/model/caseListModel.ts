import type {
  AchievementStatus,
  CaseType,
  ContributionType,
  PublicCase,
} from '../../public-content/model/publicContentTypes'

/**
 * Case 列表页的状态分组模型。
 *
 * 列表页用「交付状态」做 tab（已确认决策），而非现有 buildDossierIndex 的「按 CaseType 分组」。
 * 7 个 AchievementStatus 合并成 3 个展示组，避免零散状态把 tab 撕碎：
 *   - delivered  : DELIVERED + IMPLEMENTED_TESTED（真正交付并验证的）
 *   - prototype  : PROTOTYPE（可运行原型，未生产验证）
 *   - learning   : 其余（DESIGNED / LEARNING / PLANNED / UNKNOWN）
 *
 * learning 是保守降级：不夸大成熟度，未明确交付的统一归入「学习整理」。
 */
export type CaseStatusGroupKey = 'delivered' | 'prototype' | 'learning'

export interface CaseStatusGroup {
  key: CaseStatusGroupKey
  code: string
  label: string
  note: string
  cases: PublicCase[]
}

/** 固定展示顺序：先亮交付实力，再原型，最后学习沉淀。 */
export const CASE_STATUS_GROUP_ORDER: readonly CaseStatusGroupKey[] = [
  'delivered',
  'prototype',
  'learning',
] as const

export const CASE_STATUS_GROUP_INFO: Record<
  CaseStatusGroupKey,
  { code: string; label: string; note: string }
> = {
  delivered: {
    code: 'DELIVERED',
    label: '已交付',
    note: '已交付并验证的真实产出——这些是「做过且 ship 了」的。',
  },
  prototype: {
    code: 'PROTOTYPE',
    label: '原型验证',
    note: '可运行的原型探索，未经生产验证——能力已跑通但不等于交付。',
  },
  learning: {
    code: 'LEARNING',
    label: '学习整理',
    note: '结构化学习与知识整理——不宣称实现，是认知沉淀。',
  },
}

/** CaseType 的中文展示文案（前端展示决策，不改后端枚举）。 */
export const CASE_TYPE_LABEL: Record<CaseType, string> = {
  FEATURE: '功能任务',
  INCIDENT: '问题处理',
  EVALUATION: '工具评测',
}

/** AchievementStatus 的中文展示文案。 */
export const ACHIEVEMENT_STATUS_LABEL: Record<AchievementStatus, string> = {
  DELIVERED: '已交付',
  IMPLEMENTED_TESTED: '已实现并测试',
  PROTOTYPE: '原型验证',
  DESIGNED: '已完成设计',
  LEARNING: '学习整理',
  PLANNED: '已规划',
  UNKNOWN: '状态未定',
}

/** ContributionType 的中文展示文案。 */
export const CONTRIBUTION_LABEL: Record<ContributionType, string> = {
  PRIMARY: '主要负责',
  COLLABORATIVE: '协作参与',
  INDEPENDENT: '独立完成',
  OBSERVED_LEARNING: '观察学习',
}

/**
 * 把 AchievementStatus 映射成三个展示组之一。
 * DELIVERED 与 IMPLEMENTED_TESTED 都算交付；PROTOTYPE 单列；其余保守降级到 learning。
 */
export function caseStatusGroup(status: AchievementStatus): CaseStatusGroupKey {
  if (status === 'DELIVERED' || status === 'IMPLEMENTED_TESTED') return 'delivered'
  if (status === 'PROTOTYPE') return 'prototype'
  return 'learning'
}

/**
 * 把 cases 按交付状态分成 delivered / prototype / learning 三组。
 * 空组不返回（对应 tab 不出现），组内保持源数据顺序。
 */
export function buildCaseStatusGroups(cases: ReadonlyArray<PublicCase>): CaseStatusGroup[] {
  const buckets: Record<CaseStatusGroupKey, PublicCase[]> = {
    delivered: [],
    prototype: [],
    learning: [],
  }
  for (const item of cases) {
    buckets[caseStatusGroup(item.achievementStatus)].push(item)
  }
  return CASE_STATUS_GROUP_ORDER.filter((key) => buckets[key].length > 0).map((key) => ({
    key,
    code: CASE_STATUS_GROUP_INFO[key].code,
    label: CASE_STATUS_GROUP_INFO[key].label,
    note: CASE_STATUS_GROUP_INFO[key].note,
    cases: buckets[key],
  }))
}
