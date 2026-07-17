import { expect, test, type Page } from '@playwright/test'

import { installPublicApiMocks } from './support/publicApiMocks'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const initializedKey = 'portfolio.playwright.initialized'
    if (sessionStorage.getItem(initializedKey) !== '1') {
      localStorage.clear()
      sessionStorage.setItem(initializedKey, '1')
    }
  })
  if (process.env.PLAYWRIGHT_REAL_API !== '1') {
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
  await expect(page.locator('[data-explore-entry]')).toHaveCount(4)

  await page.locator('[data-role="MENTOR"]').click()
  await expect(page.locator('[data-role="MENTOR"]')).toHaveCSS(
    'background-color',
    'rgb(32, 28, 23)',
  )
  await expect(page.getByText('你如何复盘这个项目？')).toBeVisible()
  const answerResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/answers' &&
      response.request().method() === 'POST',
  )
  await page.getByText('你如何复盘这个项目？').click()
  expect((await answerResponse).ok()).toBe(true)
  await expect(page.locator('[data-light-answer]')).toBeVisible()
  await expect(page.locator('[data-answer-action]')).toHaveCount(3)
  await page.getByRole('link', { name: /带着上下文进入 Agent/ }).click()

  await expect(page).toHaveURL(/\/agent\?.*source=HOME/)
  await expect(page.locator('.message--user')).toContainText('你如何复盘这个项目？')
  await expect(page.locator('.message--agent')).toContainText(
    '当前版本只稳定支持项目完整介绍问题',
  )
  await expect(page.getByLabel('你的问题')).toHaveValue('')
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

  await page.getByRole('link', { name: '查看关联项目 →' }).click()
  await expect(page).toHaveURL(/\/projects\/sql-audit$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('SQL 审计与故障排查工具')

  await page.getByRole('link', { name: '查看成长时间线' }).click()
  await page.getByRole('link', { name: '查看关联证据 →' }).click()
  await expect(page).toHaveURL(/\/evidence\?evidence=sql-audit-delivery-set/)
  await expect(page.getByRole('heading', { name: 'SQL 审计工具交付证据集' })).toBeVisible()
})

test('local Agent conversation survives reload and can be cleared', async ({ page }) => {
  await openAgentDeepLink(page)

  const question =
    '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'
  await page.getByLabel('你的问题').fill(question)
  const answerResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/answers' &&
      response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: /发送/ }).click()
  expect((await answerResponse).ok()).toBe(true)
  await expect(page.getByText(/逐项验证时间排序/)).toBeVisible()

  await page.reload()
  await expect(page.getByText(question)).toBeVisible()

  if (await page.getByRole('button', { name: '会话', exact: true }).isVisible()) {
    await page.getByRole('button', { name: '会话', exact: true }).click()
  }
  await page.getByRole('button', { name: '清除本地记录' }).click()
  await expect(page.locator('.message--user')).toHaveCount(0)
  await expect(page.getByText('从一个可核验的问题开始。')).toBeVisible()
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

test('responsive Agent uses evidence and session drawers without horizontal overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1219, height: 900 })
  await openAgentDeepLink(page)
  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'true')
  await page.getByRole('button', { name: '证据', exact: true }).click()
  await expect(page.locator('.agent-workspace')).toHaveClass(/evidence-open/)
  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'false')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)

  await page.getByRole('button', { name: '关闭侧栏' }).click()
  await page.setViewportSize({ width: 980, height: 800 })
  await expect(page.locator('#local-session-rail')).toHaveAttribute('aria-hidden', 'true')
  await page.getByRole('button', { name: '会话', exact: true }).click()
  await expect(page.locator('.agent-workspace')).toHaveClass(/sessions-open/)
  await expect(page.locator('#local-session-rail')).toHaveAttribute('aria-hidden', 'false')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
