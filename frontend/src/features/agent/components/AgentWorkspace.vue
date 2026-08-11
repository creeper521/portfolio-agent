<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import type {
  AudienceRole,
  PublicPortfolio,
} from '../../public-content/model/publicContentTypes'
import { useMediaQuery } from '../../../shared/composables/useMediaQuery'
import {
  createFrontendDiagnosticEvent,
  durationBucketFor,
} from '../../../shared/diagnostics/frontendDiagnosticTypes'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import { askQuestion } from '../api/answerApi'
import {
  askWithPresetContractRetry,
  isPresetContractStale,
  isPresetContractUnavailable,
} from '../api/presetContractRetry'
import { createRequestToken } from '../api/createRequestToken'
import { PortfolioApiError } from '../../portfolio/api/portfolioApi'
import type { ErrorAction } from '../../portfolio/api/apiErrorActions'
import { useLocalSessions } from '../composables/useLocalSessions'
import {
  WORKSPACE_LIMITS,
  fitWorkspaceSplit,
  useWorkspaceSplit,
  type WorkspaceSplit,
} from '../composables/useWorkspaceSplit'
import type { AgentRouteSeed, AgentSession } from '../model/sessionTypes'
import type {
  PortfolioReferenceContext,
  ConversationSuggestedQuestion,
  ConversationTopic,
  FollowUpAction,
  PortfolioRecommendationContextRequest,
  SemanticContextRequest,
  InvalidatedPlanReference,
  PendingPlanReference,
  PlanAdjustmentRequest,
  ClarificationResolutionRequest,
} from '../model/answerTypes'
import type {
  ClarificationSubmission,
  ClarificationView,
  OpaquePlanConfirmation,
  PlanAdjustmentBarState,
} from '../model/semanticTurnView'
import { resolveActiveSemanticAction } from '../model/activeSemanticAction'
import { completeSuggestedQuestions } from '../model/completeSuggestedQuestions'
import {
  buildEvidenceDeskContext,
  type AnswerFocusTarget,
  type EvidenceDeskTab,
  type EvidenceInspectRequest,
} from '../model/evidenceDeskModel'
import { mapAnswerResponse } from '../model/mapAnswerResponse'
import ConversationThread from './ConversationThread.vue'
import EvidenceDesk from './EvidenceDesk.vue'
import LocalSessionRail from './LocalSessionRail.vue'
import PaneResizer from './PaneResizer.vue'

interface AnswerRequestContext {
  sessionId: string
  projectSlug: string | null
  caseSlug?: string | null
  question?: string
  action?: 'ASK' | 'CONFIRM_PLAN' | 'REGENERATE_PLAN'
  planConfirmation?: OpaquePlanConfirmation
  semanticContext?: SemanticContextRequest
  invalidatedPlanReference?: InvalidatedPlanReference
  planAdjustment?: PlanAdjustmentRequest
  clarificationResolution?: ClarificationResolutionRequest
  questionPresetId?: string
  contractVersion?: string
  coveredTopics?: readonly ConversationTopic[]
  referenceContext?: PortfolioReferenceContext
  requestToken?: string
  recommendationContext?: PortfolioRecommendationContextRequest
}

interface AnswerFailureView {
  message: string
  action: ErrorAction
  requestId?: string
  retryAfterSeconds?: number
}

const props = withDefaults(
  defineProps<{
    portfolio: PublicPortfolio
    initialRole?: AudienceRole
    initialQuestion?: string
    initialProject?: string
    initialEvidence?: string
    initialCase?: string
    initialSeed?: AgentRouteSeed | null
  }>(),
  {
    initialRole: 'INTERVIEWER',
    initialQuestion: '',
    initialProject: '',
    initialEvidence: '',
    initialCase: '',
    initialSeed: null,
  },
)
const emit = defineEmits<{
  navigatePortfolio: []
}>()

const sessions = useLocalSessions()
const split = useWorkspaceSplit()
const workspaceRoot = ref<HTMLElement | null>(null)
const workspaceWidth = ref(Number.POSITIVE_INFINITY)
const sessionDrawerOpen = ref(false)
const evidenceDrawerOpen = ref(false)
const sessionsIsDrawer = useMediaQuery('(max-width: 959.98px)')
const evidenceIsDrawer = useMediaQuery('(max-width: 1279.98px)')
const activeEvidenceId = ref('')
const evidenceTab = ref<EvidenceDeskTab>('EVIDENCE')
const focusedAnswerMessageId = ref('')
const answerFocusTarget = ref<AnswerFocusTarget | null>(null)
const pending = ref(false)
const answerFailure = ref<AnswerFailureView | null>(null)
const failedRequest = ref<AnswerRequestContext | null>(null)
const resolvedContractVersions = new Map<string, string>()
const semanticContinuations = new Map<string, SemanticContinuation>()
// 调整模式（决策 1 · 方案 B）：页面级单例状态，记录正在调整的会话与计划引用。
// planReference 缺失时不得进入调整态（后端合同尚未暴露待确认计划标识）。
const activeAdjustment = ref<{
  sessionId: string
  planReference: PendingPlanReference
  planTitle: string
} | null>(null)
const activeCaseSlug = ref(
  props.portfolio.cases.some((item) => item.slug === props.initialCase)
    ? props.initialCase
    : '',
)
let activeRequest: AnswerRequestContext | null = null
let activeRequestController: AbortController | null = null
let requestVersion = 0
let answerFocusRequestId = 0
let disposed = false
let workspaceResizeObserver: ResizeObserver | null = null
let retryDelayTimer: ReturnType<typeof setInterval> | null = null
let drawerReturnFocus: HTMLElement | null = null

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

