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
  claimIds: string[]
  publicStatus: 'APPROVED'
  projectSlugs: string[]
}

export interface PublicClaim {
  id: string
  subjectType: 'OWNER' | 'PROJECT' | 'INTERNSHIP'
  subjectId: string
  category: 'BACKGROUND' | 'RESPONSIBILITY' | 'TECHNICAL_DECISION' | 'IMPLEMENTATION' | 'VERIFICATION' | 'OUTCOME' | 'LIMITATION' | 'LEARNING' | 'REFLECTION'
  statement: string
  detail: string
  achievementStatus: 'DELIVERED' | 'IMPLEMENTED_TESTED' | 'PROTOTYPE' | 'DESIGNED' | 'LEARNING' | 'PLANNED' | 'UNKNOWN'
  contributionType: ContributionType
  verificationBasis: 'EVIDENCE_SUPPORTED' | 'SELF_DECLARED' | 'INFERRED' | 'UNSUPPORTED'
  verificationStatus: 'VERIFIED' | 'PARTIALLY_VERIFIED' | 'UNVERIFIED'
  materiality: 'KEY' | 'SUPPORTING'
  topics: string[]
  audiencePriorities: Partial<Record<AudienceRole, number>>
}

export interface PublicClaimEvidenceLink {
  id: string
  claimId: string
  evidenceId: string
  supportType: 'DIRECT' | 'CORROBORATING' | 'CONTEXTUAL'
  scope: string
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

export interface QuestionPreset {
  id: string
  projectSlug: string
  text: string
  audiences: AudienceRole[]
  placements: Array<'HOME' | 'AGENT' | 'PROJECT'>
}

export interface PublicPortfolio {
  contentVersion: string
  runtimeBundleHash: string
  publishedAt: string
  owner: PublicOwner
  projects: PublicProject[]
  claims: PublicClaim[]
  claimEvidenceLinks: PublicClaimEvidenceLink[]
  evidence: PublicEvidence[]
  timeline: TimelineEvent[]
  questionPresets: QuestionPreset[]
}
