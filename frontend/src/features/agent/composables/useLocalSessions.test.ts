import { beforeEach, describe, expect, it } from 'vitest'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import { useLocalSessions } from './useLocalSessions'

// Slice 5 会话模型：页面内存会话 + 闭合 PublicAgentTurn 消息；
// 不回传 coveredTopics/Context/pendingConfirmation 等旧轴。

describe('useLocalSessions', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('追加 USER/AGENT 消息并维持 40 条上限，噪声输入占位标题可被有效问题升级', () => {
    const { sessions, activeSession, createSession, appendMessage } = useLocalSessions()
    const session = createSession()
    appendMessage(session.id, { role: 'USER', content: '1' })
    expect(activeSession.value?.title).toBe('待补充问题')
    expect(activeSession.value?.titleDetail).toBe('1')

    appendMessage(session.id, { role: 'USER', content: '详细介绍 SQL 审计项目' })
    expect(activeSession.value?.title).toBe('详细介绍 SQL 审计项目')

    for (let index = 0; index < 45; index += 1) {
      appendMessage(session.id, { role: 'USER', content: `问题 ${index}` })
    }
    expect(sessions.value[0]?.messages.length).toBe(40)
  })

  it('AGENT 消息携带闭合 PublicAgentTurn；无空会话被清理', () => {
    const { sessions, activeSession, createSession, appendMessage, historySessions } =
      useLocalSessions()
    const empty = createSession()
    createSession()
    // 第二次 createSession 后，无 USER 消息的空会话被清理，只保留一个。
    expect(sessions.value).toHaveLength(1)
    expect(empty.id).not.toBe(sessions.value[0]?.id)

    const session = sessions.value[0]
    if (session === undefined) throw new Error('缺少会话')
    appendMessage(session.id, { role: 'USER', content: '介绍 SQL 审计项目' })
    appendMessage(session.id, {
      role: 'AGENT',
      content: '介绍 SQL 审计项目',
      turn: parseGoldenFixture('answer-complete.json'),
    })
    expect(historySessions.value).toHaveLength(1)
    const agentMessage = activeSession.value?.messages.at(-1)
    expect(agentMessage?.role).toBe('AGENT')
    expect(agentMessage?.turn?.kind).toBe('ANSWER')
  })

  it('conversation 凭证绑定/读取/清除只存在于会话内存', () => {
    const { activeSession, createSession, setSessionConversation, getSessionResumeToken, clearSessionConversation } =
      useLocalSessions()
    const session = createSession()
    const isActive = setSessionConversation(session.id, {
      conversationId: 'conversation-1',
      resumeToken: 'token-1',
      discussionRevision: 0,
    })
    expect(isActive).toBe(true)
    expect(activeSession.value?.conversationId).toBe('conversation-1')
    expect(getSessionResumeToken(session.id)).toBe('token-1')

    // metadata 未携带新 token 时保留当前 token。
    setSessionConversation(session.id, {
      conversationId: 'conversation-1',
      discussionRevision: 0,
    })
    expect(getSessionResumeToken(session.id)).toBe('token-1')

    clearSessionConversation(session.id)
    expect(activeSession.value?.conversationId).toBeUndefined()
    expect(getSessionResumeToken(session.id)).toBeUndefined()
  })

  it('discussion projection 只按单调 revision 前进，null pointer 也能压住晚到 ACTIVE', () => {
    const { activeSession, createSession, setSessionConversation } =
      useLocalSessions()
    const session = createSession()
    const active = {
      status: 'ACTIVE' as const,
      subject: {
        kind: 'PROJECT' as const,
        reference: 'project-a', label: '项目 A',
        route: '/projects/project-a',
      },
      expiresAt: '2026-08-21T12:00:00Z',
      routeContinuation: {
        operation: 'ROUTE_IN_CONTEXT' as const,
        contextHandle: 'discussion_handle_123',
      },
    }
    setSessionConversation(session.id, {
      conversationId: 'conversation-1',
      discussionRevision: 2,
      activeDiscussion: active,
    })
    setSessionConversation(session.id, {
      conversationId: 'conversation-1',
      discussionRevision: 1,
    })
    expect(activeSession.value?.activeDiscussion).toEqual(active)

    setSessionConversation(session.id, {
      conversationId: 'conversation-1',
      discussionRevision: 3,
    })
    expect(activeSession.value?.discussionRevision).toBe(3)
    expect(activeSession.value?.activeDiscussion).toBeUndefined()
  })

  it('seedSession 按 fingerprint 去重，只种 USER 问题不带答案', () => {
    const { activeSession, seedSession } = useLocalSessions()
    seedSession({
      role: 'INTERVIEWER',
      question: '介绍代表项目',
      projectSlug: 'sql-audit',
      source: 'HOME',
    })
    seedSession({
      role: 'INTERVIEWER',
      question: '介绍代表项目',
      projectSlug: 'sql-audit',
      source: 'HOME',
    })
    expect(activeSession.value?.messages).toHaveLength(1)
    expect(activeSession.value?.messages[0]).toMatchObject({ role: 'USER', content: '介绍代表项目' })
    expect(activeSession.value?.messages[0]?.turn).toBeUndefined()
  })

  it('appendMessage 返回消息 id，markMessageDelivery 切换 failed 标记（A2-04）', () => {
    const { activeSession, createSession, appendMessage, markMessageDelivery } = useLocalSessions()
    const session = createSession()
    const messageId = appendMessage(session.id, { role: 'USER', content: '会失败的问题' })
    expect(typeof messageId).toBe('string')

    markMessageDelivery(session.id, messageId ?? '', true)
    expect(activeSession.value?.messages[0]?.failed).toBe(true)

    markMessageDelivery(session.id, messageId ?? '', false)
    expect(activeSession.value?.messages[0]?.failed).toBe(false)

    // 未知 id / 未知会话安全无操作。
    markMessageDelivery(session.id, 'message-unknown', true)
    markMessageDelivery('session-unknown', messageId ?? '', true)
    expect(activeSession.value?.messages[0]?.failed).toBe(false)
  })


  it('模型偏好与展示通知只写会话内存：新会话从目录默认（undefined）开始（UI spec §5.4）', () => {
    const { activeSession, createSession, setSessionModelSelection, appendSessionNotice } = useLocalSessions()
    const session = createSession()
    expect(activeSession.value?.modelSelection).toBeUndefined()
    expect(activeSession.value?.notices).toEqual([])

    const selection = {
      kind: 'MODEL',
      modelRef: 'qwen-3-7-flash',
      selectionVersion: 'qwen-3-7-flash-v1',
    } as const
    expect(setSessionModelSelection(session.id, selection)).toBe(true)
    expect(activeSession.value?.modelSelection).toEqual(selection)
    expect(setSessionModelSelection(session.id, undefined)).toBe(true)
    expect(activeSession.value?.modelSelection).toBeUndefined()
    expect(setSessionModelSelection('session-unknown', undefined)).toBe(false)

    appendSessionNotice(session.id, {
      kind: 'MODEL_SWITCHED',
      title: '已切换至 Qwen3.7-Flash · 下一轮回答将由它生成',
      detail: '选择仅在本页会话内记忆，刷新后使用目录默认',
    })
    const notice = activeSession.value?.notices[0]
    expect(notice?.kind).toBe('MODEL_SWITCHED')
    expect(notice?.id).toMatch(/^notice-/)
    expect(notice?.createdAt).toBeGreaterThan(0)
    // 通知是会话内独立流：不产生 USER/AGENT 消息，不进入 messages。
    expect(activeSession.value?.messages).toHaveLength(0)
  })

  it('有 USER 消息或非空草稿的会话在新建时保留；纯空白草稿会话被清理（行为基础 Task 4）', () => {
    const { sessions, historySessions, createSession, appendMessage, selectSession, activeSession } =
      useLocalSessions()
    const withMessage = createSession()
    appendMessage(withMessage.id, { role: 'USER', content: '介绍 SQL 审计项目' })

    const withDraft = createSession()
    sessions.value.find((item) => item.id === withDraft.id)!.draft = '未发送草稿'

    const withBlankDraft = createSession()
    sessions.value.find((item) => item.id === withBlankDraft.id)!.draft = '   '

    // 再次创建：有消息与有非空草稿的会话保留，空白草稿会话被清理。
    createSession()
    const ids = sessions.value.map((item) => item.id)
    expect(ids).toContain(withMessage.id)
    expect(ids).toContain(withDraft.id)
    expect(ids).not.toContain(withBlankDraft.id)

    // 纯草稿会话进入历史列表且可重新选中（上级设计 §6.3.6）。
    expect(historySessions.value.map((item) => item.id)).toContain(withDraft.id)
    selectSession(withDraft.id)
    expect(activeSession.value?.id).toBe(withDraft.id)
    expect(activeSession.value?.draft).toBe('未发送草稿')
  })

  it('switchAudienceRole：不同角色新建会话并只继承上下文；同角色与无会话为 no-op（行为基础 Task 4）', () => {
    const { sessions, activeSession, createSession, appendMessage, switchAudienceRole } =
      useLocalSessions()
    // 初始无会话：不允许切换。
    expect(switchAudienceRole('HR', null)).toBeNull()

    const first = createSession({ role: 'INTERVIEWER', projectSlug: 'sql-audit' })
    appendMessage(first.id, { role: 'USER', content: '介绍项目' })
    sessions.value.find((item) => item.id === first.id)!.draft = '未发送草稿'

    // 同角色选择不创建会话、不改变状态。
    expect(switchAudienceRole('INTERVIEWER', 'sql-audit')).toBeNull()
    expect(activeSession.value?.id).toBe(first.id)

    const created = switchAudienceRole('HR', 'sql-audit')
    expect(created).not.toBeNull()
    expect(created?.role).toBe('HR')
    expect(created?.projectSlug).toBe('sql-audit')
    expect(created?.messages).toEqual([])
    expect(created?.draft).toBeUndefined()
    expect(created?.modelSelection).toBeUndefined()
    expect(created?.conversationId).toBeUndefined()
    expect(created?.resumeToken).toBeUndefined()
    expect(created?.notices).toEqual([])
    expect(activeSession.value?.id).toBe(created?.id)

    // 旧会话完整保留：角色、消息与草稿均留在原会话（上级设计 §6.3）。
    const old = sessions.value.find((item) => item.id === first.id)
    expect(old?.role).toBe('INTERVIEWER')
    expect(old?.messages).toHaveLength(1)
    expect(old?.draft).toBe('未发送草稿')
  })

  it('markClarificationConsumed 标记 CRITICAL 与 ANSWER 内嵌挑战，未知 id 无操作（A2-18）', () => {
    const { activeSession, createSession, appendMessage, markClarificationConsumed } =
      useLocalSessions()
    const session = createSession()
    appendMessage(session.id, { role: 'USER', content: '问题' })
    appendMessage(session.id, {
      role: 'AGENT',
      content: '澄清',
      turn: parseGoldenFixture('clarification.json'),
    })
    appendMessage(session.id, {
      role: 'AGENT',
      content: '回答',
      turn: parseGoldenFixture('answer-local-clarification.json'),
    })

    expect(markClarificationConsumed(session.id, 'clarification_fixture_critical')).toBe(true)
    expect(markClarificationConsumed(session.id, 'clarification_fixture_local')).toBe(true)
    const messages = activeSession.value?.messages ?? []
    expect(messages[1]?.clarificationConsumed).toBe(true)
    expect(messages[2]?.clarificationConsumed).toBe(true)

    expect(markClarificationConsumed(session.id, 'clarification_unknown')).toBe(false)
    expect(markClarificationConsumed('session-unknown', 'clarification_fixture_critical')).toBe(false)
  })
})