const activeCase = computed(
  () => props.portfolio.cases.find((item) => item.slug === activeCaseSlug.value),
)

// 调整条展示状态：只对持有该计划的当前会话展示。
const adjustmentBarState = computed<PlanAdjustmentBarState | null>(() => {
  const adjustment = activeAdjustment.value
  if (!adjustment || adjustment.sessionId !== sessions.activeSessionId.value) return null
  return { planTitle: adjustment.planTitle }
})

const activeProject = computed(() => {
  const projectSlug =
    activeCase.value?.projectSlug ||
    sessions.activeSession.value?.projectSlug ||
    props.initialProject
  return props.portfolio.projects.find((project) => project.slug === projectSlug) ??
    props.portfolio.projects[0]
})

const evidenceContext = computed(() =>
  buildEvidenceDeskContext(
    sessions.activeSession.value?.messages ?? [],
    focusedAnswerMessageId.value,
  ),
)

function clearFocusedAnswer() {
  focusedAnswerMessageId.value = ''
  answerFocusTarget.value = null
}

function resetEvidenceFocus() {
  clearFocusedAnswer()
  evidenceTab.value = 'EVIDENCE'
}

function coreEvidenceId(
  session = sessions.activeSession.value,
) {
  const caseEvidenceId = activeCase.value?.evidence[0]?.id
  if (caseEvidenceId) return caseEvidenceId
  const projectSlug = session?.projectSlug || props.initialProject
  const project =
    props.portfolio.projects.find((item) => item.slug === projectSlug) ??
    props.portfolio.projects[0]
  return (
    session?.evidenceId ||
    project?.evidenceIds[0] ||
    props.portfolio.evidence[0]?.id ||
    ''
  )
}

function syncActiveEvidence() {
  activeEvidenceId.value = coreEvidenceId()
}

function createSession(initialEvidenceId = '') {
  resetEvidenceFocus()
  const project = activeProject.value
  const evidenceId =
    initialEvidenceId ||
    activeCase.value?.evidence[0]?.id ||
    project?.evidenceIds[0] ||
    props.portfolio.evidence[0]?.id ||
    ''
  const session = sessions.createSession({
    role: props.initialRole,
    projectSlug: project?.slug ?? null,
    evidenceId: evidenceId || null,
  })
  activeEvidenceId.value = coreEvidenceId(session)
}

function clearAnswerFailure() {
  if (retryDelayTimer) clearInterval(retryDelayTimer)
  retryDelayTimer = null
  answerFailure.value = null
  failedRequest.value = null
}

function failureMessage(action: ErrorAction): string {
  switch (action) {
    case 'RETRY_AFTER':
      return '请求过于频繁，请在倒计时结束后重试'
    case 'CORRECT_INPUT':
      return '请检查问题后再试'
    case 'NAVIGATE_BACK':
      return '当前项目不可用，请返回作品集后继续浏览'
    case 'RETRY':
    default:
      return 'Agent 暂时无法回答，请稍后重试'
  }
}

function toAnswerFailure(error: unknown): AnswerFailureView {
  if (error instanceof PortfolioApiError) {
    return {
      message: failureMessage(error.action),
      action: error.action,
      requestId: error.requestId,
      retryAfterSeconds: error.action === 'RETRY_AFTER'
        ? error.retryAfterSeconds
        : undefined,
    }
  }
  return {
    message: failureMessage('RETRY'),
    action: 'RETRY',
  }
}

const failureSuggestions = computed<ConversationSuggestedQuestion[]>(() => {
  if (!answerFailure.value || answerFailure.value.action === 'NONE') return []
  const project = activeProject.value
  if (!project) return []
  const local = project.suggestedQuestions.map((text) => ({
    text,
    projectSlug: project.slug,
    caseSlug: null,
    facet: null,
  }))
  return completeSuggestedQuestions(local, props.portfolio, {
    currentQuestion: failedRequest.value?.question,
    recentQuestions: (sessions.activeSession.value?.messages ?? [])
      .filter((message) => message.role === 'USER')
      .slice(-6)
      .map((message) => message.content),
  }).questions
})

