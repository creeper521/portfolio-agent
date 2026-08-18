import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import {
  loadPublicAgentTurnGoldenFixtures,
  parseGoldenFixture,
} from '../model/publicAgentTurnFixtureLoader'
import { parsePublicAgentTurn } from '../model/publicAgentTurnMapper'
import type {
  PublicSourceCatalog,
  RecommendationPresentation,
} from '../model/publicAgentTurn'
import RecommendationPresentationView from './RecommendationPresentationView.vue'
import componentSource from './RecommendationPresentationView.vue?raw'

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

function recommendationOf(fileName: string): {
  presentation: RecommendationPresentation
  sourceCatalog: PublicSourceCatalog
} {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'ANSWER') throw new Error('期望 ANSWER')
  const goal = turn.answer.goalResults[1]
  if (goal?.presentation === undefined || goal.presentation.kind !== 'RECOMMENDATION') {
    throw new Error('期望 RECOMMENDATION presentation')
  }
  return { presentation: goal.presentation, sourceCatalog: turn.answer.sourceCatalog }
}

function parseMutatedAnswerComplete(
  mutate: (presentation: Record<string, unknown>) => void,
): { presentation: RecommendationPresentation; sourceCatalog: PublicSourceCatalog } {
  const raw = loadPublicAgentTurnGoldenFixtures().find(
    (fixture) => fixture.fileName === 'answer-complete.json',
  )
  if (raw === undefined) throw new Error('缺少 answer-complete.json')
  const clone = JSON.parse(JSON.stringify(raw.turn)) as Record<string, unknown>
  const goals = clone.answer as Record<string, unknown>
  const goalResults = goals.goalResults as Record<string, unknown>[]
  const presentation = goalResults[1]?.presentation as Record<string, unknown>
  mutate(presentation)
  const parsed = parsePublicAgentTurn(clone)
  if (!parsed.ok) throw new Error(`变异后应仍可解析：${parsed.error.violations.join('；')}`)
  if (parsed.turn.kind !== 'ANSWER') throw new Error('期望 ANSWER')
  const goal = parsed.turn.answer.goalResults[1]
  if (goal?.presentation === undefined || goal.presentation.kind !== 'RECOMMENDATION') {
    throw new Error('期望 RECOMMENDATION presentation')
  }
  return { presentation: goal.presentation, sourceCatalog: parsed.turn.answer.sourceCatalog }
}

describe('RecommendationPresentationView', () => {
  it('完整推荐：后端顺序渲染卡片，无数量缺口文案', () => {
    const { presentation, sourceCatalog } = recommendationOf('answer-complete.json')
    const wrapper = mount(RecommendationPresentationView, {
      props: { presentation, sourceCatalog },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    expect(wrapper.find('[data-testid="recommendation-count"]').exists()).toBe(false)
    const items = wrapper.findAll('[data-testid="recommendation-item"]')
    expect(items).toHaveLength(1)
    const link = items[0]?.find('.recommendation-presentation__item-link')
    expect(link?.text()).toBe('Agent 能力集成 MVP')
    expect(link?.attributes('href')).toBe('/projects/agent-capability-mvp')
    expect(items[0]?.text()).toContain('该项目集中展示了受约束 Agent 能力的工程实现。')
    expect(items[0]?.text()).toContain('具备完整的公开实现与验证材料')
    expect(items[0]?.find('[data-source-key="source-agent-mvp"]').text()).toBe(
      'E-02 · Agent 能力集成 MVP 公开交付证据集',
    )
    expect(wrapper.text()).not.toContain('item-goal-recommendation-1')
  })

  it('数量缺口只说明一次：计数 + incompleteReasons，不足额挂推荐顶部', () => {
    const { presentation, sourceCatalog } = parseMutatedAnswerComplete((p) => {
      p.requestedSize = 3
      p.incompleteReasons = ['符合约束的公开项目不足']
    })
    const wrapper = mount(RecommendationPresentationView, {
      props: { presentation, sourceCatalog },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    const gap = wrapper.find('[data-testid="recommendation-count"]')
    expect(gap.text()).toContain('已找到 1/3 项')
    expect(gap.text()).toContain('符合约束的公开项目不足')
    expect(wrapper.findAll('[data-testid="recommendation-count"]')).toHaveLength(1)
  })

  it('推荐卡片列表带窄屏单列响应式样式（jsdom 无法布局，断言 CSS 合同存在）', () => {
    const { presentation, sourceCatalog } = recommendationOf('answer-complete.json')
    const wrapper = mount(RecommendationPresentationView, {
      props: { presentation, sourceCatalog },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    expect(wrapper.find('.recommendation-presentation__items').exists()).toBe(true)
    expect(componentSource).toContain('@media (max-width: 640px)')
    expect(componentSource).toContain('grid-template-columns: 1fr')
  })
})
