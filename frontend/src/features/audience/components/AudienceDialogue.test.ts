import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import AudienceDialogue from './AudienceDialogue.vue'

const { askQuestionMock } = vi.hoisted(() => ({
  askQuestionMock: vi.fn(),
}))

vi.mock('../../agent/api/answerApi', () => ({
  askQuestion: askQuestionMock,
}))

function answerResponse() {
  return {
    requestId: 'request-1',
    answerMode: 'DETERMINISTIC' as const,
    matched: true,
    fallback: false,
    answer: {
      title: '项目说明',
      sections: [
        { type: 'BACKGROUND' as const, content: '背景内容' },
        { type: 'VERIFICATION' as const, content: '验证内容' },
      ],
    },
    evidence: [
      {
        id: 'sql-audit-delivery-set',
        title: '证据',
        type: 'COLLECTION' as const,
        periodStart: '2026-07-01',
        periodEnd: '2026-07-10',
        sourceCount: 1,
        summary: '摘要',
        supportedClaims: ['已验证'],
        publicStatus: 'APPROVED' as const,
        rawContentPublic: false as const,
      },
    ],
    suggestedQuestions: [],
  }
}

function mountDialogue() {
  return mount(AudienceDialogue, {
    props: { portfolio: previewPublicContent },
    global: {
      stubs: {
        RouterLink: { props: ['to'], template: '<a><slot /></a>' },
      },
    },
  })
}

describe('AudienceDialogue', () => {
  beforeEach(() => {
    askQuestionMock.mockReset()
    askQuestionMock.mockResolvedValue(answerResponse())
  })

  it('changes recommended questions with the selected visitor role', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-role="MENTOR"]').trigger('click')

    expect(wrapper.get('[data-current-role]').attributes('data-current-role')).toBe('MENTOR')
    expect(wrapper.findAll('[data-question]')).toHaveLength(4)
  })

  it('shows an API-backed summary after choosing a question', async () => {
    const wrapper = mountDialogue()

    expect(wrapper.findAll('[data-question]')).toHaveLength(4)
    expect(wrapper.get('[data-role="INTERVIEWER"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-role="INTERVIEWER"]').classes()).toContain('role-button--active')

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledWith(
      'sql-audit',
      wrapper.get('[data-question] span').text(),
    )
    expect(wrapper.get('[data-light-answer]').text()).toContain('项目说明')
    expect(wrapper.get('[data-light-answer]').text()).toContain('E-01')
    expect(wrapper.findAll('[data-answer-action]')).toHaveLength(3)
  })

  it('accepts a free-form homepage question', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('如何处理连接异常？')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledWith('sql-audit', '如何处理连接异常？')
    expect(wrapper.get('[data-light-answer]').text()).toContain('项目说明')
  })

  it('disables question input and submission while an answer is pending', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('正在核对的问题')
    await wrapper.get('[data-question-form]').trigger('submit')

    expect(wrapper.get('[data-custom-question]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-question-submit]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-question]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[role="status"]').text()).toContain('正在核对公开事实')

    resolveAnswer(answerResponse())
    await flushPromises()
    expect(wrapper.get('[data-custom-question]').attributes('disabled')).toBeUndefined()
  })

  it('shows a fixed safe error and retries the same question without double incrementing round', async () => {
    askQuestionMock
      .mockRejectedValueOnce(new Error('POST https://internal.example/api failed'))
      .mockResolvedValueOnce(answerResponse())
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('失败后重试的问题')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Agent 暂时无法回答，请稍后重试')
    expect(wrapper.text()).not.toContain('internal.example')
    expect(wrapper.text()).toContain('ROUND 00 / 03')

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(1, 'sql-audit', '失败后重试的问题')
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, 'sql-audit', '失败后重试的问题')
    expect(wrapper.text()).toContain('ROUND 01 / 03')
  })
})
