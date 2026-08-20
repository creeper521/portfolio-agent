<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'

import type { AudienceRole, PublicPortfolio } from '../../public-content/model/publicContentTypes'
import { useMediaQuery } from '../../../shared/composables/useMediaQuery'
import {
  cancelAgentTurn,
  clearConversation,
  fetchCurrentConversation,
  submitAgentTurn,
  type AgentTurnCommand,
  type AgentTurnFailure,
  type ClarificationAnswer,
  type ConversationWindowMessage,
  type SurfaceContext,
} from '../api/agentTurnApi'
import { useLocalSessions } from '../composables/useLocalSessions'
import { useConversationResume } from '../composables/useConversationResume'
import {
  WORKSPACE_LIMITS,
  fitWorkspaceSplit,
  useWorkspaceSplit,
} from '../composables/useWorkspaceSplit'
import type {
  AgentRouteSeed,
  AgentSession,
} from '../model/sessionTypes'
import type {
  ClarificationSubmissionPayload,
  PublicAgentTurn,
  SuggestedAction,
} from '../model/publicAgentTurn'
import { projectTurnFailure, type TurnFailureCategory } from '../model/turnFailureProjection'
import AnswerSourcesPanel from './AnswerSourcesPanel.vue'
import ConversationThread from './ConversationThread.vue'
import LocalSessionRail from './LocalSessionRail.vue'
import PaneResizer from './PaneResizer.vue'

// D-41/交接 §8：AgentWorkspace 只负责会话容器、输入、request lifecycle、
// 内存 Session、active ResumeToken 与 API 协调；业务投影在组件树内。
// 取消：先结束本地 pending，再 best-effort DELETE + abort；不本地伪造 Cancelled。
// A2-07/08/09：pending、failure、draft、notice 一律归属 session，
// 渲染与操作只作用于当前活跃会话；pending 允许跨会话并存，结果回流原会话。
// A2-03/04/18：USER 轮次先落账后请求；失败/取消轮次标记 failed 并排除出
// conversationWindow；澄清提交即把原挑战卡转只读，禁止重复 RESOLVE。

const FREE_TEXT_MAX_LENGTH = 2000
const CONVERSATION_WINDOW_LIMIT = 12
// §11.1 已冻结：标签页合计 pending 上限与后端来源级最大并发 2 对齐，
// 超出时不发必然 409 TURN_IN_PROGRESS 的请求，仅提示等待。
const TAB_PENDING_LIMIT = 2

interface PendingTurn {
  requestId: string
  sessionId: string
  question: string
  controller: AbortController
  userMessageId?: string
}

interface FailureView {
  category: TurnFailureCategory
  message: string
  hint?: string
  retryable: boolean
  retryAfterSeconds?: number
  requestId?: string
  command?: AgentTurnCommand
  userMessageId?: string
}

interface WorkspaceNotice {
  text: string
  sessionId: string
}

interface SuggestionChip {
  text: string
  presetId?: string
}

const props = withDefaults(
  defineProps<{
    portfolio: PublicPortfolio
    initialRole?: AudienceRole
    initialQuestion?: string
    initialProject?: string
    initialCase?: string
    initialSeed?: AgentRouteSeed | null
  }>(),
  {
    initialRole: 'INTERVIEWER',
    initialQuestion: '',
    initialProject: '',
    initialCase: '',
    initialSeed: null,
  },
)

const sessions = useLocalSessions()
const resume = useConversationResume()
const split = useWorkspaceSplit()
const workspaceRoot = ref<HTMLElement | null>(null)
const workspaceWidth = ref(Number.POSITIVE_INFINITY)
const sessionDrawerOpen = ref(false)
const evidenceDrawerOpen = ref(false)
const sessionsIsDrawer = useMediaQuery('(max-width: 959.98px)')
const evidenceIsDrawer = useMediaQuery('(max-width: 1279.98px)')
const pendingTurns = ref(new Map<string, PendingTurn>())
const failures = ref(new Map<string, FailureView>())
const clearPending = ref(false)
const clearNotice = ref<WorkspaceNotice | null>(null)
const resumeNotice = ref<WorkspaceNotice | null>(null)
const composerInput = ref<HTMLTextAreaElement | null>(null)
const focusTarget = ref<{ sectionId: string; nonce: number } | null>(null)
let locateNonce = 0
let disposed = false
let workspaceResizeObserver: ResizeObserver | null = null

function mapSet<Value>(source: Map<string, Value>, key: string, value: Value): Map<string, Value> {
  const next = new Map(source)
  next.set(key, value)
  return next
}

function mapDelete<Value>(source: Map<string, Value>, key: string): Map<string, Value> {
  if (!source.has(key)) return source
  const next = new Map(source)
  next.delete(key)
  return next
}

const activeSession = computed<AgentSession | null>(() => sessions.activeSession.value)
const activePending = computed(() => pendingTurns.value.get(activeSession.value?.id ?? '') ?? null)
const activeFailure = computed(() => failures.value.get(activeSession.value?.id ?? '') ?? null)
/** 当前会话无 pending 但标签页 pending 已满：禁止发起任何新轮次，仅提示（§11.1）。 */
const tabPendingFull = computed(
  () => activePending.value === null && pendingTurns.value.size >= TAB_PENDING_LIMIT,
)
const freeTextRoutingAvailable = computed(
  () => props.portfolio.agentAvailability.freeTextSemanticRouting === 'AVAILABLE',
)
const activeDiscussion = computed(() => activeSession.value?.activeDiscussion)

