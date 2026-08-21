import { expect, test } from '@playwright/test'
import {
  awaitFreshRateWindow,
  closeDrawers,
  createSession,
  delayTurnResponsesAfterBackend,
  exhaustSourceRateLimit,
  firstPublicPreset,
  nextTurnPost,
  nextTurnResponse,
  openSourcesPanel,
  selectSessionByTitle,
} from './support/agent-recovery'

const TURNS = '/api/agent/turns'
const CURRENT = '/api/agent/conversations/current'

async function freeTextAvailable(request: import('@playwright/test').APIRequestContext) {
  const response = await request.get('/api/portfolio')
  if (!response.ok()) return false
  const content = await response.json() as {
    agentAvailability?: { freeTextSemanticRouting?: string }
  }
  return content.agentAvailability?.freeTextSemanticRouting === 'AVAILABLE'
}

test('final API supports preset, replay, Bearer continuation and clear', async ({ request }) => {
  const contentResponse = await request.get('/api/portfolio')
  expect(contentResponse.ok()).toBeTruthy()
  const content = await contentResponse.json() as {
    questionPresets: Array<{ id: string; contractVersion: string }>
  }
  const preset = content.questionPresets[0]
  expect(preset).toBeTruthy()

  const requestId = crypto.randomUUID()
  const firstPayload = {
    requestId,
    command: {
      kind: 'ASK',
      input: { kind: 'PRESET', presetId: preset.id, presetRevision: preset.contractVersion },
    },
    conversationWindow: [],
  }
  const first = await request.post(TURNS, { data: firstPayload })
  expect(first.status()).toBe(200)
  expect(first.headers()['cache-control']).toContain('no-store')
  const firstTurn = await first.json() as Record<string, any>
  expect(firstTurn.kind).toBe('ANSWER')
  expect(firstTurn.answer.goalResults.length).toBeGreaterThan(0)
  expect(firstTurn.conversation.resumeToken).toBeTruthy()
  expect(JSON.stringify(firstTurn)).not.toMatch(/stp-v[123]|completedTasks|degradationSummary/)

  const replay = await request.post(TURNS, { data: firstPayload })
  expect(replay.status()).toBe(200)
  const replayTurn = await replay.json() as Record<string, any>
  expect(replayTurn.answer).toEqual(firstTurn.answer)

  const token = (replayTurn.conversation.resumeToken
    ?? firstTurn.conversation.resumeToken) as string
  const second = await request.post(TURNS, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      requestId: crypto.randomUUID(),
      command: {
        kind: 'ASK',
        input: {
          kind: 'PRESET',
          presetId: preset.id,
          presetRevision: preset.contractVersion,
        },
      },
      conversationWindow: [
        { role: 'USER', content: '介绍公开项目' },
        { role: 'ASSISTANT', content: '已介绍一个项目' },
      ],
    },
  })
  expect(second.status()).toBe(200)
  expect((await second.json()).kind).toBe('ANSWER')

  const current = await request.get(CURRENT, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(current.status()).toBe(200)
  expect(await current.json()).toEqual({
    conversationId: firstTurn.conversation.conversationId,
    status: 'ACTIVE',
  })
  expect((await request.delete(CURRENT, {
    headers: { Authorization: `Bearer ${token}` },
  })).status()).toBe(204)
  expect((await request.get(CURRENT, {
    headers: { Authorization: `Bearer ${token}` },
  })).status()).toBe(401)
})

test('final UI renders closed PublicAgentTurn and keeps token out of localStorage', async ({ page, isMobile }) => {
  await page.goto('/agent')
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS && response.request().method() === 'POST')
  await page.locator('.workspace-composer__suggestion').first().click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  await expect(page.locator('[data-turn-kind="ANSWER"]')).toBeVisible()
  await expect(page.getByTestId('answer-turn')).toBeVisible()
  await expect(page.getByTestId('turn-failure')).toHaveCount(0)

  const storage = await page.evaluate(() => ({
    sessionToken: sessionStorage.getItem('portfolio.agent.resume-token.v1'),
    localKeys: Object.keys(localStorage),
    url: location.href,
  }))
  expect(storage.sessionToken).toBeTruthy()
  expect(storage.localKeys.join('|')).not.toContain('resume-token')
  expect(storage.url).not.toContain(storage.sessionToken as string)

  if (isMobile) await page.getByTestId('open-session-drawer').click()
  await page.locator('[data-session-clear]').click()
  await page.locator('[data-session-clear-confirm]').click()
  await expect.poll(async () => page.evaluate(() =>
    sessionStorage.getItem('portfolio.agent.resume-token.v1'))).toBeNull()
})

