import { describe, expect, it } from 'vitest'

import { compareTimelineByDateDesc, type DatedTimelineItem } from './timelineOrder'

/**
 * 排序键从 dateLabel 前端解析，格式 ^(\d{4})\.(\d{2})(?:[–—](\d{2}))?$。
 * 取起始年月 year*100+month 做键，倒序（最新在前，符合档案从最近往回翻的阅读方向）。
 * 真实数据（bundle/portfolio.json）用 en-dash U+2013；预览 fixture 用 em-dash U+2014，两者都需支持。
 */
describe('compareTimelineByDateDesc', () => {
  it('sorts mixed single-month and range labels descending by start month', () => {
    // 生产数据实测乱序：06–07 / 07 / 04–06 / 06–07 / 05
    const items: DatedTimelineItem[] = [
      { id: 'a', dateLabel: '2026.06–07' },
      { id: 'b', dateLabel: '2026.07' },
      { id: 'c', dateLabel: '2026.04–06' },
      { id: 'd', dateLabel: '2026.06–07' },
      { id: 'e', dateLabel: '2026.05' },
    ]

    const sorted = [...items].sort(compareTimelineByDateDesc)

    expect(sorted.map((item) => item.id)).toEqual(['b', 'a', 'd', 'e', 'c'])
  })

  it('accepts em-dash (U+2014) labels the same as en-dash (U+2013)', () => {
    // 预览 fixture 用 em-dash，需与生产 en-dash 行为一致
    const items: DatedTimelineItem[] = [
      { id: 'range', dateLabel: '2026.04—06' },
      { id: 'single', dateLabel: '2026.07' },
    ]

    const sorted = [...items].sort(compareTimelineByDateDesc)

    expect(sorted.map((item) => item.id)).toEqual(['single', 'range'])
  })

  it('keeps the original relative order for items sharing the same sort key (stable)', () => {
    // 同键保持原相对顺序（现代 JS Array.sort 稳定）
    const items: DatedTimelineItem[] = [
      { id: 'first', dateLabel: '2026.06–07' },
      { id: 'second', dateLabel: '2026.07' },
      { id: 'third', dateLabel: '2026.06–07' },
    ]

    const sorted = [...items].sort(compareTimelineByDateDesc)

    // 两个 06–07 键：first 仍在 third 之前
    expect(sorted.map((item) => item.id)).toEqual(['second', 'first', 'third'])
  })

  it('sends unparseable labels to the end while preserving their relative order', () => {
    const items: DatedTimelineItem[] = [
      { id: 'bad1', dateLabel: 'TBD' },
      { id: 'good', dateLabel: '2026.07' },
      { id: 'bad2', dateLabel: '' },
    ]

    const sorted = [...items].sort(compareTimelineByDateDesc)

    expect(sorted.map((item) => item.id)).toEqual(['good', 'bad1', 'bad2'])
  })
})
