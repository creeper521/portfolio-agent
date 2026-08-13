import { ref } from 'vue'

// P3 会话级 ResumeToken 唯一 sessionStorage 槽位（handoff §10.1, §10.2）。
//
// 存储模型：
//   运行期内存：每个 AgentSession 绑定自己的 ResumeToken（见 useLocalSessions）。
//   sessionStorage：只保存当前活跃 AgentSession 的 ResumeToken，用于刷新恢复。
//
// 因此多本地会话不共享一个服务端 conversation；切换会话时槽位被替换；新建会话时清空；
// 刷新后最多恢复刷新前活跃的一个会话；关闭页签后不保证恢复。
//
// 安全边界：ResumeToken 只通过 X-Conversation-Resume-Token Header 携带，绝不进入
// URL/Cookie/日志/埋点/错误上报。槽位只保存不透明 Token 字符串，绝不保存问题、答案、
// Context payload、Evidence 或 ContentVersion。禁止 localStorage/IndexedDB/Cookie 降级。

const RESUME_TOKEN_STORAGE_KEY = 'portfolio.agent.resume-token.v1'

export function useConversationResume() {
  // sessionStorage 不可用或写入失败时，当前页签内存对话仍可运行，
  // 但无法刷新恢复——以非阻断状态提示用户。
  const resumeUnavailable = ref(false)

  function isSessionStorageAvailable(): boolean {
    try {
      const probe = '__portfolio_resume_probe__'
      window.sessionStorage.setItem(probe, '1')
      window.sessionStorage.removeItem(probe)
      return true
    } catch {
      return false
    }
  }

  /** 读取当前活跃会话的 ResumeToken；槽位空或不可用时返回 null。 */
  function getActiveToken(): string | null {
    if (!isSessionStorageAvailable()) return null
    try {
      const token = window.sessionStorage.getItem(RESUME_TOKEN_STORAGE_KEY)
      return token && token.trim() ? token : null
    } catch {
      return null
    }
  }

  /** 把活跃会话的 Token 写入唯一槽位（覆盖旧值）。写入失败仅置不可用标志，不抛错。 */
  function setActiveToken(token: string): void {
    const trimmed = token?.trim()
    if (!trimmed) return
    if (!isSessionStorageAvailable()) {
      resumeUnavailable.value = true
      return
    }
    try {
      window.sessionStorage.setItem(RESUME_TOKEN_STORAGE_KEY, trimmed)
    } catch {
      resumeUnavailable.value = true
    }
  }

  /** 清除槽位（切换到无 Token 会话 / 新建会话 / 主动清除后）。 */
  function clearActiveToken(): void {
    if (!isSessionStorageAvailable()) return
    try {
      window.sessionStorage.removeItem(RESUME_TOKEN_STORAGE_KEY)
    } catch {
      // 静默忽略：槽位不可写时不影响内存对话。
    }
  }

  return {
    resumeUnavailable,
    isSessionStorageAvailable,
    getActiveToken,
    setActiveToken,
    clearActiveToken,
  }
}
