<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { AudienceRole, PublicProject } from '../../public-content/model/publicContentTypes'
import type { AgentSession } from '../model/sessionTypes'
import type {
  AnswerSectionType,
  ConversationSuggestedQuestion,
  FollowUpAction,
  FollowUpIntent,
  PortfolioRecommendation,
  PortfolioRecommendationContextRequest,
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
  blockScopeTag,
  degradedNotice,
} from '../model/answerLabels'
import type { ErrorAction } from '../../portfolio/api/apiErrorActions'

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
}>()

const emit = defineEmits<{
  submit: [question: string]
  submitSuggestion: [suggestion: ConversationSuggestedQuestion]
  inspectEvidence: [request: EvidenceInspectRequest]
  toggleSessions: []
  toggleEvidence: []
  retry: []
  navigateBack: []
  cancel: []
  followUp: [action: FollowUpAction]
  clearCaseContext: []
  refineRecommendation: [action: { question: string; recommendationContext: PortfolioRecommendationContextRequest }]
}>()

const question = ref(props.seedQuestion ?? '')
const input = ref<HTMLTextAreaElement | null>(null)
const scrollArea = ref<HTMLElement | null>(null)
const showJumpToLatest = ref(false)
const followLatest = ref(true)
const highlightedTarget = ref('')
let highlightTimer: ReturnType<typeof setTimeout> | null = null
const state = computed(() => {
  if (props.pending) return 'generating'
  return props.session.messages.length ? 'conversation' : 'empty'
})
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

function v2Blocks(message: AgentSession['messages'][number]) {
  const answer = message.answer
  if (!answer || !answer.blocks || answer.blocks.length === 0) return []
  return answer.blocks
}

function isV2Answer(message: AgentSession['messages'][number]) {
  const answer = message.answer
  return Boolean(answer && (answer.blocks?.length || answer.intent))
}

function dynamicSuggestions(message: AgentSession['messages'][number]) {
  return message.answer?.suggestedQuestions ?? []
}