function invalidatePendingRequest() {
  activeRequestController?.abort()
  activeRequestController = null
  requestVersion += 1
  activeRequest = null
  pending.value = false
}

function buildSemanticContext(
  session: NonNullable<typeof sessions.activeSession.value>,
  context: AnswerRequestContext,
): SemanticContextRequest {
  const activeSubjects = context.caseSlug
    ? [{ subjectType: 'CASE', subjectId: context.caseSlug }]
    : context.projectSlug
      ? [{ subjectType: 'PROJECT', subjectId: context.projectSlug }]
      : []
  return {
    activeSubjects,
    resultReferences: [],
    audienceRole: session.role,
    requestSource: context.caseSlug ? 'CASE' : 'AGENT_PAGE',
    coveredTopics: [...session.coveredTopics],
  }
}

function completedConversationHistory(
  messages: AgentSession['messages'],
): AgentSession['messages'] {
  const completed: AgentSession['messages'] = []
  for (let index = 0; index + 1 < messages.length; index += 1) {
    const user = messages[index]
    const assistant = messages[index + 1]
    if (user?.role !== 'USER' || assistant?.role !== 'AGENT') continue
    completed.push(user, assistant)
    index += 1
  }
  return completed.slice(-40)
}

interface SemanticContinuation {
  question: string
  semanticContext: SemanticContextRequest
}

// FE-F08：continuation 生命周期统一收口。
// 仅当一轮到达「已回答且无待确认/待澄清/失效」终态时才清除；
// 待确认、澄清中、计划失效都仍需原问题与结构化上下文。
function settleSemanticContinuation(sessionId: string, answer: ReturnType<typeof mapAnswerResponse>) {
  const semanticTurn = answer.semanticTurn
  const hasPendingState = semanticTurn !== undefined
    && (semanticTurn.disposition === 'CONFIRMATION_REQUIRED'
      || semanticTurn.clarification !== undefined
      || semanticTurn.planChange !== undefined)
  if (!hasPendingState) semanticContinuations.delete(sessionId)
}

function clearSemanticContinuation(sessionId: string) {
  semanticContinuations.delete(sessionId)
}

