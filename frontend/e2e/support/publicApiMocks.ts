import type { Page, Route } from '@playwright/test'

import { previewPublicContent } from '../../src/features/public-content/data/previewPublicContent'

interface AnswerScenario {
  status?: number
  code?: string
  retryAfterSeconds?: number
  requestId?: string
  unsafeMessage?: string
  delayMilliseconds?: number
  networkFailure?: boolean
  onRequest?: (headers: Record<string, string>) => void
}

interface DiagnosticsMockOptions {
  failUploads?: boolean
}

interface DiagnosticsCapture {
  attempts: number
  bodies: unknown[]
  events: Record<string, unknown>[]
}

const CANONICAL_QUESTION =
  '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'
const QUESTION_ALIASES = new Set([
  ...previewPublicContent.questionPresets.map((preset) => preset.text),
  CANONICAL_QUESTION,
  '请详细介绍SQL审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么',
  '详细介绍一下 SQL 审计与故障排查工具项目',
  '你在 SQL 审计与故障排查工具项目中做了什么',
  '你在SQL审计与故障排查工具项目中做了什么',
])
const RETRIEVAL_QUESTION = '这个项目交付了什么？'
const BOUNDARY_MESSAGE =
  '当前版本只稳定支持项目完整介绍问题。你可以使用下方推荐问题了解项目背景、我的职责、技术方案、验证过程和最终状态。'

function answerResponse(
  question: string,
  questionPresetId?: string,
  referenceContext?: Record<string, unknown>,
) {
  const project = previewPublicContent.projects[0]
  const evidence = previewPublicContent.evidence[0]
  const rejected = /(?:内部|私有|private).*(?:密码|token|密钥|credential)/i.test(question)
  const matched = questionPresetId === 'sql-audit-overview' || QUESTION_ALIASES.has(question.trim())
  const retrieved = !questionPresetId && question.trim() === RETRIEVAL_QUESTION
  const followUp = Boolean(referenceContext)
  const answered = matched || retrieved || followUp
  const evidenceIds = answered ? [evidence.id] : []
  const resolution = rejected ? 'REJECTED' : answered ? 'ANSWERED' : 'BOUNDARY'
  const summary = rejected
    ? '无法处理该请求。你可以改为询问已经公开的项目、职责、方案或验证信息。'
    : answered ? project.summary : BOUNDARY_MESSAGE

  return {
    requestId: 'playwright-mock-request',
    turnId: 'playwright-turn',
    contentVersion: previewPublicContent.contentVersion,
    questionPresetId: matched ? 'sql-audit-overview' : undefined,
    resolution,
    answerSource: matched && !followUp ? 'PRESET' : answered ? 'RETRIEVAL' : undefined,
    generationMode: 'DETERMINISTIC',
    verification: matched && !followUp ? 'VERIFIED' : answered ? 'PARTIALLY_VERIFIED' : 'NOT_APPLICABLE',
    title: project.title,
    summary,
    sections: answered
        ? [
            { type: 'BACKGROUND', title: '项目背景', content: project.background, evidenceIds, claimIds: ['claim-sql-audit-delivered'] },
            { type: 'RESPONSIBILITY', title: '我的职责', content: project.responsibilities.join(' '), evidenceIds, claimIds: ['claim-sql-audit-delivered'] },
            {
              type: 'SOLUTION',
              title: '技术方案',
              content: `${project.solution} 关键决策包括：${project.keyDecisions.join(' ')}`,
              evidenceIds,
              claimIds: ['claim-sql-audit-delivered'],
            },
            { type: 'VERIFICATION', title: '验证过程', content: project.verification.join(' '), evidenceIds, claimIds: ['claim-sql-audit-delivered'] },
            { type: 'STATUS', title: '最终状态', content: `${project.outcome} ${project.handoff}`, evidenceIds, claimIds: ['claim-sql-audit-delivered'] },
          ]
        : [{
            type: rejected ? 'REJECTED' : 'BOUNDARY',
            title: '能力说明',
            content: summary,
            evidenceIds: [],
          }],
    evidenceIds,
    suggestedQuestionPresetIds: ['sql-audit-overview'],
    referenceContext: answered ? {
      previousContentVersion: previewPublicContent.contentVersion,
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
    } : undefined,
    contextVersionUpdated: false,
  }
}

