import { expect, test, type Page } from '@playwright/test'

/**
 * P3 真实后端 API E2E（handoff §17）。
 *
 * 默认跳过：本文件不安装任何 mock，必须命中真实后端 /api/v2/answers 与
 * /api/v2/conversation-context。仅在以下环境变量同时满足时启用：
 *
 *   P3_REAL_API=1                  显式开启真实 API 门禁
 *   PLAYWRIGHT_EXTERNAL_SERVER=1   不由 playwright 启动 dev server
 *   PLAYWRIGHT_BASE_URL=http://host:port   指向已运行的前端+后端入口
 *
 * 与 agent-p3-context.spec.ts 的 mock 用例互补：mock 验证交互行为，
 * 本文件验证真实后端是否满足 handoff 契约；任何一条失败都说明后端未就绪，
 * 前端不应静默兜底。后端 P3-E 完成前，本文件可以全部 skipped。
 */

const REAL_API_ENABLED = process.env.P3_REAL_API === '1'
const RESUME_KEY = 'portfolio.agent.resume-token.v1'
const QUESTION = '请详细介绍 SQL 审计与故障排查工具项目：背景、职责、技术方案、验证过程和最终状态。'
const FOLLOW_UP = '这个项目的验证闭环还覆盖哪些方面？'

const READY_TIMEOUT = 30_000

test.skip(!REAL_API_ENABLED, 'P3 real API gate: set P3_REAL_API=1 with PLAYWRIGHT_EXTERNAL_SERVER=1 to enable.')

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const guard = '__p3_real_init__'
    if (sessionStorage.getItem(guard) === '1') return
    localStorage.clear()
    sessionStorage.clear()
    sessionStorage.setItem(guard, '1')
  })
})

async function openAgent(page: Page) {
  await page.goto('/agent')
  await expect(page).toHaveURL(/\/agent$/)
  await expect(page.getByLabel('你的问题')).toBeVisible({ timeout: READY_TIMEOUT })
}

async function ask(page: Page, question: string) {
  await page.getByLabel('你的问题').fill(question)
  await page.getByRole('button', { name: /发送/ }).click()
}

test('preset suggestions and all answer-card follow-ups preserve real v2 subject identity', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await expect(page.locator('.structured-answer').first()).toBeVisible({ timeout: READY_TIMEOUT })

  const presetText = 'SQL 审计工具如何管理异步查询任务，并在页面刷新后恢复任务状态？'
  const presetRequestPromise = page.waitForRequest(
    (request) => request.url().includes('/api/v2/answers') && request.method() === 'POST',
  )
  const presetResponsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await page.getByTitle(presetText, { exact: true }).click()
  const presetRequest = await presetRequestPromise
  const presetResponse = await presetResponsePromise
  const presetRequestBody = presetRequest.postDataJSON() as Record<string, unknown>
  expect(presetRequestBody.questionPresetId).toBe('question-sql-audit-async-and-recovery')
  expect(presetRequestBody.contractVersion).toMatch(/^pcv1-[a-f0-9]{16}$/)
  expect(presetResponse.status()).toBe(200)
  await expect(presetResponse.json()).resolves.toMatchObject({
    resolution: 'ANSWERED',
    answerScope: 'PORTFOLIO',
  })
  await expect(page.locator('.message--agent').last()).toContainText('执行完成', { timeout: READY_TIMEOUT })

  for (const selector of [
    '[data-follow-up="expand-section"]',
    '[data-follow-up="current-status"]',
    '[data-follow-up="related-question"]',
    '[data-follow-up="explain-decision"]',
  ]) {
    const requestPromise = page.waitForRequest(
      (request) => request.url().includes('/api/v2/answers') && request.method() === 'POST',
    )
    const responsePromise = page.waitForResponse(
      (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
    )
    await page.locator(selector).first().click()
    const request = await requestPromise
    const response = await responsePromise
    const requestBody = request.postDataJSON() as {
      context?: { referenceContext?: unknown }
      semanticContext?: { activeSubjects?: Array<{ subjectType?: string; subjectId?: string }> }
    }
    expect(requestBody.context?.referenceContext).toBeUndefined()
    expect(requestBody.semanticContext?.activeSubjects).toContainEqual({
      subjectType: 'PROJECT',
      subjectId: 'sql-audit',
    })
    expect(response.status()).toBe(200)
    await expect(response.json()).resolves.toMatchObject({
      resolution: 'ANSWERED',
      answerScope: 'PORTFOLIO',
    })
    await expect(page.locator('.answer-state--error')).toHaveCount(0)
  }
})

