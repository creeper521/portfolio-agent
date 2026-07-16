import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('prototype visual contract', () => {
  const tokens = readFileSync(resolve('src/app/styles/tokens.css'), 'utf8')

  it('uses the prototype content width and palette', () => {
    expect(tokens).toContain('--content-width: 1180px')
    expect(tokens).toContain('--paper: #f4eee4')
    expect(tokens).toContain('--ink: #201c17')
    expect(tokens).toContain('--red: #7a2e2a')
  })

  it('does not define unapproved color families', () => {
    expect(tokens).not.toMatch(/green|teal|cyan|purple/i)
  })
})
