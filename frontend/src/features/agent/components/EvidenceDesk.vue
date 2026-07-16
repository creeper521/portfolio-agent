<script setup lang="ts">
import { computed, ref } from 'vue'

import type { PublicEvidence, PublicProject } from '../../public-content/model/publicContentTypes'

export type EvidenceDeskTab = 'EVIDENCE' | 'PROJECT' | 'TIMELINE' | 'CONTEXT'

const props = defineProps<{
  evidence: PublicEvidence[]
  project: PublicProject
  activeEvidenceId: string
}>()

const emit = defineEmits<{ select: [id: string] }>()

const activeTab = ref<EvidenceDeskTab>('EVIDENCE')
const tabs: Array<{ id: EvidenceDeskTab; label: string }> = [
  { id: 'EVIDENCE', label: '证据' },
  { id: 'PROJECT', label: '项目' },
  { id: 'TIMELINE', label: '时间线' },
  { id: 'CONTEXT', label: '脉络' },
]

const selected = computed(
  () =>
    props.evidence.find((item) => item.id === props.activeEvidenceId) ??
    props.evidence[0] ??
    null,
)
</script>

<template>
  <aside id="agent-evidence-desk" class="evidence-desk" aria-label="证据工作台">
    <header>
      <h2>证据工作台</h2>
      <span>SYNCED</span>
    </header>

    <nav class="evidence-tabs" aria-label="证据上下文">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div v-if="activeTab === 'EVIDENCE'" class="evidence-list">
      <article
        v-for="item in evidence"
        :key="item.id"
        :data-evidence-id="item.id"
        :class="{ 'evidence-card--active': selected?.id === item.id }"
        @click="emit('select', item.id)"
      >
        <span>{{ item.code }} · {{ item.type }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.summary }}</p>
        <small>{{ item.periodStart }} — {{ item.periodEnd }} · 已公开</small>
        <RouterLink :to="{ path: '/evidence', query: { evidence: item.id } }">
          在证据中心打开 →
        </RouterLink>
      </article>
    </div>

    <article v-else-if="activeTab === 'PROJECT'" class="context-panel">
      <span>{{ project.code }} · PROJECT</span>
      <h3>{{ project.title }}</h3>
      <p>{{ project.summary }}</p>
      <RouterLink :to="`/projects/${project.slug}`">打开项目档案 →</RouterLink>
    </article>

    <article v-else-if="activeTab === 'TIMELINE'" class="context-panel">
      <span>GROWTH LEDGER</span>
      <h3>查看项目形成过程</h3>
      <p>时间线记录问题、行动和能力影响，并连接当前项目与证据。</p>
      <RouterLink :to="{ path: '/timeline', query: { project: project.slug } }">
        打开成长时间线 →
      </RouterLink>
    </article>

    <article v-else class="context-panel">
      <span>CURRENT CONTEXT</span>
      <h3>{{ project.title }}</h3>
      <p>当前对话围绕该项目展开。点击回答中的引用，证据列表会同步定位。</p>
    </article>
  </aside>
</template>

<style scoped>
.evidence-desk {
  height: calc(100vh - var(--header-height));
  padding: 28px 25px;
  color: var(--ink);
  border-left: 1px solid var(--rule);
  background: var(--paper-hi);
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
  font: 8px var(--mono);
  letter-spacing: 0.14em;
}

.evidence-tabs {
  display: flex;
  margin: 24px 0 28px;
  gap: 18px;
  border-bottom: 1px solid var(--rule);
}

.evidence-tabs button {
  padding: 0 0 10px;
  color: var(--faint);
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  font: 8px var(--mono);
  letter-spacing: 0.1em;
}

.evidence-tabs button.active {
  color: var(--red);
  border-color: var(--red);
}

.evidence-list article,
.context-panel {
  padding: 18px 0;
  border-top: 1px solid var(--rule);
}

.evidence-list article {
  cursor: pointer;
}

.evidence-list .evidence-card--active {
  margin-inline: -13px;
  padding-inline: 13px;
  border-left: 2px solid var(--red);
  background: var(--paper);
}

article > span {
  color: var(--red);
  font: 8px var(--mono);
  letter-spacing: 0.1em;
}

h3 {
  margin: 9px 0;
  font: 500 17px/1.35 var(--serif);
}

p {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.8;
}

small {
  display: block;
  color: var(--faint);
  font: 8px var(--mono);
  letter-spacing: 0.07em;
}

article a {
  display: inline-block;
  margin-top: 16px;
  color: var(--red);
  font: 9px var(--mono);
}
</style>