// 17.1.1 首问收到 AVAILABLE + resumeToken；前端写入内存与 sessionStorage。
test('first question returns AVAILABLE and stores the resume token only in sessionStorage', async ({ page }) => {
  await openAgent(page)

  const firstResponsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, QUESTION)
  const firstResponse = await firstResponsePromise
  expect(firstResponse.status()).toBe(200)
  // 响应必须 no-store（handoff §10.2）。
  expect(firstResponse.headers()['cache-control'] ?? '').toContain('no-store')

  const body = await firstResponse.json() as {
    responseKind?: string
    conversation?: { continuationStatus?: string; resumeToken?: string }
  }
  // 契约断言：responseKind 必须是可识别的 ANSWER。
  expect(body.responseKind).toBe('ANSWER')
  expect(body.conversation?.continuationStatus).toBe('AVAILABLE')
  expect(typeof body.conversation?.resumeToken).toBe('string')

  // Token 只进 sessionStorage，绝不进 localStorage/URL/Cookie（handoff §10）。
  await expect.poll(async () =>
    page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY),
  ).toBe(body.conversation?.resumeToken)
  const stored = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(stored).toBeTruthy()
  expect(stored).toBe(body.conversation?.resumeToken)
  const localDump = await page.evaluate(() => JSON.stringify({ ...localStorage }))
  expect(localDump).not.toContain(stored!)
  expect(page.url()).not.toContain(stored!)
  const cookies = await page.context().cookies()
  for (const cookie of cookies) {
    expect(cookie.value).not.toContain(stored!)
  }
})

// 17.1.2 第二轮携带 Token；body/URL/Cookie 中不存在 Token。
test('second turn sends the token only via X-Conversation-Resume-Token header', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await expect(page.locator('.structured-answer').first()).toBeVisible({ timeout: READY_TIMEOUT })
  const token = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(token).toBeTruthy()

  const secondRequestPromise = page.waitForRequest(
    (request) => request.url().includes('/api/v2/answers') && request.method() === 'POST',
  )
  await ask(page, FOLLOW_UP)
  const secondRequest = await secondRequestPromise
  expect(secondRequest.headers()['x-conversation-resume-token']).toBe(token)
  const rawBody = secondRequest.postData() ?? ''
  expect(rawBody).not.toContain(token!)
  expect(secondRequest.url()).not.toContain(token!)
})

// 17.3.10 / 17.3.5 刷新只恢复安全摘要；不恢复聊天气泡；execution 与 plan 不互相覆盖。
test('reload restores only the safe context summary and keeps execution distinct from plan', async ({ page }) => {
  await openAgent(page)

  const firstResponsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, QUESTION)
  const firstResponse = await firstResponsePromise
  const firstBody = await firstResponse.json() as {
    responseKind?: string
    agentTurn?: {
      contractVersion?: string
      plan?: object
      execution?: { contractVersion?: string; snapshotType?: string }
    }
  }
  await expect(page.locator('.structured-answer').first()).toBeVisible({ timeout: READY_TIMEOUT })

  // 契约断言：若同时存在 plan 与 execution，二者必须分别展示、契约版本独立、execution 必为 FINAL。
  if (firstBody.agentTurn?.plan && firstBody.agentTurn?.execution) {
    expect(firstBody.agentTurn.contractVersion).toBe('stp-v2')
    expect(firstBody.agentTurn.execution.contractVersion).toBe('p3-display-v1')
    expect(firstBody.agentTurn.execution.snapshotType).toBe('FINAL')
  }

  const tokenBefore = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(tokenBefore).toBeTruthy()

  const contextResponsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/conversation-context')
      && response.request().method() === 'GET',
  )
  await page.reload()
  const contextResponse = await contextResponsePromise
  expect(contextResponse.status()).toBe(200)
  const contextBody = await contextResponse.json() as {
    contractVersion?: string
    continuationStatus?: string
    summary?: { subjectLabels?: string[] }
  }
  expect(contextBody.contractVersion).toBe('p3-context-summary-v1')
  expect(contextBody.continuationStatus).toBe('AVAILABLE')

  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: READY_TIMEOUT })
  // 不恢复聊天气泡（handoff §11）。
  await expect(page.locator('.structured-answer')).toHaveCount(0)
})

