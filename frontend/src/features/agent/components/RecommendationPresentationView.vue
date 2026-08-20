<script setup lang="ts">
import { computed } from 'vue'

import type {
  PublicSourceCatalog,
  PublicSourceReference,
  RecommendationItem,
  RecommendationPresentation,
  SuggestedAction,
} from '../model/publicAgentTurn'
import { SUPPORT_KIND_LABELS } from '../model/publicAgentTurnLabels'

// D-41.9：推荐嵌入所属 Goal，卡片顺序、reasons、route 与缺口说明均以后端为权威；
// 数量缺口只说明一次；窄屏单列；resultItemId 不进入可见文本。

const props = defineProps<{
  presentation: RecommendationPresentation
  sourceCatalog: PublicSourceCatalog
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
}>()

const countIncomplete = computed(
  () => props.presentation.actualSize < props.presentation.requestedSize,
)

function sourcesOf(item: RecommendationItem): readonly PublicSourceReference[] {
  return item.support.publicSourceKeys
    .map((key) => props.sourceCatalog.sources.find((source) => source.key === key))
    .filter((source): source is PublicSourceReference => source !== undefined)
}
</script>

<template>
  <div class="recommendation-presentation" data-testid="recommendation-presentation">
    <div v-if="countIncomplete" class="recommendation-presentation__gap" data-testid="recommendation-count">
      <p>已找到 {{ presentation.actualSize }}/{{ presentation.requestedSize }} 项</p>
      <p v-for="reason in presentation.incompleteReasons" :key="reason" class="recommendation-presentation__gap-reason">{{ reason }}</p>
      <p v-for="constraint in presentation.unsatisfiedConstraints" :key="constraint" class="recommendation-presentation__gap-reason">未满足：{{ constraint }}</p>
    </div>
    <ul class="recommendation-presentation__items">
      <li
        v-for="item in presentation.items"
        :key="item.resultItemId ?? item.label"
        class="recommendation-presentation__item"
        data-testid="recommendation-item"
      >
        <RouterLink :to="item.route" class="recommendation-presentation__item-link">
          {{ item.label }}
        </RouterLink>
        <p class="recommendation-presentation__summary">{{ item.summary }}</p>
        <ul v-if="item.reasons.length > 0" class="recommendation-presentation__reasons">
          <li v-for="reason in item.reasons" :key="reason">{{ reason }}</li>
        </ul>
        <p class="recommendation-presentation__support" :data-support-kind="item.support.kind">
          {{ SUPPORT_KIND_LABELS[item.support.kind] }}
          <RouterLink
            v-for="source in sourcesOf(item)"
            :key="source.key"
            :to="source.route"
            class="recommendation-presentation__source"
            :data-source-key="source.key"
          >{{ source.code === undefined ? source.label : `${source.code} · ${source.label}` }}</RouterLink>
        </p>
        <button
          v-if="item.discussionAction !== undefined"
          class="recommendation-presentation__discussion"
          type="button"
          :data-action-id="item.discussionAction.actionId"
          @click="emit('select-action', item.discussionAction)"
        >{{ item.discussionAction.label }}</button>
      </li>
    </ul>
    <div v-if="presentation.supportingSections.length > 0" class="recommendation-presentation__supporting">
      <slot name="supporting" />
    </div>
  </div>
</template>

<style scoped>
.recommendation-presentation__gap {
  margin: 0 0 10px;
  padding: 8px 12px;
  border-left: 2px solid var(--workspace-accent, var(--red));
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.7 var(--mono);
}
.recommendation-presentation__gap p { margin: 0; }
.recommendation-presentation__gap-reason { margin-top: 2px !important; color: var(--workspace-text-faint, var(--faint)); }
.recommendation-presentation__items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.recommendation-presentation__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  background: rgba(255, 255, 255, 0.4);
}
.recommendation-presentation__item-link {
  color: var(--workspace-text, var(--ink));
  font: 600 14px/1.5 var(--sans);
  text-decoration: none;
  overflow-wrap: anywhere;
}
.recommendation-presentation__item-link:hover { text-decoration: underline; }
.recommendation-presentation__summary {
  margin: 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 12px/1.7 var(--serif);
  overflow-wrap: anywhere;
}
.recommendation-presentation__reasons {
  margin: 0;
  padding-left: 16px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.7 var(--sans);
}
.recommendation-presentation__support {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  margin: 2px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
.recommendation-presentation__source {
  padding: 0 6px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 6px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10px/1.7 var(--mono);
  text-decoration: none;
}
.recommendation-presentation__source:hover { text-decoration: underline; }
.recommendation-presentation__discussion {
  align-self: flex-start;
  padding: 5px 10px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 6px;
  background: transparent;
  color: var(--workspace-text, var(--ink));
  cursor: pointer;
}
/* D-41.17：推荐窄屏单列 */
@media (max-width: 640px) {
  .recommendation-presentation__items { grid-template-columns: 1fr; }
}
</style>
