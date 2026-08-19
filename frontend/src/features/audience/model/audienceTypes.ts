import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { AgentTurnCommand, SurfaceContext } from '../../agent/api/agentTurnApi'
import type { PublicAgentTurn } from '../../agent/model/publicAgentTurn'

export interface AudienceProfile {
  id: AudienceRole
  code: string
  label: string
  description: string
  questions: string[]
}

/** 首页轻对话状态：直接持有闭合 PublicAgentTurn 与幂等重放输入（交给 Agent 页继续会话）。 */
export interface HomeAnswerState {
  round: number
  question: string
  turn: PublicAgentTurn
  projectSlug: string | null
  conversation: { conversationId: string; resumeToken?: string } | null
  replay: {
    requestId: string
    command: AgentTurnCommand
    surfaceContext: SurfaceContext
  }
}
