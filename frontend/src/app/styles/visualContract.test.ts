import { existsSync, readFileSync, statSync } from 'node:fs'
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
  const audience = readFileSync(
    resolve('src/features/audience/components/AudienceDialogue.vue'),
    'utf8',
  )
  const lightAnswer = readFileSync(
    resolve('src/features/audience/components/LightAnswerPanel.vue'),
    'utf8',
  )
  const projectPage = readFileSync(resolve('src/pages/ProjectPage.vue'), 'utf8')
  const projectsPage = readFileSync(resolve('src/pages/ProjectsPage.vue'), 'utf8')
  const evidencePage = readFileSync(resolve('src/pages/EvidencePage.vue'), 'utf8')
  const timelinePage = readFileSync(resolve('src/pages/TimelinePage.vue'), 'utf8')
  const pageLead = readFileSync(resolve('src/shared/components/PageLead.vue'), 'utf8')
  const dossierFooter = readFileSync(
    resolve('src/shared/components/DossierFooter.vue'),
    'utf8',
  )
  const notFound = readFileSync(resolve('src/pages/NotFoundPage.vue'), 'utf8')
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
    expect(conversation).toContain('margin: 0 28px 24px')
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
      '.thread-empty button',
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
    expect(workspace).toMatch(
      /:deep\(\.evidence-desk\),\s*:deep\(\.session-rail\),\s*\.workspace-scrim\s*\{[^}]*transition: none;/s,
    )
    expect(conversation).toContain(
      "window.matchMedia?.('(prefers-reduced-motion: reduce)').matches",
    )
  })

  it('keeps the compact shell and workspace navigation aligned to its header', () => {
    expect(base).toMatch(
      /@media \(max-width: 1279\.98px\) and \(min-width: 960px\)\s*\{[^}]*\.site-frame--workspace\s*\{[^}]*border-radius: 12px;/s,
    )
    expect(base).toMatch(
      /\.site-frame--workspace \.primary-nav\s*\{[^}]*inset: var\(--header-height-workspace\) 0 auto;/s,
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
    expect(workspace).toContain('--workspace-accent: var(--agent-accent)')
    expect(workspace).toContain('--workspace-action-bg: var(--agent-accent)')
    expect(workspace).not.toContain('--workspace-accent: var(--red)')
    expect(workspace).not.toContain('--workspace-action-bg: var(--red)')
  })

  it('allows user messages to use the mobile reading width', () => {
    expect(conversation).toMatch(
      /@media \(max-width: 620px\)\s*\{[\s\S]*?\.message--user\s*\{[^}]*max-width: 85%;/s,
    )
  })

  it('keeps the composer in the grid flow and the thread focus-visible', () => {
    expect(conversation).toMatch(
      /\.conversation\s*\{[^}]*grid-template-rows: auto minmax\(0, 1fr\) auto;/s,
    )
    expect(conversation).not.toContain('padding-bottom: 104px')
    expect(conversation).toMatch(/\.composer\s*\{[^}]*margin: 0 28px 24px;/s)
    expect(conversation).not.toMatch(/\.composer\s*\{[^}]*position: absolute;/s)
    expect(conversation).toContain('.conversation__body')
    expect(conversation).toMatch(/\.composer:focus-within\s*\{[^}]*border-color:/s)
    expect(conversation).not.toMatch(/textarea\s*\{[^}]*outline: 0/s)
    expect(audience).toMatch(/\.question-form:focus-within\s*\{[^}]*border-color:/s)
    expect(audience).not.toMatch(/\.question-form input\s*\{[^}]*outline: 0/s)
  })

  it('keeps touch controls visible and inputs zoom-safe on coarse pointers', () => {
    expect(sessions).toMatch(
      /@media \(hover: none\)\s*\{[\s\S]*?\.session-menu-trigger\s*\{[^}]*opacity: 1;/s,
    )
    expect(sessions).toMatch(
      /@media \(hover: none\)\s*\{[\s\S]*?input\s*\{[^}]*font-size: 16px;/s,
    )
    expect(conversation).toMatch(
      /@media \(hover: none\)\s*\{[\s\S]*?textarea\s*\{[^}]*font-size: 16px;/s,
    )
    expect(audience).toMatch(
      /@media \(hover: none\)\s*\{[\s\S]*?\.question-form input\s*\{[^}]*font-size: 16px;/s,
    )
  })

  it('keeps single token sources for red-on-ink, ink scale, and workspace header', () => {
    expect(tokens).toContain('--red-on-ink: #d27d74')
    expect(tokens).toContain('--ink-line: #4a433b')
    expect(tokens).toContain('--ink-text: #a99f91')
    expect(tokens).toContain('--ink-text-hi: #e8ddce')
    expect(tokens).toContain('--ink-text-faint: #94897c')
    expect(tokens).toContain('--header-height-workspace: 70px')
    expect(tokens).toContain('--agent-accent: var(--red)')
    expect(tokens).toContain('--agent-header: var(--agent-shell-paper)')
    expect(tokens).not.toContain('--agent-dark-control')
    expect(tokens).not.toContain('--warm:')
    expect(base).not.toMatch(/70px/)

    // 深底浅红只允许 token 一个来源
    expect(lightAnswer).not.toMatch(/#d27d74|#c7776e|#cb756c|#c9675d/i)
    expect(audience).not.toContain('#e1948b')

    // 暗色系列的线、文、弱文只允许 token 一个来源
    expect(lightAnswer).not.toMatch(/#5b5349|#60574d|#94897c|#e8ddce|#c8bcad/i)
    expect(base).not.toMatch(/#4a433b|#94897c|#5b5349/i)
    expect(projectPage).not.toMatch(/#cfc5b7|#a99f91|#4a433b|#d2c8bb|#51493f/i)
    expect(pageLead).not.toMatch(/#4a433b|#a99f91/i)
    expect(notFound).not.toContain('#a99f91')
  })
})

// WP1 整改断言：A1 组说明串色 / A2 深墨块红漏 / B4 三页页脚收尾
describe('dossier pages red discipline and footer closure', () => {
  const projectsPage = readFileSync(resolve('src/pages/ProjectsPage.vue'), 'utf8')
  const projectPage = readFileSync(resolve('src/pages/ProjectPage.vue'), 'utf8')
  const evidencePage = readFileSync(resolve('src/pages/EvidencePage.vue'), 'utf8')

  it('keeps the group note out of the red mono treatment (A1)', () => {
    // 组说明回归 --muted，且 .dossier-group__head 不再用 p 元素选择器统一染红，
    // 改为给组代码一个专属类，避免优先级压制组说明的 --muted 声明。
    expect(projectsPage).toMatch(
      /\.dossier-group__note\s*\{[^}]*color:\s*var\(--muted\)/s,
    )
    // 红色只留给组代码（编号），由专属类承载，不再由 .dossier-group__head p 笼统施加
    expect(projectsPage).toMatch(/\.dossier-group__code\b/)
    expect(projectsPage).not.toMatch(
      /\.dossier-group__head\s+p\s*\{[^}]*color:\s*var\(--red\)/s,
    )
  })

  it('does not resolve section-code / list markers / trace links to --red inside the dark block (A2)', () => {
    // 在 .project-story__dark 作用域内，三处原本用 var(--red) 的选择器
    // 应切换到深底专用浅色红（--red-hi），不再压出 #7a2e2a 的低对比红。
    const darkBlockMatch = projectPage.match(
      /\.project-story__dark\s+\.section-code\s*\{[^}]*\}/s,
    )
    expect(darkBlockMatch, 'expected .project-story__dark .section-code override').not.toBeNull()
    expect(darkBlockMatch![0]).not.toContain('var(--red)')
    expect(darkBlockMatch![0]).toMatch(/--red-hi|--red-on-ink/)

    const darkLiBefore = projectPage.match(
      /\.project-story__dark\s+li::before\s*\{[^}]*\}/s,
    )
    expect(darkLiBefore, 'expected .project-story__dark li::before override').not.toBeNull()
    expect(darkLiBefore![0]).not.toContain('var(--red)')

    const darkTraceLink = projectPage.match(
      /\.project-story__dark\s+\.section-trace\s+a\s*\{[^}]*\}/s,
    )
    expect(darkTraceLink, 'expected .project-story__dark .section-trace a override').not.toBeNull()
    expect(darkTraceLink![0]).not.toContain('var(--red)')
  })

  it('closes the three dossier pages with the shared DossierFooter (B4)', () => {
    expect(projectsPage).toContain('DossierFooter')
    expect(projectsPage).toMatch(/<DossierFooter\b/)
    expect(projectPage).toContain('DossierFooter')
    expect(projectPage).toMatch(/<DossierFooter\b/)
    expect(evidencePage).toContain('DossierFooter')
    expect(evidencePage).toMatch(/<DossierFooter\b/)
  })
})

// WP2 整改断言：B3-dt 结构标签退红 / B4 时间线页脚收尾
describe('timeline ledger red discipline and footer closure', () => {
  const timelinePage = readFileSync(resolve('src/pages/TimelinePage.vue'), 'utf8')

  it('keeps the dt structural labels out of red (B3-dt)', () => {
    // 「问题 / 行动 / 影响」是中性结构标签，红色语义只留给节点与日期。
    // dt 的 color 不再是 var(--red)，回归墨色层级（--muted）。
    const dtRule = timelinePage.match(/^dt\s*\{[^}]*\}/m)
    expect(dtRule, 'expected a top-level dt rule').not.toBeNull()
    expect(dtRule![0]).not.toMatch(/color:\s*var\(--red\)/)
    expect(dtRule![0]).toMatch(/color:\s*var\(--muted\)/)
  })

  it('closes the timeline page with the shared DossierFooter (B4)', () => {
    expect(timelinePage).toContain('DossierFooter')
    expect(timelinePage).toMatch(/<DossierFooter\b/)
  })
})

// WP3 整改断言：A3 用户消息回归文档化样式（透明底 + 2px 左线，去实心气泡/圆角）
describe('agent user message returns to the documented text-flow treatment', () => {
  const conversation = readFileSync(
    resolve('src/features/agent/components/ConversationThread.vue'),
    'utf8',
  )

  it('removes the solid bubble background and rounded corners from user messages', () => {
    // 07-22 设计文档第 116 行：用户问题用自然文本流 + 2px --workspace-accent 左线，不使用实心消息气泡。
    const userBody = conversation.match(
      /\.message--user\s+\.message__body\s*\{[^}]*\}/s,
    )
    expect(userBody, 'expected .message--user .message__body rule').not.toBeNull()
    // 不再有实心背景或圆角气泡
    expect(userBody![0]).not.toMatch(/background:/)
    expect(userBody![0]).not.toMatch(/border-radius:/)
  })

  it('keeps the 2px accent left border on user messages', () => {
    const userBody = conversation.match(
      /\.message--user\s+\.message__body\s*\{[^}]*\}/s,
    )
    expect(userBody, 'expected .message--user .message__body rule').not.toBeNull()
    // border-left: 2px <style> var(--workspace-accent, ...) —— 允许 solid 等样式关键字
    expect(userBody![0]).toMatch(/border-left:\s*2px\s+\w+\s+var\(--workspace-accent/)
  })
})

// WP3 整改断言：A5 隐私提示从右下绝对位移到会话栏静态位（不再压证据区/jump-latest）
describe('agent session privacy note moves out of the evidence overlap zone', () => {
  const workspace = readFileSync(
    resolve('src/features/agent/components/AgentWorkspace.vue'),
    'utf8',
  )
  const sessions = readFileSync(
    resolve('src/features/agent/components/LocalSessionRail.vue'),
    'utf8',
  )

  it('removes the absolutely-positioned privacy note from the workspace overlay', () => {
    // 原 .session-privacy 是 right:18px;bottom:4px 的绝对定位，压在 conversation 右下角，
    // 与 jump-latest / 证据内容重叠。移除该覆盖层。
    expect(workspace).not.toContain('class="session-privacy"')
    expect(workspace).not.toMatch(/\.session-privacy\s*\{[^}]*position:\s*absolute/s)
  })

  it('keeps the full privacy notice in the session rail footer (static, security rule)', () => {
    // AGENTS.md 8.3 安全规则要求页面清晰提示“当前对话未保存，刷新后记录会消失”。
    // 隐私文案随覆盖层移除后，必须在会话栏 footer 静态位继续承载，不能丢失。
    expect(sessions).toContain('刷新')
    expect(sessions).toMatch(/footer p\s*\{/)
  })
})

// WP3 整改断言：B5 空态布局——thread 引导区垂直居中
describe('agent empty-state thread centers vertically', () => {
  const conversation = readFileSync(
    resolve('src/features/agent/components/ConversationThread.vue'),
    'utf8',
  )

  it('centers the thread guide vertically when the conversation is empty (B5)', () => {
    // 空会话时引导区垂直居中，消除中栏约 368px 顶部死空。
    // 通过 [data-conversation-state='empty'] 锁定居中规则，不影响 conversation 状态。
    expect(conversation).toMatch(
      /\[data-conversation-state=['"]empty['"]\][^{]*\{[^}]*margin-block:\s*auto/s,
    )
  })
})

// WP4 整改断言：A4 移动端孤行 + B1 Hero 竖排边注/纹理提升 + 问答台自适应高度
describe('home hero mobile title, folio marginalia and console adaptive height', () => {
  const hero = readFileSync(
    resolve('src/features/home/components/PortfolioHero.vue'),
    'utf8',
  )
  const audience = readFileSync(
    resolve('src/features/audience/components/AudienceDialogue.vue'),
    'utf8',
  )

  it('lets the hero title use the full width on mobile instead of orphan-wrapping (A4)', () => {
    // 桌面保留 10ch 换行节奏；≤620px 放宽 max-width 并收紧字号，
    // 避免“工程实践档案”被 10ch 裁成孤行。
    expect(hero).toContain('max-width: 10ch')
    const mobileH1 = hero.match(
      /@media \(max-width: 620px\)\s*\{[\s\S]*?h1\s*\{[^}]*\}/s,
    )
    expect(mobileH1, 'expected an h1 rule inside the ≤620px media block').not.toBeNull()
    expect(mobileH1![0]).toMatch(/max-width:\s*none/)
    expect(mobileH1![0]).toContain('font-size: clamp(44px, 13.5vw, 56px)')
  })

  it('renders the hero folio marginalia as vertical text (B1)', () => {
    expect(hero).toContain('data-hero-folio')
    expect(hero).toMatch(
      /\.portfolio-hero__folio\s*\{[^}]*writing-mode:\s*vertical-rl/s,
    )
  })

  it('raises the paper texture opacity on the hero surface (B1)', () => {
    expect(hero).toContain('opacity: 0.65')
  })

  it('lets the audience console size to its content instead of a fixed 520px floor (B1)', () => {
    expect(audience).not.toContain('min-height: 520px')
  })
})

// WP5 整改断言：导航 Agent 入口不再常驻红条——红条只作当前页指示器
describe('primary nav agent entry drops its permanent red bar', () => {
  const base = readFileSync(resolve('src/app/styles/base.css'), 'utf8')

  it('keeps the red bar only on the exact-active current-page indicator', () => {
    // .primary-nav__agent 是常驻入口（红字 + ↗），并非当前页；
    // 红条若常驻它身上，任意页面都会与真实当前页形成“双红条”。
    // 在 /agent 页时它经 .router-link-exact-active 依旧有红条，语义不受损。
    expect(base).not.toMatch(/\.primary-nav__agent::after/)
    expect(base).toMatch(
      /\.primary-nav \.router-link-exact-active::after\s*\{[^}]*background:\s*var\(--red\)/s,
    )
  })

  it('keeps the agent entry visually distinct via red text', () => {
    expect(base).toMatch(
      /\.primary-nav__agent\s*\{[^}]*color:\s*var\(--red\)/s,
    )
  })
})

// WP6 整改断言：中文衬线自托管子集（同源 /fonts/，无第三方请求，swap 不阻塞首屏）
describe('self-hosted CJK serif subset', () => {
  const tokensCss = readFileSync(resolve('src/app/styles/tokens.css'), 'utf8')
  const indexHtml = readFileSync(resolve('index.html'), 'utf8')

  it('declares a same-origin @font-face for Noto Serif SC with font-display swap', () => {
    const face = tokensCss.match(/@font-face\s*\{[^}]*\}/s)
    expect(face, 'expected an @font-face block in tokens.css').not.toBeNull()
    expect(face![0]).toContain("font-family: 'Noto Serif SC'")
    expect(face![0]).toMatch(/font-display:\s*swap/)
    expect(face![0]).toContain('/fonts/noto-serif-sc-400-700.woff2')
    expect(face![0]).toMatch(/font-weight:\s*400 700/)
  })

  it('keeps Georgia leading the serif stack so Latin keeps the established display face', () => {
    // 拉丁/数字维持 Georgia；CJK 命中自托管 'Noto Serif SC' 子集；
    // 栈内其余本机字体仍是可选增强，不构成依赖。
    expect(tokensCss).toContain(
      "--serif: Georgia, 'Noto Serif SC', 'Songti SC', serif;",
    )
  })

  it('preloads the subset woff2 from the same origin', () => {
    expect(indexHtml).toContain('rel="preload"')
    expect(indexHtml).toContain('/fonts/noto-serif-sc-400-700.woff2')
    expect(indexHtml).toContain('as="font"')
  })

  it('ships the subset woff2 and its OFL license inside the size budget', () => {
    const fontPath = resolve('public/fonts/noto-serif-sc-400-700.woff2')
    expect(existsSync(fontPath)).toBe(true)
    // 预算 ≤1.3MB：可变字重单文件（实测 1.24MB）优于两个静态字重
    // （400/700 各约 0.68MB，合计 1.35MB 且 500/600 需降级匹配）。
    expect(statSync(fontPath).size).toBeLessThanOrEqual(1.3 * 1024 * 1024)
    expect(existsSync(resolve('public/fonts/OFL-noto-serif-sc.txt'))).toBe(true)
  })
})
