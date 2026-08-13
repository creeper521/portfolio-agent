import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import type { AnswerFocusTarget } from '../model/evidenceDeskModel'
import type { AgentMessage, AgentSession } from '../model/sessionTypes'
import { confirmationRequiredResponse, localPartialReadyResponse, partialSuccessResponse } from '../model/semanticTurnFixtures'
import { mapAnswerResponse } from '../model/mapAnswerResponse'
import ConversationThread from './ConversationThread.vue'

const answerMessageFixture: AgentSession['messages'][number] = {
  id: 'agent-1',
  role: 'AGENT',
  content: 'Verified answer',
  createdAt: 3,
  evidenceIds: ['sql-audit-delivery-set'],
  answer: {
    title: 'Project details',
    summary: 'Verified answer',
    resolution: 'ANSWERED',
    answerSource: 'PRESET',
    generationMode: 'DETERMINISTIC',
    verification: 'VERIFIED',
    turnId: 'turn-agent-1',
    contentVersion: '2026-07-21',
    coveredTopics: [],
    guidanceStage: null,
    evidenceIds: ['sql-audit-delivery-set'],
    suggestedQuestionPresetIds: [],
    suggestedQuestions: [],
    referenceContext: {
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
      followUpAction: 'RELATED_QUESTION',
    },
    sections: [{
      key: 'VERIFICATION:0',
      type: 'VERIFICATION',
      title: 'Verification',
      sourceScope: 'PORTFOLIO',
      content: 'Verified against delivery artifacts and test results.',
      evidenceIds: ['sql-audit-delivery-set'],
      claimIds: ['claim-sql-audit-delivered'],
    }],
  },
}

function session(messages: AgentSession['messages'] = []): AgentSession {
  return {
    id: 'session-1',
    title: '新的工程追问',
    role: 'INTERVIEWER',
    projectSlug: 'sql-audit',
    evidenceId: null,
    seedFingerprint: null,
    coveredTopics: [],
    createdAt: 1,
    updatedAt: 1,
    messages,
  }
}

function semanticMessage(id: string, answer = mapAnswerResponse(partialSuccessResponse())): AgentMessage {
  return {
    id,
    role: 'AGENT',
    content: answer.summary,
    answer,
    evidenceIds: answer.evidenceIds,
    createdAt: 2,
  }
}

function mountThread(
  messages: AgentSession['messages'] = [],
  pending = false,
  focusTarget: AnswerFocusTarget | null = null,
  failure?: {
    message: string
    action: 'NONE' | 'RETRY' | 'RETRY_AFTER' | 'CORRECT_INPUT' | 'NAVIGATE_BACK'
    requestId?: string
    retryAfterSeconds?: number
  },
) {
  return mount(ConversationThread, {
    props: {
      session: session(messages),
      role: 'INTERVIEWER',
      project: previewPublicContent.projects[0],
      pending,
      focusTarget,
      failure,
    },
  })
}

