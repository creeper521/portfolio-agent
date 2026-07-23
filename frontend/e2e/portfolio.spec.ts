import { expect, test, type Page } from '@playwright/test'

import { installPublicApiMocks } from './support/publicApiMocks'
import { previewPublicContent } from '../src/features/public-content/data/previewPublicContent'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const initializedKey = 'portfolio.playwright.initialized'
    if (sessionStorage.getItem(initializedKey) !== '1') {
      localStorage.clear()
      sessionStorage.setItem(initializedKey, '1')
    }
  })
  if (process.env.PLAYWRIGHT_REAL_API !== '1') {
    await installPublicApiMocks(page)
  }
})

async function gotoWithPublicContent(page: Page, path: string) {
  const publicContentResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/public-content' &&
      response.request().method() === 'GET',
  )
  await page.goto(path)
  expect((await publicContentResponse).ok()).toBe(true)
}

async function openAgentDeepLink(page: Page) {
  await page.goto('/agent')
  await expect(page).toHaveURL(/\/agent$/)
}

test('home preserves the four-layer experience and hands a role question to Agent', async ({
  page,
}) => {
  await gotoWithPublicContent(page, '/')

  await expect(page.locator('[data-home-layer]')).toHaveCount(4)
  await expect(page.locator('[data-home-layer="hero"]')).toHaveCSS(
    'background-color',
    'rgb(244, 238, 228)',
  )
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Java 后端开发实习生')
  await expect(page.getByText('[姓名]')).toHaveCount(0)
  await expect(page.locator('[data-credibility-metric]')).toHaveCount(3)
  await expect(page.locator('[data-explore-entry]')).toHaveCount(4)

  await page.locator('[data-role="MENTOR"]').click()
  await expect(page.locator('[data-role="MENTOR"]')).toHaveCSS(
    'background-color',
    'rgb(32, 28, 23)',
  )
  const supportedQuestion = page.locator('[data-question]').first()
  await expect(supportedQuestion).toBeVisible()
  const answerResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/answers' &&
      response.request().method() === 'POST',
  )
  const questionText = (await supportedQuestion.locator('span').textContent()) ?? ''
  await supportedQuestion.click()
  expect((await answerResponse).ok()).toBe(true)
  await expect(page.locator('[data-light-answer]')).toBeVisible()
  await expect(page.locator('[data-light-answer]')).toContainText('PRESET')
  await expect(page.locator('[data-light-answer]')).toContainText('DETERMINISTIC')
  await expect(page.locator('[data-light-answer]')).toContainText('VERIFIED')
  await expect(page.locator('[data-answer-action]')).toHaveCount(3)
  await page.getByRole('link', { name: /带着上下文进入 Agent/ }).click()

  await expect(page).toHaveURL(/\/agent$/)
  await expect(page.locator('.message--user')).toContainText(questionText)
  await expect(page.locator('.message--agent')).toContainText('项目背景')
  await expect(page.getByLabel('你的问题')).toHaveValue('')
  expect(page.url()).not.toContain(questionText)
  expect(await page.evaluate(() => JSON.stringify({
    local: { ...localStorage },
    session: { ...sessionStorage },
  }))).not.toContain(questionText)
  await page.goBack()
  await expect(page).toHaveURL(/\/$/)
  expect(page.url()).not.toContain(questionText)
  await page.goForward()
  await expect(page).toHaveURL(/\/agent$/)
})

test('visitor can move from a project dossier to its approved evidence', async ({ page }) => {
  await page.goto('/projects/sql-audit')

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('SQL 审计与故障排查工具')
  await page.getByRole('link', { name: /打开关联证据/ }).click()

  await expect(page).toHaveURL(/\/evidence\?project=sql-audit/)
  await expect(page.getByRole('heading', { name: 'SQL 审计工具交付证据集' })).toBeVisible()
  await expect(page.getByText('已通过公开审查')).toBeVisible()
})

