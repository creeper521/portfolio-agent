<script setup lang="ts">
import { computed, ref } from 'vue'

import type { ExecutionFinalStatus } from '../model/answerTypes'
import type { ExecutionDisplayPlanView } from '../model/semanticTurnView'

// P3 用户可见执行快照（FINAL，handoff §7）+ 2026-08-17 体验闭环（规格 §5）。
// 这是服务端确定性的最终阶段投影，不是实时进度：
//   - 不展示拟真的工具调用、命令日志、百分比或思维链；
//   - 映射层已保证不含 PENDING/IN_PROGRESS（违例时整个快照被丢弃）；
//   - 成功（COMPLETED）默认收起，PARTIAL/FAILED/SKIPPED 自动展开；
//   - 任务名（taskLabels 来自 completedTasks）优先于阶段重复，缺失不编造；
//   - 同一任务（displayIndex）只渲染一次；
//   - 不使用绿色状态色，状态靠文字表达。

const props = defineProps<{
  execution: ExecutionDisplayPlanView
  // displayIndex → goalLabel（由调用方从 completedTasks 构造）；只读映射。
  taskLabels?: Record<string, string>
}>()

const expanded = ref(props.execution.overallStatus !== 'COMPLETED')

const STATUS_LABELS: Record<ExecutionFinalStatus, string> = {
  COMPLETED: '完成',
  PARTIAL: '部分',
  SKIPPED: '跳过',
  FAILED: '失败',
}

const OVERALL_LABELS: Record<ExecutionFinalStatus, string> = {
  COMPLETED: '已完成',
  PARTIAL: '部分完成',
  SKIPPED: '已跳过',
  FAILED: '未完成',
}

// 同一任务只出现一次：按 displayIndex 稳定去重（防御性，正常响应不重复）。
const tasks = computed(() => {
  const seen = new Set<string>()
  return props.execution.tasks.filter((task) => {
    if (!task.displayIndex || seen.has(task.displayIndex)) return false
    seen.add(task.displayIndex)
    return true
  })
})

const overallLabel = computed(
  () => OVERALL_LABELS[props.execution.overallStatus] ?? props.execution.overallStatus,
)

const collapsedLabel = computed(() => {
  const taskPart = tasks.value.length > 1 ? `${tasks.value.length} 项任务 · ` : ''
  return `${taskPart}${overallLabel.value}`
})

function taskLabel(displayIndex: string): string {
  return props.taskLabels?.[displayIndex] ?? ''
}

function toggleExpanded() {
  expanded.value = !expanded.value
}
</script>

