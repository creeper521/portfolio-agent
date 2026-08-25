<script setup lang="ts">
// 分栏拖拽分隔条：可访问的分栏宽度调整控件（role=separator，支持键盘）。
// 宽度数值（value/min/max）完全由父组件持有，本组件不存宽度状态：
// 拖拽过程 emit preview(value) 实时预览，松手 emit commit 由父组件提交；
// 方向键 emit adjust(delta)、Home/双击 emit reset。direction=1/-1 表示
// "指针右移时数值增大/减小"的轴向映射，使左右两根分隔条可复用同一逻辑。

const props = defineProps<{
  label: string
  value: number
  min: number
  max: number
  direction: 1 | -1
}>()

const emit = defineEmits<{
  preview: [value: number]
  commit: []
  adjust: [delta: number]
  reset: []
}>()

/**
 * 指针拖拽：按下时捕获指针（capture）并记录起点，移动中按方向系数把
 * 位移换算成新数值 emit preview；抬起或取消时解绑监听并 emit commit。
 * min/max 钳制由父组件在 preview 处理中完成。
 */
function onPointerDown(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement
  const startX = event.clientX
  const startValue = props.value
  target.setPointerCapture(event.pointerId)
  target.classList.add('dragging')

  const move = (nextEvent: PointerEvent) => {
    emit('preview', startValue + (nextEvent.clientX - startX) * props.direction)
  }

  const up = () => {
    target.removeEventListener('pointermove', move)
    target.removeEventListener('pointerup', up)
    target.removeEventListener('pointercancel', up)
    target.classList.remove('dragging')
    emit('commit')
  }

  target.addEventListener('pointermove', move)
  target.addEventListener('pointerup', up)
  target.addEventListener('pointercancel', up)
}

// 键盘调整：方向键按步长 emit adjust（Shift 加大步长），Home 重置；
// 增量符号按 direction 翻转，保证左右两根分隔条的键位直觉一致。
function onKeydown(event: KeyboardEvent) {
  const step = event.shiftKey ? 48 : 16
  if (event.key === 'Home') {
    event.preventDefault()
    emit('reset')
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    emit('adjust', -step * props.direction)
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    emit('adjust', step * props.direction)
  }
}
</script>

<template>
  <div
    class="pane-resizer"
    role="separator"
    aria-orientation="vertical"
    :aria-label="label"
    :aria-valuemin="min"
    :aria-valuemax="max"
    :aria-valuenow="value"
    tabindex="0"
    @dblclick="$emit('reset')"
    @keydown="onKeydown"
    @pointerdown="onPointerDown"
  ></div>
</template>

<style scoped>
.pane-resizer {
  position: absolute;
  z-index: 8;
  top: 0;
  bottom: 0;
  width: 12px;
  cursor: col-resize;
  transform: translateX(-6px);
  touch-action: none;
}

.pane-resizer::after {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 5px;
  width: 1px;
  content: '';
  background: var(--workspace-rule, var(--rule));
  transition: background 160ms ease;
}

.pane-resizer:hover::after,
.pane-resizer:focus-visible::after,
.pane-resizer.dragging::after {
  background: var(--workspace-accent-soft, var(--red-hi));
}

@media (max-width: 1279.98px) {
  .evidence-resizer {
    display: none;
  }
}

@media (max-width: 959.98px) {
  .pane-resizer {
    display: none;
  }
}
</style>