/** 会话私有草稿（A2-09）：切换会话草稿不串线。 */
const questionDraft = computed<string>({
  get: () => sessions.activeSession.value?.draft ?? '',
  set: (value: string) => {
    const session = sessions.activeSession.value
    if (session !== null) session.draft = value
  },
})

const effectiveSplit = computed(() =>
  evidenceIsDrawer.value
    ? split.state.value
    : fitWorkspaceSplit(split.state.value, workspaceWidth.value),
)

const availableSideWidth = computed(() =>
  Number.isFinite(workspaceWidth.value)
    ? Math.floor(workspaceWidth.value) - WORKSPACE_LIMITS.chatMin
    : Number.POSITIVE_INFINITY,
)

const effectiveMaximums = computed(() => {
  if (evidenceIsDrawer.value || !Number.isFinite(availableSideWidth.value)) {
    return {
      sessions: WORKSPACE_LIMITS.sessions[1],
      evidence: WORKSPACE_LIMITS.evidence[1],
    }
  }
  return {
    sessions: Math.min(
      WORKSPACE_LIMITS.sessions[1],
      Math.max(
        WORKSPACE_LIMITS.sessions[0],
        availableSideWidth.value - WORKSPACE_LIMITS.evidence[0],
      ),
    ),
    evidence: Math.min(
      WORKSPACE_LIMITS.evidence[1],
      Math.max(
        WORKSPACE_LIMITS.evidence[0],
        availableSideWidth.value - WORKSPACE_LIMITS.sessions[0],
      ),
    ),
  }
})

const activeCaseSlug = ref(
  props.portfolio.cases.some((item) => item.slug === props.initialCase)
    ? props.initialCase
    : '',
)
const activeCase = computed(
  () => props.portfolio.cases.find((item) => item.slug === activeCaseSlug.value),
)

const activeProject = computed(() => {
  const projectSlug =
    activeCase.value?.projectSlug ||
    sessions.activeSession.value?.projectSlug ||
    props.initialProject
  return props.portfolio.projects.find((project) => project.slug === projectSlug)
    ?? props.portfolio.projects[0]
})

/** 建议问题：当前 Case 的建议问题优先（FREE_TEXT），其次 AGENT 预设（ASK/PRESET）。 */
const suggestionChips = computed<readonly SuggestionChip[]>(() => {
  if (activeCase.value !== undefined && activeCase.value.suggestedQuestions.length > 0) {
    return activeCase.value.suggestedQuestions.slice(0, 3).map((text) => ({ text }))
  }
  return props.portfolio.questionPresets
    .filter((preset) => preset.placements.includes('AGENT'))
    .slice(0, 3)
    .map((preset) => ({ text: preset.text, presetId: preset.id }))
})

/** 来源面板：最近一条 ANSWER 的唯一 SourceCatalog。 */
const activeSources = computed(() => {
  const messages = [...(activeSession.value?.messages ?? [])]
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const turn = messages[index]?.turn
    if (turn !== undefined && turn.kind === 'ANSWER') {
      return turn.answer.sourceCatalog.sources
    }
  }
  return []
})

/** 最近一条 turn（A2-06）：来源栏标题按它区分"当前/最近"。 */
const latestTurn = computed(() => {
  const messages = [...(activeSession.value?.messages ?? [])]
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const turn = messages[index]?.turn
    if (turn !== undefined) return turn
  }
  return null
})

const sourcesHeading = computed(() =>
  latestTurn.value?.kind === 'ANSWER' ? '当前回答来源' : '最近回答来源',
)

const sourcesStale = computed(
  () => latestTurn.value?.kind !== 'ANSWER' && activeSources.value.length > 0,
)

/** B7：来源 key → 最近引用它的 sectionId（最新回答优先）。 */
const citedSectionByKey = computed(() => {
  const map = new Map<string, string>()
  const messages = [...(activeSession.value?.messages ?? [])]
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const turn = messages[index]?.turn
    if (turn === undefined || turn.kind !== 'ANSWER') continue
    for (const goal of turn.answer.goalResults) {
      const presentation = goal.presentation
      if (presentation === undefined) continue
      const sections = presentation.kind === 'SECTIONED'
        ? presentation.sections
        : presentation.supportingSections
      for (const section of sections) {
        for (const key of section.support.publicSourceKeys) {
          if (!map.has(key)) map.set(key, section.sectionId)
        }
      }
    }
  }
  return map
})

const citedSourceKeys = computed(() => [...citedSectionByKey.value.keys()])

function locateSource(sourceKey: string): void {
  const sectionId = citedSectionByKey.value.get(sourceKey)
  if (sectionId === undefined) return
  locateNonce += 1
  focusTarget.value = { sectionId, nonce: locateNonce }
}

function surfaceContextOf(session: AgentSession): SurfaceContext {
  if (activeCase.value !== undefined) {
    return {
      subjectHint: { kind: 'CASE', slug: activeCase.value.slug },
      audienceRole: session.role,
      requestSource: 'CASE',
    }
  }
  if (session.projectSlug !== null) {
    return {
      subjectHint: { kind: 'PROJECT', slug: session.projectSlug },
      audienceRole: session.role,
      requestSource: 'AGENT_PAGE',
    }
  }
  return { audienceRole: session.role, requestSource: 'AGENT_PAGE' }
}

function turnWindowSummary(turn: PublicAgentTurn): string {
  if (turn.kind === 'ANSWER') {
    return turn.answer.goalResults.map((goal) => goal.label).join('；')
  }
  if (turn.kind === 'CLARIFICATION') return turn.clarification.prompt
  return turn.message
}

