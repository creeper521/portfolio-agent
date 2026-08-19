import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AgentTurnCommand } from '../api/agentTurnApi'
import {
  loadPublicAgentTurnGoldenFixtures,
} from '../model/publicAgentTurnFixtureLoader'
import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import AgentWorkspace from './AgentWorkspace.vue'

// D-41/交接 §8：Workspace 生命周期测试——closed command、Bearer 槽位、
// 取消（本地先行+best-effort DELETE）、幂等重试、动作转发与澄清提交。

const RESUME_STORAGE_KEY = 'portfolio.agent.resume-token.v1'

const apiMocks = vi.hoisted(() => ({
  submitAgentTurn: vi.fn(),
  cancelAgentTurn: vi.fn(),
  clearConversation: vi.fn(),
  fetchCurrentConversation: vi.fn(),
}))

vi.mock('../api/agentTurnApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/agentTurnApi')>()
  return { ...original, ...apiMocks }
})

function goldenTurn(fileName: string): Record<string, unknown> {
  const fixture = loadPublicAgentTurnGoldenFixtures().find(
    (candidate) => candidate.fileName === fileName,
  )
  if (fixture === undefined) throw new Error(`缺少 fixture ${fileName}`)
  return JSON.parse(JSON.stringify(fixture.turn)) as Record<string, unknown>
}

function submitOk(turn: Record<string, unknown>, conversation: Record<string, unknown> | null) {
  return { ok: true as const, turn, conversation }
}

function mountWorkspace(props: Record<string, unknown> = {}) {
  return mount(AgentWorkspace, {
    props: { portfolio: previewPublicContent, ...props },
    attachTo: document.body,
    global: {
      stubs: { RouterLink: { template: '<a :href="String($attrs.to)"><slot /></a>' } },
    },
  })
}

async function submitFreeText(wrapper: ReturnType<typeof mountWorkspace>, text: string) {
  await flushPromises()
  await wrapper.get('[data-testid="question-input"]').setValue(text)
  await wrapper.get('[data-testid="submit-question"]').trigger('submit')
  await flushPromises()
}

function lastSubmitInput(): {
  requestId: string
  command: AgentTurnCommand
  resumeToken?: string
  conversationWindow: { role: string; content: string }[]
  surfaceContext: { requestSource: string }
} {
  const call = apiMocks.submitAgentTurn.mock.calls.at(-1)
  if (call === undefined) throw new Error('submitAgentTurn 未被调用')
  return call[0] as ReturnType<typeof lastSubmitInput>
}

