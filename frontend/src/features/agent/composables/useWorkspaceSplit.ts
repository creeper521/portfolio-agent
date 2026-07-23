import { ref } from 'vue'

export const WORKSPACE_SPLIT_KEY = 'portfolio.workspace.split.v1'
export const WORKSPACE_LIMITS = {
  sessions: [250, 280] as const,
  evidence: [330, 380] as const,
  chatMin: 640,
}

export interface WorkspaceSplit {
  sessions: number
  evidence: number
}

function clamp(value: number, [min, max]: readonly [number, number]) {
  return Math.min(max, Math.max(min, Math.round(value)))
}

export function workspaceDefaults(): WorkspaceSplit {
  return {
    sessions: 260,
    evidence: 350,
  }
}

export function clampWorkspaceSplit(next: WorkspaceSplit): WorkspaceSplit {
  return {
    sessions: clamp(next.sessions, WORKSPACE_LIMITS.sessions),
    evidence: clamp(next.evidence, WORKSPACE_LIMITS.evidence),
  }
}

export function fitWorkspaceSplit(
  preferred: WorkspaceSplit,
  availableWidth: number,
): WorkspaceSplit {
  const clamped = clampWorkspaceSplit(preferred)
  if (!Number.isFinite(availableWidth)) return clamped

  const minimumSideWidth =
    WORKSPACE_LIMITS.sessions[0] + WORKSPACE_LIMITS.evidence[0]
  const availableSideWidth = Math.max(
    minimumSideWidth,
    Math.floor(availableWidth) - WORKSPACE_LIMITS.chatMin,
  )
  if (clamped.sessions + clamped.evidence <= availableSideWidth) return clamped

  const sessionsSlack = clamped.sessions - WORKSPACE_LIMITS.sessions[0]
  const evidenceSlack = clamped.evidence - WORKSPACE_LIMITS.evidence[0]
  const preferredSlack = sessionsSlack + evidenceSlack
  if (preferredSlack <= 0) return clamped

  const availableSlack = availableSideWidth - minimumSideWidth
  const sessions =
    WORKSPACE_LIMITS.sessions[0] +
    Math.floor((availableSlack * sessionsSlack) / preferredSlack)

  return {
    sessions,
    evidence: availableSideWidth - sessions,
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
