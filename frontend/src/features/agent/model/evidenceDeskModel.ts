import type { AnswerSectionType } from './answerTypes'
import type { AgentMessage } from './sessionTypes'

export type EvidenceDeskTab = 'EVIDENCE' | 'CITATIONS' | 'SOURCES'

export interface EvidenceCitation {
  id: string
  messageId: string
  sectionType: AnswerSectionType
  sectionTitle: string
  excerpt: string
  evidenceId: string
}

export interface EvidenceDeskContext {
  answerMessageId: string
  focusEvidenceIds: string[]
  citations: EvidenceCitation[]
}

export interface AnswerFocusTarget {
  requestId: number
  messageId: string
  sectionType?: AnswerSectionType
}

export interface EvidenceInspectRequest {
  messageId: string
  evidenceIds: string[]
  sectionType?: AnswerSectionType
}

export function buildEvidenceDeskContext(
  messages: AgentMessage[],
  preferredMessageId = '',
): EvidenceDeskContext {
  const answers = messages.filter(
    (message) => message.role === 'AGENT' && message.answer,
  )
  const message =
    answers.find((item) => item.id === preferredMessageId) ??
    answers.at(-1)
  if (!message?.answer) {
    return { answerMessageId: '', focusEvidenceIds: [], citations: [] }
  }

  const citations = message.answer.sections.flatMap((section) =>
    section.evidenceIds.map((evidenceId) => ({
      id: `${message.id}:${section.type}:${evidenceId}`,
      messageId: message.id,
      sectionType: section.type,
      sectionTitle: section.title,
      excerpt: section.content,
      evidenceId,
    })),
  )

  return {
    answerMessageId: message.id,
    focusEvidenceIds: [...new Set(message.answer.evidenceIds)],
    citations,
  }
}
