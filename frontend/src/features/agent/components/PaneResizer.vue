<script setup lang="ts">
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

@media (max-width: 980px) {
  .pane-resizer {
    display: none;
  }
}
</style>
