import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import PublicContentFeedback from './PublicContentFeedback.vue'

function mountFeedback(
  action: 'RETRY' | 'RETRY_AFTER' | 'CORRECT_INPUT' | 'NAVIGATE_BACK',
  retryAfterSeconds?: number,
) {
  return mount(PublicContentFeedback, {
    props: {
      status: 'error',
      action,
      retryAfterSeconds,
    },
  })
}

describe('PublicContentFeedback', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('retries a retryable public-content failure', async () => {
    const wrapper = mountFeedback('RETRY')

    await wrapper.get('[data-public-content-retry]').trigger('click')

    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('uses fixed safe routes for navigation and input-correction actions', () => {
    const navigateBack = mountFeedback('NAVIGATE_BACK')
    const correctInput = mountFeedback('CORRECT_INPUT')

    expect(navigateBack.get('[data-public-content-navigate-back]').attributes('href'))
      .toBe('/projects')
    expect(correctInput.get('[data-public-content-correct-input]').attributes('href'))
      .toBe('/')
  })

  it('disables retry until the retry-after countdown finishes', async () => {
    vi.useFakeTimers()
    const wrapper = mountFeedback('RETRY_AFTER', 2)
    const retry = wrapper.get('[data-public-content-retry-after]')

    expect(retry.attributes()).toHaveProperty('disabled')
    expect(retry.text()).toContain('2')

    await vi.advanceTimersByTimeAsync(2_000)

    expect(retry.attributes()).not.toHaveProperty('disabled')
    await retry.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
