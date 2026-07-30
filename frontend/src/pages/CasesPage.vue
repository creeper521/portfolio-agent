<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import type { CaseType } from '../features/public-content/model/publicContentTypes'
import {
  ACHIEVEMENT_STATUS_LABEL,
  CASE_STATUS_GROUP_INFO,
  CASE_STATUS_GROUP_ORDER,
  CASE_TYPE_LABEL,
  CONTRIBUTION_LABEL,
  caseStatusGroup,
} from '../features/portfolio/model/caseListModel'
import {
  DEFAULT_CASE_FILTER,
  buildCaseQueryObject,
  filterCases,
  parseCaseQuery,
  type CaseFilterState,
  type CaseStatusFilter,
  type CaseTypeFilter,
} from '../features/portfolio/model/caseQueryModel'
import DossierFooter from '../shared/components/DossierFooter.vue'
import EmptyDossier from '../shared/components/EmptyDossier.vue'
import PageLead from '../shared/components/PageLead.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const { portfolio, status, error, action, retryAfterSeconds, retry } = usePublicContent()
const route = useRoute()
const router = useRouter()

const allCases = computed(() => portfolio.value?.cases ?? [])
const total = computed(() => allCases.value.length)

/**
 * URL query 是筛选的唯一事实源：
 * 读取经 parseCaseQuery（非法值回退默认），写入经 buildCaseQueryObject（默认值不落 URL）。
 * 不做本地副本，避免「刷新丢状态 / 前进后退不一致」。
 */
const filter = computed(() => parseCaseQuery(route.query))
const results = computed(() => filterCases(allCases.value, filter.value))

function apply(patch: Partial<CaseFilterState>) {
  const next: CaseFilterState = { ...filter.value, ...patch }
  void router.replace({ path: '/cases', query: buildCaseQueryObject(next) }).catch(() => {
    // 相同 query 的重复导航在 vue-router 里是 rejected promise，静默忽略
  })
}

function resetAll() {
  void router.replace({ path: '/cases', query: {} }).catch(() => {})
}

/** 搜索框是本地输入态，与 query.q 双向同步（query 变化回填输入框，输入写回 query）。 */
const q = ref(filter.value.q)
watch(
  () => filter.value.q,
  (value) => {
    if (value !== q.value) q.value = value
  },
)
watch(q, (value) => {
  const trimmed = value.trim()
  if (trimmed !== filter.value.q) apply({ q: trimmed })
})

/** 状态选项：全部 + 四个固定档。计数基于全量案例（各维度独立计数，不做交叉联动）。 */
interface StatusOption {
  value: CaseStatusFilter
  label: string
  count: number
}

const statusOptions = computed<StatusOption[]>(() => {
  const cases = allCases.value
  return [
    { value: 'all', label: '全部', count: cases.length },
    ...CASE_STATUS_GROUP_ORDER.map((key) => ({
      value: key as CaseStatusFilter,
      label: CASE_STATUS_GROUP_INFO[key].label,
      count: cases.filter((item) => caseStatusGroup(item.achievementStatus) === key).length,
    })),
  ]
})

/** 归属选项：全部 / 各 Project / 各 Collection / 独立案例。三者互斥由 patch 一次性清掉另外两维。 */
interface AttributionOption {
  key: string
  label: string
  hint: string | null
  count: number
  patch: Partial<CaseFilterState>
}

