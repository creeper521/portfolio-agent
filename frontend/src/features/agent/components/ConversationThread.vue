<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { AudienceRole, PublicProject } from '../../public-content/model/publicContentTypes'
import type { AgentSession } from '../model/sessionTypes'
import type {
  AnswerSectionType,
  ConversationContextSummary,
  ConversationSuggestedQuestion,
  FollowUpAction,
  PortfolioFollowUpAction,
  PortfolioRecommendation,
  PortfolioRecommendationContextRequest,
  PublicAnswerCaveat,
  RecentTaskType,
  SemanticSourceDomain,
} from '../model/answerTypes'
import type {
  AnswerFocusTarget,
  EvidenceInspectRequest,
} from '../model/evidenceDeskModel'
import {
  answerScopeTag,
  answerGenerationTag,
  answerSourceTag,
  answerStatusLabel,
  answerTechTail,
  answerVerificationTag,
  degradationKindLabel,
  sourceDomainLabel,
  supportKindLabel,
} from '../model/answerLabels'
import type { ErrorAction } from '../../portfolio/api/apiErrorActions'
import { resolveActiveSemanticAction } from '../model/activeSemanticAction'
import type {
  ClarificationSubmission,
  ClarificationView,
  CompletedTaskView,
  OpaquePlanConfirmation,
  PlanAdjustmentBarState,
  RecommendationItemView,
} from '../model/semanticTurnView'
import { hasExecutionAnswerConflict } from '../model/semanticTurnView'
import CompactTaskSummary from './CompactTaskSummary.vue'
import AnswerCompositionPanel from './AnswerCompositionPanel.vue'
import ContextInvalidatedNotice from './ContextInvalidatedNotice.vue'
import ExecutionSnapshot from './ExecutionSnapshot.vue'
import PlanConfirmation from './PlanConfirmation.vue'
import PlanInvalidatedNotice from './PlanInvalidatedNotice.vue'
import SourceReferenceList from './SourceReferenceList.vue'
import TurnClarification from './TurnClarification.vue'

interface AnswerFailureView {
  message: string
  action: ErrorAction
  requestId?: string
  retryAfterSeconds?: number
}

const props = defineProps<{
  session: AgentSession
  role: AudienceRole
  project: PublicProject
  seedQuestion?: string
  caseContextTitle?: string
  suggestedQuestions?: ReadonlyArray<string>
  sessionsOpen?: boolean
  evidenceOpen?: boolean
  pending: boolean
  failure?: AnswerFailureView | null
  failureSuggestions?: ReadonlyArray<ConversationSuggestedQuestion>
  focusTarget?: AnswerFocusTarget | null
  adjustment?: PlanAdjustmentBarState | null
  dismissedPlanChanges?: ReadonlySet<string>
  // P3：刷新恢复的安全 Context Summary（handoff §11/§17.15）。
  recoverySummary?: ConversationContextSummary | null
  // P3：非阻断续接提示（PERSISTENCE_UNAVAILABLE 等，独立维度，handoff §5/§13.1）。
  continuationNotice?: string | null
  // P3：幂等完成回执（handoff §4）。
  completionReceipt?: {
    turnId: string
    completedTasks: Array<{ displayIndex: string; status: string; contextHandle?: string }>
  } | null
  // P3：sessionStorage 不可用 → 无法刷新恢复（非阻断，handoff §10.1）。
  resumeUnavailable?: boolean
  // P3：清除流程中间态（DELETE 未确认，handoff §12）。
  clearPending?: boolean
}>()

const emit = defineEmits<{
  submit: [question: string]
  submitSuggestion: [suggestion: ConversationSuggestedQuestion]
  inspectEvidence: [request: EvidenceInspectRequest]
  toggleSessions: []
  toggleEvidence: []
  retry: []
  continueBasicMode: []
  navigateBack: []
  cancel: []
  followUp: [action: FollowUpAction]
  clearCaseContext: []
  refineRecommendation: [action: { question: string; recommendationContext: PortfolioRecommendationContextRequest }]
  confirmPlan: [confirmation: OpaquePlanConfirmation]
  adjustPlan: []
  adjustSubmit: [instruction: string]
  adjustExit: []
  cancelPlan: []
  clarificationSubmit: [payload: {
    turnId: string
    clarification: ClarificationView
    submission: ClarificationSubmission
  }]
  regeneratePlan: [turnId: string]
  dismissPlanChange: [turnId: string]
  // P3：主动清除本次对话（handoff §12）。
  clearConversation: []
  // P5：Strict Context 失效恢复（设计 §13.10/§4.4）。
  recoverContext: []
  // P3：从某条结果继续追问（ContextHandle，handoff §3.2/§6）。
  // P5：resultItemId 用于有序结果项的显式续接（设计 §12.12 / handoff §2）。
  continueFromContext: [action: {
    question: string
    contextHandle: string
    expectedContextType: 'RECENT_SEMANTIC_TASK' | 'RECOMMENDATION'
    resultItemId?: string
  }]
}>()

const question = ref(props.seedQuestion ?? '')
const input = ref<HTMLTextAreaElement | null>(null)
const scrollArea = ref<HTMLElement | null>(null)
const showJumpToLatest = ref(false)
const followLatest = ref(true)
const highlightedTarget = ref('')
let highlightTimer: ReturnType<typeof setTimeout> | null = null

// P3：恢复卡只展示服务端确定性安全字段（handoff §11/§17.15）。不得显示问题/答案/handle/version。
const RECENT_TASK_TYPE_LABELS: Record<RecentTaskType, string> = {
  FACT: '事实查询',
  COMPARE: '比较分析',
  RECOMMENDATION: '作品推荐',
  REFINE: '推荐调整',
}
function recentTaskTypeLabel(value?: RecentTaskType): string | null {
  return value ? (RECENT_TASK_TYPE_LABELS[value] ?? null) : null
}

// P3：从某条已完成结果继续追问（ContextHandle，handoff §3.2/§6）。
// Fact/Compare/Synthesis → RECENT_SEMANTIC_TASK；Recommendation/Refine → RECOMMENDATION。
function continueFromCompletedTask(message: { id: string }, task: CompletedTaskView): void {
  if (!task.contextHandle) return
  const expectedContextType: 'RECENT_SEMANTIC_TASK' | 'RECOMMENDATION' =
    task.resultPayload.kind === 'RECOMMENDATION_RESULT' ? 'RECOMMENDATION' : 'RECENT_SEMANTIC_TASK'
  emit('continueFromContext', {
    question: `继续追问：${task.goalLabel}`,
    contextHandle: task.contextHandle,
    expectedContextType,
  })
}
const state = computed(() => {
  if (props.pending) return 'generating'
  return props.session.messages.length ? 'conversation' : 'empty'
})

// 唯一未决动作（P1 收口）：同一会话任何时刻最多一张卡可交互。
// 后续 READY、新确认、新澄清、新失效或用户取消都会让旧动作立即失效；
// 历史卡仍展示，但一律降级为只读（FE-F10 / FE-F11）。
const activeSemanticAction = computed(() =>
  resolveActiveSemanticAction(props.session, (turnId) => isPlanChangeTurnDismissed(turnId)),
)

function isPlanChangeTurnDismissed(turnId: string): boolean {
  return props.dismissedPlanChanges?.has(turnId) ?? false
}

function isActiveConfirmationMessage(message: AgentSession['messages'][number]): boolean {
  const action = activeSemanticAction.value
  return action?.kind === 'CONFIRMATION' && action.turnId === message.answer?.turnId
}

function isActiveClarificationMessage(message: AgentSession['messages'][number]): boolean {
  const action = activeSemanticAction.value
  return action?.kind === 'CLARIFICATION' && action.turnId === message.answer?.turnId
}

function isActiveInvalidationMessage(message: AgentSession['messages'][number]): boolean {
  const action = activeSemanticAction.value
  return action?.kind === 'PLAN_INVALIDATION' && action.turnId === message.answer?.turnId
}

// P5：CONTEXT_INVALIDATED 恢复卡只在最新一条消息可操作，历史卡降级为只读记录（单动作不变量）。
function isActiveMessage(message: AgentSession['messages'][number]): boolean {
  return props.session.messages.at(-1)?.id === message.id
}

// 确认完成或取消后，历史计划卡降级为只读记录而不是消失。
function confirmationReadonlyNote(message: AgentSession['messages'][number]): string {
  const action = activeSemanticAction.value
  return action?.kind === 'CONFIRMATION'
    ? '此计划已被后续轮次取代，仅作记录。'
    : '该计划已关闭，仅作记录。'
}

// 澄清卡的只读文案：流程正常走完说「已完成」，被更新的动作顶掉说「已被取代」。
function clarificationReadonlyNote(message: AgentSession['messages'][number]): string {
  const index = props.session.messages.findIndex((item) => item.id === message.id)
  const superseded = props.session.messages.slice(index + 1).some((item) => {
    const semanticTurn = item.answer?.semanticTurn
    return semanticTurn?.clarification !== undefined
      || semanticTurn?.planChange !== undefined
      || semanticTurn?.disposition === 'CONFIRMATION_REQUIRED'
  })
  return superseded
    ? '此澄清已被后续轮次取代，仅作记录。'
    : '此澄清已完成，仅作记录。'
}

