import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import type { AnswerFocusTarget } from '../model/evidenceDeskModel'
import type { AgentSession } from '../model/sessionTypes'
import ConversationThread from './ConversationThread.vue'

const answerMessageFixture: AgentSession['messages'][number] = {
  id: 'agent-1',
  role: 'AGENT',
  content: 'Verified answer',
  createdAt: 3,
  evidenceIds: ['sql-audit-delivery-set'],
  answer: {
    title: 'Project details',
    summary: 'Verified answer',
    resolution: 'ANSWERED',
    answerSource: 'PRESET',
    generationMode: 'DETERMINISTIC',
    verification: 'VERIFIED',
    evidenceIds: ['sql-audit-delivery-set'],
    suggestedQuestionPresetIds: [],
    contextEnvelope: {
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
    },
    sections: [{
      type: 'VERIFICATION',
      title: 'Verification',
      content: 'Verified against delivery artifacts and test results.',
      evidenceIds: ['sql-audit-delivery-set'],
      claimIds: ['claim-sql-audit-delivered'],
    }],
  },
}

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
  focusTarget: AnswerFocusTarget | null = null,
) {
  return mount(ConversationThread, {
    props: {
      session: session(messages),
      role: 'INTERVIEWER',
      project: previewPublicContent.projects[0],
      pending,
      error,
      focusTarget,
    },
  })
}

describe('ConversationThread', () => {
  it('emits a section evidence inspection instead of a follow-up request', async () => {
    const wrapper = mountThread([answerMessageFixture])
    await wrapper.get('[data-section-evidence]').trigger('click')

    expect(wrapper.emitted('inspectEvidence')).toEqual([[
      {
        messageId: 'agent-1',
        evidenceIds: ['sql-audit-delivery-set'],
        sectionType: 'VERIFICATION',
      },
    ]])
    expect(wrapper.emitted('followUp')).toBeUndefined()
  })

  it('focuses the exact answer section without smooth scrolling under reduced motion', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    const wrapper = mountThread([answerMessageFixture])
    const section = wrapper.get('[data-section-type="VERIFICATION"]')
    const scrollIntoView = vi.fn()
    Object.defineProperty(section.element, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    })

    await wrapper.setProps({
      focusTarget: {
        requestId: 1,
        messageId: 'agent-1',
        sectionType: 'VERIFICATION',
      },
    })
    await flushPromises()

    expect(scrollIntoView).toHaveBeenCalledWith({
      block: 'center',
      behavior: 'auto',
    })
    expect(section.attributes('data-answer-focus')).toBe('true')
  })

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

  it('does not submit Enter while an IME composition is active', async () => {
    const wrapper = mountThread()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('正在组合的中文')
    await textarea.trigger('keydown', { key: 'Enter', isComposing: true })

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect((textarea.element as HTMLTextAreaElement).value).toBe('正在组合的中文')
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

  it('does not auto-scroll when messages update after the reader moves away', async () => {
    const wrapper = mountThread([answerMessageFixture])
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

    await scrollArea.trigger('scroll')
    await wrapper.setProps({
      session: session([
        answerMessageFixture,
        {
          ...answerMessageFixture,
          id: 'agent-2',
          createdAt: 4,
        },
      ]),
    })
    await flushPromises()

    expect(scrollTo).not.toHaveBeenCalled()
    expect(wrapper.find('[data-jump-latest]').exists()).toBe(true)
  })
})
