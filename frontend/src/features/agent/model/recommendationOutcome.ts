// 推荐数量完整性视图（交接规格 2026-08-17 §3/§4.4/§10）。
// 新契约字段（requestedSize/actualSize/reasonCodes）齐全时给出明确「找到 X/Y」表达；
// 字段缺失时使用中性文案，绝不根据卡片数量宣称「全部满足」。
// 前端不推断缺失项目、不补造原因，reasonCodes 仅映射不直接显示。

export type RecommendationFulfillment = 'FULL' | 'PARTIAL' | 'UNKNOWN'

export interface RecommendationOutcomeSource {
  itemCount: number
  requestedSize?: number
  actualSize?: number
  reasonCodes?: readonly string[]
  unsatisfiedConstraints?: readonly string[]
}

export interface RecommendationOutcomeView {
  requestedSize: number | null
  actualSize: number | null
  itemCount: number
  fulfillment: RecommendationFulfillment
  headline: string
  statusLabel: string | null
  reasonText: string | null
  ariaLabel: string
  showRecovery: boolean
}

// 后端公开原因码闭集（后端确定性路由闭环设计 §9）；未知码 fail-safe 为通用文案。
const REASON_LABELS: Record<string, string> = {
  INSUFFICIENT_ELIGIBLE_PROJECTS: '公开候选项目数量不足',
  INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS: '其余公开项目的证据完整度暂不足',
  CAPABILITY_COVERAGE_INCOMPLETE: '当前能力约束无法完全覆盖',
}

export function recommendationReasonText(code: string | undefined): string | null {
  if (code === undefined) return null
  return REASON_LABELS[code] ?? '部分条件暂未满足'
}

function validSize(value: number | undefined): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null
}

function joinReasons(texts: readonly string[]): string | null {
  const meaningful = texts.map((text) => text.trim()).filter(Boolean)
  return meaningful.length ? meaningful.join('；') : null
}

export function deriveRecommendationOutcome(
  source: RecommendationOutcomeSource,
): RecommendationOutcomeView {
  const requestedSize = validSize(source.requestedSize)
  const actualSize = validSize(source.actualSize)
  const itemCount = validSize(source.itemCount) ?? 0

  if (requestedSize === null || actualSize === null) {
    const neutral = `作品推荐 · ${itemCount} 项`
    return {
      requestedSize,
      actualSize,
      itemCount,
      fulfillment: 'UNKNOWN',
      headline: neutral,
      statusLabel: null,
      reasonText: null,
      ariaLabel: neutral,
      showRecovery: false,
    }
  }

  if (actualSize >= requestedSize) {
    return {
      requestedSize,
      actualSize,
      itemCount,
      fulfillment: 'FULL',
      headline: `找到 ${actualSize} 个符合条件的项目`,
      statusLabel: null,
      reasonText: null,
      ariaLabel: `作品推荐 · 找到 ${actualSize} 项`,
      showRecovery: false,
    }
  }

  const mappedCodes = (source.reasonCodes ?? [])
    .map(recommendationReasonText)
    .filter((text): text is string => text !== null)
  const reasonText = joinReasons(mappedCodes)
    ?? joinReasons(source.unsatisfiedConstraints ?? [])
    ?? '部分条件暂未满足'
  return {
    requestedSize,
    actualSize,
    itemCount,
    fulfillment: 'PARTIAL',
    headline: `找到 ${actualSize}/${requestedSize} 个符合条件的项目`,
    statusLabel: actualSize === 0 ? '暂无结果' : '部分完成',
    reasonText,
    ariaLabel: `作品推荐 · 找到 ${actualSize}/${requestedSize} 项`,
    showRecovery: true,
  }
}
