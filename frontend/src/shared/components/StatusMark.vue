<script setup lang="ts">
const props = defineProps<{ status: string }>()

const labels: Record<string, string> = {
  DELIVERED: '核心版本已交付',
  IN_PROGRESS: '持续推进',
  PROTOTYPE: '原型验证',
  LEARNING_ONLY: '学习记录',
  PRIMARY: '主导贡献',
  INDEPENDENT: '独立完成',
  COLLABORATIVE: '协作完成',
  OBSERVED_LEARNING: '观察学习',
  APPROVED: '已通过公开审查',
}
</script>

<template>
  <span class="status-mark"><i aria-hidden="true">◆</i>{{ labels[props.status] ?? props.status }}</span>
</template>

<style scoped>
.status-mark {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.06em;
}

i {
  color: var(--red);
  font-size: 7px;
  font-style: normal;
}
</style>
