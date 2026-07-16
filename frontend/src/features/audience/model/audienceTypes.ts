import type { AudienceRole } from '../../public-content/model/publicContentTypes'

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
  answer: string
  projectSlug: string | null
  evidenceIds: string[]
}
