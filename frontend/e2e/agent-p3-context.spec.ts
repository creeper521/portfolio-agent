import { expect, test, type Page } from '@playwright/test'

import { installP3Mocks } from './support/publicApiMocks'

const RESUME_KEY = 'portfolio.agent.resume-token.v1'
const QUESTION =
  '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'

// 工作区在公开内容加载后才挂载；显式等待输入区就绪，避免冷启动时序导致的 flake。
const READY_TIMEOUT = 20_000

test.beforeEach(async ({ page }) => {
  // 仅在每个页签上下文首次初始化时清理存储；reload 时保留 sessionStorage 中的
  // ResumeToken，以便验证刷新恢复（handoff §10/§11）。
  await page.addInitScript(() => {
    const guard = '__p3_e2e_init__'
    if (sessionStorage.getItem(guard) === '1') return
    localStorage.clear()
    sessionStorage.clear()
    sessionStorage.setItem(guard, '1')
  })
})

async function waitForAgentReady(page: Page) {
  await expect(page.getByLabel('你的问题')).toBeVisible({ timeout: READY_TIMEOUT })
}

async function openAgent(page: Page) {
  await installP3Mocks(page)
  await page.goto('/agent')
  await expect(page).toHaveURL(/\/agent$/)
  await waitForAgentReady(page)
}

async function ask(page: Page, question: string) {
  await page.getByLabel('你的问题').fill(question)
  await page.getByRole('button', { name: /发送/ }).click()
}

// P3 公开来源引用（handoff §8/§17.19）。执行快照由 ExecutionSnapshot 组件单测覆盖。
test('renders public source references instead of legacy evidence-id citations', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await expect(page.locator('[data-source-reference]').first()).toBeVisible({ timeout: READY_TIMEOUT })
  // 有公开来源引用时不再渲染旧 evidenceId 引用按钮（过渡双读：P3 优先）。
  await expect(page.locator('[data-section-citation]')).toHaveCount(0)
})

// ResumeToken 只存在于活跃会话的 sessionStorage 与请求 Header（handoff §10）。
test('stores the resume token only in sessionStorage, never in localStorage', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await expect(page.locator('[data-source-reference]').first()).toBeVisible({ timeout: READY_TIMEOUT })

  const token = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(token).toBeTruthy()
  const localDump = await page.evaluate(() => JSON.stringify({ ...localStorage }))
  expect(localDump).not.toContain(token)
})

// 刷新只恢复安全 Context Summary，不恢复聊天历史（handoff §11/§17.10）。
test('recovers only the safe context summary on reload, not the chat history', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await expect(page.locator('.structured-answer')).toBeVisible({ timeout: READY_TIMEOUT })

  await page.reload()
  await waitForAgentReady(page)

  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: READY_TIMEOUT })
  await expect(page.locator('[data-recovery-card]')).toContainText('SQL 审计')
  // 问题/答案气泡不恢复。
  await expect(page.locator('.structured-answer')).toHaveCount(0)
})

// 主动清除：DELETE 204 后移除恢复卡与本地 Token（handoff §12/§17.12）。
test('clears the conversation context via DELETE and removes the recovery card', async ({ page }) => {
  await openAgent(page)
  await ask(page, QUESTION)
  await page.reload()
  await waitForAgentReady(page)
  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: READY_TIMEOUT })

  await page.locator('[data-clear-conversation]').click()

  await expect(page.locator('[data-recovery-card]')).toHaveCount(0)
  const token = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(token).toBeNull()
})

// DELETE 失败时不宣称已清除（handoff §12/§17.13）。
test('does not claim the conversation was cleared when DELETE fails', async ({ page }) => {
  await installP3Mocks(page, { clearFails: true })
  await page.goto('/agent')
  await waitForAgentReady(page)
  await ask(page, QUESTION)
  await page.reload()
  await waitForAgentReady(page)
  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: READY_TIMEOUT })

  await page.locator('[data-clear-conversation]').click()

  await expect(page.locator('[data-continuation-notice]')).toContainText('清除尚未在服务端确认')
  const token = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(token).toBeTruthy()
})

// 过期 Token：恢复接口返回 CONTEXT_EXPIRED 时清除本地并开始新会话（handoff §11/§17.11）。
test('starts a fresh session when the resumed context is expired', async ({ page }) => {
  await installP3Mocks(page, { contextStatus: 'CONTEXT_EXPIRED' })
  await page.addInitScript((key) => sessionStorage.setItem(key, 'opaque-expired-e2e'), RESUME_KEY)
  await page.goto('/agent')
  await waitForAgentReady(page)

  await expect(page.locator('[data-recovery-card]')).toHaveCount(0)
  const token = await page.evaluate((key) => sessionStorage.getItem(key), RESUME_KEY)
  expect(token).toBeNull()
})
