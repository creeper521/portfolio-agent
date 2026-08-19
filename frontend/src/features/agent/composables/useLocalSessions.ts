import { computed, ref } from 'vue'

import type {
  AgentMessage,
  AgentRouteSeed,
  AgentSession,
  SessionSeed,
} from '../model/sessionTypes'
import { shortSessionTitle } from '../model/sessionTitle'

// 体验闭环 §8：噪声输入（纯数字/纯符号/过短）的会话标题占位，可被后续有效问题升级。
const PENDING_TITLE = '待补充问题'
const MESSAGE_LIMIT = 40

function makeId(prefix: string) {
  const random = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
  return `${prefix}-${random}`
}

export function useLocalSessions() {
  const sessions = ref<AgentSession[]>([])
  const activeSessionId = ref('')
  const manuallyRenamedSessionIds = new Set<string>()

  const activeSession = computed(
    () => sessions.value.find((session) => session.id === activeSessionId.value) ?? null,
  )
  const historySessions = computed(() =>
    sessions.value.filter(
      (session) => session.messages.some((message) => message.role === 'USER'),
    ),
  )

  function createSession(seed: SessionSeed = {}) {
    const createdAt = Date.now()
    const session: AgentSession = {
      id: makeId('session'),
      title: seed.title?.trim() || '新的工程追问',
      role: seed.role ?? 'INTERVIEWER',
      projectSlug: seed.projectSlug ?? null,
      seedFingerprint: null,
      createdAt,
      updatedAt: createdAt,
      messages: [],
    }
    const retainedSessions = sessions.value.filter(
      (item) => item.messages.some((message) => message.role === 'USER'),
    )
    for (const item of sessions.value) {
      if (!retainedSessions.includes(item)) {
        manuallyRenamedSessionIds.delete(item.id)
      }
    }
    sessions.value = [session, ...retainedSessions]
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
    session.title = normalized
    session.updatedAt = Date.now()
    manuallyRenamedSessionIds.add(sessionId)
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
    if (session.messages.length > MESSAGE_LIMIT) {
      session.messages = session.messages.slice(-MESSAGE_LIMIT)
    }
    if (session.messages[0]?.role === 'USER' && !manuallyRenamedSessionIds.has(sessionId)) {
      // 体验闭环 §8：噪声输入不能成为永久标题；占位标题可被后续有效问题升级。
      const upgrading = session.title === PENDING_TITLE
      const source = upgrading
        ? [...session.messages].reverse().find((message) => message.role === 'USER')
            ?? session.messages[0]
        : session.messages[0]
      const shortTitle = shortSessionTitle(source.content)
      if (shortTitle) {
        session.title = shortTitle
        session.titleDetail = source.content.trim()
      } else {
        session.title = PENDING_TITLE
        session.titleDetail = source.content.trim()
      }
    }
    sessions.value = [...sessions.value]
  }

  function removeSession(sessionId: string) {
    sessions.value = sessions.value.filter((session) => session.id !== sessionId)
    manuallyRenamedSessionIds.delete(sessionId)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.id ?? ''
    }
  }

  function clearSessions() {
    sessions.value = []
    activeSessionId.value = ''
    manuallyRenamedSessionIds.clear()
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
    })
    session.seedFingerprint = fingerprint
    appendMessage(session.id, {
      role: 'USER',
      content: input.question,
    })
    return sessions.value.find((item) => item.id === session.id) ?? session
  }

  // ── 会话级 conversation 凭证（仅内存；sessionStorage 槽位由 Workspace 协调）──

  /** 把响应 envelope 的会话身份/新 Token 绑定到会话内存，返回是否为当前活跃会话。 */
  function setSessionConversation(
    sessionId: string,
    conversation: { conversationId: string; resumeToken?: string },
  ): boolean {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return false
    session.conversationId = conversation.conversationId
    const trimmed = conversation.resumeToken?.trim()
    if (trimmed) {
      session.resumeToken = trimmed
    }
    sessions.value = [...sessions.value]
    return sessionId === activeSessionId.value
  }

  function getSessionResumeToken(sessionId: string): string | undefined {
    return sessions.value.find((item) => item.id === sessionId)?.resumeToken
  }

  function adoptResumedConversation(
    sessionId: string,
    conversation: { conversationId: string; resumeToken: string },
  ): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.conversationId = conversation.conversationId
    session.resumeToken = conversation.resumeToken
    sessions.value = [...sessions.value]
  }

  function clearSessionConversation(sessionId: string): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.conversationId = undefined
    session.resumeToken = undefined
    sessions.value = [...sessions.value]
  }

  return {
    sessions,
    activeSessionId,
    activeSession,
    historySessions,
    createSession,
    selectSession,
    renameSession,
    appendMessage,
    seedSession,
    setSessionConversation,
    getSessionResumeToken,
    adoptResumedConversation,
    clearSessionConversation,
    removeSession,
    clearSessions,
  }
}
