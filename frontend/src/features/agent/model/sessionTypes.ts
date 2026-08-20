import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { AgentTurnCommand, SurfaceContext } from '../api/agentTurnApi'
import type { PublicAgentTurn } from './publicAgentTurn'

// Slice 5 会话模型：消息只存在页面内存；AGENT 消息携带闭合 PublicAgentTurn。
// resumeToken 仅会话内存 + 活跃会话的 sessionStorage 槽位（handoff §3）。

export interface AgentMessage {
  id: string
  role: 'USER' | 'AGENT'
  /** USER：访客输入；AGENT：供会话窗口/无障碍使用的简短文本。 */
  content: string
  /** 仅 AGENT 消息：已通过 mapper 校验的闭合 PublicAgentTurn。 */
  turn?: PublicAgentTurn
  createdAt: number
  /** USER 轮次送达标记：true 表示请求失败或已取消，不进入 conversationWindow（A2-04）。 */
  failed?: boolean
  /** 携带澄清挑战的 AGENT 消息：true 表示答案已提交，挑战卡转只读（A2-18）。 */
  clarificationConsumed?: boolean
}

export interface AgentSession {
  id: string
  title: string
  // 体验闭环 §8：完整首问作为辅助信息（悬停/aria），标题本身是可扫描短标题。
  titleDetail?: string
  role: AudienceRole
  projectSlug: string | null
  seedFingerprint: string | null
  createdAt: number
  updatedAt: number
  messages: AgentMessage[]
  /** 会话私有输入草稿：切换会话不串草稿（A2-09）；仅页面内存。 */
  draft?: string
  /** 服务端会话身份与凭证：仅页面内存，不落任何持久化存储。 */
  conversationId?: string
  resumeToken?: string
}

export interface SessionSeed {
  title?: string
  role?: AudienceRole
  projectSlug?: string | null
}

/** 首页 → Agent 一次性内存交接：只携带语义种子、会话凭证与幂等重放输入，不带答案。 */
export interface AgentRouteSeed {
  role: AudienceRole
  question: string
  projectSlug: string | null
  source: 'HOME' | 'PROJECT' | 'EVIDENCE'
  conversation?: { conversationId: string; resumeToken: string }
  /** 同 requestId + 同 fingerprint 精确重放首页轮次（D-31）；surface/window 必须原样。 */
  replay?: {
    requestId: string
    command: AgentTurnCommand
    surfaceContext?: SurfaceContext
  }
}