function followUp(
  message: AgentSession['messages'][number],
  question: string,
  intent: FollowUpIntent,
  selectedSectionType?: AnswerSectionType,
  referencedClaimIds?: string[],
) {
  const envelope = message.answer?.contextEnvelope
  if (!envelope || props.pending) return
  emit('followUp', {
    question,
    contextEnvelope: {
      ...envelope,
      projectSlugs: envelope.projectSlugs ? [...envelope.projectSlugs] : undefined,
      referencedClaimIds: [...(referencedClaimIds ?? envelope.referencedClaimIds)],
      selectedSectionType,
      followUpIntent: intent,
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

function refineRecommendation(
  message: AgentSession['messages'][number],
  index: number,
  intent: RecommendationRefine,
) {
  const recommendation = message.answer?.portfolioRecommendation
  if (!recommendation || props.pending) return
  const ordinal = ordinalLabel(index)
  const question = intent === 'REPLACE' ? `换掉第${ordinal}个` : `为什么推荐第${ordinal}个？`
  emit('refineRecommendation', {
    question,
    recommendationContext: recommendationContextFor(recommendation),
  })
}

function refineWhole(
  message: AgentSession['messages'][number],
  question: string,
) {
  const recommendation = message.answer?.portfolioRecommendation
  if (!recommendation || props.pending) return
  emit('refineRecommendation', {
    question,
    recommendationContext: recommendationContextFor(recommendation),
  })
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
              <span v-if="message.answer.generationMode !== 'FALLBACK' && answerVerificationTag(message.answer)" class="message__meta-tag" :data-verification="message.answer.verification">{{ answerVerificationTag(message.answer) }}</span>
              <span v-if="answerSourceTag(message.answer)" class="message__meta-tag">{{ answerSourceTag(message.answer) }}</span>
              <span v-if="answerGenerationTag(message.answer)" class="message__meta-tag" data-answer-generation>{{ answerGenerationTag(message.answer) }}</span>
            </span>
            <span v-if="answerTechTail(message.answer)" class="message__meta-tail">{{ answerTechTail(message.answer) }}</span>
          </p>
          <p v-else class="message__meta">{{ message.role === 'AGENT' ? 'AGENT' : 'YOU' }}</p>
          <p
            v-if="message.answer && degradedNotice(message.answer)"
            data-degraded-notice
            class="degraded-notice"
            role="status"
          >{{ degradedNotice(message.answer) }}</p>
          <div v-if="message.answer" class="structured-answer">
            <template v-if="isV2Answer(message)">
              <h3 v-if="message.answer.title">{{ message.answer.title }}</h3>
              <p v-if="message.answer.summary">{{ message.answer.summary }}</p>
              <section
                v-for="(block, blockIndex) in v2Blocks(message)"
                :key="blockIndex"
                :data-block-scope="block.sourceScope"
                class="answer-block"
              >
                <p class="answer-block__scope" :data-scope="block.sourceScope">{{ blockScopeTag(block.sourceScope) }}</p>
                <p class="answer-block__content">{{ block.content }}</p>
                <div v-if="block.evidenceIds.length" class="answer-block__citations">
                  <button
                    v-for="eid in block.evidenceIds"
                    :key="eid"
                    :data-block-evidence="eid"
                    type="button"
                    @click="inspectMessageEvidence(message, eid)"
                  >[{{ eid }}]</button>
                </div>
              </section>
              <p
                v-if="message.answer.contextVersionUpdated"
                data-context-version-updated
                class="context-version-updated"
                role="status"
              >公开内容已更新，本轮已按当前版本重新核对。</p>
              <!-- 结构化作品推荐卡组（可选；items 顺序是后端权威顺序，前端不重排）-->
              <section
                v-if="message.answer.portfolioRecommendation"
                class="portfolio-recommendation"
                data-portfolio-recommendation
                :aria-label="`作品推荐 · ${message.answer.portfolioRecommendation.items.length} 项`"
              >
                <p
                  v-if="message.answer.portfolioRecommendation.satisfiedConstraints.length"
                  class="reco-satisfied"
                >
                  <span class="reco-satisfied__code">已满足</span>
                  <span>{{
                    message.answer.portfolioRecommendation.satisfiedConstraints.join(' · ')
                  }}</span>
                </p>
                <p
                  v-if="message.answer.portfolioRecommendation.unsatisfiedConstraints.length"
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
                  v-if="message.answer.portfolioRecommendation.items.length"
                  class="reco-grid"
                >
                  <div
                    v-for="(item, itemIndex) in message.answer.portfolioRecommendation.items"
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
                    <div class="reco-card__actions">
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
                    </div>
                  </div>
                </div>
                <div class="reco-card__actions reco-card__actions--group">
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
            </template>
            <template v-else>
              <h3>{{ message.answer.title }}</h3>
              <p>{{ message.answer.summary }}</p>
              <p
                v-if="message.answer.contextVersionUpdated"
                data-context-version-updated
                class="context-version-updated"
                role="status"
              >公开内容已更新，本轮已按当前版本重新核对。</p>
              <section
                v-for="section in message.answer.sections"
                :key="section.type"
                :data-section-type="section.type"
                :data-answer-focus="
                  highlightedTarget === `${message.id}:${section.type}` ? 'true' : undefined
                "
                tabindex="-1"
              >
                <h4>{{ section.title }}</h4>
                <p>{{ section.content }}</p>
                <div v-if="message.answer.contextEnvelope" class="follow-up-actions">
                  <button
                    type="button"
                    :disabled="pending"
                    @click="followUp(message, `展开${section.title}`, 'EXPAND_SECTION', section.type, section.claimIds)"
                  >展开本节</button>
                  <button
                    data-section-evidence
                    type="button"
                    :disabled="pending || !section.evidenceIds.length"
                    @click="inspectSection(message, section)"
                  >查看本节证据</button>
                  <button
                    type="button"
                    :disabled="pending"
                    @click="followUp(message, `说明${section.title}的判断`, 'EXPLAIN_DECISION', section.type, section.claimIds)"
                  >说明判断</button>
                </div>
              </section>
              <div v-if="message.answer.contextEnvelope" class="follow-up-actions follow-up-actions--answer">
                <button
                  data-follow-up="current-status"
                  type="button"
                  :disabled="pending"
                  @click="followUp(message, '查看当前状态', 'CURRENT_STATUS')"
                >查看当前状态</button>
                <button
                  type="button"
                  :disabled="pending"
                  @click="followUp(message, '查看相关问题', 'RELATED_QUESTION')"
                >查看相关问题</button>
                <button
                  v-if="(message.answer.contextEnvelope.projectSlugs?.length ?? 0) > 1"
                  type="button"
                  :disabled="pending"
                  @click="followUp(message, '对比这些项目', 'COMPARE_PROJECTS')"
                >对比项目</button>
              </div>
            </template>
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
          <footer v-if="message.evidenceIds.length && !isV2Answer(message)">
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
  </section>
</template>

<style scoped>
.conversation {
  height: 100%;
  position: relative;
  display: grid;
  min-width: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  color: var(--workspace-text, var(--ink));
  background: var(--workspace-thread-bg, var(--paper-hi));
  overflow: hidden;
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
