<script setup lang="ts">
defineProps<{
  status: 'loading' | 'error'
  message?: string
}>()

defineEmits<{
  retry: []
}>()
</script>

<template>
  <main class="public-content-feedback" :aria-busy="status === 'loading'">
    <p v-if="status === 'loading'">正在装订公开档案…</p>
    <template v-else>
      <p role="alert">{{ message || '公开内容暂时无法加载，请稍后重试' }}</p>
      <button data-public-content-retry type="button" @click="$emit('retry')">
        重新加载
      </button>
    </template>
  </main>
</template>

<style scoped>
.public-content-feedback {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  padding: var(--page-gutter);
  place-content: center;
  justify-items: center;
  gap: 24px;
  color: var(--muted);
  background: var(--paper);
  text-align: center;
}

p {
  max-width: 620px;
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(20px, 3vw, 30px);
  line-height: 1.6;
}

[aria-busy='true'] p {
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

[role='alert'] {
  color: var(--red);
}

button {
  padding: 10px 0 7px;
  color: var(--red);
  cursor: pointer;
  border: 0;
  border-bottom: 1px solid var(--red);
  background: transparent;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.08em;
}
</style>
