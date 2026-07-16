<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import type { AudienceRole, PublicProject } from '../../public-content/model/publicContentTypes'
import type { AgentSession } from '../model/sessionTypes'

const props = defineProps<{
  session: AgentSession
  role: AudienceRole
  project: PublicProject
  seedQuestion?: string
  sessionsOpen?: boolean
  evidenceOpen?: boolean
}>()

const emit = defineEmits<{
  submit: [question: string]
  evidence: [id: string]
  toggleSessions: []
  toggleEvidence: []
}>()

const question = ref(props.seedQuestion ?? '')
const input = ref<HTMLTextAreaElement | null>(null)

watch(
  () => props.seedQuestion,
  async (value) => {
    if (value && !props.session.messages.length) {
      question.value = value
      await nextTick()
      input.value?.focus()
    }
  },
)

function submit() {
  const value = question.value.trim()
  if (!value) return
  emit('submit', value)
  question.value = ''
}
</script>

<template>
  <section class="conversation">
    <header class="conversation__head">
      <div>
        <p>AGENT CONVERSATION · Agent 对话</p>
        <h1>{{ session.title }}</h1>
      </div>
      <div class="conversation__tools">
        <button
          class="session-toggle"
          type="button"
          aria-controls="local-session-rail"
          :aria-expanded="sessionsOpen ? 'true' : 'false'"
          @click="$emit('toggleSessions')"
        >
          会话
        </button>
        <button
          class="evidence-toggle"
          type="button"
          aria-controls="agent-evidence-desk"
          :aria-expanded="evidenceOpen ? 'true' : 'false'"
          @click="$emit('toggleEvidence')"
        >
          证据
        </button>
        <span>{{ role }} MODE</span>
      </div>
    </header>

    <div class="conversation__scroll">
      <div class="thread">
        <section v-if="!session.messages.length" class="thread-empty">
          <p>YOU · FROM DOSSIER</p>
          <h2>从一个可核验的问题开始。</h2>
          <button
            v-for="item in project.suggestedQuestions"
            :key="item"
            type="button"
            @click="question = item"
          >
            <span>↳</span>{{ item }}
          </button>
        </section>

        <article
          v-for="message in session.messages"
          :key="message.id"
          class="message"
          :class="message.role === 'AGENT' ? 'message--agent' : 'message--user'"
        >
          <p>{{ message.role === 'AGENT' ? 'AGENT · VERIFIED ANSWER' : 'YOU' }}</p>
          <div>{{ message.content }}</div>
          <footer v-if="message.evidenceIds.length">
            <button
              v-for="id in message.evidenceIds"
              :key="id"
              type="button"
              @click="$emit('evidence', id)"
            >
              [{{ id }}]
            </button>
          </footer>
        </article>
      </div>
    </div>

    <form class="composer" @submit.prevent="submit">
      <span aria-hidden="true">›</span>
      <textarea
        ref="input"
        v-model="question"
        rows="1"
        aria-label="你的问题"
        placeholder="继续追问方案取舍、验证过程或证据"
        @keydown.ctrl.enter.prevent="submit"
      ></textarea>
      <button type="submit">发送 ↵</button>
    </form>
  </section>
</template>

<style scoped>
.conversation {
  position: relative;
  display: grid;
  min-width: 0;
  height: calc(100vh - var(--header-height));
  grid-template-rows: auto 1fr;
  color: var(--paper);
  background: var(--ink);
  overflow: hidden;
}

.conversation__head {
  display: flex;
  min-height: 82px;
  padding: 20px 35px 18px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid #554d44;
}

.conversation__head p,
.thread-empty > p,
.message > p {
  margin: 0 0 10px;
  color: #c46f66;
  font: 8px var(--mono);
  letter-spacing: 0.13em;
}

.conversation__head h1 {
  margin: 0;
  font: 500 22px var(--serif);
}

.conversation__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conversation__tools span,
.conversation__tools button {
  padding: 8px 10px;
  color: #d0c4b5;
  border: 1px solid #635a50;
  background: transparent;
  font: 8px var(--mono);
  letter-spacing: 0.08em;
}

.conversation__tools button {
  display: none;
  font-size: 9px;
}

.conversation__scroll {
  min-height: 0;
  padding-bottom: 104px;
  overflow-y: auto;
}

.thread {
  width: min(820px, calc(100% - 56px));
  margin: 35px auto 40px clamp(0px, calc(25% - 205px), 160px);
}

.thread-empty {
  padding: 0 0 10px 18px;
  border-left: 1px solid #5a5248;
}

.thread-empty h2 {
  margin: 0 0 30px;
  color: #ded3c4;
  font: 400 clamp(28px, 3.5vw, 44px) / 1.2 var(--serif);
}

.thread-empty button {
  display: flex;
  width: 100%;
  padding: 16px 0;
  gap: 13px;
  color: #b1a596;
  text-align: left;
  border: 0;
  border-top: 1px solid #51493f;
  background: transparent;
  font-size: 14px;
}

.thread-empty button:last-child {
  border-bottom: 1px solid #51493f;
}

.thread-empty button span {
  color: var(--red-hi);
}

.message {
  max-width: 760px;
  margin-bottom: 34px;
  padding-left: 18px;
  border-left: 1px solid #5a5248;
}

.message--user {
  color: #b1a596;
}

.message--agent {
  color: #ded3c4;
}

.message > div {
  font: 15px/1.95 var(--serif);
}

.message footer {
  display: flex;
  margin-top: 15px;
  gap: 7px;
}

.message footer button {
  padding: 6px 8px;
  color: #b7ab9d;
  border: 1px solid #5d554b;
  background: transparent;
  font: 8px var(--mono);
}

.composer {
  position: absolute;
  right: 35px;
  bottom: 24px;
  left: 35px;
  display: flex;
  min-height: 58px;
  padding: 0 16px;
  align-items: center;
  gap: 12px;
  border: 1px solid #62594e;
  background: #29241f;
}

.composer > span {
  color: #ca7168;
  font: 20px var(--serif);
}

textarea {
  min-height: 28px;
  max-height: 110px;
  flex: 1;
  resize: none;
  color: var(--paper);
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 13px;
}

textarea::placeholder {
  color: #756b5f;
}

.composer button {
  padding: 10px 14px;
  color: var(--ink);
  border: 0;
  background: var(--paper);
  font: 9px var(--mono);
  letter-spacing: 0.1em;
}

@media (max-width: 1220px) {
  .evidence-toggle {
    display: block !important;
  }
}

@media (max-width: 980px) {
  .session-toggle {
    display: block !important;
  }

  .thread {
    margin-inline: auto;
  }
}

@media (max-width: 620px) {
  .conversation__head {
    padding-inline: 18px;
  }

  .conversation__tools span {
    display: none;
  }

  .thread {
    width: calc(100% - 36px);
  }

  .composer {
    right: 18px;
    left: 18px;
  }
}
</style>
