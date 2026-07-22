<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import type {
  AudienceRole,
  PublicPortfolio,
} from '../../public-content/model/publicContentTypes'
import { useMediaQuery } from '../../../shared/composables/useMediaQuery'
import { askQuestion } from '../api/answerApi'
import { useLocalSessions } from '../composables/useLocalSessions'
import {
  WORKSPACE_LIMITS,
  useWorkspaceSplit,
  type WorkspaceSplit,
} from '../composables/useWorkspaceSplit'
import type { AgentRouteSeed } from '../model/sessionTypes'
import type { ContextEnvelope, FollowUpAction } from '../model/answerTypes'
import { mapAnswerResponse } from '../model/mapAnswerResponse'
import ConversationThread from './ConversationThread.vue'
import EvidenceDesk from './EvidenceDesk.vue'
import LocalSessionRail from './LocalSessionRail.vue'
import PaneResizer from './PaneResizer.vue'

interface AnswerRequestContext {
  sessionId: string
  projectSlug: string
  question: string
  questionPresetId?: string
  contextEnvelope?: ContextEnvelope
}

const props = withDefaults(
  defineProps<{
    portfolio: PublicPortfolio
    initialRole?: AudienceRole
    initialQuestion?: string
    initialProject?: string
    initialEvidence?: string
    initialSeed?: AgentRouteSeed | null
  }>(),
  {
    initialRole: 'INTERVIEWER',
    initialQuestion: '',
    initialProject: '',
    initialEvidence: '',
    initialSeed: null,
  },
)

const sessions = useLocalSessions()
const split = useWorkspaceSplit()
const sessionDrawerOpen = ref(false)
const evidenceDrawerOpen = ref(false)
const sessionsIsDrawer = useMediaQuery('(max-width: 980px)')
const evidenceIsDrawer = useMediaQuery('(max-width: 1219.98px)')
const activeEvidenceId = ref(props.initialEvidence || props.portfolio.evidence[0]?.id || '')
const pending = ref(false)
const answerError = ref('')
const failedRequest = ref<AnswerRequestContext | null>(null)
let activeRequest: AnswerRequestContext | null = null
let requestVersion = 0
let disposed = false

const activeProject = computed(
  () =>
    props.portfolio.projects.find(
      (project) =>
        project.slug === (sessions.activeSession.value?.projectSlug || props.initialProject),
    ) ??
    props.portfolio.projects[0],
)

function createSession() {
  sessions.createSession({
    role: props.initialRole,
    projectSlug: activeProject.value?.slug ?? null,
    evidenceId: activeEvidenceId.value || null,
  })
}

function clearAnswerFailure() {
  answerError.value = ''
  failedRequest.value = null
}

function invalidatePendingRequest() {
  requestVersion += 1
  activeRequest = null
  pending.value = false
}