function isPlanChangeDismissed(message: AgentSession['messages'][number]): boolean {
  const turnId = message.answer?.turnId
  return turnId !== undefined && isPlanChangeTurnDismissed(turnId)
}

const adjustmentDraft = ref('')

function submitAdjustmentDraft() {
  const instruction = adjustmentDraft.value.trim()
  if (!instruction || props.pending || !props.adjustment) return
  emit('adjustSubmit', instruction)
  adjustmentDraft.value = ''
}

function exitAdjustment() {
  adjustmentDraft.value = ''
  emit('adjustExit')
}
const starterQuestions = computed(
  () => props.suggestedQuestions?.length
    ? props.suggestedQuestions
    : props.project.suggestedQuestions,
)

watch(
  () => props.seedQuestion,
  async (value) => {
    if (value && !props.session.messages.length) {
      question.value = value
      await nextTick()
      resizeInput()
      input.value?.focus()
    }
  },
)

watch(
  () => props.failure?.action,
  (action) => {
    if (action === 'CORRECT_INPUT') focusComposer()
  },
  { immediate: true },
)

function submit() {
  const value = question.value.trim()
  if (!value || props.pending) return
  emit('submit', value)
  question.value = ''
  nextTick(resizeInput)
}

function submitSuggested(value: string | ConversationSuggestedQuestion) {
  if (props.pending) return
  if (typeof value === 'string') {
    emit('submit', value)
  } else {
    emit('submitSuggestion', value)
  }
  question.value = ''
  nextTick(resizeInput)
}

function onComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  submit()
}

