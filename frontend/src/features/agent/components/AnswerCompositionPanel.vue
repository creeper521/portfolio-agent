<script setup lang="ts">
import type { CompletedTaskView } from '../model/semanticTurnView'
import type {
  PublicAnswerCaveat,
  PublicDegradationKind,
  PublicDegradationSummary,
  SourceComposition,
} from '../model/answerTypes'
import {
  degradationKindLabel,
  fulfillmentRoleLabel,
  sourceCompositionLabel,
  sourceDomainLabel,
  supportKindLabel,
} from '../model/answerLabels'

// P5「回答构成」信任层（设计 §4.2/§4.4，handoff §2）。
// 默认折叠的 opt-in 面板：sourceComposition + 任务清单（角色/supportSummary）+ 降级 kinds + 限定语汇总。
// 普通访客默认不展开；展开后可见来源链与支持聚合，信息密度可控。
defineProps<{
  sourceComposition?: SourceComposition
  completedTasks: CompletedTaskView[]
  degradationSummary?: PublicDegradationSummary
  caveats?: PublicAnswerCaveat[]
}>()

function degradationKindsText(kinds: readonly PublicDegradationKind[]): string {
  return kinds
    .map(degradationKindLabel)
    .filter((label): label is string => label !== null)
    .join('、')
}
</script>

<template>
  <details class="answer-composition" data-testid="answer-composition-panel">
    <summary class="answer-composition__summary">回答构成</summary>
    <div class="answer-composition__body">
      <p v-if="sourceCompositionLabel(sourceComposition)" class="answer-composition__row" data-composition>
        <span class="answer-composition__label">来源组成</span>
        <span>{{ sourceCompositionLabel(sourceComposition) }}</span>
      </p>
      <ul v-if="completedTasks.length" class="answer-composition__tasks">
        <li v-for="task in completedTasks" :key="task.displayIndex" :data-task-index="task.displayIndex">
          <span class="answer-composition__task-no">{{ task.displayIndex }}</span>
          <span class="answer-composition__task-label">{{ task.goalLabel }}</span>
          <span
            v-if="sourceDomainLabel(task.sourceDomain)"
            class="answer-composition__task-domain"
          >{{ sourceDomainLabel(task.sourceDomain) }}</span>
          <span
            v-if="fulfillmentRoleLabel(task.fulfillmentRole)"
            class="answer-composition__task-role"
            :data-role="task.fulfillmentRole"
          >{{ fulfillmentRoleLabel(task.fulfillmentRole) }}</span>
          <span
            v-if="task.supportSummary"
            class="answer-composition__task-support"
            :data-support="task.supportSummary.kind"
          >{{ supportKindLabel(task.supportSummary.kind) }} · {{ task.supportSummary.statementCount }} 条陈述 · {{ task.supportSummary.publicSourceCount }} 个来源</span>
        </li>
      </ul>
      <p v-if="degradationSummary?.kinds.length" class="answer-composition__row" data-degradation-kinds>
        <span class="answer-composition__label">降级方式</span>
        <span>{{ degradationKindsText(degradationSummary.kinds) }}</span>
      </p>
      <p v-if="caveats?.length" class="answer-composition__row" data-caveat-summary>
        <span class="answer-composition__label">限定语</span>
        <span>{{ caveats.length }} 条</span>
      </p>
    </div>
  </details>
</template>

<style scoped>
.answer-composition {
  margin: 14px 0 4px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
  background: var(--workspace-surface-subtle, var(--paper-low));
  overflow: hidden;
}
.answer-composition__summary {
  padding: 8px 12px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px var(--mono);
  letter-spacing: 0.06em;
  cursor: pointer;
  list-style: none;
}
.answer-composition__summary::after {
  content: ' ▾';
  color: var(--workspace-text-faint, var(--faint));
}
.answer-composition__summary::-webkit-details-marker {
  display: none;
}
.answer-composition__body {
  padding: 4px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.answer-composition__row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin: 0;
  font: 11.5px/1.6 var(--sans);
  color: var(--workspace-text-secondary, var(--ink-2));
}
.answer-composition__label {
  color: var(--workspace-text-faint, var(--faint));
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}
.answer-composition__tasks {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.answer-composition__tasks li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  font: 11.5px/1.6 var(--sans);
  color: var(--workspace-text-secondary, var(--ink-2));
}
.answer-composition__task-no {
  color: var(--workspace-accent, var(--red));
  font-family: var(--mono);
  font-size: 11px;
}
.answer-composition__task-label {
  color: var(--workspace-text, var(--ink));
}
.answer-composition__task-domain,
.answer-composition__task-role,
.answer-composition__task-support {
  padding: 1px 6px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 4px;
  font: 10px var(--mono);
  color: var(--workspace-text-secondary, var(--muted));
}
.answer-composition__task-role[data-role='PRIMARY'] {
  color: var(--workspace-accent, var(--red));
  border-color: var(--workspace-accent, var(--red));
}
</style>
