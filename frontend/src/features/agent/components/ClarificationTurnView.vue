<script setup lang="ts">
import type { ClarificationTurn, SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload } from '../model/publicAgentTurn'
import ClarificationChallengeForm from './ClarificationChallengeForm.vue'
import SuggestedActionRow from './SuggestedActionRow.vue'

// D-38.13：Critical Clarification 是独立 Turn，无 answer/source/task/execution；
// 提交事件只携带 clarificationId + 闭合答案，由上层转为 RESOLVE_CLARIFICATION。

defineProps<{
  turn: ClarificationTurn
  disabled?: boolean
}>()

const emit = defineEmits<{
  'submit-clarification': [payload: ClarificationSubmissionPayload]
  'select-action': [action: SuggestedAction]
}>()
</script>

<template>
  <section class="clarification-turn" data-testid="clarification-turn" aria-live="polite">
    <p class="clarification-turn__eyebrow">需要澄清</p>
    <ClarificationChallengeForm
      :challenge="turn.clarification"
      :disabled="disabled"
      @submit="emit('submit-clarification', $event)"
    />
    <SuggestedActionRow
      v-if="turn.suggestedActions !== undefined && turn.suggestedActions.length > 0"
      :actions="turn.suggestedActions"
      @select="emit('select-action', $event)"
    />
  </section>
</template>

<style scoped>
.clarification-turn__eyebrow {
  margin: 0 0 8px;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}
</style>
