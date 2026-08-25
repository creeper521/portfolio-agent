import type { GoalCoverage, PublicSectionKind, SupportKind } from './publicAgentTurn'

// 冻结枚举的纯展示标签映射（领域模型层）：只把合同枚举翻译成克制的中文文案，
// 不推导业务语义（coverage/resolution/来源构成均由后端决定）。（D-38 / D-41）

// 支撑类型使用克制的文字表述：不用颜色或对勾暗示不同来源具有同等验证强度。（D-41.8）
export const SUPPORT_KIND_LABELS: Readonly<Record<SupportKind, string>> = {
  GENERAL_KNOWLEDGE: '通用知识',
  VERIFIED_PUBLIC_EVIDENCE: '已审核公开证据',
  DERIVED: '基于上述内容归纳',
}

/** PublicSectionKind → 中文段落标题文案。 */
export const SECTION_KIND_LABELS: Readonly<Record<PublicSectionKind, string>> = {
  BACKGROUND: '背景',
  RESPONSIBILITY: '职责',
  SOLUTION: '方案',
  VERIFICATION: '验证',
  STATUS: '状态',
  BOUNDARY: '边界',
  GENERAL_PRINCIPLE: '通用原理',
  PORTFOLIO_EXAMPLE: '项目实例',
  RELATION: '二者关系',
}

// FULL 不显示覆盖标签（正文保持极简）；非 FULL 用文字+符号表达，不只靠颜色区分。（D-41.1）
export const GOAL_COVERAGE_LABELS: Readonly<Record<GoalCoverage, string>> = {
  FULL: '',
  PARTIAL: '部分完成',
  NONE: '未完成',
}