function conversationWindowOf(session: AgentSession): ConversationWindowMessage[] {
  const window: ConversationWindowMessage[] = []
  for (const message of session.messages) {
    // A2-04：失败/取消的 USER 轮次不进入会话窗口，维持 USER/ASSISTANT 交替。
    if (message.failed === true) continue
    const content = message.role === 'USER'
      ? message.content
      : message.turn === undefined
        ? ''
        : turnWindowSummary(message.turn)
    if (content.length === 0) continue
    window.push({
      role: message.role === 'USER' ? 'USER' : 'ASSISTANT',
      content: content.slice(0, FREE_TEXT_MAX_LENGTH),
    })
  }
  const bounded = window.slice(-CONVERSATION_WINDOW_LIMIT)
  // 合同要求 USER/ASSISTANT 交替且以 USER 开头；截断后若首条不是 USER 则丢弃。
  return bounded[0]?.role === 'USER' ? bounded : bounded.slice(1)
}

function latestRecommendationReference(
  session: AgentSession,
): string | undefined {
  for (const message of [...session.messages].reverse()) {
    if (message.turn?.kind !== 'ANSWER') continue
    for (const goal of [...message.turn.answer.goalResults].reverse()) {
      if (goal.presentation?.kind !== 'RECOMMENDATION') continue
      const handles = goal.presentation.items.map((item) => {
        const continuation = item.discussionAction?.continuation
        return continuation?.operation === 'ENTER_RESULT'
          ? continuation.contextHandle
          : undefined
      })
      if (
        handles.length > 0
        && handles.every((handle): handle is string => handle !== undefined)
        && new Set(handles).size === 1
      ) return handles[0]
      return undefined
    }
  }
  return undefined
}

function bindConversationEnvelope(sessionId: string, conversation: {
  conversationId: string
  resumeToken?: string
} | null): void {
  if (conversation === null) return
  const isActive = sessions.setSessionConversation(sessionId, conversation)
  if (isActive && conversation.resumeToken !== undefined) {
    resume.setActiveToken(conversation.resumeToken)
  }
}

function failureViewOf(
  sessionId: string,
  requestId: string,
  command: AgentTurnCommand,
  f: AgentTurnFailure,
  userMessageId?: string,
): FailureView {
  const view = projectTurnFailure(f)
  return {
    category: view.category,
    message: view.message,
    ...(view.hint === undefined ? {} : { hint: view.hint }),
    retryable: view.retryable,
    ...(view.retryAfterSeconds === undefined ? {} : { retryAfterSeconds: view.retryAfterSeconds }),
    requestId,
    command,
    ...(userMessageId === undefined ? {} : { userMessageId }),
  }
}

interface TurnOverrides {
  surfaceContext?: SurfaceContext
  conversationWindow?: readonly ConversationWindowMessage[]
  resumeToken?: string
  displayQuestion?: string
  userMessageId?: string
}

async function runTurn(
  sessionId: string,
  requestId: string,
  command: AgentTurnCommand,
  overrides: TurnOverrides = {},
): Promise<void> {
  const session = sessions.sessions.value.find((item) => item.id === sessionId)
  if (session === undefined) return
  const controller = new AbortController()
  pendingTurns.value = mapSet(pendingTurns.value, sessionId, {
    requestId,
    sessionId,
    question: overrides.displayQuestion ?? displayQuestionOf(command),
    controller,
    ...(overrides.userMessageId === undefined ? {} : { userMessageId: overrides.userMessageId }),
  })
  failures.value = mapDelete(failures.value, sessionId)
  const resumeToken = overrides.resumeToken ?? session.resumeToken
  const result = await submitAgentTurn(
    {
      requestId,
      command,
      surfaceContext: overrides.surfaceContext ?? surfaceContextOf(session),
      conversationWindow: overrides.conversationWindow ?? conversationWindowOf(session),
      ...(resumeToken === undefined ? {} : { resumeToken }),
    },
    { signal: controller.signal },
  )
  pendingTurns.value = mapDelete(pendingTurns.value, sessionId)
  if (disposed) return
  if (!result.ok) {
    // 取消是本地先行的：ABORTED 不追加消息、不显示错误（交接 §8）。
    if (result.failure.kind === 'ABORTED') return
    failures.value = mapSet(
      failures.value,
      sessionId,
      failureViewOf(sessionId, requestId, command, result.failure, overrides.userMessageId),
    )
    if (overrides.userMessageId !== undefined) {
      sessions.markMessageDelivery(sessionId, overrides.userMessageId, true)
    }
    return
  }
  if (overrides.userMessageId !== undefined) {
    sessions.markMessageDelivery(sessionId, overrides.userMessageId, false)
  }
  bindConversationEnvelope(sessionId, result.conversation)
  sessions.appendMessage(sessionId, {
    role: 'AGENT',
    content: turnWindowSummary(result.turn),
    turn: result.turn,
  })
  const nextToken = sessions.getSessionResumeToken(sessionId)
  if (nextToken !== undefined) {
    const current = await fetchCurrentConversation(nextToken)
    if (current.ok) sessions.setActiveDiscussion(sessionId, current.activeDiscussion)
  }
  await focusComposer()
}

function displayQuestionOf(command: AgentTurnCommand): string {
  if (command.kind === 'ASK') {
    return command.input.kind === 'FREE_TEXT' ? command.input.text : ''
  }
  if (command.kind === 'CONTINUE') {
    if (command.operation === 'ROUTE_IN_CONTEXT') return command.text
    if (command.operation === 'ENTER_RESULT') return '进入项目讨论'
    if (command.operation === 'EXIT_CONTEXT') return '结束项目讨论'
    return '重新进入项目讨论'
  }
  return '补充澄清'
}

