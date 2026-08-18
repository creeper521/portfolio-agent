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
