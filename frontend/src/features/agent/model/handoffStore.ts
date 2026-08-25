import type { AgentRouteSeed } from './sessionTypes'

// 首页/项目页 → Agent 页的一次性内存交接存储（领域模型层）。
// 交接种子只存活于当前页面内存：5 分钟过期、读取即删除（一次性消费），
// 不进入 URL、浏览器历史或任何持久化存储。
const HANDOFF_TTL_MS = 5 * 60_000
const handoffs = new Map<string, { seed: AgentRouteSeed; expiresAt: number }>()
const caseHandoffs = new Map<string, { seed: CaseAgentHandoff; expiresAt: number }>()

/** 案例页 → Agent 页的轻量交接：只带案例 slug 与预填问题。 */
export interface CaseAgentHandoff {
  caseSlug: string
  question: string
}

function randomId() {
  return globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
}

/** 顺手清理已过期条目，避免长驻页面不断累积死数据。 */
function removeExpired<T>(store: Map<string, { seed: T; expiresAt: number }>, now: number) {
  for (const [id, entry] of store) {
    if (entry.expiresAt <= now) store.delete(id)
  }
}

/** 登记一次 Agent 交接种子并返回随机一次性 id；id 只在当前页面内存中有效。 */
export function createAgentHandoff(seed: AgentRouteSeed, now = Date.now()) {
  removeExpired(handoffs, now)
  const id = randomId()
  handoffs.set(id, { seed, expiresAt: now + HANDOFF_TTL_MS })
  return id
}

/** 一次性消费交接：读取即删除；id 不存在或已过期返回 null。 */
export function consumeAgentHandoff(id: string, now = Date.now()): AgentRouteSeed | null {
  const entry = handoffs.get(id)
  handoffs.delete(id)
  if (!entry || entry.expiresAt <= now) return null
  return entry.seed
}

/** 登记一次案例交接种子并返回随机一次性 id。 */
export function createCaseAgentHandoff(seed: CaseAgentHandoff, now = Date.now()) {
  removeExpired(caseHandoffs, now)
  const id = randomId()
  caseHandoffs.set(id, { seed, expiresAt: now + HANDOFF_TTL_MS })
  return id
}

/** 一次性消费案例交接；id 不存在或已过期返回 null。 */
export function consumeCaseAgentHandoff(
  id: string,
  now = Date.now(),
): CaseAgentHandoff | null {
  const entry = caseHandoffs.get(id)
  caseHandoffs.delete(id)
  if (!entry || entry.expiresAt <= now) return null
  return entry.seed
}

/** 仅供测试使用：清空全部交接存储。 */
export function clearAgentHandoffsForTest() {
  handoffs.clear()
  caseHandoffs.clear()
}
