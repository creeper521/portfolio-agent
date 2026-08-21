import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import {
  clearAgentHandoffsForTest,
  createAgentHandoff,
  createCaseAgentHandoff,
} from '../features/agent/model/handoffStore'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import AgentPage from './AgentPage.vue'

const SESSION_KEY = 'forbidden-session-key'

const { submitAgentTurnMock } = vi.hoisted(() => ({
  submitAgentTurnMock: vi.fn(),
}))

vi.mock('../features/agent/api/agentTurnApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/agent/api/agentTurnApi')>()
  return {
    ...original,
    submitAgentTurn: submitAgentTurnMock,
    fetchCurrentConversation: vi.fn().mockResolvedValue({ ok: false, invalid: false }),
  }
})

async function mountAgentPage(
  state = readyPublicContentState(),
  location = '/agent',
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent', component: AgentPage }],
  })
  await router.push(location)
  await router.isReady()

  const wrapper = mount(AgentPage, {
    global: {
      plugins: [router],
      provide: { [publicContentStateKey as symbol]: state },
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  return { wrapper, router }
}

describe('AgentPage', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    clearAgentHandoffsForTest()
    submitAgentTurnMock.mockReset()
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query.includes('1219'),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
  })

  it('mounts the Agent workspace once public content is ready without auto-asking', async () => {
    const { wrapper } = await mountAgentPage()
    await flushPromises()

    expect(submitAgentTurnMock).not.toHaveBeenCalled()
    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
  })

  it('does not mount the Agent workspace before public content is ready', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const { wrapper } = await mountAgentPage(state)

    expect(submitAgentTurnMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('正在装订公开档案')
    expect(wrapper.find('.agent-workspace').exists()).toBe(false)
  })

  it('keeps the public-content error state inside the Agent shell', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'error'
    state.error.value = '公开内容加载失败'
    const { wrapper } = await mountAgentPage(state)

    expect(wrapper.get('[role="alert"]').text()).toContain('公开内容加载失败')
  })

  it('content-only 部署显示中性不可用提示且不挂载提交界面', async () => {
    const state = readyPublicContentState()
    if (state.portfolio.value !== null) {
      state.portfolio.value = {
        ...state.portfolio.value,
        agentAvailability: { status: 'UNAVAILABLE', freeTextSemanticRouting: 'DISABLED' },
      }
    }
    const { wrapper } = await mountAgentPage(state)

    expect(wrapper.get('[data-testid="agent-unavailable"]').text()).toContain('仅提供作品集浏览')
    expect(wrapper.find('[data-testid="question-input"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="submit-question"]').exists()).toBe(false)
  })

  it('consumes a homepage handoff once, replays the turn, and invalidates reuse', async () => {
    submitAgentTurnMock.mockResolvedValue({
      ok: true,
      turn: {
        requestId: '10000000-0000-4000-8000-000000000001',
        kind: 'CONVERSATIONAL',
        message: '你好，我可以介绍公开项目、案例和工程取舍。',
        suggestedActions: [],
      },
      conversation: { conversationId: 'conversation-1', discussionRevision: 0 },
    })
    const handoffId = createAgentHandoff({
      role: 'INTERVIEWER',
      question: '如何验证结果？',
      projectSlug: 'sql-audit',
      source: 'HOME',
      replay: {
        requestId: '10000000-0000-4000-8000-000000000009',
        command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '如何验证结果？' } },
      },
    })
    const { wrapper, router } = await mountAgentPage(
      readyPublicContentState(), `/agent?handoffId=${handoffId}`,
    )
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/agent')
    expect(submitAgentTurnMock).toHaveBeenCalledTimes(1)
    const request = submitAgentTurnMock.mock.calls[0]?.[0] as { requestId: string }
    expect(request.requestId).toBe('10000000-0000-4000-8000-000000000009')
    expect(wrapper.get('[data-message-role="USER"]').text()).toBe('如何验证结果？')
    expect(wrapper.get('[data-testid="conversational-turn"]').text()).toContain('你好')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    wrapper.unmount()
    const consumed = await mountAgentPage(
      readyPublicContentState(), `/agent?handoffId=${handoffId}`,
    )
    await flushPromises()
    expect(consumed.wrapper.find('[data-invalid-handoff]').exists()).toBe(true)
  })

  it('consumes a Case handoff once and prefills the composer without submitting', async () => {
    const handoffId = createCaseAgentHandoff({
      caseSlug: 'multilingual-image-preservation',
      question: '这个案例如何验证？',
    })
    const { wrapper, router } = await mountAgentPage(
      readyPublicContentState(),
      `/agent?caseHandoffId=${handoffId}`,
    )
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/agent')
    expect(submitAgentTurnMock).not.toHaveBeenCalled()
    expect((wrapper.get('[data-testid="question-input"]').element as HTMLTextAreaElement).value)
      .toBe('这个案例如何验证？')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    wrapper.unmount()
    const consumed = await mountAgentPage(
      readyPublicContentState(),
      `/agent?caseHandoffId=${handoffId}`,
    )
    await flushPromises()
    expect(consumed.wrapper.find('[data-invalid-handoff]').exists()).toBe(true)
  })

  it('drops legacy question query params without submitting or retaining them', async () => {
    const { wrapper, router } = await mountAgentPage(
      readyPublicContentState(), '/agent?question=不得进入历史的问题&project=sql-audit',
    )
    await flushPromises()

    expect(submitAgentTurnMock).not.toHaveBeenCalled()
    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('不得进入历史的问题')
    expect(router.currentRoute.value.fullPath).toBe('/agent')
  })
})
