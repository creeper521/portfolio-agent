<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'

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
  type CurrentDiscussionSummary,
  type SurfaceContext,
} from '../api/agentTurnApi'
import { newRequestId } from '../api/requestId'
import {
  catalogEntryOfSelection,
  displayNameOfSelection,
  modelTagOfExecution,
  parseModelCatalogProjection,
  EMPTY_MODEL_CATALOG,
  type ModelCatalogProjection,
  type ModelSelection,
} from '../model/modelSelection'
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
import { audienceRolePresentations, presentationOf } from '../model/audienceRolePresentation'
import AnswerSourcesPanel from './AnswerSourcesPanel.vue'
import ConversationThread from './ConversationThread.vue'
import ModelSelector from './ModelSelector.vue'
import LocalSessionRail from './LocalSessionRail.vue'
import PaneResizer from './PaneResizer.vue'

// Agent 工作台（会话容器）：三栏布局的根组件，只负责会话容器、输入、
// 请求生命周期（request lifecycle）、内存 Session、活跃 ResumeToken 的
// sessionStorage 槽位维护与 API 协调；所有业务展示都投影在子组件树内。
// 会话数据来自 useLocalSessions（页面内存 + 活跃会话的 sessionStorage 槽位），
// 会话恢复来自 useConversationResume，分栏宽度来自 useWorkspaceSplit，
// 作品集与预设来自 props.portfolio。本地状态包括 pending 请求表、失败
// 视图表、清理/恢复通知与抽屉开关；由 AgentPage 直接挂载，不 emit 事件。
// 取消纪律：先结束本地 pending，再 best-effort DELETE + abort，
// 不在本地伪造 Cancelled 轮次（D-41）。
// 会话归属：pending、failure、draft、notice 一律归属各自 session，渲染
// 与操作只作用于当前活跃会话；pending 允许跨会话并存，结果回流原会话
// （A2-07/08/09）。
// 落账纪律：USER 轮次先落账后请求；失败/取消的轮次标记 failed 并排除出
// conversationWindow；澄清提交即把原挑战卡转只读，禁止重复 RESOLVE
// （A2-03/04/18）。

const FREE_TEXT_MAX_LENGTH = 2000
// 发往后端的会话窗口长度上限：只携带最近 N 条摘要，不传完整历史。
const CONVERSATION_WINDOW_LIMIT = 12
// 标签页合计 pending 上限与后端来源级最大并发 2 对齐：超出时不再发出
// 必然 409 TURN_IN_PROGRESS 的请求，仅在界面提示等待（§11.1）。
const TAB_PENDING_LIMIT = 2

interface PendingTurn {
  requestId: string
  sessionId: string
  question: string
  controller: AbortController
  userMessageId?: string
  /** RESOLVE_CLARIFICATION 待处理时携带；取消需据此恢复澄清卡（A2-70）。 */
  clarificationId?: string
}

/** 同一 requestId 重试时的请求快照：重试只消费这一份页面内存快照，不重算指纹类输入（A2-22）。
 * 快照含当轮 ModelSelection：同 requestId 重试原样复用（重试不换模型）；
 * 换模型重问构造全新快照与 requestId，绝不改写旧快照（UI spec §5.1）。 */
interface TurnSubmissionSnapshot {
  readonly requestId: string
  readonly modelSelection: ModelSelection
  readonly command: AgentTurnCommand
  readonly surfaceContext: SurfaceContext
  readonly conversationWindow: readonly ConversationWindowMessage[]
  readonly resumeToken?: string
  readonly displayQuestion: string
  readonly userMessageId?: string
}

/** 失败轮次的展示投影：由 turnFailureProjection 归类，可携带原提交快照供幂等重试。 */
interface FailureView {
  category: TurnFailureCategory
  message: string
  hint?: string
  retryable: boolean
  retryAfterSeconds?: number
  submission?: TurnSubmissionSnapshot
}

/** 归属到具体会话的工作台级通知（清理结果等），切换会话时不串显。 */
interface WorkspaceNotice {
  text: string
  sessionId: string
}

/** 建议问题 chip：已发布预设带 presetId（PRESET 命令），Case 建议只有文本（FREE_TEXT）。 */
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
// 工作台实测宽度（ResizeObserver 维护），用于把两栏宽度收敛到可用空间内。
const workspaceWidth = ref(Number.POSITIVE_INFINITY)
// 两个窄屏抽屉的开关：会话栏（≤959.98px）与来源栏（≤1279.98px）变为覆盖式抽屉。
const sessionDrawerOpen = ref(false)
const evidenceDrawerOpen = ref(false)
const sessionsIsDrawer = useMediaQuery('(max-width: 959.98px)')
const evidenceIsDrawer = useMediaQuery('(max-width: 1279.98px)')
// sessionId → 进行中的轮次；以替换整表 Map 的方式写入以触发响应式。
const pendingTurns = ref(new Map<string, PendingTurn>())
// sessionId → 最近一次失败投影；与 pending 互斥出现。
const failures = ref(new Map<string, FailureView>())
/** A7 五个 settled 模型终局的提交快照（UI spec §2.6）：按 requestId 索引并携带归属会话，供双动作复用。 */
interface ModelFailureContext {
  sessionId: string
  submission: TurnSubmissionSnapshot
  /** 该轮实际失败的 modelRef：以 turn.modelExecution 投影为准，前端不推断。 */
  failedModelRef: string | null
}
const modelFailureContexts = ref(new Map<string, ModelFailureContext>())
const clearPending = ref(false)
const clearNotice = ref<WorkspaceNotice | null>(null)
const resumeNotice = ref<WorkspaceNotice | null>(null)
const composerInput = ref<HTMLTextAreaElement | null>(null)
// 传给 ConversationThread 的定位目标；nonce 递增保证重复定位同一 section 也能触发。
const focusTarget = ref<{ sectionId: string; nonce: number } | null>(null)
let locateNonce = 0
// 卸载标记：置位后所有异步回调一律不再触碰会话状态。
let disposed = false
let workspaceResizeObserver: ResizeObserver | null = null

// Map 均以"复制新表再替换引用"的方式更新，确保 ref 整体保持响应式。
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

/** 仅当该 session 的 pending 仍是这次 requestId 时才移除：防止旧请求的回调误删后继请求的 pending。 */
function deletePendingGeneration(
  source: Map<string, PendingTurn>,
  sessionId: string,
  requestId: string,
): Map<string, PendingTurn> {
  return source.get(sessionId)?.requestId === requestId
    ? mapDelete(source, sessionId)
    : source
}

const activeSession = computed<AgentSession | null>(() => sessions.activeSession.value)
const activePending = computed(() => pendingTurns.value.get(activeSession.value?.id ?? '') ?? null)
/** 存在 pending Turn 的会话 id 列表：供会话行显示「· 生成中」（audience-role UI 设计 §4.2）。 */
const pendingSessionIds = computed(() => [...pendingTurns.value.keys()])
const activeFailure = computed(() => failures.value.get(activeSession.value?.id ?? '') ?? null)
/** 当前会话无 pending 但标签页 pending 已满：禁止发起任何新轮次，仅提示（§11.1）。 */
const tabPendingFull = computed(
  () => activePending.value === null && pendingTurns.value.size >= TAB_PENDING_LIMIT,
)
const freeTextRoutingAvailable = computed(
  () => props.portfolio.agentAvailability.freeTextSemanticRouting === 'AVAILABLE',
)