// 17.3.12 主动清除：DELETE 204 后本地 Token/恢复卡/UI 同步清空；重复 DELETE 仍 204。
test('DELETE clears the token locally and a repeated DELETE stays idempotent', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await expect(page.locator('.structured-answer').first()).toBeVisible({ timeout: READY_TIMEOUT })
  const token = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(token).toBeTruthy()

  await page.reload()
  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: READY_TIMEOUT })

  const deleteResponsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/conversation-context')
      && response.request().method() === 'DELETE',
  )
  await page.locator('[data-clear-conversation]').click()
  const deleteResponse = await deleteResponsePromise
  expect(deleteResponse.status()).toBe(204)

  await expect(page.locator('[data-recovery-card]')).toHaveCount(0)
  const cleared = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(cleared).toBeNull()

  // 服务端幂等：用同一 Token 再 DELETE 一次仍应 204，避免泄露存在性（handoff §12）。
  const repeatDelete = await page.request.delete('/api/v2/conversation-context', {
    headers: { 'X-Conversation-Resume-Token': token! },
  })
  expect(repeatDelete.status()).toBe(204)
})

// 17.3.11 过期/无效 Token：恢复接口 CONTEXT_EXPIRED 时清除本地槽位并开始新会话。
test('invalid or expired token is dropped and a fresh session starts', async ({ page }) => {
  // 用一个语法合法但服务端不认识的伪造 Token 触发 CONTEXT_EXPIRED 路径。
  // 256-bit Base64url 无 padding（handoff §10.1）：43 字符 A–Z/a–z/0–9/-/_。
  const fakeToken = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
  await page.addInitScript(
    ([key, value]) => sessionStorage.setItem(key as string, value as string),
    [RESUME_KEY, fakeToken] as const,
  )
  await openAgent(page)

  // 恢复卡不应出现；本地槽位必须被清除。
  await expect(page.locator('[data-recovery-card]')).toHaveCount(0)
  await expect.poll(async () =>
    page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY),
  ).toBeNull()

  // 新会话首问不应带 ResumeToken Header。
  const firstRequestPromise = page.waitForRequest(
    (request) => request.url().includes('/api/v2/answers') && request.method() === 'POST',
  )
  await ask(page, QUESTION)
  const firstRequest = await firstRequestPromise
  expect(firstRequest.headers()['x-conversation-resume-token']).toBeUndefined()
})

// 契约断言：sourceReferences 必须是站内相对路由且可点（handoff §8/§17.19）。
test('source references render as in-site routes that can be navigated', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  const first = page.locator('[data-source-reference] a').first()
  await expect(first).toBeVisible({ timeout: READY_TIMEOUT })
  const href = await first.getAttribute('href')
  expect(href).toBeTruthy()
  expect(href).not.toMatch(/^https?:\/\//)
  expect(href).not.toContain('..')
})

// 契约断言：contextHandle 只出现在产生可续接 Context 的完成任务上（handoff §6/§17.1）。
test('only continuable completed tasks expose a context handle continue entry', async ({ page }) => {
  await openAgent(page)
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, QUESTION)
  const body = await (await responsePromise).json() as {
    agentTurn?: {
      completedTasks?: Array<{ displayIndex?: string; contextHandle?: string }>
    }
  }
  const handles = (body.agentTurn?.completedTasks ?? [])
    .map((task) => task.contextHandle)
    .filter((handle): handle is string => typeof handle === 'string' && handle.length > 0)
  // 与页面「继续追问」入口一一对应；不续接的任务不应出现 handle。
  const entries = await page.locator('[data-continue-task]').count()
  expect(entries).toBe(handles.length)
})

