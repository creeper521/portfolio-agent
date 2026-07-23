import { computed, ref } from 'vue'

import type {
  AgentMessage,
  AgentRouteSeed,
  AgentSession,
  SessionSeed,
} from '../model/sessionTypes'

function makeId(prefix: string) {
  const random = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
  return `${prefix}-${random}`
}

export function useLocalSessions() {
  const sessions = ref<AgentSession[]>([])
  const activeSessionId = ref('')

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
      messages: [],
    }
    sessions.value = [session, ...sessions.value]
    activeSessionId.value = session.id
    return session
  }

  function selectSession(sessionId: string) {
    if (sessions.value.some((session) => session.id === sessionId)) {
      activeSessionId.value = sessionId
    }
  }

  function renameSession(sessionId: string, title: string) {
    const normalized = title.trim()
    if (!normalized) return
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.title = normalized.slice(0, 40)
    session.updatedAt = Date.now()
    sessions.value = [...sessions.value]
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
    if (session.messages[0]?.role === 'USER') {
      session.title = session.messages[0].content.slice(0, 24)
    }
    sessions.value = [...sessions.value]
  }

  function removeSession(sessionId: string) {
    sessions.value = sessions.value.filter((session) => session.id !== sessionId)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.id ?? ''
    }
  }

  function clearSessions() {
    sessions.value = []
    activeSessionId.value = ''
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
      answer: null,
      evidenceIds: [],
    })
    appendMessage(session.id, {
      role: 'AGENT',
      content: input.answer.summary,
      answer: input.answer,
      evidenceIds: input.evidenceIds,
    })
    return sessions.value.find((item) => item.id === session.id) ?? session
  }

  return {
    sessions,
    activeSessionId,
    activeSession,
    createSession,
    selectSession,
    renameSession,
    appendMessage,
    seedSession,
    removeSession,
    clearSessions,
  }
}
