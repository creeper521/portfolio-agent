import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.clear())
})

test('home preserves the four-layer experience and hands a role question to Agent', async ({
  page,
}) => {
  await page.goto('/')

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
  await page.getByText('你如何复盘这个项目？').click()
  await expect(page.locator('[data-light-answer]')).toBeVisible()
  await expect(page.locator('[data-answer-action]')).toHaveCount(3)
  await page.getByRole('link', { name: /带着上下文进入 Agent/ }).click()

  await expect(page).toHaveURL(/\/agent\?.*source=HOME/)
  await expect(page.locator('.message--user')).toContainText('你如何复盘这个项目？')
  await expect(page.locator('.message--agent')).toContainText('边界明确')
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

test('local Agent conversation survives reload and can be cleared', async ({ page }) => {
  await page.goto('/agent')

  await page.getByLabel('你的问题').fill('你如何验证查询、进度和归档链路？')
  await page.getByRole('button', { name: /发送/ }).click()
  await expect(page.getByText(/验证覆盖时间排序/)).toBeVisible()

  await page.reload()
  await expect(page.getByText('你如何验证查询、进度和归档链路？')).toBeVisible()

  if (await page.getByRole('button', { name: '会话', exact: true }).isVisible()) {
    await page.getByRole('button', { name: '会话', exact: true }).click()
  }
  await page.getByRole('button', { name: '清除本地记录' }).click()
  await expect(page.locator('.message--user')).toHaveCount(0)
  await expect(page.getByText('从一个可核验的问题开始。')).toBeVisible()
})

test('workspace separators support keyboard adjustment and reset', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/agent')

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
  await page.goto('/agent')
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
