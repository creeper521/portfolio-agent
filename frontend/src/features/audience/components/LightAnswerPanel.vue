<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type {
  AudienceRole,
  PublicEvidence,
} from '../../public-content/model/publicContentTypes'
import type { HomeAnswerState } from '../model/audienceTypes'
import { createAgentHandoff } from '../../agent/model/handoffStore'
import {
  answerSourceTag,
  answerStatusLabel,
  answerTechTail,
} from '../../agent/model/answerLabels'

const props = defineProps<{
  role: AudienceRole
  answer: HomeAnswerState
  evidence: PublicEvidence[]
}>()

defineEmits<{ followUp: [] }>()

const visibleAnswer = ref('')
const complete = ref(false)
let typingTimer: ReturnType<typeof setInterval> | null = null

const citedEvidence = computed(() =>
  props.evidence.filter((item) => props.answer.evidenceIds.includes(item.id)),
)

const projectTarget = computed(() =>
  props.answer.projectSlug ? `/projects/${props.answer.projectSlug}` : '/projects',
)

const agentTarget = computed(() => ({
  path: '/agent',
  query: {
    handoffId: createAgentHandoff({
      role: props.role,
      question: props.answer.question,
      answer: props.answer.answer,
      projectSlug: props.answer.projectSlug,
      evidenceIds: props.answer.evidenceIds,
      source: 'HOME',
    }),
  },
}))

const answerStatus = computed(() => answerStatusLabel(props.answer.answer))
const sourceTag = computed(() => answerSourceTag(props.answer.answer))
const techTail = computed(() => answerTechTail(props.answer.answer))

function stopTyping() {
  if (typingTimer) clearInterval(typingTimer)
  typingTimer = null
}

function startTyping() {
  stopTyping()
  const reduced =
    typeof window.matchMedia !== 'function' ||
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduced) {
    visibleAnswer.value = props.answer.answer.summary
    complete.value = true
    return
  }

  visibleAnswer.value = ''
  complete.value = false
  let index = 0
  typingTimer = setInterval(() => {
    index += 2
    visibleAnswer.value = props.answer.answer.summary.slice(0, index)
    if (index >= props.answer.answer.summary.length) {
      stopTyping()
      complete.value = true
    }
  }, 20)
}

watch(() => props.answer, startTyping, { immediate: true, deep: true })
onBeforeUnmount(stopTyping)
</script>

<template>
  <section class="light-answer" data-light-answer>
    <aside>
      <b>{{ role }}</b>
      <span>ROUND {{ String(answer.round).padStart(2, '0') }} / 03</span>
      <span v-if="sourceTag">SOURCE<br />{{ sourceTag }}</span>
      <span>{{ techTail }}</span>
    </aside>
    <div class="light-answer__content">
      <p class="light-answer__speaker">YOU · {{ answer.question }}</p>
      <h2 class="light-answer__title">{{ answer.answer.title }}</h2>
      <div class="light-answer__text" aria-live="polite">
        {{ visibleAnswer }}<i v-if="!complete" aria-hidden="true"></i>
      </div>
      <div v-if="complete" class="light-answer__sections">
        <section v-for="section in answer.answer.sections" :key="section.type">
          <h3>{{ section.title }}</h3>
          <p>{{ section.content }}</p>
        </section>
      </div>
      <p v-if="complete" class="light-answer__status">{{ answerStatus }}</p>
      <div v-if="complete" class="light-answer__cites">
        <RouterLink
          v-for="item in citedEvidence"
          :key="item.id"
          :to="{ path: '/evidence', query: { evidence: item.id } }"
        >
          [{{ item.code }}] {{ item.title }}
        </RouterLink>
      </div>
      <div v-if="complete" class="light-answer__actions">
        <button data-answer-action type="button" @click="$emit('followUp')">继续追问</button>
        <RouterLink data-answer-action :to="projectTarget">查看相关项目</RouterLink>
        <RouterLink data-answer-action class="primary" :to="agentTarget">
          带着上下文进入 Agent →
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.light-answer {
  display: grid;
  min-height: 390px;
  padding: 45px;
  grid-column: 1 / -1;
  grid-template-columns: 180px 1fr;
  gap: 36px;
  color: var(--paper);
  background: var(--ink);
}

aside {
  padding-right: 25px;
  border-right: 1px solid var(--ink-line);
}

aside b {
  color: var(--red-on-ink);
  font: 10px var(--mono);
  font-weight: 400;
  letter-spacing: 0.14em;
}

aside span {
  display: block;
  margin-top: 15px;
  color: var(--ink-text-faint);
  font: 10px/1.7 var(--mono);
}

.light-answer__content {
  max-width: 760px;
}

.light-answer__speaker {
  margin: 0 0 22px;
  color: var(--red-on-ink);
  font: 10px var(--mono);
  letter-spacing: 0.14em;
}

.light-answer__text {
  min-height: 105px;
  color: var(--ink-text-hi);
  font: 17px/2 var(--serif);
}

.light-answer__text i {
  display: inline-block;
  width: 10px;
  height: 18px;
  margin-left: 5px;
  vertical-align: -3px;
  background: var(--red-on-ink);
  animation: cursor-blink 0.78s steps(1) infinite;
}

.light-answer__status {
  margin: 26px 0 0;
  padding-top: 18px;
  color: var(--red-on-ink);
  border-top: 1px solid var(--ink-line);
  font: 10px var(--mono);
  letter-spacing: 0.1em;
}

.light-answer__cites,
.light-answer__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.light-answer__cites a,
.light-answer__actions a,
.light-answer__actions button {
  padding: 8px 11px;
  color: var(--ink-text-hi);
  border: 1px solid var(--ink-line);
  background: transparent;
  font: 10px var(--mono);
}

.light-answer__actions .primary {
  color: var(--ink);
  border-color: var(--paper);
  background: var(--paper);
}

@media (max-width: 760px) {
  .light-answer {
    padding: 34px 20px;
    grid-template-columns: 1fr;
  }

  aside {
    padding: 0 0 15px;
    border-right: 0;
    border-bottom: 1px solid var(--ink-line);
  }
}
</style>
