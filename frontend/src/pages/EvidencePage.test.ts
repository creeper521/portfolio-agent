import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import EvidencePage from './EvidencePage.vue'

describe('EvidencePage', () => {
  it('renders approved evidence and its public boundary', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/evidence', component: EvidencePage }],
    })
    await router.push('/evidence')
    await router.isReady()

    const wrapper = mount(EvidencePage, {
      global: {
        plugins: [router],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('SQL 审计工具交付证据集')
    expect(wrapper.text()).toContain('只展示经过公开审查的脱敏索引')
    expect(wrapper.text()).toContain('E-01')
    expect(wrapper.get('[data-selected-evidence]').classes()).not.toContain(
      'evidence-catalog__item--red',
    )
  })
})
