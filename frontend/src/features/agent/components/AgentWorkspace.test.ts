import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { SESSION_KEY } from '../composables/useLocalSessions'
import AgentWorkspace from './AgentWorkspace.vue'

const { askQuestionMock } = vi.hoisted(() => ({
  askQuestionMock: vi.fn(),
}))

vi.mock('../api/answerApi', () => ({
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

function mountWorkspace() {
  return mount(AgentWorkspace, {
    props: { portfolio: previewPublicContent },
    global: {
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

function storedMessages() {
  const sessions = JSON.parse(localStorage.getItem(SESSION_KEY) ?? '[]')
  return sessions[0]?.messages ?? []
}

describe('AgentWorkspace', () => {
  beforeEach(() => {
    localStorage.clear()
    askQuestionMock.mockReset()
    askQuestionMock.mockResolvedValue(answerResponse())
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
    const wrapper = mountWorkspace()

    expect(wrapper.text()).toContain('本地会话')
    expect(wrapper.text()).toContain('Agent 对话')
    expect(wrapper.text()).toContain('证据工作台')
    expect(wrapper.findAll('[role="separator"]')).toHaveLength(2)
    expect(wrapper.get('.agent-workspace').classes()).toContain('agent-workspace--prototype')
    expect(wrapper.find('.thread-empty-card').exists()).toBe(false)
    expect(wrapper.find('.message--user-card').exists()).toBe(false)
  })

  it('moves a separator by 16px and resets it with Home', async () => {
    const wrapper = mountWorkspace()
    const handle = wrapper.get('[aria-label="调整历史会话宽度"]')
    const before = Number(handle.attributes('aria-valuenow'))

    await handle.trigger('keydown', { key: 'ArrowRight' })
    expect(Number(handle.attributes('aria-valuenow'))).toBe(before + 16)

    await handle.trigger('keydown', { key: 'Home' })
    expect(Number(handle.attributes('aria-valuenow'))).not.toBe(before + 16)
  })

  it('exposes drawer state and closes an open drawer with Escape', async () => {
    const wrapper = mountWorkspace()
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
    const wrapper = mountWorkspace()

    await wrapper.get('[aria-label^="删除会话："]').trigger('click')

    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
    expect(wrapper.text()).toContain('从一个可核验的问题开始。')
  })

  it('shows the user message immediately and persists the Agent message only after API success', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('如何验证结果？')
    await wrapper.get('.composer').trigger('submit')

    expect(askQuestionMock).toHaveBeenCalledWith('sql-audit', '如何验证结果？')
    expect(wrapper.get('.message--user').text()).toContain('如何验证结果？')
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-agent-submit]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[role="status"]').text()).toContain('正在核对公开事实')
    expect(storedMessages()).toHaveLength(1)
    expect(storedMessages()[0].role).toBe('USER')

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(wrapper.get('.message--agent').text()).toContain('项目说明')
    expect(storedMessages()).toHaveLength(2)
    expect(storedMessages()[1]).toMatchObject({
      role: 'AGENT',
      content: '项目说明\n\n背景内容\n\n验证内容',
      evidenceIds: ['sql-audit-delivery-set'],
    })
  })

  it('retries a failed answer without duplicating the persisted user message', async () => {
    askQuestionMock
      .mockRejectedValueOnce(new Error('POST https://internal.example/api failed'))
      .mockResolvedValueOnce(answerResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('失败后重试的问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Agent 暂时无法回答，请稍后重试')
    expect(wrapper.text()).not.toContain('internal.example')
    expect(storedMessages()).toHaveLength(1)

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(1, 'sql-audit', '失败后重试的问题')
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, 'sql-audit', '失败后重试的问题')
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(1)
    expect(storedMessages()).toHaveLength(2)
  })
})
