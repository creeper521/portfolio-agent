<script setup lang="ts">
const entries = [
  { code: '01 / PROJECTS', title: '核心项目', description: '问题、方案、职责、验证、结果与限制。', to: '/projects' },
  { code: '02 / TIMELINE', title: '成长时间线', description: '按阶段回溯问题、行动与能力形成。', to: '/timeline' },
  { code: '03 / EVIDENCE', title: '证据档案', description: '脱敏摘要、公开状态与关联项目。', to: '/evidence' },
  { code: '04 / AGENT', title: '完整对话', description: '长回答、引用核验与历史会话。', to: '/agent' },
]
</script>

<template>
  <section
    id="explore"
    class="explore-index paper-surface"
    data-home-layer="explore"
    aria-labelledby="explore-title"
  >
    <div class="page-shell">
      <div class="explore-index__head">
        <div>
          <p class="eyebrow">03 · EXPLORE THE RECORD</p>
          <h2 id="explore-title">继续深入。</h2>
        </div>
        <p>具体工程产出、成长过程和证据被放进独立页面：主页负责引导，不承担全部内容。</p>
      </div>
      <div class="explore-index__grid">
        <RouterLink
          v-for="entry in entries"
          :key="entry.code"
          data-explore-entry
          :to="entry.to"
        >
          <span>{{ entry.code }}</span>
          <h3>{{ entry.title }}</h3>
          <p>{{ entry.description }}</p>
          <i aria-hidden="true">查看 →</i>
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.explore-index {
  padding: 86px 0 96px;
}

.explore-index__head {
  display: flex;
  margin-bottom: 38px;
  align-items: end;
  justify-content: space-between;
  gap: 40px;
}

.explore-index__head .eyebrow {
  margin: 0 0 10px;
}

.explore-index__head h2 {
  margin: 0;
  font: 600 clamp(42px, 4.4vw, 62px) / 1 var(--serif);
  letter-spacing: -0.045em;
}

.explore-index__head > p {
  max-width: 39ch;
  margin: 0;
  color: var(--ink-2);
  font-size: 14.5px;
  line-height: 1.72;
}

.explore-index__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.explore-index__grid a {
  display: flex;
  min-height: 260px;
  padding: 24px 22px 25px;
  flex-direction: column;
  border-right: 1px solid var(--rule);
  transition: 0.35s var(--ease);
}

.explore-index__grid a:last-child {
  border-right: 0;
}

.explore-index__grid a:hover {
  color: var(--paper);
  background: var(--ink);
  transform: translateY(-8px);
}

.explore-index__grid span {
  color: var(--red);
  font: 10.5px var(--mono);
  letter-spacing: 0.12em;
}

.explore-index__grid h3 {
  margin: 44px 0 14px;
  font: 500 25px/1.15 var(--serif);
}

.explore-index__grid p {
  margin: 0;
  color: var(--ink-2);
  font-size: 12.5px;
  line-height: 1.75;
}

.explore-index__grid a:hover p {
  color: #c6baac;
}

.explore-index__grid i {
  margin-top: auto;
  color: var(--muted);
  font: 11px var(--mono);
  font-style: normal;
}

@media (max-width: 820px) {
  .explore-index__head {
    align-items: start;
    flex-direction: column;
  }

  .explore-index__grid {
    grid-template-columns: 1fr 1fr;
  }

  .explore-index__grid a:nth-child(2) {
    border-right: 0;
  }
}

@media (max-width: 520px) {
  .explore-index__grid {
    grid-template-columns: 1fr;
  }

  .explore-index__grid a {
    min-height: 220px;
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
}
</style>
