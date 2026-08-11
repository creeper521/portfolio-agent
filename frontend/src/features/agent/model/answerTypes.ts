export type AnswerResolution =
  | 'ANSWERED'
  | 'AWAITING_CONFIRMATION'
  | 'NEEDS_CLARIFICATION'
  | 'NOT_SUPPORTED'
  | 'CAPABILITY_UNAVAILABLE'
  | 'REJECTED'
  | 'BOUNDARY'
  | 'INVALID_INPUT'
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
export type SemanticSourceDomain = 'GENERAL' | 'PORTFOLIO' | 'SYNTHESIS'
export type TurnAction = 'ASK' | 'CONFIRM_PLAN' | 'REGENERATE_PLAN'
export type TurnDisposition =
  | 'READY'
  | 'PARTIAL_READY'
  | 'CONFIRMATION_REQUIRED'
  | 'CLARIFICATION_REQUIRED'
  | 'BOUNDARY'
  | 'REJECTED'
export type TaskSummaryDisplayMode = 'HIDDEN' | 'COLLAPSED' | 'EXPANDED'
export type TaskSummaryStatus =
  | 'COMPLETED'
  | 'EVIDENCE_INSUFFICIENT'
  | 'NOT_SUPPORTED'
  | 'EMPTY'
  | 'BLOCKED'
  | 'FAILED'
  | 'CANCELLED'
export type PlanOutcome = 'SUCCEEDED' | 'PARTIAL' | 'NO_RESULT' | 'FAILED' | 'CANCELLED'
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
export interface LegacyAnswerSection {
  type: AnswerSectionType
  title: string
  content: string
  evidenceIds: string[]
  claimIds?: string[]
}

export interface AnswerBlock {
  sourceScope: BlockSourceScope
  sectionType?: AnswerSectionType
  title?: string
  content: string
  claimIds: string[]
  evidenceIds: string[]
}

