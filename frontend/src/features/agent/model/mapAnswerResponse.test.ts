import { describe, expect, it } from 'vitest'

import { mapAnswerResponse } from './mapAnswerResponse'

describe('mapAnswerResponse', () => {
  function response(resolution: 'ANSWERED' | 'BOUNDARY' = 'ANSWERED') {
    return {
      requestId: 'request-1',
      turnId: 'turn-1',
      contentVersion: '2026-07-21',
      questionPresetId: resolution === 'ANSWERED' ? 'preset-1' : undefined,
      resolution,
      answerSource: resolution === 'ANSWERED' ? ('PRESET' as const) : undefined,
      generationMode: 'DETERMINISTIC' as const,
      verification: resolution === 'ANSWERED' ? ('VERIFIED' as const) : ('NOT_APPLICABLE' as const),
      title: '项目说明',
      summary: '公开摘要',
      sections: [{
        type: resolution === 'ANSWERED' ? ('BACKGROUND' as const) : ('BOUNDARY' as const),
        title: '背景',
        content: '结构化内容',
        evidenceIds: resolution === 'ANSWERED' ? ['evidence-1'] : [],
        claimIds: resolution === 'ANSWERED' ? ['claim-1'] : [],
      }],
      evidenceIds: resolution === 'ANSWERED' ? ['evidence-1'] : [],
      suggestedQuestionPresetIds: ['preset-1'],
    }
  }

  it('preserves structured sections and all four answer dimensions', () => {
    const source = response()
    const mapped = mapAnswerResponse(source)

    expect(mapped.sections).toEqual(source.sections)
    expect(mapped).toMatchObject({
      resolution: 'ANSWERED',
      answerSource: 'PRESET',
      generationMode: 'DETERMINISTIC',
      verification: 'VERIFIED',
      evidenceIds: ['evidence-1'],
    })
    expect(mapped.sections[0].claimIds).toEqual(['claim-1'])
    expect(mapped.sections[0].claimIds).not.toBe(source.sections[0].claimIds)
  })

  it('rejects an answer whose title, summary and sections are all blank', () => {
    const blank = response()
    blank.title = ' '
    blank.summary = ' '
    blank.sections[0].content = ' '

    expect(() => mapAnswerResponse(blank)).toThrowError('Answer response has no content')
  })

  it('keeps a boundary unverified and without a fact source', () => {
    const mapped = mapAnswerResponse(response('BOUNDARY'))

    expect(mapped.resolution).toBe('BOUNDARY')
    expect(mapped.answerSource).toBeNull()
    expect(mapped.verification).toBe('NOT_APPLICABLE')
    expect(mapped.evidenceIds).toEqual([])
  })
})
