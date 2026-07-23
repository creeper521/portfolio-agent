import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('prototype visual contract', () => {
  const tokens = readFileSync(resolve('src/app/styles/tokens.css'), 'utf8')
  const workspace = readFileSync(
    resolve('src/features/agent/components/AgentWorkspace.vue'),
    'utf8',
  )
  const sessions = readFileSync(
    resolve('src/features/agent/components/LocalSessionRail.vue'),
    'utf8',
  )
  const conversation = readFileSync(
    resolve('src/features/agent/components/ConversationThread.vue'),
    'utf8',
  )
  const evidence = readFileSync(
    resolve('src/features/agent/components/EvidenceDesk.vue'),
    'utf8',
  )
  const agentPage = readFileSync(resolve('src/pages/AgentPage.vue'), 'utf8')

  it('uses the prototype content width and palette', () => {
    expect(tokens).toContain('--content-width: 1180px')
    expect(tokens).toContain('--paper: #f4eee4')
    expect(tokens).toContain('--ink: #201c17')
    expect(tokens).toContain('--red: #7a2e2a')
  })

  it('does not define unapproved color families', () => {
    expect(tokens).not.toMatch(/green|teal|cyan|purple/i)
  })

  it('defines the approved balanced-paper Agent hierarchy', () => {
    expect(workspace).toContain('--workspace-rail-bg:')
    expect(workspace).toContain('--workspace-thread-bg: var(--paper-hi)')
    expect(workspace).toContain('--workspace-evidence-bg: var(--paper)')
    expect(workspace).toContain('--workspace-primary-bg: var(--ink)')
    expect(sessions).toContain('background: var(--workspace-rail-bg, var(--paper))')
    expect(conversation).toContain('background: var(--workspace-thread-bg, var(--paper-hi))')
    expect(evidence).toContain('background: var(--workspace-evidence-bg, var(--paper))')
    expect(agentPage).toContain('background: var(--paper-hi)')
  })

  it('removes the dark conversation stage and keeps only the two primary controls solid', () => {
    expect(conversation).not.toContain('background: #29241f')
    expect(conversation).not.toContain('background: var(--ink)')
    expect(conversation).toMatch(
      /\.composer button\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s,
    )
    expect(sessions).toMatch(
      /\.session-rail__new\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s,
    )
  })
})
