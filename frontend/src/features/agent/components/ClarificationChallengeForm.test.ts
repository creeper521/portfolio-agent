import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { ClarificationChallenge } from '../model/publicAgentTurn'
import ClarificationChallengeForm from './ClarificationChallengeForm.vue'

function challengeOf(fileName: string): ClarificationChallenge {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'CLARIFICATION') throw new Error('期望 CLARIFICATION')
  return turn.clarification
}

function localChallengeOf(fileName: string): ClarificationChallenge {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'ANSWER' || turn.answer.localClarification === undefined) {
    throw new Error('期望 ANSWER 带 localClarification')
  }
  return turn.answer.localClarification
}

describe('ClarificationChallengeForm', () => {
  it('SINGLE_CHOICE 使用 fieldset/legend 与原生 radio，label 关联 choice', () => {
    const challenge = challengeOf('clarification.json')
    const wrapper = mount(ClarificationChallengeForm, { props: { challenge } })
    const fieldset = wrapper.find('fieldset[data-field-kind="SINGLE_CHOICE"]')
    expect(fieldset.find('legend').text()).toContain('公开项目')
    const radios = fieldset.findAll('input[type="radio"]')
    expect(radios).toHaveLength(2)
    expect(radios[0]?.attributes('value')).toBe('choice_sql')
    const label = wrapper.find(`label[for="${radios[0]?.attributes('id') ?? ''}"]`)
    expect(label.text()).toBe('SQL 审计与故障排查工具')
  })

  it('必填未作答时提交禁用；选择后提交事件只携带 clarificationId 与闭合答案', async () => {
    const challenge = challengeOf('clarification.json')
    const wrapper = mount(ClarificationChallengeForm, { props: { challenge } })
    const submit = wrapper.find('button[data-clarification-submit]')
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.find('input[type="radio"][value="choice_platform"]').setValue()
    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('submit')

    const emitted = wrapper.emitted('submit')
    expect(emitted).toHaveLength(1)
    expect(emitted?.[0]?.[0]).toEqual({
      clarificationId: 'clarification_fixture_critical',
      answers: [{ fieldId: 'field_subject', kind: 'SINGLE_CHOICE', choiceId: 'choice_platform' }],
    })
  })

  it('TEXT 字段受 limit 约束并显示计数', () => {
    const wrapper = mount(ClarificationChallengeForm, {
      props: {
        challenge: {
          clarificationId: 'clar_text',
          prompt: '请补充说明',
          fields: [
            { kind: 'TEXT', fieldId: 'field_note', label: '补充说明', required: true, limit: 20 },
          ],
        },
      },
    })
    const textarea = wrapper.find('textarea[data-clarification-text]')
    expect(textarea.attributes('maxlength')).toBe('20')
    expect(wrapper.find('[data-clarification-text-count]').text()).toBe('0/20')
  })

  it('local clarification 与 critical 共用同一表单（opaque id 不含内部字段）', () => {
    const local = localChallengeOf('answer-local-clarification.json')
    const wrapper = mount(ClarificationChallengeForm, { props: { challenge: local } })
    expect(wrapper.find('p.clarification-form__prompt').text()).toBe('请选择另一个要比较的公开项目。')
    expect(wrapper.text()).not.toContain('promptCode')
    expect(wrapper.text()).not.toContain('fieldKey')
  })

  // A2-18：CONSUMED/SUPERSEDED 只渲染只读摘要，不提供任何提交入口。
  it('CONSUMED/SUPERSEDED 状态渲染只读卡且无表单与提交按钮', () => {
    const turn = parseGoldenFixture('clarification.json')
    if (turn.kind !== 'CLARIFICATION') throw new Error('期望 CLARIFICATION')
    const challenge = turn.clarification
    const consumed = mount(ClarificationChallengeForm, {
      props: { challenge, state: 'CONSUMED' },
    })
    expect(consumed.find('[data-testid="clarification-readonly"]').exists()).toBe(true)
    expect(consumed.attributes('data-clarification-state')).toBe('CONSUMED')
    expect(consumed.text()).toContain('不可重复提交')
    expect(consumed.find('[data-testid="clarification-form"]').exists()).toBe(false)
    expect(consumed.find('button[data-clarification-submit]').exists()).toBe(false)

    const superseded = mount(ClarificationChallengeForm, {
      props: { challenge, state: 'SUPERSEDED' },
    })
    expect(superseded.attributes('data-clarification-state')).toBe('SUPERSEDED')
    expect(superseded.text()).toContain('已被后续轮次取代')
    expect(superseded.find('button[data-clarification-submit]').exists()).toBe(false)
  })
})
