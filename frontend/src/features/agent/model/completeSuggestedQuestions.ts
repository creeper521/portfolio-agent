import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import type { ConversationSuggestedQuestion } from './answerTypes'

export interface CompleteSuggestedQuestionsOptions {
  currentQuestion?: string
  recentQuestions?: readonly string[]
}

export interface CompletedSuggestedQuestions {
  questions: ConversationSuggestedQuestion[]
  recoveredCount: number
}

const TARGET_QUESTION_COUNT = 3

export function completeSuggestedQuestions(
  suggestions: readonly ConversationSuggestedQuestion[],
  portfolio: PublicPortfolio,
  options: CompleteSuggestedQuestionsOptions = {},
): CompletedSuggestedQuestions {
  const seen = new Set<string>()
  const questions: ConversationSuggestedQuestion[] = []
  for (const item of suggestions) {
    const text = item.text.trim()
    if (!text) continue
    if (item.projectSlug && item.caseSlug) continue
    if (seen.has(text)) continue
    seen.add(text)
    questions.push({
      text,
      projectSlug: item.projectSlug,
      caseSlug: item.caseSlug,
      facet: item.facet,
    })
    if (questions.length === TARGET_QUESTION_COUNT) break
  }

  let recoveredCount = 0
  if (questions.length < TARGET_QUESTION_COUNT) {
    const excluded = new Set(seen)
    const currentQuestion = options.currentQuestion?.trim()
    if (currentQuestion) excluded.add(currentQuestion)
    for (const recent of options.recentQuestions ?? []) {
      const normalized = recent.trim()
      if (normalized) excluded.add(normalized)
    }
    for (const preset of portfolio.questionPresets) {
      if (questions.length === TARGET_QUESTION_COUNT) break
      if (!preset.placements.includes('AGENT')) continue
      const text = preset.text.trim()
      if (!text || excluded.has(text)) continue
      excluded.add(text)
      questions.push({
        text,
        projectSlug: preset.projectSlug,
        caseSlug: null,
        facet: null,
      })
      recoveredCount += 1
    }
  }

  return { questions, recoveredCount }
}
