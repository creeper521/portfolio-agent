import { describe, expect, it } from 'vitest'

import {
  deriveRecommendationOutcome,
  recommendationReasonText,
} from './recommendationOutcome'

describe('deriveRecommendationOutcome', () => {
  it('旧协议缺失 actualSize 时使用中性文案，不根据卡片数量宣称全部满足', () => {
    const outcome = deriveRecommendationOutcome({ itemCount: 2, requestedSize: 2 })
    expect(outcome.fulfillment).toBe('UNKNOWN')
    expect(outcome.headline).toBe('作品推荐 · 2 项')
    expect(outcome.statusLabel).toBeNull()
    expect(outcome.reasonText).toBeNull()
    expect(outcome.showRecovery).toBe(false)
    expect(outcome.ariaLabel).toBe('作品推荐 · 2 项')
    expect(outcome.headline).not.toContain('全部')
    expect(outcome.headline).not.toContain('完成')
  })

  it('新契约字段齐全且数量不足时给出 1/3 表达与部分完成状态', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 1,
      requestedSize: 3,
      actualSize: 1,
      reasonCodes: ['INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS'],
    })
    expect(outcome.fulfillment).toBe('PARTIAL')
    expect(outcome.headline).toBe('找到 1/3 个符合条件的项目')
    expect(outcome.statusLabel).toBe('部分完成')
    expect(outcome.reasonText).toBe('其余公开项目的证据完整度暂不足')
    expect(outcome.showRecovery).toBe(true)
    expect(outcome.ariaLabel).toContain('1/3')
  })

  it('数量满足时展示实际数量且不重复成功徽标', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 2,
      requestedSize: 2,
      actualSize: 2,
    })
    expect(outcome.fulfillment).toBe('FULL')
    expect(outcome.headline).toBe('找到 2 个符合条件的项目')
    expect(outcome.statusLabel).toBeNull()
    expect(outcome.showRecovery).toBe(false)
  })

  it('零结果时明确表达 0/N 而不伪装成功', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 0,
      requestedSize: 3,
      actualSize: 0,
      unsatisfiedConstraints: ['公开候选项目数量不足'],
    })
    expect(outcome.fulfillment).toBe('PARTIAL')
    expect(outcome.headline).toBe('找到 0/3 个符合条件的项目')
    expect(outcome.statusLabel).toBe('暂无结果')
    expect(outcome.reasonText).toBe('公开候选项目数量不足')
  })

  it('未知 reasonCode 映射为通用文案，不显示原始码', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 1,
      requestedSize: 3,
      actualSize: 1,
      reasonCodes: ['SOME_FUTURE_CODE'],
    })
    expect(outcome.reasonText).toBe('部分条件暂未满足')
    expect(outcome.reasonText).not.toContain('SOME_FUTURE_CODE')
  })

  it('多个原因按顺序拼接；reasonCodes 优先于 unsatisfiedConstraints', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 1,
      requestedSize: 3,
      actualSize: 1,
      reasonCodes: ['INSUFFICIENT_ELIGIBLE_PROJECTS', 'CAPABILITY_COVERAGE_INCOMPLETE'],
      unsatisfiedConstraints: ['不应出现'],
    })
    expect(outcome.reasonText).toBe('公开候选项目数量不足；当前能力约束无法完全覆盖')
  })

  it('部分完成但服务端未给出任何原因时使用中性兜底', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 1,
      requestedSize: 3,
      actualSize: 1,
    })
    expect(outcome.reasonText).toBe('部分条件暂未满足')
  })

  it('非法数量字段按缺失处理（负数、非整数）', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 2,
      requestedSize: -1,
      actualSize: 1.5,
    })
    expect(outcome.fulfillment).toBe('UNKNOWN')
    expect(outcome.headline).toBe('作品推荐 · 2 项')
  })

  it('actualSize 超过 requestedSize 时按满足处理', () => {
    const outcome = deriveRecommendationOutcome({
      itemCount: 3,
      requestedSize: 2,
      actualSize: 3,
    })
    expect(outcome.fulfillment).toBe('FULL')
    expect(outcome.headline).toBe('找到 3 个符合条件的项目')
  })
})

describe('recommendationReasonText', () => {
  it('映射闭集原因码', () => {
    expect(recommendationReasonText('INSUFFICIENT_ELIGIBLE_PROJECTS'))
      .toBe('公开候选项目数量不足')
    expect(recommendationReasonText('INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS'))
      .toBe('其余公开项目的证据完整度暂不足')
    expect(recommendationReasonText('CAPABILITY_COVERAGE_INCOMPLETE'))
      .toBe('当前能力约束无法完全覆盖')
    expect(recommendationReasonText('WHATEVER')).toBe('部分条件暂未满足')
    expect(recommendationReasonText(undefined)).toBeNull()
  })
})
