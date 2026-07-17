import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { SESSION_KEY } from '../features/agent/composables/useLocalSessions'
import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import AgentPage from './AgentPage.vue'

const { askQuestionMock } = vi.hoisted(() => ({
  askQuestionMock: vi.fn(),
}))

vi.mock('../features/agent/api/answerApi', () => ({
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

  it('mounts the Agent workspace once public content is ready and no seed question exists', async () => {
    const { wrapper } = await mountAgentPage()
    await flushPromises()

    expect(askQuestionMock).not.toHaveBeenCalled()
    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
  })

  it('does not mount the Agent workspace before public content is ready', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const { wrapper } = await mountAgentPage(state)

    expect(askQuestionMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('正在装订公开档案')
    expect(wrapper.find('.agent-workspace').exists()).toBe(false)
  })

  it('waits for the route seed API answer before mounting the workspace', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const { wrapper } = await mountAgentPage(
      readyPublicContentState(),
      '/agent?question=如何验证结果？&project=sql-audit&source=HOME',
    )

    expect(askQuestionMock).toHaveBeenCalledWith('sql-audit', '如何验证结果？')
    expect(wrapper.find('.agent-workspace').exists()).toBe(false)
    expect(wrapper.get('[role="status"]').text()).toContain('正在核对公开事实')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.get('.message--user').text()).toContain('如何验证结果？')
    expect(wrapper.get('.message--agent').text()).toContain('项目说明')
  })

  it('retries a route seed safely without changing URL or creating a session before success', async () => {
    askQuestionMock
      .mockRejectedValueOnce(new Error('POST https://internal.example/api failed'))
      .mockResolvedValueOnce(answerResponse())
    const location =
      '/agent?question=失败后重试的问题&project=sql-audit&source=PROJECT&role=MENTOR'
    const { wrapper, router } = await mountAgentPage(readyPublicContentState(), location)
    const originalUrl = router.currentRoute.value.fullPath
    await flushPromises()

    expect(wrapper.find('.agent-workspace').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('Agent 暂时无法回答，请稍后重试')
    expect(wrapper.text()).not.toContain('internal.example')
    expect(router.currentRoute.value.fullPath).toBe(originalUrl)
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(1, 'sql-audit', '失败后重试的问题')
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, 'sql-audit', '失败后重试的问题')
    expect(router.currentRoute.value.fullPath).toBe(originalUrl)
    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    const sessions = JSON.parse(localStorage.getItem(SESSION_KEY) ?? '[]')
    expect(sessions).toHaveLength(1)
    expect(sessions[0].role).toBe('MENTOR')
    expect(sessions[0].messages).toHaveLength(2)
  })

  it('invalidates a pending route seed when the question is removed', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const { wrapper, router } = await mountAgentPage(
      readyPublicContentState(),
      '/agent?question=即将移除的问题&project=sql-audit',
    )

    await router.push('/agent?project=sql-audit')
    await flushPromises()
    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(
      (wrapper.vm as unknown as { initialSeed: unknown }).initialSeed,
    ).toBeNull()

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(
      (wrapper.vm as unknown as { initialSeed: unknown }).initialSeed,
    ).toBeNull()
    expect(wrapper.find('.message--user').exists()).toBe(false)
  })

  it('invalidates a pending route seed when public content leaves ready', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const state = readyPublicContentState()
    const { wrapper } = await mountAgentPage(
      state,
      '/agent?question=状态变化中的问题&project=sql-audit',
    )

    state.status.value = 'loading'
    await flushPromises()
    resolveAnswer(answerResponse())
    await flushPromises()

    expect(wrapper.find('.agent-workspace').exists()).toBe(false)
    expect(
      (wrapper.vm as unknown as { initialSeed: unknown }).initialSeed,
    ).toBeNull()
    expect(
      (wrapper.vm as unknown as { seedStatus: string }).seedStatus,
    ).toBe('idle')
  })
})
