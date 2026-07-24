<script setup lang="ts">
import { computed } from 'vue'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import { buildDossierIndex } from '../features/portfolio/model/dossierIndexModel'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'
import StatusMark from '../shared/components/StatusMark.vue'

const { portfolio, status, error, retry } = usePublicContent()
const groups = computed(() => {
  const data = portfolio.value
  if (!data) return []
  return buildDossierIndex(data.projects, data.cases)
})
const total = computed(() => groups.value.reduce((sum, group) => sum + group.entries.length, 0))
</script>

<template>
  <main v-if="status === 'ready'">
    <PageLead
      code="01 / DOSSIER INDEX"
      title="工程案卷目录"
      description="核心项目、功能修复案例与工具评测统一归档。每项标明类型、贡献方式与核验规模；评测类条目独立分组，与交付物显式区分。"
    />

    <section v-if="total" class="dossier-index">
      <div v-for="group in groups" :key="group.code" class="dossier-group page-shell">
        <header class="dossier-group__head">
          <p>{{ group.code }}</p>
          <h2>{{ group.title }}</h2>
          <p class="dossier-group__note">{{ group.note }}</p>
        </header>

        <div class="dossier-group__list">
          <RouterLink
            v-for="entry in group.entries"
            :key="entry.slug"
            class="dossier-row"
            :to="`/projects/${entry.slug}`"
          >
            <div class="dossier-row__code">
              <span>{{ entry.code }}</span>
              <span
                class="type-tag"
                :data-t="entry.kind === 'PROJECT' ? 'PROJECT' : entry.group"
              >{{ entry.typeLabel }}</span>
            </div>
            <div class="dossier-row__body">
              <h3>{{ entry.title }}</h3>
              <p>{{ entry.summary }}</p>
              <div class="dossier-row__status">
                <StatusMark :status="entry.status" />
                <StatusMark :status="entry.contributionType" />
              </div>
            </div>
            <ul v-if="entry.technologies.length" class="dossier-row__tech" aria-label="技术栈">
              <li v-for="tech in entry.technologies.slice(0, 5)" :key="tech">{{ tech }}</li>
            </ul>
            <i aria-hidden="true">↗</i>
          </RouterLink>
        </div>
      </div>
    </section>

    <div v-else class="page-shell">
      <EmptyDossier title="案卷资料准备中" description="目前还没有可以公开的工程案卷。" />
    </div>
  </main>
  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    @retry="retry"
  />
</template>

<style scoped>
.dossier-index {
  padding: 60px 0 120px;
}

.dossier-group + .dossier-group {
  margin-top: 64px;
}

.dossier-group__head {
  display: grid;
  padding-bottom: 18px;
  grid-template-columns: 0.34fr 1fr 0.58fr;
  gap: clamp(26px, 5vw, 80px);
  align-items: end;
  border-bottom: 1px solid var(--ink);
}

.dossier-group__head p {
  margin: 0;
  color: var(--red);
  font: 9px var(--mono);
  letter-spacing: 0.15em;
}

.dossier-group__head h2 {
  margin: 0;
  font: 400 clamp(30px, 3.5vw, 46px)/1.05 var(--serif);
  letter-spacing: -0.03em;
}

.dossier-group__note {
  margin: 0;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.7;
}

.dossier-row {
  position: relative;
  display: grid;
  padding: 34px 0;
  grid-template-columns: 150px minmax(0, 1fr) 240px;
  gap: clamp(24px, 5vw, 72px);
  border-bottom: 1px solid var(--rule);
  transition: 0.3s var(--ease);
}

.dossier-row:hover {
  background: var(--paper-hi);
}

.dossier-row__code {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: flex-start;
}

.dossier-row__code > span:first-child {
  color: var(--red);
  font: 10px var(--mono);
  letter-spacing: 0.12em;
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: fit-content;
  padding: 3px 7px;
  border: 1px solid var(--rule);
  color: var(--ink-2);
  font: 9px var(--mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.type-tag[data-t='PROJECT'] {
  border-color: var(--red);
  color: var(--red);
}

.type-tag[data-t='DELIVERED'] {
  border-color: var(--ink-2);
  color: var(--ink-2);
}

.type-tag[data-t='EVALUATION'] {
  border-style: dashed;
  border-color: var(--faint);
  color: var(--faint);
}

.dossier-row__body h3 {
  margin: 0;
  font: 400 clamp(24px, 2.8vw, 38px)/1.1 var(--serif);
}

.dossier-row__body p {
  max-width: 640px;
  margin: 14px 0 18px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}

.dossier-row__status {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.dossier-row__tech {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dossier-row__tech li {
  padding: 7px 9px;
  border: 1px solid var(--rule);
  font: 9px var(--mono);
  letter-spacing: 0.04em;
}

.dossier-row > i {
  position: absolute;
  right: 0;
  bottom: 34px;
  color: var(--red);
  font-style: normal;
}

@media (max-width: 760px) {
  .dossier-row {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .dossier-group__head {
    grid-template-columns: 1fr;
  }
}
</style>
