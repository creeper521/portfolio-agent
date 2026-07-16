import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TimelinePage from './TimelinePage.vue'

describe('TimelinePage', () => {
  it('renders the factual public timeline', async () => {
    const wrapper = mount(TimelinePage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('公开成长时间线')
    expect(wrapper.text()).toContain('从固定路径查询到可交付工具')
    expect(wrapper.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
  })
})
