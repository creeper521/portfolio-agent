<script setup lang="ts">
import { computed, ref } from 'vue'

import { submitAgentTurn } from '../../agent/api/agentTurnApi'
import type { AgentTurnCommand, SurfaceContext } from '../../agent/api/agentTurnApi'
import { newRequestId } from '../../agent/api/requestId'
import { displayNameOfSelection, type ModelSelection } from '../../agent/model/modelSelection'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import { audienceProfiles } from '../data/audienceProfiles'
import type { AudienceProfile, HomeAnswerState } from '../model/audienceTypes'
import LightAnswerPanel from './LightAnswerPanel.vue'

// 首页轻对话：一次性 ASK（无会话凭证首问），结果为闭合 PublicAgentTurn；
// 重放输入随 handoff 交给 Agent 页精确重放同一轮（D-31 幂等）。
// A2-72/73：失败重试冻结完整提交快照（requestId/命令/surface 原样复用），
// 不生成新 requestId、不把 Preset 退化成 FREE_TEXT。

/** A2-71：每次提问都是独立单轮，round 只计数已回答次数，不表达多轮会话。 */
interface FailedSubmission {
  readonly requestId: string
  readonly command: AgentTurnCommand
  readonly surfaceContext: SurfaceContext
  readonly question: string
  /** A7：首页轮次使用目录默认选择，失败重试快照原样携带（UI spec §5.1）。 */
  readonly modelSelection: ModelSelection
}

const props = defineProps<{ portfolio: PublicPortfolio }>()

const selectedRole = ref<AudienceProfile>(audienceProfiles[0])
const answer = ref<HomeAnswerState | null>(null)
const customQuestion = ref('')
const round = ref(0)
const pending = ref(false)
const answerError = ref('')
const failedSubmission = ref<FailedSubmission | null>(null)

const primaryProject = computed(() => props.portfolio.projects[0] ?? null)
/** 目录默认选择（D-MS-6）：首页不提供切换，统一使用 /api/portfolio 投影默认。 */
const defaultModelSelection = computed<ModelSelection>(
  () => props.portfolio.agentAvailability.defaultModelSelection,
)
/** 默认未就绪（NONE）但目录非空：首页无选择路径，不得自动发起自由文本 Turn（设计 §8）。 */
const customQuestionBlocked = computed(
  () => props.portfolio.agentAvailability.selectableModels.length > 0
    && defaultModelSelection.value.kind === 'NONE',
)
/** 首页徽标（UI spec §2.7）：目录默认模型的显示名；NONE 时不显示徽标。 */
const defaultModelName = computed(() =>
  displayNameOfSelection(
    {
      modelCatalogVersion: props.portfolio.agentAvailability.modelCatalogVersion,
      defaultModelSelection: defaultModelSelection.value,
      selectableModels: props.portfolio.agentAvailability.selectableModels,
    },
    defaultModelSelection.value,
  ),
)
const supportedQuestions = computed(() =>
  props.portfolio.questionPresets.filter(
    (preset) => preset.audiences.includes(selectedRole.value.id) &&
      preset.placements.includes('HOME'),
  ),
)

function chooseRole(profile: AudienceProfile) {
  selectedRole.value = profile
  answer.value = null
  customQuestion.value = ''
  round.value = 0
  failedSubmission.value = null
}

async function ask(question: string, questionPresetId?: string) {
  const normalized = question.trim()
  const project = primaryProject.value
  if (!normalized || !project || pending.value) return

  const preset = questionPresetId === undefined
    ? undefined
    : props.portfolio.questionPresets.find((item) => item.id === questionPresetId)
  // 首页无选择路径：默认 NONE 且目录非空时，不自动发起自由文本 Turn（设计 §8）。
  if (preset === undefined && customQuestionBlocked.value) return
  const surfaceContext: SurfaceContext = {
    subjectHint: { kind: 'PROJECT', slug: project.slug },
    audienceRole: selectedRole.value.id,
    requestSource: 'HOME',
  }
  const command: AgentTurnCommand = preset === undefined
    ? { kind: 'ASK', input: { kind: 'FREE_TEXT', text: normalized.slice(0, 2000) } }
    : {
      kind: 'ASK',
      input: { kind: 'PRESET', presetId: preset.id, presetRevision: preset.contractVersion },
    }
  await executeSubmission(
    newRequestId(), command, surfaceContext, normalized, defaultModelSelection.value,
  )
}

