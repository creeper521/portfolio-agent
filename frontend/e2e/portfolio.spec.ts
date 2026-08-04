import { expect, test, type Page } from '@playwright/test'

import {
  installAnswerApiMock,
  installAnswerScenarioMock,
  installDiagnosticsApiMock,
  installPublicApiMocks,
} from './support/publicApiMocks'

const usesRealApi = process.env.PLAYWRIGHT_REAL_API === '1'
const SERVER_REQUEST_ID = '11111111-1111-4111-8111-111111111111'
const FORBIDDEN_DIAGNOSTIC_KEYS = [
  'question',
  'messages',
  'answer',
  'stack',
  'url',
  'headers',
  'requestBody',
  'responseBody',
]

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const initializedKey = 'portfolio.playwright.initialized'
    if (sessionStorage.getItem(initializedKey) !== '1') {
      localStorage.clear()
      sessionStorage.setItem(initializedKey, '1')
    }
  })
  if (!usesRealApi) {
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

async function submitAgentQuestion(page: Page, question = '这个项目交付了什么？') {
  await page.getByLabel('你的问题').fill(question)
  await page.getByRole('button', { name: /发送/ }).click()
}

function expectClosedDiagnosticBodies(bodies: unknown[]) {
  for (const body of bodies) {
    const serialized = JSON.stringify(body)
    for (const key of FORBIDDEN_DIAGNOSTIC_KEYS) {
      expect(serialized).not.toContain(`"${key}"`)
    }
  }
}

test.describe('browser diagnostics release gate', () => {
  test.skip(usesRealApi, 'deterministic failure scenarios use browser API mocks')

  test('429 renders a countdown and uploads only a closed correlated diagnostic', async ({ page }) => {
    const diagnostics = await installDiagnosticsApiMock(page)
    await installAnswerScenarioMock(page, {
      status: 429,
      code: 'ANSWER_RATE_LIMITED',
      retryAfterSeconds: 3,
      requestId: SERVER_REQUEST_ID,
    })
    await openAgentDeepLink(page)

    await submitAgentQuestion(page)

    await expect(page.locator('[data-answer-retry]')).toBeDisabled()
    await expect(page.locator('[data-answer-retry]')).toContainText('3 秒后可重试')
    await expect(page.locator('[data-answer-retry]')).toBeEnabled({ timeout: 4_500 })
    await expect.poll(() => diagnostics.events.length).toBe(1)
    expect(diagnostics.events[0]).toMatchObject({
      eventName: 'frontend.agent.request.failed',
      serverRequestId: SERVER_REQUEST_ID,
      errorCode: 'ANSWER_RATE_LIMITED',
    })
    expectClosedDiagnosticBodies(diagnostics.bodies)
  })

  test('503 timeout offers retry and preserves the returned request correlation', async ({ page }) => {
    const diagnostics = await installDiagnosticsApiMock(page)
    await installAnswerScenarioMock(page, {
      status: 503,
      code: 'ANSWER_REQUEST_TIMEOUT',
      requestId: SERVER_REQUEST_ID,
    })
    await openAgentDeepLink(page)

    await submitAgentQuestion(page)

    await expect(page.locator('[data-answer-recovery-action="retry"]')).toBeEnabled()
    await expect.poll(() => diagnostics.events.length).toBe(1)
    expect(diagnostics.events[0]).toMatchObject({
      eventName: 'frontend.agent.request.failed',
      serverRequestId: SERVER_REQUEST_ID,
      errorCode: 'ANSWER_REQUEST_TIMEOUT',
    })
    expectClosedDiagnosticBodies(diagnostics.bodies)
  })

  test('PROJECT_NOT_FOUND offers safe navigation without exposing the server body', async ({ page }) => {
    const diagnostics = await installDiagnosticsApiMock(page)
    await installAnswerScenarioMock(page, {
      status: 404,
      code: 'PROJECT_NOT_FOUND',
      requestId: SERVER_REQUEST_ID,
      unsafeMessage: 'visitor question and internal stack must stay hidden',
    })
    await openAgentDeepLink(page)

    await submitAgentQuestion(page)

    const recovery = page.locator('[data-answer-recovery-action="navigate-back"]')
    await expect(recovery).toBeVisible()
    await expect(page.getByRole('alert')).not.toContainText('visitor question')
    await recovery.click()
    await expect(page).toHaveURL(/\/projects$/)
    await expect(page.getByRole('heading', { level: 1, name: '项目主线' })).toBeVisible()
    await expect.poll(() => diagnostics.events.length).toBe(1)
    expectClosedDiagnosticBodies(diagnostics.bodies)
  })

  test('caller cancellation appends no failure answer', async ({ page }) => {
    const diagnostics = await installDiagnosticsApiMock(page)
    await installAnswerScenarioMock(page, { delayMilliseconds: 5_100 })
    await openAgentDeepLink(page)

    await submitAgentQuestion(page, '取消这次回答')
    await page.locator('[data-answer-cancel]').click()

    await expect(page.locator('[data-agent-loading]')).toHaveCount(0)
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('.message--agent')).toHaveCount(0)
    await expect.poll(() => diagnostics.events.length).toBe(1)
    expect(diagnostics.events[0]).toMatchObject({
      eventName: 'frontend.agent.request.cancelled',
      errorCode: 'REQUEST_CANCELLED',
    })
    expectClosedDiagnosticBodies(diagnostics.bodies)
  })

  test('one slow answer emits one diagnostic and an upload failure stays invisible without retry', async ({ page }) => {
    const diagnostics = await installDiagnosticsApiMock(page, { failUploads: true })
    await installAnswerScenarioMock(page, { delayMilliseconds: 5_100 })
    await openAgentDeepLink(page)

    await submitAgentQuestion(page)

    await expect(page.locator('.message--agent')).toBeVisible({ timeout: 8_000 })
    await expect.poll(() => diagnostics.attempts).toBe(1)
    await page.waitForTimeout(2_500)
    expect(diagnostics.attempts).toBe(1)
    expect(diagnostics.events).toHaveLength(1)
    expect(diagnostics.events[0]).toMatchObject({
      eventName: 'frontend.agent.request.slow',
      durationBucket: 'GE_5000_MS',
    })
    await expect(page.getByRole('alert')).toHaveCount(0)
    expectClosedDiagnosticBodies(diagnostics.bodies)
  })

  test('a pre-response network failure reports only client correlation', async ({ page }) => {
    const diagnostics = await installDiagnosticsApiMock(page)
    await installAnswerScenarioMock(page, { networkFailure: true })
    await openAgentDeepLink(page)

    await submitAgentQuestion(page)

    await expect(page.locator('[data-answer-recovery-action="retry"]')).toBeVisible()
    await expect.poll(() => diagnostics.events.length).toBe(1)
    expect(diagnostics.events[0]).toMatchObject({
      eventName: 'frontend.agent.request.failed',
      errorCode: 'CLIENT_NETWORK_ERROR',
    })
    expect(diagnostics.events[0]).toHaveProperty('clientRequestId')
    expect(diagnostics.events[0]).not.toHaveProperty('serverRequestId')
    expectClosedDiagnosticBodies(diagnostics.bodies)
  })

  test('refresh creates a new ephemeral client session id', async ({ page }) => {
    const sessionIds: string[] = []
    await installAnswerScenarioMock(page, {
      onRequest: (headers) => sessionIds.push(headers['x-client-session-id'] ?? ''),
    })
    await openAgentDeepLink(page)
    await submitAgentQuestion(page)
    await expect(page.locator('.message--agent')).toBeVisible()

    await page.reload()
    await submitAgentQuestion(page)
    await expect(page.locator('.message--agent')).toBeVisible()

    expect(sessionIds).toHaveLength(2)
    expect(sessionIds[0]).toMatch(/^[0-9a-f-]{36}$/)
    expect(sessionIds[1]).toMatch(/^[0-9a-f-]{36}$/)
    expect(sessionIds[1]).not.toBe(sessionIds[0])
  })
})

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
  await expect(page.locator('[data-explore-entry]')).toHaveCount(5)

  await page.locator('[data-role="MENTOR"]').click()
  await expect(page.locator('[data-role="MENTOR"]')).toHaveCSS(
    'background-color',
    'rgb(32, 28, 23)',
  )
  const supportedQuestion = page.locator('[data-question]').first()
  await expect(supportedQuestion).toBeVisible()
  const answerResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v2/answers' &&
      response.request().method() === 'POST',
  )
  const questionText = (await supportedQuestion.locator('span').textContent()) ?? ''
  await supportedQuestion.click()
  expect((await answerResponse).ok()).toBe(true)
  await expect(page.locator('[data-light-answer]')).toBeVisible()
  if (usesRealApi) {
    await expect(page.locator('[data-light-answer]')).toContainText('ANSWERED')
    await expect(page.locator('[data-light-answer]')).toContainText('[E-01]')
  } else {
    await expect(page.locator('[data-light-answer]')).toContainText('预设问题')
    await expect(page.locator('[data-light-answer]')).toContainText('DETERMINISTIC')
    await expect(page.locator('[data-light-answer]')).toContainText('已核验')
  }
  await expect(page.locator('[data-answer-action]')).toHaveCount(3)
  await page.getByRole('link', { name: /带着上下文进入 Agent/ }).click()

  await expect(page).toHaveURL(/\/agent$/)
  await expect(page.locator('.message--user')).toContainText(questionText)
  await expect(page.locator('.message--agent')).toBeVisible()
  if (!usesRealApi) {
    await expect(page.locator('.message--agent')).toContainText('项目背景')
  }
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

  const deliveryEvent = page.locator('article').filter({
    has: page.getByRole('heading', { name: '从固定路径查询到可交付工具' }),
  })
  await deliveryEvent.getByRole('link', { name: '查看关联项目 →' }).click()
  await expect(page).toHaveURL(/\/projects\/sql-audit$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('SQL 审计与故障排查工具')

  await page.getByRole('link', { name: '查看成长时间线' }).click()
  await deliveryEvent.getByRole('link', { name: '查看关联证据 →' }).click()
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
      new URL(response.url()).pathname === '/api/v2/answers' &&
      response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: /发送/ }).click()
  expect((await answerResponse).ok()).toBe(true)
  await expect(page.locator('.message--agent').last()).toBeVisible()
  if (!usesRealApi) {
    await expect(page.getByText(/逐项验证时间排序/)).toBeVisible()
  }

  const userMessage = page.locator('.message--user').last()
  const agentMessage = page.locator('.message--agent').last()
  await expect(userMessage.locator('.message__body')).toHaveCSS(
    'background-color',
    'rgba(0, 0, 0, 0)',
  )
  await expect(userMessage.locator('.message__body')).toHaveCSS(
    'border-left-width',
    '2px',
  )
  await expect(agentMessage).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')

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
  await expect(page.getByText(
    '从一个可核验的问题开始——这里只回答有公开证据支撑的内容。',
  )).toBeVisible()
})

