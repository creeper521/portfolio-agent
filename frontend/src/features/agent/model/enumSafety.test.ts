import { describe, expect, it } from 'vitest'

import { knownEnum, safeEnum } from './enumSafety'

const FRUITS = new Set(['APPLE', 'BANANA'] as const)

describe('safeEnum', () => {
  it('returns the value when it is in the known set', () => {
    expect(safeEnum('APPLE', FRUITS, 'APPLE')).toBe('APPLE')
    expect(safeEnum('BANANA', FRUITS, 'APPLE')).toBe('BANANA')
  })

  it('returns the fallback for an unknown string', () => {
    expect(safeEnum('CHERRY', FRUITS, 'APPLE')).toBe('APPLE')
  })

  it('returns the fallback for non-string values', () => {
    expect(safeEnum(undefined, FRUITS, 'APPLE')).toBe('APPLE')
    expect(safeEnum(null, FRUITS, 'APPLE')).toBe('APPLE')
    expect(safeEnum(7, FRUITS, 'APPLE')).toBe('APPLE')
    expect(safeEnum({ code: 'APPLE' }, FRUITS, 'APPLE')).toBe('APPLE')
  })
})

describe('knownEnum', () => {
  it('returns the value when it is in the known set', () => {
    expect(knownEnum('BANANA', FRUITS)).toBe('BANANA')
  })

  it('returns undefined for unknown or non-string values', () => {
    expect(knownEnum('CHERRY', FRUITS)).toBeUndefined()
    expect(knownEnum(undefined, FRUITS)).toBeUndefined()
    expect(knownEnum(7, FRUITS)).toBeUndefined()
  })
})
