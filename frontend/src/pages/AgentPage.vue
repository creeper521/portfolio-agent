<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AgentWorkspace from '../features/agent/components/AgentWorkspace.vue'
import {
  consumeAgentHandoff,
  consumeCaseAgentHandoff,
} from '../features/agent/model/handoffStore'
import type { AgentRouteSeed } from '../features/agent/model/sessionTypes'
import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import type { AudienceRole } from '../features/public-content/model/publicContentTypes'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const route = useRoute()
const router = useRouter()
const { portfolio, status, error, action, retryAfterSeconds, retry } = usePublicContent()

function queryString(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const requestedHandoffId = queryString('handoffId')
const handoffSeed = consumeAgentHandoff(requestedHandoffId)
const requestedCaseHandoffId = queryString('caseHandoffId')
const caseHandoff = consumeCaseAgentHandoff(requestedCaseHandoffId)
const invalidHandoff = Boolean(
  (requestedHandoffId && !handoffSeed) ||
  (requestedCaseHandoffId && !caseHandoff),
)
if (
  route.query.handoffId ||
  route.query.caseHandoffId ||
  route.query.question ||
  route.query.q ||
  route.query.case ||
  route.query.source
) {
  void router.replace({ path: '/agent' })
}

function initialRole(): AudienceRole {
  const role = handoffSeed?.role ?? queryString('role')
  return ['INTERVIEWER', 'MENTOR', 'HR', 'GUEST'].includes(role)
    ? (role as AudienceRole)
    : 'INTERVIEWER'
}

const initialSeed = ref<AgentRouteSeed | null>(handoffSeed)

function navigateToPortfolio() {
  void router.push({ path: '/projects' })
}
</script>

<template>
  <PublicContentFeedback
    v-if="status === 'loading' || status === 'error'"
    class="agent-route-feedback"
    :status="status"
    :message="error"
    :action="action"
    :retry-after-seconds="retryAfterSeconds"
    @retry="retry"
  />
  <AgentWorkspace
    v-else-if="status === 'ready' && portfolio && !invalidHandoff"
    :portfolio="portfolio"
    :initial-role="initialRole()"
    :initial-project="queryString('project')"
    :initial-evidence="queryString('evidence')"
    :initial-seed="initialSeed"
    :initial-case="caseHandoff?.caseSlug ?? ''"
    :initial-question="caseHandoff?.question ?? ''"
    @navigate-portfolio="navigateToPortfolio"
  />
  <section v-else-if="status === 'ready' && portfolio" class="route-seed-feedback" data-invalid-handoff role="status">
    <p>这次页面内交接已失效或已被使用。</p>
    <RouterLink to="/agent">开始新的临时对话</RouterLink>
  </section>
</template>

<style scoped>
.route-seed-feedback {
  display: grid;
  min-height: 100%;
  place-content: center;
  gap: 18px;
  color: var(--ink);
  background: var(--paper-hi);
  font: 12px/1.7 var(--mono);
}

.route-seed-feedback p {
  margin: 0;
}

.route-seed-feedback a {
  justify-self: center;
  padding: 9px 13px;
  color: var(--red);
  border: 1px solid currentcolor;
  background: transparent;
  font: 10px var(--mono);
}

.agent-route-feedback {
  min-height: 100%;
}
</style>
