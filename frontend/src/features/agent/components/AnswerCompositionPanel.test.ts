import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { CompletedTaskView } from '../model/semanticTurnView'
import AnswerCompositionPanel from './AnswerCompositionPanel.vue'

const completedTasks: CompletedTaskView[] = [
  {
    displayIndex: '01',
    goalLabel: '介绍项目',
    sourceDomain: 'PORTFOLIO',
    resultPayload: { kind: 'SECTION_RESULT', blocks: [] },
    fulfillmentRole: 'PRIMARY',
    supportSummary: { kind: 'VERIFIED_PUBLIC_EVIDENCE', statementCount: 3, publicSourceCount: 2 },
  },
  {
    displayIndex: '02',
    goalLabel: '综合建议',
    sourceDomain: 'SYNTHESIS',
    resultPayload: { kind: 'SECTION_RESULT', blocks: [] },
    fulfillmentRole: 'SUPPORTING',
    supportSummary: { kind: 'DERIVED_FROM_TASKS', statementCount: 2, publicSourceCount: 1, sourceTaskCount: 2 },
  },
]

describe('AnswerCompositionPanel', () => {
  it('renders the collapsed trust-layer entry with the composition badge and task roles', () => {
    const wrapper = mount(AnswerCompositionPanel, {
      props: {
        sourceComposition: 'CROSS_DOMAIN_DERIVED',
        completedTasks,
        degradationSummary: { degraded: true, kinds: ['RETRIEVAL_FALLBACK'], affectedTaskIds: [] },
        caveats: [{ code: 'C1', message: 'm', appliesToBlockIds: [], sourceTaskIds: [] }],
      },
    })

    expect(wrapper.get('[data-testid="answer-composition-panel"]').text()).toContain('回答构成')
    expect(wrapper.get('[data-composition]').text()).toContain('跨域派生')
    // 任务角色与来源域
    expect(wrapper.get('[data-task-index="01"] [data-role="PRIMARY"]').text()).toBe('主')
    expect(wrapper.get('[data-task-index="02"] [data-role="SUPPORTING"]').text()).toBe('辅')
    // 支持聚合计数
    expect(wrapper.get('[data-task-index="01"] [data-support="VERIFIED_PUBLIC_EVIDENCE"]').text())
      .toContain('3 条陈述')
    // 降级 kinds + 限定语计数
    expect(wrapper.get('[data-degradation-kinds]').text()).toContain('检索回退')
    expect(wrapper.get('[data-caveat-summary]').text()).toContain('1 条')
  })

  it('omits the composition row when sourceComposition is absent', () => {
    const wrapper = mount(AnswerCompositionPanel, { props: { completedTasks } })
    expect(wrapper.find('[data-composition]').exists()).toBe(false)
    // 任务清单仍展示
    expect(wrapper.findAll('[data-task-index]')).toHaveLength(2)
  })

  it('hides degradation and caveat rows when none are provided', () => {
    const wrapper = mount(AnswerCompositionPanel, { props: { completedTasks } })
    expect(wrapper.find('[data-degradation-kinds]').exists()).toBe(false)
    expect(wrapper.find('[data-caveat-summary]').exists()).toBe(false)
  })

  it('does not badge a task role when fulfillmentRole is missing', () => {
    const tasks: CompletedTaskView[] = [
      { displayIndex: '01', goalLabel: '仅一个任务', sourceDomain: 'GENERAL', resultPayload: { kind: 'SECTION_RESULT', blocks: [] } },
    ]
    const wrapper = mount(AnswerCompositionPanel, { props: { completedTasks: tasks } })
    expect(wrapper.find('[data-role]').exists()).toBe(false)
  })
})
