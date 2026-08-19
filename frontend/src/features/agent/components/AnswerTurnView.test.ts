import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { AnswerTurn } from '../model/publicAgentTurn'
import AnswerTurnView from './AnswerTurnView.vue'

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

function answerTurnOf(fileName: string): AnswerTurn {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'ANSWER') throw new Error('期望 ANSWER')
  return turn
}

function mountAnswer(fileName: string) {
  return mount(AnswerTurnView, {
    props: { turn: answerTurnOf(fileName) },
    attachTo: document.body,
    global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
  })
}

describe('AnswerTurnView', () => {
  it('多 Goal 按后端顺序分组，不显示技术状态标签（D-41.2）', () => {
    const wrapper = mountAnswer('answer-complete.json')
    const goals = wrapper.findAll('.goal-result')
    expect(goals).toHaveLength(2)
    expect(goals[0]?.find('.goal-result__label').text()).toBe('介绍 SQL 审计项目')
    expect(goals[1]?.find('.goal-result__label').text()).toBe('推荐一个代表项目')
    expect(wrapper.find('[data-testid="answer-progress"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('COMPLETE')
    expect(wrapper.text()).not.toContain('已切换到基础回答')
  })

  it('PARTIAL 顶部显示"已完成 N/M 个目标"，FULL Goal 不被整体染红', () => {
    const wrapper = mountAnswer('answer-partial.json')
    expect(wrapper.find('[data-testid="answer-progress"]').text()).toBe('已完成 1/2 个目标')
    const goals = wrapper.findAll('.goal-result')
    expect(goals[0]?.attributes('data-goal-coverage')).toBe('FULL')
    expect(goals[0]?.find('[data-testid="goal-coverage"]').exists()).toBe(false)
    expect(goals[1]?.find('[data-testid="goal-coverage"]').text()).toContain('未完成')
  })

  it('NO_RESULT 不生成空正文：显示 Goal、原因与恢复动作', () => {
    const wrapper = mountAnswer('answer-no-result.json')
    expect(wrapper.find('.goal-result__label').text()).toBe('查找未公开的验收结论')
    expect(wrapper.find('.goal-result__notice').text()).toContain('当前公开材料不足以支持这个结论。')
    expect(wrapper.find('[data-testid="sectioned-presentation"]').exists()).toBe(false)
    const action = wrapper.find('button[data-action-id="ask-public-scope"]')
    expect(action.exists()).toBe(true)
  })

  it('local clarification 贴在首个受影响 Goal 下，并说明其余目标继续', () => {
    const wrapper = mountAnswer('answer-local-clarification.json')
    const goals = wrapper.findAll('.goal-result')
    expect(goals).toHaveLength(2)
    expect(goals[0]?.find('[data-testid="clarification-form"]').exists()).toBe(false)
    const form = goals[1]?.find('[data-testid="clarification-form"]')
    expect(form.exists()).toBe(true)
    expect(goals[1]?.find('.answer-turn__continued').text()).toContain('其余 1 个目标将继续执行')
  })

  it('查看全部来源按钮打开抽屉，Esc 关闭', async () => {
    const wrapper = mountAnswer('answer-complete.json')
    expect(wrapper.find('[data-testid="source-drawer"]').exists()).toBe(false)
    await wrapper.find('[data-testid="open-source-drawer"]').trigger('click')
    expect(wrapper.find('[data-testid="source-drawer"]').exists()).toBe(true)
    expect(wrapper.findAll('.source-drawer__item')).toHaveLength(2)
    await wrapper.find('[data-testid="source-drawer"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[data-testid="source-drawer"]').exists()).toBe(false)
  })

  it('SuggestedAction 事件原样转发（含 continuation）', async () => {
    const wrapper = mountAnswer('answer-complete.json')
    await wrapper.find('button[data-action-id="continue-verification"]').trigger('click')
    const emitted = wrapper.emitted('select-action')
    expect(emitted).toHaveLength(1)
    expect(emitted?.[0]?.[0]).toEqual({
      actionId: 'continue-verification',
      label: '继续了解验证方式',
      inputText: '继续介绍验证方式',
      continuation: { contextHandle: 'ctx_fixture_overview' },
    })
  })
})
