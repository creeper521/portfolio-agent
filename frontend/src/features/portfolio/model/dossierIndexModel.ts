import type {
  CaseType,
  ContributionType,
  ProjectStatus,
  AchievementStatus,
  PublicCase,
  PublicProject,
} from '../../public-content/model/publicContentTypes'

export type DossierIndexKind = 'PROJECT' | 'CASE'
export type DossierIndexGroupCode = 'MAINLINE' | 'TASK' | 'INCIDENT' | 'EVALUATION'

/**
 * 案卷索引条目 —— 比 Dossier 更轻，只承载列表页需要的信息。
 * project 与 case 都归一成这个形状，列表页不再区分两种实体。
 */
export interface DossierIndexEntry {
  slug: string
  code: string
  kind: DossierIndexKind
  /** 用于分组：主线、任务、问题处理和知识/评测。 */
  group: DossierIndexGroupCode
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

function caseGroup(caseType: CaseType): DossierIndexGroupCode {
  if (caseType === 'EVALUATION') return 'EVALUATION'
  if (caseType === 'INCIDENT') return 'INCIDENT'
  return 'TASK'
}

function caseTypeLabel(caseType: CaseType): string {
  if (caseType === 'EVALUATION') return '工具评测'
  if (caseType === 'INCIDENT') return '问题处理'
  return '功能任务'
}

export function projectToIndexEntry(project: PublicProject): DossierIndexEntry {
  return {
    slug: project.slug,
    code: project.code,
    kind: 'PROJECT',
    group: 'MAINLINE',
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
 * 把 projects + cases 归并成主线、任务、问题处理与知识/评测四组。
 * 每组保持源数据顺序，空组不渲染。
 */
export function buildDossierIndex(
  projects: ReadonlyArray<PublicProject>,
  cases: ReadonlyArray<PublicCase>,
): DossierIndexGroup[] {
  const entriesByGroup: Record<DossierIndexGroupCode, DossierIndexEntry[]> = {
    MAINLINE: projects.map(projectToIndexEntry),
    TASK: cases.filter((item) => caseGroup(item.type) === 'TASK').map(caseToIndexEntry),
    INCIDENT: cases.filter((item) => caseGroup(item.type) === 'INCIDENT').map(caseToIndexEntry),
    EVALUATION: cases.filter((item) => caseGroup(item.type) === 'EVALUATION').map(caseToIndexEntry),
  }
  const definitions: Array<{
    group: DossierIndexGroupCode
    code: string
    title: string
    note: string
  }> = [
    {
      group: 'MAINLINE',
      code: 'A / MAINLINE',
      title: '长期主线',
      note: '持续推进的项目、平台、研究与工程学习主线，状态按真实成熟度展示。',
    },
    {
      group: 'TASK',
      code: 'B / TASK',
      title: '单体任务',
      note: '有明确边界的功能、工具、文档与交付任务。',
    },
    {
      group: 'INCIDENT',
      code: 'C / INCIDENT',
      title: '问题处理',
      note: '故障定位与问题排查记录；没有最终验收的条目不会表述为已修复。',
    },
    {
      group: 'EVALUATION',
      code: 'D / KNOWLEDGE',
      title: '知识与评测',
      note: '研究、学习手册、方案比较与离线评测，不等同于生产实现。',
    },
  ]
  return definitions
    .filter(({ group }) => entriesByGroup[group].length > 0)
    .map(({ group, code, title, note }) => ({
      code,
      title,
      note,
      entries: entriesByGroup[group],
    }))
}
