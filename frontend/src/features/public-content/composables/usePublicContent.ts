import { inject, ref, type InjectionKey } from 'vue'

import type { PublicPortfolio } from '../model/publicContentTypes'
import {
  publicContentRepository,
  type PublicContentRepository,
} from '../repository/publicContentRepository'

export type PublicContentStatus = 'idle' | 'loading' | 'ready' | 'error'

export function createPublicContentState(repository: PublicContentRepository) {
  const portfolio = ref<PublicPortfolio | null>(null)
  const status = ref<PublicContentStatus>('idle')
  const error = ref('')

  async function load(): Promise<void> {
    if (status.value === 'loading' || status.value === 'ready') {
      return
    }

    status.value = 'loading'
    error.value = ''

    try {
      portfolio.value = await repository.getPortfolio()
      status.value = 'ready'
    } catch (cause) {
      status.value = 'error'
      error.value = cause instanceof Error
        ? cause.message
        : '公开内容暂时无法加载，请稍后重试'
    }
  }

  async function retry(): Promise<void> {
    repository.invalidate()
    status.value = 'idle'
    await load()
  }

  return { portfolio, status, error, load, retry }
}

export type PublicContentState = ReturnType<typeof createPublicContentState>

export const publicContentStateKey: InjectionKey<PublicContentState> = Symbol('public-content-state')

const productionState = createPublicContentState(publicContentRepository)

export function usePublicContent(): PublicContentState {
  const state = inject(publicContentStateKey, productionState)
  void state.load()
  return state
}
