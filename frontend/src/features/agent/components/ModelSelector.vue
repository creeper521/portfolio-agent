<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import {
  displayNameOfSelection,
  sameModelSelection,
  type ModelCatalogProjection,
  type ModelSelection,
} from '../model/modelSelection'

// 模型选择器（UI spec §2，布局 A：发送区内联）：触发钮 + 向上弹出的目录浮层。
// 纯 props 驱动：目录/默认/当前选择全部来自 /api/portfolio 投影与会话内存偏好，
// 组件不持有目录知识、不持久化任何状态（D-MS-3）；pending 锁定由 locked 表达（D-MS-4）。
// 目录条目没有"暂不可用"公开标记；不可用条目表现为目录刷新后缺席，由 Workspace
// 执行 §2.9 stale 回退，本组件不自造第三种条目状态。

const props = withDefaults(
  defineProps<{
    catalog: ModelCatalogProjection
    selection: ModelSelection
    locked?: boolean
  }>(),
  { locked: false },
)

const emit = defineEmits<{
  select: [selection: ModelSelection]
}>()

const open = ref(false)
const triggerElement = ref<HTMLButtonElement | null>(null)

const emptyCatalog = computed(() => props.catalog.selectableModels.length === 0)
const currentDisplayName = computed(() =>
  displayNameOfSelection(props.catalog, props.selection),
)
const defaultSelection = computed(() => props.catalog.defaultModelSelection)

function isSelected(modelRef: string, selectionVersion: string): boolean {
  return sameModelSelection(props.selection, {
    kind: 'MODEL',
    modelRef,
    selectionVersion,
  })
}

function optionId(modelRef: string): string {
  return `model-option-${modelRef}`
}

function toggleOpen(): void {
  if (props.locked || emptyCatalog.value) return
  open.value = !open.value
  if (open.value) {
    focusSelectedOption()
  }
}

function close(returnFocus = true): void {
  open.value = false
  if (returnFocus) {
    triggerElement.value?.focus()
  }
}

function cssEscape(value: string): string {
  const escapeFn = globalThis.CSS?.escape?.bind(globalThis.CSS)
  if (escapeFn !== undefined) return escapeFn(value)
  return value.replace(/["\\]/g, '\\$&')
}

function focusSelectedOption(): void {
  const target = props.selection.kind === 'MODEL' ? props.selection.modelRef : null
  queueMicrotask(() => {
    const selector = target === null
      ? '[data-testid="model-selector-option"]'
      : `#${cssEscape(optionId(target))}`
    document.querySelector<HTMLElement>(selector)?.focus()
  })
}

function onOptionKeydown(event: KeyboardEvent, modelRef: string): void {
  const options = [...document.querySelectorAll<HTMLElement>('[data-testid="model-selector-option"]')]
  const index = options.findIndex((element) => element.dataset.modelRef === modelRef)
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    options[(index + 1) % options.length]?.focus()
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    options[(index - 1 + options.length) % options.length]?.focus()
    return
  }
  if (event.key === 'Enter' || event.key === ' ') {
    // 焦点条目上的 Enter/Space 即确认选择；显式处理不依赖浏览器合成 click。
    event.preventDefault()
    pick(modelRef, true)
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
  }
}

function onPopoverKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
  }
}

function pick(modelRef: string, returnFocus = false): void {
  close(returnFocus)
  const entry = props.catalog.selectableModels.find((model) => model.modelRef === modelRef)
  if (entry === undefined) return
  const selection: ModelSelection = {
    kind: 'MODEL',
    modelRef: entry.modelRef,
    selectionVersion: entry.selectionVersion,
  }
  if (sameModelSelection(selection, props.selection)) return
  emit('select', selection)
}

function onDocumentClick(event: MouseEvent): void {
  if (!open.value) return
  const target = event.target as Node | null
  if (target !== null && triggerElement.value?.closest('.model-selector')?.contains(target)) {
    return
  }
  open.value = false
}

