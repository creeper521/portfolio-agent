import { expect, test, type Page } from '@playwright/test'

import {
  installAnswerScenarioMock,
  installGuidedAnswerMock,
  installPublicApiMocks,
} from './support/publicApiMocks'

const ALIAS_QUESTION = '详细介绍一下 SQL 审计项目'
const LONG_TITLE_QUESTION =
  '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const initializedKey = 'portfolio.playwright.initialized'
    if (sessionStorage.getItem(initializedKey) !== '1') {
      localStorage.clear()
      sessionStorage.setItem(initializedKey, '1')
    }
  })
  await installPublicApiMocks(page)
})

async function openAgent(page: Page) {
  await page.goto('/agent')
  await expect(page).toHaveURL(/\/agent$/)
}

async function ask(page: Page, question: string) {
  await page.getByLabel('你的问题').fill(question)
  await page.getByRole('button', { name: /发送/ }).click()
}

test('每个回答展示自己的三个引导问题并透传 coveredTopics', async ({ page }) => {
  const requests: Record<string, unknown>[] = []
  await installGuidedAnswerMock(
    page,
    [
      {
        coveredTopics: ['BACKGROUND'],
        guidanceStage: 'OPENING',
        suggestedQuestions: [
          { text: '项目的核心取舍是什么？', projectSlug: 'sql-audit' },
          { text: '验证链路怎么设计的？', projectSlug: 'sql-audit' },
          { text: '这个项目最大的失败是什么？', projectSlug: 'sql-audit' },
        ],
      },
      {
        coveredTopics: ['BACKGROUND', 'SOLUTION'],
        guidanceStage: 'DEEPENING',
        suggestedQuestions: [
          { text: '继续深挖当前项目', projectSlug: 'sql-audit' },
          { text: '继续深挖验证过程', projectSlug: 'sql-audit' },
          { text: '看看代码图谱评测项目', projectSlug: 'codegraph-evaluation' },
        ],
      },
    ],
    (body) => {
      requests.push(body)
    },
  )
  await openAgent(page)
  await ask(page, ALIAS_QUESTION)

  const firstAnswer = page.locator('.message--agent').first()
  await expect(firstAnswer.locator('[data-suggested-follow-up]')).toHaveCount(3)

  await firstAnswer.locator('[data-suggested-follow-up]').first().click()
  const secondAnswer = page.locator('.message--agent').nth(1)
  await expect(secondAnswer.locator('[data-suggested-follow-up]')).toHaveCount(3)
  await expect(secondAnswer.locator('[data-suggested-follow-up]').nth(2))
    .toHaveText('看看代码图谱评测项目')

  expect(requests).toHaveLength(2)
  expect(requests[1]?.question).toBe('项目的核心取舍是什么？')
  const secondContext = requests[1]?.context as {
    projectSlug?: string
    coveredTopics?: string[]
  }
  expect(secondContext.projectSlug).toBe('sql-audit')
  expect(secondContext.coveredTopics).toEqual(['BACKGROUND'])

  await secondAnswer.locator('[data-suggested-follow-up]').nth(2).click()
  await expect(page.locator('.message--agent')).toHaveCount(3)
  expect(requests).toHaveLength(3)
  const thirdContext = requests[2]?.context as {
    projectSlug?: string
    coveredTopics?: string[]
  }
  expect(thirdContext.projectSlug).toBe('codegraph-evaluation')
  expect(thirdContext.coveredTopics).toEqual(['BACKGROUND', 'SOLUTION'])
})

test('降级回答保持可交互并展示自己的引导问题', async ({ page }) => {
  await installGuidedAnswerMock(page, [
    {
      generationMode: 'FALLBACK',
      degraded: true,
      suggestedQuestions: [
        { text: '降级后继续追问一', projectSlug: 'sql-audit' },
        { text: '降级后继续追问二', projectSlug: 'sql-audit' },
        { text: '降级后继续追问三', projectSlug: 'sql-audit' },
      ],
    },
  ])
  await openAgent(page)
  await ask(page, ALIAS_QUESTION)

  await expect(page.locator('[data-degraded-notice]')).toBeVisible()
  await expect(page.locator('[role="alert"]')).toHaveCount(0)
  await expect(page.locator('[data-suggested-follow-up]')).toHaveCount(3)

  await page.locator('[data-suggested-follow-up]').first().click()
  await expect(page.locator('.message--user')).toHaveCount(2)
  await expect(page.locator('.message--agent')).toHaveCount(2)
  await expect(page.locator('[role="alert"]')).toHaveCount(0)
})

test('网络失败保留重试并提供三个本地公开问题继续对话', async ({ page }) => {
  await installAnswerScenarioMock(page, { networkFailure: true })
  await openAgent(page)
  await ask(page, ALIAS_QUESTION)

  await expect(page.locator('[data-answer-retry]')).toBeVisible()
  const failureSuggestions = page.locator('[data-failure-suggestion]')
  await expect(failureSuggestions).toHaveCount(3)

  await installGuidedAnswerMock(page, [
    {
      suggestedQuestions: [
        { text: '恢复后的追问一', projectSlug: 'sql-audit' },
        { text: '恢复后的追问二', projectSlug: 'sql-audit' },
        { text: '恢复后的追问三', projectSlug: 'sql-audit' },
      ],
    },
  ])
  const firstSuggestion = (await failureSuggestions.first().textContent())?.trim() ?? ''
  await failureSuggestions.first().click()
  await expect(page.locator('.message--user')).toHaveCount(2)
  await expect(page.locator('.message--user').nth(1)).toContainText(firstSuggestion)
  await expect(page.locator('.message--agent')).toHaveCount(1)
  await expect(page.locator('[data-suggested-follow-up]')).toHaveCount(3)
})

test('长会话标题保持完整并只做视觉省略', async ({ page, isMobile }) => {
  await installGuidedAnswerMock(page, [])
  await openAgent(page)
  await ask(page, LONG_TITLE_QUESTION)
  await expect(page.locator('.message--agent')).toHaveCount(1)

  const mainTitle = page.locator('.conversation__head h1')
  await expect(mainTitle).toHaveText(LONG_TITLE_QUESTION)
  await expect(mainTitle).toHaveAttribute('title', LONG_TITLE_QUESTION)
  await expect(mainTitle).toHaveAttribute('aria-label', LONG_TITLE_QUESTION)
  await expect(mainTitle).toHaveCSS('-webkit-line-clamp', '2')

  if (isMobile) {
    await page.getByRole('button', { name: '会话', exact: true }).click()
  }
  const railTitle = page.locator('.session-select').first()
  await expect(railTitle).toHaveAttribute('title', LONG_TITLE_QUESTION)
  await expect(railTitle).toHaveCSS('white-space', 'nowrap')
  await expect(railTitle).toHaveCSS('text-overflow', 'ellipsis')
})
