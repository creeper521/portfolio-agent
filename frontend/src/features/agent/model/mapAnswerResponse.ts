import type { AnswerResponse, MappedAnswer } from './answerTypes'

export function mapAnswerResponse(response: AnswerResponse): MappedAnswer {
  if (!response.title.trim() && !response.summary.trim() &&
      response.sections.every((section) => !section.content.trim())) {
    throw new Error('Answer response has no content')
  }

  return {
    title: response.title,
    summary: response.summary,
    sections: response.sections.map((section) => ({
      ...section,
      evidenceIds: [...section.evidenceIds],
      claimIds: [...(section.claimIds ?? [])],
    })),
    resolution: response.resolution,
    answerSource: response.answerSource ?? null,
    generationMode: response.generationMode,
    verification: response.verification,
    evidenceIds: [...response.evidenceIds],
    suggestedQuestionPresetIds: [...response.suggestedQuestionPresetIds],
    contextEnvelope: response.contextEnvelope
      ? {
          ...response.contextEnvelope,
          projectSlugs: [...response.contextEnvelope.projectSlugs],
          referencedClaimIds: [...response.contextEnvelope.referencedClaimIds],
        }
      : undefined,
    contextVersionUpdated: response.contextVersionUpdated === true,
  }
}
