<script setup lang="ts">
import type { BoundaryTurn, SuggestedAction } from '../model/publicAgentTurn'
import SuggestedActionRow from './SuggestedActionRow.vue'

// D-38.15：BOUNDARY 返回原因与可恢复动作；code 为后端冻结稳定公共码，
// 前端不翻译内部 reason，只原样展示稳定码供诊断对照。

defineProps<{
  turn: BoundaryTurn
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
}>()
</script>

<template>
  <div class="boundary-turn" data-testid="boundary-turn">
    <p class="boundary-turn__eyebrow">能力边界</p>
    <p class="boundary-turn__message" data-testid="turn-message">{{ turn.message }}</p>
    <p class="boundary-turn__code" data-testid="turn-code">{{ turn.code }}</p>
    <SuggestedActionRow
      v-if="turn.suggestedActions !== undefined"
      :actions="turn.suggestedActions"
      @select="emit('select-action', $event)"
    />
  </div>
</template>

<style scoped>
.boundary-turn__eyebrow {
  margin: 0 0 6px;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}
.boundary-turn__message {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 15px/1.75 var(--serif);
  overflow-wrap: anywhere;
}
.boundary-turn__code {
  margin: 6px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
</style>
