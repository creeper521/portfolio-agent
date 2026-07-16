<script setup lang="ts">
import { computed, ref } from 'vue'

import { createPreviewAnswer } from '../../agent/data/previewAnswers'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import { audienceProfiles } from '../data/audienceProfiles'
import type { AudienceProfile, HomeAnswerState } from '../model/audienceTypes'
import LightAnswerPanel from './LightAnswerPanel.vue'

const props = defineProps<{ portfolio: PublicPortfolio }>()

const selectedRole = ref<AudienceProfile>(audienceProfiles[0])
const answer = ref<HomeAnswerState | null>(null)
const customQuestion = ref('')
const round = ref(0)

const primaryProject = computed(() => props.portfolio.projects[0] ?? null)

function chooseRole(profile: AudienceProfile) {
  selectedRole.value = profile
  answer.value = null
  customQuestion.value = ''
  round.value = 0
}

function ask(question: string) {
  const normalized = question.trim()
  const project = primaryProject.value
  if (!normalized || !project) return

  const result = createPreviewAnswer(
    normalized,
    selectedRole.value.id,
    project,
    props.portfolio.evidence,
  )
  round.value = Math.min(round.value + 1, 3)
  answer.value = {
    round: round.value,
    question: normalized,
    answer: result.content,
    projectSlug: project.slug,
    evidenceIds: result.evidenceIds,
  }
  customQuestion.value = ''
}

function focusCustomQuestion() {
  document.querySelector<HTMLInputElement>('[data-custom-question]')?.focus()
}
</script>

<template>
  <section
    id="dialogue"
    class="audience-dialogue"
    data-home-layer="dialogue"
    aria-labelledby="audience-title"
  >
    <div class="page-shell audience-dialogue__head">
      <div>
        <p class="eyebrow">02 · LIGHT CONVERSATION</p>
        <h2 id="audience-title">选择你的视角。</h2>
      </div>
      <span>ROUND {{ String(round).padStart(2, '0') }} / 03 · HOMEPAGE PREVIEW</span>
    </div>

    <div class="page-shell audience-console">
      <section class="role-side">
        <p class="eyebrow">SELECT YOUR ROLE</p>
        <h3>你是谁？</h3>
        <div class="role-grid" aria-label="选择访客身份">
          <button
            v-for="profile in audienceProfiles"
            :key="profile.id"
            type="button"
            :class="{ 'role-button--active': profile.id === selectedRole.id }"
            :data-role="profile.id"
            :aria-pressed="profile.id === selectedRole.id"
            @click="chooseRole(profile)"
          >
            <small>{{ profile.code }}</small>
            {{ profile.label }}
          </button>
        </div>
        <p class="role-description">{{ selectedRole.description }}</p>
      </section>

      <section class="question-side" :data-current-role="selectedRole.id">
        <p class="eyebrow">SUGGESTED QUESTIONS</p>
        <h3>推荐问题</h3>
        <div class="question-list">
          <button
            v-for="(item, index) in selectedRole.questions"
            :key="item"
            type="button"
            data-question
            @click="ask(item)"
          >
            <b>Q·{{ String(index + 1).padStart(2, '0') }}</b>
            <span>{{ item }}</span>
            <i aria-hidden="true">→</i>
          </button>
        </div>
        <form class="question-form" data-question-form @submit.prevent="ask(customQuestion)">
          <span aria-hidden="true">›</span>
          <input
            v-model="customQuestion"
            data-custom-question
            aria-label="输入自己的问题"
            placeholder="也可以输入自己的问题"
          />
          <button type="submit">发送 ↵</button>
        </form>
      </section>

      <LightAnswerPanel
        v-if="answer"
        :role="selectedRole.id"
        :answer="answer"
        :evidence="portfolio.evidence"
        @follow-up="focusCustomQuestion"
      />
    </div>
  </section>
</template>

