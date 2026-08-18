<script setup lang="ts">
import type { ConversationalTurn, SuggestedAction } from '../model/publicAgentTurn'
import SuggestedActionRow from './SuggestedActionRow.vue'

// D-38.15：CONVERSATIONAL 只保留 message 与 suggestedActions，不携带 answer/goal/source。

defineProps<{
  turn: ConversationalTurn
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
}>()
</script>

<template>
  <div class="conversational-turn" data-testid="conversational-turn">
    <p class="conversational-turn__message" data-testid="turn-message">{{ turn.message }}</p>
    <SuggestedActionRow
      v-if="turn.suggestedActions !== undefined"
      :actions="turn.suggestedActions"
      @select="emit('select-action', $event)"
    />
  </div>
</template>

<style scoped>
.conversational-turn__message {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 15px/1.75 var(--serif);
  white-space: pre-line;
  overflow-wrap: anywhere;
}
</style>
