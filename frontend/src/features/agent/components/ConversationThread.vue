<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload, PublicAgentTurn } from '../model/publicAgentTurn'
import type { AgentMessage } from '../model/sessionTypes'
import type { ClarificationCardState } from './ClarificationChallengeForm.vue'
import type { ClarificationFallbackPreset } from './ClarificationTurnView.vue'
import PublicAgentTurnMessage from './PublicAgentTurnMessage.vue'

// D-41.14：ConversationThread 只负责消息列表、scroll、focus、pending 与事件转发；
// 业务投影全部在 PublicAgentTurnMessage 组件树内，不在此重算任何业务语义。
// B6：滚动纪律——用户上滑即暂停自动跟随，浮现"回到最新回答"；回到底部附近恢复跟随。
// B7：focusTarget 定位回答内 section 并短暂高亮。
// A2-18：只有会话中最新、未提交、仍有效的澄清卡可操作，其余转只读。

const props = defineProps<{
  messages: readonly AgentMessage[]
  pending: boolean
  pendingQuestion: string
  focusTarget?: { sectionId: string; nonce: number } | null
  fallbackPresets?: readonly ClarificationFallbackPreset[]
}>()

const emit = defineEmits<{
  cancel: []
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
  ask: [entry: ClarificationFallbackPreset]
}>()

const listElement = ref<HTMLElement | null>(null)
const followLatest = ref(true)
const showJumpLatest = ref(false)
const NEAR_BOTTOM_PX = 80
const LOCATE_HIGHLIGHT_MS = 2400
let locateTimer: ReturnType<typeof setTimeout> | null = null

