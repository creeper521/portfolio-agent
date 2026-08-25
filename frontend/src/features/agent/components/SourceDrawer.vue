<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { PublicSourceReference } from '../model/publicAgentTurn'

// Answer 级"查看全部来源"抽屉：右侧滑出面板，列出该 Answer 唯一
// sourceCatalog 的全部公开来源与内容版本号。开合由父组件经 props.open
// 控制，关闭时 emit close；数据全部来自 props，本地只保存焦点管理所需
// 的引用。可访问性约定：role=dialog + aria-modal、Esc 关闭、Tab 键焦点
// 陷阱、关闭后焦点返回触发元素（D-41.7）；contentReleaseId 只出现在
// 抽屉底部详情区，不占正文 header（D-41.8）。

const props = defineProps<{
  open: boolean
  sources: readonly PublicSourceReference[]
  contentReleaseId: string
}>()

const emit = defineEmits<{
  close: []
}>()

const dialogRef = ref<HTMLElement | null>(null)
// 打开抽屉前的焦点元素；关闭后把焦点归还给它，避免键盘用户丢失位置。
let returnFocusTarget: HTMLElement | null = null

const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled])'

/** 收集抽屉内当前可聚焦元素（链接与未禁用按钮），供焦点陷阱循环。 */
function focusables(): readonly HTMLElement[] {
  if (dialogRef.value === null) return []
  return [...dialogRef.value.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)]
}

function requestClose(): void {
  emit('close')
}

/**
 * Tab 焦点陷阱：Shift+Tab 在首元素处跳到末元素，Tab 在末元素处跳回首
 * 元素；焦点意外落在对话框外时拉回首元素。无任何可聚焦元素时聚焦
 * 对话框容器本身，保证键盘不会逃逸到遮罩之下的正文。
 */
async function trapTab(event: KeyboardEvent): Promise<void> {
  const list = focusables()
  if (list.length === 0) {
    event.preventDefault()
    dialogRef.value?.focus()
    return
  }
  const first = list[0] as HTMLElement
  const last = list[list.length - 1] as HTMLElement
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === dialogRef.value)) {
    event.preventDefault()
    last.focus()
    return
  }
  if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
    return
  }
  await nextTick()
  if (!dialogRef.value?.contains(document.activeElement)) {
    event.preventDefault()
    first.focus()
  }
}

// 开合焦点管理：打开时记录触发元素并把焦点移入抽屉（优先首个可聚焦
// 元素）；关闭时归还焦点给触发元素（用户焦点已移到 body 时不强抢）。
watch(
  () => props.open,
  async (open) => {
    if (open) {
      returnFocusTarget = (document.activeElement as HTMLElement | null) ?? null
      await nextTick()
      const first = focusables()[0]
      if (first !== undefined) {
        first.focus()
      } else {
        dialogRef.value?.focus()
      }
      return
    }
    if (returnFocusTarget !== null && document.activeElement !== document.body) {
      returnFocusTarget.focus()
    }
    returnFocusTarget = null
  },
  { immediate: true },
)

// 组件在打开状态下被卸载时放弃归还焦点，避免聚焦已移除的节点。
onBeforeUnmount(() => {
  returnFocusTarget = null
})
</script>

<template>
  <div v-if="open" class="source-drawer-root">
    <button
      class="source-drawer__overlay"
      type="button"
      aria-label="关闭来源抽屉"
      data-testid="source-drawer-overlay"
      @click="requestClose"
    ></button>
    <div
      ref="dialogRef"
      class="source-drawer"
      role="dialog"
      aria-modal="true"
      aria-label="全部来源"
      data-testid="source-drawer"
      tabindex="-1"
      @keydown.esc.stop.prevent="requestClose"
      @keydown.tab="trapTab"
    >
      <div class="source-drawer__head">
        <h4 class="source-drawer__title">全部来源</h4>
        <button
          class="source-drawer__close"
          type="button"
          data-testid="source-drawer-close"
          @click="requestClose"
        >关闭</button>
      </div>
      <ul class="source-drawer__list">
        <li v-for="source in sources" :key="source.key" class="source-drawer__item" :data-source-key="source.key">
          <RouterLink :to="source.route" class="source-drawer__link">
            <span v-if="source.code !== undefined" class="source-drawer__code">{{ source.code }}</span>
            <span class="source-drawer__label">{{ source.label }}</span>
          </RouterLink>
        </li>
      </ul>
      <p class="source-drawer__release">内容版本：{{ contentReleaseId }}</p>
    </div>
  </div>
</template>

<style scoped>
.source-drawer-root { position: fixed; inset: 0; z-index: 60; }
.source-drawer__overlay {
  position: absolute;
  inset: 0;
  border: none;
  background: rgba(32, 28, 23, 0.42);
  cursor: pointer;
}
.source-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(400px, 92vw);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 20px;
  overflow-y: auto;
  border-left: 1px solid var(--workspace-rule, var(--rule));
  background: var(--paper-hi);
  box-shadow: var(--agent-shell-shadow, 0 18px 48px rgba(0, 0, 0, 0.32));
}
.source-drawer:focus { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: -2px; }
.source-drawer__head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; }
.source-drawer__title { margin: 0; color: var(--workspace-text, var(--ink)); font: 600 14px/1.4 var(--sans); }
.source-drawer__close {
  min-height: 30px;
  padding: 5px 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  background: transparent;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px var(--mono);
  cursor: pointer;
}
.source-drawer__close:hover { border-color: var(--workspace-accent, var(--red)); color: var(--workspace-accent, var(--red)); }
.source-drawer__list { margin: 0; padding: 0; list-style: none; }
.source-drawer__item { border-bottom: 1px solid var(--workspace-rule, var(--rule)); }
.source-drawer__item:last-child { border-bottom: none; }
.source-drawer__link {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 2px;
  color: var(--workspace-text, var(--ink));
  text-decoration: none;
}
.source-drawer__link:hover .source-drawer__label { text-decoration: underline; }
.source-drawer__code {
  padding: 0 6px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 4px;
  color: var(--workspace-accent, var(--red));
  font: 10px/1.7 var(--mono);
  flex-shrink: 0;
}
.source-drawer__label { font: 12px/1.6 var(--sans); overflow-wrap: anywhere; }
.source-drawer__release { margin: auto 0 0; color: var(--workspace-text-faint, var(--faint)); font: 10px/1.6 var(--mono); }
</style>
