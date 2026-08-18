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
  // 存量修复（体验闭环验证时发现）：应用侧 follow-up 早已不发送 context.referenceContext，
  // 改按受控 follow-up 问句前缀识别（展开/说明/查看状态/查看相关/对比），维持旧断言语义。
  const followUp = Boolean(referenceContext)
    || (!questionPresetId && /^(展开|说明|查看当前状态|查看相关问题|对比这些项目)/.test(question.trim()))
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
    // P-1 合同对齐：纯数字噪声（如 112233）返回受控澄清而非 BOUNDARY 能力说明，
    // 与后端热修后的真实合同一致（行为 UI 基线「112233 is clarified」）。
    if (questionPresetId === undefined && /^\d+$/.test(question.trim())) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: withP3Fields(noiseClarificationResponse()),
      })
      return
    }
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

/**
 * P-1：纯数字噪声（如 112233）的受控澄清响应——无 Evidence、无来源、
 * 只提供公开主体选项；与后端热修后的合同一致（澄清不带公开来源）。
 */
function noiseClarificationResponse(): Record<string, unknown> {
  return {
    requestId: 'request-noise-clarification',
    turnId: 'turn-noise-clarification',
    contentVersion: previewPublicContent.contentVersion,
    resolution: 'NEEDS_CLARIFICATION',
    answerScope: 'PORTFOLIO',
    intentSource: 'RULE',
    evidenceState: 'NOT_REQUIRED',
    generationMode: 'DETERMINISTIC',
    verification: 'NOT_APPLICABLE',
    title: '',
    summary: '这个问题还无法对应到公开作品集内容。',
    sections: [],
    blocks: [],
    evidenceIds: [],
    suggestedQuestions: [],
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'CLARIFICATION_REQUIRED',
      clarification: {
        clarificationId: 'clarify-noise-0123456789abcdef0123456789abcdef',
        scope: 'CRITICAL',
        promptCode: 'ROUTING_SUBJECT_CLARIFICATION_REQUIRED',
        prompt: '请说明或选择你想了解的公开项目',
        fields: [{
          fieldKey: 'subject',
          inputMode: 'SINGLE_CHOICE',
          options: previewPublicContent.projects.map((project) => ({
            value: project.slug,
            label: project.title,
            resolution: { kind: 'SUBJECT_REFERENCE', subjectType: 'PROJECT', subjectId: project.slug },
          })),
          required: true,
          affectedGoalLabels: [],
        }],
        blockedTaskCount: 1,
        continuingTaskCount: 0,
        continuingGoalLabels: [],
        blockedGoals: [],
      },
      reasonCodes: [],
    },
  }
}

// ── P4 任务级 composition Mock（设计 §11 / handoff §2/§6）──────────────────────
// 后端 P4 公共契约尚未落地，前端按冻结文档契约以 Mock 验收三类表达来源：
// MODEL_GROUNDED / FALLBACK / MIXED。三种使用同一套章节、引用与 Evidence Desk。
// 顶层 generation/construction 模式按 §11.3 聚合表赋值。
export type P4CompositionScenario = 'MODEL_GROUNDED' | 'FALLBACK' | 'MIXED'

function p4SourceReferences(): Array<Record<string, unknown>> {
  return [{
    referenceKey: 'SRC_SQL_AUDIT_P4',
    label: 'SQL 审计 · 交付证据',
    sourceType: 'DOCUMENT',
    subjectRoute: '/projects/sql-audit',
    evidenceRoute: '/evidence?evidence=sql-audit-delivery-set',
    publishedVersion: previewPublicContent.contentVersion,
  }]
}

function p4CompletedTask(
  displayIndex: string,
  goalLabel: string,
  content: string,
  composition: { mode: string; degraded: boolean },
): Record<string, unknown> {
  return {
    displayIndex,
    goalLabel,
    sourceDomain: 'PORTFOLIO',
    composition,
    resultPayload: {
      kind: 'SECTION_RESULT',
      blocks: [{
        sourceScope: 'PORTFOLIO',
        sectionType: 'SOLUTION',
        title: goalLabel,
        content,
        claimIds: [],
        evidenceIds: [],
        sourceReferences: p4SourceReferences(),
      }],
    },
  }
}