async function requestAnswer(context: AnswerRequestContext, appendUser: boolean) {
  const session = sessions.sessions.value.find((item) => item.id === context.sessionId)
  const project = props.portfolio.projects.find((item) => item.slug === context.projectSlug)
  if (!session || !project || pending.value || disposed) {
    if (!session || !project) clearAnswerFailure()
    return
  }

  if (appendUser) {
    sessions.appendMessage(session.id, {
      role: 'USER',
      content: context.question,
      answer: null,
      evidenceIds: [],
    })
  }
  const request = ++requestVersion
  activeRequest = context
  pending.value = true
  clearAnswerFailure()
  try {
    const mapped = mapAnswerResponse(
      await askQuestion({
        turnId: globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`,
        projectSlug: context.projectSlug,
        audienceRole: session.role,
        source: 'AGENT_PAGE',
        focusEvidenceIds: session.evidenceId ? [session.evidenceId] : [],
        questionPresetId: context.questionPresetId,
        question: context.questionPresetId ? undefined : context.question,
        contextEnvelope: context.contextEnvelope,
      }),
    )
    if (disposed || request !== requestVersion) return
    sessions.appendMessage(session.id, {
      role: 'AGENT',
      content: mapped.summary,
      answer: mapped,
      evidenceIds: mapped.evidenceIds,
    })
  } catch {
    if (disposed || request !== requestVersion) return
    failedRequest.value = context
    answerError.value = 'Agent 暂时无法回答，请稍后重试'
  } finally {
    if (!disposed && request === requestVersion) {
      activeRequest = null
      pending.value = false
    }
  }
}

function submit(question: string) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  if (!session || !project) return
  const preset = props.portfolio.questionPresets.find(
    (item) => item.projectSlug === project.slug && item.text === question,
  )
  void requestAnswer(
    {
      sessionId: session.id,
      projectSlug: project.slug,
      question,
      questionPresetId: preset?.id,
    },
    true,
  )
}

function retryAnswer() {
  const context = failedRequest.value
  if (!context) return
  const sessionExists = sessions.sessions.value.some((item) => item.id === context.sessionId)
  const projectExists = props.portfolio.projects.some(
    (item) => item.slug === context.projectSlug,
  )
  if (!sessionExists || !projectExists) {
    clearAnswerFailure()
    return
  }
  void requestAnswer(context, false)
}

function previewSplit(key: keyof WorkspaceSplit, value: number) {
  split.set(key, value)
}

function openEvidence(id: string) {
  activeEvidenceId.value = id
  sessionDrawerOpen.value = false
  evidenceDrawerOpen.value = true
}

function toggleSessions() {
  sessionDrawerOpen.value = !sessionDrawerOpen.value
  if (sessionDrawerOpen.value) evidenceDrawerOpen.value = false
  if (sessionDrawerOpen.value && sessionsIsDrawer.value) focusDrawer('#local-session-rail')
}

function submitFollowUp(action: FollowUpAction) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  if (!session || !project) return
  void requestAnswer(
    {
      sessionId: session.id,
      projectSlug: project.slug,
      question: action.question,
      contextEnvelope: action.contextEnvelope,
    },
    true,
  )
}

function toggleEvidence() {
  evidenceDrawerOpen.value = !evidenceDrawerOpen.value
  if (evidenceDrawerOpen.value) sessionDrawerOpen.value = false
  if (evidenceDrawerOpen.value && evidenceIsDrawer.value) focusDrawer('#agent-evidence-desk')
}

function focusDrawer(selector: string) {
  requestAnimationFrame(() => {
    const root = document.querySelector<HTMLElement>(selector)
    root?.querySelector<HTMLElement>('button, a, input, textarea, [tabindex]:not([tabindex="-1"])')?.focus()
  })
}

function trapDrawerFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return
  const selector = sessionDrawerOpen.value && sessionsIsDrawer.value
    ? '#local-session-rail'
    : evidenceDrawerOpen.value && evidenceIsDrawer.value
      ? '#agent-evidence-desk'
      : ''
  if (!selector) return
  const root = document.querySelector<HTMLElement>(selector)
  const focusable = Array.from(root?.querySelectorAll<HTMLElement>(
    'button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  ) ?? [])
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function clearAllSessions() {
  invalidatePendingRequest()
  clearAnswerFailure()
  sessions.clearSessions()
  createSession()
}

function removeSession(sessionId: string) {
  if (activeRequest?.sessionId === sessionId) {
    invalidatePendingRequest()
  }
  if (failedRequest.value?.sessionId === sessionId) {
    clearAnswerFailure()
  }
  sessions.removeSession(sessionId)
  if (!sessions.activeSession.value) {
    createSession()
  }
}

function closeDrawers(restoreFocus = false) {
  const focusTarget = evidenceDrawerOpen.value ? '.evidence-toggle' : '.session-toggle'
  sessionDrawerOpen.value = false
  evidenceDrawerOpen.value = false
  if (restoreFocus) {
    requestAnimationFrame(() => {
      document.querySelector<HTMLElement>(focusTarget)?.focus()
    })
  }
}

function onWindowKeydown(event: KeyboardEvent) {
  trapDrawerFocus(event)
  if (event.key === 'Escape' && (sessionDrawerOpen.value || evidenceDrawerOpen.value)) {
    closeDrawers(true)
  }
}

if (props.initialSeed) {
  const seeded = sessions.seedSession(props.initialSeed)
  activeEvidenceId.value = props.initialSeed.evidenceIds[0] ?? seeded.evidenceId ?? ''
} else if (!sessions.activeSession.value) {
  createSession()
}

onMounted(() => window.addEventListener('keydown', onWindowKeydown))
onBeforeUnmount(() => {
  disposed = true
  invalidatePendingRequest()
  window.removeEventListener('keydown', onWindowKeydown)
})
</script>

<template>
  <main
    v-if="activeProject && sessions.activeSession.value"
    class="agent-workspace agent-workspace--prototype"
    :class="{
      'sessions-open': sessionDrawerOpen,
      'evidence-open': evidenceDrawerOpen,
    }"
    :style="{
      '--sessions-width': `${split.state.value.sessions}px`,
      '--evidence-width': `${split.state.value.evidence}px`,
    }"
  >
    <p class="session-privacy" role="note">当前对话未保存，刷新后记录会消失</p>
    <LocalSessionRail
      :sessions="sessions.sessions.value"
      :active-id="sessions.activeSessionId.value"
      :inert="sessionsIsDrawer && !sessionDrawerOpen ? true : undefined"
      :aria-hidden="sessionsIsDrawer ? String(!sessionDrawerOpen) : undefined"
      @create="createSession"
      @select="sessions.selectSession"
      @remove="removeSession"
      @clear="clearAllSessions"
    />

    <PaneResizer
      class="session-resizer"
      label="调整历史会话宽度"
      :value="split.state.value.sessions"
      :min="WORKSPACE_LIMITS.sessions[0]"
      :max="WORKSPACE_LIMITS.sessions[1]"
      :direction="1"
      @preview="previewSplit('sessions', $event)"
      @commit="split.persist"
      @adjust="split.adjust('sessions', $event)"
      @reset="split.reset"
    />

    <ConversationThread
      :session="sessions.activeSession.value"
      :role="sessions.activeSession.value.role"
      :project="activeProject"
      :seed-question="initialQuestion"
      :sessions-open="sessionDrawerOpen"
      :evidence-open="evidenceDrawerOpen"
      :pending="pending"
      :error="answerError"
      @submit="submit"
      @follow-up="submitFollowUp"
      @retry="retryAnswer"
      @evidence="openEvidence"
      @toggle-sessions="toggleSessions"
      @toggle-evidence="toggleEvidence"
    />

    <PaneResizer
      class="evidence-resizer"
      label="调整证据工作台宽度"
      :value="split.state.value.evidence"
      :min="WORKSPACE_LIMITS.evidence[0]"
      :max="WORKSPACE_LIMITS.evidence[1]"
      :direction="-1"
      @preview="previewSplit('evidence', $event)"
      @commit="split.persist"
      @adjust="split.adjust('evidence', $event)"
      @reset="split.reset"
    />

    <EvidenceDesk
      :evidence="portfolio.evidence"
      :project="activeProject"
      :active-evidence-id="activeEvidenceId"
      :inert="evidenceIsDrawer && !evidenceDrawerOpen ? true : undefined"
      :aria-hidden="evidenceIsDrawer ? String(!evidenceDrawerOpen) : undefined"
      @select="activeEvidenceId = $event"
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
  position: relative;
  display: grid;
  width: 100%;
  height: calc(100vh - var(--header-height));
  grid-template-columns: var(--sessions-width) minmax(600px, 1fr) var(--evidence-width);
  background: var(--ink);
  overflow: hidden;
}

.session-privacy {
  position: absolute;
  z-index: 20;
  right: 18px;
  bottom: 4px;
  margin: 0;
  color: #a99d8f;
  font: 9px/1.5 var(--mono);
  pointer-events: none;
}

.session-resizer {
  left: var(--sessions-width);
}

.evidence-resizer {
  right: var(--evidence-width);
}

.workspace-scrim {
  display: none;
}

@media (max-width: 1279px) and (min-width: 1221px) {
  .agent-workspace {
    --sessions-width: clamp(220px, 18vw, 240px) !important;
    --evidence-width: 380px !important;
  }
}

@media (max-width: 1220px) {
  .agent-workspace {
    grid-template-columns: var(--sessions-width) minmax(0, 1fr);
  }

  .evidence-resizer {
    display: none;
  }

  :deep(.evidence-desk) {
    position: fixed;
    z-index: 70;
    top: var(--header-height);
    right: 0;
    width: min(88vw, 520px);
    transform: translateX(100%);
    transition: transform 220ms ease;
  }

  .evidence-open :deep(.evidence-desk) {
    transform: translateX(0);
  }

  .workspace-scrim {
    position: fixed;
    z-index: 60;
    inset: var(--header-height) 0 0;
    display: block;
    cursor: default;
    border: 0;
    background: rgba(32, 28, 23, 0.5);
  }
}

@media (max-width: 980px) {
  .agent-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  :deep(.session-rail) {
    position: fixed;
    z-index: 70;
    top: var(--header-height);
    left: 0;
    width: min(86vw, 340px);
    transform: translateX(-100%);
    transition: transform 220ms ease;
  }

  .sessions-open :deep(.session-rail) {
    transform: translateX(0);
  }
}
</style>
