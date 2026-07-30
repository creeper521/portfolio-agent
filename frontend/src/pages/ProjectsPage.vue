<script setup lang="ts">
import { computed } from 'vue'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import {
  CAREER_TRACK_LABEL,
  PROJECT_NATURE_LABEL,
  buildProjectMainlines,
} from '../features/portfolio/model/projectMainlineModel'
import DossierFooter from '../shared/components/DossierFooter.vue'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'
import StatusMark from '../shared/components/StatusMark.vue'

const { portfolio, status, error, action, retryAfterSeconds, retry } = usePublicContent()
const groups = computed(() => {
  const data = portfolio.value
  if (!data) return []
  return buildProjectMainlines(data.projects)
})
const total = computed(() => groups.value.reduce((sum, group) => sum + group.projects.length, 0))
</script>

<template>
  <main v-if="status === 'ready' && portfolio">
    <PageLead
      code="01 / PROJECT MAINLINES"
      title="项目主线"
      description="Java 后端与 Agent 两个求职方向并列成架，不互相隐藏。每个项目标明方向、性质、成熟度与贡献方式；具体任务与问题处理沉入项目详情与案例索引，从这里逐层下钻。"
    />

    <section v-if="total" class="mainline-index">
      <div v-for="group in groups" :key="group.key" class="dossier-group page-shell">
        <header class="dossier-group__head">
          <p class="dossier-group__code">{{ group.code }}</p>
          <h2>{{ group.label }}</h2>
          <p class="dossier-group__note">{{ group.note }}</p>
        </header>

        <div class="dossier-group__list">
          <RouterLink
            v-for="project in group.projects"
            :key="project.slug"
            class="mainline-card"
            :to="`/projects/${project.slug}`"
          >
            <div class="mainline-card__meta">
              <span class="type-tag" :data-t="project.careerTrack">{{
                CAREER_TRACK_LABEL[project.careerTrack]
              }}</span>
              <span class="type-tag">{{ PROJECT_NATURE_LABEL[project.projectNature] }}</span>
            </div>
            <div class="mainline-card__body">
              <h3>{{ project.title }}</h3>
              <p>{{ project.summary }}</p>
              <div class="mainline-card__status">
                <StatusMark :status="project.status" />
                <StatusMark :status="project.contributionType" />
                <span v-if="project.caseCount > 0" class="mainline-card__cases"
                  >{{ project.caseCount }} 个案例</span
                >
              </div>
            </div>
            <ul v-if="project.technologies.length" class="mainline-card__tech" aria-label="核心技术">
              <li v-for="tech in project.technologies.slice(0, 5)" :key="tech">{{ tech }}</li>
            </ul>
            <span class="mainline-card__entry">调阅案卷 <i aria-hidden="true">↗</i></span>
          </RouterLink>
        </div>
      </div>
    </section>

    <div v-else class="page-shell">
      <EmptyDossier title="案卷资料准备中" description="目前还没有可以公开的项目。" />
    </div>

    <DossierFooter :content-version="portfolio.contentVersion" />
  </main>
  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    :action="action"
    :retry-after-seconds="retryAfterSeconds"
    @retry="retry"
  />
</template>

<style scoped>
.mainline-index {
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

.dossier-group__code {
  margin: 0;
  color: var(--red);
  font: 10px var(--mono);
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

.mainline-card {
  position: relative;
  display: grid;
  padding: 34px 0;
  grid-template-columns: 150px minmax(0, 1fr) 240px;
  gap: clamp(24px, 5vw, 72px);
  border-bottom: 1px solid var(--rule);
  color: inherit;
  text-decoration: none;
  transition: 0.3s var(--ease);
}

.mainline-card:hover {
  background: var(--paper-hi);
}

.mainline-card__meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  width: fit-content;
  padding: 3px 7px;
  border: 1px solid var(--rule);
  color: var(--ink-2);
  font: 10px var(--mono);
  letter-spacing: 0.08em;
}

.type-tag[data-t='AGENT'] {
  border-color: var(--red);
  color: var(--red);
}

.mainline-card__body h3 {
  margin: 0;
  font: 400 clamp(24px, 2.8vw, 38px)/1.1 var(--serif);
}

.mainline-card__body p {
  max-width: 640px;
  margin: 14px 0 18px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}

.mainline-card__status {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: center;
}

.mainline-card__cases {
  color: var(--muted);
  font: 10px var(--mono);
  letter-spacing: 0.06em;
}

.mainline-card__tech {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  margin: 0;
  padding: 0;
  list-style: none;
}

.mainline-card__tech li {
  padding: 7px 9px;
  border: 1px solid var(--rule);
  font: 10px var(--mono);
  letter-spacing: 0.04em;
}

.mainline-card__entry {
  position: absolute;
  right: 0;
  bottom: 34px;
  color: var(--red);
  font: 10px var(--mono);
  letter-spacing: 0.1em;
}

.mainline-card__entry i {
  font-style: normal;
}

@media (max-width: 760px) {
  .mainline-card {
    grid-template-columns: 1fr;
    gap: 18px;
    padding-bottom: 64px;
  }

  .dossier-group__head {
    grid-template-columns: 1fr;
  }
}
</style>
