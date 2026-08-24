import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { ContinuationReference, PublicAgentTurn, SuggestedAction } from '../model/publicAgentTurn'
import type { ModelSelection } from '../model/modelSelection'
import { parsePublicAgentTurn } from '../model/publicAgentTurnMapper'

// 四条无版本 /api/agent 资源（提交 Turn、取消 Turn、查询当前会话、清除当前会话）
// 的唯一前端传输层（API 层）：上层只经此文件访问网络，不直接 fetch。
// ResumeToken 只经 Authorization: Bearer 请求头传输，绝不进入 URL、请求体、日志或诊断；
// 业务结果一律由 parsePublicAgentTurn 做 fail-closed 解析，合同不匹配即失败，不回退旧格式。
// 每个 Turn 请求都显式携带闭合 ModelSelection（不隐式回退默认模型），
// 其序列化形状以 contracts/agent-turn/request-fixtures 的冻结 fixtures 为准。
// （D-46 / 前端交接 §2-§4、§16.2；模型目录 Slice A / 设计 §9）

/** 透传 Turn 请求使用的闭合 ModelSelection 类型，调用方无需感知 model 模块路径。 */
export type { ModelSelection } from '../model/modelSelection'

/** ASK 命令的输入：自由文本提问，或按 id+revision 引用一个预设问题。 */
export type AskCommandInput =
  | { readonly kind: 'FREE_TEXT'; readonly text: string }
  | { readonly kind: 'PRESET'; readonly presetId: string; readonly presetRevision: string }

/** 澄清挑战的作答载荷：单选提交 opaque choiceId，文本提交 bounded 文本。 */
export type ClarificationAnswer =
  | { readonly kind: 'CHOICE'; readonly choiceId: string }
  | { readonly kind: 'TEXT'; readonly text: string }

/**
 * 一次 Agent 轮次的闭合命令变体：新提问（ASK）、四种 Context 续读/跳出
 * （CONTINUE，经 opaque ContextHandle 引用服务端上下文）、或提交澄清答案
 * （RESOLVE_CLARIFICATION）。前端不理解 ContextHandle 内容，只原样回传。
 */
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

/**
 * 请求来源表面上下文：告知服务端本次提问从哪个页面、以什么受众视角发起，
 * 供其做路由与内容裁剪；全部可选，语义由后端定义。
 */
export interface SurfaceContext {
  readonly subjectHint?: { readonly kind: 'PROJECT' | 'CASE'; readonly slug: string }
  readonly audienceRole?: AudienceRole
  readonly requestSource?: 'HOME' | 'PROJECT' | 'CASE' | 'AGENT_PAGE'
}

/** 随请求上送的最小会话窗口消息：仅角色与文本，供服务端裁剪上下文。 */
export interface ConversationWindowMessage {
  readonly role: 'USER' | 'ASSISTANT'
  readonly content: string
}

/**
 * 响应根级 additive envelope：承载跨轮次的权威会话状态。
 * resumeToken 只在首轮签发或轮换时出现，继续沿用旧 Token 时省略。（交接 §16.2）
 */
export interface ConversationEnvelope {
  readonly conversationId: string
  readonly resumeToken?: string
  readonly discussionRevision: number
  readonly activeDiscussion?: CurrentDiscussionSummary
}

/**
 * 轮次失败类别闭合集：API（服务端业务错误）、CONTRACT（响应不符合冻结合同）、
 * ABORTED（用户主动停止等待）、NETWORK（连接失败）、TIMEOUT（本地等待计时到点）。
 */
export type AgentTurnFailureKind = 'API' | 'CONTRACT' | 'ABORTED' | 'NETWORK' | 'TIMEOUT'

/**
 * 结构化轮次失败：传输层不抛异常，一切失败都折叠为该结构向上返回。
 * status 仅 API 类别携带；code 为服务端稳定码或前端本地码。
 */
export interface AgentTurnFailure {
  readonly kind: AgentTurnFailureKind
  readonly status?: number
  /** 服务端稳定码，或前端本地码（PUBLIC_TURN_CONTRACT_INVALID / NETWORK_UNAVAILABLE）。 */
  readonly code?: string
  readonly message: string
  readonly retryable: boolean
  readonly retryAfterSeconds?: number
}

/** submitAgentTurn 的返回：成功携带解析后的 Turn 与会话 envelope，失败携带结构化 failure。 */
export type AgentTurnTransportResult =
  | { readonly ok: true; readonly turn: PublicAgentTurn; readonly conversation: ConversationEnvelope | null }
  | { readonly ok: false; readonly failure: AgentTurnFailure }

