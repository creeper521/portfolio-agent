export type RecentResultSourceKind = 'RECOMMENDATION' | 'COMPARISON'

export interface RecentResultSetItem {
  position: number
  subjectType: 'PROJECT' | 'CASE' | 'RESULT'
  subjectId: string
}

/** Opaque signed envelope. It is deliberately retained only by an in-memory store. */
export interface RecentResultSetEnvelope {
  resultSetId: string
  sourceKind: RecentResultSourceKind
  issuedAt: string
  contentVersion: string
  expiresAt: string
  supersedesResultSetId?: string
  items: RecentResultSetItem[]
  integrityToken: string
}

export interface RecentResultSetStore {
  snapshot(): RecentResultSetEnvelope[]
  replace(envelopes: RecentResultSetEnvelope[]): void
  clear(): void
}

const MAX_SETS = 3
const MAX_ITEMS = 10

export function createRecentResultSetMemoryStore(): RecentResultSetStore {
  let values: RecentResultSetEnvelope[] = []

  function replace(envelopes: RecentResultSetEnvelope[]) {
    const copied = envelopes.map(cloneEnvelope)
    const ids = new Set(copied.map((value) => value.resultSetId))
    const superseded = new Set(
      copied
        .map((value) => value.supersedesResultSetId)
        .filter((value): value is string => value !== undefined && ids.has(value)),
    )
    const ordered = copied
      .filter((value) => !superseded.has(value.resultSetId))
      .sort((left, right) => {
        const byIssuedAt = Date.parse(right.issuedAt) - Date.parse(left.issuedAt)
        return byIssuedAt === 0 ? left.resultSetId.localeCompare(right.resultSetId) : byIssuedAt
      })
    const retained: RecentResultSetEnvelope[] = []
    let itemCount = 0
    for (const value of ordered) {
      if (retained.length >= MAX_SETS || itemCount + value.items.length > MAX_ITEMS) continue
      retained.push(value)
      itemCount += value.items.length
    }
    values = retained
  }

  return {
    snapshot: () => values.map(cloneEnvelope),
    replace,
    clear: () => { values = [] },
  }
}

function cloneEnvelope(value: RecentResultSetEnvelope): RecentResultSetEnvelope {
  return { ...value, items: value.items.map((item) => ({ ...item })) }
}
