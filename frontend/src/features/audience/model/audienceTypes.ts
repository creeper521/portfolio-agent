import type { AudienceRole } from '../../public-content/model/publicContentTypes'
import type { MappedAnswer } from '../../agent/model/answerTypes'

export interface AudienceProfile {
  id: AudienceRole
  code: string
  label: string
  description: string
  questions: string[]
}

export interface HomeAnswerState {
  round: number
  question: string
  answer: MappedAnswer
  projectSlug: string | null
  evidenceIds: string[]
}
