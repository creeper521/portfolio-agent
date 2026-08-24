<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload, PublicAgentTurn } from '../model/publicAgentTurn'
import type { AgentMessage, AgentThreadNotice } from '../model/sessionTypes'
import type { ClarificationCardState } from './ClarificationChallengeForm.vue'
import type { ClarificationFallbackPreset } from './ClarificationTurnView.vue'
import PublicAgentTurnMessage from './PublicAgentTurnMessage.vue'

// 会话消息列表（工作台主栏中部）：只负责消息流渲染、滚动跟随/定位、
// pending 指示与用户事件转发；所有业务投影都在 PublicAgentTurnMessage
// 组件树内完成，本组件不重算任何业务语义（D-41.14）。
// 滚动纪律：用户上滑离开底部即暂停自动跟随并浮现"回到最新回答"按钮，
// 回到底部附近自动恢复跟随（B6）。
// focusTarget（sectionId + nonce）用于"定位"来源引用：滚动到回答内
// 对应 section 并短暂高亮（B7）。
// 澄清卡状态推导：只有会话中最新、未提交、仍有效的澄清卡可操作，
// 其余一律转只读（A2-18）。
// 数据来自 props（消息列表是父组件页面内存会话的只读投影）；本地状态
// 仅滚动跟随开关、跳转按钮可见性与定位高亮计时器；emit cancel /
// select-action / submit-clarification / ask 四种动作。

const props = defineProps<{
  messages: readonly AgentMessage[]
  pending: boolean
  pendingQuestion: string
  focusTarget?: { sectionId: string; nonce: number } | null
  fallbackPresets?: readonly ClarificationFallbackPreset[]
  /** 会话内展示通知（UI spec §2.4/§2.9）：与消息按时间交错渲染，不进会话窗口。 */
  notices?: readonly AgentThreadNotice[]
  /** 回答模型标识（UI spec §2.5）：由会话层按 modelExecution 投影提供，前端不推断。 */
  modelTagOf?: (turn: PublicAgentTurn) => string | null
  /** 五个模型不可用终局的双动作上下文（UI spec §2.6），按消息判定提供。 */
  modelRecoveryOf?: (turn: PublicAgentTurn) => { failedModelName: string; otherModelName?: string } | undefined
}>()

const emit = defineEmits<{
  cancel: []
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
  ask: [entry: ClarificationFallbackPreset]
  'retry-same-request': [requestId: string]
  'switch-model-reask': [requestId: string]
}>()

/** 消息与通知按 createdAt 交错合并；同毫秒内消息先于通知，保证切换通知落在轮次之间。 */
const timeline = computed(() => {
  const rankOf = (kind: 'message' | 'notice') => (kind === 'message' ? 0 : 1)
  return [
    ...props.messages.map((entry) => ({ kind: 'message' as const, at: entry.createdAt, entry })),
    ...(props.notices ?? []).map((entry) => ({ kind: 'notice' as const, at: entry.createdAt, entry })),
  ].sort((left, right) => left.at - right.at || rankOf(left.kind) - rankOf(right.kind))
})

const listElement = ref<HTMLElement | null>(null)
// 自动跟随开关：距底部足够近时为 true，新消息到达即滚到底。
const followLatest = ref(true)
const showJumpLatest = ref(false)
// 距底部不超过该距离即视为"在底部"，用于恢复自动跟随。
const NEAR_BOTTOM_PX = 80
// "定位"命中的 section 高亮持续时间。
const LOCATE_HIGHLIGHT_MS = 2400
let locateTimer: ReturnType<typeof setTimeout> | null = null

