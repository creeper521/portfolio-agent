import { describe, expect, it } from 'vitest'

import { resolveAnswerSuccess } from './answerTypes'
import type { AnswerResponse, CompletionReceiptResponse } from './answerTypes'

// P3 公共契约第一层判别（handoff §4）：200 响应必须先按 responseKind 分流，
// 禁止根据 blocks 是否存在猜类型。缺 responseKind 视为 ANSWER（过渡期 P2 后端兼容）。
describe('resolveAnswerSuccess', () => {
  it('treats a response without responseKind as an ANSWER (transitional P2 compat)', () => {
    const response = {
      turnId: 'turn-1',
      contentVersion: 'v1',
      resolution: 'ANSWERED',
      title: 't',
      summary: 's',
    } as AnswerResponse

    const resolved = resolveAnswerSuccess(response)

    expect(resolved.kind).toBe('ANSWER')
    if (resolved.kind === 'ANSWER') expect(resolved.response.turnId).toBe('turn-1')
  })

  it('routes an explicit ANSWER responseKind to the ANSWER branch', () => {
    const response = {
      responseKind: 'ANSWER',
      turnId: 'turn-2',
      contentVersion: 'v1',
      resolution: 'ANSWERED',
      title: 't',
    } as AnswerResponse

    expect(resolveAnswerSuccess(response).kind).toBe('ANSWER')
  })

  it('routes a COMPLETION_RECEIPT to the receipt branch without reading answer fields', () => {
    const receipt: CompletionReceiptResponse = {
      responseKind: 'COMPLETION_RECEIPT',
      turnId: 'turn-3',
      requestToken: '00000000-0000-4000-8000-000000000003',
      requestStatus: 'REQUEST_ALREADY_COMPLETED',
      completedTasks: [
        { displayIndex: '01', status: 'COMPLETED', contextHandle: 'handle-opaque' },
      ],
      conversation: { continuationStatus: 'AVAILABLE' },
    }

    const resolved = resolveAnswerSuccess(receipt)

    expect(resolved.kind).toBe('COMPLETION_RECEIPT')
    if (resolved.kind === 'COMPLETION_RECEIPT') {
      expect(resolved.response.requestStatus).toBe('REQUEST_ALREADY_COMPLETED')
      expect(resolved.response.completedTasks[0]?.contextHandle).toBe('handle-opaque')
    }
  })

  it('classifies an unknown responseKind as a contract error', () => {
    const response = { responseKind: 'WHATEVER', turnId: 'turn-4' } as unknown as Parameters<
      typeof resolveAnswerSuccess
    >[0]

    const resolved = resolveAnswerSuccess(response)

    expect(resolved.kind).toBe('CONTRACT_ERROR')
    if (resolved.kind === 'CONTRACT_ERROR') expect(resolved.responseKind).toBe('WHATEVER')
  })
})
