import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CompactTaskSummary from './CompactTaskSummary.vue'

function item(displayIndex: string, goalLabel: string, status: string) {
  return {
    displayIndex,
    goalLabel,
    status: status as 'COMPLETED' | 'BLOCKED' | 'NOT_SUPPORTED',
    sourceDomain: 'PORTFOLIO' as const,
    reasonCodes: [],
    blockedByDisplayIndexes: [],
  }
}

const successSummary = {
  displayMode: 'COLLAPSED' as const,
  totalCount: 3,
  answeredCount: 3,
  notSupportedCount: 0,
  emptyCount: 0,
  blockedCount: 0,
  failedCount: 0,
  cancelledCount: 0,
  degradedCount: 0,
  items: [
    item('01', '介绍 SQL 审计', 'COMPLETED'),
    item('02', '介绍 ABTest', 'COMPLETED'),
    item('03', '比较两个项目', 'COMPLETED'),
  ],
}

const partialSummary = {
  displayMode: 'EXPANDED' as const,
  totalCount: 3,
  answeredCount: 2,
  notSupportedCount: 0,
  emptyCount: 0,
  blockedCount: 1,
  failedCount: 0,
  cancelledCount: 0,
  degradedCount: 0,
  items: [
    item('01', '介绍 SQL 审计', 'COMPLETED'),
    item('02', '介绍 ABTest', 'COMPLETED'),
    item('03', '比较两个项目', 'BLOCKED'),
  ],
}

describe('CompactTaskSummary', () => {
  it('collapses fully successful plans into a flow-arrow label and toggles aria-expanded', async () => {
    const wrapper = mount(CompactTaskSummary, { props: { summary: successSummary } })
    const toggle = wrapper.get('[data-testid="task-summary-toggle"]')

    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('false')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.text()).toContain('介绍 SQL 审计 → 介绍 ABTest → 比较两个项目 · 3 步已完成')
    await toggle.trigger('click')
    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('true')
  })

  it('starts expanded for partial results and keeps the anomaly counts in its collapsed label', async () => {
    const wrapper = mount(CompactTaskSummary, { props: { summary: partialSummary } })
    const toggle = wrapper.get('[data-testid="task-summary-toggle"]')

    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('true')
    expect(toggle.text()).toContain('2/3 完成')
    expect(toggle.text()).toContain('1 阻塞')
    await toggle.trigger('click')
    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('false')
    expect(toggle.text()).toContain('2/3 完成 · 1 阻塞')
  })
})