test('问候与失败都归属原会话，新会话不继承其状态', async ({ page, request, isMobile }) => {
  test.skip(!(await freeTextAvailable(request)), '模型关闭 lane 不提供自由文本')
  await page.goto('/agent')
  await page.getByTestId('question-input').fill('你好')
  await page.getByTestId('submit-question').click()
  await expect(page.getByTestId('conversational-turn')).toBeVisible()

  await createSession(page, isMobile)
  await page.route('**/api/agent/turns', (route) => route.abort('connectionfailed'), { times: 1 })
  await page.getByTestId('question-input').fill('这条请求将模拟网络失败')
  await page.getByTestId('submit-question').click()
  await expect(page.locator('[data-failure-category="NETWORK"]')).toBeVisible()

  await createSession(page, isMobile)
  await expect(page.getByTestId('turn-failure')).toHaveCount(0)
  await expect(page.getByTestId('conversational-turn')).toHaveCount(0)
  await expect(page.getByTestId('question-input')).toBeEnabled()
})

test('cancel sends final DELETE resource and leaves no error turn', async ({ page }) => {
  await page.route('**/api/agent/turns', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1_500))
    await route.continue()
  })
  await page.goto('/agent')
  await page.locator('.workspace-composer__suggestion').first().click()
  await expect(page.getByTestId('conversation-pending')).toBeVisible()
  const deleteRequest = page.waitForRequest((request) =>
    /\/api\/agent\/turns\/[0-9a-f-]+$/i.test(new URL(request.url()).pathname)
      && request.method() === 'DELETE')
  await page.getByTestId('cancel-turn').click()
  await deleteRequest
  await expect(page.getByTestId('conversation-pending')).toHaveCount(0)
  await expect(page.getByTestId('turn-failure')).toHaveCount(0)
})

// ── 恢复矩阵（docs/15 §10.5、§12 与 2026-08-19 稳定化计划 Task 7 Step 3）──
// 以下用例全部依赖真实后端：路由只延迟真实请求，不伪造响应体。
// DEFAULT lane 用高配额验证交互；10 RPM 只在独立 ADMISSION lane 内验证，
// 避免限流窗口污染其他浏览器用例。

test('两个会话的 pending 占满标签页槽位，第三个会话的新提问被阻止，取消后释放', async ({ page, request, isMobile }) => {
  test.skip(!(await freeTextAvailable(request)), '模型关闭 lane 不提供自由文本并发输入')
  test.setTimeout(75_000)
  const delayedResponses = await delayTurnResponsesAfterBackend(page, 20_000)
  await page.goto('/agent')

  // 会话 A：发起第一个 pending。
  await page.getByTestId('question-input').fill('SQL 审计与故障排查工具')
  const firstPost = nextTurnPost(page)
  await page.getByTestId('submit-question').click()
  const firstRequestId = (await firstPost).requestId
  await expect(page.getByTestId('conversation-pending')).toBeVisible()

  // 会话 B：槽位未满，第二个 pending 可以发起。
  await createSession(page, isMobile)
  await page.getByTestId('question-input').fill('活动系统工程实践')
  const secondPost = nextTurnPost(page)
  await page.getByTestId('submit-question').click()
  await secondPost
  await expect(page.getByTestId('conversation-pending')).toBeVisible()

  // 新会话 C（当前会话、无 pending）：标签页槽位已满，新提问入口被阻止，但草稿仍可编辑。
  await createSession(page, isMobile)
  await expect(page.getByTestId('tab-pending-notice')).toBeVisible()
  await page.getByTestId('question-input').fill('先记录一个草稿')
  await expect(page.getByTestId('submit-question')).toBeDisabled()
  await expect(page.getByTestId('question-input')).toBeEnabled()

  // 取消 A 必须发送它自己的 requestId，不能误取消 B。
  await selectSessionByTitle(page, isMobile, 'SQL 审计')
  const deleteRequest = page.waitForRequest((request) =>
    request.method() === 'DELETE' && new URL(request.url()).pathname.endsWith(`/${firstRequestId}`))
  await page.getByTestId('cancel-turn').click()
  await deleteRequest
  await expect(page.locator('[data-message-failed="true"]')).toHaveCount(1)

  // B 不受 A 取消影响；真实结果完成后只回流 B，不抢占当前 A。
  await expect(page.locator('[data-turn-kind="ANSWER"]')).toHaveCount(0)
  await selectSessionByTitle(page, isMobile, '活动系统')
  await expect(page.locator('[data-turn-kind="ANSWER"]')).toBeVisible({ timeout: 25_000 })
  await expect(page.getByTestId('tab-pending-notice')).toHaveCount(0)
  await createSession(page, isMobile)
  await expect(page.locator('[data-turn-kind="ANSWER"]')).toHaveCount(0)
  await page.getByTestId('question-input').fill('槽位释放后可以再次提问')
  await expect(page.getByTestId('submit-question')).toBeEnabled()
  await delayedResponses.stop()
})

