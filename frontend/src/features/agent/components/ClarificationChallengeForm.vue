<script setup lang="ts">
import { computed, ref } from 'vue'

import type {
  ClarificationChallenge,
  ClarificationFieldAnswer,
  ClarificationSubmissionPayload,
} from '../model/publicAgentTurn'

// D-38.14 / 前端交接 §4-5：澄清挑战使用 opaque clarificationId/fieldId/choiceId，
// 前端不接触 promptCode、subject binding 或内部 Task；SINGLE_CHOICE 用原生
// radio group（fieldset/legend），TEXT 用 bounded textarea。提交事件只携带
// clarificationId + 闭合答案，由上层在未来 API 接线时转为 RESOLVE_CLARIFICATION。

const props = defineProps<{
  challenge: ClarificationChallenge
  submitLabel?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [payload: ClarificationSubmissionPayload]
}>()

const TEXT_FALLBACK_LIMIT = 2000

const selectedChoiceIds = ref<Record<string, string>>({})
const textValues = ref<Record<string, string>>({})

const answered = computed(() =>
  props.challenge.fields.every((field) => {
    if (field.kind === 'SINGLE_CHOICE') {
      return selectedChoiceIds.value[field.fieldId] !== undefined
    }
    return (textValues.value[field.fieldId] ?? '').trim().length > 0
  }),
)

const submitDisabled = computed(() => Boolean(props.disabled) || !answered.value)

function textLimitOf(field: { limit?: number }): number {
  return field.limit === undefined ? TEXT_FALLBACK_LIMIT : field.limit
}

function submit(): void {
  if (submitDisabled.value) return
  const answers: ClarificationFieldAnswer[] = props.challenge.fields.map((field) => {
    if (field.kind === 'SINGLE_CHOICE') {
      return {
        fieldId: field.fieldId,
        kind: 'SINGLE_CHOICE' as const,
        choiceId: selectedChoiceIds.value[field.fieldId] as string,
      }
    }
    return {
      fieldId: field.fieldId,
      kind: 'TEXT' as const,
      text: (textValues.value[field.fieldId] ?? '').trim(),
    }
  })
  emit('submit', { clarificationId: props.challenge.clarificationId, answers })
}
</script>

<template>
  <form class="clarification-form" data-testid="clarification-form" @submit.prevent="submit">
    <p class="clarification-form__prompt">{{ challenge.prompt }}</p>
    <fieldset
      v-for="field in challenge.fields"
      :key="field.fieldId"
      class="clarification-form__field"
      :data-field-id="field.fieldId"
      :data-field-kind="field.kind"
    >
      <legend class="clarification-form__legend">{{ field.label }}<span v-if="field.required" aria-hidden="true">（必填）</span></legend>
      <template v-if="field.kind === 'SINGLE_CHOICE'">
        <div
          v-for="choice in field.choices"
          :key="choice.choiceId"
          class="clarification-form__choice"
          :data-choice-id="choice.choiceId"
        >
          <input
            :id="`${challenge.clarificationId}-${field.fieldId}-${choice.choiceId}`"
            v-model="selectedChoiceIds[field.fieldId]"
            type="radio"
            :name="`${challenge.clarificationId}-${field.fieldId}`"
            :value="choice.choiceId"
            :disabled="disabled"
            :aria-required="field.required ? 'true' : undefined"
          />
          <label :for="`${challenge.clarificationId}-${field.fieldId}-${choice.choiceId}`">{{ choice.label }}</label>
        </div>
      </template>
      <template v-else>
        <textarea
          v-model="textValues[field.fieldId]"
          class="clarification-form__textarea"
          data-clarification-text
          rows="2"
          :maxlength="textLimitOf(field)"
          :disabled="disabled"
          :aria-label="field.label"
        ></textarea>
        <p class="clarification-form__hint" data-clarification-text-count>
          {{ (textValues[field.fieldId] ?? '').length }}/{{ textLimitOf(field) }}
        </p>
      </template>
    </fieldset>
    <div class="clarification-form__submit-row">
      <button
        class="clarification-form__submit"
        type="submit"
        data-clarification-submit
        :disabled="submitDisabled"
      >{{ submitLabel ?? '提交补充' }}</button>
      <span v-if="!answered" class="clarification-form__hint">请完成必填项后提交</span>
    </div>
  </form>
</template>

<style scoped>
.clarification-form {
  margin: 12px 0 0;
  padding: 14px 16px;
  border: 1px solid var(--workspace-accent, var(--red));
  background: var(--paper-hi);
}
.clarification-form__prompt {
  position: relative;
  margin: 0 0 10px;
  padding-left: 18px;
  color: var(--workspace-text, var(--ink));
  font: 500 15px/1.6 var(--serif);
  overflow-wrap: anywhere;
}
.clarification-form__prompt::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.6em;
  width: 7px;
  height: 7px;
  background: var(--workspace-accent, var(--red));
  transform: rotate(45deg);
}
.clarification-form__field {
  margin: 0 0 10px;
  padding: 0;
  border: none;
}
.clarification-form__legend {
  padding: 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
.clarification-form__choice {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-height: 34px;
  margin-top: 6px;
}
.clarification-form__choice input[type='radio']:focus-visible {
  outline: 2px solid var(--workspace-accent, var(--red));
  outline-offset: 2px;
}
.clarification-form__choice label {
  color: var(--workspace-text, var(--ink));
  font: 13px/1.6 var(--sans);
  overflow-wrap: anywhere;
}
.clarification-form__textarea {
  width: 100%;
  min-height: 56px;
  margin-top: 6px;
  padding: 9px 11px;
  resize: vertical;
  border: 1px solid var(--workspace-rule, var(--rule));
  border-radius: var(--agent-radius-sm, 8px);
  background: rgba(255, 255, 255, 0.5);
  font: 13px/1.6 var(--sans);
  color: var(--workspace-text, var(--ink));
}
.clarification-form__textarea:focus { outline: 2px solid var(--workspace-accent, var(--red)); outline-offset: 1px; }
.clarification-form__hint { margin: 4px 0 0; color: var(--workspace-text-faint, var(--faint)); font: 10px/1.6 var(--mono); }
.clarification-form__submit-row { display: flex; align-items: baseline; gap: 10px; margin-top: 10px; }
.clarification-form__submit {
  min-height: 34px;
  padding: 7px 14px;
  border: 1px solid var(--workspace-accent, var(--red));
  border-radius: var(--agent-radius-sm, 8px);
  background: var(--workspace-accent, var(--red));
  color: var(--paper-hi);
  font: 11px var(--mono);
  cursor: pointer;
}
.clarification-form__submit:disabled { opacity: 0.45; cursor: not-allowed; }
</style>
