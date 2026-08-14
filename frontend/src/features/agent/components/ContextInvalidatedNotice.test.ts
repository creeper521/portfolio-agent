import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ContextInvalidation } from '../model/answerTypes'
import ContextInvalidatedNotice from './ContextInvalidatedNotice.vue'

const invalidation: ContextInvalidation = {
  reasonCode: 'CONTEXT_RESULT_STALE',
  recoveryAction: 'RESTART_FROM_CURRENT_CONTENT',
  contextType: 'RECOMMENDATION',
  currentContentVersion: 'public-2026-08-13',
}

describe('ContextInvalidatedNotice', () => {
  it('renders the recovery card with the whitelisted reason and action label', () => {
    const wrapper = mount(ContextInvalidatedNotice, { props: { invalidation } })
    expect(wrapper.get('[data-testid="context-invalidated-notice"]').text())
      .toContain('对话上下文已失效')
    // 白名单原因短句
    expect(wrapper.text()).toContain('该上下文已与最新内容不兼容')
    // 恢复动作按钮文案
    expect(wrapper.get('[data-action="recover-context"]').text()).toBe('基于最新内容重新开始')
    expect(wrapper.attributes('data-recovery-action')).toBe('RESTART_FROM_CURRENT_CONTENT')
  })

  it('emits recover when the recovery button is clicked', async () => {
    const wrapper = mount(ContextInvalidatedNotice, { props: { invalidation } })
    await wrapper.get('[data-action="recover-context"]').trigger('click')
    expect(wrapper.emitted('recover')).toEqual([[]])
  })

  it('hides the recovery button and stays read-only', () => {
    const wrapper = mount(ContextInvalidatedNotice, {
      props: { invalidation, readonly: true },
    })
    expect(wrapper.find('[data-action="recover-context"]').exists()).toBe(false)
  })

  it('keeps the button disabled while pending', () => {
    const wrapper = mount(ContextInvalidatedNotice, {
      props: { invalidation, pending: true },
    })
    expect(wrapper.get('[data-action="recover-context"]').attributes('disabled')).toBeDefined()
  })

  it('falls back to a restrained generic sentence for an unknown reason code', () => {
    const wrapper = mount(ContextInvalidatedNotice, {
      props: { invalidation: { ...invalidation, reasonCode: 'SOME_FUTURE_CODE' } },
    })
    // 不暴露原始码；给出克制通用句
    expect(wrapper.text()).not.toContain('SOME_FUTURE_CODE')
    expect(wrapper.text()).toContain('请重新提问')
  })

  it('labels each recovery action in human terms', () => {
    const cases: Array<[string, string]> = [
      ['RESTART_FROM_CURRENT_CONTENT', '基于最新内容重新开始'],
      ['RESELECT_RESULTS', '重新选择结果'],
      ['REASK_WITHOUT_CONTEXT', '不带上下文重新提问'],
    ]
    for (const [action, label] of cases) {
      const wrapper = mount(ContextInvalidatedNotice, {
        props: { invalidation: { ...invalidation, recoveryAction: action as ContextInvalidation['recoveryAction'] } },
      })
      expect(wrapper.get('[data-action="recover-context"]').text()).toBe(label)
    }
  })
})
