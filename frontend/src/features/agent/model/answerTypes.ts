export type AnswerResolution =
  | 'ANSWERED'
  | 'AWAITING_CONFIRMATION'
  | 'NEEDS_CLARIFICATION'
  | 'NOT_SUPPORTED'
  | 'CAPABILITY_UNAVAILABLE'
  | 'REJECTED'
  | 'BOUNDARY'
  | 'INVALID_INPUT'
  // P3 顶层 resolution 新增（handoff §9）。
  | 'PARTIALLY_ANSWERED'
  | 'PRESENTATION_BLOCKED'
export type AnswerSource = 'PRESET' | 'RETRIEVAL' | 'TOOL'
// P4：顶层聚合新增 MIXED（设计 §11.3 / handoff §2.1）。
export type GenerationMode = 'DETERMINISTIC' | 'MODEL' | 'FALLBACK' | 'MIXED'
// P4：顶层构造模式新增 MIXED_COMPOSITION（设计 §11.3 / handoff §2.1）。
export type AnswerConstructionMode =
  | 'TEMPLATE'
  | 'EVIDENCE_COMPOSITION'
  | 'MODEL_GROUNDED'
  | 'GENERAL_MODEL'
  | 'MIXED_COMPOSITION'
// P4：单个 completed task 的表达来源（设计 §11.2 / handoff §2.2）。
// 与顶层 GenerationMode 不同：这里是任务级闭集，不含 MODEL/MIXED。
export type TaskCompositionMode = 'DETERMINISTIC' | 'MODEL_GROUNDED' | 'FALLBACK'
// P4：任务级表达状态。用于协议状态与测试，不要求在访客主界面展示（handoff §2.2/§3）。
export interface TaskComposition {
  mode: TaskCompositionMode
  degraded: boolean
}
export type AnswerIntentSource = 'PRESET' | 'RULE' | 'MODEL' | 'REFERENCE' | 'GLOBAL'
export type AnswerEvidenceState = 'VERIFIED' | 'NOT_REQUIRED' | 'INSUFFICIENT' | 'MIXED'
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
// P5 stp-v2：公共 Semantic Turn Contract 版本（设计 §17.2 / handoff §7）。
export type SemanticTurnContract = 'stp-v1' | 'stp-v2'
export type TurnDisposition =
  | 'READY'
  | 'PARTIAL_READY'
  | 'CONFIRMATION_REQUIRED'
  | 'CLARIFICATION_REQUIRED'
  | 'BOUNDARY'
  | 'REJECTED'
  // P5 stp-v2：Strict Context 失效（设计 §13.9 / handoff §3）。优先于 answerResolution，
  // 进入专用恢复卡；FE-0 仅保证安全解析不崩溃/不误入通用澄清，恢复卡 UI 在 FE-5。
  | 'CONTEXT_INVALIDATED'
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
  // TRANSITIONAL(p3-e): claimIds/evidenceIds 是旧 P2 公开引用，P3 最终删除。
  // P3 路径优先消费 sourceReferences；缺省时映射层只读旧 ID 维持旧 Evidence Desk。
  claimIds?: string[]
  evidenceIds?: string[]
  sourceReferences?: PublicSourceReference[]
  // ── P5 stp-v2（设计 §9.3 / handoff §5）──
  blockId?: string
  // 真实来源域，权威。SYNTHESIS 时旧 sourceScope 必须省略或 null（不得伪装 GENERAL）。
  sourceDomain?: SemanticSourceDomain
  support?: AnswerBlockSupport
}

// 统一章节视图：ConversationThread 与 Evidence Desk 只消费该结构。
export interface AnswerSectionView {
  key: string
  type: AnswerSectionType
  title: string
  sourceScope: BlockSourceScope
  content: string
  // TRANSITIONAL(p3-e): 旧 P2 引用，P3 删除。
  claimIds: string[]
  evidenceIds: string[]
  // P3：公开来源引用（handoff §8）。存在时优先于旧 evidenceIds 渲染。
  sourceReferences?: PublicSourceReference[]
  // ── P5 stp-v2（设计 §9.3 / §9.7）──
  blockId?: string
  sourceDomain?: SemanticSourceDomain
  support?: AnswerBlockSupport
}

