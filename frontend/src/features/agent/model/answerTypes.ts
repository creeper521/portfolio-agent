export type AnswerResolution = 'ANSWERED' | 'BOUNDARY' | 'REJECTED'
export type AnswerSource = 'PRESET' | 'RETRIEVAL'
export type GenerationMode = 'DETERMINISTIC' | 'MODEL' | 'FALLBACK'
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

export type FollowUpIntent =
  | 'EXPAND_SECTION'
  | 'SHOW_EVIDENCE'
  | 'EXPLAIN_DECISION'
  | 'COMPARE_PROJECTS'
  | 'CURRENT_STATUS'
  | 'RELATED_QUESTION'

export type AnswerIntent =
  | 'CONVERSATION'
  | 'GENERAL_KNOWLEDGE'
  | 'PORTFOLIO_GROUNDED'
  | 'HYBRID'
  | 'TIME_SENSITIVE'
  | 'UNSUPPORTED_OR_UNSAFE'

export type AnswerScope = 'CONVERSATION' | 'GENERAL' | 'PORTFOLIO' | 'HYBRID'
export type BlockSourceScope = 'GENERAL' | 'PORTFOLIO'
export type PortfolioKnowledgeFacet =
  | 'OVERVIEW'
  | 'IMPLEMENTATION'
  | 'DECISION'
  | 'CHALLENGE'
  | 'INCIDENT'
  | 'VERIFICATION'
  | 'LIMITATION'
  | 'LEARNING'

export interface ConversationSuggestedQuestion {
  text: string
  projectSlug: string | null
  caseSlug: string | null
  facet: PortfolioKnowledgeFacet | null
}

export interface ContextEnvelope {
  previousContentVersion: string
  projectSlugs?: string[]
  caseSlugs?: string[]
  questionPresetId?: string
  referencedClaimIds: string[]
  selectedSectionType?: AnswerSectionType
  followUpIntent?: FollowUpIntent
}

export interface FollowUpAction {
  question: string
  contextEnvelope: ContextEnvelope
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

export interface AnswerResponse {
  requestId?: string
  turnId: string
  contentVersion: string
  questionPresetId?: string
  intent?: AnswerIntent
  answerScope?: AnswerScope
  resolution: AnswerResolution
  answerSource?: AnswerSource
  generationMode?: GenerationMode
  verification?: Verification
  title: string
  summary?: string // maybe missing in v2
  sections?: AnswerSection[] // legacy v1
  blocks?: AnswerBlock[] // new v2
  evidenceIds?: string[]
  suggestedQuestionPresetIds?: string[] // legacy v1
  suggestedQuestions?: Array<string | ConversationSuggestedQuestion>
  degraded?: boolean
  contextEnvelope?: ContextEnvelope
  contextVersionUpdated?: boolean
}

export interface MappedAnswer {
  title: string
  summary: string
  sections: AnswerSection[]
  blocks?: AnswerBlock[]
  intent?: AnswerIntent
  answerScope?: AnswerScope
  resolution: AnswerResolution
  answerSource: AnswerSource | null
  generationMode?: GenerationMode
  verification?: Verification
  evidenceIds: string[]
  suggestedQuestionPresetIds: string[]
  suggestedQuestions: ConversationSuggestedQuestion[]
  degraded?: boolean
  contextEnvelope?: ContextEnvelope
  contextVersionUpdated?: boolean
}
