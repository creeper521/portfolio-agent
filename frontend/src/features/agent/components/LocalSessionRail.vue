<script setup lang="ts">
import type { AgentSession } from '../model/sessionTypes'

defineProps<{
  sessions: AgentSession[]
  activeId: string
}>()

defineEmits<{
  create: []
  select: [id: string]
  remove: [id: string]
  clear: []
}>()

function timeLabel(timestamp: number) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(timestamp)
}
</script>

<template>
  <aside id="local-session-rail" class="session-rail" aria-label="本地会话">
    <p class="session-rail__label">本地会话</p>
    <button class="session-rail__new" type="button" @click="$emit('create')">
      ＋ 新对话
    </button>

    <div class="session-list">
      <article
        v-for="session in sessions"
        :key="session.id"
        :class="{ active: session.id === activeId }"
      >
        <button class="session-select" type="button" @click="$emit('select', session.id)">
          <strong>{{ session.title }}</strong>
          <small>{{ session.role }} · {{ timeLabel(session.updatedAt) }}</small>
        </button>
        <button
          class="session-remove"
          type="button"
          :aria-label="`删除会话：${session.title}`"
          @click="$emit('remove', session.id)"
        >
          ×
        </button>
      </article>
      <p v-if="!sessions.length" class="session-empty">还没有本地会话。</p>
    </div>

    <footer>
      <p>会话保存在当前浏览器<br />7 天后自动过期</p>
      <button v-if="sessions.length" type="button" @click="$emit('clear')">清除本地记录</button>
    </footer>
  </aside>
</template>

<style scoped>
.session-rail {
  display: flex;
  min-width: 0;
  height: calc(100vh - var(--header-height));
  padding: 25px 23px;
  flex-direction: column;
  color: var(--ink);
  border-right: 1px solid var(--rule);
  background: var(--paper);
  overflow: hidden;
}

.session-rail__label {
  margin: 0 0 10px;
  color: var(--faint);
  font: 10px/1 var(--mono);
  letter-spacing: 0.14em;
}

.session-rail__new {
  min-height: 44px;
  color: var(--paper);
  border: 1px solid var(--ink);
  background: var(--ink);
  font: 11px var(--mono);
  letter-spacing: 0.08em;
}

.session-list {
  min-height: 0;
  margin-top: 24px;
  overflow-y: auto;
}

article {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 28px;
  border-top: 1px solid var(--rule);
}

article.active {
  box-shadow: inset 2px 0 var(--red);
}

.session-select,
.session-remove {
  color: inherit;
  border: 0;
  background: transparent;
}

.session-select {
  display: grid;
  min-width: 0;
  padding: 14px 7px 14px 11px;
  gap: 6px;
  text-align: left;
}

.session-select strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-select small {
  color: var(--faint);
  font: 10px var(--mono);
}

.session-remove {
  color: var(--faint);
  opacity: 0.45;
}

article:hover .session-remove,
.session-remove:focus-visible {
  color: var(--red);
  opacity: 1;
}

.session-empty {
  color: var(--muted);
  font-size: 13px;
}

footer {
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid var(--rule);
}

footer p {
  margin: 0;
  color: var(--faint);
  font: 10px/1.7 var(--mono);
}

footer button {
  margin-top: 8px;
  padding: 8px 0;
  color: var(--red);
  border: 0;
  background: transparent;
  font: 10px var(--mono);
}
</style>