// ── 模型目录（UI spec §5.5）：只读 /api/portfolio 投影，随 portfolio 刷新 ──

const modelCatalog = computed<ModelCatalogProjection>(() => {
  // 目录只读 /api/portfolio 投影；测试或旧快照缺目录字段时按空目录 fail-closed。
  const availability = props.portfolio.agentAvailability as unknown
  return parseModelCatalogProjection(availability) ?? EMPTY_MODEL_CATALOG
})

/** 会话生效选择：显式偏好必须在当前目录内，否则回落目录默认（§2.9 判定输入）。 */
function effectiveSelectionOf(session: AgentSession): ModelSelection {
  const preference = session.modelSelection
  if (preference !== undefined && catalogEntryOfSelection(modelCatalog.value, preference) !== null) {
    return preference
  }
  return modelCatalog.value.defaultModelSelection
}

/** 目录刷新（如 portfolio 重取）后，失效的显式偏好回退目录默认并插入可见通知；
 * 该会话存在未完成 pending Turn 时不中途回退（单 Turn 单模型优先），终局后再生效（§2.9）。 */
function reconcileSessionModelSelections(): void {
  for (const session of sessions.sessions.value) {
    const preference = session.modelSelection
    if (preference === undefined || preference.kind === 'NONE') continue
    if (catalogEntryOfSelection(modelCatalog.value, preference) !== null) continue
    if (pendingTurns.value.has(session.id)) continue
    const fallbackName = displayNameOfSelection(
      modelCatalog.value,
      modelCatalog.value.defaultModelSelection,
    )
    sessions.setSessionModelSelection(session.id, undefined)
    sessions.appendSessionNotice(session.id, {
      kind: 'MODEL_STALE_FALLBACK',
      title: `${displayNameOfSelection(modelCatalog.value, preference) ?? preference.modelRef} 当前不可用，已回到目录默认 ${fallbackName ?? '确定性回答'}`,
    })
  }
}

watch(modelCatalog, () => {
  reconcileSessionModelSelections()
}, { immediate: false })

const activeDiscussion = computed(() => activeSession.value?.activeDiscussion)
/** 活跃会话的生效模型选择（显式偏好或目录默认）。 */
const activeModelSelection = computed<ModelSelection | null>(() =>
  activeSession.value === null ? null : effectiveSelectionOf(activeSession.value),
)
/** 目录有可选模型但生效选择为 NONE（默认未就绪）：自由文本必须先显式选择（设计 §8）。 */
const modelSelectionRequired = computed(
  () => modelCatalog.value.selectableModels.length > 0
    && activeModelSelection.value?.kind === 'NONE',
)
const discussionPaused = computed(
  () => activeDiscussion.value !== undefined
    && activeSession.value?.discussionPaused === true,
)
// 讨论到期标签的本地时钟：30 秒走一次，避免每秒重算倒计时文本。
const discussionClock = ref(Date.now())
let discussionClockTimer: ReturnType<typeof setInterval> | null = null
const discussionExpiryLabel = computed(() => {
  const discussion = activeDiscussion.value
  if (discussion === undefined) return ''
  const remaining = Date.parse(discussion.expiresAt) - discussionClock.value
  if (remaining <= 0 || discussion.status === 'EXPIRED') return '已到期'
  return `剩余约 ${Math.max(1, Math.ceil(remaining / 60_000))} 分钟`
})

/**
 * 客户端倒计时归零而本地仍是 ACTIVE 时，对权威 discussion summary 做一次
 * 冷取（fetchCurrentConversation），让 EXPIRED 状态与恢复动作不必刷新
 * 页面即可出现（A2-76/77）。
 * 并发控制：in-flight 集合按"会话 + revision + 到期时间"为 key，只阻止
 * 同一 discussion generation 的并发 GET；冷取失败或服务端仍返回 ACTIVE 时，
 * 下一个 30 秒时钟周期会再次尝试。
 */
const discussionExpirySyncInFlight = new Set<string>()
const discussionExpirySyncTarget = computed(() => {
  const session = activeSession.value
  const discussion = session?.activeDiscussion
  if (session === null || discussion === undefined) return null
  if (discussion.status !== 'ACTIVE') return null
  if (Date.parse(discussion.expiresAt) - discussionClock.value > 0) return null
  const resumeToken = sessions.getSessionResumeToken(session.id)
  if (resumeToken === undefined) return null
  const key = `${session.id}:${session.discussionRevision}:${discussion.expiresAt}`
  return { sessionId: session.id, resumeToken, key }
})

watch(discussionExpirySyncTarget, (target) => {
  if (target === null || discussionExpirySyncInFlight.has(target.key)) return
  discussionExpirySyncInFlight.add(target.key)
  void (async () => {
    try {
      const current = await fetchCurrentConversation(target.resumeToken)
      if (disposed || !current.ok) return
      sessions.adoptResumedConversation(target.sessionId, {
        conversationId: current.conversationId,
        resumeToken: target.resumeToken,
        discussionRevision: current.discussionRevision,
        ...(current.activeDiscussion === undefined
          ? {}
          : { activeDiscussion: current.activeDiscussion }),
      })
    } finally {
      discussionExpirySyncInFlight.delete(target.key)
    }
  })()
}, { immediate: true })

/** 会话私有草稿（A2-09）：切换会话草稿不串线。 */
const questionDraft = computed<string>({
  get: () => sessions.activeSession.value?.draft ?? '',
  set: (value: string) => {
    const session = sessions.activeSession.value
    if (session !== null) session.draft = value
  },
})

// 生效分栏宽度：来源栏以抽屉形态呈现时直接用持久化值，否则按工作台
// 实测宽度收敛，保证三栏互不挤压。
const effectiveSplit = computed(() =>
  evidenceIsDrawer.value
    ? split.state.value
    : fitWorkspaceSplit(split.state.value, workspaceWidth.value),
)

// 两个侧栏共享的可用总宽 = 工作台宽度 - 对话栏最小宽。
const availableSideWidth = computed(() =>
  Number.isFinite(workspaceWidth.value)
    ? Math.floor(workspaceWidth.value) - WORKSPACE_LIMITS.chatMin
    : Number.POSITIVE_INFINITY,
)

// 分隔条 aria 与拖拽的最大值：一侧调宽时另一侧至少保留其最小宽，
// 两栏上限互相钳制；抽屉形态或宽度未知时退回静态上限。
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

// 当前 Case 选项：仅接受作品集中真实存在的 slug，避免无效初值。
const activeCaseSlug = ref(
  props.portfolio.cases.some((item) => item.slug === props.initialCase)
    ? props.initialCase
    : '',
)
const activeCase = computed(
  () => props.portfolio.cases.find((item) => item.slug === activeCaseSlug.value),
)

// 当前项目上下文：优先 Case 绑定的项目，其次会话保存的项目，最后回落
// 到初始参数；始终有值以保证模板 v-if 与 surface hint 可用。
const activeProject = computed(() => {
  const projectSlug =
    activeCase.value?.projectSlug ||
    sessions.activeSession.value?.projectSlug ||
    props.initialProject
  return props.portfolio.projects.find((project) => project.slug === projectSlug)
    ?? props.portfolio.projects[0]
})

