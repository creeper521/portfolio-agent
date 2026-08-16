import type { AnswerApiRequest } from '../../src/features/agent/api/answerApi'
import type { ContextReferenceRequest, SemanticSubjectReference } from '../../src/features/agent/model/answerTypes'
import type { BehaviorInputClass, BehaviorTurn, TurnTransportOutcome } from './agentBehaviorTypes'

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

function requestTokenFor(turnId: string): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi !== undefined && typeof cryptoApi.randomUUID === 'function') {
    return `behavior-${turnId}-${cryptoApi.randomUUID()}`
  }
  return `behavior-${turnId}`
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
    requestToken: requestTokenFor(turn.id),
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
