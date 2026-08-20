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

// D-38.18：前端直接使用 discriminated union switch(turn.kind)，
// 不映射回 legacy disposition；未知附加字段已在 mapper 层忽略。
// A2-18：clarificationState 由会话消息流推导（ACTIVE/CONSUMED/SUPERSEDED）；
// interactionDisabled 表示本会话有请求 pending，统一禁用历史表单。

defineProps<{
  turn: PublicAgentTurn
  interactionDisabled?: boolean
  clarificationState?: ClarificationCardState
  fallbackPresets?: readonly ClarificationFallbackPreset[]
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
  ask: [entry: ClarificationFallbackPreset]
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
      @select-action="emit('select-action', $event)"
    />
  </article>
</template>

<style scoped>
.public-turn-message {
  overflow-wrap: anywhere;
}
</style>