test('澄清挑战恢复推荐目标：CONSUMED 只读、当前/最近来源切换、预设脱困入口发出 PRESET', async ({ page, request, isMobile }) => {
  test.skip(!(await freeTextAvailable(request)), '模型关闭 lane 不提供语义澄清')
  await page.goto('/agent')

  // “推荐 9 个项目”数量越界，确定性进入 REQUESTED_SIZE 澄清，不依赖模型。
  await page.getByTestId('question-input').fill('推荐 9 个项目')
  const askPost = nextTurnPost(page)
  const askResponse = nextTurnResponse(page)
  await page.getByTestId('submit-question').click()
  await askPost
  expect((await askResponse).status()).toBe(200)
  await expect(page.getByTestId('clarification-turn').last()).toBeVisible()
  await expect(page.getByTestId('clarification-form')).toBeVisible()

  // 选择 2 个：RESOLVE 命令携带原始 clarificationId 与 CHOICE，答案恢复同一个推荐目标。
  await page.locator('[data-choice-id="choice_size_2"] input').check()
  const resolvePost = nextTurnPost(page)
  const resolveResponse = nextTurnResponse(page)
  await page.locator('[data-clarification-submit]').click()
  const resolve = await resolvePost
  const resolvedHttp = await resolveResponse
  expect(resolvedHttp.status()).toBe(200)
  const resolvedTurn = await resolvedHttp.json() as { kind?: string; code?: string }
  expect({ kind: resolvedTurn.kind, code: resolvedTurn.code }).toEqual({
    kind: 'ANSWER',
    code: undefined,
  })
  expect(resolve.command.kind).toBe('RESOLVE_CLARIFICATION')
  expect(resolve.command.clarificationId).toBeTruthy()
  expect(resolve.command.answer).toEqual({ kind: 'CHOICE', choiceId: 'choice_size_2' })

  // 恢复后的回答是缺省数量为 2 的推荐；旧挑战卡转为只读 CONSUMED。
  await expect(page.getByTestId('recommendation-presentation')).toBeVisible()
  await expect(page.getByTestId('recommendation-item')).toHaveCount(2)
  await expect(page.locator('[data-clarification-state="CONSUMED"]')).toBeVisible()
  await expect(page.getByTestId('clarification-form')).toHaveCount(0)

  // 当前 Turn 为 ANSWER：来源面板显示“当前回答来源”。
  await openSourcesPanel(page, isMobile)
  await expect(page.getByTestId('sources-panel-list')).toBeVisible()
  await expect(page.locator('[data-sources-stale]')).toHaveCount(0)
  await closeDrawers(page, isMobile)

  // 再次进入澄清：来源面板切换为弱化的“最近回答来源”。
  await page.getByTestId('question-input').fill('推荐 8 个项目')
  const secondAskResponse = nextTurnResponse(page)
  await page.getByTestId('submit-question').click()
  expect((await secondAskResponse).status()).toBe(200)
  await expect(page.getByTestId('clarification-turn').last()).toBeVisible()
  await openSourcesPanel(page, isMobile)
  await expect(page.locator('[data-sources-stale]')).toHaveCount(1)
  await closeDrawers(page, isMobile)

  // 脱困入口只消费已发布 preset：点击发出 PRESET 提问并回到 ANSWER。
  const escapeEntry = page.locator('[data-testid="clarification-preset-fallback"] button').first()
  const escapePresetId = await escapeEntry.getAttribute('data-fallback-preset')
  expect(escapePresetId).toBeTruthy()
  const escapePost = nextTurnPost(page)
  const escapeResponse = nextTurnResponse(page)
  await escapeEntry.click()
  const escaped = await escapePost
  expect((await escapeResponse).status()).toBe(200)
  expect(escaped.command.kind).toBe('ASK')
  expect(escaped.command.input).toMatchObject({ kind: 'PRESET', presetId: escapePresetId })
  await expect(page.locator('[data-turn-kind="ANSWER"]').last()).toBeVisible()
  await openSourcesPanel(page, isMobile)
  await expect(page.locator('[data-sources-stale]')).toHaveCount(0)
})