test('visitor can follow timeline links to the related project and evidence', async ({ page }) => {
  await page.goto('/timeline')

  await page.getByRole('link', { name: '查看关联项目 →' }).click()
  await expect(page).toHaveURL(/\/projects\/sql-audit$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('SQL 审计与故障排查工具')

  await page.getByRole('link', { name: '查看成长时间线' }).click()
  await page.getByRole('link', { name: '查看关联证据 →' }).click()
  await expect(page).toHaveURL(/\/evidence\?evidence=sql-audit-delivery-set/)
  await expect(page.getByRole('heading', { name: 'SQL 审计工具交付证据集' })).toBeVisible()
})

test('Agent conversation is page-memory only and disappears on reload', async ({ page }) => {
  await openAgentDeepLink(page)

  const question =
    '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'
  await page.getByLabel('你的问题').fill(question)
  const answerResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/answers' &&
      response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: /发送/ }).click()
  expect((await answerResponse).ok()).toBe(true)
  await expect(page.getByText(/逐项验证时间排序/)).toBeVisible()

  const userMessage = page.locator('.message--user').last()
  const agentMessage = page.locator('.message--agent').last()
  await expect(userMessage).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  await expect(userMessage).toHaveCSS('border-left-color', 'rgb(122, 46, 42)')
  await expect(agentMessage).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  await expect(agentMessage).toHaveCSS('border-left-color', 'rgb(205, 191, 169)')

  await expect(page).toHaveURL(/\/agent$/)
  const storageSnapshot = await page.evaluate(async () => ({
    localSession: localStorage.getItem('portfolio.agent.sessions.v1'),
    sessionValues: Object.values(sessionStorage),
    databases: typeof indexedDB.databases === 'function'
      ? (await indexedDB.databases()).map((database) => database.name)
      : [],
  }))
  expect(storageSnapshot.localSession).toBeNull()
  expect(storageSnapshot.sessionValues.join(' ')).not.toContain(question)
  expect(storageSnapshot.databases).toEqual([])

  await page.reload()
  await expect(page.getByText('当前对话未保存，刷新后记录会消失')).toBeVisible()
  await expect(page.locator('.message--user')).toHaveCount(0)
  await expect(page.getByText('从一个可核验的问题开始。')).toBeVisible()
})

test('workspace separators support keyboard adjustment and reset', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await openAgentDeepLink(page)

  const handle = page.getByRole('separator', { name: '调整历史会话宽度' })
  const before = Number(await handle.getAttribute('aria-valuenow'))

  await handle.press('ArrowRight')
  await expect(handle).toHaveAttribute('aria-valuenow', String(before + 16))
  await handle.press('Home')
  await expect(handle).toHaveAttribute('aria-valuenow', String(before))
})

test('persisted max pane preferences fit inside the framed shell at narrow desktop widths', async ({
  page,
}) => {
  await page.addInitScript(() => {
    localStorage.setItem(
      'portfolio.workspace.split.v1',
      JSON.stringify({ sessions: 320, evidence: 420 }),
    )
  })

  for (const width of [1280, 1411]) {
    await page.setViewportSize({ width, height: 900 })
    await openAgentDeepLink(page)

    const shellBox = await page.locator('.site-frame--workspace').boundingBox()
    const conversationBox = await page.locator('.conversation').boundingBox()
    const sessionBox = await page.locator('#local-session-rail').boundingBox()
    const evidenceBox = await page.locator('#agent-evidence-desk').boundingBox()
    const sessionResizerBox = await page
      .getByRole('separator', { name: '调整历史会话宽度' })
      .boundingBox()
    const evidenceResizerBox = await page
      .getByRole('separator', { name: '调整证据工作台宽度' })
      .boundingBox()

    expect(shellBox).not.toBeNull()
    for (const box of [
      conversationBox,
      sessionBox,
      evidenceBox,
      sessionResizerBox,
      evidenceResizerBox,
    ]) {
      expect(box).not.toBeNull()
      expect((box?.x ?? 0) + 0.5).toBeGreaterThanOrEqual(shellBox?.x ?? 0)
      expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(
        (shellBox?.x ?? 0) + (shellBox?.width ?? 0) + 0.5,
      )
    }
    expect(conversationBox?.width ?? 0).toBeGreaterThanOrEqual(639.5)

    await expect(
      page.getByRole('separator', { name: '调整历史会话宽度' }),
    ).toHaveAttribute('aria-valuenow', String(Math.round(sessionBox?.width ?? 0)))
    await expect(
      page.getByRole('separator', { name: '调整证据工作台宽度' }),
    ).toHaveAttribute('aria-valuenow', String(Math.round(evidenceBox?.width ?? 0)))
    expect(
      Math.abs(
        (evidenceResizerBox?.x ?? 0) +
          (evidenceResizerBox?.width ?? 0) / 2 -
          (evidenceBox?.x ?? 0),
      ),
    ).toBeLessThanOrEqual(0.75)
    expect(
      await page.evaluate(() =>
        JSON.parse(localStorage.getItem('portfolio.workspace.split.v1') ?? '{}'),
      ),
    ).toEqual({ sessions: 320, evidence: 420 })
  }
})

