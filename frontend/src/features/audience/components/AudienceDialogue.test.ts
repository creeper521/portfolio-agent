import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  loadPublicAgentTurnGoldenFixtures,
} from '../../agent/model/publicAgentTurnFixtureLoader'
import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import AudienceDialogue from './AudienceDialogue.vue'

const { submitAgentTurnMock } = vi.hoisted(() => ({
  submitAgentTurnMock: vi.fn(),
}))

vi.mock('../../agent/api/agentTurnApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../agent/api/agentTurnApi')>()
  return {
    ...original,
    submitAgentTurn: submitAgentTurnMock,
  }
})

function goldenTurn(fileName: string) {
  const fixture = loadPublicAgentTurnGoldenFixtures().find(
    (candidate) => candidate.fileName === fileName,
  )
  if (fixture === undefined) throw new Error(`缺少 fixture ${fileName}`)
  return JSON.parse(JSON.stringify(fixture.turn)) as Record<string, unknown>
}

function submitSuccess() {
  return {
    ok: true,
    turn: goldenTurn('answer-complete.json'),
    conversation: {
      conversationId: 'conversation-home', resumeToken: 'token-home',
      discussionRevision: 0,
    },
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
    submitAgentTurnMock.mockReset()
    submitAgentTurnMock.mockResolvedValue(submitSuccess())
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })),
    )
  })

  it('changes recommended questions with the selected visitor role', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-role="MENTOR"]').trigger('click')

    expect(wrapper.get('[data-current-role]').attributes('data-current-role')).toBe('MENTOR')
    expect(wrapper.findAll('[data-question]')).toHaveLength(1)
  })

  it('asks via the closed preset command and renders the light PublicAgentTurn projection', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()

    expect(submitAgentTurnMock).toHaveBeenCalledTimes(1)
    const request = submitAgentTurnMock.mock.calls[0]?.[0] as {
      requestId: string
      command: { kind: string; input: { kind: string; presetId?: string } }
      surfaceContext: { subjectHint: { slug: string }; requestSource: string }
      conversationWindow: unknown[]
    }
    expect(request.command).toEqual({
      kind: 'ASK',
      input: {
        kind: 'PRESET',
        presetId: previewPublicContent.questionPresets[0].id,
        presetRevision: previewPublicContent.questionPresets[0].contractVersion,
      },
    })
    expect(request.surfaceContext).toMatchObject({
      subjectHint: { kind: 'PROJECT', slug: 'sql-audit' },
      requestSource: 'HOME',
    })
    expect(request.conversationWindow).toEqual([])

    const panel = wrapper.get('[data-light-answer]').text()
    expect(panel).toContain('介绍 SQL 审计项目')
    expect(panel).toContain('E-01')
    expect(wrapper.findAll('[data-answer-action]')).toHaveLength(3)
  })

  it('accepts a free-form homepage question via ASK/FREE_TEXT', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('如何处理连接异常？')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    const request = submitAgentTurnMock.mock.calls[0]?.[0] as {
      command: { input: { kind: string; text: string } }
    }
    expect(request.command).toEqual({
      kind: 'ASK',
      input: { kind: 'FREE_TEXT', text: '如何处理连接异常？' },
    })
    expect(wrapper.get('[data-light-answer]').text()).toContain('介绍 SQL 审计项目')
  })

  it('renders boundary turns without evidence and with a safe status label', async () => {
    submitAgentTurnMock.mockResolvedValue({
      ok: true,
      turn: goldenTurn('boundary.json'),
      conversation: null,
    })
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('帮我做医疗决策')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    const panel = wrapper.get('[data-light-answer]').text()
    expect(panel).toContain('能力边界')
    expect(panel).toContain('高风险建议')
    expect(panel).not.toContain('VERIFIED_PUBLIC_EVIDENCE')
  })

  it('disables question input and submission while an answer is pending', async () => {
    let resolveAnswer!: (value: ReturnType<typeof submitSuccess>) => void
    submitAgentTurnMock.mockReturnValue(
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

    resolveAnswer(submitSuccess())
    await flushPromises()
    expect(wrapper.get('[data-custom-question]').attributes('disabled')).toBeUndefined()
  })

  it('shows a fixed safe error on failure and retries the same question once succeeded', async () => {
    submitAgentTurnMock
      .mockResolvedValueOnce({
        ok: false,
        failure: { kind: 'NETWORK', message: '网络不可用，请稍后重试', retryable: true },
      })
      .mockResolvedValueOnce(submitSuccess())
    const wrapper = mountDialogue()

    await wrapper.get('[data-custom-question]').setValue('失败后重试的问题')
    await wrapper.get('[data-question-form]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Agent 暂时无法回答，请稍后重试')
    expect(wrapper.text()).not.toContain('internal.example')
    expect(wrapper.text()).toContain('ROUND 00 / 03')

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(submitAgentTurnMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('ROUND 01 / 03')
  })
})
