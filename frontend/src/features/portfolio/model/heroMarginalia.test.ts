import { describe, expect, it } from 'vitest'

import type {
  PublicProject,
  TimelineEvent,
} from '../../public-content/model/publicContentTypes'
import { heroMarginalia } from './heroMarginalia'

// 最简夹具：heroMarginalia 只读 code / dateLabel，其余字段用断言补齐
function project(code: string): PublicProject {
  return { id: 'p-1', slug: 'p', code } as PublicProject
}

function event(dateLabel: string): TimelineEvent {
  return { id: `t-${dateLabel}`, dateLabel } as TimelineEvent
}

describe('heroMarginalia', () => {
  it('spans mixed-granularity labels with a normalized en-dash range', () => {
    const text = heroMarginalia({
      projects: [project('P-01')],
      timeline: [
        '2026.06–07',
        '2026.07',
        '2026.04–06',
        '2026.06–07',
        '2026.05',
      ].map(event),
    })

    expect(text).toBe('P-01 · 2026.04–2026.07')
  })

  it('accepts em-dash interval labels from the preview fixture', () => {
    const text = heroMarginalia({
      projects: [project('P-01')],
      timeline: [event('2026.06—07')],
    })

    expect(text).toBe('P-01 · 2026.06–2026.07')
  })

  it('collapses a single-month span to one label', () => {
    const text = heroMarginalia({
      projects: [project('P-01')],
      timeline: [event('2026.05')],
    })

    expect(text).toBe('P-01 · 2026.05')
  })

  it('falls back to the project code alone when no label parses', () => {
    const text = heroMarginalia({
      projects: [project('P-01')],
      timeline: [event('进行中'), event('')],
    })

    expect(text).toBe('P-01')
  })

  it('returns null when the portfolio has no project', () => {
    expect(
      heroMarginalia({ projects: [], timeline: [event('2026.05')] }),
    ).toBeNull()
  })
})
