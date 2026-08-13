import type { AnswerSectionType, PublicSourceReference } from './answerTypes'
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
  // P3：聚焦回答的公开来源引用（handoff §8），按后端顺序、展示层去重保序。
  sources: PublicSourceReference[]
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
    return { answerMessageId: '', focusEvidenceIds: [], citations: [], sources: [] }
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

  // P3：聚合章节与推荐项的公开来源引用，去重保序（按 referenceKey 第一次出现）。
  const sources: PublicSourceReference[] = []
  const seenSources = new Set<string>()
  const collect = (reference: PublicSourceReference) => {
    if (seenSources.has(reference.referenceKey)) return
    seenSources.add(reference.referenceKey)
    sources.push(reference)
  }
  for (const section of message.answer.sections) {
    for (const reference of section.sourceReferences ?? []) collect(reference)
  }
  const recommendation = message.answer.portfolioRecommendation
  if (recommendation) {
    for (const item of recommendation.items) {
      for (const reference of item.sourceReferences ?? []) collect(reference)
    }
  }

  return {
    answerMessageId: message.id,
    focusEvidenceIds: [...new Set(message.answer.evidenceIds)],
    citations,
    sources,
  }
}
