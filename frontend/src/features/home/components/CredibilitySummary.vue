<script setup lang="ts">
import { computed } from 'vue'

import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'

const props = defineProps<{ portfolio: PublicPortfolio }>()

const metrics = computed(() =>
  [
    {
      value: props.portfolio.projects.length,
      code: 'PROJECTS',
      label: '核心项目',
      description: '项目状态与个人贡献按独立标记。',
      to: '/projects',
    },
    {
      value: props.portfolio.evidence.length,
      code: 'RECORDS',
      label: '脱敏证据',
      description: '只展示通过公开审查的索引。',
      to: '/evidence',
    },
    {
      value: props.portfolio.timeline.length,
      code: 'MILESTONES',
      label: '成长记录',
      description: '记录问题、行动与能力影响。',
      to: '/timeline',
    },
  ].filter((metric) => metric.value > 0),
)
</script>

<template>
  <section
    id="credibility"
    class="credibility paper-surface"
    data-home-layer="credibility"
    aria-labelledby="credibility-title"
  >
    <div class="page-shell">
      <div class="section-intro">
        <p class="eyebrow">01 · VERIFIED OVERVIEW</p>
        <div>
          <h2 id="credibility-title">先建立可信度，<br />再进入细节。</h2>
          <p>首页只展示能够由公开内容核对的规模摘要。点击任一数字，进入对应的独立页面。</p>
        </div>
      </div>

      <div class="credibility__grid">
        <RouterLink
          v-for="metric in metrics"
          :key="metric.code"
          data-credibility-metric
          :to="metric.to"
        >
          <strong>{{ String(metric.value).padStart(2, '0') }}</strong>
          <span>{{ metric.code }} · {{ metric.label }}</span>
          <p>{{ metric.description }}</p>
          <i aria-hidden="true">↗</i>
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.credibility {
  padding: 80px 0 90px;
  border-bottom: 1px solid var(--rule);
}

.section-intro {
  display: grid;
  margin-bottom: 42px;
  grid-template-columns: 190px 1fr;
  gap: 40px;
  align-items: start;
}

.section-intro > p {
  margin: 0;
}

.section-intro h2 {
  max-width: 14ch;
  margin: 0;
  font: 600 clamp(38px, 3.8vw, 56px) / 1.08 var(--serif);
  letter-spacing: -0.035em;
}

.section-intro div > p {
  max-width: 56ch;
  margin: 14px 0 0;
  color: var(--ink-2);
  font-size: 16.5px;
  line-height: 1.75;
}

.credibility__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.credibility__grid a {
  position: relative;
  padding: 32px 30px 38px;
  border-right: 1px solid var(--rule);
  transition: 0.35s var(--ease);
}

.credibility__grid a:last-child {
  border-right: 0;
}

.credibility__grid a:hover {
  background: var(--paper-hi);
  transform: translateY(-6px);
}

.credibility__grid strong {
  font: 400 clamp(60px, 5.2vw, 82px) / 1 var(--serif);
  letter-spacing: -0.06em;
}

.credibility__grid span {
  display: block;
  margin-top: 15px;
  color: var(--red);
  font: 12px var(--mono);
  letter-spacing: 0.11em;
}

.credibility__grid p {
  margin: 12px 0 0;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.68;
}

.credibility__grid i {
  position: absolute;
  top: 24px;
  right: 24px;
  color: var(--muted);
  font: 18px var(--serif);
  font-style: normal;
}

@media (max-width: 760px) {
  .section-intro {
    grid-template-columns: 1fr;
  }

  .credibility__grid {
    grid-template-columns: 1fr;
  }

  .credibility__grid a {
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
}
</style>
