import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import {
  loadPublicAgentTurnGoldenFixtures,
  parseGoldenFixture,
} from '../model/publicAgentTurnFixtureLoader'
import { parsePublicAgentTurn } from '../model/publicAgentTurnMapper'
import PublicAgentTurnMessage from './PublicAgentTurnMessage.vue'

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

const FIXTURE_BY_KIND: Readonly<Record<string, string>> = {
  ANSWER: 'answer-complete.json',
  CLARIFICATION: 'clarification.json',
  CONVERSATIONAL: 'conversational.json',
  BOUNDARY: 'boundary.json',
  CAPABILITY_UNAVAILABLE: 'capability-unavailable.json',
}

function mountTurn(fileName: string) {
  return mount(PublicAgentTurnMessage, {
    props: { turn: parseGoldenFixture(fileName) },
    global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
  })
}

describe('PublicAgentTurnMessage', () => {
  it('五种闭合 variants 各渲染专属视图，data-turn-kind 为唯一判别痕迹', () => {
    for (const [kind, fileName] of Object.entries(FIXTURE_BY_KIND)) {
      const wrapper = mountTurn(fileName)
      expect(wrapper.attributes('data-turn-kind')).toBe(kind)
      expect(wrapper.find(`[data-testid="${kind.toLowerCase().replace(/_/g, '-')}-turn"]`).exists(), kind).toBe(true)
    }
  })

  it('五个模型不可用终局之一且提供 modelRecovery 时渲染双动作并上抛两个事件（UI spec §2.6/D-MS-7）', async () => {
    const wrapper = mount(PublicAgentTurnMessage, {
      props: {
        turn: parseGoldenFixture('selected-model-temporarily-unavailable.json'),
        modelRecovery: {
          failedModelName: 'Qwen3.7-Flash',
          sameModelRetryable: true,
          otherModelName: 'GLM-4.7-Flash',
        },
      },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    expect(wrapper.get('[data-testid="model-failure-title"]').text())
      .toBe('Qwen3.7-Flash 暂时无法完成这次回答')
    expect(wrapper.text()).toContain('同一模型重新提问 = 发送一条新请求（新标识）；失败请求的结果不会被复用')
    expect(wrapper.text()).toContain('换模型重新提问 = 发送一条新请求（新标识），由另一模型重新生成回答')
    await wrapper.get('[data-testid="model-retry-same-model"]').trigger('click')
    expect(wrapper.emitted('retry-same-model')?.[0]).toEqual([
      '10000000-0000-4000-8000-00000000000b',
    ])
    await wrapper.get('[data-testid="model-switch-reask"]').trigger('click')
    expect(wrapper.emitted('switch-model-reask')?.[0]).toEqual([
      '10000000-0000-4000-8000-00000000000b',
    ])
    wrapper.unmount()
  })

  it('同模型重问不可用（模型已不在目录）时不渲染主动作，仅保留换模型入口', () => {
    const wrapper = mount(PublicAgentTurnMessage, {
      props: {
        turn: parseGoldenFixture('selected-model-unavailable.json'),
        modelRecovery: {
          failedModelName: 'Qwen3.7-Flash',
          sameModelRetryable: false,
          otherModelName: 'GLM-4.7-Flash',
        },
      },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    expect(wrapper.find('[data-testid="model-retry-same-model"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="model-switch-reask"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('非模型终局不出现换模型入口（capability-unavailable 默认单动作路径不变）', () => {
    const wrapper = mountTurn('capability-unavailable.json')
    expect(wrapper.find('[data-testid="model-retry-same-model"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="model-switch-reask"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="turn-message"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('非 ANSWER 变体不渲染 answer 语义结构', () => {
    const wrapper = mountTurn('clarification.json')
    expect(wrapper.find('[data-testid="answer-turn"]').exists()).toBe(false)
    expect(wrapper.find('.goal-result').exists()).toBe(false)
  })

  it('CONVERSATIONAL/BOUNDARY/CAPABILITY 渲染 message 与稳定码，动作事件上抛', async () => {
    const conversational = mountTurn('conversational.json')
    expect(conversational.find('[data-testid="turn-message"]').text()).toContain('你好')
    await conversational.find('button[data-action-id="ask-project-overview"]').trigger('click')
    expect(conversational.emitted('select-action')).toHaveLength(1)

    const boundary = mountTurn('boundary.json')
    expect(boundary.find('[data-testid="turn-code"]').text()).toBe('HIGH_RISK_ADVICE_OUT_OF_SCOPE')

    const capability = mountTurn('capability-unavailable.json')
    expect(capability.find('[data-testid="turn-retryable"]').text()).toContain('重试')
  })

  it('REPLAY_BODY_NOT_RETAINED 呈现"未保留"语义与重新提问指引，不显示能力故障文案（A2-32）', () => {
    const turn = {
      ...parseGoldenFixture('capability-unavailable.json'),
      code: 'REPLAY_BODY_NOT_RETAINED',
      message: '该回答未被保留，请重新提问。',
      retryable: false,
      suggestedActions: undefined,
    }
    const wrapper = mount(PublicAgentTurnMessage, {
      props: { turn },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    expect(wrapper.attributes('data-turn-kind')).toBe('CAPABILITY_UNAVAILABLE')
    expect(wrapper.text()).toContain('该回答未保留')
    expect(wrapper.text()).not.toContain('能力暂时不可用')
    expect(wrapper.get('[data-testid="turn-retryable"]').text()).toContain('重新提问')
    expect(wrapper.text()).not.toContain('调整提问方式')
  })

  it('clarification 表单提交事件上抛为 RESOLVE 载荷形状', async () => {
    const wrapper = mountTurn('clarification.json')
    await wrapper.find('input[type="radio"][value="choice_sql"]').setValue()
    await wrapper.find('button[data-clarification-submit]').trigger('submit')
    const emitted = wrapper.emitted('submit-clarification')
    expect(emitted).toHaveLength(1)
    expect(emitted?.[0]?.[0]).toEqual({
      clarificationId: 'clarification_fixture_critical',
      answers: [{ fieldId: 'field_subject', kind: 'SINGLE_CHOICE', choiceId: 'choice_sql' }],
    })
  })

  it('恶意脚本文本只做纯文本插值，不进入 DOM 元素（D-45.8）', () => {
    const raw = loadPublicAgentTurnGoldenFixtures().find(
      (fixture) => fixture.fileName === 'answer-complete.json',
    )
    if (raw === undefined) throw new Error('缺少 answer-complete.json')
    const clone = JSON.parse(JSON.stringify(raw.turn)) as {
      answer: { goalResults: { presentation?: { sections?: { title: string; content: string }[] } }[] }
    }
    const section = clone.answer.goalResults[0]?.presentation?.sections?.[0]
    if (section === undefined) throw new Error('缺少 section')
    section.title = '<script>alert("title")</script>'
    section.content = '<img src=x onerror="alert(1)">恶意正文'
    const parsed = parsePublicAgentTurn(clone)
    expect(parsed.ok).toBe(true)
    if (!parsed.ok) return

    const wrapper = mount(PublicAgentTurnMessage, {
      props: { turn: parsed.turn },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    expect(wrapper.element.querySelector('script')).toBeNull()
    expect(wrapper.element.querySelector('img')).toBeNull()
    expect(wrapper.text()).toContain('<script>alert("title")</script>')
    expect(wrapper.text()).toContain('<img src=x onerror="alert(1)">恶意正文')
  })
})
