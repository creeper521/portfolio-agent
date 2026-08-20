import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { ContinuationReference, PublicAgentTurn, SuggestedAction } from '../model/publicAgentTurn'
import { parsePublicAgentTurn } from '../model/publicAgentTurnMapper'

// D-46 / 前端交接 §2-§4/§16.2：四条无版本 Agent 资源的唯一前端传输层。
// ResumeToken 只经 Authorization: Bearer 传输，不进 body/URL/日志/诊断；
// 业务结果一律由 parsePublicAgentTurn fail-closed 解析，不回退旧合同。

export type AskCommandInput =
  | { readonly kind: 'FREE_TEXT'; readonly text: string }
  | { readonly kind: 'PRESET'; readonly presetId: string; readonly presetRevision: string }

export type ClarificationAnswer =
  | { readonly kind: 'CHOICE'; readonly choiceId: string }
  | { readonly kind: 'TEXT'; readonly text: string }

export type AgentTurnCommand =
  | {
    readonly kind: 'ASK'
    readonly input: AskCommandInput
    readonly referenceContextHandle?: string
  }
  | {
    readonly kind: 'CONTINUE'
    readonly operation: 'ENTER_RESULT'
    readonly contextHandle: string
    readonly resultItemId: string
  }
  | {
    readonly kind: 'CONTINUE'
    readonly operation: 'ROUTE_IN_CONTEXT'
    readonly contextHandle: string
    readonly text: string
  }
  | {
    readonly kind: 'CONTINUE'
    readonly operation: 'EXIT_CONTEXT'
    readonly contextHandle: string
  }
  | {
    readonly kind: 'CONTINUE'
    readonly operation: 'REENTER_SUBJECT'
    readonly subject: { readonly kind: 'PROJECT'; readonly reference: string }
  }
  | {
    readonly kind: 'RESOLVE_CLARIFICATION'
    readonly clarificationId: string
    readonly answer: ClarificationAnswer
  }

export interface SurfaceContext {
  readonly subjectHint?: { readonly kind: 'PROJECT' | 'CASE'; readonly slug: string }
  readonly audienceRole?: AudienceRole
  readonly requestSource?: 'HOME' | 'PROJECT' | 'CASE' | 'AGENT_PAGE'
}

export interface ConversationWindowMessage {
  readonly role: 'USER' | 'ASSISTANT'
  readonly content: string
}

/** 根级 additive envelope（交接 §16.2）：resumeToken 仅首轮签发或轮换时出现。 */
export interface ConversationEnvelope {
  readonly conversationId: string
  readonly resumeToken?: string
}

export type AgentTurnFailureKind = 'API' | 'CONTRACT' | 'ABORTED' | 'NETWORK' | 'TIMEOUT'

export interface AgentTurnFailure {
  readonly kind: AgentTurnFailureKind
  readonly status?: number
  /** 服务端稳定码，或前端本地码（PUBLIC_TURN_CONTRACT_INVALID / NETWORK_UNAVAILABLE）。 */
  readonly code?: string
  readonly message: string
  readonly retryable: boolean
  readonly retryAfterSeconds?: number
}

export type AgentTurnTransportResult =
  | { readonly ok: true; readonly turn: PublicAgentTurn; readonly conversation: ConversationEnvelope | null }
  | { readonly ok: false; readonly failure: AgentTurnFailure }

export type CancelTurnResult = 'CANCELLED' | 'ALREADY_COMPLETED' | 'NOT_FOUND' | 'FAILED'
export type ClearConversationResult = 'CLEARED' | 'FAILED'
export type CurrentConversationResult =
  | {
    readonly ok: true
    readonly conversationId: string
    readonly status: string
    readonly activeDiscussion?: CurrentDiscussionSummary
  }
  | { readonly ok: false; readonly invalid: boolean }

