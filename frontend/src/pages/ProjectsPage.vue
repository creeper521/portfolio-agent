<script setup lang="ts">
import { onMounted, ref } from 'vue'

import type { PublicProject } from '../features/public-content/model/publicContentTypes'
import { publicContentRepository } from '../features/public-content/repository/publicContentRepository'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import StatusMark from '../shared/components/StatusMark.vue'

const projects = ref<PublicProject[]>([])

onMounted(async () => {
  projects.value = await publicContentRepository.getProjects()
})
</script>

<template>
  <main>
    <PageLead
      code="01 / PROJECT DOSSIERS"
      title="工程案卷目录"
      description="每个项目都从问题、职责、技术决策和证据四个方向展开；没有公开的内容不会被想象补齐。"
    />

    <section v-if="projects.length" class="project-index page-shell">
      <RouterLink
        v-for="project in projects"
        :key="project.slug"
        class="project-index__item"
        :to="`/projects/${project.slug}`"
      >
        <span class="project-index__code">{{ project.code }}</span>
        <div>
          <h2>{{ project.title }}</h2>
          <p>{{ project.summary }}</p>
          <div class="project-index__status">
            <StatusMark :status="project.status" />
            <StatusMark :status="project.contributionType" />
          </div>
        </div>
        <ul aria-label="技术栈">
          <li v-for="technology in project.technologies.slice(0, 5)" :key="technology">
            {{ technology }}
          </li>
        </ul>
        <i aria-hidden="true">↗</i>
      </RouterLink>
    </section>

    <div v-else class="page-shell">
      <EmptyDossier title="项目资料准备中" description="目前还没有可以公开的项目案卷。" />
    </div>
  </main>
</template>

<style scoped>
.project-index {
  padding: 70px 0 120px;
}

.project-index__item {
  position: relative;
  display: grid;
  padding: 36px 0;
  grid-template-columns: 140px minmax(0, 1fr) 320px;
  gap: clamp(24px, 5vw, 72px);
  border-bottom: 1px solid var(--rule);
  transition: 0.3s var(--ease);
}

.project-index__item:hover {
  background: var(--paper-hi);
  transform: translateY(-4px);
}

.project-index__item:first-child {
  border-top: 1px solid var(--rule);
}

.project-index__code {
  color: var(--red);
  font-family: var(--mono);
  font-size: 10px;
}

h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(27px, 3.5vw, 45px);
  font-weight: 400;
}

p {
  max-width: 720px;
  margin: 16px 0 22px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}

.project-index__status {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

ul {
  display: flex;
  margin: 0;
  padding: 0;
  align-content: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
}

li {
  padding: 7px 9px;
  border: 1px solid var(--rule);
  font-family: var(--mono);
  font-size: 8px;
}

.project-index__item > i {
  position: absolute;
  right: 0;
  bottom: 34px;
  color: var(--red);
  font-style: normal;
}

@media (max-width: 760px) {
  .project-index__item {
    grid-template-columns: 1fr;
    gap: 18px;
  }
}
</style>