/** 建议问题：当前 Case 的建议问题优先（FREE_TEXT），其次匹配会话角色的 AGENT 预设（ASK/PRESET）。 */
const suggestionChips = computed<readonly SuggestionChip[]>(() => {
  if (activeCase.value !== undefined && activeCase.value.suggestedQuestions.length > 0) {
    return activeCase.value.suggestedQuestions.slice(0, 3).map((text) => ({ text }))
  }
  const role = activeSession.value?.role ?? props.initialRole
  return props.portfolio.questionPresets
    .filter((preset) => preset.placements.includes('AGENT') && preset.audiences.includes(role))
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

// 来源 key → 最近引用它的 sectionId（最新回答优先），供"定位"跳转使用（B7）。
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

/** 来源"定位"：找到引用该 key 的 section，以递增 nonce 更新 focusTarget 交给 ConversationThread 滚动高亮。 */
function locateSource(sourceKey: string): void {
  const sectionId = citedSectionByKey.value.get(sourceKey)
  if (sectionId === undefined) return
  locateNonce += 1
  focusTarget.value = { sectionId, nonce: locateNonce }
}

/** 请求表面上下文：优先当前 Case，其次会话绑定的项目，都没有则不带 subjectHint。 */
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

/** AGENT 轮次进入 conversationWindow 的短摘要：ANSWER 取 Goal 标签拼接，其余取 prompt/message。 */
function turnWindowSummary(turn: PublicAgentTurn): string {
  if (turn.kind === 'ANSWER') {
    return turn.answer.goalResults.map((goal) => goal.label).join('；')
  }
  if (turn.kind === 'CLARIFICATION') return turn.clarification.prompt
  return turn.message
}

/**
 * 判定澄清 reservation busy（CLARIFICATION_IN_PROGRESS）：它是可重试的
 * 临时终局，Challenge 未被消费、本轮对话也未推进；它不属于已结算的
 * 可信轮次，因此不得进入会话窗口（A2-69）。
 */
function isTransientClarificationBusy(turn: PublicAgentTurn): boolean {
  return turn.kind === 'CAPABILITY_UNAVAILABLE'
    && turn.code === 'CLARIFICATION_IN_PROGRESS'
}

/**
 * 构造发往后端的 conversationWindow：跳过失败/取消的 USER 轮次与临时
 * busy 终局，AGENT 侧只放窗口摘要；截取最近 N 条后仍需满足"USER 开头
 * 且 USER/ASSISTANT 交替"的合同，否则丢弃首条。
 */
function conversationWindowOf(session: AgentSession): ConversationWindowMessage[] {
  const window: ConversationWindowMessage[] = []
  for (const message of session.messages) {
    // 失败/取消的 USER 轮次不进入会话窗口，维持 USER/ASSISTANT 交替（A2-04）。
    if (message.failed === true) continue
    if (message.turn !== undefined && isTransientClarificationBusy(message.turn)) continue
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

/**
 * 提取最近的推荐讨论 ContextHandle（作为 ASK 的 referenceContextHandle）：
 * 只取最近一条 ANSWER 内的推荐；其后的非推荐回答代表话题已切换，
 * 不得向更早历史回溯旧推荐 Context（A2-59）。
 */
function latestRecommendationReference(
  session: AgentSession,
): string | undefined {
  for (const message of [...session.messages].reverse()) {
    // 调用方已追加当前 USER 消息，因此只跳过无 turn 的记录；
    // 一旦遇到最近 AGENT 终局就停止，非 ANSWER 同样截断旧 hint。
    const turn = message.turn
    if (turn === undefined) continue
    if (turn.kind !== 'ANSWER') return undefined
    for (const goal of [...turn.answer.goalResults].reverse()) {
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
    return undefined
  }
  return undefined
}

/** 把服务端返回的会话信封（conversationId/ResumeToken/discussion）写回对应会话；活跃会话的 Token 同步进 sessionStorage 槽位。 */
function bindConversationEnvelope(sessionId: string, conversation: {
  conversationId: string
  resumeToken?: string
  discussionRevision: number
  activeDiscussion?: CurrentDiscussionSummary
} | null): void {
  if (conversation === null) return
  const isActive = sessions.setSessionConversation(sessionId, conversation)
  if (isActive && conversation.resumeToken !== undefined) {
    resume.setActiveToken(conversation.resumeToken)
  }
}

/** 由统一失败投影构造展示视图，并挂上原提交快照以便"重试"幂等重放。 */
function failureViewOf(
  submission: TurnSubmissionSnapshot,
  f: AgentTurnFailure,
): FailureView {
  const view = projectTurnFailure(f)
  return {
    category: view.category,
    message: view.message,
    ...(view.hint === undefined ? {} : { hint: view.hint }),
    retryable: view.retryable,
    ...(view.retryAfterSeconds === undefined ? {} : { retryAfterSeconds: view.retryAfterSeconds }),
    submission,
  }
}

/** 允许调用方覆盖快照的默认值（重试时整体复用 submission，避免重算 window/token）。 */
interface TurnOverrides {
  surfaceContext?: SurfaceContext
  conversationWindow?: readonly ConversationWindowMessage[]
  resumeToken?: string
  displayQuestion?: string
  userMessageId?: string
  submission?: TurnSubmissionSnapshot
  /** 换模型重问时的新选择：进入全新快照，绝不改写任何旧快照（§5.1）。 */
  modelSelection?: ModelSelection
}

/**
 * 单次轮次的请求生命周期：组装不可变提交快照 -> 登记 pending ->
 * 发送 submitAgentTurn（可 abort）-> 回调结算。所有回调副作用都只属于
 * "安装该 pending 项的那一代请求"：被取消/替换的请求可能在 AbortSignal
 * 之后才返回，绝不允许污染后继轮次或会话状态（generation 校验）。
 * 成功路径：绑定会话信封、追加 AGENT 消息、焦点回到输入框。
 * 失败路径：按投影登记失败视图、USER 消息标 failed；CONTRACT 失败额外
 * 暂停该会话的讨论续谈。
 */
async function runTurn(
  sessionId: string,
  requestId: string,
  command: AgentTurnCommand,
  overrides: TurnOverrides = {},
): Promise<void> {
  const session = sessions.sessions.value.find((item) => item.id === sessionId)
  if (session === undefined) return
  const effectiveResumeToken = overrides.resumeToken ?? session.resumeToken
  const submission: TurnSubmissionSnapshot = overrides.submission ?? {
    requestId,
    modelSelection: overrides.modelSelection ?? effectiveSelectionOf(session),
    command,
    surfaceContext: overrides.surfaceContext ?? surfaceContextOf(session),
    conversationWindow: overrides.conversationWindow ?? conversationWindowOf(session),
    ...(effectiveResumeToken === undefined
      ? {}
      : { resumeToken: effectiveResumeToken }),
    displayQuestion: overrides.displayQuestion ?? displayQuestionOf(command),
    ...(overrides.userMessageId === undefined
      ? {}
      : { userMessageId: overrides.userMessageId }),
  }
  const controller = new AbortController()
  pendingTurns.value = mapSet(pendingTurns.value, sessionId, {
    requestId: submission.requestId,
    sessionId,
    question: submission.displayQuestion,
    controller,
    ...(submission.userMessageId === undefined ? {} : { userMessageId: submission.userMessageId }),
    ...(submission.command.kind === 'RESOLVE_CLARIFICATION'
      ? { clarificationId: submission.command.clarificationId }
      : {}),
  })
  failures.value = mapDelete(failures.value, sessionId)
  const result = await submitAgentTurn(
    {
      requestId: submission.requestId,
      modelSelection: submission.modelSelection,
      command: submission.command,
      surfaceContext: submission.surfaceContext,
      conversationWindow: submission.conversationWindow,
      ...(submission.resumeToken === undefined ? {} : { resumeToken: submission.resumeToken }),
    },
    { signal: controller.signal },
  )
  const ownsGeneration = pendingTurns.value.get(sessionId)?.requestId
    === submission.requestId
  pendingTurns.value = deletePendingGeneration(
    pendingTurns.value, sessionId, submission.requestId,
  )
  // 每个回调副作用都只属于安装 pending 项的那一代请求：被取消/替换的
  // 请求可能在 AbortSignal 之后才结算，不得改动后继轮次或会话。
  if (disposed || !ownsGeneration) return
  if (!result.ok) {
    // 取消是本地先行的：ABORTED 不追加消息、不显示错误。
    if (result.failure.kind === 'ABORTED') return
    if (result.failure.kind === 'CONTRACT') {
      sessions.setDiscussionPaused(sessionId, true)
    }
    failures.value = mapSet(
      failures.value,
      sessionId,
      failureViewOf(submission, result.failure),
    )
    if (submission.userMessageId !== undefined) {
      sessions.markMessageDelivery(sessionId, submission.userMessageId, true)
    }
    // 可恢复失败回滚乐观 consumed，卡片与失败重试均为合法再提交入口；
    // 不可恢复失败保持只读，避免对已失效会话制造必然失败的提交入口（A2-70）。
    if (
      submission.command.kind === 'RESOLVE_CLARIFICATION'
      && result.failure.retryable === true
    ) {
      sessions.markClarificationConsumed(
        sessionId, submission.command.clarificationId, false,
      )
    }
    return
  }
  if (submission.userMessageId !== undefined) {
    sessions.markMessageDelivery(sessionId, submission.userMessageId, false)
  }
  // 澄清 reservation busy（服务端处理中）：挑战未被消费，回滚乐观只读，
  // 并把本轮 USER 消息标记 failed，等待用户稍后重试提交。
  if (submission.command.kind === 'RESOLVE_CLARIFICATION'
    && result.turn.kind === 'CAPABILITY_UNAVAILABLE'
    && result.turn.code === 'CLARIFICATION_IN_PROGRESS') {
    sessions.markClarificationConsumed(
      sessionId, submission.command.clarificationId, false,
    )
    if (submission.userMessageId !== undefined) {
      sessions.markMessageDelivery(sessionId, submission.userMessageId, true)
    }
  }
  bindConversationEnvelope(sessionId, result.conversation)
  sessions.appendMessage(sessionId, {
    role: 'AGENT',
    content: turnWindowSummary(result.turn),
    turn: result.turn,
  })
  if (isModelUnavailableTerminal(result.turn)) {
    recordModelFailureContext(sessionId, result.turn.requestId, submission, result.turn)
  }
  // 终局解除 pending 后，补执行被延后的目录失效回退（§2.9）。
  reconcileSessionModelSelections()
  await focusComposer()
}

/** A7 冻结的五个 settled 模型不可用终局码（设计 §16.2）；只有它们提供换模型入口。 */
const MODEL_UNAVAILABLE_CODES: readonly string[] = [
  'MODEL_SELECTION_STALE',
  'SELECTED_MODEL_UNAVAILABLE',
  'SELECTED_MODEL_TEMPORARILY_UNAVAILABLE',
  'SELECTED_MODEL_RATE_LIMITED',
  'SELECTED_MODEL_INVALID_RESPONSE',
]

function isModelUnavailableTerminal(turn: PublicAgentTurn): boolean {
  return turn.kind === 'CAPABILITY_UNAVAILABLE'
    && MODEL_UNAVAILABLE_CODES.includes(turn.code)
}

/** 每会话只保留最新一个模型失败上下文，旧终局的动作入口随新上下文替换。 */
function recordModelFailureContext(
  sessionId: string,
  requestId: string,
  submission: TurnSubmissionSnapshot,
  turn: PublicAgentTurn,
): void {
  for (const [existingRequestId, existing] of modelFailureContexts.value) {
    if (existing.sessionId === sessionId && existingRequestId !== requestId) {
      modelFailureContexts.value = mapDelete(modelFailureContexts.value, existingRequestId)
    }
  }
  const execution = turn.modelExecution
  modelFailureContexts.value = mapSet(modelFailureContexts.value, requestId, {
    sessionId,
    submission,
    failedModelRef: execution?.selectionKind === 'MODEL' ? execution.requestedModelRef ?? null : null,
  })
}

/** 目录内除失败模型外的首选换用条目：目录默认优先，其次第一个其他条目（§2.6 动作二）。 */
function otherSelectableModelOf(failedModelRef: string | null): {
  name: string
  selection: ModelSelection
} | undefined {
  const defaultRef = modelCatalog.value.defaultModelSelection.kind === 'MODEL'
    ? modelCatalog.value.defaultModelSelection.modelRef
    : null
  const candidate =
    (defaultRef !== null && defaultRef !== failedModelRef
      ? modelCatalog.value.selectableModels.find((model) => model.modelRef === defaultRef)
      : undefined)
    ?? modelCatalog.value.selectableModels.find((model) => model.modelRef !== failedModelRef)
  if (candidate === undefined) return undefined
  return {
    name: candidate.displayName,
    selection: {
      kind: 'MODEL',
      modelRef: candidate.modelRef,
      selectionVersion: candidate.selectionVersion,
    },
  }
}

/** 回答模型标识（§2.5）：只消费该轮 modelExecution 投影。 */
function modelTagOf(turn: PublicAgentTurn): string | null {
  if (turn.modelExecution === undefined) return null
  return modelTagOfExecution(turn.modelExecution, modelCatalog.value)
}

/** 模型不可用终局的双动作上下文（§2.6）：按消息 turn 判定，仅最新失败上下文提供。 */
function modelRecoveryOf(
  turn: PublicAgentTurn,
): {
  failedModelName: string
  sameModelRetryable: boolean
  otherModelName?: string
} | undefined {
  if (!isModelUnavailableTerminal(turn)) return undefined
  const context = modelFailureContexts.value.get(turn.requestId)
  if (context === undefined) return undefined
  const failedRef = context.failedModelRef
    ?? (context.submission.modelSelection.kind === 'MODEL'
      ? context.submission.modelSelection.modelRef
      : null)
  const failedName = failedRef === null
    ? displayNameOfSelection(modelCatalog.value, context.submission.modelSelection)
    : modelCatalog.value.selectableModels.find(
        (model) => model.modelRef === failedRef,
      )?.displayName ?? failedRef
  const other = otherSelectableModelOf(failedRef)
  return {
    failedModelName: failedName ?? '所选模型',
    // 同模型重问的前提：该 modelRef 仍在当前目录（可取最新 selectionVersion）。
    sameModelRetryable: failedRef !== null
      && modelCatalog.value.selectableModels.some((model) => model.modelRef === failedRef),
    ...(other === undefined ? {} : { otherModelName: other.name }),
  }
}

/** 切换会话模型偏好（§2.4）：写会话内存 + 插入可见通知；不产生 Turn。 */
function handleModelSelected(selection: ModelSelection): void {
  const session = activeSession.value
  if (session === null || activePending.value !== null) return
  sessions.setSessionModelSelection(session.id, selection)
  const nextName = displayNameOfSelection(modelCatalog.value, selection)
  if (nextName === null) return
  sessions.appendSessionNotice(session.id, {
    kind: 'MODEL_SWITCHED',
    title: `已切换至 ${nextName} · 下一轮回答将由它生成`,
    detail: '选择仅在本页会话内记忆，刷新后使用目录默认',
  })
}

/** 双动作一（§2.6/D-MS-7）：用同一模型重新提问——新 requestId、新快照，
 * 携带当前目录中该 modelRef 的最新 selectionVersion（stale 后版本前进）；
 * 绝不复用旧快照（settled 终局的同 requestId 只会回放原失败）。 */
function retryModelTurn(requestId: string): void {
  const context = modelFailureContexts.value.get(requestId)
  if (context === undefined || tabPendingFull.value) return
  const sessionId = context.sessionId
  const failedRef = context.failedModelRef
    ?? (context.submission.modelSelection.kind === 'MODEL'
      ? context.submission.modelSelection.modelRef
      : null)
  if (failedRef === null) return
  const entry = modelCatalog.value.selectableModels.find(
    (model) => model.modelRef === failedRef,
  )
  if (entry === undefined) return
  const session = sessions.sessions.value.find((item) => item.id === sessionId)
  if (session === undefined || pendingTurns.value.has(sessionId)) return
  const selection: ModelSelection = {
    kind: 'MODEL',
    modelRef: entry.modelRef,
    selectionVersion: entry.selectionVersion,
  }
  const newId = newRequestId()
  sessions.appendSessionNotice(sessionId, {
    kind: 'MODEL_RETRY',
    title: `已用 ${entry.displayName} 重新发起这次提问`,
    detail: `新请求标识 ${newId.slice(0, 8)} · 不复用原请求的任何结果`,
  })
  failures.value = mapDelete(failures.value, sessionId)
  const window = conversationWindowOf(session)
  const messageId = sessions.appendMessage(sessionId, {
    role: 'USER',
    content: userContentOfCommand(context.submission.command),
  })
  void runTurn(sessionId, newId, context.submission.command, {
    modelSelection: selection,
    conversationWindow: window,
    ...(messageId === null ? {} : { userMessageId: messageId }),
  })
}

/**
 * 双动作二（§2.6）：换模型重新提问——新 requestId、全新快照、携带新选择；
 * 绝不改写旧快照。先插入换模型通知（副行携带新请求标识前缀），再进入 pending。
 */
function reaskWithModel(requestId: string): void {
  const context = modelFailureContexts.value.get(requestId)
  if (context === undefined || tabPendingFull.value) return
  const sessionId = context.sessionId
  const submission = context.submission
  const other = otherSelectableModelOf(context.failedModelRef)
  if (other === undefined) return
  const session = sessions.sessions.value.find((item) => item.id === sessionId)
  if (session === undefined || pendingTurns.value.has(sessionId)) return
  sessions.setSessionModelSelection(sessionId, other.selection)
  const newId = newRequestId()
  sessions.appendSessionNotice(sessionId, {
    kind: 'MODEL_REASK',
    title: `已切换至 ${other.name} · 下一轮回答将由它生成`,
    detail: `新请求标识 ${newId.slice(0, 8)} · 不复用原请求的任何结果`,
  })
  failures.value = mapDelete(failures.value, sessionId)
  // conversationWindow 只携带本轮之前的会话历史；本轮输入在 command 内。
  const window = conversationWindowOf(session)
  const messageId = sessions.appendMessage(sessionId, {
    role: 'USER',
    content: userContentOfCommand(submission.command),
  })
  void runTurn(sessionId, newId, submission.command, {
    modelSelection: other.selection,
    conversationWindow: window,
    ...(messageId === null ? {} : { userMessageId: messageId }),
  })
}

/** 换模型重问的 USER 落账文本：PRESET 用预设公开文本，其余沿用展示短语。 */
function userContentOfCommand(command: AgentTurnCommand): string {
  if (command.kind === 'ASK') {
    const input = command.input
    if (input.kind === 'PRESET') {
      return props.portfolio.questionPresets.find(
        (preset) => preset.id === input.presetId,
      )?.text ?? displayQuestionOf(command)
    }
    return input.text
  }
  return displayQuestionOf(command)
}

/** pending 指示与 USER 落账使用的展示问题文本：按命令类型给出可读的中文短语。 */
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

/** 等 DOM 更新后聚焦输入框（新轮次结束、关闭抽屉等场景保持键盘连续性）。 */
async function focusComposer(): Promise<void> {
  await nextTick()
  composerInput.value?.focus()
}

/** 取活跃会话；不存在时按初始角色/项目新建一个。 */
function ensureSession(): AgentSession {
  const current = sessions.activeSession.value
  if (current !== null) return current
  return sessions.createSession({
    role: props.initialRole,
    projectSlug: props.initialProject || null,
  })
}

/**
 * 自由文本提交：有活跃讨论时构造 CONTINUE/ROUTE_IN_CONTEXT 续谈命令，
 * 否则构造 ASK/FREE_TEXT，并附最近推荐的 ContextHandle 作参考。
 * USER 消息先落账再请求；window 快照取自落账前的会话（本轮输入在
 * command 内，不重复进窗口）。
 */
function submitFreeText(rawText: string): void {
  const text = rawText.trim()
  if (
    !freeTextRoutingAvailable.value
    || discussionPaused.value
    || modelSelectionRequired.value
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

/** 已发布预设提交：走 ASK/PRESET 命令，携带 presetId 与合同版本。 */
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

/**
 * 建议动作统一入口：按 action.continuation 构造 CONTINUE 命令
 * （ENTER_RESULT/REENTER_SUBJECT/EXIT_CONTEXT 原样透传，ROUTE_IN_CONTEXT
 * 附加用户可见文本）；无 continuation 时降级为 ASK/FREE_TEXT。
 */
function handleSelectAction(action: SuggestedAction): void {
  if (activePending.value !== null || tabPendingFull.value) return
  if (action.continuation === undefined
    && (!freeTextRoutingAvailable.value || modelSelectionRequired.value)) return
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

/**
 * 澄清提交入口：把表单载荷转为 RESOLVE_CLARIFICATION 命令。合同冻结为
 * 单一 answer（CHOICE|TEXT），多字段表单在本地直接拦截并提示。提交后
 * 乐观地把原挑战卡转只读；失败/取消时由 runTurn/cancelTurn 回滚。
 */
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
  // 澄清答案记为 USER 轮次以保持窗口交替（A2-03）；原挑战卡立即乐观转只读（A2-18）。
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

/** 取消当前轮次：先结束本地等待并回滚乐观状态（failed 标记、澄清只读），再 best-effort DELETE + abort。 */
function cancelTurn(): void {
  const current = activePending.value
  if (current === null) return
  // 先结束本地等待，再 best-effort DELETE + abort；DELETE 结果不伪造本地状态。
  pendingTurns.value = deletePendingGeneration(
    pendingTurns.value, current.sessionId, current.requestId,
  )
  if (current.userMessageId !== undefined) {
    sessions.markMessageDelivery(current.sessionId, current.userMessageId, true)
  }
  // 取消的澄清提交未被服务端消费，乐观 consumed 必须回滚，卡片恢复可提交（A2-70）。
  if (current.clarificationId !== undefined) {
    sessions.markClarificationConsumed(
      current.sessionId, current.clarificationId, false,
    )
  }
  const token = sessions.getSessionResumeToken(current.sessionId)
  void cancelAgentTurn(current.requestId, token)
  current.controller.abort()
}

/** 幂等重试：复用失败时的原 requestId 与提交快照重放（D-30），服务端按幂等语义去重。 */
function retryFailure(): void {
  const current = activeFailure.value
  if (
    current === null
    || current.submission === undefined
    || tabPendingFull.value
  ) {
    return
  }
  const sessionId = activeSession.value?.id
  if (sessionId === undefined) return
  failures.value = mapDelete(failures.value, sessionId)
  void runTurn(
    sessionId,
    current.submission.requestId,
    current.submission.command,
    { submission: current.submission },
  )
}

/** 删除本地会话：中止其 pending、best-effort 清理服务端会话（不留孤儿状态到 TTL，A2-75），再移除本地记录；删空则自动新建一个会话。 */
function removeSession(sessionId: string): void {
  const pending = pendingTurns.value.get(sessionId)
  if (pending !== undefined) {
    pendingTurns.value = mapDelete(pendingTurns.value, sessionId)
    void cancelAgentTurn(pending.requestId, sessions.getSessionResumeToken(sessionId))
    pending.controller.abort()
  }
  failures.value = mapDelete(failures.value, sessionId)
  const token = sessions.getSessionResumeToken(sessionId)
  if (token !== undefined) {
    void clearConversation(token)
  }
  if (sessionId === sessions.activeSessionId.value && token !== undefined) {
    resume.clearActiveToken()
  }
  sessions.removeSession(sessionId)
  if (sessions.sessions.value.length === 0) {
    createSession()
  }
}

/** 新建本地会话并清空活跃 ResumeToken 槽位（新会话尚无服务端身份）。 */
function createSession(): void {
  sessions.createSession({
    role: props.initialRole,
    projectSlug: props.initialProject || null,
  })
  resume.clearActiveToken()
}

/**
 * 切换会话视角（行为基础 Task 4）：不同角色 = 创建并激活一个只继承当前公开
 * Project/Case 上下文的新会话；同角色或无活跃会话为 no-op。成功创建后清空
 * 活跃 ResumeToken 槽位；不取消旧 pending、绝不原位改写当前会话的 role。
 */
function switchAudienceRole(targetRole: AudienceRole): boolean {
  const current = sessions.activeSession.value
  if (current === null) return false
  const projectSlug = activeCase.value?.projectSlug
    ?? current.projectSlug
    ?? (props.initialProject || null)
  const created = sessions.switchAudienceRole(targetRole, projectSlug)
  if (created === null) return false
  resume.clearActiveToken()
  return sessions.activeSessionId.value === created.id
}

/**
 * 清空全部本地会话：先中止所有 pending，再逐会话清理服务端会话；
 * 任一清理失败则保留本地会话并提示稍后重试，避免产生服务端孤儿。
 */
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

// ── 会话视角行与切换浮层（audience-role UI 设计 §2/§3/§9）──
// 触发钮没有任何禁用态：角色是会话身份而非本轮设置，pending/失败/澄清中
// 都允许切换（上级设计 §7.1）。切换只经 switchAudienceRole 接缝，绝不原位
// 改写 session.role，也不取消旧 pending（D-AR-9）。
const roleMenuOpen = ref(false)
const roleMenuError = ref('')
const roleSwitchStatus = ref('')
const roleSwitchTrigger = ref<HTMLButtonElement | null>(null)

const activeRolePresentation = computed(() =>
  activeSession.value === null ? null : presentationOf(activeSession.value.role),
)
/** 浮层动作项 = 除当前角色外的三个角色（当前角色渲染为非动作行，D-AR-3）。 */
const roleMenuOptions = computed(() =>
  audienceRolePresentations.filter((item) => item.role !== activeSession.value?.role),
)
/** 提示行只读既有状态（草稿/pending），不新增状态通道（UI 设计 §3.2）。 */
const roleMenuHints = computed(() => {
  const hints = ['切换视角会开启新会话；当前会话自动保留在列表。同视角重新开始请用「新对话」。']
  const session = activeSession.value
  if (session !== null && (session.draft?.trim().length ?? 0) > 0) {
    hints.push('当前会话有未发送草稿，草稿将保留在原会话。')
  }
  if (activePending.value !== null) {
    hints.push('当前会话的回答仍在生成，结果只写回原会话。')
  }
  return hints
})

function toggleRoleMenu(): void {
  roleMenuOpen.value = !roleMenuOpen.value
  if (roleMenuOpen.value) {
    roleMenuError.value = ''
    // 与 ModelSelector 相同的打开即聚焦手感（D-MS-1）：当前行不聚焦。
    queueMicrotask(() => {
      document.querySelector<HTMLElement>('[data-testid="role-option"]')?.focus()
    })
  }
}

function closeRoleMenu(returnFocus = true): void {
  roleMenuOpen.value = false
  if (returnFocus) {
    roleSwitchTrigger.value?.focus()
  }
}

/** 方向键在动作按钮间循环、Enter/Space 确认、Esc 关闭还焦（UI 设计 §3.4）。 */
function onRoleOptionKeydown(event: KeyboardEvent, role: AudienceRole): void {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const options = [...document.querySelectorAll<HTMLElement>('[data-testid="role-option"]')]
    if (options.length === 0) return
    const index = options.findIndex((element) => element.dataset.role === role)
    if (index < 0) return
    const next = event.key === 'ArrowDown'
      ? (index + 1) % options.length
      : (index - 1 + options.length) % options.length
    options[next]?.focus()
    return
  }
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    handleRoleSwitch(role)
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    closeRoleMenu()
  }
}

function onRolePopoverKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeRoleMenu()
  }
}

/** Tab/Shift+Tab 焦点离开触发钮+浮层容器即关闭（UI 设计 §3.4，非模态无陷阱）。 */
function onRoleSwitchFocusout(event: FocusEvent): void {
  if (!roleMenuOpen.value) return
  const next = event.relatedTarget as Node | null
  if (
    next !== null
    && roleSwitchTrigger.value
      ?.closest('.workspace-composer__role-switch')
      ?.contains(next) === true
  ) {
    return
  }
  roleMenuOpen.value = false
}

/** document 点击外关闭：点击死区时还焦触发钮；点击其他可交互元素让焦点自然落位。 */
function onDocumentClickCloseRoleMenu(event: MouseEvent): void {
  if (!roleMenuOpen.value) return
  const target = event.target as HTMLElement | null
  if (target === null) return
  if (
    roleSwitchTrigger.value
      ?.closest('.workspace-composer__role-switch')
      ?.contains(target) === true
  ) {
    return
  }
  const interactive = target.closest('button, a, input, textarea, select, [tabindex]')
  closeRoleMenu(interactive === null)
}

/**
 * UI 设计 §9.1：一次用户动作恰好一次接缝调用；createSession 异常按失败处理
 * （上级设计 §15）。失败时浮层保持打开并提示，成功时宣布并聚焦输入框。
 */
function handleRoleSwitch(target: AudienceRole): void {
  let ok = false
  try {
    ok = switchAudienceRole(target)
  } catch {
    ok = false
  }
  if (!ok) {
    roleMenuError.value = '未能开启新会话，请稍后重试。'
    return
  }
  roleMenuOpen.value = false
  roleSwitchStatus.value = `已切换到${presentationOf(target).label}视角，开始新会话`
  void focusComposer()
}

// 两个抽屉互斥：打开一个立即收起另一个，避免窄屏双层遮挡。
function toggleSessions(): void {
  sessionDrawerOpen.value = !sessionDrawerOpen.value
  if (sessionDrawerOpen.value) evidenceDrawerOpen.value = false
}

function toggleEvidence(): void {
  evidenceDrawerOpen.value = !evidenceDrawerOpen.value
  if (evidenceDrawerOpen.value) sessionDrawerOpen.value = false
}

/** 关闭全部抽屉；returnFocus 时把焦点还给输入框，保持键盘操作连续。 */
function closeDrawers(returnFocus = false): void {
  sessionDrawerOpen.value = false
  evidenceDrawerOpen.value = false
  if (returnFocus) composerInput.value?.focus()
}

// 分隔条事件到分栏 composable 的桥接：preview 拖拽实时预览、adjust 键盘微调。
function previewSplit(pane: 'sessions' | 'evidence', width: number): void {
  split.set(pane, width)
}

function adjustSplit(pane: 'sessions' | 'evidence', delta: number): void {
  split.adjust(pane, delta)
}

// 活跃会话的 ResumeToken 变化即时同步唯一 sessionStorage 槽位：
// 有 Token 即写入，无 Token 即清空（handoff §3）。
watchEffect(() => {
  const token = activeSession.value?.resumeToken
  if (activeSession.value === null) return
  if (token !== undefined) {
    resume.setActiveToken(token)
  } else {
    resume.clearActiveToken()
  }
})

/**
 * 挂载初始化：启动讨论时钟与工作台宽度观测；处理首页交接种子
 * （AgentRouteSeed：语义种子、会话凭证、幂等重放）；无种子会话时尝试用
 * sessionStorage 槽位里的 ResumeToken 恢复会话身份（历史消息按隐私契约
 * 不在浏览器保留，只恢复会话与讨论状态）；最后确保存在活跃会话并按
 * 种子/初始参数预填问题草稿或精确重放首页轮次。
 */
onMounted(async () => {
  document.addEventListener('click', onDocumentClickCloseRoleMenu)
  discussionClockTimer = setInterval(() => {
    discussionClock.value = Date.now()
  }, 30_000)
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
            discussionRevision: current.discussionRevision,
            ...(current.activeDiscussion === undefined
            ? {} : { activeDiscussion: current.activeDiscussion }),
        })
        resumeNotice.value = {
          text: '已恢复当前会话；历史消息按隐私约定不在浏览器中保留。',
          sessionId: session.id,
        }
      } else if (current.invalid) {
        resume.clearActiveToken()
      } else if (current.reason === 'CONTRACT_INVALID') {
        resume.clearActiveToken()
        const session = ensureSession()
        resumeNotice.value = {
          text: '会话状态结构异常，已停止恢复并开始新的本地会话。',
          sessionId: session.id,
        }
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

/** 卸载清理：置 disposed 阻断异步回调、停时钟、abort 全部 pending、断开宽度观测。 */
onBeforeUnmount(() => {
  disposed = true
  document.removeEventListener('click', onDocumentClickCloseRoleMenu)
  if (discussionClockTimer !== null) clearInterval(discussionClockTimer)
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
    <!-- 三栏工作台：会话栏 | 对话区 | 来源栏；两个 PaneResizer 分别调节左右栏宽，
         窄屏时侧栏降级为覆盖式抽屉（见样式区断点） -->
    <LocalSessionRail
      :sessions="sessions.historySessions.value"
      :active-id="sessions.activeSessionId.value"
      :pending-ids="pendingSessionIds"
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
      <!-- 窄屏工具条：侧栏为抽屉形态时提供开合入口，两抽屉互斥 -->
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
      <!-- 会话级通知：恢复结果（status）与清理结果（alert）只在自己的会话中显示 -->
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
        :notices="activeSession.notices"
        :model-tag-of="modelTagOf"
        :model-recovery-of="modelRecoveryOf"
        @cancel="cancelTurn"
        @select-action="handleSelectAction"
        @submit-clarification="handleClarification"
        @ask="handleFallbackAsk"
        @retry-same-model="retryModelTurn"
        @switch-model-reask="reaskWithModel"
      />
      <!-- 最近一次失败投影：可重试失败提供"重试"（幂等复用原 requestId） -->
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
      <!-- 输入区：会话视角行 -> 标签页 pending 上限提示 -> 活跃讨论摘要 -> 建议 chip -> 输入表单 -> 隐私提示 -->
      <div class="workspace-composer">
        <!-- 会话视角身份行（布局 A，UI 设计 §2）：eyebrow + serif 角色名 + 截断描述 + 切换触发钮 -->
        <div class="workspace-composer__role-row">
          <p class="workspace-composer__role-eyebrow">AUDIENCE·会话视角</p>
          <span class="workspace-composer__role-name">{{ activeRolePresentation?.label ?? '' }}</span>
          <span class="workspace-composer__role-desc">{{ activeRolePresentation?.description ?? '' }}</span>
          <div class="workspace-composer__role-switch" @focusout="onRoleSwitchFocusout">
            <button
              ref="roleSwitchTrigger"
              type="button"
              class="workspace-composer__role-trigger"
              data-testid="role-switch-trigger"
              aria-haspopup="dialog"
              :aria-expanded="roleMenuOpen ? 'true' : 'false'"
              aria-controls="role-switch-popover"
              @click="toggleRoleMenu"
            >切换视角 <span class="workspace-composer__role-caret" aria-hidden="true">▾</span></button>
            <div
              v-if="roleMenuOpen"
              id="role-switch-popover"
              class="workspace-composer__role-popover"
              role="dialog"
              aria-label="切换会话视角"
              data-testid="role-switch-popover"
              @keydown="onRolePopoverKeydown"
            >
              <p class="workspace-composer__role-hints" role="note" data-testid="role-menu-hints">
                <span
                  v-for="hint in roleMenuHints"
                  :key="hint"
                  class="workspace-composer__role-hint"
                >{{ hint }}</span>
              </p>
              <div
                v-if="activeRolePresentation !== null"
                class="workspace-composer__role-current"
                aria-current="true"
                data-testid="role-current"
              >
                <span class="workspace-composer__role-badge">当前</span>
                <span class="workspace-composer__role-current-text">
                  <b>{{ activeRolePresentation.label }}</b>
                  <small>{{ activeRolePresentation.description }}</small>
                </span>
              </div>
              <button
                v-for="option in roleMenuOptions"
                :key="option.role"
                type="button"
                class="workspace-composer__role-option"
                data-testid="role-option"
                :data-role="option.role"
                :aria-label="`以${option.label}视角开启新会话`"
                @click="handleRoleSwitch(option.role)"
                @keydown="onRoleOptionKeydown($event, option.role)"
              >
                <span class="workspace-composer__role-option-text">
                  <b>{{ option.label }}</b>
                  <small>{{ option.description }}</small>
                </span>
                <span class="workspace-composer__role-new-tag" aria-hidden="true">新会话 ›</span>
              </button>
              <p
                v-if="roleMenuError !== ''"
                class="workspace-composer__role-error"
                role="alert"
                data-testid="role-switch-error"
              >{{ roleMenuError }}</p>
            </div>
          </div>
        </div>
        <p class="workspace-composer__sr-status" role="status" data-testid="role-switch-status">{{ roleSwitchStatus }}</p>
        <p
          v-if="tabPendingFull"
          class="workspace-composer__tab-limit"
          role="status"
          data-testid="tab-pending-notice"
        >已有两个请求正在处理；可先浏览其他会话，稍后再提问。</p>
        <!-- 活跃讨论（typed discussion）摘要卡：主题、状态、剩余时长与后端下定的退出/重进/换题动作 -->
        <div
          v-if="activeDiscussion !== undefined"
          class="workspace-composer__discussion"
          :data-discussion-status="activeDiscussion.status"
          data-testid="active-discussion"
        >
          <p>当前讨论：{{ activeDiscussion.subject.label }}</p>
          <p>{{ activeDiscussion.status === 'ACTIVE' ? '讨论进行中' : '讨论已过期' }}</p>
          <p data-testid="discussion-expiry">{{ discussionExpiryLabel }}</p>
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
        <div class="workspace-composer__top">
          <ModelSelector
            :catalog="modelCatalog"
            :selection="activeModelSelection ?? { kind: 'NONE' }"
            :locked="activePending !== null"
            @select="handleModelSelected"
          />
          <div
            v-if="suggestionChips.length > 0"
            class="workspace-composer__suggestions workspace-composer__suggestions--inline"
          >
          <button
            v-for="chip in suggestionChips"
            :key="chip.presetId ?? chip.text"
            class="workspace-composer__suggestion"
            type="button"
            :disabled="
              activePending !== null
              || tabPendingFull
              || (chip.presetId === undefined
                && (!freeTextRoutingAvailable || discussionPaused || modelSelectionRequired))
            "
            @click="chip.presetId === undefined ? submitFreeText(chip.text) : submitPreset(chip.presetId)"
          >{{ chip.text }}</button>
          </div>
        </div>
        <p
          v-if="modelSelectionRequired"
          class="workspace-composer__model-required"
          data-testid="model-selection-required"
          role="status"
        >目录默认模型暂未就绪：请先在上方选择一个模型，再提交自由文本问题。</p>
        <form class="workspace-composer__form" @submit.prevent="submitFreeText(questionDraft)">
          <textarea
            ref="composerInput"
            v-model="questionDraft"
            class="workspace-composer__input"
            data-testid="question-input"
            rows="2"
            :maxlength="FREE_TEXT_MAX_LENGTH"
            :disabled="
              activePending !== null
              || !freeTextRoutingAvailable
              || discussionPaused
              || modelSelectionRequired
            "
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
              || discussionPaused
              || modelSelectionRequired
              || questionDraft.trim().length === 0
            "
          >发送</button>
        </form>
        <p
          v-if="discussionPaused"
          class="workspace-composer__routing-disabled"
          data-testid="discussion-state-paused"
          role="alert"
        >讨论状态结构异常，已暂停自由文本续谈。你仍可结束当前讨论。</p>
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

    <!-- 抽屉打开时的遮罩：点击关闭并让焦点回到输入框 -->
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
  position: relative;
  border-top: 1px solid var(--workspace-rule);
  padding: 10px clamp(14px, 2.4vw, 26px) 8px;
  background: var(--workspace-thread-bg);
}
.workspace-composer__role-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  margin: 0 0 10px;
  padding: 4px 2px 10px;
  border-bottom: 1px solid var(--workspace-rule);
}
.workspace-composer__role-eyebrow {
  flex: none;
  margin: 0;
  color: var(--workspace-text-faint);
  font: 10px/1.6 var(--mono);
  letter-spacing: 0.14em;
}
.workspace-composer__role-name {
  flex: none;
  color: var(--workspace-text);
  font: 600 15px/1.3 var(--serif);
  white-space: nowrap;
}
.workspace-composer__role-desc {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--workspace-text-secondary);
  font: 10.5px/1.5 var(--mono);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workspace-composer__role-switch {
  position: relative;
  flex: none;
}
.workspace-composer__role-trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  padding: 6px 14px;
  border: 1px solid var(--workspace-rule);
  border-radius: 999px;
  background: var(--paper-hi);
  color: var(--workspace-text);
  font: 11px/1.4 var(--mono);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: border-color 160ms ease;
}
.workspace-composer__role-trigger:hover {
  border-color: var(--workspace-accent);
}
.workspace-composer__role-trigger:focus-visible {
  outline: 2px solid var(--workspace-accent);
  outline-offset: 2px;
}
.workspace-composer__role-caret {
  color: var(--workspace-text-faint);
}
.workspace-composer__role-popover {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  z-index: 30;
  width: min(420px, calc(100vw - 48px));
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--workspace-rule);
  border-radius: var(--agent-radius-md, 12px);
  background: var(--paper-hi);
  box-shadow: 0 18px 44px rgba(32, 28, 23, 0.28);
}
.workspace-composer__role-hints {
  margin: 0 0 4px;
  padding: 6px 10px 8px;
  border-bottom: 1px dashed var(--workspace-rule);
}
.workspace-composer__role-hint {
  display: block;
  color: var(--workspace-text-secondary);
  font: 10px/1.7 var(--mono);
}
.workspace-composer__role-current {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 11px;
}
.workspace-composer__role-badge {
  flex: none;
  padding: 1px 7px;
  border: 1px solid color-mix(in srgb, var(--workspace-accent) 40%, var(--workspace-rule));
  border-radius: 999px;
  color: var(--workspace-accent);
  font: 9px/1.5 var(--mono);
  letter-spacing: 0.1em;
}
.workspace-composer__role-current-text {
  min-width: 0;
}
.workspace-composer__role-current-text b {
  color: var(--workspace-text);
  font: 600 13px/1.4 var(--sans);
}
.workspace-composer__role-current-text small {
  display: block;
  margin-top: 2px;
  color: var(--workspace-text-faint);
  font: 10px/1.5 var(--mono);
}
.workspace-composer__role-option {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 8px 11px;
  border: 0;
  border-radius: var(--agent-radius-sm, 8px);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.workspace-composer__role-option:hover,
.workspace-composer__role-option:focus-visible {
  background: color-mix(in srgb, var(--workspace-accent) 6%, var(--paper-hi));
  outline: none;
}
.workspace-composer__role-option-text {
  flex: 1;
  min-width: 0;
}
.workspace-composer__role-option-text b {
  color: var(--workspace-text);
  font: 600 13px/1.4 var(--sans);
}
.workspace-composer__role-option-text small {
  display: block;
  margin-top: 2px;
  color: var(--workspace-text-faint);
  font: 10px/1.5 var(--mono);
}
.workspace-composer__role-new-tag {
  flex: none;
  color: var(--workspace-text-faint);
  font: 9px/1.5 var(--mono);
  letter-spacing: 0.1em;
}
.workspace-composer__role-error {
  margin: 4px 10px 6px;
  color: var(--red, var(--workspace-accent));
  font: 10.5px/1.6 var(--mono);
}
.workspace-composer__sr-status {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  border: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
.workspace-composer__top {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.workspace-composer__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.workspace-composer__suggestions--inline {
  margin-bottom: 0;
}
.workspace-composer__model-required {
  margin: 0 0 8px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10.5px/1.6 var(--mono);
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

/* 窄屏（<720px）角色行折叠为两行：首行 eyebrow + 触发钮，次行角色名 + 截断描述（UI 设计 §7）。 */
@media (max-width: 719.98px) {
  .workspace-composer__role-row {
    flex-wrap: wrap;
    row-gap: 6px;
  }
  .workspace-composer__role-eyebrow { order: 1; margin-right: auto; }
  .workspace-composer__role-switch { order: 2; margin-left: auto; }
  .workspace-composer__role-name { order: 3; }
  .workspace-composer__role-desc { order: 4; flex-basis: 100%; }
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
