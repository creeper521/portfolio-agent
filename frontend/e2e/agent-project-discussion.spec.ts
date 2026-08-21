import { expect, test } from '@playwright/test'

const TURNS = '/api/agent/turns'

async function nextTurn(page: import('@playwright/test').Page) {
  return page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS
      && response.request().method() === 'POST')
}

test('recommendation omission enters bounded project discussion and refresh restores focus', async ({ page }) => {
  test.setTimeout(90_000)
  await page.goto('/agent')
  await expect(page.getByTestId('question-input')).toBeEnabled()

  await page.getByTestId('question-input').fill('推荐两个公开项目')
  const recommendation = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expect((await recommendation).status()).toBe(200)
  await expect(page.getByTestId('recommendation-item')).toHaveCount(2)

  await page.getByTestId('question-input').fill('继续第二个')
  const routed = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expect((await routed).status()).toBe(200)

  if (await page.getByTestId('clarification-form').isVisible()) {
    const choices = page.locator('[data-choice-id] input')
    expect(await choices.count()).toBe(2)
    await choices.nth(1).check()
    const resolved = nextTurn(page)
    await page.locator('[data-clarification-submit]').click()
    expect((await resolved).status()).toBe(200)
  }

  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'ACTIVE')
  const subject = await page.getByTestId('active-discussion').locator('p').first().textContent()

  await page.reload()
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'ACTIVE')
  await expect(page.getByTestId('active-discussion').locator('p').first())
    .toHaveText(subject ?? '')

  const browserState = await page.evaluate(() => ({
    url: location.href,
    localKeys: Object.keys(localStorage),
    localValues: Object.values(localStorage),
    historyState: history.state,
  }))
  const persisted = JSON.stringify(browserState)
  expect(persisted).not.toMatch(/contextHandle|resultItemId|推荐两个公开项目|继续第二个/)

  const exited = nextTurn(page)
  await page.getByTestId('exit-discussion').click()
  expect((await exited).status()).toBe(200)
  await expect(page.getByTestId('active-discussion')).toHaveCount(0)
})

test('card entry, historical switch, locked concept route and direct ASK override keep one backend subject', async ({ page, request }) => {
  test.setTimeout(120_000)
  await page.goto('/agent')
  await page.getByTestId('question-input').fill('推荐两个公开项目')
  let response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expect((await response).status()).toBe(200)
  const items = page.getByTestId('recommendation-item')
  await expect(items).toHaveCount(2)
  const firstLabel = (await items.nth(0).locator('a').first().textContent())?.trim() ?? ''
  const secondLabel = (await items.nth(1).locator('a').first().textContent())?.trim() ?? ''

  response = nextTurn(page)
  await items.nth(1).locator('button').click()
  expect((await response).status()).toBe(200)
  await expect(page.getByTestId('active-discussion')).toContainText(secondLabel)

  response = nextTurn(page)
  await items.nth(0).locator('button').click()
  expect((await response).status()).toBe(200)
  await expect(page.getByTestId('active-discussion')).toContainText(firstLabel)

  await page.getByTestId('question-input').fill('解释这个项目中的幂等设计')
  response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expect((await response).status()).toBe(200)
  await expect(page.getByTestId('active-discussion')).toContainText(firstLabel)

  const token = await page.evaluate(() =>
    sessionStorage.getItem('portfolio.agent.resume-token.v1'))
  expect(token).not.toBeNull()
  const direct = await request.post(TURNS, {
    headers: { Authorization: `Bearer ${token ?? ''}` },
    data: {
      requestId: crypto.randomUUID(),
      command: {
        kind: 'ASK',
        input: { kind: 'FREE_TEXT', text: '继续说明验证方式' },
      },
      conversationWindow: [],
    },
  })
  expect(direct.status()).toBe(200)
  const directBody = await direct.json() as {
    conversation: {
      activeDiscussion: { subject: { label: string } }
    }
  }
  expect(directBody.conversation.activeDiscussion.subject.label)
    .toContain(firstLabel)

  response = nextTurn(page)
  await page.getByTestId('exit-discussion').click()
  expect((await response).status()).toBe(200)
  await expect(page.getByTestId('active-discussion')).toHaveCount(0)
})

test('single recommendation requires an explicit AI enter route and never backend auto-enters NEEDS_CLARIFICATION', async ({ page }) => {
  test.setTimeout(90_000)
  await page.goto('/agent')
  await page.getByTestId('question-input').fill('推荐一个公开项目')
  let response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expect((await response).status()).toBe(200)
  await expect(page.getByTestId('recommendation-item')).toHaveCount(1)

  await page.getByTestId('question-input').fill('继续这个项目')
  response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expect((await response).status()).toBe(200)
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'ACTIVE')
  await expect(page.getByTestId('clarification-form')).toHaveCount(0)
})
