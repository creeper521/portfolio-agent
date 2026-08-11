import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PlanConfirmation from './PlanConfirmation.vue'

const confirmation = {
  confirmationId: 'confirmation-01',
  confirmationPlan: 'opaque-envelope',
  planFingerprint: 'sha256:opaque-fingerprint',
  integrityToken: 'opaque-integrity-token',
  expiresAt: '2026-08-10T12:10:00Z',
}

describe('PlanConfirmation', () => {
  it('renders a numbered linear plan and emits only the opaque confirmation on keyboard activation', async () => {
    const wrapper = mount(PlanConfirmation, {
      props: {
        plan: {
          taskCount: 4,
          executableTaskCount: 4,
          constraints: ['只使用公开作品集资料'],
          tasks: [
            { displayIndex: '01', goalLabel: '审阅 SQL 项目', sourceDomain: 'PORTFOLIO', dependencySummary: null },
            { displayIndex: '02', goalLabel: '比较两个项目', sourceDomain: 'PORTFOLIO', dependencySummary: '需要 01 先完成' },
          ],
        },
        confirmation,
      },
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
      props: {
        plan: { taskCount: 1, executableTaskCount: 1, constraints: [], tasks: [] },
        confirmation,
      },
    })

    await wrapper.get('[data-action="adjust-plan"]').trigger('click')
    await wrapper.get('[data-action="cancel-plan"]').trigger('click')
    expect(wrapper.emitted('adjust')).toEqual([[]])
    expect(wrapper.emitted('cancel')).toEqual([[]])
  })
})