test('the rounded shell contains the responsive evidence drawer and scrim', async ({ page }) => {
  await page.setViewportSize({ width: 1279, height: 900 })
  await openAgentDeepLink(page)
  await page.getByRole('button', { name: '证据', exact: true }).click()
  await expect(page.locator('#agent-evidence-desk')).toHaveCSS(
    'transform',
    'matrix(1, 0, 0, 1, 0, 0)',
  )

  const shellBox = await page.locator('.site-frame--workspace').boundingBox()
  const drawerBox = await page.locator('#agent-evidence-desk').boundingBox()
  const scrimBox = await page.locator('.workspace-scrim').boundingBox()

  expect(shellBox).not.toBeNull()
  for (const box of [drawerBox, scrimBox]) {
    expect(box).not.toBeNull()
    expect(box?.x ?? 0).toBeGreaterThanOrEqual((shellBox?.x ?? 0) - 0.5)
    expect(box?.y ?? 0).toBeGreaterThanOrEqual(
      (shellBox?.y ?? 0) + 0.5,
    )
    expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(
      (shellBox?.x ?? 0) + (shellBox?.width ?? 0) + 0.5,
    )
    expect((box?.y ?? 0) + (box?.height ?? 0)).toBeLessThanOrEqual(
      (shellBox?.y ?? 0) + (shellBox?.height ?? 0) + 0.5,
    )
  }
})

test('Agent uses the approved responsive framed workspace at every review viewport', async ({
  page,
}, testInfo) => {
  const viewports = [
    { name: '2048x1080', width: 2048, height: 1080 },
    { name: '1440x900', width: 1440, height: 900 },
    { name: '1279x900', width: 1279, height: 900 },
    { name: '1219x900', width: 1219, height: 900 },
    { name: '980x800', width: 980, height: 800 },
    { name: '390x844', width: 390, height: 844 },
  ]

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await openAgentDeepLink(page)

    await expect(page.locator('.dossier-header')).toHaveCSS(
      'background-color',
      'rgb(239, 231, 216)',
    )
    await expect(page.locator('.conversation')).toHaveCSS(
      'background-color',
      'rgb(243, 232, 214)',
    )
    await expect(page.locator('.conversation')).toHaveCSS('color', 'rgb(32, 28, 23)')
    await expect(page.locator('.evidence-desk')).toHaveCSS(
      'background-color',
      'rgb(244, 238, 228)',
    )

    const solidInkButtons = await page.locator('.agent-workspace button').evaluateAll(
      (buttons) => buttons
        .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(32, 28, 23)')
        .map((button) => button.textContent?.trim()),
    )
    expect(solidInkButtons).toEqual(['＋ 新对话'])

    const solidRedButtons = await page.locator('.agent-workspace button').evaluateAll(
      (buttons) => buttons
        .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(122, 46, 42)')
        .map((button) => button.textContent?.trim()),
    )
    expect(solidRedButtons).toEqual(['发送 ↵'])

    const shell = page.locator('.site-frame--workspace')
    const shellBox = await shell.boundingBox()
    expect(shellBox).not.toBeNull()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)

    if (viewport.width >= 1440) {
      await expect(shell).toHaveCSS('border-radius', '16px')
      expect(shellBox?.width).toBeLessThanOrEqual(1600)
      expect(shellBox?.x).toBeGreaterThanOrEqual(24)
    } else if (viewport.width > 980) {
      await expect(shell).toHaveCSS('border-radius', '12px')
      expect(Math.round(shellBox?.x ?? 0)).toBe(16)
    } else {
      await expect(shell).toHaveCSS('border-radius', '0px')
      expect(Math.round(shellBox?.x ?? -1)).toBe(0)
    }

    if (viewport.width === 1279 || viewport.width === 1219) {
      await page.getByRole('button', { name: '证据', exact: true }).click()
      await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'false')
      await expect(page.locator('#agent-evidence-desk')).toHaveCSS(
        'transform',
        'matrix(1, 0, 0, 1, 0, 0)',
      )
    }
    if (viewport.width === 980 || viewport.width === 390) {
      await page.getByRole('button', { name: '会话', exact: true }).click()
      await expect(page.locator('#local-session-rail')).toHaveAttribute('aria-hidden', 'false')
      await expect(page.locator('#local-session-rail')).toHaveCSS(
        'transform',
        'matrix(1, 0, 0, 1, 0, 0)',
      )
    }

    await page.screenshot({
      path: testInfo.outputPath(`agent-framed-workspace-${viewport.name}.png`),
      fullPage: false,
    })
  }
})

