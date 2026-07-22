import { request } from '../../portfolio/api/portfolioApi'
import type { AnswerResponse } from '../model/answerTypes'
import type { AudienceRole } from '../../public-content/model/publicContentTypes'

export interface AnswerApiRequest {
  turnId: string
  projectSlug: string
  audienceRole: AudienceRole
  source: 'HOME' | 'AGENT_PAGE' | 'PROJECT' | 'EVIDENCE'
  focusEvidenceIds?: string[]
  questionPresetId?: string
  question?: string
}

export function askQuestion(input: AnswerApiRequest): Promise<AnswerResponse> {
  return request<AnswerResponse>('/api/v1/answers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      turnId: input.turnId,
      questionPresetId: input.questionPresetId,
      question: input.question,
      context: {
        projectSlug: input.projectSlug,
        audienceRole: input.audienceRole,
        focusEvidenceIds: input.focusEvidenceIds ?? [],
        source: input.source,
      },
    }),
  })
}