function p4Execution(taskCount: number): Record<string, unknown> {
  // 仍是 P3 四阶段（handoff §4）；P4 的 Provider/Draft/Validator 是 RESULT_COMPOSED 内部实现。
  return {
    contractVersion: 'p3-display-v1',
    snapshotType: 'FINAL',
    overallStatus: 'COMPLETED',
    tasks: Array.from({ length: taskCount }, (_, index) => ({
      displayIndex: String(index + 1).padStart(2, '0'),
      finalStatus: 'COMPLETED',
      stages: [
        { code: 'SCOPE_CONFIRMED', label: '确认查询范围', status: 'COMPLETED' },
        { code: 'MATERIALS_RETRIEVED', label: '查找已发布材料', status: 'COMPLETED' },
        { code: 'EVIDENCE_VALIDATED', label: '核验证据', status: 'COMPLETED' },
        { code: 'RESULT_COMPOSED', label: '形成回答', status: 'COMPLETED' },
      ],
    })),
  }
}

function p4CompositionResponse(scenario: P4CompositionScenario): Record<string, unknown> {
  const project = previewPublicContent.projects[0]
  let generationMode: string
  let constructionMode: string
  let degraded: boolean
  let completedTasks: Array<Record<string, unknown>>
  if (scenario === 'MODEL_GROUNDED') {
    generationMode = 'MODEL'
    constructionMode = 'MODEL_GROUNDED'
    degraded = false
    completedTasks = [p4CompletedTask(
      '01', 'SQL 审计项目说明',
      `${project.solution} 模型受控表达正文。`,
      { mode: 'MODEL_GROUNDED', degraded: false },
    )]
  } else if (scenario === 'FALLBACK') {
    generationMode = 'FALLBACK'
    constructionMode = 'EVIDENCE_COMPOSITION'
    degraded = true
    completedTasks = [p4CompletedTask(
      '01', 'SQL 审计项目说明',
      `${project.solution} 确定性 fallback 正文。`,
      { mode: 'FALLBACK', degraded: true },
    )]
  } else {
    generationMode = 'MIXED'
    constructionMode = 'MIXED_COMPOSITION'
    degraded = false
    completedTasks = [
      p4CompletedTask(
        '01', 'SQL 审计项目说明',
        `${project.solution} 确定性正文。`,
        { mode: 'DETERMINISTIC', degraded: false },
      ),
      p4CompletedTask(
        '02', 'SQL 审计验证过程',
        `${project.verification.join(' ')} 模型受控表达正文。`,
        { mode: 'MODEL_GROUNDED', degraded: false },
      ),
    ]
  }
  return {
    requestId: 'playwright-p4-mock',
    turnId: 'playwright-p4-turn',
    contentVersion: previewPublicContent.contentVersion,
    responseKind: 'ANSWER',
    resolution: 'ANSWERED',
    answerScope: 'PORTFOLIO',
    generationMode,
    constructionMode,
    verification: 'VERIFIED',
    evidenceState: 'VERIFIED',
    degraded,
    title: project.title,
    summary: '',
    sections: [],
    blocks: [],
    evidenceIds: [],
    suggestedQuestionPresetIds: [],
    suggestedQuestions: [],
    conversation: { resumeToken: MOCK_RESUME_TOKEN, continuationStatus: 'AVAILABLE' },
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'READY',
      outcome: {
        taskSummary: {
          displayMode: 'EXPANDED',
          totalCount: completedTasks.length,
          answeredCount: completedTasks.length,
          notSupportedCount: 0,
          emptyCount: 0,
          blockedCount: 0,
          failedCount: 0,
          cancelledCount: 0,
          degradedCount: scenario === 'FALLBACK' ? 1 : 0,
          items: completedTasks.map((task) => ({
            displayIndex: task.displayIndex,
            goalLabel: task.goalLabel,
            status: 'COMPLETED',
            sourceDomain: 'PORTFOLIO',
          })),
        },
      },
      completedTasks,
      execution: p4Execution(completedTasks.length),
    },
  }
}

/**
 * P4 表达来源 Mock：在公共内容 + 诊断 + 会话上下文基础上，按场景返回
 * MODEL_GROUNDED / FALLBACK / MIXED 的 ready 语义轮次。用于前端契约验收，
 * 不代表真实 Provider 已验收或部署已启用（设计 §20 状态阶梯）。
 */
