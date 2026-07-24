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
    suggestedQuestions: [],
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

  it('renders a v2 GENERAL answer with a 通用知识 tag and no evidence citations', () => {
    const wrapper = mountThread([
      {
        id: 'agent-general',
        role: 'AGENT',
        content: 'HTTP 是无状态协议。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'GENERAL_KNOWLEDGE',
          answerScope: 'GENERAL',
          sections: [],
          blocks: [
            {
              sourceScope: 'GENERAL' as const,
              content: 'HTTP 是无状态的应用层协议。',
              claimIds: [],
              evidenceIds: [],
            },
          ],
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('通用知识')
    expect(wrapper.text()).toContain('HTTP 是无状态的应用层协议')
    expect(wrapper.text()).not.toContain('基于作者审核资料生成')
  })

  it('renders a v2 PORTFOLIO answer with 作品集资料 tag and evidence citations', () => {
    const wrapper = mountThread([
      {
        id: 'agent-portfolio',
        role: 'AGENT',
        content: 'SQL 审计项目...',
        createdAt: 2,
        evidenceIds: ['sql-audit-delivery-set'],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'VERIFIED',
          intent: 'PORTFOLIO_GROUNDED',
          answerScope: 'PORTFOLIO',
          sections: [],
          blocks: [
            {
              sourceScope: 'PORTFOLIO' as const,
              content: 'SQL 审计项目交付了完整流水线。',
              claimIds: ['claim-sql-audit-delivered'],
              evidenceIds: ['sql-audit-delivery-set'],
            },
          ],
          evidenceIds: ['sql-audit-delivery-set'],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('作品集资料')
    expect(wrapper.text()).toContain('SQL 审计项目交付了完整流水线。')
    // PORTFOLIO blocks should render claim/evidence reference hooks
    expect(wrapper.find('[data-block-evidence="sql-audit-delivery-set"]').exists()).toBe(true)
    expect(wrapper.find('[data-message-evidence="sql-audit-delivery-set"]').exists()).toBe(false)
  })

  it('renders a v2 HYBRID answer with block-level sourceScope tags split visually', () => {
    const wrapper = mountThread([
      {
        id: 'agent-hybrid',
        role: 'AGENT',
        content: '先讲通用原理，再讲作者实现。',
        createdAt: 2,
        evidenceIds: ['sql-audit-delivery-set'],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'VERIFIED',
          intent: 'HYBRID',
          answerScope: 'HYBRID',
          sections: [],
          blocks: [
            {
              sourceScope: 'GENERAL' as const,
              content: 'RBAC 是常见的访问控制模型。',
              claimIds: [],
              evidenceIds: [],
            },
            {
              sourceScope: 'PORTFOLIO' as const,
              content: '作者在 SQL 审计项目里使用 RBAC 隔离了审计师角色。',
              claimIds: ['claim-sql-audit-delivered'],
              evidenceIds: ['sql-audit-delivery-set'],
            },
          ],
          evidenceIds: ['sql-audit-delivery-set'],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('混合回答')
    // Each block should carry its own scope marker
    const general = wrapper.find('[data-block-scope="GENERAL"]')
    const portfolio = wrapper.find('[data-block-scope="PORTFOLIO"]')
    expect(general.exists()).toBe(true)
    expect(portfolio.exists()).toBe(true)
    expect(general.text()).toContain('RBAC 是常见的访问控制模型')
    expect(portfolio.text()).toContain('作者在 SQL 审计项目里使用 RBAC')
    // 通用知识块不应附作品集引用
    expect(general.find('[data-block-evidence]').exists()).toBe(false)
  })

  it('renders dynamic suggestedQuestions as clickable follow-ups and supports empty array', async () => {
    const wrapper = mountThread([
      {
        id: 'agent-suggested',
        role: 'AGENT',
        content: '答案',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'GENERAL_KNOWLEDGE',
          answerScope: 'GENERAL',
          sections: [],
          blocks: [
            { sourceScope: 'GENERAL' as const, content: '一些通用内容', claimIds: [], evidenceIds: [] },
          ],
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [
            {
              text: '介绍一下 SQL 审计项目',
              projectSlug: 'sql-audit',
              caseSlug: null,
              facet: 'OVERVIEW',
            },
            {
              text: '讲讲作者的 RBAC 实现',
              projectSlug: 'sql-audit',
              caseSlug: 'sql-audit-rbac',
              facet: 'IMPLEMENTATION',
            },
          ],
        },
      },
    ])

    const followups = wrapper.findAll('[data-suggested-follow-up]')
    expect(followups).toHaveLength(2)
    await followups[0].trigger('click')
    expect(wrapper.emitted('submitSuggestion')).toEqual([[
      {
        text: '介绍一下 SQL 审计项目',
        projectSlug: 'sql-audit',
        caseSlug: null,
        facet: 'OVERVIEW',
      },
    ]])
  })

  it('shows a restrained degraded notice when generationMode is FALLBACK', () => {
    const wrapper = mountThread([
      {
        id: 'agent-degraded',
        role: 'AGENT',
        content: '已退回到基础回答。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '预设回答',
          summary: '来自已发布问题的固定回答',
          resolution: 'ANSWERED',
          answerSource: 'PRESET',
          generationMode: 'FALLBACK',
          verification: 'VERIFIED',
          intent: 'PORTFOLIO_GROUNDED',
          answerScope: 'PORTFOLIO',
          sections: [],
          blocks: [],
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('已切换到基础回答')
    // 不应伪装成 MODEL
    expect(wrapper.text()).not.toContain('MODEL')
  })

  it('labels TIME_SENSITIVE as needing real-time information without inventing freshness', () => {
    const wrapper = mountThread([
      {
        id: 'agent-time',
        role: 'AGENT',
        content: '我无法确认最新版本。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'BOUNDARY',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'TIME_SENSITIVE',
          answerScope: 'GENERAL',
          sections: [],
          blocks: [
            { sourceScope: 'GENERAL' as const, content: '我目前无法访问实时网络信息。', claimIds: [], evidenceIds: [] },
          ],
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('暂时不可用')
    expect(wrapper.text()).toContain('无法访问实时网络信息')
  })

  it('labels UNSUPPORTED_OR_UNSAFE as a refusal without pretending to be a normal answer', () => {
    const wrapper = mountThread([
      {
        id: 'agent-rejected',
        role: 'AGENT',
        content: '我不会协助这个请求。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'REJECTED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'UNSUPPORTED_OR_UNSAFE',
          answerScope: 'CONVERSATION',
          sections: [],
          blocks: [
            { sourceScope: 'GENERAL' as const, content: '这个请求涉及私密信息，无法处理。', claimIds: [], evidenceIds: [] },
          ],
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('无法处理该请求')
    expect(wrapper.text()).not.toContain('已核验回答')
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
