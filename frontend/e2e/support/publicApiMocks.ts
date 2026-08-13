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
  eventsNamed(eventName: string): Record<string, unknown>[]
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
const FOCUSED_VERIFICATION_QUESTION = '这个项目的验证过程是怎样的？'
const COMPARISON_QUESTION = '比较一下 SQL 审计项目和图片上传项目'
const RECOMMENDATION_QUESTION = '推荐两个适合后端面试展示的作品'
const BOUNDARY_MESSAGE =
  '当前版本只稳定支持项目完整介绍问题。你可以使用下方推荐问题了解项目背景、我的职责、技术方案、验证过程和最终状态。'
const GAP_BOUNDARY_MESSAGE = '当前公开材料未覆盖最终状态。'

// P3 mock helpers（handoff §4–§13）。这些字段与旧 P2 字段并存，便于过渡双读。
const MOCK_RESUME_TOKEN = 'opaque-mock-resume-token-e2e'

function sampleSourceReferences(evidenceId: string, projectSlug: string) {
  return [{
    referenceKey: 'SRC_SQL_AUDIT_DELIVERED',
    label: 'SQL 审计 · 交付证据',
    sourceType: 'DOCUMENT',
    subjectRoute: `/projects/${projectSlug}`,
    evidenceRoute: `/evidence?evidence=${evidenceId}`,
    publishedVersion: previewPublicContent.contentVersion,
  }]
}

/**
 * 给 mock 答案叠加 P3 公共契约字段（responseKind / conversation / sourceReferences /
 * completedTasks.contextHandle / execution）。旧 P2 字段保留，便于过渡双读。
 */
