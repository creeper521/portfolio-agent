import { request } from '../../portfolio/api/portfolioApi'
import type { AnswerResponse, ContextEnvelope } from '../model/answerTypes'
import type { AudienceRole } from '../../public-content/model/publicContentTypes'

export interface AnswerApiRequest {
  turnId: string
  projectSlug?: string | null
  caseSlug?: string | null
  audienceRole: AudienceRole
  source: 'HOME' | 'AGENT_PAGE' | 'PROJECT' | 'EVIDENCE'
  focusEvidenceIds?: string[]
  questionPresetId?: string
  question?: string
  messages?: { role: 'USER' | 'ASSISTANT'; content: string }[]
  contextEnvelope?: ContextEnvelope
}

export function askQuestion(input: AnswerApiRequest): Promise<AnswerResponse> {
  return request<AnswerResponse>('/api/v2/answers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      turnId: input.turnId,
      question: input.question,
      messages: input.messages,
      context: {
        projectSlug: input.projectSlug ?? null,
        caseSlug: input.caseSlug ?? null,
        audienceRole: input.audienceRole,
        source: input.source,
      },
    }),
  })
}
