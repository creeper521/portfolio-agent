import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import TimelinePage from './TimelinePage.vue'

const RouterLinkStub = { template: '<a><slot /></a>' }

describe('TimelinePage', () => {
  it('renders the factual public timeline', async () => {
    const wrapper = mount(TimelinePage, {
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('公开成长时间线')
    expect(wrapper.text()).toContain('从固定路径查询到可交付工具')
    expect(wrapper.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
  })

  it('shows loading feedback before deciding the timeline is empty', () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = mount(TimelinePage, {
      global: {
        provide: { [publicContentStateKey as symbol]: state },
        stubs: { RouterLink: RouterLinkStub },
      },
    })

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('公开时间线正在整理')
  })
})
