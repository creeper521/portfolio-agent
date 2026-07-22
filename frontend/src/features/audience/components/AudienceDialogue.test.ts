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
    turnId: 'turn-1',
    contentVersion: '2026-07-21',
    questionPresetId: 'sql-audit-overview',
    resolution: 'ANSWERED' as const,
    answerSource: 'PRESET' as const,
    generationMode: 'DETERMINISTIC' as const,
    verification: 'VERIFIED' as const,
    title: '项目说明',
    summary: '公开摘要',
    sections: [
      { type: 'BACKGROUND' as const, title: '背景', content: '背景内容', evidenceIds: ['sql-audit-delivery-set'] },
      { type: 'VERIFICATION' as const, title: '验证', content: '验证内容', evidenceIds: ['sql-audit-delivery-set'] },
    ],
    evidenceIds: ['sql-audit-delivery-set'],
    suggestedQuestionPresetIds: ['sql-audit-overview'],
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
    expect(wrapper.findAll('[data-question]')).toHaveLength(1)
  })

  it('shows an API-backed summary after choosing a question', async () => {
    const wrapper = mountDialogue()

    expect(wrapper.findAll('[data-question]')).toHaveLength(1)
    expect(wrapper.get('[data-role="INTERVIEWER"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-role="INTERVIEWER"]').classes()).toContain('role-button--active')

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledWith(expect.objectContaining({
      projectSlug: 'sql-audit',
      questionPresetId: 'sql-audit-overview',
      source: 'HOME',
    }))
    expect(wrapper.get('[data-light-answer]').text()).toContain('项目说明')
    expect(wrapper.get('[data-light-answer]').text()).toContain('E-01')
    expect(wrapper.findAll('[data-answer-action]')).toHaveLength(3)
  })

  it('accepts a free-form homepage question', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('如何处理连接异常？')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledWith(expect.objectContaining({
      projectSlug: 'sql-audit',
      question: '如何处理连接异常？',
      source: 'HOME',
    }))
    expect(wrapper.get('[data-light-answer]').text()).toContain('项目说明')
  })

  it('labels retrieval independently from partial verification', async () => {
    askQuestionMock.mockResolvedValue({
      ...answerResponse(),
      questionPresetId: undefined,
      answerSource: 'RETRIEVAL' as const,
      verification: 'PARTIALLY_VERIFIED' as const,
    })
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('检索公开交付结果')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    const panel = wrapper.get('[data-light-answer]').text()
    expect(panel).toContain('RETRIEVAL · 来自公开资料检索')
    expect(panel).toContain('部分事实已核验')
    expect(panel).not.toContain('已核验回答')
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
    expect(askQuestionMock).toHaveBeenNthCalledWith(1, expect.objectContaining({ question: '失败后重试的问题' }))
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ question: '失败后重试的问题' }))
    expect(wrapper.text()).toContain('ROUND 01 / 03')
  })
})
