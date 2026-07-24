<script setup lang="ts">
import { computed } from 'vue'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import { resolveDossier } from '../features/portfolio/model/dossierModel'
import {
  buildSectionTraces,
  type SectionAnchor,
} from '../features/portfolio/model/sectionTrace'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'
import StatusMark from '../shared/components/StatusMark.vue'

const props = defineProps<{ slug: string }>()
const { portfolio, status, error, retry } = usePublicContent()

const dossier = computed(() => {
  const data = portfolio.value
  if (!data) return null
  return resolveDossier(props.slug, data.projects, data.cases)
})

const evidenceTarget = computed(() => {
  const slug = dossier.value?.kind === 'PROJECT' ? dossier.value.slug : (dossier.value?.slug ?? '')
  return { path: '/evidence', query: { project: slug } }
})

// 段落追溯信封：每个锚点对应几条已核验断言、直连几条脱敏证据。
// null 表示该段落无对应 claim（case 常见），模板据此不渲染脚注。
const traces = computed(() => {
  const data = portfolio.value
  const current = dossier.value
  if (!data || !current) return null
  return buildSectionTraces(current.subjectId, data.claims, data.claimEvidenceLinks)
})

function traceFor(anchor: SectionAnchor) {
  return traces.value?.[anchor] ?? null
}
</script>

<template>
  <main v-if="status === 'ready' && dossier" class="project-dossier">
    <header class="project-cover">
      <div class="page-shell project-cover__grid">
        <div class="project-cover__code">
          <span>{{ dossier.code }}</span>
          <StatusMark :status="dossier.status.status" />
          <StatusMark :status="dossier.status.contributionType" />
        </div>
        <div class="project-cover__copy">
          <p>{{ dossier.kind === 'PROJECT' ? 'ENGINEERING PROJECT RECORD' : 'ENGINEERING CASE RECORD' }}</p>
          <h1 data-mobile-balanced>{{ dossier.title }}</h1>
          <blockquote>{{ dossier.summary }}</blockquote>
        </div>
        <ul>
          <li v-for="technology in dossier.technologies" :key="technology">{{ technology }}</li>
        </ul>
      </div>
    </header>

    <div class="project-body page-shell">
      <aside class="project-toc">
        <span>CONTENTS</span>
        <a v-for="section in dossier.sections" :key="section.anchor" :href="`#${section.anchor}`">
          {{ section.code }} {{ section.title }}
        </a>
      </aside>

      <div class="project-story">
        <section id="why">
          <p class="section-code">01 / WHY</p>
          <h2>为什么做</h2>
          <p class="story-lead">{{ dossier.problem }}</p>
          <p v-if="traceFor('why')" class="section-trace">
            对应 <em>{{ traceFor('why')!.claims }}</em> 条已核验断言，直连 <em>{{ traceFor('why')!.evidence }}</em> 条脱敏证据
            <RouterLink :to="evidenceTarget">证据 →</RouterLink>
          </p>
        </section>

        <section id="role">
          <p class="section-code">02 / RESPONSIBILITY</p>
          <h2>我的职责</h2>
          <ol>
            <li v-for="item in dossier.responsibilities" :key="item">{{ item }}</li>
          </ol>
          <p v-if="traceFor('role')" class="section-trace">
            对应 <em>{{ traceFor('role')!.claims }}</em> 条已核验断言，直连 <em>{{ traceFor('role')!.evidence }}</em> 条脱敏证据
            <RouterLink :to="evidenceTarget">证据 →</RouterLink>
          </p>
        </section>

        <section id="how" class="project-story__dark">
          <p class="section-code">03 / SOLUTION</p>
          <h2>如何做</h2>
          <p class="story-lead">{{ dossier.solution }}</p>
          <h3>关键决策</h3>
          <ol>
            <li v-for="item in dossier.decisions" :key="item">{{ item }}</li>
          </ol>
          <p v-if="traceFor('how')" class="section-trace">
            对应 <em>{{ traceFor('how')!.claims }}</em> 条已核验断言，直连 <em>{{ traceFor('how')!.evidence }}</em> 条脱敏证据
            <RouterLink :to="evidenceTarget">证据 →</RouterLink>
          </p>
        </section>

        <section id="proof">
          <p class="section-code">04 / VERIFICATION</p>
          <h2>如何证明</h2>
          <ol>
            <li v-for="item in dossier.verification" :key="item">{{ item }}</li>
          </ol>
          <RouterLink class="evidence-link" :to="evidenceTarget">打开关联证据 →</RouterLink>
          <p v-if="traceFor('proof')" class="section-trace">
            对应 <em>{{ traceFor('proof')!.claims }}</em> 条已核验断言，直连 <em>{{ traceFor('proof')!.evidence }}</em> 条脱敏证据
            <RouterLink :to="evidenceTarget">证据 →</RouterLink>
          </p>
        </section>

        <section id="status">
          <p class="section-code">05 / STATUS</p>
          <h2>最终状态</h2>
          <p class="story-lead">{{ dossier.outcome }}</p>
          <p v-if="dossier.boundary">{{ dossier.boundary }}</p>
          <p v-if="traceFor('status')" class="section-trace">
            对应 <em>{{ traceFor('status')!.claims }}</em> 条已核验断言，直连 <em>{{ traceFor('status')!.evidence }}</em> 条脱敏证据
            <RouterLink :to="evidenceTarget">证据 →</RouterLink>
          </p>
        </section>

        <footer class="project-next">
          <RouterLink :to="{ path: '/timeline', query: { project: dossier.slug } }">查看成长时间线</RouterLink>
          <RouterLink :to="{ path: '/agent', query: { project: dossier.slug } }">针对这个项目继续提问</RouterLink>
        </footer>
      </div>
    </div>
  </main>

  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    @retry="retry"
  />

  <main v-else-if="status === 'ready' && !dossier" class="page-shell">
    <EmptyDossier
      code="UNPUBLISHED"
      title="该案卷尚未公开"
      description="这个地址没有对应的公开项目或案例，或相关资料仍在审核中。"
    >
      <RouterLink to="/projects">返回案卷目录 →</RouterLink>
    </EmptyDossier>
  </main>