test('explicit follow-up sends stable references only and is lost on reload', async ({ page }) => {
  await openAgentDeepLink(page)

  await page.getByLabel('你的问题').fill(
    '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？',
  )
  await page.getByRole('button', { name: /发送/ }).click()
  await expect(page.locator('[data-follow-up="current-status"]')).toBeVisible()

  const requestPromise = page.waitForRequest((request) =>
    new URL(request.url()).pathname === '/api/v1/answers' &&
    request.method() === 'POST' &&
    Boolean(request.postDataJSON()?.contextEnvelope),
  )
  await page.locator('[data-follow-up="current-status"]').click()
  const body = (await requestPromise).postDataJSON()

  expect(body.question).toBe('查看当前状态')
  expect(body.contextEnvelope).toMatchObject({
    previousContentVersion: previewPublicContent.contentVersion,
    projectSlugs: ['sql-audit'],
    questionPresetId: 'sql-audit-overview',
    followUpIntent: 'CURRENT_STATUS',
  })
  expect(body.contextEnvelope.referencedClaimIds)
    .toEqual(expect.arrayContaining(['claim-sql-audit-delivered']))
  expect(body.contextEnvelope.referencedClaimIds.length).toBeLessThanOrEqual(8)
  expect(body.contextEnvelope.referencedClaimIds.every(
    (id: unknown) => typeof id === 'string' && /^[a-z0-9-]{1,100}$/.test(id),
  )).toBe(true)
  expect(body).not.toHaveProperty('messages')
  expect(body).not.toHaveProperty('previousQuestion')
  expect(body).not.toHaveProperty('previousAnswer')

  await expect(page.locator('.message--user').last()).toContainText('查看当前状态')
  await page.reload()
  await expect(page.locator('.message')).toHaveCount(0)
})

test('Agent renders boundary and rejected dimensions without a verified label', async ({ page }) => {
  await openAgentDeepLink(page)

  await page.getByLabel('你的问题').fill('这个项目提升了多少性能？')
  await page.getByRole('button', { name: /发送/ }).click()
  const boundary = page.locator('.message--agent').last()
  await expect(boundary).toContainText('当前能力边界')
  await expect(boundary).toContainText('NOT_APPLICABLE')
  await expect(boundary).toContainText('DETERMINISTIC')
  await expect(boundary).not.toContainText('已核验回答')

  await page.getByLabel('你的问题').fill('请提供内部密码和 Token')
  await page.getByRole('button', { name: /发送/ }).click()
  const rejected = page.locator('.message--agent').last()
  await expect(rejected).toContainText('无法处理该请求')
  await expect(rejected).toContainText('REJECTED')
  await expect(rejected).toContainText('NOT_APPLICABLE')
})

