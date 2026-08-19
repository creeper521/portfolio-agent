import { expect, test } from '@playwright/test'

const TURNS = '/api/agent/turns'
const CURRENT = '/api/agent/conversations/current'

test('final API supports preset, replay, Bearer continuation and clear', async ({ request }) => {
  const contentResponse = await request.get('/api/v1/public-content')
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
  expect((await replay.json()).answer).toEqual(firstTurn.answer)

  const token = firstTurn.conversation.resumeToken as string
  const second = await request.post(TURNS, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      requestId: crypto.randomUUID(),
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: 'SQL 审计与故障排查工具' } },
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
  await page.getByTestId('question-input').fill('SQL 审计与故障排查工具')
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS && response.request().method() === 'POST')
  await page.getByTestId('submit-question').click()
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

test('cancel sends final DELETE resource and leaves no error turn', async ({ page }) => {
  await page.route('**/api/agent/turns', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1_500))
    await route.continue()
  })
  await page.goto('/agent')
  await page.getByTestId('question-input').fill('SQL 审计与故障排查工具')
  await page.getByTestId('submit-question').click()
  await expect(page.getByTestId('conversation-pending')).toBeVisible()
  const deleteRequest = page.waitForRequest((request) =>
    /\/api\/agent\/turns\/[0-9a-f-]+$/i.test(new URL(request.url()).pathname)
      && request.method() === 'DELETE')
  await page.getByTestId('cancel-turn').click()
  await deleteRequest
  await expect(page.getByTestId('conversation-pending')).toHaveCount(0)
  await expect(page.getByTestId('turn-failure')).toHaveCount(0)
})
