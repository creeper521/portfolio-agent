/**
 * 时间线排序：纯展示层派生。
 *
 * 排序键从 dateLabel 前端解析（不改动已审核 JSON）：
 *   ^(\d{4})\.(\d{2})(?:[–—](\d{2}))?$
 * 区间标签取起始年月作为键 year*100+month；倒序，最新在前
 * （符合档案从最近往回翻的阅读方向）。
 *
 * dash 兼容：生产数据（bundle/portfolio.json）实测用 en-dash U+2013；
 * 预览 fixture 用 em-dash U+2014，两者都按区间处理，行为一致。
 * 解析失败的条目排到最后，并保持原相对顺序（依赖现代 JS Array.sort 的稳定性）。
 */

/** 只需要 dateLabel 与稳定标识的最小条目形状，避免耦合完整 timeline 类型。 */
export interface DatedTimelineItem {
  id: string
  dateLabel: string
}

// en-dash U+2013 或 em-dash U+2014 均视为区间分隔
const DATE_LABEL_PATTERN = /^(\d{4})\.(\d{2})(?:[\u2013\u2014](\d{2}))?$/

// 解析失败用最小键，使其在倒序（最新在前）里沉到底部，排到最后。
const UNPARSEABLE = Number.NEGATIVE_INFINITY

/** dateLabel 解析出的起止年月键（year*100+month，越大越新）。 */
export interface TimelineLabelRange {
  /** 起始年月键 */
  start: number
  /** 结束年月键；单月标签与 start 相同。区间标签按现行格式不跨年度。 */
  end: number
}

/**
 * 把 dateLabel 解析为起止年月键（year*100+month）。
 * 区间标签（2026.06–07 / 2026.06—07）end 取结束月；单月标签 end 与 start 相同。
 * 解析失败返回 null。
 */
export function timelineLabelRange(dateLabel: string): TimelineLabelRange | null {
  const match = DATE_LABEL_PATTERN.exec(dateLabel)
  if (!match) return null
  const year = Number.parseInt(match[1]!, 10)
  const startMonth = Number.parseInt(match[2]!, 10)
  if (Number.isNaN(year) || Number.isNaN(startMonth)) return null
  const endMonth =
    match[3] === undefined ? startMonth : Number.parseInt(match[3], 10)
  if (Number.isNaN(endMonth)) return null
  return { start: year * 100 + startMonth, end: year * 100 + endMonth }
}

/**
 * 把 dateLabel 解析为可比较的数值键。
 * 越大越新（用于倒序）；解析失败返回 -Infinity，使其在倒序里沉到底部。
 */
function sortKey(dateLabel: string): number {
  return timelineLabelRange(dateLabel)?.start ?? UNPARSEABLE
}

/**
 * 时间线倒序比较器（最新在前）。
 * 解析失败的条目排到最后；同键返回 0，由稳定排序保持原相对顺序。
 */
export function compareTimelineByDateDesc(
  a: DatedTimelineItem,
  b: DatedTimelineItem,
): number {
  return sortKey(b.dateLabel) - sortKey(a.dateLabel)
}
