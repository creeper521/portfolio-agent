import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from '../app/router'
import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import CasesPage from './CasesPage.vue'

type ContentState = ReturnType<typeof readyPublicContentState>

/**
 * 用真实路由挂载：/cases 的筛选状态完全由 URL query 驱动，
 * 只有真实 router 才能验证「query 恢复筛选 / 交互写回 query」的闭环。
 */
async function mountCasesPage(initialUrl = '/cases', beforeMount?: (state: ContentState) => void) {
  const state = readyPublicContentState()
  beforeMount?.(state)
  const router = createAppRouter(createMemoryHistory())
  await router.push(initialUrl)
  await router.isReady()
  const wrapper = mount(CasesPage, {
    global: {
      provide: { [publicContentStateKey as symbol]: state },
      plugins: [router],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('CasesPage', () => {
  it('渲染页头，默认只显示「已交付」案例，数量与条件常显', async () => {
    const { wrapper } = await mountCasesPage()

    expect(wrapper.text()).toContain('案例目录')
    // 默认筛选 = 已交付：preview 里只有多语言案例是 DELIVERED
    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).not.toContain('代码图谱工具端到端评测')
    // 当前条件 chips 常显默认状态
    expect(wrapper.text()).toContain('状态 · 已交付')
    // 三组筛选栏都在
    expect(wrapper.find('[data-filter-group="status"]').exists()).toBe(true)
    expect(wrapper.find('[data-filter-group="attribution"]').exists()).toBe(true)
    expect(wrapper.find('[data-filter-group="type"]').exists()).toBe(true)
  })

  it('URL query 恢复筛选：status=all 显示全部案例', async () => {
    const { wrapper } = await mountCasesPage('/cases?status=all')

    expect(wrapper.text()).toContain('2 条结果')
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).toContain('代码图谱工具端到端评测')
  })

  it('非法 query 回退安全默认（status=bogus → 已交付）', async () => {
    const { wrapper } = await mountCasesPage('/cases?status=bogus')

    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).not.toContain('代码图谱工具端到端评测')
  })

  it('project 归属筛选：?project=sql-audit&status=all 只显示该项目案例并亮出项目名', async () => {
    const { wrapper } = await mountCasesPage('/cases?project=sql-audit&status=all', (state) => {
      const data = state.portfolio.value!
      state.portfolio.value = {
        ...data,
        cases: data.cases.map((item, index) =>
          index === 0 ? { ...item, projectSlug: 'sql-audit' } : item,
        ),
      }
    })

    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).not.toContain('代码图谱工具端到端评测')
    // 条件 chip 显示项目标题而非裸 slug
    expect(wrapper.text()).toContain('项目 · SQL 审计与故障排查工具')
  })

  it('collection 归属筛选：?collection=open-source-evaluation&status=all', async () => {
    const { wrapper } = await mountCasesPage('/cases?collection=open-source-evaluation&status=all')

    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('代码图谱工具端到端评测')
    expect(wrapper.text()).toContain('集合 · 开源项目体验与测试')
  })

  it('独立案例筛选：?independent=1&status=all 显示所有无归属案例', async () => {
    const { wrapper } = await mountCasesPage('/cases?independent=1&status=all')

    // preview 两个案例都没有 projectSlug
    expect(wrapper.text()).toContain('2 条结果')
    expect(wrapper.text()).toContain('归属 · 独立案例')
  })

  it('类型筛选：?type=EVALUATION&status=all 只显示工具评测', async () => {
    const { wrapper } = await mountCasesPage('/cases?type=EVALUATION&status=all')

    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('代码图谱工具端到端评测')
    expect(wrapper.text()).not.toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).toContain('类型 · 工具评测')
  })

  it('关键词搜索：?q=代码图谱 命中标题并回填搜索框', async () => {
    const { wrapper } = await mountCasesPage('/cases?q=代码图谱&status=all')

    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('代码图谱工具端到端评测')
    expect(wrapper.text()).toContain('关键词 · 代码图谱')
    const input = wrapper.find('input[type="search"]')
    expect((input.element as HTMLInputElement).value).toBe('代码图谱')
  })

  it('点击状态选项写回 URL query（已交付 → 原型验证）', async () => {
    const { wrapper, router } = await mountCasesPage()

    const option = wrapper.find('[data-filter-group="status"] [data-value="prototype"]')
    expect(option.exists()).toBe(true)
    await option.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.status).toBe('prototype')
    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('代码图谱工具端到端评测')
    expect(wrapper.text()).not.toContain('多语言图片上传结果保留修复')
  })

  it('空结果态与无数据态区分：筛选无匹配 ≠ 没有案例', async () => {
    // preview 没有 INVESTIGATED 案例 → 合法筛选、零匹配
    const { wrapper } = await mountCasesPage('/cases?status=investigated')

    expect(wrapper.text()).toContain('0 条结果')
    expect(wrapper.text()).toContain('没有符合当前条件的案例')
    expect(wrapper.text()).not.toContain('案卷资料准备中')

    // 数据源本身为空 → 无数据态
    const { wrapper: emptyWrapper } = await mountCasesPage('/cases', (state) => {
      state.portfolio.value = { ...state.portfolio.value!, cases: [] }
    })
    expect(emptyWrapper.text()).toContain('案卷资料准备中')
    expect(emptyWrapper.text()).not.toContain('没有符合当前条件的案例')
  })

  it('空结果态可一键清除全部条件，回到默认「已交付」视图', async () => {
    const { wrapper, router } = await mountCasesPage('/cases?status=investigated')

    const reset = wrapper.find('[data-clear-all]')
    expect(reset.exists()).toBe(true)
    await reset.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({})
    expect(wrapper.text()).toContain('1 条结果')
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
  })

  it('case 行跳转到规范 /cases/:slug 路由', async () => {
    const { wrapper } = await mountCasesPage()

    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href'))
    expect(hrefs).toContain('/cases/multilingual-image-preservation')
    // 不出现指向 /projects/ 的 case 链接
    expect(hrefs.some((href) => href?.startsWith('/projects/'))).toBe(false)
  })

  it('加载中显示反馈文案，不渲染目录', async () => {
    const { wrapper } = await mountCasesPage('/cases', (state) => {
      state.portfolio.value = null
      state.status.value = 'loading'
    })

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('案例目录')
  })

  it('渲染页脚并带 contentVersion', async () => {
    const { wrapper } = await mountCasesPage()

    expect(wrapper.text()).toContain('VOLUME 01')
    expect(wrapper.text()).toContain('PUBLIC · DESENSITIZED')
  })
})
