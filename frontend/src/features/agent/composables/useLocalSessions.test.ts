import { beforeEach, describe, expect, it } from 'vitest'

import type { AgentRouteSeed } from '../model/sessionTypes'
import { useLocalSessions } from './useLocalSessions'

const mappedAnswer = {
  title: '项目说明',
  summary: '公开摘要',
  sections: [{ type: 'BACKGROUND' as const, title: '背景', content: '背景内容', evidenceIds: ['sql-audit-delivery-set'] }],
  resolution: 'ANSWERED' as const,
  answerSource: 'PRESET' as const,
  generationMode: 'DETERMINISTIC' as const,
  verification: 'VERIFIED' as const,
  evidenceIds: ['sql-audit-delivery-set'],
  suggestedQuestionPresetIds: ['sql-audit-overview'],
}

describe('useLocalSessions', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('starts empty for every page-memory store instance', () => {
    const first = useLocalSessions()
    first.createSession()
    expect(first.sessions.value).toHaveLength(1)
    expect(useLocalSessions().sessions.value).toEqual([])
  })

  it('keeps only one active empty draft outside history until its first user message', () => {
    const store = useLocalSessions()
    store.createSession()
    const draft = store.createSession()
    store.createSession()

    expect(store.sessions.value).toHaveLength(1)
    expect(store.historySessions.value).toEqual([])

    store.appendMessage(store.activeSessionId.value, {
      role: 'USER',
      content: '第一条用户消息',
      answer: null,
      evidenceIds: [],
    })

    expect(store.historySessions.value).toHaveLength(1)
    expect(store.historySessions.value[0]?.id).not.toBe(draft.id)
  })

  it('never persists a visitor session', () => {
    const store = useLocalSessions()
    store.createSession({ role: 'MENTOR', title: '项目复盘' })
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('clears all local sessions', () => {
    const store = useLocalSessions()
    store.createSession()

    store.clearSessions()

    expect(store.sessions.value).toEqual([])
    expect(localStorage.length).toBe(0)
  })

  it('renames a session in page memory and ignores blank titles', () => {
    const state = useLocalSessions()
    const session = state.createSession({ title: '原标题' })

    state.renameSession(session.id, '  新标题  ')
    expect(state.activeSession.value?.title).toBe('新标题')

    state.renameSession(session.id, '   ')
    expect(state.activeSession.value?.title).toBe('新标题')
  })

  it('preserves a renamed title when messages are appended', () => {
    const state = useLocalSessions()
    const session = state.createSession({ title: '原标题' })

    state.renameSession(session.id, '手动标题')
    state.appendMessage(session.id, {
      role: 'USER',
      content: '第一条用户消息',
      answer: null,
      evidenceIds: [],
    })

    expect(state.activeSession.value?.title).toBe('手动标题')
  })

  it('creates user and agent messages from a homepage seed without duplicating it', () => {
    const homeSeed: AgentRouteSeed = {
      role: 'INTERVIEWER',
      question: '介绍 SQL 审计工具的完整迭代。',
      answer: mappedAnswer,
      projectSlug: 'sql-audit',
      evidenceIds: ['sql-audit-delivery-set'],
      source: 'HOME',
    }
    const store = useLocalSessions()

    store.seedSession(homeSeed)
    const session = store.seedSession(homeSeed)

    expect(session.messages.map((item) => item.role)).toEqual(['USER', 'AGENT'])
    expect(session.messages[1]?.evidenceIds).toEqual(homeSeed.evidenceIds)
    expect(session.messages[1]?.answer?.sections).toEqual(mappedAnswer.sections)
    expect(store.sessions.value).toHaveLength(1)
  })
})
