<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import { createCaseAgentHandoff } from '../features/agent/model/handoffStore'
import {
  ACHIEVEMENT_STATUS_LABEL,
  CASE_TYPE_LABEL,
  CONTRIBUTION_LABEL,
  caseStatusGroup,
} from '../features/portfolio/model/caseListModel'
import DossierFooter from '../shared/components/DossierFooter.vue'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const props = defineProps<{ slug: string }>()
const { portfolio, status, error, retry } = usePublicContent()

/**
 * 只在 cases 里解析——详情页是 Case 专属入口，project 不在此路由渲染。
 * （与 ProjectPage 的 resolveDossier 不同：这里传空 projects，强制只匹配 case。）
 */
const caseStudy = computed(() => {
  const data = portfolio.value
  if (!data) return null
  return data.cases.find((c) => c.slug === props.slug) ?? null
})

const statusGroupKey = computed(() =>
  caseStudy.value ? caseStatusGroup(caseStudy.value.achievementStatus) : null,
)

/** 已核验断言数：与 buildSectionTraces 同款约定——按 subjectId 匹配本案例的公开 claim。 */
const claimCount = computed(() => {
  const data = portfolio.value
  const current = caseStudy.value
  if (!data || !current) return 0
  return data.claims.filter((claim) => claim.subjectId === current.id).length
})

/** 关联项目：按 projectSlug 反查项目标题；查不到就退回显示 slug 本身。 */
const linkedProject = computed(() => {
  const data = portfolio.value
  const slug = caseStudy.value?.projectSlug
  if (!data || !slug) return null
  return data.projects.find((p) => p.slug === slug) ?? null
})

const evidenceTarget = computed(() => ({
  path: '/evidence',
  query: { case: props.slug },
}))

const agentTargets = computed(() => {
  const current = caseStudy.value
  if (!current) return new Map<string, { path: string; query: { caseHandoffId: string } }>()
  const questions = ['', ...current.suggestedQuestions]
  return new Map(
    questions.map((question) => [
      question,
      {
        path: '/agent',
        query: {
          caseHandoffId: createCaseAgentHandoff({
            caseSlug: current.slug,
            question,
          }),
        },
      },
    ]),
  )
})

function agentTarget(question = '') {
  return agentTargets.value.get(question) ?? { path: '/agent' }
}

/* ───────── TOC scrollspy ───────── */
const sections: ReadonlyArray<{ id: string; code: string; title: string }> = [
  { id: 'problem', code: '01', title: '问题与背景' },
  { id: 'actions', code: '02', title: '采取的动作' },
  { id: 'decisions', code: '03', title: '关键判断' },
  { id: 'verify', code: '04', title: '验证过程' },
  { id: 'outcome', code: '05', title: '结果' },
  { id: 'limits', code: '06', title: '限制与边界' },
  { id: 'evidence', code: '07', title: '公开证据' },
  { id: 'questions', code: '08', title: '建议问题' },
]

const activeSection = ref('problem')
let observer: IntersectionObserver | null = null

function setupScrollSpy() {
  if (typeof IntersectionObserver === 'undefined') return
  observer?.disconnect()
  const targets = sections
    .map((s) => document.getElementById(`sec-${s.id}`))
    .filter((el): el is HTMLElement => el !== null)
  if (targets.length === 0) return
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeSection.value = entry.target.id.replace('sec-', '')
        }
      }
    },
    { rootMargin: '-30% 0px -60% 0px' },
  )
  targets.forEach((el) => observer!.observe(el))
}

function teardownScrollSpy() {
  observer?.disconnect()
  observer = null
}

onMounted(() => {
  // 等 DOM 渲染完再绑定
  requestAnimationFrame(setupScrollSpy)
})
onBeforeUnmount(teardownScrollSpy)
watch(caseStudy, () => {
  requestAnimationFrame(setupScrollSpy)
})
</script>