/** 用户偏好减少动效时，滚动/定位一律瞬时完成，不做平滑动画。 */
function prefersReducedMotion(): boolean {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

/** 滚动纪律的判定点：按距底部距离切换自动跟随，并决定是否浮现"回到最新回答"。 */
function onScroll(): void {
  const element = listElement.value
  if (element === null) return
  const distance = element.scrollHeight - element.scrollTop - element.clientHeight
  const nearBottom = distance <= NEAR_BOTTOM_PX
  followLatest.value = nearBottom
  showJumpLatest.value = !nearBottom && props.messages.length > 0
}

// 新消息/pending 变化后自动滚到底，但仅在用户仍处于跟随态时执行，
// 避免打断用户回看历史。
watch(
  () => [props.messages.length, props.notices?.length ?? 0, props.pending] as const,
  async () => {
    await nextTick()
    if (!followLatest.value) return
    const element = listElement.value
    if (element !== null) {
      element.scrollTop = element.scrollHeight
    }
  },
)

/** 手动回到底部：按用户动效偏好选择平滑/瞬时滚动，并恢复自动跟随。 */
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

// sectionId 可能含特殊字符，选择器属性值需转义；环境无 CSS.escape 时退回手工转义。
function cssEscape(value: string): string {
  return globalThis.CSS?.escape?.(value) ?? value.replace(/["\\]/g, '\\$&')
}

/**
 * "定位"处理：等 DOM 稳定后按 data-section-id 找到目标 section，滚动到
 * 视口中央并加高亮类，超时自动撤销。nonce 保证重复定位同一 section 时
* watch 仍会触发；定时器先清再设，避免多次定位叠加高亮。
 */
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

// 组件卸载时清理高亮定时器，避免过期回调操作已移除的 DOM。
onBeforeUnmount(() => {
  if (locateTimer !== null) clearTimeout(locateTimer)
})

/** 提取消息所携带澄清挑战的 clarificationId；CLARIFICATION 与 ANSWER 内嵌局部澄清两种形态都识别，无则返回 null。 */
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

/**
 * 推导某条消息上澄清卡的状态：已提交为 CONSUMED；是会话中最新且未提交
 * 的挑战为 ACTIVE；其余（更早的未提交挑战）一律 SUPERSEDED 转只读。
 */
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
        <template v-for="item in timeline" :key="item.entry.id">
          <li
            v-if="item.kind === 'notice'"
            class="conversation-thread__item conversation-thread__item--notice"
            :data-notice-kind="item.entry.kind"
          >
            <div class="conversation-thread__notice" data-testid="model-notice" role="status">
              <p class="conversation-thread__notice-title">{{ item.entry.title }}</p>
              <p v-if="item.entry.detail !== undefined" class="conversation-thread__notice-detail">
                {{ item.entry.detail }}
              </p>
            </div>
          </li>
          <li
            v-else
            class="conversation-thread__item"
            :data-message-role="item.entry.role"
            :data-message-failed="item.entry.failed ? 'true' : undefined"
          >
            <p v-if="item.entry.role === 'USER'" class="conversation-thread__question">{{ item.entry.content }}</p>
            <template v-else>
              <p class="conversation-thread__agent-head" aria-hidden="true">
                AGENT<span
                  v-if="item.entry.turn !== undefined && modelTagOf !== undefined && modelTagOf(item.entry.turn) !== null"
                  class="conversation-thread__model-tag"
                  data-testid="model-tag"
                >{{ modelTagOf(item.entry.turn!) }}</span>
              </p>
              <PublicAgentTurnMessage
                v-if="item.entry.turn !== undefined"
                :turn="item.entry.turn"
                :interaction-disabled="pending"
                :clarification-state="clarificationStateOf(item.entry)"
                :fallback-presets="fallbackPresets"
                :model-recovery="modelRecoveryOf !== undefined ? modelRecoveryOf(item.entry.turn) : undefined"
                @select-action="emit('select-action', $event)"
                @submit-clarification="emit('submit-clarification', $event)"
                @ask="emit('ask', $event)"
                @retry-same-request="emit('retry-same-request', $event)"
                @switch-model-reask="emit('switch-model-reask', $event)"
              />
            </template>
          </li>
        </template>
      </ol>
      <!-- pending 指示：等待中的轮次可取消；role=status 供读屏感知状态变化 -->
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
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 0 0 6px;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px var(--mono);
  letter-spacing: 0.12em;
}
/* 回答模型标识（UI spec §2.5）：只反映该轮 modelExecution 投影，不由前端推断。 */
.conversation-thread__model-tag {
  color: var(--workspace-text-secondary, var(--muted));
  letter-spacing: 0.06em;
}
/* 切换/stale 通知卡（UI spec §2.4/§2.9）：纯展示，不产生 Turn、不进会话窗口。 */
.conversation-thread__notice {
  padding: 10px 13px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  background: var(--paper-hi, #fff);
}
.conversation-thread__notice-title {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 13px/1.6 var(--sans);
}
.conversation-thread__notice-detail {
  margin: 3px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10.5px/1.6 var(--mono);
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
