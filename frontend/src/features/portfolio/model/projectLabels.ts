import type { ContributionType, ProjectStatus } from './portfolioTypes'

export const statusLabels: Record<ProjectStatus, string> = {
  DELIVERED: '核心版本已交付',
  IN_PROGRESS: '持续推进中',
  PROTOTYPE: '原型已验证',
  LEARNING_ONLY: '学习与观察',
}

export const contributionLabels: Record<ContributionType, string> = {
  INDEPENDENT: '独立完成',
  PRIMARY: '主导贡献',
  COLLABORATIVE: '协作贡献',
  OBSERVED_LEARNING: '观察学习',
}
