import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { PortfolioApiError } from '../../portfolio/api/portfolioApi'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import { WORKSPACE_SPLIT_KEY } from '../composables/useWorkspaceSplit'
import {
  confirmationRequiredResponse,
  criticalClarificationResponse,
  invalidatedPlanResponse,
  localPartialReadyResponse,
  partialSuccessResponse,
  shortTextClarificationResponse,
} from '../model/semanticTurnFixtures'
import AgentWorkspace from './AgentWorkspace.vue'
import ConversationThread from './ConversationThread.vue'

const SESSION_KEY = 'forbidden-session-key'

const { askQuestionMock } = vi.hoisted(() => ({
  askQuestionMock: vi.fn(),
}))

vi.mock('../api/answerApi', () => ({
  askQuestion: askQuestionMock,
}))

function answerResponse(
  evidenceId = 'sql-audit-delivery-set',
  title = '项目说明',
) {
  return {
    requestId: 'request-1',
    turnId: 'turn-1',
    contentVersion: '2026-07-21',
    questionPresetId: 'sql-audit-overview',
    resolution: 'ANSWERED' as const,
    constructionMode: 'EVIDENCE_COMPOSITION' as const,
    intentSource: 'PRESET' as const,
    evidenceState: 'VERIFIED' as const,
    answerSource: 'PRESET' as const,
    generationMode: 'DETERMINISTIC' as const,
    verification: 'VERIFIED' as const,
    title,
    summary: '公开摘要',
    sections: [
      { type: 'BACKGROUND' as const, title: '背景', content: '背景内容', evidenceIds: [evidenceId], claimIds: ['claim-sql-audit-delivered'] },
      { type: 'VERIFICATION' as const, title: '验证', content: '验证内容', evidenceIds: [evidenceId], claimIds: ['claim-sql-audit-delivered'] },
    ],
    evidenceIds: [evidenceId],
    suggestedQuestionPresetIds: ['sql-audit-overview'],
    coveredTopics: ['BACKGROUND'],
    guidanceStage: 'OPENING',
    suggestedQuestions: [
      {
        text: '当前项目追问',
        projectSlug: 'sql-audit',
        caseSlug: null,
        facet: 'IMPLEMENTATION' as const,
      },
      {
        text: '了解代码图谱评测项目',
        projectSlug: 'codegraph-evaluation',
        caseSlug: null,
        facet: 'OVERVIEW' as const,
      },
      {
        text: '开源评测案例怎么做的？',
        projectSlug: null,
        caseSlug: 'open-source-evaluation',
        facet: null,
      },
    ],
    referenceContext: {
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
      followUpAction: 'RELATED_QUESTION',
    },
  }
}

function v2AnswerResponse(
  intent = 'GENERAL_KNOWLEDGE',
  answerScope = 'GENERAL',
  blocks: Array<{ sourceScope: string; content: string; claimIds: string[]; evidenceIds: string[] }> = [],
) {
  return {
    requestId: 'request-v2',
    turnId: 'turn-v2',
    contentVersion: '2026-07-21',
    resolution: 'ANSWERED' as const,
    generationMode: 'MODEL' as const,
    verification: 'NOT_APPLICABLE' as const,
    intent,
    answerScope,
    title: '',
    summary: '',
    sections: [],
    blocks: blocks.length ? blocks : [
      { sourceScope: 'GENERAL' as const, content: '通用回答', claimIds: [], evidenceIds: [] },
    ],
    evidenceIds: [],
    suggestedQuestionPresetIds: [],
    suggestedQuestions: ['介绍一下 SQL 审计项目'],
  }
}

function portfolioWithSecondaryEvidence() {
  const core = previewPublicContent.evidence[0]!
  const secondary = {
    ...core,
    id: 'sql-audit-secondary',
    code: 'E-SECONDARY',
    title: '次级证据',
  }
  return {
    ...previewPublicContent,
    evidence: [core, secondary],
    projects: previewPublicContent.projects.map((project, index) =>
      index === 0
        ? { ...project, evidenceIds: [core.id, secondary.id] }
        : project),
  }
}

