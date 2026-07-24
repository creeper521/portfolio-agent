<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { AudienceRole, PublicProject } from '../../public-content/model/publicContentTypes'
import type { AgentSession } from '../model/sessionTypes'
import type {
  AnswerSectionType,
  ConversationSuggestedQuestion,
  FollowUpAction,
  FollowUpIntent,
} from '../model/answerTypes'
import type {
  AnswerFocusTarget,
  EvidenceInspectRequest,
} from '../model/evidenceDeskModel'

const props = defineProps<{
  session: AgentSession
  role: AudienceRole
  project: PublicProject
  seedQuestion?: string
  sessionsOpen?: boolean
  evidenceOpen?: boolean
  pending: boolean
  error: string
  focusTarget?: AnswerFocusTarget | null
}>()

const emit = defineEmits<{
  submit: [question: string]
  submitSuggestion: [suggestion: ConversationSuggestedQuestion]
  inspectEvidence: [request: EvidenceInspectRequest]
  toggleSessions: []
  toggleEvidence: []
  retry: []
  followUp: [action: FollowUpAction]
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
    const message = scrollArea.value?.querySelector<HTMLElement>(
      `[data-message-id="${target.messageId}"]`,
    )
    const element = target.sectionType
      ? message?.querySelector<HTMLElement>(
        `[data-section-type="${target.sectionType}"]`,
      )
      : message
    element?.scrollIntoView({
      block: 'center',
      behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    })
    element?.focus({ preventScroll: true })
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

function answerLabel(message: AgentSession['messages'][number]) {
  const answer = message.answer
  if (!answer) return ''
  // v2: prioritize intent-driven visible labels
  if (answer.intent === 'TIME_SENSITIVE') return '暂时不可用'
  if (answer.intent === 'UNSUPPORTED_OR_UNSAFE') return '无法处理该请求'
  if (answer.resolution === 'BOUNDARY') return '当前能力边界'
  if (answer.resolution === 'REJECTED') return '无法处理该请求'
  if (answer.intent) return '回答'
  // v1 fallback
  if (answer.verification === 'VERIFIED') return '已核验回答'
  if (answer.verification === 'PARTIALLY_VERIFIED') return '部分事实已核验'
  return '尚未核验'
}

function answerScopeTag(message: AgentSession['messages'][number]) {
  const answer = message.answer
  if (!answer) return ''
  if (answer.answerScope === 'GENERAL') return '通用知识'
  if (answer.answerScope === 'PORTFOLIO') return '作品集资料'
  if (answer.answerScope === 'HYBRID') return '混合回答'
  return ''
}

function blockScopeTag(scope: 'GENERAL' | 'PORTFOLIO') {
  return scope === 'GENERAL' ? '通用知识' : '作品集资料'
}

function degradedNotice(message: AgentSession['messages'][number]) {
  if (!message.answer?.degraded && message.answer?.generationMode !== 'FALLBACK') return ''
  return '已切换到基础回答'
}

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

function answerSourceLabel(message: AgentSession['messages'][number]) {
  const source = message.answer?.answerSource
  if (source === 'RETRIEVAL') return 'RETRIEVAL · 来自公开资料检索'
  if (source === 'PRESET') return 'PRESET · 来自已发布问题'
  return ''
}

// 来源的人话短标签（去掉冗长的「来自…」，只留检索/预设）
function answerSourceTag(message: AgentSession['messages'][number]) {
  const source = message.answer?.answerSource
  if (source === 'RETRIEVAL') return '资料检索'
  if (source === 'PRESET') return '预设问题'
  return ''
}

// 核验状态的人话标签，用于做视觉重点
function answerVerificationTag(message: AgentSession['messages'][number]) {
  const verification = message.answer?.verification
  if (verification === 'VERIFIED') return '已核验'
  if (verification === 'PARTIALLY_VERIFIED') return '部分核验'
  if (verification === 'UNVERIFIED') return '未核验'
  return ''
}

// 技术枚举尾注：resolution + generationMode，价值低，降级成极淡尾注
function answerTechTail(message: AgentSession['messages'][number]) {
  const answer = message.answer
  if (!answer) return ''
  return [answer.resolution, answer.generationMode].filter(Boolean).join(' · ')
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
</script>

<template>
  <section class="conversation">
    <header class="conversation__head">
      <div>
        <p>AGENT CONVERSATION · Agent 对话</p>
        <h1>{{ session.title }}</h1>
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

    <div ref="scrollArea" class="conversation__scroll" @scroll.passive="onThreadScroll">
      <div class="thread" :data-conversation-state="state">
        <section v-if="state === 'empty'" class="thread-empty">
          <p>YOU · FROM DOSSIER</p>
          <p class="thread-empty__lead">从一个可核验的问题开始——这里只回答有公开证据支撑的内容。</p>
          <div class="thread-empty__list">
            <button
              v-for="item in project.suggestedQuestions"
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
            <span class="message__meta-prefix">AGENT · {{ answerLabel(message) }}</span>
            <span class="message__meta-tags">
              <span v-if="answerScopeTag(message)" class="message__meta-tag" :data-scope="message.answer.answerScope">{{ answerScopeTag(message) }}</span>
              <span v-if="message.answer.generationMode !== 'FALLBACK' && answerVerificationTag(message)" class="message__meta-tag" :data-verification="message.answer.verification">{{ answerVerificationTag(message) }}</span>
              <span v-if="answerSourceTag(message)" class="message__meta-tag">{{ answerSourceTag(message) }}</span>
            </span>
            <span v-if="answerTechTail(message)" class="message__meta-tail">{{ answerTechTail(message) }}</span>
          </p>
          <p v-else class="message__meta">{{ message.role === 'AGENT' ? 'AGENT' : 'YOU' }}</p>
          <p
            v-if="message.answer && degradedNotice(message)"
            data-degraded-notice
            class="degraded-notice"
            role="status"
          >{{ degradedNotice(message) }}</p>
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
              <div v-if="dynamicSuggestions(message).length" class="dynamic-suggestions">
                <button
                  v-for="(q, qi) in dynamicSuggestions(message)"
                  :key="qi"
                  data-suggested-follow-up
                  type="button"
                  :disabled="pending"
                  @click="submitSuggested(q)"
                >{{ q.text }}</button>
              </div>
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
          AGENT · 正在核验证据
        </div>
        <div v-else-if="error" class="answer-state answer-state--error" role="alert">
          <p>{{ error }}</p>
          <div>
            <button data-answer-retry type="button" @click="$emit('retry')">重新回答</button>
            <button data-answer-edit type="button" @click="focusComposer">修改问题</button>
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
  grid-template-rows: auto 1fr;
  color: var(--workspace-text, var(--ink));
  background: var(--workspace-thread-bg, var(--paper-hi));
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

.conversation__head h1 {
  margin: 0;
  font: 500 22px var(--serif);
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
  min-height: 0;
  padding-bottom: 104px;
  overflow-y: auto;
}

.thread {
  width: min(820px, calc(100% - 96px));
  margin: 24px auto 40px;
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

.message--user .message__body {
  padding: 14px 18px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border-radius: 12px 12px 4px 12px;
  background: var(--workspace-primary-bg, var(--ink));
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
  right: 44px;
  bottom: 96px;
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
  position: absolute;
  right: 28px;
  bottom: 24px;
  left: 28px;
  display: flex;
  min-height: 62px;
  padding: 0 16px;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-md);
  background: color-mix(in srgb, var(--agent-shell-paper) 86%, white);
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
  outline: 0;
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
    right: 18px;
    left: 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .message,
  .structured-answer > section {
    transition: none;
  }
}
</style>
