import type { Page, Route } from '@playwright/test'

import { previewPublicContent } from '../../src/features/public-content/data/previewPublicContent'

const CANONICAL_QUESTION =
  '请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？'
const QUESTION_ALIASES = new Set([
  CANONICAL_QUESTION,
  '请详细介绍SQL审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么',
  '详细介绍一下 SQL 审计与故障排查工具项目',
  '你在 SQL 审计与故障排查工具项目中做了什么',
  '你在SQL审计与故障排查工具项目中做了什么',
])
const BOUNDARY_MESSAGE =
  '当前版本只稳定支持项目完整介绍问题。你可以使用下方推荐问题了解项目背景、我的职责、技术方案、验证过程和最终状态。'

function answerResponse(question: string, questionPresetId?: string) {
  const project = previewPublicContent.projects[0]
  const evidence = previewPublicContent.evidence[0]
  const rejected = /(?:内部|私有|private).*(?:密码|token|密钥|credential)/i.test(question)
  const matched = questionPresetId === 'sql-audit-overview' || QUESTION_ALIASES.has(question.trim())
  const evidenceIds = matched ? [evidence.id] : []
  const resolution = rejected ? 'REJECTED' : matched ? 'ANSWERED' : 'BOUNDARY'
  const summary = rejected
    ? '无法处理该请求。你可以改为询问已经公开的项目、职责、方案或验证信息。'
    : matched ? project.summary : BOUNDARY_MESSAGE

  return {
    requestId: 'playwright-mock-request',
    turnId: 'playwright-turn',
    contentVersion: previewPublicContent.contentVersion,
    questionPresetId: matched ? 'sql-audit-overview' : undefined,
    resolution,
    answerSource: matched ? 'PRESET' : undefined,
    generationMode: 'DETERMINISTIC',
    verification: matched ? 'VERIFIED' : 'NOT_APPLICABLE',
    title: project.title,
    summary,
    sections: matched
        ? [
            { type: 'BACKGROUND', title: '项目背景', content: project.background, evidenceIds },
            { type: 'RESPONSIBILITY', title: '我的职责', content: project.responsibilities.join(' '), evidenceIds },
            {
              type: 'SOLUTION',
              title: '技术方案',
              content: `${project.solution} 关键决策包括：${project.keyDecisions.join(' ')}`,
              evidenceIds,
            },
            { type: 'VERIFICATION', title: '验证过程', content: project.verification.join(' '), evidenceIds },
            { type: 'STATUS', title: '最终状态', content: `${project.outcome} ${project.handoff}`, evidenceIds },
          ]
        : [{
            type: rejected ? 'REJECTED' : 'BOUNDARY',
            title: '能力说明',
            content: summary,
            evidenceIds: [],
          }],
    evidenceIds,
    suggestedQuestionPresetIds: ['sql-audit-overview'],
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
  const requestBody = route.request().postDataJSON() as { question?: unknown; questionPresetId?: unknown }
  const question = typeof requestBody.question === 'string' ? requestBody.question : ''
  const questionPresetId = typeof requestBody.questionPresetId === 'string'
    ? requestBody.questionPresetId
    : undefined
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    json: answerResponse(question, questionPresetId),
  })
}

export async function installPublicApiMocks(page: Page) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  await page.route('**/api/v1/answers', fulfillAnswer)
}