export async function installP4CompositionMocks(page: Page, scenario: P4CompositionScenario) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  await installDiagnosticsApiMock(page)
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: p4CompositionResponse(scenario),
    })
  })
  await installConversationContextMocks(page, { status: 'AVAILABLE' })
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

// ── 体验闭环（2026-08-17 交接规格 §11 场景 A–F）──────────────────────────────
// 后端确定性路由闭环字段（requestedSize/actualSize/reasonCodes 等）尚未上线，
// 此处按冻结契约出合同 fixture：噪声澄清、1/3 部分推荐、2/2 完整推荐与句柄续接。
// 注意：这些响应不经 withP3Fields 加工，sourceReferences 保持缺省，
// 以便验证旧 evidenceId 回退路径上的「E-01 · 标题」引用渲染。

export interface ClosureRequestLogEntry {
  question: string
  contextReference?: {
    contextHandle?: string
    expectedContextType?: string
    resultItemId?: string
  }
}

const CLOSURE_HANDLE = 'reco-handle-closure'
const CLOSURE_CONTENT_VERSION = previewPublicContent.contentVersion

// 推荐候选（preview 公开数据只有 1 个 Project；合成另外两个公开作品条目，
// 仅供推荐卡渲染，路由均指向站内公开页面）。
const CLOSURE_RECO_CANDIDATES = [
  { slug: 'sql-audit', title: 'SQL 审计与故障排查工具', evidenceId: 'sql-audit-delivery-set' },
  { slug: 'codegraph-evaluation', title: '代码图谱工具端到端评测', evidenceId: 'evidence-case-codegraph' },
  { slug: 'multilingual-image-preservation', title: '多语言图片上传保留修复', evidenceId: 'evidence-case-multilingual' },
]

function closureEnvelope(body: Record<string, unknown>): Record<string, unknown> {
  return {
    responseKind: 'ANSWER',
    conversation: { continuationStatus: 'AVAILABLE' },
    ...body,
  }
}

function closureNoiseClarification(): Record<string, unknown> {
  return closureEnvelope({
    requestId: 'request-closure-noise',
    turnId: 'turn-closure-noise',
    contentVersion: CLOSURE_CONTENT_VERSION,
    resolution: 'NEEDS_CLARIFICATION',
    answerScope: 'PORTFOLIO',
    intentSource: 'RULE',
    evidenceState: 'NOT_REQUIRED',
    generationMode: 'DETERMINISTIC',
    verification: 'NOT_APPLICABLE',
    title: '',
    summary: '',
    sections: [],
    blocks: [],
    evidenceIds: [],
    suggestedQuestions: [],
    agentTurn: {
      contractVersion: 'stp-v2',
      disposition: 'CLARIFICATION_REQUIRED',
      clarification: {
        clarificationId: 'clarify-closure-0123456789abcdef0123456789abcdef',
        scope: 'CRITICAL',
        promptCode: 'ROUTING_INPUT_UNFORMED',
        prompt: '想了解什么？可以说明要了解、比较还是推荐作品。',
        fields: [],
        blockedTaskCount: 0,
        continuingTaskCount: 0,
        continuingGoalLabels: [],
        blockedGoals: [],
      },
    },
  })
}

function closureExecution(finalStatus: string, overallStatus: string): Record<string, unknown> {
  return {
    contractVersion: 'p3-display-v1',
    snapshotType: 'FINAL',
    overallStatus,
    tasks: [{
      displayIndex: '01',
      finalStatus,
      stages: [
        { code: 'SCOPE_CONFIRMED', label: '确认查询范围', status: 'COMPLETED' },
        { code: 'MATERIALS_RETRIEVED', label: '查找已发布材料', status: 'COMPLETED' },
        { code: 'EVIDENCE_VALIDATED', label: '核验证据', status: 'COMPLETED' },
        { code: 'RESULT_COMPOSED', label: '形成回答', status: finalStatus },
      ],
    }],
  }
}