test('recommended question enters the conversation immediately', async ({ page }) => {
  let releaseResponse!: () => void
  const responseGate = new Promise<void>((resolve) => {
    releaseResponse = resolve
  })
  await page.route('**/api/v2/answers', async (route) => {
    await responseGate
    await route.fallback()
  })
  await openAgentDeepLink(page)
  const suggestion = page.locator('[data-suggested-question]').first()
  const text = (await suggestion.textContent())?.trim() ?? ''
  const response = page.waitForResponse(
    (item) =>
      new URL(item.url()).pathname === '/api/v2/answers' &&
      item.request().method() === 'POST',
  )

  await suggestion.click()
  await expect(page.locator('.message--user')).toContainText(text.replace(/^↳/, '').trim())
  await expect(page.locator('[data-agent-loading]')).toBeVisible()
  releaseResponse()
  expect((await response).ok()).toBe(true)
  await expect(page.locator('.message--agent')).toBeVisible()
  await expect(page.locator('[data-conversation-state]'))
    .toHaveAttribute('data-conversation-state', 'conversation')
})

test('answer evidence opens citations and returns to the cited section', async ({ page }) => {
  await installAnswerApiMock(page)
  await openAgentDeepLink(page)
  await page.locator('[data-suggested-question]').first().click()
  await expect(page.locator('.message--agent')).toBeVisible()

  await page.locator('[data-section-evidence]').first().click()
  await expect(page.getByRole('tab', { name: '引用' })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await expect(page.locator('[data-citation-id]').first()).toBeVisible()
  await page.locator('[data-citation-id]').first().click()
  await expect(page.locator('[data-answer-focus]')).toBeVisible()
})

test('Agent citation round trip closes the responsive evidence drawer and focuses the answer', async ({
  page,
}) => {
  await installAnswerApiMock(page)
  await page.setViewportSize({ width: 959, height: 800 })
  await openAgentDeepLink(page)
  await page.locator('[data-suggested-question]').first().click()
  await expect(page.locator('.message--agent')).toBeVisible()

  const answerSection = page.locator('[data-section-type]').first()
  await answerSection.locator('[data-section-evidence]').click()
  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'false')
  await expect(page.locator('.workspace-scrim')).toBeVisible()

  await page.locator('[data-citation-id]').first().click()

  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'true')
  await expect(page.locator('.workspace-scrim')).toHaveCount(0)
  await expect(answerSection).toHaveAttribute('data-answer-focus', 'true')
  await expect(answerSection).toBeFocused()
  await expect(page.getByLabel('你的问题')).toBeVisible()
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
    { name: '960x800', width: 960, height: 800 },
    { name: '959x800', width: 959, height: 800 },
    { name: '390x844', width: 390, height: 844 },
  ]

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await openAgentDeepLink(page)
    await page.locator('[data-suggested-question]').first().click()
    await expect(page.locator('.message--agent')).toBeVisible()
    await page.locator('.conversation__scroll').evaluate((element) => {
      element.scrollTop = 0
    })

    await expect(page.locator('.conversation')).toHaveCSS(
      'background-color',
      'rgb(245, 232, 209)',
    )
    await expect(page.locator('.conversation')).toHaveCSS('color', 'rgb(32, 28, 23)')
    await expect(page.locator('.evidence-desk')).toHaveCSS(
      'background-color',
      'rgb(248, 243, 234)',
    )
    await expect(page.locator('.session-rail')).toHaveCSS(
      'background-color',
      'rgb(240, 233, 222)',
    )

    const solidInkButtons = await page.locator('.agent-workspace button').evaluateAll(
      (buttons) => buttons
        .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(32, 28, 23)')
        .map((button) => button.textContent?.trim()),
    )
    expect(solidInkButtons).toEqual(['＋新对话'])

    const solidAccentButtons = await page.locator('.agent-workspace button').evaluateAll(
      (buttons) => buttons
        .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(122, 46, 42)')
        .map((button) => button.textContent?.trim()),
    )
    expect(solidAccentButtons).toEqual(['发送 ↵'])

    const shell = page.locator('.site-frame--workspace')
    const shellBox = await shell.boundingBox()
    expect(shellBox).not.toBeNull()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)

    if (viewport.width >= 1440) {
      await expect(shell).toHaveCSS('border-radius', '20px')
      expect(shellBox?.x).toBeGreaterThan(0)
    } else if (viewport.width >= 960) {
      await expect(shell).toHaveCSS('border-radius', '12px')
      expect(Math.round(shellBox?.x ?? 0)).toBe(16)
    } else {
      await expect(shell).toHaveCSS('border-radius', '0px')
      expect(Math.round(shellBox?.x ?? -1)).toBe(0)
    }

    if (viewport.width === 1279 || viewport.width === 960) {
      await page.getByRole('button', { name: '证据', exact: true }).click()
      await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'false')
      await expect(page.locator('#agent-evidence-desk')).toHaveCSS(
        'transform',
        'matrix(1, 0, 0, 1, 0, 0)',
      )
    }
    if (viewport.width === 959 || viewport.width === 390) {
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

test('explicit follow-up uses the strict v2 payload and is lost on reload', async ({ page }) => {
  await installAnswerApiMock(page)
  await openAgentDeepLink(page)

  await page.getByLabel('你的问题').fill(
    '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？',
  )
  await page.getByRole('button', { name: /发送/ }).click()
  await expect(page.locator('[data-follow-up="current-status"]')).toBeVisible()

  const requestPromise = page.waitForRequest((request) =>
    new URL(request.url()).pathname === '/api/v2/answers' &&
    request.method() === 'POST' &&
    request.postDataJSON()?.question === '查看当前状态',
  )
  await page.locator('[data-follow-up="current-status"]').click()
  const body = (await requestPromise).postDataJSON()

  expect(body.question).toBe('查看当前状态')
  expect(body.context).toMatchObject({
    projectSlug: 'sql-audit',
    caseSlug: null,
    audienceRole: 'INTERVIEWER',
    source: 'AGENT_PAGE',
  })
  expect(body.messages).toEqual(expect.arrayContaining([
    expect.objectContaining({ role: 'USER' }),
    expect.objectContaining({ role: 'ASSISTANT' }),
  ]))
  expect(body).not.toHaveProperty('contextEnvelope')
  expect(body).not.toHaveProperty('questionPresetId')
  expect(body.context).not.toHaveProperty('focusEvidenceIds')
  expect(body).not.toHaveProperty('previousQuestion')
  expect(body).not.toHaveProperty('previousAnswer')

  await expect(page.locator('.message--user').last()).toContainText('查看当前状态')
  await page.reload()
  await expect(page.locator('.message')).toHaveCount(0)
})

test('Agent renders unsupported and rejected dimensions without a verified label', async ({ page }) => {
  await openAgentDeepLink(page)

  await page.getByLabel('你的问题').fill('这个项目提升了多少性能？')
  await page.getByRole('button', { name: /发送/ }).click()
  const unsupported = page.locator('.message--agent').last()
  await expect(unsupported).toContainText('当前公开证据不足')
  await expect(unsupported).toContainText('NOT_SUPPORTED')
  await expect(unsupported).toContainText('EVIDENCE_COMPOSITION')
  await expect(unsupported).not.toContainText('已核验回答')

  await page.getByLabel('你的问题').fill('请提供内部密码和 Token')
  await page.getByRole('button', { name: /发送/ }).click()
  const rejected = page.locator('.message--agent').last()
  await expect(rejected).toContainText('无法处理该请求')
  await expect(rejected).toContainText('REJECTED')
  if (!usesRealApi) {
    await expect(rejected).toContainText('DETERMINISTIC')
  }
})

test('visitor can open a Case and hand its question to Agent without URL persistence', async ({
  page,
}) => {
  await gotoWithPublicContent(page, '/cases')

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('案例目录')
  await page.getByRole('link', { name: /多语言图片上传结果保留修复/ }).click()
  await expect(page).toHaveURL(/\/cases\/multilingual-image-preservation$/)

  const questionLink = page.getByRole('link', { name: /Q01/ })
  const question = (await questionLink.textContent())?.replace(/^Q01\s*/, '').trim() ?? ''
  await questionLink.click()

  await expect(page).toHaveURL(/\/agent$/)
  await expect(page.locator('[data-case-context]')).toContainText(
    '多语言图片上传结果保留修复',
  )
  await expect(page.getByLabel('你的问题')).toHaveValue(question)
  await expect(page.locator('[data-evidence-id]')).toHaveCount(1)
  expect(page.url()).not.toContain(question)
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage }))).not.toContain(question)
})

