import type {
  CaseType,
  ContributionType,
  ProjectStatus,
  AchievementStatus,
  PublicCase,
  PublicProject,
} from '../../public-content/model/publicContentTypes'

export type DossierIndexKind = 'PROJECT' | 'CASE'

/**
 * 案卷索引条目 —— 比 Dossier 更轻，只承载列表页需要的信息。
 * project 与 case 都归一成这个形状，列表页不再区分两种实体。
 */
export interface DossierIndexEntry {
  slug: string
  code: string
  kind: DossierIndexKind
  /** 用于分组：交付物（项目 + FEATURE case）vs 评测（EVALUATION case） */
  group: 'DELIVERED' | 'EVALUATION'
  typeLabel: string
  title: string
  summary: string
  technologies: string[]
  status: ProjectStatus | AchievementStatus
  contributionType: ContributionType
}

const TYPE_LABELS: Record<DossierIndexKind | 'PROJECT', string> = {
  PROJECT: '核心项目',
  CASE: '功能修复',
}

function caseGroup(caseType: CaseType): 'DELIVERED' | 'EVALUATION' {
  return caseType === 'EVALUATION' ? 'EVALUATION' : 'DELIVERED'
}

function caseTypeLabel(caseType: CaseType): string {
  if (caseType === 'EVALUATION') return '工具评测'
  if (caseType === 'BUGFIX') return '缺陷修复'
  return '功能修复'
}

export function projectToIndexEntry(project: PublicProject): DossierIndexEntry {
  return {
    slug: project.slug,
    code: project.code,
    kind: 'PROJECT',
    group: 'DELIVERED',
    typeLabel: TYPE_LABELS.PROJECT,
    title: project.title,
    summary: project.summary,
    technologies: project.technologies,
    status: project.status,
    contributionType: project.contributionType,
  }
}

export function caseToIndexEntry(caseStudy: PublicCase): DossierIndexEntry {
  return {
    slug: caseStudy.slug,
    code: caseStudy.code,
    kind: 'CASE',
    group: caseGroup(caseStudy.type),
    typeLabel: caseTypeLabel(caseStudy.type),
    title: caseStudy.title,
    summary: caseStudy.summary,
    technologies: [],
    status: caseStudy.achievementStatus,
    contributionType: caseStudy.contributionType,
  }
}

export interface DossierIndexGroup {
  code: string
  title: string
  note: string
  entries: DossierIndexEntry[]
}

/**
 * 把 projects + cases 归并成「核心交付 / 探索与评测」两组。
 * 每组保持原序：projects 在前，cases 按数据顺序在后。
 * 评测组即使只有一条也独立成组（保持分组语义完整）。
 */
export function buildDossierIndex(
  projects: ReadonlyArray<PublicProject>,
  cases: ReadonlyArray<PublicCase>,
): DossierIndexGroup[] {
  const delivered: DossierIndexEntry[] = [
    ...projects.map(projectToIndexEntry),
    ...cases
      .filter((item) => caseGroup(item.type) === 'DELIVERED')
      .map(caseToIndexEntry),
  ]
  const evaluation: DossierIndexEntry[] = cases
    .filter((item) => caseGroup(item.type) === 'EVALUATION')
    .map(caseToIndexEntry)

  const groups: DossierIndexGroup[] = []
  if (delivered.length) {
    groups.push({
      code: 'A / DELIVERED',
      title: '核心交付',
      note: '已完成、部署或回归的主导工作，含核心项目与功能修复案例。',
      entries: delivered,
    })
  }
  if (evaluation.length) {
    groups.push({
      code: 'B / EVALUATION',
      title: '探索与评测',
      note: '原型阶段的工具评测与失效分析，结论定性，非生产交付物。',
      entries: evaluation,
    })
  }
  return groups
}
