import { inject, ref, type InjectionKey } from 'vue'

import type { PublicPortfolio } from '../model/publicContentTypes'
import {
  publicContentRepository,
  type PublicContentRepository,
} from '../repository/publicContentRepository'
import { PortfolioApiError } from '../../portfolio/api/portfolioApi'
import type { ErrorAction } from '../../portfolio/api/apiErrorActions'
import { createFrontendDiagnosticEvent } from '../../../shared/diagnostics/frontendDiagnosticTypes'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'

export type PublicContentStatus = 'idle' | 'loading' | 'ready' | 'error'

const PUBLIC_CONTENT_LOAD_ERROR = '公开内容暂时无法加载，请稍后重试'

export function createPublicContentState(repository: PublicContentRepository) {
  const portfolio = ref<PublicPortfolio | null>(null)
  const status = ref<PublicContentStatus>('idle')
  const error = ref('')
  const action = ref<ErrorAction>('RETRY')
  const retryAfterSeconds = ref(0)
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
    action.value = 'RETRY'
    retryAfterSeconds.value = 0

    let portfolioRequest: Promise<PublicPortfolio>
    try {
      portfolioRequest = repository.getPortfolio()
    } catch {
      portfolioRequest = Promise.reject()
    }

    const request = portfolioRequest
      .then((loadedPortfolio) => {
        const invalidPresetCount = loadedPortfolio.questionPresets.filter(
          (preset) => !/^pcv1-[a-f0-9]{16}$/.test(preset.contractVersion),
        ).length
        if (invalidPresetCount > 0) {
          frontendDiagnostics.report(createFrontendDiagnosticEvent({
            eventName: 'frontend.response.invalid',
            errorCode: 'PRESET_CONTRACT_VERSION_INVALID',
            errorKind: 'INVALID_RESPONSE',
            contentVersion: loadedPortfolio.contentVersion,
          }))
        }
        portfolio.value = {
          ...loadedPortfolio,
          questionPresets: loadedPortfolio.questionPresets.filter(
            (preset) => /^pcv1-[a-f0-9]{16}$/.test(preset.contractVersion),
          ),
        }
        status.value = 'ready'
        frontendDiagnostics.report(createFrontendDiagnosticEvent({
          eventName: 'frontend.content.load.completed',
          contentVersion: loadedPortfolio.contentVersion,
        }))
      })
      .catch((failure: unknown) => {
        status.value = 'error'
        error.value = PUBLIC_CONTENT_LOAD_ERROR
        action.value = failure instanceof PortfolioApiError ? failure.action : 'RETRY'
        retryAfterSeconds.value = failure instanceof PortfolioApiError
          ? Math.max(0, Math.ceil(failure.retryAfterSeconds ?? 0))
          : 0
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

  return { portfolio, status, error, action, retryAfterSeconds, load, retry }
}

export type PublicContentState = Omit<
  ReturnType<typeof createPublicContentState>,
  'action' | 'retryAfterSeconds'
> & {
  action?: ReturnType<typeof createPublicContentState>['action']
  retryAfterSeconds?: ReturnType<typeof createPublicContentState>['retryAfterSeconds']
}

export const publicContentStateKey: InjectionKey<PublicContentState> = Symbol('public-content-state')

const productionState = createPublicContentState(publicContentRepository)

export function usePublicContent(): PublicContentState {
  const state = inject(publicContentStateKey, productionState)
  void state.load()
  return state
}
