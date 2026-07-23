import { describe, expect, it } from 'vitest'

import type { AgentMessage } from './sessionTypes'
import { buildEvidenceDeskContext } from './evidenceDeskModel'

const messages: AgentMessage[] = [{
  id: 'answer-1',
  role: 'AGENT',
  content: '摘要',
  createdAt: 1,
  evidenceIds: ['evidence-a'],
  answer: {
    title: '回答',
    summary: '摘要',
    resolution: 'ANSWERED',
    answerSource: 'PRESET',
    generationMode: 'DETERMINISTIC',
    verification: 'VERIFIED',
    evidenceIds: ['evidence-a'],
    suggestedQuestionPresetIds: [],
    sections: [{
      type: 'VERIFICATION',
      title: '验证',
      content: '通过公开交付物核验。',
      evidenceIds: ['evidence-a'],
    }],
  },
}]

describe('buildEvidenceDeskContext', () => {
  it('maps the current answer into focused evidence and citations', () => {
    expect(buildEvidenceDeskContext(messages)).toEqual({
      answerMessageId: 'answer-1',
      focusEvidenceIds: ['evidence-a'],
      citations: [{
        id: 'answer-1:VERIFICATION:evidence-a',
        messageId: 'answer-1',
        sectionType: 'VERIFICATION',
        sectionTitle: '验证',
        excerpt: '通过公开交付物核验。',
        evidenceId: 'evidence-a',
      }],
    })
  })

  it('returns a stable empty context when no Agent answer exists', () => {
    expect(buildEvidenceDeskContext([])).toEqual({
      answerMessageId: '',
      focusEvidenceIds: [],
      citations: [],
    })
  })
})
