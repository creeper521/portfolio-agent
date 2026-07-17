import type { AnswerResponse } from './answerTypes'

export function mapAnswerResponse(response: AnswerResponse) {
  const content = [response.answer.title]
    .concat(response.answer.sections.map((section) => section.content))
    .filter((item) => item.trim().length > 0)
    .join('\n\n')

  if (!content) {
    throw new Error('Answer response has no content')
  }

  return {
    content,
    evidenceIds: response.evidence.map((item) => item.id),
  }
}