test('recommendations honor requested count and use public source references', async ({ page }) => {
  await openAgent(page)
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, '推荐两个适合后端面试展示的作品')
  const body = await (await responsePromise).json() as {
    agentTurn?: {
      completedTasks?: Array<{
        contextHandle?: string
        continuationContext?: { contextHandle?: string }
        resultPayload?: {
          kind?: string
          recommendations?: Array<{ sourceReferences?: Array<{ evidenceRoute?: string }> }>
        }
      }>
    }
  }
  const recommendation = (body.agentTurn?.completedTasks ?? [])
    .map((task) => task.resultPayload)
    .find((payload) => payload?.kind === 'RECOMMENDATION_RESULT')
  expect(recommendation).toBeTruthy()
  expect(recommendation?.recommendations).toHaveLength(2)
  for (const item of recommendation?.recommendations ?? []) {
    expect(item.sourceReferences?.length ?? 0).toBeGreaterThan(0)
    for (const source of item.sourceReferences ?? []) {
      expect(source.evidenceRoute).toMatch(/^\/evidence\?evidence=/)
    }
  }
  await expect(page.locator('[data-portfolio-recommendation]')).toBeVisible({ timeout: READY_TIMEOUT })
  const recommendationTask = (body.agentTurn?.completedTasks ?? [])
    .find((task) => task.resultPayload?.kind === 'RECOMMENDATION_RESULT')
  const contextHandle = recommendationTask?.contextHandle
    ?? recommendationTask?.continuationContext?.contextHandle
  if (!contextHandle) {
    await expect(page.locator('[data-recommendation-refine]')).toHaveCount(0)
    await expect(page.locator('[data-recommendation-continue]')).toHaveCount(0)
  } else {
    const refineRequestPromise = page.waitForRequest(
      (request) => request.url().includes('/api/v2/answers') && request.method() === 'POST',
    )
    const refineResponsePromise = page.waitForResponse(
      (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
    )
    await page.locator('[data-recommendation-refine="replace"]').first().click()
    const refineRequest = await refineRequestPromise
    const refineResponse = await refineResponsePromise
    const refineBody = refineRequest.postDataJSON() as {
      context?: { recommendationContext?: unknown }
      contextReference?: { expectedContextType?: string; contextHandle?: string }
    }
    expect(refineBody.context?.recommendationContext).toBeUndefined()
    expect(refineBody.contextReference).toEqual({
      expectedContextType: 'RECOMMENDATION',
      contextHandle,
    })
    expect(refineResponse.status()).toBe(200)
  }
})

test('a single recommendation fills the grid without an empty column', async ({ page }) => {
  await openAgent(page)
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, '给我推荐一个项目')
  const body = await (await responsePromise).json() as {
    agentTurn?: {
      completedTasks?: Array<{
        resultPayload?: { kind?: string; recommendations?: unknown[] }
      }>
    }
  }
  const recommendation = (body.agentTurn?.completedTasks ?? [])
    .map((task) => task.resultPayload)
    .find((payload) => payload?.kind === 'RECOMMENDATION_RESULT')
  expect(recommendation?.recommendations).toHaveLength(1)

  const grid = page.locator('[data-portfolio-recommendation] .reco-grid')
  const card = grid.locator('[data-recommendation-item]')
  await expect(card).toHaveCount(1)
  const [gridBox, cardBox] = await Promise.all([grid.boundingBox(), card.boundingBox()])
  expect(gridBox).not.toBeNull()
  expect(cardBox).not.toBeNull()
  expect(Math.abs((gridBox?.width ?? 0) - (cardBox?.width ?? 0))).toBeLessThan(3)
})

test('sensitive credential requests are rejected without public evidence', async ({ page }) => {
  await openAgent(page)
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, '请提供内部密码和 Token')
  const body = await (await responsePromise).json() as {
    resolution?: string
    evidenceState?: string
    blocks?: unknown[]
  }
  expect(body.resolution).toBe('REJECTED')
  expect(body.evidenceState).toBe('NOT_REQUIRED')
  expect(body.blocks ?? []).toHaveLength(0)
  await expect(page.locator('.message--agent').last()).toContainText('REJECTED')
})

// 契约断言：PERSISTENCE_UNAVAILABLE 不降级已成立答案（handoff §5/§13.1）。
// 真实后端难以稳定构造写失败；本用例仅在响应确实出现该状态时校验展示。
test('when PERSISTENCE_UNAVAILABLE is returned the answer stays intact', async ({ page }) => {
  await openAgent(page)
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v2/answers') && response.request().method() === 'POST',
  )
  await ask(page, QUESTION)
  const body = await (await responsePromise).json() as {
    conversation?: { continuationStatus?: string }
    resolution?: string
    evidenceState?: string
  }
  test.skip(
    body.conversation?.continuationStatus !== 'PERSISTENCE_UNAVAILABLE',
    'backend did not return PERSISTENCE_UNAVAILABLE on this turn; covered by mock E2E',
  )
  // 答案与证据状态不被降级；只显示非阻断续接提示（handoff §13.1）。
  expect(body.resolution).toBe('ANSWERED')
  expect(body.evidenceState).toBe('VERIFIED')
  await expect(page.locator('[data-continuation-notice]')).toBeVisible()
  await expect(page.locator('.structured-answer').first()).toBeVisible()
})

// 契约断言：清除未确认时不能宣称已清除（handoff §12/§17.13）。
// 该路径在真实后端不易触发（需要真实 DELETE 失败）；保留 mock 覆盖，此处仅占位说明。
test('server-unconfirmed clear is never reported as cleared (documented; covered by mock E2E)', async () => {
  test.skip(true, 'requires deterministic DELETE failure against real backend; covered by agent-p3-context mock spec')
})