</template>

<style scoped>
.project-cover {
  padding: 92px 0 76px;
  color: var(--paper);
  background: var(--ink);
}

.project-cover__grid {
  display: grid;
  grid-template-columns: 0.26fr 1fr 0.35fr;
  gap: clamp(28px, 5vw, 72px);
}

.project-cover__grid > * {
  min-width: 0;
}

.project-cover__code {
  display: flex;
  align-content: flex-start;
  flex-direction: column;
  gap: 14px;
}

.project-cover__code > span,
.project-cover__copy > p {
  color: var(--red-hi);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}

h1 {
  margin: 24px 0 28px;
  font-family: var(--serif);
  font-size: clamp(48px, 6vw, 88px);
  font-weight: 400;
  line-height: 1.02;
  letter-spacing: -0.04em;
}

blockquote {
  max-width: 760px;
  margin: 0;
  color: #cfc5b7;
  font-family: var(--serif);
  font-size: 20px;
  line-height: 1.7;
}

.project-cover ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.project-cover li {
  padding: 10px 0;
  color: #a99f91;
  border-bottom: 1px solid #4a433b;
  font-family: var(--mono);
  font-size: 10px;
}

.project-body {
  display: grid;
  grid-template-columns: 240px minmax(0, 860px);
  gap: clamp(48px, 8vw, 130px);
  justify-content: center;
  padding-top: 90px;
  padding-bottom: 130px;
}

.project-toc {
  position: sticky;
  top: calc(var(--header-height) + 30px);
  display: grid;
  height: fit-content;
}

.project-toc span {
  margin-bottom: 18px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 10px;
}

