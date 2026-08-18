import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { PublicSourceReference } from '../model/publicAgentTurn'
import SourceDrawer from './SourceDrawer.vue'

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

function sourcesOf(fileName: string): readonly PublicSourceReference[] {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'ANSWER') throw new Error('期望 ANSWER')
  return turn.answer.sourceCatalog.sources
}

function mountDrawer(open: boolean) {
  return mount(SourceDrawer, {
    props: {
      open,
      sources: sourcesOf('answer-complete.json'),
      contentReleaseId: '2026-08-05.1',
    },
    attachTo: document.body,
    global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
  })
}

describe('SourceDrawer', () => {
  it('open=false 不渲染；open 渲染 dialog 与全部来源、内容版本只在来源详情出现', async () => {
    const closed = mountDrawer(false)
    expect(closed.find('[data-testid="source-drawer"]').exists()).toBe(false)
    await closed.unmount()

    const wrapper = mountDrawer(true)
    const dialog = wrapper.find('[data-testid="source-drawer"]')
    expect(dialog.attributes('role')).toBe('dialog')
    expect(dialog.attributes('aria-modal')).toBe('true')
    const items = wrapper.findAll('.source-drawer__item')
    expect(items).toHaveLength(2)
    expect(items[0]?.find('.source-drawer__code')?.text()).toBe('E-01')
    expect(wrapper.find('.source-drawer__release').text()).toContain('2026-08-05.1')
    await wrapper.unmount()
  })

  it('Esc、关闭按钮与遮罩都触发 close 事件', async () => {
    const wrapper = mountDrawer(true)
    await wrapper.find('[data-testid="source-drawer"]').trigger('keydown', { key: 'Escape' })
    await wrapper.find('[data-testid="source-drawer-close"]').trigger('click')
    await wrapper.find('[data-testid="source-drawer-overlay"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(3)
    await wrapper.unmount()
  })

  it('打开后焦点进入对话框，关闭后焦点返回触发元素', async () => {
    const opener = document.createElement('button')
    opener.textContent = '查看全部来源'
    document.body.appendChild(opener)
    opener.focus()
    expect(document.activeElement).toBe(opener)

    const wrapper = mountDrawer(true)
    await nextTickTwice()
    const dialog = wrapper.find('[data-testid="source-drawer"]').element as HTMLElement
    expect(dialog.contains(document.activeElement)).toBe(true)

    await wrapper.find('[data-testid="source-drawer-close"]').trigger('click')
    await wrapper.setProps({ open: false })
    await nextTickTwice()
    expect(document.activeElement).toBe(opener)
    opener.remove()
    await wrapper.unmount()
  })

  it('Tab 焦点陷阱：最后一个可聚焦元素上 Tab 回到第一个', async () => {
    const wrapper = mountDrawer(true)
    await nextTickTwice()
    const focusable = [
      ...wrapper.find('[data-testid="source-drawer"]').element.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled])',
      ),
    ]
    expect(focusable.length).toBeGreaterThan(2)
    const last = focusable[focusable.length - 1] as HTMLElement
    last.focus()
    await wrapper.find('[data-testid="source-drawer"]').trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(focusable[0])
    await wrapper.unmount()
  })
})

async function nextTickTwice(): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, 0))
}
