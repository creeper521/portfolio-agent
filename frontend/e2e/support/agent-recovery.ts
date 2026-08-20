import { expect, type APIRequestContext, type Page, type Request, type Response } from '@playwright/test'

/**
 * 恢复矩阵 E2E 的共享助手。
 *
 * 这些用例运行在 packaged-JAR lane 上，只允许“延迟真实请求”，不允许伪造响应体，
 * 所以助手不提供任何 route.fulfill 能力；所有断言都基于服务端真实回包。
 * 每次真实 POST 都会消耗同一匿名来源的 10 RPM 预算，因此用例中的 POST 数量
 * 是跨用例统筹过的，新增用例前先核对整份 spec 的 POST 总数。
 */

export const TURNS_PATH = '/api/agent/turns'

export interface PublicPreset {
  readonly id: string
  readonly contractVersion: string
}

export interface RateLimitEvidence {
  readonly status: number
  readonly retryAfterHeader: string
  readonly body: {
    error?: {
      code?: string
      retryable?: boolean
      retryAfterSeconds?: number
    }
  }
}

export interface DelayedTurnResponses {
  readonly firstSettled: Promise<{
    readonly status: number
    readonly body: Record<string, unknown>
  }>
  stop(): Promise<void>
}

function pathname(url: string): string {
  return new URL(url).pathname
}

export function isTurnPost(request: Request): boolean {
  return pathname(request.url()) === TURNS_PATH && request.method() === 'POST'
}

/** 等待下一条到达 /api/agent/turns 的 POST 请求并解析其 JSON 命令体。 */
export async function nextTurnPost(page: Page): Promise<{ requestId: string; command: Record<string, unknown> }> {
  const request = await page.waitForRequest(isTurnPost)
  const body = request.postDataJSON() as {
    requestId?: string
    command?: Record<string, unknown>
  }
  expect(body.requestId).toBeTruthy()
  return { requestId: body.requestId ?? '', command: body.command ?? {} }
}

export async function nextTurnResponse(page: Page): Promise<Response> {
  return page.waitForResponse((response) =>
    pathname(response.url()) === TURNS_PATH && response.request().method() === 'POST')
}

/**
 * 先把真实 POST 交给后端并等待终局，再延迟浏览器可见响应。
 * 这样前端超时后的同 requestId 重试验证的是真实 replay，不是一次未发送的新请求。
 */
export async function delayTurnResponsesAfterBackend(
  page: Page,
  delayMs: number,
): Promise<DelayedTurnResponses> {
  let resolveFirst!: (value: { status: number; body: Record<string, unknown> }) => void
  const firstSettled = new Promise<{ status: number; body: Record<string, unknown> }>((resolve) => {
    resolveFirst = resolve
  })
  let captured = false
  const handler = async (route: import('@playwright/test').Route) => {
    const response = await route.fetch()
    if (!captured) {
      captured = true
      const value = await response.json() as Record<string, unknown>
      resolveFirst({ status: response.status(), body: value })
    }
    await new Promise((resolve) => setTimeout(resolve, delayMs))
    await route.fulfill({ response }).catch(() => undefined)
  }
  await page.route(`**${TURNS_PATH}`, handler)
  return {
    firstSettled,
    stop: async () => {
      await page.unroute(`**${TURNS_PATH}`, handler).catch(() => undefined)
    },
  }
}

/** 移动端会话列表/来源面板在抽屉里；触发器是切换开关，所以按 aria-expanded 保证幂等打开。 */
async function ensureDrawerOpen(page: Page, testId: 'open-session-drawer' | 'open-source-panel'): Promise<void> {
  const trigger = page.getByTestId(testId)
  if ((await trigger.getAttribute('aria-expanded')) !== 'true') {
    await trigger.click()
  }
}

export async function openSessionRail(page: Page, isMobile: boolean): Promise<void> {
  if (isMobile) {
    await ensureDrawerOpen(page, 'open-session-drawer')
  }
}

export async function openSourcesPanel(page: Page, isMobile: boolean): Promise<void> {
  if (isMobile) {
    await ensureDrawerOpen(page, 'open-source-panel')
  }
}

