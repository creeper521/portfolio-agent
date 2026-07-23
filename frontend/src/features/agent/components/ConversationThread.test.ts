import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import type { AgentSession } from '../model/sessionTypes'
import ConversationThread from './ConversationThread.vue'

function session(messages: AgentSession['messages'] = []): AgentSession {
  return {
    id: 'session-1',
    title: '新的工程追问',
    role: 'INTERVIEWER',
    projectSlug: 'sql-audit',
    evidenceId: null,
    seedFingerprint: null,
    createdAt: 1,
    updatedAt: 1,
    messages,
  }
}

function mountThread(
  messages: AgentSession['messages'] = [],
  pending = false,
  error = '',
) {
  return mount(ConversationThread, {
    props: {
      session: session(messages),
      role: 'INTERVIEWER',
      project: previewPublicContent.projects[0],
      pending,
      error,
    },
  })
}

describe('ConversationThread', () => {
  it('submits a suggested question immediately', async () => {
    const wrapper = mountThread()
    const question = previewPublicContent.projects[0].suggestedQuestions[0]

    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('empty')
    await wrapper.get('[data-suggested-question]').trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[question]])
  })

  it('renders a user bubble and an open Agent document', () => {
    const wrapper = mountThread([
      {
        id: 'user-1',
        role: 'USER',
        content: '为什么没有运行产物？',
        answer: null,
        evidenceIds: [],
        createdAt: 2,
      },
      {
        id: 'agent-1',
        role: 'AGENT',
        content: '当前仅作为审计证据保留。',
        answer: null,
        evidenceIds: [],
        createdAt: 3,
      },
    ])

    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('conversation')
    expect(wrapper.get('[data-message-id="user-1"] .message__body').text())
      .toBe('为什么没有运行产物？')
    expect(wrapper.get('[data-message-id="agent-1"]').classes()).toContain('message--agent')
    expect(wrapper.get('[data-message-id="agent-1"]').find('.message__bubble').exists()).toBe(false)
  })

  it('sends on Enter but keeps Shift+Enter for a newline', async () => {
    const wrapper = mountThread()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('继续追问')
    await textarea.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('submit')).toEqual([['继续追问']])

    await textarea.setValue('第一行')
    await textarea.trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('marks the generating state with progressive Agent copy', () => {
    const wrapper = mountThread([], true)

    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('generating')
    expect(wrapper.get('[data-agent-loading]').text()).toContain('正在核验证据')
  })

  it('grows the Composer with its content up to the configured height', async () => {
    const wrapper = mountThread()
    const textarea = wrapper.get('textarea')
    Object.defineProperty(textarea.element, 'scrollHeight', {
      configurable: true,
      value: 96,
    })

    await textarea.setValue('需要更多空间的追问')
    await textarea.trigger('input')

    expect((textarea.element as HTMLTextAreaElement).style.height).toBe('96px')
  })

  it('recovers a failed question into the focused Composer for editing', async () => {
    const wrapper = mountThread([
      {
        id: 'user-1',
        role: 'USER',
        content: '修改这条失败的问题',
        answer: null,
        evidenceIds: [],
        createdAt: 2,
      },
    ], false, '回答失败')
    document.body.appendChild(wrapper.element)

    await wrapper.get('[data-answer-edit]').trigger('click')

    expect(wrapper.get('textarea').element).toBe(document.activeElement)
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value)
      .toBe('修改这条失败的问题')
    wrapper.unmount()
  })

  it('offers a jump to the latest answer after the reader scrolls away', async () => {
    const wrapper = mountThread([
      {
        id: 'agent-1',
        role: 'AGENT',
        content: '较早的回答',
        answer: null,
        evidenceIds: [],
        createdAt: 2,
      },
    ])
    const scrollArea = wrapper.get('.conversation__scroll')
    Object.defineProperties(scrollArea.element, {
      scrollHeight: { configurable: true, value: 1000 },
      scrollTop: { configurable: true, writable: true, value: 100 },
      clientHeight: { configurable: true, value: 300 },
    })
    const scrollTo = vi.fn()
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))

    await scrollArea.trigger('scroll')
    await wrapper.get('[data-jump-latest]').trigger('click')

    expect(scrollTo).toHaveBeenCalledWith({ top: 1000, behavior: 'auto' })
    expect(wrapper.find('[data-jump-latest]').exists()).toBe(false)
  })
})
