// P5 stp-v2 Consumer Compatibility Preflight 回归（设计 §6 FE-0 / Spec §17.3）。
//
// 门禁：在后端首次产出 stp-v2 新语义前，前端对未知/未来枚举与 CONTEXT_INVALIDATED
// 既不崩溃，也不误入通用澄清卡。完整映射在 FE-1，恢复卡 UI 在 FE-5；这里只锁定安全下限。
import { describe, expect, it } from 'vitest'

import { mapSemanticTurnResponse } from './semanticTurnView'

describe('P5 stp-v2 preflight safety', () => {
  it('maps a CONTEXT_INVALIDATED disposition without throwing and never routes to generic clarification', () => {
    const view = mapSemanticTurnResponse({ disposition: 'CONTEXT_INVALIDATED' })
    expect(view.completedTasks).toEqual([])
    // 关键不变量：不误入通用澄清卡（设计 §3.3 / handoff §3）。
    expect(view.disposition).not.toBe('CLARIFICATION_REQUIRED')
  })

  it('maps an unknown future disposition without throwing', () => {
    const view = mapSemanticTurnResponse({ disposition: 'SOME_FUTURE_STATE' })
    expect(view.completedTasks).toEqual([])
    expect(view.disposition).not.toBe('CLARIFICATION_REQUIRED')
  })

  it('maps a stp-v2 contractVersion payload without throwing (full mapping lands in FE-1)', () => {
    const view = mapSemanticTurnResponse({
      contractVersion: 'stp-v2',
      disposition: 'READY',
      outcome: {},
      completedTasks: [],
    })
    expect(view.completedTasks).toEqual([])
  })
})
