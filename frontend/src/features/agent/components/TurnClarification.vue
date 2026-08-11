<script setup lang="ts">
import { computed } from 'vue'

import type { ClarificationView } from '../model/semanticTurnView'

const emit = defineEmits<{ select: [selection: { fieldKey: string; value: string }] }>()
const props = defineProps<{ clarification: ClarificationView; pending?: boolean }>()
const affectedGoals = computed(() => [...new Set(
  props.clarification.fields.flatMap((field) => field.affectedGoalLabels),
)])
</script>

<template>
  <section data-testid="turn-clarification" class="turn-clarification" :data-scope="clarification.scope" aria-live="polite">
    <p class="turn-clarification__eyebrow">{{ clarification.scope === 'LOCAL' ? '局部澄清' : '需要澄清' }}</p>
    <p class="turn-clarification__prompt">{{ clarification.prompt }}</p>
    <p v-if="clarification.scope === 'LOCAL'" class="turn-clarification__notice">已继续 {{ clarification.continuingTaskCount }} 个可安全完成的任务</p>
    <p v-else class="turn-clarification__notice">在收到选择前不会执行这项计划；{{ clarification.blockedTaskCount }} 个下游任务当前被阻塞</p>
    <div v-if="clarification.scope === 'CRITICAL'" class="turn-clarification__blocked">
      <p>受影响的下游目标：</p>
      <ul>
        <li v-for="goal in affectedGoals" :key="goal">{{ goal }}</li>
      </ul>
    </div>
    <div v-for="field in clarification.fields" :key="field.fieldKey" class="turn-clarification__field">
      <p>请选择：</p>
      <div class="turn-clarification__options">
        <button v-for="option in field.options" :key="option.value" :data-clarification-option="option.value" type="button" :disabled="pending" @click="emit('select', { fieldKey: field.fieldKey, value: option.value })">{{ option.label }}</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.turn-clarification { margin: 18px 0; padding: 16px; overflow-wrap: anywhere; border: 1px solid var(--workspace-accent, var(--red)); background: var(--paper-hi); }
.turn-clarification__eyebrow { margin: 0 0 8px; color: var(--workspace-accent, var(--red)); font: 11px var(--mono); letter-spacing: .1em; }
.turn-clarification__prompt { margin: 0; color: var(--workspace-text, var(--ink)); font: 500 17px/1.5 var(--serif); }
.turn-clarification__notice, .turn-clarification__field > p { margin: 10px 0 0; color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.6 var(--mono); }
.turn-clarification__blocked { margin-top: 10px; padding: 9px 11px; border-left: 2px solid var(--workspace-accent, var(--red)); color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.6 var(--mono); }
.turn-clarification__blocked p { margin: 0; }
.turn-clarification__blocked ul { margin: 5px 0 0; padding-left: 18px; }
.turn-clarification__options { display: flex; flex-wrap: wrap; margin-top: 7px; gap: 7px; }
.turn-clarification__options button { min-height: 34px; padding: 7px 10px; color: var(--workspace-accent, var(--red)); border: 1px solid currentcolor; background: transparent; font: 12px var(--sans); cursor: pointer; }
.turn-clarification__options button:disabled { opacity: .55; cursor: wait; }
</style>
