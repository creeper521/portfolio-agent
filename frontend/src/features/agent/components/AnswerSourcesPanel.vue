<script setup lang="ts">
import type { PublicSourceReference } from '../model/publicAgentTurn'

// 工作台第三栏：展示当前回答的唯一 SourceCatalog 公开来源。
// 与 Answer 内 SourceDrawer 共享同一数据权威；这里只做列表呈现与站内跳转。

defineProps<{
  sources: readonly PublicSourceReference[]
}>()
</script>

<template>
  <aside class="sources-panel" aria-label="当前回答来源">
    <p class="sources-panel__eyebrow">SOURCES · 当前回答来源</p>
    <ul v-if="sources.length > 0" class="sources-panel__list" data-testid="sources-panel-list">
      <li v-for="source in sources" :key="source.key" class="sources-panel__item" :data-source-key="source.key">
        <RouterLink :to="source.route" class="sources-panel__link">
          <span v-if="source.code !== undefined" class="sources-panel__code">{{ source.code }}</span>
          <span class="sources-panel__label">{{ source.label }}</span>
        </RouterLink>
      </li>
    </ul>
    <p v-else class="sources-panel__empty">暂无来源。提问后，这里展示当前回答引用的公开来源。</p>
  </aside>
</template>

<style scoped>
.sources-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--workspace-evidence-bg, var(--agent-evidence-paper));
  padding: 16px 18px;
  overflow-y: auto;
}
.sources-panel__eyebrow {
  margin: 0 0 12px;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px var(--mono);
  letter-spacing: 0.12em;
}
.sources-panel__list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.sources-panel__item { border-bottom: 1px solid var(--workspace-rule, var(--agent-hairline)); }
.sources-panel__item:last-child { border-bottom: none; }
.sources-panel__link {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 2px;
  color: var(--workspace-text, var(--ink));
  text-decoration: none;
}
.sources-panel__link:hover .sources-panel__label { text-decoration: underline; }
.sources-panel__link:focus-visible { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 2px; }
.sources-panel__code {
  padding: 0 6px;
  border: 1px solid var(--workspace-rule, var(--agent-hairline));
  border-radius: 4px;
  color: var(--workspace-accent, var(--red));
  font: 10px/1.7 var(--mono);
  flex-shrink: 0;
}
.sources-panel__label { font: 12px/1.6 var(--sans); overflow-wrap: anywhere; }
.sources-panel__empty {
  margin: 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.8 var(--mono);
}
</style>
