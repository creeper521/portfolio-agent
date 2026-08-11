<script setup lang="ts">
import type { DisplayPlanView, OpaquePlanConfirmation } from '../model/semanticTurnView'

defineProps<{
  plan: DisplayPlanView
  confirmation: OpaquePlanConfirmation
  pending?: boolean
}>()

const emit = defineEmits<{
  confirm: [confirmation: OpaquePlanConfirmation]
  adjust: []
  cancel: []
}>()

function sourceLabel(sourceDomain: DisplayPlanView['tasks'][number]['sourceDomain']): string {
  if (sourceDomain === 'PORTFOLIO') return '作品集资料'
  if (sourceDomain === 'GENERAL') return '通用知识'
  return '综合结论'
}
</script>

<template>
  <section data-testid="plan-confirmation" class="plan-confirmation" aria-label="待确认的任务计划">
    <p class="plan-confirmation__eyebrow">计划确认 · {{ plan.taskCount }} 项任务</p>
    <ol class="plan-confirmation__tasks">
      <li v-for="task in plan.tasks" :key="task.displayIndex">
        <span class="plan-confirmation__index">{{ task.displayIndex }}</span>
        <div>
          <strong>{{ task.goalLabel }}</strong>
          <span class="plan-confirmation__source" :data-source-label="task.sourceDomain">{{ sourceLabel(task.sourceDomain) }}</span>
          <p v-if="task.dependencySummary">↳ {{ task.dependencySummary }}</p>
        </div>
      </li>
    </ol>
    <ul v-if="plan.constraints.length" class="plan-confirmation__constraints" aria-label="计划约束">
      <li v-for="constraint in plan.constraints" :key="constraint">{{ constraint }}</li>
    </ul>
    <div class="plan-confirmation__actions">
      <button
        data-action="confirm-plan"
        type="button"
        :disabled="pending"
        @click="emit('confirm', confirmation)"
        @keydown.enter.prevent="emit('confirm', confirmation)"
      >按此计划继续</button>
      <button data-action="adjust-plan" type="button" :disabled="pending" @click="emit('adjust')">调整计划</button>
      <button data-action="cancel-plan" type="button" :disabled="pending" @click="emit('cancel')">取消</button>
    </div>
  </section>
</template>

<style scoped>
.plan-confirmation { margin: 18px 0; padding: 18px; overflow-wrap: anywhere; border: 1px solid var(--workspace-rule, var(--rule)); background: var(--workspace-surface-subtle, var(--paper-low)); }
.plan-confirmation__eyebrow, .plan-confirmation__index, .plan-confirmation__tasks p { color: var(--workspace-accent, var(--red)); font: 11px/1.5 var(--mono); letter-spacing: .08em; }
.plan-confirmation__eyebrow { margin: 0 0 14px; }
.plan-confirmation__tasks { display: grid; margin: 0; padding: 0; gap: 12px; list-style: none; }
.plan-confirmation__tasks li { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 10px; }
.plan-confirmation__tasks strong { color: var(--workspace-text, var(--ink)); font: 600 17px/1.4 var(--serif); }
.plan-confirmation__tasks p { margin: 3px 0 0; color: var(--workspace-text-secondary, var(--muted)); }
.plan-confirmation__constraints { margin: 14px 0 0; padding: 10px 0 0 18px; border-top: 1px dashed var(--workspace-rule, var(--rule)); color: var(--workspace-text-secondary, var(--muted)); font: 12px/1.6 var(--sans); }
.plan-confirmation__actions { display: flex; flex-wrap: wrap; margin-top: 16px; gap: 8px; }
.plan-confirmation__actions button { min-height: 34px; padding: 7px 11px; border: 1px solid var(--workspace-accent, var(--red)); background: transparent; color: var(--workspace-accent, var(--red)); font: 11px var(--mono); cursor: pointer; }
.plan-confirmation__actions button:first-child { background: var(--workspace-accent, var(--red)); color: var(--paper-hi); }
.plan-confirmation__actions button:disabled { opacity: .55; cursor: wait; }
@media (max-width: 620px) { .plan-confirmation { padding: 14px; } }
</style>
