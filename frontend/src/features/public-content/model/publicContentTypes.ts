export type AudienceRole = 'INTERVIEWER' | 'MENTOR' | 'HR' | 'GUEST'
export type ProjectStatus = 'DELIVERED' | 'IN_PROGRESS' | 'PROTOTYPE' | 'LEARNING_ONLY'
export type ContributionType = 'INDEPENDENT' | 'PRIMARY' | 'COLLABORATIVE' | 'OBSERVED_LEARNING'

export interface PublicOwner {
  name: string
  role: string
  summary: string
}

export interface PublicEvidence {
  id: string
  code: string
  title: string
  type: 'COLLECTION' | 'DOCUMENT' | 'SCREENSHOT' | 'CODE' | 'TEST_RESULT'
  periodStart: string
  periodEnd: string
  sourceCount: number
  summary: string
  supportedClaims: string[]
  publicStatus: 'APPROVED'
  projectSlugs: string[]
}

export interface PublicProject {
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
}

export interface TimelineEvent {
  id: string
  dateLabel: string
  title: string
  problem: string
  action: string
  impact: string
  projectSlugs: string[]
  evidenceIds: string[]
}

export interface PublicPortfolio {
  contentVersion: string
  publishedAt: string
  owner: PublicOwner
  projects: PublicProject[]
  evidence: PublicEvidence[]
  timeline: TimelineEvent[]
}
