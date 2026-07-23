import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { createAppRouter } from '../../app/router'
import DossierHeader from './DossierHeader.vue'

describe('DossierHeader', () => {
  it('uses the paper theme and homepage anchors on the homepage', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DossierHeader, {
      global: { plugins: [router] },
    })

    expect(wrapper.get('[data-header-theme]').attributes('data-header-theme')).toBe('paper')
    expect(wrapper.findAll('[data-home-anchor]')).toHaveLength(3)
  })

  it('uses the warm theme on the Agent workspace route', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/agent')
    await router.isReady()

    const wrapper = mount(DossierHeader, {
      global: { plugins: [router] },
    })

    expect(wrapper.get('[data-header-theme]').attributes('data-header-theme')).toBe('warm')
  })
})
