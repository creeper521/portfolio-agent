<script setup lang="ts">
import { computed, ref } from 'vue'

import type { TaskSummaryView } from '../model/semanticTurnView'
import TaskStatusSummary from './TaskStatusSummary.vue'

const props = defineProps<{ summary: TaskSummaryView }>()

const expanded = ref(props.summary.displayMode === 'EXPANDED' || hasNonSuccess(props.summary))

const allSucceeded = computed(() => !hasNonSuccess(props.summary))

// U01-C：全成功折叠态展示执行流程箭头；非全成功折叠态展示计数与异常摘要，
// 让部分失败在折叠态仍可感知。
const collapsedLabel = computed(() => {
  if (allSucceeded.value) {
    const flow = props.summary.items.map((item) => item.goalLabel).join(' → ')
    return `${flow} · ${props.summary.totalCount} 步已完成`
  }
  const segments = [`${props.summary.answeredCount}/${props.summary.totalCount} 完成`]
  const notSupported = props.summary.notSupportedCount + props.summary.emptyCount
  if (notSupported > 0) segments.push(`${notSupported} 证据不足`)
  if (props.summary.blockedCount > 0) segments.push(`${props.summary.blockedCount} 阻塞`)
  if (props.summary.failedCount > 0) segments.push(`${props.summary.failedCount} 未完成`)
  if (props.summary.cancelledCount > 0) segments.push(`${props.summary.cancelledCount} 已取消`)
  return segments.join(' · ')
})

const eyebrow = computed(() => (allSucceeded.value ? '任务摘要' : '任务状态'))

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
    >
      <span class="compact-task-summary__eyebrow">{{ eyebrow }}</span>
      <span class="compact-task-summary__label">{{ collapsedLabel }}</span>
      <span class="compact-task-summary__chev" aria-hidden="true">{{ expanded ? '−' : '+' }}</span>
    </button>
    <TaskStatusSummary v-if="expanded" :summary="summary" />
  </section>
</template>

<style scoped>
.compact-task-summary { margin: 16px 0; overflow: hidden; border: 1px solid var(--workspace-rule, var(--rule)); }
.compact-task-summary > button { display: flex; width: 100%; min-height: 38px; padding: 9px 12px; align-items: baseline; gap: 10px; color: var(--workspace-text-secondary, var(--muted)); border: 0; background: var(--workspace-surface-subtle, var(--paper-low)); font: 11px var(--mono); letter-spacing: .04em; cursor: pointer; text-align: left; }
.compact-task-summary > button:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: -2px; }
.compact-task-summary__eyebrow { color: var(--workspace-accent, var(--red)); letter-spacing: .1em; flex-shrink: 0; }
.compact-task-summary__label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.compact-task-summary__chev { flex-shrink: 0; }
.compact-task-summary :deep(.task-status-summary) { padding: 0 10px 10px; }
</style>