describe('AgentWorkspace（PublicAgentTurn 生命周期）', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    for (const mock of Object.values(apiMocks)) mock.mockReset()
    apiMocks.fetchCurrentConversation.mockResolvedValue({ ok: false, invalid: false })
    apiMocks.cancelAgentTurn.mockResolvedValue('CANCELLED')
    apiMocks.clearConversation.mockResolvedValue('CLEARED')
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })),
    )
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('提交 FREE_TEXT 后渲染闭合 turn，并把轮换 Token 写入唯一 sessionStorage 槽位', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(
        submitOk(
          goldenTurn('answer-complete.json'),
          { conversationId: 'conversation-1', resumeToken: 'token-1' },
        ),
      )
      .mockResolvedValueOnce(
        submitOk(goldenTurn('conversational.json'), { conversationId: 'conversation-1' }),
      )
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    expect(wrapper.get('[data-message-role="USER"]').text()).toBe('介绍 SQL 审计项目')
    expect(wrapper.find('[data-testid="answer-turn"]').exists()).toBe(true)
    expect(sessionStorage.getItem(RESUME_STORAGE_KEY)).toBe('token-1')
    const first = lastSubmitInput()
    expect(first.command).toEqual({
      kind: 'ASK',
      input: { kind: 'FREE_TEXT', text: '介绍 SQL 审计项目' },
    })
    expect(first.resumeToken).toBeUndefined()
    expect(first.conversationWindow).toEqual([])

    await submitFreeText(wrapper, '继续介绍验证方式')
    const second = lastSubmitInput()
    expect(second.resumeToken).toBe('token-1')
    expect(second.conversationWindow.map((message) => message.role)).toEqual([
      'USER',
      'ASSISTANT',
    ])
    wrapper.unmount()
  })

  it('取消：本地先结束 pending，best-effort DELETE，不追加消息也不显示错误', async () => {
    apiMocks.submitAgentTurn.mockReturnValue(new Promise(() => {}))
    const wrapper = mountWorkspace()
    await flushPromises()

    await wrapper.get('[data-testid="question-input"]').setValue('漫长的问题')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="conversation-pending"]').text()).toContain('漫长的问题')

    await wrapper.get('[data-testid="cancel-turn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conversation-pending"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="turn-failure"]').exists()).toBe(false)
    expect(wrapper.find('[data-message-role="AGENT"]').exists()).toBe(false)
    const requestId = lastSubmitInput().requestId
    expect(apiMocks.cancelAgentTurn).toHaveBeenCalledWith(requestId, undefined)
    wrapper.unmount()
  })

  it('API 失败显示可重试错误，重试复用同一 requestId（幂等）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce({
        ok: false,
        failure: {
          kind: 'API',
          status: 503,
          code: 'AGENT_STATE_UNAVAILABLE',
          message: '状态暂不可用',
          retryable: true,
        },
      })
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '会失败的问题')
    expect(wrapper.get('[data-testid="turn-failure"]').text()).toContain('状态暂不可用')
    const failedRequestId = lastSubmitInput().requestId

    await wrapper.get('[data-testid="retry-turn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conversational-turn"]').exists()).toBe(true)
    expect(lastSubmitInput().requestId).toBe(failedRequestId)
    wrapper.unmount()
  })

  it('SuggestedAction 转发：有 continuation 发送 CONTINUE，无 continuation 发送 ASK', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('answer-complete.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('boundary.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    await wrapper.get('button[data-action-id="continue-verification"]').trigger('click')
    await flushPromises()
    expect(lastSubmitInput().command).toEqual({
      kind: 'CONTINUE',
      contextHandle: 'ctx_fixture_overview',
      text: '继续介绍验证方式',
    })

    await wrapper.get('[data-action-id="ask-engineering-topic"]').trigger('click')
    await flushPromises()
    expect(lastSubmitInput().command).toEqual({
      kind: 'ASK',
      input: { kind: 'FREE_TEXT', text: '解释一个稳定的工程概念' },
    })
    wrapper.unmount()
  })

  it('Critical clarification 提交映射为 RESOLVE_CLARIFICATION 单答案命令', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('clarification.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('answer-complete.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '随便看看')
    await wrapper.get('input[type="radio"][value="choice_sql"]').setValue()
    await wrapper.get('button[data-clarification-submit]').trigger('submit')
    await flushPromises()

    expect(lastSubmitInput().command).toEqual({
      kind: 'RESOLVE_CLARIFICATION',
      clarificationId: 'clarification_fixture_critical',
      answer: { kind: 'CHOICE', choiceId: 'choice_sql' },
    })
    wrapper.unmount()
  })

  it('清除会话：服务端 204 后清空本地与 Token 槽位；失败时不宣称已清除', async () => {
    apiMocks.submitAgentTurn.mockResolvedValue(
      submitOk(goldenTurn('answer-complete.json'), {
        conversationId: 'conversation-1',
        resumeToken: 'token-1',
      }),
    )
    const wrapper = mountWorkspace()
    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    expect(sessionStorage.getItem(RESUME_STORAGE_KEY)).toBe('token-1')

    apiMocks.clearConversation.mockResolvedValueOnce('FAILED')
    await wrapper.get('[data-session-clear]').trigger('click')
    await wrapper.get('[data-session-clear-confirm]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('服务端尚未确认清除')
    expect(sessionStorage.getItem(RESUME_STORAGE_KEY)).toBe('token-1')

    await wrapper.get('[data-session-clear]').trigger('click')
    await wrapper.get('[data-session-clear-confirm]').trigger('click')
    await flushPromises()
    expect(apiMocks.clearConversation).toHaveBeenCalledWith('token-1')
    expect(sessionStorage.getItem(RESUME_STORAGE_KEY)).toBeNull()
    wrapper.unmount()
  })

  it('回答来源面板展示最近 ANSWER 的唯一 SourceCatalog', async () => {
    apiMocks.submitAgentTurn.mockResolvedValue(
      submitOk(goldenTurn('answer-complete.json'), null),
    )
    const wrapper = mountWorkspace()
    await submitFreeText(wrapper, '介绍 SQL 审计项目')

    const items = wrapper.findAll('[data-testid="sources-panel-list"] li')
    expect(items).toHaveLength(2)
    expect(items[0]?.attributes('data-source-key')).toBe('source-sql-audit')
    wrapper.unmount()
  })
})
