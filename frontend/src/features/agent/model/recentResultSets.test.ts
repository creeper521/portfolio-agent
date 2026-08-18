import { describe, expect, it } from 'vitest'
import {
  createRecentResultSetMemoryStore,
  type RecentResultSetEnvelope,
} from './recentResultSets'

function resultSet(
  id: string,
  issuedAt: string,
  itemCount = 1,
  supersedesResultSetId?: string,
): RecentResultSetEnvelope {
  return {
    resultSetId: id,
    sourceKind: 'RECOMMENDATION',
    issuedAt,
    contentVersion: 'public-v1',
    expiresAt: '2026-08-17T07:00:00Z',
    ...(supersedesResultSetId === undefined ? {} : { supersedesResultSetId }),
    items: Array.from({ length: itemCount }, (_, index) => ({
      position: index + 1,
      subjectType: 'PROJECT',
      subjectId: `${id}-${index + 1}`,
    })),
    integrityToken: `opaque-${id}`,
  }
}

describe('recent result set memory store', () => {
  it('keeps only the newest three sets and ten total items without mutating tokens', () => {
    const store = createRecentResultSetMemoryStore()
    store.replace([
      resultSet('old', '2026-08-17T06:00:00Z', 2),
      resultSet('newest', '2026-08-17T06:00:03Z', 4),
      resultSet('middle', '2026-08-17T06:00:02Z', 4),
      resultSet('new', '2026-08-17T06:00:01Z', 2),
    ])

    expect(store.snapshot().map((value) => value.resultSetId))
      .toEqual(['newest', 'middle', 'new'])
    expect(store.snapshot()[0]?.integrityToken).toBe('opaque-newest')
  })

  it('atomically drops a superseded set and never exposes mutable internal values', () => {
    const store = createRecentResultSetMemoryStore()
    store.replace([
      resultSet('old', '2026-08-17T06:00:00Z'),
      resultSet('new', '2026-08-17T06:00:01Z', 1, 'old'),
    ])
    const snapshot = store.snapshot()
    snapshot[0]!.items[0]!.subjectId = 'tampered-locally'

    expect(store.snapshot().map((value) => value.resultSetId)).toEqual(['new'])
    expect(store.snapshot()[0]?.items[0]?.subjectId).toBe('new-1')
    store.clear()
    expect(store.snapshot()).toEqual([])
  })
})
