import { describe, expect, test } from 'vitest'
import {
  appendAcceptedTurn,
  createBehaviorRequest,
  type BehaviorConversationState,
  type BehaviorExchange,
} from './agentBehaviorRequest'
import type { BehaviorTurn, TurnTransportOutcome } from './agentBehaviorTypes'

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
