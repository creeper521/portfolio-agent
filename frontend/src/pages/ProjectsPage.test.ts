import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProjectsPage from './ProjectsPage.vue'

describe('ProjectsPage', () => {
  it('renders the approved project index', async () => {
    const wrapper = mount(ProjectsPage, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('工程案卷目录')
    expect(wrapper.text()).toContain('SQL 审计与故障排查工具')
    expect(wrapper.text()).toContain('P-01')
    expect(wrapper.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
  })
})
