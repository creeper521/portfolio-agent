<script setup lang="ts">
import { computed } from 'vue'
import type { CapabilityUnavailableTurn, SuggestedAction } from '../model/publicAgentTurn'
import SuggestedActionRow from './SuggestedActionRow.vue'

// CAPABILITY_UNAVAILABLE 轮次视图：只读展示不可用原因、可重试性与
// 恢复动作。数据全部来自 props，仅用 computed 区分文案分支，无本地状态；
// 用户点击建议动作时向父组件转发 select-action。
// 可重试性用文字表达而非只靠颜色，保证非视觉通道也能感知（D-38.15）。
// REPLAY_BODY_NOT_RETAINED 特殊分支：Provider 正文按隐私设计未持久化的
// 固定终局，属于正常回放形态而非能力故障；指引固定为"重新提问"，
// 禁止套用故障眉题或"调整提问方式"等措辞（A2-32）。
// 模型不可用双动作分支（UI spec §2.6/D-MS-5/D-MS-7）：仅 A7 五个 settled 模型终局
// 出现「同模型重新提问」与「换模型重新提问」并列双动作；两个动作都是新 requestId
// 新快照（settled 终局的同 requestId 只会回放原失败——设计 §16.2/docs/15 A2-86），
// 区别只在模型；其余失败类别沿用单动作文案，不出现换模型入口。

const props = defineProps<{
  turn: CapabilityUnavailableTurn
  /** 五个模型不可用终局时由 Workspace 提供：失败模型名、同模型重问可用性与可选的另一模型名。 */
  modelRecovery?: {
    failedModelName: string
    sameModelRetryable: boolean
    otherModelName?: string
  }
}>()

const emit = defineEmits<{
  'select-action': [action: SuggestedAction]
  'retry-same-model': [requestId: string]
  'switch-model-reask': [requestId: string]
}>()

// 判定是否为"正文未保留"的隐私终局：命中时走专属文案分支。
const replayBodyNotRetained = computed(
  () => props.turn.code === 'REPLAY_BODY_NOT_RETAINED',
)

// 模型不可用类终局的双动作卡只在 Workspace 判定并提供 modelRecovery 时出现。
const modelUnavailable = computed(() => props.modelRecovery !== undefined)
</script>

<template>
  <div class="capability-unavailable-turn" data-testid="capability-unavailable-turn">
    <template v-if="modelUnavailable && modelRecovery">
      <p class="capability-unavailable-turn__eyebrow">模型暂时不可用</p>
      <p class="capability-unavailable-turn__message" data-testid="model-failure-title">
        {{ modelRecovery.failedModelName }} 暂时无法完成这次回答
      </p>
      <p class="capability-unavailable-turn__model-note">
        本轮请求已安全结束。你可以用同一模型重新提问，或换一个模型重新提问。
      </p>
      <p class="capability-unavailable-turn__code" data-testid="turn-code">{{ turn.code }}</p>
      <p
        v-if="turn.retryAfterSeconds !== undefined"
        class="capability-unavailable-turn__model-note"
      >约 {{ turn.retryAfterSeconds }} 秒后可重新提交</p>
      <div class="capability-unavailable-turn__actions">
        <button
          v-if="modelRecovery.sameModelRetryable"
          type="button"
          class="capability-unavailable-turn__retry-primary"
          data-testid="model-retry-same-model"
          @click="emit('retry-same-model', turn.requestId)"
        >用 {{ modelRecovery.failedModelName }} 重新提问</button>
        <button
          v-if="modelRecovery.otherModelName !== undefined"
          type="button"
          class="capability-unavailable-turn__reask"
          data-testid="model-switch-reask"
          @click="emit('switch-model-reask', turn.requestId)"
        >换 {{ modelRecovery.otherModelName }} 重新提问</button>
        <span class="capability-unavailable-turn__action-hints">
          同一模型重新提问 = 发送一条新请求（新标识）；失败请求的结果不会被复用<br />
          换模型重新提问 = 发送一条新请求（新标识），由另一模型重新生成回答
        </span>
      </div>
    </template>
    <template v-else>
      <p class="capability-unavailable-turn__eyebrow">{{ replayBodyNotRetained ? '该回答未保留' : '能力暂时不可用' }}</p>
      <p class="capability-unavailable-turn__message" data-testid="turn-message">{{ turn.message }}</p>
      <p class="capability-unavailable-turn__code" data-testid="turn-code">{{ turn.code }}</p>
      <p v-if="replayBodyNotRetained" class="capability-unavailable-turn__retryable" data-testid="turn-retryable">
        重试无法找回这条回答，请直接重新提问。
      </p>
      <p v-else-if="turn.retryable !== undefined" class="capability-unavailable-turn__retryable" data-testid="turn-retryable">
        {{ turn.retryable ? '稍后可以重试' : '当前无法重试，请调整提问方式' }}
      </p>
      <p v-if="!replayBodyNotRetained && turn.retryAfterSeconds !== undefined" class="capability-unavailable-turn__retryable">
        约 {{ turn.retryAfterSeconds }} 秒后可重新提交
      </p>
      <SuggestedActionRow
        v-if="turn.suggestedActions !== undefined"
        :actions="turn.suggestedActions"
        @select="emit('select-action', $event)"
      />
    </template>
  </div>
</template>

<style scoped>
.capability-unavailable-turn__eyebrow {
  margin: 0 0 6px;
  color: var(--workspace-accent, var(--red));
  font: 11px var(--mono);
  letter-spacing: 0.1em;
}
.capability-unavailable-turn__message {
  margin: 0;
  color: var(--workspace-text, var(--ink));
  font: 15px/1.75 var(--serif);
  overflow-wrap: anywhere;
}
.capability-unavailable-turn__code {
  margin: 6px 0 0;
  color: var(--workspace-text-faint, var(--faint));
  font: 10px/1.6 var(--mono);
}
.capability-unavailable-turn__retryable {
  margin: 4px 0 0;
  color: var(--workspace-text-secondary, var(--muted));
  font: 11px/1.6 var(--mono);
}
.capability-unavailable-turn__model-note {
  margin: 4px 0 0;
  color: var(--workspace-text-secondary, var(--ink-2, var(--ink)));
  font: 12.5px/1.7 var(--sans);
}
.capability-unavailable-turn__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}
.capability-unavailable-turn__retry-primary,
.capability-unavailable-turn__reask {
  min-height: 34px;
  padding: 8px 14px;
  border-radius: var(--agent-radius-sm, 8px);
  cursor: pointer;
  font: 12px/1.4 var(--sans);
  transition: transform 160ms ease;
}
.capability-unavailable-turn__retry-primary {
  border: 1px solid var(--workspace-accent, var(--red));
  background: var(--workspace-accent, var(--red));
  color: var(--paper-hi, #fff);
}
.capability-unavailable-turn__reask {
  border: 1px solid var(--workspace-accent, var(--red));
  background: transparent;
  color: var(--workspace-accent, var(--red));
}
.capability-unavailable-turn__retry-primary:hover:not(:disabled),
.capability-unavailable-turn__reask:hover:not(:disabled) {
  transform: translateY(-1px);
}
.capability-unavailable-turn__retry-primary:focus-visible,
.capability-unavailable-turn__reask:focus-visible {
  outline: 2px solid var(--workspace-accent, var(--red));
  outline-offset: 2px;
}
.capability-unavailable-turn__action-hints {
  flex-basis: 100%;
  color: var(--workspace-text-faint, var(--faint));
  font: 10.5px/1.7 var(--mono);
}
</style>