async function focusComposer(): Promise<void> {
  await nextTick()
  composerInput.value?.focus()
}

function ensureSession(): AgentSession {
  const current = sessions.activeSession.value
  if (current !== null) return current
  return sessions.createSession({
    role: props.initialRole,
    projectSlug: props.initialProject || null,
  })
}

function newRequestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`
}

function submitFreeText(rawText: string): void {
  const text = rawText.trim()
  if (
    !freeTextRoutingAvailable.value
    || text.length === 0
    || activePending.value !== null
    || tabPendingFull.value
  ) return
  const session = ensureSession()
  // conversationWindow 只携带本轮之前的会话历史；本轮输入已在 command 内。
  const window = conversationWindowOf(session)
  const messageId = sessions.appendMessage(session.id, { role: 'USER', content: text })
  session.draft = ''
  const referenceContextHandle =
    session.activeDiscussion === undefined
      ? latestRecommendationReference(session)
      : undefined
  const command: AgentTurnCommand = session.activeDiscussion === undefined
    ? {
      kind: 'ASK',
      input: {
        kind: 'FREE_TEXT',
        text: text.slice(0, FREE_TEXT_MAX_LENGTH),
      },
      ...(referenceContextHandle === undefined
        ? {} : { referenceContextHandle }),
    }
    : {
      kind: 'CONTINUE',
      operation: 'ROUTE_IN_CONTEXT',
      contextHandle: session.activeDiscussion.routeContinuation.contextHandle,
      text: text.slice(0, FREE_TEXT_MAX_LENGTH),
    }
  void runTurn(
    session.id,
    newRequestId(),
    command,
    { conversationWindow: window, ...(messageId === null ? {} : { userMessageId: messageId }) },
  )
}

function submitPreset(presetId: string): void {
  if (activePending.value !== null || tabPendingFull.value) return
  const preset = props.portfolio.questionPresets.find((item) => item.id === presetId)
  if (preset === undefined) return
  const session = ensureSession()
  const window = conversationWindowOf(session)
  const messageId = sessions.appendMessage(session.id, { role: 'USER', content: preset.text })
  void runTurn(
    session.id,
    newRequestId(),
    {
      kind: 'ASK',
      input: { kind: 'PRESET', presetId: preset.id, presetRevision: preset.contractVersion },
    },
    { conversationWindow: window, ...(messageId === null ? {} : { userMessageId: messageId }) },
  )
}

function handleSelectAction(action: SuggestedAction): void {
  if (activePending.value !== null || tabPendingFull.value) return
  const session = ensureSession()
  const text = action.inputText ?? action.label
  let command: AgentTurnCommand
  if (action.continuation !== undefined) {
    const continuation = action.continuation
    if (continuation.operation === 'ENTER_RESULT') {
      command = { kind: 'CONTINUE', ...continuation }
    } else if (continuation.operation === 'REENTER_SUBJECT') {
      command = { kind: 'CONTINUE', ...continuation }
    } else if (continuation.operation === 'EXIT_CONTEXT') {
      command = { kind: 'CONTINUE', ...continuation }
    } else {
      command = { kind: 'CONTINUE', ...continuation, text }
    }
  } else {
    command = { kind: 'ASK', input: { kind: 'FREE_TEXT', text } }
  }
  const window = conversationWindowOf(session)
  const messageId = sessions.appendMessage(session.id, { role: 'USER', content: action.label })
  void runTurn(
    session.id,
    newRequestId(),
    command,
    { conversationWindow: window, ...(messageId === null ? {} : { userMessageId: messageId }) },
  )
}

/** 澄清卡脱困入口只消费已发布预设：有 presetId 走 PRESET 命令，否则 FREE_TEXT（§11 第 6 项）。 */
function handleFallbackAsk(entry: { text: string; presetId?: string }): void {
  if (entry.presetId === undefined) {
    submitFreeText(entry.text)
    return
  }
  submitPreset(entry.presetId)
}

/** 澄清答案的展示摘要：CHOICE 用公开选项标签，TEXT 用原文（docs/15 §11.2）。 */
function clarificationAnswerSummary(
  session: AgentSession,
  clarificationId: string,
  answer: ClarificationSubmissionPayload['answers'][number],
): string {
  if (answer.kind === 'TEXT') return answer.text
  for (let index = session.messages.length - 1; index >= 0; index -= 1) {
    const turn = session.messages[index]?.turn
    if (turn === undefined) continue
    const challenge = turn.kind === 'CLARIFICATION'
      ? turn.clarification
      : turn.kind === 'ANSWER' && turn.answer.localClarification?.clarificationId === clarificationId
        ? turn.answer.localClarification
        : undefined
    if (challenge === undefined || challenge.clarificationId !== clarificationId) continue
    for (const field of challenge.fields) {
      if (field.kind !== 'SINGLE_CHOICE') continue
      const choice = field.choices.find((item) => item.choiceId === answer.choiceId)
      if (choice !== undefined) return choice.label
    }
  }
  return '补充澄清'
}

function handleClarification(payload: ClarificationSubmissionPayload): void {
  if (activePending.value !== null || tabPendingFull.value) return
  const session = ensureSession()
  // 冻结合同：RESOLVE_CLARIFICATION 只携带单一 answer（CHOICE|TEXT）。
  const first = payload.answers[0]
  if (payload.answers.length !== 1 || first === undefined) {
    failures.value = mapSet(failures.value, session.id, {
      category: 'CONVERSATION_MISMATCH',
      message: '当前澄清包含多个字段，暂时无法在此提交。',
      hint: '请直接换个说法提问。',
      retryable: false,
    })
    return
  }
  const answer: ClarificationAnswer = first.kind === 'SINGLE_CHOICE'
    ? { kind: 'CHOICE', choiceId: first.choiceId }
    : { kind: 'TEXT', text: first.text }
  // A2-03：澄清答案记为 USER 轮次，窗口保持交替；A2-18：原挑战卡立即转只读。
  const summary = clarificationAnswerSummary(session, payload.clarificationId, first)
  const messageId = sessions.appendMessage(session.id, { role: 'USER', content: summary })
  sessions.markClarificationConsumed(session.id, payload.clarificationId)
  void runTurn(
    session.id,
    newRequestId(),
    {
      kind: 'RESOLVE_CLARIFICATION',
      clarificationId: payload.clarificationId,
      answer,
    },
    {
      displayQuestion: summary,
      ...(messageId === null ? {} : { userMessageId: messageId }),
    },
  )
}

function cancelTurn(): void {
  const current = activePending.value
  if (current === null) return
  // 先结束本地等待，再 best-effort DELETE + abort；DELETE 结果不伪造本地状态。
  pendingTurns.value = mapDelete(pendingTurns.value, current.sessionId)
  if (current.userMessageId !== undefined) {
    sessions.markMessageDelivery(current.sessionId, current.userMessageId, true)
  }
  const token = sessions.getSessionResumeToken(current.sessionId)
  void cancelAgentTurn(current.requestId, token)
  current.controller.abort()
}

function retryFailure(): void {
  const current = activeFailure.value
  if (
    current === null
    || current.requestId === undefined
    || current.command === undefined
    || tabPendingFull.value
  ) {
    return
  }
  // 幂等重试：同一 requestId 复用（交接 §8/D-30）；成功后同轮次解除 failed 标记。
  const sessionId = activeSession.value?.id
  if (sessionId === undefined) return
  failures.value = mapDelete(failures.value, sessionId)
  void runTurn(sessionId, current.requestId, current.command, {
    ...(current.userMessageId === undefined ? {} : { userMessageId: current.userMessageId }),
  })
}

function removeSession(sessionId: string): void {
  const pending = pendingTurns.value.get(sessionId)
  if (pending !== undefined) {
    pendingTurns.value = mapDelete(pendingTurns.value, sessionId)
    void cancelAgentTurn(pending.requestId, sessions.getSessionResumeToken(sessionId))
    pending.controller.abort()
  }
  failures.value = mapDelete(failures.value, sessionId)
  if (sessionId === sessions.activeSessionId.value) {
    const token = sessions.getSessionResumeToken(sessionId)
    if (token !== undefined) {
      void clearConversation(token)
      resume.clearActiveToken()
    }
  }
  sessions.removeSession(sessionId)
  if (sessions.sessions.value.length === 0) {
    createSession()
  }
}

function createSession(): void {
  sessions.createSession({
    role: props.initialRole,
    projectSlug: props.initialProject || null,
  })
  resume.clearActiveToken()
}

async function clearAllSessions(): Promise<void> {
  if (clearPending.value) return
  for (const pending of pendingTurns.value.values()) {
    pendingTurns.value = mapDelete(pendingTurns.value, pending.sessionId)
    void cancelAgentTurn(pending.requestId, sessions.getSessionResumeToken(pending.sessionId))
    pending.controller.abort()
  }
  const tokens = sessions.sessions.value
    .map((session) => session.resumeToken)
    .filter((token): token is string => token !== undefined)
  if (tokens.length === 0) {
    sessions.clearSessions()
    createSession()
    attachClearNotice(null)
    return
  }
  clearPending.value = true
  try {
    const results = await Promise.all(tokens.map((token) => clearConversation(token)))
    if (results.includes('FAILED')) {
      attachClearNotice('服务端尚未确认清除，请稍后重试。')
      return
    }
    resume.clearActiveToken()
    sessions.clearSessions()
    createSession()
    clearNotice.value = null
  } finally {
    clearPending.value = false
  }
}

/** 通知归属会话（A2-09）：sessionId 为空时归属随后创建的活跃会话。 */
function attachClearNotice(text: string | null): void {
  const sessionId = sessions.activeSessionId.value
  if (text === null || sessionId === '') {
    clearNotice.value = null
    return
  }
  clearNotice.value = { text, sessionId }
}

function toggleSessions(): void {
  sessionDrawerOpen.value = !sessionDrawerOpen.value
  if (sessionDrawerOpen.value) evidenceDrawerOpen.value = false
}

function toggleEvidence(): void {
  evidenceDrawerOpen.value = !evidenceDrawerOpen.value
  if (evidenceDrawerOpen.value) sessionDrawerOpen.value = false
}

function closeDrawers(returnFocus = false): void {
  sessionDrawerOpen.value = false
  evidenceDrawerOpen.value = false
  if (returnFocus) composerInput.value?.focus()
}

function previewSplit(pane: 'sessions' | 'evidence', width: number): void {
  split.set(pane, width)
}

function adjustSplit(pane: 'sessions' | 'evidence', delta: number): void {
  split.adjust(pane, delta)
}

// 活跃会话的 Token 变化同步唯一 sessionStorage 槽位（handoff §3）。
watchEffect(() => {
  const token = activeSession.value?.resumeToken
  if (activeSession.value === null) return
  if (token !== undefined) {
    resume.setActiveToken(token)
  } else {
    resume.clearActiveToken()
  }
})

onMounted(async () => {
  if (workspaceRoot.value !== null && typeof ResizeObserver === 'function') {
    workspaceResizeObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width
      if (typeof width === 'number') {
        workspaceWidth.value = width
      }
    })
    workspaceResizeObserver.observe(workspaceRoot.value)
  }

  const seed = props.initialSeed
  if (seed !== null) {
    sessions.seedSession(seed)
    if (seed.conversation !== undefined) {
      const session = sessions.activeSession.value
      if (session !== null) {
        sessions.adoptResumedConversation(session.id, seed.conversation)
        resume.setActiveToken(seed.conversation.resumeToken)
      }
    }
  }

  // 刷新恢复：只恢复会话身份（历史消息按隐私契约不在浏览器保留）。
  if (seed?.conversation === undefined) {
    const storedToken = resume.getActiveToken()
    if (storedToken !== null) {
      const current = await fetchCurrentConversation(storedToken)
      if (disposed) return
      if (current.ok) {
        const session = ensureSession()
        sessions.adoptResumedConversation(session.id, {
          conversationId: current.conversationId,
          resumeToken: storedToken,
          ...(current.activeDiscussion === undefined
            ? {} : { activeDiscussion: current.activeDiscussion }),
        })
        resumeNotice.value = {
          text: '已恢复当前会话；历史消息按隐私约定不在浏览器中保留。',
          sessionId: session.id,
        }
      } else if (current.invalid) {
        resume.clearActiveToken()
      }
    }
  }
  ensureSession()

  if (seed !== null) {
    if (seed.replay !== undefined) {
      // 同 requestId 精确重放首页轮次（D-31）：surface/window 原样回放。
      const session = sessions.activeSession.value
      if (session !== null) {
        void runTurn(session.id, seed.replay.requestId, seed.replay.command, {
          surfaceContext: seed.replay.surfaceContext,
          conversationWindow: [],
          ...(seed.conversation === undefined
            ? {}
            : { resumeToken: seed.conversation.resumeToken }),
          displayQuestion: seed.question,
        })
      }
    } else if (seed.question.trim().length > 0) {
      questionDraft.value = seed.question
    }
  } else if (props.initialQuestion.trim().length > 0) {
    questionDraft.value = props.initialQuestion
  }
})

onBeforeUnmount(() => {
  disposed = true
  for (const pending of pendingTurns.value.values()) {
    pending.controller.abort()
  }
  workspaceResizeObserver?.disconnect()
  workspaceResizeObserver = null
})
</script>

<template>
  <main
    v-if="activeProject && activeSession"
    ref="workspaceRoot"
    class="agent-workspace agent-workspace--prototype"
    :class="{
      'sessions-open': sessionDrawerOpen,
      'evidence-open': evidenceDrawerOpen,
    }"
    :style="{
      '--sessions-width': `${effectiveSplit.sessions}px`,
      '--evidence-width': `${effectiveSplit.evidence}px`,
    }"
  >
    <LocalSessionRail
      :sessions="sessions.historySessions.value"
      :active-id="sessions.activeSessionId.value"
      :inert="sessionsIsDrawer && !sessionDrawerOpen ? true : undefined"
      :aria-hidden="sessionsIsDrawer ? String(!sessionDrawerOpen) : undefined"
      @create="createSession"
      @select="sessions.selectSession"
      @rename="sessions.renameSession"
      @remove="removeSession"
      @clear="clearAllSessions"
    />

    <PaneResizer
      class="session-resizer"
      label="调整历史会话宽度"
      :value="effectiveSplit.sessions"
      :min="WORKSPACE_LIMITS.sessions[0]"
      :max="effectiveMaximums.sessions"
      :direction="1"
      @preview="previewSplit('sessions', $event)"
      @commit="split.persist"
      @adjust="adjustSplit('sessions', $event)"
      @reset="split.reset"
    />

    <section class="workspace-thread-pane" aria-label="对话区">
      <div v-if="sessionsIsDrawer || evidenceIsDrawer" class="workspace-mobile-tools">
        <button
          v-if="sessionsIsDrawer"
          type="button"
          data-testid="open-session-drawer"
          :aria-expanded="sessionDrawerOpen"
          @click="toggleSessions"
        >会话</button>
        <button
          v-if="evidenceIsDrawer"
          type="button"
          data-testid="open-source-panel"
          :aria-expanded="evidenceDrawerOpen"
          @click="toggleEvidence"
        >来源</button>
      </div>
      <p
        v-if="resumeNotice !== null && resumeNotice.sessionId === activeSession.id"
        class="workspace-notice"
        role="status"
      >{{ resumeNotice.text }}</p>
      <p
        v-if="clearNotice !== null && clearNotice.sessionId === activeSession.id"
        class="workspace-notice"
        role="alert"
      >{{ clearNotice.text }}</p>
      <ConversationThread
        :messages="activeSession.messages"
        :pending="activePending !== null"
        :pending-question="activePending?.question ?? ''"
        :focus-target="focusTarget"
        :fallback-presets="suggestionChips"
        @cancel="cancelTurn"
        @select-action="handleSelectAction"
        @submit-clarification="handleClarification"
        @ask="handleFallbackAsk"
      />
      <div
        v-if="activeFailure !== null"
        class="workspace-failure"
        role="alert"
        data-testid="turn-failure"
        :data-failure-category="activeFailure.category"
      >
        <p class="workspace-failure__message">{{ activeFailure.message }}</p>
        <p v-if="activeFailure.hint !== undefined" class="workspace-failure__hint">
          {{ activeFailure.hint }}
        </p>
        <p v-if="activeFailure.retryAfterSeconds !== undefined" class="workspace-failure__hint">
          约 {{ activeFailure.retryAfterSeconds }} 秒后可重试
        </p>
        <button
          v-if="activeFailure.retryable"
          class="workspace-failure__retry"
          type="button"
          data-testid="retry-turn"
          @click="retryFailure"
        >重试</button>
      </div>
      <div class="workspace-composer">
        <p
          v-if="tabPendingFull"
          class="workspace-composer__tab-limit"
          role="status"
          data-testid="tab-pending-notice"
        >已有两个请求正在处理；可先浏览其他会话，稍后再提问。</p>
        <div
          v-if="activeDiscussion !== undefined"
          class="workspace-composer__discussion"
          :data-discussion-status="activeDiscussion.status"
          data-testid="active-discussion"
        >
          <p>当前讨论：{{ activeDiscussion.subject.label }}</p>
          <p>{{ activeDiscussion.status === 'ACTIVE' ? '讨论进行中' : '讨论已过期' }}</p>
          <button
            v-if="activeDiscussion.exitAction !== undefined"
            type="button"
            data-testid="exit-discussion"
            @click="handleSelectAction(activeDiscussion.exitAction)"
          >{{ activeDiscussion.exitAction.label }}</button>
          <button
            v-if="activeDiscussion.reenterAction !== undefined"
            type="button"
            data-testid="reenter-discussion"
            @click="handleSelectAction(activeDiscussion.reenterAction)"
          >{{ activeDiscussion.reenterAction.label }}</button>
          <button
            v-if="activeDiscussion.newTopicAction !== undefined"
            type="button"
            data-testid="new-topic"
            @click="handleSelectAction(activeDiscussion.newTopicAction)"
          >{{ activeDiscussion.newTopicAction.label }}</button>
        </div>
        <div v-if="suggestionChips.length > 0" class="workspace-composer__suggestions">
          <button
            v-for="chip in suggestionChips"
            :key="chip.presetId ?? chip.text"
            class="workspace-composer__suggestion"
            type="button"
            :disabled="
              activePending !== null
              || tabPendingFull
              || (chip.presetId === undefined && !freeTextRoutingAvailable)
            "
            @click="chip.presetId === undefined ? submitFreeText(chip.text) : submitPreset(chip.presetId)"
          >{{ chip.text }}</button>
        </div>
        <form class="workspace-composer__form" @submit.prevent="submitFreeText(questionDraft)">
          <textarea
            ref="composerInput"
            v-model="questionDraft"
            class="workspace-composer__input"
            data-testid="question-input"
            rows="2"
            :maxlength="FREE_TEXT_MAX_LENGTH"
            :disabled="activePending !== null || !freeTextRoutingAvailable"
            aria-label="输入你的问题"
            placeholder="问问公开项目、案例或工程取舍…"
            @keydown.enter.exact.prevent="submitFreeText(questionDraft)"
          ></textarea>
          <button
            class="workspace-composer__send"
            type="submit"
            data-testid="submit-question"
            :disabled="
              activePending !== null
              || tabPendingFull
              || !freeTextRoutingAvailable
              || questionDraft.trim().length === 0
            "
          >发送</button>
        </form>
        <p
          v-if="!freeTextRoutingAvailable"
          class="workspace-composer__routing-disabled"
          data-testid="free-text-routing-disabled"
          role="status"
        >当前部署的自由文本语义理解未启用；已发布预设与确定性操作仍可使用。</p>
        <p class="workspace-composer__privacy">当前对话未保存，刷新或关闭页面后记录会消失。</p>
      </div>
    </section>

    <PaneResizer
      class="evidence-resizer"
      label="调整来源工作台宽度"
      :value="effectiveSplit.evidence"
      :min="WORKSPACE_LIMITS.evidence[0]"
      :max="effectiveMaximums.evidence"
      :direction="-1"
      @preview="previewSplit('evidence', $event)"
      @commit="split.persist"
      @adjust="adjustSplit('evidence', $event)"
      @reset="split.reset"
    />

    <AnswerSourcesPanel
      :sources="activeSources"
      :heading="sourcesHeading"
      :stale="sourcesStale"
      :cited-source-keys="citedSourceKeys"
      :inert="evidenceIsDrawer && !evidenceDrawerOpen ? true : undefined"
      :aria-hidden="evidenceIsDrawer ? String(!evidenceDrawerOpen) : undefined"
      @locate="locateSource"
    />

    <button
      v-if="sessionDrawerOpen || evidenceDrawerOpen"
      class="workspace-scrim"
      type="button"
      aria-label="关闭侧栏"
      @click="closeDrawers(true)"
    ></button>
  </main>
</template>

<style scoped>
.agent-workspace {
  --workspace-rail-bg: var(--agent-rail-paper);
  --workspace-thread-bg: var(--agent-thread-paper);
  --workspace-evidence-bg: var(--agent-evidence-paper);
  --workspace-surface-subtle: color-mix(in srgb, var(--paper-low) 46%, transparent);
  --workspace-text: var(--ink);
  --workspace-text-secondary: var(--muted);
  --workspace-text-faint: var(--faint);
  --workspace-rule: var(--agent-hairline);
  --workspace-accent: var(--agent-accent);
  --workspace-accent-soft: color-mix(in srgb, var(--agent-accent) 68%, white);
  --workspace-primary-bg: var(--ink);
  --workspace-primary-text: var(--paper-hi);
  --workspace-action-bg: var(--agent-accent);
  --workspace-action-bg-hover: color-mix(in srgb, var(--agent-accent) 82%, black);
  position: relative;
  display: grid;
  width: 100%;
  height: 100%;
  grid-template-columns: var(--sessions-width) minmax(640px, 1fr) var(--evidence-width);
  grid-template-rows: minmax(0, 1fr);
  background: var(--workspace-evidence-bg);
  overflow: hidden;
}

.session-resizer { left: var(--sessions-width); }
.evidence-resizer { right: var(--evidence-width); transform: translateX(6px); }
.workspace-scrim { display: none; }

.workspace-thread-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--workspace-thread-bg);
}

.workspace-notice {
  margin: 0;
  padding: 8px clamp(14px, 2.4vw, 26px);
  border-bottom: 1px solid var(--workspace-rule);
  color: var(--workspace-text-secondary);
  font: 11px/1.6 var(--mono);
}

.workspace-failure {
  margin: 0 clamp(14px, 2.4vw, 26px) 10px;
  padding: 10px 14px;
  border: 1px solid var(--workspace-accent);
  background: var(--paper-hi);
}
.workspace-failure__message { margin: 0; color: var(--workspace-text); font: 13px/1.6 var(--sans); }
.workspace-failure__hint { margin: 4px 0 0; color: var(--workspace-text-faint); font: 10px var(--mono); }
.workspace-failure__retry {
  min-height: 30px;
  margin-top: 8px;
  padding: 5px 12px;
  border: 1px solid var(--workspace-accent);
  border-radius: var(--agent-radius-sm, 8px);
  background: var(--workspace-accent);
  color: var(--paper-hi);
  font: 10px var(--mono);
  cursor: pointer;
}

.workspace-composer {
  border-top: 1px solid var(--workspace-rule);
  padding: 10px clamp(14px, 2.4vw, 26px) 8px;
  background: var(--workspace-thread-bg);
}
.workspace-composer__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.workspace-composer__suggestion {
  min-height: 28px;
  padding: 4px 10px;
  border: 1px solid var(--workspace-rule);
  border-radius: 999px;
  background: transparent;
  color: var(--workspace-text-secondary);
  font: 11px/1.5 var(--sans);
  cursor: pointer;
}
.workspace-composer__suggestion:hover:not(:disabled) {
  border-color: var(--workspace-accent);
  color: var(--workspace-accent);
}
.workspace-composer__suggestion:disabled { opacity: 0.5; cursor: default; }
.workspace-composer__form { display: flex; align-items: flex-end; gap: 10px; }
.workspace-composer__input {
  flex: 1;
  min-height: 44px;
  max-height: 140px;
  padding: 10px 12px;
  resize: none;
  border: 1px solid var(--workspace-rule);
  border-radius: var(--agent-radius-sm, 8px);
  background: rgba(255, 255, 255, 0.55);
  color: var(--workspace-text);
  font: 14px/1.6 var(--sans);
}
.workspace-composer__input:focus { outline: 2px solid var(--workspace-accent); outline-offset: 1px; }
.workspace-mobile-tools { display: flex; gap: 0.5rem; padding: 0.5rem 0.75rem 0; }
.workspace-mobile-tools button { min-height: 2.5rem; padding: 0.4rem 0.8rem; }
.workspace-composer__send {
  min-height: 44px;
  padding: 10px 20px;
  border: none;
  border-radius: var(--agent-radius-sm, 8px);
  background: var(--workspace-action-bg);
  color: var(--paper-hi);
  font: 12px var(--mono);
  letter-spacing: 0.08em;
  cursor: pointer;
}
.workspace-composer__send:disabled { opacity: 0.45; cursor: not-allowed; }
.workspace-composer__send:focus-visible { outline: 2px solid var(--workspace-accent); outline-offset: 2px; }
.workspace-composer__privacy {
  margin: 6px 0 0;
  color: var(--workspace-text-faint);
  font: 10px/1.6 var(--mono);
}
.workspace-composer__routing-disabled {
  margin: 8px 0 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
.workspace-composer__discussion {
  margin: 0 0 8px;
  padding: 8px 10px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
.workspace-composer__discussion p { margin: 0; }
.workspace-composer__tab-limit {
  margin: 0 0 8px;
  padding: 6px 10px;
  border-left: 2px solid var(--workspace-accent);
  color: var(--workspace-text-secondary);
  font: 11px/1.6 var(--mono);
}

@media (max-width: 1279.98px) {
  .agent-workspace { grid-template-columns: var(--sessions-width) minmax(0, 1fr); }
  .evidence-resizer { display: none; }
  :deep(.sources-panel) {
    position: absolute;
    z-index: 70;
    grid-area: 1 / 1 / -1 / -1;
    inset: 0 0 0 auto;
    height: 100%;
    width: min(88%, 520px);
    transform: translateX(100%);
    transition: transform 220ms ease;
  }
  .evidence-open :deep(.sources-panel) { transform: translateX(0); }
  .workspace-scrim {
    position: absolute;
    z-index: 60;
    grid-area: 1 / 1 / -1 / -1;
    inset: 0;
    display: block;
    cursor: default;
    border: 0;
    background: rgba(32, 28, 23, 0.5);
  }
}

@media (max-width: 959.98px) {
  .agent-workspace { grid-template-columns: minmax(0, 1fr); }
  :deep(.session-rail) {
    position: absolute;
    z-index: 70;
    grid-area: 1 / 1 / -1 / -1;
    inset: 0 auto 0 0;
    height: 100%;
    width: min(86%, 340px);
    transform: translateX(-100%);
    transition: transform 220ms ease;
  }
  .sessions-open :deep(.session-rail) { transform: translateX(0); }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.sources-panel),
  :deep(.session-rail),
  .workspace-scrim {
    transition: none;
    animation: none;
  }
}
</style>
