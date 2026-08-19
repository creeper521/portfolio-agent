import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { AgentSession } from '../model/sessionTypes'
import LocalSessionRail from './LocalSessionRail.vue'

const sessions: AgentSession[] = [{
  id: 'session-1',
  title: 'SQL 审计项目的取舍',
  role: 'INTERVIEWER',
  projectSlug: 'sql-audit',
  seedFingerprint: null,
  createdAt: 1,
  updatedAt: 2,
  messages: [],
}]

describe('LocalSessionRail', () => {
  it('renders a title-only list with an active row', () => {
    const wrapper = mount(LocalSessionRail, {
      props: { sessions, activeId: 'session-1' },
    })

    expect(wrapper.text()).not.toContain('当前页面会话')
    expect(wrapper.text()).not.toContain('INTERVIEWER')
    expect(wrapper.get('.session-select').text()).toBe('SQL 审计项目的取舍')
    expect(wrapper.get('.session-select').attributes('aria-current')).toBe('true')
  })

  it('renames and removes through the row menu', async () => {
    const wrapper = mount(LocalSessionRail, {
      props: { sessions, activeId: 'session-1' },
    })

    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-rename]').trigger('click')
    await wrapper.get('[data-session-rename-input]').setValue('审计项目复盘')
    await wrapper.get('[data-session-rename-form]').trigger('submit')
    expect(wrapper.emitted('rename')).toEqual([['session-1', '审计项目复盘']])

    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-remove]').trigger('click')
    expect(wrapper.emitted('remove')).toEqual([['session-1']])
  })

  it('requires an accessible confirmation before clearing local sessions', async () => {
    const wrapper = mount(LocalSessionRail, {
      attachTo: document.body,
      props: { sessions, activeId: 'session-1' },
    })

    await wrapper.get('[data-session-clear]').trigger('click')
    expect(wrapper.get('[role="alertdialog"]').attributes('aria-labelledby'))
      .toBe('clear-sessions-title')
    expect(wrapper.emitted('clear')).toBeUndefined()

    await wrapper.get('[data-session-clear-cancel]').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(wrapper.emitted('clear')).toBeUndefined()

    await wrapper.get('[data-session-clear]').trigger('click')
    await wrapper.get('[data-session-clear-confirm]').trigger('click')
    expect(wrapper.emitted('clear')).toEqual([[]])
    wrapper.unmount()
  })

  it('exposes the full session title on the row for truncated titles', () => {
    const longTitle = '这是一段超过二十四个字符的会话标题用来验证悬停时可以读到完整内容'
    const wrapper = mount(LocalSessionRail, {
      props: {
        sessions: [{ ...sessions[0]!, title: longTitle }],
        activeId: 'session-1',
      },
    })

    expect(wrapper.get('.session-select').attributes('title')).toBe(longTitle)
  })

  it('does not cap the rename input length', async () => {
    const wrapper = mount(LocalSessionRail, {
      props: { sessions, activeId: 'session-1' },
    })

    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-rename]').trigger('click')

    expect(wrapper.get('[data-session-rename-input]').attributes('maxlength')).toBeUndefined()
  })
})