export interface CurrentDiscussionSummary {
  readonly status: 'ACTIVE' | 'EXPIRED'
  readonly subject: {
    readonly kind: 'PROJECT'
    readonly reference: string
    readonly label: string
    readonly route: string
  }
  readonly expiresAt: string
  readonly routeContinuation: Extract<ContinuationReference, { operation: 'ROUTE_IN_CONTEXT' }>
  readonly exitAction?: SuggestedAction
  readonly reenterAction?: SuggestedAction
  readonly newTopicAction?: SuggestedAction
}

const TURNS_PATH = '/api/agent/turns'
const CURRENT_CONVERSATION_PATH = '/api/agent/conversations/current'
// 跨端预算（docs/15 §11.4）：服务端 Turn 在 20 秒内结算，
// 前端多等待 5 秒只为覆盖传输和响应投影，不代替服务端 deadline。
const REQUEST_TIMEOUT_MS = 25_000
const SHORT_TIMEOUT_MS = 10_000

function bearerHeaders(resumeToken: string | undefined): Record<string, string> {
  return resumeToken === undefined ? {} : { Authorization: `Bearer ${resumeToken}` }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function currentContinuation(value: unknown): ContinuationReference | null {
  if (!isRecord(value) || typeof value.operation !== 'string') return null
  if (value.operation === 'ENTER_RESULT') {
    return typeof value.contextHandle === 'string' && typeof value.resultItemId === 'string'
      ? { operation: value.operation, contextHandle: value.contextHandle, resultItemId: value.resultItemId }
      : null
  }
  if (value.operation === 'ROUTE_IN_CONTEXT' || value.operation === 'EXIT_CONTEXT') {
    return typeof value.contextHandle === 'string'
      ? { operation: value.operation, contextHandle: value.contextHandle }
      : null
  }
  if (value.operation === 'REENTER_SUBJECT' && isRecord(value.subject)
      && value.subject.kind === 'PROJECT' && typeof value.subject.reference === 'string') {
    return { operation: value.operation, subject: { kind: 'PROJECT', reference: value.subject.reference } }
  }
  return null
}

function currentAction(value: unknown): SuggestedAction | null {
  if (!isRecord(value) || typeof value.actionId !== 'string' || typeof value.label !== 'string') return null
  const continuation = currentContinuation(value.continuation)
  if (continuation === null) return null
  return { actionId: value.actionId, label: value.label, continuation }
}

function currentDiscussion(value: unknown): CurrentDiscussionSummary | null {
  if (!isRecord(value) || (value.status !== 'ACTIVE' && value.status !== 'EXPIRED')
      || !isRecord(value.subject) || value.subject.kind !== 'PROJECT'
      || typeof value.subject.reference !== 'string' || typeof value.subject.label !== 'string'
      || typeof value.subject.route !== 'string' || !value.subject.route.startsWith('/')
      || typeof value.expiresAt !== 'string' || Number.isNaN(Date.parse(value.expiresAt))) return null
  const route = currentContinuation(value.routeContinuation)
  if (route === null || route.operation !== 'ROUTE_IN_CONTEXT') return null
  const exitAction = value.exitAction === undefined || value.exitAction === null
    ? undefined : currentAction(value.exitAction)
  const reenterAction = value.reenterAction === undefined || value.reenterAction === null
    ? undefined : currentAction(value.reenterAction)
  const newTopicAction = value.newTopicAction === undefined || value.newTopicAction === null
    ? undefined : currentAction(value.newTopicAction)
  if ((value.exitAction != null && exitAction === null)
      || (value.reenterAction != null && reenterAction === null)
      || (value.newTopicAction != null && newTopicAction === null)) return null
  const validExitAction = exitAction ?? undefined
  const validReenterAction = reenterAction ?? undefined
  const validNewTopicAction = newTopicAction ?? undefined
  return {
    status: value.status,
    subject: {
      kind: 'PROJECT', reference: value.subject.reference,
      label: value.subject.label, route: value.subject.route,
    },
    expiresAt: value.expiresAt,
    routeContinuation: route,
    ...(validExitAction === undefined ? {} : { exitAction: validExitAction }),
    ...(validReenterAction === undefined ? {} : { reenterAction: validReenterAction }),
    ...(validNewTopicAction === undefined ? {} : { newTopicAction: validNewTopicAction }),
  }
}

function parseConversationEnvelope(value: unknown): ConversationEnvelope | null {
  if (!isRecord(value)) return null
  const conversationId = value.conversationId
  if (typeof conversationId !== 'string' || conversationId.length === 0) return null
  const resumeToken = value.resumeToken
  if (resumeToken !== undefined && typeof resumeToken !== 'string') return null
  return resumeToken === undefined ? { conversationId } : { conversationId, resumeToken }
}

function parseErrorEnvelope(value: unknown): {
  code?: string
  message?: string
  retryable?: boolean
  retryAfterSeconds?: number
} | null {
  if (!isRecord(value) || !isRecord(value.error)) return null
  const error = value.error
  return {
    code: typeof error.code === 'string' ? error.code : undefined,
    message: typeof error.message === 'string' ? error.message : undefined,
    retryable: typeof error.retryable === 'boolean' ? error.retryable : undefined,
    retryAfterSeconds:
      typeof error.retryAfterSeconds === 'number' ? error.retryAfterSeconds : undefined,
  }
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return (await response.json()) as unknown
  } catch {
    return null
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

/** 仅内部等待计时器触发的 abort 会抛出该错误；用户主动取消仍走 ABORTED（A2-10）。 */
class TurnTimeoutError extends Error {}

async function fetchWithTimeout(
  input: string,
  init: RequestInit,
  timeoutMs: number,
  externalSignal?: AbortSignal,
): Promise<Response> {
  const timeoutController = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    timeoutController.abort()
  }, timeoutMs)
  const composite = new AbortController()
  const onExternalAbort = () => composite.abort()
  const onTimeoutAbort = () => composite.abort()
  externalSignal?.addEventListener('abort', onExternalAbort, { once: true })
  timeoutController.signal.addEventListener('abort', onTimeoutAbort, { once: true })
  try {
    return await fetch(input, { ...init, signal: composite.signal })
  } catch (error) {
    if (timedOut && isAbortError(error)) throw new TurnTimeoutError('request timeout')
    throw error
  } finally {
    clearTimeout(timer)
    externalSignal?.removeEventListener('abort', onExternalAbort)
    timeoutController.signal.removeEventListener('abort', onTimeoutAbort)
  }
}