/** 取消 Turn 的结果：已取消 / 已完成不可取消 / 服务端不认识该请求 / 传输失败或未预期状态。 */
export type CancelTurnResult = 'CANCELLED' | 'ALREADY_COMPLETED' | 'NOT_FOUND' | 'FAILED'
/** 清除当前会话的结果：仅 204 记为 CLEARED，其余一律 FAILED，由调用方提示重试。 */
export type ClearConversationResult = 'CLEARED' | 'FAILED'
/**
 * fetchCurrentConversation 的返回：ok=true 携带权威会话状态；
 * ok=false 且 invalid=true 表示 ResumeToken 失效（401，应引导新建会话）；
 * invalid=false 表示网络失败，或响应不符合合同（reason=CONTRACT_INVALID）。
 */
export type CurrentConversationResult =
  | {
    readonly ok: true
    readonly conversationId: string
    readonly status: string
    readonly discussionRevision: number
    readonly activeDiscussion?: CurrentDiscussionSummary
  }
  | {
    readonly ok: false
    readonly invalid: boolean
    readonly reason?: 'CONTRACT_INVALID'
  }

/**
 * 当前活跃主题讨论（typed discussion focus）的公开摘要：
 * 完全由服务端下发，前端只展示并在用户触发时回传其中的 continuation，不自行构造。
 */
export interface CurrentDiscussionSummary {
  readonly status: 'ACTIVE' | 'EXPIRED'
  readonly subject: {
    readonly kind: 'PROJECT'
    readonly reference: string
    readonly label: string
    readonly route: string
  }
  readonly expiresAt: string
  /** 继续在当前主题内追问的现成 continuation。 */
  readonly routeContinuation: Extract<ContinuationReference, { operation: 'ROUTE_IN_CONTEXT' }>
  readonly exitAction?: SuggestedAction
  readonly reenterAction?: SuggestedAction
  readonly newTopicAction?: SuggestedAction
}

const TURNS_PATH = '/api/agent/turns'
const CURRENT_CONVERSATION_PATH = '/api/agent/conversations/current'
// 跨端等待预算：服务端承诺 Turn 在 20 秒内结算，前端只多等 5 秒
// 覆盖传输与响应投影耗时；该值不替代服务端自身的 deadline。（docs/15 §11.4）
const REQUEST_TIMEOUT_MS = 25_000
const SHORT_TIMEOUT_MS = 10_000

