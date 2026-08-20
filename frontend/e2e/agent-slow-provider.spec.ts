import { expect, test, type APIRequestContext } from '@playwright/test'
import { nextTurnPost } from './support/agent-recovery'

interface FixtureStatus {
  readonly state: 'READY' | 'ACTIVE' | 'CLOSED'
  readonly ready: boolean
  readonly active: boolean
  readonly closed: boolean
  readonly providerRequestCount: number
}

function fixtureStatusUrl(): string {
  const raw = process.env.PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL
  expect(raw, 'BODY_STALL lane must expose its loopback-only coordination URL').toBeTruthy()
  const url = new URL(raw ?? '')
  expect(url.protocol).toBe('http:')
  expect(url.hostname).toBe('127.0.0.1')
  expect(url.pathname).toBe('/status')
  expect(url.username).toBe('')
  expect(url.password).toBe('')
  expect(url.search).toBe('')
  expect(url.hash).toBe('')
  return url.toString()
}

async function readFixtureStatus(
  request: APIRequestContext,
  statusUrl: string,
): Promise<FixtureStatus | undefined> {
  try {
    const response = await request.get(statusUrl, { timeout: 1_000 })
    if (response.status() !== 200) return undefined
    return await response.json() as FixtureStatus
  } catch {
    return undefined
  }
}

test('packaged body-stall Provider closes on exact cancellation without a late turn', async ({ page, request }) => {
  test.skip(process.env.PLAYWRIGHT_SLOW_PROVIDER !== '1', '仅在 packaged BODY_STALL lane 执行')
  test.setTimeout(45_000)
  const statusUrl = fixtureStatusUrl()

  await expect.poll(async () => {
    const status = await readFixtureStatus(request, statusUrl)
    return status?.ready === true ? `${status.state}:${status.providerRequestCount}` : 'UNAVAILABLE'
  }, { timeout: 5_000 }).toBe('READY:0')

  await page.goto('/agent')
  await page.getByTestId('question-input').fill('解释幂等请求为什么需要稳定的请求标识')
  const posted = nextTurnPost(page)
  await page.getByTestId('submit-question').click()
  const { requestId } = await posted
  await expect(page.getByTestId('conversation-pending')).toBeVisible()

  await expect.poll(async () => {
    const status = await readFixtureStatus(request, statusUrl)
    return status === undefined ? 'UNAVAILABLE' : `${status.state}:${status.providerRequestCount}`
  }, { timeout: 10_000, intervals: [100, 250, 500] }).toBe('ACTIVE:1')

  const deleteResponse = page.waitForResponse((response) =>
    response.request().method() === 'DELETE'
      && new URL(response.url()).pathname === `/api/agent/turns/${requestId}`)
  await page.getByTestId('cancel-turn').click()
  expect((await deleteResponse).status()).toBe(204)
  await expect(page.getByTestId('conversation-pending')).toHaveCount(0)
  await expect(page.getByTestId('turn-failure')).toHaveCount(0)

  await expect.poll(async () => {
    const status = await readFixtureStatus(request, statusUrl)
    return status === undefined ? 'UNAVAILABLE' : `${status.state}:${status.providerRequestCount}`
  }, { timeout: 10_000, intervals: [100, 250, 500] }).toBe('CLOSED:1')

  // Keep observing after disconnect: cancellation must not settle a late public turn
  // or silently start a second Provider execution.
  await page.waitForTimeout(1_500)
  const finalStatus = await readFixtureStatus(request, statusUrl)
  expect(finalStatus).toMatchObject({
    state: 'CLOSED',
    active: true,
    closed: true,
    providerRequestCount: 1,
  })
  await expect(page.locator('[data-turn-kind]')).toHaveCount(0)
  await expect(page.getByTestId('answer-turn')).toHaveCount(0)
  await expect(page.getByTestId('clarification-turn')).toHaveCount(0)
  await expect(page.getByTestId('conversational-turn')).toHaveCount(0)
})