<style scoped>
.audience-dialogue {
  padding: 86px 0 96px;
  background: var(--paper-hi);
  border-bottom: 1px solid var(--rule);
}

.audience-dialogue__head {
  display: flex;
  margin-bottom: 36px;
  align-items: end;
  justify-content: space-between;
  gap: 30px;
}

.audience-dialogue__head p {
  margin: 0 0 12px;
}

.audience-dialogue__head h2 {
  margin: 0;
  font: 600 clamp(42px, 4.4vw, 64px) / 1 var(--serif);
  letter-spacing: -0.045em;
}

.audience-dialogue__head > span {
  color: var(--ink-2);
  font: 11.5px var(--mono);
  letter-spacing: 0.1em;
}

.audience-console {
  display: grid;
  min-height: 520px;
  grid-template-columns: 1fr 1.15fr;
  border: 1px solid var(--rule);
  background: var(--paper);
}

.role-side,
.question-side {
  padding: 38px 36px;
}

.role-side {
  border-right: 1px solid var(--rule);
}

.role-side > p:first-child,
.question-side > p:first-child {
  margin: 0;
}

h3 {
  margin: 13px 0 26px;
  font: 400 29px/1.2 var(--serif);
}

.role-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid var(--rule);
}

.role-grid button {
  min-height: 88px;
  padding: 20px 18px;
  color: var(--ink);
  text-align: left;
  border: 0;
  border-right: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
  background: transparent;
  font-size: 15px;
  transition: 0.28s var(--ease);
}

.role-grid button:nth-child(2n) {
  border-right: 0;
}

.role-grid button:nth-child(n + 3) {
  border-bottom: 0;
}

.role-grid button small {
  display: block;
  margin-bottom: 9px;
  color: var(--ink-2);
  font: 11.5px var(--mono);
  letter-spacing: 0.1em;
}

.role-grid .role-button--active {
  color: var(--paper);
  background: var(--ink);
}

.role-grid .role-button--active small {
  color: #e1948b;
}

.role-description {
  min-height: 42px;
  margin: 24px 0 0;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.72;
}

.question-list {
  border-top: 1px solid var(--rule);
}

.question-list button {
  display: grid;
  width: 100%;
  padding: 18px 4px;
  grid-template-columns: 64px 1fr auto;
  gap: 12px;
  align-items: center;
  color: var(--ink);
  text-align: left;
  border: 0;
  border-bottom: 1px solid var(--rule);
  background: transparent;
  transition: 0.25s var(--ease);
}

.question-list button:hover {
  padding-left: 14px;
  color: var(--red);
  background: rgba(122, 46, 42, 0.018);
}

.question-list b {
  color: var(--ink-2);
  font: 11.5px var(--mono);
  font-weight: 400;
  letter-spacing: 0.1em;
}

.question-list span {
  font-size: 16px;
  line-height: 1.5;
}

.question-list i {
  color: var(--muted);
  font: 20px var(--serif);
  font-style: normal;
}

.question-form {
  display: flex;
  margin-top: 25px;
  padding: 12px 0;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--ink);
}

.question-form > span {
  color: var(--red);
  font: 18px var(--serif);
}

.question-form input {
  width: 100%;
  color: var(--ink);
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 13px;
}

.question-form button {
  padding: 0;
  color: var(--red);
  white-space: nowrap;
  border: 0;
  background: transparent;
  font: 9px var(--mono);
  letter-spacing: 0.12em;
}

@media (max-width: 900px) {
  .audience-console {
    grid-template-columns: 1fr;
  }

  .role-side {
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
}

@media (max-width: 620px) {
  .audience-dialogue__head {
    align-items: start;
    flex-direction: column;
  }

  .role-side,
  .question-side {
    padding: 34px 20px;
  }

  .role-grid {
    grid-template-columns: 1fr;
  }

  .role-grid button {
    border-right: 0;
    border-bottom: 1px solid var(--rule) !important;
  }
}
</style>
