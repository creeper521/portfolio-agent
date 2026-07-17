import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import HomePage from './HomePage.vue'

const RouterLinkStub = {
  props: ['to'],
  template: '<a :data-to="JSON.stringify(to)"><slot /></a>',
}

describe('HomePage', () => {
  it('renders exactly four home layers from approved public content', async () => {
    const wrapper = mount(HomePage, {
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.findAll('[data-home-layer]')).toHaveLength(4)
    expect(wrapper.get('[data-home-layer="hero"]').text()).toContain('Java 后端开发实习生')
    expect(wrapper.get('[data-home-layer="credibility"]').text()).toContain('1')
    expect(wrapper.get('[data-home-layer="dialogue"]').text()).toContain('技术面试官')
    expect(wrapper.get('[data-home-layer="explore"]').text()).toContain('完整对话')
    expect(wrapper.get('[data-home-layer="hero"]').classes()).toContain('paper-surface')
    expect(wrapper.get('[data-home-layer="explore"]').classes()).toContain('paper-surface')
    expect(wrapper.find('[data-dossier-footer]').exists()).toBe(true)
    expect(wrapper.get('[data-hero-index]').text()).toContain('FACTS BEFORE WORDS')
    expect(wrapper.get('[data-hero-primary-action]').attributes('href')).toBe('#credibility')
    expect(wrapper.get('[data-hero-question-action]').attributes('href')).toBe('#dialogue')
    expect(wrapper.findAll('[data-credibility-metric]')).toHaveLength(3)
    expect(wrapper.findAll('[data-explore-entry]')).toHaveLength(4)
  })

  it('hides a missing owner name instead of rendering a placeholder', async () => {
    const wrapper = mount(HomePage, {
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('[姓名]')
    expect(wrapper.text()).not.toContain('待填写')
  })

  it('shows a safe retry action when public content fails', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'error'
    state.error.value = '公开内容暂时无法加载，请稍后重试'
    const wrapper = mount(HomePage, {
      global: { provide: { [publicContentStateKey as symbol]: state } },
    })

    await wrapper.get('[data-public-content-retry]').trigger('click')

    expect(state.retry).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('公开内容暂时无法加载，请稍后重试')
    expect(wrapper.text()).not.toContain('/api/v1/public-content')
  })
})