const attributionOptions = computed<AttributionOption[]>(() => {
  const data = portfolio.value
  const cases = allCases.value
  if (!data) return []
  const options: AttributionOption[] = [
    {
      key: 'all',
      label: '全部',
      hint: null,
      count: cases.length,
      patch: { project: null, collection: null, independent: false },
    },
  ]
  for (const project of data.projects) {
    options.push({
      key: `project:${project.slug}`,
      label: project.title,
      hint: '项目',
      count: cases.filter((item) => item.projectSlug === project.slug).length,
      patch: { project: project.slug, collection: null, independent: false },
    })
  }
  for (const collection of data.collections) {
    options.push({
      key: `collection:${collection.slug}`,
      label: collection.title,
      hint: '集合',
      count: cases.filter((item) => item.collectionSlugs.includes(collection.slug)).length,
      patch: { project: null, collection: collection.slug, independent: false },
    })
  }
  options.push({
    key: 'independent',
    label: '独立案例',
    hint: null,
    count: cases.filter((item) => item.projectSlug === null).length,
    patch: { project: null, collection: null, independent: true },
  })
  return options
})

const activeAttributionKey = computed(() => {
  const current = filter.value
  if (current.independent) return 'independent'
  if (current.project !== null) return `project:${current.project}`
  if (current.collection !== null) return `collection:${current.collection}`
  return 'all'
})

const CASE_TYPE_ORDER: readonly CaseType[] = ['FEATURE', 'INCIDENT', 'EVALUATION']

interface TypeOption {
  value: CaseTypeFilter
  label: string
  count: number
}

const typeOptions = computed<TypeOption[]>(() => {
  const cases = allCases.value
  return [
    { value: 'all', label: '全部', count: cases.length },
    ...CASE_TYPE_ORDER.map((type) => ({
      value: type as CaseTypeFilter,
      label: CASE_TYPE_LABEL[type],
      count: cases.filter((item) => item.type === type).length,
    })),
  ]
})

/** 当前条件 chips：状态常显（默认值不可移除），其余只在非默认时出现、可单独移除。 */
interface Chip {
  key: string
  label: string
  removable: boolean
  patch: Partial<CaseFilterState>
}

const chips = computed<Chip[]>(() => {
  const data = portfolio.value
  const current = filter.value
  const list: Chip[] = []
  const statusLabel =
    current.status === 'all' ? '全部' : CASE_STATUS_GROUP_INFO[current.status].label
  list.push({
    key: 'status',
    label: `状态 · ${statusLabel}`,
    removable: current.status !== DEFAULT_CASE_FILTER.status,
    patch: { status: DEFAULT_CASE_FILTER.status },
  })
  if (current.project !== null) {
    const project = data?.projects.find((item) => item.slug === current.project)
    list.push({
      key: 'project',
      label: `项目 · ${project?.title ?? current.project}`,
      removable: true,
      patch: { project: null },
    })
  }
  if (current.collection !== null) {
    const collection = data?.collections.find((item) => item.slug === current.collection)
    list.push({
      key: 'collection',
      label: `集合 · ${collection?.title ?? current.collection}`,
      removable: true,
      patch: { collection: null },
    })
  }
  if (current.independent) {
    list.push({
      key: 'independent',
      label: '归属 · 独立案例',
      removable: true,
      patch: { independent: false },
    })
  }
  if (current.type !== 'all') {
    list.push({
      key: 'type',
      label: `类型 · ${CASE_TYPE_LABEL[current.type]}`,
      removable: true,
      patch: { type: 'all' },
    })
  }
  if (current.q.length > 0) {
    list.push({ key: 'q', label: `关键词 · ${current.q}`, removable: true, patch: { q: '' } })
  }
  return list
})

/** 移动端筛选栏折叠开关（桌面端常显，按钮隐藏）。 */
const filtersOpen = ref(false)
</script>

