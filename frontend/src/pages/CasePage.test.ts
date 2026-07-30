import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import CasePage from './CasePage.vue'

// 把 `to` prop 序列化成 href，字符串直接用，对象 {path,query} 拼成带 query 的 URL
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

function mountCasePage(slug: string, state = readyPublicContentState()) {
  return mount(CasePage, {
    props: { slug },
    global: {
      provide: { [publicContentStateKey as symbol]: state },
      stubs: { RouterLink: RouterLinkStub },
    },
  })
}

describe('CasePage', () => {
  it('渲染 case 详情封面（code / 标题 / 摘要 / record-type）', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    expect(wrapper.text()).toContain('CASE-01')
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).toContain('修复后续语言上传覆盖既有语言映射的问题。')
    expect(wrapper.text()).toContain('ENGINEERING CASE RECORD')
  })

  it('渲染八段结构的全部段标', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('01 / 背景')
    expect(text).toContain('02 / 动作')
    expect(text).toContain('03 / 判断')
    expect(text).toContain('04 / 验证')
    expect(text).toContain('05 / 结果')
    expect(text).toContain('06 / 边界')
    expect(text).toContain('07 / 证据')
    expect(text).toContain('08 / 延伸')
  })

  it('验证段是深色反白面板（case-story__dark）', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const darkSection = wrapper.find('#sec-verify.case-story__dark')
    expect(darkSection.exists()).toBe(true)
  })

  it('problem 段渲染问题背景；actions 段渲染动作列表', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const text = wrapper.text()
    // problem
    expect(text).toContain('分次上传不同语言图片时')
    // actions
    expect(text).toContain('读取已经持久化的语言映射')
    // decisions（与 actions 不重复，单独一段）
    expect(text).toContain('使用既有持久化映射与本次有效上传的并集')
  })

  it('结果段渲染 outcome 引述', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    expect(wrapper.text()).toContain('德语上传、德语查询、法语上传')
  })

  it('限制段逐条渲染 limitations（非合并成一坨）', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const limitsSection = wrapper.find('#sec-limits')
    const items = limitsSection.findAll('li')
    expect(items.length).toBeGreaterThanOrEqual(2)
    expect(wrapper.text()).toContain('不公开内部模块、数据结构')
  })

  it('证据段渲染 case 直接关联的 APPROVED 证据', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const text = wrapper.text()
    // preview 数据里该 case 的 evidence title
    expect(text).toContain('多语言图片')
  })

  it('建议问题通过一次性交接 ID 进入 Agent，不把问题或案例上下文写入 URL', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('询问本案例')

    // URL 只携带随机交接 ID；问题、case slug 与 source 留在页内内存。
    const agentLinks = wrapper.findAll('a').filter((a) => {
      const href = a.attributes('href') ?? ''
      return href.startsWith('/agent')
    })
    expect(agentLinks.length).toBeGreaterThan(0)
    const firstHref = agentLinks[0].attributes('href') ?? ''
    expect(firstHref).toContain('caseHandoffId=')
    expect(firstHref).not.toContain('q=')
    expect(firstHref).not.toContain('question=')
    expect(firstHref).not.toContain('case=multilingual-image-preservation')
    expect(firstHref).not.toContain('source=CASE')
  })

  it('封面右列展示 case 元信息（类型 / 关联项目 / 证据数 / 断言数）', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('案卷信息')
    expect(text).toContain('功能任务') // 类型
    expect(text).toContain('独立案例') // preview 数据该 case projectSlug=null
    expect(text).toContain('1 APPROVED') // 证据数（preview 该 case 有 1 条证据）
    expect(text).toContain('1 CLAIMS') // 断言数（preview 该 case 有 1 条 claim）
  })

  it('关联项目渲染项目标题并链接到项目详情（原型：封面元信息）', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = {
      ...state.portfolio.value!,
      cases: state.portfolio.value!.cases.map((c) =>
        c.slug === 'multilingual-image-preservation' ? { ...c, projectSlug: 'sql-audit' } : c,
      ),
    }
    const wrapper = mountCasePage('multilingual-image-preservation', state)
    await flushPromises()

    const link = wrapper
      .findAll('a')
      .find((a) => (a.attributes('href') ?? '') === '/projects/sql-audit')
    expect(link).toBeDefined()
    expect(link!.text()).toContain('SQL 审计与故障排查工具')
    expect(wrapper.text()).not.toContain('独立案例')
  })

  it('底部「不带案例上下文提问」链接到不带 query 的 /agent（原型 case-next）', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const plainAgent = wrapper
      .findAll('a')
      .filter((a) => (a.attributes('href') ?? '') === '/agent')
    expect(plainAgent.length).toBeGreaterThan(0)
    expect(plainAgent[0].text()).toContain('不带案例上下文提问')
  })

  it('不存在的 slug 显示 404（CASE_NOT_FOUND），不渲染详情', async () => {
    const wrapper = mountCasePage('does-not-exist')
    await flushPromises()

    expect(wrapper.text()).toContain('CASE_NOT_FOUND')
    expect(wrapper.text()).not.toContain('案卷信息')
  })

  it('加载中显示反馈文案', () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = mountCasePage('multilingual-image-preservation', state)

    expect(wrapper.text()).toContain('正在装订公开档案…')
  })

  it('渲染面包屑（案例目录 / 类型 / CASE 编号）', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('案例目录')
    // 面包屑里有返回链接指向 /cases
    const homeLinks = wrapper.findAll('a').filter((a) => (a.attributes('href') ?? '') === '/cases')
    expect(homeLinks.length).toBeGreaterThan(0)
  })

  it('返回目录链接指向 /cases', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const backLinks = wrapper.findAll('a').filter((a) => {
      const href = a.attributes('href') ?? ''
      return href === '/cases'
    })
    expect(backLinks.length).toBeGreaterThan(0)
  })

  it('案卷信息区展示工作状态行与维度独立性说明', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    const meta = wrapper.find('.case-cover__meta')
    expect(meta.text()).toContain('工作状态')
    expect(meta.text()).toContain('已交付')
    expect(meta.text()).toContain('工作状态与证据强度是两个独立维度')
  })

  it('有所属集合时渲染集合行，链接到 /cases?collection=<slug>&status=all', async () => {
    const wrapper = mountCasePage('codegraph-evaluation')
    await flushPromises()

    const meta = wrapper.find('.case-cover__meta')
    expect(meta.text()).toContain('所属集合')
    expect(meta.text()).toContain('开源项目体验与测试')

    const collectionLink = wrapper
      .findAll('a')
      .find((a) => (a.attributes('href') ?? '').startsWith('/cases?collection='))
    expect(collectionLink).toBeDefined()
    expect(collectionLink!.attributes('href')).toContain('collection=open-source-evaluation')
    expect(collectionLink!.attributes('href')).toContain('status=all')
  })

  it('无所属集合时不渲染集合行', async () => {
    const wrapper = mountCasePage('multilingual-image-preservation')
    await flushPromises()

    expect(wrapper.find('.case-cover__meta').text()).not.toContain('所属集合')
  })
})
