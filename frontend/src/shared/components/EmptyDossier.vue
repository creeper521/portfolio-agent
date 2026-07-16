<script setup lang="ts">
defineProps<{
  code?: string
  title: string
  description: string
}>()
</script>

<template>
  <section class="empty-dossier" role="status">
    <span>{{ code ?? 'PENDING' }}</span>
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <slot />
  </section>
</template>

<style scoped>
.empty-dossier {
  max-width: 720px;
  margin: 90px auto;
  padding: 48px 0;
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

span {
  color: var(--red);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.14em;
}

h2 {
  margin: 24px 0 16px;
  font-family: var(--serif);
  font-size: 36px;
  font-weight: 400;
}

p {
  margin: 0 0 24px;
  color: var(--muted);
  line-height: 1.8;
}
</style>
