import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import AgentWorkspace from './AgentWorkspace.vue'

describe('AgentWorkspace', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query.includes('1219'),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
  })

  it('renders sessions, conversation, evidence desk, and two accessible separators', () => {
    const wrapper = mount(AgentWorkspace, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })

    expect(wrapper.text()).toContain('本地会话')
    expect(wrapper.text()).toContain('Agent 对话')
    expect(wrapper.text()).toContain('证据工作台')
    expect(wrapper.findAll('[role="separator"]')).toHaveLength(2)
    expect(wrapper.get('.agent-workspace').classes()).toContain('agent-workspace--prototype')
    expect(wrapper.find('.thread-empty-card').exists()).toBe(false)
    expect(wrapper.find('.message--user-card').exists()).toBe(false)
  })

  it('moves a separator by 16px and resets it with Home', async () => {
    const wrapper = mount(AgentWorkspace, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    const handle = wrapper.get('[aria-label="调整历史会话宽度"]')
    const before = Number(handle.attributes('aria-valuenow'))

    await handle.trigger('keydown', { key: 'ArrowRight' })
    expect(Number(handle.attributes('aria-valuenow'))).toBe(before + 16)

    await handle.trigger('keydown', { key: 'Home' })
    expect(Number(handle.attributes('aria-valuenow'))).not.toBe(before + 16)
  })

  it('exposes drawer state and closes an open drawer with Escape', async () => {
    const wrapper = mount(AgentWorkspace, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    const toggle = wrapper.get('.evidence-toggle')

    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('#agent-evidence-desk').attributes('aria-hidden')).toBe('false')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(toggle.attributes('aria-expanded')).toBe('false')
  })

  it('keeps the workspace usable after deleting the only session', async () => {
    const wrapper = mount(AgentWorkspace, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })

    await wrapper.get('[aria-label^="删除会话："]').trigger('click')

    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
    expect(wrapper.text()).toContain('从一个可核验的问题开始。')
  })
})