async function requestAnswer(context: AnswerRequestContext, appendUser: boolean) {
  const session = sessions.sessions.value.find((item) => item.id === context.sessionId)
  if (!session || pending.value || disposed) {
    if (!session) clearAnswerFailure()
    return
  }

  if (context.question && context.action === undefined) {
    semanticContinuations.set(session.id, {
      question: context.question,
      semanticContext: context.semanticContext ?? buildSemanticContext(session, context),
    })
  }

  const preparedContext = context.requestToken
    ? context
    : {
        ...context,
        coveredTopics: context.coveredTopics ?? [...session.coveredTopics],
        requestToken: createRequestToken(),
      }
  const controller = new AbortController()
  clearFocusedAnswer()
  if (appendUser) {
    if (!context.question) return
    sessions.appendMessage(session.id, {
      role: 'USER',
      content: context.question,
      answer: null,
      evidenceIds: [],
    })
  }
  const request = ++requestVersion
  activeRequest = preparedContext
  activeRequestController = controller
  pending.value = true
  clearAnswerFailure()
  const requestStartedAt = Date.now()
  try {
    // Build conversation history from current session (last 40 messages = 20 rounds)
    const completedMessages = session.messages.at(-1)?.role === 'USER'
      ? session.messages.slice(0, -1)
      : session.messages
    const history = completedConversationHistory(completedMessages)
      .map((m) => {
        let content = m.content
        if (m.role === 'AGENT' && m.answer) {
          if (m.answer.summary) {
            content = m.answer.summary
          } else if (m.answer.sections?.length) {
            content = m.answer.sections.map((s) => s.content).join('\n\n')
          }
        }
        return {
          role: m.role === 'USER' ? 'USER' as const : 'ASSISTANT' as const,
          content,
        }
      })

    const response = await askWithPresetContractRetry({
        turnId: globalThis.crypto?.randomUUID?.() ?? `turn-${Date.now()}`,
        requestToken: preparedContext.requestToken,
        action: preparedContext.action,
        agentTurnContract: preparedContext.action === undefined ? undefined : 'stp-v1',
        planConfirmation: preparedContext.planConfirmation,
        semanticContext: preparedContext.semanticContext,
        invalidatedPlanReference: preparedContext.invalidatedPlanReference,
        planAdjustment: preparedContext.planAdjustment,
        clarificationResolution: preparedContext.clarificationResolution,
        signal: controller.signal,
        projectSlug: preparedContext.caseSlug ? null : preparedContext.projectSlug,
        caseSlug: preparedContext.caseSlug ?? null,
        audienceRole: session.role,
        source: context.caseSlug ? 'CASE' : 'AGENT_PAGE',
        focusEvidenceIds: session.evidenceId ? [session.evidenceId] : [],
        questionPresetId: preparedContext.questionPresetId,
        contractVersion: preparedContext.contractVersion,
        question: preparedContext.question,
        messages: history,
        coveredTopics: preparedContext.coveredTopics,
        referenceContext: preparedContext.referenceContext,
        recommendationContext: preparedContext.recommendationContext,
      }, askQuestion)
    if (disposed || request !== requestVersion) return
    if (response.questionPresetId && response.contractVersion) {
      resolvedContractVersions.set(response.questionPresetId, response.contractVersion)
    }
    if (isPresetContractStale(response)) {
      answerFailure.value = {
        message: '这个推荐问题正在更新，请刷新后重试。',
        action: 'NONE',
      }
      return
    }
    if (isPresetContractUnavailable(response)) {
      answerFailure.value = {
        message: '这个推荐问题暂时无法回答，内容正在更新。',
        action: 'NONE',
      }
      return
    }
    sessions.acceptSemanticTurnResponse(session.id, response)
    const mapped = mapAnswerResponse(response)
    settleSemanticContinuation(session.id, mapped)
    if (activeAdjustment.value?.sessionId === session.id) activeAdjustment.value = null
    if (!mapped.referenceContext
      && mapped.resolution === 'ANSWERED'
      && (mapped.answerScope === 'PORTFOLIO' || mapped.answerScope === 'MIXED')
      && (preparedContext.projectSlug || preparedContext.caseSlug)) {
      const referencedClaimIds = [...new Set(
        mapped.sections.flatMap((section) => section.claimIds ?? []),
      )]
      mapped.referenceContext = {
        previousContentVersion: mapped.contentVersion,
        projectSlugs: preparedContext.projectSlug ? [preparedContext.projectSlug] : [],
        caseSlugs: preparedContext.caseSlug ? [preparedContext.caseSlug] : [],
        questionPresetId: preparedContext.questionPresetId,
        referencedClaimIds,
        followUpAction: 'RELATED_QUESTION',
      }
    }
    clearFocusedAnswer()
    const completed = completeSuggestedQuestions(mapped.suggestedQuestions, props.portfolio, {
      currentQuestion: preparedContext.question,
      recentQuestions: session.messages
        .filter((message) => message.role === 'USER')
        .slice(-6)
        .map((message) => message.content),
    })
    mapped.suggestedQuestions = completed.questions
    if (completed.recoveredCount > 0) {
      frontendDiagnostics.report(createFrontendDiagnosticEvent({
        eventName: 'frontend.response.invalid',
        errorCode: 'SUGGESTION_CONTRACT_RECOVERED',
        errorKind: 'INVALID_RESPONSE',
        turnId: mapped.turnId,
        recoveredCount: completed.recoveredCount,
        ...(mapped.guidanceStage === null ? {} : { guidanceStage: mapped.guidanceStage }),
      }))
    }
    sessions.appendMessage(session.id, {
      role: 'AGENT',
      content: mapped.summary,
      answer: mapped,
      evidenceIds: mapped.evidenceIds,
    })
    sessions.applyAnswerProgress(session.id, mapped)
    frontendDiagnostics.report(createFrontendDiagnosticEvent({
      eventName: 'frontend.agent.request.completed',
      turnId: mapped.turnId,
      durationBucket: durationBucketFor(Date.now() - requestStartedAt),
      httpStatus: 200,
      ...(mapped.generationMode === undefined
        ? {}
        : { generationMode: mapped.generationMode }),
      degraded: mapped.degraded === true,
      ...(mapped.guidanceStage === null
        ? {}
        : { guidanceStage: mapped.guidanceStage }),
      suggestedQuestionCount: mapped.suggestedQuestions.length,
      contentVersion: mapped.contentVersion,
    }))
  } catch (error) {
    if (disposed || request !== requestVersion) return
    if (controller.signal.aborted
      || (error instanceof PortfolioApiError && error.action === 'NONE')) {
      clearAnswerFailure()
      return
    }
    failedRequest.value = preparedContext
    const failure = toAnswerFailure(error)
    answerFailure.value = failure
    if (failure.retryAfterSeconds) {
      startRetryDelay(failure.retryAfterSeconds)
    }
  } finally {
    if (!disposed && request === requestVersion) {
      activeRequest = null
      activeRequestController = null
      pending.value = false
    }
  }
}

function startRetryDelay(seconds: number) {
  if (retryDelayTimer) clearInterval(retryDelayTimer)
  if (!answerFailure.value) return
  answerFailure.value = {
    ...answerFailure.value,
    retryAfterSeconds: Math.max(1, Math.ceil(seconds)),
  }
  retryDelayTimer = setInterval(() => {
    if (!answerFailure.value) return
    const retryAfterSeconds = Math.max(0, (answerFailure.value.retryAfterSeconds ?? 0) - 1)
    answerFailure.value = { ...answerFailure.value, retryAfterSeconds }
    if (retryAfterSeconds === 0 && retryDelayTimer) {
      clearInterval(retryDelayTimer)
      retryDelayTimer = null
    }
  }, 1_000)
}