function bearerHeaders(resumeToken: string | undefined): Record<string, string> {
  return resumeToken === undefined ? {} : { Authorization: `Bearer ${resumeToken}` }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** 从 unknown 防御性投影 ContinuationReference；形状不合法返回 null（不收集违规明细）。 */
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

/** 投影单个 SuggestedAction；actionId/label/continuation 任一不合法即整体返回 null。 */
function currentAction(value: unknown): SuggestedAction | null {
  if (!isRecord(value) || typeof value.actionId !== 'string' || typeof value.label !== 'string') return null
  const continuation = currentContinuation(value.continuation)
  if (continuation === null) return null
  return { actionId: value.actionId, label: value.label, continuation }
}

/**
 * 投影 CurrentDiscussionSummary：对 status、subject.route 前缀、expiresAt 可解析性、
 * routeContinuation 形状与各可选 action 做严格校验，任一不合法整体返回 null，
 * 由调用方按"缺少权威会话状态 / 合同无效"处理。
 */
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

/**
 * fail-closed 解析响应根级 conversation envelope。
 * 返回 null 表示结构缺失或不合法，submitAgentTurn 会将其判为 CONTRACT 失败，
 * 绝不构造部分默认值。
 */
export function parseConversationEnvelope(value: unknown): ConversationEnvelope | null {
  if (!isRecord(value)) return null
  const conversationId = value.conversationId
  if (typeof conversationId !== 'string' || conversationId.length === 0) return null
  const resumeToken = value.resumeToken
  if (resumeToken !== undefined && typeof resumeToken !== 'string') return null
  const discussionRevision = value.discussionRevision
  if (!Number.isSafeInteger(discussionRevision) || Number(discussionRevision) < 0) return null
  const activeDiscussion = value.activeDiscussion === undefined || value.activeDiscussion === null
    ? undefined : currentDiscussion(value.activeDiscussion)
  if (value.activeDiscussion != null && activeDiscussion === null) return null
  const validActiveDiscussion = activeDiscussion ?? undefined
  return {
    conversationId,
    discussionRevision: Number(discussionRevision),
    ...(resumeToken === undefined ? {} : { resumeToken }),
    ...(validActiveDiscussion === undefined
      ? {} : { activeDiscussion: validActiveDiscussion }),
  }
}

/**
 * fail-closed 解析 GET current 的响应载荷；结构不匹配时返回
 * ok=false、invalid=false、reason='CONTRACT_INVALID'，不猜测字段。
 */
export function parseCurrentConversationPayload(
  payload: unknown,
): CurrentConversationResult {
  if (!isRecord(payload) || typeof payload.conversationId !== 'string'
      || !Number.isSafeInteger(payload.discussionRevision)
      || Number(payload.discussionRevision) < 0) {
    return { ok: false, invalid: false, reason: 'CONTRACT_INVALID' }
  }
  const activeDiscussion = payload.activeDiscussion === undefined || payload.activeDiscussion === null
    ? undefined : currentDiscussion(payload.activeDiscussion)
  if (payload.activeDiscussion != null && activeDiscussion === null) {
    return { ok: false, invalid: false, reason: 'CONTRACT_INVALID' }
  }
  const validActiveDiscussion = activeDiscussion ?? undefined
  return {
    ok: true,
    conversationId: payload.conversationId,
    status: typeof payload.status === 'string' ? payload.status : 'UNKNOWN',
    discussionRevision: Number(payload.discussionRevision),
    ...(validActiveDiscussion === undefined ? {} : { activeDiscussion: validActiveDiscussion }),
  }
}

/** 提取服务端错误 envelope（{ error: { code, message, retryable, ... } }）的已知字段；形状不符返回 null。 */
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

/**
 * 仅当本地等待计时器触发的 abort 会抛出该错误，用于把"前端等待超时"与
 * "用户主动取消"区分开：前者映射为 TIMEOUT，后者仍映射为 ABORTED。（A2-10）
 */
class TurnTimeoutError extends Error {}

/**
 * 带本地超时的 fetch：把内部超时 abort 与外部 signal abort 复合到同一个
 * AbortSignal 上。仅当超时先发生时把 AbortError 转换为 TurnTimeoutError；
 * 外部取消原样抛 AbortError，由上层映射为 ABORTED。
 */
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

/** 提交 Turn 的入参；requestId 同时是幂等重放键。 */
export interface SubmitAgentTurnInput {
  /** 幂等键：同 requestId 重试可取回同一 Turn 的结算结果。 */
  readonly requestId: string
  /** 本轮的显式模型选择（MODEL 或 NONE）；服务端不做隐式 Provider 回退。 */
  readonly modelSelection: ModelSelection
  readonly command: AgentTurnCommand
  readonly surfaceContext?: SurfaceContext
  readonly conversationWindow?: readonly ConversationWindowMessage[]
  readonly resumeToken?: string
}

/**
 * 提交一次 Agent 轮次命令并解析响应（POST /api/agent/turns）。
 *
 * 成功返回经 fail-closed 解析的 PublicAgentTurn 与 conversation envelope；
 * 超时、取消、网络错误、非 200、Turn 合同不匹配或缺少权威会话状态，
 * 一律返回结构化 AgentTurnFailure 而非抛异常。
 */
export async function submitAgentTurn(
  input: SubmitAgentTurnInput,
  options: { signal?: AbortSignal } = {},
): Promise<AgentTurnTransportResult> {
  const body: Record<string, unknown> = {
    requestId: input.requestId,
    modelSelection: input.modelSelection,
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
      // 超时只是停止本地等待，并不取消服务端的 Active Turn：
      // 最终结果仍可用同一 requestId 幂等重放取回。（A2-11/A2-14）
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
  if (conversation === null) {
    return {
      ok: false,
      failure: {
        kind: 'CONTRACT',
        code: 'PUBLIC_TURN_CONTRACT_INVALID',
        message: '回答缺少权威会话状态',
        retryable: false,
      },
    }
  }
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

/**
 * 用 ResumeToken 查询当前匿名会话状态（GET /api/agent/conversations/current）。
 * 401 返回 invalid=true（Token 失效，应引导新建会话）；网络失败返回 ok=false
 * 且 invalid=false；200 但载荷不符合合同按 CONTRACT_INVALID 返回。
 */
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
  return parseCurrentConversationPayload(payload)
}

/**
 * 清除当前匿名会话（DELETE /api/agent/conversations/current）。
 * 仅 204 记为 CLEARED；其余状态与网络失败一律 FAILED，由调用方提示重试。
 */
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
