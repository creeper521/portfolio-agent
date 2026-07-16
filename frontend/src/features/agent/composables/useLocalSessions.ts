import { computed, ref } from 'vue'

import type {
  AgentMessage,
  AgentRouteSeed,
  AgentSession,
  SessionSeed,
} from '../model/sessionTypes'

export const SESSION_KEY = 'portfolio.agent.sessions.v1'
export const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000

function makeId(prefix: string) {
  const random = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
  return `${prefix}-${random}`
}

function readSessions(now: number): AgentSession[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(SESSION_KEY) ?? '[]') as AgentSession[]
    return Array.isArray(parsed)
      ? parsed
          .filter((session) => session.expiresAt > now)
          .map((session) => ({
            ...session,
            seedFingerprint: session.seedFingerprint ?? null,
          }))
      : []
  } catch {
    return []
  }
}

export function useLocalSessions() {
  const now = Date.now()
  const sessions = ref<AgentSession[]>(readSessions(now))
  const activeSessionId = ref(sessions.value[0]?.id ?? '')
  const storageWarning = ref('')

  function persist() {
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(sessions.value))
      storageWarning.value = ''
    } catch {
      storageWarning.value = '当前浏览器无法保存会话，本次对话仍可继续。'
    }
  }

  if (localStorage.getItem(SESSION_KEY) !== JSON.stringify(sessions.value)) {
    persist()
  }

  const activeSession = computed(
    () => sessions.value.find((session) => session.id === activeSessionId.value) ?? null,
  )

  function createSession(seed: SessionSeed = {}) {
    const createdAt = Date.now()
    const session: AgentSession = {
      id: makeId('session'),
      title: seed.title?.trim() || '新的工程追问',
      role: seed.role ?? 'INTERVIEWER',
      projectSlug: seed.projectSlug ?? null,
      evidenceId: seed.evidenceId ?? null,
      seedFingerprint: null,
      createdAt,
      updatedAt: createdAt,
      expiresAt: createdAt + SESSION_TTL_MS,
      messages: [],
    }
    sessions.value = [session, ...sessions.value]
    activeSessionId.value = session.id
    persist()
    return session
  }

  function selectSession(sessionId: string) {
    if (sessions.value.some((session) => session.id === sessionId)) {
      activeSessionId.value = sessionId
    }
  }

  function appendMessage(sessionId: string, message: Omit<AgentMessage, 'id' | 'createdAt'>) {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return

    const timestamp = Date.now()
    session.messages.push({
      ...message,
      id: makeId('message'),
      createdAt: timestamp,
    })
    session.updatedAt = timestamp
    session.expiresAt = timestamp + SESSION_TTL_MS
    if (session.messages[0]?.role === 'USER') {
      session.title = session.messages[0].content.slice(0, 24)
    }
    sessions.value = [...sessions.value]
    persist()
  }

  function removeSession(sessionId: string) {
    sessions.value = sessions.value.filter((session) => session.id !== sessionId)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.id ?? ''
    }
    persist()
  }

  function clearSessions() {
    sessions.value = []
    activeSessionId.value = ''
    persist()
  }

  function seedSession(input: AgentRouteSeed) {
    const fingerprint =
      `${input.source}:${input.role}:${input.projectSlug ?? ''}:${input.question}`
    const existing = sessions.value.find(
      (session) => session.seedFingerprint === fingerprint,
    )
    if (existing) {
      activeSessionId.value = existing.id
      return existing
    }

    const session = createSession({
      role: input.role,
      projectSlug: input.projectSlug,
      evidenceId: input.evidenceIds[0] ?? null,
    })
    session.seedFingerprint = fingerprint
    appendMessage(session.id, {
      role: 'USER',
      content: input.question,
      evidenceIds: [],
    })
    appendMessage(session.id, {
      role: 'AGENT',
      content: input.answer,
      evidenceIds: input.evidenceIds,
    })
    return sessions.value.find((item) => item.id === session.id) ?? session
  }

  return {
    sessions,
    activeSessionId,
    activeSession,
    storageWarning,
    createSession,
    selectSession,
    appendMessage,
    seedSession,
    removeSession,
    clearSessions,
  }
}
