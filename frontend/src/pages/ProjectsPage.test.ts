import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { previewPublicContent } from '../features/public-content/data/previewPublicContent'
import type { PublicProject } from '../features/public-content/model/publicContentTypes'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import ProjectsPage from './ProjectsPage.vue'

const RouterLinkStub = {
  props: ['to'],
  template: '<a :href="to"><slot /></a>',
}

function makeProject(overrides: Partial<PublicProject> = {}): PublicProject {
  return {
    id: 'project-x',
    slug: 'project-x',
    code: 'P-XX',
    title: '占位项目',
    summary: '占位摘要',
    background: '占位背景',
    responsibilities: [],
    solution: '',
    keyDecisions: [],
    technologies: [],
    verification: [],
    outcome: '',
    handoff: '',
    status: 'DELIVERED',
    contributionType: 'PRIMARY',
    careerTrack: 'JAVA_BACKEND',
    projectNature: 'TOOL',
    displayTier: 'PRIMARY',
    caseCount: 0,
    featuredCases: [],
    evidenceIds: [],
    evidence: [],
    suggestedQuestions: [],
    ...overrides,
  }
}

function mountPage(projects: PublicProject[]) {
  const state = readyPublicContentState()
  state.portfolio.value = { ...previewPublicContent, projects }
  return mount(ProjectsPage, {
    global: {
      provide: { [publicContentStateKey as symbol]: state },
      stubs: { RouterLink: RouterLinkStub },
    },
  })
}

describe('ProjectsPage · 项目主线', () => {
  it('页面命名为「项目主线」，Java 后端与 Agent 两条主线同时可见', async () => {
    const wrapper = mountPage([
      makeProject({ slug: 'sql-audit', title: 'SQL 审计与故障排查工具', caseCount: 2 }),
      makeProject({
        slug: 'personal-agent-platform',
        title: 'Agent 能力集成 MVP',
        careerTrack: 'AGENT',
        projectNature: 'INTEGRATION_PROTOTYPE',
        status: 'PROTOTYPE',
      }),
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('项目主线')
    expect(wrapper.text()).toContain('Java 后端主线')
    expect(wrapper.text()).toContain('Agent 主线')
    expect(wrapper.text()).toContain('SQL 审计与故障排查工具')
    expect(wrapper.text()).toContain('Agent 能力集成 MVP')
    expect(wrapper.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
  })

  it('不再渲染任何 Case 条目', async () => {
    const state = readyPublicContentState()
    const wrapper = mount(ProjectsPage, {
      global: {
        provide: { [publicContentStateKey as symbol]: state },
        stubs: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).not.toContain('代码图谱工具端到端评测')
    expect(wrapper.text()).not.toContain('CASE-01')
  })

  it('卡片呈现方向、性质、成熟度、贡献、摘要与核心技术', async () => {
    const wrapper = mountPage([
      makeProject({
        slug: 'sql-audit',
        title: 'SQL 审计与故障排查工具',
        summary: '把慢 SQL 排查从半天压到几分钟。',
        technologies: ['Java 21', 'Spring Boot', 'MyBatis'],
        caseCount: 2,
      }),
    ])
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Java 后端')
    expect(text).toContain('工具')
    expect(text).toContain('核心版本已交付')
    expect(text).toContain('主导贡献')
    expect(text).toContain('把慢 SQL 排查从半天压到几分钟。')
    expect(text).toContain('Java 21')
    expect(text).toContain('Spring Boot')
  })

  it('caseCount 大于零时显示案例数量，等于零时不出现「0 个案例」', async () => {
    const wrapper = mountPage([
      makeProject({ slug: 'with-cases', title: '有案例的项目', caseCount: 3 }),
      makeProject({ slug: 'no-cases', title: '没案例的项目', caseCount: 0 }),
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('3 个案例')
    expect(wrapper.text()).not.toContain('0 个案例')
  })

  it('整张卡片是指向项目详情的链接', async () => {
    const wrapper = mountPage([makeProject({ slug: 'sql-audit', title: 'SQL 审计与故障排查工具' })])
    await flushPromises()

    const link = wrapper.get('a[href="/projects/sql-audit"]')
    expect(link.text()).toContain('SQL 审计与故障排查工具')
  })

  it('次级项目进入「次级项目」架位，排在主线之后', async () => {
    const wrapper = mountPage([
      makeProject({ slug: 'image-upload-audit', title: '图片上传与审计', displayTier: 'SECONDARY' }),
      makeProject({ slug: 'sql-audit', title: 'SQL 审计与故障排查工具' }),
    ])
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('次级项目')
    expect(text.indexOf('SQL 审计与故障排查工具')).toBeLessThan(text.indexOf('次级项目'))
  })

  it('没有项目时显示空态而不是空架位', async () => {
    const wrapper = mountPage([])
    await flushPromises()

    expect(wrapper.text()).toContain('案卷资料准备中')
  })

  it('loading 时先显示装订反馈，不提前判定为空', () => {
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
    expect(wrapper.text()).not.toContain('案卷资料准备中')
  })
})
