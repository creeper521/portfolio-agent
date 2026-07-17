<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { askQuestion } from '../features/agent/api/answerApi'
import AgentWorkspace from '../features/agent/components/AgentWorkspace.vue'
import { mapAnswerResponse } from '../features/agent/model/mapAnswerResponse'
import type { AgentRouteSeed } from '../features/agent/model/sessionTypes'
import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import type { AudienceRole } from '../features/public-content/model/publicContentTypes'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const route = useRoute()
const { portfolio, status, error, retry } = usePublicContent()

function queryString(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function initialRole(): AudienceRole {
  const role = queryString('role')
  return ['INTERVIEWER', 'MENTOR', 'HR', 'GUEST'].includes(role)
    ? (role as AudienceRole)
    : 'INTERVIEWER'
}

const selectedProject = computed(() => {
  const content = portfolio.value
  if (!content) return null
  return (
    content.projects.find((project) => project.slug === queryString('project')) ??
    content.projects[0] ??
    null
  )
})

const initialSeed = ref<AgentRouteSeed | null>(null)
const seedStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const seedError = ref('')
let seedRequest = 0

async function loadRouteSeed() {
  const request = ++seedRequest
  const content = portfolio.value
  const project = selectedProject.value
  const question = queryString('question').trim()
  if (status.value !== 'ready' || !content) {
    initialSeed.value = null
    seedError.value = ''
    seedStatus.value = 'idle'
    return
  }
  if (!project || !question) {
    initialSeed.value = null
    seedError.value = ''
    seedStatus.value = 'ready'
    return
  }

  seedStatus.value = 'loading'
  seedError.value = ''
  try {
    const mapped = mapAnswerResponse(await askQuestion(project.slug, question))
    if (request !== seedRequest) return
    const source = queryString('source')
    initialSeed.value = {
      role: initialRole(),
      question,
      answer: mapped.content,
      projectSlug: project.slug,
      evidenceIds: mapped.evidenceIds,
      source:
        source === 'PROJECT' || source === 'EVIDENCE'
          ? source
          : 'HOME',
    }
    seedStatus.value = 'ready'
  } catch {
    if (request !== seedRequest) return
    seedError.value = 'Agent 暂时无法回答，请稍后重试'
    seedStatus.value = 'error'
  }
}

watch(
  () => [
    status.value,
    portfolio.value,
    selectedProject.value?.slug,
    queryString('question'),
    queryString('role'),
    queryString('source'),
  ],
  () => {
    void loadRouteSeed()
  },
  { immediate: true },
)
</script>

<template>
  <PublicContentFeedback
    v-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    @retry="retry"
  />
  <AgentWorkspace
    v-else-if="status === 'ready' && portfolio && seedStatus === 'ready'"
    :portfolio="portfolio"
    :initial-role="initialRole()"
    :initial-project="queryString('project')"
    :initial-evidence="queryString('evidence')"
    :initial-seed="initialSeed"
  />
  <section
    v-else-if="status === 'ready' && portfolio && seedStatus === 'loading'"
    class="route-seed-feedback"
    role="status"
  >
    正在核对公开事实…
  </section>
  <section
    v-else-if="status === 'ready' && portfolio && seedStatus === 'error'"
    class="route-seed-feedback route-seed-feedback--error"
    role="alert"
  >
    <p>{{ seedError }}</p>
    <button data-answer-retry type="button" @click="loadRouteSeed">重新回答</button>
  </section>
</template>

<style scoped>
.route-seed-feedback {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  place-content: center;
  gap: 18px;
  color: var(--paper);
  background: var(--ink);
  font: 12px/1.7 var(--mono);
}

.route-seed-feedback p {
  margin: 0;
}

.route-seed-feedback button {
  justify-self: center;
  padding: 9px 13px;
  color: #e1948b;
  border: 1px solid currentcolor;
  background: transparent;
  font: 10px var(--mono);
}
</style>