// 结构化作品推荐（可选字段，仅推荐类回答出现）。
// items 顺序是后端权威顺序，前端不得重排/去重/增删。
export interface PortfolioRecommendationItem {
  portfolioId: string
  title: string
  route: string
  matchReasons: string[]
  // TRANSITIONAL(p3-e): evidenceIds 是旧 P2 公开引用，P3 最终删除。
  evidenceIds: string[]
  sourceReferences?: PublicSourceReference[]
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

/** 调整计划请求（stp-v1 §11.2）：自然语言调整绑定待确认计划引用，不编辑内部图。 */
export interface PlanAdjustmentRequest {
  instruction: string
  pendingPlanReference: PendingPlanReference
}

/** 澄清回答请求（stp-v1 §11.1）：受控 resolution，selectedOption 与 textValue 互斥。 */
export interface ClarificationResolutionRequest {
  clarificationId: string
  promptCode: string
  fieldKey: string
  selectedOption?: {
    value: string
    subjectReference?: SemanticSubjectReference
  }
  textValue?: string
}

export interface AgentTurnDisplayTaskResponse {
  displayIndex: string
  goalLabel: string
  sourceDomain: SemanticSourceDomain
  dependencySummary?: string
  // P5 stp-v2（设计 §10.4 / handoff）：履约角色，只读。
  fulfillmentRole?: FulfillmentRole
  [field: string]: unknown
}

export interface PendingPlanReference {
  planId: string
  planFingerprint: string
}

export interface AgentTurnDisplayPlanResponse {
  taskCount: number
  executableTaskCount?: number
  summaryLabel?: string | null
  tasks: AgentTurnDisplayTaskResponse[]
  constraints?: string[]
  [field: string]: unknown
}

export interface AgentTurnClarificationOptionResolution {
  kind: string
  subjectType: string
  subjectId: string
  [field: string]: unknown
}

export interface AgentTurnClarificationOptionResponse {
  value: string
  label: string
  resolution?: AgentTurnClarificationOptionResolution | null
  [field: string]: unknown
}

export type AgentTurnClarificationInputMode =
  | 'SINGLE_CHOICE'
  | 'MULTI_CHOICE'
  | 'SHORT_TEXT'

export interface AgentTurnClarificationFieldResponse {
  fieldKey: string
  inputMode: AgentTurnClarificationInputMode
  options: AgentTurnClarificationOptionResponse[]
  required: boolean
  affectedGoalLabels: string[]
  [field: string]: unknown
}

export interface AgentTurnClarificationBlockedGoalResponse {
  goalLabel: string
  reasonCode: string
  [field: string]: unknown
}

export interface AgentTurnClarificationResponse {
  clarificationId?: string
  scope: 'LOCAL' | 'CRITICAL'
  promptCode?: string
  prompt: string
  fields: AgentTurnClarificationFieldResponse[]
  blockedTaskCount: number
  continuingTaskCount: number
  continuingGoalLabels?: string[]
  blockedGoals?: AgentTurnClarificationBlockedGoalResponse[]
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
  reasonCodes?: string[]
  blockedByDisplayIndexes?: string[]
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

// P5 stp-v2：推荐结果项在 stp-v1 字段之外可选携带有序结果项身份（resultItemId/position/subject），
// 供「第二个继续」等显式结果项续接（设计 §12.12 / handoff §2）。
export interface AgentTurnRecommendationItemResponse extends Omit<PortfolioRecommendationItem, 'evidenceIds'> {
  evidenceIds?: string[]
  resultItemId?: string
  position?: number
  subject?: SemanticSubjectReference
}

export interface AgentTurnRecommendationResultResponse {
  kind: 'RECOMMENDATION_RESULT'
  recommendations: AgentTurnRecommendationItemResponse[]
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
  // P3：仅产生可续接 Context 的完成任务返回不透明 handle（handoff §6）。
  contextHandle?: string
  // P4：任务级表达状态（设计 §11.2 / handoff §2.2）。缺省视为未提供（兼容旧响应）。
  composition?: TaskComposition
  // P5 stp-v2（设计 §10.4/§9.4/§11.14，handoff §2/§4）：履约角色、支持聚合、续接句柄。
  fulfillmentRole?: FulfillmentRole
  supportSummary?: TaskSupportSummary
  continuationContext?: ContinuationContext
  [field: string]: unknown
}

export interface AgentTurnPlanConfirmationResponse extends PlanConfirmationSubmission {
  expiresAt: string
  triggerCodes: string[]
  pendingPlanReference?: PendingPlanReference
  [field: string]: unknown
}

export interface AgentTurnBaseResponse {
  // P5 stp-v2：迁移期同时接受 stp-v1/stp-v2（设计 §17.2）。
  contractVersion: 'stp-v1' | 'stp-v2'
  plan?: AgentTurnDisplayPlanResponse
  planChange?: AgentTurnPlanChangeResponse
  // P3：与 plan 同级的最终执行快照（handoff §7）。两者不可相互替代。
  execution?: ExecutionDisplayPlanResponse
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

// P5 stp-v2（设计 §13.9 / handoff §3）：Strict Context 失效。contextInvalidation 数据在
// AnswerResponse 顶层（与 agentTurn 同级），不在此处；映射层负责跨字段一致性校验。
export interface AgentTurnContextInvalidatedResponse extends AgentTurnBaseResponse {
  disposition: 'CONTEXT_INVALIDATED'
  clarification?: never
  planConfirmation?: never
  outcome?: never
  completedTasks?: never
}

/** Known response shapes after discriminant validation. */
export type AgentTurnResponse =
  | AgentTurnReadyResponse
  | AgentTurnConfirmationRequiredResponse
  | AgentTurnClarificationRequiredResponse
  | AgentTurnBoundaryResponse
  | AgentTurnContextInvalidatedResponse

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
  // P3：200 响应第一层判别字段（handoff §4）。过渡期 P2 后端缺省视为 ANSWER。
  responseKind?: 'ANSWER'
  // P3：会话续接状态与 ResumeToken（handoff §5）。
  conversation?: ConversationResponse
  // ── P5 stp-v2 顶层字段（均可选；迁移期新旧并存，见设计 §2.2）──
  sourceComposition?: SourceComposition
  publicSourceCatalog?: PublicSourceCatalogEntry[]
  degradationSummary?: PublicDegradationSummary
  caveats?: PublicAnswerCaveat[]
  contextInvalidation?: ContextInvalidation
  contextResolution?: ContextResolution
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
  // P3：会话续接状态与 ResumeToken（handoff §5）。
  conversation?: MappedConversation
  // ── P5 stp-v2 顶层字段（可选；FE-1 映射，FE-2..FE-5 渲染）──
  sourceComposition?: SourceComposition
  publicSourceCatalog?: PublicSourceCatalogEntry[]
  degradationSummary?: PublicDegradationSummary
  caveats?: PublicAnswerCaveat[]
  contextInvalidation?: ContextInvalidation
  contextResolution?: ContextResolution
}

// ── P3 视图模型 ──────────────────────────────────────────────────────────────

/** 会话续接信息视图（handoff §5）。resumeToken 仅首次签发或重签时存在。 */
export interface MappedConversation {
  resumeToken?: string
  continuationStatus: ConversationContinuationStatus
  activeContextSummary?: ConversationContextSummary
}

/** 完成回执任务视图（handoff §4）。 */
export interface MappedCompletionReceiptTask {
  displayIndex: string
  status: PublicTaskStatus
  contextHandle?: string
}

/** 完成回执视图（handoff §4）。不含伪造的 title/blocks/正文。 */
export interface MappedCompletionReceipt {
  turnId: string
  requestToken: string
  completedTasks: MappedCompletionReceiptTask[]
  conversation: MappedConversation
}

/** 映射后的 200 成功联合结果。 */
export type MappedAnswerSuccess =
  | { kind: 'ANSWER'; answer: MappedAnswer }
  | { kind: 'COMPLETION_RECEIPT'; receipt: MappedCompletionReceipt }
  | { kind: 'CONTRACT_ERROR'; responseKind: string }

// ─────────────────────────────────────────────────────────────────────────────
// P3 公共契约（p3-display-v1 / p3-context-summary-v1）
//
// 这些类型对应 P3-E 后端公共契约（handoff §3–§13）。前端只消费后端闭集字段，
// 不重建主体范围/推荐排序/证据判断/业务 Context，也不伪造工具调用过程。
//
// 过渡期（P3-E 前）：旧 AnswerBlock/PortfolioRecommendationItem 仍保留
// claimIds/evidenceIds，映射层在 sourceReferences 缺省时只读旧 ID 维持旧
// Evidence Desk；P3 路径仅在 sourceReferences 存在时生效。旧字段标注为
// TRANSITIONAL(p3-e)，在 P3-E 原子验收时删除。
// ─────────────────────────────────────────────────────────────────────────────

/** 公开来源类型闭集，复用公开 Evidence 类型（handoff §8）。 */
export type PublicSourceType =
  | 'COLLECTION'
  | 'DOCUMENT'
  | 'SCREENSHOT'
  | 'CODE'
  | 'TEST_RESULT'

/**
 * 公开来源引用（handoff §8）。
 * referenceKey 是公开稳定 code，不是 Claim/Evidence/Chunk 或数据库 ID；
 * subjectRoute/evidenceRoute 只接受站内相对公开路由，前端不得拼接对象存储地址。
 */
export interface PublicSourceReference {
  referenceKey: string
  label: string
  sourceType: PublicSourceType
  subjectRoute: string
  evidenceRoute?: string
  publishedVersion: string
}

/** 回答请求顶层强类型 Context 引用（handoff §3.2）。 */
export type ConversationContextType = 'RECENT_SEMANTIC_TASK' | 'RECOMMENDATION'

export interface ContextReferenceRequest {
  contextHandle: string
  expectedContextType: ConversationContextType
  // P5 stp-v2（设计 §12.12 / handoff §2）：Context 内的显式结果项选择；缺省=整个 Context。
  resultItemId?: string
}

/** 会话可续接性状态闭集（handoff §5）。与 degraded 是不同维度。 */
export type ConversationContinuationStatus =
  | 'AVAILABLE'
  | 'PERSISTENCE_UNAVAILABLE'
  | 'CONTEXT_EXPIRED'
  | 'CONTEXT_CLEARED'
  | 'NOT_APPLICABLE'

export type RecentTaskType = 'FACT' | 'COMPARE' | 'RECOMMENDATION' | 'REFINE'

/**
 * 刷新恢复用的安全 Context Summary（handoff §11）。
 * 恢复卡只显示这些服务端确定性字段，不得显示问题/答案/理由/handle/version。
 */
export interface ConversationContextSummary {
  recentTaskType?: RecentTaskType
  subjectLabels: string[]
  facetLabels: string[]
  comparisonDimensionLabels: string[]
  preferenceLabels: string[]
  canRefine: boolean
}

/** 回答响应中的会话信息（handoff §5）。resumeToken 仅首次签发或重签时返回。 */
export interface ConversationResponse {
  resumeToken?: string
  continuationStatus: ConversationContinuationStatus
  activeContextSummary?: ConversationContextSummary
}

/** GET /api/v2/conversation-context 响应（handoff §11）。 */
export interface ConversationContextSummaryResponse {
  contractVersion: 'p3-context-summary-v1'
  continuationStatus: ConversationContinuationStatus
  summary?: ConversationContextSummary
}

// ── 用户可见执行快照（FINAL，handoff §7）───────────────────────────────────
// 最终响应只返回 FINAL 快照；PENDING/IN_PROGRESS 属于契约错误，不得渲染为实时进度。

export type ExecutionFinalStatus = 'COMPLETED' | 'PARTIAL' | 'SKIPPED' | 'FAILED'

export type ExecutionStageCode =
  | 'SCOPE_CONFIRMED'
  | 'MATERIALS_RETRIEVED'
  | 'EVIDENCE_VALIDATED'
  | 'RESULT_COMPOSED'

export interface ExecutionDisplayStageResponse {
  code: ExecutionStageCode
  label: string
  status: ExecutionFinalStatus
}

export interface ExecutionDisplayTaskResponse {
  displayIndex: string
  finalStatus: ExecutionFinalStatus
  stages: ExecutionDisplayStageResponse[]
}

export interface ExecutionDisplayPlanResponse {
  contractVersion: 'p3-display-v1'
  snapshotType: 'FINAL'
  overallStatus: ExecutionFinalStatus
  tasks: ExecutionDisplayTaskResponse[]
}

// ── 幂等完成回执（handoff §4）──────────────────────────────────────────────

// P5 stp-v2 公共闭集（设计 §10.9 / handoff §1）：v2 权威值 + 迁移期保留的旧值。
// legacy→v2 归一在任务摘要 UI（FE-3）统一处理；这里仅声明前端已知全集。
export type PublicTaskStatus =
  | 'COMPLETED'
  | 'PARTIAL'
  | 'EMPTY'
  | 'NOT_SUPPORTED'
  | 'NOT_APPLICABLE'
  | 'BLOCKED'
  | 'UNAVAILABLE'
  | 'STALE'
  | 'FAILED'
  | 'REJECTED'
  | 'NOT_EXECUTED'
  // ── 迁移期旧值（handoff §1）──
  | 'PRESENTATION_BLOCKED'
  | 'DEPENDENCY_UNAVAILABLE'
  | 'NOT_EXECUTED_BUDGET'
  | 'CANCELLED'

export interface CompletionReceiptTask {
  displayIndex: string
  status: PublicTaskStatus
  contextHandle?: string
}

export interface CompletionReceiptResponse {
  responseKind: 'COMPLETION_RECEIPT'
  turnId: string
  requestToken: string
  requestStatus: 'REQUEST_ALREADY_COMPLETED'
  completedTasks: CompletionReceiptTask[]
  conversation: ConversationResponse
}

// ── P5 stp-v2 公共契约类型（设计 §9/§10/§13，handoff §1—§6）─────────────────
// 所有字段在后端为闭集；前端按 fail-closed 消费未知值（enumSafety）。FE-0 仅声明类型
// 与可选字段；完整解析/映射在 FE-1，视觉在 FE-2..FE-5。

export type SourceComposition =
  | 'GENERAL_ONLY'
  | 'PORTFOLIO_ONLY'
  | 'MULTI_SOURCE'          // General+Portfolio 并列，无成功 Synthesis
  | 'CROSS_DOMAIN_DERIVED'  // 至少一个合法 Synthesis Block

export type AnswerSupportKind =
  | 'VERIFIED_PUBLIC_EVIDENCE' // 仅 P3 Evidence Promotion 的 Portfolio 内容
  | 'GENERAL_KNOWLEDGE'        // 明确不是 Portfolio Evidence
  | 'DERIVED_FROM_TASKS'       // 仅通过 Relation Policy + 跨域 Validator 的 Synthesis

// P5 §9.3：Block 级支持明细（权威）。前端不要求展示完整内部 Material。
export interface AnswerBlockSupport {
  kind: AnswerSupportKind
  statementReferences: StatementSupportReference[]
  sourceTaskIds: string[]
  publicSourceKeys: string[]
  contentVersion?: string
}

// 响应级 Provenance：消费者可校验来源链；前端不要求展示或理解完整内部 Material（§9.3）。
export interface StatementSupportReference {
  statementId: string
  sourceTaskId?: string
  publicSourceKeys?: string[]
  [field: string]: unknown
}

// P5 §10.4：履约角色，由服务端按用户目标判定；前端只读、不推断、不重排。
export type FulfillmentRole = 'PRIMARY' | 'SUPPORTING' | 'OPTIONAL'

// P5 §9.4：Task 级支持聚合投影（Block Support 是权威明细）。
export interface TaskSupportSummary {
  kind: AnswerSupportKind
  statementCount: number
  publicSourceCount: number
  sourceTaskCount?: number // 仅 Synthesis
  contentVersion?: string
}

// P5 §12.12：有序结果项（定稿落在 completedTasks[].resultPayload.recommendations[]，handoff §2）。
export interface OrderedResultItem {
  resultItemId: string
  position: number
  subject: SemanticSubjectReference
}

export type PublicDegradationKind =
  | 'RETRIEVAL_FALLBACK'
  | 'EXPRESSION_FALLBACK'
  | 'CROSS_DOMAIN_EXPRESSION_FALLBACK'
  | 'CONTENT_BACKEND_FALLBACK'

export interface PublicDegradationSummary {
  degraded: boolean
  kinds: PublicDegradationKind[]
  affectedTaskIds: string[]
}

export interface PublicAnswerCaveat {
  code: string
  message: string
  appliesToBlockIds: string[]
  sourceTaskIds: string[]
}

// 与 PublicSourceReference 同构；顶层 publicSourceCatalog 以 referenceKey 去重（设计 §9.7）。
export interface PublicSourceCatalogEntry {
  referenceKey: string
  label: string
  sourceType: PublicSourceType
  subjectRoute: string
  evidenceRoute?: string
  publishedVersion: string
}

// Context 失效与版本（设计 §13，handoff §6）
export type ContextInvalidationRecoveryAction =
  | 'RESTART_FROM_CURRENT_CONTENT'
  | 'RESELECT_RESULTS'
  | 'REASK_WITHOUT_CONTEXT'

// 前端已知 reasonCode 白名单；后端可能产出其它安全码，未知码 fail-closed 文案。
export type ContextInvalidationReasonCode =
  | 'CONTEXT_REFERENCE_INVALID'
  | 'CONTEXT_REFERENCE_EXPIRED'
  | 'CONTEXT_RESULT_STALE'
  | 'REFERENCED_SUBJECT_UNAVAILABLE'
  | 'REFERENCED_PUBLIC_SOURCE_CHANGED'
  | 'CONTEXT_RESOLUTION_UNAVAILABLE'
  | 'ROUTING_CONTEXT_CONFLICT'
  | 'CONTINUATION_GOAL_UNRESOLVED'
  | 'CONTEXT_SUBJECT_REQUIRED'
  | 'RESULT_POSITION_OUT_OF_RANGE'
  | 'RESULT_CONTEXT_AMBIGUITY'

export interface ContextInvalidation {
  reasonCode: string
  recoveryAction: ContextInvalidationRecoveryAction
  contextType: ConversationContextType
  currentContentVersion: string
}

export type ContextResolutionMode = 'REVALIDATED_TO_CURRENT'

export interface ContextResolution {
  mode: ContextResolutionMode
  contextType: ConversationContextType
  currentContentVersion: string
}

// 每个可继续完成任务的续接句柄（completedTasks[].continuationContext，非顶层；handoff §4）。
export interface ContinuationContext {
  contextHandle: string
  contextType: ConversationContextType
  sourceTaskId: string
}

/** POST /api/v2/answers 的 200 成功联合类型（handoff §4）。 */
export type P3AnswerSuccess = AnswerResponse | CompletionReceiptResponse

export type ResolvedAnswerSuccess =
  | { kind: 'ANSWER'; response: AnswerResponse }
  | { kind: 'COMPLETION_RECEIPT'; response: CompletionReceiptResponse }
  | { kind: 'CONTRACT_ERROR'; responseKind: string }

/**
 * 200 响应第一层判别（handoff §4）：必须先按 responseKind 分流，禁止靠 blocks 猜类型。
 * 缺 responseKind 视为 ANSWER（过渡期 P2 后端兼容）；未知值进入契约错误分支。
 */
export function resolveAnswerSuccess(response: P3AnswerSuccess): ResolvedAnswerSuccess {
  const raw = (response as { responseKind?: unknown }).responseKind
  if (raw === 'COMPLETION_RECEIPT') {
    return { kind: 'COMPLETION_RECEIPT', response: response as CompletionReceiptResponse }
  }
  if (raw === undefined || raw === 'ANSWER') {
    return { kind: 'ANSWER', response: response as AnswerResponse }
  }
  return { kind: 'CONTRACT_ERROR', responseKind: String(raw) }
}
