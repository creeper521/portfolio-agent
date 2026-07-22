import type { AgentRouteSeed } from './sessionTypes'

const HANDOFF_TTL_MS = 5 * 60_000
const handoffs = new Map<string, { seed: AgentRouteSeed; expiresAt: number }>()

function randomId() {
  return globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
}

export function createAgentHandoff(seed: AgentRouteSeed, now = Date.now()) {
  const id = randomId()
  handoffs.set(id, { seed, expiresAt: now + HANDOFF_TTL_MS })
  return id
}

export function consumeAgentHandoff(id: string, now = Date.now()): AgentRouteSeed | null {
  const entry = handoffs.get(id)
  handoffs.delete(id)
  if (!entry || entry.expiresAt <= now) return null
  return entry.seed
}

export function clearAgentHandoffsForTest() {
  handoffs.clear()
}
