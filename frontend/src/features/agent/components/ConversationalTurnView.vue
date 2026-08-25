<script setup lang="ts">
import type { ConversationalTurn, SuggestedAction } from '../model/publicAgentTurn'
import SuggestedActionRow from './SuggestedActionRow.vue'

// CONVERSATIONAL 轮次视图：最简轮次形态，只渲染纯文本 message
// （pre-line 保留换行）与可选建议动作。该 kind 按公开合同不携带
// answer/goal/source 等结构化正文；自身无状态，仅转发 select-action（D-38.15）。

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
