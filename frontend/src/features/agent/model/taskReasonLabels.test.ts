import { describe, expect, it } from 'vitest'

import { blockedGoalReasonText, taskReasonText } from './taskReasonLabels'

describe('taskReasonLabels', () => {
  it('prefers the specific blocked-by sentence with display indexes', () => {
    expect(taskReasonText({
      reasonCodes: ['EXECUTION_DEPENDENCY_BLOCKED'],
      blockedByDisplayIndexes: ['02', '03'],
    })).toBe('依赖任务 02、03 未完成，因此暂不执行')
  })

  it('maps whitelisted reason codes to safe Chinese text', () => {
    expect(taskReasonText({
      reasonCodes: ['PORTFOLIO_EVIDENCE_INSUFFICIENT'],
      blockedByDisplayIndexes: [],
    })).toBe('公开证据不足，无法生成可信结论')
    expect(taskReasonText({
      reasonCodes: ['GENERAL_PROVIDER_UNAVAILABLE'],
      blockedByDisplayIndexes: [],
    })).toBe('当前公开能力无法完成此任务')
    expect(taskReasonText({
      reasonCodes: ['EXECUTION_UNEXPECTED_FAILURE'],
      blockedByDisplayIndexes: [],
    })).toBe('任务未安全完成，请稍后重试')
  })

  it('falls back to a restrained generic sentence for unknown codes', () => {
    const text = taskReasonText({
      reasonCodes: ['SOME_FUTURE_CODE'],
      blockedByDisplayIndexes: [],
    })
    expect(text).toBe('该任务未能安全完成')
    expect(text).not.toContain('SOME_FUTURE_CODE')
  })

  it('returns null when there is nothing to explain', () => {
    expect(taskReasonText({ reasonCodes: [], blockedByDisplayIndexes: [] })).toBeNull()
  })

  it('maps blocked-goal codes and hides unknown raw codes', () => {
    expect(blockedGoalReasonText('WAITING_FOR_COMPARISON_SUBJECT')).toBe('等待你确认比较对象')
    expect(blockedGoalReasonText('WAITING_FOR_SUBJECT')).toBe('等待你确认主体')
    expect(blockedGoalReasonText('WHATEVER_NEW')).toBe('等待你补充信息')
  })
})
