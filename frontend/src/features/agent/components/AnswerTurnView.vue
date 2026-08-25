<script setup lang="ts">
import { computed, ref } from 'vue'

import type { AnswerTurn, SuggestedAction } from '../model/publicAgentTurn'
import type { ClarificationSubmissionPayload } from '../model/publicAgentTurn'
import type { ClarificationCardState } from './ClarificationChallengeForm.vue'
import ClarificationChallengeForm from './ClarificationChallengeForm.vue'
import GoalResultView from './GoalResultView.vue'
import SourceDrawer from './SourceDrawer.vue'
import SuggestedActionRow from './SuggestedActionRow.vue'

// ANSWER 轮次的正文视图：渲染 answer.goalResults 的 Goal-first 分组结果。
// 数据全部来自 props（AnswerTurn），本地状态仅"来源抽屉开关"一处；
// 向父组件转发 select-action 与 submit-clarification 两种用户动作。
// 排版约定：多 Goal 按后端给定顺序分组；resolution 为 PARTIAL 时顶部
// 最多一条"已完成 N/M 个目标"进度简报，具体缺口挂在对应 Goal 下说明；
// NO_RESULT 不生成空正文；局部澄清（localClarification）贴在首个受影响
// Goal 下；Answer 底部提供"查看全部来源"抽屉入口（D-41）。

const props = defineProps<{
  turn: AnswerTurn
  interactionDisabled?: boolean
  clarificationState?: ClarificationCardState
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
  'submit-clarification': [payload: ClarificationSubmissionPayload]
}>()

const answer = computed(() => props.turn.answer)
const fullGoalCount = computed(
  () => answer.value.goalResults.filter((goal) => goal.coverage === 'FULL').length,
)
// 只有 PARTIAL 才展示顶部进度简报，COMPLETE/NO_RESULT 不需要冗余行。
const showProgress = computed(() => answer.value.resolution === 'PARTIAL')

const localClarification = computed(() => answer.value.localClarification)
const firstAffectedGoalId = computed(() => localClarification.value?.affectedGoalIds[0])

// 局部澄清影响多个 Goal 时，提示"补充后其余 N 个目标将继续执行"，
// N 为未受影响的目标数；全部受影响时返回空串不展示（D-41.5）。
const continuedGoalNotice = computed(() => {
  const local = localClarification.value
  if (local === undefined) return ''
  const unaffected = answer.value.goalResults.filter(
    (goal) => !local.affectedGoalIds.includes(goal.goalId),
  )
  return unaffected.length > 0 ? `补充后其余 ${unaffected.length} 个目标将继续执行` : ''
})

// Answer 内唯一的本地 UI 状态：来源抽屉的打开/关闭。
const sourceDrawerOpen = ref(false)

function forwardAction(action: SuggestedAction): void {
  emit('select-action', action)
}

function forwardClarification(payload: ClarificationSubmissionPayload): void {
  emit('submit-clarification', payload)
}
</script>

<template>
  <div class="answer-turn" data-testid="answer-turn">
    <p v-if="showProgress" class="answer-turn__progress" data-testid="answer-progress">
      已完成 {{ fullGoalCount }}/{{ answer.goalResults.length }} 个目标
    </p>
    <GoalResultView
      v-for="goal in answer.goalResults"
      :key="goal.goalId"
      :goal="goal"
      :source-catalog="answer.sourceCatalog"
      @select-action="forwardAction"
    >
      <!-- 局部澄清卡片只挂在首个受影响 Goal 下，避免多 Goal 时重复渲染表单 -->
      <template #appendix>
        <template v-if="localClarification !== undefined && goal.goalId === firstAffectedGoalId">
          <ClarificationChallengeForm
            :challenge="localClarification"
            :submit-label="answer.goalResults.length > 1 ? '提交并继续' : '提交补充'"
            :disabled="interactionDisabled"
            :state="clarificationState"
            @submit="forwardClarification"
          />
          <p v-if="continuedGoalNotice" class="answer-turn__continued">{{ continuedGoalNotice }}</p>
        </template>
      </template>
    </GoalResultView>
    <div v-if="answer.sourceCatalog.sources.length > 0" class="answer-turn__sources">
      <button
        class="answer-turn__sources-toggle"
        type="button"
        data-testid="open-source-drawer"
        @click="sourceDrawerOpen = true"
      >查看全部来源（{{ answer.sourceCatalog.sources.length }}）</button>
    </div>
    <SuggestedActionRow
      v-if="answer.suggestedActions !== undefined"
      :actions="answer.suggestedActions"
      @select="forwardAction"
    />
    <SourceDrawer
      :open="sourceDrawerOpen"
      :sources="answer.sourceCatalog.sources"
      :content-release-id="answer.contentReleaseId"
      @close="sourceDrawerOpen = false"
    />
  </div>
</template>

<style scoped>
.answer-turn__progress {
  margin: 0 0 14px;
  padding: 6px 10px;
  border-left: 2px solid var(--workspace-accent, var(--red));
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.7 var(--mono);
}
.answer-turn__continued {
  margin: 6px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
.answer-turn__sources { margin-top: 4px; }
.answer-turn__sources-toggle {
  min-height: 30px;
  padding: 5px 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  background: transparent;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10px var(--mono);
  letter-spacing: 0.06em;
  cursor: pointer;
}
.answer-turn__sources-toggle:hover { border-color: var(--workspace-accent, var(--red)); color: var(--workspace-accent, var(--red)); }
.answer-turn__sources-toggle:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 2px; }
</style>
