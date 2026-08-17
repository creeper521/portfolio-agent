import { expect, test, type Page } from '@playwright/test'

import {
  installExperienceClosureMocks,
  type ClosureRequestLogEntry,
} from './support/publicApiMocks'

// 体验闭环验收（2026-08-17 交接规格 §11 场景 A–F）。
// 后端确定性路由闭环字段尚未上线：全部场景由 installExperienceClosureMocks
// 按冻结契约出合同 fixture（含 1/3 部分推荐与 contextReference 句柄）。

const READY_TIMEOUT = 20_000

async function openAgent(
  page: Page,
  onRequest?: (entry: ClosureRequestLogEntry) => void,
) {
  await page.addInitScript(() => {
    const guard = 'portfolio.playwright.initialized'
    if (sessionStorage.getItem(guard) !== '1') {
      localStorage.clear()
      sessionStorage.setItem(guard, '1')
    }
  })
  await installExperienceClosureMocks(page, { onRequest })
  await page.goto('/agent')
  await page.getByLabel('你的问题').waitFor({ state: 'visible', timeout: READY_TIMEOUT })
}

async function ask(page: Page, question: string) {
  await page.getByLabel('你的问题').fill(question)
  await page.getByRole('button', { name: /发送/ }).click()
  await page.locator('.message--agent').last().waitFor({ timeout: READY_TIMEOUT })
  await page.waitForTimeout(300)
}

// 场景 A：噪声输入
test('场景 A · 输入「1」只显示澄清，不出现回答、执行完成与证据状态', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await openAgent(page)
  await ask(page, '1')

  await expect(page.getByTestId('turn-clarification')).toBeVisible()
  await expect(page.getByTestId('turn-clarification')).toContainText('想了解什么')
  // 三类安全入口
  const entries = page.locator('[data-safe-entry]')
  await expect(entries).toHaveCount(3)
  await expect(entries.nth(2)).toHaveText('推荐项目')
  // 无答案块、无验证/范围标签、无执行快照、无来源摘要
  await expect(page.locator('.answer-block')).toHaveCount(0)
  await expect(page.locator('.message__meta-tag')).toHaveCount(0)
  await expect(page.locator('[data-execution-snapshot]')).toHaveCount(0)
  await expect(page.locator('[data-answer-sources]')).toHaveCount(0)
  // 噪声输入不成为会话标题
  await expect(page.locator('.session-select').first()).toHaveText('待补充问题')
})

// 场景 A 附加：安全入口直接提交安全通用问题
test('场景 A · 安全入口直接发出通用推荐问题', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  const requests: ClosureRequestLogEntry[] = []
  await openAgent(page, (entry) => requests.push(entry))
  await ask(page, '1')

  await page.locator('[data-safe-entry="recommend"]').click()
  await page.locator('[data-portfolio-recommendation]').waitFor({ timeout: READY_TIMEOUT })
  expect(requests.some((entry) => entry.question === '给我推荐两个项目')).toBe(true)
})

// 场景 B：两项目推荐
test('场景 B · 「给我推荐两个项目」显示两个不同公开项目', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await openAgent(page)
  await ask(page, '给我推荐两个项目')

  const cards = page.locator('[data-recommendation-item]')
  await expect(cards).toHaveCount(2)
  const titles = await cards.locator('.reco-card__title').allInnerTexts()
  expect(new Set(titles).size).toBe(2)
  const section = page.locator('[data-portfolio-recommendation]')
  await expect(section).toHaveAttribute('aria-label', /2 项/)
  await expect(section.locator('[data-recommendation-headline] h3'))
    .toHaveText('找到 2 个符合条件的项目')
  await expect(section.locator('[data-recommendation-status]')).toHaveCount(0)
})

// 场景 C：三项目部分完成
test('场景 C · 1/3 部分推荐明确显示数量、状态、原因与恢复操作', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await openAgent(page)
  await ask(page, '给我推荐三个项目')

  const section = page.locator('[data-portfolio-recommendation]')
  await expect(section.locator('[data-recommendation-headline] h3'))
    .toHaveText('找到 1/3 个符合条件的项目')
  await expect(section.locator('[data-recommendation-status]')).toHaveText('部分完成')
  await expect(section.locator('[data-recommendation-unsatisfied]'))
    .toContainText('其余公开项目的证据完整度暂不足')
  await expect(page.locator('[data-recommendation-item]')).toHaveCount(1)
  await expect(section.locator('[data-recommendation-recovery]')).toHaveText('放宽条件重新推荐')
  await expect(section).not.toContainText('执行完成')
  // 部分完成：执行快照自动展开并显示任务名
  const snapshot = page.locator('[data-execution-snapshot]')
  await expect(snapshot).toBeVisible()
  await expect(snapshot.locator('[data-execution-toggle]')).toHaveAttribute('aria-expanded', 'true')
  await expect(snapshot.locator('[data-execution-task="01"]')).toContainText('推荐 3 个公开项目')
})

// 场景 C 附加：恢复操作通过可信句柄重新推荐
test('场景 C · 恢复操作携带推荐句柄重新请求', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  const requests: ClosureRequestLogEntry[] = []
  await openAgent(page, (entry) => requests.push(entry))
  await ask(page, '给我推荐三个项目')

  await page.locator('[data-recommendation-recovery]').click()
  await page.locator('[data-recommendation-headline] h3')
    .filter({ hasText: '找到 2 个符合条件的项目' })
    .waitFor({ timeout: READY_TIMEOUT })
  const recovery = requests.find((entry) => entry.question === '放宽条件重新推荐')
  expect(recovery?.contextReference).toEqual({
    contextHandle: 'reco-handle-closure',
    expectedContextType: 'RECOMMENDATION',
  })
})