export interface SubmitAgentTurnInput {
  readonly requestId: string
  readonly command: AgentTurnCommand
  readonly surfaceContext?: SurfaceContext
  readonly conversationWindow?: readonly ConversationWindowMessage[]
  readonly resumeToken?: string
}

export async function submitAgentTurn(
  input: SubmitAgentTurnInput,
  options: { signal?: AbortSignal } = {},
): Promise<AgentTurnTransportResult> {
  const body: Record<string, unknown> = {
    requestId: input.requestId,
    command: input.command,
    ...(input.surfaceContext === undefined ? {} : { surfaceContext: input.surfaceContext }),
    conversationWindow: [...(input.conversationWindow ?? [])],
  }
  let response: Response
  try {
    response = await fetchWithTimeout(
      TURNS_PATH,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...bearerHeaders(input.resumeToken),
        },
        body: JSON.stringify(body),
      },
      REQUEST_TIMEOUT_MS,
      options.signal,
    )
  } catch (error) {
    if (error instanceof TurnTimeoutError) {
      // 超时不取消服务端 Active Turn：最终结果仍可由同 requestId 重放取回（A2-11/A2-14）。
      return {
        ok: false,
        failure: {
          kind: 'TIMEOUT',
          code: 'REQUEST_TIMEOUT',
          message: '等待超时：回答可能仍在生成',
          retryable: true,
        },
      }
    }
    if (isAbortError(error)) {
      return {
        ok: false,
        failure: { kind: 'ABORTED', code: 'REQUEST_ABORTED', message: '请求已停止等待', retryable: false },
      }
    }
    return {
      ok: false,
      failure: {
        kind: 'NETWORK',
        code: 'NETWORK_UNAVAILABLE',
        message: '网络不可用，请稍后重试',
        retryable: true,
      },
    }
  }

  if (response.status !== 200) {
    const envelope = parseErrorEnvelope(await readJson(response))
    return {
      ok: false,
      failure: {
        kind: 'API',
        status: response.status,
        code: envelope?.code,
        message: envelope?.message ?? 'Agent 暂时无法处理这条请求',
        retryable: envelope?.retryable ?? false,
        retryAfterSeconds: envelope?.retryAfterSeconds,
      },
    }
  }

  const payload = await readJson(response)
  const parsed = parsePublicAgentTurn(payload)
  if (!parsed.ok) {
    return {
      ok: false,
      failure: {
        kind: 'CONTRACT',
        code: 'PUBLIC_TURN_CONTRACT_INVALID',
        message: '回答结构不符合冻结合同',
        retryable: false,
      },
    }
  }
  const conversation = isRecord(payload) ? parseConversationEnvelope(payload.conversation) : null
  return { ok: true, turn: parsed.turn, conversation }
}

