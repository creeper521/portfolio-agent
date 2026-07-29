import type { AgentRouteSeed } from './sessionTypes'

const HANDOFF_TTL_MS = 5 * 60_000
const handoffs = new Map<string, { seed: AgentRouteSeed; expiresAt: number }>()
const caseHandoffs = new Map<string, { seed: CaseAgentHandoff; expiresAt: number }>()

export interface CaseAgentHandoff {
  caseSlug: string
  question: string
}

function randomId() {
  return globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
}

function removeExpired<T>(store: Map<string, { seed: T; expiresAt: number }>, now: number) {
  for (const [id, entry] of store) {
    if (entry.expiresAt <= now) store.delete(id)
  }
}

export function createAgentHandoff(seed: AgentRouteSeed, now = Date.now()) {
  removeExpired(handoffs, now)
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

export function createCaseAgentHandoff(seed: CaseAgentHandoff, now = Date.now()) {
  removeExpired(caseHandoffs, now)
  const id = randomId()
  caseHandoffs.set(id, { seed, expiresAt: now + HANDOFF_TTL_MS })
  return id
}

export function consumeCaseAgentHandoff(
  id: string,
  now = Date.now(),
): CaseAgentHandoff | null {
  const entry = caseHandoffs.get(id)
  caseHandoffs.delete(id)
  if (!entry || entry.expiresAt <= now) return null
  return entry.seed
}

export function clearAgentHandoffsForTest() {
  handoffs.clear()
  caseHandoffs.clear()
}
