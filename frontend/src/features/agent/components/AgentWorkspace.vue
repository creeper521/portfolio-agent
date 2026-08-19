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
import AnswerSourcesPanel from './AnswerSourcesPanel.vue'
import ConversationThread from './ConversationThread.vue'
import LocalSessionRail from './LocalSessionRail.vue'
import PaneResizer from './PaneResizer.vue'

// D-41/交接 §8：AgentWorkspace 只负责会话容器、输入、request lifecycle、
// 内存 Session、active ResumeToken 与 API 协调；业务投影在组件树内。
// 取消：先结束本地 pending，再 best-effort DELETE + abort；不本地伪造 Cancelled。

const FREE_TEXT_MAX_LENGTH = 2000
const CONVERSATION_WINDOW_LIMIT = 12

interface PendingTurn {
  requestId: string
  sessionId: string
  question: string
  controller: AbortController
}

interface FailureView {
  message: string
  retryable: boolean
  retryAfterSeconds?: number
  requestId?: string
  command?: AgentTurnCommand
  sessionId?: string
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
const pending = ref<PendingTurn | null>(null)
const failure = ref<FailureView | null>(null)
const clearPending = ref(false)
const clearNotice = ref<string | null>(null)
const resumeNotice = ref<string | null>(null)
const questionDraft = ref('')
const composerInput = ref<HTMLTextAreaElement | null>(null)
let disposed = false
let workspaceResizeObserver: ResizeObserver | null = null

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

const activeSession = computed<AgentSession | null>(() => sessions.activeSession.value)

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
): FailureView {
  return {
    message: f.message,
    retryable: f.retryable,
    ...(f.retryAfterSeconds === undefined ? {} : { retryAfterSeconds: f.retryAfterSeconds }),
    requestId,
    command,
    sessionId,
  }
}

interface TurnOverrides {
  surfaceContext?: SurfaceContext
  conversationWindow?: readonly ConversationWindowMessage[]
  resumeToken?: string
  displayQuestion?: string
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
  pending.value = {
    requestId,
    sessionId,
    question: overrides.displayQuestion ?? displayQuestionOf(command),
    controller,
  }
  failure.value = null
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
  if (disposed) return
  pending.value = null
  if (!result.ok) {
    // 取消是本地先行的：ABORTED 不追加消息、不显示错误（交接 §8）。
    if (result.failure.kind !== 'ABORTED') {
      failure.value = failureViewOf(sessionId, requestId, command, result.failure)
    }
    return
  }
  bindConversationEnvelope(sessionId, result.conversation)
  sessions.appendMessage(sessionId, {
    role: 'AGENT',
    content: turnWindowSummary(result.turn),
    turn: result.turn,
  })
  await focusComposer()
}

