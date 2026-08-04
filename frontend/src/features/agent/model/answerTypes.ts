export type AnswerResolution =
  | 'ANSWERED'
  | 'NEEDS_CLARIFICATION'
  | 'NOT_SUPPORTED'
  | 'CAPABILITY_UNAVAILABLE'
  | 'REJECTED'
  | 'BOUNDARY'
export type AnswerSource = 'PRESET' | 'RETRIEVAL' | 'TOOL'
export type GenerationMode = 'DETERMINISTIC' | 'MODEL' | 'FALLBACK'
export type AnswerConstructionMode =
  | 'TEMPLATE'
  | 'EVIDENCE_COMPOSITION'
  | 'MODEL_GROUNDED'
  | 'GENERAL_MODEL'
export type AnswerIntentSource = 'PRESET' | 'RULE' | 'MODEL' | 'REFERENCE' | 'GLOBAL'
export type AnswerEvidenceState = 'VERIFIED' | 'NOT_REQUIRED' | 'INSUFFICIENT'
export type Verification =
  | 'VERIFIED'
  | 'PARTIALLY_VERIFIED'
  | 'UNVERIFIED'
  | 'NOT_APPLICABLE'

export type AnswerSectionType =
  | 'BACKGROUND'
  | 'RESPONSIBILITY'
  | 'SOLUTION'
  | 'VERIFICATION'
  | 'STATUS'
  | 'BOUNDARY'
  | 'REJECTED'
  | 'GENERAL' // added for compatibility, though sourceScope handles it better

export type PortfolioFollowUpAction =
  | 'EXPAND_SECTION'
  | 'SHOW_EVIDENCE'
  | 'EXPLAIN_DECISION'
  | 'COMPARE_SUBJECTS'
  | 'CURRENT_STATUS'
  | 'RELATED_QUESTION'

export type AnswerIntent =
  | 'CONVERSATION'
  | 'GENERAL_KNOWLEDGE'
  | 'PORTFOLIO_GROUNDED'
  | 'HYBRID'
  | 'TIME_SENSITIVE'
  | 'UNSUPPORTED_OR_UNSAFE'

export type AnswerScope =
  | 'GLOBAL'
  | 'GENERAL'
  | 'PORTFOLIO'
  | 'MIXED'
  | 'CONVERSATION'
  | 'HYBRID'
export type BlockSourceScope = 'GENERAL' | 'PORTFOLIO'
export type PortfolioKnowledgeFacet =
  | 'OVERVIEW'
  | 'RESPONSIBILITY'
  | 'IMPLEMENTATION'
  | 'DECISION'
  | 'CHALLENGE'
  | 'INCIDENT'
  | 'VERIFICATION'
  | 'OUTCOME'
  | 'LIMITATION'
  | 'LEARNING'

export type ConversationTopic =
  | 'BACKGROUND'
  | 'RESPONSIBILITY'
  | 'SOLUTION'
  | 'TRADEOFF'
  | 'FAILURE'
  | 'VERIFICATION'
  | 'OUTCOME'

export type ConversationGuidanceStage =
  | 'OPENING'
  | 'DEEPENING'
  | 'WRAP_UP'
  | 'EXPLORE_OTHERS'

export interface ConversationSuggestedQuestion {
  text: string
  projectSlug: string | null
  caseSlug: string | null
  facet: PortfolioKnowledgeFacet | null
}

export interface PortfolioReferenceContext {
  previousContentVersion: string
  projectSlugs?: string[]
  caseSlugs?: string[]
  questionPresetId?: string
  referencedClaimIds: string[]
  selectedSectionType?: AnswerSectionType
  followUpAction: PortfolioFollowUpAction
}

export interface FollowUpAction {
  question: string
  referenceContext: PortfolioReferenceContext
}

// Keep AnswerSection for backward compatibility or map v2 blocks to it
export interface AnswerSection {
  type: AnswerSectionType
  title: string
  content: string
  evidenceIds: string[]
  claimIds?: string[]
}

export interface AnswerBlock {
  sourceScope: BlockSourceScope
  content: string
  claimIds: string[]
  evidenceIds: string[]
}

// 结构化作品推荐（可选字段，仅推荐类回答出现）。
// items 顺序是后端权威顺序，前端不得重排/去重/增删。
export interface PortfolioRecommendationItem {
  portfolioId: string
  title: string
  route: string
  matchReasons: string[]
  evidenceIds: string[]
}

export interface PortfolioRecommendationContext {
  recommendationBatchId: string
  contentVersion: string
  careerTrack: string | null
  audienceRole: string
  capabilityCodes: string[]
  requestedSize: number
  selectedPortfolioIds: string[]
}

export type PortfolioRecommendationContextRequest = PortfolioRecommendationContext

export interface PortfolioRecommendation {
  recommendationBatchId: string
  context: PortfolioRecommendationContext
  items: PortfolioRecommendationItem[]
  satisfiedConstraints: string[]
  unsatisfiedConstraints: string[]
}

export interface AnswerResponse {
  requestId?: string
  turnId: string
  contentVersion: string
  questionPresetId?: string
  contractVersion?: string
  intent?: AnswerIntent
  answerScope?: AnswerScope
  resolution: AnswerResolution
  answerSource?: AnswerSource
  generationMode?: GenerationMode
  constructionMode?: AnswerConstructionMode
  intentSource?: AnswerIntentSource
  evidenceState?: AnswerEvidenceState
  verification?: Verification
  title: string
  summary?: string // maybe missing in v2
  sections?: AnswerSection[] // legacy v1
  blocks?: AnswerBlock[] // new v2
  evidenceIds?: string[]
  suggestedQuestionPresetIds?: string[] // legacy v1
  suggestedQuestions?: Array<string | ConversationSuggestedQuestion>
  coveredTopics?: ConversationTopic[]
  guidanceStage?: ConversationGuidanceStage
  degraded?: boolean
  noticeCode?: string
  referenceContext?: PortfolioReferenceContext
  contextVersionUpdated?: boolean
  portfolioRecommendation?: PortfolioRecommendation
}

export interface MappedAnswer {
  turnId: string
  contentVersion: string
  contractVersion?: string
  title: string
  summary: string
  sections: AnswerSection[]
  blocks?: AnswerBlock[]
  intent?: AnswerIntent
  answerScope?: AnswerScope
  resolution: AnswerResolution
  answerSource: AnswerSource | null
  generationMode?: GenerationMode
  constructionMode?: AnswerConstructionMode
  intentSource?: AnswerIntentSource
  evidenceState?: AnswerEvidenceState
  verification?: Verification
  evidenceIds: string[]
  suggestedQuestionPresetIds: string[]
  suggestedQuestions: ConversationSuggestedQuestion[]
  coveredTopics: ConversationTopic[]
  guidanceStage: ConversationGuidanceStage | null
  degraded?: boolean
  referenceContext?: PortfolioReferenceContext
  contextVersionUpdated?: boolean
  portfolioRecommendation?: PortfolioRecommendation
}
