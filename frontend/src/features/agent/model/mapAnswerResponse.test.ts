import { describe, expect, it } from 'vitest'

import { mapAnswerResponse } from './mapAnswerResponse'

describe('mapAnswerResponse', () => {
  it('maps structured sections and evidence ids without inventing content', () => {
    const mapped = mapAnswerResponse({
      requestId: 'request-1',
      answerMode: 'DETERMINISTIC',
      matched: true,
      fallback: false,
      answer: {
        title: '项目说明',
        sections: [
          { type: 'BACKGROUND', content: '背景内容' },
          { type: 'VERIFICATION', content: '验证内容' },
        ],
      },
      evidence: [
        {
          id: 'evidence-1',
          title: '证据',
          type: 'DOCUMENT',
          periodStart: '2026-07-01',
          periodEnd: '2026-07-10',
          sourceCount: 1,
          summary: '摘要',
          supportedClaims: ['已验证'],
          publicStatus: 'APPROVED',
          rawContentPublic: false,
        },
      ],
      suggestedQuestions: [],
    })

    expect(mapped.content).toBe('项目说明\n\n背景内容\n\n验证内容')
    expect(mapped.evidenceIds).toEqual(['evidence-1'])
  })

  it('rejects an answer whose title and sections are all blank', () => {
    expect(() =>
      mapAnswerResponse({
        requestId: 'request-blank',
        answerMode: 'DETERMINISTIC',
        matched: true,
        fallback: false,
        answer: {
          title: '  ',
          sections: [
            { type: 'BACKGROUND', content: '\n' },
            { type: 'VERIFICATION', content: '\t' },
          ],
        },
        evidence: [],
        suggestedQuestions: [],
      }),
    ).toThrowError('Answer response has no content')
  })

  it('preserves a nonblank boundary answer when matched is false', () => {
    const mapped = mapAnswerResponse({
      requestId: 'request-boundary',
      answerMode: 'DETERMINISTIC',
      matched: false,
      fallback: false,
      answer: {
        title: '项目说明',
        sections: [
          { type: 'BOUNDARY', content: '当前版本仅支持推荐问题。' },
        ],
      },
      evidence: [],
      suggestedQuestions: ['请介绍项目'],
    })

    expect(mapped).toEqual({
      content: '项目说明\n\n当前版本仅支持推荐问题。',
      evidenceIds: [],
    })
  })
})
