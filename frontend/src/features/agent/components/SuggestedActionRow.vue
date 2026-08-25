<script setup lang="ts">
import type { SuggestedAction } from '../model/publicAgentTurn'

// 建议动作按钮行：把后端下发的 SuggestedAction 列表渲染为一排可点按钮。
// 后端是业务动作的唯一权威，前端只原样转发 actionId/inputText/
// continuation 完整对象，绝不按按钮文案或出现位置重建动作协议（D-41.10）。
// 自身无状态；点击时把整个 action emit 给父组件。

defineProps<{
  actions: readonly SuggestedAction[]
}>()

const emit = defineEmits<{
  select: [action: SuggestedAction]
}>()
</script>

<template>
  <div v-if="actions.length > 0" class="suggested-action-row" data-testid="suggested-actions">
    <button
      v-for="action in actions"
      :key="action.actionId"
      class="suggested-action-row__action"
      type="button"
      :data-action-id="action.actionId"
      :data-has-continuation="action.continuation === undefined ? undefined : 'true'"
      @click="emit('select', action)"
    >{{ action.label }}</button>
  </div>
</template>

<style scoped>
.suggested-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.suggested-action-row__action {
  min-height: 34px;
  padding: 7px 14px;
  border: 1px solid var(--workspace-accent, var(--red));
  border-radius: var(--agent-radius-sm, 8px);
  background: transparent;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: background var(--agent-motion-fast, 160ms), color var(--agent-motion-fast, 160ms);
}
.suggested-action-row__action:hover { background: var(--workspace-accent, var(--red)); color: var(--paper-hi); }
.suggested-action-row__action:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) {
  .suggested-action-row__action { transition: none; }
}
</style>
