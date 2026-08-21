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

  it('free-text disabled only blocks the composer and keeps deterministic presets usable', async () => {
    const wrapper = mountWorkspace({
      portfolio: {
        ...previewPublicContent,
        agentAvailability: {
          status: 'AVAILABLE',
          freeTextSemanticRouting: 'DISABLED',
        },
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="question-input"]').attributes('disabled'))
      .toBeDefined()
    expect(wrapper.get('[data-testid="submit-question"]').attributes('disabled'))
      .toBeDefined()
    expect(wrapper.get('[data-testid="free-text-routing-disabled"]').text())
      .toContain('自由文本语义理解未启用')
    expect(wrapper.findAll('.workspace-composer__suggestion')
      .some((button) => button.attributes('disabled') === undefined)).toBe(true)
    wrapper.unmount()
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
    expect(second.command).toMatchObject({
      kind: 'ASK',
      referenceContextHandle: 'ctx_fixture_recommendation',
    })
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

  it.each([
    {
      label: '成功',
      lateResult: submitOk(goldenTurn('conversational.json'), {
        conversationId: 'stale-conversation',
        discussionRevision: 99,
      }),
    },
    {
      label: '非 ABORTED 失败',
      lateResult: {
        ok: false as const,
        failure: {
          kind: 'API' as const,
          status: 503,
          code: 'AGENT_STATE_UNAVAILABLE',
          message: '迟到失败',
          retryable: true,
        },
      },
    },
  ])('取消 A 后提交 B，A 的迟到$label不得污染 B（A2-27）', async ({ lateResult }) => {
    let resolveA: (value: unknown) => void = () => {}
    apiMocks.submitAgentTurn
      .mockImplementationOnce(() => new Promise((resolve) => { resolveA = resolve }))
      .mockReturnValueOnce(new Promise(() => {}))
    const wrapper = mountWorkspace()
    await flushPromises()

    await wrapper.get('[data-testid="question-input"]').setValue('请求 A')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-testid="cancel-turn"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="question-input"]').setValue('请求 B')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="conversation-pending"]').text()).toContain('请求 B')

    resolveA(lateResult)
    await flushPromises()

    expect(wrapper.get('[data-testid="conversation-pending"]').text()).toContain('请求 B')
    expect(wrapper.find('[data-testid="turn-failure"]').exists()).toBe(false)
    expect(wrapper.find('[data-message-role="AGENT"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('API 失败按类别显示可行动文案，重试复用同一 requestId（幂等）', async () => {
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
    const failure = wrapper.get('[data-testid="turn-failure"]')
    expect(failure.attributes('data-failure-category')).toBe('SERVICE_UNAVAILABLE')
    expect(failure.text()).toContain('Agent 服务暂时不可用')
    const failedRequestId = lastSubmitInput().requestId

    await wrapper.get('[data-testid="retry-turn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="conversational-turn"]').exists()).toBe(true)
    expect(lastSubmitInput().requestId).toBe(failedRequestId)
    wrapper.unmount()
  })

  it('超时不再被静默吞掉：显示超时类别与重试入口，重试成功后解除失败标记（A2-10/A2-14）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce({
        ok: false,
        failure: { kind: 'TIMEOUT', code: 'REQUEST_TIMEOUT', message: '等待超时', retryable: true },
      })
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '慢回复问题')
    const failure = wrapper.get('[data-testid="turn-failure"]')
    expect(failure.attributes('data-failure-category')).toBe('TIMEOUT')
    expect(failure.text()).toContain('回答可能仍在生成')
    // 超时轮次先标记 failed，不进入后续会话窗口。
    expect(wrapper.find('[data-message-failed="true"]').exists()).toBe(true)
    const timedOutRequestId = lastSubmitInput().requestId

    await wrapper.get('[data-testid="retry-turn"]').trigger('click')
    await flushPromises()

    expect(lastSubmitInput().requestId).toBe(timedOutRequestId)
    expect(wrapper.find('[data-testid="conversational-turn"]').exists()).toBe(true)
    expect(wrapper.find('[data-message-failed="true"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('澄清失败重试原样复用完整提交身份（A2-22）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('clarification.json'), {
        conversationId: 'conversation-1',
        resumeToken: 'token-1',
      }))
      .mockResolvedValueOnce({
        ok: false,
        failure: {
          kind: 'TIMEOUT',
          code: 'REQUEST_TIMEOUT',
          message: '等待超时',
          retryable: true,
        },
      })
      .mockResolvedValueOnce(submitOk(goldenTurn('answer-complete.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '需要澄清的问题')
    await wrapper.get('input[type="radio"][value="choice_sql"]').setValue()
    await wrapper.get('button[data-clarification-submit]').trigger('submit')
    await flushPromises()
    const original = JSON.parse(JSON.stringify(apiMocks.submitAgentTurn.mock.calls[1]?.[0]))

    await wrapper.get('[data-testid="retry-turn"]').trigger('click')
    await flushPromises()

    expect(apiMocks.submitAgentTurn.mock.calls[2]?.[0]).toEqual(original)
    wrapper.unmount()
  })

  it('首页 handoff replay 失败重试保留 HOME surface、空 window 与原 Token（A2-22）', async () => {
    const replayCommand: AgentTurnCommand = {
      kind: 'ASK',
      input: { kind: 'FREE_TEXT', text: '首页交接问题' },
    }
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce({
        ok: false,
        failure: {
          kind: 'TIMEOUT',
          code: 'REQUEST_TIMEOUT',
          message: '等待超时',
          retryable: true,
        },
      })
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace({
      initialSeed: {
        role: 'INTERVIEWER',
        question: '首页交接问题',
        projectSlug: null,
        source: 'HOME',
        conversation: {
          conversationId: 'conversation-home',
          resumeToken: 'token-home',
        },
        replay: {
          requestId: '11111111-1111-4111-8111-111111111111',
          command: replayCommand,
          surfaceContext: {
            audienceRole: 'INTERVIEWER',
            requestSource: 'HOME',
          },
        },
      },
    })
    await flushPromises()
    const original = JSON.parse(JSON.stringify(apiMocks.submitAgentTurn.mock.calls[0]?.[0]))

    await wrapper.get('[data-testid="retry-turn"]').trigger('click')
    await flushPromises()

    expect(apiMocks.submitAgentTurn.mock.calls[1]?.[0]).toEqual(original)
    wrapper.unmount()
  })

  it('失败 USER 轮次被排除出 conversationWindow，后续请求保持 USER 起始交替（A2-04）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce({
        ok: false,
        failure: {
          kind: 'API',
          status: 400,
          code: 'VALIDATION_ERROR',
          message: '校验失败',
          retryable: false,
        },
      })
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '会失败的问题')
    expect(wrapper.find('[data-testid="retry-turn"]').exists()).toBe(false)
    expect(wrapper.find('[data-message-failed="true"]').exists()).toBe(true)

    await submitFreeText(wrapper, '第二个问题')
    const input = lastSubmitInput()
    // 窗口只携带本轮之前的历史；失败轮次被排除后为空，而不是携带失败问题。
    expect(input.conversationWindow).toEqual([])
    wrapper.unmount()
  })

  it('backend action 转发：推荐讨论发送 closed CONTINUE，普通建议发送 ASK', async () => {
    apiMocks.fetchCurrentConversation
      .mockResolvedValueOnce({
        ok: true,
        conversationId: 'conversation-1',
        status: 'ACTIVE',
      })
      .mockResolvedValueOnce({
        ok: true,
        conversationId: 'conversation-1',
        status: 'ACTIVE',
        activeDiscussion: {
          status: 'ACTIVE',
          subject: {
            kind: 'PROJECT',
            reference: 'project-a',
            label: '项目 A',
            route: '/projects/project-a',
          },
          expiresAt: '2026-08-20T08:30:00Z',
          routeContinuation: {
            operation: 'ROUTE_IN_CONTEXT',
            contextHandle: 'discussion_handle_123',
          },
          exitAction: {
            actionId: 'discussion-exit',
            label: '结束讨论',
            continuation: {
              operation: 'EXIT_CONTEXT',
              contextHandle: 'discussion_handle_123',
            },
          },
        },
      })
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(
        goldenTurn('answer-complete.json'),
        {
          conversationId: 'conversation-1', resumeToken: 'token-1',
          discussionRevision: 0,
        },
      ))
      .mockResolvedValueOnce(submitOk(goldenTurn('boundary.json'), {
        conversationId: 'conversation-1',
        discussionRevision: 1,
        activeDiscussion: {
          status: 'ACTIVE',
          subject: {
            kind: 'PROJECT', reference: 'project-a',
            label: '项目 A', route: '/projects/project-a',
          },
          expiresAt: '2026-08-20T08:30:00Z',
          routeContinuation: {
            operation: 'ROUTE_IN_CONTEXT',
            contextHandle: 'discussion_handle_123',
          },
          exitAction: {
            actionId: 'discussion-exit', label: '结束讨论',
            continuation: {
              operation: 'EXIT_CONTEXT',
              contextHandle: 'discussion_handle_123',
            },
          },
        },
      }))
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), {
        conversationId: 'conversation-1',
        discussionRevision: 1,
        activeDiscussion: {
          status: 'ACTIVE',
          subject: {
            kind: 'PROJECT', reference: 'project-a',
            label: '项目 A', route: '/projects/project-a',
          },
          expiresAt: '2026-08-20T08:30:00Z',
          routeContinuation: {
            operation: 'ROUTE_IN_CONTEXT',
            contextHandle: 'discussion_handle_123',
          },
          exitAction: {
            actionId: 'discussion-exit', label: '结束讨论',
            continuation: {
              operation: 'EXIT_CONTEXT',
              contextHandle: 'discussion_handle_123',
            },
          },
        },
      }))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    await wrapper.get('button[data-action-id="discuss-item-goal-recommendation-1"]').trigger('click')
    await flushPromises()
    expect(lastSubmitInput().command).toEqual({
      kind: 'CONTINUE',
      operation: 'ENTER_RESULT',
      contextHandle: 'ctx_fixture_recommendation',
      resultItemId: 'item-goal-recommendation-1',
    })
    expect(wrapper.get('[data-testid="active-discussion"]').text())
      .toContain('项目 A')

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

  it('reservation busy 恢复乐观 consumed 卡并排除未接受的 USER 摘要', async () => {
    const busy = goldenTurn('capability-unavailable.json')
    busy.code = 'CLARIFICATION_IN_PROGRESS'
    busy.message = '当前澄清正在由另一请求处理，请稍后重新提交。'
    busy.retryable = true
    busy.retryAfterSeconds = 6
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('clarification.json'), null))
      .mockResolvedValueOnce(submitOk(busy, null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '需要澄清的问题')
    await wrapper.get('input[type="radio"][value="choice_sql"]').setValue()
    await wrapper.get('button[data-clarification-submit]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-clarification-state="CONSUMED"]').exists())
      .toBe(false)
    expect(wrapper.find('button[data-clarification-submit]').exists()).toBe(true)
    expect(wrapper.find('[data-message-failed="true"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="capability-unavailable-turn"]').text())
      .toContain('约 6 秒后可重新提交')
    wrapper.unmount()
  })

  it('澄清答案记为 USER 轮次且原卡转只读，后续窗口保持 USER 起始交替（A2-03/A2-18）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('clarification.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('answer-complete.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '随便看看')
    await wrapper.get('input[type="radio"][value="choice_sql"]').setValue()
    await wrapper.get('button[data-clarification-submit]').trigger('submit')
    await flushPromises()

    // 澄清答案以公开选项标签记为 USER 轮次。
    const userMessages = wrapper.findAll('[data-message-role="USER"]')
    expect(userMessages.at(-1)?.text()).toBe('SQL 审计与故障排查工具')
    // 原挑战卡立即只读，不再出现可提交表单。
    expect(wrapper.find('[data-clarification-state="CONSUMED"]').exists()).toBe(true)
    expect(wrapper.find('button[data-clarification-submit]').exists()).toBe(false)

    await submitFreeText(wrapper, '继续追问')
    const input = lastSubmitInput()
    expect(input.conversationWindow.map((message) => message.role)).toEqual([
      'USER',
      'ASSISTANT',
      'USER',
      'ASSISTANT',
    ])
    wrapper.unmount()
  })

  it('新对话不继承旧会话的失败与 pending，切回旧会话可各自操作（A2-07/08）', async () => {
    apiMocks.submitAgentTurn.mockResolvedValue({
      ok: false,
      failure: {
        kind: 'API',
        status: 503,
        code: 'AGENT_STATE_UNAVAILABLE',
        message: '状态暂不可用',
        retryable: true,
      },
    })
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '旧会话问题')
    expect(wrapper.find('[data-testid="turn-failure"]').exists()).toBe(true)

    await wrapper.get('button.session-rail__new').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="turn-failure"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="conversation-pending"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="question-input"]').attributes('disabled')).toBeUndefined()

    // 切回旧会话：失败仍归属原会话，可重试。
    await wrapper.get('button.session-select').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="turn-failure"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="retry-turn"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('会话 A pending 时不阻塞会话 B 输入；B 的取消不作用于 A（A2-08）', async () => {    apiMocks.submitAgentTurn.mockReturnValue(new Promise(() => {}))
    const wrapper = mountWorkspace()
    await flushPromises()

    await wrapper.get('[data-testid="question-input"]').setValue('会话A的问题')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="conversation-pending"]').exists()).toBe(true)

    await wrapper.get('button.session-rail__new').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="conversation-pending"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="question-input"]').attributes('disabled')).toBeUndefined()

    apiMocks.submitAgentTurn.mockResolvedValueOnce(
      submitOk(goldenTurn('conversational.json'), null),
    )
    await submitFreeText(wrapper, '会话B的问题')
    expect(lastSubmitInput().command).toMatchObject({
      kind: 'ASK',
      input: { kind: 'FREE_TEXT', text: '会话B的问题' },
    })
    // B 会话没有取消按钮，无法误取消 A 的请求。
    expect(wrapper.find('[data-testid="cancel-turn"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('标签页合计最多两个 pending：第三个会话仅提示，一个完成后自动解除（§11.1）', async () => {
    let resolveFirst: (value: unknown) => void = () => {}
    apiMocks.submitAgentTurn
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve
          }),
      )
      .mockReturnValue(new Promise(() => {}))
      .mockReturnValue(new Promise(() => {}))
    const wrapper = mountWorkspace()
    await flushPromises()

    await submitFreeText(wrapper, '问题一')
    await wrapper.get('button.session-rail__new').trigger('click')
    await flushPromises()
    await submitFreeText(wrapper, '问题二')
    expect(apiMocks.submitAgentTurn).toHaveBeenCalledTimes(2)

    await wrapper.get('button.session-rail__new').trigger('click')
    await flushPromises()
    const notice = wrapper.get('[data-testid="tab-pending-notice"]')
    expect(notice.text()).toContain('已有两个请求正在处理')
    expect(notice.attributes('role')).toBe('status')
    expect(wrapper.get('[data-testid="submit-question"]').attributes('disabled')).toBeDefined()
    // 输入区仍可打字（草稿按会话保留），但提交被拦截。
    const input = wrapper.get('[data-testid="question-input"]').element as HTMLTextAreaElement
    expect(input.disabled).toBe(false)

    await wrapper.get('[data-testid="question-input"]').setValue('问题三')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    expect(apiMocks.submitAgentTurn).toHaveBeenCalledTimes(2)

    // 第一个请求完成后腾出槽位，提示消失、可继续提交。
    resolveFirst(submitOk(goldenTurn('conversational.json'), null))
    await flushPromises()
    expect(wrapper.find('[data-testid="tab-pending-notice"]').exists()).toBe(false)

    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    expect(apiMocks.submitAgentTurn).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('草稿按会话隔离：切换会话不串草稿（A2-09）', async () => {
    const wrapper = mountWorkspace()
    await flushPromises()
    await wrapper.get('[data-testid="question-input"]').setValue('会话A的草稿')
    await wrapper.get('button.session-rail__new').trigger('click')
    await flushPromises()
    const inputElement = wrapper.get('[data-testid="question-input"]').element as HTMLTextAreaElement
    expect(inputElement.value).toBe('')
    wrapper.unmount()
  })

  it('冷恢复 summary 破损时显式提示并清除 Token，不静默退化普通 ASK', async () => {
    sessionStorage.setItem(RESUME_STORAGE_KEY, 'token-broken')
    apiMocks.fetchCurrentConversation.mockResolvedValue({
      ok: false,
      invalid: false,
      reason: 'CONTRACT_INVALID',
    })
    const wrapper = mountWorkspace()
    await flushPromises()

    expect(wrapper.get('.workspace-notice').text()).toContain('会话状态结构异常')
    expect(sessionStorage.getItem(RESUME_STORAGE_KEY)).toBeNull()
    wrapper.unmount()
  })

  it('热路径 envelope 破损时暂停讨论自由文本，保留确定性退出', async () => {
    const active = {
      status: 'ACTIVE',
      subject: {
        kind: 'PROJECT', reference: 'project-a',
        label: '项目 A', route: '/projects/project-a',
      },
      expiresAt: '2026-08-21T16:30:00Z',
      routeContinuation: {
        operation: 'ROUTE_IN_CONTEXT', contextHandle: 'discussion_handle_123',
      },
      exitAction: {
        actionId: 'discussion-exit', label: '结束讨论',
        continuation: {
          operation: 'EXIT_CONTEXT', contextHandle: 'discussion_handle_123',
        },
      },
    }
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), {
        conversationId: 'conversation-1', resumeToken: 'token-1',
        discussionRevision: 1, activeDiscussion: active,
      }))
      .mockResolvedValueOnce({
        ok: false,
        failure: {
          kind: 'CONTRACT', code: 'PUBLIC_TURN_CONTRACT_INVALID',
          message: '回答结构不符合冻结合同', retryable: false,
        },
      })
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '进入讨论')
    await submitFreeText(wrapper, '继续追问')

    expect(wrapper.get('[data-testid="discussion-state-paused"]').text())
      .toContain('已暂停自由文本续谈')
    expect(wrapper.get('[data-testid="question-input"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="exit-discussion"]').attributes('disabled')).toBeUndefined()
    expect(apiMocks.submitAgentTurn).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('来源栏标题区分当前/最近回答并随 stale 弱化（A2-06）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('answer-complete.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('clarification.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    const panel = wrapper.get('.sources-panel')
    expect(panel.text()).toContain('当前回答来源')
    expect(panel.attributes('data-sources-stale')).toBeUndefined()

    await submitFreeText(wrapper, '再来一个问题')
    expect(wrapper.get('.sources-panel').text()).toContain('最近回答来源')
    expect(wrapper.get('.sources-panel').attributes('data-sources-stale')).toBe('true')
    wrapper.unmount()
  })

  it('被正文引用的来源提供定位入口，点击定位到回答内 section（B7）', async () => {
    apiMocks.submitAgentTurn.mockResolvedValue(
      submitOk(goldenTurn('answer-complete.json'), null),
    )
    const wrapper = mountWorkspace()
    await submitFreeText(wrapper, '介绍 SQL 审计项目')

    const locateButton = wrapper.get('[data-locate-source-key="source-sql-audit"]')
    await locateButton.trigger('click')
    await flushPromises()

    const thread = wrapper.getComponent({ name: 'ConversationThread' })
    expect(thread.props('focusTarget')).toMatchObject({ sectionId: 'section-background' })
    const section = wrapper.get('[data-section-id="section-background"]')
    expect(section.classes()).toContain('conversation-thread--located')
    wrapper.unmount()
  })

  it('无后端建议时澄清卡脱困入口只消费已发布 QuestionPreset（PRESET 命令）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(submitOk(goldenTurn('clarification.json'), null))
      .mockResolvedValueOnce(submitOk(goldenTurn('conversational.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '你好')
    const fallback = wrapper.get('[data-testid="clarification-preset-fallback"]')
    expect(fallback.text()).toContain('请介绍 SQL 审计工具的完整迭代过程。')

    await wrapper.get('[data-fallback-preset="sql-audit-overview"]').trigger('click')
    await flushPromises()

    expect(lastSubmitInput().command).toEqual({
      kind: 'ASK',
      input: {
        kind: 'PRESET',
        presetId: 'sql-audit-overview',
        presetRevision: 'pcv1-0123456789abcdef',
      },
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
