<script setup lang="ts">
import { computed, ref } from 'vue'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import {
  ACHIEVEMENT_STATUS_LABEL,
  CASE_STATUS_GROUP_ORDER,
  CONTRIBUTION_LABEL,
  CASE_TYPE_LABEL,
  buildCaseStatusGroups,
  type CaseStatusGroupKey,
} from '../features/portfolio/model/caseListModel'
import DossierFooter from '../shared/components/DossierFooter.vue'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const { portfolio, status, error, action, retryAfterSeconds, retry } = usePublicContent()

const groups = computed(() => {
  const data = portfolio.value
  if (!data) return []
  return buildCaseStatusGroups(data.cases)
})

/** 当前选中的状态 tab。默认 delivered——先亮交付实力。 */
const activeTab = ref<CaseStatusGroupKey>('delivered')

const activeGroup = computed(() => {
  // 若当前 tab 因数据变化而消失，回退到第一个可用组
  const found = groups.value.find((g) => g.key === activeTab.value)
  return found ?? groups.value[0] ?? null
})

const total = computed(() => groups.value.reduce((sum, g) => sum + g.cases.length, 0))

/** tab 键盘：← → / Home / End 切换 */
function onTabKeydown(event: KeyboardEvent) {
  const available = groups.value.map((g) => g.key)
  if (available.length === 0) return
  const idx = available.indexOf(activeGroup.value?.key ?? activeTab.value)
  let nextKey: CaseStatusGroupKey | null = null
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    nextKey = available[(idx + 1) % available.length]
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    nextKey = available[(idx - 1 + available.length) % available.length]
  } else if (event.key === 'Home') {
    nextKey = available[0]
  } else if (event.key === 'End') {
    nextKey = available[available.length - 1]
  }
  if (nextKey) {
    event.preventDefault()
    activeTab.value = nextKey
    // 移动焦点到新选中的 tab
    requestAnimationFrame(() => {
      const el = document.querySelector<HTMLElement>(`[data-sg="${nextKey}"]`)
      el?.focus()
    })
  }
}

function countFor(key: CaseStatusGroupKey): number {
  return groups.value.find((g) => g.key === key)?.cases.length ?? 0
}
</script>

<template>
  <main v-if="status === 'ready' && portfolio">
    <PageLead
      code="02 / CASE INDEX"
      title="案例目录"
      :description="`${total} 个具体任务、问题与评测——记录它们如何被识别、判断、处理与验证。与「项目」并列的独立公开入口，强调具体问题而非长期主线。`"
    />

    <section v-if="total" class="case-index page-shell">
      <!-- 状态 tab（ARIA tablist） -->
      <div class="case-tabs" role="tablist" aria-label="按交付状态筛选案例" @keydown="onTabKeydown">
        <button
          v-for="key in CASE_STATUS_GROUP_ORDER"
          v-show="countFor(key) > 0"
          :id="`tab-${key}`"
          :key="key"
          class="case-tab"
          :class="{ 'is-selected': activeGroup?.key === key }"
          :data-sg="key"
          role="tab"
          :aria-selected="activeGroup?.key === key"
          :tabindex="activeGroup?.key === key ? 0 : -1"
          :aria-controls="`panel-${key}`"
          @click="activeTab = key"
        >
          <span class="case-tab__label">
            <span class="case-tab__dot" aria-hidden="true" />
            {{ key === 'delivered' ? '已交付' : key === 'prototype' ? '原型验证' : '学习整理' }}
          </span>
          <span class="case-tab__count">{{ String(countFor(key)).padStart(2, '0') }}</span>
        </button>
      </div>

      <div
        v-if="activeGroup"
        :id="`panel-${activeGroup.key}`"
        role="tabpanel"
        :aria-labelledby="`tab-${activeGroup.key}`"
      >
        <header class="case-panel-intro">
          <span class="case-panel-intro__code">
            0{{ CASE_STATUS_GROUP_ORDER.indexOf(activeGroup.key) + 1 }} / {{ activeGroup.code }} ·
            {{ String(activeGroup.cases.length).padStart(2, '0') }}
          </span>
          <h2>{{ activeGroup.label }}</h2>
          <p class="case-panel-intro__note">{{ activeGroup.note }}</p>
        </header>

        <div class="case-list">
          <RouterLink
            v-for="item in activeGroup.cases"
            :key="item.slug"
            class="case-row"
            :to="`/cases/${item.slug}`"
          >
            <span class="case-row__code">{{ item.code }}</span>
            <div class="case-row__main">
              <h3>{{ item.title }}</h3>
              <p class="case-row__summary">{{ item.summary }}</p>
            </div>
            <div class="case-row__type">
              <b>{{ CASE_TYPE_LABEL[item.type] }}</b>
              {{ ACHIEVEMENT_STATUS_LABEL[item.achievementStatus] }}
            </div>
            <span class="case-row__stat">{{ CONTRIBUTION_LABEL[item.contributionType] }}</span>
            <i class="case-row__arrow" aria-hidden="true">↗</i>
          </RouterLink>
        </div>
      </div>
    </section>

    <div v-else class="page-shell">
      <EmptyDossier title="案卷资料准备中" description="目前还没有可以公开的工程案例。" />
    </div>

    <DossierFooter :content-version="portfolio.contentVersion" />
  </main>
  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    :action="action"
    :retry-after-seconds="retryAfterSeconds"
    @retry="retry"
  />
