import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TaskStatusSummary from './TaskStatusSummary.vue'

describe('TaskStatusSummary', () => {
  it('uses text and shape classes for completed, insufficient, and blocked tasks', () => {
    const wrapper = mount(TaskStatusSummary, {
      props: {
        summary: {
          displayMode: 'EXPANDED', totalCount: 3, answeredCount: 1, notSupportedCount: 1,
          emptyCount: 0, blockedCount: 1, failedCount: 0, cancelledCount: 0, degradedCount: 0,
          items: [
            { displayIndex: '01', goalLabel: 'SQL 审阅', status: 'COMPLETED', sourceDomain: 'PORTFOLIO' },
            { displayIndex: '02', goalLabel: '通用比较', status: 'NOT_SUPPORTED', sourceDomain: 'GENERAL' },
            { displayIndex: '03', goalLabel: '综合建议', status: 'BLOCKED', sourceDomain: 'SYNTHESIS' },
          ],
        },
      },
    })

    expect(wrapper.get('[data-task-status="COMPLETED"]').text()).toContain('已完成')
    expect(wrapper.get('[data-task-status="NOT_SUPPORTED"]').text()).toContain('证据不足')
    expect(wrapper.get('[data-task-status="BLOCKED"]').text()).toContain('被阻塞')
    expect(wrapper.get('[data-task-status="NOT_SUPPORTED"]').text()).toContain('通用知识')
    expect(wrapper.get('[data-task-status="BLOCKED"]').text()).toContain('综合结论')
    expect(wrapper.html()).not.toContain('task-01')
  })
})
