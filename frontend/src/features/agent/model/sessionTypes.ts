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
  role: AudienceRole
  projectSlug: string | null
  evidenceId: string | null
  seedFingerprint: string | null
  createdAt: number
  updatedAt: number
  messages: AgentMessage[]
  coveredTopics: ConversationTopic[]
  pendingConfirmation?: PendingPlanConfirmation
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
