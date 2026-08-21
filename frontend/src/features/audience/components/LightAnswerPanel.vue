<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import { createAgentHandoff } from '../../agent/model/handoffStore'
import type { HomeAnswerState } from '../model/audienceTypes'
import { SUPPORT_KIND_LABELS } from '../../agent/model/publicAgentTurnLabels'

// 首页轻回答：直接呈现闭合 PublicAgentTurn 的轻量投影；
// “进入 Agent”通过内存 handoff 携带会话凭证与幂等重放输入（不带答案正文）。

const props = defineProps<{
  role: AudienceRole
  answer: HomeAnswerState
}>()

defineEmits<{ followUp: [] }>()

const visibleAnswer = ref('')
const complete = ref(false)
let typingTimer: ReturnType<typeof setInterval> | null = null

const turnSummary = computed(() => {
  const turn = props.answer.turn
  if (turn.kind === 'ANSWER') {
    const goal = turn.answer.goalResults[0]
    if (goal?.presentation?.kind === 'SECTIONED' && goal.presentation.sections[0] !== undefined) {
      return goal.presentation.sections[0].content
    }
    if (goal?.presentation?.kind === 'RECOMMENDATION' && goal.presentation.items[0] !== undefined) {
      return goal.presentation.items[0].summary
    }
    return goal?.label ?? ''
  }
  if (turn.kind === 'CLARIFICATION') return turn.clarification.prompt
  return turn.message
})

const turnTitle = computed(() => {
  const turn = props.answer.turn
  if (turn.kind === 'ANSWER') return turn.answer.goalResults[0]?.label ?? '回答'
  if (turn.kind === 'CLARIFICATION') return '需要补充'
  return '回应'
})

const turnStatus = computed(() => {
  const turn = props.answer.turn
  if (turn.kind === 'ANSWER') {
    if (turn.answer.resolution === 'COMPLETE') return '回答完整'
    if (turn.answer.resolution === 'PARTIAL') return '部分完成'
    return '未形成回答'
  }
  if (turn.kind === 'CONVERSATIONAL') return '对话回复'
  if (turn.kind === 'BOUNDARY') return '能力边界'
  if (turn.kind === 'CAPABILITY_UNAVAILABLE') return '能力暂时不可用'
  return '需要澄清'
})

const supportTag = computed(() => {
  const turn = props.answer.turn
  if (turn.kind !== 'ANSWER') return ''
  return [...new Set(turn.answer.sourceComposition.map((kind) => SUPPORT_KIND_LABELS[kind]))]
    .join(' · ')
})

const answerSections = computed(() => {
  const turn = props.answer.turn
  if (turn.kind !== 'ANSWER') return []
  return turn.answer.goalResults.flatMap((goal) => {
    if (goal.presentation?.kind === 'SECTIONED') {
      return goal.presentation.sections.map((section) => ({ title: section.title, content: section.content }))
    }
    if (goal.presentation?.kind === 'RECOMMENDATION') {
      return goal.presentation.items.map((item) => ({ title: item.label, content: item.summary }))
    }
    return []
  }).slice(0, 3)
})

const citedSources = computed(() => {
  const turn = props.answer.turn
  if (turn.kind !== 'ANSWER') return []
  return turn.answer.sourceCatalog.sources
})

const projectTarget = computed(() =>
  props.answer.projectSlug ? `/projects/${props.answer.projectSlug}` : '/projects',
)

const agentTarget = computed(() => {
  const envelope = props.answer.conversation
  const conversation = envelope !== null && envelope.resumeToken !== undefined
    ? {
      conversationId: envelope.conversationId,
      resumeToken: envelope.resumeToken,
      discussionRevision: envelope.discussionRevision,
      ...(envelope.activeDiscussion === undefined
        ? {} : { activeDiscussion: envelope.activeDiscussion }),
    }
    : undefined
  return {
    path: '/agent',
    query: {
      handoffId: createAgentHandoff({
        role: props.role,
        question: props.answer.question,
        projectSlug: props.answer.projectSlug,
        source: 'HOME',
        ...(conversation === undefined ? {} : { conversation }),
        replay: props.answer.replay,
      }),
    },
  }
})

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
    visibleAnswer.value = turnSummary.value
    complete.value = true
    return
  }

  visibleAnswer.value = ''
  complete.value = false
  let index = 0
  typingTimer = setInterval(() => {
    index += 2
    visibleAnswer.value = turnSummary.value.slice(0, index)
    if (index >= turnSummary.value.length) {
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
      <span v-if="supportTag">SOURCE<br />{{ supportTag }}</span>
    </aside>
    <div class="light-answer__content">
      <p class="light-answer__speaker">YOU · {{ answer.question }}</p>
      <h2 class="light-answer__title">{{ turnTitle }}</h2>
      <div class="light-answer__text" aria-live="polite">
        {{ visibleAnswer }}<i v-if="!complete" aria-hidden="true"></i>
      </div>
      <div v-if="complete" class="light-answer__sections">
        <section v-for="section in answerSections" :key="section.title">
          <h3>{{ section.title }}</h3>
          <p>{{ section.content }}</p>
        </section>
      </div>
      <p v-if="complete" class="light-answer__status">{{ turnStatus }}</p>
      <div v-if="complete && citedSources.length > 0" class="light-answer__cites">
        <RouterLink
          v-for="source in citedSources"
          :key="source.key"
          :to="source.route"
        >
          [{{ source.code ?? source.key }}] {{ source.label }}
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
  overflow-wrap: anywhere;
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

.light-answer__title {
  overflow-wrap: anywhere;
}

.light-answer__sections {
  margin-top: 18px;
}

.light-answer__sections h3 {
  margin: 0 0 4px;
  color: var(--ink-text-hi);
  font: 12px var(--mono);
}

.light-answer__sections p {
  margin: 0 0 12px;
  color: var(--ink-text);
  font: 13px/1.8 var(--serif);
  overflow-wrap: anywhere;
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
  text-decoration: none;
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
