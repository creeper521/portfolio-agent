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

  it('defines the warm-black floating Agent shell', () => {
    expect(tokens).toContain('--agent-stage: #1e1b17')
    expect(tokens).toContain('--agent-shell-paper: #f3eadb')
    expect(tokens).toContain('--agent-rail-paper: #f0e9de')
    expect(tokens).toContain('--agent-thread-paper: #f5e8d1')
    expect(tokens).toContain('--agent-evidence-paper: #f8f3ea')
    expect(tokens).toContain('--agent-radius-shell: 20px')
    expect(tokens).toContain('--agent-radius-md: 12px')
    expect(tokens).toContain('--agent-radius-sm: 8px')
    expect(tokens).toContain('--agent-hairline: rgba(104, 84, 60, 0.22)')
    expect(base).toContain('border-radius: var(--agent-radius-shell)')
    expect(base).toContain('background: var(--agent-stage)')
  })

  it('keeps structural panes square inside the rounded shell', () => {
    expect(workspace).toContain('--workspace-rail-bg: var(--agent-rail-paper)')
    expect(workspace).toContain('--workspace-thread-bg: var(--agent-thread-paper)')
    expect(workspace).toContain('--workspace-evidence-bg: var(--agent-evidence-paper)')
    expect(workspace).toContain('--workspace-rule: var(--agent-hairline)')
    expect(workspace).not.toMatch(/\.agent-workspace\s*\{[^}]*border-radius:/s)
  })

  it('defines the approved balanced-paper Agent hierarchy', () => {
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
    expect(sessions).toContain('padding: 18px 0')
    expect(sessions).toMatch(/article\.menu-open\s*\{[^}]*min-height:/s)
    expect(sessions).not.toMatch(/\.session-menu\s*\{[^}]*position:\s*absolute/s)
    expect(evidence).toContain('padding: 28px 20px')
    expect(agentPage).toContain('min-height: 100%')
  })

  it('uses one evidence-drawer breakpoint across behavior and presentation', () => {
    const evidenceBreakpoint = '@media (max-width: 1279.98px)'
    const sessionsBreakpoint = '@media (max-width: 959.98px)'

    expect(workspace).toContain("useMediaQuery('(max-width: 959.98px)')")
    expect(workspace).toContain(sessionsBreakpoint)
    expect(conversation).toContain(sessionsBreakpoint)
    expect(paneResizer).toContain(sessionsBreakpoint)
    expect(base).toContain(sessionsBreakpoint)
    expect(base).not.toContain('@media (max-width: 980px)')
    expect(conversation).not.toContain('@media (max-width: 980px)')
    expect(paneResizer).not.toContain('@media (max-width: 980px)')
    expect(workspace).toContain(evidenceBreakpoint)
    expect(conversation).toContain(evidenceBreakpoint)
    expect(paneResizer).toContain(evidenceBreakpoint)
    expect(workspace).not.toContain('@media (max-width: 1279px)')
    expect(conversation).not.toContain('@media (max-width: 1279px)')
    expect(paneResizer).not.toContain('@media (max-width: 1220px)')
  })

  it('removes Agent motion and smooth scrolling when reduced motion is requested', () => {
    expect(workspace).toContain('@media (prefers-reduced-motion: reduce)')
    for (const selector of [
      '.thread-empty',
      '.message',
      '.evidence-card',
      '.citation-card',
      '.source-card',
    ]) {
      expect(workspace).toContain(`:deep(${selector})`)
    }
    expect(workspace).toContain('scroll-behavior: auto')
    expect(workspace).toContain('transition: none')
    expect(workspace).toContain('animation: none')
    expect(conversation).toContain(
      "window.matchMedia?.('(prefers-reduced-motion: reduce)').matches",
    )
  })

  it('keeps the compact shell and workspace navigation aligned to its header', () => {
    expect(base).toMatch(
      /@media \(max-width: 1279\.98px\) and \(min-width: 960px\)\s*\{[^}]*\.site-frame--workspace\s*\{[^}]*border-radius: 12px;/s,
    )
    expect(base).toMatch(
      /\.site-frame--workspace \.primary-nav\s*\{[^}]*inset: 70px 0 auto;/s,
    )
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