// 场景 D：SQL 正式预设
test('场景 D · SQL 预设只显示一组任务状态，章节不重复，引用为公开编号', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await openAgent(page)
  await ask(page, '详细介绍一下 SQL 审计与故障排查工具项目')

  // 执行快照默认收起，展开后只有一个任务
  const snapshot = page.locator('[data-execution-snapshot]')
  await expect(snapshot).toBeVisible()
  await expect(snapshot.locator('[data-execution-toggle]')).toHaveAttribute('aria-expanded', 'false')
  await snapshot.locator('[data-execution-toggle]').click()
  await expect(snapshot.locator('[data-execution-task]')).toHaveCount(1)
  await expect(snapshot.locator('[data-execution-task="01"]'))
    .toContainText('介绍 SQL 审计与故障排查工具项目')

  // 章节内容唯一：交付概览只出现一次
  await expect(page.locator('.answer-block__head h4', { hasText: '交付概览' })).toHaveCount(1)

  // 引用显示公开编号与标题
  await expect(page.locator('[data-section-citation]')).toHaveCount(2)
  await expect(page.locator('[data-section-citation]').first())
    .toHaveText('E-01 · SQL 审计工具交付证据集')

  // 回答级来源摘要
  await expect(page.locator('[data-answer-sources]')).toHaveText('依据 1 组已审核公开证据')
})

// 场景 E：证据聚焦与焦点返回
test('场景 E · 点击章节引用打开证据工作台，关闭后焦点回到引用按钮', async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 800 })
  await openAgent(page)
  await ask(page, '详细介绍一下 SQL 审计与故障排查工具项目')

  const citation = page.locator('[data-section-citation]').first()
  await citation.click()
  const desk = page.locator('#agent-evidence-desk')
  await expect(desk).toBeVisible()
  await expect(desk).toContainText('聚焦当前回答')
  // 点击章节引用进入聚焦态：引用页签展示该章节引用，显示公开编号与标题。
  await expect(desk.locator('[data-citation-id]')).toHaveCount(2)
  await expect(desk.locator('[data-citation-id] small').first())
    .toHaveText('引用自 E-01 · SQL 审计工具交付证据集')

  await page.keyboard.press('Escape')
  // 抽屉关闭后回到 inert/aria-hidden，焦点返回触发引用的按钮。
  await expect(desk).toHaveAttribute('aria-hidden', 'true')
  await expect(desk).toHaveAttribute('inert', '')
  await expect(citation).toBeFocused()
})

// 场景 F：视觉规范
test('场景 F · 无绿色状态色、无内部 ID、无原始英文枚举', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await openAgent(page)
  await ask(page, '详细介绍一下 SQL 审计与故障排查工具项目')
  await page.locator('[data-execution-toggle]').click()

  const threadText = await page.locator('.conversation__scroll').innerText()
  expect(threadText).not.toContain('sql-audit-delivery-set')
  expect(threadText).not.toMatch(/ANSWERED|EVIDENCE_COMPOSITION|NOT_SUPPORTED|VERIFIED/)

  // 执行快照区域不使用绿色（绿色通道显著高于红/蓝通道即判绿）。
  // Chrome 对 color-mix() 可能返回 0–1 浮点 rgb，需按小数解析并归一到 0–255。
  const greenish = await page.locator('[data-execution-snapshot] *').evaluateAll((nodes) =>
    nodes.flatMap((node) => {
      const raw = getComputedStyle(node).color.match(/-?[\d.]+/g)
      if (!raw || raw.length < 3) return []
      const channels = raw.slice(0, 3).map(Number).map((value) => (value <= 1 ? value * 255 : value))
      const [r, g, b] = channels
      if (g > 120 && g > r * 1.4 && g > b * 1.4) {
        return [`${node.tagName}.${node.className}:${channels.join(',')}`]
      }
      return []
    }),
  )
  expect(greenish).toEqual([])
})

// 场景 5（关键要求）：推荐结果上追问「第二个呢」携带可信续接句柄
test('追问 · 推荐后输入「第二个呢」通过可信句柄定位第二项', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  const requests: ClosureRequestLogEntry[] = []
  await openAgent(page, (entry) => requests.push(entry))
  await ask(page, '给我推荐两个项目')
  await ask(page, '第二个呢')

  await expect(page.locator('.message--agent').last()).toContainText('代码图谱工具端到端评测')
  const followUp = requests.find((entry) => entry.question === '第二个呢')
  expect(followUp?.contextReference).toEqual({
    contextHandle: 'reco-handle-closure',
    expectedContextType: 'RECOMMENDATION',
  })
})

// 窄屏：回答、输入框、证据入口与关键状态可用
test('窄屏 · 回答与证据入口可用，证据栏不挤压主回答', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openAgent(page)
  await ask(page, '详细介绍一下 SQL 审计与故障排查工具项目')

  await expect(page.locator('.answer-block').first()).toBeVisible()
  await expect(page.getByLabel('你的问题')).toBeVisible()
  await expect(page.locator('[data-answer-sources]')).toBeVisible()
  await expect(page.locator('.evidence-toggle')).toBeVisible()

  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  )
  expect(hasHorizontalOverflow).toBe(false)
})
