import type { AudienceRole } from '../../public-content/model/publicContentTypes'

export interface AgentMessage {
  id: string
  role: 'USER' | 'AGENT'
  content: string
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
  expiresAt: number
  messages: AgentMessage[]
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
  answer: string
  projectSlug: string | null
  evidenceIds: string[]
  source: 'HOME' | 'PROJECT' | 'EVIDENCE'
}