function cancelAnswer() {
  activeRequestController?.abort()
}

function submit(question: string) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  if (!session || !project) return
  activeAdjustment.value = null
  const preset = props.portfolio.questionPresets.find(
    (item) => item.projectSlug === project.slug && item.text === question,
  )
  void requestAnswer(
    {
      sessionId: session.id,
      projectSlug: project.slug,
      caseSlug: activeCaseSlug.value || null,
      question,
      questionPresetId: preset?.id,
      contractVersion: preset
        ? resolvedContractVersions.get(preset.id) ?? preset.contractVersion
        : undefined,
    },
    true,
  )
}

function confirmSemanticPlan(confirmation: OpaquePlanConfirmation) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  if (!session || !project || pending.value) return
  // stale-click guard + 唯一未决动作校验：仅防止旧卡/过期卡触发，不是安全边界。
  const action = activeSemanticActionFor(session.id)
  if (action?.kind !== 'CONFIRMATION' || action.confirmationId !== confirmation.confirmationId) return
  if (session.pendingConfirmation?.confirmationId !== confirmation.confirmationId) return
  void requestAnswer({
    sessionId: session.id,
    projectSlug: project.slug,
    caseSlug: activeCaseSlug.value || null,
    action: 'CONFIRM_PLAN',
    planConfirmation: confirmation,
  }, false)
}

function latestDisplayPlan(sessionId: string) {
  const session = sessions.sessions.value.find((item) => item.id === sessionId)
  if (!session) return undefined
  for (let index = session.messages.length - 1; index >= 0; index -= 1) {
    const plan = session.messages[index]?.answer?.semanticTurn?.displayPlan
    if (plan) return plan
  }
  return undefined
}

// 唯一未决动作（P1 收口）：事件身份校验的唯一依据。
// 旧卡片即使因时序或注入触发事件，也不得消费或清除属于最新轮次的 continuation。
function activeSemanticActionFor(sessionId: string) {
  const session = sessions.sessions.value.find((item) => item.id === sessionId)
  if (!session) return null
  return resolveActiveSemanticAction(session, (turnId) => sessions.isPlanChangeDismissed(turnId))
}

// 调整模式（决策 1）：进入需要当前未决动作为待确认计划，且引用齐备。
function adjustSemanticPlan() {
  const session = sessions.activeSession.value
  if (!session || pending.value || !session.pendingConfirmation) return
  if (activeSemanticActionFor(session.id)?.kind !== 'CONFIRMATION') return
  const plan = latestDisplayPlan(session.id)
  if (!plan?.pendingPlanReference) return
  activeAdjustment.value = {
    sessionId: session.id,
    planReference: plan.pendingPlanReference,
    planTitle: plan.summaryLabel
      ? `${plan.taskCount} 步 · ${plan.summaryLabel}`
      : `${plan.taskCount} 项任务`,
  }
}

function exitPlanAdjustment() {
  activeAdjustment.value = null
}

function submitPlanAdjustment(instruction: string) {
  const adjustment = activeAdjustment.value
  const session = sessions.activeSession.value
  const project = activeProject.value
  const continuation = session ? semanticContinuations.get(session.id) : undefined
  const trimmed = instruction.trim()
  if (!adjustment || !session || !project || !continuation || !trimmed || pending.value) return
  if (adjustment.sessionId !== session.id) return
  void requestAnswer({
    sessionId: session.id,
    projectSlug: project.slug,
    caseSlug: activeCaseSlug.value || null,
    action: 'ASK',
    question: continuation.question,
    semanticContext: {
      ...continuation.semanticContext,
      pendingPlanReference: { ...adjustment.planReference },
      coveredTopics: [...(continuation.semanticContext.coveredTopics ?? [])],
    },
    planAdjustment: {
      instruction: trimmed,
      pendingPlanReference: { ...adjustment.planReference },
    },
  }, false)
}

function cancelSemanticPlan() {
  const session = sessions.activeSession.value
  if (!session) return
  sessions.clearPendingConfirmation(session.id)
  activeAdjustment.value = null
  clearSemanticContinuation(session.id)
}

