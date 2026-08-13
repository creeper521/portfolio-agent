import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { ExecutionDisplayPlanView } from '../model/semanticTurnView'
import ExecutionSnapshot from './ExecutionSnapshot.vue'

// P3 用户可见执行快照（FINAL，handoff §7/§17.15-17.16）。
// 只渲染服务端最终阶段状态；不展示拟真的工具调用、百分比或思维链。
function finalPlan(): ExecutionDisplayPlanView {
  return {
    overallStatus: 'COMPLETED',
    tasks: [
      {
        displayIndex: '01',
        finalStatus: 'COMPLETED',
        stages: [
          { code: 'SCOPE_CONFIRMED', label: '确认查询范围', status: 'COMPLETED' },
          { code: 'MATERIALS_RETRIEVED', label: '查找已发布材料', status: 'COMPLETED' },
          { code: 'EVIDENCE_VALIDATED', label: '核验证据', status: 'COMPLETED' },
          { code: 'RESULT_COMPOSED', label: '形成回答', status: 'COMPLETED' },
        ],
      },
    ],
  }
}

describe('ExecutionSnapshot', () => {
  it('renders the four FINAL stages with their labels and status codes', () => {
    const wrapper = mount(ExecutionSnapshot, {
      props: { execution: finalPlan() },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.find('[data-execution-snapshot]').exists()).toBe(true)
    const codes = wrapper.findAll('[data-stage-code]').map((node) => node.attributes('data-stage-code'))
    expect(codes).toEqual([
      'SCOPE_CONFIRMED', 'MATERIALS_RETRIEVED', 'EVIDENCE_VALIDATED', 'RESULT_COMPOSED',
    ])
    // 每个 stage 标注其最终状态（用于 a11y 与测试）。
    expect(wrapper.find('[data-stage-code="MATERIALS_RETRIEVED"]').attributes('data-stage-status'))
      .toBe('COMPLETED')
  })

  it('shows the overall status summary', () => {
    const wrapper = mount(ExecutionSnapshot, {
      props: { execution: { ...finalPlan(), overallStatus: 'PARTIAL' } },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.find('[data-execution-overall]').attributes('data-execution-overall'))
      .toBe('PARTIAL')
  })

  it('does not render live progress, percentages or fake tool logs', () => {
    const wrapper = mount(ExecutionSnapshot, {
      props: { execution: finalPlan() },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    const html = wrapper.html()
    expect(html).not.toContain('%')
    expect(html).not.toMatch(/检索中|进行中|thinking|tool/i)
  })
})
