<script setup lang="ts">
import type { TaskSummaryItemView, TaskSummaryView } from '../model/semanticTurnView'
import { taskReasonText } from '../model/taskReasonLabels'

defineProps<{ summary: TaskSummaryView }>()

function statusLabel(status: TaskSummaryItemView['status']): string {
  switch (status) {
    case 'COMPLETED': return '已完成'
    case 'EVIDENCE_INSUFFICIENT':
    case 'NOT_SUPPORTED':
    case 'EMPTY': return '证据不足'
    case 'BLOCKED': return '被阻塞'
    case 'FAILED': return '未完成'
    case 'CANCELLED': return '已取消'
  }
}

function sourceLabel(sourceDomain: TaskSummaryItemView['sourceDomain']): string {
  if (sourceDomain === 'PORTFOLIO') return '作品集资料'
  if (sourceDomain === 'GENERAL') return '通用知识'
  return '综合结论'
}

// 非成功状态才展示原因行；成功是默认期待，不加噪音。
function reasonLabel(item: TaskSummaryItemView): string | null {
  return item.status === 'COMPLETED' ? null : taskReasonText(item)
}
</script>

<template>
  <section data-testid="task-status-summary" class="task-status-summary" aria-label="任务状态摘要">
    <ul>
      <li v-for="item in summary.items" :key="item.displayIndex" :data-task-status="item.status" :class="`task-status-summary--${item.status.toLowerCase()}`">
        <span class="task-status-summary__shape" aria-hidden="true"></span>
        <span class="task-status-summary__index">{{ item.displayIndex }}</span>
        <span class="task-status-summary__goal">
          {{ item.goalLabel }}
          <span v-if="reasonLabel(item)" class="task-status-summary__reason">{{ reasonLabel(item) }}</span>
        </span>
        <span class="task-status-summary__source" :data-source-label="item.sourceDomain">{{ sourceLabel(item.sourceDomain) }}</span>
        <span class="task-status-summary__label">{{ statusLabel(item.status) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.task-status-summary { margin-top: 10px; overflow-wrap: anywhere; }
.task-status-summary ul { display: grid; margin: 0; padding: 0; gap: 6px; list-style: none; }
.task-status-summary li { display: grid; grid-template-columns: 8px 30px minmax(0, 1fr) auto auto; align-items: center; gap: 7px; padding: 8px 10px; border: 1px solid var(--workspace-rule, var(--rule)); color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.5 var(--sans); }
.task-status-summary__shape { width: 7px; height: 7px; border: 1px solid currentcolor; border-radius: 50%; }
.task-status-summary__index { color: var(--workspace-text-faint, var(--faint)); font-family: var(--mono); }
.task-status-summary__goal { color: var(--workspace-text, var(--ink)); }
.task-status-summary__reason { display: block; margin-top: 2px; color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.5 var(--sans); }
.task-status-summary__source { color: var(--workspace-text-faint, var(--faint)); font: 10px var(--mono); white-space: nowrap; }
/* 来源可区分（spec §5 原则3）：作品集事实=红字，与通用知识/综合的默认灰字区分 */
.task-status-summary__source[data-source-label='PORTFOLIO'] { color: var(--workspace-accent, var(--red)); }
.task-status-summary__label { font: 10px var(--mono); letter-spacing: .04em; white-space: nowrap; }
.task-status-summary--completed .task-status-summary__shape { background: currentcolor; }
.task-status-summary--evidence_insufficient, .task-status-summary--not_supported, .task-status-summary--empty, .task-status-summary--blocked, .task-status-summary--failed { border-color: var(--workspace-accent, var(--red)); color: var(--workspace-accent, var(--red)); }
.task-status-summary--blocked .task-status-summary__shape, .task-status-summary--failed .task-status-summary__shape { transform: rotate(45deg); border-radius: 0; }
</style>