// 澄清提交（§11.1 目标合同）：受控 clarificationResolution，不按 fieldKey 猜类型（FE-F03）。
// turnId 必须对应当前唯一未决澄清动作，旧澄清卡的事件一律拒绝（FE-F11）。
function submitClarification(payload: {
  turnId: string
  clarification: ClarificationView
  submission: ClarificationSubmission
}) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  const continuation = session ? semanticContinuations.get(session.id) : undefined
  if (!session || !project || !continuation || pending.value) return

  const action = activeSemanticActionFor(session.id)
  if (action?.kind !== 'CLARIFICATION' || action.turnId !== payload.turnId) return

  const { clarification, submission } = payload
  if (submission.kind === 'MULTI_CHOICE') {
    // 合同暂未提供多值 resolution 通道：受控主体引用合并进 activeSubjects（全部受控才可到此）。
    const references = submission.options
      .map((option) => option.subjectReference)
      .filter((reference): reference is NonNullable<typeof reference> => reference !== null)
    if (references.length !== submission.options.length || references.length === 0) return
    const existing = continuation.semanticContext.activeSubjects ?? []
    const merged = [...existing]
    for (const reference of references) {
      if (!merged.some((item) => item.subjectType === reference.subjectType
        && item.subjectId === reference.subjectId)) {
        merged.push({ ...reference })
      }
    }
    void requestAnswer({
      sessionId: session.id,
      projectSlug: project.slug,
      caseSlug: activeCaseSlug.value || null,
      action: 'ASK',
      question: continuation.question,
      semanticContext: {
        ...continuation.semanticContext,
        activeSubjects: merged,
        coveredTopics: [...(continuation.semanticContext.coveredTopics ?? [])],
      },
    }, false)
    return
  }

  if (!clarification.clarificationId || !clarification.promptCode) return
  const resolution: ClarificationResolutionRequest = submission.kind === 'TEXT'
    ? {
        clarificationId: clarification.clarificationId,
        promptCode: clarification.promptCode,
        fieldKey: submission.fieldKey,
        textValue: submission.text,
      }
    : {
        clarificationId: clarification.clarificationId,
        promptCode: clarification.promptCode,
        fieldKey: submission.fieldKey,
        selectedOption: {
          value: submission.option.value,
          ...(submission.option.subjectReference === null
            ? {}
            : { subjectReference: { ...submission.option.subjectReference } }),
        },
      }
  void requestAnswer({
    sessionId: session.id,
    projectSlug: project.slug,
    caseSlug: activeCaseSlug.value || null,
    action: 'ASK',
    question: continuation.question,
    semanticContext: {
      ...continuation.semanticContext,
      coveredTopics: [...(continuation.semanticContext.coveredTopics ?? [])],
    },
    clarificationResolution: resolution,
  }, false)
}

function regenerateSemanticPlan(turnId: string) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  const continuation = session ? semanticContinuations.get(session.id) : undefined
  if (!session || !project || !continuation || pending.value) return
  // 只有当前唯一未决失效动作可以触发重生成；旧失效卡的注入事件一律拒绝。
  const action = activeSemanticActionFor(session.id)
  if (action?.kind !== 'PLAN_INVALIDATION' || action.turnId !== turnId) return
  const invalidatedPlanReference = session.messages
    .find((message) => message.answer?.turnId === turnId)
    ?.answer?.semanticTurn?.planChange?.invalidatedPlanReference
  if (!invalidatedPlanReference) return
  void requestAnswer({
    sessionId: session.id,
    projectSlug: project.slug,
    caseSlug: activeCaseSlug.value || null,
    action: 'REGENERATE_PLAN',
    question: continuation.question,
    semanticContext: continuation.semanticContext,
    invalidatedPlanReference,
  }, false)
}

// 失效卡「暂不处理」（决策 3）：纯本地 dismiss + continuation 清理，不发请求。
// 旧失效卡绝不清除新确认计划的 continuation——只有当前未决失效动作可 dismiss。
function dismissSemanticPlanChange(turnId: string) {
  const session = sessions.activeSession.value
  if (!session) return
  const action = activeSemanticActionFor(session.id)
  if (action?.kind !== 'PLAN_INVALIDATION' || action.turnId !== turnId) return
  sessions.dismissPlanChange(turnId)
  clearSemanticContinuation(session.id)
}

function submitSuggestion(suggestion: ConversationSuggestedQuestion) {
  const session = sessions.activeSession.value
  if (!session) return
  activeAdjustment.value = null
  void requestAnswer(
    {
      sessionId: session.id,
      projectSlug: suggestion.projectSlug ?? null,
      caseSlug: suggestion.caseSlug ?? null,
      question: suggestion.text,
    },
    true,
  )
}

function clearCaseContext() {
  activeCaseSlug.value = ''
  syncActiveEvidence()
}

function retryAnswer() {
  const failure = answerFailure.value
  if (!failure || !['RETRY', 'RETRY_AFTER'].includes(failure.action)) return
  if ((failure.retryAfterSeconds ?? 0) > 0) return
  const context = failedRequest.value
  if (!context) return
  const sessionExists = sessions.sessions.value.some((item) => item.id === context.sessionId)
  if (!sessionExists) {
    clearAnswerFailure()
    return
  }
  void requestAnswer(context, false)
}

function navigateBackFromFailure() {
  clearAnswerFailure()
  clearCaseContext()
  emit('navigatePortfolio')
}

function previewSplit(key: keyof WorkspaceSplit, value: number) {
  setEffectiveSplit(key, value)
}

