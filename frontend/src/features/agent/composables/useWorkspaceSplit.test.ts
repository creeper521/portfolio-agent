import { beforeEach, describe, expect, it } from 'vitest'

import {
  WORKSPACE_SPLIT_KEY,
  clampWorkspaceSplit,
  useWorkspaceSplit,
} from './useWorkspaceSplit'

describe('useWorkspaceSplit', () => {
  beforeEach(() => localStorage.clear())

  it('clamps sessions and evidence widths to the approved framed-shell bounds', () => {
    expect(clampWorkspaceSplit({ sessions: 100, evidence: 900 })).toEqual({
      sessions: 220,
      evidence: 420,
    })
  })

  it('persists keyboard adjustments and resets to the approved fixed defaults', () => {
    const split = useWorkspaceSplit()

    split.adjust('sessions', 16)
    const persisted = JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? '{}')
    expect(persisted.sessions).toBe(split.state.value.sessions)

    split.reset()
    expect(split.state.value).toEqual({ sessions: 250, evidence: 340 })
  })

  it('clamps legacy persisted widths without changing the storage key', () => {
    localStorage.setItem(
      WORKSPACE_SPLIT_KEY,
      JSON.stringify({ sessions: 380, evidence: 760 }),
    )

    expect(useWorkspaceSplit().state.value).toEqual({
      sessions: 320,
      evidence: 420,
    })
  })
})
