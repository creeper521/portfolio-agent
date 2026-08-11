<script setup lang="ts">
import type { PlanChangeView } from '../model/semanticTurnView'

defineProps<{ planChange: PlanChangeView; pending?: boolean }>()
const emit = defineEmits<{ regenerate: [] }>()
</script>

<template>
  <section data-testid="plan-invalidated-notice" class="plan-invalidated-notice" role="status">
    <p class="plan-invalidated-notice__title">原计划已失效</p>
    <p>{{ planChange.summary }}</p>
    <ul v-if="planChange.changeLabels.length">
      <li v-for="label in planChange.changeLabels" :key="label">{{ label }}</li>
    </ul>
    <button data-action="regenerate-plan" type="button" :disabled="pending" @click="emit('regenerate')">重新生成计划</button>
  </section>
</template>

<style scoped>
.plan-invalidated-notice { margin: 18px 0; padding: 15px; overflow-wrap: anywhere; border: 1px solid var(--workspace-accent, var(--red)); background: var(--paper-hi); color: var(--workspace-text, var(--ink)); font: 13px/1.65 var(--sans); }
.plan-invalidated-notice__title { margin: 0; color: var(--workspace-accent, var(--red)); font: 11px var(--mono); letter-spacing: .1em; }
.plan-invalidated-notice > p:not(:first-child) { margin: 8px 0 0; }
.plan-invalidated-notice ul { margin: 8px 0; padding-left: 18px; color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.5 var(--mono); }
.plan-invalidated-notice button { min-height: 34px; padding: 7px 10px; border: 1px solid var(--workspace-accent, var(--red)); background: var(--workspace-accent, var(--red)); color: var(--paper-hi); font: 11px var(--mono); cursor: pointer; }
.plan-invalidated-notice button:disabled { opacity: .55; cursor: wait; }
</style>
