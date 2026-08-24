// PublicAgentTurn 闭合 wire 合同的前端消费者类型（领域模型层）。
// 只表达与后端通过共享 Golden Fixtures 冻结的 JSON 结构；未知附加字段按
// additive evolution 忽略，不在此建模，也不承载任何旧合同
// （旧 disposition/任务快照/公共降级轴等）的兼容形状。
// 结构校验见 publicAgentTurnMapper，本文件只声明类型。（D-38 / 前端交接 §4～§6）

/** Turn 的五种闭合变体：结构化回答 / 澄清挑战 / 纯对话回复 / 边界拒绝 / 能力不可用。 */
export type PublicAgentTurnKind =
  | 'ANSWER'
  | 'CLARIFICATION'
  | 'CONVERSATIONAL'
  | 'BOUNDARY'
  | 'CAPABILITY_UNAVAILABLE'

/** 整个 answer 的产出分辨率：全部完成 / 部分完成 / 无结果（后端权威判定）。 */
export type AnswerResolution = 'COMPLETE' | 'PARTIAL' | 'NO_RESULT'

/** 单个 Goal 的覆盖度：后端权威判定，前端只负责展示。 */
export type GoalCoverage = 'FULL' | 'PARTIAL' | 'NONE'

/** 内容支撑类型闭合集：通用知识 / 已审核公开证据 / 基于既有内容归纳。 */
export type SupportKind =
  | 'GENERAL_KNOWLEDGE'
  | 'VERIFIED_PUBLIC_EVIDENCE'
  | 'DERIVED'

/** 段落/结果项的支撑声明；publicSourceKeys 必须全部能在 answer.sourceCatalog 中解析。 */
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

/** answer 的唯一公开来源目录；一切 publicSourceKeys 以它为权威。 */
export interface PublicSourceCatalog {
  readonly sources: readonly PublicSourceReference[]
}

/** Goal 级用户安全缺口说明；code 为后端冻结的稳定公共码。 */
export interface GoalNotice {
  readonly code: string
  readonly message: string
}

/**
 * 服务端签发的 opaque 续读引用：前端不理解其内容，只保存并在用户触发时原样回传。
 * 四种闭合操作：进入推荐结果、在当前主题内追问、退出主题、重新进入某个主题。
 */
export type ContinuationReference =
  | { readonly operation: 'ENTER_RESULT'; readonly contextHandle: string; readonly resultItemId: string }
  | { readonly operation: 'ROUTE_IN_CONTEXT'; readonly contextHandle: string }
  | { readonly operation: 'EXIT_CONTEXT'; readonly contextHandle: string }
  | {
    readonly operation: 'REENTER_SUBJECT'
    readonly subject: { readonly kind: 'PROJECT'; readonly reference: string }
  }

/** 后端是业务 action 唯一权威；前端只转发 actionId/inputText/continuation。 */
export interface SuggestedAction {
  readonly actionId: string
  readonly label: string
  readonly inputText?: string
  readonly continuation?: ContinuationReference
}

/** sectionKind 闭合枚举：取值已随合同冻结，新增需先更新合同与 Golden Fixtures。（S5-01 / 前端交接 §16.1） */
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

/** 结构化回答中的一个段落：闭合 kind + 标题 + 内容 + 支撑声明。 */
export interface PublicSection {
  readonly sectionId: string
  readonly sectionKind: PublicSectionKind
  readonly title: string
  readonly content: string
  readonly support: PublicSupport
}

/** 推荐结果项：route 为站内相对路径；discussionAction（可省略）用于进入该结果的主题讨论。（S5-01 / 前端交接 §16.2） */
export interface RecommendationItem {
  readonly resultItemId?: string
  readonly label: string
  readonly summary: string
  readonly route: string
  readonly reasons: readonly string[]
  readonly support: PublicSupport
  readonly discussionAction?: SuggestedAction
}

/** 分段式呈现：至少一个段落。 */
export interface SectionedPresentation {
  readonly kind: 'SECTIONED'
  readonly sections: readonly PublicSection[]
}

/** 推荐式呈现：1—5 个结果项，附带支撑段落与数量缺口说明。 */
export interface RecommendationPresentation {
  readonly kind: 'RECOMMENDATION'
  readonly requestedSize: number
  readonly actualSize: number
  readonly items: readonly RecommendationItem[]
  readonly unsatisfiedConstraints: readonly string[]
  readonly incompleteReasons: readonly string[]
  readonly supportingSections: readonly PublicSection[]
}

/** Goal 内容呈现的两种闭合形态。 */
export type GoalPresentation = SectionedPresentation | RecommendationPresentation

/** 单个 Goal 的回答结果：覆盖度 + 可选呈现 + 冻结稳定码通知列表。 */
export interface AnswerGoalResult {
  readonly goalId: string
  readonly label: string
  readonly coverage: GoalCoverage
  readonly presentation?: GoalPresentation
  readonly notices: readonly GoalNotice[]
}

