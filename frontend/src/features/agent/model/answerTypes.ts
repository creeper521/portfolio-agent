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

export type FollowUpIntent =
  | 'EXPAND_SECTION'
  | 'SHOW_EVIDENCE'
  | 'EXPLAIN_DECISION'
  | 'COMPARE_PROJECTS'
  | 'CURRENT_STATUS'
  | 'RELATED_QUESTION'

export interface ContextEnvelope {
  previousContentVersion: string
  projectSlugs: string[]
  questionPresetId?: string
  referencedClaimIds: string[]
  selectedSectionType?: AnswerSectionType
  followUpIntent?: FollowUpIntent
}

export interface FollowUpAction {
  question: string
  contextEnvelope: ContextEnvelope
}

export interface AnswerSection {
  type: AnswerSectionType
  title: string
  content: string
  evidenceIds: string[]
  claimIds?: string[]
}

export interface AnswerResponse {
  requestId: string
  turnId: string
  contentVersion: string
  questionPresetId?: string
  resolution: AnswerResolution
  answerSource?: AnswerSource
  generationMode: GenerationMode
  verification: Verification
  title: string
  summary: string
  sections: AnswerSection[]
  evidenceIds: string[]
  suggestedQuestionPresetIds: string[]
  contextEnvelope?: ContextEnvelope
  contextVersionUpdated?: boolean
}

export interface MappedAnswer {
  title: string
  summary: string
  sections: AnswerSection[]
  resolution: AnswerResolution
  answerSource: AnswerSource | null
  generationMode: GenerationMode
  verification: Verification
  evidenceIds: string[]
  suggestedQuestionPresetIds: string[]
  contextEnvelope?: ContextEnvelope
  contextVersionUpdated?: boolean
}
