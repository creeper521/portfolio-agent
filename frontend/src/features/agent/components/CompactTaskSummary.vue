<script setup lang="ts">
import { computed, ref } from 'vue'

import type { TaskSummaryView } from '../model/semanticTurnView'
import TaskStatusSummary from './TaskStatusSummary.vue'

const props = defineProps<{ summary: TaskSummaryView }>()

const expanded = ref(props.summary.displayMode === 'EXPANDED' || hasNonSuccess(props.summary))
const completedLabel = computed(() => `${props.summary.answeredCount}/${props.summary.totalCount} 完成`)

function hasNonSuccess(summary: TaskSummaryView): boolean {
  return summary.notSupportedCount + summary.emptyCount + summary.blockedCount + summary.failedCount + summary.cancelledCount + summary.degradedCount > 0
}
</script>

<template>
  <section data-testid="task-summary" class="compact-task-summary" :data-expanded="String(expanded)">
    <button
      data-testid="task-summary-toggle"
      type="button"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >任务进度 · {{ completedLabel }} <span aria-hidden="true">{{ expanded ? '−' : '+' }}</span></button>
    <TaskStatusSummary v-if="expanded" :summary="summary" />
  </section>
</template>

<style scoped>
.compact-task-summary { margin: 16px 0; overflow: hidden; border: 1px solid var(--workspace-rule, var(--rule)); }
.compact-task-summary > button { display: flex; width: 100%; min-height: 38px; padding: 9px 12px; align-items: center; justify-content: space-between; color: var(--workspace-text-secondary, var(--muted)); border: 0; background: var(--workspace-surface-subtle, var(--paper-low)); font: 11px var(--mono); letter-spacing: .04em; cursor: pointer; }
.compact-task-summary > button:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: -2px; }
.compact-task-summary :deep(.task-status-summary) { padding: 0 10px 10px; }
</style>
