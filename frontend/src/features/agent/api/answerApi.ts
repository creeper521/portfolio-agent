import { request } from '../../portfolio/api/portfolioApi'
import type { AnswerResponse } from '../model/answerTypes'

export function askQuestion(projectSlug: string, question: string): Promise<AnswerResponse> {
  return request<AnswerResponse>('/api/v1/answers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectSlug, question }),
  })
}