watch(() => props.locked, (locked) => {
  if (locked) open.value = false
})

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <div v-if="emptyCatalog" class="model-selector model-selector--none" data-testid="model-selector-none">
    <p class="model-selector__none-title">确定性回答 · 未配置模型</p>
    <p class="model-selector__none-note">当前部署未配置可选模型，仅提供基于公开资料的确定性回答</p>
  </div>
  <div v-else class="model-selector">
    <button
      ref="triggerElement"
      type="button"
      class="model-selector__trigger"
      data-testid="model-selector-trigger"
      :disabled="locked"
      :aria-disabled="locked ? 'true' : undefined"
      aria-haspopup="listbox"
      :aria-expanded="open ? 'true' : 'false'"
      @click="toggleOpen"
      @keydown.enter.prevent="toggleOpen"
      @keydown.space.prevent="toggleOpen"
    >
      <span class="model-selector__dot" aria-hidden="true"></span>
      <span class="model-selector__name">{{ currentDisplayName }}</span>
      <span class="model-selector__caret" aria-hidden="true">▾</span>
    </button>
    <p
      v-if="locked"
      class="model-selector__lock-note"
      data-testid="model-selector-lock-note"
    >回答生成中 · 本轮结束后可切换模型</p>
    <div
      v-else-if="open"
      class="model-selector__popover"
      data-testid="model-selector-popover"
      data-open="true"
      role="listbox"
      aria-label="选择回答模型"
      @keydown="onPopoverKeydown"
    >
      <button
        v-for="model in catalog.selectableModels"
        :id="optionId(model.modelRef)"
        :key="model.modelRef"
        type="button"
        class="model-selector__option"
        data-testid="model-selector-option"
        :data-model-ref="model.modelRef"
        role="option"
        tabindex="-1"
        :aria-selected="isSelected(model.modelRef, model.selectionVersion) ? 'true' : 'false'"
        @click="pick(model.modelRef)"
        @keydown="onOptionKeydown($event, model.modelRef)"
      >
        <span class="model-selector__option-name">
          <span>{{ model.displayName }}</span>
          <span
            v-if="sameModelSelection(defaultSelection, { kind: 'MODEL', modelRef: model.modelRef, selectionVersion: model.selectionVersion })"
            class="model-selector__option-badge"
          >目录默认</span>
        </span>
        <span class="model-selector__option-ref">{{ model.modelRef }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.model-selector {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.model-selector__trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 999px;
  background: var(--paper-hi, #fff);
  color: var(--workspace-text, var(--ink));
  font: 11px/1.4 var(--mono);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: border-color 160ms ease;
}
.model-selector__trigger:hover:not(:disabled) {
  border-color: var(--workspace-accent, var(--red));
}
.model-selector__trigger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.model-selector__trigger:focus-visible {
  outline: 2px solid var(--workspace-accent, var(--red));
  outline-offset: 2px;
}
.model-selector__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--workspace-accent, var(--red));
}
.model-selector__caret {
  color: var(--workspace-text-faint, var(--faint));
}
.model-selector__lock-note {
  margin: 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10.5px/1.6 var(--mono);
}
.model-selector__popover {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  z-index: 30;
  width: 252px;
  max-width: min(252px, calc(100vw - 48px));
  padding: 6px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-md, 12px);
  background: var(--paper-hi, #fff);
  box-shadow: 0 18px 44px rgba(32, 28, 23, 0.28);
}
.model-selector__option {
  display: block;
  width: 100%;
  padding: 9px 11px;
  border: 0;
  border-radius: var(--agent-radius-sm, 8px);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.model-selector__option:hover,
.model-selector__option:focus-visible {
  background: color-mix(in srgb, var(--workspace-accent, var(--red)) 6%, var(--paper-hi, #fff));
  outline: none;
}
.model-selector__option[aria-selected='true'] {
  background: color-mix(in srgb, var(--workspace-accent, var(--red)) 10%, var(--paper-hi, #fff));
}
.model-selector__option-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--workspace-text, var(--ink));
  font: 600 13px/1.4 var(--sans);
}
.model-selector__option-badge {
  padding: 1px 7px;
  border: 1px solid color-mix(in srgb, var(--workspace-accent, var(--red)) 40%, var(--rule));
  border-radius: 999px;
  color: var(--workspace-accent, var(--red));
  font: 9px/1.5 var(--mono);
  letter-spacing: 0.1em;
}
.model-selector__option-ref {
  display: block;
  margin-top: 3px;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.5 var(--mono);
}
.model-selector--none {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}
.model-selector__none-title {
  margin: 0;
  padding: 6px 12px;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: 999px;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.4 var(--mono);
}
.model-selector__none-note {
  margin: 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 10.5px/1.6 var(--mono);
}
</style>
