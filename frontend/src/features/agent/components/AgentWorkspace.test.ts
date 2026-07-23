import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { WORKSPACE_SPLIT_KEY } from '../composables/useWorkspaceSplit'
import AgentWorkspace from './AgentWorkspace.vue'

const SESSION_KEY = 'forbidden-session-key'

const { askQuestionMock } = vi.hoisted(() => ({
  askQuestionMock: vi.fn(),
}))

vi.mock('../api/answerApi', () => ({
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
      { type: 'BACKGROUND' as const, title: '背景', content: '背景内容', evidenceIds: ['sql-audit-delivery-set'], claimIds: ['claim-sql-audit-delivered'] },
      { type: 'VERIFICATION' as const, title: '验证', content: '验证内容', evidenceIds: ['sql-audit-delivery-set'], claimIds: ['claim-sql-audit-delivered'] },
    ],
    evidenceIds: ['sql-audit-delivery-set'],
    suggestedQuestionPresetIds: ['sql-audit-overview'],
    contextEnvelope: {
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
    },
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
  return storedSessions()[0]?.messages ?? []
}

function storedSessions() {
  return JSON.parse(localStorage.getItem(SESSION_KEY) ?? '[]')
}

describe('AgentWorkspace', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    localStorage.clear()
    askQuestionMock.mockReset()
    askQuestionMock.mockResolvedValue(answerResponse())
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query.includes('1279'),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
  })

  it('renders sessions, conversation, evidence desk, and two accessible separators', () => {
    const wrapper = mountWorkspace()

    expect(wrapper.text()).toContain('会话仅保留在当前标签页')
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

  it('fits max persisted widths to a 1280px viewport shell without rewriting the preference', async () => {
    localStorage.setItem(
      WORKSPACE_SPLIT_KEY,
      JSON.stringify({ sessions: 320, evidence: 420 }),
    )
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(1248)
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )

    const wrapper = mountWorkspace()
    await wrapper.vm.$nextTick()

    const workspaceStyle = wrapper.get('.agent-workspace').attributes('style')
    expect(workspaceStyle).toContain('--sessions-width: 260px')
    expect(workspaceStyle).toContain('--evidence-width: 348px')
    expect(
      wrapper.get('[aria-label="调整历史会话宽度"]').attributes('aria-valuenow'),
    ).toBe('260')
    expect(
      wrapper.get('[aria-label="调整证据工作台宽度"]').attributes('aria-valuenow'),
    ).toBe('348')
    expect(JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? '{}')).toEqual({
      sessions: 320,
      evidence: 420,
    })
  })

  it('exposes drawer state and closes an open drawer with Escape', async () => {
    const matchMedia = vi.mocked(window.matchMedia)
    const wrapper = mountWorkspace()
    const toggle = wrapper.get('.evidence-toggle')

    expect(matchMedia).toHaveBeenCalledWith('(max-width: 1279.98px)')
    expect(matchMedia).toHaveBeenCalledWith('(max-width: 959.98px)')
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

    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-remove]').trigger('click')

    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
    expect(wrapper.text()).toContain('从一个可核验的问题开始。')
  })

  it('shows the user immediately and appends a structured Agent answer only after API success', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('如何验证结果？')
    await wrapper.get('.composer').trigger('submit')

    expect(askQuestionMock).toHaveBeenCalledWith(expect.objectContaining({
      projectSlug: 'sql-audit',
      question: '如何验证结果？',
      source: 'AGENT_PAGE',
    }))
    expect(wrapper.get('.message--user').text()).toContain('如何验证结果？')
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-agent-submit]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[role="status"]').text()).toContain('正在核对公开事实')
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(0)
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(wrapper.get('.message--agent').text()).toContain('项目说明')
    expect(wrapper.get('.message--agent').text()).toContain('背景内容')
    expect(wrapper.get('.message--agent').text()).toContain('已核验回答')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('shows retrieval provenance without turning a boundary into an applicable source', async () => {
    askQuestionMock
      .mockResolvedValueOnce({
        ...answerResponse(),
        questionPresetId: undefined,
        answerSource: 'RETRIEVAL' as const,
        verification: 'PARTIALLY_VERIFIED' as const,
      })
      .mockResolvedValueOnce({
        ...answerResponse(),
        questionPresetId: undefined,
        resolution: 'BOUNDARY' as const,
        answerSource: undefined,
        verification: 'NOT_APPLICABLE' as const,
      })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('公开检索问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.findAll('.message--agent')[0].text())
      .toContain('RETRIEVAL · 来自公开资料检索')

    await wrapper.get('textarea').setValue('越界问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    const boundary = wrapper.findAll('.message--agent')[1].text()
    expect(boundary).toContain('当前能力边界')
    expect(boundary).not.toContain('来自公开资料检索')
    expect(boundary).not.toContain('PRESET')
    expect(boundary).not.toContain('RETRIEVAL')
  })

  it('sends only stable page-memory references after an explicit follow-up action', async () => {
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('公开检索问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    await wrapper.get('[data-follow-up="current-status"]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
      question: '查看当前状态',
      questionPresetId: undefined,
      contextEnvelope: {
        previousContentVersion: '2026-07-21',
        projectSlugs: ['sql-audit'],
        questionPresetId: 'sql-audit-overview',
        referencedClaimIds: ['claim-sql-audit-delivered'],
        selectedSectionType: undefined,
        followUpIntent: 'CURRENT_STATUS',
      },
    }))
    expect(JSON.stringify(askQuestionMock.mock.calls[1]?.[0])).not.toContain('公开摘要')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('announces when stable references were revalidated against a newer content version', async () => {
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      contextVersionUpdated: true,
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('查看更新后的状态')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-context-version-updated]').text())
      .toContain('公开内容已更新，本轮已按当前版本重新核对')
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
    expect(wrapper.findAll('.message--user')).toHaveLength(1)

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(1, expect.objectContaining({ question: '失败后重试的问题' }))
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ question: '失败后重试的问题' }))
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(1)
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('retries against the failed session after the user switches sessions', async () => {
    askQuestionMock
      .mockRejectedValueOnce(new Error('first request failed'))
      .mockResolvedValueOnce(answerResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('textarea').setValue('保留原会话上下文')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    await wrapper.findAll('.session-select')[1].trigger('click')
    expect(wrapper.get('.session-list article.active .session-select').text()).not.toContain(
      '保留原会话上下文',
    )

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.message')).toHaveLength(0)
    await wrapper.findAll('.session-select')[0].trigger('click')
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(1)
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ question: '保留原会话上下文' }))
  })

  it('clears retry safely when the failed session is deleted', async () => {
    askQuestionMock.mockRejectedValueOnce(new Error('first request failed'))
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('删除失败会话')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-answer-retry]').exists()).toBe(true)

    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-remove]').trigger('click')

    expect(wrapper.find('[data-answer-retry]').exists()).toBe(false)
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
    expect(wrapper.findAll('.message')).toHaveLength(0)
  })

  it('does not persist a late resolved answer after unmount', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('卸载后成功')
    await wrapper.get('.composer').trigger('submit')
    const storedBeforeUnmount = localStorage.getItem(SESSION_KEY)
    wrapper.unmount()

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(localStorage.getItem(SESSION_KEY)).toBe(storedBeforeUnmount)
  })

  it('does not overwrite state when a request rejects after unmount', async () => {
    let rejectAnswer!: (reason: Error) => void
    askQuestionMock.mockReturnValue(
      new Promise((_, reject) => {
        rejectAnswer = reject
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('卸载后失败')
    await wrapper.get('.composer').trigger('submit')
    const storedBeforeUnmount = localStorage.getItem(SESSION_KEY)
    wrapper.unmount()

    rejectAnswer(new Error('POST https://internal.example/api failed'))
    await flushPromises()

    expect(localStorage.getItem(SESSION_KEY)).toBe(storedBeforeUnmount)
  })
})
