import { beforeEach, describe, expect, it } from 'vitest'

import type { ConversationTopic } from '../model/answerTypes'
import type { AgentRouteSeed } from '../model/sessionTypes'
import { confirmationRequiredResponse } from '../model/semanticTurnFixtures'
import { useLocalSessions } from './useLocalSessions'

const mappedAnswer = {
  turnId: 'turn-1',
  contentVersion: '2026-07-21',
  title: '项目说明',
  summary: '公开摘要',
  sections: [{ key: 'BACKGROUND:0', type: 'BACKGROUND' as const, title: '背景', sourceScope: 'PORTFOLIO' as const, content: '背景内容', claimIds: [], evidenceIds: ['sql-audit-delivery-set'] }],
  resolution: 'ANSWERED' as const,
  answerSource: 'PRESET' as const,
  generationMode: 'DETERMINISTIC' as const,
  verification: 'VERIFIED' as const,
  evidenceIds: ['sql-audit-delivery-set'],
  suggestedQuestionPresetIds: ['sql-audit-overview'],
  suggestedQuestions: [],
  coveredTopics: ['BACKGROUND' as const],
  guidanceStage: 'OPENING' as const,
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

  it('keeps confirmation state in memory and never browser storage', () => {
    const store = useLocalSessions()
    const session = store.createSession()

    store.acceptSemanticTurnResponse(session.id, confirmationRequiredResponse())

    expect(store.activeSession.value?.pendingConfirmation?.confirmationPlan)
      .toBe('opaque-envelope')
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

  it('caps session messages at 40 (20 rounds) to enforce memory limit', () => {
    const store = useLocalSessions()
    const session = store.createSession()

    for (let i = 0; i < 25; i++) {
      store.appendMessage(session.id, {
        role: 'USER',
        content: `问题 ${i}`,
        answer: null,
        evidenceIds: [],
      })
      store.appendMessage(session.id, {
        role: 'AGENT',
        content: `回答 ${i}`,
        answer: null,
        evidenceIds: [],
      })
    }

    expect(session.messages).toHaveLength(40)
    // 保留最近的 20 轮
    expect(session.messages[0]?.content).toBe('问题 5')
    expect(session.messages[39]?.content).toBe('回答 24')
  })

  it('never writes to localStorage, sessionStorage, or IndexedDB', () => {
    const store = useLocalSessions()
    store.createSession()
    store.appendMessage(store.activeSessionId.value, {
      role: 'USER',
      content: '测试隐私',
      answer: null,
      evidenceIds: [],
    })

    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
    // IndexedDB 在 jsdom 中不可用，但代码不应引用它
    expect(typeof indexedDB).toBe('undefined')
  })

  it('keeps the full text of the first question as the session title', () => {
    const store = useLocalSessions()
    const session = store.createSession()
    const longQuestion = '请完整介绍一下你在 SQL 审计工具项目中负责的模块边界、关键取舍与最终验证方式'

    store.appendMessage(session.id, {
      role: 'USER',
      content: longQuestion,
      answer: null,
      evidenceIds: [],
    })

    expect(store.activeSession.value?.title).toBe(longQuestion)
  })

  it('trims the first question without collapsing inner whitespace', () => {
    const store = useLocalSessions()
    const session = store.createSession()

    store.appendMessage(session.id, {
      role: 'USER',
      content: '  第一行问题\n\n第二行问题  ',
      answer: null,
      evidenceIds: [],
    })

    expect(store.activeSession.value?.title).toBe('第一行问题\n\n第二行问题')
  })

  it('keeps a manually renamed title longer than forty characters', () => {
    const store = useLocalSessions()
    const session = store.createSession({ title: '原标题' })
    const longTitle = '这是一段明显超过四十个字符的手动会话标题用于验证重命名流程不会再被静默截断掉任何内容'

    store.renameSession(session.id, longTitle)

    expect(store.activeSession.value?.title).toBe(longTitle)
  })

  it('starts every session with an empty coveredTopics list', () => {
    const store = useLocalSessions()
    const first = store.createSession()
    const second = store.createSession()

    expect(first.coveredTopics).toEqual([])
    expect(second.coveredTopics).toEqual([])
  })

  it('replaces coveredTopics with the full list from the latest answer', () => {
    const store = useLocalSessions()
    const session = store.createSession()
    const firstTopics: ConversationTopic[] = ['BACKGROUND', 'SOLUTION']

    store.applyAnswerProgress(session.id, { ...mappedAnswer, coveredTopics: firstTopics })

    expect(store.activeSession.value?.coveredTopics).toEqual(['BACKGROUND', 'SOLUTION'])
    expect(store.activeSession.value?.coveredTopics).not.toBe(firstTopics)

    store.applyAnswerProgress(session.id, { ...mappedAnswer, coveredTopics: ['OUTCOME'] })

    expect(store.activeSession.value?.coveredTopics).toEqual(['OUTCOME'])
  })

  it('keeps coveredTopics isolated between sessions', () => {
    const store = useLocalSessions()
    const first = store.createSession()
    store.appendMessage(first.id, {
      role: 'USER',
      content: '第一个会话的问题',
      answer: null,
      evidenceIds: [],
    })
    const second = store.createSession()

    store.applyAnswerProgress(first.id, { ...mappedAnswer, coveredTopics: ['BACKGROUND'] })

    expect(store.sessions.value.find((item) => item.id === first.id)?.coveredTopics)
      .toEqual(['BACKGROUND'])
    expect(store.sessions.value.find((item) => item.id === second.id)?.coveredTopics)
      .toEqual([])
  })

  it('adopts the seed answer coveredTopics for a homepage-seeded session', () => {
    const store = useLocalSessions()
    const seed: AgentRouteSeed = {
      role: 'INTERVIEWER',
      question: '介绍 SQL 审计工具的完整迭代。',
      answer: { ...mappedAnswer, coveredTopics: ['BACKGROUND', 'SOLUTION'] },
      projectSlug: 'sql-audit',
      evidenceIds: ['sql-audit-delivery-set'],
      source: 'HOME',
    }

    const session = store.seedSession(seed)

    expect(session.coveredTopics).toEqual(['BACKGROUND', 'SOLUTION'])
  })
})
