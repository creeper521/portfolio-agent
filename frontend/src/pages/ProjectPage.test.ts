import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import ProjectPage from './ProjectPage.vue'

const RouterLinkStub = { props: ['to'], template: '<a><slot /></a>' }
const testRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }],
})

describe('ProjectPage', () => {
  it('presents the project as a why-how-proof dossier', async () => {
    const wrapper = mount(ProjectPage, {
      props: { slug: 'sql-audit' },
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
        plugins: [testRouter()],
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
        plugins: [testRouter()],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('该案卷尚未公开')
  })

  it('renders a section-trace footnote only on sections that have verified claims', async () => {
    // preview fixture: sql-audit 只有 1 个 OUTCOME claim → 仅 status 段有脚注
    const wrapper = mount(ProjectPage, {
      props: { slug: 'sql-audit' },
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
        plugins: [testRouter()],
      },
    })
    await flushPromises()

    const traces = wrapper.findAll('.section-trace')
    expect(traces).toHaveLength(1)
    // 落在最终状态段，且含断言数与证据数
    expect(wrapper.get('#status .section-trace').text()).toContain('1')
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
        plugins: [testRouter()],
      },
    })

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('该案卷尚未公开')
  })

  // spec §5.1：旧的 /projects/{caseSlug} 必须重定向到规范 /cases/{caseSlug}。
  // ProjectPage 检测到访问的 slug 实际是 case 时，静默 replace 到 case 路由。
  it('把 case slug 从旧 /projects/:slug 重定向到 /cases/:slug（规范 URL）', async () => {
    const { createAppRouter } = await import('../app/router')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/projects/multilingual-image-preservation')
    await router.isReady()

    mount(ProjectPage, {
      props: { slug: 'multilingual-image-preservation' },
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
        plugins: [router],
      },
    })

    // replace 目标是懒加载路由，动态 import 解析不止一次事件循环，轮询等待落地
    await vi.waitFor(() => {
      expect(router.currentRoute.value.name).toBe('case')
    })
    expect(router.currentRoute.value.params.slug).toBe('multilingual-image-preservation')
  })

  it('不会把真正的 project 重定向（project 仍在 /projects/:slug 渲染）', async () => {
    const { createAppRouter } = await import('../app/router')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/projects/sql-audit')
    await router.isReady()

    const wrapper = mount(ProjectPage, {
      props: { slug: 'sql-audit' },
      global: {
        provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
        stubs: { RouterLink: RouterLinkStub },
        plugins: [router],
      },
    })
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('project')
    expect(wrapper.text()).toContain('SQL 审计与故障排查工具')
  })
})
