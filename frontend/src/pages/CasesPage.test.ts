import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import CasesPage from './CasesPage.vue'

// 把 `to` prop 渲染成 href，便于断言跳转目标（字符串 to 直接用，对象 to 取 path）
const RouterLinkStub = defineComponent({
  props: { to: { type: [String, Object], required: true } },
  computed: {
    href(): string {
      const to = this.to as string | { path?: string }
      return typeof to === 'string' ? to : (to.path ?? '')
    },
  },
  template: '<a :href="href"><slot /></a>',
})

function mountCasesPage(state = readyPublicContentState()) {
  return mount(CasesPage, {
    global: {
      provide: { [publicContentStateKey as symbol]: state },
      stubs: { RouterLink: RouterLinkStub },
    },
  })
}

describe('CasesPage', () => {
  it('渲染案例目录页头与状态 tab', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    expect(wrapper.text()).toContain('案例目录')
    expect(wrapper.text()).toContain(`${readyPublicContentState().portfolio.value!.cases.length} 个具体任务`)
    expect(wrapper.text()).not.toContain('49 个具体任务')
    // 三个状态 tab（preview 数据含 delivered + prototype）
    expect(wrapper.text()).toContain('已交付')
    expect(wrapper.text()).toContain('原型验证')
  })

  it('默认进入「已交付」tab，先亮交付实力', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    // 默认 tab 选中 delivered
    const selectedTab = wrapper.find('[role="tab"][aria-selected="true"]')
    expect(selectedTab.attributes('data-sg')).toBe('delivered')
    // 面板导言编号与原型一致：01 / DELIVERED
    expect(wrapper.text()).toContain('01 / DELIVERED')
    // 多语言案例是 DELIVERED，应出现在默认视图
    expect(wrapper.text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.text()).toContain('CASE-01')
  })

  it('case 行跳转到规范 /cases/:slug 路由，而非旧 /projects/:slug', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    const links = wrapper.findAll('a')
    const caseLinks = links.filter((a) => a.attributes('href')?.startsWith('/cases/'))
    expect(caseLinks.length).toBeGreaterThan(0)
    // 不应出现指向 /projects/ 的 case 链接
    const projectLinks = links.filter((a) => a.attributes('href')?.startsWith('/projects/'))
    expect(projectLinks.length).toBe(0)
  })

  it('状态 tab 实现 ARIA tablist：有 tablist/tab 角色，选中态 aria-selected', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    expect(wrapper.find('[role="tablist"]').exists()).toBe(true)
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.length).toBeGreaterThanOrEqual(1)
    const selectedCount = tabs.filter((t) => t.attributes('aria-selected') === 'true').length
    expect(selectedCount).toBe(1)
    // tab 的 id 与 panel 的 aria-labelledby 对应（ARIA 关联完整）
    const panel = wrapper.find('[role="tabpanel"]')
    expect(panel.attributes('aria-labelledby')).toBe('tab-delivered')
    expect(wrapper.find('[role="tab"][data-sg="delivered"]').attributes('id')).toBe('tab-delivered')
  })

  it('点击 tab 切换分组', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    // preview 数据里 codegraph 是 PROTOTYPE
    const protoTab = wrapper.find('[role="tab"][data-sg="prototype"]')
    await protoTab.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="tab"][data-sg="prototype"]').attributes('aria-selected')).toBe(
      'true',
    )
    expect(wrapper.text()).toContain('代码图谱工具端到端评测')
  })

  it('未选中 tab 的 tabindex=-1，选中 tab tabindex=0（键盘 tab 模式）', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    const tabs = wrapper.findAll('[role="tab"]')
    const selected = tabs.find((t) => t.attributes('aria-selected') === 'true')!
    expect(selected.attributes('tabindex')).toBe('0')
    const unselected = tabs.filter((t) => t.attributes('aria-selected') !== 'true')
    for (const t of unselected) {
      expect(t.attributes('tabindex')).toBe('-1')
    }
  })

  it('加载中显示反馈文案，不渲染目录', () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = mountCasesPage(state)

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.text()).not.toContain('案例目录')
  })

  it('没有 case 时显示空状态', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = { ...state.portfolio.value!, cases: [] }
    const wrapper = mountCasesPage(state)
    await flushPromises()

    expect(wrapper.text()).toContain('案卷资料准备中')
  })

  it('渲染页脚并带 contentVersion', async () => {
    const wrapper = mountCasesPage()
    await flushPromises()

    expect(wrapper.text()).toContain('VOLUME 01')
    expect(wrapper.text()).toContain('PUBLIC · DESENSITIZED')
  })
})
