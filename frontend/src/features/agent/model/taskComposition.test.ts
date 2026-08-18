import { describe, expect, it, vi } from 'vitest'

import { mapSemanticTurnResponse } from './semanticTurnView'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import type { AgentTurnPayload } from './answerTypes'

// P4 任务级 composition 映射（设计 §11.2 / handoff §2.2/§3/§6）。
// 三种 composition 使用同一套章节/引用 UI；composition 仅用于协议状态与测试。
function readyTurn(completedTasks: Array<Record<string, unknown>>): AgentTurnPayload {
  return {
    contractVersion: 'stp-v1',
    disposition: 'READY',
    outcome: {},
    completedTasks,
  } as unknown as AgentTurnPayload
}

function sectionTask(
  displayIndex: string,
  content: string,
  composition?: unknown,
): Record<string, unknown> {
  const task: Record<string, unknown> = {
    displayIndex,
    goalLabel: `任务 ${displayIndex}`,
    sourceDomain: 'PORTFOLIO',
    resultPayload: {
      kind: 'SECTION_RESULT',
      blocks: [{
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        title: `${displayIndex} 标题`,
        content,
        claimIds: [],
        evidenceIds: [],
      }],
    },
  }
  if (composition !== undefined) task.composition = composition
  return task
}

describe('P4 task composition mapping', () => {
  it('maps a single MODEL_GROUNDED task and keeps the section body', () => {
    const view = mapSemanticTurnResponse(readyTurn([
      sectionTask('01', '模型正文', { mode: 'MODEL_GROUNDED', degraded: false }),
    ]))

    expect(view.disposition).toBe('READY')
    expect(view.completedTasks[0]?.composition)
      .toEqual({ mode: 'MODEL_GROUNDED', degraded: false })
    expect(view.completedTasks[0]?.resultPayload.kind).toBe('SECTION_RESULT')
  })

  it('maps a single FALLBACK task as a successful answer with no failure state', () => {
    const view = mapSemanticTurnResponse(readyTurn([
      sectionTask('01', 'fallback 正文', { mode: 'FALLBACK', degraded: true }),
    ]))

    // Fallback 仍是成功回答：disposition 保持 READY，不进入 BOUNDARY/REJECTED。
    expect(view.disposition).toBe('READY')
    expect(view.completedTasks[0]?.composition)
      .toEqual({ mode: 'FALLBACK', degraded: true })
  })

  it('maps a MIXED turn with per-task compositions without losing content', () => {
    const view = mapSemanticTurnResponse(readyTurn([
      sectionTask('01', '确定性正文', { mode: 'DETERMINISTIC', degraded: false }),
      sectionTask('02', '模型正文', { mode: 'MODEL_GROUNDED', degraded: false }),
    ]))

    expect(view.completedTasks).toHaveLength(2)
    expect(view.completedTasks[0]?.composition?.mode).toBe('DETERMINISTIC')
    expect(view.completedTasks[1]?.composition?.mode).toBe('MODEL_GROUNDED')
    // 每个 task payload 均展示，不丢正文
    expect(view.completedTasks.map((task) => task.resultPayload.kind))
      .toEqual(['SECTION_RESULT', 'SECTION_RESULT'])
  })

  it('treats a missing composition as compatible without reporting a diagnostic', () => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')
    const view = mapSemanticTurnResponse(readyTurn([sectionTask('01', '旧响应正文')]))

    expect(view.completedTasks[0]?.composition).toBeUndefined()
    expect(reportSpy).not.toHaveBeenCalled()
  })

  it.each([
    ['an unknown mode', { mode: 'AI_MAGIC', degraded: false }],
    ['a non-boolean degraded', { mode: 'MODEL_GROUNDED', degraded: 'maybe' }],
  ])('drops invalid composition metadata (%s), keeps the trusted body and reports a sanitized diagnostic', (_reason, composition) => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')
    const view = mapSemanticTurnResponse(readyTurn([
      sectionTask('01', '可信正文', composition),
    ]))

    // 非法 composition 只丢 metadata，可信正文保留
    expect(view.completedTasks[0]?.composition).toBeUndefined()
    expect(view.completedTasks[0]?.resultPayload.kind).toBe('SECTION_RESULT')
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.response.invalid',
      errorCode: 'TASK_COMPOSITION_INVALID',
      errorKind: 'INVALID_RESPONSE',
    }))
    // 诊断 payload 不含正文、mode 字面值或 reference key
    const payload = JSON.stringify(reportSpy.mock.calls[0]?.[0])
    expect(payload).not.toContain('可信正文')
    expect(payload).not.toContain('AI_MAGIC')
  })

  it('drops a non-object composition and reports a sanitized diagnostic', () => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')
    const view = mapSemanticTurnResponse(readyTurn([sectionTask('01', '正文', 'garbage')]))

    expect(view.completedTasks[0]?.composition).toBeUndefined()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      errorCode: 'TASK_COMPOSITION_INVALID',
    }))
  })
})
