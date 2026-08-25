import { expect, test } from '@playwright/test'
import { defaultModelSelection } from './support/agent-recovery'

const TURNS = '/api/agent/turns'

// A2-78/92/94：happy path 不得只断言 HTTP 200。每条真实 Provider 轮次都解析
// PublicAgentTurn 终局，拒绝 CAPABILITY_UNAVAILABLE/BOUNDARY 混入成功路径，
// 并对回答内容做最低完整性检查（推荐项数量、非 NONE 覆盖、非空 section）。

interface TurnBody {
  kind: string
  code?: string
  answer?: {
    resolution: string
    goalResults: Array<{
      coverage: string
      presentation?: {
        kind: string
        items?: unknown[]
        sections?: unknown[]
      }
    }>
  }
}

async function turnBody(pending: Promise<Response>): Promise<TurnBody> {
  const response = await pending
  expect(response.status()).toBe(200)
  return await response.json() as TurnBody
}

async function expectAnswer(pending: Promise<Response>): Promise<TurnBody> {
  const body = await turnBody(pending)
  expect(
    body.kind,
    `期望 ANSWER 终局，实际 ${body.kind}${body.code === undefined ? '' : ':' + body.code}`,
  ).toBe('ANSWER')
  return body
}

function expectRecommendationItems(body: TurnBody, expected: number): void {
  const presentation = body.answer?.goalResults
    .find((goal) => goal.presentation?.kind === 'RECOMMENDATION')?.presentation
  expect(presentation?.items?.length, '推荐 presentation 项数').toBe(expected)
}

function expectNonEmptyAnswer(body: TurnBody): void {
  const goals = body.answer?.goalResults ?? []
  expect(goals.length, 'goalResults 不得为空').toBeGreaterThan(0)
  for (const goal of goals) {
    expect(goal.coverage, '回答 coverage 不得为 NONE').not.toBe('NONE')
    if (goal.presentation?.kind === 'SECTIONED') {
      expect(goal.presentation.sections?.length ?? 0, 'SECTIONED 回答必须包含非空 section').toBeGreaterThan(0)
    }
  }
}

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
  expectRecommendationItems(await expectAnswer(recommendation), 2)
  await expect(page.getByTestId('recommendation-item')).toHaveCount(2)

  await page.getByTestId('question-input').fill('继续第二个')
  const routed = nextTurn(page)
  await page.getByTestId('submit-question').click()
  // 受限候选切换允许 CLARIFICATION 终局；错误终局（CAPABILITY_UNAVAILABLE 等）必须失败。
  const routedBody = await turnBody(routed)
  expect(['ANSWER', 'CLARIFICATION']).toContain(routedBody.kind)

  if (await page.getByTestId('clarification-form').isVisible()) {
    const choices = page.locator('[data-choice-id] input')
    expect(await choices.count()).toBe(2)
    await choices.nth(1).check()
    const resolved = nextTurn(page)
    await page.locator('[data-clarification-submit]').click()
    expectNonEmptyAnswer(await expectAnswer(resolved))
  } else {
    expectNonEmptyAnswer(routedBody)
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
  const exitedBody = await turnBody(exited)
  expect(exitedBody.kind, '退出讨论必须是 CONVERSATIONAL 终局').toBe('CONVERSATIONAL')
  await expect(page.getByTestId('active-discussion')).toHaveCount(0)
})

test('card entry, historical switch, locked concept route and direct ASK override keep one backend subject', async ({ page, request }) => {
  test.setTimeout(120_000)
  await page.goto('/agent')
  await page.getByTestId('question-input').fill('推荐两个公开项目')
  let response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expectRecommendationItems(await expectAnswer(response), 2)
  const items = page.getByTestId('recommendation-item')
  await expect(items).toHaveCount(2)
  const firstLabel = (await items.nth(0).locator('a').first().textContent())?.trim() ?? ''
  const secondLabel = (await items.nth(1).locator('a').first().textContent())?.trim() ?? ''

  response = nextTurn(page)
  await items.nth(1).locator('button').click()
  expectNonEmptyAnswer(await expectAnswer(response))
  await expect(page.getByTestId('active-discussion')).toContainText(secondLabel)

  response = nextTurn(page)
  await items.nth(0).locator('button').click()
  expectNonEmptyAnswer(await expectAnswer(response))
  await expect(page.getByTestId('active-discussion')).toContainText(firstLabel)

  await page.getByTestId('question-input').fill('解释这个项目中的幂等设计')
  response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expectNonEmptyAnswer(await expectAnswer(response))
  await expect(page.getByTestId('active-discussion')).toContainText(firstLabel)

  const token = await page.evaluate(() =>
    sessionStorage.getItem('portfolio.agent.resume-token.v1'))
  expect(token).not.toBeNull()
  // 直连自由文本续问必须显式携带目录默认选择（A7），与页面内行为一致。
  const direct = await request.post(TURNS, {
    headers: { Authorization: `Bearer ${token ?? ''}` },
    data: {
      requestId: crypto.randomUUID(),
      modelSelection: await defaultModelSelection(request),
      command: {
        kind: 'ASK',
        input: { kind: 'FREE_TEXT', text: '继续说明验证方式' },
      },
      conversationWindow: [],
    },
  })
  expect(direct.status()).toBe(200)
  const directBody = await direct.json() as TurnBody & {
    conversation: {
      activeDiscussion: { subject: { label: string } }
    }
  }
  // 受限讨论内续问允许 CLARIFICATION；错误终局（CAPABILITY_UNAVAILABLE 等）必须失败。
  expect(['ANSWER', 'CLARIFICATION']).toContain(directBody.kind)
  expect(directBody.conversation.activeDiscussion.subject.label)
    .toContain(firstLabel)

  response = nextTurn(page)
  await page.getByTestId('exit-discussion').click()
  const exitBody = await turnBody(response)
  expect(exitBody.kind, '退出讨论必须是 CONVERSATIONAL 终局').toBe('CONVERSATIONAL')
  await expect(page.getByTestId('active-discussion')).toHaveCount(0)
})

test('single recommendation requires an explicit AI enter route and never backend auto-enters NEEDS_CLARIFICATION', async ({ page }) => {
  test.setTimeout(90_000)
  await page.goto('/agent')
  await page.getByTestId('question-input').fill('推荐一个公开项目')
  let response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  expectRecommendationItems(await expectAnswer(response), 1)
  await expect(page.getByTestId('recommendation-item')).toHaveCount(1)

  await page.getByTestId('question-input').fill('继续这个项目')
  response = nextTurn(page)
  await page.getByTestId('submit-question').click()
  // A2-24：单候选讨论进入必须直接 ANSWER，不得再出现必填澄清。
  expectNonEmptyAnswer(await expectAnswer(response))
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'ACTIVE')
  await expect(page.getByTestId('clarification-form')).toHaveCount(0)
})
