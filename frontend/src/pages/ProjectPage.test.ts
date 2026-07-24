import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import ProjectPage from './ProjectPage.vue'

const RouterLinkStub = { props: ['to'], template: '<a><slot /></a>' }

describe('ProjectPage', () => {
  it('presents the project as a why-how-proof dossier', async () => {
    const wrapper = mount(ProjectPage, {
      props: { slug: 'sql-audit' },
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
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
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('该案卷尚未公开')
  })

  it('renders a case slug as a dossier via the same template', async () => {
    const wrapper = mount(ProjectPage, {
      props: { slug: 'multilingual-image-preservation' },
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).toContain('CASE-01')
    expect(wrapper.text()).toContain('为什么做')
    expect(wrapper.text()).toContain('如何证明')
  })

  it('does not report an unpublished project while public content is loading', () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = mount(ProjectPage, {
      props: { slug: 'private-project' },
      global: {
        provide: { [publicContentStateKey as symbol]: state },
        stubs: { RouterLink: RouterLinkStub },
      },
    })

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('该案卷尚未公开')
  })
})
