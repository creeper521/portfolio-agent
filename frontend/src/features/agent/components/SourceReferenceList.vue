<script setup lang="ts">
import type { PublicSourceReference, PublicSourceType } from '../model/answerTypes'

// P3 公开来源引用列表（handoff §8）。
// 引用来自后端审核过的公开快照稳定 code；route 只接受站内相对公开路由（映射层已校验）。
// 前端保持后端顺序，展示层去重保序（按 referenceKey 第一次出现），不改变结论—来源绑定。

const props = defineProps<{
  references: PublicSourceReference[]
}>()

const SOURCE_TYPE_LABELS: Record<PublicSourceType, string> = {
  COLLECTION: '合集',
  DOCUMENT: '文档',
  SCREENSHOT: '截图',
  CODE: '代码',
  TEST_RESULT: '测试结果',
}

// 展示层去重保序：保留每个 referenceKey 的第一次出现。
import { computed } from 'vue'
const deduped = computed(() => {
  const seen = new Set<string>()
  const out: PublicSourceReference[] = []
  for (const reference of props.references) {
    if (seen.has(reference.referenceKey)) continue
    seen.add(reference.referenceKey)
    out.push(reference)
  }
  return out
})
</script>

<template>
  <ul v-if="deduped.length" class="source-reference-list">
    <li
      v-for="reference in deduped"
      :key="reference.referenceKey"
      class="source-reference"
      :data-source-reference="reference.referenceKey"
      :data-source-type="reference.sourceType"
    >
      <RouterLink :to="reference.subjectRoute" class="source-reference__link">
        <span class="source-reference__type">{{ SOURCE_TYPE_LABELS[reference.sourceType] ?? reference.sourceType }}</span>
        <span class="source-reference__label">{{ reference.label }}</span>
      </RouterLink>
      <RouterLink
        v-if="reference.evidenceRoute"
        :to="reference.evidenceRoute"
        class="source-reference__evidence"
      >证据</RouterLink>
      <span class="source-reference__version">{{ reference.publishedVersion }}</span>
    </li>
  </ul>
</template>

<style scoped>
.source-reference-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.5rem;
  margin: 0.375rem 0 0;
  padding: 0;
  list-style: none;
}
.source-reference {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.125rem 0.375rem;
  border: 1px solid var(--workspace-rule, currentColor);
  border-radius: 6px;
  font-size: 0.75rem;
  color: var(--workspace-text-secondary, inherit);
}
.source-reference__link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: inherit;
  text-decoration: none;
}
.source-reference__link:hover {
  text-decoration: underline;
}
.source-reference__type {
  padding: 0 0.25rem;
  border-radius: 3px;
  background: var(--workspace-action-bg, rgba(0, 0, 0, 0.06));
  color: var(--workspace-primary-text, inherit);
  font-size: 0.6875rem;
}
.source-reference__evidence {
  color: var(--workspace-accent, inherit);
  text-decoration: none;
  font-size: 0.6875rem;
}
.source-reference__evidence:hover {
  text-decoration: underline;
}
.source-reference__version {
  font-size: 0.625rem;
  opacity: 0.7;
}
</style>
