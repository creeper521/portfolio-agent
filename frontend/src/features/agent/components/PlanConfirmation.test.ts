import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { DisplayPlanView } from '../model/semanticTurnView'
import PlanConfirmation from './PlanConfirmation.vue'

const confirmation = {
  confirmationId: 'confirmation-01',
  confirmationPlan: 'opaque-envelope',
  planFingerprint: 'sha256:opaque-fingerprint',
  integrityToken: 'opaque-integrity-token',
  expiresAt: '2026-08-10T12:10:00Z',
}

function plan(overrides: Partial<DisplayPlanView> = {}): DisplayPlanView {
  return {
    taskCount: 2,
    executableTaskCount: 2,
    summaryLabel: null,
    pendingPlanReference: { planId: 'plan-pending-01', planFingerprint: 'sha256:opaque-fingerprint' },
    constraints: ['只使用公开作品集资料'],
    tasks: [
      { displayIndex: '01', goalLabel: '审阅 SQL 项目', sourceDomain: 'PORTFOLIO', dependencySummary: null },
      { displayIndex: '02', goalLabel: '比较两个项目', sourceDomain: 'PORTFOLIO', dependencySummary: '需要 01 先完成' },
    ],
    ...overrides,
  }
}

describe('PlanConfirmation', () => {
  it('renders a numbered linear plan and emits only the opaque confirmation on keyboard activation', async () => {
    const wrapper = mount(PlanConfirmation, {
      props: { plan: plan(), confirmation },
    })

    expect(wrapper.get('[data-testid="plan-confirmation"]').text()).toContain('01')
    expect(wrapper.text()).toContain('需要 01 先完成')
    expect(wrapper.text()).toContain('作品集资料')
    expect(wrapper.html()).not.toContain('opaque-envelope')
    expect(wrapper.html()).not.toContain('taskId')

    await wrapper.get('[data-action="confirm-plan"]').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('confirm')?.[0]?.[0]).toEqual(confirmation)
  })

  it('exposes adjust and cancel as separate user choices', async () => {
    const wrapper = mount(PlanConfirmation, {
      props: { plan: plan(), confirmation },
    })

    await wrapper.get('[data-action="adjust-plan"]').trigger('click')
    await wrapper.get('[data-action="cancel-plan"]').trigger('click')
    expect(wrapper.emitted('adjust')).toEqual([[]])
    expect(wrapper.emitted('cancel')).toEqual([[]])
  })

  it('lists constraints before the task list so the decision premise comes first', () => {
    const wrapper = mount(PlanConfirmation, {
      props: { plan: plan(), confirmation },
    })

    const html = wrapper.html()
    expect(html.indexOf('只使用公开作品集资料')).toBeLessThan(html.indexOf('审阅 SQL 项目'))
  })

  it('prefers the backend summary label and falls back to a plain task count', () => {
    const withLabel = mount(PlanConfirmation, {
      props: { plan: plan({ summaryLabel: '从了解到推荐' }), confirmation },
    })
    expect(withLabel.text()).toContain('计划确认 · 2 步 · 从了解到推荐')

    const withoutLabel = mount(PlanConfirmation, {
      props: { plan: plan({ summaryLabel: null }), confirmation },
    })
    expect(withoutLabel.text()).toContain('计划确认 · 2 项任务')
  })

  it('marks the card as adjusting while keeping the confirm action operable', () => {
    const wrapper = mount(PlanConfirmation, {
      props: { plan: plan(), confirmation, adjusting: true },
    })

    expect(wrapper.text()).toContain('调整中 · 仍可确认原计划')
    expect(wrapper.get('[data-action="confirm-plan"]').attributes('disabled')).toBeUndefined()
  })

  it('disables adjustment when the plan reference is missing', async () => {
    const wrapper = mount(PlanConfirmation, {
      props: { plan: plan({ pendingPlanReference: null }), confirmation, adjustDisabled: true },
    })

    await wrapper.get('[data-action="adjust-plan"]').trigger('click')
    expect(wrapper.emitted('adjust')).toBeUndefined()
  })

  it('renders a read-only record without actions for superseded plans', async () => {
    const wrapper = mount(PlanConfirmation, {
      props: { plan: plan(), readonly: true },
    })

    expect(wrapper.text()).toContain('仅作记录')
    expect(wrapper.find('[data-action="confirm-plan"]').exists()).toBe(false)
    expect(wrapper.find('[data-action="adjust-plan"]').exists()).toBe(false)
    expect(wrapper.find('[data-action="cancel-plan"]').exists()).toBe(false)
  })
})