function adjustSplit(key: keyof WorkspaceSplit, delta: number) {
  setEffectiveSplit(key, effectiveSplit.value[key] + delta, true)
}

function setEffectiveSplit(
  key: keyof WorkspaceSplit,
  value: number,
  persistChange = false,
) {
  const other: keyof WorkspaceSplit = key === 'sessions' ? 'evidence' : 'sessions'
  const [minimum] = WORKSPACE_LIMITS[key]
  const target = Math.min(effectiveMaximums.value[key], Math.max(minimum, value))
  const next = { ...effectiveSplit.value, [key]: target }

  if (!evidenceIsDrawer.value && Number.isFinite(availableSideWidth.value)) {
    const overflow = next.sessions + next.evidence - availableSideWidth.value
    if (overflow > 0) {
      next[other] = Math.max(WORKSPACE_LIMITS[other][0], next[other] - overflow)
    }
  }

  split.set(other, next[other])
  split.set(key, next[key], persistChange)
}

function updateWorkspaceWidth() {
  const width = workspaceRoot.value?.clientWidth ?? 0
  if (width > 0) workspaceWidth.value = width
}

function inspectEvidence(request: EvidenceInspectRequest) {
  const trigger = document.activeElement
  drawerReturnFocus =
    trigger instanceof HTMLElement && trigger !== document.body ? trigger : null
  focusedAnswerMessageId.value = request.messageId
  activeEvidenceId.value = request.evidenceIds[0] ?? activeEvidenceId.value
  evidenceTab.value = 'CITATIONS'
  sessionDrawerOpen.value = false
  evidenceDrawerOpen.value = true
  if (evidenceIsDrawer.value) focusDrawer('#agent-evidence-desk')
}

function locateAnswer(target: Omit<AnswerFocusTarget, 'requestId'>) {
  if (evidenceDrawerOpen.value && evidenceIsDrawer.value) {
    closeDrawers()
  }
  answerFocusRequestId += 1
  answerFocusTarget.value = {
    ...target,
    requestId: answerFocusRequestId,
  }
}

function toggleSessions() {
  sessionDrawerOpen.value = !sessionDrawerOpen.value
  drawerReturnFocus = null
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
      referenceContext: action.referenceContext,
    },
    true,
  )
}

// 推荐调整原样回传当前回答的完整 recommendationContext，仍走 /api/v2/answers。
// 普通问题（submit / submitSuggestion / submitFollowUp）不构造 recommendationContext，
// 因此不会携带陈旧推荐上下文。
function refineRecommendation(action: {
  question: string
  recommendationContext: PortfolioRecommendationContextRequest
}) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  if (!session || !project) return
  void requestAnswer(
    {
      sessionId: session.id,
      projectSlug: project.slug,
      question: action.question,
      recommendationContext: action.recommendationContext,
    },
    true,
  )
}

function toggleEvidence() {
  evidenceDrawerOpen.value = !evidenceDrawerOpen.value
  drawerReturnFocus = null
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
  resetEvidenceFocus()
  semanticContinuations.clear()
  activeAdjustment.value = null
  sessions.clearSessions()
  sessions.pruneDismissedPlanChanges()
  createSession()
}

function removeSession(sessionId: string) {
  const previousSessionId = sessions.activeSessionId.value
  if (activeRequest?.sessionId === sessionId) {
    invalidatePendingRequest()
  }
  if (failedRequest.value?.sessionId === sessionId) {
    clearAnswerFailure()
  }
  if (activeAdjustment.value?.sessionId === sessionId) {
    activeAdjustment.value = null
  }
  clearSemanticContinuation(sessionId)
  sessions.removeSession(sessionId)
  sessions.pruneDismissedPlanChanges()
  if (sessions.activeSessionId.value !== previousSessionId) {
    resetEvidenceFocus()
    if (sessions.activeSession.value) {
      syncActiveEvidence()
    } else {
      createSession()
    }
  }
}

function selectSession(sessionId: string) {
  const previousSessionId = sessions.activeSessionId.value
  sessions.selectSession(sessionId)
  if (sessions.activeSessionId.value !== previousSessionId) {
    activeAdjustment.value = null
    resetEvidenceFocus()
    syncActiveEvidence()
  }
}

