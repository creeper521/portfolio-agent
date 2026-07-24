import type {
  AchievementStatus,
  ContributionType,
  CaseType,
  ProjectStatus,
} from '../../public-content/model/publicContentTypes'

/**
 * Dossier —— 统一案卷视图模型。
 *
 * project 和 case 是两种领域实体，但它们在「工程案卷」叙事里承担相同的角色：
 * 都按 为什么做 / 我的职责 / 如何做 / 如何证明 / 最终状态 五段展开。
 * Dossier 把两者的字段差异（background↔problem、responsibilities↔actions、
 * solution/keyDecisions↔decisions、handoff↔limitations）在消费层归一化，
 * 让 ProjectPage 只认一种形状，避免模板里散落 isCase 分支。
 */
export type DossierKind = 'PROJECT' | 'CASE'

export interface DossierStatus {
  /** 项目用 status，case 用 achievementStatus；二者映射到同一组交付状态文案 */
  status: ProjectStatus | AchievementStatus
  contributionType: ContributionType
}

export interface DossierSection {
  /** 段落锚点 id，用于详情页 TOC 与跨页跳转 */
  anchor: 'why' | 'role' | 'how' | 'proof' | 'status'
  code: string
  title: string
}

export interface Dossier {
  kind: DossierKind
  /** case 的 type（FEATURE/EVALUATION/...）；project 为 null */
  caseType: CaseType | null
  slug: string
  code: string
  title: string
  summary: string
  problem: string
  responsibilities: string[]
  solution: string
  decisions: string[]
  verification: string[]
  outcome: string
  /** 项目 handoff 或 case 的 limitations 合并文本，落在「最终状态」段 */
  boundary: string
  technologies: string[]
  evidenceIds: string[]
  suggestedQuestions: string[]
  status: DossierStatus
  sections: DossierSection[]
}

const SHARED_SECTIONS: DossierSection[] = [
  { anchor: 'why', code: '01 / WHY', title: '为什么做' },
  { anchor: 'role', code: '02 / RESPONSIBILITY', title: '我的职责' },
  { anchor: 'how', code: '03 / SOLUTION', title: '如何做' },
  { anchor: 'proof', code: '04 / VERIFICATION', title: '如何证明' },
  { anchor: 'status', code: '05 / STATUS', title: '最终状态' },
]

export function projectToDossier(
  project: {
    slug: string
    code: string
    title: string
    summary: string
    background: string
    responsibilities: string[]
    solution: string
    keyDecisions: string[]
    technologies: string[]
    verification: string[]
    outcome: string
    handoff: string
    status: ProjectStatus
    contributionType: ContributionType
    evidenceIds: string[]
    suggestedQuestions: string[]
  },
): Dossier {
  return {
    kind: 'PROJECT',
    caseType: null,
    slug: project.slug,
    code: project.code,
    title: project.title,
    summary: project.summary,
    problem: project.background,
    responsibilities: project.responsibilities,
    solution: project.solution,
    decisions: project.keyDecisions,
    verification: project.verification,
    outcome: project.outcome,
    boundary: project.handoff,
    technologies: project.technologies,
    evidenceIds: project.evidenceIds,
    suggestedQuestions: project.suggestedQuestions,
    status: {
      status: project.status,
      contributionType: project.contributionType,
    },
    sections: SHARED_SECTIONS,
  }
}

export function caseToDossier(
  caseStudy: {
    slug: string
    code: string
    type: CaseType
    title: string
    summary: string
    problem: string
    actions: string[]
    decisions: string[]
    verification: string[]
    outcome: string
    limitations: string[]
    achievementStatus: AchievementStatus
    contributionType: ContributionType
    evidence: ReadonlyArray<{ id: string }>
    suggestedQuestions: string[]
  },
): Dossier {
  return {
    kind: 'CASE',
    caseType: caseStudy.type,
    slug: caseStudy.slug,
    code: caseStudy.code,
    title: caseStudy.title,
    summary: caseStudy.summary,
    problem: caseStudy.problem,
    responsibilities: caseStudy.actions,
    solution: caseStudy.decisions.join(' '),
    decisions: caseStudy.decisions,
    verification: caseStudy.verification,
    outcome: caseStudy.outcome,
    boundary: caseStudy.limitations.join(' '),
    technologies: [],
    evidenceIds: caseStudy.evidence.map((item) => item.id),
    suggestedQuestions: caseStudy.suggestedQuestions,
    status: {
      status: caseStudy.achievementStatus,
      contributionType: caseStudy.contributionType,
    },
    sections: SHARED_SECTIONS,
  }
}

/**
 * 在 projects 与 cases 中按 slug 解析出 Dossier，供 /projects/:slug 路由复用。
 * project 优先；找不到再在 cases 里找。两者都找不到返回 null。
 */
export function resolveDossier(
  slug: string,
  projects: ReadonlyArray<Parameters<typeof projectToDossier>[0]>,
  cases: ReadonlyArray<Parameters<typeof caseToDossier>[0]>,
): Dossier | null {
  const project = projects.find((item) => item.slug === slug)
  if (project) return projectToDossier(project)
  const caseStudy = cases.find((item) => item.slug === slug)
  if (caseStudy) return caseToDossier(caseStudy)
  return null
}
