import type { AnswerResponse } from './answerTypes'

export function mapAnswerResponse(response: AnswerResponse) {
  return {
    content: [response.answer.title]
      .concat(response.answer.sections.map((section) => section.content))
      .filter((item) => item.trim().length > 0)
      .join('\n\n'),
    evidenceIds: response.evidence.map((item) => item.id),
  }
}
