import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { clearAgentHandoffsForTest, createAgentHandoff } from '../features/agent/model/handoffStore'
import { mapAnswerResponse } from '../features/agent/model/mapAnswerResponse'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import AgentPage from './AgentPage.vue'

const SESSION_KEY = 'forbidden-session-key'

const { askQuestionMock } = vi.hoisted(() => ({
  askQuestionMock: vi.fn(),
}))

vi.mock('../features/agent/api/answerApi', () => ({
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
    clearAgentHandoffsForTest()
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

  it('consumes a homepage handoff once and removes it from the URL', async () => {
    const handoffId = createAgentHandoff({
      role: 'INTERVIEWER',
      question: '如何验证结果？',
      answer: mapAnswerResponse(answerResponse()),
      projectSlug: 'sql-audit',
      evidenceIds: ['sql-audit-delivery-set'],
      source: 'HOME',
    })
    const { wrapper, router } = await mountAgentPage(
      readyPublicContentState(), `/agent?handoffId=${handoffId}`,
    )
    await flushPromises()

    expect(askQuestionMock).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/agent')
    expect(wrapper.get('.message--user').text()).toContain('如何验证结果？')
    expect(wrapper.get('.message--agent').text()).toContain('背景内容')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    wrapper.unmount()
    const consumed = await mountAgentPage(
      readyPublicContentState(), `/agent?handoffId=${handoffId}`,
    )
    await flushPromises()
    expect(consumed.wrapper.find('[data-invalid-handoff]').exists()).toBe(true)
    expect(consumed.wrapper.text()).toContain('已失效或已被使用')
  })

  it('drops a legacy question query without submitting or retaining it', async () => {
    const { wrapper, router } = await mountAgentPage(
      readyPublicContentState(), '/agent?question=不得进入历史的问题&project=sql-audit',
    )
    await flushPromises()

    expect(askQuestionMock).not.toHaveBeenCalled()
    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('不得进入历史的问题')
    expect(router.currentRoute.value.fullPath).toBe('/agent')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
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
    expect(wrapper.text()).toContain('正在装订公开档案')
  })
})
