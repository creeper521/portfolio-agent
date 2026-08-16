import { describe, expect, test } from 'vitest'
import {
  appendAcceptedTurn,
  createBehaviorRequest,
  scenarioSeedFor,
  scenarioTurnFor,
  type BehaviorConversationState,
  type BehaviorExchange,
  type ScenarioSeed,
} from './agentBehaviorRequest'
import type { BehaviorTurn, TurnTransportOutcome } from './agentBehaviorTypes'

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

const publicContent = {
  projects: [{ slug: 'sql-audit' }, { slug: 'activity-engineering' }],
  cases: [{ slug: 'multilingual-image-preservation' }],
}

function emptyState(): BehaviorConversationState {
  return { acceptedMessages: [], diagnosticTurnIds: [], historyTurnIds: [] }
}

function stateWithResumeToken(resumeToken: string): BehaviorConversationState {
  return { ...emptyState(), resumeToken }
}

function turn(id: string, input: string): BehaviorTurn {
  return { id, input, inputClass: 'AMBIGUOUS_REFERENCE' }
}

function exchange(turnId: string, outcome: TurnTransportOutcome): BehaviorExchange {
  return { turnId, outcome, userContent: 'synthetic-user', assistantContent: 'synthetic-assistant' }
}

describe('privacy-safe behavior request history', () => {
  test('failed turns never enter later history', () => {
    const state = appendAcceptedTurn(emptyState(), exchange('t1', 'TIMED_OUT'))
    expect(createBehaviorRequest(state, turn('t2', '继续')).apiInput.messages).toEqual([])
    expect(state.diagnosticTurnIds).toEqual(['t1'])
    expect(state.historyTurnIds).toEqual([])
  })

  test('resume token travels only in the header', () => {
    const request = createBehaviorRequest(stateWithResumeToken('secret-token'), turn('t2', '继续'))
    expect(request.headers['X-Conversation-Resume-Token']).toBe('secret-token')
    expect(JSON.stringify(request.body)).not.toContain('secret-token')
    expect(request.apiInput.resumeToken).toBe('secret-token')
  })

  test('accepted exchanges append alternating user and assistant history', () => {
    const state = appendAcceptedTurn(emptyState(), exchange('t1', 'ACCEPTED'))
    const request = createBehaviorRequest(state, turn('t2', '继续'))
    expect(request.apiInput.messages).toEqual([
      { role: 'USER', content: 'synthetic-user' },
      { role: 'ASSISTANT', content: 'synthetic-assistant' },
    ])
    expect(request.historyTurnIds).toEqual(['t1'])
  })

  test('explicit preset clears stale context before projecting a request', () => {
    const state: BehaviorConversationState = {
      ...emptyState(),
      activeSubjects: [{ subjectType: 'PROJECT', subjectId: 'old-project' }],
      contextReference: { contextHandle: 'old-handle', expectedContextType: 'RECENT_SEMANTIC_TASK' },
      pendingClarification: { clarificationId: 'old-clarification' },
      pageHint: 'old-page',
    }
    const request = createBehaviorRequest({ ...state }, { id: 't2', input: '新预设', inputClass: 'ACTIVE_PRESET' })
    expect(request.apiInput.semanticContext).toBeUndefined()
    expect(request.apiInput.contextReference).toBeUndefined()
    expect(JSON.stringify(request.body)).not.toContain('old-project')
    expect(JSON.stringify(request.body)).not.toContain('old-handle')
  })
})

describe('backend-contract behavior request seeding', () => {
  test('request token is a plain UUID accepted by the tightened backend contract', () => {
    const request = createBehaviorRequest(emptyState(), turn('t1', '112233'))
    expect(request.apiInput.requestToken).toMatch(UUID_V4)
    expect(String((request.body as { requestToken: unknown }).requestToken)).toMatch(UUID_V4)
    expect(request.apiInput.requestToken).not.toContain('behavior-')
  })

  test('PROJECT_HINT seed binds the page project into every turn request', () => {
    const seed: ScenarioSeed = scenarioSeedFor('PROJECT_HINT', publicContent)
    expect(seed.projectSlug).toBe('sql-audit')
    expect(seed.caseSlug).toBeUndefined()
    expect(seed.state.activeSubjects).toEqual([{ subjectType: 'PROJECT', subjectId: 'sql-audit' }])
    const request = createBehaviorRequest(seed.state, scenarioTurnFor(turn('t1', '它现在做得怎么样？'), seed))
    expect(request.apiInput.projectSlug).toBe('sql-audit')
    expect(request.apiInput.semanticContext?.activeSubjects)
      .toEqual([{ subjectType: 'PROJECT', subjectId: 'sql-audit' }])
    expect((request.body as { context: { projectSlug: unknown } }).context.projectSlug).toBe('sql-audit')
  })

  test('CASE_HINT seed binds the first public case without a project hint', () => {
    const seed: ScenarioSeed = scenarioSeedFor('CASE_HINT', publicContent)
    expect(seed.caseSlug).toBe('multilingual-image-preservation')
    expect(seed.projectSlug).toBeUndefined()
    expect(seed.state.activeSubjects)
      .toEqual([{ subjectType: 'CASE', subjectId: 'multilingual-image-preservation' }])
    const request = createBehaviorRequest(seed.state, scenarioTurnFor(turn('t1', '那个的结果是什么？'), seed))
    expect(request.apiInput.caseSlug).toBe('multilingual-image-preservation')
    expect(request.apiInput.projectSlug).toBeUndefined()
  })

  test('SINGLE_SUBJECT seed carries the bound subject without a page hint', () => {
    const seed: ScenarioSeed = scenarioSeedFor('SINGLE_SUBJECT', publicContent)
    expect(seed.projectSlug).toBeUndefined()
    expect(seed.caseSlug).toBeUndefined()
    const request = createBehaviorRequest(seed.state, scenarioTurnFor(turn('t1', '112233'), seed))
    expect(request.apiInput.semanticContext?.activeSubjects)
      .toEqual([{ subjectType: 'PROJECT', subjectId: 'sql-audit' }])
    expect(request.apiInput.projectSlug).toBeUndefined()
  })

  test('FRESH scenarios stay unseeded so requests carry no implicit subject', () => {
    const seed: ScenarioSeed = scenarioSeedFor('FRESH', publicContent)
    expect(seed.projectSlug).toBeUndefined()
    expect(seed.caseSlug).toBeUndefined()
    expect(seed.state.activeSubjects).toBeUndefined()
    const request = createBehaviorRequest(seed.state, scenarioTurnFor(turn('t1', '112233'), seed))
    expect(request.apiInput.semanticContext).toBeUndefined()
    expect(request.apiInput.projectSlug).toBeUndefined()
  })
})
