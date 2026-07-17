import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import ProjectsPage from './ProjectsPage.vue'

const RouterLinkStub = { template: '<a><slot /></a>' }

describe('ProjectsPage', () => {
  it('renders the approved project index', async () => {
    const wrapper = mount(ProjectsPage, {
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('工程案卷目录')
    expect(wrapper.text()).toContain('SQL 审计与故障排查工具')
    expect(wrapper.text()).toContain('P-01')
    expect(wrapper.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
  })

  it('shows loading feedback before deciding the project list is empty', () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = mount(ProjectsPage, {
      global: {
        provide: { [publicContentStateKey as symbol]: state },
        stubs: { RouterLink: RouterLinkStub },
      },
    })

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('项目资料准备中')
  })
})
