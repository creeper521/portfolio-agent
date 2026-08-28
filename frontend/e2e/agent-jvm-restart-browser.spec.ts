import { expect, test, type Page } from '@playwright/test'
import { promises as fs } from 'node:fs'
import path from 'node:path'

const TURNS_PATH = '/api/agent/turns'
const CURRENT_PATH = '/api/agent/conversations/current'
const RESUME_TOKEN_STORAGE_KEY = 'portfolio.agent.resume-token.v1'
const COORDINATION_DIRECTORY_PREFIX = 'portfolio-jvm-restart-browser-'
const BROWSER_READY_FILE = 'browser-ready.signal'
const SERVER_RESTARTED_FILE = 'server-restarted.signal'
const BROWSER_READY = 'BROWSER_READY'
const SERVER_RESTARTED = 'SERVER_RESTARTED'
const RESTART_TIMEOUT_MS = 120_000

interface ConversationState {
  conversationId: string
  status: string
  discussionRevision: number
}

interface SettledAnswer {
  kind: string
  requestId: string
  answer?: unknown
  modelExecution?: { selectionKind?: string }
  conversation?: {
    conversationId?: string
    resumeToken?: string
  }
}

interface FirstTurnRequest {
  requestId: string
  modelSelection: { kind: string }
  command: unknown
  conversationWindow: unknown[]
  surfaceContext?: unknown
}

function coordinationDirectory(): string {
  const configured = process.env.PLAYWRIGHT_JVM_RESTART_COORDINATION_DIR
  if (configured === undefined || configured.trim().length === 0) {
    throw new Error('PLAYWRIGHT_JVM_RESTART_COORDINATION_DIR is required')
  }
  if (!path.isAbsolute(configured)) {
    throw new Error('JVM restart coordination directory must be absolute')
  }
  const resolved = path.resolve(configured)
  if (!path.basename(resolved).startsWith(COORDINATION_DIRECTORY_PREFIX)) {
    throw new Error(
      `JVM restart coordination directory must start with ${COORDINATION_DIRECTORY_PREFIX}`,
    )
  }
  return resolved
}

async function assertControlledDirectory(directory: string): Promise<void> {
  const stat = await fs.stat(directory)
  if (!stat.isDirectory()) {
    throw new Error('JVM restart coordination path is not a directory')
  }
}

async function writeClosedSignal(
  directory: string,
  fileName: typeof BROWSER_READY_FILE,
  state: typeof BROWSER_READY,
): Promise<void> {
  const target = path.join(directory, fileName)
  await fs.writeFile(target, `${state}\n`, { encoding: 'utf8', flag: 'wx' })
}

