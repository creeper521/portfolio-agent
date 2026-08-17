import { expect, type Page, type Request } from '@playwright/test'

import { installP3Mocks } from '../support/publicApiMocks'

const READY_TIMEOUT = 20_000

/**
 * Browser driver for the behavior audit.  It deliberately exposes only
 * contract facts (subject ids, scopes, counts and state markers); question and
 * answer text is used by the page but is never returned in an observation.
 */
export interface BehaviorUiObservation {
  readonly route: string
  readonly caseContextVisible: boolean
  readonly caseContextLabel?: string
  readonly inputLength: number
  readonly userMessageCount: number
  readonly agentMessageCount: number
  readonly clarificationCount: number
  readonly answerState: 'IDLE' | 'LOADING' | 'ERROR'
  readonly recoveryCardVisible: boolean
  readonly sessionCount: number
  readonly urlContainsQuestion: boolean
}

export interface BehaviorRequestFacts {
  readonly projectSlug?: string
  readonly caseSlug?: string
  readonly source?: string
  readonly activeSubjects: readonly { subjectType: string; subjectId: string }[]
  readonly hasReferenceContext: boolean
  readonly hasResumeTokenHeader: boolean
  readonly messageCount: number
}

export async function installBehaviorUiApi(page: Page): Promise<void> {
  await installP3Mocks(page)
}

export async function openAgent(page: Page, query = ''): Promise<void> {
  await page.goto(`/agent${query}`)
  await expect(page).toHaveURL(/\/agent(?:\?.*)?$/)
  await expect(page.getByLabel('你的问题')).toBeVisible({ timeout: READY_TIMEOUT })
}

export async function openCaseQuestion(page: Page): Promise<void> {
  await page.goto('/cases/multilingual-image-preservation')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('多语言图片上传结果保留修复')
  await page.getByRole('link', { name: /Q01/ }).click()
  await expect(page).toHaveURL(/\/agent$/)
  await expect(page.locator('[data-case-context]')).toBeVisible({ timeout: READY_TIMEOUT })
  await expect(page.getByLabel('你的问题')).toBeVisible({ timeout: READY_TIMEOUT })
}

export async function ask(page: Page, question: string): Promise<void> {
  await page.getByLabel('你的问题').fill(question)
  await page.getByRole('button', { name: /发送/ }).click()
}

export async function waitForAnswer(page: Page): Promise<void> {
  await expect(page.locator('.message--agent').last()).toBeVisible({ timeout: READY_TIMEOUT })
  await expect(page.locator('[data-agent-loading]')).toHaveCount(0, { timeout: READY_TIMEOUT })
}

export async function waitForPost(page: Page): Promise<Request> {
  return page.waitForRequest(
    (request) => request.url().includes('/api/v2/answers') && request.method() === 'POST',
    { timeout: READY_TIMEOUT },
  )
}

/** Extract safe request metadata without retaining messages or question text. */
export function requestFacts(request: Request): BehaviorRequestFacts {
  const body = request.postDataJSON() as {
    projectSlug?: unknown
    caseSlug?: unknown
    source?: unknown
    messages?: unknown
    semanticContext?: { activeSubjects?: unknown }
    context?: { projectSlug?: unknown; caseSlug?: unknown; source?: unknown; referenceContext?: unknown }
    contextReference?: unknown
  }
  const subjects = Array.isArray(body.semanticContext?.activeSubjects)
    ? body.semanticContext.activeSubjects
      .filter((subject): subject is { subjectType?: unknown; subjectId?: unknown } =>
        typeof subject === 'object' && subject !== null,
      )
      .filter((subject) => typeof subject.subjectType === 'string' && typeof subject.subjectId === 'string')
      .map((subject) => ({ subjectType: subject.subjectType as string, subjectId: subject.subjectId as string }))
    : []
  return {
    ...(typeof (body.context?.projectSlug ?? body.projectSlug) === 'string'
      ? { projectSlug: (body.context?.projectSlug ?? body.projectSlug) as string } : {}),
    ...(typeof (body.context?.caseSlug ?? body.caseSlug) === 'string'
      ? { caseSlug: (body.context?.caseSlug ?? body.caseSlug) as string } : {}),
    ...(typeof (body.context?.source ?? body.source) === 'string'
      ? { source: (body.context?.source ?? body.source) as string } : {}),
    activeSubjects: subjects,
    hasReferenceContext: body.context?.referenceContext !== undefined || body.contextReference !== undefined,
    hasResumeTokenHeader: request.headers()['x-conversation-resume-token'] !== undefined,
    messageCount: Array.isArray(body.messages) ? body.messages.length : 0,
  }
}

export async function observe(page: Page): Promise<BehaviorUiObservation> {
  const input = page.getByLabel('你的问题')
  const inputValue = await input.inputValue().catch(() => '')
  const url = page.url()
  const caseContextVisible = await page.locator('[data-case-context]').isVisible().catch(() => false)
  return {
    route: new URL(url).pathname,
    caseContextVisible,
    // textContent() auto-waits for the element; without a case context the
    // element never attaches, so only read the label when one is visible.
    caseContextLabel: caseContextVisible
      ? await page.locator('[data-case-context]').textContent().catch(() => undefined) ?? undefined
      : undefined,
    inputLength: inputValue.length,
    userMessageCount: await page.locator('.message--user').count(),
    agentMessageCount: await page.locator('.message--agent').count(),
    clarificationCount: await page.locator('[data-testid="turn-clarification"]').count(),
    answerState: await page.locator('[data-agent-loading]').count() > 0
      ? 'LOADING'
      : await page.locator('.answer-state--error').count() > 0 ? 'ERROR' : 'IDLE',
    recoveryCardVisible: await page.locator('[data-recovery-card]').isVisible().catch(() => false),
    sessionCount: await page.locator('.session-list article').count(),
    urlContainsQuestion: /[?&](?:q|question)=/.test(url),
  }
}

export async function clearCaseContext(page: Page): Promise<void> {
  await page.locator('[data-clear-case-context]').click()
  await expect(page.locator('[data-case-context]')).toHaveCount(0)
}

export async function clearConversation(page: Page): Promise<void> {
  await page.reload()
  await expect(page.getByLabel('你的问题')).toBeVisible({ timeout: READY_TIMEOUT })
  await expect(page.locator('[data-recovery-card]')).toBeVisible({ timeout: READY_TIMEOUT })
  await page.locator('[data-clear-conversation]').click()
  await expect(page.locator('[data-recovery-card]')).toHaveCount(0)
}

export { READY_TIMEOUT }
