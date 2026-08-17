import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import type { ConversationSuggestedQuestion } from './answerTypes'

export interface CompleteSuggestedQuestionsOptions {
  currentQuestion?: string
  recentQuestions?: readonly string[]
  // 体验闭环（2026-08-17 §7）：推荐回答后的补全建议优先围绕当前结果集合，
  // 不被页面默认项目污染。只影响补全顺序，不改变去重与数量规则。
  preferredProjects?: readonly string[]
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
    const preferred = new Set(options.preferredProjects ?? [])
    const preferredRank = (slug: string | null): number => (slug !== null && preferred.has(slug) ? 1 : 0)
    const orderedPresets = preferred.size
      ? [...portfolio.questionPresets].sort((left, right) =>
          preferredRank(right.projectSlug) - preferredRank(left.projectSlug))
      : portfolio.questionPresets
    for (const preset of orderedPresets) {
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