<template>
  <main v-if="status === 'ready' && portfolio && caseStudy" class="case-dossier">
    <!-- 面包屑 -->
    <nav class="case-breadcrumb page-shell" aria-label="面包屑">
      <ol>
        <li><RouterLink to="/cases">案例目录</RouterLink></li>
        <li aria-hidden="true">/</li>
        <li><RouterLink to="/cases">{{ CASE_TYPE_LABEL[caseStudy.type] }}</RouterLink></li>
        <li aria-hidden="true">/</li>
        <li aria-current="location">{{ caseStudy.code }}</li>
      </ol>
    </nav>

    <!-- 深色封面 -->
    <header class="case-cover">
      <div class="page-shell case-cover__grid">
        <div class="case-cover__code">
          <span class="case-cover__code-text">{{ caseStudy.code }}</span>
          <span class="cover-status" :data-sg="statusGroupKey ?? ''">
            <i class="cover-status__dot" aria-hidden="true" />
            {{ ACHIEVEMENT_STATUS_LABEL[caseStudy.achievementStatus] }}
          </span>
          <span class="cover-status cover-status--contrib">
            {{ CONTRIBUTION_LABEL[caseStudy.contributionType] }}
          </span>
        </div>

        <div class="case-cover__copy">
          <p class="case-cover__record-type">
            ENGINEERING CASE RECORD · {{ CASE_TYPE_LABEL[caseStudy.type] }}
          </p>
          <h1 data-mobile-balanced>{{ caseStudy.title }}</h1>
          <blockquote>{{ caseStudy.summary }}</blockquote>
        </div>

        <!-- 右列：case 元信息（替代 Project 页的 tech 列表） -->
        <div class="case-cover__meta">
          <p class="case-cover__meta-label">案卷信息</p>
          <div class="case-cover__meta-item">
            <span class="case-cover__meta-key">类型</span>
            <span class="case-cover__meta-val">{{ CASE_TYPE_LABEL[caseStudy.type] }}</span>
          </div>
          <div class="case-cover__meta-item">
            <span class="case-cover__meta-key">关联项目</span>
            <span v-if="caseStudy.projectSlug" class="case-cover__meta-val">
              <RouterLink :to="`/projects/${caseStudy.projectSlug}`">
                {{ linkedProject?.title ?? caseStudy.projectSlug }} ↗
              </RouterLink>
            </span>
            <span v-else class="case-cover__meta-val case-cover__meta-val--muted">独立案例</span>
          </div>
          <div class="case-cover__meta-item">
            <span class="case-cover__meta-key">公开证据</span>
            <span class="case-cover__meta-val case-cover__meta-val--mono">
              {{ caseStudy.evidence.length }} APPROVED
            </span>
          </div>
          <div class="case-cover__meta-item">
            <span class="case-cover__meta-key">已核验断言</span>
            <span class="case-cover__meta-val case-cover__meta-val--mono">
              {{ claimCount }} CLAIMS
            </span>
          </div>
        </div>
      </div>
    </header>

    <!-- 八段正文 -->
    <div class="case-body page-shell">
      <aside class="case-toc">
        <span>CONTENTS</span>
        <a
          v-for="section in sections"
          :key="section.id"
          :href="`#sec-${section.id}`"
          :class="{ 'is-active': activeSection === section.id }"
        >
          <span class="case-toc__num">{{ section.code }}</span>{{ section.title }}
        </a>
        <RouterLink class="case-toc__ask" :to="agentTarget()">询问本案例 ↗</RouterLink>
      </aside>

      <article class="case-story">
        <!-- 01 背景 -->
        <section id="sec-problem">
          <p class="section-code">01 / 背景</p>
          <h2>问题与背景</h2>
          <p class="story-lead">{{ caseStudy.problem }}</p>
        </section>

        <!-- 02 动作 -->
        <section id="sec-actions">
          <p class="section-code">02 / 动作</p>
          <h2>采取的动作</h2>
          <ol>
            <li v-for="(item, i) in caseStudy.actions" :key="i">{{ item }}</li>
          </ol>
        </section>

        <!-- 03 判断 -->
        <section id="sec-decisions">
          <p class="section-code">03 / 判断</p>
          <h2>关键判断</h2>
          <ol>
            <li v-for="(item, i) in caseStudy.decisions" :key="i">{{ item }}</li>
          </ol>
        </section>

        <!-- 04 验证（深色反白） -->
        <section id="sec-verify" class="case-story__dark">
          <p class="section-code">04 / 验证</p>
          <h2>验证过程</h2>
          <ol>
            <li v-for="(item, i) in caseStudy.verification" :key="i">{{ item }}</li>
          </ol>
        </section>

        <!-- 05 结果 -->
        <section id="sec-outcome">
          <p class="section-code">05 / 结果</p>
          <h2>结果</h2>
          <blockquote class="case-outcome">{{ caseStudy.outcome }}</blockquote>
        </section>

        <!-- 06 限制 -->
        <section id="sec-limits">
          <p class="section-code">06 / 边界</p>
          <h2>限制与边界</h2>
          <ul class="case-limits">
            <li v-for="(item, i) in caseStudy.limitations" :key="i">{{ item }}</li>
          </ul>
        </section>

        <!-- 07 证据 -->
        <section id="sec-evidence">
          <p class="section-code">07 / 证据</p>
          <h2>公开证据</h2>
          <div v-if="caseStudy.evidence.length" class="case-evidence">
            <div v-for="ev in caseStudy.evidence" :key="ev.id" class="case-evidence__item">
              <span class="case-evidence__code">{{ ev.code }}</span>
              <div>
                <h3 class="case-evidence__title">{{ ev.title }}</h3>
                <p class="case-evidence__summary">{{ ev.summary }}</p>
                <p class="case-evidence__meta">
                  {{ ev.periodStart }} → {{ ev.periodEnd }} · {{ ev.sourceCount }} 个脱敏来源 ·
                  {{ ev.type }}
                </p>
              </div>
            </div>
          </div>
          <p v-else class="case-evidence__empty">该案例当前没有已批准的公开证据。</p>
        </section>

        <!-- 08 建议问题 + 询问入口 -->
        <section id="sec-questions">
          <p class="section-code">08 / 延伸</p>
          <h2>建议问题</h2>
          <div v-if="caseStudy.suggestedQuestions.length" class="case-questions">
            <RouterLink
              v-for="(q, i) in caseStudy.suggestedQuestions"
              :key="i"
              class="case-question"
              :to="agentTarget(q)"
            >
              <span class="case-question__q">Q{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="case-question__text">{{ q }}</span>
            </RouterLink>
          </div>
          <p v-else class="case-evidence__empty">该案例当前没有预设的建议问题。</p>

          <div class="case-ask-banner">
            <div>
              <p class="case-ask-banner__title">就本案例向 Agent 提问</p>
              <p class="case-ask-banner__sub">context.source = CASE · 仅限本案例公开事实</p>
            </div>
            <RouterLink class="case-ask-banner__btn" :to="agentTarget()">询问本案例 ↗</RouterLink>
          </div>
        </section>

        <footer class="case-next">
          <RouterLink to="/cases">
            <span class="case-next__label">返回目录</span>
            全部案例 →
          </RouterLink>
          <RouterLink to="/agent">
            <span class="case-next__label">完整 Agent</span>
            不带案例上下文提问 →
          </RouterLink>
        </footer>
      </article>
    </div>

    <DossierFooter :content-version="portfolio.contentVersion" />
  </main>

  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    @retry="retry"
  />

  <main v-else-if="status === 'ready' && !caseStudy" class="page-shell">
    <EmptyDossier
      code="CASE_NOT_FOUND"
      title="未找到该案例"
      description="这个地址没有对应的公开案例，或相关资料仍在审核中。Agent 不会针对不存在的案例调用 Provider 猜测答案。"
    >
      <RouterLink to="/cases">返回案例目录 →</RouterLink>
    </EmptyDossier>
  </main>
</template>

<style scoped>
/* ───────── 面包屑 ───────── */
.case-breadcrumb {
  padding-top: calc(var(--header-height) + 26px);
  padding-bottom: 0;
  background: var(--ink);
  color: var(--ink-text);
}

.case-breadcrumb ol {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
  font: 11px var(--mono);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.case-breadcrumb a {
  transition: color 0.2s var(--ease);
}

.case-breadcrumb a:hover {
  color: var(--red-hi);
}

.case-breadcrumb li:last-child {
  color: var(--paper-hi);
}

/* ───────── 深色封面 ───────── */
.case-cover {
  padding: 48px 0 76px;
  color: var(--paper);
  background: var(--ink);
}

.case-cover__grid {
  display: grid;
  grid-template-columns: 0.26fr 1fr 0.35fr;
  gap: clamp(28px, 5vw, 72px);
}

.case-cover__grid > * {
  min-width: 0;
}

.case-cover__code {
  display: flex;
  flex-direction: column;
  align-content: flex-start;
  gap: 14px;
}

.case-cover__code-text {
  color: var(--red-hi);
  font: 10px var(--mono);
  letter-spacing: 0.14em;
}

.cover-status {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--ink-text);
  font: 10px var(--mono);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.cover-status__dot {
  width: 8px;
  height: 8px;
  border: 1.5px solid var(--red-on-ink);
  border-radius: 50%;
  background: var(--ink);
}

.cover-status[data-sg='delivered'] .cover-status__dot {
  background: var(--red-on-ink);
}

.cover-status[data-sg='prototype'] .cover-status__dot {
  background: var(--ink);
  border-style: dashed;
}

.cover-status[data-sg='learning'] .cover-status__dot {
  background: var(--ink);
  border-color: var(--ink-text);
}

.cover-status--contrib {
  color: var(--ink-text);
}

.case-cover__record-type {
  margin: 0 0 24px;
  color: var(--red-on-ink);
  font: 10px var(--mono);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.case-cover h1 {
  margin: 0 0 28px;
  font: 400 clamp(48px, 6vw, 88px) / 1.02 var(--serif);
  letter-spacing: -0.04em;
}

.case-cover blockquote {
  max-width: 760px;
  margin: 0;
  color: var(--ink-text-hi);
  font: 20px / 1.7 var(--serif);
}

/* 封面右列：case 元信息 */
.case-cover__meta {
  display: flex;
  flex-direction: column;
}

.case-cover__meta-label {
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ink-line);
  color: var(--ink-text);
  font: 9.5px var(--mono);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.case-cover__meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid var(--ink-line);
}

.case-cover__meta-item:last-child {
  border-bottom: 0;
}

.case-cover__meta-key {
  color: var(--ink-text-faint);
  font: 9.5px var(--mono);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.case-cover__meta-val {
  color: var(--ink-text-hi);
  font: 15px / 1.4 var(--serif);
}

.case-cover__meta-val--mono {
  font: 12px var(--mono);
  letter-spacing: 0.04em;
}

.case-cover__meta-val--muted {
  color: var(--ink-text-faint);
  font-style: italic;
}

.case-cover__meta-val a {
  border-bottom: 1px solid var(--ink-line);
  padding-bottom: 1px;
  transition: color 0.2s var(--ease);
}

.case-cover__meta-val a:hover {
  color: var(--paper);
}

/* ───────── 正文区 ───────── */
.case-body {
  display: grid;
  grid-template-columns: 240px minmax(0, 860px);
  gap: clamp(48px, 8vw, 130px);
  justify-content: center;
  padding-top: 90px;
  padding-bottom: 130px;
}

.case-toc {
  position: sticky;
  top: calc(var(--header-height) + 30px);
  display: grid;
  height: fit-content;
}

.case-toc > span {
  margin-bottom: 18px;
  color: var(--red);
  font: 10px var(--mono);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.case-toc a {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 12px 0;
  color: var(--muted);
  border-bottom: 1px solid var(--rule);
  font-size: 13px;
  transition: color 0.2s var(--ease);
}

.case-toc a:first-of-type {
  border-top: 1px solid var(--rule);
}

.case-toc a:hover,
.case-toc a.is-active {
  color: var(--red);
}

.case-toc__num {
  color: var(--red);
  font: 10px var(--mono);
  letter-spacing: 0.06em;
  opacity: 0.7;
}

.case-toc a.is-active .case-toc__num {
  opacity: 1;
}

.case-toc__ask {
  margin-top: 22px;
  padding: 14px 16px;
  border: 1px solid var(--red);
  color: var(--red);
  font: 11px var(--mono);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  transition:
    background-color 0.2s var(--ease),
    border-color 0.2s var(--ease),
    color 0.2s var(--ease);
}

.case-toc__ask:hover {
  background: var(--red);
  color: var(--paper-hi);
}

.case-story {
  min-width: 0;
}

.case-story section {
  padding: 0 0 84px;
  scroll-margin-top: 100px;
}

.case-story section + section:not(.case-story__dark) {
  padding-top: 76px;
  border-top: 1px solid var(--rule);
}

.section-code {
  display: block;
  margin: 0 0 16px;
  color: var(--red);
  font: 10px var(--mono);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.case-story h2 {
  margin: 0 0 28px;
  font: 400 clamp(36px, 4vw, 54px) / 1.08 var(--serif);
  letter-spacing: -0.03em;
}

.story-lead,
.case-story li {
  color: var(--ink-2);
  font: 17px / 2 var(--serif);
}

.story-lead {
  font-size: 19px;
  line-height: 1.8;
  color: var(--ink);
}

/* 编号列表 */
.case-story ol {
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: cs;
}

.case-story ol > li {
  position: relative;
  padding: 18px 0 18px 54px;
  border-top: 1px solid var(--rule);
  counter-increment: cs;
  line-height: 1.8;
}

.case-story ol > li:last-child {
  border-bottom: 1px solid var(--rule);
}

.case-story ol > li::before {
  position: absolute;
  top: 20px;
  left: 0;
  content: counter(cs, decimal-leading-zero);
  color: var(--red);
  font: 10px var(--mono);
}

/* 深色反白：验证段 */
.case-story__dark {
  margin: 0 0 84px !important;
  padding: 64px 50px !important;
  color: var(--paper);
  background: var(--ink);
}

.case-story__dark .section-code {
  color: var(--red-on-ink);
}

.case-story__dark h2 {
  color: var(--paper);
}

.case-story__dark ol > li {
  color: var(--ink-text-hi);
  border-color: var(--ink-line);
}

.case-story__dark ol > li::before {
  color: var(--red-on-ink);
}

@media (min-width: 1250px) {
  .case-story__dark {
    width: calc(100% + 100px);
    margin-left: -50px !important;
  }
}

@media (max-width: 900px) {
  .case-story__dark {
    margin-inline: calc(var(--page-gutter) * -0.5) !important;
    width: auto;
  }
}

/* 结果引述 */
.case-outcome {
  margin: 8px 0 0;
  padding: 6px 0 6px 28px;
  max-width: 60ch;
  border-left: 2px solid var(--red);
  color: var(--ink);
  font: clamp(22px, 2.4vw, 30px) / 1.55 var(--serif);
}

/* 限制段 */
.case-limits {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.case-limits li {
  position: relative;
  max-width: 64ch;
  padding: 20px 0 20px 30px;
  border-top: 1px solid var(--rule);
  color: var(--ink-2);
  font: 16px / 1.8 var(--serif);
}

.case-limits li:last-child {
  border-bottom: 1px solid var(--rule);
}

.case-limits li::before {
  position: absolute;
  top: 20px;
  left: 0;
  color: var(--muted);
  content: '—';
  font-family: var(--mono);
}

/* 证据段 */
.case-evidence {
  display: flex;
  flex-direction: column;
}

.case-evidence__item {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 24px;
  align-items: start;
  padding: 26px 0;
  border-top: 1px solid var(--rule);
}

.case-evidence__item:last-child {
  border-bottom: 1px solid var(--rule);
}

.case-evidence__code {
  color: var(--red);
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}

.case-evidence__title {
  margin: 0 0 8px;
  color: var(--ink);
  font: 18px / 1.4 var(--serif);
}

.case-evidence__summary {
  max-width: 60ch;
  margin: 0 0 8px;
  color: var(--muted);
  font: 14.5px / 1.75 var(--serif);
}

.case-evidence__meta {
  margin: 0;
  color: var(--faint);
  font: 10.5px var(--mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.case-evidence__empty {
  color: var(--faint);
  font-style: italic;
  font: 16px var(--serif);
}

/* 建议问题 */
.case-questions {
  display: flex;
  flex-direction: column;
  margin-bottom: 36px;
}

.case-question {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 22px 0;
  border-top: 1px solid var(--rule);
  transition: color 0.2s var(--ease);
}

.case-question:last-child {
  border-bottom: 1px solid var(--rule);
}

.case-question__q {
  flex-shrink: 0;
  margin-top: 5px;
  color: var(--red);
  font: 10.5px var(--mono);
  letter-spacing: 0.1em;
}

.case-question__text {
  color: var(--ink-2);
  font: 17px / 1.6 var(--serif);
  transition: color 0.2s var(--ease);
}

.case-question:hover .case-question__text {
  color: var(--red);
}

.case-question:hover .case-question__text::after {
  content: ' ↗';
  color: var(--red);
}

/* 询问 banner */
.case-ask-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 20px;
  padding: 34px 38px;
  color: var(--paper);
  background: var(--ink);
}

.case-ask-banner__title {
  margin: 0;
  color: var(--paper);
  font: 24px var(--serif);
  letter-spacing: -0.01em;
}

.case-ask-banner__sub {
  margin: 6px 0 0;
  color: var(--ink-text);
  font: 11px var(--mono);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.case-ask-banner__btn {
  padding: 15px 26px;
  background: var(--red);
  color: var(--paper-hi);
  font: 12px var(--mono);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  transition: background 0.2s var(--ease);
}

.case-ask-banner__btn:hover {
  background: var(--red-hi);
}

/* 底部 next */
.case-next {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-top: 80px;
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.case-next a {
  padding: 26px;
  color: var(--ink-2);
  font: 15px var(--serif);
  transition: color 0.2s var(--ease);
}

.case-next a:first-child {
  border-right: 1px solid var(--rule);
}

.case-next a:hover {
  color: var(--red);
}

.case-next__label {
  display: block;
  margin-bottom: 10px;
  color: var(--muted);
  font: 9.5px var(--mono);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

/* 响应式 */
@media (max-width: 1000px) {
  .case-cover__grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}

@media (max-width: 900px) {
  .case-body {
    grid-template-columns: 1fr;
  }

  .case-toc {
    position: static;
    grid-template-columns: repeat(4, 1fr);
    margin-bottom: 40px;
  }

  .case-toc > span {
    grid-column: 1 / -1;
  }

  .case-toc__ask {
    grid-column: 1 / -1;
    margin-top: 18px;
  }
}

@media (max-width: 620px) {
  .case-cover h1 {
    overflow-wrap: anywhere;
    font-size: clamp(42px, 13vw, 58px);
    line-height: 1.04;
    text-wrap: balance;
  }

  .case-toc {
    display: none;
  }

  .case-next {
    grid-template-columns: 1fr;
  }

  .case-next a:first-child {
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
}

@media (prefers-reduced-motion: reduce) {
  .case-question__text,
  .case-toc a,
  .case-breadcrumb a,
  .case-next a {
    transition: none;
  }
}
</style>
