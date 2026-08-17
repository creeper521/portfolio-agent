import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { ExecutionDisplayPlanView } from '../model/semanticTurnView'
import ExecutionSnapshot from './ExecutionSnapshot.vue'

// P3 用户可见执行快照（FINAL，handoff §7/17.15-17.16）+ 2026-08-17 体验闭环：
// 成功默认收起、异常自动展开、任务名优先于阶段、同一任务只出现一次、无绿色状态色。
function finalPlan(overrides: Partial<ExecutionDisplayPlanView> = {}): ExecutionDisplayPlanView {
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
    ...overrides,
  }
}

function mountSnapshot(execution: ExecutionDisplayPlanView, taskLabels?: Record<string, string>) {
  return mount(ExecutionSnapshot, {
    props: taskLabels ? { execution, taskLabels } : { execution },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
}

describe('ExecutionSnapshot', () => {
  it('renders the four FINAL stages with their labels and status codes after expanding', async () => {
    const wrapper = mountSnapshot(finalPlan())
    expect(wrapper.find('[data-execution-snapshot]').exists()).toBe(true)
    expect(wrapper.find('[data-stage-code]').exists()).toBe(false)
    await wrapper.find('[data-execution-toggle]').trigger('click')
    const codes = wrapper.findAll('[data-stage-code]').map((node) => node.attributes('data-stage-code'))
    expect(codes).toEqual([
      'SCOPE_CONFIRMED', 'MATERIALS_RETRIEVED', 'EVIDENCE_VALIDATED', 'RESULT_COMPOSED',
    ])
    // 每个 stage 标注其最终状态（用于 a11y 与测试）。
    expect(wrapper.find('[data-stage-code="MATERIALS_RETRIEVED"]').attributes('data-stage-status'))
      .toBe('COMPLETED')
  })

  it('shows the overall status summary', () => {
    const wrapper = mountSnapshot(finalPlan({ overallStatus: 'PARTIAL' }))
    expect(wrapper.find('[data-execution-overall]').attributes('data-execution-overall'))
      .toBe('PARTIAL')
  })

  it('does not render live progress, percentages or fake tool logs', async () => {
    const wrapper = mountSnapshot(finalPlan())
    await wrapper.find('[data-execution-toggle]').trigger('click')
    const html = wrapper.html()
    expect(html).not.toContain('%')
    expect(html).not.toMatch(/检索中|进行中|thinking|tool/i)
  })
})

describe('ExecutionSnapshot · 体验闭环（2026-08-17）', () => {
  it('成功（COMPLETED）默认收起，阶段明细不外露', () => {
    const wrapper = mountSnapshot(finalPlan())
    const toggle = wrapper.find('[data-execution-toggle]')
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[data-stage-code]').exists()).toBe(false)
    // 收起条上仍有可读的整体状态文字（不只靠展开）。
    expect(wrapper.find('[data-execution-toggle]').text()).toContain('已完成')
  })

  it('部分完成与失败时自动展开', () => {
    for (const overallStatus of ['PARTIAL', 'FAILED'] as const) {
      const wrapper = mountSnapshot(finalPlan({ overallStatus }))
      expect(wrapper.find('[data-execution-toggle]').attributes('aria-expanded')).toBe('true')
      expect(wrapper.find('[data-stage-code]').exists()).toBe(true)
    }
  })

  it('展示任务名（goalLabel），任务名优先于阶段重复', async () => {
    const wrapper = mountSnapshot(finalPlan(), { '01': '介绍 SQL 审计与故障排查工具' })
    await wrapper.find('[data-execution-toggle]').trigger('click')
    expect(wrapper.find('[data-execution-task="01"]').text()).toContain('介绍 SQL 审计与故障排查工具')
  })

  it('无 taskLabels 时不编造任务名，仍显示任务编号与状态', async () => {
    const wrapper = mountSnapshot(finalPlan())
    await wrapper.find('[data-execution-toggle]').trigger('click')
    const task = wrapper.find('[data-execution-task="01"]')
    expect(task.exists()).toBe(true)
    expect(task.find('.execution-snapshot__task-name').text()).toBe('')
    expect(task.find('.execution-snapshot__task-status').text()).toBe('完成')
  })

  it('同一任务（displayIndex）只渲染一次', async () => {
    const duplicated = finalPlan({
      tasks: [
        finalPlan().tasks[0],
        { ...finalPlan().tasks[0] },
      ],
    })
    const wrapper = mountSnapshot(duplicated)
    await wrapper.find('[data-execution-toggle]').trigger('click')
    expect(wrapper.findAll('[data-execution-task]').length).toBe(1)
  })

  it('状态不只依赖颜色：任务与阶段都有文字状态', () => {
    const wrapper = mountSnapshot(finalPlan({ overallStatus: 'PARTIAL' }))
    const stage = wrapper.find('[data-stage-code="EVIDENCE_VALIDATED"]')
    expect(stage.attributes('data-stage-status')).toBe('COMPLETED')
    expect(stage.text()).toContain('完成')
  })

  it('源码契约：不使用绿色状态色', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/agent/components/ExecutionSnapshot.vue'),
      'utf8',
    )
    expect(source).not.toContain('#157a3b')
    expect(source.toLowerCase()).not.toContain('green')
  })
})
