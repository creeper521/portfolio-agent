import { beforeEach, describe, expect, it } from 'vitest'

import {
  WORKSPACE_SPLIT_KEY,
  clampWorkspaceSplit,
  useWorkspaceSplit,
} from './useWorkspaceSplit'

describe('useWorkspaceSplit', () => {
  beforeEach(() => localStorage.clear())

  it('clamps sessions and evidence widths to the approved bounds', () => {
    expect(clampWorkspaceSplit({ sessions: 100, evidence: 900 })).toEqual({
      sessions: 220,
      evidence: 760,
    })
  })

  it('persists keyboard adjustments and resets to responsive defaults', () => {
    const split = useWorkspaceSplit()

    split.adjust('sessions', 16)
    const persisted = JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? '{}')
    expect(persisted.sessions).toBe(split.state.value.sessions)

    split.reset()
    expect(split.state.value.sessions).toBeGreaterThanOrEqual(240)
    expect(split.state.value.evidence).toBeGreaterThanOrEqual(420)
  })
})
