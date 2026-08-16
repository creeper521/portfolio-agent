import { expect, test } from '@playwright/test'

import {
  ask,
  clearCaseContext,
  clearConversation,
  installBehaviorUiApi,
  observe,
  openAgent,
  openCaseQuestion,
  requestFacts,
  waitForAnswer,
  waitForPost,
} from './agentBehaviorUiDriver'

const OVERVIEW = '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'
const UNKNOWN = '112233'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await installBehaviorUiApi(page)
})

test('project hint is visible and becomes a single project subject', async ({ page }) => {
  await openAgent(page, '?project=sql-audit')
  await expect(page.locator('[data-case-context]')).toHaveCount(0)
  const requestPromise = waitForPost(page)
  await ask(page, OVERVIEW)
  const facts = requestFacts(await requestPromise)
  expect(facts.projectSlug).toBe('sql-audit')
  expect(facts.caseSlug).toBeUndefined()
  expect(facts.activeSubjects).toEqual([{ subjectType: 'PROJECT', subjectId: 'sql-audit' }])
  await waitForAnswer(page)
})

test('case handoff keeps the case hint and never puts the question in the URL', async ({ page }) => {
  await openCaseQuestion(page)
  const observation = await observe(page)
  expect(observation.caseContextVisible).toBe(true)
  expect(observation.urlContainsQuestion).toBe(false)
  const requestPromise = waitForPost(page)
  await page.getByRole('button', { name: /发送/ }).click()
  const facts = requestFacts(await requestPromise)
  expect(facts.caseSlug).toBe('multilingual-image-preservation')
  expect(facts.projectSlug).toBeUndefined()
  expect(facts.source).toBe('CASE')
  expect(facts.activeSubjects).toEqual([
    { subjectType: 'CASE', subjectId: 'multilingual-image-preservation' },
  ])
  await waitForAnswer(page)
})

test('112233 is clarified as unknown input, without evidence or fabricated answer', async ({ page }) => {
  await openAgent(page)
  const requestPromise = waitForPost(page)
  await ask(page, UNKNOWN)
  const request = await requestPromise
  expect(request.postDataJSON()).not.toHaveProperty('questionPresetId')
  await waitForAnswer(page)
  const answer = page.locator('.message--agent').last()
  await expect(answer.locator('[data-source-reference]')).toHaveCount(0)
  await expect(answer.locator('[data-section-citation]')).toHaveCount(0)
  const observation = await observe(page)
  // 112233 is not a subject.  A boundary paragraph alone is not a safe
  // clarification because it leaves the visitor unsure what can be asked.
  expect(observation.clarificationCount).toBeGreaterThan(0)
})

test('explicitly clearing a case context prevents the next turn from retaining CASE identity', async ({ page }) => {
  await openCaseQuestion(page)
  await clearCaseContext(page)
  const requestPromise = waitForPost(page)
  await ask(page, UNKNOWN)
  const facts = requestFacts(await requestPromise)
  expect(facts.caseSlug).toBeUndefined()
  expect(facts.activeSubjects).toEqual([])
})

test('ambiguous continuation remains tied to one active subject and does not invent a second subject', async ({ page }) => {
  await openAgent(page, '?project=sql-audit')
  await ask(page, OVERVIEW)
  await waitForAnswer(page)
  for (const ambiguousInput of ['继续', '它', '第二个']) {
    const requestPromise = waitForPost(page)
    await ask(page, ambiguousInput)
    const facts = requestFacts(await requestPromise)
    expect(facts.activeSubjects).toEqual([{ subjectType: 'PROJECT', subjectId: 'sql-audit' }])
    expect(facts.activeSubjects).toHaveLength(1)
    await waitForAnswer(page)
  }
})

test('reload recovery and clear are page-local; a second tab does not inherit the first tab history', async ({ page, context }) => {
  await openAgent(page, '?project=sql-audit')
  await ask(page, OVERVIEW)
  await waitForAnswer(page)
  const secondTab = await context.newPage()
  await installBehaviorUiApi(secondTab)
  await openAgent(secondTab)
  expect((await observe(secondTab)).recoveryCardVisible).toBe(false)

  await page.reload()
  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: 20_000 })
  await expect(page.locator('.structured-answer')).toHaveCount(0)
  await clearConversation(page)
  expect((await observe(page)).recoveryCardVisible).toBe(false)
  expect((await observe(secondTab)).recoveryCardVisible).toBe(false)
  await secondTab.close()
})

test('cancelled late response cannot overwrite the newer turn', async ({ page }) => {
  let first = true
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST' || !first) {
      await route.fallback()
      return
    }
    first = false
    await new Promise((resolve) => setTimeout(resolve, 800))
    try {
      await route.fallback()
    } catch {
      // Browser cancellation is expected; the UI must still ignore this turn.
    }
  })
  await openAgent(page)
  await ask(page, OVERVIEW)
  await expect(page.locator('[data-answer-cancel]')).toBeVisible()
  await page.locator('[data-answer-cancel]').click()
  await ask(page, UNKNOWN)
  await waitForAnswer(page)
  await page.waitForTimeout(900)
  const observation = await observe(page)
  expect(observation.agentMessageCount).toBe(1)
  await expect(page.locator('.message--agent').last()).toContainText('当前版本只稳定支持')
})
