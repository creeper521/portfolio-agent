import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { AnswerGoalResult, PublicSourceCatalog } from '../model/publicAgentTurn'
import GoalResultView from './GoalResultView.vue'

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

function goalOf(fileName: string, index = 0): {
  goal: AnswerGoalResult
  sourceCatalog: PublicSourceCatalog
} {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'ANSWER') throw new Error('期望 ANSWER')
  const goal = turn.answer.goalResults[index]
  if (goal === undefined) throw new Error(`缺少 goalResults[${index}]`)
  return { goal, sourceCatalog: turn.answer.sourceCatalog }
}

function mountGoal(fileName: string, index = 0) {
  const { goal, sourceCatalog } = goalOf(fileName, index)
  return mount(GoalResultView, {
    props: { goal, sourceCatalog },
    global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
  })
}

describe('GoalResultView', () => {
  it('FULL Goal 极简：显示 label 与正文，不显示覆盖标签（D-41.1）', () => {
    const wrapper = mountGoal('answer-complete.json')
    expect(wrapper.find('h3.goal-result__label').text()).toBe('介绍 SQL 审计项目')
    expect(wrapper.find('[data-testid="goal-coverage"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('项目背景')
    // 不出现技术性标签文本
    expect(wrapper.text()).not.toContain('FULL')
    expect(wrapper.text()).not.toContain('READY')
  })

  it('NONE Goal 不渲染 Presentation，显示用户安全 notice（D-41.4）', () => {
    const wrapper = mountGoal('answer-partial.json', 1)
    expect(wrapper.attributes('data-goal-coverage')).toBe('NONE')
    expect(wrapper.find('[data-testid="goal-coverage"]').text()).toContain('未完成')
    expect(wrapper.find('[data-testid="sectioned-presentation"]').exists()).toBe(false)
    const notice = wrapper.find('.goal-result__notice')
    expect(notice.attributes('data-notice-code')).toBe('OUT_OF_SCOPE')
    expect(notice.text()).toContain('当前能力不查询实时外部版本信息。')
  })

  it('heading 层级为 h3，不依赖颜色表达覆盖状态', () => {
    const wrapper = mountGoal('answer-no-result.json')
    expect(wrapper.find('h3').exists()).toBe(true)
    const coverage = wrapper.find('[data-testid="goal-coverage"]')
    expect(coverage.text()).toMatch(/未完成/)
  })
})
