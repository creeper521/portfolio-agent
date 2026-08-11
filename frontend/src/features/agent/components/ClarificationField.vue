<script setup lang="ts">
import { computed, ref } from 'vue'

import type {
  ClarificationFieldView,
  ClarificationOptionView,
  ClarificationSubmission,
} from '../model/semanticTurnView'

const props = defineProps<{
  field: ClarificationFieldView
  pending?: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ClarificationSubmission] }>()

const selectedValues = ref<string[]>([])
const textValue = ref('')

// 与后端 clarificationResolution.textValue 的 2000 字上限对齐：
// maxlength 阻止继续输入，超限（如程序化写入）同时禁止提交。
const TEXT_VALUE_MAX_LENGTH = 2000

const numberedOptions = computed(() =>
  props.field.options.map((option, index) => ({
    option,
    serial: String(index + 1).padStart(2, '0'),
  })),
)

const selectedOptions = computed(() =>
  props.field.options.filter((option) => selectedValues.value.includes(option.value)),
)

// MULTI_CHOICE 提交是临时兼容路径：合同暂未提供多数组通道，
// 仅当所有选中项都携带受控主体引用时才允许提交（待后端确认多值通道后切换）。
const multiSubmitDisabled = computed(() => {
  if (props.pending || props.readonly) return true
  if (selectedOptions.value.length === 0) return true
  return selectedOptions.value.some((option) => option.subjectReference === null)
})

const multiHint = computed(() => {
  if (selectedOptions.value.length === 0) return '至少选择 1 项后可提交'
  if (selectedOptions.value.some((option) => option.subjectReference === null)) {
    return '包含暂不支持提交的选项'
  }
  return `已选 ${selectedOptions.value.length} 项`
})

const textSubmitDisabled = computed(() =>
  Boolean(props.pending || props.readonly)
  || (props.field.required && textValue.value.trim().length === 0)
  || textValue.value.length > TEXT_VALUE_MAX_LENGTH,
)

function toggleMulti(option: ClarificationOptionView) {
  if (props.pending || props.readonly) return
  selectedValues.value = selectedValues.value.includes(option.value)
    ? selectedValues.value.filter((value) => value !== option.value)
    : [...selectedValues.value, option.value]
}

function submitSingle(option: ClarificationOptionView) {
  if (props.pending || props.readonly) return
  emit('submit', { kind: 'CHOICE', fieldKey: props.field.fieldKey, option })
}

function submitMulti() {
  if (multiSubmitDisabled.value) return
  emit('submit', {
    kind: 'MULTI_CHOICE',
    fieldKey: props.field.fieldKey,
    options: [...selectedOptions.value],
  })
}

function submitText() {
  if (textSubmitDisabled.value) return
  emit('submit', {
    kind: 'TEXT',
    fieldKey: props.field.fieldKey,
    text: textValue.value.trim(),
  })
}
</script>

<template>
  <div class="clarification-field" :data-input-mode="field.inputMode" :data-readonly="readonly ? 'true' : undefined">
    <template v-if="field.inputMode === 'SINGLE_CHOICE'">
      <div v-if="field.options.length" class="clarification-field__options" role="group" aria-label="可选项">
        <button
          v-for="entry in numberedOptions"
          :key="entry.option.value"
          class="clarification-field__chip"
          :data-clarification-option="entry.option.value"
          type="button"
          :disabled="pending || readonly"
          @click="submitSingle(entry.option)"
        >
          <span class="clarification-field__serial" aria-hidden="true">{{ entry.serial }}</span>
          <span class="clarification-field__label">{{ entry.option.label }}</span>
          <span class="clarification-field__hint">选择</span>
        </button>
      </div>
      <p v-else class="clarification-field__empty">暂无可选项，请直接在下方输入框补充说明。</p>
    </template>

    <template v-else-if="field.inputMode === 'MULTI_CHOICE'">
      <div class="clarification-field__options" role="group" aria-label="可多选项">
        <button
          v-for="entry in numberedOptions"
          :key="entry.option.value"
          class="clarification-field__chip"
          :data-clarification-option="entry.option.value"
          type="button"
          :aria-pressed="selectedValues.includes(entry.option.value)"
          :disabled="pending || readonly"
          @click="toggleMulti(entry.option)"
        >
          <span class="clarification-field__serial" aria-hidden="true">{{ entry.serial }}</span>
          <span class="clarification-field__label">{{ entry.option.label }}</span>
          <span class="clarification-field__hint">{{ selectedValues.includes(entry.option.value) ? '已选' : '未选' }}</span>
        </button>
      </div>
      <div class="clarification-field__submit-row">
        <button
          class="clarification-field__submit"
          data-clarification-submit
          type="button"
          :disabled="multiSubmitDisabled"
          @click="submitMulti"
        >确认选择</button>
        <span class="clarification-field__submit-hint">{{ multiHint }}</span>
      </div>
    </template>

    <template v-else-if="field.inputMode === 'SHORT_TEXT'">
      <textarea
        v-model="textValue"
        class="clarification-field__textarea"
        data-clarification-text
        rows="2"
        :maxlength="TEXT_VALUE_MAX_LENGTH"
        :disabled="pending || readonly"
        aria-label="补充说明"
      ></textarea>
      <div class="clarification-field__submit-row">
        <button
          class="clarification-field__submit"
          data-clarification-submit
          type="button"
          :disabled="textSubmitDisabled"
          @click="submitText"
        >提交</button>
        <span class="clarification-field__submit-hint" data-clarification-text-count>{{ textValue.length }}/2000</span>
        <span v-if="field.required" class="clarification-field__submit-hint">内容为空时不可提交</span>
      </div>
    </template>

    <p v-else class="clarification-field__empty">该澄清暂不支持在此提交，请直接在下面对话框补充说明。</p>
  </div>
