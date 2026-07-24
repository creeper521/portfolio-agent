import type { AnswerResponse, MappedAnswer } from './answerTypes'

export function mapAnswerResponse(response: AnswerResponse): MappedAnswer {
  const isBlank = !response.title?.trim() && !response.summary?.trim() &&
    (!response.sections || response.sections.every(s => !s.content?.trim())) &&
    (!response.blocks || response.blocks.every(b => !b.content?.trim()))

  if (isBlank) {
    throw new Error('Answer response has no content')
  }

  const blocks = response.blocks?.map(block => ({
    ...block,
    evidenceIds: [...block.evidenceIds],
    claimIds: [...block.claimIds],
  }))
  const evidenceIds = response.evidenceIds ??
    [...new Set((blocks ?? []).flatMap((block) => block.evidenceIds))]

  return {
    title: response.title || '',
    summary: response.summary || '',
    intent: response.intent,
    answerScope: response.answerScope,
    sections: response.sections?.map((section) => ({
      ...section,
      evidenceIds: [...section.evidenceIds],
      claimIds: [...(section.claimIds ?? [])],
    })) || [],
    blocks,
    resolution: response.resolution,
    answerSource: response.answerSource ?? null,
    generationMode: response.generationMode,
    verification: response.verification,
    evidenceIds: [...evidenceIds],
    suggestedQuestionPresetIds: [...(response.suggestedQuestionPresetIds || [])],
    suggestedQuestions: (response.suggestedQuestions || []).map((suggestion) =>
      typeof suggestion === 'string'
        ? {
            text: suggestion,
            projectSlug: null,
            caseSlug: null,
            facet: null,
          }
        : { ...suggestion },
    ),
    degraded: response.degraded === true,
    contextEnvelope: response.contextEnvelope
      ? {
          ...response.contextEnvelope,
          projectSlugs: response.contextEnvelope.projectSlugs ? [...response.contextEnvelope.projectSlugs] : undefined,
          caseSlugs: response.contextEnvelope.caseSlugs ? [...response.contextEnvelope.caseSlugs] : undefined,
          referencedClaimIds: [...response.contextEnvelope.referencedClaimIds],
        }
      : undefined,
    contextVersionUpdated: response.contextVersionUpdated === true,
  }
}
