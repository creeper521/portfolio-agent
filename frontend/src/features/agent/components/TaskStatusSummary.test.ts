import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { TaskSummaryItemView } from '../model/semanticTurnView'
import TaskStatusSummary from './TaskStatusSummary.vue'

function item(
  displayIndex: string,
  goalLabel: string,
  status: TaskSummaryItemView['status'],
  sourceDomain: TaskSummaryItemView['sourceDomain'],
  overrides: Partial<Pick<TaskSummaryItemView, 'reasonCodes' | 'blockedByDisplayIndexes'>> = {},
): TaskSummaryItemView {
  return {
    displayIndex,
    goalLabel,
    status,
    sourceDomain,
    reasonCodes: [],
    blockedByDisplayIndexes: [],
    ...overrides,
  }
}

function mountSummary(items: TaskSummaryItemView[]) {
  return mount(TaskStatusSummary, {
    props: {
      summary: {
        displayMode: 'EXPANDED', totalCount: items.length, answeredCount: 1, notSupportedCount: 1,
        emptyCount: 0, blockedCount: 1, failedCount: 0, cancelledCount: 0, degradedCount: 0,
        items,
      },
    },
  })
}

describe('TaskStatusSummary', () => {
  it('uses text and shape classes for completed, insufficient, and blocked tasks', () => {
    const wrapper = mountSummary([
      item('01', 'SQL 审阅', 'COMPLETED', 'PORTFOLIO'),
      item('02', '通用比较', 'NOT_SUPPORTED', 'GENERAL'),
      item('03', '综合建议', 'BLOCKED', 'SYNTHESIS'),
    ])

    expect(wrapper.get('[data-task-status="COMPLETED"]').text()).toContain('已完成')
    expect(wrapper.get('[data-task-status="NOT_SUPPORTED"]').text()).toContain('证据不足')
    expect(wrapper.get('[data-task-status="BLOCKED"]').text()).toContain('被阻塞')
    expect(wrapper.get('[data-task-status="NOT_SUPPORTED"]').text()).toContain('通用知识')
    expect(wrapper.get('[data-task-status="BLOCKED"]').text()).toContain('综合结论')
    expect(wrapper.html()).not.toContain('task-01')
  })

  it('shows whitelisted reason text and blocked-by references for non-completed tasks', () => {
    const wrapper = mountSummary([
      item('01', 'SQL 审阅', 'COMPLETED', 'PORTFOLIO'),
      item('02', 'ABTest 介绍', 'NOT_SUPPORTED', 'PORTFOLIO', {
        reasonCodes: ['PORTFOLIO_EVIDENCE_INSUFFICIENT'],
      }),
      item('03', '比较两个项目', 'BLOCKED', 'PORTFOLIO', {
        reasonCodes: ['EXECUTION_DEPENDENCY_BLOCKED'],
        blockedByDisplayIndexes: ['02'],
      }),
    ])

    const completed = wrapper.get('[data-task-status="COMPLETED"]')
    expect(completed.text()).not.toContain('依赖任务')
    expect(wrapper.get('[data-task-status="NOT_SUPPORTED"]').text()).toContain('公开证据不足，无法生成可信结论')
    expect(wrapper.get('[data-task-status="BLOCKED"]').text()).toContain('依赖任务 02 未完成，因此暂不执行')
  })

  it('never renders raw unknown reason codes', () => {
    const wrapper = mountSummary([
      item('01', 'SQL 审阅', 'COMPLETED', 'PORTFOLIO'),
      item('02', '通用比较', 'FAILED', 'GENERAL', {
        reasonCodes: ['UNREGISTERED_FUTURE_CODE'],
      }),
    ])

    const failed = wrapper.get('[data-task-status="FAILED"]')
    expect(failed.text()).toContain('该任务未能安全完成')
    expect(failed.text()).not.toContain('UNREGISTERED_FUTURE_CODE')
  })
})
