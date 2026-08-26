import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  loadPublicAgentTurnGoldenFixtures,
} from '../../agent/model/publicAgentTurnFixtureLoader'
import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { consumeAgentHandoff } from '../../agent/model/handoffStore'
import AudienceDialogue from './AudienceDialogue.vue'

const { submitAgentTurnMock, handoffRecorder } = vi.hoisted(() => ({
  submitAgentTurnMock: vi.fn(),
  handoffRecorder: vi.fn(),
}))

vi.mock('../../agent/api/agentTurnApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../agent/api/agentTurnApi')>()
  return {
    ...original,
    submitAgentTurn: submitAgentTurnMock,
  }
})

vi.mock('../../agent/model/handoffStore', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../agent/model/handoffStore')>()
  return {
    ...original,
    createAgentHandoff: (seed: Parameters<typeof original.createAgentHandoff>[0]) =>
      handoffRecorder(seed) as string,
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
    expect(wrapper.text()).toContain('ANSWERED 00')

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(submitAgentTurnMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('ANSWERED 01')
  })

  it('Preset 失败重试原样复用同一 requestId 与 PRESET 身份，不退化为 FREE_TEXT（A2-72/A2-73）', async () => {
    submitAgentTurnMock
      .mockResolvedValueOnce({
        ok: false,
        failure: { kind: 'TIMEOUT', message: '等待超时', retryable: true },
      })
      .mockResolvedValueOnce(submitSuccess())
    const wrapper = mountDialogue()

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Agent 暂时无法回答')
    const firstCall = JSON.parse(JSON.stringify(submitAgentTurnMock.mock.calls[0]?.[0]))

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(submitAgentTurnMock).toHaveBeenCalledTimes(2)
    expect(submitAgentTurnMock.mock.calls[1]?.[0]).toEqual(firstCall)
    expect((submitAgentTurnMock.mock.calls[1]?.[0] as { command: { input: { kind: string } } }).command.input.kind).toBe('PRESET')
  })

  it('pending 期间角色按钮真实禁用；答案与 handoff 使用提交时冻结的角色快照（行为基础 Task 5）', async () => {
    handoffRecorder.mockClear()
    let resolveAnswer!: (value: ReturnType<typeof submitSuccess>) => void
    submitAgentTurnMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountDialogue()

    await wrapper.get('[data-role="MENTOR"]').trigger('click')
    await wrapper.get('[data-custom-question]').setValue('以导师视角提问')
    await wrapper.get('[data-question-form]').trigger('submit')

    // pending：四个角色按钮均带真实 disabled 属性，尝试点击 HR 不改变选择。
    const roleButtons = wrapper.findAll('.role-grid button')
    expect(roleButtons).toHaveLength(4)
    for (const button of roleButtons) {
      expect(button.attributes('disabled')).toBeDefined()
    }
    await wrapper.get('[data-role="HR"]').trigger('click')
    expect(wrapper.get('[data-current-role]').attributes('data-current-role')).toBe('MENTOR')

    resolveAnswer(submitSuccess())
    await flushPromises()

    // 答案面板与 handoff 一律使用提交时冻结的角色快照，不读响应式选择值。
    expect(wrapper.get('[data-light-answer] aside b').text()).toBe('MENTOR')
    expect(handoffRecorder).toHaveBeenCalledTimes(1)
    const seed = handoffRecorder.mock.calls[0]?.[0] as {
      role: string
      replay?: { surfaceContext?: { audienceRole?: string } }
    }
    expect(seed.role).toBe('MENTOR')
    expect(seed.replay?.surfaceContext?.audienceRole).toBe('MENTOR')
    wrapper.unmount()
  })
})

describe('AudienceDialogue（模型目录默认选择，UI spec §2.7/§8.3）', () => {
  beforeEach(() => {
    submitAgentTurnMock.mockReset()
    submitAgentTurnMock.mockResolvedValue(submitSuccess())
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })),
    )
  })

  it('首页轮次携带目录默认 ModelSelection；回答面板显示默认模型徽标', async () => {
    const wrapper = mountDialogue()

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()

    const request = submitAgentTurnMock.mock.calls[0]?.[0] as {
      modelSelection: { kind: string; modelRef: string; selectionVersion: string }
    }
    expect(request.modelSelection).toEqual({
      kind: 'MODEL',
      modelRef: 'glm-4-7-flash',
      selectionVersion: 'glm-4-7-flash-v4',
    })
    const badge = wrapper.get('[data-testid="light-answer-model"]')
    expect(badge.text()).toContain('由 GLM-4.7-Flash · 目录默认 生成')
    expect(badge.text()).toContain('首页不提供切换，进入 Agent 页后可选')
    wrapper.unmount()
  })

  it('handoff 种子不携带模型字段：Agent 页按目录默认初始化（D-MS-6/§2.7）', async () => {
    const recorded: Array<Record<string, unknown>> = []
    handoffRecorder.mockImplementation((seed) => {
      recorded.push(seed as unknown as Record<string, unknown>)
      return 'handoff-id-test'
    })
    const wrapper = mountDialogue()

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()

    expect(recorded).toHaveLength(1)
    const seed = recorded[0]!
    expect(seed.source).toBe('HOME')
    expect(seed.replay).toBeDefined()
    expect(JSON.stringify(seed)).not.toMatch(/modelSelection|modelRef|selectionVersion/i)
    wrapper.unmount()
  })

  it('默认未就绪（NONE）且目录非空：自由文本输入被禁用，预设仍可提交 NONE 选择（设计 §8）', async () => {
    submitAgentTurnMock.mockResolvedValue(submitSuccess())
    const wrapper = mount(AudienceDialogue, {
      props: {
        portfolio: {
          ...previewPublicContent,
          agentAvailability: {
            ...previewPublicContent.agentAvailability,
            defaultModelSelection: { kind: 'NONE' },
          },
        },
      },
      global: {
        stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } },
      },
    })

    expect(wrapper.get('[data-custom-question]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('目录默认模型暂未就绪')

    await wrapper.get('[data-question]').trigger('click')
    await flushPromises()
    const request = submitAgentTurnMock.mock.calls[0]?.[0] as {
      modelSelection: { kind: string }
    }
    expect(request?.modelSelection).toEqual({ kind: 'NONE' })
    wrapper.unmount()
  })
})
