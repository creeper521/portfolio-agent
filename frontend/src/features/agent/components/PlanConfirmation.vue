<script setup lang="ts">
import { computed } from 'vue'

import type { DisplayPlanView, OpaquePlanConfirmation } from '../model/semanticTurnView'

const props = defineProps<{
  plan: DisplayPlanView
  confirmation?: OpaquePlanConfirmation
  pending?: boolean
  adjusting?: boolean
  readonly?: boolean
  readonlyNote?: string
  adjustDisabled?: boolean
}>()

const emit = defineEmits<{
  confirm: [confirmation: OpaquePlanConfirmation]
  adjust: []
  cancel: []
}>()

const interactive = computed(() => !props.pending && !props.readonly)

// U05：后端提供确定性 summaryLabel 时展示「N 步 · 概括」；
// 缺失时回落为纯任务数，不拼接无限长 goalLabel。
const planTitle = computed(() =>
  props.plan.summaryLabel
    ? `${props.plan.taskCount} 步 · ${props.plan.summaryLabel}`
    : `${props.plan.taskCount} 项任务`,
)

const statusLabel = computed(() => {
  if (props.readonly) return '已失效'
  return props.adjusting ? '调整中 · 仍可确认原计划' : '待你确认'
})

function sourceLabel(sourceDomain: DisplayPlanView['tasks'][number]['sourceDomain']): string {
  if (sourceDomain === 'PORTFOLIO') return '作品集资料'
  if (sourceDomain === 'GENERAL') return '通用知识'
  return '综合结论'
}
</script>

<template>
  <section
    data-testid="plan-confirmation"
    class="plan-confirmation"
    :data-adjusting="adjusting ? 'true' : undefined"
    :data-readonly="readonly ? 'true' : undefined"
    aria-label="待确认的任务计划"
  >
    <div class="plan-confirmation__head">
      <p class="plan-confirmation__eyebrow">计划确认 · {{ planTitle }}</p>
      <span class="plan-confirmation__status" :data-mode="adjusting ? 'adjusting' : 'idle'">{{ statusLabel }}</span>
    </div>
    <ul v-if="plan.constraints.length" class="plan-confirmation__constraints" aria-label="计划约束">
      <li v-for="constraint in plan.constraints" :key="constraint">{{ constraint }}</li>
    </ul>
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
    <p v-if="readonly" class="plan-confirmation__readonly-note">{{ readonlyNote ?? '此计划已被后续轮次取代，仅作记录。' }}</p>
    <div v-else-if="confirmation" class="plan-confirmation__actions">
      <button
        data-action="confirm-plan"
        type="button"
        :disabled="!interactive"
        @click="emit('confirm', confirmation)"
        @keydown.enter.prevent="emit('confirm', confirmation)"
      >按此计划继续</button>
      <button
        data-action="adjust-plan"
        type="button"
        :disabled="!interactive || adjustDisabled"
        :title="adjustDisabled ? '调整暂不可用：当前计划缺少可引用标识' : undefined"
        @click="emit('adjust')"
      >调整计划</button>
      <button data-action="cancel-plan" type="button" :disabled="!interactive" @click="emit('cancel')">取消</button>
    </div>
  </section>
</template>

<style scoped>
.plan-confirmation { margin: 18px 0; padding: 18px; overflow-wrap: anywhere; border: 1px solid var(--workspace-rule, var(--rule)); background: var(--workspace-surface-subtle, var(--paper-low)); }
.plan-confirmation__head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.plan-confirmation__eyebrow, .plan-confirmation__index, .plan-confirmation__tasks p { color: var(--workspace-accent, var(--red)); font: 11px/1.5 var(--mono); letter-spacing: .08em; }
.plan-confirmation__eyebrow { margin: 0; flex: 1; }
.plan-confirmation__status { font: 10px var(--mono); letter-spacing: .08em; color: var(--workspace-text-faint, var(--faint)); }
.plan-confirmation__status[data-mode='adjusting'] { color: var(--workspace-accent, var(--red)); border: 1px solid var(--workspace-accent, var(--red)); padding: 1px 6px; border-radius: 3px; }
.plan-confirmation__tasks { display: grid; margin: 0; padding: 0; gap: 12px; list-style: none; }
.plan-confirmation__tasks li { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 10px; }
.plan-confirmation__tasks strong { color: var(--workspace-text, var(--ink)); font: 600 17px/1.4 var(--serif); }
.plan-confirmation__tasks p { margin: 3px 0 0; color: var(--workspace-text-secondary, var(--muted)); }
.plan-confirmation__constraints { margin: 0 0 14px; padding: 10px 0 10px 18px; border-top: 1px dashed var(--workspace-rule, var(--rule)); border-bottom: 1px dashed var(--workspace-rule, var(--rule)); color: var(--workspace-text-secondary, var(--muted)); font: 12px/1.6 var(--sans); list-style: none; display: flex; flex-wrap: wrap; gap: 4px 14px; }
.plan-confirmation__source { margin-left: 7px; padding: 1px 5px; border: 1px solid var(--workspace-rule, var(--rule)); font: 10px var(--mono); letter-spacing: .04em; }
/* 来源可区分（spec §5 原则3）：作品集事实=红边，通用知识/综合=默认灰描边 */
.plan-confirmation__source[data-source-label='PORTFOLIO'] { color: var(--workspace-accent, var(--red)); border-color: var(--workspace-accent, var(--red)); }
.plan-confirmation__readonly-note { margin: 10px 0 0; color: var(--workspace-text-faint, var(--faint)); font: 10.5px var(--mono); letter-spacing: .04em; }
.plan-confirmation__actions { display: flex; flex-wrap: wrap; margin-top: 16px; gap: 8px; }
.plan-confirmation__actions button { min-height: 34px; padding: 7px 11px; border: 1px solid var(--workspace-accent, var(--red)); background: transparent; color: var(--workspace-accent, var(--red)); font: 11px var(--mono); cursor: pointer; }
.plan-confirmation__actions button:first-child { background: var(--workspace-accent, var(--red)); color: var(--paper-hi); }
.plan-confirmation__actions button:disabled { opacity: .55; cursor: wait; }
@media (max-width: 620px) { .plan-confirmation { padding: 14px; } }
</style>
