import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AgentRouteSeed, AgentSession } from '../model/sessionTypes'
import {
  SESSION_KEY,
  SESSION_TTL_MS,
  useLocalSessions,
} from './useLocalSessions'

const now = new Date('2026-07-16T08:00:00.000Z').getTime()

function makeSession(id: string, updatedAt: number): AgentSession {
  return {
    id,
    title: 'SQL 审计工具追问',
    role: 'INTERVIEWER',
    projectSlug: 'sql-audit',
    evidenceId: null,
    seedFingerprint: null,
    createdAt: updatedAt,
    updatedAt,
    expiresAt: updatedAt + SESSION_TTL_MS,
    messages: [],
  }
}

describe('useLocalSessions', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
    vi.setSystemTime(now)
  })

  it('removes sessions older than seven days', () => {
    const expired = makeSession('expired', now - SESSION_TTL_MS - 1)
    const current = makeSession('current', now)
    localStorage.setItem(SESSION_KEY, JSON.stringify([expired, current]))

    const store = useLocalSessions()

    expect(store.sessions.value.map((item) => item.id)).toEqual(['current'])
  })

  it('persists a new local session with a seven-day expiry', () => {
    const store = useLocalSessions()

    const session = store.createSession({ role: 'MENTOR', title: '项目复盘' })
    const persisted = JSON.parse(localStorage.getItem(SESSION_KEY) ?? '[]') as AgentSession[]

    expect(session.role).toBe('MENTOR')
    expect(session.expiresAt).toBe(now + SESSION_TTL_MS)
    expect(persisted[0]?.id).toBe(session.id)
  })

  it('clears all local sessions', () => {
    const store = useLocalSessions()
    store.createSession()

    store.clearSessions()

    expect(store.sessions.value).toEqual([])
    expect(localStorage.getItem(SESSION_KEY)).toBe('[]')
  })

  it('creates user and agent messages from a homepage seed without duplicating it', () => {
    const homeSeed: AgentRouteSeed = {
      role: 'INTERVIEWER',
      question: '介绍 SQL 审计工具的完整迭代。',
      answer: '该项目从固定路径查询演进为可配置、可恢复、可追溯的交付工具。',
      projectSlug: 'sql-audit',
      evidenceIds: ['sql-audit-delivery-set'],
      source: 'HOME',
    }
    const store = useLocalSessions()

    store.seedSession(homeSeed)
    const session = store.seedSession(homeSeed)

    expect(session.messages.map((item) => item.role)).toEqual(['USER', 'AGENT'])
    expect(session.messages[1]?.evidenceIds).toEqual(homeSeed.evidenceIds)
    expect(store.sessions.value).toHaveLength(1)
  })
})
