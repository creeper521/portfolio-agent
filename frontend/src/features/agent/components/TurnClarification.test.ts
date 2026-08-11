import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TurnClarification from './TurnClarification.vue'

const field = {
  fieldKey: 'comparisonSubject',
  inputMode: 'SINGLE_CHOICE' as const,
  required: true,
  affectedGoalLabels: ['比较候选作品'],
  options: [
    { value: 'sql-audit', label: 'SQL 审计工具' },
    { value: 'image-upload', label: '图片上传平台' },
  ],
}

describe('TurnClarification', () => {
  it('explains that a local clarification has continued safe independent work', async () => {
    const wrapper = mount(TurnClarification, {
      props: { clarification: { scope: 'LOCAL', prompt: '请选择比较对象', fields: [field], blockedTaskCount: 1, continuingTaskCount: 1 } },
    })

    expect(wrapper.get('[data-testid="turn-clarification"]').text()).toContain('已继续 1 个可安全完成的任务')
    await wrapper.get('[data-clarification-option="sql-audit"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[{ fieldKey: 'comparisonSubject', value: 'sql-audit' }]])
  })

  it('states that critical clarification has not executed the plan', () => {
    const wrapper = mount(TurnClarification, {
      props: { clarification: { scope: 'CRITICAL', prompt: '请选择主体', fields: [field], blockedTaskCount: 2, continuingTaskCount: 0 } },
    })

    expect(wrapper.text()).toContain('在收到选择前不会执行这项计划')
    expect(wrapper.text()).toContain('比较候选作品')
    expect(wrapper.text()).toContain('受影响的下游目标')
  })
})