function displayQuestionOf(command: AgentTurnCommand): string {
  if (command.kind === 'ASK') {
    return command.input.kind === 'FREE_TEXT' ? command.input.text : ''
  }
  if (command.kind === 'CONTINUE') {
    return command.text
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

function submitFreeText(rawText: string): void {
  const text = rawText.trim()
  if (text.length === 0 || pending.value !== null) return
  const session = ensureSession()
  // conversationWindow 只携带本轮之前的会话历史；本轮输入已在 command 内。
  const window = conversationWindowOf(session)
  sessions.appendMessage(session.id, { role: 'USER', content: text })
  questionDraft.value = ''
  void runTurn(
    session.id,
    globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`,
    { kind: 'ASK', input: { kind: 'FREE_TEXT', text: text.slice(0, FREE_TEXT_MAX_LENGTH) } },
    { conversationWindow: window },
  )
}

function submitPreset(presetId: string): void {
  if (pending.value !== null) return
  const preset = props.portfolio.questionPresets.find((item) => item.id === presetId)
  if (preset === undefined) return
  const session = ensureSession()
  const window = conversationWindowOf(session)
  sessions.appendMessage(session.id, { role: 'USER', content: preset.text })
  void runTurn(
    session.id,
    globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`,
    {
      kind: 'ASK',
      input: { kind: 'PRESET', presetId: preset.id, presetRevision: preset.contractVersion },
    },
    { conversationWindow: window },
  )
}

function handleSelectAction(action: SuggestedAction): void {
  if (pending.value !== null) return
  const session = ensureSession()
  const text = action.inputText ?? action.label
  let command: AgentTurnCommand
  if (action.continuation !== undefined) {
    command = {
      kind: 'CONTINUE',
      contextHandle: action.continuation.contextHandle,
      ...(action.continuation.resultItemId === undefined
        ? {}
        : { resultItemId: action.continuation.resultItemId }),
      text,
    }
  } else {
    command = { kind: 'ASK', input: { kind: 'FREE_TEXT', text } }
  }
  const window = conversationWindowOf(session)
  sessions.appendMessage(session.id, { role: 'USER', content: action.label })
  void runTurn(
    session.id,
    globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`,
    command,
    { conversationWindow: window },
  )
}

function handleClarification(payload: ClarificationSubmissionPayload): void {
  if (pending.value !== null) return
  // 冻结合同：RESOLVE_CLARIFICATION 只携带单一 answer（CHOICE|TEXT）。
  const first = payload.answers[0]
  if (payload.answers.length !== 1 || first === undefined) {
    failure.value = {
      message: '当前澄清包含多个字段，暂时无法在此提交，请直接换个说法提问。',
      retryable: false,
    }
    return
  }
  const answer: ClarificationAnswer = first.kind === 'SINGLE_CHOICE'
    ? { kind: 'CHOICE', choiceId: first.choiceId }
    : { kind: 'TEXT', text: first.text }
  const session = ensureSession()
  void runTurn(session.id, globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`, {
    kind: 'RESOLVE_CLARIFICATION',
    clarificationId: payload.clarificationId,
    answer,
  })
}

function cancelTurn(): void {
  const current = pending.value
  if (current === null) return
  // 先结束本地等待，再 best-effort DELETE + abort；DELETE 结果不伪造本地状态。
  pending.value = null
  const token = sessions.getSessionResumeToken(current.sessionId)
  void cancelAgentTurn(current.requestId, token)
  current.controller.abort()
}

function retryFailure(): void {
  const current = failure.value
  if (
    current === null
    || current.requestId === undefined
    || current.command === undefined
    || current.sessionId === undefined
  ) {
    return
  }
  // 幂等重试：同一 requestId 复用（交接 §8/D-30）。
  failure.value = null
  void runTurn(current.sessionId, current.requestId, current.command)
}

function removeSession(sessionId: string): void {
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
  const tokens = sessions.sessions.value
    .map((session) => session.resumeToken)
    .filter((token): token is string => token !== undefined)
  if (tokens.length === 0) {
    sessions.clearSessions()
    createSession()
    clearNotice.value = null
    return
  }
  clearPending.value = true
  clearNotice.value = null
  try {
    const results = await Promise.all(tokens.map((token) => clearConversation(token)))
    if (results.includes('FAILED')) {
      clearNotice.value = '服务端尚未确认清除，请稍后重试。'
      return
    }
    resume.clearActiveToken()
    sessions.clearSessions()
    createSession()
  } finally {
    clearPending.value = false
  }
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
        })
        resumeNotice.value = '已恢复当前会话；历史消息按隐私约定不在浏览器中保留。'
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
  pending.value?.controller.abort()
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
      <p v-if="resumeNotice !== null" class="workspace-notice" role="status">{{ resumeNotice }}</p>
      <p v-if="clearNotice !== null" class="workspace-notice" role="alert">{{ clearNotice }}</p>
      <ConversationThread
        :messages="activeSession.messages"
        :pending="pending !== null"
        :pending-question="pending?.question ?? ''"
        @cancel="cancelTurn"
        @select-action="handleSelectAction"
        @submit-clarification="handleClarification"
      />
      <div v-if="failure !== null" class="workspace-failure" role="alert" data-testid="turn-failure">
        <p class="workspace-failure__message">{{ failure.message }}</p>
        <p v-if="failure.retryAfterSeconds !== undefined" class="workspace-failure__hint">
          约 {{ failure.retryAfterSeconds }} 秒后可重试
        </p>
        <button
          v-if="failure.retryable"
          class="workspace-failure__retry"
          type="button"
          data-testid="retry-turn"
          @click="retryFailure"
        >重试</button>
      </div>
      <div class="workspace-composer">
        <div v-if="suggestionChips.length > 0" class="workspace-composer__suggestions">
          <button
            v-for="chip in suggestionChips"
            :key="chip.presetId ?? chip.text"
            class="workspace-composer__suggestion"
            type="button"
            :disabled="pending !== null"
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
            :disabled="pending !== null"
            aria-label="输入你的问题"
            placeholder="问问公开项目、案例或工程取舍…"
            @keydown.enter.exact.prevent="submitFreeText(questionDraft)"
          ></textarea>
          <button
            class="workspace-composer__send"
            type="submit"
            data-testid="submit-question"
            :disabled="pending !== null || questionDraft.trim().length === 0"
          >发送</button>
        </form>
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
      :inert="evidenceIsDrawer && !evidenceDrawerOpen ? true : undefined"
      :aria-hidden="evidenceIsDrawer ? String(!evidenceDrawerOpen) : undefined"
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
