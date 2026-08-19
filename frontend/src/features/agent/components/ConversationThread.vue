<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import type { SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload } from '../model/publicAgentTurn'
import type { AgentMessage } from '../model/sessionTypes'
import PublicAgentTurnMessage from './PublicAgentTurnMessage.vue'

// D-41.14：ConversationThread 只负责消息列表、scroll、focus、pending 与事件转发；
// 业务投影全部在 PublicAgentTurnMessage 组件树内，不在此重算任何业务语义。

const props = defineProps<{
  messages: readonly AgentMessage[]
  pending: boolean
  pendingQuestion: string
}>()

const emit = defineEmits<{
  cancel: []
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
}>()

const listElement = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.pending] as const,
  async () => {
    await nextTick()
    const element = listElement.value
    if (element !== null) {
      element.scrollTop = element.scrollHeight
    }
  },
)
</script>

<template>
  <div ref="listElement" class="conversation-thread" data-testid="conversation-thread">
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
      >
        <p v-if="message.role === 'USER'" class="conversation-thread__question">{{ message.content }}</p>
        <template v-else>
          <p class="conversation-thread__agent-head" aria-hidden="true">AGENT</p>
          <PublicAgentTurnMessage
            v-if="message.turn !== undefined"
            :turn="message.turn"
            @select-action="emit('select-action', $event)"
            @submit-clarification="emit('submit-clarification', $event)"
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
</template>

<style scoped>
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
</style>
