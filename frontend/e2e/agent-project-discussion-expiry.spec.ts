import { expect, test } from '@playwright/test'

const TURNS = '/api/agent/turns'
const CURRENT = '/api/agent/conversations/current'
const RESUME_STORAGE_KEY = 'portfolio.agent.resume-token.v1'

async function nextTurn(page: import('@playwright/test').Page) {
  return page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS
      && response.request().method() === 'POST')
}

async function waitForExpired(
  request: import('@playwright/test').APIRequestContext,
  resumeToken: string,
) {
  await expect.poll(async () => {
    const response = await request.get(CURRENT, {
      headers: { Authorization: `Bearer ${resumeToken}` },
    })
    if (response.status() !== 200) return `HTTP_${response.status()}`
    const body = await response.json() as {
      activeDiscussion?: { status?: string }
    }
    return body.activeDiscussion?.status ?? 'NONE'
  }, { timeout: 10_000, intervals: [200, 300, 500] }).toBe('EXPIRED')
}

test('short discussion TTL exposes EXPIRED actions while model-disabled deterministic recovery stays usable', async ({ page, request }) => {
  test.setTimeout(90_000)
  const portfolioResponse = await request.get('/api/portfolio')
  expect(portfolioResponse.status()).toBe(200)
  const portfolio = await portfolioResponse.json() as {
    questionPresets: Array<{ id: string; contractVersion: string }>
  }
  const preset = portfolio.questionPresets[0]
  expect(preset).toBeDefined()

  const first = await request.post(TURNS, {
    data: {
      requestId: crypto.randomUUID(),
      // 确定性 PRESET 与 REENTER 直连路径显式 NONE（A7），不依赖 Provider。
      modelSelection: { kind: 'NONE' },
      command: {
        kind: 'ASK',
        input: {
          kind: 'PRESET',
          presetId: preset?.id,
          presetRevision: preset?.contractVersion,
        },
      },
      conversationWindow: [],
    },
  })
  expect(first.status()).toBe(200)
  const firstBody = await first.json() as {
    conversation: {
      resumeToken: string
      discussionRevision: number
    }
  }
  expect(firstBody.conversation.discussionRevision).toBe(0)

  const entered = await request.post(TURNS, {
    headers: {
      Authorization: `Bearer ${firstBody.conversation.resumeToken}`,
    },
    data: {
      requestId: crypto.randomUUID(),
      modelSelection: { kind: 'NONE' },
      command: {
        kind: 'CONTINUE',
        operation: 'REENTER_SUBJECT',
        subject: {
          kind: 'PROJECT',
          reference: 'sql-audit-project',
        },
      },
      conversationWindow: [],
    },
  })
  expect(entered.status()).toBe(200)
  const enteredBody = await entered.json() as {
    conversation: {
      discussionRevision: number
      activeDiscussion: { status: string }
    }
  }
  expect(enteredBody.conversation).toMatchObject({
    discussionRevision: 1,
    activeDiscussion: { status: 'ACTIVE' },
  })

  await page.addInitScript(({ key, token }) => {
    sessionStorage.setItem(key, token)
  }, { key: RESUME_STORAGE_KEY, token: firstBody.conversation.resumeToken })
  await page.goto('/agent')
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'ACTIVE')
  await expect(page.getByTestId('discussion-expiry')).toContainText('剩余约')
  await expect(page.getByTestId('question-input')).toBeDisabled()
  await expect(page.getByTestId('exit-discussion')).toBeEnabled()

  await waitForExpired(request, firstBody.conversation.resumeToken)
  await page.reload()
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'EXPIRED')
  await expect(page.getByTestId('discussion-expiry')).toHaveText('已到期')
  await expect(page.getByTestId('reenter-discussion')).toBeEnabled()
  await expect(page.getByTestId('new-topic')).toBeEnabled()

  const reentered = nextTurn(page)
  await page.getByTestId('reenter-discussion').click()
  expect((await reentered).status()).toBe(200)
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'ACTIVE')

  await waitForExpired(request, firstBody.conversation.resumeToken)
  await page.reload()
  await expect(page.getByTestId('active-discussion'))
    .toHaveAttribute('data-discussion-status', 'EXPIRED')
  const newTopic = nextTurn(page)
  await page.getByTestId('new-topic').click()
  expect((await newTopic).status()).toBe(200)
  await expect(page.getByTestId('active-discussion')).toHaveCount(0)
})
