<script setup lang="ts">
import { computed } from 'vue'

import type {
  PublicEvidence,
  PublicProject,
} from '../../public-content/model/publicContentTypes'
import type {
  EvidenceCitation,
  EvidenceDeskTab,
} from '../model/evidenceDeskModel'

const props = defineProps<{
  evidence: PublicEvidence[]
  project: PublicProject
  activeEvidenceId: string
  focusEvidenceIds: string[]
  citations: EvidenceCitation[]
  tab: EvidenceDeskTab
}>()

const emit = defineEmits<{
  'update:tab': [tab: EvidenceDeskTab]
  select: [id: string]
  locateAnswer: [target: {
    messageId: string
    sectionType: EvidenceCitation['sectionType']
  }]
}>()

const tabs: Array<{ id: EvidenceDeskTab; label: string }> = [
  { id: 'EVIDENCE', label: '证据' },
  { id: 'CITATIONS', label: '引用' },
  { id: 'SOURCES', label: '来源' },
]

const orderedEvidence = computed(() => {
  const focused = new Set(props.focusEvidenceIds)
  return [...props.evidence].sort(
    (left, right) =>
      Number(focused.has(right.id)) - Number(focused.has(left.id)),
  )
})
</script>

<template>
  <aside
    id="agent-evidence-desk"
    class="evidence-desk"
    aria-label="证据工作台"
    :data-project-slug="project.slug"
  >
    <header>
      <h2>证据工作台</h2>
      <span>SYNCED</span>
    </header>

    <nav class="evidence-tabs" role="tablist" aria-label="证据上下文">
      <button
        v-for="item in tabs"
        :key="item.id"
        role="tab"
        type="button"
        :aria-selected="tab === item.id"
        :class="{ active: tab === item.id }"
        @click="emit('update:tab', item.id)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div v-if="tab === 'EVIDENCE'" class="evidence-list">
      <article
        v-for="item in orderedEvidence"
        :key="item.id"
        class="evidence-card"
        :class="{
          'evidence-card--active': activeEvidenceId === item.id,
          'evidence-card--focused': focusEvidenceIds.includes(item.id),
        }"
        :data-evidence-id="item.id"
        @click="emit('select', item.id)"
      >
        <span>EVIDENCE · {{ item.code }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.summary }}</p>
        <small>
          {{ item.publicStatus }} · {{ item.type }} · {{ item.periodStart }}
        </small>
        <RouterLink :to="{ path: '/evidence', query: { evidence: item.id } }">
          查看证据 →
        </RouterLink>
      </article>
    </div>

    <div v-else-if="tab === 'CITATIONS'" class="citation-list">
      <button
        v-for="citation in citations"
        :key="citation.id"
        data-citation-id
        class="citation-card"
        type="button"
        @click="emit('locateAnswer', {
          messageId: citation.messageId,
          sectionType: citation.sectionType,
        })"
      >
        <span>{{ citation.sectionTitle }}</span>
        <q>{{ citation.excerpt }}</q>
        <small>引用自 {{ citation.evidenceId }}</small>
      </button>
      <p v-if="!citations.length" class="evidence-empty">
        当前回答没有直接引用证据。
      </p>
    </div>

    <div v-else class="source-list">
      <article
        v-for="item in orderedEvidence"
        :key="item.id"
        class="source-card"
      >
        <span>{{ item.type }} · {{ item.code }}</span>
        <h3>{{ item.title }}</h3>
        <p>
          {{ item.sourceCount }} 个公开来源 ·
          {{ item.periodStart }}—{{ item.periodEnd }}
        </p>
        <small>{{ item.publicStatus }}</small>
      </article>
    </div>
  </aside>
</template>

<style scoped>
.evidence-desk {
  height: 100%;
  padding: 28px 20px;
  color: var(--workspace-text, var(--ink));
  border-left: 1px solid var(--workspace-rule, var(--rule));
  background: var(--workspace-evidence-bg, var(--paper));
  overflow-y: auto;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

header h2 {
  margin: 0;
  font: 500 22px var(--serif);
}

header span {
  color: var(--muted);
  font: 11px var(--mono);
  letter-spacing: 0.14em;
}

.evidence-tabs {
  display: flex;
  margin: 24px 0;
  gap: 18px;
  border-bottom: 1px solid var(--workspace-rule, var(--rule));
}

.evidence-tabs button {
  padding: 0 0 10px;
  color: var(--faint);
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  font: 12px var(--mono);
  letter-spacing: 0.1em;
  cursor: pointer;
}

.evidence-tabs button.active {
  color: var(--workspace-accent, var(--red));
  border-color: var(--workspace-accent, var(--red));
}

.evidence-list,
.citation-list,
.source-list {
  display: grid;
  gap: 12px;
}

.evidence-card,
.citation-card,
.source-card {
  box-sizing: border-box;
  width: 100%;
  padding: 16px;
  color: inherit;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  text-align: left;
  transition: border-color var(--agent-motion-fast) var(--ease);
}

.evidence-card,
.citation-card {
  cursor: pointer;
}

.evidence-card--focused {
  border-left: 3px solid var(--workspace-accent, var(--red));
}

.evidence-card--active {
  border-left: 2px solid var(--workspace-accent, var(--red));
}

.evidence-card--active.evidence-card--focused {
  border-left-width: 3px;
}

.evidence-card > span,
.citation-card > span,
.source-card > span {
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}

h3 {
  margin: 9px 0;
  font: 500 17px/1.35 var(--serif);
}

p,
q {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

p {
  margin: 0 0 12px;
}

q {
  display: block;
  margin: 10px 0 12px;
}

small {
  display: block;
  color: var(--faint);
  font: 11px var(--mono);
  letter-spacing: 0.07em;
}

.evidence-card a {
  display: inline-block;
  margin-top: 16px;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
}

.evidence-empty {
  padding: 16px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
}
</style>