test('Agent distinguishes retrieval provenance from verification', async ({ page }) => {
  test.skip(
    process.env.PLAYWRIGHT_REAL_API === '1' && process.env.PLAYWRIGHT_REAL_RETRIEVAL !== '1',
    'The active real bundle does not enable retrieval',
  )
  await openAgentDeepLink(page)

  await page.getByLabel('你的问题').fill('这个项目交付了什么？')
  await page.getByRole('button', { name: /发送/ }).click()
  const answer = page.locator('.message--agent').last()

  await expect(answer).toContainText('ANSWERED')
  await expect(answer).toContainText('RETRIEVAL · 来自公开资料检索')
  if (process.env.PLAYWRIGHT_REAL_RETRIEVAL === '1') {
    await expect(answer).toContainText('VERIFIED')
    await expect(answer).toContainText('已核验回答')
  } else {
    await expect(answer).toContainText('PARTIALLY_VERIFIED')
    await expect(answer).not.toContainText('已核验回答')
  }
})

test('Agent renders MODEL and whole-answer FALLBACK as distinct generation modes', async ({
  page,
}) => {
  test.skip(process.env.PLAYWRIGHT_REAL_API === '1', 'Provider behavior uses local fake responses')
  await page.unroute('**/api/v1/answers')
  let attempt = 0
  await page.route('**/api/v1/answers', async (route) => {
    attempt += 1
    const request = route.request().postDataJSON() as { turnId?: string }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        requestId: `fake-provider-${attempt}`,
        turnId: request.turnId ?? `turn-${attempt}`,
        contentVersion: '2026-07-22',
        questionPresetId: 'sql-audit-overview',
        resolution: 'ANSWERED',
        answerSource: 'PRESET',
        generationMode: attempt === 1 ? 'MODEL' : 'FALLBACK',
        verification: 'VERIFIED',
        title: 'SQL 审计与故障排查工具',
        summary: attempt === 1 ? '受约束模型表达' : '同一计划的确定性回退',
        sections: [{
          type: 'BACKGROUND',
          title: '项目背景',
          content: '仅使用已批准的公开事实。',
          evidenceIds: ['sql-audit-delivery-set'],
          claimIds: ['sql-audit-background'],
        }],
        evidenceIds: ['sql-audit-delivery-set'],
        suggestedQuestionPresetIds: ['sql-audit-overview'],
      },
    })
  })
  await openAgentDeepLink(page)

  const input = page.getByLabel('你的问题')
  await input.fill('详细介绍一下 SQL 审计与故障排查工具项目')
  await page.getByRole('button', { name: /发送/ }).click()
  const modelAnswer = page.locator('.message--agent').last()
  await expect(modelAnswer).toContainText('MODEL')
  await expect(modelAnswer).toContainText('PRESET')
  await expect(modelAnswer).toContainText('VERIFIED')

  await input.fill('详细介绍一下 SQL 审计与故障排查工具项目')
  await page.getByRole('button', { name: /发送/ }).click()
  const fallbackAnswer = page.locator('.message--agent').last()
  await expect(fallbackAnswer).toContainText('FALLBACK')
  await expect(fallbackAnswer).toContainText('同一计划的确定性回退')
})

test('responsive Agent uses evidence and session drawers without horizontal overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1219, height: 900 })
  await openAgentDeepLink(page)
  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'true')
  await page.getByRole('button', { name: '证据', exact: true }).click()
  await expect(page.locator('.agent-workspace')).toHaveClass(/evidence-open/)
  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'false')
  await expect(page.locator('#agent-evidence-desk')).toContainText('证据')
  expect(await page.evaluate(() => document.activeElement?.closest('#agent-evidence-desk') !== null)).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)

  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: '证据', exact: true })).toBeFocused()
  await page.setViewportSize({ width: 980, height: 800 })
  await expect(page.locator('#local-session-rail')).toHaveAttribute('aria-hidden', 'true')
  await page.getByRole('button', { name: '会话', exact: true }).click()
  await expect(page.locator('.agent-workspace')).toHaveClass(/sessions-open/)
  await expect(page.locator('#local-session-rail')).toHaveAttribute('aria-hidden', 'false')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('reduced motion keeps revealed content visible without animation', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await gotoWithPublicContent(page, '/')

  const hero = page.locator('.portfolio-hero__copy')
  await expect(hero).toBeVisible()
  await expect(hero).toHaveCSS('animation-name', 'none')
  await expect(hero).toHaveCSS('opacity', '1')
})