</template>

<style scoped>
.case-index {
  padding: 32px 0 120px;
}

/* ─────────── 状态 tab ─────────── */
.case-tabs {
  display: flex;
  align-items: flex-end;
  border-bottom: 1px solid var(--ink);
  position: sticky;
  top: var(--header-height);
  z-index: 50;
  background: var(--paper);
  overflow-x: auto;
  scrollbar-width: none;
}

.case-tabs::-webkit-scrollbar {
  display: none;
}

.case-tab {
  position: relative;
  flex-shrink: 0;
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  padding: 22px 26px 20px;
  border: none;
  background: none;
  color: var(--muted);
  font: 11.5px var(--mono);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  transition: color 0.2s var(--ease);
}

.case-tab:hover {
  color: var(--ink-2);
}

.case-tab.is-selected {
  color: var(--ink);
}

.case-tab.is-selected::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--red);
}

.case-tab__label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.case-tab__dot {
  width: 8px;
  height: 8px;
  border: 1.5px solid var(--red);
  border-radius: 50%;
  background: var(--paper);
}

.case-tab[data-sg='delivered'] .case-tab__dot {
  background: var(--red);
}

.case-tab[data-sg='prototype'] .case-tab__dot {
  background: var(--paper);
  border-style: dashed;
}

.case-tab[data-sg='learning'] .case-tab__dot {
  background: var(--paper);
  border-color: var(--muted);
}

.case-tab__count {
  color: var(--faint);
  font: 10px var(--mono);
  letter-spacing: 0.06em;
}

.case-tab.is-selected .case-tab__count {
  color: var(--red);
}

.case-tab:focus-visible {
  outline-offset: -4px;
}

/* ─────────── 面板导言 ─────────── */
.case-panel-intro {
  display: grid;
  grid-template-columns: 0.34fr 1fr 0.58fr;
  gap: clamp(24px, 4vw, 56px);
  align-items: end;
  padding: 32px 0 22px;
}

.case-panel-intro__code {
  color: var(--red);
  font: 11px var(--mono);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.case-panel-intro h2 {
  margin: 0;
  font: 400 clamp(26px, 3vw, 38px) / 1.1 var(--serif);
  letter-spacing: -0.025em;
}

.case-panel-intro__note {
  max-width: 40ch;
  margin: 0;
  color: var(--muted);
  font-family: var(--serif);
  font-size: 13.5px;
  line-height: 1.7;
}

/* ─────────── V3 索引行 ─────────── */
.case-list {
  padding-top: 2px;
}

.case-row {
  position: relative;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 190px 88px;
  gap: clamp(16px, 2.4vw, 28px);
  align-items: baseline;
  padding: 20px clamp(6px, 1vw, 12px);
  border-bottom: 1px solid var(--rule);
  transition: background 0.2s var(--ease);
}

.case-row:hover {
  background: var(--paper-hi);
}

.case-row:focus-visible {
  background: var(--paper-hi);
  outline-offset: -2px;
}

.case-row__code {
  align-self: center;
  color: var(--red);
  font: 11px var(--mono);
  letter-spacing: 0.08em;
}

.case-row__main {
  min-width: 0;
}

.case-row__main h3 {
  margin: 0;
  font: 400 clamp(19px, 1.9vw, 26px) / 1.2 var(--serif);
  letter-spacing: -0.015em;
  transition: color 0.2s var(--ease);
}

.case-row:hover .case-row__main h3 {
  color: var(--red);
}

.case-row__summary {
  max-height: 0;
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  font-family: var(--serif);
  font-size: 13px;
  line-height: 1.7;
  opacity: 0;
  transition:
    max-height 0.3s var(--ease),
    opacity 0.3s var(--ease),
    margin-top 0.3s var(--ease);
}

.case-row:hover .case-row__summary,
.case-row:focus-within .case-row__summary {
  max-height: 90px;
  margin-top: 8px;
  opacity: 1;
}

.case-row__type {
  align-self: center;
  color: var(--muted);
  font: 10px var(--mono);
  letter-spacing: 0.08em;
  text-align: right;
  text-transform: uppercase;
}

.case-row__type b {
  display: block;
  margin-bottom: 3px;
  color: var(--ink-2);
  font-weight: 400;
  font-size: 10.5px;
}

.case-row__stat {
  align-self: center;
  color: var(--faint);
  font: 10px var(--mono);
  letter-spacing: 0.06em;
  text-align: right;
  text-transform: uppercase;
  white-space: nowrap;
}

.case-row__arrow {
  position: absolute;
  top: 50%;
  right: 14px;
  color: var(--red);
  font-size: 14px;
  font-style: normal;
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity 0.2s var(--ease);
}

.case-row:hover .case-row__arrow {
  opacity: 1;
}

@media (max-width: 760px) {
  .case-row {
    grid-template-columns: auto 1fr;
    gap: 12px;
    padding: 18px 6px;
  }

  .case-row__type,
  .case-row__stat,
  .case-row__arrow {
    display: none;
  }

  .case-row__summary {
    max-height: none;
    margin-top: 6px;
    opacity: 1;
  }

  .case-panel-intro {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 22px 0 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .case-row__summary {
    transition: none;
  }
}
</style>
