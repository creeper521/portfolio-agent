<script setup lang="ts">
import { computed } from 'vue'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const { portfolio, status, error, retry } = usePublicContent()
const events = computed(() => portfolio.value?.timeline ?? [])
</script>

<template>
  <main v-if="status === 'ready'">
    <PageLead
      code="02 / GROWTH LEDGER"
      title="公开成长时间线"
      description="时间线不只记录日期，而是记录问题如何被识别、决策如何形成、能力如何在验证中变得可复用。"
    />

    <section v-if="events.length" class="timeline-ledger page-shell">
      <article v-for="(event, index) in events" :key="event.id">
        <div class="timeline-ledger__axis">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <time>{{ event.dateLabel }}</time>
        </div>
        <div class="timeline-ledger__body">
          <h2>{{ event.title }}</h2>
          <dl>
            <div><dt>问题</dt><dd>{{ event.problem }}</dd></div>
            <div><dt>行动</dt><dd>{{ event.action }}</dd></div>
            <div><dt>影响</dt><dd>{{ event.impact }}</dd></div>
          </dl>
          <div class="timeline-ledger__links">
            <RouterLink v-for="slug in event.projectSlugs" :key="slug" :to="`/projects/${slug}`">
              查看关联项目 →
            </RouterLink>
            <RouterLink
              v-for="id in event.evidenceIds"
              :key="id"
              :to="{ path: '/evidence', query: { evidence: id } }"
            >
              查看关联证据 →
            </RouterLink>
          </div>
        </div>
      </article>
    </section>

    <div v-else class="page-shell">
      <EmptyDossier title="公开时间线正在整理" description="已有项目仍可从项目目录和完整 Agent 中查看。" />
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
.timeline-ledger {
  padding: 90px 0 130px;
}

article {
  display: grid;
  grid-template-columns: 0.34fr 1fr;
  gap: clamp(36px, 7vw, 110px);
  padding-bottom: 80px;
}

.timeline-ledger__axis {
  position: relative;
  display: grid;
  align-content: start;
  gap: 22px;
  color: var(--red);
  border-right: 1px solid var(--rule);
  font-family: var(--mono);
  font-size: 10px;
}

.timeline-ledger__axis::after {
  position: absolute;
  top: 2px;
  right: -5px;
  width: 9px;
  height: 9px;
  content: '';
  border: 1px solid var(--red);
  background: var(--paper);
  transform: rotate(45deg);
}

.timeline-ledger__axis time {
  color: var(--muted);
  writing-mode: vertical-rl;
}

h2 {
  max-width: 760px;
  margin: 0 0 40px;
  font-family: var(--serif);
  font-size: clamp(36px, 5vw, 64px);
  font-weight: 400;
}

dl {
  margin: 0;
}

dl div {
  display: grid;
  padding: 22px 0;
  grid-template-columns: 110px 1fr;
  border-top: 1px solid var(--rule);
}

dt {
  color: var(--red);
  font-family: var(--mono);
  font-size: 13px;
}

dd {
  margin: 0;
  color: var(--ink-2);
  font-family: var(--serif);
  font-size: 16px;
  line-height: 1.9;
}

.timeline-ledger__links {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-top: 26px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 12px;
}

@media (max-width: 620px) {
  article {
    grid-template-columns: 1fr;
  }

  .timeline-ledger__axis {
    padding-bottom: 14px;
    grid-template-columns: auto 1fr;
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }

  .timeline-ledger__axis time {
    text-align: right;
    writing-mode: initial;
  }

  .timeline-ledger__axis::after {
    right: auto;
    bottom: -5px;
    left: 0;
    top: auto;
  }

  dl div {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}
</style>