</template>

<style scoped>
.clarification-field { margin-top: 10px; }
.clarification-field__options { display: flex; flex-direction: column; gap: 8px; max-height: 240px; overflow-y: auto; }
.clarification-field__chip {
  display: flex; align-items: baseline; gap: 12px;
  width: 100%; min-height: 40px; padding: 10px 14px; text-align: left;
  border: 1px solid var(--workspace-rule, var(--rule)); border-radius: var(--agent-radius-sm, 8px);
  background: rgba(255,255,255,0.4); cursor: pointer; font-family: var(--sans);
  transition: border-color var(--agent-motion-fast, 160ms), background var(--agent-motion-fast, 160ms);
}
.clarification-field__chip:hover:not(:disabled) { border-color: var(--workspace-accent, var(--red)); background: var(--paper-hi); }
.clarification-field__chip:disabled { opacity: .55; cursor: default; }
.clarification-field__chip[aria-pressed='true'] { border-color: var(--workspace-accent, var(--red)); background: rgba(122,46,42,0.05); }
.clarification-field__chip[aria-pressed='true'] .clarification-field__hint { color: var(--workspace-accent, var(--red)); }
.clarification-field__serial { font: 13px var(--mono); color: var(--workspace-accent, var(--red)); flex-shrink: 0; }
.clarification-field__label { font-size: 14px; color: var(--workspace-text, var(--ink)); flex: 1; min-width: 0; overflow-wrap: anywhere; }
.clarification-field__hint { font: 10px var(--mono); letter-spacing: .08em; color: var(--workspace-text-faint, var(--faint)); flex-shrink: 0; }
.clarification-field__empty { margin: 0; color: var(--workspace-text-secondary, var(--muted)); font: 11px/1.6 var(--mono); }
.clarification-field__textarea {
  width: 100%; min-height: 56px; padding: 9px 11px; resize: vertical;
  border: 1px solid var(--workspace-rule, var(--rule)); border-radius: var(--agent-radius-sm, 8px);
  background: rgba(255,255,255,0.5); font: 13px/1.6 var(--sans); color: var(--workspace-text, var(--ink));
}
.clarification-field__textarea:focus { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 1px; }
.clarification-field__submit-row { display: flex; align-items: baseline; gap: 10px; margin-top: 8px; }
.clarification-field__submit {
  min-height: 34px; padding: 7px 14px; border-radius: var(--agent-radius-sm, 8px);
  border: 1px solid var(--workspace-accent, var(--red)); background: var(--workspace-accent, var(--red));
  color: var(--paper-hi); font: 11px var(--mono); cursor: pointer;
}
.clarification-field__submit:disabled { opacity: .45; cursor: not-allowed; }
.clarification-field__submit-hint { font: 10px var(--mono); color: var(--workspace-text-faint, var(--faint)); }
@media (prefers-reduced-motion: reduce) {
  .clarification-field__chip { transition: none; }
}
</style>
