import { expect, test } from '@playwright/test'
import {
  expectAnswer,
  type PublicTurnBody,
} from './support/public-turn-happy-path'

const TURNS = '/api/agent/turns'

// 模型目录 UI spec §8.5（Slice A packaged 门）：
// 链路一「默认模型问答 → 会话内切换 → 下一轮跨模型续问」；
// 链路二「模型不可用 → 换模型重问」——通过在浏览器层把出站请求的
// selectionVersion 改写为失效值，让真实后端按冻结合同 Settlement 出
// MODEL_SELECTION_STALE 终局，再验证前端双动作恢复流（响应不做任何伪造）。
// 本 lane 需要打包 JAR 的公开目录非空（模型运行时已启用）；目录为空时跳过，
// 不伪造合同。注册于 PLAYWRIGHT_MODEL_SELECTION=1，由 packaged 运行器调用。

interface CatalogEntry {
  modelRef: string
  selectionVersion: string
  displayName: string
}

interface CatalogProjection {
  modelCatalogVersion: string
  defaultModelSelection: { kind: string; modelRef?: string }
  selectableModels: CatalogEntry[]
}

async function readCatalog(request: import('@playwright/test').APIRequestContext): Promise<CatalogProjection | null> {
  const response = await request.get('/api/portfolio')
  if (response.status() !== 200) return null
  const payload = await response.json() as { agentAvailability?: CatalogProjection }
  const catalog = payload.agentAvailability
  if (catalog === undefined || !Array.isArray(catalog.selectableModels)) return null
  if (catalog.selectableModels.length < 2) return null
  return catalog
}

test('default model answer, in-session switch, then cross-model follow-up', async ({ page, request }) => {
  test.setTimeout(120_000)
  const catalog = await readCatalog(request)
  test.skip(catalog === null, '公开目录为空（模型运行时未启用），本 lane 需要 ≥2 个可选模型')

  await page.goto('/agent')
  await expect(page.getByTestId('model-selector-trigger')).toBeVisible()
  await expect(page.getByTestId('model-selector-trigger'))
    .toContainText(catalog!.selectableModels[0]!.displayName)

  const firstTurn = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS
      && response.request().method() === 'POST')
  await page.getByTestId('question-input').fill('介绍 SQL 审计项目')
  await page.getByTestId('submit-question').click()
  const firstBody = await (await firstTurn).json() as PublicTurnBody & {
    modelExecution?: { selectionKind: string; requestedModelRef?: string }
  }
  expectAnswer(firstBody)
  expect(firstBody.modelExecution?.selectionKind).toBe('MODEL')
  expect(firstBody.modelExecution?.requestedModelRef).toBe(catalog!.defaultModelSelection.modelRef)

  // 会话内切换到第二个模型：浮层来自目录投影，切换通知可见（§2.4）。
  await page.getByTestId('model-selector-trigger').click()
  const secondEntry = catalog!.selectableModels[1]!
  await page.getByTestId('model-selector-popover')
    .locator('[data-testid="model-selector-option"]', { hasText: secondEntry.displayName })
    .click()
  await expect(page.getByTestId('model-notice'))
    .toContainText(`已切换至 ${secondEntry.displayName} · 下一轮回答将由它生成`)

  // 下一轮跨模型续问：出站请求携带新选择，回答标识反映该轮实际执行模型。
  const followUpPending = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS
      && response.request().method() === 'POST')
  await page.getByTestId('question-input').fill('再展开讲讲其中的取舍')
  await page.getByTestId('submit-question').click()
  const followUpResponse = await followUpPending
  expect((JSON.parse(String(followUpResponse.request().postData())) as {
    modelSelection: { modelRef: string; selectionVersion: string }
  }).modelSelection).toEqual({
    kind: 'MODEL',
    modelRef: secondEntry.modelRef,
    selectionVersion: secondEntry.selectionVersion,
  })
  const followUpBody = await followUpResponse.json() as PublicTurnBody & {
    modelExecution?: { requestedModelRef?: string }
  }
  expectAnswer(followUpBody)
  expect(followUpBody.modelExecution?.requestedModelRef).toBe(secondEntry.modelRef)

  // 模型偏好不进入浏览器存储与 URL（隐私边界 §6）。
  const persisted = await page.evaluate(() => JSON.stringify({
    url: location.href,
    local: Object.entries(localStorage),
    session: Object.entries(sessionStorage),
  }))
  expect(persisted).not.toMatch(/modelRef|selectionVersion/)
})

test('model unavailable terminal offers reask with another model', async ({ page, request }) => {
  test.setTimeout(120_000)
  const catalog = await readCatalog(request)
  test.skip(catalog === null, '公开目录为空（模型运行时未启用），本 lane 需要 ≥2 个可选模型')

  // 出站请求层把 selectionVersion 改写为失效值：后端按真实合同 Settlement
  // MODEL_SELECTION_STALE 终局（设计 §16.2）；响应完全来自真实后端。
  await page.route(TURNS, async (route) => {
    const postData = route.request().postData() ?? '{}'
    const body = JSON.parse(postData) as {
      modelSelection?: { kind: string; selectionVersion?: string }
    }
    if (body.modelSelection?.kind === 'MODEL') {
      body.modelSelection.selectionVersion = 'e2e-stale-selection-version'
    }
    await route.continue({ postData: JSON.stringify(body) })
  })

  await page.goto('/agent')
  await page.getByTestId('question-input').fill('介绍 SQL 审计项目')
  const staleTurn = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS
      && response.request().method() === 'POST')
  await page.getByTestId('submit-question').click()
  const staleBody = await (await staleTurn).json() as {
    kind: string
    code?: string
    requestId?: string
    modelExecution?: { requestedModelRef?: string }
  }
  expect(staleBody.kind).toBe('CAPABILITY_UNAVAILABLE')
  expect(staleBody.code).toBe('MODEL_SELECTION_STALE')

  // 双动作卡（§2.6/D-MS-7）：标题、说明与两条语义区分说明齐备；
  // settled 终局的两个动作都是新请求，不提供同 requestId 重放入口。
  await expect(page.getByTestId('model-failure-title')).toBeVisible()
  await expect(page.getByTestId('model-failure-title'))
    .toContainText('暂时无法完成这次回答')
  await expect(page.getByTestId('model-retry-same-model')).toContainText('重新提问')
  await expect(page.getByTestId('model-switch-reask')).toContainText('重新提问')

  // 换模型重问：新 requestId + 目录默认模型选择，通知携带新请求标识（§4）。
  await page.unroute(TURNS)
  const secondEntry = catalog!.selectableModels[1]!
  const reaskPending = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS
      && response.request().method() === 'POST')
  await page.getByTestId('model-switch-reask').click()
  const reaskResponse = await reaskPending
  const reaskBody = JSON.parse(String(reaskResponse.request().postData())) as {
    requestId: string
    modelSelection: { modelRef: string }
  }
  expect(reaskBody.requestId).not.toBe(staleBody.requestId ?? '')
  expect(reaskBody.modelSelection.modelRef).toBe(secondEntry.modelRef)
  expectAnswer(await reaskResponse.json() as PublicTurnBody)
  await expect(page.getByTestId('model-notice'))
    .toContainText('不复用原请求的任何结果')
})
