import { computed, ref } from 'vue'

import type {
  AgentMessage,
  AgentRouteSeed,
  AgentSession,
  AgentThreadNotice,
  SessionSeed,
} from '../model/sessionTypes'
import type { CurrentDiscussionSummary } from '../api/agentTurnApi'
import type { ModelSelection } from '../model/modelSelection'
import { shortSessionTitle } from '../model/sessionTitle'

// 本地多会话的内存状态层（composable）：会话列表、活跃会话切换、消息追加、
// 标题自动升级、澄清消费标记，以及会话级 conversation 凭证与主题讨论状态的绑定。
// 一切数据只存页面内存；持久化仅限活跃会话的 ResumeToken 槽位，
// 由上层 Workspace 用 useConversationResume 协调，本模块自身不做任何存储。

// 噪声输入（纯数字/纯符号/过短）暂无法生成标题时使用的占位文案，可被后续有效提问升级。（体验闭环 §8）
const PENDING_TITLE = '待补充问题'
// 单个会话在内存中保留的最大消息条数（超出截尾保留最近的），防止消息无限增长。
const MESSAGE_LIMIT = 40
// 单个会话保留的展示通知上限（与消息同一截尾策略，仅影响展示不影响合同）。
const NOTICE_LIMIT = 20

function makeId(prefix: string) {
  const random = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
  return `${prefix}-${random}`
}

