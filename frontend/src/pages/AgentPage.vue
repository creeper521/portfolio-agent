<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import AgentWorkspace from '../features/agent/components/AgentWorkspace.vue'
import { createPreviewAnswer } from '../features/agent/data/previewAnswers'
import type { AgentRouteSeed } from '../features/agent/model/sessionTypes'
import type {
  AudienceRole,
  PublicPortfolio,
} from '../features/public-content/model/publicContentTypes'
import { publicContentRepository } from '../features/public-content/repository/publicContentRepository'

const route = useRoute()
const portfolio = ref<PublicPortfolio | null>(null)

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

const initialSeed = computed<AgentRouteSeed | null>(() => {
  const content = portfolio.value
  const project = selectedProject.value
  const question = queryString('question')
  if (!content || !project || !question) return null

  const answer = createPreviewAnswer(
    question,
    initialRole(),
    project,
    content.evidence,
  )
  const source = queryString('source')
  return {
    role: initialRole(),
    question,
    answer: answer.content,
    projectSlug: project.slug,
    evidenceIds: answer.evidenceIds,
    source:
      source === 'PROJECT' || source === 'EVIDENCE'
        ? source
        : 'HOME',
  }
})

onMounted(async () => {
  portfolio.value = await publicContentRepository.getPortfolio()
})
</script>

<template>
  <AgentWorkspace
    v-if="portfolio"
    :portfolio="portfolio"
    :initial-role="initialRole()"
    :initial-project="queryString('project')"
    :initial-evidence="queryString('evidence')"
    :initial-seed="initialSeed"
  />
</template>
