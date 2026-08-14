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
    turnId: 'turn-1',
    contentVersion: '2026-07-21',
    coveredTopics: [],
    guidanceStage: null,
    evidenceIds: ['evidence-a'],
    suggestedQuestionPresetIds: [],
    suggestedQuestions: [],
    sections: [{
      key: 'VERIFICATION:0',
      type: 'VERIFICATION',
      title: '验证',
      sourceScope: 'PORTFOLIO',
      content: '通过公开交付物核验。',
      claimIds: [],
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
      // P3：无公开来源引用时 sources 为空数组（handoff §8）。
      sources: [],
    })
  })

  it('returns a stable empty context when no Agent answer exists', () => {
    expect(buildEvidenceDeskContext([])).toEqual({
      answerMessageId: '',
      focusEvidenceIds: [],
      citations: [],
      sources: [],
    })
  })

  it('aggregates P3 public source references from sections in order with dedup', () => {
    const withSources: AgentMessage = {
      ...messages[0]!,
      answer: {
        ...messages[0]!.answer!,
        sections: [{
          ...messages[0]!.answer!.sections[0]!,
          sourceReferences: [
            { referenceKey: 'SRC_A', label: 'A', sourceType: 'DOCUMENT', subjectRoute: '/projects/sql-audit', publishedVersion: 'v1' },
            { referenceKey: 'SRC_B', label: 'B', sourceType: 'CODE', subjectRoute: '/projects/sql-audit', publishedVersion: 'v1' },
            { referenceKey: 'SRC_A', label: 'A dup', sourceType: 'DOCUMENT', subjectRoute: '/projects/sql-audit', publishedVersion: 'v1' },
          ],
        }],
      },
    }
    const context = buildEvidenceDeskContext([withSources])
    expect(context.sources.map((s) => s.referenceKey)).toEqual(['SRC_A', 'SRC_B'])
  })

  it('prefers the P5 publicSourceCatalog over scattered inline sourceReferences (FE-6)', () => {
    // catalog 存在时作为 SOURCES 权威目录；inline sourceReferences 不再混入
    const withCatalog: AgentMessage = {
      ...messages[0]!,
      answer: {
        ...messages[0]!.answer!,
        publicSourceCatalog: [
          { referenceKey: 'CAT_A', label: '目录 A', sourceType: 'DOCUMENT', subjectRoute: '/projects/sql-audit', publishedVersion: 'v2' },
          { referenceKey: 'CAT_B', label: '目录 B', sourceType: 'SCREENSHOT', subjectRoute: '/cases/case-one', evidenceRoute: '/evidence?evidence=evi-1', publishedVersion: 'v2' },
        ],
        sections: [{
          ...messages[0]!.answer!.sections[0]!,
          sourceReferences: [
            { referenceKey: 'INLINE_X', label: '内联', sourceType: 'CODE', subjectRoute: '/projects/sql-audit', publishedVersion: 'v1' },
          ],
        }],
      },
    }
    const context = buildEvidenceDeskContext([withCatalog])
    expect(context.sources.map((s) => s.referenceKey)).toEqual(['CAT_A', 'CAT_B'])
    // 目录项字段完整透传（含 evidenceRoute）
    expect(context.sources[1]).toMatchObject({ evidenceRoute: '/evidence?evidence=evi-1' })
    // inline sourceReferences 被权威目录取代，不再出现
    expect(context.sources.some((s) => s.referenceKey === 'INLINE_X')).toBe(false)
  })

  it('prefers an explicitly inspected answer and otherwise selects the latest answer', () => {
    const latest: AgentMessage = {
      ...messages[0]!,
      id: 'answer-2',
      createdAt: 2,
      evidenceIds: ['evidence-b'],
      answer: {
        ...messages[0]!.answer!,
        evidenceIds: ['evidence-b'],
        sections: [{
          ...messages[0]!.answer!.sections[0]!,
          evidenceIds: ['evidence-b'],
        }],
      },
    }
    const conversation = [messages[0]!, latest]

    expect(buildEvidenceDeskContext(conversation).answerMessageId).toBe('answer-2')
    expect(buildEvidenceDeskContext(conversation, 'answer-1').answerMessageId)
      .toBe('answer-1')
    expect(buildEvidenceDeskContext(conversation, 'missing-answer').answerMessageId)
      .toBe('answer-2')
  })
})