function closurePresetAnswer(): Record<string, unknown> {
  return closureEnvelope({
    requestId: 'request-closure-preset',
    turnId: 'turn-closure-preset',
    contentVersion: CLOSURE_CONTENT_VERSION,
    questionPresetId: 'sql-audit-overview',
    contractVersion: 'pcv1-0123456789abcdef',
    resolution: 'ANSWERED',
    answerScope: 'PORTFOLIO',
    intentSource: 'PRESET',
    evidenceState: 'VERIFIED',
    generationMode: 'DETERMINISTIC',
    constructionMode: 'EVIDENCE_COMPOSITION',
    verification: 'VERIFIED',
    title: 'SQL 审计与故障排查工具',
    summary: '该工具以命令行交付，覆盖固定路径 SQL 查询、慢查询归档与审计报表三类日常任务。',
    blocks: [
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'BACKGROUND',
        title: '交付概览',
        content: '该工具以命令行交付，覆盖固定路径 SQL 查询、慢查询归档与审计报表三类日常任务。',
        evidenceIds: ['sql-audit-delivery-set'],
        claimIds: ['claim-sql-audit-delivered'],
      },
      {
        sourceScope: 'PORTFOLIO',
        sectionType: 'RESPONSIBILITY',
        title: '责任与边界',
        content: '本人独立负责查询解析与报表模块；审计策略沿用团队既有约定，不直接修改数据库。',
        evidenceIds: ['sql-audit-delivery-set'],
        claimIds: ['claim-sql-audit-delivered'],
      },
    ],
    evidenceIds: ['sql-audit-delivery-set'],
    suggestedQuestions: [],
    coveredTopics: ['BACKGROUND'],
    agentTurn: {
      contractVersion: 'stp-v2',
      disposition: 'READY',
      outcome: {},
      completedTasks: [{
        displayIndex: '01',
        goalLabel: '介绍 SQL 审计与故障排查工具项目',
        sourceDomain: 'PORTFOLIO',
        contextHandle: 'fact-handle-closure',
        resultPayload: {
          kind: 'SECTION_RESULT',
          blocks: [{
            sourceScope: 'PORTFOLIO',
            sectionType: 'BACKGROUND',
            title: '交付概览',
            content: '该工具以命令行交付，覆盖固定路径 SQL 查询、慢查询归档与审计报表三类日常任务。',
            evidenceIds: ['sql-audit-delivery-set'],
            claimIds: [],
          }],
        },
      }],
      execution: closureExecution('COMPLETED', 'COMPLETED'),
    },
  })
}

function closureRecommendationAnswer(
  requestedSize: number,
  actualSize: number,
): Record<string, unknown> {
  const partial = actualSize < requestedSize
  const items = CLOSURE_RECO_CANDIDATES.slice(0, Math.max(actualSize, 0)).map(
    (candidate, index) => ({
      portfolioId: candidate.slug,
      title: candidate.title,
      route: `/projects/${candidate.slug}`,
      matchReasons: ['完整交付闭环与已验证证据'],
      evidenceIds: [candidate.evidenceId],
      resultItemId: `result-item-${index + 1}`,
      position: index + 1,
      subject: { subjectType: 'PROJECT', subjectId: candidate.slug },
    }),
  )
  return closureEnvelope({
    requestId: `request-closure-reco-${requestedSize}-${actualSize}`,
    turnId: `turn-closure-reco-${requestedSize}-${actualSize}`,
    contentVersion: CLOSURE_CONTENT_VERSION,
    resolution: partial ? 'PARTIALLY_ANSWERED' : 'ANSWERED',
    answerScope: 'PORTFOLIO',
    intentSource: 'RULE',
    evidenceState: partial ? 'INSUFFICIENT' : 'VERIFIED',
    generationMode: 'DETERMINISTIC',
    constructionMode: 'EVIDENCE_COMPOSITION',
    verification: partial ? 'PARTIALLY_VERIFIED' : 'VERIFIED',
    title: '',
    summary: partial
      ? '已找到 1 个符合条件的项目，其余公开项目的证据完整度暂不足。'
      : '已根据公开且经过验证的证据完成推荐。',
    blocks: [],
    sections: [],
    evidenceIds: [],
    suggestedQuestions: [],
    coveredTopics: [],
    agentTurn: {
      contractVersion: 'stp-v2',
      disposition: partial ? 'PARTIAL_READY' : 'READY',
      outcome: {},
      completedTasks: [{
        displayIndex: '01',
        goalLabel: `推荐 ${requestedSize} 个公开项目`,
        sourceDomain: 'PORTFOLIO',
        contextHandle: CLOSURE_HANDLE,
        resultPayload: {
          kind: 'RECOMMENDATION_RESULT',
          recommendations: items,
          requestedSize,
          actualSize,
          candidateScope: 'ALL_PUBLISHED_PROJECTS',
          reasonCodes: partial ? ['INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS'] : [],
          unsatisfiedConstraints: partial ? ['其余公开项目的证据完整度暂不足'] : [],
        },
      }],
      execution: closureExecution(partial ? 'PARTIAL' : 'COMPLETED', partial ? 'PARTIAL' : 'COMPLETED'),
    },
  })
}

