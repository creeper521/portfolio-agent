<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import type { ErrorAction } from '../../features/portfolio/api/apiErrorActions'

const props = withDefaults(defineProps<{
  status: 'loading' | 'error'
  message?: string
  action?: ErrorAction
  retryAfterSeconds?: number
}>(), {
  action: 'RETRY',
  retryAfterSeconds: 0,
})

defineEmits<{
  retry: []
}>()

const remainingSeconds = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

function clearCountdown(): void {
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = null
}

watch(
  () => [props.action, props.retryAfterSeconds] as const,
  ([action, seconds]) => {
    clearCountdown()
    remainingSeconds.value = action === 'RETRY_AFTER'
      ? Math.max(0, Math.ceil(seconds))
      : 0
    if (remainingSeconds.value === 0) return
    countdownTimer = setInterval(() => {
      remainingSeconds.value = Math.max(0, remainingSeconds.value - 1)
      if (remainingSeconds.value === 0) clearCountdown()
    }, 1_000)
  },
  { immediate: true },
)

onBeforeUnmount(clearCountdown)
</script>

<template>
  <main class="public-content-feedback" :aria-busy="status === 'loading'">
    <p v-if="status === 'loading'">正在装订公开档案…</p>
    <template v-else>
      <p role="alert">{{ message || '公开内容暂时无法加载，请稍后重试' }}</p>
      <button
        v-if="action === 'RETRY'"
        data-public-content-retry
        type="button"
        @click="$emit('retry')"
      >
        重新加载
      </button>
      <button
        v-else-if="action === 'RETRY_AFTER'"
        data-public-content-retry-after
        type="button"
        :disabled="remainingSeconds > 0"
        @click="$emit('retry')"
      >
        {{ remainingSeconds > 0 ? `${remainingSeconds} 秒后可重试` : '重新加载' }}
      </button>
      <a
        v-else-if="action === 'NAVIGATE_BACK'"
        data-public-content-navigate-back
        href="/projects"
      >
        返回作品集
      </a>
      <a
        v-else-if="action === 'CORRECT_INPUT'"
        data-public-content-correct-input
        href="/"
      >
        返回安全入口
      </a>
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

button,
a {
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

button:disabled {
  cursor: wait;
  opacity: 0.6;
}
</style>
