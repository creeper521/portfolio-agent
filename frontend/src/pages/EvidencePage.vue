<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'
import StatusMark from '../shared/components/StatusMark.vue'

const route = useRoute()
const { portfolio, status, error, retry } = usePublicContent()
const projectFilter = computed(() =>
  typeof route.query.project === 'string' ? route.query.project : '',
)
const evidence = computed(() => (portfolio.value?.evidence ?? []).filter(
  (item) => !projectFilter.value || item.projectSlugs.includes(projectFilter.value),
))
const selectedId = ref(typeof route.query.evidence === 'string' ? route.query.evidence : '')

const selected = computed(
  () => selectedId.value
    ? evidence.value.find((item) => item.id === selectedId.value) ?? null
    : evidence.value[0] ?? null,
)
const selectedClaims = computed(() => selected.value
  ? selected.value.claimIds
      .map((claimId) => portfolio.value?.claims.find((claim) => claim.id === claimId))
      .filter((claim) => claim !== undefined)
  : [])
</script>

<template>
  <main v-if="status === 'ready'">
    <PageLead
      code="03 / EVIDENCE DESK"
      title="证据中心"
      description="只展示经过公开审查的脱敏索引。原始日报、内部截图、私有路径与未批准材料不会进入页面。"
    />

    <section v-if="evidence.length" class="evidence-catalog page-shell">
      <div class="evidence-catalog__list">
        <p>APPROVED INDEX</p>
        <button
          v-for="item in evidence"
          :key="item.id"
          type="button"
          :aria-pressed="selected?.id === item.id"
          :data-selected-evidence="selected?.id === item.id ? '' : undefined"
          @click="selectedId = item.id"
        >
          <span>{{ item.code }}</span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.periodStart }} — {{ item.periodEnd }}</small>
        </button>
      </div>

      <article v-if="selected" class="evidence-preview">
        <header>
          <div>
            <p>{{ selected.code }} · {{ selected.type }}</p>
            <h2>{{ selected.title }}</h2>
          </div>
          <StatusMark :status="selected.publicStatus" />
        </header>

        <blockquote>{{ selected.summary }}</blockquote>

        <dl>
          <div><dt>公开周期</dt><dd>{{ selected.periodStart }} — {{ selected.periodEnd }}</dd></div>
          <div><dt>脱敏来源</dt><dd>{{ selected.sourceCount }} 项来源汇总</dd></div>
          <div><dt>公开边界</dt><dd>只公开索引和摘要，不公开原始内容。</dd></div>
        </dl>

        <section>
          <p>SUPPORTED CLAIMS</p>
          <ol>
            <li v-for="claim in selectedClaims" :key="claim.id">{{ claim.statement }}</li>
          </ol>
        </section>

        <footer>
          <RouterLink
            v-for="slug in selected.projectSlugs"
            :key="slug"
            :to="`/projects/${slug}`"
          >
            打开关联项目 →
          </RouterLink>
          <RouterLink :to="{ path: '/agent', query: { evidence: selected.id } }">
            围绕该证据继续提问 →
          </RouterLink>
        </footer>
      </article>
      <div v-else class="evidence-preview" data-invalid-evidence role="status">
        <EmptyDossier title="未找到该公开证据" description="该引用无效，或不属于当前项目筛选范围。" />
      </div>
    </section>

    <div v-else class="page-shell">
      <EmptyDossier title="证明材料尚未公开" description="公开审查完成后，证据索引会出现在这里。" />
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
.evidence-catalog {
  display: grid;
  min-height: 680px;
  padding: 70px 0 120px;
  grid-template-columns: minmax(280px, 0.38fr) minmax(0, 1fr);
  gap: clamp(36px, 6vw, 90px);
}

.evidence-catalog__list > p,
.evidence-preview header p,
.evidence-preview section > p {
  margin: 0 0 20px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.evidence-catalog__list button {
  display: grid;
  width: 100%;
  padding: 22px 0;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  border: 0;
  border-top: 1px solid var(--rule);
  background: transparent;
}

.evidence-catalog__list button:last-child {
  border-bottom: 1px solid var(--rule);
}

.evidence-catalog__list button[aria-pressed='true'] {
  padding-inline: 18px;
  color: var(--paper);
  background: var(--ink);
  box-shadow: inset 3px 0 var(--red);
}

.evidence-catalog__list span,
.evidence-catalog__list small {
  font-family: var(--mono);
  font-size: 8px;
}

.evidence-catalog__list strong {
  margin: 10px 0;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 400;
}

.evidence-preview {
  padding: clamp(32px, 5vw, 64px);
  border: 1px solid var(--rule);
  background: var(--paper-hi);
}

.evidence-preview header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
}

.evidence-preview :deep(.status-mark) {
  flex: 0 0 auto;
  white-space: nowrap;
}

h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(32px, 4vw, 52px);
  font-weight: 400;
}

blockquote {
  margin: 54px 0;
  padding-left: 24px;
  color: var(--ink-2);
  border-left: 2px solid var(--red);
  font-family: var(--serif);
  font-size: 18px;
  line-height: 1.9;
}

dl {
  margin: 0 0 54px;
}

dl div {
  display: grid;
  padding: 16px 0;
  grid-template-columns: 130px 1fr;
  border-top: 1px solid var(--rule);
}

dt {
  color: var(--red);
  font-family: var(--mono);
  font-size: 13px;
}

dd {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

ol {
  margin: 0;
  padding: 0;
  list-style: none;
}

li {
  padding: 16px 0;
  color: var(--ink-2);
  border-top: 1px solid var(--rule);
  font-family: var(--serif);
  line-height: 1.7;
}

.evidence-preview footer {
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
  margin-top: 46px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 12px;
}

@media (max-width: 820px) {
  .evidence-catalog {
    grid-template-columns: 1fr;
  }
}
</style>
