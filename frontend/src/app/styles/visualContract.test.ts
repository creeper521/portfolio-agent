import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('prototype visual contract', () => {
  const tokens = readFileSync(resolve('src/app/styles/tokens.css'), 'utf8')
  const base = readFileSync(resolve('src/app/styles/base.css'), 'utf8')
  const header = readFileSync(
    resolve('src/shared/components/DossierHeader.vue'),
    'utf8',
  )
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
  const paneResizer = readFileSync(
    resolve('src/features/agent/components/PaneResizer.vue'),
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

  it('defines the approved responsive framed Agent shell', () => {
    expect(tokens).toContain('--agent-stage: #2a2620')
    expect(tokens).toContain('--agent-header: #efe7d8')
    expect(tokens).toContain('--agent-shell-max: 1600px')
    expect(tokens).toContain('--agent-shell-radius: 16px')
    expect(base).toContain('.site-frame--workspace')
    expect(base).toContain('border-radius: var(--agent-shell-radius)')
    expect(base).toContain('box-shadow: var(--agent-shell-shadow)')
    expect(base).toContain("[data-header-theme='warm']")
    expect(header).toContain("route.meta.workspace === true ? 'warm' : 'paper'")
  })

  it('defines the approved balanced-paper Agent hierarchy', () => {
    expect(workspace).toContain('--workspace-rail-bg:')
    expect(workspace).toContain('--workspace-thread-bg: var(--warm)')
    expect(workspace).toContain('--workspace-evidence-bg: var(--paper)')
    expect(workspace).toContain('--workspace-primary-bg: var(--ink)')
    expect(sessions).toContain('background: var(--workspace-rail-bg, var(--paper))')
    expect(conversation).toContain('background: var(--workspace-thread-bg, var(--paper-hi))')
    expect(evidence).toContain('background: var(--workspace-evidence-bg, var(--paper))')
    expect(agentPage).toContain('background: var(--paper-hi)')
    expect(workspace).toContain('height: 100%')
    expect(workspace).toContain('minmax(640px, 1fr)')
    expect(workspace).toContain('@media (max-width: 1279.98px)')
    expect(conversation).toContain('right: 28px')
    expect(conversation).toContain('left: 28px')
    expect(sessions).toContain('padding: 25px 20px')
    expect(evidence).toContain('padding: 28px 20px')
    expect(agentPage).toContain('min-height: 100%')
  })

  it('uses one evidence-drawer breakpoint across behavior and presentation', () => {
    const evidenceBreakpoint = '@media (max-width: 1279.98px)'

    expect(workspace).toContain("useMediaQuery('(max-width: 1279.98px)')")
    expect(workspace).toContain(evidenceBreakpoint)
    expect(conversation).toContain(evidenceBreakpoint)
    expect(paneResizer).toContain(evidenceBreakpoint)
    expect(workspace).not.toContain('@media (max-width: 1279px)')
    expect(conversation).not.toContain('@media (max-width: 1279px)')
    expect(paneResizer).not.toContain('@media (max-width: 1220px)')
  })

  it('keeps Agent loading and error feedback relative to the workspace shell', () => {
    expect(agentPage).toContain('class="agent-route-feedback"')
    expect(agentPage).toMatch(
      /\.agent-route-feedback\s*\{[^}]*min-height: 100%;/s,
    )
    expect(agentPage).not.toContain(':deep(.public-content-feedback)')
  })

  it('sizes the conversation from its workspace shell instead of the viewport', () => {
    expect(conversation).toMatch(
      /\.conversation\s*\{[^}]*height: 100%;/s,
    )
    expect(conversation).not.toContain(
      'height: calc(100vh - var(--header-height))',
    )
  })

  it('removes the dark conversation stage and keeps only the two primary controls solid', () => {
    expect(conversation).not.toContain('background: #29241f')
    expect(conversation).not.toContain('background: var(--ink)')
    expect(conversation).toMatch(
      /\.composer button\s*\{[^}]*background: var\(--workspace-action-bg, var\(--red\)\)/s,
    )
    expect(sessions).toMatch(
      /\.session-rail__new\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s,
    )
    expect(workspace).toContain('--workspace-action-bg: var(--red)')
    expect(workspace).toContain('--workspace-action-bg-hover: #662522')
  })
})
