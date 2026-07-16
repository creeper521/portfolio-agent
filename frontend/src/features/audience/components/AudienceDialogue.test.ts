import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import AudienceDialogue from './AudienceDialogue.vue'

describe('AudienceDialogue', () => {
  it('changes recommended questions with the selected visitor role', async () => {
    const wrapper = mount(AudienceDialogue, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })

    await wrapper.get('[data-role="MENTOR"]').trigger('click')

    expect(wrapper.get('[data-current-role]').attributes('data-current-role')).toBe('MENTOR')
    expect(wrapper.text()).toContain('你如何复盘这个项目')
  })

  it('shows an evidence-backed summary after choosing a question', async () => {
    const wrapper = mount(AudienceDialogue, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })

    expect(wrapper.findAll('[data-question]')).toHaveLength(4)
    expect(wrapper.get('[data-role="INTERVIEWER"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-role="INTERVIEWER"]').classes()).toContain('role-button--active')

    await wrapper.get('[data-question]').trigger('click')

    expect(wrapper.get('[data-light-answer]').text()).toContain('SQL 审计')
    expect(wrapper.get('[data-light-answer]').text()).toContain('E-01')
    expect(wrapper.findAll('[data-answer-action]')).toHaveLength(3)
  })

  it('accepts a free-form homepage question', async () => {
    const wrapper = mount(AudienceDialogue, {
      props: { portfolio: previewPublicContent },
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })

    await wrapper.get('[data-custom-question]').setValue('如何处理连接异常？')
    await wrapper.get('[data-question-form]').trigger('submit')

    expect(wrapper.get('[data-light-answer]').text()).toContain('如何处理连接异常？')
  })
})
