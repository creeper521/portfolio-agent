import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { SuggestedAction } from '../model/publicAgentTurn'
import SuggestedActionRow from './SuggestedActionRow.vue'

const actionsOf = (fileName: string): readonly SuggestedAction[] => {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'CONVERSATIONAL' || turn.suggestedActions === undefined) {
    throw new Error('期望 CONVERSATIONAL 带 suggestedActions')
  }
  return turn.suggestedActions
}

describe('SuggestedActionRow', () => {
  it('按后端顺序渲染动作并原样转发整个 action（含 continuation）', async () => {
    const actions = actionsOf('conversational.json')
    const wrapper = mount(SuggestedActionRow, { props: { actions } })
    const buttons = wrapper.findAll('button[data-action-id]')
    expect(buttons).toHaveLength(1)
    expect(buttons[0]?.attributes('data-action-id')).toBe('ask-project-overview')
    expect(buttons[0]?.text()).toBe('了解代表项目')

    await buttons[0]?.trigger('click')
    const emitted = wrapper.emitted('select')
    expect(emitted).toHaveLength(1)
    expect(emitted?.[0]?.[0]).toEqual(actions[0])
  })

  it('有 continuation 的动作标记 data-has-continuation，无 continuation 不标记', () => {
    const turn = parseGoldenFixture('answer-complete.json')
    if (turn.kind !== 'ANSWER' || turn.answer.suggestedActions === undefined) {
      throw new Error('期望 ANSWER 带 suggestedActions')
    }
    const withContinuation = mount(SuggestedActionRow, {
      props: { actions: turn.answer.suggestedActions },
    })
    expect(
      withContinuation.find('button[data-action-id="continue-verification"]').attributes('data-has-continuation'),
    ).toBe('true')

    const without = mount(SuggestedActionRow, { props: { actions: actionsOf('conversational.json') } })
    expect(
      without.find('button[data-action-id="ask-project-overview"]').attributes('data-has-continuation'),
    ).toBeUndefined()
  })

  it('空动作列表不渲染容器', () => {
    const wrapper = mount(SuggestedActionRow, { props: { actions: [] } })
    expect(wrapper.find('[data-testid="suggested-actions"]').exists()).toBe(false)
  })
})
