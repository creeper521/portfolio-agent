<script setup lang="ts">
import { nextTick, ref } from 'vue'

import type { AgentSession } from '../model/sessionTypes'

defineProps<{
  sessions: AgentSession[]
  activeId: string
}>()

const emit = defineEmits<{
  create: []
  select: [id: string]
  rename: [id: string, title: string]
  remove: [id: string]
  clear: []
}>()

const menuId = ref('')
const editingId = ref('')
const draftTitle = ref('')
const clearConfirmationOpen = ref(false)
const renameInput = ref<HTMLInputElement | HTMLInputElement[] | null>(null)
const clearTrigger = ref<HTMLButtonElement | null>(null)
const clearCancelButton = ref<HTMLButtonElement | null>(null)

async function beginRename(session: AgentSession) {
  editingId.value = session.id
  draftTitle.value = session.title
  menuId.value = ''
  await nextTick()
  const input = Array.isArray(renameInput.value) ? renameInput.value[0] : renameInput.value
  input?.select()
}

function confirmRename() {
  const title = draftTitle.value.trim()
  if (editingId.value && title) emit('rename', editingId.value, title)
  editingId.value = ''
}

async function beginClear() {
  clearConfirmationOpen.value = true
  menuId.value = ''
  await nextTick()
  clearCancelButton.value?.focus()
}

async function cancelClear() {
  clearConfirmationOpen.value = false
  await nextTick()
  clearTrigger.value?.focus()
}

function confirmClear() {
  clearConfirmationOpen.value = false
  emit('clear')
}
</script>

<template>
  <aside id="local-session-rail" class="session-rail" aria-label="会话">
    <button class="session-rail__new" type="button" @click="emit('create')">
      <span aria-hidden="true">＋</span>新对话
    </button>

    <div class="session-list">
      <article
        v-for="session in sessions"
        :key="session.id"
        :class="{
          active: session.id === activeId,
          'menu-open': menuId === session.id,
        }"
      >
        <form
          v-if="editingId === session.id"
          data-session-rename-form
          @submit.prevent="confirmRename"
        >
          <input
            ref="renameInput"
            v-model="draftTitle"
            data-session-rename-input
            maxlength="40"
            aria-label="会话标题"
            @keydown.esc="editingId = ''"
          />
        </form>
        <button
          v-else
          class="session-select"
          type="button"
          :aria-current="session.id === activeId ? 'true' : undefined"
          @click="emit('select', session.id)"
        >
          {{ session.title }}
        </button>

        <button
          v-if="editingId !== session.id"
          data-session-menu
          class="session-menu-trigger"
          type="button"
          :aria-expanded="menuId === session.id"
          :aria-label="`管理会话：${session.title}`"
          @click="menuId = menuId === session.id ? '' : session.id"
        >···</button>
        <div v-if="menuId === session.id" class="session-menu">
          <button data-session-rename type="button" @click="beginRename(session)">重命名</button>
          <button data-session-remove type="button" @click="emit('remove', session.id)">删除</button>
        </div>
      </article>
      <p v-if="!sessions.length" class="session-empty">还没有会话。</p>
    </div>

    <footer>
      <p>会话仅保留在当前标签页</p>
      <button
        v-if="sessions.length"
        ref="clearTrigger"
        data-session-clear
        type="button"
        @click="beginClear"
      >
        清空本地会话
      </button>
      <div
        v-if="clearConfirmationOpen"
        class="clear-confirmation"
        role="alertdialog"
        aria-labelledby="clear-sessions-title"
        aria-describedby="clear-sessions-description"
        @keydown.esc.stop.prevent="cancelClear"
      >
        <h3 id="clear-sessions-title">清空本地会话？</h3>
        <p id="clear-sessions-description">此操作会移除当前标签页中的全部会话记录。</p>
        <div>
          <button
            ref="clearCancelButton"
            data-session-clear-cancel
            type="button"
            @click="cancelClear"
          >取消</button>
          <button
            data-session-clear-confirm
            type="button"
            @click="confirmClear"
          >确认清空</button>
        </div>
      </div>
    </footer>
  </aside>
</template>

<style scoped>
.session-rail {
  display: flex;
  min-width: 0;
  height: 100%;
  padding: 18px 0;
  flex-direction: column;
  color: var(--workspace-text, var(--ink));
  border-right: 1px solid var(--workspace-rule, var(--rule));
  background: var(--workspace-rail-bg, var(--paper));
  overflow: hidden;
}

.session-rail__new {
  min-height: 44px;
  margin: 0 18px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 1px solid var(--workspace-primary-bg, var(--ink));
  background: var(--workspace-primary-bg, var(--ink));
  font: 13px var(--mono);
  letter-spacing: 0.08em;
}

.session-rail__new:not(:disabled):hover {
  background: var(--ink-2);
}

.session-list {
  min-height: 0;
  margin-top: 18px;
  overflow-y: auto;
}

article {
  position: relative;
  display: grid;
  min-height: 48px;
  grid-template-columns: minmax(0, 1fr) 34px;
}

article.active {
  background: var(--workspace-surface-subtle, var(--paper-low));
  box-shadow: inset 2px 0 var(--workspace-accent, var(--red));
}

article.menu-open {
  min-height: 88px;
}

.session-select,
.session-menu-trigger {
  color: inherit;
  border: 0;
  background: transparent;
}

.session-select {
  min-width: 0;
  padding: 0 8px 0 18px;
  overflow: hidden;
  font-size: 13px;
  line-height: 48px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-menu-trigger {
  padding: 0;
  color: var(--faint);
  font: 16px/1 var(--mono);
  opacity: 0;
}

article:hover .session-menu-trigger,
article:focus-within .session-menu-trigger {
  opacity: 1;
}

.session-menu {
  display: flex;
  padding: 0 18px 8px;
  grid-column: 1 / -1;
  gap: 4px;
}

.session-menu button {
  flex: 1;
  padding: 8px 10px;
  color: inherit;
  border: 0;
  background: transparent;
  font: 12px var(--mono);
  text-align: left;
}

.session-menu button:hover,
.session-menu button:focus-visible {
  background: var(--workspace-surface-subtle, var(--paper-low));
}

form {
  grid-column: 1 / -1;
}

input {
  width: 100%;
  min-height: 48px;
  padding: 0 18px;
  color: inherit;
  border: 0;
  background: transparent;
  font: 13px var(--sans);
}

.session-empty {
  margin: 18px;
  color: var(--muted);
  font-size: 13px;
}

footer {
  margin-top: auto;
  padding: 15px 18px 0;
  border-top: 1px solid var(--workspace-rule, var(--rule));
}

footer p {
  margin: 0;
  color: var(--faint);
  font: 11px/1.7 var(--mono);
}

footer button {
  margin-top: 8px;
  padding: 8px 0;
  color: var(--red);
  border: 0;
  background: transparent;
  font: 11px var(--mono);
}

.clear-confirmation {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm);
  background: var(--workspace-rail-bg, var(--paper));
}

.clear-confirmation h3 {
  margin: 0 0 6px;
  font: 500 14px var(--serif);
}

.clear-confirmation p {
  color: var(--muted);
  font: 11px/1.6 var(--sans);
}

.clear-confirmation > div {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.clear-confirmation button {
  margin-top: 8px;
  padding: 6px 8px;
  border: 1px solid currentcolor;
}
</style>
