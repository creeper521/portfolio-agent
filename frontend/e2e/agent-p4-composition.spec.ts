import { expect, test, type Page } from '@playwright/test'

import { installP4CompositionMocks, type P4CompositionScenario } from './support/publicApiMocks'

// P4 任务级 composition 前端契约验收（设计 §11/§14 / handoff §2/§6）。
// 后端 P4 公共契约尚未落地，此处用 Mock 验收三类表达来源，不代表真实 Provider 已验收。
// 配置默认在 Desktop Chrome 与 Pixel 7 两个 project 上各跑一遍，自动覆盖桌面/移动。

const QUESTION =
  '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'
const READY_TIMEOUT = 20_000

// 三种 composition 均使用同一套章节/引用/Evidence Desk（handoff §3）。
const FORBIDDEN_BADGE_PHRASES = ['AI 增强', '由模型生成', 'Provider', 'DeepSeek', 'GLM', '模型失败']

test.beforeEach(async ({ page }) => {
  // 每个页签上下文首次初始化时清理存储，避免 ResumeToken/会话残留干扰。
  await page.addInitScript(() => {
    const guard = '__p4_e2e_init__'
    if (sessionStorage.getItem(guard) === '1') return
    localStorage.clear()
    sessionStorage.clear()
    sessionStorage.setItem(guard, '1')
  })
})

async function openAgent(page: Page, scenario: P4CompositionScenario) {
  await installP4CompositionMocks(page, scenario)
  await page.goto('/agent')
  await expect(page).toHaveURL(/\/agent$/)
  await expect(page.getByLabel('你的问题')).toBeVisible({ timeout: READY_TIMEOUT })
}

async function ask(page: Page) {
  await page.getByLabel('你的问题').fill(QUESTION)
  await page.getByRole('button', { name: /发送/ }).click()
}

async function waitForAnswer(page: Page) {
  await expect(page.locator('.structured-answer')).toBeVisible({ timeout: READY_TIMEOUT })
}

test('MODEL_GROUNDED answer renders sections and citations like a deterministic answer', async ({ page }) => {
  await openAgent(page, 'MODEL_GROUNDED')
  await ask(page)
  await waitForAnswer(page)

  await expect(page.locator('[data-source-reference]').first())
    .toBeVisible({ timeout: READY_TIMEOUT })
  // 不展示 AI/Provider 徽标或失败原因文案。
  const text = await page.locator('.structured-answer').innerText()
  for (const phrase of FORBIDDEN_BADGE_PHRASES) {
    expect(text).not.toContain(phrase)
  }
})

test('FALLBACK answer renders as a success with no error UI and openable evidence', async ({ page }) => {
  await openAgent(page, 'FALLBACK')
  await ask(page)
  await waitForAnswer(page)

  // Fallback 仍是成功回答：不显示错误态、重试按钮或告警卡。
  await expect(page.locator('.answer-state--error')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '重新回答' })).toHaveCount(0)
  await expect(page.locator('[data-source-reference]').first())
    .toBeVisible({ timeout: READY_TIMEOUT })

  // Evidence 可达：每条公开来源都带证据链接（桌面与移动均渲染，不依赖
  // 仅在窄屏出现的 Evidence Desk 切换按钮）。
  await expect(page.locator('.source-reference__evidence').first())
    .toBeVisible({ timeout: READY_TIMEOUT })
})

test('MIXED answer renders every task section without losing content', async ({ page }) => {
  await openAgent(page, 'MIXED')
  await ask(page)
  await waitForAnswer(page)

  // 两个任务各产出一段章节，正文不丢。用 data-section-type 精确匹配答案章节，
  // 排除 ExecutionSnapshot 等同样使用 <section> 的相邻块。
  const sections = page.locator('.structured-answer [data-section-type]')
  await expect(sections).toHaveCount(2)
  const body = await page.locator('.structured-answer').innerText()
  expect(body).toContain('确定性正文')
  expect(body).toContain('模型受控表达正文')
  // 每段章节各带 1 条引用（同一 referenceKey 在不同章节各出现一次，不算重复）。
  await expect(page.locator('[data-source-reference]')).toHaveCount(2)
  // 不触发未知 enum 的错误态。
  await expect(page.locator('.answer-state--error')).toHaveCount(0)
})

test('execution snapshot stays on the four P3 stages with no fake model progress', async ({ page }) => {
  await openAgent(page, 'MIXED')
  await ask(page)
  await waitForAnswer(page)

  const snapshot = page.locator('[data-execution-snapshot]')
  await expect(snapshot).toBeVisible({ timeout: READY_TIMEOUT })
  // 体验闭环 §5：成功回答默认收起执行快照，先展开再校验阶段集合。
  await snapshot.locator('[data-execution-toggle]').click()
  const codes = await snapshot.locator('[data-stage-code]').evaluateAll((nodes) =>
    nodes.map((node) => (node as HTMLElement).dataset.stageCode ?? ''),
  )
  const allowed = new Set(['SCOPE_CONFIRMED', 'MATERIALS_RETRIEVED', 'EVIDENCE_VALIDATED', 'RESULT_COMPOSED'])
  for (const code of codes) {
    expect(allowed.has(code)).toBe(true)
  }
  // 必须出现全部四个阶段，且不新增“调用模型/验证模型”拟真阶段。
  expect(new Set(codes)).toEqual(allowed)
})

test('composition answers do not overflow horizontally on desktop or mobile', async ({ page }) => {
  await openAgent(page, 'MIXED')
  await ask(page)
  await waitForAnswer(page)

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  // 允许 1px 四舍五入误差；不产生横向滚动条。
  expect(overflow).toBeLessThanOrEqual(1)
})