/** 澄清单选项；choiceId 为 opaque，前端不解析其构成。 */
export interface ClarificationChoice {
  readonly choiceId: string
  readonly label: string
}

/** 澄清单选字段：choices 至少一项。 */
export interface SingleChoiceField {
  readonly kind: 'SINGLE_CHOICE'
  readonly fieldId: string
  readonly label: string
  readonly required?: boolean
  readonly choices: readonly ClarificationChoice[]
}

/** 澄清文本字段：limit 为服务端给定的长度上限（可省略）。 */
export interface TextField {
  readonly kind: 'TEXT'
  readonly fieldId: string
  readonly label: string
  readonly required?: boolean
  readonly limit?: number
}

/** 澄清表单字段的闭合集。 */
export type ClarificationField = SingleChoiceField | TextField

/** 澄清表单提交答案：SINGLE_CHOICE 提交 opaque choiceId，TEXT 提交 bounded 文本。 */
export type ClarificationFieldAnswer =
  | { readonly fieldId: string; readonly kind: 'SINGLE_CHOICE'; readonly choiceId: string }
  | { readonly fieldId: string; readonly kind: 'TEXT'; readonly text: string }

/** 澄清表单的整体提交载荷：挑战 id + 每个字段一条作答。 */
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

/** ANSWER 变体的完整载荷：Goal 结果、来源目录、来源构成与可选建议动作。 */
export interface PublicAnswer {
  readonly resolution: AnswerResolution
  readonly contentReleaseId: string
  readonly goalResults: readonly AnswerGoalResult[]
  readonly sourceCatalog: PublicSourceCatalog
  /** 本回答整体用到的支撑类型列表。 */
  readonly sourceComposition: readonly SupportKind[]
  readonly suggestedActions?: readonly SuggestedAction[]
  readonly localClarification?: LocalClarification
}

/** 模型参与度闭合枚举：只反映该轮实际执行/成功采纳的阶段，由服务端投影，前端不推断。（2026-08-21 设计 §13） */
export type ModelParticipation =
  | 'NONE'
  | 'GOAL_INTERPRETATION_ONLY'
  | 'ANSWER_GENERATION'
  | 'GOAL_AND_ANSWER'
  | 'ATTEMPTED_UNAVAILABLE'

/** 该轮的模型执行投影：selectionKind=MODEL 必须携带 ref+version，NONE 不得携带。 */
export interface ModelExecutionProjection {
  readonly selectionKind: 'MODEL' | 'NONE'
  readonly requestedModelRef?: string
  readonly selectionVersion?: string
  readonly participation: ModelParticipation
}

/** 结构化回答 Turn。 */
export interface AnswerTurn {
  readonly kind: 'ANSWER'
  readonly requestId: string
  readonly answer: PublicAnswer
  readonly modelExecution?: ModelExecutionProjection
}

/** 要求访客补充信息的澄清挑战 Turn。 */
export interface ClarificationTurn {
  readonly kind: 'CLARIFICATION'
  readonly requestId: string
  readonly message: string
  readonly clarification: ClarificationChallenge
  readonly suggestedActions?: readonly SuggestedAction[]
  readonly modelExecution?: ModelExecutionProjection
}

/** 无结构化产物的对话式回复 Turn。 */
export interface ConversationalTurn {
  readonly kind: 'CONVERSATIONAL'
  readonly requestId: string
  readonly message: string
  readonly suggestedActions?: readonly SuggestedAction[]
  readonly modelExecution?: ModelExecutionProjection
}

/** 边界拒绝 Turn；code 为后端冻结稳定码。 */
export interface BoundaryTurn {
  readonly kind: 'BOUNDARY'
  readonly requestId: string
  readonly code: string
  readonly message: string
  readonly suggestedActions?: readonly SuggestedAction[]
  readonly modelExecution?: ModelExecutionProjection
}

/** 能力暂不可用 Turn；可携带重试建议（retryable/retryAfterSeconds）。 */
export interface CapabilityUnavailableTurn {
  readonly kind: 'CAPABILITY_UNAVAILABLE'
  readonly requestId: string
  readonly code: string
  readonly message: string
  readonly retryable?: boolean
  readonly retryAfterSeconds?: number
  readonly suggestedActions?: readonly SuggestedAction[]
  readonly modelExecution?: ModelExecutionProjection
}

/** 五种闭合变体的判别联合，kind 为判别字段。 */
export type PublicAgentTurn =
  | AnswerTurn
  | ClarificationTurn
  | ConversationalTurn
  | BoundaryTurn
  | CapabilityUnavailableTurn

/** 合同校验失败：reason 固定 CONTRACT_INVALID，violations 列出全部违规点。 */
export interface PublicAgentTurnContractError {
  readonly reason: 'CONTRACT_INVALID'
  readonly violations: readonly string[]
}

/** fail-closed 解析结果：ok=false 时绝不猜测字段或回退旧格式。 */
export type PublicAgentTurnParseResult =
  | { readonly ok: true; readonly turn: PublicAgentTurn }
  | { readonly ok: false; readonly error: PublicAgentTurnContractError }
