<script setup lang="ts">
import type { AnswerGoalResult, PublicSourceCatalog } from '../model/publicAgentTurn'
import { GOAL_COVERAGE_LABELS } from '../model/publicAgentTurnLabels'
import RecommendationPresentationView from './RecommendationPresentationView.vue'
import SectionedPresentationView from './SectionedPresentationView.vue'

// D-41：信息层级固定为 Goal 正文 -> Notice -> 来源与支持（在 Presentation 内）；
// FULL 不显示覆盖标签；非 FULL 用文字表达，不只靠颜色；Supporting Task 不在此出现。

defineProps<{
  goal: AnswerGoalResult
  sourceCatalog: PublicSourceCatalog
}>()
</script>

<template>
  <section class="goal-result" :data-goal-id="goal.goalId" :data-goal-coverage="goal.coverage">
    <h3 class="goal-result__label">{{ goal.label }}</h3>
    <p v-if="goal.coverage !== 'FULL'" class="goal-result__coverage" data-testid="goal-coverage">
      <span aria-hidden="true">{{ goal.coverage === 'PARTIAL' ? '◐' : '○' }}</span>
      {{ GOAL_COVERAGE_LABELS[goal.coverage] }}
    </p>
    <ul v-if="goal.notices.length > 0" class="goal-result__notices">
      <li v-for="notice in goal.notices" :key="notice.code" class="goal-result__notice" :data-notice-code="notice.code">
        {{ notice.message }}
      </li>
    </ul>
    <SectionedPresentationView
      v-if="goal.presentation !== undefined && goal.presentation.kind === 'SECTIONED'"
      :presentation="goal.presentation"
      :source-catalog="sourceCatalog"
    />
    <RecommendationPresentationView
      v-else-if="goal.presentation !== undefined && goal.presentation.kind === 'RECOMMENDATION'"
      :presentation="goal.presentation"
      :source-catalog="sourceCatalog"
    />
    <slot name="appendix" />
  </section>
</template>

<style scoped>
.goal-result { margin: 0 0 18px; }
.goal-result:last-child { margin-bottom: 0; }
.goal-result__label {
  margin: 0 0 8px;
  color: var(--workspace-text, var(--ink));
  font: 600 16px/1.5 var(--sans);
  overflow-wrap: anywhere;
}
.goal-result__coverage {
  margin: 0 0 8px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
.goal-result__notices {
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
}
.goal-result__notice {
  padding: 6px 10px;
  border-left: 2px solid var(--workspace-rule, var(--rule));
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.7 var(--sans);
  overflow-wrap: anywhere;
}
</style>
