import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import type { ConversationSuggestedQuestion } from './answerTypes'
import { completeSuggestedQuestions } from './completeSuggestedQuestions'

function suggestion(
  text: string,
  overrides: Partial<ConversationSuggestedQuestion> = {},
): ConversationSuggestedQuestion {
  return {
    text,
    projectSlug: null,
    caseSlug: null,
    facet: null,
    ...overrides,
  }
}

function portfolioWithPresets(): PublicPortfolio {
  return {
    ...previewPublicContent,
    questionPresets: [
      {
        id: 'preset-a',
        projectSlug: 'sql-audit',
        text: '预设问题 A',
        audiences: ['INTERVIEWER'],
        placements: ['AGENT'],
        contractVersion: 'pcv1-0000000000000001',
        availability: 'ACTIVE',
      },
      {
        id: 'preset-b',
        projectSlug: 'sql-audit',
        text: '预设问题 B',
        audiences: ['INTERVIEWER'],
        placements: ['HOME', 'AGENT'],
        contractVersion: 'pcv1-0000000000000002',
        availability: 'ACTIVE',
      },
      {
        id: 'preset-c',
        projectSlug: 'codegraph-evaluation',
        text: '预设问题 C',
        audiences: ['INTERVIEWER'],
        placements: ['AGENT', 'PROJECT'],
        contractVersion: 'pcv1-0000000000000003',
        availability: 'ACTIVE',
      },
      {
        id: 'preset-home-only',
        projectSlug: 'sql-audit',
        text: '只在首页出现的预设',
        audiences: ['INTERVIEWER'],
        placements: ['HOME'],
        contractVersion: 'pcv1-0000000000000004',
        availability: 'ACTIVE',
      },
    ],
  }
}

describe('completeSuggestedQuestions', () => {
  it('keeps three valid suggestions untouched without recovery', () => {
    const input = [
      suggestion('问题一', { projectSlug: 'sql-audit' }),
      suggestion('问题二', { caseSlug: 'multilingual-image-preservation' }),
      suggestion('问题三'),
    ]

    const result = completeSuggestedQuestions(input, portfolioWithPresets())

    expect(result.questions).toEqual(input)
    expect(result.recoveredCount).toBe(0)
  })

  it('drops blank texts and items that set both projectSlug and caseSlug', () => {
    const result = completeSuggestedQuestions(
      [
        suggestion('   '),
        suggestion('合法问题', { projectSlug: 'sql-audit' }),
        suggestion('非法问题', { projectSlug: 'sql-audit', caseSlug: 'multilingual-image-preservation' }),
      ],
      portfolioWithPresets(),
    )

    expect(result.questions.map((item) => item.text)).toContain('合法问题')
    expect(result.questions.map((item) => item.text)).not.toContain('非法问题')
    expect(result.questions.every((item) => item.text.trim().length > 0)).toBe(true)
  })

  it('deduplicates by text keeping the first occurrence', () => {
    const result = completeSuggestedQuestions(
      [
        suggestion('重复问题', { projectSlug: 'sql-audit' }),
        suggestion('重复问题', { projectSlug: 'codegraph-evaluation' }),
        suggestion('另一个问题'),
        suggestion('第三个问题'),
      ],
      portfolioWithPresets(),
    )

    const duplicates = result.questions.filter((item) => item.text === '重复问题')
    expect(duplicates).toHaveLength(1)
    expect(duplicates[0]?.projectSlug).toBe('sql-audit')
  })

  it('trims suggestion text and keeps only the first three valid items', () => {
    const result = completeSuggestedQuestions(
      [
        suggestion('  问题一  '),
        suggestion('问题二'),
        suggestion('问题三'),
        suggestion('问题四'),
      ],
      portfolioWithPresets(),
    )

    expect(result.questions).toHaveLength(3)
    expect(result.questions[0]?.text).toBe('问题一')
    expect(result.recoveredCount).toBe(0)
  })

  it('fills missing suggestions from AGENT question presets in stable order', () => {
    const result = completeSuggestedQuestions(
      [suggestion('后端给出的唯一问题', { projectSlug: 'sql-audit' })],
      portfolioWithPresets(),
    )

    expect(result.questions).toHaveLength(3)
    expect(result.questions.map((item) => item.text)).toEqual([
      '后端给出的唯一问题',
      '预设问题 A',
      '预设问题 B',
    ])
    expect(result.questions[1]).toEqual({
      text: '预设问题 A',
      projectSlug: 'sql-audit',
      caseSlug: null,
      facet: null,
    })
    expect(result.recoveredCount).toBe(2)
  })

  it('excludes the current question, recent user questions, and existing texts when filling', () => {
    const result = completeSuggestedQuestions(
      [suggestion('预设问题 A', { projectSlug: 'sql-audit' })],
      portfolioWithPresets(),
      {
        currentQuestion: '预设问题 B',
        recentQuestions: ['预设问题 C', '更早的问题'],
      },
    )

    expect(result.questions).toHaveLength(1)
    expect(result.questions[0]?.text).toBe('预设问题 A')
    expect(result.recoveredCount).toBe(0)
  })

  it('never uses presets without the AGENT placement and stops when presets run out', () => {
    const result = completeSuggestedQuestions(
      [],
      {
        ...portfolioWithPresets(),
        questionPresets: [
          {
            id: 'preset-home-only',
            projectSlug: 'sql-audit',
            text: '只在首页出现的预设',
            audiences: ['INTERVIEWER'],
            placements: ['HOME'],
            contractVersion: 'pcv1-0000000000000005',
            availability: 'ACTIVE',
          },
        ],
      },
    )

    expect(result.questions).toHaveLength(0)
    expect(result.recoveredCount).toBe(0)
  })
})
