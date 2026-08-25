<script setup lang="ts">
import type { AnswerGoalResult, PublicSourceCatalog, SuggestedAction } from '../model/publicAgentTurn'
import { GOAL_COVERAGE_LABELS } from '../model/publicAgentTurnLabels'
import RecommendationPresentationView from './RecommendationPresentationView.vue'
import SectionedPresentationView from './SectionedPresentationView.vue'

// 单个 Goal 的结果区块：标题 -> 覆盖状态 -> Notice 列表 -> Presentation
// （SECTIONED / RECOMMENDATION 二选一）。信息层级固定：来源与支持说明由
// Presentation 内部渲染，此层不重复；FULL 目标不显示覆盖标签，非 FULL 用
// 文字表达覆盖状态而非只靠颜色；Supporting Task 不在此层出现（D-41）。
// 数据来自 props（goal + Answer 级唯一 sourceCatalog），自身无状态、
// 不主动 emit；appendix 插槽供父组件挂局部澄清卡等附加内容。

defineProps<{
  goal: AnswerGoalResult
  sourceCatalog: PublicSourceCatalog
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
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
      @select-action="emit('select-action', $event)"
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
