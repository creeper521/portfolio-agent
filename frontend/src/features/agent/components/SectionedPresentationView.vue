<script setup lang="ts">
import type {
  PublicSection,
  PublicSourceCatalog,
  PublicSourceReference,
  SectionedPresentation,
} from '../model/publicAgentTurn'
import { SECTION_KIND_LABELS, SUPPORT_KIND_LABELS } from '../model/publicAgentTurnLabels'

// SECTIONED 展示视图：按后端给定顺序逐段渲染 Goal 的分节正文，每节附
// 节类型徽标、pre-line 正文与支持说明。来源引用通过 publicSourceKeys 从
// Answer 级唯一 sourceCatalog 解析为"公开编号 · 标题"的站内链接；
// 没有 publicSourceKey 的 Section 只显示支持类型，不显示任何内部引用（D-41.6-8）。
// 纯只读组件：props 进、模板出，无本地状态、无 emit。

const props = defineProps<{
  presentation: SectionedPresentation
  sourceCatalog: PublicSourceCatalog
}>()

/** 把 Section 的 publicSourceKeys 解析为 sourceCatalog 中的来源条目；catalog 中查不到的 key 静默忽略。 */
function sourcesOf(section: PublicSection): readonly PublicSourceReference[] {
  return section.support.publicSourceKeys
    .map((key) => props.sourceCatalog.sources.find((source) => source.key === key))
    .filter((source): source is PublicSourceReference => source !== undefined)
}
</script>

<template>
  <div class="sectioned-presentation" data-testid="sectioned-presentation">
    <section
      v-for="section in presentation.sections"
      :key="section.sectionId"
      class="sectioned-presentation__section"
      :data-section-id="section.sectionId"
      :data-section-kind="section.sectionKind"
    >
      <h4 class="sectioned-presentation__title">
        <span class="sectioned-presentation__kind">{{ SECTION_KIND_LABELS[section.sectionKind] }}</span>
        {{ section.title }}
      </h4>
      <p class="sectioned-presentation__content">{{ section.content }}</p>
      <p class="sectioned-presentation__support" :data-support-kind="section.support.kind">
        {{ SUPPORT_KIND_LABELS[section.support.kind] }}
        <template v-if="sourcesOf(section).length > 0">
          <RouterLink
            v-for="source in sourcesOf(section)"
            :key="source.key"
            :to="source.route"
            class="sectioned-presentation__source"
            :data-source-key="source.key"
          >{{ source.code === undefined ? source.label : `${source.code} · ${source.label}` }}</RouterLink>
        </template>
      </p>
    </section>
  </div>
</template>

<style scoped>
.sectioned-presentation__section { margin: 0 0 14px; }
.sectioned-presentation__section:last-child { margin-bottom: 0; }
.sectioned-presentation__title {
  margin: 0 0 6px;
  color: var(--workspace-text, var(--ink));
  font: 600 15px/1.5 var(--sans);
  overflow-wrap: anywhere;
}
.sectioned-presentation__kind {
  display: inline-block;
  margin-right: 8px;
  padding: 0 6px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 4px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10px/1.7 var(--mono);
  vertical-align: 2px;
}
.sectioned-presentation__content {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 15px/1.75 var(--serif);
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.sectioned-presentation__support {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
  margin: 6px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
.sectioned-presentation__source {
  padding: 0 6px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 6px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10px/1.7 var(--mono);
  text-decoration: none;
}
.sectioned-presentation__source:hover { text-decoration: underline; }
</style>
