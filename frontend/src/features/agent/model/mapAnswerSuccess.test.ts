import { describe, expect, it, vi } from 'vitest'

import { mapAnswerSuccess } from './mapAnswerResponse'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import type {
  AnswerResponse,
  CompletionReceiptResponse,
  PublicSourceReference,
} from './answerTypes'

const SOURCE: PublicSourceReference = {
  referenceKey: 'SRC_SQL_AUDIT_DELIVERED',
  label: 'SQL 审计 · 交付证据',
  sourceType: 'DOCUMENT',
  subjectRoute: '/projects/sql-audit',
  evidenceRoute: '/evidence?evidence=evi-sql-audit',
  publishedVersion: 'public-2026-07-31',
}

function answerWithSources(): AnswerResponse {
  return {
    turnId: 'turn-src',
    contentVersion: 'public-2026-07-31',
    resolution: 'ANSWERED',
    title: '项目说明',
    summary: '摘要',
    blocks: [{
      sourceScope: 'PORTFOLIO',
      sectionType: 'SOLUTION',
      title: '技术方案',
      content: '受控正文',
      claimIds: ['claim-1'],
      evidenceIds: ['evi-sql-audit'],
      sourceReferences: [SOURCE],
    }],
  } as AnswerResponse
}

describe('mapAnswerSuccess', () => {
  it('maps an ANSWER and surfaces block sourceReferences on the section view', () => {
    const mapped = mapAnswerSuccess(answerWithSources())

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    expect(mapped.answer.sections[0]?.sourceReferences).toEqual([SOURCE])
    // P3 与旧引用并存（过渡双读）：不删除旧 evidenceIds。
    expect(mapped.answer.sections[0]?.evidenceIds).toEqual(['evi-sql-audit'])
  })

  it('maps a final P3 semantic block without legacy claim or evidence ids', () => {
    const response = answerWithSources()
    response.agentTurn = {
      contractVersion: 'stp-v1',
      disposition: 'READY',
      outcome: { planOutcome: 'SUCCEEDED' },
      completedTasks: [{
        displayIndex: '01',
        goalLabel: '介绍公开项目',
        sourceDomain: 'PORTFOLIO',
        resultPayload: {
          kind: 'SECTION_RESULT',
          blocks: [{
            sourceScope: 'PORTFOLIO',
            content: '受控正文',
            sourceReferences: [SOURCE],
          }],
        },
      }],
    } as AnswerResponse['agentTurn']

    const mapped = mapAnswerSuccess(response)

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    expect(mapped.answer.sections[0]).toMatchObject({
      content: '受控正文',
      claimIds: [],
      evidenceIds: [],
      sourceReferences: [SOURCE],
    })
  })

  it('keeps the legacy evidenceId citation path when sourceReferences are absent (transitional)', () => {
    const legacy = answerWithSources()
    if (legacy.blocks) delete legacy.blocks[0].sourceReferences

    const mapped = mapAnswerSuccess(legacy)

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    expect(mapped.answer.sections[0]?.sourceReferences).toBeUndefined()
    expect(mapped.answer.sections[0]?.evidenceIds).toEqual(['evi-sql-audit'])
  })

  it('maps the FINAL execution snapshot onto the semantic turn view', () => {
    const response = answerWithSources()
    response.agentTurn = {
      contractVersion: 'stp-v1',
      disposition: 'READY',
      outcome: { planOutcome: 'SUCCEEDED' },
      completedTasks: [],
      execution: {
        contractVersion: 'p3-display-v1',
        snapshotType: 'FINAL',
        overallStatus: 'COMPLETED',
        tasks: [{
          displayIndex: '01',
          finalStatus: 'COMPLETED',
          stages: [
            { code: 'SCOPE_CONFIRMED', label: '确认查询范围', status: 'COMPLETED' },
            { code: 'MATERIALS_RETRIEVED', label: '查找已发布材料', status: 'COMPLETED' },
            { code: 'EVIDENCE_VALIDATED', label: '核验证据', status: 'COMPLETED' },
            { code: 'RESULT_COMPOSED', label: '形成回答', status: 'COMPLETED' },
          ],
        }],
      },
    } as AnswerResponse['agentTurn']

    const mapped = mapAnswerSuccess(response)

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    const execution = mapped.answer.semanticTurn?.execution
    expect(execution).toBeDefined()
    expect(execution?.overallStatus).toBe('COMPLETED')
    expect(execution?.tasks[0]?.stages.map((stage) => stage.code)).toEqual([
      'SCOPE_CONFIRMED', 'MATERIALS_RETRIEVED', 'EVIDENCE_VALIDATED', 'RESULT_COMPOSED',
    ])
  })

  it('drops the execution snapshot and diagnoses when a FINAL response contains IN_PROGRESS', () => {
    const report = vi.spyOn(frontendDiagnostics, 'report')
    const response = answerWithSources()
    response.agentTurn = {
      contractVersion: 'stp-v1',
      disposition: 'READY',
      outcome: { planOutcome: 'SUCCEEDED' },
      completedTasks: [],
      // 契约错误：FINAL 快照不得残留运行中状态（handoff §7）。
      execution: {
        contractVersion: 'p3-display-v1',
        snapshotType: 'FINAL',
        overallStatus: 'COMPLETED',
        tasks: [{
          displayIndex: '01',
          finalStatus: 'COMPLETED',
          stages: [
            { code: 'MATERIALS_RETRIEVED', label: '查找材料', status: 'IN_PROGRESS' as never },
          ],
        }],
      },
    } as AnswerResponse['agentTurn']

    const mapped = mapAnswerSuccess(response)

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    expect(mapped.answer.semanticTurn?.execution).toBeUndefined()
    expect(report).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.response.invalid',
      errorCode: 'EXECUTION_SNAPSHOT_INVALID',
    }))
  })

  it('maps the conversation envelope with resume token and continuation status', () => {
    const response = answerWithSources()
    response.conversation = {
      resumeToken: 'opaque-resume-token',
      continuationStatus: 'AVAILABLE',
    }

    const mapped = mapAnswerSuccess(response)

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    expect(mapped.answer.conversation).toEqual({
      resumeToken: 'opaque-resume-token',
      continuationStatus: 'AVAILABLE',
    })
  })

  it('does not downgrade an established answer when continuation is PERSISTENCE_UNAVAILABLE', () => {
    const report = vi.spyOn(frontendDiagnostics, 'report')
    const response = answerWithSources()
    response.degraded = true
    response.conversation = { continuationStatus: 'PERSISTENCE_UNAVAILABLE' }

    const mapped = mapAnswerSuccess(response)

    expect(mapped.kind).toBe('ANSWER')
    if (mapped.kind !== 'ANSWER') throw new Error('expected ANSWER')
    // 续接不可用是独立维度，不改证据状态（handoff §5, §13.1）。
    expect(mapped.answer.conversation?.continuationStatus).toBe('PERSISTENCE_UNAVAILABLE')
    expect(mapped.answer.evidenceState).toBe('VERIFIED')
    expect(report).not.toHaveBeenCalled()
  })

  it('maps a COMPLETION_RECEIPT without fabricating an answer body', () => {
    const receipt: CompletionReceiptResponse = {
      responseKind: 'COMPLETION_RECEIPT',
      turnId: 'turn-receipt',
      requestToken: '00000000-0000-4000-8000-000000000010',
      requestStatus: 'REQUEST_ALREADY_COMPLETED',
      completedTasks: [
        { displayIndex: '01', status: 'COMPLETED', contextHandle: 'handle-opaque' },
        { displayIndex: '02', status: 'NOT_SUPPORTED' },
      ],
      conversation: { continuationStatus: 'AVAILABLE' },
    }

    const mapped = mapAnswerSuccess(receipt)

    expect(mapped.kind).toBe('COMPLETION_RECEIPT')
    if (mapped.kind !== 'COMPLETION_RECEIPT') throw new Error('expected COMPLETION_RECEIPT')
    expect(mapped.receipt.turnId).toBe('turn-receipt')
    expect(mapped.receipt.completedTasks).toEqual([
      { displayIndex: '01', status: 'COMPLETED', contextHandle: 'handle-opaque' },
      { displayIndex: '02', status: 'NOT_SUPPORTED', contextHandle: undefined },
    ])
    // 回执不得携带伪造的 title/blocks/正文（handoff §4）。
    expect(mapped.receipt).not.toHaveProperty('title')
    expect(mapped.receipt).not.toHaveProperty('blocks')
  })

  it('classifies an unknown responseKind as a contract error', () => {
    const response = { responseKind: 'WHAT', turnId: 't' } as unknown as Parameters<
      typeof mapAnswerSuccess
    >[0]

    const mapped = mapAnswerSuccess(response)

    expect(mapped.kind).toBe('CONTRACT_ERROR')
    if (mapped.kind === 'CONTRACT_ERROR') expect(mapped.responseKind).toBe('WHAT')
  })
})