test('前端等待超时投影 TIMEOUT，并以同 requestId 重试取得终局', async ({ page }) => {
  test.setTimeout(90_000)
  const delayedResponses = await delayTurnResponsesAfterBackend(page, 26_000)
  await page.goto('/agent')

  const askPost = nextTurnPost(page)
  await page.locator('.workspace-composer__suggestion').first().click()
  const original = await askPost
  const firstSettled = await delayedResponses.firstSettled
  expect(firstSettled.status).toBe(200)
  expect(firstSettled.body.requestId).toBe(original.requestId)
  await expect(page.getByTestId('conversation-pending')).toBeVisible()

  // 25 秒网络等待上限只是服务端 20 秒结算后的兜底；超时不伪装成用户取消。
  await expect(page.locator('[data-failure-category="TIMEOUT"]')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByTestId('retry-turn')).toBeVisible()
  await delayedResponses.stop()

  // 原请求已在后端结算；同 requestId 重试必须取得该终局 replay。
  const retryPost = nextTurnPost(page)
  const retryResponse = nextTurnResponse(page)
  await page.getByTestId('retry-turn').click()
  const retried = await retryPost
  expect(retried.requestId).toBe(original.requestId)
  const replayHttp = await retryResponse
  expect(replayHttp.status()).toBe(200)
  const replay = await replayHttp.json() as Record<string, any>
  expect(replay.requestId).toBe(firstSettled.body.requestId)
  expect(replay.kind).toBe(firstSettled.body.kind)
  expect(replay.answer).toEqual(firstSettled.body.answer)
  expect(replay.conversation?.conversationId).toBe(
    (firstSettled.body.conversation as { conversationId?: string } | undefined)?.conversationId,
  )
  await expect(page.locator('[data-turn-kind="ANSWER"]').last()).toBeVisible()
  await expect(page.getByTestId('turn-failure')).toHaveCount(0)
})

test('真实来源限流返回双通道 429，前端显示限流恢复', async ({ page, request, isMobile }) => {
  test.skip(process.env.PLAYWRIGHT_ADMISSION !== '1', '仅在低 RPM 隔离 admission lane 执行')
  test.skip(isMobile, '限流用例消耗共享的匿名来源 RPM 窗口，移动端复跑由窗口重置保证，重复价值低')
  test.setTimeout(150_000)

  // 用真实 PRESET 请求逼近来源限流；Retry-After 头与 JSON envelope 必须来自同一次计算。
  const preset = await firstPublicPreset(request)
  const evidence = await exhaustSourceRateLimit(request, preset)
  expect(evidence.status).toBe(429)
  expect(evidence.body.error?.code).toBe('RATE_LIMITED')
  expect(evidence.body.error?.retryable).toBe(true)
  expect(String(evidence.body.error?.retryAfterSeconds)).toBe(evidence.retryAfterHeader)

  // 页面内同一路径的提问得到同样的 429：类别投影 + 剩余秒数 + 重试入口。
  await page.goto('/agent')
  const limited = nextTurnResponse(page)
  await page.locator('.workspace-composer__suggestion').first().click()
  expect((await limited).status()).toBe(429)
  await expect(page.locator('[data-failure-category="RATE_LIMITED"]')).toBeVisible()
  await expect(page.getByTestId('turn-failure')).toContainText(/约 \d+ 秒后可重试/)
  await expect(page.getByTestId('retry-turn')).toBeVisible()

  // 固定 RPM 窗口过期后再结束，避免毒化同一服务上的下一个测试项目。
  await awaitFreshRateWindow(request, preset)
})
