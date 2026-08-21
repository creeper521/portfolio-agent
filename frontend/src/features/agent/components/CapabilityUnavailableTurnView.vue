<script setup lang="ts">
import type { CapabilityUnavailableTurn, SuggestedAction } from '../model/publicAgentTurn'
import SuggestedActionRow from './SuggestedActionRow.vue'

// D-38.15：CAPABILITY_UNAVAILABLE 返回原因、可重试性与恢复动作；
// retryable 用文字表达，不只靠颜色。

defineProps<{
  turn: CapabilityUnavailableTurn
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
}>()
</script>

<template>
  <div class="capability-unavailable-turn" data-testid="capability-unavailable-turn">
    <p class="capability-unavailable-turn__eyebrow">能力暂时不可用</p>
    <p class="capability-unavailable-turn__message" data-testid="turn-message">{{ turn.message }}</p>
    <p class="capability-unavailable-turn__code" data-testid="turn-code">{{ turn.code }}</p>
    <p v-if="turn.retryable !== undefined" class="capability-unavailable-turn__retryable" data-testid="turn-retryable">
      {{ turn.retryable ? '稍后可以重试' : '当前无法重试，请调整提问方式' }}
    </p>
    <p v-if="turn.retryAfterSeconds !== undefined" class="capability-unavailable-turn__retryable">
      约 {{ turn.retryAfterSeconds }} 秒后可重新提交
    </p>
    <SuggestedActionRow
      v-if="turn.suggestedActions !== undefined"
      :actions="turn.suggestedActions"
      @select="emit('select-action', $event)"
    />
  </div>
</template>

<style scoped>
.capability-unavailable-turn__eyebrow {
  margin: 0 0 6px;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}
.capability-unavailable-turn__message {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 15px/1.75 var(--serif);
  overflow-wrap: anywhere;
}
.capability-unavailable-turn__code {
  margin: 6px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
.capability-unavailable-turn__retryable {
  margin: 4px 0 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
</style>
