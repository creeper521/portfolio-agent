import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  loadPublicAgentTurnGoldenFixtures,
} from '../model/publicAgentTurnFixtureLoader'
import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import AgentWorkspace from './AgentWorkspace.vue'

// 模型目录 UI spec §8.2 验收：会话内选择、pending 锁定、双动作恢复、
// stale 回退与通知不进会话窗口。快照不变量承 A2-22（§5.1）。

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
  modelSelection: { kind: string; modelRef?: string; selectionVersion?: string }
  command: unknown
  conversationWindow: { role: string; content: string }[]
} {
  const call = apiMocks.submitAgentTurn.mock.calls.at(-1)
  if (call === undefined) throw new Error('submitAgentTurn 未被调用')
  return call[0] as ReturnType<typeof lastSubmitInput>
}

const GLM_SELECTION = {
  kind: 'MODEL',
  modelRef: 'glm-4-7-flash',
  selectionVersion: 'glm-4-7-flash-v1',
}

const QWEN_SELECTION = {
  kind: 'MODEL',
  modelRef: 'qwen-3-7-flash',
  selectionVersion: 'qwen-3-7-flash-v1',
}

describe('AgentWorkspace（模型目录：会话内选择与恢复动作，UI spec §8.2）', () => {
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

  it('默认目录选择随首轮携带；切换后下一轮携带新选择，通知卡可见且不进会话窗口（§2.4）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(
        submitOk(goldenTurn('answer-complete.json'), { conversationId: 'c1', resumeToken: 't1' }),
      )
      .mockResolvedValueOnce(submitOk(goldenTurn('answer-partial.json'), null))
    const wrapper = mountWorkspace()

    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    expect(lastSubmitInput().modelSelection).toEqual(GLM_SELECTION)
    // 回答标识只消费该轮 modelExecution 投影（§2.5）。
    expect(wrapper.get('[data-testid="model-tag"]').text()).toBe('GLM-4.7-FLASH')

    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[1]!.trigger('click')
    const notice = wrapper.get('[data-testid="model-notice"]')
    expect(notice.text()).toContain('已切换至 Qwen3.7-Flash · 下一轮回答将由它生成')
    expect(notice.text()).toContain('选择仅在本页会话内记忆，刷新后使用目录默认')

    await submitFreeText(wrapper, '第二个问题')
    const second = lastSubmitInput()
    expect(second.modelSelection).toEqual(QWEN_SELECTION)
    // 通知卡是纯展示：conversationWindow 只含 USER/ASSISTANT 轮次，不携带通知文案。
    expect(
      second.conversationWindow.some((message) => message.content.includes('已切换至')),
    ).toBe(false)
    // conversationWindow 只携带本轮之前的会话历史；本轮输入在 command 内。
    expect(second.conversationWindow.map((message) => message.role)).toEqual([
      'USER', 'ASSISTANT',
    ])
    wrapper.unmount()
  })

  it('Pending 锁定：触发钮禁用且浮层不可打开，同时给出文字原因（§2.3/D-MS-4）', async () => {
    apiMocks.submitAgentTurn.mockReturnValue(new Promise(() => {}))
    const wrapper = mountWorkspace()
    await flushPromises()
    await wrapper.get('[data-testid="question-input"]').setValue('慢问题')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()

    const trigger = wrapper.get('[data-testid="model-selector-trigger"]')
    expect(trigger.attributes('disabled')).toBeDefined()
    expect(trigger.attributes('aria-disabled')).toBe('true')
    await trigger.trigger('click')
    expect(wrapper.find('[data-testid="model-selector-popover"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="model-selector-lock-note"]').text())
      .toBe('回答生成中 · 本轮结束后可切换模型')
    wrapper.unmount()
  })

  it('模型不可用终局出现双动作；重试复用同 requestId 与原快照（含原选择）（§2.6/§5.1）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(
        submitOk(
          goldenTurn('selected-model-temporarily-unavailable.json'),
          { conversationId: 'c1', resumeToken: 't1' },
        ),
      )
      .mockResolvedValueOnce(
        submitOk(goldenTurn('answer-complete.json'), { conversationId: 'c1' }),
      )
    const wrapper = mountWorkspace()
    await flushPromises()

    // 先切换到 Qwen，使提交选择与终局投影（requestedModelRef=qwen）一致。
    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[1]!.trigger('click')
    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    const first = lastSubmitInput()
    expect(first.modelSelection).toEqual(QWEN_SELECTION)
    expect(wrapper.get('[data-testid="model-failure-title"]').text())
      .toBe('Qwen3.7-Flash 暂时无法完成这次回答')
    expect(wrapper.text()).toContain('本轮请求已安全结束。你可以用同一请求重试，或换一个模型重新提问。')
    expect(wrapper.get('[data-testid="model-retry-same-request"]').text()).toBe('重试本次请求')
    expect(wrapper.get('[data-testid="model-switch-reask"]').text())
      .toBe('换 GLM-4.7-Flash 重新提问')

    await wrapper.get('[data-testid="model-retry-same-request"]').trigger('click')
    await flushPromises()
    const retry = lastSubmitInput()
    expect(retry.requestId).toBe(first.requestId)
    expect(retry.modelSelection).toEqual(first.modelSelection)
    expect(retry.command).toEqual(first.command)
    expect(retry.conversationWindow).toEqual(first.conversationWindow)
    wrapper.unmount()
  })

  it('换模型重问：新 requestId、新快照携带新选择，出现新请求标识通知（§2.6 动作二）', async () => {
    apiMocks.submitAgentTurn
      .mockResolvedValueOnce(
        submitOk(
          goldenTurn('selected-model-temporarily-unavailable.json'),
          { conversationId: 'c1', resumeToken: 't1' },
        ),
      )
      .mockResolvedValueOnce(
        submitOk(goldenTurn('answer-complete.json'), { conversationId: 'c1' }),
      )
    const wrapper = mountWorkspace()
    await flushPromises()

    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[1]!.trigger('click')
    await submitFreeText(wrapper, '介绍 SQL 审计项目')
    const original = lastSubmitInput()
    await wrapper.get('[data-testid="model-switch-reask"]').trigger('click')
    await flushPromises()

    const reask = lastSubmitInput()
    expect(reask.requestId).not.toBe(original.requestId)
    expect(reask.modelSelection).toEqual(GLM_SELECTION)
    expect(reask.command).toEqual(original.command)
    const notice = wrapper.get('[data-notice-kind="MODEL_REASK"]')
    expect(notice.text()).toContain('已切换至 GLM-4.7-Flash · 下一轮回答将由它生成')
    expect(notice.text()).toContain(`新请求标识 ${reask.requestId.slice(0, 8)} · 不复用原请求的任何结果`)
    // 新请求是全新 USER 轮次：同问题再次落账。
    expect(wrapper.findAll('[data-message-role="USER"]')).toHaveLength(2)
    wrapper.unmount()
  })

  it('目录刷新致选择失效：pending 中不回退，终局后回退目录默认并插入可见通知（§2.9）', async () => {
    let resolveFirst: (value: unknown) => void = () => {}
    apiMocks.submitAgentTurn.mockReturnValue(
      new Promise((resolve) => {
        resolveFirst = resolve
      }),
    )
    const wrapper = mountWorkspace()
    await flushPromises()

    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[1]!.trigger('click')
    await submitFreeText(wrapper, '用 Qwen 的问题')
    expect(lastSubmitInput().modelSelection).toEqual(QWEN_SELECTION)

    const catalogWithoutQwen = {
      ...previewPublicContent,
      agentAvailability: {
        ...previewPublicContent.agentAvailability,
        selectableModels: previewPublicContent.agentAvailability.selectableModels.filter(
          (model) => model.modelRef !== 'qwen-3-7-flash',
        ),
      },
    }
    await wrapper.setProps({ portfolio: catalogWithoutQwen })
    await flushPromises()
    // pending 中不中途回退：无 stale 通知。
    expect(wrapper.find('[data-notice-kind="MODEL_STALE_FALLBACK"]').exists()).toBe(false)

    resolveFirst({
      ok: true,
      turn: goldenTurn('conversational.json'),
      conversation: { conversationId: 'c1', discussionRevision: 0 },
    })
    await flushPromises()
    const staleNotice = wrapper.get('[data-notice-kind="MODEL_STALE_FALLBACK"]')
    expect(staleNotice.text())
      .toBe('qwen-3-7-flash 当前不可用，已回到目录默认 GLM-4.7-Flash')
    // 回退后下一轮按目录默认携带。
    await wrapper.get('[data-testid="question-input"]').setValue('再问一个')
    await wrapper.get('[data-testid="submit-question"]').trigger('submit')
    await flushPromises()
    expect(lastSubmitInput().modelSelection).toEqual(GLM_SELECTION)
    wrapper.unmount()
  })

  it('确定性终局的回答标识为 DETERMINISTIC（§2.5，selectionKind=NONE）', async () => {
    apiMocks.submitAgentTurn.mockResolvedValueOnce(
      submitOk(goldenTurn('conversational.json'), null),
    )
    const wrapper = mountWorkspace()
    await submitFreeText(wrapper, '你好')
    expect(wrapper.get('[data-testid="model-tag"]').text()).toBe('DETERMINISTIC')
    wrapper.unmount()
  })

  it('目录有可选模型但默认未就绪（NONE）：自由文本被阻止并提示先选择（设计 §8）', async () => {
    const wrapper = mountWorkspace({
      portfolio: {
        ...previewPublicContent,
        agentAvailability: {
          ...previewPublicContent.agentAvailability,
          defaultModelSelection: { kind: 'NONE' },
        },
      },
    })
    await flushPromises()
    expect(wrapper.get('[data-testid="question-input"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="model-selection-required"]').text())
      .toContain('请先在上方选择一个模型')
    // 显式选择后恢复可提交。
    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="model-selection-required"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="question-input"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('空目录（显式 NONE）：只读态 + 常显说明，确定性预设仍可提交 NONE 选择（§2.8/设计决策 9）', async () => {
    apiMocks.submitAgentTurn.mockResolvedValueOnce(
      submitOk(goldenTurn('capability-unavailable.json'), null),
    )
    const wrapper = mountWorkspace({
      portfolio: {
        ...previewPublicContent,
        agentAvailability: {
          status: 'AVAILABLE',
          freeTextSemanticRouting: 'AVAILABLE',
          modelCatalogVersion: '',
          defaultModelSelection: { kind: 'NONE' },
          selectableModels: [],
        },
      },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="model-selector-trigger"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="model-selector-none"]').text())
      .toContain('确定性回答 · 未配置模型')
    expect(wrapper.get('[data-testid="model-selector-none"]').text())
      .toContain('当前部署未配置可选模型，仅提供基于公开资料的确定性回答')
    const presetChip = wrapper
      .findAll('.workspace-composer__suggestion')
      .find((chip) => chip.attributes('disabled') === undefined)
    expect(presetChip).toBeDefined()
    await presetChip!.trigger('click')
    await flushPromises()
    expect(lastSubmitInput().modelSelection).toEqual({ kind: 'NONE' })
    wrapper.unmount()
  })
})