function closureSecondItemAnswer(): Record<string, unknown> {
  const project = CLOSURE_RECO_CANDIDATES[1] ?? CLOSURE_RECO_CANDIDATES[0]
  return closureEnvelope({
    requestId: 'request-closure-second',
    turnId: 'turn-closure-second',
    contentVersion: CLOSURE_CONTENT_VERSION,
    resolution: 'ANSWERED',
    answerScope: 'PORTFOLIO',
    intentSource: 'REFERENCE',
    evidenceState: 'VERIFIED',
    generationMode: 'DETERMINISTIC',
    constructionMode: 'EVIDENCE_COMPOSITION',
    verification: 'VERIFIED',
    title: '',
    summary: `已定位推荐结果中的第二项：${project.title}。`,
    blocks: [{
      sourceScope: 'PORTFOLIO',
      sectionType: 'BACKGROUND',
      title: '第二项定位',
      content: `通过你上一轮的推荐结果定位到第二项：${project.title}。该项目材料均已通过公开审核。`,
      evidenceIds: [project.evidenceId],
      claimIds: [],
    }],
    evidenceIds: [project.evidenceId],
    suggestedQuestions: [],
    coveredTopics: [],
    agentTurn: {
      contractVersion: 'stp-v2',
      disposition: 'READY',
      outcome: {},
      completedTasks: [{
        displayIndex: '01',
        goalLabel: '介绍推荐结果中的第二项',
        sourceDomain: 'PORTFOLIO',
        contextHandle: 'fact-handle-closure',
        resultPayload: {
          kind: 'SECTION_RESULT',
          blocks: [{
            sourceScope: 'PORTFOLIO',
            sectionType: 'BACKGROUND',
            title: '第二项定位',
            content: `通过你上一轮的推荐结果定位到第二项：${project.title}。该项目材料均已通过公开审核。`,
            evidenceIds: [],
            claimIds: [],
          }],
        },
      }],
      execution: closureExecution('COMPLETED', 'COMPLETED'),
    },
  })
}

/**
 * 体验闭环合同 fixture 安装器（场景 A–F）。
 * onRequest 回调暴露每次 /answers 请求的问题与 contextReference（用于句柄断言）。
 */
export async function installExperienceClosureMocks(
  page: Page,
  options: { onRequest?: (entry: ClosureRequestLogEntry) => void } = {},
) {
  await page.route('**/api/v1/public-content', fulfillPublicContent)
  await installDiagnosticsApiMock(page)
  await page.route('**/api/v2/answers', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as {
      question?: unknown
      contextReference?: Record<string, unknown>
    }
    const question = typeof body.question === 'string' ? body.question.trim() : ''
    const rawReference = body.contextReference
    options.onRequest?.({
      question,
      contextReference: rawReference === undefined ? undefined : {
        contextHandle: typeof rawReference.contextHandle === 'string' ? rawReference.contextHandle : undefined,
        expectedContextType: typeof rawReference.expectedContextType === 'string' ? rawReference.expectedContextType : undefined,
        resultItemId: typeof rawReference.resultItemId === 'string' ? rawReference.resultItemId : undefined,
      },
    })
    let response: Record<string, unknown>
    if (/^\d+$/.test(question)) {
      response = closureNoiseClarification()
    } else if (question === '给我推荐三个项目') {
      response = closureRecommendationAnswer(3, 1)
    } else if (question === '给我推荐两个项目' || question === '放宽条件重新推荐') {
      response = closureRecommendationAnswer(2, 2)
    } else if (question === '第二个呢') {
      response = closureSecondItemAnswer()
    } else {
      response = closurePresetAnswer()
    }
    await route.fulfill({ status: 200, contentType: 'application/json', json: response })
  })
  await installConversationContextMocks(page, { status: 'AVAILABLE' })
}
