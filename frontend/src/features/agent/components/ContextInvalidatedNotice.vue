<script setup lang="ts">
import type { ContextInvalidation } from '../model/answerTypes'
import { contextReasonText, recoveryActionLabel } from '../model/taskReasonLabels'

// P5 Strict Context 失效恢复卡（设计 §13.9/§13.10/§4.4，handoff §3）。
// 沿用 PlanInvalidatedNotice 范式：只展示白名单原因 + 明确恢复动作，
// 不静默替换目标、不暴露内部 reasonCode/version/handle。
defineProps<{ invalidation: ContextInvalidation; pending?: boolean; readonly?: boolean }>()
const emit = defineEmits<{ recover: [] }>()
</script>

<template>
  <section
    data-testid="context-invalidated-notice"
    class="context-invalidated-notice"
    :data-recovery-action="invalidation.recoveryAction"
    role="status"
  >
    <p class="context-invalidated-notice__title">对话上下文已失效</p>
    <p>{{ contextReasonText(invalidation.reasonCode) }}</p>
    <div v-if="!readonly" class="context-invalidated-notice__actions">
      <button
        data-action="recover-context"
        type="button"
        :disabled="pending"
        @click="emit('recover')"
      >{{ recoveryActionLabel(invalidation.recoveryAction) }}</button>
    </div>
  </section>
</template>

<style scoped>
.context-invalidated-notice {
  margin: 18px 0;
  padding: 15px;
  overflow-wrap: anywhere;
  border: 1px solid var(--workspace-accent, var(--red));
  background: var(--paper-hi);
  color: var(--workspace-text, var(--ink));
  font: 13px/1.65 var(--sans);
}
.context-invalidated-notice__title {
  margin: 0;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}
.context-invalidated-notice > p:not(:first-child) {
  margin: 8px 0 0;
}
.context-invalidated-notice__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.context-invalidated-notice button {
  min-height: 34px;
  padding: 7px 12px;
  border: 1px solid var(--workspace-accent, var(--red));
  background: var(--workspace-accent, var(--red));
  color: var(--paper-hi);
  font: 11px var(--mono);
  cursor: pointer;
}
.context-invalidated-notice button:disabled {
  opacity: 0.55;
  cursor: wait;
}
</style>
