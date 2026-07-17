import { inject, ref, type InjectionKey } from 'vue'

import type { PublicPortfolio } from '../model/publicContentTypes'
import {
  publicContentRepository,
  type PublicContentRepository,
} from '../repository/publicContentRepository'

export type PublicContentStatus = 'idle' | 'loading' | 'ready' | 'error'

const PUBLIC_CONTENT_LOAD_ERROR = '公开内容暂时无法加载，请稍后重试'

export function createPublicContentState(repository: PublicContentRepository) {
  const portfolio = ref<PublicPortfolio | null>(null)
  const status = ref<PublicContentStatus>('idle')
  const error = ref('')
  let inFlight: Promise<void> | null = null

  function load(): Promise<void> {
    if (inFlight) {
      return inFlight
    }
    if (status.value === 'ready') {
      return Promise.resolve()
    }

    status.value = 'loading'
    error.value = ''

    let portfolioRequest: Promise<PublicPortfolio>
    try {
      portfolioRequest = repository.getPortfolio()
    } catch {
      portfolioRequest = Promise.reject()
    }

    const request = portfolioRequest
      .then((loadedPortfolio) => {
        portfolio.value = loadedPortfolio
        status.value = 'ready'
      })
      .catch(() => {
        status.value = 'error'
        error.value = PUBLIC_CONTENT_LOAD_ERROR
      })

    const trackedRequest = request.finally(() => {
      if (inFlight === trackedRequest) {
        inFlight = null
      }
    })
    inFlight = trackedRequest
    return trackedRequest
  }

  function retry(): Promise<void> {
    if (inFlight) {
      return inFlight
    }
    repository.invalidate()
    status.value = 'idle'
    return load()
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
