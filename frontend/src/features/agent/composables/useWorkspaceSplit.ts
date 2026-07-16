import { ref } from 'vue'

export const WORKSPACE_SPLIT_KEY = 'portfolio.workspace.split.v1'
export const WORKSPACE_LIMITS = {
  sessions: [220, 380] as const,
  evidence: [380, 760] as const,
  chatMin: 600,
}

export interface WorkspaceSplit {
  sessions: number
  evidence: number
}

function clamp(value: number, [min, max]: readonly [number, number]) {
  return Math.min(max, Math.max(min, Math.round(value)))
}

export function workspaceDefaults(viewport = globalThis.innerWidth || 1440): WorkspaceSplit {
  return {
    sessions: clamp(viewport * 0.15, [240, 320]),
    evidence: clamp(viewport * 0.31, [420, 720]),
  }
}

export function clampWorkspaceSplit(next: WorkspaceSplit): WorkspaceSplit {
  return {
    sessions: clamp(next.sessions, WORKSPACE_LIMITS.sessions),
    evidence: clamp(next.evidence, WORKSPACE_LIMITS.evidence),
  }
}

function readWorkspaceSplit(): WorkspaceSplit | null {
  try {
    const parsed = JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? 'null')
    if (
      parsed &&
      typeof parsed.sessions === 'number' &&
      typeof parsed.evidence === 'number'
    ) {
      return clampWorkspaceSplit(parsed)
    }
  } catch {
    return null
  }
  return null
}

export function useWorkspaceSplit() {
  const state = ref(readWorkspaceSplit() ?? workspaceDefaults())

  function persist() {
    localStorage.setItem(WORKSPACE_SPLIT_KEY, JSON.stringify(state.value))
  }

  function set(
    key: keyof WorkspaceSplit,
    value: number,
    persistChange = false,
  ) {
    state.value = clampWorkspaceSplit({ ...state.value, [key]: value })
    if (persistChange) persist()
  }

  function adjust(key: keyof WorkspaceSplit, delta: number) {
    set(key, state.value[key] + delta, true)
  }

  function reset() {
    state.value = workspaceDefaults()
    persist()
  }

  return { state, set, adjust, reset, persist }
}
