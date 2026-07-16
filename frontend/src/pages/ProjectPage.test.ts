import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProjectPage from './ProjectPage.vue'

describe('ProjectPage', () => {
  it('presents the project as a why-how-proof dossier', async () => {
    const wrapper = mount(ProjectPage, {
      props: { slug: 'sql-audit' },
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('SQL 审计与故障排查工具')
    expect(wrapper.text()).toContain('为什么做')
    expect(wrapper.text()).toContain('如何做')
    expect(wrapper.text()).toContain('如何证明')
    expect(wrapper.get('h1').attributes('data-mobile-balanced')).toBeDefined()
  })

  it('shows an unpublished state for an unknown slug', async () => {
    const wrapper = mount(ProjectPage, {
      props: { slug: 'private-project' },
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('该项目尚未公开')
  })
})
