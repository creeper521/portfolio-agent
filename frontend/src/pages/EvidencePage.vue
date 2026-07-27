<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import DossierFooter from '../shared/components/DossierFooter.vue'
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

// 索引小结卡：纯展示层派生，从当前页可见证据收口左列底部死区。
// 覆盖项目/案例数取证据 projectSlugs 并集（去重），最近更新月份取 periodEnd 最大值。
const summary = computed(() => {
  const items = evidence.value
  const coveredSlugs = new Set<string>()
  items.forEach((item) => item.projectSlugs.forEach((slug) => coveredSlugs.add(slug)))
  const latestEnd = items
    .map((item) => item.periodEnd)
    .filter(Boolean)
    .sort()
    .at(-1)
  const latestMonth = latestEnd ? latestEnd.slice(0, 7).replace('-', '.') : '—'
  return {
    count: items.length,
    covered: coveredSlugs.size,
    latestMonth,
  }
})
</script>

<template>
  <main v-if="status === 'ready' && portfolio">
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
          :aria-current="selected?.id === item.id ? 'true' : undefined"
          :data-selected-evidence="selected?.id === item.id ? '' : undefined"
          @click="selectedId = item.id"
        >
          <span>{{ item.code }}</span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.periodStart }} — {{ item.periodEnd }}</small>
        </button>
        <div class="evidence-catalog__summary" data-evidence-summary>
          <span>INDEX SUMMARY</span>
          <dl>
            <div><dt>本页证据</dt><dd>{{ summary.count }}</dd></div>
            <div><dt>覆盖项目</dt><dd>{{ summary.covered }}</dd></div>
            <div><dt>最近更新</dt><dd>{{ summary.latestMonth }}</dd></div>
          </dl>
        </div>
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

    <DossierFooter :content-version="portfolio.contentVersion" />
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
  grid-template-columns: minmax(280px, 0.38fr) minmax(0, 0.82fr);
  gap: clamp(36px, 6vw, 90px);
  align-items: start;
}

.evidence-catalog__list > p,
.evidence-preview header p,
.evidence-preview section > p {
  margin: 0 0 20px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 10px;
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

.evidence-catalog__list button[aria-current='true'] {
  padding-inline: 18px;
  color: var(--paper);
  background: var(--ink);
  box-shadow: inset 3px 0 var(--red);
}

.evidence-catalog__list span,
.evidence-catalog__list small {
  font-family: var(--mono);
  font-size: 10px;
}

.evidence-catalog__list strong {
  margin: 10px 0;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 400;
}

/* 索引小结卡：收口左列底部死区。等宽体小号，纯派生数据，不新增事实。
   结构性中性标签（本页证据/覆盖项目/最近更新）退出红色，回到墨色层级。 */
.evidence-catalog__summary {
  margin-top: 28px;
  padding: 16px 0 0;
  border-top: 1px solid var(--rule);
}

.evidence-catalog__summary span {
  display: block;
  margin-bottom: 14px;
  color: var(--ink-2);
  font: 10px var(--mono);
  letter-spacing: 0.14em;
}

.evidence-catalog__summary dl {
  display: grid;
  margin: 0;
  gap: 8px;
}

.evidence-catalog__summary div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
}

.evidence-catalog__summary dt {
  color: var(--muted);
  font: 11px var(--mono);
  letter-spacing: 0.04em;
}

.evidence-catalog__summary dd {
  margin: 0;
  color: var(--ink);
  font: 600 13px var(--mono);
  font-variant-numeric: tabular-nums;
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
