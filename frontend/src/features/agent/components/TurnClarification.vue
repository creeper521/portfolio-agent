<script setup lang="ts">
import { computed } from 'vue'

import type { ClarificationSubmission, ClarificationView } from '../model/semanticTurnView'
import { blockedGoalReasonText } from '../model/taskReasonLabels'
import ClarificationField from './ClarificationField.vue'

const props = defineProps<{
  clarification: ClarificationView
  pending?: boolean
  readonly?: boolean
  readonlyNote?: string
}>()

const emit = defineEmits<{
  submit: [payload: { clarification: ClarificationView; submission: ClarificationSubmission }]
}>()

const interactive = computed(() => !props.readonly && !props.pending)

// 局部澄清：优先点名已继续任务（后端 continuingGoalLabels），
// 缺失时回落为数量文案，兼容未提供该字段的响应（FE-F06）。
const continuedNotice = computed(() => {
  if (props.clarification.scope !== 'LOCAL') return ''
  if (props.clarification.continuingGoalLabels.length > 0) {
    return `已继续：${props.clarification.continuingGoalLabels.join('、')}，不受影响`
  }
  return `已继续 ${props.clarification.continuingTaskCount} 个可安全完成的任务`
})

// 关键澄清：优先使用后端 blockedGoals（带安全 reasonCode 白名单文案）；
// 无 blockedGoals 时回落为受影响目标列表（FE-F07 兼容路径）。
const blockedGoals = computed(() => props.clarification.blockedGoals)
const fallbackAffectedGoals = computed(() => [...new Set(
  props.clarification.fields.flatMap((field) => field.affectedGoalLabels),
)])

function forward(submission: ClarificationSubmission) {
  if (!interactive.value) return
  emit('submit', { clarification: props.clarification, submission })
}
</script>

<template>
  <section
    data-testid="turn-clarification"
    class="turn-clarification"
    :data-scope="clarification.scope"
    :data-readonly="readonly ? 'true' : undefined"
    :data-pending="pending ? 'true' : undefined"
    aria-live="polite"
  >
    <p class="turn-clarification__eyebrow">{{ clarification.scope === 'LOCAL' ? '局部澄清' : '需要澄清' }}</p>
    <p class="turn-clarification__prompt">{{ clarification.prompt }}</p>

    <p v-if="clarification.scope === 'LOCAL'" class="turn-clarification__notice">{{ continuedNotice }}</p>
    <p v-else class="turn-clarification__notice">在收到选择前不会执行这项计划；{{ clarification.blockedTaskCount }} 个下游任务当前被阻塞</p>

    <div v-if="clarification.scope === 'CRITICAL' && blockedGoals.length" class="turn-clarification__blocked">
      <p>被阻塞的下游任务：</p>
      <ul>
        <li v-for="goal in blockedGoals" :key="goal.goalLabel">
          {{ goal.goalLabel }}<span class="turn-clarification__blocked-reason">— {{ blockedGoalReasonText(goal.reasonCode) }}</span>
        </li>
      </ul>
    </div>
    <div v-else-if="clarification.scope === 'CRITICAL'" class="turn-clarification__blocked">
      <p>受影响的下游目标：</p>
      <ul>
        <li v-for="goal in fallbackAffectedGoals" :key="goal">{{ goal }}</li>
      </ul>
    </div>

    <ClarificationField
      v-for="field in clarification.fields"
      :key="field.fieldKey"
      :field="field"
      :pending="pending"
      :readonly="readonly"
      @submit="forward"
    />

    <p v-if="pending" class="turn-clarification__notice" role="status">已提交，正在按你的选择重新规划…</p>
    <p v-else-if="readonly" class="turn-clarification__notice">{{ readonlyNote ?? '此澄清已被后续轮次取代，仅作记录，不可再提交。' }}</p>
  </section>
</template>

<style scoped>
.turn-clarification { margin: 18px 0; padding: 16px; overflow-wrap: anywhere; border: 1px solid var(--workspace-accent, var(--red)); background: var(--paper-hi); }
.turn-clarification[data-readonly='true'] { border-color: var(--workspace-rule, var(--rule)); }
.turn-clarification__eyebrow { margin: 0 0 8px; color: var(--workspace-accent, var(--red)); font: 11px var(--mono); letter-spacing: .1em; }
.turn-clarification__prompt { position: relative; margin: 0; padding-left: 18px; color: var(--workspace-text, var(--ink)); font: 500 17px/1.5 var(--serif); }
.turn-clarification__prompt::before { content: ""; position: absolute; left: 0; top: .6em; width: 7px; height: 7px; background: var(--workspace-accent, var(--red)); transform: rotate(45deg); }
.turn-clarification__notice, .turn-clarification__field > p { margin: 10px 0 0; color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.6 var(--mono); }
.turn-clarification__blocked { margin-top: 10px; padding: 9px 11px; border-left: 2px solid var(--workspace-accent, var(--red)); color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.6 var(--mono); }
.turn-clarification__blocked p { margin: 0; }
.turn-clarification__blocked ul { margin: 5px 0 0; padding-left: 18px; }
.turn-clarification__blocked-reason { color: var(--workspace-text-faint, var(--faint)); margin-left: 6px; }
</style>
