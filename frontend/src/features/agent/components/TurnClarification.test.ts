import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ClarificationView } from '../model/semanticTurnView'
import TurnClarification from './TurnClarification.vue'

function choiceField(overrides: Partial<ClarificationView['fields'][number]> = {}) {
  return {
    fieldKey: 'comparisonSubject',
    inputMode: 'SINGLE_CHOICE' as const,
    required: true,
    affectedGoalLabels: ['比较候选作品'],
    options: [
      {
        value: 'sql-audit',
        label: 'SQL 审计工具',
        subjectReference: { subjectType: 'PROJECT', subjectId: 'sql-audit' },
      },
      {
        value: 'image-upload',
        label: '图片上传平台',
        subjectReference: { subjectType: 'PROJECT', subjectId: 'image-upload' },
      },
    ],
    ...overrides,
  }
}

function clarification(overrides: Partial<ClarificationView> = {}): ClarificationView {
  return {
    clarificationId: 'clarify-0a1b2c3d4e5f60718293a4b5c6d7e8f9',
    scope: 'LOCAL',
    promptCode: 'ROUTING_COMPARISON_SUBJECT_MISSING',
    prompt: '请选择比较对象',
    fields: [choiceField()],
    blockedTaskCount: 1,
    continuingTaskCount: 1,
    continuingGoalLabels: [],
    blockedGoals: [],
    ...overrides,
  }
}

describe('TurnClarification', () => {
  it('names the continued safe tasks instead of only counting them', async () => {
    const wrapper = mount(TurnClarification, {
      props: {
        clarification: clarification({ continuingGoalLabels: ['介绍 SQL 审计项目'] }),
      },
    })

    expect(wrapper.text()).toContain('已继续：介绍 SQL 审计项目，不受影响')
    await wrapper.get('[data-clarification-option="sql-audit"]').trigger('click')
    const events = wrapper.emitted('submit')
    expect(events?.[0]?.[0]).toMatchObject({
      submission: {
        kind: 'CHOICE',
        fieldKey: 'comparisonSubject',
        option: { value: 'sql-audit' },
      },
    })
  })

  it('falls back to the continued count when goal labels are absent', () => {
    const wrapper = mount(TurnClarification, {
      props: { clarification: clarification({ continuingTaskCount: 2 }) },
    })

    expect(wrapper.text()).toContain('已继续 2 个可安全完成的任务')
  })

  it('lists blocked downstream goals with whitelisted reason text for critical clarifications', () => {
    const wrapper = mount(TurnClarification, {
      props: {
        clarification: clarification({
          scope: 'CRITICAL',
          continuingTaskCount: 0,
          blockedTaskCount: 2,
          blockedGoals: [
            { goalLabel: '比较两个项目', reasonCode: 'WAITING_FOR_COMPARISON_SUBJECT' },
            { goalLabel: '推荐一个项目', reasonCode: 'UNREGISTERED_REASON_CODE' },
          ],
        }),
      },
    })

    expect(wrapper.text()).toContain('在收到选择前不会执行这项计划')
    expect(wrapper.text()).toContain('比较两个项目')
    expect(wrapper.text()).toContain('等待你确认比较对象')
    // 未知码必须回落为克制通用句，不展示原字符串
    expect(wrapper.text()).toContain('等待你补充信息')
    expect(wrapper.text()).not.toContain('UNREGISTERED_REASON_CODE')
  })

  it('degrades historical cards to a read-only record without submitting', async () => {
    const wrapper = mount(TurnClarification, {
      props: { clarification: clarification(), readonly: true },
    })

    expect(wrapper.text()).toContain('仅作记录')
    await wrapper.get('[data-clarification-option="sql-audit"]').trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('announces the pending state after submission', () => {
    const wrapper = mount(TurnClarification, {
      props: { clarification: clarification(), pending: true },
    })

    expect(wrapper.text()).toContain('已提交，正在按你的选择重新规划')
    expect(wrapper.get('[data-clarification-option="sql-audit"]').attributes('disabled')).toBeDefined()
  })
})