function closeDrawers(restoreFocus = false) {
  const fallbackSelector = evidenceDrawerOpen.value ? '.evidence-toggle' : '.session-toggle'
  const returnFocus = drawerReturnFocus?.isConnected
    ? drawerReturnFocus
    : document.querySelector<HTMLElement>(fallbackSelector)
  drawerReturnFocus = null
  sessionDrawerOpen.value = false
  evidenceDrawerOpen.value = false
  if (restoreFocus) {
    requestAnimationFrame(() => {
      returnFocus?.focus()
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
  sessions.seedSession(props.initialSeed)
  syncActiveEvidence()
} else if (!sessions.activeSession.value) {
  createSession(props.initialEvidence)
}

onMounted(() => {
  window.addEventListener('keydown', onWindowKeydown)
  window.addEventListener('resize', updateWorkspaceWidth)
  updateWorkspaceWidth()
  if (typeof ResizeObserver !== 'undefined' && workspaceRoot.value) {
    workspaceResizeObserver = new ResizeObserver(updateWorkspaceWidth)
    workspaceResizeObserver.observe(workspaceRoot.value)
  }
})
onBeforeUnmount(() => {
  disposed = true
  if (retryDelayTimer) clearInterval(retryDelayTimer)
  invalidatePendingRequest()
  semanticContinuations.clear()
  activeAdjustment.value = null
  workspaceResizeObserver?.disconnect()
  window.removeEventListener('keydown', onWindowKeydown)
  window.removeEventListener('resize', updateWorkspaceWidth)
})
</script>

<template>
  <main
    v-if="activeProject && sessions.activeSession.value"
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
      @select="selectSession"
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

    <ConversationThread
      :session="sessions.activeSession.value"
      :role="sessions.activeSession.value.role"
      :project="activeProject"
      :seed-question="initialQuestion"
      :case-context-title="activeCase?.title"
      :suggested-questions="activeCase?.suggestedQuestions"
      :sessions-open="sessionDrawerOpen"
      :evidence-open="evidenceDrawerOpen"
      :pending="pending"
      :failure="answerFailure"
      :failure-suggestions="failureSuggestions"
      :focus-target="answerFocusTarget"
      :adjustment="adjustmentBarState"
      :dismissed-plan-changes="sessions.dismissedPlanChangeTurnIds.value"
      @submit="submit"
      @submit-suggestion="submitSuggestion"
      @follow-up="submitFollowUp"
      @refine-recommendation="refineRecommendation"
      @retry="retryAnswer"
      @navigate-back="navigateBackFromFailure"
      @cancel="cancelAnswer"
      @inspect-evidence="inspectEvidence"
      @toggle-sessions="toggleSessions"
      @toggle-evidence="toggleEvidence"
      @clear-case-context="clearCaseContext"
      @confirm-plan="confirmSemanticPlan"
      @adjust-plan="adjustSemanticPlan"
      @adjust-submit="submitPlanAdjustment"
      @adjust-exit="exitPlanAdjustment"
      @cancel-plan="cancelSemanticPlan"
      @clarification-submit="submitClarification"
      @regenerate-plan="regenerateSemanticPlan"
      @dismiss-plan-change="dismissSemanticPlanChange"
    />

    <PaneResizer
      class="evidence-resizer"
      label="调整证据工作台宽度"
      :value="effectiveSplit.evidence"
      :min="WORKSPACE_LIMITS.evidence[0]"
      :max="effectiveMaximums.evidence"
      :direction="-1"
      @preview="previewSplit('evidence', $event)"
      @commit="split.persist"
      @adjust="adjustSplit('evidence', $event)"
      @reset="split.reset"
    />

    <EvidenceDesk
      :evidence="activeCase?.evidence ?? portfolio.evidence"
      :project="activeProject"
      :active-evidence-id="activeEvidenceId"
      :focus-evidence-ids="evidenceContext.focusEvidenceIds"
      :citations="evidenceContext.citations"
      :tab="evidenceTab"
      :inert="evidenceIsDrawer && !evidenceDrawerOpen ? true : undefined"
      :aria-hidden="evidenceIsDrawer ? String(!evidenceDrawerOpen) : undefined"
      @update:tab="evidenceTab = $event"
      @select="activeEvidenceId = $event"
      @locate-answer="locateAnswer"
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

.session-resizer {
  left: var(--sessions-width);
}

.evidence-resizer {
  right: var(--evidence-width);
  transform: translateX(6px);
}

.workspace-scrim {
  display: none;
}

@media (max-width: 1279.98px) {
  .agent-workspace {
    grid-template-columns: var(--sessions-width) minmax(0, 1fr);
  }

  .evidence-resizer {
    display: none;
  }

  :deep(.evidence-desk) {
    position: absolute;
    z-index: 70;
    grid-area: 1 / 1 / -1 / -1;
    inset: 0 0 0 auto;
    height: 100%;
    width: min(88%, 520px);
    transform: translateX(100%);
    transition: transform 220ms ease;
  }

  .evidence-open :deep(.evidence-desk) {
    transform: translateX(0);
  }

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
  .agent-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

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

  .sessions-open :deep(.session-rail) {
    transform: translateX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.evidence-desk),
  :deep(.session-rail),
  .workspace-scrim {
    transition: none;
    animation: none;
  }

  :deep(.thread-empty),
  :deep(.thread-empty button),
  :deep(.message),
  :deep(.evidence-card),
  :deep(.citation-card),
  :deep(.source-card) {
    scroll-behavior: auto;
    transition: none;
    animation: none;
  }
}
</style>