function withP3Fields(response: Record<string, unknown>): Record<string, unknown> {
  const augmented = { ...response }
  if (augmented.responseKind === undefined) augmented.responseKind = 'ANSWER'
  if (augmented.conversation === undefined) {
    augmented.conversation = { resumeToken: MOCK_RESUME_TOKEN, continuationStatus: 'AVAILABLE' }
  }
  // 给 blocks 追加 sourceReferences。
  if (Array.isArray(augmented.blocks)) {
    augmented.blocks = (augmented.blocks as Array<Record<string, unknown>>).map((block) => {
      if (Array.isArray(block.sourceReferences) && block.sourceReferences.length) return block
      const evidenceId = Array.isArray(block.evidenceIds) && block.evidenceIds.length
        ? String(block.evidenceIds[0])
        : ''
      const projectSlug = block.sourceScope === 'GENERAL' ? 'sql-audit' : 'sql-audit'
      return {
        ...block,
        ...(evidenceId ? { sourceReferences: sampleSourceReferences(evidenceId, projectSlug) } : {}),
      }
    })
  }
  // 给 agentTurn.completedTasks 追加 contextHandle 与 execution 快照。
  const agentTurn = augmented.agentTurn as Record<string, unknown> | undefined
  if (agentTurn && Array.isArray(agentTurn.completedTasks)) {
    agentTurn.completedTasks = (agentTurn.completedTasks as Array<Record<string, unknown>>).map(
      (task, index) => ({
        ...task,
        ...(task.contextHandle === undefined ? { contextHandle: `handle-mock-${index}` } : {}),
      }),
    )
    if (agentTurn.execution === undefined) {
      agentTurn.execution = {
        contractVersion: 'p3-display-v1',
        snapshotType: 'FINAL',
        overallStatus: 'COMPLETED',
        tasks: [{
          displayIndex: '01',
          finalStatus: 'COMPLETED',
          stages: [
            { code: 'SCOPE_CONFIRMED', label: '确认查询范围', status: 'COMPLETED' },
            { code: 'MATERIALS_RETRIEVED', label: '查找已发布材料', status: 'COMPLETED' },
            { code: 'EVIDENCE_VALIDATED', label: '核验证据', status: 'COMPLETED' },
            { code: 'RESULT_COMPOSED', label: '形成回答', status: 'COMPLETED' },
          ],
        }],
      }
    }
  }
  return augmented
}

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
  const focused = !questionPresetId && question.trim() === FOCUSED_VERIFICATION_QUESTION
  const comparison = !questionPresetId && /比较.*(项目|案例)/.test(question)
  const recommendation = !questionPresetId && question.trim() === RECOMMENDATION_QUESTION
  const unsupported = !questionPresetId && /提升了多少性能|性能提升/.test(question)
  const followUp = Boolean(referenceContext)
  const answered = matched || retrieved || focused || comparison || recommendation || followUp
  const evidenceIds = answered ? [evidence.id] : []
  const resolution = rejected ? 'REJECTED' : unsupported ? 'NOT_SUPPORTED' : answered ? 'ANSWERED' : 'BOUNDARY'
  const summary = rejected
    ? '无法处理该请求。你可以改为询问已经公开的项目、职责、方案或验证信息。'
    : unsupported ? '当前公开内容中没有足够的已验证材料。'
      : answered ? project.summary : BOUNDARY_MESSAGE

  return {
    requestId: 'playwright-mock-request',
    turnId: 'playwright-turn',
    contentVersion: previewPublicContent.contentVersion,
    questionPresetId: matched ? 'sql-audit-overview' : undefined,
    resolution,
    answerScope: 'PORTFOLIO',
    answerSource: matched && !followUp ? 'PRESET' : answered ? 'RETRIEVAL' : undefined,
    generationMode: 'DETERMINISTIC',
    verification: matched && !followUp ? 'VERIFIED' : answered ? 'PARTIALLY_VERIFIED' : 'NOT_APPLICABLE',
    constructionMode: (recommendation || unsupported) ? 'EVIDENCE_COMPOSITION' : undefined,
    evidenceState: unsupported ? 'INSUFFICIENT' : answered ? 'VERIFIED' : 'INSUFFICIENT',
    title: project.title,
    summary: focused ? undefined : summary,
    // Comparison/Recommendation 尚未迁移，返回无类型旧 Block（模拟生产旧路径）
    blocks: unsupported
        ? [{
            sourceScope: 'PORTFOLIO',
            sectionType: 'BOUNDARY',
            title: '能力说明',
            content: '当前公开内容中没有足够的已验证材料。',
            claimIds: [],
            evidenceIds: [],
          }]
        : comparison
        ? [{
            sourceScope: 'PORTFOLIO',
            content: 'SQL 审计项目侧重数据库链路与故障排查，图片上传项目侧重文件生命周期管理。',
            claimIds: ['claim-sql-audit-delivered'],
            evidenceIds: [evidence.id],
          }]
        : recommendation
        ? [{
            sourceScope: 'PORTFOLIO',
            content: '已根据公开且经过验证的证据生成 2 项确定性推荐。',
            claimIds: [],
            evidenceIds: [evidence.id],
          }]
        : answered ? typedAnswerBlocks(project, evidence.id, matched, focused, followUp)
        : [{
            sourceScope: 'PORTFOLIO',
            sectionType: rejected ? 'REJECTED' : 'BOUNDARY',
            title: '能力说明',
            content: summary,
            claimIds: [],
            evidenceIds: [],
          }],
    evidenceIds,
    portfolioRecommendation: recommendation ? {
      recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      context: {
        recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        contentVersion: previewPublicContent.contentVersion,
        careerTrack: 'BACKEND',
        audienceRole: 'INTERVIEWER',
        capabilityCodes: ['POSTGRESQL'],
        requestedSize: 2,
        selectedPortfolioIds: ['sql-audit', 'image-upload'],
      },
      items: [
        {
          portfolioId: 'sql-audit',
          title: project.title,
          route: '/projects/sql-audit',
          matchReasons: ['展示后端数据链路与验证能力'],
          evidenceIds: [evidence.id],
        },
        {
          portfolioId: 'image-upload',
          title: '图片上传与处理平台',
          route: '/projects/image-upload',
          matchReasons: ['补充文件生命周期与可靠性设计'],
          evidenceIds: [evidence.id],
        },
      ],
      satisfiedConstraints: ['BACKEND', 'POSTGRESQL'],
      unsatisfiedConstraints: [],
    } : undefined,
    suggestedQuestionPresetIds: ['sql-audit-overview'],
    referenceContext: answered && !comparison ? {
      previousContentVersion: previewPublicContent.contentVersion,
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
    } : undefined,
    contextVersionUpdated: false,
  }
}