<template>
  <main v-if="status === 'ready' && portfolio">
    <PageLead
      code="02 / CASE INDEX"
      title="案例目录"
      :description="`${total} 个具体任务、问题与评测——记录它们如何被识别、判断、处理与验证。可按状态、归属与类型组合检索；从项目进入时会自动带上归属条件。`"
    />

    <section v-if="total" class="case-index page-shell">
      <button
        class="case-filters__toggle"
        type="button"
        :aria-expanded="filtersOpen"
        aria-controls="case-filters"
        @click="filtersOpen = !filtersOpen"
      >
        <span>筛选 / FILTERS</span>
        <span class="case-filters__toggle-state">{{ filtersOpen ? '收起 −' : '展开 ＋' }}</span>
      </button>

      <div class="case-layout">
        <aside id="case-filters" class="case-filters" :class="{ 'is-open': filtersOpen }">
          <div class="filter-group">
            <label class="filter-group__label" for="case-search">检索 / SEARCH</label>
            <input
              id="case-search"
              v-model="q"
              class="filter-search"
              type="search"
              placeholder="标题 / 编号 / 摘要"
            />
          </div>

          <div class="filter-group" data-filter-group="status">
            <p class="filter-group__label">状态 / STATUS</p>
            <button
              v-for="opt in statusOptions"
              :key="opt.value"
              type="button"
              class="filter-option"
              :class="{ 'is-active': filter.status === opt.value }"
              :data-value="opt.value"
              :aria-pressed="filter.status === opt.value"
              @click="apply({ status: opt.value })"
            >
              <span
                v-if="opt.value !== 'all'"
                class="filter-option__dot"
                :data-sg="opt.value"
                aria-hidden="true"
              />
              <span class="filter-option__label">{{ opt.label }}</span>
              <span class="filter-option__count">{{ String(opt.count).padStart(2, '0') }}</span>
            </button>
          </div>

          <div class="filter-group" data-filter-group="attribution">
            <p class="filter-group__label">归属 / BELONGING</p>
            <button
              v-for="opt in attributionOptions"
              :key="opt.key"
              type="button"
              class="filter-option"
              :class="{ 'is-active': activeAttributionKey === opt.key }"
              :data-value="opt.key"
              :aria-pressed="activeAttributionKey === opt.key"
              @click="apply(opt.patch)"
            >
              <span class="filter-option__label">{{ opt.label }}</span>
              <span v-if="opt.hint" class="filter-option__hint">{{ opt.hint }}</span>
              <span class="filter-option__count">{{ String(opt.count).padStart(2, '0') }}</span>
            </button>
          </div>

          <div class="filter-group" data-filter-group="type">
            <p class="filter-group__label">类型 / TYPE</p>
            <button
              v-for="opt in typeOptions"
              :key="opt.value"
              type="button"
              class="filter-option"
              :class="{ 'is-active': filter.type === opt.value }"
              :data-value="opt.value"
              :aria-pressed="filter.type === opt.value"
              @click="apply({ type: opt.value })"
            >
              <span class="filter-option__label">{{ opt.label }}</span>
              <span class="filter-option__count">{{ String(opt.count).padStart(2, '0') }}</span>
            </button>
          </div>
        </aside>

        <div class="case-results">
          <header class="case-results__head">
            <p class="case-results__count">{{ results.length }} 条结果</p>
            <ul class="case-chips">
              <li v-for="chip in chips" :key="chip.key" class="case-chip">
                <span>{{ chip.label }}</span>
                <button
                  v-if="chip.removable"
                  type="button"
                  class="case-chip__remove"
                  :aria-label="`移除条件：${chip.label}`"
                  @click="apply(chip.patch)"
                >
                  ×
                </button>
              </li>
            </ul>
          </header>

          <div v-if="results.length" class="case-list">
            <RouterLink
              v-for="item in results"
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

          <div v-else class="case-empty">
            <p class="case-empty__title">没有符合当前条件的案例</p>
            <p class="case-empty__note">
              状态、归属、类型与关键词取交集——放宽任一条件，或清除全部回到默认视图。
            </p>
            <button type="button" class="case-empty__reset" data-clear-all @click="resetAll">
              清除全部条件
            </button>
          </div>
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

/* 桌面：左筛选栏 + 右结果列；筛选栏粘性随行 */
.case-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: clamp(36px, 5vw, 80px);
  align-items: start;
}

/* 移动端筛选开关（桌面隐藏） */
.case-filters__toggle {
  display: none;
}