async function waitForClosedSignal(
  directory: string,
  fileName: typeof SERVER_RESTARTED_FILE,
  expected: typeof SERVER_RESTARTED,
): Promise<void> {
  const target = path.join(directory, fileName)
  const deadline = Date.now() + RESTART_TIMEOUT_MS
  while (Date.now() < deadline) {
    try {
      const actual = (await fs.readFile(target, 'utf8')).trim()
      if (actual !== expected) {
        throw new Error(`Unexpected JVM restart coordination state: ${actual}`)
      }
      return
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
    }
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  throw new Error(`Timed out waiting for ${expected}`)
}

async function assertClosedCoordinationContents(directory: string): Promise<void> {
  const entries = await fs.readdir(directory)
  expect(new Set(entries)).toEqual(new Set([
    BROWSER_READY_FILE,
    SERVER_RESTARTED_FILE,
  ]))
  expect((await fs.readFile(path.join(directory, BROWSER_READY_FILE), 'utf8')).trim())
    .toBe(BROWSER_READY)
  expect((await fs.readFile(path.join(directory, SERVER_RESTARTED_FILE), 'utf8')).trim())
    .toBe(SERVER_RESTARTED)
}

async function resumeToken(page: Page): Promise<string | null> {
  return page.evaluate((storageKey) => sessionStorage.getItem(storageKey),
    RESUME_TOKEN_STORAGE_KEY)
}

test('same BrowserContext restores deterministic conversation and replay across packaged JVM restart', async ({ page }) => {
  test.setTimeout(180_000)
  const coordination = coordinationDirectory()
  await assertControlledDirectory(coordination)

  await page.goto('/agent')
  const preset = page.locator('.workspace-composer__suggestion').first()
  await expect(preset).toBeEnabled()
  const firstPending = page.waitForResponse((response) =>
    new URL(response.url()).pathname === TURNS_PATH
      && response.request().method() === 'POST')
  await preset.click()
  const firstResponse = await firstPending
  expect(firstResponse.status()).toBe(200)

  const firstRequest = JSON.parse(
    firstResponse.request().postData() ?? '{}',
  ) as FirstTurnRequest
  const firstBody = await firstResponse.json() as SettledAnswer
  expect(firstRequest.requestId).toBeTruthy()
  expect(firstRequest.modelSelection.kind).toBe('NONE')
  expect(firstBody.kind).toBe('ANSWER')
  expect(firstBody.requestId).toBe(firstRequest.requestId)
  expect(firstBody.modelExecution?.selectionKind).toBe('NONE')

  const tokenBeforeRestart = await resumeToken(page)
  expect(tokenBeforeRestart).toBeTruthy()
  expect(tokenBeforeRestart).toBe(firstBody.conversation?.resumeToken)
  const conversationId = firstBody.conversation?.conversationId
  expect(conversationId).toBeTruthy()

  const currentBeforeResponse = await page.context().request.get(CURRENT_PATH, {
    headers: { Authorization: `Bearer ${tokenBeforeRestart ?? ''}` },
  })
  expect(currentBeforeResponse.status()).toBe(200)
  const currentBefore = await currentBeforeResponse.json() as ConversationState
  expect(currentBefore.conversationId).toBe(conversationId)
  expect(currentBefore.status).toBe('ACTIVE')

  // 文件内容是闭集状态，不携带 token、requestId、conversationId 或任何正文。
  await writeClosedSignal(coordination, BROWSER_READY_FILE, BROWSER_READY)
  await waitForClosedSignal(
    coordination,
    SERVER_RESTARTED_FILE,
    SERVER_RESTARTED,
  )

  // parent 只有在 JVM#2 readiness 成功后才能写 SERVER_RESTARTED。
  const resumedCurrentPending = page.waitForResponse((response) =>
    new URL(response.url()).pathname === CURRENT_PATH
      && response.request().method() === 'GET')
  await page.reload({ waitUntil: 'domcontentloaded' })
  const resumedCurrentResponse = await resumedCurrentPending
  expect(resumedCurrentResponse.status()).toBe(200)
  const resumedCurrent = await resumedCurrentResponse.json() as ConversationState
  expect(resumedCurrent).toEqual(currentBefore)
  // The deterministic lane disables free text; the preset proves the pending lock cleared.
  await expect(page.locator('.workspace-composer__suggestion').first()).toBeEnabled()

  const tokenAfterRestart = await resumeToken(page)
  expect(tokenAfterRestart).toBe(tokenBeforeRestart)

  const replayResponse = await page.context().request.post(TURNS_PATH, {
    headers: { Authorization: `Bearer ${tokenAfterRestart ?? ''}` },
    data: firstRequest,
  })
  expect(replayResponse.status()).toBe(200)
  const replayBody = await replayResponse.json() as SettledAnswer
  expect(replayBody.kind).toBe('ANSWER')
  expect(replayBody.requestId).toBe(firstRequest.requestId)
  expect(replayBody.conversation?.conversationId).toBe(conversationId)
  expect(replayBody.answer).toEqual(firstBody.answer)

  await assertClosedCoordinationContents(coordination)
})