function typedAnswerBlocks(
  project: typeof previewPublicContent.projects[number],
  evidenceId: string,
  overview: boolean,
  focusedVerification: boolean,
  followUp: boolean,
) {
  if (focusedVerification) {
    return [{
      sourceScope: 'PORTFOLIO',
      sectionType: 'VERIFICATION',
      title: '验证过程',
      content: project.verification.join(' '),
      claimIds: ['claim-sql-audit-delivered'],
      evidenceIds: [evidenceId],
    }]
  }
  if (followUp) {
    return [
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        title: '技术方案',
        content: `${project.solution} 关键决策包括：${project.keyDecisions.join(' ')}`,
        claimIds: ['claim-sql-audit-delivered'],
        evidenceIds: [evidenceId],
      },
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'BOUNDARY',
        title: '边界与复盘',
        content: GAP_BOUNDARY_MESSAGE,
        claimIds: [],
        evidenceIds: [],
      },
    ]
  }
  if (overview) {
    return [
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'BACKGROUND',
        title: '项目背景',
        content: project.background,
        claimIds: ['claim-sql-audit-delivered'],
        evidenceIds: [evidenceId],
      },
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'RESPONSIBILITY',
        title: '我的职责',
        content: project.responsibilities.join(' '),
        claimIds: ['claim-sql-audit-delivered'],
        evidenceIds: [evidenceId],
      },
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        title: '技术方案',
        content: `${project.solution} 关键决策包括：${project.keyDecisions.join(' ')}`,
        claimIds: ['claim-sql-audit-delivered'],
        evidenceIds: [evidenceId],
      },
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'VERIFICATION',
        title: '验证过程',
        content: project.verification.join(' '),
        claimIds: ['claim-sql-audit-delivered'],
        evidenceIds: [evidenceId],
      },
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'STATUS',
        title: '最终状态',
        content: `${project.outcome} ${project.handoff}`,
        claimIds: ['claim-sql-audit-delivered'],
        evidenceIds: [evidenceId],
      },
    ]
  }
  return [{
    sourceScope: 'PORTFOLIO',
    sectionType: 'STATUS',
    title: '最终状态',
    content: `${project.outcome} ${project.handoff}`,
    claimIds: ['claim-sql-audit-delivered'],
    evidenceIds: [evidenceId],
  }]
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
  const mockResponse = answerResponse(question, questionPresetId, requestBody.context?.referenceContext)
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    json: mockResponse,
  })
}

export async function installPublicApiMocks(page: Page) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  await installAnswerApiMock(page)
}

// P3：刷新恢复（GET）与主动清除（DELETE）的 conversation-context mock（handoff §11/§12）。
export async function installConversationContextMocks(
  page: Page,
  options: { status?: 'AVAILABLE' | 'CONTEXT_EXPIRED'; clearFails?: boolean } = {},
) {
  const status = options.status ?? 'AVAILABLE'
  const clearFails = options.clearFails === true
  await page.route('**/api/v2/conversation-context', async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      if (status === 'CONTEXT_EXPIRED') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          json: { contractVersion: 'p3-context-summary-v1', continuationStatus: 'CONTEXT_EXPIRED' },
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: {
          contractVersion: 'p3-context-summary-v1',
          continuationStatus: 'AVAILABLE',
          summary: {
            recentTaskType: 'FACT',
            subjectLabels: ['SQL 审计'],
            facetLabels: ['VERIFICATION'],
            comparisonDimensionLabels: [],
            preferenceLabels: [],
            canRefine: true,
          },
        },
      })
      return
    }
    if (method === 'DELETE') {
      if (clearFails) {
        await route.abort('failed')
        return
      }
      await route.fulfill({ status: 204 })
      return
    }
    await route.fallback()
  })
}

/**
 * P3 专用 mock：在公共内容 + 回答 mock 基础上叠加 P3 契约字段（responseKind /
 * conversation / sourceReferences / execution / contextHandle）与会话上下文 API。
 * 同时 mock 客户端诊断接口，避免 dev server 因后端未起而向 console 输出 ECONNREFUSED 噪声。
 * 旧 portfolio.spec.ts 继续使用 installPublicApiMocks（不带 P3 字段）以保持稳定。
 */