/* ─────────── 筛选栏 ─────────── */
.case-filters {
  position: sticky;
  top: calc(var(--header-height) + 30px);
}

.filter-group {
  padding: 20px 0;
  border-top: 1px solid var(--rule);
}

.filter-group:first-child {
  padding-top: 0;
  border-top: 0;
}

.filter-group__label {
  display: block;
  margin: 0 0 12px;
  color: var(--red);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.filter-search {
  width: 100%;
  padding: 9px 2px;
  color: var(--ink);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--ink);
  font-family: var(--serif);
  font-size: 15px;
}

.filter-search::placeholder {
  color: var(--faint);
}

.filter-option {
  display: flex;
  align-items: baseline;
  gap: 10px;
  width: 100%;
  padding: 8px 0 8px 12px;
  color: var(--muted);
  background: none;
  border: 0;
  border-left: 2px solid transparent;
  font-family: var(--serif);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: color 0.2s var(--ease);
}

.filter-option:hover {
  color: var(--ink-2);
}

.filter-option.is-active {
  color: var(--ink);
  border-left-color: var(--red);
}

.filter-option__dot {
  align-self: center;
  width: 8px;
  height: 8px;
  border: 1.5px solid var(--red);
  border-radius: 50%;
  background: var(--paper);
}

.filter-option__dot[data-sg='delivered'] {
  background: var(--red);
}

.filter-option__dot[data-sg='investigated'] {
  background: linear-gradient(90deg, var(--red) 50%, var(--paper) 50%);
}

.filter-option__dot[data-sg='prototype'] {
  border-style: dashed;
}

.filter-option__dot[data-sg='learning'] {
  border-color: var(--muted);
}

.filter-option__label {
  flex: 1;
  min-width: 0;
}

.filter-option__hint {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.filter-option__count {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
}

.filter-option.is-active .filter-option__count {
  color: var(--red);
}
</style>

<style scoped>
/* ─────────── 结果列 ─────────── */
.case-results {
  min-width: 0;
}

.case-results__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 14px 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--ink);
}

.case-results__count {
  margin: 0;
  color: var(--red);
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.12em;
}

.case-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.case-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  color: var(--ink-2);
  border: 1px solid var(--rule);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.case-chip__remove {
  padding: 0;
  color: var(--red);
  background: none;
  border: 0;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

/* ─────────── 案例行（沿用索引行样式） ─────────── */
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
</style>

<style scoped>
/* ─────────── 空结果态（≠ 无数据态） ─────────── */
.case-empty {
  padding: 70px 0 90px;
}

.case-empty__title {
  margin: 0 0 12px;
  font-family: var(--serif);
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 400;
}

.case-empty__note {
  max-width: 46ch;
  margin: 0;
  color: var(--muted);
  font-family: var(--serif);
  font-size: 14px;
  line-height: 1.8;
}

.case-empty__reset {
  margin-top: 26px;
  padding: 0 0 7px;
  color: var(--red);
  background: none;
  border: 0;
  border-bottom: 1px solid var(--red);
  font-family: var(--mono);
  font-size: 12px;
  cursor: pointer;
}

@media (max-width: 960px) {
  .case-layout {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .case-filters__toggle {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    width: 100%;
    padding: 16px 0;
    color: var(--ink);
    background: none;
    border: 0;
    border-top: 1px solid var(--ink);
    border-bottom: 1px solid var(--ink);
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.12em;
    cursor: pointer;
  }

  .case-filters__toggle-state {
    color: var(--red);
    font-size: 10px;
  }

  .case-filters {
    position: static;
    display: none;
    padding-bottom: 10px;
  }

  .case-filters.is-open {
    display: block;
  }

  .case-results__head {
    margin-top: 26px;
  }
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
}

@media (prefers-reduced-motion: reduce) {
  .case-row__summary {
    transition: none;
  }
}
</style>
