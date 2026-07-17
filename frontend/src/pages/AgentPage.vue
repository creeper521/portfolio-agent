<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AgentWorkspace from '../features/agent/components/AgentWorkspace.vue'
import { createPreviewAnswer } from '../features/agent/data/previewAnswers'
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
</script>

<template>
  <AgentWorkspace
    v-if="status === 'ready' && portfolio"
    :portfolio="portfolio"
    :initial-role="initialRole()"
    :initial-project="queryString('project')"
    :initial-evidence="queryString('evidence')"
    :initial-seed="initialSeed"
  />
  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    @retry="retry"
  />
</template>
