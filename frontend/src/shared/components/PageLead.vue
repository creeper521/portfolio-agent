<script setup lang="ts">
withDefaults(
  defineProps<{
    code: string
    title: string
    description: string
    theme?: 'paper' | 'ink-section'
    dark?: boolean
  }>(),
  {
    theme: 'paper',
    dark: false,
  },
)
</script>

<template>
  <header
    class="page-lead"
    data-page-lead
    :data-theme="dark ? 'ink-section' : theme"
    :class="{ 'page-lead--dark': dark || theme === 'ink-section' }"
  >
    <div class="page-shell page-lead__inner">
      <p>{{ code }}</p>
      <h1>{{ title }}</h1>
      <div class="page-lead__description">{{ description }}</div>
    </div>
  </header>
</template>

<style scoped>
.page-lead {
  padding: clamp(74px, 8vw, 112px) 0 58px;
  color: var(--ink);
  border-bottom: 1px solid var(--rule);
  background: var(--paper-hi);
}

.page-lead--dark {
  color: var(--paper);
  border-color: #4a433b;
  background: var(--ink);
}

.page-lead__inner {
  display: grid;
  padding-top: var(--header-height);
  grid-template-columns: 0.34fr 1fr 0.58fr;
  gap: clamp(26px, 5vw, 80px);
  align-items: end;
}

p {
  margin: 0;
  color: var(--red);
  font: 10px var(--mono);
  letter-spacing: 0.15em;
}

.page-lead--dark p {
  color: var(--red-hi);
}

h1 {
  margin: 0;
  font: 400 clamp(44px, 6vw, 78px) / 0.98 var(--serif);
  letter-spacing: -0.045em;
}

.page-lead__description {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}

.page-lead--dark .page-lead__description {
  color: #a99f91;
}

@media (max-width: 820px) {
  .page-lead__inner {
    grid-template-columns: 1fr;
  }
}
</style>
