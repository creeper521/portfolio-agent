// D-38 PublicAgentTurn 闭合 wire 合同的前端消费者类型。
// 只表达冻结结构（共享 Golden Fixtures + 前端交接 §4～§6）；
// 未知附加字段按 additive evolution 忽略，不在此建模，
// 也不承载任何旧合同（disposition/completedTasks/degraded 等）的兼容形状。

export type PublicAgentTurnKind =
  | 'ANSWER'
  | 'CLARIFICATION'
  | 'CONVERSATIONAL'
  | 'BOUNDARY'
  | 'CAPABILITY_UNAVAILABLE'

export type AnswerResolution = 'COMPLETE' | 'PARTIAL' | 'NO_RESULT'

export type GoalCoverage = 'FULL' | 'PARTIAL' | 'NONE'

export type SupportKind =
  | 'GENERAL_KNOWLEDGE'
  | 'VERIFIED_PUBLIC_EVIDENCE'
  | 'DERIVED'

export interface PublicSupport {
  readonly kind: SupportKind
  readonly publicSourceKeys: readonly string[]
}

/** 唯一公开来源权威 answer.sourceCatalog 中的条目；route 只允许站内相对路径。 */
export interface PublicSourceReference {
  readonly key: string
  readonly label: string
  readonly route: string
  readonly code?: string
  readonly type?: string
}

export interface PublicSourceCatalog {
  readonly sources: readonly PublicSourceReference[]
}

/** Goal 级用户安全缺口说明；code 为后端冻结的稳定公共码。 */
export interface GoalNotice {
  readonly code: string
  readonly message: string
}

/** 续接引用：contextHandle 定位公开 Goal Result，resultItemId 选择其中一项。 */
export interface ContinuationReference {
  readonly contextHandle: string
  readonly resultItemId?: string
}

/** 后端是业务 action 唯一权威；前端只转发 actionId/inputText/continuation。 */
export interface SuggestedAction {
  readonly actionId: string
  readonly label: string
  readonly inputText?: string
  readonly continuation?: ContinuationReference
}

/** S5-01 冻结的 sectionKind 闭集（前端交接 §16.1）。 */
export type PublicSectionKind =
  | 'BACKGROUND'
  | 'RESPONSIBILITY'
  | 'SOLUTION'
  | 'VERIFICATION'
  | 'STATUS'
  | 'BOUNDARY'
  | 'GENERAL_PRINCIPLE'
  | 'PORTFOLIO_EXAMPLE'
  | 'RELATION'

export interface PublicSection {
  readonly sectionId: string
  readonly sectionKind: PublicSectionKind
  readonly title: string
  readonly content: string
  readonly support: PublicSupport
}

/** S5-01 冻结的推荐结果项字段（前端交接 §16.2）。 */
export interface RecommendationItem {
  readonly resultItemId?: string
  readonly label: string
  readonly summary: string
  readonly route: string
  readonly reasons: readonly string[]
  readonly support: PublicSupport
}

export interface SectionedPresentation {
  readonly kind: 'SECTIONED'
  readonly sections: readonly PublicSection[]
}

export interface RecommendationPresentation {
  readonly kind: 'RECOMMENDATION'
  readonly requestedSize: number
  readonly actualSize: number
  readonly items: readonly RecommendationItem[]
  readonly unsatisfiedConstraints: readonly string[]
  readonly incompleteReasons: readonly string[]
  readonly supportingSections: readonly PublicSection[]
}

export type GoalPresentation = SectionedPresentation | RecommendationPresentation

export interface AnswerGoalResult {
  readonly goalId: string
  readonly label: string
  readonly coverage: GoalCoverage
  readonly presentation?: GoalPresentation
  readonly notices: readonly GoalNotice[]
  readonly continuation?: ContinuationReference
}

export interface ClarificationChoice {
  readonly choiceId: string
  readonly label: string
}

export interface SingleChoiceField {
  readonly kind: 'SINGLE_CHOICE'
  readonly fieldId: string
  readonly label: string
  readonly required?: boolean
  readonly choices: readonly ClarificationChoice[]
}

export interface TextField {
  readonly kind: 'TEXT'
  readonly fieldId: string
  readonly label: string
  readonly required?: boolean
  readonly limit?: number
}

export type ClarificationField = SingleChoiceField | TextField

/** 澄清表单提交答案：SINGLE_CHOICE 提交 opaque choiceId，TEXT 提交 bounded 文本。 */
export type ClarificationFieldAnswer =
  | { readonly fieldId: string; readonly kind: 'SINGLE_CHOICE'; readonly choiceId: string }
  | { readonly fieldId: string; readonly kind: 'TEXT'; readonly text: string }

export interface ClarificationSubmissionPayload {
  readonly clarificationId: string
  readonly answers: readonly ClarificationFieldAnswer[]
}

/** 澄清挑战使用 opaque id；前端不接触 promptCode、subject binding 或内部 Task。 */
export interface ClarificationChallenge {
  readonly clarificationId: string
  readonly prompt: string
  readonly fields: readonly ClarificationField[]
}

/** ANSWER 内的局部澄清：贴在受影响 Goal 下，affectedGoalIds 引用同一 answer 的 goalResult。 */
export interface LocalClarification extends ClarificationChallenge {
  readonly affectedGoalIds: readonly string[]
}

export interface PublicAnswer {
  readonly resolution: AnswerResolution
  readonly contentReleaseId: string
  readonly goalResults: readonly AnswerGoalResult[]
  readonly sourceCatalog: PublicSourceCatalog
  readonly sourceComposition: readonly SupportKind[]
  readonly suggestedActions?: readonly SuggestedAction[]
  readonly localClarification?: LocalClarification
}

export interface AnswerTurn {
  readonly kind: 'ANSWER'
  readonly requestId: string
  readonly answer: PublicAnswer
}

export interface ClarificationTurn {
  readonly kind: 'CLARIFICATION'
  readonly requestId: string
  readonly message: string
  readonly clarification: ClarificationChallenge
  readonly suggestedActions?: readonly SuggestedAction[]
}

export interface ConversationalTurn {
  readonly kind: 'CONVERSATIONAL'
  readonly requestId: string
  readonly message: string
  readonly suggestedActions?: readonly SuggestedAction[]
}

export interface BoundaryTurn {
  readonly kind: 'BOUNDARY'
  readonly requestId: string
  readonly code: string
  readonly message: string
  readonly suggestedActions?: readonly SuggestedAction[]
}

export interface CapabilityUnavailableTurn {
  readonly kind: 'CAPABILITY_UNAVAILABLE'
  readonly requestId: string
  readonly code: string
  readonly message: string
  readonly retryable?: boolean
  readonly suggestedActions?: readonly SuggestedAction[]
}

export type PublicAgentTurn =
  | AnswerTurn
  | ClarificationTurn
  | ConversationalTurn
  | BoundaryTurn
  | CapabilityUnavailableTurn

export interface PublicAgentTurnContractError {
  readonly reason: 'CONTRACT_INVALID'
  readonly violations: readonly string[]
}

export type PublicAgentTurnParseResult =
  | { readonly ok: true; readonly turn: PublicAgentTurn }
  | { readonly ok: false; readonly error: PublicAgentTurnContractError }