export async function installP3Mocks(
  page: Page,
  options: { contextStatus?: 'AVAILABLE' | 'CONTEXT_EXPIRED'; clearFails?: boolean } = {},
) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  // P3：诊断端点一并不返回 202，避免 dev server proxy 报 ECONNREFUSED 干扰 P3 用例。
  await installDiagnosticsApiMock(page)
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    const requestBody = route.request().postDataJSON() as {
      question?: unknown
      questionPresetId?: unknown
      context?: { referenceContext?: Record<string, unknown> }
    }
    const question = typeof requestBody.question === 'string' ? requestBody.question : ''
    const questionPresetId = typeof requestBody.questionPresetId === 'string'
      ? requestBody.questionPresetId
      : undefined
    const mockResponse = withP3Fields(
      answerResponse(question, questionPresetId, requestBody.context?.referenceContext) as Record<string, unknown>,
    )
    await route.fulfill({ status: 200, contentType: 'application/json', json: mockResponse })
  })
  await installConversationContextMocks(page, {
    status: options.contextStatus ?? 'AVAILABLE',
    clearFails: options.clearFails,
  })
}

export async function installAnswerApiMock(page: Page) {
  await page.route('**/api/v2/answers', fulfillAnswer)
}

/** Deterministic stp-v1 fixtures for the approved A–H interaction gate. */
export async function mockSemanticTurnStates(page: Page) {
  let turn = 0
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as {
      action?: string
      planAdjustment?: unknown
      clarificationResolution?: unknown
    }
    const response = semanticTurnResponse(
      turn,
      body.action === 'CONFIRM_PLAN',
      body.action === 'REGENERATE_PLAN',
      body.planAdjustment !== undefined,
      body.clarificationResolution !== undefined,
    )
    turn += 1
    await route.fulfill({ status: 200, contentType: 'application/json', json: response })
  })
}

function semanticTurnResponse(
  index: number,
  isConfirmation: boolean,
  isRegenerate: boolean,
  isAdjustment: boolean,
  isClarificationContinuation: boolean,
) {
  const response = {
    requestId: `semantic-request-${index}`,
    turnId: `semantic-turn-${index}`,
    contentVersion: previewPublicContent.contentVersion,
    resolution: 'ANSWERED',
    title: '',
    summary: '',
    sections: [],
    evidenceIds: [],
    suggestedQuestionPresetIds: [],
    suggestedQuestions: [],
  }
  if (index === 0) return {
    ...response,
    agentTurn: readySemanticTurn(1, 'HIDDEN', [completedTask('01', '状态 A 单任务', 'A only completed body')]),
  }
  if (index === 1) return {
    ...response,
    agentTurn: readySemanticTurn(2, 'COLLAPSED', [
      completedTask('01', '状态 B 审阅', 'B first completed body'),
      completedTask('02', '状态 B 比较', 'B second completed body'),
    ]),
  }
  if (index === 2) return {
    ...response,
    resolution: 'AWAITING_CONFIRMATION',
    agentTurn: confirmationSemanticTurn(5, 'semantic-confirmation'),
  }
  // 动作分支优先于序号分支：任何调整/确认/重生成/澄清提交都按动作语义响应，
  // 不依赖它们在测试脚本里的绝对顺序。
  if (isAdjustment) return {
    ...response,
    resolution: 'AWAITING_CONFIRMATION',
    agentTurn: confirmationSemanticTurn(4, `adjusted-confirmation-${index}`),
  }
  if (isConfirmation) return {
    ...response,
    agentTurn: readySemanticTurn(3, 'EXPANDED', [completedTask('01', '状态 F 已完成', 'F only completed body')], {
      answeredCount: 1,
      blockedCount: 1,
      notSupportedCount: 1,
      items: [
        taskStatus('01', '状态 F 已完成', 'COMPLETED'),
        taskStatus('02', '状态 F 被阻塞', 'BLOCKED', ['EXECUTION_DEPENDENCY_BLOCKED'], ['03']),
        taskStatus('03', '状态 F 证据不足', 'NOT_SUPPORTED', ['PORTFOLIO_EVIDENCE_INSUFFICIENT']),
      ],
    }),
  }
  if (isRegenerate) return {
    ...response,
    resolution: 'AWAITING_CONFIRMATION',
    agentTurn: confirmationSemanticTurn(4, `regenerated-confirmation-${index}`),
  }
  if (isClarificationContinuation) return {
    ...response,
    agentTurn: readySemanticTurn(2, 'EXPANDED', [completedTask('01', '继续完成的任务', 'F only completed body')]),
  }
  if (index === 5) return localClarificationSemanticResponse(response)
  if (index === 7) return clarificationSemanticResponse(response, 'CRITICAL', 0)
  if (index === 9) return {
    ...response,
    resolution: 'REJECTED',
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'REJECTED',
      planChange: {
        summary: '公开内容版本已变化，需要重新生成计划。',
        changeLabels: ['内容版本变化'],
        invalidatedPlanReference: {
          planId: 'plan-opaque',
          planFingerprint: 'sha256:opaque',
        },
      },
    },
  }
  return {
    ...response,
    resolution: 'BOUNDARY',
    summary: '这项请求不能在当前公开范围内继续。',
    agentTurn: { contractVersion: 'stp-v1', disposition: 'BOUNDARY' },
  }
}

