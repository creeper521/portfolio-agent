import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import { timelineLabelRange } from './timelineOrder'

/**
 * Hero 右下角竖排边注（folio）：项目编号 + 时间线跨度。
 *
 * 纯展示层派生，与 timelineOrder 共用同一套 dateLabel 解析（不改已审核 JSON）：
 * 取全部时间线条目的最早起始月与最晚结束月，格式化为
 *   P-01 · 2026.04–2026.07
 * 单月跨度只写一个月份；全部标签不可解析时退化为纯项目编号；
 * 无项目时返回 null（Hero 不渲染边注）。
 */

function formatMonthKey(key: number): string {
  const year = Math.floor(key / 100)
  const month = key % 100
  return `${year}.${String(month).padStart(2, '0')}`
}

export function heroMarginalia(
  portfolio: Pick<PublicPortfolio, 'projects' | 'timeline'>,
): string | null {
  const project = portfolio.projects[0]
  if (!project) return null

  let minStart = Number.POSITIVE_INFINITY
  let maxEnd = Number.NEGATIVE_INFINITY
  for (const item of portfolio.timeline) {
    const range = timelineLabelRange(item.dateLabel)
    if (!range) continue
    if (range.start < minStart) minStart = range.start
    if (range.end > maxEnd) maxEnd = range.end
  }

  if (minStart === Number.POSITIVE_INFINITY) return project.code
  if (minStart === maxEnd) return `${project.code} · ${formatMonthKey(minStart)}`
  return `${project.code} · ${formatMonthKey(minStart)}–${formatMonthKey(maxEnd)}`
}
