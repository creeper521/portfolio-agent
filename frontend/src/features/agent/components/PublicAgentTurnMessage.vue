<script setup lang="ts">
import type { SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload, PublicAgentTurn } from '../model/publicAgentTurn'
import type { ClarificationCardState } from './ClarificationChallengeForm.vue'
import type { ClarificationFallbackPreset } from './ClarificationTurnView.vue'
import AnswerTurnView from './AnswerTurnView.vue'
import BoundaryTurnView from './BoundaryTurnView.vue'
import CapabilityUnavailableTurnView from './CapabilityUnavailableTurnView.vue'
import ClarificationTurnView from './ClarificationTurnView.vue'
import ConversationalTurnView from './ConversationalTurnView.vue'

// 轮次消息的统一外观组件：按 PublicAgentTurn 的 kind（判别联合）
// 将只读渲染分发到 Answer/Clarification/Conversational/Boundary/
// CapabilityUnavailable 五种子视图。自身不请求数据、不持有本地状态，
// 仅做只读投影，并把用户动作（选择建议 action、提交澄清、追问）原样上抛给父组件。
// 前端直接消费闭合的 turn.kind 联合类型而不映射回旧 disposition，
// 未知附加字段已在 mapper 层按 additive evolution 忽略（D-38.18）。
// clarificationState 由会话消息流推导（ACTIVE/CONSUMED/SUPERSEDED），
// 描述同一条澄清挑战在会话中的当前状态（A2-18）；interactionDisabled
// 表示本会话有请求 pending，需统一禁用历史澄清表单以防并发提交。

defineProps<{
  turn: PublicAgentTurn
  interactionDisabled?: boolean
  clarificationState?: ClarificationCardState
  fallbackPresets?: readonly ClarificationFallbackPreset[]
  /** 五个模型不可用终局的双动作上下文（UI spec §2.6），由会话层判定提供。 */
  modelRecovery?: { failedModelName: string; otherModelName?: string }
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
  ask: [entry: ClarificationFallbackPreset]
  'retry-same-request': [requestId: string]
  'switch-model-reask': [requestId: string]
}>()
</script>

<template>
  <article class="public-turn-message" :data-turn-kind="turn.kind">
    <AnswerTurnView
      v-if="turn.kind === 'ANSWER'"
      :turn="turn"
      :interaction-disabled="interactionDisabled"
      :clarification-state="clarificationState"
      @select-action="emit('select-action', $event)"
      @submit-clarification="emit('submit-clarification', $event)"
    />
    <ClarificationTurnView
      v-else-if="turn.kind === 'CLARIFICATION'"
      :turn="turn"
      :disabled="interactionDisabled"
      :clarification-state="clarificationState"
      :fallback-presets="fallbackPresets"
      @submit-clarification="emit('submit-clarification', $event)"
      @select-action="emit('select-action', $event)"
      @ask="emit('ask', $event)"
    />
    <ConversationalTurnView
      v-else-if="turn.kind === 'CONVERSATIONAL'"
      :turn="turn"
      @select-action="emit('select-action', $event)"
    />
    <BoundaryTurnView
      v-else-if="turn.kind === 'BOUNDARY'"
      :turn="turn"
      @select-action="emit('select-action', $event)"
    />
    <CapabilityUnavailableTurnView
      v-else-if="turn.kind === 'CAPABILITY_UNAVAILABLE'"
      :turn="turn"
      :model-recovery="modelRecovery"
      @select-action="emit('select-action', $event)"
      @retry-same-request="emit('retry-same-request', $event)"
      @switch-model-reask="emit('switch-model-reask', $event)"
    />
  </article>
</template>

<style scoped>
.public-turn-message {
  overflow-wrap: anywhere;
}
</style>
