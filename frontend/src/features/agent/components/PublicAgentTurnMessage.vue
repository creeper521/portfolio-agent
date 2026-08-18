<script setup lang="ts">
import type { SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload, PublicAgentTurn } from '../model/publicAgentTurn'
import AnswerTurnView from './AnswerTurnView.vue'
import BoundaryTurnView from './BoundaryTurnView.vue'
import CapabilityUnavailableTurnView from './CapabilityUnavailableTurnView.vue'
import ClarificationTurnView from './ClarificationTurnView.vue'
import ConversationalTurnView from './ConversationalTurnView.vue'

// D-38.18：前端直接使用 discriminated union switch(turn.kind)，
// 不映射回 legacy disposition；未知附加字段已在 mapper 层忽略。

defineProps<{
  turn: PublicAgentTurn
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
}>()
</script>

<template>
  <article class="public-turn-message" :data-turn-kind="turn.kind">
    <AnswerTurnView
      v-if="turn.kind === 'ANSWER'"
      :turn="turn"
      @select-action="emit('select-action', $event)"
      @submit-clarification="emit('submit-clarification', $event)"
    />
    <ClarificationTurnView
      v-else-if="turn.kind === 'CLARIFICATION'"
      :turn="turn"
      @submit-clarification="emit('submit-clarification', $event)"
      @select-action="emit('select-action', $event)"
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