function confirmationSemanticTurn(taskCount: number, confirmationId: string) {
  return {
    contractVersion: 'stp-v1',
    disposition: 'CONFIRMATION_REQUIRED',
    plan: displayPlan(taskCount),
    planConfirmation: {
      confirmationId,
      confirmationPlan: 'opaque-confirmation-envelope',
      planFingerprint: 'sha256:semantic-fingerprint',
      integrityToken: 'opaque-integrity-token',
      expiresAt: '2026-08-10T12:10:00Z',
      triggerCodes: ['TASK_COUNT_REQUIRES_CONFIRMATION'],
      pendingPlanReference: {
        planId: 'plan-pending-e2e',
        planFingerprint: 'sha256:semantic-fingerprint',
      },
    },
  }
}

function localClarificationSemanticResponse(response: Record<string, unknown>) {
  return {
    ...response,
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'PARTIAL_READY',
      plan: displayPlan(2),
      outcome: {
        planOutcome: 'PARTIAL',
        taskSummary: {
          displayMode: 'EXPANDED', totalCount: 2, answeredCount: 1,
          notSupportedCount: 1, emptyCount: 0, blockedCount: 1,
          failedCount: 0, cancelledCount: 0, degradedCount: 0,
          items: [taskStatus('01', 'D 已完成任务', 'COMPLETED')],
        },
      },
      completedTasks: [completedTask('01', 'D 已完成任务', 'D only completed body')],
      clarification: {
        clarificationId: 'clarify-0000000000000000000000000000000d',
        scope: 'LOCAL',
        promptCode: 'ROUTING_COMPARISON_SUBJECT_MISSING',
        prompt: '请选择第二个比较对象',
        fields: [{
          fieldKey: 'comparisonSubject', inputMode: 'SINGLE_CHOICE', required: true,
          affectedGoalLabels: ['比较候选作品'],
          options: [{
            value: 'project-b',
            label: '项目 B',
            resolution: { kind: 'SUBJECT_REFERENCE', subjectType: 'PROJECT', subjectId: 'project-b' },
          }],
        }],
        blockedTaskCount: 1,
        continuingTaskCount: 1,
        continuingGoalLabels: ['D 已完成任务'],
        blockedGoals: [{ goalLabel: '比较候选作品', reasonCode: 'WAITING_FOR_COMPARISON_SUBJECT' }],
      },
    },
  }
}

function displayPlan(taskCount: number) {
  return {
    taskCount,
    executableTaskCount: taskCount,
    summaryLabel: '从了解到推荐',
    constraints: ['只基于已审核公开资料'],
    tasks: Array.from({ length: taskCount }, (_, index) => ({
      displayIndex: String(index + 1).padStart(2, '0'),
      goalLabel: `任务 ${index + 1}`,
      sourceDomain: 'PORTFOLIO',
      dependencySummary: index === 0 ? undefined : `需要 ${String(index).padStart(2, '0')} 先完成`,
    })),
  }
}

function completedTask(displayIndex: string, goalLabel: string, content: string) {
  return {
    displayIndex,
    goalLabel,
    sourceDomain: 'PORTFOLIO',
    resultPayload: {
      kind: 'SECTION_RESULT',
      blocks: [{ sourceScope: 'PORTFOLIO', sectionType: 'SOLUTION', title: goalLabel, content, claimIds: [], evidenceIds: [] }],
    },
  }
}

function taskStatus(
  displayIndex: string,
  goalLabel: string,
  status: string,
  reasonCodes: string[] = [],
  blockedByDisplayIndexes: string[] = [],
) {
  return { displayIndex, goalLabel, status, sourceDomain: 'PORTFOLIO', reasonCodes, blockedByDisplayIndexes }
}

