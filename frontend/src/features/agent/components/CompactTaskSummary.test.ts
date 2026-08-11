import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CompactTaskSummary from './CompactTaskSummary.vue'

const summary = {
  displayMode: 'COLLAPSED' as const,
  totalCount: 3,
  answeredCount: 2,
  notSupportedCount: 0,
  emptyCount: 0,
  blockedCount: 1,
  failedCount: 0,
  cancelledCount: 0,
  degradedCount: 0,
  items: [
    { displayIndex: '01', goalLabel: '已完成任务', status: 'COMPLETED' as const, sourceDomain: 'PORTFOLIO' as const },
    { displayIndex: '02', goalLabel: '被阻塞任务', status: 'BLOCKED' as const, sourceDomain: 'GENERAL' as const },
  ],
}

describe('CompactTaskSummary', () => {
  it('starts collapsed for fully successful small plans and toggles aria-expanded', async () => {
    const wrapper = mount(CompactTaskSummary, { props: { summary: { ...summary, blockedCount: 0 } } })
    const toggle = wrapper.get('[data-testid="task-summary-toggle"]')

    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('false')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('true')
  })

  it('starts expanded for partial results and keeps the completed count in its collapsed label', async () => {
    const wrapper = mount(CompactTaskSummary, { props: { summary } })
    const toggle = wrapper.get('[data-testid="task-summary-toggle"]')

    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('true')
    expect(toggle.text()).toContain('2/3 完成')
    await toggle.trigger('click')
    expect(toggle.text()).toContain('2/3 完成')
  })
})