/** A2-72/73：重试只消费冻结快照，同 requestId 幂等回收服务端可能已完成的终局。 */
async function retryFailedSubmission() {
  const snapshot = failedSubmission.value
  if (snapshot === null || pending.value) return
  await executeSubmission(
    snapshot.requestId, snapshot.command, snapshot.surfaceContext, snapshot.question,
    snapshot.modelSelection,
  )
}

async function executeSubmission(
  requestId: string,
  command: AgentTurnCommand,
  surfaceContext: SurfaceContext,
  question: string,
  modelSelection: ModelSelection,
) {
  const project = primaryProject.value
  if (project === null || pending.value) return
  pending.value = true
  answerError.value = ''
  failedSubmission.value = { requestId, command, surfaceContext, question, modelSelection }
  try {
    const result = await submitAgentTurn({
      requestId,
      modelSelection,
      command,
      surfaceContext,
      conversationWindow: [],
    })
    if (!result.ok) {
      throw new Error(result.failure.code ?? 'AGENT_TURN_FAILED')
    }
    round.value = Math.min(round.value + 1, 3)
    answer.value = {
      round: round.value,
      question,
      turn: result.turn,
      projectSlug: project.slug,
      conversation: result.conversation,
      replay: { requestId, command, surfaceContext },
    }
    customQuestion.value = ''
    failedSubmission.value = null
  } catch {
    answerError.value = 'Agent 暂时无法回答，请稍后重试'
  } finally {
    pending.value = false
  }
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
      <span>ANSWERED {{ String(round).padStart(2, '0') }} · HOMEPAGE PREVIEW</span>
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
            v-for="(item, index) in supportedQuestions"
            :key="item.id"
            type="button"
            data-question
            :disabled="pending"
            @click="ask(item.text, item.id)"
          >
            <b>Q·{{ String(index + 1).padStart(2, '0') }}</b>
            <span>{{ item.text }}</span>
            <i aria-hidden="true">→</i>
          </button>
        </div>
        <form class="question-form" data-question-form @submit.prevent="ask(customQuestion)">
          <span aria-hidden="true">›</span>
          <input
            v-model="customQuestion"
            data-custom-question
            :disabled="pending || customQuestionBlocked"
            aria-label="输入自己的问题"
            placeholder="也可以输入自己的问题"
          />
          <button data-question-submit type="submit" :disabled="pending || customQuestionBlocked">发送 ↵</button>
        </form>
        <p v-if="customQuestionBlocked" class="answer-feedback" role="status">
          目录默认模型暂未就绪：可先使用推荐问题，或进入 Agent 页选择模型后提问。
        </p>
        <p v-if="pending" class="answer-feedback" role="status">
          正在核对公开事实…
        </p>
        <div v-else-if="answerError" class="answer-feedback answer-feedback--error" role="alert">
          <p>{{ answerError }}</p>
          <button data-answer-retry type="button" @click="retryFailedSubmission">重新回答</button>
        </div>
      </section>

      <LightAnswerPanel
        v-if="answer"
        :role="selectedRole.id"
        :answer="answer"
        :default-model-name="defaultModelName"
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
  color: var(--red-on-ink);
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
  background: transparent;
  font-size: 13px;
}

.question-form:focus-within {
  border-color: var(--red);
}

.question-form button {
  padding: 0;
  color: var(--red);
  white-space: nowrap;
  border: 0;
  background: transparent;
  font: 10px var(--mono);
  letter-spacing: 0.12em;
}

.question-list button:disabled,
.question-form input:disabled,
.question-form button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.answer-feedback {
  margin: 18px 0 0;
  color: var(--ink-2);
  font: 12px/1.6 var(--mono);
}

.answer-feedback--error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--red);
}

.answer-feedback--error p {
  margin: 0;
}

.answer-feedback--error button {
  padding: 7px 10px;
  color: var(--red);
  border: 1px solid currentcolor;
  background: transparent;
  font: 10px var(--mono);
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

@media (hover: none) {
  .question-form input {
    font-size: 16px;
  }
}
</style>