function mountWorkspace(
  portfolio = previewPublicContent,
  routeContext: { initialCase?: string; initialQuestion?: string } = {},
) {
  return mount(AgentWorkspace, {
    props: { portfolio, ...routeContext },
    global: {
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

function activeEvidenceId(wrapper: ReturnType<typeof mountWorkspace>) {
  return wrapper.get('.evidence-card--active').attributes('data-evidence-id')
}

function storedMessages() {
  return storedSessions()[0]?.messages ?? []
}

function storedSessions() {
  return JSON.parse(localStorage.getItem(SESSION_KEY) ?? '[]')
}

describe('AgentWorkspace', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    localStorage.clear()
    askQuestionMock.mockReset()
    askQuestionMock.mockResolvedValue(answerResponse())
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query.includes('1279'),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
  })

  it('stores the received opaque confirmation in tab memory before sending confirm-plan', async () => {
    askQuestionMock
      .mockResolvedValueOnce(confirmationRequiredResponse())
      .mockResolvedValueOnce(partialSuccessResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="confirm-plan"]').trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0]).toMatchObject({
      action: 'CONFIRM_PLAN',
      agentTurnContract: 'stp-v1',
      planConfirmation: {
        confirmationId: 'confirmation-01',
        confirmationPlan: 'opaque-envelope',
        planFingerprint: 'sha256:opaque-fingerprint',
        integrityToken: 'opaque-integrity-token',
      },
    })
    expect(askQuestionMock.mock.calls[1]?.[0].question).toBeUndefined()
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('continues a local clarification with a controlled resolution and no fieldKey guessing', async () => {
    askQuestionMock.mockResolvedValueOnce(localPartialReadyResponse()).mockResolvedValueOnce(partialSuccessResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-clarification-option="project-b"]').trigger('click')
    await flushPromises()

    const continuation = askQuestionMock.mock.calls[1]?.[0]
    expect(continuation).toMatchObject({
      action: 'ASK',
      agentTurnContract: 'stp-v1',
      question: '先审阅再比较',
      clarificationResolution: {
        clarificationId: 'clarify-0a1b2c3d4e5f60718293a4b5c6d7e8f9',
        promptCode: 'ROUTING_COMPARISON_SUBJECT_MISSING',
        fieldKey: 'comparisonSubject',
        selectedOption: {
          value: 'project-b',
          subjectReference: { subjectType: 'PROJECT', subjectId: 'project-b' },
        },
      },
    })
    expect(continuation.semanticContext).toBeDefined()
    expect(continuation.planConfirmation).toBeUndefined()
    // FE-F03：前端不再构造非法主体类型，也不按 fieldKey 猜领域类型
    expect(JSON.stringify(continuation)).not.toContain('CONTROLLED_OPTION')
  })

  it('keeps critical clarification execution-free until a controlled choice is submitted', async () => {
    askQuestionMock.mockResolvedValueOnce(criticalClarificationResponse()).mockResolvedValueOnce(partialSuccessResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('比较两个项目')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="turn-clarification"]').attributes('data-scope')).toBe('CRITICAL')
    expect(wrapper.find('[data-section-type]').exists()).toBe(false)
    await wrapper.get('[data-clarification-option="project-b"]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    const continuation = askQuestionMock.mock.calls[1]?.[0]
    expect(continuation).toMatchObject({
      action: 'ASK',
      agentTurnContract: 'stp-v1',
      question: '比较两个项目',
      clarificationResolution: {
        clarificationId: 'clarify-f9e8d7c6b5a4938271605f4e3d2c1b0a',
        promptCode: 'ROUTING_SUBJECT_CLARIFICATION_REQUIRED',
        fieldKey: 'subject',
        selectedOption: {
          value: 'project-b',
          subjectReference: { subjectType: 'PROJECT', subjectId: 'project-b' },
        },
      },
    })
    expect(continuation.planConfirmation).toBeUndefined()
  })

  it('submits a short-text clarification as a controlled text resolution', async () => {
    askQuestionMock.mockResolvedValueOnce(shortTextClarificationResponse()).mockResolvedValueOnce(partialSuccessResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('一口气问八个目标')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-clarification-text]').setValue('先只介绍 SQL 审计和 ABTest')
    await wrapper.get('[data-clarification-submit]').trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0]).toMatchObject({
      action: 'ASK',
      question: '一口气问八个目标',
      clarificationResolution: {
        clarificationId: 'clarify-11223344556677889900aabbccddeeff',
        promptCode: 'ROUTING_TASK_SPLIT_REQUIRED',
        fieldKey: 'taskSplit',
        textValue: '先只介绍 SQL 审计和 ABTest',
      },
    })
  })

  it('enters an explicit adjustment mode and submits instruction with the pending plan reference', async () => {
    askQuestionMock
      .mockResolvedValueOnce(confirmationRequiredResponse())
      .mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    await wrapper.get('[data-action="adjust-plan"]').trigger('click')
    const bar = wrapper.get('[data-testid="plan-adjustment-bar"]')
    expect(bar.text()).toContain('正在调整当前计划')
    expect(bar.text()).toContain('4 步 · 从了解到推荐')
    // 调整中确认卡保持可操作（反悔路径）
    expect(wrapper.get('[data-action="confirm-plan"]').attributes('disabled')).toBeUndefined()

    await bar.get('[data-adjustment-input]').setValue('去掉总结那一步')
    await bar.get('[data-action="submit-adjustment"]').trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0]).toMatchObject({
      action: 'ASK',
      question: '先审阅再比较',
      planAdjustment: {
        instruction: '去掉总结那一步',
        pendingPlanReference: {
          planId: 'plan-pending-01',
          planFingerprint: 'sha256:opaque-fingerprint',
        },
      },
      semanticContext: {
        pendingPlanReference: {
          planId: 'plan-pending-01',
          planFingerprint: 'sha256:opaque-fingerprint',
        },
      },
    })
    // 新计划到达后调整态自动退出
    expect(wrapper.find('[data-testid="plan-adjustment-bar"]').exists()).toBe(false)
  })

  it('exits the adjustment mode without sending any request', async () => {
    askQuestionMock.mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="adjust-plan"]').trigger('click')
    await wrapper.get('[data-action="exit-adjustment"]').trigger('click')

    expect(wrapper.find('[data-testid="plan-adjustment-bar"]').exists()).toBe(false)
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
  })

  it('degrades the confirmed plan card to read-only after it executes', async () => {
    askQuestionMock
      .mockResolvedValueOnce(confirmationRequiredResponse())
      .mockResolvedValueOnce(partialSuccessResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="confirm-plan"]').trigger('click')
    await flushPromises()

    const card = wrapper.get('[data-testid="plan-confirmation"]')
    expect(card.attributes('data-readonly')).toBe('true')
    expect(card.text()).toContain('该计划已关闭，仅作记录')
    expect(card.find('[data-action="confirm-plan"]').exists()).toBe(false)
  })

  it('degrades the plan card to read-only after cancellation instead of removing it from history', async () => {
    askQuestionMock.mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="cancel-plan"]').trigger('click')

    const card = wrapper.get('[data-testid="plan-confirmation"]')
    expect(card.attributes('data-readonly')).toBe('true')
    expect(card.text()).toContain('该计划已关闭，仅作记录')
    expect(card.find('[data-action="confirm-plan"]').exists()).toBe(false)
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
  })

  it('rejects an injected stale clarification submission once a newer confirmation owns the pending action', async () => {
    askQuestionMock
      .mockResolvedValueOnce(localPartialReadyResponse())
      .mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('先审阅再比较')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('textarea').setValue('按上面的思路给我一个计划')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="plan-confirmation"]').exists()).toBe(true)
    // 旧澄清卡已只读，不能再通过界面提交
    const oldCard = wrapper.get('[data-testid="turn-clarification"]')
    expect(oldCard.attributes('data-readonly')).toBe('true')

    // 注入旧澄清事件：turnId 对应当前未决动作之外，Workspace 必须拒绝
    wrapper.findComponent(ConversationThread).vm.$emit('clarificationSubmit', {
      turnId: 'turn-semantic-local-clarification',
      clarification: {
        clarificationId: 'clarify-0a1b2c3d4e5f60718293a4b5c6d7e8f9',
        scope: 'LOCAL',
        promptCode: 'ROUTING_COMPARISON_SUBJECT_MISSING',
        prompt: '请选择',
        fields: [],
        blockedTaskCount: 1,
        continuingTaskCount: 1,
        continuingGoalLabels: [],
        blockedGoals: [],
      },
      submission: {
        kind: 'CHOICE',
        fieldKey: 'comparisonSubject',
        option: {
          value: 'project-b',
          label: '项目 B',
          subjectReference: { subjectType: 'PROJECT', subjectId: 'project-b' },
        },
      },
    })
    await flushPromises()
    expect(askQuestionMock).toHaveBeenCalledTimes(2)
  })

  it('degrades the invalidation card to read-only once regeneration yields a new confirmation', async () => {
    askQuestionMock
      .mockResolvedValueOnce(invalidatedPlanResponse())
      .mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('重新比较项目')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="regenerate-plan"]').trigger('click')
    await flushPromises()

    const notice = wrapper.get('[data-testid="plan-invalidated-notice"]')
    expect(notice.find('[data-action="regenerate-plan"]').exists()).toBe(false)
    expect(notice.find('[data-action="dismiss-plan-change"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="plan-confirmation"]').exists()).toBe(true)
  })

  it('rejects injected stale invalidation events and keeps the new plan adjustable', async () => {
    askQuestionMock
      .mockResolvedValueOnce(invalidatedPlanResponse())
      .mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('重新比较项目')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="regenerate-plan"]').trigger('click')
    await flushPromises()
    expect(askQuestionMock).toHaveBeenCalledTimes(2)

    // 注入旧失效卡的 dismiss：不得清除新确认计划的 continuation
    wrapper.findComponent(ConversationThread).vm.$emit('dismissPlanChange', 'turn-semantic-invalidated')
    await flushPromises()
    // 注入旧失效卡的 regenerate：不得发送任何请求
    wrapper.findComponent(ConversationThread).vm.$emit('regeneratePlan', 'turn-semantic-invalidated')
    await flushPromises()
    expect(askQuestionMock).toHaveBeenCalledTimes(2)

    // 新确认计划仍能正常进入调整模式并提交调整（证明 continuation 未被旧卡清除）
    await wrapper.get('[data-action="adjust-plan"]').trigger('click')
    const bar = wrapper.get('[data-testid="plan-adjustment-bar"]')
    askQuestionMock.mockResolvedValueOnce(confirmationRequiredResponse())
    await bar.get('[data-adjustment-input]').setValue('把推荐数量改成 2 个')
    await bar.get('[data-action="submit-adjustment"]').trigger('click')
    await flushPromises()
    expect(askQuestionMock).toHaveBeenCalledTimes(3)
    expect(askQuestionMock.mock.calls[2]?.[0]).toMatchObject({
      action: 'ASK',
      planAdjustment: {
        instruction: '把推荐数量改成 2 个',
        pendingPlanReference: {
          planId: 'plan-pending-01',
          planFingerprint: 'sha256:opaque-fingerprint',
        },
      },
    })
  })

  it('dismisses an invalidated plan locally and drops the continuation without any request', async () => {
    askQuestionMock.mockResolvedValueOnce(invalidatedPlanResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('重新比较项目')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="plan-invalidated-notice"]').exists()).toBe(true)

    await wrapper.get('[data-action="dismiss-plan-change"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="plan-invalidated-notice"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('已暂不处理')
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
  })

  it('regenerates an invalidated plan with original context and no confirmation envelope', async () => {
    askQuestionMock.mockResolvedValueOnce(invalidatedPlanResponse()).mockResolvedValueOnce(confirmationRequiredResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('重新比较项目')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-action="regenerate-plan"]').trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0]).toMatchObject({
      action: 'REGENERATE_PLAN',
      agentTurnContract: 'stp-v1',
      question: '重新比较项目',
      semanticContext: {
        activeSubjects: [{ subjectType: 'PROJECT', subjectId: 'sql-audit' }],
      },
      invalidatedPlanReference: {
        planId: 'plan-opaque',
        planFingerprint: 'sha256:opaque',
      },
    })
    expect(askQuestionMock.mock.calls[1]?.[0].planConfirmation).toBeUndefined()
  })

  it('focuses cited evidence after an answer and opens citations from a section', async () => {
    const wrapper = mountWorkspace()
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(1)
    expect(askQuestionMock.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
      question: previewPublicContent.projects[0].suggestedQuestions[0],
      questionPresetId: 'sql-audit-overview',
    }))
    expect(wrapper.get('.message--user')).toBeTruthy()
    expect(wrapper.get('[data-evidence-id="sql-audit-delivery-set"]').classes())
      .toContain('evidence-card--focused')

    await wrapper.get('[data-section-evidence]').trigger('click')
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.evidence-toggle').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('引用')
    expect(wrapper.find('[data-citation-id]').exists()).toBe(true)

    const answerSection = wrapper.get('[data-section-type="BACKGROUND"]')
    const scrollArea = wrapper.get('.conversation__scroll')
    const scrollTo = vi.fn()
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })
    await wrapper.get('[data-citation-id]').trigger('click')
    await flushPromises()

    expect(scrollTo).toHaveBeenCalledWith({
      top: 0,
      behavior: 'smooth',
    })
    expect(answerSection.attributes('data-answer-focus')).toBe('true')
  })

  it('retries a stale preset once with the server contract version', async () => {
    askQuestionMock
      .mockResolvedValueOnce({
        resolution: 'CAPABILITY_UNAVAILABLE',
        noticeCode: 'PRESET_CONTRACT_STALE',
        questionPresetId: 'sql-audit-overview',
        contractVersion: 'pcv1-1111111111111111',
      })
      .mockResolvedValueOnce({
        ...answerResponse(),
        contractVersion: 'pcv1-1111111111111111',
      })
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
      questionPresetId: 'sql-audit-overview',
      contractVersion: 'pcv1-0123456789abcdef',
    }))
    expect(askQuestionMock.mock.calls[1]?.[0]).toEqual(expect.objectContaining({
      questionPresetId: 'sql-audit-overview',
      contractVersion: 'pcv1-1111111111111111',
    }))
  })

  it('uses the in-memory server contract version for a later preset request', async () => {
    askQuestionMock
      .mockResolvedValueOnce({
        resolution: 'CAPABILITY_UNAVAILABLE',
        noticeCode: 'PRESET_CONTRACT_STALE',
        questionPresetId: 'sql-audit-overview',
        contractVersion: 'pcv1-1111111111111111',
      })
      .mockResolvedValueOnce({
        ...answerResponse(),
        contractVersion: 'pcv1-1111111111111111',
      })
      .mockResolvedValueOnce({
        ...answerResponse(),
        contractVersion: 'pcv1-1111111111111111',
      })
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('textarea').setValue(previewPublicContent.projects[0].suggestedQuestions[0])
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenNthCalledWith(3, expect.objectContaining({
      questionPresetId: 'sql-audit-overview',
      contractVersion: 'pcv1-1111111111111111',
    }))
  })

  it('shows a contract-specific message after a second stale response', async () => {
    askQuestionMock.mockResolvedValue({
      resolution: 'CAPABILITY_UNAVAILABLE',
      noticeCode: 'PRESET_CONTRACT_STALE',
      questionPresetId: 'sql-audit-overview',
      contractVersion: 'pcv1-1111111111111111',
    })
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.answer-state--error').text()).toContain('正在更新')
    expect(wrapper.find('[data-answer-retry]').exists()).toBe(false)
  })

  it('shows a contract-specific unavailable message without retrying', async () => {
    askQuestionMock.mockResolvedValue({
      resolution: 'CAPABILITY_UNAVAILABLE',
      noticeCode: 'PRESET_CONTRACT_UNAVAILABLE',
    })
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledOnce()
    expect(wrapper.get('.answer-state--error').text()).toContain('暂时无法回答')
    expect(wrapper.find('[data-answer-retry]').exists()).toBe(false)
  })

  it('uses the newest successful answer after inspecting an older answer', async () => {
    const portfolio = portfolioWithSecondaryEvidence()
    askQuestionMock
      .mockResolvedValueOnce(answerResponse('sql-audit-delivery-set', '较早回答'))
      .mockResolvedValueOnce(answerResponse('sql-audit-secondary', '最新回答'))
    const wrapper = mountWorkspace(portfolio)

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-section-evidence]').trigger('click')
    expect(wrapper.get('[data-citation-id] small').text())
      .toContain('sql-audit-delivery-set')

    await wrapper.get('textarea').setValue('生成更新后的回答')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-citation-id] small').text())
      .toContain('sql-audit-secondary')
    await wrapper.findAll('[role="tab"]')[0]!.trigger('click')
    expect(wrapper.get('.evidence-card--focused').attributes('data-evidence-id'))
      .toBe('sql-audit-secondary')
  })

  it('returns to cited answer content without smooth motion when reduced motion is requested', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query === '(prefers-reduced-motion: reduce)',
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
    const wrapper = mountWorkspace()
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-section-evidence]').trigger('click')

    const answerSection = wrapper.get('[data-section-type="BACKGROUND"]')
    const scrollArea = wrapper.get('.conversation__scroll')
    const scrollTo = vi.fn()
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })
    await wrapper.get('[data-citation-id]').trigger('click')
    await flushPromises()

    expect(scrollTo).toHaveBeenCalledWith({
      top: 0,
      behavior: 'auto',
    })
  })

  it('resets citation focus when selecting a different session', async () => {
    const wrapper = mountWorkspace()
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-section-evidence]').trigger('click')
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('引用')

    await wrapper.findAll('.session-select').at(-1)!.trigger('click')

    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('证据')
    expect(wrapper.find('[data-citation-id]').exists()).toBe(false)
  })

  it('restores focus to the exact evidence inspection trigger after closing the drawer', async () => {
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0)
      return 0
    })
    const wrapper = mount(AgentWorkspace, {
      attachTo: document.body,
      props: { portfolio: previewPublicContent },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    const trigger = wrapper.get('[data-section-evidence]')
    ;(trigger.element as HTMLElement).focus()

    await trigger.trigger('click')
    expect(document.activeElement?.getAttribute('role')).toBe('tab')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()

    expect(document.activeElement).toBe(trigger.element)
    wrapper.unmount()
  })

  it('closes a responsive evidence drawer before focusing cited answer content', async () => {
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0)
      return 0
    })
    const wrapper = mount(AgentWorkspace, {
      attachTo: document.body,
      props: { portfolio: previewPublicContent },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-section-evidence]').trigger('click')
    const answerSection = wrapper.get('[data-section-type="BACKGROUND"]')
    const scrollArea = wrapper.get('.conversation__scroll')
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: vi.fn(),
    })

    await wrapper.get('[data-citation-id]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.evidence-toggle').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.workspace-scrim').exists()).toBe(false)
    expect(document.activeElement).toBe(answerSection.element)
    wrapper.unmount()
  })

  it('renders sessions, conversation, evidence desk, and two accessible separators', () => {
    const wrapper = mountWorkspace()

    // A5：隐私提示从右下绝对位覆盖层移到会话栏 footer 静态位，
    // 文案改为安全规则要求的完整版（AGENTS.md 8.3）。
    expect(wrapper.text()).toContain('当前对话未保存，刷新后记录会消失')
    expect(wrapper.text()).toContain('Agent 对话')
    expect(wrapper.text()).toContain('证据工作台')
    expect(wrapper.findAll('[role="separator"]')).toHaveLength(2)
    expect(wrapper.get('.agent-workspace').classes()).toContain('agent-workspace--prototype')
    expect(wrapper.find('.thread-empty-card').exists()).toBe(false)
    expect(wrapper.find('.message--user-card').exists()).toBe(false)
  })

  it('moves a separator by 16px and resets it with Home', async () => {
    const wrapper = mountWorkspace()
    const handle = wrapper.get('[aria-label="调整历史会话宽度"]')
    const before = Number(handle.attributes('aria-valuenow'))

    await handle.trigger('keydown', { key: 'ArrowRight' })
    expect(Number(handle.attributes('aria-valuenow'))).toBe(before + 16)

    await handle.trigger('keydown', { key: 'Home' })
    expect(Number(handle.attributes('aria-valuenow'))).not.toBe(before + 16)
  })

  it('fits max persisted widths to a 1280px viewport shell without rewriting the preference', async () => {
    localStorage.setItem(
      WORKSPACE_SPLIT_KEY,
      JSON.stringify({ sessions: 320, evidence: 420 }),
    )
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(1248)
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )

    const wrapper = mountWorkspace()
    await wrapper.vm.$nextTick()

    const workspaceStyle = wrapper.get('.agent-workspace').attributes('style')
    expect(workspaceStyle).toContain('--sessions-width: 260px')
    expect(workspaceStyle).toContain('--evidence-width: 348px')
    expect(
      wrapper.get('[aria-label="调整历史会话宽度"]').attributes('aria-valuenow'),
    ).toBe('260')
    expect(
      wrapper.get('[aria-label="调整证据工作台宽度"]').attributes('aria-valuenow'),
    ).toBe('348')
    expect(JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? '{}')).toEqual({
      sessions: 320,
      evidence: 420,
    })
  })

  it('exposes drawer state and closes an open drawer with Escape', async () => {
    const matchMedia = vi.mocked(window.matchMedia)
    const wrapper = mountWorkspace()
    const toggle = wrapper.get('.evidence-toggle')

    expect(matchMedia).toHaveBeenCalledWith('(max-width: 1279.98px)')
    expect(matchMedia).toHaveBeenCalledWith('(max-width: 959.98px)')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('#agent-evidence-desk').attributes('aria-hidden')).toBe('false')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(toggle.attributes('aria-expanded')).toBe('false')
  })

  it('keeps the workspace usable after deleting the only session', async () => {
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-remove]').trigger('click')

    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
    expect(wrapper.findAll('.session-list article')).toHaveLength(0)
    expect(wrapper.text()).toContain('从一个可核验的问题开始')
  })

  it('stages a new draft outside history until the first user message', async () => {
    const wrapper = mountWorkspace()

    expect(wrapper.findAll('.session-list article')).toHaveLength(0)
    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('.session-rail__new').trigger('click')
    expect(wrapper.findAll('.session-list article')).toHaveLength(0)

    await wrapper.get('textarea').setValue('进入历史的第一条消息')
    await wrapper.get('.composer').trigger('submit')

    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
  })

  it('restores project core evidence when creating a conversation', async () => {
    const wrapper = mountWorkspace(portfolioWithSecondaryEvidence())

    await wrapper.get('[data-evidence-id="sql-audit-secondary"]').trigger('click')
    expect(activeEvidenceId(wrapper)).toBe('sql-audit-secondary')

    await wrapper.get('.session-rail__new').trigger('click')

    expect(activeEvidenceId(wrapper)).toBe('sql-audit-delivery-set')
  })

  it('restores the selected session core evidence', async () => {
    const wrapper = mountWorkspace(portfolioWithSecondaryEvidence())
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('[data-evidence-id="sql-audit-secondary"]').trigger('click')

    await wrapper.findAll('.session-select').at(-1)!.trigger('click')

    expect(activeEvidenceId(wrapper)).toBe('sql-audit-delivery-set')
  })

  it('restores fallback session core evidence after removing the current session', async () => {
    const wrapper = mountWorkspace(portfolioWithSecondaryEvidence())
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('textarea').setValue('第二个历史会话')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-evidence-id="sql-audit-secondary"]').trigger('click')

    await wrapper.get('.session-list article.active [data-session-menu]').trigger('click')
    await wrapper.get('.session-list article.active [data-session-remove]').trigger('click')

    expect(activeEvidenceId(wrapper)).toBe('sql-audit-delivery-set')
    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
  })

  it('cancels or confirms clearing history and restores fallback core evidence', async () => {
    const wrapper = mountWorkspace(portfolioWithSecondaryEvidence())
    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-evidence-id="sql-audit-secondary"]').trigger('click')

    await wrapper.get('[data-session-clear]').trigger('click')
    await wrapper.get('[data-session-clear-cancel]').trigger('click')
    expect(wrapper.findAll('.session-list article')).toHaveLength(1)
    expect(activeEvidenceId(wrapper)).toBe('sql-audit-secondary')

    await wrapper.get('[data-session-clear]').trigger('click')
    await wrapper.get('[data-session-clear-confirm]').trigger('click')

    expect(wrapper.findAll('.session-list article')).toHaveLength(0)
    expect(wrapper.findAll('.message')).toHaveLength(0)
    expect(activeEvidenceId(wrapper)).toBe('sql-audit-delivery-set')
  })

  it('shows the user immediately and appends a structured Agent answer only after API success', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('如何验证结果？')
    await wrapper.get('.composer').trigger('submit')

    expect(askQuestionMock).toHaveBeenCalledWith(expect.objectContaining({
      projectSlug: 'sql-audit',
      question: '如何验证结果？',
      source: 'AGENT_PAGE',
    }))
    expect(wrapper.get('.message--user .message__body').text()).toBe('如何验证结果？')
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-agent-submit]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('generating')
    expect(wrapper.get('[data-agent-loading]').text()).toContain('正在核验证据')
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(0)
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(wrapper.get('.message--agent').text()).toContain('项目说明')
    expect(wrapper.get('.message--agent').text()).toContain('背景内容')
    expect(wrapper.get('.message--agent').text()).toContain('已验证回答')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('shows a clearable Case context and scopes requests to that Case', async () => {
    const wrapper = mountWorkspace(previewPublicContent, {
      initialCase: 'multilingual-image-preservation',
      initialQuestion: '这个案例如何验证？',
    })

    expect(wrapper.get('[data-case-context]').text()).toContain('多语言图片上传结果保留修复')
    expect(wrapper.get('textarea').element.value).toBe('这个案例如何验证？')
    expect(wrapper.get('.evidence-card--active').attributes('data-evidence-id')).toBe(
      previewPublicContent.cases.find(
        (item) => item.slug === 'multilingual-image-preservation',
      )!.evidence[0].id,
    )

    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        projectSlug: null,
        caseSlug: 'multilingual-image-preservation',
        source: 'CASE',
        question: '这个案例如何验证？',
      }),
    )

    await wrapper.get('[data-clear-case-context]').trigger('click')
    expect(wrapper.find('[data-case-context]').exists()).toBe(false)

    await wrapper.get('textarea').setValue('现在介绍完整项目')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        projectSlug: 'sql-audit',
        caseSlug: null,
        source: 'AGENT_PAGE',
        question: '现在介绍完整项目',
      }),
    )
  })

  it('submits a suggestion without contract identity but with a subject slug', async () => {
    const wrapper = mountWorkspace()
    await wrapper.get('textarea').setValue('公开检索问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    const suggestion = wrapper.findAll('[data-suggested-follow-up]')
      .find((button) => button.text() === '开源评测案例怎么做的？')
    if (!suggestion) throw new Error('suggestion button missing')
    await suggestion.trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenLastCalledWith(expect.objectContaining({
      question: '开源评测案例怎么做的？',
      projectSlug: null,
      caseSlug: 'open-source-evaluation',
      questionPresetId: undefined,
      contractVersion: undefined,
    }))
  })

  it('shows rule provenance without turning a clarification into an applicable source', async () => {
    askQuestionMock
      .mockResolvedValueOnce({
        ...answerResponse(),
        questionPresetId: undefined,
        answerSource: 'RETRIEVAL' as const,
        intentSource: 'RULE' as const,
        verification: 'PARTIALLY_VERIFIED' as const,
      })
      .mockResolvedValueOnce({
        ...answerResponse(),
        questionPresetId: undefined,
        resolution: 'NEEDS_CLARIFICATION' as const,
        answerSource: undefined,
        intentSource: 'GLOBAL' as const,
        constructionMode: 'TEMPLATE' as const,
        evidenceState: 'NOT_REQUIRED' as const,
        verification: 'NOT_APPLICABLE' as const,
      })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('公开检索问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.findAll('.message--agent')[0].text())
      .toContain('规则识别')

    await wrapper.get('textarea').setValue('越界问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    const boundary = wrapper.findAll('.message--agent')[1].text()
    expect(boundary).toContain('需要补充信息')
    expect(boundary).not.toContain('来自公开资料检索')
    expect(boundary).not.toContain('PRESET')
    expect(boundary).not.toContain('RETRIEVAL')
  })

  it('sends only stable page-memory references after an explicit follow-up action', async () => {
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('公开检索问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    await wrapper.get('[data-follow-up="current-status"]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
      question: '查看当前状态',
      questionPresetId: undefined,
      referenceContext: {
        previousContentVersion: '2026-07-21',
        projectSlugs: ['sql-audit'],
        questionPresetId: 'sql-audit-overview',
        referencedClaimIds: ['claim-sql-audit-delivered'],
        selectedSectionType: undefined,
        followUpAction: 'CURRENT_STATUS',
      },
    }))
    const body = askQuestionMock.mock.calls[1]?.[0]
    expect(body.messages).toBeDefined()
    expect(body.messages.length).toBeGreaterThanOrEqual(2)
    expect(body.referenceContext).toEqual({
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
      selectedSectionType: undefined,
      followUpAction: 'CURRENT_STATUS',
    })
    // 消息历史不应包含原始内部字段
    expect(JSON.stringify(body.messages)).not.toContain('claim-sql-audit-delivered')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('announces when stable references were revalidated against a newer content version', async () => {
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      contextVersionUpdated: true,
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('查看更新后的状态')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-context-version-updated]').text())
      .toContain('公开内容已更新，本轮已按当前版本重新核对')
  })

  it('retries a failed answer without duplicating the persisted user message', async () => {
    askQuestionMock
      .mockRejectedValueOnce(new Error('POST https://internal.example/api failed'))
      .mockResolvedValueOnce(answerResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('失败后重试的问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Agent 暂时无法回答，请稍后重试')
    expect(wrapper.text()).not.toContain('internal.example')
    expect(wrapper.findAll('.message--user')).toHaveLength(1)

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock).toHaveBeenNthCalledWith(1, expect.objectContaining({ question: '失败后重试的问题' }))
    expect(askQuestionMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ question: '失败后重试的问题' }))
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(1)
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('retries against the failed session after the user switches sessions', async () => {
    askQuestionMock
      .mockResolvedValueOnce(answerResponse())
      .mockRejectedValueOnce(new Error('first request failed'))
      .mockResolvedValueOnce(answerResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('textarea').setValue('保留原会话上下文')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    await wrapper.findAll('.session-select').at(-1)!.trigger('click')
    expect(wrapper.get('.session-list article.active .session-select').text()).not.toContain(
      '保留原会话上下文',
    )

    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(1)
    await wrapper.findAll('.session-select')[0].trigger('click')
    expect(wrapper.findAll('.message--user')).toHaveLength(1)
    expect(wrapper.findAll('.message--agent')).toHaveLength(1)
    expect(askQuestionMock).toHaveBeenNthCalledWith(3, expect.objectContaining({ question: '保留原会话上下文' }))
  })

  it('reuses the same request token when retrying a failed answer', async () => {
    askQuestionMock
      .mockRejectedValueOnce(new Error('failed'))
      .mockResolvedValueOnce(answerResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('retry token')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[0]?.[0].requestToken).toBeTruthy()
    expect(askQuestionMock.mock.calls[0]?.[0].requestToken)
      .toBe(askQuestionMock.mock.calls[1]?.[0].requestToken)
  })

  it('honors Retry-After before allowing another retry', async () => {
    askQuestionMock.mockRejectedValueOnce(
      new PortfolioApiError('rate limited', 429, 'ANSWER_RATE_LIMITED', 17),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('rate limited question')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    const retry = wrapper.get('[data-answer-retry]')
    expect(retry.attributes('disabled')).toBeDefined()
    expect(retry.text()).toContain('17 秒后可重试')
    await retry.trigger('click')
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('renders timeout recovery as a retry without a correction action', async () => {
    askQuestionMock.mockRejectedValueOnce(new PortfolioApiError('fixture timeout', {
      kind: 'TIMEOUT',
      code: 'ANSWER_REQUEST_TIMEOUT',
      requestId: 'req-timeout-12345678',
      action: 'RETRY',
      clientRequestId: 'client-timeout',
    }))
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-answer-recovery-action]').attributes('data-answer-recovery-action'))
      .toBe('retry')
    expect(wrapper.find('[data-answer-edit]').exists()).toBe(false)
  })

  it('requests safe portfolio navigation for a missing project', async () => {
    askQuestionMock.mockRejectedValueOnce(new PortfolioApiError('fixture missing project', {
      kind: 'HTTP',
      status: 404,
      code: 'PROJECT_NOT_FOUND',
      requestId: 'req-project-missing',
      action: 'NAVIGATE_BACK',
      clientRequestId: 'client-project-missing',
    }))
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-answer-recovery-action="navigate-back"]').trigger('click')

    expect(wrapper.emitted('navigatePortfolio')).toHaveLength(1)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('clears a cancelled API request without appending a failure answer', async () => {
    askQuestionMock.mockRejectedValueOnce(new PortfolioApiError('fixture cancellation', {
      kind: 'CANCELLED',
      code: 'REQUEST_CANCELLED',
      action: 'NONE',
      clientRequestId: 'client-cancelled',
    }))
    const wrapper = mountWorkspace()

    await wrapper.get('[data-suggested-question]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('.message--agent')).toHaveLength(0)
  })

  it('cancels a pending answer without showing a failure', async () => {
    askQuestionMock.mockImplementation((input) =>
      new Promise((_resolve, reject) => {
        input.signal?.addEventListener('abort', () => reject(new Error('cancelled')))
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('cancel answer')
    await wrapper.get('.composer').trigger('submit')
    const signal = askQuestionMock.mock.calls[0]?.[0].signal as AbortSignal
    await wrapper.get('[data-answer-cancel]').trigger('click')
    await flushPromises()

    expect(signal.aborted).toBe(true)
    expect(wrapper.find('[data-agent-loading]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('.message--agent')).toHaveLength(0)
  })

  it('clears retry safely when the failed session is deleted', async () => {
    askQuestionMock.mockRejectedValueOnce(new Error('first request failed'))
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('删除失败会话')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-answer-retry]').exists()).toBe(true)

    await wrapper.get('[data-session-menu]').trigger('click')
    await wrapper.get('[data-session-remove]').trigger('click')

    expect(wrapper.find('[data-answer-retry]').exists()).toBe(false)
    expect(askQuestionMock).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.session-list article')).toHaveLength(0)
    expect(wrapper.findAll('.message')).toHaveLength(0)
  })

  it('does not persist a late resolved answer after unmount', async () => {
    let resolveAnswer!: (value: ReturnType<typeof answerResponse>) => void
    askQuestionMock.mockReturnValue(
      new Promise((resolve) => {
        resolveAnswer = resolve
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('卸载后成功')
    await wrapper.get('.composer').trigger('submit')
    const storedBeforeUnmount = localStorage.getItem(SESSION_KEY)
    wrapper.unmount()

    resolveAnswer(answerResponse())
    await flushPromises()

    expect(localStorage.getItem(SESSION_KEY)).toBe(storedBeforeUnmount)
  })

  it('does not overwrite state when a request rejects after unmount', async () => {
    let rejectAnswer!: (reason: Error) => void
    askQuestionMock.mockReturnValue(
      new Promise((_, reject) => {
        rejectAnswer = reject
      }),
    )
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('卸载后失败')
    await wrapper.get('.composer').trigger('submit')
    const storedBeforeUnmount = localStorage.getItem(SESSION_KEY)
    wrapper.unmount()

    rejectAnswer(new Error('POST https://internal.example/api failed'))
    await flushPromises()

    expect(localStorage.getItem(SESSION_KEY)).toBe(storedBeforeUnmount)
  })

  it('sends recent conversation messages to the v2 API', async () => {
    const wrapper = mountWorkspace()

    // First turn
    askQuestionMock.mockResolvedValueOnce(v2AnswerResponse('GENERAL_KNOWLEDGE', 'GENERAL'))
    await wrapper.get('textarea').setValue('什么是 HTTP？')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    // Second turn - should include history
    askQuestionMock.mockResolvedValueOnce(v2AnswerResponse('PORTFOLIO_GROUNDED', 'PORTFOLIO'))
    await wrapper.get('textarea').setValue('作者在项目中用过 HTTP 吗？')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    const secondCall = askQuestionMock.mock.calls[1]?.[0]
    expect(secondCall.messages).toBeDefined()
    expect(secondCall.messages.length).toBeGreaterThanOrEqual(2)
    expect(secondCall.messages[0]).toEqual({ role: 'USER', content: '什么是 HTTP？' })
    expect(secondCall.messages[1]).toEqual({ role: 'ASSISTANT', content: '通用回答' })
    expect(secondCall.caseSlug).toBeNull()
  })

  it('does not duplicate the current question in conversation history', async () => {
    const wrapper = mountWorkspace()

    askQuestionMock.mockResolvedValueOnce(v2AnswerResponse())
    await wrapper.get('textarea').setValue('什么是 HTTP？')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[0]?.[0].messages).toEqual([])
  })

  it('excludes failed user turns from the next request history', async () => {
    const wrapper = mountWorkspace()
    askQuestionMock
      .mockResolvedValueOnce(v2AnswerResponse('GENERAL_KNOWLEDGE', 'GENERAL'))
      .mockRejectedValueOnce(new Error('request failed'))
      .mockResolvedValueOnce(v2AnswerResponse('GENERAL_KNOWLEDGE', 'GENERAL'))

    await wrapper.get('textarea').setValue('first completed question')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('textarea').setValue('failed question')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    await wrapper.get('textarea').setValue('next valid question')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[2]?.[0].messages).toEqual([
      { role: 'USER', content: 'first completed question' },
      { role: 'ASSISTANT', content: '通用回答' },
    ])
  })

  it('caps conversation history at 20 rounds (40 messages)', async () => {
    const wrapper = mountWorkspace()

    // Simulate 25 rounds of conversation
    for (let i = 0; i < 25; i++) {
      askQuestionMock.mockResolvedValueOnce(v2AnswerResponse('GENERAL_KNOWLEDGE', 'GENERAL'))
      await wrapper.get('textarea').setValue(`问题 ${i}`)
      await wrapper.get('.composer').trigger('submit')
      await flushPromises()
    }

    const lastCall = askQuestionMock.mock.calls[24]?.[0]
    expect(lastCall.messages.length).toBeLessThanOrEqual(40)
    expect(lastCall.messages.length % 2).toBe(0)
    expect(lastCall.messages[0]?.role).toBe('USER')
    expect(lastCall.messages.at(-1)?.role).toBe('ASSISTANT')
  })

  it('threads coveredTopics from each answer into the next request', async () => {
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      coveredTopics: ['BACKGROUND', 'SOLUTION'],
      guidanceStage: 'DEEPENING',
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('第一轮问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[0]?.[0].coveredTopics).toEqual([])

    await wrapper.get('textarea').setValue('第二轮问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0].coveredTopics).toEqual(['BACKGROUND', 'SOLUTION'])
  })

  it('keeps coveredTopics isolated between sessions', async () => {
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      coveredTopics: ['BACKGROUND'],
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('会话 A 首轮')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(askQuestionMock.mock.calls[0]?.[0].coveredTopics).toEqual([])

    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('textarea').setValue('会话 B 首轮')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0].coveredTopics).toEqual([])
  })

  it('retries a failed turn with the coveredTopics snapshot from that turn', async () => {
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      coveredTopics: ['BACKGROUND'],
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('第一轮问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    askQuestionMock.mockRejectedValueOnce(new Error('network down'))
    await wrapper.get('textarea').setValue('第二轮问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0].coveredTopics).toEqual(['BACKGROUND'])
    expect(wrapper.find('[data-answer-retry]').exists()).toBe(true)

    askQuestionMock.mockResolvedValueOnce(answerResponse())
    await wrapper.get('[data-answer-retry]').trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[2]?.[0].coveredTopics).toEqual(['BACKGROUND'])
    expect(askQuestionMock.mock.calls[2]?.[0].question).toBe('第二轮问题')
  })

  it('renders three suggested questions per answer and follows their own targets', async () => {
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('首轮问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    const suggestions = wrapper.findAll('[data-suggested-follow-up]')
    expect(suggestions).toHaveLength(3)
    expect(suggestions[0]?.text()).toBe('当前项目追问')
    expect(suggestions[0]?.attributes('title')).toBe('当前项目追问')

    await suggestions[1]?.trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0]).toEqual(
      expect.objectContaining({
        question: '了解代码图谱评测项目',
        projectSlug: 'codegraph-evaluation',
        caseSlug: null,
        source: 'AGENT_PAGE',
      }),
    )

    const refreshed = wrapper.findAll('[data-suggested-follow-up]')
    await refreshed[2]?.trigger('click')
    await flushPromises()

    expect(askQuestionMock.mock.calls[2]?.[0]).toEqual(
      expect.objectContaining({
        question: '开源评测案例怎么做的？',
        projectSlug: null,
        caseSlug: 'open-source-evaluation',
        source: 'CASE',
      }),
    )
  })

  it('recovers incomplete suggested questions from public presets with a sanitized diagnostic', async () => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')
    const portfolio = {
      ...previewPublicContent,
      questionPresets: [
        {
          id: 'agent-preset-1',
          projectSlug: 'sql-audit',
caseSlugs: [],
          text: '补足预设一',
          audiences: ['HR' as const],
          placements: ['AGENT' as const],
          contractVersion: 'pcv1-0000000000000001',
          availability: 'ACTIVE' as const,
        },
        {
          id: 'agent-preset-2',
          projectSlug: 'sql-audit',
caseSlugs: [],
          text: '补足预设二',
          audiences: ['HR' as const],
          placements: ['AGENT' as const],
          contractVersion: 'pcv1-0000000000000002',
          availability: 'ACTIVE' as const,
        },
      ],
    }
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      turnId: '123e4567-e89b-42d3-a456-426614174000',
      suggestedQuestions: [
        { text: '唯一的后端建议', projectSlug: 'sql-audit', caseSlug: null, facet: null },
        { text: '   ', projectSlug: 'sql-audit', caseSlug: null, facet: null },
        {
          text: '非法建议',
          projectSlug: 'sql-audit',
          caseSlug: 'multilingual-image-preservation',
          facet: null,
        },
      ],
    })
    const wrapper = mountWorkspace(portfolio)

    await wrapper.get('textarea').setValue('诊断补足测试问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    const labels = wrapper.findAll('[data-suggested-follow-up]').map((button) => button.text())
    expect(labels).toEqual(['唯一的后端建议', '补足预设一', '补足预设二'])

    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.response.invalid',
      errorCode: 'SUGGESTION_CONTRACT_RECOVERED',
      errorKind: 'INVALID_RESPONSE',
      recoveredCount: 2,
      guidanceStage: 'OPENING',
    }))
    const diagnosticPayload = JSON.stringify(reportSpy.mock.calls.map((call) => call[0]))
    expect(diagnosticPayload).not.toContain('唯一的后端建议')
    expect(diagnosticPayload).not.toContain('诊断补足测试问题')
  })

  it('reports a completed answer with safe metadata only', async () => {
    const reportSpy = vi.spyOn(frontendDiagnostics, 'report')
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      turnId: '123e4567-e89b-42d3-a456-426614174001',
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('不能进入诊断的访客问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(reportSpy).toHaveBeenCalledOnce()
    expect(reportSpy).toHaveBeenCalledWith(expect.objectContaining({
      eventName: 'frontend.agent.request.completed',
      turnId: '123e4567-e89b-42d3-a456-426614174001',
      httpStatus: 200,
      generationMode: 'DETERMINISTIC',
      degraded: false,
      guidanceStage: 'OPENING',
      suggestedQuestionCount: 3,
      contentVersion: '2026-07-21',
      durationBucket: expect.stringMatching(
        /^(?:LT_1000_MS|FROM_1000_TO_4999_MS|GE_5000_MS)$/,
      ),
    }))
    const payload = JSON.stringify(reportSpy.mock.calls)
    expect(payload).not.toContain('不能进入诊断的访客问题')
    expect(payload).not.toContain('公开摘要')
    expect(payload).not.toContain('当前项目追问')
  })

  it('treats a fallback answer as a successful response with its own suggestions', async () => {
    askQuestionMock.mockResolvedValueOnce({
      ...answerResponse(),
      generationMode: 'FALLBACK',
      degraded: true,
    })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('触发降级的问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-degraded-notice]').text()).toBe('已切换到基础回答')
    const suggestions = wrapper.findAll('[data-suggested-follow-up]')
    expect(suggestions).toHaveLength(3)

    await suggestions[0]?.trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock.mock.calls[1]?.[0]).toEqual(
      expect.objectContaining({ question: '当前项目追问', projectSlug: 'sql-audit' }),
    )
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('offers local suggested questions alongside retry after a network failure', async () => {
    askQuestionMock.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('网络失败时的问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-answer-retry]').exists()).toBe(true)
    const suggestions = wrapper.findAll('[data-failure-suggestion]')
    expect(suggestions).toHaveLength(3)
    expect(suggestions.map((button) => button.text())).toEqual([
      '请介绍 SQL 审计工具的完整迭代过程。',
      '这个项目中最关键的技术决策是什么？',
      '你如何验证查询、进度和归档链路？',
    ])

    await suggestions[0]?.trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock.mock.calls[1]?.[0]).toEqual(
      expect.objectContaining({
        question: '请介绍 SQL 审计工具的完整迭代过程。',
        projectSlug: 'sql-audit',
        caseSlug: null,
      }),
    )
  })

  // —— 结构化作品推荐：批次 ID 回传与会话隔离 ——
  function recommendationResponse(batchId = 'rec_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa') {
    return {
      ...answerResponse(),
      intent: 'PORTFOLIO_GROUNDED' as const,
      answerScope: 'PORTFOLIO' as const,
      answerSource: 'RETRIEVAL' as const,
      portfolioRecommendation: {
        recommendationBatchId: batchId,
        context: {
          recommendationBatchId: batchId,
          contentVersion: 'public-2026-07-31',
          careerTrack: null,
          audienceRole: 'INTERVIEWER',
          capabilityCodes: ['POSTGRESQL', 'RAG'],
          requestedSize: 2,
          selectedPortfolioIds: ['project-1', 'case-2'],
        },
        items: [
          {
            portfolioId: 'project-1',
            title: '项目一',
            route: '/projects/project-one',
            matchReasons: ['匹配后端能力要求'],
            evidenceIds: ['sql-audit-delivery-set'],
          },
          {
            portfolioId: 'case-2',
            title: '案例二',
            route: '/cases/case-two',
            matchReasons: ['补充 PostgreSQL 与验证能力'],
            evidenceIds: ['sql-audit-delivery-set'],
          },
        ],
        satisfiedConstraints: ['受众角色', '数量'],
        unsatisfiedConstraints: [],
      },
    }
  }

  it('echoes the current recommendation batch id when refining a recommendation', async () => {
    askQuestionMock.mockResolvedValueOnce(recommendationResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('给我挑 2 个后端作品')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    // 第一轮不应携带批次 ID（普通问题）
    expect(askQuestionMock.mock.calls[0]?.[0].recommendationContext).toBeUndefined()

    // 点击第 2 张卡片的「换掉这个」→ 仍调用 /api/v2/answers 且回传批次 ID
    const replaceActions = wrapper.findAll('[data-recommendation-refine="replace"]')
    await replaceActions[1]?.trigger('click')
    await flushPromises()

    expect(askQuestionMock).toHaveBeenCalledTimes(2)
    expect(askQuestionMock.mock.calls[1]?.[0]).toEqual(
      expect.objectContaining({
        question: '换掉第二个',
        recommendationContext: {
          recommendationBatchId: 'rec_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          contentVersion: 'public-2026-07-31',
          careerTrack: null,
          audienceRole: 'INTERVIEWER',
          capabilityCodes: ['POSTGRESQL', 'RAG'],
          requestedSize: 2,
          selectedPortfolioIds: ['project-1', 'case-2'],
        },
      }),
    )
    // 上下文里也回传，不是只挂在顶层
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('does not carry a stale batch id on a plain follow-up question', async () => {
    askQuestionMock.mockResolvedValueOnce(recommendationResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('给我挑 2 个后端作品')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    // 现在有推荐批次；但用户在输入框打了一个普通问题
    await wrapper.get('textarea').setValue('这两个作品有什么区别？')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0].recommendationContext).toBeUndefined()
    expect(askQuestionMock.mock.calls[1]?.[0].question).toBe('这两个作品有什么区别？')
  })

  it('does not restore recommendation context after starting a new session', async () => {
    askQuestionMock.mockResolvedValueOnce(recommendationResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('给我挑 2 个后端作品')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    // 新建会话后，批次上下文应消失——不随新标签恢复
    await wrapper.get('.session-rail__new').trigger('click')
    await wrapper.get('textarea').setValue('新会话的问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    expect(askQuestionMock.mock.calls[1]?.[0].recommendationContext).toBeUndefined()
    // 新会话内不残留上一批推荐卡
    expect(wrapper.find('[data-portfolio-recommendation]').exists()).toBe(false)
  })

  it('does not issue a portfolio-selections request for recommendation refinement', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    askQuestionMock.mockResolvedValueOnce(recommendationResponse())
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('给我挑 2 个后端作品')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()

    await wrapper.get('[data-recommendation-refine="shift-backend"]').trigger('click')
    await flushPromises()

    const selectionCalls = fetchSpy.mock.calls.filter(([url]) =>
      String(url).includes('/api/portfolio-selections'),
    )
    expect(selectionCalls).toHaveLength(0)
    fetchSpy.mockRestore()
  })
})