describe('ConversationThread', () => {
  it('does not render plan UI for a single successful semantic task', () => {
    const answer = mapAnswerResponse(partialSuccessResponse())
    answer.semanticTurn = {
      ...answer.semanticTurn!,
      taskSummary: undefined,
      completedTasks: [answer.semanticTurn!.completedTasks[0]!],
    }
    const wrapper = mountThread([semanticMessage('semantic-single', answer)])

    expect(wrapper.find('[data-testid="plan-confirmation"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="task-summary"]').exists()).toBe(false)
  })

  it('renders partial task status and no body for blocked work', () => {
    const wrapper = mountThread([semanticMessage('semantic-partial')])

    expect(wrapper.get('[data-testid="task-summary"]').attributes('data-expanded')).toBe('true')
    expect(wrapper.text()).toContain('Only completed-task content appears here.')
    expect(wrapper.find('[data-testid="blocked-task-body"]').exists()).toBe(false)
  })

  it('renders a real PARTIAL_READY local clarification alongside its safe completed body', async () => {
    const answer = mapAnswerResponse(localPartialReadyResponse())
    const wrapper = mountThread([semanticMessage('semantic-local', answer)])

    expect(wrapper.find('[data-testid="turn-clarification"][data-scope="LOCAL"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Only completed-task content appears here.')
    expect(wrapper.text()).toContain('作品集资料')
    expect(wrapper.text()).toContain('已继续：Review the SQL project，不受影响')
    await wrapper.get('[data-clarification-option="project-b"]').trigger('click')
    expect(wrapper.emitted('clarificationSubmit')?.[0]?.[0]).toMatchObject({
      clarification: { clarificationId: 'clarify-0a1b2c3d4e5f60718293a4b5c6d7e8f9' },
      submission: {
        kind: 'CHOICE',
        fieldKey: 'comparisonSubject',
        option: {
          value: 'project-b',
          subjectReference: { subjectType: 'PROJECT', subjectId: 'project-b' },
        },
      },
    })
  })

  it('degrades a clarification card to read-only once a later READY answer completes the flow', async () => {
    const clarificationAnswer = { ...mapAnswerResponse(localPartialReadyResponse()), turnId: 'turn-clarify-done' }
    const readyAnswer = { ...mapAnswerResponse(partialSuccessResponse()), turnId: 'turn-ready-after' }
    const wrapper = mountThread([
      semanticMessage('semantic-clarify', clarificationAnswer),
      semanticMessage('semantic-ready', readyAnswer),
    ])

    const card = wrapper.get('[data-testid="turn-clarification"]')
    expect(card.attributes('data-readonly')).toBe('true')
    expect(card.text()).toContain('此澄清已完成，仅作记录。')
    await card.get('[data-clarification-option="project-b"]').trigger('click')
    expect(wrapper.emitted('clarificationSubmit')).toBeUndefined()
  })

  it('keeps at most one card actionable per session at any moment', () => {
    const confirmationAnswer = { ...mapAnswerResponse(confirmationRequiredResponse()), turnId: 'turn-mix-confirm' }
    const invalidatedAnswer = mapAnswerResponse(partialSuccessResponse())
    invalidatedAnswer.turnId = 'turn-mix-invalidated'
    invalidatedAnswer.semanticTurn = {
      ...invalidatedAnswer.semanticTurn!,
      disposition: 'REJECTED',
      displayPlan: undefined,
      taskSummary: undefined,
      completedTasks: [],
      planChange: {
        summary: '内容版本已变化',
        changeLabels: [],
        invalidatedPlanReference: { planId: 'plan-opaque', planFingerprint: 'sha256:opaque' },
      },
    }
    const clarificationAnswer = { ...mapAnswerResponse(localPartialReadyResponse()), turnId: 'turn-mix-clarify' }
    const current = session([
      semanticMessage('mix-confirm', confirmationAnswer),
      semanticMessage('mix-invalidated', invalidatedAnswer),
      semanticMessage('mix-clarify', clarificationAnswer),
    ])
    current.pendingConfirmation = {
      confirmationId: 'confirmation-01',
      confirmationPlan: 'opaque-envelope',
      planFingerprint: 'sha256:opaque-fingerprint',
      integrityToken: 'opaque-integrity-token',
      expiresAt: '2026-08-10T12:10:00Z',
    }
    const wrapper = mount(ConversationThread, {
      props: { session: current, role: 'INTERVIEWER', project: previewPublicContent.projects[0], pending: false },
    })

    const cards = [
      ...wrapper.findAll('[data-testid="plan-confirmation"]'),
      ...wrapper.findAll('[data-testid="turn-clarification"]'),
      ...wrapper.findAll('[data-testid="plan-invalidated-notice"]'),
    ]
    expect(cards.length).toBe(3)
    const actionable = cards.filter((card) =>
      card.findAll('button').some((button) => button.attributes('disabled') === undefined))
    expect(actionable).toHaveLength(1)
    expect(actionable[0]?.attributes('data-testid')).toBe('turn-clarification')
    expect(wrapper.get('[data-testid="plan-confirmation"]').attributes('data-readonly')).toBe('true')
    expect(wrapper.get('[data-testid="plan-confirmation"]').find('[data-action="confirm-plan"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="plan-invalidated-notice"]').find('[data-action="regenerate-plan"]').exists()).toBe(false)
  })

  it('keeps only the newest clarification card interactive and degrades older ones to read-only', async () => {
    const first = { ...mapAnswerResponse(localPartialReadyResponse()), turnId: 'turn-local-old' }
    const second = { ...mapAnswerResponse(localPartialReadyResponse()), turnId: 'turn-local-new' }
    const wrapper = mountThread([
      semanticMessage('semantic-local-old', first),
      semanticMessage('semantic-local-new', second),
    ])

    const cards = wrapper.findAll('[data-testid="turn-clarification"]')
    expect(cards).toHaveLength(2)
    expect(cards[0]?.attributes('data-readonly')).toBe('true')
    expect(cards[0]?.text()).toContain('此澄清已被后续轮次取代，仅作记录。')
    expect(cards[1]?.attributes('data-readonly')).toBeUndefined()
    await cards[0]?.get('[data-clarification-option="project-b"]').trigger('click')
    expect(wrapper.emitted('clarificationSubmit')).toBeUndefined()
  })

  it('keeps the confirmation card actionable only while the tab-memory opaque state is present', async () => {
    const answer = mapAnswerResponse(confirmationRequiredResponse())
    const current = session([semanticMessage('semantic-confirmation', answer)])
    current.pendingConfirmation = {
      confirmationId: 'confirmation-01',
      confirmationPlan: 'opaque-envelope',
      planFingerprint: 'sha256:opaque-fingerprint',
      integrityToken: 'opaque-integrity-token',
      expiresAt: '2026-08-10T12:10:00Z',
    }
    const wrapper = mount(ConversationThread, {
      props: { session: current, role: 'INTERVIEWER', project: previewPublicContent.projects[0], pending: false },
    })

    expect(wrapper.get('[data-testid="plan-confirmation"]').text()).toContain('计划确认')
    await wrapper.get('[data-action="confirm-plan"]').trigger('click')
    expect(wrapper.emitted('confirmPlan')?.[0]?.[0]).toMatchObject({ confirmationPlan: 'opaque-envelope' })
  })

  it('keeps a confirmation card as a read-only record once its envelope state is gone', () => {
    const answer = mapAnswerResponse(confirmationRequiredResponse())
    const current = session([semanticMessage('semantic-confirmation', answer)])
    const wrapper = mount(ConversationThread, {
      props: { session: current, role: 'INTERVIEWER', project: previewPublicContent.projects[0], pending: false },
    })

    const card = wrapper.get('[data-testid="plan-confirmation"]')
    expect(card.attributes('data-readonly')).toBe('true')
    expect(card.text()).toContain('该计划已关闭，仅作记录')
    expect(card.find('[data-action="confirm-plan"]').exists()).toBe(false)
  })

  it('shows an invalidated-plan notice without silently rendering a replacement plan', () => {
    const answer = mapAnswerResponse(partialSuccessResponse())
    answer.semanticTurn = {
      ...answer.semanticTurn!,
      disposition: 'REJECTED',
      displayPlan: undefined,
      taskSummary: undefined,
      completedTasks: [],
      planChange: { summary: '内容版本已变化，请重新生成计划', changeLabels: ['内容版本变化'] },
    }
    const wrapper = mountThread([semanticMessage('semantic-invalidated', answer)])

    expect(wrapper.get('[data-testid="plan-invalidated-notice"]').text()).toContain('重新生成计划')
    expect(wrapper.find('[data-testid="plan-confirmation"]').exists()).toBe(false)
  })

  it('keeps only the newest confirmation card actionable so an old card cannot submit a mismatched envelope', async () => {
    const first = { ...mapAnswerResponse(confirmationRequiredResponse()), turnId: 'turn-confirm-old' }
    const second = { ...mapAnswerResponse(confirmationRequiredResponse()), turnId: 'turn-confirm-new' }
    const current = session([
      semanticMessage('semantic-confirmation-old', first),
      semanticMessage('semantic-confirmation-new', second),
    ])
    current.pendingConfirmation = {
      confirmationId: 'confirmation-02',
      confirmationPlan: 'opaque-envelope-2',
      planFingerprint: 'sha256:opaque-fingerprint-2',
      integrityToken: 'opaque-integrity-token-2',
      expiresAt: '2026-08-10T12:20:00Z',
    }
    const wrapper = mount(ConversationThread, {
      props: { session: current, role: 'INTERVIEWER', project: previewPublicContent.projects[0], pending: false },
    })

    const cards = wrapper.findAll('[data-testid="plan-confirmation"]')
    expect(cards).toHaveLength(2)
    // 旧卡只读：不渲染操作按钮，避免「旧文案 + 新信封」错配提交（FE-F10）
    expect(cards[0]?.attributes('data-readonly')).toBe('true')
    expect(cards[0]?.find('[data-action="confirm-plan"]').exists()).toBe(false)
    expect(cards[0]?.text()).toContain('仅作记录')
    await cards[1]?.get('[data-action="confirm-plan"]').trigger('click')
    expect(wrapper.emitted('confirmPlan')?.[0]?.[0]).toMatchObject({ confirmationId: 'confirmation-02' })
  })

  it('renders the adjustment bar and submits a trimmed instruction', async () => {
    const wrapper = mount(ConversationThread, {
      props: {
        session: session([semanticMessage('m1')]),
        role: 'INTERVIEWER',
        project: previewPublicContent.projects[0],
        pending: false,
        adjustment: { planTitle: '5 步 · 从了解到推荐' },
      },
    })

    const bar = wrapper.get('[data-testid="plan-adjustment-bar"]')
    expect(bar.text()).toContain('正在调整当前计划')
    expect(bar.text()).toContain('5 步 · 从了解到推荐')
    expect(bar.get('[data-action="submit-adjustment"]').attributes('disabled')).toBeDefined()
    await bar.get('[data-adjustment-input]').setValue('  去掉总结那一步  ')
    await bar.get('[data-action="submit-adjustment"]').trigger('click')
    expect(wrapper.emitted('adjustSubmit')).toEqual([['去掉总结那一步']])
    await bar.get('[data-action="exit-adjustment"]').trigger('click')
    expect(wrapper.emitted('adjustExit')).toEqual([[]])
  })

  it('shows a restrained placeholder line for a dismissed plan change', () => {
    const answer = mapAnswerResponse(partialSuccessResponse())
    answer.semanticTurn = {
      ...answer.semanticTurn!,
      disposition: 'REJECTED',
      displayPlan: undefined,
      taskSummary: undefined,
      completedTasks: [],
      planChange: { summary: '内容版本已变化', changeLabels: [] },
    }
    const message = semanticMessage('semantic-dismissed', answer)
    const wrapper = mount(ConversationThread, {
      props: {
        session: session([message]),
        role: 'INTERVIEWER',
        project: previewPublicContent.projects[0],
        pending: false,
        dismissedPlanChanges: new Set([answer.turnId]),
      },
    })

    expect(wrapper.find('[data-testid="plan-invalidated-notice"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('已暂不处理')
  })

  it('renders a disabled retry-after countdown as its only recovery action', () => {
    const wrapper = mountThread([], false, null, {
      message: '请稍后再试',
      action: 'RETRY_AFTER',
      requestId: 'req-rate-12345678',
      retryAfterSeconds: 12,
    })

    const actions = wrapper.findAll('[data-answer-recovery-action]')
    expect(actions).toHaveLength(1)
    expect(actions[0]?.attributes('data-answer-recovery-action')).toBe('retry')
    expect(actions[0]?.attributes('disabled')).toBeDefined()
    expect(actions[0]?.text()).toContain('12 秒后可重试')
    expect(wrapper.get('[data-answer-support-reference]').text()).toContain('req-rate')
  })

  it('keeps a validation input available and renders only a correction action', async () => {
    const wrapper = mountThread([
      {
        id: 'user-validation',
        role: 'USER',
        content: '可修改的输入',
        answer: null,
        evidenceIds: [],
        createdAt: 1,
      },
    ], false, null, {
      message: '请检查输入后再试',
      action: 'CORRECT_INPUT',
    })

    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('请检查输入后再试')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('可修改的输入')
    expect(wrapper.findAll('[data-answer-recovery-action]')).toHaveLength(1)
    expect(wrapper.get('[data-answer-recovery-action]').attributes('data-answer-recovery-action'))
      .toBe('correct-input')
  })

  it('emits a safe back-navigation action for unavailable project context', async () => {
    const wrapper = mountThread([], false, null, {
      message: '当前项目不可用',
      action: 'NAVIGATE_BACK',
    })

    const back = wrapper.get('[data-answer-recovery-action]')
    expect(back.attributes('data-answer-recovery-action')).toBe('navigate-back')
    expect(back.attributes('href')).toBeUndefined()
    await back.trigger('click')
    expect(wrapper.emitted('navigateBack')).toEqual([[]])
  })

  it('renders an unknown failure as a single generic retry action', () => {
    const wrapper = mountThread([], false, null, {
      message: '暂时无法完成，请稍后重试',
      action: 'RETRY',
    })

    expect(wrapper.findAll('[data-answer-recovery-action]')).toHaveLength(1)
    expect(wrapper.get('[data-answer-recovery-action]').attributes('data-answer-recovery-action'))
      .toBe('retry')
  })

  it('emits a section evidence inspection instead of a follow-up request', async () => {
    const wrapper = mountThread([answerMessageFixture])
    await wrapper.get('[data-section-evidence]').trigger('click')

    expect(wrapper.emitted('inspectEvidence')).toEqual([[
      {
        messageId: 'agent-1',
        evidenceIds: ['sql-audit-delivery-set'],
        sectionType: 'VERIFICATION',
      },
    ]])
    expect(wrapper.emitted('followUp')).toBeUndefined()
  })

  it('renders a v2 GENERAL answer with a 通用知识 tag and no evidence citations', () => {
    const wrapper = mountThread([
      {
        id: 'agent-general',
        role: 'AGENT',
        content: 'HTTP 是无状态协议。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'GENERAL_KNOWLEDGE',
          answerScope: 'GENERAL',
          sections: [{
            key: 'GENERAL:0',
            type: 'GENERAL',
            title: '',
            sourceScope: 'GENERAL',
            content: 'HTTP 是无状态的应用层协议。',
            claimIds: [],
            evidenceIds: [],
          }],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('通用知识')
    expect(wrapper.text()).toContain('HTTP 是无状态的应用层协议')
    expect(wrapper.text()).not.toContain('基于作者审核资料生成')
  })

  it('renders a v2 PORTFOLIO answer with 作品集资料 tag and evidence citations', () => {
    const wrapper = mountThread([
      {
        id: 'agent-portfolio',
        role: 'AGENT',
        content: 'SQL 审计项目...',
        createdAt: 2,
        evidenceIds: ['sql-audit-delivery-set'],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'VERIFIED',
          intent: 'PORTFOLIO_GROUNDED',
          answerScope: 'PORTFOLIO',
          sections: [{
            key: 'BOUNDARY:0',
            type: 'BOUNDARY',
            title: '',
            sourceScope: 'PORTFOLIO',
            content: 'SQL 审计项目交付了完整流水线。',
            claimIds: ['claim-sql-audit-delivered'],
            evidenceIds: ['sql-audit-delivery-set'],
          }],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: ['sql-audit-delivery-set'],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('作品集资料')
    expect(wrapper.text()).toContain('SQL 审计项目交付了完整流水线。')
    // PORTFOLIO sections should render claim/evidence reference hooks
    expect(wrapper.find('[data-section-citation="sql-audit-delivery-set"]').exists()).toBe(true)
    expect(wrapper.find('[data-message-evidence="sql-audit-delivery-set"]').exists()).toBe(false)
  })

  it('renders a v2 HYBRID answer with block-level sourceScope tags split visually', () => {
    const wrapper = mountThread([
      {
        id: 'agent-hybrid',
        role: 'AGENT',
        content: '先讲通用原理，再讲作者实现。',
        createdAt: 2,
        evidenceIds: ['sql-audit-delivery-set'],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'VERIFIED',
          intent: 'HYBRID',
          answerScope: 'HYBRID',
          sections: [
            {
              key: 'GENERAL:0',
              type: 'GENERAL',
              title: '',
              sourceScope: 'GENERAL',
              content: 'RBAC 是常见的访问控制模型。',
              claimIds: [],
              evidenceIds: [],
            },
            {
              key: 'BOUNDARY:1',
              type: 'BOUNDARY',
              title: '',
              sourceScope: 'PORTFOLIO',
              content: '作者在 SQL 审计项目里使用 RBAC 隔离了审计师角色。',
              claimIds: ['claim-sql-audit-delivered'],
              evidenceIds: ['sql-audit-delivery-set'],
            },
          ],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: ['sql-audit-delivery-set'],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('混合回答')
    expect(wrapper.text()).toContain('RBAC 是常见的访问控制模型')
    expect(wrapper.text()).toContain('作者在 SQL 审计项目里使用 RBAC')
    // 通用知识章节不应附作品集引用；PORTFOLIO 章节保留引用钩子
    const generalSection = wrapper.get('[data-section-type="GENERAL"]')
    expect(generalSection.find('[data-section-citation]').exists()).toBe(false)
    expect(wrapper.find('[data-section-citation="sql-audit-delivery-set"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-block-scope]')).toHaveLength(0)
  })

  it('renders dynamic suggestedQuestions as clickable follow-ups and supports empty array', async () => {
    const wrapper = mountThread([
      {
        id: 'agent-suggested',
        role: 'AGENT',
        content: '答案',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'ANSWERED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'GENERAL_KNOWLEDGE',
          answerScope: 'GENERAL',
          sections: [{
            key: 'GENERAL:0',
            type: 'GENERAL',
            title: '',
            sourceScope: 'GENERAL',
            content: '一些通用内容',
            claimIds: [],
            evidenceIds: [],
          }],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [
            {
              text: '介绍一下 SQL 审计项目',
              projectSlug: 'sql-audit',
              caseSlug: null,
              facet: 'OVERVIEW',
            },
            {
              text: '讲讲作者的 RBAC 实现',
              projectSlug: 'sql-audit',
              caseSlug: 'sql-audit-rbac',
              facet: 'IMPLEMENTATION',
            },
          ],
        },
      },
    ])

    const followups = wrapper.findAll('[data-suggested-follow-up]')
    expect(followups).toHaveLength(2)
    await followups[0].trigger('click')
    expect(wrapper.emitted('submitSuggestion')).toEqual([[
      {
        text: '介绍一下 SQL 审计项目',
        projectSlug: 'sql-audit',
        caseSlug: null,
        facet: 'OVERVIEW',
      },
    ]])
  })

  it('renders three suggested questions on a legacy sections answer with the full text as title', async () => {
    const answer = answerMessageFixture.answer
    if (!answer) throw new Error('fixture answer missing')
    const wrapper = mountThread([
      {
        ...answerMessageFixture,
        answer: {
          ...answer,
          suggestedQuestions: [
            { text: '建议问题一', projectSlug: 'sql-audit', caseSlug: null, facet: null },
            { text: '建议问题二', projectSlug: null, caseSlug: 'multilingual-image-preservation', facet: null },
            { text: '建议问题三', projectSlug: 'codegraph-evaluation', caseSlug: null, facet: null },
          ],
        },
      },
    ])

    const followups = wrapper.findAll('[data-suggested-follow-up]')
    expect(followups).toHaveLength(3)
    expect(followups[0]?.attributes('title')).toBe('建议问题一')
    expect(followups[1]?.attributes('title')).toBe('建议问题二')
    expect(followups[2]?.attributes('title')).toBe('建议问题三')

    await followups[1]?.trigger('click')
    expect(wrapper.emitted('submitSuggestion')).toEqual([[
      { text: '建议问题二', projectSlug: null, caseSlug: 'multilingual-image-preservation', facet: null },
    ]])
  })

  it('shows a restrained degraded notice only when degraded is explicit', () => {
    const wrapper = mountThread([
      {
        id: 'agent-degraded',
        role: 'AGENT',
        content: '已退回到基础回答。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '预设回答',
          summary: '来自已发布问题的固定回答',
          resolution: 'ANSWERED',
          answerSource: 'PRESET',
          generationMode: 'FALLBACK',
          degraded: true,
          verification: 'VERIFIED',
          intent: 'PORTFOLIO_GROUNDED',
          answerScope: 'PORTFOLIO',
          sections: [],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('已切换到基础回答')
    // 不应伪装成 MODEL
    expect(wrapper.text()).not.toContain('MODEL')
  })

  it('labels TIME_SENSITIVE as needing real-time information without inventing freshness', () => {
    const wrapper = mountThread([
      {
        id: 'agent-time',
        role: 'AGENT',
        content: '我无法确认最新版本。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'BOUNDARY',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'TIME_SENSITIVE',
          answerScope: 'GENERAL',
          sections: [{
            key: 'GENERAL:0',
            type: 'GENERAL',
            title: '',
            sourceScope: 'GENERAL',
            content: '我目前无法访问实时网络信息。',
            claimIds: [],
            evidenceIds: [],
          }],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('暂时不可用')
    expect(wrapper.text()).toContain('无法访问实时网络信息')
  })

  it('labels UNSUPPORTED_OR_UNSAFE as a refusal without pretending to be a normal answer', () => {
    const wrapper = mountThread([
      {
        id: 'agent-rejected',
        role: 'AGENT',
        content: '我不会协助这个请求。',
        createdAt: 2,
        evidenceIds: [],
        answer: {
          title: '',
          summary: '',
          resolution: 'REJECTED',
          answerSource: null,
          generationMode: 'MODEL',
          verification: 'NOT_APPLICABLE',
          intent: 'UNSUPPORTED_OR_UNSAFE',
          answerScope: 'CONVERSATION',
          sections: [{
            key: 'GENERAL:0',
            type: 'GENERAL',
            title: '',
            sourceScope: 'GENERAL',
            content: '这个请求涉及私密信息，无法处理。',
            claimIds: [],
            evidenceIds: [],
          }],
          turnId: 'turn-inline',
          contentVersion: '2026-07-21',
          coveredTopics: [],
          guidanceStage: null,
          evidenceIds: [],
          suggestedQuestionPresetIds: [],
          suggestedQuestions: [],
        },
      },
    ])

    expect(wrapper.text()).toContain('无法处理该请求')
    expect(wrapper.text()).not.toContain('已核验回答')
  })

  // —— 结构化作品推荐卡片 ——
  function recommendationMessage(overrides: Record<string, unknown> = {}): AgentMessage {
    return {
      id: 'agent-reco',
      role: 'AGENT',
      content: '选出了 2 个作品。',
      createdAt: 2,
      evidenceIds: ['evidence-1', 'evidence-2'],
      answer: {
        title: '推荐结果',
        summary: '',
        resolution: 'ANSWERED',
        answerSource: 'RETRIEVAL',
        generationMode: 'DETERMINISTIC',
        verification: 'VERIFIED',
        intent: 'PORTFOLIO_GROUNDED',
        answerScope: 'PORTFOLIO',
        sections: [{
          key: 'BOUNDARY:0',
          type: 'BOUNDARY',
          title: '',
          sourceScope: 'PORTFOLIO',
          content: '我按公开证据选出了 2 个作品。',
          claimIds: [],
          evidenceIds: ['evidence-1'],
        }],
        turnId: 'turn-reco',
        contentVersion: '2026-07-21',
        coveredTopics: [],
        guidanceStage: null,
        evidenceIds: ['evidence-1', 'evidence-2'],
        suggestedQuestionPresetIds: [],
        suggestedQuestions: [],
        portfolioRecommendation: {
          recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
          context: {
            recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
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
              evidenceIds: ['evidence-1'],
            },
            {
              portfolioId: 'case-2',
              title: '案例二',
              route: '/cases/case-two',
              matchReasons: ['补充 PostgreSQL 与验证能力'],
              evidenceIds: ['evidence-2'],
            },
          ],
          satisfiedConstraints: ['受众角色', '数量'],
          unsatisfiedConstraints: [],
        },
        ...overrides,
      },
    } as unknown as AgentMessage
  }

  it('does not render a recommendation container on a plain answer', () => {
    const wrapper = mountThread([answerMessageFixture])

    expect(wrapper.find('[data-portfolio-recommendation]').exists()).toBe(false)
  })

  it('renders recommendation items in backend order with reasons and portfolio links', () => {
    const wrapper = mountThread([recommendationMessage()])

    const cards = wrapper.findAll('[data-recommendation-item]')
    expect(cards).toHaveLength(2)
    expect(cards.map((card) => card.attributes('data-portfolio-id')))
      .toEqual(['project-1', 'case-2'])
    expect(cards[0]?.text()).toContain('项目一')
    expect(cards[0]?.text()).toContain('匹配后端能力要求')
    expect(cards[1]?.text()).toContain('案例二')
    // 查看作品按钮使用后端 route
    expect(cards[0]?.get('[data-recommendation-link]').attributes('href')).toBe('/projects/project-one')
  })

  it('shows unsatisfied constraints without fabricating fake cards when items is empty', () => {
    const wrapper = mountThread([
      recommendationMessage({
        portfolioRecommendation: {
          recommendationBatchId: 'rec_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
          context: {
            recommendationBatchId: 'rec_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
            contentVersion: 'public-2026-07-31',
            careerTrack: null,
            audienceRole: 'INTERVIEWER',
            capabilityCodes: ['POSTGRESQL'],
            requestedSize: 2,
            selectedPortfolioIds: [],
          },
          items: [],
          satisfiedConstraints: [],
          unsatisfiedConstraints: ['没有完全匹配的组合'],
        },
      }),
    ])

    expect(wrapper.findAll('[data-recommendation-item]')).toHaveLength(0)
    expect(wrapper.get('[data-recommendation-unsatisfied]').text())
      .toContain('没有完全匹配的组合')
  })

  it('keeps showing the recommendation cards on a degraded answer', () => {
    const wrapper = mountThread([
      recommendationMessage({
        generationMode: 'FALLBACK',
        degraded: true,
      }),
    ])

    expect(wrapper.get('[data-degraded-notice]').text()).toBe('已切换到基础回答')
    expect(wrapper.findAll('[data-recommendation-item]')).toHaveLength(2)
  })

  it('emits a refine action with the batch id and a natural-language question', async () => {
    const wrapper = mountThread([recommendationMessage()])

    // 第 2 张卡片点击「换掉这个」→ 发送「换掉第二个」并回传批次 ID
    const actions = wrapper.findAll('[data-recommendation-refine="replace"]')
    await actions[1]?.trigger('click')

    expect(wrapper.emitted('refineRecommendation')).toEqual([[
      {
        question: '换掉第二个',
        recommendationContext: {
          recommendationBatchId: 'rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
          contentVersion: 'public-2026-07-31',
          careerTrack: null,
          audienceRole: 'INTERVIEWER',
          capabilityCodes: ['POSTGRESQL', 'RAG'],
          requestedSize: 2,
          selectedPortfolioIds: ['project-1', 'case-2'],
        },
      },
    ]])
  })

  it('renders recommendations inside the agent message without overflowing on narrow screens', () => {
    const wrapper = mountThread([recommendationMessage()])

    // 推荐容器复用现有响应式类，不产生额外页面或横向滚动结构
    const container = wrapper.get('[data-portfolio-recommendation]')
    expect(container.classes()).toContain('portfolio-recommendation')
    const grid = wrapper.find('[data-portfolio-recommendation] .reco-grid')
    expect(grid.exists()).toBe(true)
  })

  it('focuses the exact answer section without smooth scrolling under reduced motion', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    const wrapper = mountThread([answerMessageFixture])
    const section = wrapper.get('[data-section-type="VERIFICATION"]')
    const scrollArea = wrapper.get('.conversation__scroll')
    const scrollTo = vi.fn()
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })

    await wrapper.setProps({
      focusTarget: {
        requestId: 1,
        messageId: 'agent-1',
        sectionType: 'VERIFICATION',
      },
    })
    await flushPromises()

    expect(scrollTo).toHaveBeenCalledWith({
      top: 0,
      behavior: 'auto',
    })
    expect(section.attributes('data-answer-focus')).toBe('true')
  })

  it('submits a suggested question immediately', async () => {
    const wrapper = mountThread()
    const question = previewPublicContent.projects[0].suggestedQuestions[0]

    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('empty')
    await wrapper.get('[data-suggested-question]').trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[question]])
  })

  it('renders a user bubble and an open Agent document', () => {
    const wrapper = mountThread([
      {
        id: 'user-1',
        role: 'USER',
        content: '为什么没有运行产物？',
        answer: null,
        evidenceIds: [],
        createdAt: 2,
      },
      {
        id: 'agent-1',
        role: 'AGENT',
        content: '当前仅作为审计证据保留。',
        answer: null,
        evidenceIds: [],
        createdAt: 3,
      },
    ])

    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('conversation')
    expect(wrapper.get('[data-message-id="user-1"] .message__body').text())
      .toBe('为什么没有运行产物？')
    expect(wrapper.get('[data-message-id="agent-1"]').classes()).toContain('message--agent')
    expect(wrapper.get('[data-message-id="agent-1"]').find('.message__bubble').exists()).toBe(false)
  })

  it('sends on Enter but keeps Shift+Enter for a newline', async () => {
    const wrapper = mountThread()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('继续追问')
    await textarea.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('submit')).toEqual([['继续追问']])

    await textarea.setValue('第一行')
    await textarea.trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('does not submit Enter while an IME composition is active', async () => {
    const wrapper = mountThread()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('正在组合的中文')
    await textarea.trigger('keydown', { key: 'Enter', isComposing: true })

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect((textarea.element as HTMLTextAreaElement).value).toBe('正在组合的中文')
  })

  it('marks the generating state with progressive Agent copy', () => {
    const wrapper = mountThread([], true)

    expect(wrapper.get('[data-conversation-state]').attributes('data-conversation-state'))
      .toBe('generating')
    expect(wrapper.get('[data-agent-loading]').text()).toContain('正在核验证据')
  })

  it('grows the Composer with its content up to the configured height', async () => {
    const wrapper = mountThread()
    const textarea = wrapper.get('textarea')
    Object.defineProperty(textarea.element, 'scrollHeight', {
      configurable: true,
      value: 96,
    })

    await textarea.setValue('需要更多空间的追问')
    await textarea.trigger('input')

    expect((textarea.element as HTMLTextAreaElement).style.height).toBe('96px')
  })

  it('recovers a failed question into the focused Composer for editing', async () => {
    const wrapper = mountThread([
      {
        id: 'user-1',
        role: 'USER',
        content: '修改这条失败的问题',
        answer: null,
        evidenceIds: [],
        createdAt: 2,
      },
    ], false, null, {
      message: '请检查问题后再试',
      action: 'CORRECT_INPUT',
    })
    document.body.appendChild(wrapper.element)

    await wrapper.get('[data-answer-edit]').trigger('click')

    expect(wrapper.get('textarea').element).toBe(document.activeElement)
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value)
      .toBe('修改这条失败的问题')
    wrapper.unmount()
  })

  it('offers a jump to the latest answer after the reader scrolls away', async () => {
    const wrapper = mountThread([
      {
        id: 'agent-1',
        role: 'AGENT',
        content: '较早的回答',
        answer: null,
        evidenceIds: [],
        createdAt: 2,
      },
    ])
    const scrollArea = wrapper.get('.conversation__scroll')
    Object.defineProperties(scrollArea.element, {
      scrollHeight: { configurable: true, value: 1000 },
      scrollTop: { configurable: true, writable: true, value: 100 },
      clientHeight: { configurable: true, value: 300 },
    })
    const scrollTo = vi.fn()
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))

    await scrollArea.trigger('scroll')
    await wrapper.get('[data-jump-latest]').trigger('click')

    expect(scrollTo).toHaveBeenCalledWith({ top: 1000, behavior: 'auto' })
    expect(wrapper.find('[data-jump-latest]').exists()).toBe(false)
  })

  it('does not auto-scroll when messages update after the reader moves away', async () => {
    const wrapper = mountThread([answerMessageFixture])
    const scrollArea = wrapper.get('.conversation__scroll')
    Object.defineProperties(scrollArea.element, {
      scrollHeight: { configurable: true, value: 1000 },
      scrollTop: { configurable: true, writable: true, value: 100 },
      clientHeight: { configurable: true, value: 300 },
    })
    const scrollTo = vi.fn()
    Object.defineProperty(scrollArea.element, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })

    await scrollArea.trigger('scroll')
    await wrapper.setProps({
      session: session([
        answerMessageFixture,
        {
          ...answerMessageFixture,
          id: 'agent-2',
          createdAt: 4,
        },
      ]),
    })
    await flushPromises()

    expect(scrollTo).not.toHaveBeenCalled()
    expect(wrapper.find('[data-jump-latest]').exists()).toBe(true)
  })
})

// ── P3：sourceReferences / 执行快照 / ContextHandle 继续入口（handoff §3.2/§6/§7/§8）──
describe('ConversationThread P3 surfaces', () => {
  function p3Answer() {
    return {
      title: 'P3 answer',
      summary: 'grounded answer',
      resolution: 'ANSWERED' as const,
      turnId: 'turn-p3',
      contentVersion: 'public-2026-07-31',
      coveredTopics: [],
      guidanceStage: null,
      evidenceIds: [],
      suggestedQuestionPresetIds: [],
      suggestedQuestions: [],
      sections: [{
        key: 'SOLUTION:0',
        type: 'SOLUTION' as const,
        title: '技术方案',
        sourceScope: 'PORTFOLIO' as const,
        content: '受控正文',
        evidenceIds: [],
        claimIds: [],
        sourceReferences: [{
          referenceKey: 'SRC_SQL_AUDIT_DELIVERED',
          label: 'SQL 审计 · 交付证据',
          sourceType: 'DOCUMENT' as const,
          subjectRoute: '/projects/sql-audit',
          evidenceRoute: '/evidence?evidence=evi-sql-audit',
          publishedVersion: 'public-2026-07-31',
        }],
      }],
      semanticTurn: {
        contractVersion: 'stp-v1' as const,
        disposition: 'READY' as const,
        completedTasks: [{
          displayIndex: '01',
          goalLabel: '介绍项目',
          sourceDomain: 'PORTFOLIO' as const,
          contextHandle: 'handle-opaque',
          resultPayload: { kind: 'SECTION_RESULT' as const, blocks: [] },
        }],
        execution: {
          overallStatus: 'COMPLETED' as const,
          tasks: [{
            displayIndex: '01',
            finalStatus: 'COMPLETED' as const,
            stages: [
              { code: 'SCOPE_CONFIRMED' as const, label: '确认查询范围', status: 'COMPLETED' as const },
              { code: 'RESULT_COMPOSED' as const, label: '形成回答', status: 'COMPLETED' as const },
            ],
          }],
        },
      },
    }
  }

  function mountP3() {
    const message = semanticMessage('agent-p3', p3Answer() as never)
    return mount(ConversationThread, {
      props: {
        session: session([message]),
        role: 'INTERVIEWER',
        project: previewPublicContent.projects[0],
        pending: false,
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
  }

  it('renders public source references, the FINAL execution snapshot, and a continue-from-result entry', () => {
    const wrapper = mountP3()

    expect(wrapper.find('[data-source-reference="SRC_SQL_AUDIT_DELIVERED"]').exists()).toBe(true)
    expect(wrapper.find('[data-execution-snapshot]').exists()).toBe(true)
    expect(wrapper.find('[data-continue-task="01"]').exists()).toBe(true)
    // 有 sourceReferences 时不渲染旧 evidenceId 引用按钮（过渡双读：P3 优先）。
    expect(wrapper.find('[data-section-citation]').exists()).toBe(false)
  })

  it('emits continueFromContext with the task handle and RECENT_SEMANTIC_TASK for a section result', async () => {
    const wrapper = mountP3()

    await wrapper.get('[data-continue-task="01"]').trigger('click')

    expect(wrapper.emitted('continueFromContext')).toBeTruthy()
    const event = wrapper.emitted('continueFromContext')?.[0]?.[0] as {
      contextHandle: string
      expectedContextType: string
      question: string
    }
    expect(event.contextHandle).toBe('handle-opaque')
    expect(event.expectedContextType).toBe('RECENT_SEMANTIC_TASK')
  })

  it('renders P3 recommendation results and refines them through their context handle', async () => {
    const answer = p3Answer()
    answer.semanticTurn.completedTasks = [{
      displayIndex: '01',
      goalLabel: '推荐后端项目',
      sourceDomain: 'PORTFOLIO',
      contextHandle: 'recommendation-handle',
      resultPayload: {
        kind: 'RECOMMENDATION_RESULT',
        recommendations: [{
          portfolioId: 'sql-audit',
          title: 'SQL 审计与故障排查工具',
          route: '/projects/sql-audit',
          matchReasons: ['体现后端交付闭环'],
          evidenceIds: [],
          sourceReferences: [{
            referenceKey: 'E-SQL-AUDIT',
            label: 'SQL 审计交付证据',
            sourceType: 'DOCUMENT',
            subjectRoute: '/projects/sql-audit',
            evidenceRoute: '/evidence?evidence=evi-sql-audit',
            publishedVersion: 'public-2026-07-31',
          }],
        }],
      },
    }] as never
    const wrapper = mountThread([semanticMessage('agent-p3-recommendation', answer as never)])

    expect(wrapper.get('[data-portfolio-recommendation]').text())
      .toContain('SQL 审计与故障排查工具')
    expect(wrapper.find('[data-source-reference="E-SQL-AUDIT"]').exists()).toBe(true)

    await wrapper.get('[data-recommendation-refine="replace"]').trigger('click')
    expect(wrapper.emitted('continueFromContext')?.[0]?.[0]).toEqual({
      question: '换掉第一个',
      contextHandle: 'recommendation-handle',
      expectedContextType: 'RECOMMENDATION',
    })
  })

  // P3 防御性展示（handoff §7/§9）：当顶层宣称 ANSWERED + VERIFIED 但 FINAL 执行快照
  // 全任务全阶段 FAILED 时，前端不能静默包装成完整成功，也不能伪造阶段成功。
  it('shows a degraded notice when ANSWERED + VERIFIED meets an all-FAILED FINAL snapshot', () => {
    const conflicted = {
      ...p3Answer(),
      evidenceState: 'VERIFIED' as const,
      semanticTurn: {
        ...p3Answer().semanticTurn,
        execution: {
          overallStatus: 'FAILED' as const,
          tasks: [{
            displayIndex: '01',
            finalStatus: 'FAILED' as const,
            stages: [
              { code: 'SCOPE_CONFIRMED' as const, label: '确认查询范围', status: 'FAILED' as const },
              { code: 'MATERIALS_RETRIEVED' as const, label: '读取作品集材料', status: 'FAILED' as const },
              { code: 'EVIDENCE_VALIDATED' as const, label: '校验证据', status: 'FAILED' as const },
              { code: 'RESULT_COMPOSED' as const, label: '形成回答', status: 'FAILED' as const },
            ],
          }],
        },
      },
    }
    const wrapper = mount(ConversationThread, {
      props: {
        session: session([semanticMessage('agent-p3-conflict', conflicted as never)]),
        role: 'INTERVIEWER',
        project: previewPublicContent.projects[0],
        pending: false,
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    // 必须出现非阻断降级提示。
    const notice = wrapper.find('[data-execution-conflict-notice]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain('执行能力降级')
    // 快照仍按服务端 FINAL 状态原样呈现，不得改写为成功。
    const stages = wrapper.findAll('[data-stage-status]')
    expect(stages.length).toBeGreaterThan(0)
    for (const stage of stages) {
      expect(stage.attributes('data-stage-status')).toBe('FAILED')
    }
    expect(wrapper.find('[data-execution-overall]').attributes('data-execution-overall')).toBe('FAILED')
  })

  it('does not show the conflict notice when execution succeeded', () => {
    const wrapper = mountP3()
    expect(wrapper.find('[data-execution-conflict-notice]').exists()).toBe(false)
  })
})
