import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref(false)
  let media: MediaQueryList | null =
    typeof window === 'undefined' || typeof window.matchMedia !== 'function'
      ? null
      : window.matchMedia(query)

  const update = (event?: MediaQueryListEvent) => {
    matches.value = event ? event.matches : Boolean(media?.matches)
  }

  update()

  onMounted(() => {
    if (!media && typeof window.matchMedia === 'function') {
      media = window.matchMedia(query)
      update()
    }
    media?.addEventListener('change', update)
  })

  onBeforeUnmount(() => {
    media?.removeEventListener('change', update)
  })

  return matches
}