function prefersReducedMotion(): boolean {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

function onScroll(): void {
  const element = listElement.value
  if (element === null) return
  const distance = element.scrollHeight - element.scrollTop - element.clientHeight
  const nearBottom = distance <= NEAR_BOTTOM_PX
  followLatest.value = nearBottom
  showJumpLatest.value = !nearBottom && props.messages.length > 0
}

watch(
  () => [props.messages.length, props.pending] as const,
  async () => {
    await nextTick()
    if (!followLatest.value) return
    const element = listElement.value
    if (element !== null) {
      element.scrollTop = element.scrollHeight
    }
  },
)

function jumpToLatest(): void {
  const element = listElement.value
  if (element === null) return
  if (typeof element.scrollTo === 'function') {
    element.scrollTo({
      top: element.scrollHeight,
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    })
  } else {
    element.scrollTop = element.scrollHeight
  }
  followLatest.value = true
  showJumpLatest.value = false
}

function cssEscape(value: string): string {
  return globalThis.CSS?.escape?.(value) ?? value.replace(/["\\]/g, '\\$&')
}

watch(
  () => props.focusTarget,
  async (target) => {
    if (target === null || target === undefined) return
    await nextTick()
    const element = listElement.value?.querySelector<HTMLElement>(
      `[data-section-id="${cssEscape(target.sectionId)}"]`,
    )
    if (element === null || element === undefined) return
    element.scrollIntoView?.({
      block: 'center',
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    })
    element.classList.add('conversation-thread--located')
    element.setAttribute('data-located-nonce', String(target.nonce))
    if (locateTimer !== null) clearTimeout(locateTimer)
    locateTimer = setTimeout(() => {
      element.classList.remove('conversation-thread--located')
    }, LOCATE_HIGHLIGHT_MS)
  },
)

onBeforeUnmount(() => {
  if (locateTimer !== null) clearTimeout(locateTimer)
})

function clarificationChallengeIdOf(turn: PublicAgentTurn | undefined): string | null {
  if (turn === undefined) return null
  if (turn.kind === 'CLARIFICATION') return turn.clarification.clarificationId
  if (turn.kind === 'ANSWER' && turn.answer.localClarification !== undefined) {
    return turn.answer.localClarification.clarificationId
  }
  return null
}

/** 会话内最新、未提交的澄清挑战消息 id；没有则无活跃卡（A2-18）。 */
const activeClarificationMessageId = computed(() => {
  for (let index = props.messages.length - 1; index >= 0; index -= 1) {
    const message = props.messages[index]
    if (message === undefined || clarificationChallengeIdOf(message.turn) === null) continue
    return message.clarificationConsumed ? null : message.id
  }
  return null
})

function clarificationStateOf(message: AgentMessage): ClarificationCardState | undefined {
  if (clarificationChallengeIdOf(message.turn) === null) return undefined
  if (message.clarificationConsumed) return 'CONSUMED'
  return message.id === activeClarificationMessageId.value ? 'ACTIVE' : 'SUPERSEDED'
}
</script>

<template>
  <div class="conversation-thread-shell">
    <div
      ref="listElement"
      class="conversation-thread"
      data-testid="conversation-thread"
      @scroll.passive="onScroll"
    >
      <div
        v-if="messages.length === 0 && !pending"
        class="conversation-thread__empty"
        data-conversation-state="empty"
      >
        <p class="conversation-thread__empty-title">从一次提问开始</p>
        <p class="conversation-thread__empty-hint">可点击下方建议问题，或直接输入你的问题。</p>
      </div>
      <ol class="conversation-thread__list" aria-label="对话记录">
        <li
          v-for="message in messages"
          :key="message.id"
          class="conversation-thread__item"
          :data-message-role="message.role"
          :data-message-failed="message.failed ? 'true' : undefined"
        >
          <p v-if="message.role === 'USER'" class="conversation-thread__question">{{ message.content }}</p>
          <template v-else>
            <p class="conversation-thread__agent-head" aria-hidden="true">AGENT</p>
            <PublicAgentTurnMessage
              v-if="message.turn !== undefined"
              :turn="message.turn"
              :interaction-disabled="pending"
              :clarification-state="clarificationStateOf(message)"
              :fallback-presets="fallbackPresets"
              @select-action="emit('select-action', $event)"
              @submit-clarification="emit('submit-clarification', $event)"
              @ask="emit('ask', $event)"
            />
          </template>
        </li>
      </ol>
      <div v-if="pending" class="conversation-thread__pending" role="status" data-testid="conversation-pending">
        <p class="conversation-thread__pending-text">正在处理<span v-if="pendingQuestion">：{{ pendingQuestion }}</span>…</p>
        <button
          class="conversation-thread__cancel"
          type="button"
          data-testid="cancel-turn"
          @click="emit('cancel')"
        >取消回答</button>
      </div>
    </div>
    <button
      v-if="showJumpLatest"
      class="conversation-thread__jump"
      type="button"
      data-testid="jump-latest"
      @click="jumpToLatest"
    >回到最新回答</button>
  </div>
</template>

<style scoped>
.conversation-thread-shell {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.conversation-thread {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
  padding: 18px clamp(14px, 2.4vw, 26px);
}
/* B5：空会话引导区垂直居中，消除顶部死空 */
.conversation-thread__empty[data-conversation-state='empty'] { margin-block: auto; }
.conversation-thread__empty-title {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 500 15px/1.6 var(--serif);
}
.conversation-thread__empty-hint {
  margin: 6px 0 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.7 var(--mono);
}
.conversation-thread__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.conversation-thread__item { margin: 0 0 20px; }
.conversation-thread__item:last-child { margin-bottom: 0; }
/* 07-22 设计：用户问题用自然文本流 + 2px accent 左线，不使用实心消息气泡 */
.conversation-thread__question {
  margin: 0 0 0 auto;
  max-width: 86%;
  width: fit-content;
  padding-left: 12px;
  border-left: 2px solid var(--workspace-accent, var(--red));
  color: var(--workspace-text, var(--ink));
  font: 14px/1.7 var(--sans);
  overflow-wrap: anywhere;
  white-space: pre-line;
}
/* A2-04：失败/取消的 USER 轮次降为弱化展示，仍可见但不进入会话窗口。 */
.conversation-thread__item[data-message-failed='true'] .conversation-thread__question {
  color: var(--workspace-text-faint, var(--faint));
  text-decoration: line-through;
}
.conversation-thread__agent-head {
  margin: 0 0 6px;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px var(--mono);
  letter-spacing: 0.12em;
}
.conversation-thread__pending {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}
.conversation-thread__pending-text {
  margin: 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
.conversation-thread__cancel {
  min-height: 30px;
  padding: 5px 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  background: transparent;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10px var(--mono);
  letter-spacing: 0.06em;
  cursor: pointer;
}
.conversation-thread__cancel:hover { border-color: var(--workspace-accent, var(--red)); color: var(--workspace-accent, var(--red)); }
.conversation-thread__cancel:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 2px; }
.conversation-thread__jump {
  position: absolute;
  right: 22px;
  bottom: 14px;
  z-index: 2;
  min-height: 32px;
  padding: 6px 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 999px;
  background: var(--paper-hi, #fff);
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px var(--mono);
  letter-spacing: 0.06em;
  box-shadow: 0 2px 8px rgba(32, 28, 23, 0.12);
  cursor: pointer;
}
.conversation-thread__jump:hover { border-color: var(--workspace-accent, var(--red)); color: var(--workspace-accent, var(--red)); }
.conversation-thread__jump:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 2px; }
/* B7：定位高亮作用于子组件渲染的 section 元素。 */
.conversation-thread :deep(.conversation-thread--located) {
  outline: 2px solid var(--workspace-accent, var(--red));
  outline-offset: 6px;
  border-radius: 2px;
}
@media (prefers-reduced-motion: reduce) {
  .conversation-thread__jump { transition: none; }
}
</style>
