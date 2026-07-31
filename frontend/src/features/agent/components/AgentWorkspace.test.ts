import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { PortfolioApiError } from '../../portfolio/api/portfolioApi'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import { WORKSPACE_SPLIT_KEY } from '../composables/useWorkspaceSplit'
import AgentWorkspace from './AgentWorkspace.vue'

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
    contextEnvelope: {
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
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
    expect(wrapper.get('.message--agent').text()).toContain('已核验回答')
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

  it('shows retrieval provenance without turning a boundary into an applicable source', async () => {
    askQuestionMock
      .mockResolvedValueOnce({
        ...answerResponse(),
        questionPresetId: undefined,
        answerSource: 'RETRIEVAL' as const,
        verification: 'PARTIALLY_VERIFIED' as const,
      })
      .mockResolvedValueOnce({
        ...answerResponse(),
        questionPresetId: undefined,
        resolution: 'BOUNDARY' as const,
        answerSource: undefined,
        verification: 'NOT_APPLICABLE' as const,
      })
    const wrapper = mountWorkspace()

    await wrapper.get('textarea').setValue('公开检索问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.findAll('.message--agent')[0].text())
      .toContain('资料检索')

    await wrapper.get('textarea').setValue('越界问题')
    await wrapper.get('.composer').trigger('submit')
    await flushPromises()
    const boundary = wrapper.findAll('.message--agent')[1].text()
    expect(boundary).toContain('当前能力边界')
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
      contextEnvelope: {
        previousContentVersion: '2026-07-21',
        projectSlugs: ['sql-audit'],
        questionPresetId: 'sql-audit-overview',
        referencedClaimIds: ['claim-sql-audit-delivered'],
        selectedSectionType: undefined,
        followUpIntent: 'CURRENT_STATUS',
      },
    }))
    const body = askQuestionMock.mock.calls[1]?.[0]
    expect(body.messages).toBeDefined()
    expect(body.messages.length).toBeGreaterThanOrEqual(2)
    expect(body.contextEnvelope).toEqual({
      previousContentVersion: '2026-07-21',
      projectSlugs: ['sql-audit'],
      questionPresetId: 'sql-audit-overview',
      referencedClaimIds: ['claim-sql-audit-delivered'],
      selectedSectionType: undefined,
      followUpIntent: 'CURRENT_STATUS',
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
          text: '补足预设一',
          audiences: ['HR' as const],
          placements: ['AGENT' as const],
        },
        {
          id: 'agent-preset-2',
          projectSlug: 'sql-audit',
          text: '补足预设二',
          audiences: ['HR' as const],
          placements: ['AGENT' as const],
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
    }))
    const diagnosticPayload = JSON.stringify(reportSpy.mock.calls.map((call) => call[0]))
    expect(diagnosticPayload).not.toContain('唯一的后端建议')
    expect(diagnosticPayload).not.toContain('诊断补足测试问题')
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
})
