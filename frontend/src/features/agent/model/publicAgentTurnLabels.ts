import type { GoalCoverage, PublicSectionKind, SupportKind } from './publicAgentTurn'

// D-38/D-41 的纯展示标签映射。只翻译冻结枚举为克制中文文案；
// 不推导业务语义（coverage/resolution/来源构成均由后端决定）。

// D-41.8：Support 使用克制文本，不靠颜色或对勾暗示同等验证强度。
export const SUPPORT_KIND_LABELS: Readonly<Record<SupportKind, string>> = {
  GENERAL_KNOWLEDGE: '通用知识',
  VERIFIED_PUBLIC_EVIDENCE: '已审核公开证据',
  DERIVED: '基于上述内容归纳',
}

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

// FULL 不显示覆盖标签（D-41.1 极简正文）；非 FULL 用文字+符号表达，不只靠颜色。
export const GOAL_COVERAGE_LABELS: Readonly<Record<GoalCoverage, string>> = {
  FULL: '',
  PARTIAL: '部分完成',
  NONE: '未完成',
}
