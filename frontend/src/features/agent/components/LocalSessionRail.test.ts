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
  notices: [],
  discussionRevision: 0,
}]

describe('LocalSessionRail', () => {
  it('renders a title-only list with an active row', () => {
    const wrapper = mount(LocalSessionRail, {
      props: { sessions, activeId: 'session-1' },
    })

    expect(wrapper.text()).not.toContain('当前页面会话')
    expect(wrapper.text()).not.toContain('INTERVIEWER')
    expect(wrapper.get('.session-select').text()).toContain('SQL 审计项目的取舍')
    expect(wrapper.get('.session-select').attributes('aria-current')).toBe('true')
  })

  it('标出创建角色短标签，并把角色并入行按钮可访问名（audience-role UI 设计 §4）', () => {
    const wrapper = mount(LocalSessionRail, {
      props: {
        sessions: [
          { ...sessions[0]!, titleDetail: '介绍 SQL 审计项目' },
          { ...sessions[0]!, id: 'session-2', title: '如何复盘这个项目', role: 'MENTOR' },
        ],
        activeId: 'session-1',
      },
    })

    const first = wrapper.get('[data-session-role="INTERVIEWER"]')
    expect(first.text()).toContain('面试官')
    expect(first.find('.session-role-tag').attributes('aria-hidden')).toBe('true')
    expect(first.attributes('aria-label'))
      .toBe('会话（面试官视角）：SQL 审计项目的取舍（问题：介绍 SQL 审计项目）')

    const second = wrapper.get('[data-session-role="MENTOR"]')
    expect(second.text()).toContain('导师')
    expect(second.attributes('aria-label')).toBe('会话（导师视角）：如何复盘这个项目')
    // 无 pending/草稿时不渲染状态后缀。
    expect(wrapper.find('[data-session-pending]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('生成中')
    expect(wrapper.text()).not.toContain('草稿')
  })

  it('pending 行标记「生成中」，无消息但非空草稿的行标记「草稿」', () => {
    const wrapper = mount(LocalSessionRail, {
      props: {
        sessions: [
          sessions[0]!,
          {
            ...sessions[0]!,
            id: 'session-2',
            title: '用一分钟介绍这段实习',
            role: 'HR',
            draft: '  未发送的草稿 ',
            messages: [],
          },
        ],
        activeId: 'session-1',
        pendingIds: ['session-1'],
      },
    })

    const pendingRow = wrapper.get('[data-session-pending]')
    expect(pendingRow.attributes('data-session-id')).toBe('session-1')
    expect(pendingRow.text()).toContain('生成中')
    expect(wrapper.get('[data-session-role="INTERVIEWER"]').attributes('aria-label'))
      .toBe('会话（面试官视角，回答生成中）：SQL 审计项目的取舍')

    const draftRow = wrapper.get('[data-session-role="HR"]')
    expect(draftRow.text()).toContain('草稿')
    expect(draftRow.attributes('aria-label'))
      .toBe('会话（HR视角，含草稿）：用一分钟介绍这段实习')
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