/** 幂等取消：204 cancel-wins/already-cancelled；409 已完成；404 未知；其余视为失败。 */
export async function cancelAgentTurn(
  requestId: string,
  resumeToken?: string,
): Promise<CancelTurnResult> {
  let response: Response
  try {
    response = await fetchWithTimeout(
      `${TURNS_PATH}/${encodeURIComponent(requestId)}`,
      { method: 'DELETE', headers: bearerHeaders(resumeToken) },
      SHORT_TIMEOUT_MS,
    )
  } catch {
    return 'FAILED'
  }
  if (response.status === 204) return 'CANCELLED'
  if (response.status === 409) return 'ALREADY_COMPLETED'
  if (response.status === 404) return 'NOT_FOUND'
  return 'FAILED'
}

export async function fetchCurrentConversation(
  resumeToken: string,
): Promise<CurrentConversationResult> {
  let response: Response
  try {
    response = await fetchWithTimeout(
      CURRENT_CONVERSATION_PATH,
      { method: 'GET', headers: bearerHeaders(resumeToken) },
      SHORT_TIMEOUT_MS,
    )
  } catch {
    return { ok: false, invalid: false }
  }
  if (response.status === 401) return { ok: false, invalid: true }
  if (response.status !== 200) return { ok: false, invalid: false }
  const payload = await readJson(response)
  if (!isRecord(payload) || typeof payload.conversationId !== 'string') {
    return { ok: false, invalid: false }
  }
  const activeDiscussion = payload.activeDiscussion === undefined || payload.activeDiscussion === null
    ? undefined : currentDiscussion(payload.activeDiscussion)
  if (payload.activeDiscussion != null && activeDiscussion === null) {
    return { ok: false, invalid: false }
  }
  const validActiveDiscussion = activeDiscussion ?? undefined
  return {
    ok: true,
    conversationId: payload.conversationId,
    status: typeof payload.status === 'string' ? payload.status : 'UNKNOWN',
    ...(validActiveDiscussion === undefined ? {} : { activeDiscussion: validActiveDiscussion }),
  }
}

export async function clearConversation(resumeToken: string): Promise<ClearConversationResult> {
  let response: Response
  try {
    response = await fetchWithTimeout(
      CURRENT_CONVERSATION_PATH,
      { method: 'DELETE', headers: bearerHeaders(resumeToken) },
      SHORT_TIMEOUT_MS,
    )
  } catch {
    return 'FAILED'
  }
  return response.status === 204 ? 'CLEARED' : 'FAILED'
}
