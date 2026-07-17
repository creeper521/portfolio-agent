import { ref } from 'vue'
import { vi } from 'vitest'

import { previewPublicContent } from '../features/public-content/data/previewPublicContent'
import type { PublicContentState } from '../features/public-content/composables/usePublicContent'

export function readyPublicContentState(): PublicContentState {
  return {
    portfolio: ref(previewPublicContent),
    status: ref('ready'),
    error: ref(''),
    load: vi.fn().mockResolvedValue(undefined),
    retry: vi.fn().mockResolvedValue(undefined),
  }
}