<template>
  <section
    class="execution-snapshot"
    data-execution-snapshot
    :data-execution-overall="execution.overallStatus"
    :class="`execution-snapshot--${execution.overallStatus.toLowerCase()}`"
    aria-label="执行概览"
  >
    <button
      data-execution-toggle
      type="button"
      class="execution-snapshot__bar"
      :aria-expanded="expanded"
      @click="toggleExpanded"
    >
      <span class="execution-snapshot__eyebrow">执行概览</span>
      <span class="execution-snapshot__summary">{{ collapsedLabel }}</span>
      <span class="execution-snapshot__chev" aria-hidden="true">{{ expanded ? '−' : '+' }}</span>
    </button>
    <ol v-if="expanded" class="execution-snapshot__tasks">
      <li
        v-for="task in tasks"
        :key="task.displayIndex"
        class="execution-snapshot__task"
        :data-execution-task="task.displayIndex"
        :data-task-final-status="task.finalStatus"
      >
        <p class="execution-snapshot__task-head">
          <span class="execution-snapshot__task-no">{{ task.displayIndex }}</span>
          <span class="execution-snapshot__task-name">{{ taskLabel(task.displayIndex) }}</span>
          <span class="execution-snapshot__task-status">{{ STATUS_LABELS[task.finalStatus] ?? task.finalStatus }}</span>
        </p>
        <ul class="execution-snapshot__stages">
          <li
            v-for="stage in task.stages"
            :key="stage.code"
            class="execution-snapshot__stage"
            :data-stage-code="stage.code"
            :data-stage-status="stage.status"
          >
            <span class="execution-snapshot__stage-label">{{ stage.label }}</span>
            <span class="execution-snapshot__stage-status">{{ STATUS_LABELS[stage.status] ?? stage.status }}</span>
          </li>
        </ul>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.execution-snapshot {
  margin: 0 0 0.5rem;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 8px;
  background: var(--workspace-surface-subtle, rgba(0, 0, 0, 0.03));
  font-size: 0.8125rem;
  overflow: hidden;
}
/* 异常态（非 COMPLETED）边线转暗红，提示但不只用颜色表达（文字状态恒在）。 */
.execution-snapshot--partial,
.execution-snapshot--failed {
  border-color: var(--workspace-accent, var(--red));
}
.execution-snapshot__bar {
  display: flex;
  width: 100%;
  min-height: 38px;
  padding: 9px 12px;
  align-items: baseline;
  gap: 10px;
  color: var(--workspace-text-secondary, var(--muted));
  border: 0;
  background: transparent;
  font: 11px var(--mono);
  letter-spacing: 0.04em;
  cursor: pointer;
  text-align: left;
}
.execution-snapshot__bar:focus-visible {
  outline: 2px solid var(--workspace-accent, var(--red));
  outline-offset: -2px;
}
.execution-snapshot__eyebrow {
  flex-shrink: 0;
  color: var(--workspace-accent, var(--red));
  letter-spacing: 0.1em;
}
.execution-snapshot--partial .execution-snapshot__eyebrow,
.execution-snapshot--failed .execution-snapshot__eyebrow {
  color: var(--workspace-accent, var(--red));
}
.execution-snapshot__summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--workspace-text, var(--ink-2));
}
.execution-snapshot--partial .execution-snapshot__summary,
.execution-snapshot--failed .execution-snapshot__summary {
  color: var(--workspace-accent, var(--red));
}
.execution-snapshot__chev {
  flex-shrink: 0;
}
.execution-snapshot__tasks {
  margin: 0;
  padding: 0 10px 10px;
  list-style: none;
}
.execution-snapshot__task {
  padding: 6px 2px;
  border-top: 1px dotted var(--workspace-rule, var(--rule));
}
.execution-snapshot__task-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  margin: 0 0 4px;
}
.execution-snapshot__task-no {
  color: var(--workspace-accent-soft, var(--red-hi));
  font-family: var(--mono);
  font-size: 10.5px;
}
.execution-snapshot__task-name {
  flex: 1;
  min-width: 0;
  color: var(--workspace-text, var(--ink));
  font: 11.5px/1.5 var(--sans);
}
.execution-snapshot__task-status {
  font: 10px var(--mono);
  letter-spacing: 0.04em;
  white-space: nowrap;
  color: var(--workspace-text-secondary, var(--muted));
}
.execution-snapshot__task[data-task-final-status='PARTIAL'] .execution-snapshot__task-status,
.execution-snapshot__task[data-task-final-status='FAILED'] .execution-snapshot__task-status {
  color: var(--workspace-accent, var(--red));
}
.execution-snapshot__stages {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.execution-snapshot__stage {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--workspace-text-secondary, inherit);
}
.execution-snapshot__stage-status {
  padding: 0 0.3rem;
  border-radius: 4px;
  font-size: 0.6875rem;
  background: rgba(0, 0, 0, 0.06);
  color: var(--workspace-text-secondary, var(--ink-2));
}
.execution-snapshot__stage[data-stage-status='FAILED'] .execution-snapshot__stage-status {
  color: var(--workspace-accent, var(--red));
}
.execution-snapshot__stage[data-stage-status='PARTIAL'] .execution-snapshot__stage-status {
  color: var(--workspace-accent-soft, var(--red-hi));
}
@media (prefers-reduced-motion: reduce) {
  .execution-snapshot,
  .execution-snapshot__stage {
    transition: none;
    animation: none;
  }
}
</style>
