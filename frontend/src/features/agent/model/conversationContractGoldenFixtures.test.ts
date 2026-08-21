import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import {
  parseConversationEnvelope,
  parseCurrentConversationPayload,
} from '../api/agentTurnApi'
import { parsePublicAgentTurn } from './publicAgentTurnMapper'

function fixture(name: string): unknown {
  let directory = process.cwd()
  for (let depth = 0; depth < 6; depth += 1) {
    const candidate = resolve(
      directory, 'contracts', 'agent-turn',
      'conversation-fixtures', name,
    )
    if (existsSync(candidate)) {
      return JSON.parse(readFileSync(candidate, 'utf8')) as unknown
    }
    directory = resolve(directory, '..')
  }
  throw new Error(`缺少 conversation fixture ${name}`)
}

describe('共享 Conversation/Turn envelope fixtures', () => {
  it('ACTIVE 与 EXPIRED summary 都携带单调 revision 和 backend actions', () => {
    const active = parseCurrentConversationPayload(
      fixture('conversation-active.json'))
    const expired = parseCurrentConversationPayload(
      fixture('conversation-expired.json'))

    expect(active).toMatchObject({
      ok: true, discussionRevision: 7,
      activeDiscussion: { status: 'ACTIVE' },
    })
    expect(expired).toMatchObject({
      ok: true, discussionRevision: 9,
      activeDiscussion: {
        status: 'EXPIRED',
        reenterAction: { continuation: { operation: 'REENTER_SUBJECT' } },
        newTopicAction: { continuation: { operation: 'EXIT_CONTEXT' } },
      },
    })
  })

  it('discussion unavailable Turn 与同响应当前 conversation projection 同时可解析', () => {
    const payload = fixture('turn-envelope-discussion-unavailable.json')
    expect(typeof payload).toBe('object')
    const record = payload as Record<string, unknown>
    const parsed = parsePublicAgentTurn(record)
    expect(parsed.ok).toBe(true)
    if (parsed.ok) {
      expect(parsed.turn).toMatchObject({
        kind: 'CAPABILITY_UNAVAILABLE',
        code: 'DISCUSSION_INTERPRETATION_UNAVAILABLE',
        retryable: true,
        suggestedActions: [
          { actionId: 'discussion-retry', inputText: '继续说明验证方式' },
          {
            actionId: 'discussion-exit',
            continuation: { operation: 'EXIT_CONTEXT' },
          },
        ],
      })
    }
    expect(parseConversationEnvelope(record.conversation)).toMatchObject({
      discussionRevision: 7,
      activeDiscussion: { status: 'ACTIVE' },
    })
  })

  it.each([
    ['turn-envelope-discussion-context-unavailable.json', 'DISCUSSION_CONTEXT_UNAVAILABLE'],
    ['turn-envelope-discussion-context-mismatch.json', 'DISCUSSION_CONTEXT_MISMATCH'],
    ['turn-envelope-discussion-subject-unavailable.json', 'DISCUSSION_SUBJECT_UNAVAILABLE'],
    ['turn-envelope-discussion-context-expired.json', 'DISCUSSION_CONTEXT_EXPIRED'],
    ['turn-envelope-free-text-unavailable.json', 'SEMANTIC_ROUTING_UNAVAILABLE'],
  ])('%s 冻结错误码与权威会话投影', (name, code) => {
    const payload = fixture(name) as Record<string, unknown>
    const parsed = parsePublicAgentTurn(payload)
    expect(parsed.ok).toBe(true)
    if (parsed.ok) {
      expect(parsed.turn).toMatchObject({ kind: 'CAPABILITY_UNAVAILABLE', code })
    }
    expect(parseConversationEnvelope(payload.conversation)).toMatchObject({
      discussionRevision: 7,
      activeDiscussion: { status: 'ACTIVE' },
    })
  })
})