test('legacy project-shaped Case URL redirects to its canonical Case route', async ({ page }) => {
  await page.goto('/projects/multilingual-image-preservation')

  await expect(page).toHaveURL(/\/cases\/multilingual-image-preservation$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    '多语言图片上传结果保留修复',
  )
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
  await expect(answer).toContainText('资料检索')
  if (process.env.PLAYWRIGHT_REAL_RETRIEVAL === '1') {
    await expect(answer).toContainText('已核验')
    await expect(answer).toContainText('已核验回答')
  } else {
    await expect(answer).toContainText('部分核验')
    await expect(answer).not.toContainText('已核验回答')
  }
})

test('Agent renders MODEL and whole-answer FALLBACK as distinct generation modes', async ({
  page,
}) => {
  test.skip(process.env.PLAYWRIGHT_REAL_API === '1', 'Provider behavior uses local fake responses')
  await page.unroute('**/api/v2/answers')
  let attempt = 0
  await page.route('**/api/v2/answers', async (route) => {
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
  await expect(modelAnswer).toContainText('预设问题')
  await expect(modelAnswer).toContainText('已核验')

  await input.fill('详细介绍一下 SQL 审计与故障排查工具项目')
  await page.getByRole('button', { name: /发送/ }).click()
  const fallbackAnswer = page.locator('.message--agent').last()
  await expect(fallbackAnswer).toContainText('FALLBACK')
  await expect(fallbackAnswer).toContainText('同一计划的确定性回退')
})

test('responsive Agent uses evidence and session drawers without horizontal overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1279, height: 900 })
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
  await page.setViewportSize({ width: 959, height: 800 })
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

test('Agent reduced motion disables suggested-question and drawer transitions', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 959, height: 800 })
  await openAgentDeepLink(page)

  const suggestion = page.locator('[data-suggested-question]').first()
  await expect(suggestion).toBeVisible()
  await expect(suggestion).toHaveCSS('transition-duration', '0s')
  await expect(page.locator('#agent-evidence-desk')).toHaveCSS('transition-duration', '0s')
  await expect(page.locator('#local-session-rail')).toHaveCSS('transition-duration', '0s')

  await page.getByRole('button', { name: '证据', exact: true }).click()
  await expect(page.locator('.workspace-scrim')).toHaveCSS('transition-duration', '0s')
})