async function fulfillPublicContent(route: Route) {
  if (route.request().method() !== 'GET') {
    await route.fallback()
    return
  }
  await route.fulfill({ status: 200, contentType: 'application/json', json: previewPublicContent })
}

async function fulfillAnswer(route: Route) {
  if (route.request().method() !== 'POST') {
    await route.fallback()
    return
  }
  const requestBody = route.request().postDataJSON() as {
    question?: unknown
    questionPresetId?: unknown
    context?: {
      referenceContext?: Record<string, unknown>
    }
  }
  const question = typeof requestBody.question === 'string' ? requestBody.question : ''
  const questionPresetId = typeof requestBody.questionPresetId === 'string'
    ? requestBody.questionPresetId
    : undefined
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    json: answerResponse(question, questionPresetId, requestBody.context?.referenceContext),
  })
}

export async function installPublicApiMocks(page: Page) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  await installAnswerApiMock(page)
}

export async function installAnswerApiMock(page: Page) {
  await page.route('**/api/v2/answers', fulfillAnswer)
}

export async function installAnswerScenarioMock(page: Page, scenario: AnswerScenario) {
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    scenario.onRequest?.(await route.request().allHeaders())
    if (scenario.delayMilliseconds) {
      await new Promise((resolve) => setTimeout(resolve, scenario.delayMilliseconds))
    }
    if (scenario.networkFailure) {
      await route.abort('failed')
      return
    }
    if (scenario.status && scenario.status >= 400) {
      await route.fulfill({
        status: scenario.status,
        contentType: 'application/json',
        headers: scenario.requestId ? { 'X-Request-Id': scenario.requestId } : undefined,
        json: {
          code: scenario.code,
          retryAfterSeconds: scenario.retryAfterSeconds,
          message: scenario.unsafeMessage,
        },
      })
      return
    }
    await fulfillAnswer(route)
  })
}

export async function installDiagnosticsApiMock(
  page: Page,
  options: DiagnosticsMockOptions = {},
): Promise<DiagnosticsCapture> {
  const capture: DiagnosticsCapture = {
    attempts: 0,
    bodies: [],
    events: [],
  }
  await page.route('**/api/v1/client-diagnostics', async (route) => {
    capture.attempts += 1
    const body = route.request().postDataJSON() as { events?: Record<string, unknown>[] }
    capture.bodies.push(body)
    capture.events.push(...(Array.isArray(body.events) ? body.events : []))
    if (options.failUploads) {
      await route.abort('failed')
      return
    }
    await route.fulfill({ status: 202, body: '' })
  })
  return capture
}

export interface GuidedAnswerTurn {
  coveredTopics?: string[]
  guidanceStage?: string
  generationMode?: string
  degraded?: boolean
  suggestedQuestions?: Array<{
    text: string
    projectSlug?: string | null
    caseSlug?: string | null
    facet?: string | null
  }>
}

export async function installGuidedAnswerMock(
  page: Page,
  turns: GuidedAnswerTurn[],
  onRequest?: (body: Record<string, unknown>, index: number) => void,
) {
  let index = 0
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    onRequest?.(body, index)
    const turn = (
      turns.length ? turns[Math.min(index, turns.length - 1)] : {}
    ) as GuidedAnswerTurn
    index += 1
    const base = answerResponse(
      typeof body.question === 'string' ? body.question : '',
      typeof body.questionPresetId === 'string' ? body.questionPresetId : undefined,
      (body.context as { referenceContext?: Record<string, unknown> } | undefined)
        ?.referenceContext,
    )
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        ...base,
        generationMode: turn.generationMode ?? base.generationMode,
        degraded: turn.degraded === true,
        coveredTopics: turn.coveredTopics ?? ['BACKGROUND'],
        guidanceStage: turn.guidanceStage ?? 'OPENING',
        suggestedQuestions: turn.suggestedQuestions ?? [],
      },
    })
  })
}
