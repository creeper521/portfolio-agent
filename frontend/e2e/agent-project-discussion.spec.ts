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
