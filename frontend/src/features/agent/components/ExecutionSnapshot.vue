<script setup lang="ts">
import type { ExecutionFinalStatus } from '../model/answerTypes'
import type { ExecutionDisplayPlanView } from '../model/semanticTurnView'

// P3 用户可见执行快照（FINAL，handoff §7）。
// 这是服务端确定性的最终阶段投影，不是实时进度：
//   - 不展示拟真的工具调用、命令日志、百分比或思维链；
//   - 映射层已保证不含 PENDING/IN_PROGRESS（违例时整个快照被丢弃）。

defineProps<{
  execution: ExecutionDisplayPlanView
}>()

const STATUS_LABELS: Record<ExecutionFinalStatus, string> = {
  COMPLETED: '完成',
  PARTIAL: '部分',
  SKIPPED: '跳过',
  FAILED: '失败',
}

const OVERALL_LABELS: Record<ExecutionFinalStatus, string> = {
  COMPLETED: '执行完成',
  PARTIAL: '部分完成',
  SKIPPED: '已跳过',
  FAILED: '未完成',
}
</script>

<template>
  <section
    class="execution-snapshot"
    data-execution-snapshot
    :data-execution-overall="execution.overallStatus"
    aria-label="执行概览"
  >
    <p class="execution-snapshot__overall">
      执行概览 · {{ OVERALL_LABELS[execution.overallStatus] ?? execution.overallStatus }}
    </p>
    <ol class="execution-snapshot__tasks">
      <li
        v-for="task in execution.tasks"
        :key="task.displayIndex"
        class="execution-snapshot__task"
        :data-task-final-status="task.finalStatus"
      >
        <ul class="execution-snapshot__stages">
          <li
            v-for="stage in task.stages"
            :key="stage.code"
            class="execution-snapshot__stage"
            :class="`execution-snapshot__stage--${stage.status.toLowerCase()}`"
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
  margin: 0 0 0.75rem;
  padding: 0.625rem 0.75rem;
  border: 1px solid var(--workspace-rule, currentColor);
  border-radius: 8px;
  background: var(--workspace-surface-subtle, rgba(0, 0, 0, 0.03));
  font-size: 0.8125rem;
}
.execution-snapshot__overall {
  margin: 0 0 0.375rem;
  font-weight: 600;
}
.execution-snapshot__tasks {
  margin: 0;
  padding: 0;
  list-style: none;
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
}
.execution-snapshot__stage--completed .execution-snapshot__stage-status {
  color: #157a3b;
}
.execution-snapshot__stage--failed .execution-snapshot__stage-status {
  color: #9a2a2a;
}
.execution-snapshot__stage--partial .execution-snapshot__stage-status {
  color: #8a6a14;
}
@media (prefers-reduced-motion: reduce) {
  .execution-snapshot,
  .execution-snapshot__stage {
    transition: none;
    animation: none;
  }
}
</style>
