import { computed, ref } from 'vue'

import type { MappedAnswer } from '../model/answerTypes'
import type { AnswerResponse } from '../model/answerTypes'
import type {
  AgentMessage,
  AgentRouteSeed,
  AgentSession,
  SessionSeed,
} from '../model/sessionTypes'
import { extractOpaquePlanConfirmation } from '../model/semanticTurnView'
import { shortSessionTitle } from '../model/sessionTitle'

// 体验闭环 §8：噪声输入（纯数字/纯符号/过短）的会话标题占位，可被后续有效问题升级。
const PENDING_TITLE = '待补充问题'

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
  const historySessions = computed(
    () => sessions.value.filter(
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
      evidenceId: seed.evidenceId ?? null,
      seedFingerprint: null,
      createdAt,
      updatedAt: createdAt,
      messages: [],
      coveredTopics: [],
      pendingConfirmation: undefined,
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
    const messageLimit = session.messages.at(-1)?.role === 'USER' ? 41 : 40
    if (session.messages.length > messageLimit) {
      session.messages = session.messages.slice(-messageLimit)
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
    applyAnswerProgress(session.id, input.answer)
    return sessions.value.find((item) => item.id === session.id) ?? session
  }

  function applyAnswerProgress(sessionId: string, answer: MappedAnswer) {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.coveredTopics = [...answer.coveredTopics]
    sessions.value = [...sessions.value]
  }

  function acceptSemanticTurnResponse(sessionId: string, response: AnswerResponse) {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    const pendingConfirmation = extractOpaquePlanConfirmation(response.agentTurn)
    session.pendingConfirmation = pendingConfirmation === undefined
      ? undefined
      : { ...pendingConfirmation }
    sessions.value = [...sessions.value]
  }

  // ── P3：会话级 ResumeToken 与恢复摘要（handoff §6, §10, §11）──
  // Token 只存当前会话内存；sessionStorage 槽位由 useConversationResume + Workspace 协调。
  // 摘要只来自服务端确定性投影，供恢复卡展示，绝不包含问题/答案/handle/version。

  /** 把服务端签发/重签的 ResumeToken 绑定到指定会话内存。返回该会话是否为当前活跃会话。 */
  function setSessionResumeToken(sessionId: string, token: string): boolean {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return false
    const trimmed = token.trim()
    if (!trimmed) return false
    session.resumeToken = trimmed
    sessions.value = [...sessions.value]
    return sessionId === activeSessionId.value
  }

  /** 读取指定会话绑定的内存 Token（用于切换槽位、DELETE 清除）。 */
  function getSessionResumeToken(sessionId: string): string | undefined {
    const session = sessions.value.find((item) => item.id === sessionId)
    return session?.resumeToken
  }

  /** 清除指定会话的内存 Token（DELETE 成功或过期后）。 */
  function clearSessionResumeToken(sessionId: string): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.resumeToken = undefined
    session.activeContextSummary = undefined
    sessions.value = [...sessions.value]
  }

  /** 记录刷新恢复得到的安全 Context Summary（仅活跃会话恢复卡）。 */
  function setSessionContextSummary(
    sessionId: string,
    summary: import('../model/answerTypes').ConversationContextSummary | undefined,
  ): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.activeContextSummary = summary
    sessions.value = [...sessions.value]
  }

  function clearPendingConfirmation(sessionId: string) {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session || session.pendingConfirmation === undefined) return
    session.pendingConfirmation = undefined
    sessions.value = [...sessions.value]
  }

  // 计划失效卡的「暂不处理」记录（按 turnId 记，tab 内存语义，随会话删除/清空回收）。
  const dismissedPlanChangeTurnIds = ref<ReadonlySet<string>>(new Set())

  function dismissPlanChange(turnId: string) {
    if (dismissedPlanChangeTurnIds.value.has(turnId)) return
    dismissedPlanChangeTurnIds.value = new Set([...dismissedPlanChangeTurnIds.value, turnId])
  }

  function isPlanChangeDismissed(turnId: string): boolean {
    return dismissedPlanChangeTurnIds.value.has(turnId)
  }

  function pruneDismissedPlanChanges() {
    const aliveTurnIds = new Set(
      sessions.value.flatMap((session) =>
        session.messages
          .map((message) => message.answer?.turnId)
          .filter((turnId): turnId is string => typeof turnId === 'string')),
    )
    dismissedPlanChangeTurnIds.value = new Set(
      [...dismissedPlanChangeTurnIds.value].filter((turnId) => aliveTurnIds.has(turnId)),
    )
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
    applyAnswerProgress,
    acceptSemanticTurnResponse,
    setSessionResumeToken,
    getSessionResumeToken,
    clearSessionResumeToken,
    setSessionContextSummary,
    clearPendingConfirmation,
    dismissPlanChange,
    isPlanChangeDismissed,
    dismissedPlanChangeTurnIds,
    pruneDismissedPlanChanges,
    removeSession,
    clearSessions,
  }
}
