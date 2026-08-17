import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { ConversationTopic, MappedAnswer } from './answerTypes'
import type { OpaquePlanConfirmation } from './semanticTurnView'

export interface PendingPlanConfirmation extends OpaquePlanConfirmation {}

export interface AgentMessage {
  id: string
  role: 'USER' | 'AGENT'
  content: string
  answer?: MappedAnswer | null
  createdAt: number
  evidenceIds: string[]
}

export interface AgentSession {
  id: string
  title: string
  // 体验闭环 §8：完整首问作为辅助信息（悬停/aria），标题本身是可扫描短标题。
  titleDetail?: string
  role: AudienceRole
  projectSlug: string | null
  evidenceId: string | null
  seedFingerprint: string | null
  createdAt: number
  updatedAt: number
  messages: AgentMessage[]
  coveredTopics: ConversationTopic[]
  pendingConfirmation?: PendingPlanConfirmation
  // P3：该会话绑定的服务端 conversation ResumeToken（仅内存，handoff §10.1）。
  // 一会话一 Token；不透明值，前端不生成/解析/修改。sessionStorage 只保存活跃会话的 Token。
  resumeToken?: string
  // P3：刷新恢复得到的安全业务上下文摘要（仅活跃会话恢复卡使用）。
  activeContextSummary?: import('./answerTypes').ConversationContextSummary
}

export interface SessionSeed {
  title?: string
  role?: AudienceRole
  projectSlug?: string | null
  evidenceId?: string | null
}

export interface AgentRouteSeed {
  role: AudienceRole
  question: string
  answer: MappedAnswer
  projectSlug: string | null
  evidenceIds: string[]
  source: 'HOME' | 'PROJECT' | 'EVIDENCE'
}
