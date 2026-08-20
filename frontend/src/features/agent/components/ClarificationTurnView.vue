<script setup lang="ts">
import type { ClarificationTurn, SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload } from '../model/publicAgentTurn'
import type { ClarificationCardState } from './ClarificationChallengeForm.vue'
import ClarificationChallengeForm from './ClarificationChallengeForm.vue'
import SuggestedActionRow from './SuggestedActionRow.vue'

// D-38.13：Critical Clarification 是独立 Turn，无 answer/source/task/execution；
// 提交事件只携带 clarificationId + 闭合答案，由上层转为 RESOLVE_CLARIFICATION。
// 后端未提供 suggestedActions 时，以已发布 QuestionPreset 作为脱困入口；
// 叶子组件不自造业务问题，只渲染上层传入的已发布预设（§11 确认第 6 项）。

export interface ClarificationFallbackPreset {
  readonly text: string
  readonly presetId?: string
}

defineProps<{
  turn: ClarificationTurn
  disabled?: boolean
  clarificationState?: ClarificationCardState
  fallbackPresets?: readonly ClarificationFallbackPreset[]
}>()

const emit = defineEmits<{
  'submit-clarification': [payload: ClarificationSubmissionPayload]
  'select-action': [action: SuggestedAction]
  ask: [entry: ClarificationFallbackPreset]
}>()
</script>

<template>
  <section class="clarification-turn" data-testid="clarification-turn" aria-live="polite">
    <p class="clarification-turn__eyebrow">需要澄清</p>
    <ClarificationChallengeForm
      :challenge="turn.clarification"
      :disabled="disabled"
      :state="clarificationState"
      @submit="emit('submit-clarification', $event)"
    />
    <SuggestedActionRow
      v-if="turn.suggestedActions !== undefined && turn.suggestedActions.length > 0"
      :actions="turn.suggestedActions"
      @select="emit('select-action', $event)"
    />
    <div
      v-else-if="
        (clarificationState === undefined || clarificationState === 'ACTIVE')
          && fallbackPresets !== undefined && fallbackPresets.length > 0
      "
      class="clarification-turn__preset-fallback"
      data-testid="clarification-preset-fallback"
    >
      <p class="clarification-turn__fallback-hint">也可以先换个方向问：</p>
      <div class="clarification-turn__fallback-row">
        <button
          v-for="entry in fallbackPresets"
          :key="entry.presetId ?? entry.text"
          class="clarification-turn__fallback-entry"
          type="button"
          :data-fallback-preset="entry.presetId ?? entry.text"
          :disabled="disabled"
          @click="emit('ask', entry)"
        >{{ entry.text }}</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.clarification-turn__eyebrow {
  margin: 0 0 8px;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}
.clarification-turn__preset-fallback { margin-top: 10px; }
.clarification-turn__fallback-hint {
  margin: 0 0 6px;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
.clarification-turn__fallback-row { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.clarification-turn__fallback-entry {
  min-height: 28px;
  padding: 4px 10px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 999px;
  background: transparent;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.5 var(--sans);
  text-align: left;
  cursor: pointer;
}
.clarification-turn__fallback-entry:hover:not(:disabled) {
  border-color: var(--workspace-accent, var(--red));
  color: var(--workspace-accent, var(--red));
}
.clarification-turn__fallback-entry:disabled { opacity: 0.5; cursor: default; }
.clarification-turn__fallback-entry:focus-visible {
  outline: 2px solid var(--workspace-accent, var(--red));
  outline-offset: 2px;
}
</style>