// 统一章节视图：ConversationThread 与 Evidence Desk 只消费该结构。
export interface AnswerSectionView {
  key: string
  type: AnswerSectionType
  title: string
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

export interface PlanConfirmationSubmission {
  confirmationId: string
  confirmationPlan: string
  planFingerprint: string
  integrityToken: string
}

export interface SemanticSubjectReference {
  subjectType: string
  subjectId: string
}

export interface SemanticContextRequest {
  activeSubjects?: SemanticSubjectReference[]
  resultReferences?: Array<{ referenceType: string; referenceId: string }>
  pendingPlanReference?: { planId: string; planFingerprint: string }
  audienceRole?: string
  requestSource?: string
  coveredTopics?: ConversationTopic[]
}

export interface InvalidatedPlanReference {
  planId: string
  planFingerprint: string
}

export interface AgentTurnDisplayTaskResponse {
  displayIndex: string
  goalLabel: string
  sourceDomain: SemanticSourceDomain
  dependencySummary?: string
  [field: string]: unknown
}

export interface AgentTurnDisplayPlanResponse {
  taskCount: number
  executableTaskCount?: number
  tasks: AgentTurnDisplayTaskResponse[]
  constraints?: string[]
  [field: string]: unknown
}

export interface AgentTurnClarificationOptionResponse {
  value: string
  label: string
  [field: string]: unknown
}

export interface AgentTurnClarificationFieldResponse {
  fieldKey: string
  inputMode: 'SINGLE_CHOICE'
  options: AgentTurnClarificationOptionResponse[]
  required: boolean
  affectedGoalLabels: string[]
  [field: string]: unknown
}

export interface AgentTurnClarificationResponse {
  scope: 'LOCAL' | 'CRITICAL'
  prompt: string
  fields: AgentTurnClarificationFieldResponse[]
  blockedTaskCount: number
  continuingTaskCount: number
  [field: string]: unknown
}

export interface AgentTurnPlanChangeResponse {
  summary: string
  changeLabels: string[]
  invalidatedPlanReference?: InvalidatedPlanReference
  [field: string]: unknown
}

export interface AgentTurnTaskSummaryItemResponse {
  displayIndex: string
  goalLabel: string
  status: TaskSummaryStatus
  sourceDomain: SemanticSourceDomain
  [field: string]: unknown
}

export interface AgentTurnTaskSummaryResponse {
  displayMode: TaskSummaryDisplayMode
  totalCount: number
  answeredCount: number
  notSupportedCount: number
  emptyCount: number
  blockedCount: number
  failedCount: number
  cancelledCount: number
  degradedCount: number
  items: AgentTurnTaskSummaryItemResponse[]
  [field: string]: unknown
}

export interface AgentTurnOutcomeResponse {
  planOutcome: PlanOutcome
  taskSummary?: AgentTurnTaskSummaryResponse
  [field: string]: unknown
}

export interface AgentTurnSectionResultResponse {
  kind: 'SECTION_RESULT'
  blocks: AnswerBlock[]
  [field: string]: unknown
}

export interface AgentTurnRecommendationResultResponse {
  kind: 'RECOMMENDATION_RESULT'
  recommendations: PortfolioRecommendationItem[]
  [field: string]: unknown
}

export interface AgentTurnSynthesisResultResponse {
  kind: 'SYNTHESIS_RESULT'
  blocks: AnswerBlock[]
  originDomains: SemanticSourceDomain[]
  [field: string]: unknown
}

export type AgentTurnResultPayloadResponse =
  | AgentTurnSectionResultResponse
  | AgentTurnRecommendationResultResponse
  | AgentTurnSynthesisResultResponse

export interface AgentTurnCompletedTaskResponse {
  displayIndex: string
  goalLabel: string
  sourceDomain: SemanticSourceDomain
  resultPayload: AgentTurnResultPayloadResponse
  [field: string]: unknown
}

export interface AgentTurnPlanConfirmationResponse extends PlanConfirmationSubmission {
  expiresAt: string
  triggerCodes: string[]
  [field: string]: unknown
}

export interface AgentTurnBaseResponse {
  contractVersion: 'stp-v1'
  plan?: AgentTurnDisplayPlanResponse
  planChange?: AgentTurnPlanChangeResponse
  [field: string]: unknown
}

export interface AgentTurnReadyResponse extends AgentTurnBaseResponse {
  disposition: 'READY' | 'PARTIAL_READY'
  outcome: AgentTurnOutcomeResponse
  completedTasks: AgentTurnCompletedTaskResponse[]
  clarification?: AgentTurnClarificationResponse
  planConfirmation?: never
}

export interface AgentTurnConfirmationRequiredResponse extends AgentTurnBaseResponse {
  disposition: 'CONFIRMATION_REQUIRED'
  planConfirmation: AgentTurnPlanConfirmationResponse
  clarification?: never
  outcome?: never
  completedTasks?: never
}

export interface AgentTurnClarificationRequiredResponse extends AgentTurnBaseResponse {
  disposition: 'CLARIFICATION_REQUIRED'
  clarification: AgentTurnClarificationResponse
  planConfirmation?: never
  outcome?: never
  completedTasks?: never
}

export interface AgentTurnBoundaryResponse extends AgentTurnBaseResponse {
  disposition: 'BOUNDARY' | 'REJECTED'
  clarification?: never
  planConfirmation?: never
  outcome?: never
  completedTasks?: never
}

/** Known stp-v1 response shapes after discriminant validation. */
export type AgentTurnResponse =
  | AgentTurnReadyResponse
  | AgentTurnConfirmationRequiredResponse
  | AgentTurnClarificationRequiredResponse
  | AgentTurnBoundaryResponse

/** Raw wire fallback; never passed to rendering without semantic narrowing. */
export interface RawAgentTurnResponse {
  contractVersion?: unknown
  disposition: string
  [field: string]: unknown
}

export type AgentTurnPayload = AgentTurnResponse | RawAgentTurnResponse

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
  sections?: LegacyAnswerSection[] // legacy v1
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
  agentTurn?: AgentTurnPayload
}

export interface MappedAnswer {
  turnId: string
  contentVersion: string
  contractVersion?: string
  title: string
  summary: string
  sections: AnswerSectionView[]
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
  semanticTurn?: import('./semanticTurnView').SemanticTurnView
}
