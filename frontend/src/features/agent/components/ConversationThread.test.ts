import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { AgentMessage } from '../model/sessionTypes'
import ConversationThread from './ConversationThread.vue'

// D-41.14：ConversationThread 只负责列表、scroll、pending 与事件转发；
// 业务投影在 PublicAgentTurnMessage 组件树内。

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

function buildMessages(): AgentMessage[] {
  return [
    {
      id: 'message-1',
      role: 'USER',
      content: '介绍 SQL 审计项目',
      createdAt: 1,
    },
    {
      id: 'message-2',
      role: 'AGENT',
      content: '介绍 SQL 审计项目',
      turn: parseGoldenFixture('answer-complete.json'),
      createdAt: 2,
    },
    {
      id: 'message-3',
      role: 'AGENT',
      content: '边界回复',
      turn: parseGoldenFixture('boundary.json'),
      createdAt: 3,
    },
  ]
}

function mountThread(messages: AgentMessage[], pending = false, pendingQuestion = '') {
  return mount(ConversationThread, {
    props: { messages, pending, pendingQuestion },
    global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
  })
}

describe('ConversationThread', () => {
  it('按顺序渲染 USER 气泡与闭合 PublicAgentTurn，不做业务投影', () => {
    const wrapper = mountThread(buildMessages())
    const items = wrapper.findAll('.conversation-thread__item')
    expect(items).toHaveLength(3)
    expect(items[0]?.attributes('data-message-role')).toBe('USER')
    expect(items[0]?.text()).toBe('介绍 SQL 审计项目')
    expect(items[1]?.attributes('data-message-role')).toBe('AGENT')
    expect(items[1]?.find('[data-testid="answer-turn"]').exists()).toBe(true)
    expect(items[2]?.find('[data-testid="boundary-turn"]').exists()).toBe(true)
    // 旧链技术轴不出现（旧 UX 文案与任务快照痕迹）。
    expect(wrapper.text()).not.toContain('已切换到基础回答')
    expect(wrapper.text()).not.toContain('任务摘要')
  })

  it('pending 时显示状态与取消按钮并转发 cancel，完成后消失', async () => {
    const wrapper = mountThread([], true, '介绍 SQL 审计项目')
    const pending = wrapper.find('[data-testid="conversation-pending"]')
    expect(pending.attributes('role')).toBe('status')
    expect(pending.text()).toContain('介绍 SQL 审计项目')
    await wrapper.find('[data-testid="cancel-turn"]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)

    await wrapper.setProps({ pending: false, pendingQuestion: '' })
    expect(wrapper.find('[data-testid="conversation-pending"]').exists()).toBe(false)
  })

  it('转发组件树的 select-action 与 submit-clarification，不重建协议', async () => {
    const wrapper = mountThread(buildMessages())
    await wrapper.find('button[data-action-id="continue-verification"]').trigger('click')
    expect(wrapper.emitted('select-action')).toHaveLength(1)
    expect(wrapper.emitted('select-action')?.[0]?.[0]).toMatchObject({
      actionId: 'continue-verification',
    })
  })

  it('无消息时不渲染空占位正文', () => {
    const wrapper = mountThread([])
    expect(wrapper.findAll('.conversation-thread__item')).toHaveLength(0)
  })
})
