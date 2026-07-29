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
    expect(wrapper.find('#primary-navigation a[href="/cases"]').exists()).toBe(true)
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

  it('exposes projects and cases as separate primary navigation entries', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/timeline')
    await router.isReady()

    const wrapper = mount(DossierHeader, {
      global: { plugins: [router] },
    })

    const links = wrapper.findAll('#primary-navigation a')
    expect(links.some((link) => link.attributes('href') === '/projects')).toBe(true)
    expect(links.some((link) => link.attributes('href') === '/cases')).toBe(true)
  })
})
