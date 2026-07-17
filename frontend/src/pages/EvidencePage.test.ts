import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import EvidencePage from './EvidencePage.vue'

describe('EvidencePage', () => {
  async function mountEvidencePage(state = readyPublicContentState()) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/evidence', component: EvidencePage }],
    })
    await router.push('/evidence')
    await router.isReady()

    return mount(EvidencePage, {
      global: {
        plugins: [router],
        provide: { [publicContentStateKey as symbol]: state },
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
  }

  it('renders approved evidence and its public boundary', async () => {
    const wrapper = await mountEvidencePage()
    await flushPromises()

    expect(wrapper.text()).toContain('SQL 审计工具交付证据集')
    expect(wrapper.text()).toContain('只展示经过公开审查的脱敏索引')
    expect(wrapper.text()).toContain('E-01')
    expect(wrapper.get('[data-selected-evidence]').classes()).not.toContain(
      'evidence-catalog__item--red',
    )
  })

  it('shows loading feedback before deciding the evidence index is empty', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = await mountEvidencePage(state)

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('证明材料尚未公开')
  })
})