function readySemanticTurn(
  totalCount: number,
  displayMode: 'HIDDEN' | 'COLLAPSED' | 'EXPANDED',
  completedTasks: ReturnType<typeof completedTask>[],
  counts: Partial<{ answeredCount: number; blockedCount: number; notSupportedCount: number; items: ReturnType<typeof taskStatus>[] }> = {},
) {
  const answeredCount = counts.answeredCount ?? completedTasks.length
  return {
    contractVersion: 'stp-v1',
    disposition: 'READY',
    plan: displayPlan(totalCount),
    outcome: {
      planOutcome: totalCount === answeredCount ? 'SUCCEEDED' : 'PARTIAL',
      taskSummary: {
        displayMode,
        totalCount,
        answeredCount,
        notSupportedCount: counts.notSupportedCount ?? 0,
        emptyCount: 0,
        blockedCount: counts.blockedCount ?? 0,
        failedCount: 0,
        cancelledCount: 0,
        degradedCount: 0,
        items: counts.items ?? completedTasks.map((task) => taskStatus(task.displayIndex, task.goalLabel, 'COMPLETED')),
      },
    },
    completedTasks,
  }
}

function clarificationSemanticResponse(
  response: Record<string, unknown>,
  scope: 'LOCAL' | 'CRITICAL',
  continuingTaskCount: number,
) {
  return {
    ...response,
    resolution: 'NEEDS_CLARIFICATION',
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'CLARIFICATION_REQUIRED',
      plan: displayPlan(2),
      clarification: {
        clarificationId: 'clarify-0000000000000000000000000000000e',
        scope,
        promptCode: 'ROUTING_SUBJECT_CLARIFICATION_REQUIRED',
        prompt: '请选择第二个比较对象',
        fields: [{
          fieldKey: 'comparisonSubject',
          inputMode: 'SINGLE_CHOICE',
          required: true,
          affectedGoalLabels: ['比较候选作品'],
          options: [{
            value: 'project-b',
            label: '项目 B',
            resolution: { kind: 'SUBJECT_REFERENCE', subjectType: 'PROJECT', subjectId: 'project-b' },
          }],
        }],
        blockedTaskCount: 2 - continuingTaskCount,
        continuingTaskCount,
        continuingGoalLabels: continuingTaskCount > 0 ? ['已完成的独立任务'] : [],
        blockedGoals: [{ goalLabel: '比较候选作品', reasonCode: 'WAITING_FOR_COMPARISON_SUBJECT' }],
      },
    },
  }
}

export async function installAnswerScenarioMock(page: Page, scenario: AnswerScenario) {
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    scenario.onRequest?.(await route.request().allHeaders())
    if (scenario.delayMilliseconds) {
      const aborted = await waitForAbortOrTimeout(
        page,
        route.request(),
        scenario.delayMilliseconds,
      )
      if (aborted) {
        // The caller cancelled the fetch; Playwright never rejects the page-side
        // promise on its own while the route handler is suspended, so let the
        // browser abort semantics complete the request lifecycle.
        return
      }
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

async function waitForAbortOrTimeout(
  page: Page,
  request: import('@playwright/test').Request,
  delayMilliseconds: number,
): Promise<boolean> {
  let aborted = false
  let listener: ((failedRequest: import('@playwright/test').Request) => void) | undefined
  const abortPromise = new Promise<boolean>((resolve) => {
    listener = (failedRequest: import('@playwright/test').Request) => {
      if (failedRequest === request) {
        page.off('requestaborted', listener)
        aborted = true
        resolve(true)
      }
    }
    page.on('requestaborted', listener)
  })
  const timeoutPromise = new Promise<boolean>((resolve) => {
    setTimeout(() => resolve(false), delayMilliseconds)
  })
  try {
    return await Promise.race([abortPromise, timeoutPromise])
  } finally {
    if (!aborted && listener) {
      page.off('requestaborted', listener)
    }
  }
}

export async function installDiagnosticsApiMock(
  page: Page,
  options: DiagnosticsMockOptions = {},
): Promise<DiagnosticsCapture> {
  const capture: DiagnosticsCapture = {
    attempts: 0,
    bodies: [],
    events: [],
    eventsNamed(eventName: string): Record<string, unknown>[] {
      return this.events.filter((event) => event.eventName === eventName)
    },
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