function resizeInput() {
  const element = input.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(element.scrollHeight, 110)}px`
}

function focusComposer() {
  question.value = props.session.messages.at(-1)?.role === 'USER'
    ? props.session.messages.at(-1)?.content ?? ''
    : ''
  nextTick(() => {
    resizeInput()
    input.value?.focus()
  })
}

function shortSupportReference(requestId: string): string {
  return requestId.slice(0, 8)
}

function copySupportReference(requestId: string) {
  void navigator.clipboard?.writeText(requestId)
}

function onThreadScroll() {
  const element = scrollArea.value
  if (!element) return
  const distance = element.scrollHeight - element.scrollTop - element.clientHeight
  followLatest.value = distance < 80
  showJumpToLatest.value = !followLatest.value
}

function jumpToLatest() {
  const element = scrollArea.value
  if (!element) return
  element.scrollTo?.({
    top: element.scrollHeight,
    behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      ? 'auto'
      : 'smooth',
  })
  followLatest.value = true
  showJumpToLatest.value = false
}

watch(
  () => [props.session.messages.length, props.pending],
  async () => {
    if (!followLatest.value) return
    await nextTick()
    jumpToLatest()
  },
)

watch(
  () => props.focusTarget?.requestId,
  async () => {
    const target = props.focusTarget
    if (!target) return
    await nextTick()
    const container = scrollArea.value
    const message = container?.querySelector<HTMLElement>(
      `[data-message-id="${target.messageId}"]`,
    )
    const element = target.sectionType
      ? message?.querySelector<HTMLElement>(
        `[data-section-type="${target.sectionType}"]`,
      )
      : message
    if (!container || !element) return
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    const rect = element.getBoundingClientRect()
    const containerRect = container.getBoundingClientRect()
    const top =
      container.scrollTop +
      (rect.top - containerRect.top) -
      (container.clientHeight - rect.height) / 2
    container.scrollTo?.({
      top: Math.max(0, top),
      behavior: reduced ? 'auto' : 'smooth',
    })
    element.focus({ preventScroll: true })
    highlightedTarget.value = `${target.messageId}:${target.sectionType ?? ''}`
    if (highlightTimer) clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => {
      highlightedTarget.value = ''
    }, 1600)
  },
)

onBeforeUnmount(() => {
  if (highlightTimer) clearTimeout(highlightTimer)
})

function isV2Answer(message: AgentSession['messages'][number]) {
  const answer = message.answer
  return Boolean(answer && (answer.sections?.length || answer.intent))
}

function dynamicSuggestions(message: AgentSession['messages'][number]) {
  return message.answer?.suggestedQuestions ?? []
}

// P5 stp-v2 来源域解析（设计 §2.7 规则1）：块级 sourceDomain 权威；缺失回落任务级
// sourceDomain，再回落旧 sourceScope。返回 null 表示无域信息（不渲染域标记，fail-closed）。
function sectionDomain(
  message: AgentSession['messages'][number],
  section: NonNullable<AgentSession['messages'][number]['answer']>['sections'][number],
): SemanticSourceDomain | null {
  if (section.sourceDomain) return section.sourceDomain
  if (section.key.startsWith('semantic:')) {
    const displayIndex = section.key.split(':')[1]
    const task = message.answer?.semanticTurn?.completedTasks.find((item) => item.displayIndex === displayIndex)
    if (task?.sourceDomain) return task.sourceDomain
  }
  return section.sourceScope === 'GENERAL' || section.sourceScope === 'PORTFOLIO' ? section.sourceScope : null
}

// P5 降级提示（设计 §4.4）：有 degradationSummary 时细化到 kinds，否则回落布尔 degraded。
function degradationNoticeText(answer: NonNullable<AgentSession['messages'][number]['answer']>): string {
  const summary = answer.degradationSummary
  if (summary?.degraded) {
    const kinds = summary.kinds
      .map(degradationKindLabel)
      .filter((label): label is string => label !== null)
    return kinds.length ? `已切换到基础回答方式（${kinds.join('、')}）` : '已切换到基础回答'
  }
  return answer.degraded ? '已切换到基础回答' : ''
}

// P5 结构化限定语（设计 §4.4/§9.9）：挂在所涉 Block 下方，绝不省略/反转。
function caveatsForBlock(
  answer: NonNullable<AgentSession['messages'][number]['answer']>,
  blockId: string | undefined,
): PublicAnswerCaveat[] {
  if (!blockId || !answer.caveats?.length) return []
  return answer.caveats.filter((caveat) => caveat.appliesToBlockIds.includes(blockId))
}

// 未匹配到任何已渲染 Block 的限定语在回答级统一展示（含 appliesToBlockIds 为空者）。
function generalCaveats(message: AgentSession['messages'][number]): PublicAnswerCaveat[] {
  const answer = message.answer
  if (!answer?.caveats?.length) return []
  const renderedBlockIds = new Set(
    answer.sections
      .map((section) => section.blockId)
      .filter((id): id is string => Boolean(id)),
  )
  return answer.caveats.filter((caveat) =>
    caveat.appliesToBlockIds.length === 0
    || !caveat.appliesToBlockIds.some((id) => renderedBlockIds.has(id)))
}

// P5「回答构成」信任层入口（设计 §4.2/§4.4）：有多任务/角色/支持聚合/来源组成/降级/限定语时才出现；
// 单任务且无信任细节时隐藏，避免噪声（§4.4「单任务可隐藏」）。
function hasCompositionDetail(message: AgentSession['messages'][number]): boolean {
  const answer = message.answer
  const tasks = answer?.semanticTurn?.completedTasks ?? []
  const hasTaskDetail = tasks.length > 1
    || tasks.some((task) => task.fulfillmentRole || task.supportSummary)
  return Boolean(answer?.sourceComposition)
    || Boolean(answer?.degradationSummary?.kinds.length)
    || Boolean(answer?.caveats?.length)
    || hasTaskDetail
}

function followUp(
  message: AgentSession['messages'][number],
  question: string,
  intent: PortfolioFollowUpAction,
  selectedSectionType?: AnswerSectionType,
  referencedClaimIds?: string[],
) {
  const reference = message.answer?.referenceContext
  if (!reference || props.pending) return
  emit('followUp', {
    question,
    referenceContext: {
      ...reference,
      projectSlugs: reference.projectSlugs ? [...reference.projectSlugs] : undefined,
      referencedClaimIds: [...(referencedClaimIds ?? reference.referencedClaimIds)],
      selectedSectionType,
      followUpAction: intent,
    },
  })
}

function inspectSection(
  message: AgentSession['messages'][number],
  section: NonNullable<AgentSession['messages'][number]['answer']>['sections'][number],
) {
  emit('inspectEvidence', {
    messageId: message.id,
    evidenceIds: [...section.evidenceIds],
    sectionType: section.type,
  })
}

function inspectMessageEvidence(
  message: AgentSession['messages'][number],
  evidenceId: string,
) {
  emit('inspectEvidence', {
    messageId: message.id,
    evidenceIds: [evidenceId],
  })
}

// 中文序数：用于「换掉第N个」自然语言问题（1→一，2→二，依此类推）。
const ORDINAL_CN = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

function ordinalLabel(index: number): string {
  return ORDINAL_CN[index] ?? String(index + 1)
}

// 推荐调整：只做两件事——生成自然语言问题，并在请求 context 中回传当前批次 ID。
// 仍然走现有 /api/v2/answers 链路；不在客户端计算替换结果，也不把作品 ID 写进问题。
type RecommendationRefine = 'REPLACE' | 'EXPLAIN'

function recommendationContextFor(
  recommendation: PortfolioRecommendation,
): PortfolioRecommendationContextRequest {
  return {
    ...recommendation.context,
    capabilityCodes: [...recommendation.context.capabilityCodes],
    selectedPortfolioIds: [...recommendation.context.selectedPortfolioIds],
  }
}

function semanticRecommendationTask(
  message: AgentSession['messages'][number],
): CompletedTaskView | undefined {
  return message.answer?.semanticTurn?.completedTasks.find(
    (task) => task.resultPayload.kind === 'RECOMMENDATION_RESULT',
  )
}

function recommendationContextHandle(
  message: AgentSession['messages'][number],
): string | undefined {
  const task = semanticRecommendationTask(message)
  return task?.contextHandle ?? task?.continuationContext?.contextHandle
}

function canRefineRecommendation(message: AgentSession['messages'][number]): boolean {
  if (recommendationContextHandle(message)) return true
  // 只有纯旧版回答保留 P2 兼容入口；P5 任务无 handle 时不展示无效操作。
  return message.answer?.semanticTurn === undefined
    && message.answer?.portfolioRecommendation?.context !== undefined
}

function recommendationItems(message: AgentSession['messages'][number]): RecommendationItemView[] {
  const legacy = message.answer?.portfolioRecommendation?.items
  if (legacy) return legacy
  const task = semanticRecommendationTask(message)
  return task?.resultPayload.kind === 'RECOMMENDATION_RESULT'
    ? task.resultPayload.recommendations
    : []
}

// P5 有序结果项续接（设计 §12.12 / handoff §2）：携带 contextHandle + resultItemId 显式选择某一项。
function continueFromResultItem(
  message: AgentSession['messages'][number],
  item: RecommendationItemView,
): void {
  if (props.pending) return
  const task = semanticRecommendationTask(message)
  const handle = task?.contextHandle ?? task?.continuationContext?.contextHandle
  if (!handle || !item.resultItemId) return
  emit('continueFromContext', {
    question: `继续了解：${item.title}`,
    contextHandle: handle,
    expectedContextType: 'RECOMMENDATION',
    resultItemId: item.resultItemId,
  })
}

function refineRecommendation(
  message: AgentSession['messages'][number],
  index: number,
  intent: RecommendationRefine,
) {
  const recommendation = message.answer?.portfolioRecommendation
  if (props.pending) return
  const ordinal = ordinalLabel(index)
  const question = intent === 'REPLACE' ? `换掉第${ordinal}个` : `为什么推荐第${ordinal}个？`
  const handle = recommendationContextHandle(message)
  if (handle) {
    emit('continueFromContext', {
      question,
      contextHandle: handle,
      expectedContextType: 'RECOMMENDATION',
    })
    return
  }
  if (recommendation) {
    emit('refineRecommendation', {
      question,
      recommendationContext: recommendationContextFor(recommendation),
    })
    return
  }
}

function refineWhole(
  message: AgentSession['messages'][number],
  question: string,
) {
  const recommendation = message.answer?.portfolioRecommendation
  if (props.pending) return
  const handle = recommendationContextHandle(message)
  if (handle) {
    emit('continueFromContext', {
      question,
      contextHandle: handle,
      expectedContextType: 'RECOMMENDATION',
    })
    return
  }
  if (recommendation) {
    emit('refineRecommendation', {
      question,
      recommendationContext: recommendationContextFor(recommendation),
    })
    return
  }
}
</script>

<template>
  <section class="conversation">
    <header class="conversation__head">
      <div class="conversation__title">
        <p>AGENT CONVERSATION · Agent 对话</p>
        <h1 :title="session.title" :aria-label="session.title">{{ session.title }}</h1>
        <div
          v-if="caseContextTitle"
          class="conversation__case-context"
          data-case-context
          role="status"
        >
          <span>案例上下文 · {{ caseContextTitle }}</span>
          <button
            data-clear-case-context
            type="button"
            aria-label="清除案例上下文"
            @click="$emit('clearCaseContext')"
          >清除</button>
        </div>
      </div>
      <div class="conversation__tools">
        <button
          class="session-toggle"
          type="button"
          aria-controls="local-session-rail"
          :aria-expanded="sessionsOpen ? 'true' : 'false'"
          @click="$emit('toggleSessions')"
        >
          会话
        </button>
        <button
          class="evidence-toggle"
          type="button"
          aria-controls="agent-evidence-desk"
          :aria-expanded="evidenceOpen ? 'true' : 'false'"
          @click="$emit('toggleEvidence')"
        >
          证据
        </button>
        <span>{{ role }} MODE</span>
      </div>
    </header>

    <div class="conversation__body">
      <div ref="scrollArea" class="conversation__scroll" @scroll.passive="onThreadScroll">
      <div class="thread" :data-conversation-state="state">
        <!-- P3：刷新恢复卡（handoff §11/§17.15）。只显示安全字段 + 清除入口。 -->
        <section
          v-if="recoverySummary"
          class="thread-p3-card thread-recovery"
          data-recovery-card
          role="status"
          aria-live="polite"
        >
          <p class="thread-p3-card__title">已恢复本次对话的业务上下文</p>
          <ul class="thread-recovery__fields">
            <li v-if="recentTaskTypeLabel(recoverySummary.recentTaskType)">
              最近任务 · {{ recentTaskTypeLabel(recoverySummary.recentTaskType) }}
            </li>
            <li v-if="recoverySummary.subjectLabels.length">
              主体 · {{ recoverySummary.subjectLabels.join('、') }}
            </li>
            <li v-if="recoverySummary.facetLabels.length">
              维度 · {{ recoverySummary.facetLabels.join('、') }}
            </li>
            <li v-if="recoverySummary.comparisonDimensionLabels.length">
              比较项 · {{ recoverySummary.comparisonDimensionLabels.join('、') }}
            </li>
            <li v-if="recoverySummary.preferenceLabels.length">
              偏好 · {{ recoverySummary.preferenceLabels.join('、') }}
            </li>
          </ul>
          <div class="thread-p3-card__actions">
            <button
              data-clear-conversation
              type="button"
              :disabled="clearPending"
              @click="$emit('clearConversation')"
            >{{ clearPending ? '清除中…' : '清除本次对话' }}</button>
          </div>
        </section>

        <!-- P3：幂等完成回执（handoff §4）。不伪造答案；提示可基于 Context 继续。 -->
        <section
          v-if="completionReceipt"
          class="thread-p3-card thread-completion"
          data-completion-receipt
          role="status"
          aria-live="polite"
        >
          <p class="thread-p3-card__title">这个请求此前已经完成</p>
          <p class="thread-p3-card__body">
            原回答正文不被服务端保存，因此无法重放。你可以基于已保存的业务上下文继续追问，或重新提问。
          </p>
        </section>

        <!-- P3：非阻断续接提示（handoff §5/§13.1）。不改本次回答的证据状态。 -->
        <p
          v-if="continuationNotice"
          class="thread-continuation-notice"
          data-continuation-notice
          role="status"
          aria-live="polite"
        >{{ continuationNotice }}</p>

        <section v-if="state === 'empty'" class="thread-empty">
          <p>YOU · FROM DOSSIER</p>
          <p class="thread-empty__lead">从一个可核验的问题开始——这里只回答有公开证据支撑的内容。</p>
          <div class="thread-empty__list">
            <button
              v-for="item in starterQuestions"
              :key="item"
              data-suggested-question
              type="button"
              :disabled="pending"
              @click="submitSuggested(item)"
            >
              <span>↳</span>{{ item }}
            </button>
          </div>
        </section>

        <article
          v-for="message in session.messages"
          :key="message.id"
          class="message"
          :class="message.role === 'AGENT' ? 'message--agent' : 'message--user'"
          :data-message-id="message.id"
          :data-answer-focus="highlightedTarget === `${message.id}:` ? 'true' : undefined"
          tabindex="-1"
        >
          <p v-if="message.answer" class="message__meta">
            <span class="message__meta-prefix">AGENT · {{ answerStatusLabel(message.answer) }}</span>
            <span class="message__meta-tags">
              <span v-if="answerScopeTag(message.answer)" class="message__meta-tag" :data-scope="message.answer.answerScope">{{ answerScopeTag(message.answer) }}</span>
              <span v-if="!message.answer.degraded && answerVerificationTag(message.answer)" class="message__meta-tag" :data-verification="message.answer.evidenceState">{{ answerVerificationTag(message.answer) }}</span>
              <span v-if="answerSourceTag(message.answer)" class="message__meta-tag">{{ answerSourceTag(message.answer) }}</span>
              <span v-if="answerGenerationTag(message.answer)" class="message__meta-tag" data-answer-generation>{{ answerGenerationTag(message.answer) }}</span>
            </span>
            <span v-if="answerTechTail(message.answer)" class="message__meta-tail">{{ answerTechTail(message.answer) }}</span>
          </p>
          <p v-else class="message__meta">{{ message.role === 'AGENT' ? 'AGENT' : 'YOU' }}</p>
          <p
            v-if="message.answer && degradationNoticeText(message.answer)"
            data-degraded-notice
            class="degraded-notice"
            role="status"
          >{{ degradationNoticeText(message.answer) }}</p>
          <!-- P5 部分完成（设计 §9.8/§4.4）：温和横幅，已发布事实仍充分可信 -->
          <p
            v-if="message.answer?.resolution === 'PARTIALLY_ANSWERED'"
            data-partial-banner
            class="partial-banner"
            role="status"
          >已回答部分内容，部分主题暂无可发布结果；已发布事实仍按其来源标注可信度。</p>
          <div v-if="message.answer" class="structured-answer">
            <PlanConfirmation
              v-if="message.answer.semanticTurn?.disposition === 'CONFIRMATION_REQUIRED' && message.answer.semanticTurn.displayPlan"
              :plan="message.answer.semanticTurn.displayPlan"
              :confirmation="isActiveConfirmationMessage(message) ? session.pendingConfirmation : undefined"
              :pending="pending"
              :adjusting="adjustment != null && isActiveConfirmationMessage(message)"
              :readonly="!isActiveConfirmationMessage(message)"
              :readonly-note="confirmationReadonlyNote(message)"
              :adjust-disabled="message.answer.semanticTurn.displayPlan.pendingPlanReference == null"
              @confirm="$emit('confirmPlan', $event)"
              @adjust="$emit('adjustPlan')"
              @cancel="$emit('cancelPlan')"
            />
            <TurnClarification
              v-if="message.answer.semanticTurn?.clarification"
              :clarification="message.answer.semanticTurn.clarification"
              :pending="pending && isActiveClarificationMessage(message)"
              :readonly="!isActiveClarificationMessage(message)"
              :readonly-note="clarificationReadonlyNote(message)"
              @submit="$emit('clarificationSubmit', { turnId: message.answer.turnId, ...$event })"
            />
            <template v-if="message.answer.semanticTurn?.planChange">
              <p v-if="isPlanChangeDismissed(message)" class="plan-change-dismissed">已暂不处理 · 该计划不会执行，可直接继续提问。</p>
              <PlanInvalidatedNotice
                v-else
                :plan-change="message.answer.semanticTurn.planChange"
                :pending="pending"
                :readonly="!isActiveInvalidationMessage(message)"
                @regenerate="$emit('regeneratePlan', message.answer.turnId)"
                @dismiss="$emit('dismissPlanChange', message.answer.turnId)"
              />
            </template>
            <CompactTaskSummary
              v-if="message.answer.semanticTurn?.taskSummary && message.answer.semanticTurn.taskSummary.totalCount > 1 && message.answer.semanticTurn.taskSummary.displayMode !== 'HIDDEN'"
              :summary="message.answer.semanticTurn.taskSummary"
            />
            <!-- P3：最终执行快照（FINAL，handoff §7）。只在服务端返回时渲染，无拟真实时进度。 -->
            <ExecutionSnapshot
              v-if="message.answer.semanticTurn?.execution"
              :execution="message.answer.semanticTurn.execution"
            />
            <!--
              P3 防御性展示（handoff §7/§9）：顶层宣称 ANSWERED + VERIFIED，但 FINAL 快照
              全任务全阶段 FAILED 时是后端矛盾状态。前端不伪造阶段成功，也不把答案静默
              包装为完整成功，仅以非阻断方式提示本轮执行能力降级。
            -->
            <p
              v-if="hasExecutionAnswerConflict(message.answer)"
              data-execution-conflict-notice
              class="thread-execution-conflict"
              role="status"
            >回答已返回，但本轮执行能力降级；执行快照按服务端最终状态展示，未掩饰失败阶段。</p>
            <p
              v-if="(message.answer.semanticTurn?.disposition === 'BOUNDARY' || message.answer.semanticTurn?.disposition === 'REJECTED') && !message.answer.semanticTurn.planChange && message.answer.summary"
              data-semantic-boundary-message
            >{{ message.answer.summary }}</p>
            <h3 v-if="!message.answer.semanticTurn && message.answer.title">{{ message.answer.title }}</h3>
            <p v-if="!message.answer.semanticTurn && message.answer.summary" data-answer-summary>{{ message.answer.summary }}</p>
            <p
              v-if="message.answer.contextVersionUpdated"
              data-context-version-updated
              class="context-version-updated"
              role="status"
            >公开内容已更新，本轮已按当前版本重新核对。</p>
            <!-- P5 Strict Context 失效恢复卡（设计 §13.9/§4.4）：优先于正文，进入独立恢复路径 -->
            <ContextInvalidatedNotice
              v-if="message.answer.semanticTurn?.disposition === 'CONTEXT_INVALIDATED' && message.answer.contextInvalidation"
              :invalidation="message.answer.contextInvalidation"
              :pending="pending"
              :readonly="!isActiveMessage(message)"
              @recover="$emit('recoverContext')"
            />
            <!-- P5 重验证成功轻提示（设计 §13.14）：一次性、不阻断 -->
            <p
              v-if="message.answer.contextResolution"
              data-context-resolution
              class="context-resolution-notice"
              role="status"
            >已基于最新内容重新核对你引用的上下文。</p>
            <section
              v-for="section in message.answer.sections"
              :key="section.key"
              class="answer-block"
              :data-section-type="section.type"
              :data-domain="sectionDomain(message, section) ?? undefined"
              :data-answer-focus="
                highlightedTarget === `${message.id}:${section.type}` ? 'true' : undefined
              "
              tabindex="-1"
            >
              <header
                v-if="section.title || sourceDomainLabel(sectionDomain(message, section)) || supportKindLabel(section.support?.kind)"
                class="answer-block__head"
              >
                <h4 v-if="section.title">{{ section.title }}</h4>
                <span
                  v-if="sourceDomainLabel(sectionDomain(message, section))"
                  class="source-pill"
                  :data-domain="sectionDomain(message, section)"
                  data-source-label
                >
                  <span class="source-pill__dot" aria-hidden="true"></span>{{ sourceDomainLabel(sectionDomain(message, section)) }}
                </span>
                <span
                  v-if="supportKindLabel(section.support?.kind)"
                  class="support-badge"
                  :data-support="section.support?.kind"
                  data-support-badge
                >{{ supportKindLabel(section.support?.kind) }}</span>
              </header>
              <p>{{ section.content }}</p>
              <!-- P3：公开来源引用（handoff §8）。存在时优先于旧 evidenceIds 渲染。 -->
              <SourceReferenceList
                v-if="section.sourceReferences && section.sourceReferences.length"
                :references="section.sourceReferences"
              />
              <!-- TRANSITIONAL(p3-e): 无 sourceReferences 时回落旧 evidenceId 引用按钮。 -->
              <div
                v-if="(!section.sourceReferences || !section.sourceReferences.length) && section.evidenceIds.length"
                class="answer-block__citations"
              >
                <button
                  v-for="eid in section.evidenceIds"
                  :key="eid"
                  :data-section-citation="eid"
                  type="button"
                  @click="inspectMessageEvidence(message, eid)"
                >[{{ eid }}]</button>
              </div>
              <!-- P5 结构化限定语（设计 §9.9/§4.4）：挂所涉 Block 下方，绝不省略/反转 -->
              <p
                v-for="caveat in caveatsForBlock(message.answer, section.blockId)"
                :key="`caveat-${section.key}-${caveat.code}`"
                class="answer-caveat"
                :data-caveat-code="caveat.code"
                :data-caveat-block="section.blockId"
                role="note"
              >{{ caveat.message }}</p>
              <div v-if="message.answer.referenceContext" class="follow-up-actions">
                <button
                  data-follow-up="expand-section"
                  type="button"
                  :disabled="pending"
                  @click="followUp(message, `展开${section.title || section.type}`, 'EXPAND_SECTION', section.type, section.claimIds)"
                >展开本节</button>
                <button
                  data-section-evidence
                  type="button"
                  :disabled="pending || !section.evidenceIds.length"
                  @click="inspectSection(message, section)"
                >查看本节证据</button>
                <button
                  data-follow-up="explain-decision"
                  type="button"
                  :disabled="pending"
                  @click="followUp(message, `说明${section.title || section.type}的判断`, 'EXPLAIN_DECISION', section.type, section.claimIds)"
                >说明判断</button>
              </div>
            </section>
            <!-- P5 通用限定语：未归属到任何已渲染 Block 的结构化限定语（设计 §9.9） -->
            <div v-if="generalCaveats(message).length" class="answer-caveats" data-caveats-general>
              <p
                v-for="caveat in generalCaveats(message)"
                :key="`caveat-general-${caveat.code}`"
                class="answer-caveat"
                :data-caveat-code="caveat.code"
                role="note"
              >{{ caveat.message }}</p>
            </div>
            <!-- P5「回答构成」信任层（设计 §4.2/§4.4）：默认折叠，单任务无细节时隐藏 -->
            <AnswerCompositionPanel
              v-if="hasCompositionDetail(message)"
              :source-composition="message.answer.sourceComposition"
              :completed-tasks="message.answer.semanticTurn?.completedTasks ?? []"
              :degradation-summary="message.answer.degradationSummary"
              :caveats="message.answer.caveats"
            />
            <div v-if="message.answer.referenceContext" class="follow-up-actions follow-up-actions--answer">
              <button
                data-follow-up="current-status"
                type="button"
                :disabled="pending"
                @click="followUp(message, '查看当前状态', 'CURRENT_STATUS')"
              >查看当前状态</button>
              <button
                data-follow-up="related-question"
                type="button"
                :disabled="pending"
                @click="followUp(message, '查看相关问题', 'RELATED_QUESTION')"
              >查看相关问题</button>
              <button
                v-if="(message.answer.referenceContext.projectSlugs?.length ?? 0) > 1"
                data-follow-up="compare-projects"
                type="button"
                :disabled="pending"
                @click="followUp(message, '对比这些项目', 'COMPARE_SUBJECTS')"
              >对比项目</button>
            </div>
            <!-- P3：从产生可续接 Context 的已完成结果继续追问（handoff §3.2/§6）。 -->
            <div
              v-if="message.answer.semanticTurn?.completedTasks.some((task) => task.contextHandle)"
              class="follow-up-actions follow-up-actions--context"
              data-context-continue
            >
              <button
                v-for="task in message.answer.semanticTurn?.completedTasks.filter((t) => t.contextHandle) ?? []"
                :key="`continue-${task.displayIndex}`"
                :data-continue-task="task.displayIndex"
                type="button"
                :disabled="pending"
                @click="continueFromCompletedTask(message, task)"
              >从「{{ task.goalLabel }}」继续追问</button>
            </div>
            <!-- 结构化作品推荐卡组（可选；items 顺序是后端权威顺序，前端不重排）-->
            <section
              v-if="message.answer.portfolioRecommendation || recommendationItems(message).length"
              class="portfolio-recommendation"
              data-portfolio-recommendation
              :aria-label="`作品推荐 · ${recommendationItems(message).length} 项`"
            >
                <p
                  v-if="message.answer.portfolioRecommendation?.satisfiedConstraints.length"
                  class="reco-satisfied"
                >
                  <span class="reco-satisfied__code">已满足</span>
                  <span>{{
                    message.answer.portfolioRecommendation.satisfiedConstraints.join(' · ')
                  }}</span>
                </p>
                <p
                  v-if="message.answer.portfolioRecommendation?.unsatisfiedConstraints.length"
                  class="reco-unsatisfied"
                  data-recommendation-unsatisfied
                  role="status"
                >
                  <span class="reco-unsatisfied__code">未满足</span>
                  <span>{{
                    message.answer.portfolioRecommendation.unsatisfiedConstraints.join(' · ')
                  }}</span>
                </p>
                <div
                  v-if="recommendationItems(message).length"
                  class="reco-grid"
                >
                  <div
                    v-for="(item, itemIndex) in recommendationItems(message)"
                    :key="item.portfolioId"
                    class="reco-card"
                    :data-recommendation-item="item.portfolioId"
                    :data-portfolio-id="item.portfolioId"
                  >
                    <div class="reco-card__top">
                      <span class="reco-card__no">{{ String(itemIndex + 1).padStart(2, '0') }}</span>
                      <span class="reco-card__title">{{ item.title }}</span>
                    </div>
                    <p v-if="item.matchReasons.length" class="reco-card__reason">
                      {{ item.matchReasons.join('；') }}
                    </p>
                    <SourceReferenceList
                      v-if="item.sourceReferences && item.sourceReferences.length"
                      :references="item.sourceReferences"
                    />
                    <div v-if="item.evidenceIds.length" class="reco-card__evidence">
                      <button
                        v-for="eid in item.evidenceIds"
                        :key="eid"
                        class="reco-evi"
                        :data-recommendation-evidence="eid"
                        type="button"
                        @click="inspectMessageEvidence(message, eid)"
                      >EVIDENCE · {{ eid }}</button>
                    </div>
                    <a
                      class="reco-card__link"
                      data-recommendation-link
                      :href="item.route"
                      @click.prevent
                    >查看作品 →</a>
                    <div v-if="canRefineRecommendation(message)" class="reco-card__actions">
                      <button
                        class="reco-card__action"
                        data-recommendation-refine="replace"
                        type="button"
                        :disabled="pending"
                        @click="refineRecommendation(message, itemIndex, 'REPLACE')"
                      >换掉这个</button>
                      <button
                        class="reco-card__action"
                        data-recommendation-refine="explain"
                        type="button"
                        :disabled="pending"
                        @click="refineRecommendation(message, itemIndex, 'EXPLAIN')"
                      >为什么推荐这个？</button>
                      <!-- P5 有序结果项续接（设计 §12.12）：仅当后端暴露 resultItemId 时提供 -->
                      <button
                        v-if="item.resultItemId"
                        class="reco-card__action"
                        data-recommendation-continue
                        :data-result-item="item.resultItemId"
                        type="button"
                        :disabled="pending"
                        @click="continueFromResultItem(message, item)"
                      >继续了解这一项</button>
                    </div>
                  </div>
                </div>
                <div
                  v-if="canRefineRecommendation(message)"
                  class="reco-card__actions reco-card__actions--group"
                >
                  <button
                    class="reco-card__action"
                    data-recommendation-refine="shift-backend"
                    type="button"
                    :disabled="pending"
                    @click="refineWhole(message, '再偏后端一点')"
                  >再偏后端一点</button>
                  <button
                    class="reco-card__action"
                    data-recommendation-refine="resize"
                    type="button"
                    :disabled="pending"
                    @click="refineWhole(message, '把数量改成 2 个')"
                  >把数量改成 2 个</button>
                </div>
              </section>
            <div v-if="dynamicSuggestions(message).length" class="dynamic-suggestions">
              <button
                v-for="(q, qi) in dynamicSuggestions(message)"
                :key="qi"
                data-suggested-follow-up
                type="button"
                :disabled="pending"
                :title="q.text"
                @click="submitSuggested(q)"
              >{{ q.text }}</button>
            </div>
          </div>
          <div v-else class="message__body">{{ message.content }}</div>
          <footer v-if="message.evidenceIds.length && (!message.answer || !message.answer.sections.some(s => s.evidenceIds.length))">
            <button
              v-for="id in message.evidenceIds"
              :key="id"
              :data-message-evidence="id"
              type="button"
              @click="inspectMessageEvidence(message, id)"
            >
              [{{ id }}]
            </button>
          </footer>
        </article>

        <div v-if="pending" data-agent-loading class="answer-state" role="status">
          <button data-answer-cancel type="button" @click="$emit('cancel')">取消回答</button>
          AGENT · 正在核验证据
        </div>
        <div v-else-if="failure" class="answer-state answer-state--error" role="alert">
          <p>{{ failure.message }}</p>
          <button
            v-if="failure.requestId"
            :title="`复制支持参考 ${shortSupportReference(failure.requestId)}`"
            class="answer-state__reference"
            data-answer-support-reference
            type="button"
            @click="copySupportReference(failure.requestId)"
          >支持参考：{{ shortSupportReference(failure.requestId) }}</button>
          <div>
            <button
              v-if="failure.action === 'RETRY' || failure.action === 'RETRY_AFTER'"
              data-answer-recovery-action="retry"
              data-answer-retry
              type="button"
              :disabled="(failure.retryAfterSeconds ?? 0) > 0"
              @click="$emit('retry')"
            >{{ (failure.retryAfterSeconds ?? 0) > 0 ? `${failure.retryAfterSeconds} 秒后可重试` : '重新回答' }}</button>
            <button
              v-else-if="failure.action === 'CORRECT_INPUT'"
              data-answer-edit
              data-answer-recovery-action="correct-input"
              type="button"
              @click="focusComposer"
            >修改问题</button>
            <button
              v-else-if="failure.action === 'NAVIGATE_BACK'"
              data-answer-recovery-action="navigate-back"
              type="button"
              @click="$emit('navigateBack')"
            >返回作品集</button>
            <button
              v-else-if="failure.action === 'UPGRADE_REQUIRED'"
              data-answer-recovery-action="continue-basic-mode"
              type="button"
              @click="$emit('continueBasicMode')"
            >以基础模式继续</button>
          </div>
          <div v-if="failureSuggestions?.length" class="dynamic-suggestions">
            <button
              v-for="(q, qi) in failureSuggestions"
              :key="qi"
              data-failure-suggestion
              type="button"
              :disabled="pending"
              :title="q.text"
              @click="submitSuggested(q)"
            >{{ q.text }}</button>
          </div>
        </div>
      </div>
      </div>

      <button
        v-if="showJumpToLatest"
        data-jump-latest
        class="jump-latest"
        type="button"
        @click="jumpToLatest"
      >回到最新回答</button>
    </div>

    <div v-if="adjustment" class="adjust-bar" data-testid="plan-adjustment-bar">
      <p class="adjust-bar__label">正在调整当前计划</p>
      <p class="adjust-bar__title">{{ adjustment.planTitle }}</p>
      <p class="adjust-bar__examples">例如：<span>「去掉总结那一步」</span><span>「把推荐数量改成 2 个」</span></p>
      <div class="adjust-bar__row">
        <input
          v-model="adjustmentDraft"
          type="text"
          data-adjustment-input
          placeholder="用一句话描述怎么调整，提交后会带着原计划重新规划"
          aria-label="调整说明"
          :disabled="pending"
          @keydown.enter.prevent="submitAdjustmentDraft"
        >
        <button
          class="adjust-bar__submit"
          data-action="submit-adjustment"
          type="button"
          :disabled="pending || adjustmentDraft.trim().length === 0"
          @click="submitAdjustmentDraft"
        >提交调整</button>
        <button class="adjust-bar__exit" data-action="exit-adjustment" type="button" @click="exitAdjustment">退出调整</button>
      </div>
    </div>

    <form class="composer" @submit.prevent="submit">
      <span aria-hidden="true">›</span>
      <textarea
        ref="input"
        v-model="question"
        rows="1"
        :disabled="pending"
        aria-label="你的问题"
        placeholder="继续追问方案取舍、验证过程或证据"
        @input="resizeInput"
        @keydown="onComposerKeydown"
      ></textarea>
      <button data-agent-submit type="submit" :disabled="pending">发送 ↵</button>
    </form>
    <!-- P3：访客告知（handoff §15）。持续可见，非阻断，无同意弹窗。 -->
    <p class="conversation__privacy-notice" data-privacy-notice>
      系统会短期保存本次对话的任务范围与偏好（默认空闲 24 小时、最长 7 天），用于刷新恢复与连续追问；
      不保存问题原文、助手答案或证据正文。关闭页签后不会跨页签或跨设备自动恢复。
      <button
        v-if="recoverySummary || resumeUnavailable"
        data-clear-conversation-footer
        type="button"
        class="conversation__privacy-clear"
        :disabled="clearPending"
        @click="$emit('clearConversation')"
      >{{ clearPending ? '清除中…' : '清除本次对话' }}</button>
    </p>
  </section>
</template>

<style scoped>
.conversation {
  height: 100%;
  position: relative;
  display: grid;
  min-width: 0;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  color: var(--workspace-text, var(--ink));
  background: var(--workspace-thread-bg, var(--paper-hi));
  overflow: hidden;
}

/* P3：刷新恢复卡 / 完成回执 / 续接提示（handoff §4/§5/§11）。 */
.thread-p3-card {
  margin: 0 0 0.75rem;
  padding: 0.625rem 0.75rem;
  border: 1px solid var(--workspace-rule, currentColor);
  border-radius: 8px;
  background: var(--workspace-surface-subtle, rgba(0, 0, 0, 0.03));
  font-size: 0.8125rem;
  line-height: 1.45;
}
.thread-p3-card__title {
  margin: 0 0 0.25rem;
  font-weight: 600;
}
.thread-p3-card__body {
  margin: 0;
  color: var(--workspace-text-secondary, inherit);
}
.thread-recovery__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.875rem;
  margin: 0 0 0.5rem;
  padding: 0;
  list-style: none;
  color: var(--workspace-text-secondary, inherit);
}
.thread-p3-card__actions {
  display: flex;
  gap: 0.5rem;
}
.thread-p3-card__actions button,
.conversation__privacy-clear {
  border: 1px solid var(--workspace-rule, currentColor);
  border-radius: 6px;
  background: transparent;
  color: var(--workspace-text, inherit);
  padding: 0.25rem 0.625rem;
  font-size: 0.8125rem;
  cursor: pointer;
}
.thread-p3-card__actions button:not(:disabled):hover,
.conversation__privacy-clear:not(:disabled):hover {
  background: var(--workspace-action-bg, rgba(0, 0, 0, 0.06));
}
.thread-p3-card__actions button:disabled,
.conversation__privacy-clear:disabled {
  opacity: 0.6;
  cursor: progress;
}
.thread-continuation-notice {
  margin: 0 0 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  background: var(--workspace-surface-subtle, rgba(0, 0, 0, 0.03));
  font-size: 0.8125rem;
  color: var(--workspace-text-secondary, inherit);
}
/* P3 防御性提示：执行快照与顶层回答矛盾时（handoff §7/§9）。 */
.thread-execution-conflict {
  margin: 0 0 0.75rem;
  padding: 0.5rem 0.75rem;
  border-left: 2px solid var(--workspace-warning, #8a6a14);
  background: var(--workspace-surface-subtle, rgba(0, 0, 0, 0.03));
  font-size: 0.8125rem;
  color: var(--workspace-text-secondary, inherit);
}
.conversation__privacy-notice[data-privacy-notice] {
  margin: 0;
  padding: 0.5rem 1rem 0.625rem;
  border-top: 1px solid var(--workspace-rule, currentColor);
  font-size: 0.6875rem;
  line-height: 1.5;
  color: var(--workspace-text-faint, var(--muted, inherit));
}

.conversation__body {
  position: relative;
  min-height: 0;
  overflow: hidden;
}

.conversation__head {
  display: flex;
  min-height: 82px;
  padding: 20px 28px 18px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--workspace-rule, var(--rule));
}

.conversation__head p,
.thread-empty > p,
.message > p {
  margin: 0 0 10px;
  color: var(--workspace-accent-soft, var(--red-hi));
  font: 11px var(--mono);
  letter-spacing: 0.13em;
}

/* Agent meta 分层：把「核验状态/来源」做视觉重点，技术枚举降级成尾注。
   替代原来六个字段用 · 串成一行日志式的写法。 */
.message__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 12px;
}

.message__meta-prefix {
  color: var(--workspace-accent-soft, var(--red-hi));
}

.message__meta-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 核验状态与来源：边框小标签，承载「可追溯」这个核心信号 */
.message__meta-tag {
  padding: 2px 7px;
  color: var(--workspace-accent, var(--red));
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 4px;
  font-size: 10px;
  letter-spacing: 0.08em;
}

/* 已核验状态用红色实底，强调可信度这个卖点 */
.message__meta-tag[data-verification='VERIFIED'] {
  color: var(--workspace-primary-text, var(--paper-hi));
  border-color: var(--workspace-accent, var(--red));
  background: var(--workspace-accent, var(--red));
}

/* 技术枚举尾注：resolution/generationMode，价值低，降到极淡 */
.message__meta-tail {
  color: var(--workspace-text-faint, var(--faint));
  font-size: 10px;
  letter-spacing: 0.06em;
}

.conversation__title {
  min-width: 0;
}

.conversation__head h1 {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  font: 500 22px var(--serif);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.conversation__case-context {
  display: flex;
  margin-top: 10px;
  align-items: center;
  gap: 8px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.5 var(--mono);
}

.conversation__case-context button {
  padding: 2px 6px;
  color: var(--workspace-accent, var(--red));
  border: 1px solid currentcolor;
  border-radius: var(--agent-radius-sm);
  background: transparent;
  font: inherit;
}

.conversation__case-context button:hover {
  background: color-mix(in srgb, var(--workspace-accent, var(--red)) 8%, transparent);
}

.conversation__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conversation__tools span,
.conversation__tools button {
  padding: 8px 10px;
  color: var(--workspace-text-secondary, var(--muted));
  border: 1px solid var(--workspace-rule, var(--rule));
  background: transparent;
  font: 11px var(--mono);
  letter-spacing: 0.08em;
}

.conversation__tools button {
  display: none;
  font-size: 11px;
}

.conversation__scroll {
  height: 100%;
  overflow-y: auto;
}

.thread {
  width: min(820px, calc(100% - 96px));
  margin: 24px auto 40px;
}

/* B5：空会话时引导区垂直居中，消除中栏约 368px 顶部死空。
   空态内容少，min-height: 100% + grid 居中让引导区在滚动视口里垂直落中。 */
.thread[data-conversation-state='empty'] {
  display: grid;
  min-height: 100%;
  margin-block: auto;
  align-content: center;
}

.thread-empty {
  padding: 8px 0 10px;
  border: 0;
}

.thread-empty > p:first-child {
  margin: 0 0 18px;
}

.thread-empty__lead {
  margin: 0 0 32px;
  padding-left: 14px;
  max-width: 460px;
  color: var(--workspace-text, var(--ink));
  border-left: 2px solid var(--workspace-accent, var(--red));
  font: 400 17px/1.75 var(--serif);
}

.thread-empty__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.thread-empty__list button {
  display: flex;
  width: 100%;
  padding: 11px 14px;
  gap: 8px;
  color: var(--workspace-text, var(--ink));
  text-align: left;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
  background: rgba(255, 255, 255, 0.35);
  font: 13px/1.5 var(--sans);
  transition: border-color var(--agent-motion-fast) var(--ease),
    color var(--agent-motion-fast) var(--ease);
}

.thread-empty__list button:not(:disabled):hover {
  border-color: var(--workspace-accent, var(--red));
  color: var(--workspace-accent, var(--red));
}

.thread-empty__list button span {
  color: var(--workspace-accent, var(--red));
  font-family: var(--mono);
}

.dynamic-suggestions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
}

.dynamic-suggestions button {
  display: flex;
  align-items: baseline;
  width: 100%;
  padding: 10px 14px;
  gap: 8px;
  color: var(--workspace-text, var(--ink));
  text-align: left;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
  background: rgba(255, 255, 255, 0.35);
  font: 13px/1.5 var(--sans);
  cursor: pointer;
  transition: border-color var(--agent-motion-fast) var(--ease),
    color var(--agent-motion-fast) var(--ease);
}

.dynamic-suggestions button::before {
  content: '↳';
  flex: none;
  color: var(--workspace-accent, var(--red));
  font-family: var(--mono);
}

.dynamic-suggestions button:not(:disabled):hover {
  border-color: var(--workspace-accent, var(--red));
  color: var(--workspace-accent, var(--red));
}

.dynamic-suggestions button:disabled {
  opacity: 0.55;
  cursor: default;
}

.message {
  max-width: 760px;
  margin-bottom: 34px;
  border-radius: var(--agent-radius-sm);
  transition: background-color 360ms ease, box-shadow 360ms ease;
}

.message--user {
  width: fit-content;
  max-width: 64%;
  margin-left: auto;
  padding: 0;
  border: 0;
}

.message--user .message__meta {
  text-align: right;
}

/* 用户消息回归文档化样式（07-22 第 116 行）：自然文本流 + 2px --workspace-accent 左线，
   不使用实心消息气泡。去 background 与 border-radius，文字回到墨色，
   靠左线与右对齐的 meta 区分用户侧。未来若恢复实心气泡需先回写设计文档授权。 */
.message--user .message__body {
  padding: 4px 0 4px 14px;
  color: var(--workspace-text, var(--ink));
  border-left: 2px solid var(--workspace-accent, var(--red));
  font: 16px/1.7 var(--sans);
}

.message--agent {
  padding: 0;
  border: 0;
  color: var(--workspace-text, var(--ink));
}

.structured-answer > section {
  border-radius: var(--agent-radius-sm);
  transition: background-color 360ms ease, box-shadow 360ms ease;
}

[data-answer-focus="true"] {
  background: color-mix(in srgb, var(--workspace-accent, var(--red)) 9%, transparent);
  box-shadow: 0 0 0 8px
    color-mix(in srgb, var(--workspace-accent, var(--red)) 4%, transparent);
}

.message > div {
  font: 16px/1.85 var(--serif);
}

.structured-answer h3 {
  margin: 0 0 8px;
  color: var(--workspace-text, var(--ink));
  font: 600 16px/1.45 var(--serif);
}

.structured-answer h4,
.structured-answer section h4 {
  margin: 16px 0 4px;
  color: var(--workspace-text, var(--ink-2));
  font: 600 13px/1.4 var(--sans);
}

/* P5 stp-v2 来源域视觉（鲜明版 B，设计 §4.3/§4.4，原型 compare-source-domain.html）：
   饱和域色底 + 同色描边 + 满高 5px 域色左条；SYNTHESIS 最强靛蓝卡 + 头部色带。
   --dc 为当前域色，--dc-bg 为域色着色底，按 data-domain 注入。 */
.answer-block[data-domain] {
  position: relative;
  margin: 0 0 14px;
  padding: 14px 16px 16px 20px;
  border: 1px solid color-mix(in srgb, var(--dc) 42%, var(--workspace-rule, var(--rule)));
  border-radius: var(--agent-radius-sm);
  background: var(--dc-bg);
  overflow: hidden;
}
.answer-block[data-domain]::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  background: var(--dc);
}
.answer-block[data-domain='GENERAL'] {
  --dc: var(--agent-source-general);
  --dc-bg: var(--agent-source-general-bg);
}
.answer-block[data-domain='PORTFOLIO'] {
  --dc: var(--agent-source-portfolio);
  --dc-bg: var(--agent-source-portfolio-bg);
}
.answer-block[data-domain='SYNTHESIS'] {
  --dc: var(--agent-source-synthesis);
  --dc-bg: var(--agent-source-synthesis-bg);
  background: color-mix(in srgb, var(--agent-source-synthesis) 16%, var(--paper-hi));
  border-color: color-mix(in srgb, var(--agent-source-synthesis) 60%, var(--workspace-rule, var(--rule)));
}
/* SYNTHESIS 头部色带：让跨域推导一眼可辨 */
.answer-block[data-domain='SYNTHESIS'] .answer-block__head {
  margin: -14px -16px 12px -20px;
  padding: 10px 16px 10px 20px;
  background: color-mix(in srgb, var(--agent-source-synthesis) 22%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--agent-source-synthesis) 40%, var(--workspace-rule, var(--rule)));
}
.answer-block__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.answer-block__head h4 {
  flex: 1 1 100%;
  margin: 0;
}
/* 实底来源药丸：域色底 + paper 字 + paper 色点 */
.source-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--dc);
  color: var(--paper-hi);
  font: 10px var(--mono);
  letter-spacing: 0.04em;
}
.source-pill__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--paper-hi);
}
/* SYNTHESIS 来源药丸用淡底描边（头部色带上实底会糊），保持可读 */
.answer-block[data-domain='SYNTHESIS'] .source-pill {
  background: color-mix(in srgb, var(--agent-source-synthesis) 14%, var(--paper-hi));
  color: var(--agent-source-synthesis);
  border: 1px solid color-mix(in srgb, var(--agent-source-synthesis) 45%, var(--workspace-rule, var(--rule)));
}
.answer-block[data-domain='SYNTHESIS'] .source-pill__dot {
  background: var(--agent-source-synthesis);
}
/* 实底支持徽标：域色底 + paper 字 */
.support-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px solid var(--dc);
  border-radius: 4px;
  background: var(--dc);
  color: var(--paper-hi);
  font: 10px var(--mono);
  letter-spacing: 0.04em;
}
.support-badge[data-support='VERIFIED_PUBLIC_EVIDENCE'] {
  --dc: var(--agent-source-portfolio);
}
.support-badge[data-support='GENERAL_KNOWLEDGE'] {
  --dc: var(--agent-source-general);
}
.support-badge[data-support='DERIVED_FROM_TASKS'] {
  --dc: var(--agent-source-synthesis);
}

.message footer {
  display: flex;
  margin-top: 15px;
  gap: 7px;
}

.follow-up-actions button,
.message footer button {
  min-height: 32px;
  padding: 6px 10px;
  color: var(--workspace-text-secondary, var(--muted));
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.35);
  font: 11px var(--mono);
  transition: border-color var(--agent-motion-fast) var(--ease),
    color var(--agent-motion-fast) var(--ease);
}

.follow-up-actions button:not(:disabled):hover,
.message footer button:hover {
  border-color: var(--workspace-accent, var(--red));
  color: var(--workspace-accent, var(--red));
}

.follow-up-actions {
  display: flex;
  flex-wrap: wrap;
  margin-top: 10px;
  gap: 7px;
}

.context-version-updated {
  padding: 9px 11px;
  color: var(--ink-2);
  border-left: 2px solid var(--workspace-accent-soft, var(--red-hi));
  background: var(--workspace-surface-subtle, var(--paper-low));
  font: 11px/1.6 var(--mono);
}

/* P5 重验证成功轻提示（设计 §13.14）：克制、不阻断 */
.context-resolution-notice {
  margin: 0 0 0.75rem;
  padding: 7px 11px;
  color: var(--workspace-text-secondary, var(--ink-2));
  border-left: 2px solid var(--workspace-text-faint, var(--faint));
  background: var(--workspace-surface-subtle, var(--paper-low));
  font: 11px/1.6 var(--mono);
}

/* P5 部分完成横幅（设计 §9.8）：温和提示，不阻断，已发布事实仍可信 */
.partial-banner {
  margin: 0 0 0.75rem;
  padding: 8px 11px;
  border-left: 2px solid var(--workspace-accent-soft, var(--red-hi));
  background: var(--workspace-surface-subtle, var(--paper-low));
  color: var(--workspace-text-secondary, var(--ink-2));
  font: 11px/1.6 var(--mono);
}

/* P5 结构化限定语（设计 §9.9）：克制提示，挂在所涉 Block 下方或回答级 */
.answer-caveats {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 10px 0 0;
}
.answer-caveat {
  margin: 8px 0 0;
  padding: 7px 10px;
  border-left: 2px solid var(--workspace-text-faint, var(--faint));
  background: var(--workspace-surface-subtle, rgba(0, 0, 0, 0.02));
  color: var(--workspace-text-secondary, var(--muted));
  font: 11.5px/1.6 var(--sans);
}

.follow-up-actions--answer {
  margin-top: 18px;
}

.follow-up-actions button {
  font: 12px var(--mono);
}

.follow-up-actions button:disabled {
  cursor: wait;
  opacity: 0.55;
}

/* —— 结构化作品推荐卡组（复刻证据卡 / 资产卡底盘，沿用现有 token）—— */
.portfolio-recommendation {
  margin-top: 18px;
}

.reco-satisfied {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  margin: 0 0 12px;
  padding: 9px 13px;
  border: 1px solid var(--workspace-rule, var(--rule));
  background: var(--workspace-surface-subtle, var(--paper-low));
  font: 12px/1.6 var(--mono);
  color: var(--workspace-text-secondary, var(--muted));
}

.reco-satisfied__code {
  color: var(--workspace-text-faint, var(--faint));
  font-size: 10px;
  letter-spacing: 0.1em;
}

.reco-unsatisfied {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  margin: 0 0 12px;
  padding: 11px 15px;
  border: 1px solid var(--workspace-accent, var(--red));
  background: var(--paper-hi);
  font: 12.5px/1.6 var(--mono);
  color: var(--ink-2);
}

.reco-unsatisfied::before {
  content: "";
  flex: none;
  align-self: center;
  width: 7px;
  height: 7px;
  background: var(--workspace-accent, var(--red));
  transform: rotate(45deg);
}

.reco-unsatisfied__code {
  color: var(--workspace-accent, var(--red));
  font-size: 10px;
  letter-spacing: 0.1em;
}

/* 卡组网格：发丝线分隔，2 列（与 .sel-bundle 同源） */
.reco-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: var(--workspace-rule, var(--rule));
  border: 1px solid var(--workspace-rule, var(--rule));
}

.reco-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px 18px 16px;
  background: var(--paper-hi);
  transition: background var(--agent-motion-fast) var(--ease);
}

.reco-card:only-child {
  grid-column: 1 / -1;
}

.reco-card:hover {
  background: var(--paper);
}

.reco-card__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.reco-card__no {
  flex: none;
  color: var(--workspace-accent, var(--red));
  font: 500 18px var(--mono);
  letter-spacing: 0.04em;
}

.reco-card__title {
  flex: 1;
  color: var(--workspace-text, var(--ink));
  font: 500 18px/1.3 var(--serif);
  letter-spacing: -0.01em;
}

.reco-card__reason {
  margin: 0;
  padding-top: 10px;
  border-top: 1px dashed var(--workspace-rule, var(--rule));
  color: var(--muted);
  font: 13px/1.7 var(--serif);
}

.reco-card__evidence {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.reco-evi {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
  color: var(--muted);
  font: 10.5px/1.55 var(--mono);
  letter-spacing: 0.02em;
  transition: color var(--agent-motion-fast) var(--ease);
}

.reco-evi::before {
  content: "";
  flex: none;
  align-self: center;
  width: 5px;
  height: 5px;
  background: var(--red-hi);
  transform: rotate(45deg);
}

.reco-evi:hover {
  color: var(--workspace-accent, var(--red));
}

.reco-card__link {
  align-self: flex-start;
  margin-top: auto;
  padding-top: 4px;
  border-bottom: 1px solid transparent;
  color: var(--workspace-accent, var(--red));
  text-decoration: none;
  font: 11px var(--mono);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  transition: border-color var(--agent-motion-fast) var(--ease);
}

.reco-card__link:hover {
  border-bottom-color: var(--workspace-accent, var(--red));
}

/* 继续对话操作：沿用现有 follow-up-actions 描边按钮语汇 */
.reco-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.reco-card__actions--group {
  margin-top: 12px;
}

.reco-card__action {
  min-height: 32px;
  padding: 6px 10px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.35);
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px var(--mono);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: border-color var(--agent-motion-fast) var(--ease),
    color var(--agent-motion-fast) var(--ease);
}

.reco-card__action:not(:disabled):hover {
  border-color: var(--workspace-accent, var(--red));
  color: var(--workspace-accent, var(--red));
}

.reco-card__action:disabled {
  cursor: wait;
  opacity: 0.55;
}

@media (max-width: 620px) {
  .reco-grid {
    grid-template-columns: 1fr;
  }
}

.answer-state {
  max-width: 760px;
  margin-bottom: 34px;
  padding: 14px 18px;
  color: var(--workspace-text-secondary, var(--muted));
  border-left: 1px solid var(--workspace-rule, var(--rule));
  font: 11px/1.7 var(--mono);
}

.answer-state--error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--workspace-accent, var(--red));
}

.answer-state--error p {
  margin: 0;
}

.answer-state--error > div {
  display: flex;
  gap: 8px;
}

.answer-state--error button {
  padding: 7px 10px;
  color: inherit;
  border: 1px solid currentcolor;
  border-radius: var(--agent-radius-sm);
  background: transparent;
  font: 11px var(--mono);
}

.jump-latest {
  position: absolute;
  right: 28px;
  bottom: 16px;
  z-index: 2;
  min-height: 32px;
  padding: 6px 10px;
  color: var(--workspace-text-secondary, var(--muted));
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
  background: color-mix(in srgb, var(--agent-shell-paper) 90%, white);
  box-shadow: 0 8px 20px rgb(26 20 16 / 10%);
  font: 11px var(--mono);
}

.composer {
  display: flex;
  min-height: 62px;
  margin: 0 28px 24px;
  padding: 0 16px;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-md);
  background: color-mix(in srgb, var(--agent-shell-paper) 86%, white);
}

.composer:focus-within {
  border-color: var(--workspace-accent, var(--red));
}

.composer > span {
  color: var(--workspace-accent-soft, var(--red-hi));
  font: 20px var(--serif);
}

textarea {
  min-height: 28px;
  max-height: 110px;
  flex: 1;
  resize: none;
  color: var(--workspace-text, var(--ink));
  border: 0;
  background: transparent;
  font-size: 13px;
}

textarea::placeholder {
  color: var(--workspace-text-faint, var(--faint));
}

.composer button {
  min-height: 42px;
  padding: 10px 14px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 0;
  border-radius: var(--agent-radius-sm);
  background: var(--workspace-action-bg, var(--red));
  font: 13px var(--mono);
  letter-spacing: 0.1em;
}

.composer button:not(:disabled):hover {
  background: var(--workspace-action-bg-hover, #662522);
}

.thread-empty button:disabled,
textarea:disabled,
.composer button:disabled {
  cursor: wait;
  opacity: 0.55;
}

@media (max-width: 1279.98px) {
  .evidence-toggle {
    display: block !important;
  }
}

@media (max-width: 959.98px) {
  .session-toggle {
    display: block !important;
  }

  .thread {
    margin-inline: auto;
  }
}

@media (max-width: 620px) {
  .conversation__head {
    padding-inline: 18px;
  }

  .conversation__tools span {
    display: none;
  }

  .thread {
    width: calc(100% - 36px);
  }

  .message--user {
    max-width: 85%;
  }

.plan-change-dismissed {
  margin: 18px 0;
  padding: 10px 14px;
  border: 1px dashed var(--workspace-rule, var(--rule));
  color: var(--workspace-text-faint, var(--faint));
  font: 10.5px var(--mono);
  letter-spacing: .04em;
}

/* 调整模式上下文条（决策 1 · 方案 B）：composer 上方的显式调整态 */
.adjust-bar {
  margin: 0 28px 12px;
  padding: 12px 14px;
  border: 1px solid var(--workspace-accent, var(--red));
  background: var(--paper-hi);
}
.adjust-bar__label { margin: 0; font: 10px var(--mono); letter-spacing: .1em; color: var(--workspace-accent, var(--red)); }
.adjust-bar__title { margin: 3px 0 0; font: 600 14px var(--serif); color: var(--workspace-text, var(--ink)); }
.adjust-bar__examples { margin: 6px 0 0; font: 11.5px/1.7 var(--sans); color: var(--workspace-text-secondary, var(--muted)); }
.adjust-bar__examples span { display: inline-block; margin-right: 12px; font-family: var(--mono); font-size: 10.5px; color: var(--workspace-text-faint, var(--faint)); }
.adjust-bar__row { display: flex; gap: 8px; margin-top: 10px; }
.adjust-bar__row input {
  flex: 1; min-width: 0; padding: 8px 10px;
  border: 1px solid var(--workspace-rule, var(--rule)); border-radius: var(--agent-radius-sm, 8px);
  background: rgba(255,255,255,0.5); font: 13px var(--sans); color: var(--workspace-text, var(--ink));
}
.adjust-bar__row input:focus { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 1px; }
.adjust-bar__row button { font: 11px var(--mono); padding: 8px 14px; border-radius: var(--agent-radius-sm, 8px); cursor: pointer; }
.adjust-bar__submit { border: 1px solid var(--workspace-accent, var(--red)); background: var(--workspace-accent, var(--red)); color: var(--paper-hi); }
.adjust-bar__submit:disabled { opacity: .45; cursor: not-allowed; }
.adjust-bar__exit { border: 1px solid var(--workspace-rule, var(--rule)); background: transparent; color: var(--workspace-text-secondary, var(--muted)); }
.adjust-bar__exit:hover { border-color: var(--workspace-accent, var(--red)); color: var(--workspace-accent, var(--red)); }
@media (max-width: 620px) {
  .adjust-bar__row { flex-direction: column; }
}

.composer {
    margin-inline: 18px;
  }
}

@media (hover: none) {
  textarea {
    font-size: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .message,
  .structured-answer > section {
    transition: none;
  }
}
</style>