/** 移动端抽屉打开时有全屏遮罩，操作完抽屉后必须关闭才能继续操作主界面。 */
export async function closeDrawers(page: Page, isMobile: boolean): Promise<void> {
  if (isMobile) {
    const scrim = page.getByRole('button', { name: '关闭侧栏' })
    if (await scrim.isVisible()) {
      // 抽屉面板位于遮罩上方，真实指针可点击面板外露出的遮罩。
      // E2E 直接激活该语义按钮，避免视口差异导致命中面板子元素。
      await scrim.evaluate((element) => (element as HTMLButtonElement).click())
    }
  }
}

export async function createSession(page: Page, isMobile: boolean): Promise<void> {
  await openSessionRail(page, isMobile)
  await page.getByRole('button', { name: /新对话/ }).click()
  await closeDrawers(page, isMobile)
}

/** 按创建顺序选择本地会话（rail 按钮的可访问名称以“会话：”开头）。 */
/** 按创建顺序选择本地会话。rail 只列出已有 USER 消息的会话，所以 index 只对有消息的会话有效。 */
export async function selectSession(page: Page, isMobile: boolean, index: number): Promise<void> {
  await openSessionRail(page, isMobile)
  await page.getByRole('button', { name: /^会话：/ }).nth(index).click()
  await closeDrawers(page, isMobile)
}

export async function selectSessionByTitle(
  page: Page,
  isMobile: boolean,
  title: string,
): Promise<void> {
  await openSessionRail(page, isMobile)
  await page.getByRole('button', { name: new RegExp(`^\u4f1a\u8bdd：.*${title}`) }).click()
  await closeDrawers(page, isMobile)
}

export async function firstPublicPreset(request: APIRequestContext): Promise<PublicPreset> {
  const contentResponse = await request.get('/api/v1/public-content')
  expect(contentResponse.ok()).toBeTruthy()
  const content = await contentResponse.json() as { questionPresets: Array<{ id: string; contractVersion: string }> }
  const preset = content.questionPresets[0]
  expect(preset).toBeTruthy()
  return preset
}

export async function postPresetTurn(
  request: APIRequestContext,
  preset: PublicPreset,
  authorization?: string,
): Promise<Response> {
  return request.post(TURNS_PATH, {
    ...(authorization === undefined ? {} : { headers: { Authorization: `Bearer ${authorization}` } }),
    data: {
      requestId: crypto.randomUUID(),
      command: {
        kind: 'ASK',
        input: { kind: 'PRESET', presetId: preset.id, presetRevision: preset.contractVersion },
      },
      conversationWindow: [],
    },
  })
}

/**
 * 用真实请求逼近来源限流：持续发确定性 PRESET 请求直到出现 429。
 * 之前的用例可能已消耗部分 RPM 预算，所以这里必须循环而不是假设精确阈值。
 */
export async function exhaustSourceRateLimit(
  request: APIRequestContext,
  preset: PublicPreset,
  maxProbes = 6,
): Promise<RateLimitEvidence> {
  let lastStatus = 0
  for (let attempt = 0; attempt < maxProbes; attempt += 1) {
    const response = await postPresetTurn(request, preset)
    lastStatus = response.status()
    if (lastStatus === 429) {
      const header = response.headers()['retry-after']
      expect(header).toBeTruthy()
      return {
        status: lastStatus,
        retryAfterHeader: header ?? '',
        body: await response.json() as RateLimitEvidence['body'],
      }
    }
    expect(response.ok(), `probe ${attempt + 1} 应当成功或限流，实际 ${lastStatus}`).toBeTruthy()
  }
  throw new Error(`连续 ${maxProbes} 次探针都未触发限流（最后一次状态 ${lastStatus}），RPM 预算与用例预算不匹配`)
}

/**
 * 限流用例收尾时等待固定 RPM 窗口过期，避免毒化同一服务上的下一个测试项目
 * （例如 mobile-chromium 复跑同一份 spec）。窗口是 60 秒固定窗口，从本 spec
 * 第一条 POST 起算；探测成功即说明新窗口已开启。
 */
export async function awaitFreshRateWindow(
  request: APIRequestContext,
  preset: PublicPreset,
): Promise<void> {
  await expect.poll(async () => (await postPresetTurn(request, preset)).status(), {
    timeout: 75_000,
    intervals: [5_000],
  }).toBe(200)
}