.project-toc a {
  padding: 12px 0;
  color: var(--muted);
  border-bottom: 1px solid var(--rule);
  font-size: 13px;
}

.project-story {
  min-width: 0;
}

.project-story section {
  padding: 0 0 84px;
  scroll-margin-top: 100px;
}

.project-story section + section:not(.project-story__dark) {
  padding-top: 76px;
  border-top: 1px solid var(--rule);
}

.section-code {
  margin: 0 0 16px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}

h2 {
  margin: 0 0 28px;
  font-family: var(--serif);
  font-size: clamp(36px, 4vw, 54px);
  font-weight: 400;
}

h3 {
  margin: 44px 0 18px;
  font-family: var(--serif);
  font-size: 25px;
  font-weight: 400;
}

.story-lead,
.project-story li,
.project-story section > p:last-child {
  color: var(--ink-2);
  font-family: var(--serif);
  font-size: 17px;
  line-height: 2;
}

/* 段落追溯信封：衬线引语式批注，右对齐，数字微微加重。
   与段落正文同源（serif），像编辑用铅笔在段落旁写的注释，
   而非外挂数据标签。无断言的段落不渲染（v-if 控制）。 */
.section-trace {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px;
  margin: 20px 0 0;
  font: italic 15px/1.6 var(--serif);
  color: var(--muted);
}

.section-trace em {
  font-style: normal;
  font-weight: 500;
  color: var(--red);
  font-variant-numeric: tabular-nums;
}

.section-trace a {
  align-self: center;
  font: 10px var(--mono);
  letter-spacing: 0.06em;
  color: var(--red);
}

/* 黑底段落里的追溯行用暖色，避免在深底上发灰 */
.project-story__dark .section-trace {
  color: #a99f91;
}

.project-story__dark .section-trace em {
  color: var(--red-hi);
}

ol {
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: dossier;
}

li {
  position: relative;
  padding: 18px 0 18px 54px;
  border-top: 1px solid var(--rule);
  counter-increment: dossier;
}

li::before {
  position: absolute;
  left: 0;
  content: counter(dossier, decimal-leading-zero);
  color: var(--red);
  font-family: var(--mono);
  font-size: 10px;
}

.project-story__dark {
  margin: 0 0 84px;
  padding: 64px 50px !important;
  color: var(--paper);
  background: var(--ink);
}

/* 侧边留白（--gutter = 4vw）在 1250px 以上才容得下 ±50px 的突破宽度 */
@media (min-width: 1250px) {
  .project-story__dark {
    width: calc(100% + 100px);
    margin-left: -50px;
  }
}

.project-story__dark .story-lead,
.project-story__dark li {
  color: #d2c8bb;
}

.project-story__dark li {
  border-color: #51493f;
}

.evidence-link {
  display: inline-block;
  margin-top: 30px;
  padding-bottom: 7px;
  color: var(--red);
  border-bottom: 1px solid var(--red);
  font-family: var(--mono);
  font-size: 12px;
}

.project-next {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.project-next a {
  padding: 26px;
  font-family: var(--serif);
}

.project-next a:first-child {
  border-right: 1px solid var(--rule);
}

@media (max-width: 900px) {
  .project-cover__grid,
  .project-body {
    grid-template-columns: 1fr;
  }

  .project-toc {
    position: static;
    grid-template-columns: repeat(5, 1fr);
  }

  .project-toc span {
    grid-column: 1 / -1;
  }

  .project-story__dark {
    width: auto;
    margin-inline: calc(var(--page-gutter) * -0.5);
  }
}

@media (max-width: 620px) {
  .project-cover h1 {
    overflow-wrap: anywhere;
    font-size: clamp(42px, 13vw, 58px);
    line-height: 1.04;
    text-wrap: balance;
    word-break: normal;
  }

  .project-toc {
    display: none;
  }

  .project-next {
    grid-template-columns: 1fr;
  }

  .project-next a:first-child {
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
}
</style>
