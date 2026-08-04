import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { previewPublicContent } from '../features/public-content/data/previewPublicContent'
import type {
  PublicCaseSummary,
  PublicProject,
} from '../features/public-content/model/publicContentTypes'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import ProjectPage from './ProjectPage.vue'

const RouterLinkStub = defineComponent({
  props: { to: { type: [String, Object], required: true } },
  computed: {
    href(): string {
      const to = this.to as string | { path?: string; query?: Record<string, string> }
      if (typeof to === 'string') return to
      let url: string = to.path ?? ''
      if (to.query) {
        const qs = new URLSearchParams(to.query).toString()
        if (qs) url += (url.includes('?') ? '&' : '?') + qs
      }
      return url
    },
  },
  template: '<a :href="href"><slot /></a>',
})
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

describe('ProjectPage · 相关案例', () => {
  const makeSummary = (overrides: Partial<PublicCaseSummary> = {}): PublicCaseSummary => ({
    slug: 'featured-case',
    code: 'CASE-09',
    type: 'FEATURE',
    title: '精选案例',
    summary: '一次具体任务的公开摘要。',
    achievementStatus: 'DELIVERED',
    contributionType: 'PRIMARY',
    projectSlug: 'sql-audit',
    collectionSlugs: [],
    ...overrides,
  })

  const makeProject = (overrides: Partial<PublicProject> = {}): PublicProject => ({
    ...previewPublicContent.projects[0],
    ...overrides,
  })

  function mountWithProject(project: PublicProject) {
    const state = readyPublicContentState()
    state.portfolio.value = { ...previewPublicContent, projects: [project] }
    return mount(ProjectPage, {
      props: { slug: project.slug },
      global: {
        provide: { [publicContentStateKey as symbol]: state },
        stubs: { RouterLink: RouterLinkStub },
        plugins: [testRouter()],
      },
    })
  }

  it('项目没有案例时不渲染相关案例区', async () => {
    const wrapper = mountWithProject(makeProject({ caseCount: 0, featuredCases: [] }))
    await flushPromises()

    expect(wrapper.find('#cases').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('相关案例')
  })

  it('1–3 个案例时全部展示摘要，且不出现查看全部入口', async () => {
    const wrapper = mountWithProject(makeProject())
    await flushPromises()

    const section = wrapper.get('#cases')
    expect(section.text()).toContain('相关案例')
    expect(section.text()).toContain('审计查询归档策略复用')
    expect(section.text()).toContain('WebSocket 进度推送降级排查')
    expect(section.text()).not.toContain('查看全部')
  })

  it('相关案例在目录中登记锚点', async () => {
    const wrapper = mountWithProject(makeProject())
    await flushPromises()

    expect(wrapper.get('.project-toc a[href="#cases"]').text()).toContain('相关案例')
  })

  it('超过 3 个案例时展示精选并提供「查看全部 N 个案例」入口', async () => {
    const featuredCases = [1, 2, 3].map((n) =>
      makeSummary({ slug: `featured-${n}`, code: `CASE-1${n}`, title: `精选案例 ${n}` }),
    )
    const wrapper = mountWithProject(makeProject({ caseCount: 6, featuredCases }))
    await flushPromises()

    const section = wrapper.get('#cases')
    expect(section.text()).toContain('精选案例 1')
    expect(section.text()).toContain('精选案例 3')
    const all = section.get('a[href="/cases?project=sql-audit&status=all"]')
    expect(all.text()).toContain('查看全部 6 个案例')
  })

  it('条目只呈现摘要信息，并链接到案例详情页', async () => {
    const wrapper = mountWithProject(makeProject())
    await flushPromises()

    const section = wrapper.get('#cases')
    const entry = section.get('a[href="/cases/query-archive-reuse"]')
    expect(entry.text()).toContain('CASE-02')
    expect(entry.text()).toContain('功能任务')
    expect(entry.text()).toContain('审计查询归档策略复用')
    expect(entry.text()).toContain('复用既有查询归档能力承载审计结果')
  })

  it('完整呈现 ABTest 主叙事、三个 Case、证据与 Agent 入口', async () => {
    const featuredCases = [
      makeSummary({
        slug: 'abtest-experiment-design',
        code: 'CASE-53',
        title: '实验设计与稳定分流',
        projectSlug: 'weekend-login-abtest',
      }),
      makeSummary({
        slug: 'abtest-service-sql',
        code: 'CASE-54',
        title: '服务端能力与配置 SQL',
        projectSlug: 'weekend-login-abtest',
      }),
      makeSummary({
        slug: 'abtest-validation-risk-control',
        code: 'CASE-55',
        title: '验证、观测与风险控制',
        projectSlug: 'weekend-login-abtest',
      }),
    ]
    const wrapper = mountWithProject(makeProject({
      slug: 'weekend-login-abtest',
      code: 'P-07',
      title: '周末登录奖励 ABTest 完整闭环',
      background: '重要活动需要同时保证样本可比、归组稳定与事件口径一致。',
      responsibilities: ['完成实验建模与服务端实验能力扩展。'],
      solution: '按历史登录天数分层，在层内稳定分桶并持久化实验标签。',
      keyDecisions: ['把分层窗口配置化，并为不同活动期次隔离实验身份。'],
      verification: ['核对配置、缓存、埋点、停止条件与回滚边界。'],
      outcome: '形成从需求、实现、配置到验证和风险控制的完整工程闭环。',
      technologies: ['Java', 'A/B Testing', 'SQL'],
      projectNature: 'WORKSTREAM',
      caseCount: 3,
      featuredCases,
    }))
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('周末登录奖励 ABTest 完整闭环')
    expect(text).toContain('为什么做')
    expect(text).toContain('按历史登录天数分层')
    expect(text).toContain('核对配置、缓存、埋点、停止条件与回滚边界')
    expect(text).toContain('实验设计与稳定分流')
    expect(text).toContain('服务端能力与配置 SQL')
    expect(text).toContain('验证、观测与风险控制')
    expect(wrapper.get('a[href="/cases/abtest-experiment-design"]')).toBeDefined()
    expect(wrapper.findAll('a').some((link) =>
      (link.attributes('href') ?? '').startsWith('/agent'))).toBe(true)
  })
})
