import type { Evidence } from '../../portfolio/model/portfolioTypes'

export type AnswerMode = 'DETERMINISTIC' | 'MODEL' | 'FALLBACK'
export type AnswerSectionType =
  | 'BACKGROUND'
  | 'RESPONSIBILITY'
  | 'SOLUTION'
  | 'VERIFICATION'
  | 'STATUS'
  | 'BOUNDARY'

export interface AnswerSection {
  type: AnswerSectionType
  content: string
}

export interface AnswerResponse {
  requestId: string
  answerMode: AnswerMode
  matched: boolean
  fallback: boolean
  answer: {
    title: string
    sections: AnswerSection[]
  }
  evidence: Evidence[]
  suggestedQuestions: string[]
}
