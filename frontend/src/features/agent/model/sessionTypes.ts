import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { AgentTurnCommand, CurrentDiscussionSummary, SurfaceContext } from '../api/agentTurnApi'
import type { ModelSelection } from './modelSelection'
import type { PublicAgentTurn } from './publicAgentTurn'

// 本地会话与消息的前端类型（领域模型层）：消息只存页面内存，
// AGENT 消息携带闭合 PublicAgentTurn；resumeToken 仅存会话内存
// 加活跃会话的 sessionStorage 槽位。（Slice 5 / handoff §3）

/** 会话窗口中的单条消息：USER 输入，或携带闭合 Turn 的 AGENT 回复。 */
export interface AgentMessage {
  id: string
  role: 'USER' | 'AGENT'
  /** USER：访客输入；AGENT：供会话窗口/无障碍使用的简短文本。 */
  content: string
  /** 仅 AGENT 消息：已通过 mapper 校验的闭合 PublicAgentTurn。 */
  turn?: PublicAgentTurn
  createdAt: number
  /** USER 轮次送达标记：true 表示请求失败或已取消，不进入 conversationWindow 上送。（A2-04） */
  failed?: boolean
  /** 携带澄清挑战的 AGENT 消息：true 表示答案已提交，挑战卡转只读防重复提交。（A2-18） */
  clarificationConsumed?: boolean
}

/** 会话内纯展示通知（UI spec §2.4/§2.9）：不产生 Turn、不进入 conversationWindow。 */
export interface AgentThreadNotice {
  id: string
  createdAt: number
  kind: 'MODEL_SWITCHED' | 'MODEL_REASK' | 'MODEL_STALE_FALLBACK'
  title: string
  detail?: string
}

/** 一个本地会话：页面内存中的消息流，加上会话级服务端凭证与讨论状态绑定。 */
export interface AgentSession {
  id: string
  title: string
  // 完整首问仅作辅助信息（悬停/aria）；标题本身保持为可扫描的短标题。（体验闭环 §8）
  titleDetail?: string
  role: AudienceRole
  projectSlug: string | null
  seedFingerprint: string | null
  createdAt: number
  updatedAt: number
  messages: AgentMessage[]
  /** 会话私有输入草稿：仅页面内存，切换会话时互不串写。（A2-09） */
  draft?: string
  /**
   * 会话内模型偏好（UI spec §5.4/D-MS-3）：undefined = 使用目录默认；
   * 只存在页面内存，逐轮随 Turn 请求显式携带，不写任何浏览器存储或 URL。
   */
  modelSelection?: ModelSelection
  /** 会话内展示通知流：与 messages 按时间交错渲染，但不进入会话窗口。 */
  notices: AgentThreadNotice[]
  /** 服务端会话身份与凭证：仅页面内存，不落任何持久化存储。 */
  conversationId?: string
  resumeToken?: string
  /** 服务端拥有的单调递增讨论投影版本号，用于丢弃陈旧的讨论状态。 */
  discussionRevision: number
  /** 服务端 typed discussion focus；仅页面内存，由 Turn/Summary 刷新。 */
  activeDiscussion?: CurrentDiscussionSummary
  /** 合同损坏时暂停主题讨论的语义续读，直到权威状态恢复为止。 */
  discussionPaused?: boolean
}

/** createSession 的可选初始化参数。 */
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
  /** 首页交接时已有的会话凭证：用于延续同一服务端会话（可省略）。 */
  conversation?: {
    conversationId: string
    resumeToken: string
    discussionRevision?: number
    activeDiscussion?: CurrentDiscussionSummary
  }
  /** 同 requestId + 同 fingerprint 时精确重放首页轮次；surface/window 必须与原请求一致。（D-31） */
  replay?: {
    requestId: string
    command: AgentTurnCommand
    surfaceContext?: SurfaceContext
  }
}
