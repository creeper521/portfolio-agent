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

function answerResponse(question: string) {
  const project = previewPublicContent.projects[0]
  const evidence = previewPublicContent.evidence[0]
  const matched = QUESTION_ALIASES.has(question.trim())

  return {
    requestId: 'playwright-mock-request',
    answerMode: 'DETERMINISTIC',
    matched,
    fallback: false,
    answer: {
      title: project.title,
      sections: matched
        ? [
            { type: 'BACKGROUND', content: project.background },
            { type: 'RESPONSIBILITY', content: project.responsibilities.join(' ') },
            {
              type: 'SOLUTION',
              content: `${project.solution} 关键决策包括：${project.keyDecisions.join(' ')}`,
            },
            { type: 'VERIFICATION', content: project.verification.join(' ') },
            { type: 'STATUS', content: `${project.outcome} ${project.handoff}` },
          ]
        : [{ type: 'BOUNDARY', content: BOUNDARY_MESSAGE }],
    },
    evidence: matched
      ? [
          {
            id: evidence.id,
            title: evidence.title,
            type: evidence.type,
            periodStart: evidence.periodStart,
            periodEnd: evidence.periodEnd,
            sourceCount: evidence.sourceCount,
            summary: evidence.summary,
            supportedClaims: evidence.supportedClaims,
            publicStatus: evidence.publicStatus,
            rawContentPublic: false,
          },
        ]
      : [],
    suggestedQuestions: [CANONICAL_QUESTION],
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
  const requestBody = route.request().postDataJSON() as { question?: unknown }
  const question = typeof requestBody.question === 'string' ? requestBody.question : ''
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    json: answerResponse(question),
  })
}

export async function installPublicApiMocks(page: Page) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  await page.route('**/api/v1/answers', fulfillAnswer)
}
