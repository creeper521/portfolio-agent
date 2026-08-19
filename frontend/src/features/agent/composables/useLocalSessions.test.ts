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
    })
    expect(isActive).toBe(true)
    expect(activeSession.value?.conversationId).toBe('conversation-1')
    expect(getSessionResumeToken(session.id)).toBe('token-1')

    // metadata 未携带新 token 时保留当前 token。
    setSessionConversation(session.id, { conversationId: 'conversation-1' })
    expect(getSessionResumeToken(session.id)).toBe('token-1')

    clearSessionConversation(session.id)
    expect(activeSession.value?.conversationId).toBeUndefined()
    expect(getSessionResumeToken(session.id)).toBeUndefined()
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
})
