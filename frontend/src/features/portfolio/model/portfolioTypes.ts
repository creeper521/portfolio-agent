export type ProjectStatus = 'DELIVERED' | 'IN_PROGRESS' | 'PROTOTYPE' | 'LEARNING_ONLY'
export type ContributionType = 'INDEPENDENT' | 'PRIMARY' | 'COLLABORATIVE' | 'OBSERVED_LEARNING'

export interface OwnerProfile {
  name: string
  role: string
  summary: string
  githubUrl?: string | null
  email?: string | null
  resumeUrl?: string | null
}

export interface ProjectSummary {
  slug: string
  title: string
  summary: string
  status: ProjectStatus
  contributionType: ContributionType
}

export interface PortfolioHome {
  contentVersion: string
  publishedAt: string
  owner: OwnerProfile
  projects: ProjectSummary[]
}

export interface Evidence {
  id: string
  title: string
  type: 'COLLECTION' | 'DOCUMENT' | 'SCREENSHOT' | 'CODE' | 'TEST_RESULT'
  periodStart: string
  periodEnd: string
  sourceCount: number
  summary: string
  claimIds: string[]
  publicStatus: 'APPROVED'
  rawContentPublic: false
}

export interface ProjectDetail extends ProjectSummary {
  background: string
  responsibilities: string[]
  solution: string
  keyDecisions: string[]
  technologies: string[]
  verification: string[]
  outcome: string
  handoff: string
  evidence: Evidence[]
  suggestedQuestions: string[]
}