/**
 * 本地会话集合的响应式状态与操作（composable）。
 * 会话与消息仅存页面内存；服务端会话凭证（conversationId/ResumeToken）
 * 绑定在会话对象上，由上层负责与 sessionStorage 槽位同步。
 */
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

  /** 新建空白会话并置为活跃；没有用户消息的旧会话同时被丢弃（不算历史）。 */
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
      // 模型偏好不初始化：新会话从目录默认开始（UI spec §3 规则 1）。
      notices: [],
      discussionRevision: 0,
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

  /** 切换活跃会话；sessionId 不存在时不做任何事。 */
  function selectSession(sessionId: string) {
    if (sessions.value.some((session) => session.id === sessionId)) {
      activeSessionId.value = sessionId
    }
  }

  /** 手动重命名：置标题并记入「已手动命名」集合，此后不再被自动标题升级覆盖。 */
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

  /** 追加消息并返回其 id，供调用方后续标记送达/澄清消费状态。 */
  function appendMessage(sessionId: string, message: Omit<AgentMessage, 'id' | 'createdAt'>): string | null {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return null

    const timestamp = Date.now()
    const messageId = makeId('message')
    session.messages.push({
      ...message,
      id: messageId,
      createdAt: timestamp,
    })
    session.updatedAt = timestamp
    if (session.messages.length > MESSAGE_LIMIT) {
      session.messages = session.messages.slice(-MESSAGE_LIMIT)
    }
    if (session.messages[0]?.role === 'USER' && !manuallyRenamedSessionIds.has(sessionId)) {
      // 噪声输入不能固化为永久标题：短标题生成失败时先落占位，
      // 之后的有效提问会把占位升级为可扫描短标题。（体验闭环 §8）
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
    return messageId
  }

  /**
   * 设置会话模型偏好（UI spec §2.4）：undefined 表示回到目录默认；
   * 只写会话内存，不产生 Turn、不进入任何浏览器存储。
   */
  function setSessionModelSelection(sessionId: string, selection: undefined | ModelSelection): boolean {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return false
    session.modelSelection = selection
    session.updatedAt = Date.now()
    sessions.value = [...sessions.value]
    return true
  }

  /**
   * 追加会话内纯展示通知（UI spec §2.4/§2.9）：与消息按时间交错渲染，
   * 但 conversationWindow 只消费 messages，通知结构性不进窗口。
   */
  function appendSessionNotice(
    sessionId: string,
    notice: Omit<AgentThreadNotice, 'id' | 'createdAt'>,
  ): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    const timestamp = Date.now()
    session.notices.push({
      ...notice,
      id: makeId('notice'),
      createdAt: timestamp,
    })
    session.updatedAt = timestamp
    if (session.notices.length > NOTICE_LIMIT) {
      session.notices = session.notices.slice(-NOTICE_LIMIT)
    }
    sessions.value = [...sessions.value]
  }

  /** 标记 USER 轮次送达结果；failed=true 的消息不进入后续 conversationWindow 上送。（A2-04） */
  function markMessageDelivery(sessionId: string, messageId: string, failed: boolean): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    const message = session?.messages.find((item) => item.id === messageId)
    if (!message) return
    message.failed = failed
    sessions.value = [...sessions.value]
  }

  /** 标记澄清挑战已提交消费：挑战卡转只读，防止对同一 clarificationId 重复提交。（A2-18） */
  function markClarificationConsumed(
    sessionId: string,
    clarificationId: string,
    consumed = true,
  ): boolean {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return false
    const message = session.messages.find((item) => {
      const turn = item.turn
      if (turn === undefined) return false
      if (turn.kind === 'CLARIFICATION') {
        return turn.clarification.clarificationId === clarificationId
      }
      if (turn.kind === 'ANSWER' && turn.answer.localClarification !== undefined) {
        return turn.answer.localClarification.clarificationId === clarificationId
      }
      return false
    })
    if (!message) return false
    message.clarificationConsumed = consumed
    sessions.value = [...sessions.value]
    return true
  }

  /** 删除会话；若删除的是活跃会话则回落到剩余列表的第一个，无剩余时活跃 id 置空。 */
  function removeSession(sessionId: string) {
    sessions.value = sessions.value.filter((session) => session.id !== sessionId)
    manuallyRenamedSessionIds.delete(sessionId)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.id ?? ''
    }
  }

  /** 清空全部本地会话与手动命名记录，活跃会话 id 置空。 */
  function clearSessions() {
    sessions.value = []
    activeSessionId.value = ''
    manuallyRenamedSessionIds.clear()
  }

  /**
   * 依据首页/项目页的一次性交接种子创建会话：相同 fingerprint 的种子幂等复用
   * 既有会话，避免重复交接生成多个空壳会话；新会话直接以种子问题开首条消息。
   */
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
    conversation: {
      conversationId: string
      resumeToken?: string
      discussionRevision: number
      activeDiscussion?: CurrentDiscussionSummary
    },
  ): boolean {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return false
    session.conversationId = conversation.conversationId
    const trimmed = conversation.resumeToken?.trim()
    if (trimmed) {
      session.resumeToken = trimmed
    }
    const discussionApplied = applyDiscussionState(
      session, conversation.discussionRevision,
      conversation.activeDiscussion,
    )
    if (discussionApplied) session.discussionPaused = false
    sessions.value = [...sessions.value]
    return sessionId === activeSessionId.value
  }

  /** 读取会话内存中的 ResumeToken；会话不存在或未绑定时返回 undefined。 */
  function getSessionResumeToken(sessionId: string): string | undefined {
    return sessions.value.find((item) => item.id === sessionId)?.resumeToken
  }

  /** 刷新恢复/交接恢复时，把取回的会话凭证与讨论状态整体绑定到会话（覆盖旧绑定）。 */
  function adoptResumedConversation(
    sessionId: string,
    conversation: {
      conversationId: string
      resumeToken: string
      discussionRevision?: number
      activeDiscussion?: CurrentDiscussionSummary
    },
  ): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.conversationId = conversation.conversationId
    session.resumeToken = conversation.resumeToken
    applyDiscussionState(
      session, conversation.discussionRevision ?? 0,
      conversation.activeDiscussion,
    )
    session.discussionPaused = false
    sessions.value = [...sessions.value]
  }

  /** 解绑会话的服务端凭证与讨论状态（服务端清除成功或本地放弃恢复时调用）。 */
  function clearSessionConversation(sessionId: string): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) return
    session.conversationId = undefined
    session.resumeToken = undefined
    session.discussionRevision = 0
    session.activeDiscussion = undefined
    session.discussionPaused = false
    sessions.value = [...sessions.value]
  }

  /** 设置主题讨论的暂停标志；无活跃讨论的会话忽略该操作。 */
  function setDiscussionPaused(sessionId: string, paused: boolean): void {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session || session.activeDiscussion === undefined) return
    session.discussionPaused = paused
    sessions.value = [...sessions.value]
  }

  /** 只接受单调不减的 discussionRevision：陈旧或非法响应不得覆盖更新的讨论投影。 */
  function applyDiscussionState(
    session: AgentSession,
    revision: number,
    activeDiscussion: CurrentDiscussionSummary | undefined,
  ): boolean {
    if (!Number.isSafeInteger(revision) || revision < session.discussionRevision) {
      return false
    }
    session.discussionRevision = revision
    session.activeDiscussion = activeDiscussion
    return true
  }

  return {
    sessions,
    activeSessionId,
    activeSession,
    historySessions,
    createSession,
    selectSession,
    setSessionModelSelection,
    appendSessionNotice,
    renameSession,
    appendMessage,
    seedSession,
    setSessionConversation,
    getSessionResumeToken,
    adoptResumedConversation,
    clearSessionConversation,
    setDiscussionPaused,
    markMessageDelivery,
    markClarificationConsumed,
    removeSession,
    clearSessions,
  }
}
