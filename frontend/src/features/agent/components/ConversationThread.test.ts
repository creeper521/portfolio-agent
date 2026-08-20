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

function mountThread(
  messages: AgentMessage[],
  pending = false,
  pendingQuestion = '',
  fallbackPresets?: ReadonlyArray<{ text: string; presetId?: string }>,
) {
  return mount(ConversationThread, {
    props: { messages, pending, pendingQuestion, ...(fallbackPresets === undefined ? {} : { fallbackPresets }) },
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

  // B6：滚动纪律——用户上滑停止自动跟随并出现"回到最新回答"，回底部恢复跟随。
  it('用户上滑后停止自动滚动并显示回到最新，点击后恢复跟随', async () => {
    const wrapper = mountThread(buildMessages())
    const scroller = wrapper.get('[data-testid="conversation-thread"]').element as HTMLElement
    Object.defineProperty(scroller, 'scrollHeight', { value: 1000, configurable: true })
    Object.defineProperty(scroller, 'clientHeight', { value: 400, configurable: true })
    scroller.scrollTop = 100
    await wrapper.vm.$nextTick()

    await scroller.dispatchEvent(new Event('scroll'))
    expect(wrapper.find('[data-testid="jump-latest"]').exists()).toBe(true)

    // 跟随已暂停：新消息到达不强制滚底。
    await wrapper.setProps({ messages: [...buildMessages(), {
      id: 'message-4', role: 'USER', content: '新消息', createdAt: 4,
    }] })
    expect(scroller.scrollTop).toBe(100)

    await wrapper.get('[data-testid="jump-latest"]').trigger('click')
    expect(scroller.scrollTop).toBe(1000)
    expect(wrapper.find('[data-testid="jump-latest"]').exists()).toBe(false)
  })

  it('用户停留在底部附近时保持跟随，不出现回到最新按钮', async () => {
    const wrapper = mountThread(buildMessages())
    const scroller = wrapper.get('[data-testid="conversation-thread"]').element as HTMLElement
    Object.defineProperty(scroller, 'scrollHeight', { value: 1000, configurable: true })
    Object.defineProperty(scroller, 'clientHeight', { value: 400, configurable: true })
    scroller.scrollTop = 950
    await scroller.dispatchEvent(new Event('scroll'))
    expect(wrapper.find('[data-testid="jump-latest"]').exists()).toBe(false)
  })

  // B7：focusTarget 定位回答内 section 并短暂高亮。
  it('focusTarget 定位对应 section 并添加高亮类', async () => {
    const wrapper = mountThread(buildMessages())
    await wrapper.setProps({ focusTarget: { sectionId: 'section-background', nonce: 1 } })
    await wrapper.vm.$nextTick()
    const section = wrapper.get('[data-section-id="section-background"]')
    expect(section.classes()).toContain('conversation-thread--located')
  })

  // A2-18：只有最新未消费的挑战卡可操作；历史卡只读。
  it('多张澄清卡时只有最新未消费卡可提交，其余只读', () => {
    const messages: AgentMessage[] = [
      { id: 'message-1', role: 'USER', content: '问题', createdAt: 1 },
      {
        id: 'message-2',
        role: 'AGENT',
        content: '澄清1',
        turn: parseGoldenFixture('clarification.json'),
        createdAt: 2,
      },
      {
        id: 'message-3',
        role: 'AGENT',
        content: '澄清2',
        turn: parseGoldenFixture('clarification.json'),
        createdAt: 3,
      },
    ]
    const wrapper = mountThread(messages, false, '', [
      { text: '请介绍 SQL 审计工具的完整迭代过程。', presetId: 'sql-audit-overview' },
    ])
    const states = wrapper.findAll('[data-testid="clarification-turn"]')
    expect(states).toHaveLength(2)
    expect(states[0]?.find('[data-clarification-state="SUPERSEDED"]').exists()).toBe(true)
    // 脱困预设只出现在最新 ACTIVE 卡上，历史卡不重复提供入口。
    expect(states[0]?.find('[data-testid="clarification-preset-fallback"]').exists()).toBe(false)
    expect(states[1]?.find('[data-testid="clarification-form"]').exists()).toBe(true)
    expect(states[1]?.find('[data-testid="clarification-preset-fallback"]').exists()).toBe(true)

    // 最新卡提交后（consumed 标记）转只读摘要，脱困入口一并消失。
    const consumed: AgentMessage[] = messages.map((message) =>
      message.id === 'message-3' ? { ...message, clarificationConsumed: true } : message,
    )
    const wrapperConsumed = mountThread(consumed, false, '', [
      { text: '请介绍 SQL 审计工具的完整迭代过程。', presetId: 'sql-audit-overview' },
    ])
    const readonlyCards = wrapperConsumed.findAll('[data-testid="clarification-readonly"]')
    expect(readonlyCards).toHaveLength(2)
    expect(readonlyCards[1]?.attributes('data-clarification-state')).toBe('CONSUMED')
    expect(wrapperConsumed.find('[data-testid="clarification-preset-fallback"]').exists()).toBe(false)
  })

  // §11 第 6 项：脱困入口只渲染上层传入的已发布预设并转发 ask，不自造业务问题。
  it('澄清卡脱困预设点击后转发 ask 事件（携带 presetId）', async () => {
    const wrapper = mountThread(
      [
        { id: 'message-1', role: 'USER', content: '你好', createdAt: 1 },
        {
          id: 'message-2',
          role: 'AGENT',
          content: '澄清',
          turn: parseGoldenFixture('clarification.json'),
          createdAt: 2,
        },
      ],
      false,
      '',
      [{ text: '请介绍 SQL 审计工具的完整迭代过程。', presetId: 'sql-audit-overview' }],
    )
    await wrapper.get('[data-fallback-preset="sql-audit-overview"]').trigger('click')
    expect(wrapper.emitted('ask')).toHaveLength(1)
    expect(wrapper.emitted('ask')?.[0]).toEqual([
      { text: '请介绍 SQL 审计工具的完整迭代过程。', presetId: 'sql-audit-overview' },
    ])
  })
})
