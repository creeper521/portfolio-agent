import type { AnswerApiRequest } from '../../src/features/agent/api/answerApi'
import type { ContextReferenceRequest, SemanticSubjectReference } from '../../src/features/agent/model/answerTypes'
import type { BehaviorContextState, BehaviorInputClass, BehaviorTurn, TurnTransportOutcome } from './agentBehaviorTypes'

export interface BehaviorConversationMessage {
  readonly role: 'USER' | 'ASSISTANT'
  readonly content: string
}

export interface BehaviorConversationState {
  readonly acceptedMessages: readonly BehaviorConversationMessage[]
  readonly diagnosticTurnIds: readonly string[]
  readonly historyTurnIds: readonly string[]
  readonly resumeToken?: string
  readonly activeSubjects?: readonly SemanticSubjectReference[]
  readonly contextReference?: ContextReferenceRequest
  readonly pendingClarification?: unknown
  readonly pageHint?: string
}

export interface BehaviorExchange {
  readonly turnId: string
  readonly outcome: TurnTransportOutcome
  readonly userContent: string
  readonly assistantContent?: string
}

export interface BehaviorPreparedRequest {
  readonly apiInput: AnswerApiRequest
  readonly headers: Readonly<Record<string, string>>
  readonly body: Readonly<Record<string, unknown>>
  readonly historyTurnIds: readonly string[]
}

// 后端将 requestToken 定型为 UUID（ConversationAnswerRequest#requestToken）；
// 非 UUID（如带前缀的自由文本）会在反序列化阶段被 400 拒绝。
function requestTokenFor(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi !== undefined && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID()
  }
  const bytes = new Uint8Array(16)
  cryptoApi.getRandomValues(bytes)
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function hasExplicitContextSwitch(turn: BehaviorTurn): boolean {
  return turn.inputClass === 'ACTIVE_PRESET' || turn.inputClass === 'CONTEXT_SWITCH'
}

function hasUniquelyIdentifiedSubject(turn: BehaviorTurn): boolean {
  const candidate = turn as BehaviorTurn & {
    readonly subjectReference?: SemanticSubjectReference
    readonly explicitSubject?: SemanticSubjectReference
  }
  return candidate.subjectReference !== undefined || candidate.explicitSubject !== undefined
}

function clearStaleContext(state: BehaviorConversationState, turn: BehaviorTurn): BehaviorConversationState {
  if (!hasExplicitContextSwitch(turn) && !hasUniquelyIdentifiedSubject(turn)) return state
  return {
    ...state,
    activeSubjects: undefined,
    contextReference: undefined,
    pendingClarification: undefined,
    pageHint: undefined,
  }
}

function projectBody(input: AnswerApiRequest): Readonly<Record<string, unknown>> {
  return {
    turnId: input.turnId,
    requestToken: input.requestToken,
    ...(input.action === undefined ? {} : { action: input.action }),
    ...(input.questionPresetId === undefined ? {} : { questionPresetId: input.questionPresetId }),
    ...(input.contractVersion === undefined ? {} : { contractVersion: input.contractVersion }),
    ...(input.question === undefined ? {} : { question: input.question }),
    messages: input.messages?.map((message) => ({ role: message.role, content: message.content })),
    context: {
      projectSlug: input.projectSlug ?? null,
      caseSlug: input.caseSlug ?? null,
      audienceRole: input.audienceRole,
      source: input.source,
      coveredTopics: [...new Set(input.coveredTopics ?? [])],
    },
    ...(input.semanticContext === undefined ? {} : { semanticContext: input.semanticContext }),
    ...(input.contextReference === undefined ? {} : { contextReference: input.contextReference }),
  }
}

export interface ScenarioSeedPublicContent {
  readonly projects: readonly { slug: string }[]
  readonly cases: readonly { slug: string }[]
}

export interface ScenarioSeed {
  readonly state: BehaviorConversationState
  readonly projectSlug?: string
  readonly caseSlug?: string
}

/**
 * 把语料场景的 initialState 翻译成首轮请求真实携带的页面上下文：
 * PROJECT_HINT/CASE_HINT 绑定公开快照中的第一个主体（页面提示 + 语义主体），
 * SINGLE_SUBJECT 只携带语义主体（模拟上一轮回答绑定的主体）。
 * 不做种子映射时，PROJECT_HINT 场景会以无上下文发送，测不到
 * 「带页面提示不得静默绑定主体」的目标行为。
 */
