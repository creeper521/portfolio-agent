import { describe, expect, it } from 'vitest'
import {
  expectAnswer,
  expectAnswerKind,
  expectNonEmptyAnswer,
  type PublicTurnBody,
} from './public-turn-happy-path'

function validAnswer(): PublicTurnBody {
  return {
    kind: 'ANSWER',
    answer: {
      resolution: 'COMPLETE',
      goalResults: [{
        coverage: 'FULL',
        presentation: {
          kind: 'SECTIONED',
          sections: [{
            content: '这是有公开证据支撑的完整回答。',
            support: {
              kind: 'VERIFIED_PUBLIC_EVIDENCE',
              publicSourceKeys: ['source-1'],
            },
          }],
        },
      }],
      sourceCatalog: { sources: [{ key: 'source-1' }] },
    },
  }
}

function clone(body: PublicTurnBody): PublicTurnBody {
  return structuredClone(body)
}

describe('PublicTurn happy-path body assertions', () => {
  it('accepts a complete answer with non-empty sections and declared sources', () => {
    expect(() => expectNonEmptyAnswer(validAnswer())).not.toThrow()
  })

  it('rejects an HTTP 200 terminal with the wrong kind', () => {
    expect(() => expectAnswerKind({
      kind: 'CAPABILITY_UNAVAILABLE',
      code: 'SELECTED_MODEL_INVALID_RESPONSE',
    })).toThrow(/期望 ANSWER/)
  })

  it('rejects a non-COMPLETE resolution on a happy path', () => {
    const body = clone(validAnswer())
    body.answer!.resolution = 'PARTIAL'
    expect(() => expectAnswer(body)).toThrow(/resolution 必须为 COMPLETE/)
  })

  it('accepts PARTIAL only when the caller marks the discussion route as partial-capable', () => {
    const body = clone(validAnswer())
    body.answer!.resolution = 'PARTIAL'
    body.answer!.goalResults![0]!.coverage = 'PARTIAL'
    expect(() => expectNonEmptyAnswer(body)).toThrow(/不接受 resolution PARTIAL/)
    expect(() => expectNonEmptyAnswer(body, { allowPartial: true })).not.toThrow()
  })

  it('rejects empty goalResults', () => {
    const body = clone(validAnswer())
    body.answer!.goalResults = []
    expect(() => expectNonEmptyAnswer(body)).toThrow(/goalResults 不得为空/)
  })

  it('rejects NONE coverage', () => {
    const body = clone(validAnswer())
    body.answer!.goalResults![0]!.coverage = 'NONE'
    expect(() => expectNonEmptyAnswer(body)).toThrow(/coverage 不得为 NONE/)
  })

  it('rejects SECTIONED presentations without sections', () => {
    const body = clone(validAnswer())
    body.answer!.goalResults![0]!.presentation!.sections = []
    expect(() => expectNonEmptyAnswer(body)).toThrow(/必须包含 section/)
  })

  it('rejects blank SECTIONED content', () => {
    const body = clone(validAnswer())
    body.answer!.goalResults![0]!.presentation!.sections![0]!.content = '   '
    expect(() => expectNonEmptyAnswer(body)).toThrow(/content 不得为空白/)
  })

  it('rejects a missing public source catalog', () => {
    const body = clone(validAnswer())
    body.answer!.sourceCatalog = { sources: [] }
    expect(() => expectNonEmptyAnswer(body)).toThrow(/sourceCatalog\.sources/)
  })

  it('rejects support keys absent from the public source catalog', () => {
    const body = clone(validAnswer())
    body.answer!.sourceCatalog = { sources: [{ key: 'other-source' }] }
    expect(() => expectNonEmptyAnswer(body)).toThrow(/未声明来源 source-1/)
  })
})
