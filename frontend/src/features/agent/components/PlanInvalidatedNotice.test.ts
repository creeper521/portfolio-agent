import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PlanInvalidatedNotice from './PlanInvalidatedNotice.vue'

describe('PlanInvalidatedNotice', () => {
  it('requires an explicit regeneration action instead of replacing a plan silently', async () => {
    const wrapper = mount(PlanInvalidatedNotice, {
      props: { planChange: { summary: '公开内容已更新，需要重新生成计划', changeLabels: ['内容版本变化'] } },
    })

    expect(wrapper.get('[data-testid="plan-invalidated-notice"]').text()).toContain('重新生成')
    await wrapper.get('[data-action="regenerate-plan"]').trigger('click')
    expect(wrapper.emitted('regenerate')).toEqual([[]])
  })
})