export function scenarioSeedFor(
  initialState: BehaviorContextState,
  publicContent: ScenarioSeedPublicContent,
): ScenarioSeed {
  const base: BehaviorConversationState = { acceptedMessages: [], diagnosticTurnIds: [], historyTurnIds: [] }
  if (initialState === 'PROJECT_HINT') {
    const projectSlug = publicContent.projects[0]?.slug
    return projectSlug === undefined
      ? { state: base }
      : { state: { ...base, activeSubjects: [{ subjectType: 'PROJECT', subjectId: projectSlug }] }, projectSlug }
  }
  if (initialState === 'CASE_HINT') {
    const caseSlug = publicContent.cases[0]?.slug
    return caseSlug === undefined
      ? { state: base }
      : { state: { ...base, activeSubjects: [{ subjectType: 'CASE', subjectId: caseSlug }] }, caseSlug }
  }
  if (initialState === 'SINGLE_SUBJECT') {
    const projectSlug = publicContent.projects[0]?.slug
    return projectSlug === undefined
      ? { state: base }
      : { state: { ...base, activeSubjects: [{ subjectType: 'PROJECT', subjectId: projectSlug }] } }
  }
  return { state: base }
}

/** 页面提示持续作用于场景的每一轮（与 UI 从带参页面发起连续提问一致）。 */
export function scenarioTurnFor(turn: BehaviorTurn, seed: ScenarioSeed): BehaviorTurn {
  if (seed.projectSlug === undefined && seed.caseSlug === undefined) return turn
  return {
    ...turn,
    ...(seed.projectSlug !== undefined ? { projectSlug: seed.projectSlug } : {}),
    ...(seed.caseSlug !== undefined ? { caseSlug: seed.caseSlug } : {}),
  }
}

export function appendAcceptedTurn(
  state: BehaviorConversationState,
  exchange: BehaviorExchange,
): BehaviorConversationState {
  if (exchange.outcome !== 'ACCEPTED') {
    return {
      ...state,
      diagnosticTurnIds: state.diagnosticTurnIds.includes(exchange.turnId)
        ? state.diagnosticTurnIds
        : [...state.diagnosticTurnIds, exchange.turnId],
    }
  }
  if (state.historyTurnIds.includes(exchange.turnId)) return state

  const messages: BehaviorConversationMessage[] = [
    ...state.acceptedMessages,
    { role: 'USER', content: exchange.userContent },
  ]
  if (exchange.assistantContent !== undefined) {
    messages.push({ role: 'ASSISTANT', content: exchange.assistantContent })
  }
  return {
    ...state,
    acceptedMessages: messages,
    historyTurnIds: state.historyTurnIds.includes(exchange.turnId)
      ? state.historyTurnIds
      : [...state.historyTurnIds, exchange.turnId],
  }
}

export function createBehaviorRequest(
  state: BehaviorConversationState,
  turn: BehaviorTurn,
): BehaviorPreparedRequest {
  const effectiveState = clearStaleContext(state, turn)
  const candidate = turn as BehaviorTurn & {
    readonly questionPresetId?: string
    readonly projectSlug?: string | null
    readonly caseSlug?: string | null
    readonly subjectReference?: SemanticSubjectReference
    readonly explicitSubject?: SemanticSubjectReference
  }
  const subjectReference = candidate.subjectReference ?? candidate.explicitSubject
  const semanticContext = effectiveState.activeSubjects === undefined && subjectReference === undefined
    ? undefined
    : {
        ...(effectiveState.activeSubjects === undefined && subjectReference === undefined
          ? {}
          : { activeSubjects: subjectReference === undefined ? [...(effectiveState.activeSubjects ?? [])] : [subjectReference] }),
      }
  const apiInput: AnswerApiRequest = {
    turnId: turn.id,
    requestToken: requestTokenFor(),
    question: turn.input,
    messages: effectiveState.acceptedMessages.map((message) => ({ role: message.role, content: message.content })),
    audienceRole: 'GUEST',
    source: 'AGENT_PAGE',
    resumeToken: effectiveState.resumeToken,
    contextReference: effectiveState.contextReference,
    semanticContext,
    questionPresetId: candidate.questionPresetId,
    projectSlug: candidate.projectSlug,
    caseSlug: candidate.caseSlug,
  }
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (effectiveState.resumeToken !== undefined) headers['X-Conversation-Resume-Token'] = effectiveState.resumeToken
  return {
    apiInput,
    headers,
    body: projectBody(apiInput),
    historyTurnIds: [...effectiveState.historyTurnIds],
  }
}

export function transportOutcomeForError(error: unknown): TurnTransportOutcome {
  if (typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError') return 'CANCELLED'
  return 'UNAVAILABLE'
}

export type BehaviorRequestInputClass = BehaviorInputClass
