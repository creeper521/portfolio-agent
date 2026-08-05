import { describe, expect, it } from 'vitest'

import {
  answerScopeTag,
  answerGenerationTag,
  answerSourceTag,
  answerStatusLabel,
  answerTechTail,
  answerVerificationTag,
  blockScopeTag,
  degradedNotice,
} from './answerLabels'

const base = {
  intent: undefined,
  answerScope: undefined,
  resolution: 'ANSWERED' as const,
  intentSource: 'PRESET' as const,
  constructionMode: 'EVIDENCE_COMPOSITION' as const,
  evidenceState: 'VERIFIED' as const,
  degraded: false,
}

describe('answerLabels', () => {
  it('labels intent and resolution states in human terms', () => {
    expect(answerStatusLabel({ ...base, intent: 'TIME_SENSITIVE' })).toBe('暂时不可用')
    expect(answerStatusLabel({ ...base, intent: 'UNSUPPORTED_OR_UNSAFE' }))
      .toBe('无法处理该请求')
    expect(answerStatusLabel({ ...base, resolution: 'NEEDS_CLARIFICATION' }))
      .toBe('需要补充信息')
    expect(answerStatusLabel({ ...base, resolution: 'NOT_SUPPORTED' }))
      .toBe('当前公开证据不足')
    expect(answerStatusLabel({ ...base, resolution: 'CAPABILITY_UNAVAILABLE' }))
      .toBe('服务暂不可用')
    expect(answerStatusLabel({ ...base, resolution: 'INVALID_INPUT' }))
      .toBe('请求的作品范围无效')
    expect(answerStatusLabel({ ...base, resolution: 'REJECTED' })).toBe('无法处理该请求')
    expect(answerStatusLabel({ ...base, intent: 'PORTFOLIO_GROUNDED' })).toBe('回答')
  })

  it('falls back to evidence wording without an intent', () => {
    expect(answerStatusLabel(base)).toBe('已验证回答')
    expect(answerStatusLabel({ ...base, evidenceState: 'INSUFFICIENT' }))
      .toBe('当前公开证据不足')
    expect(answerStatusLabel(null)).toBe('')
    expect(answerStatusLabel(undefined)).toBe('')
  })

  it('tags answer and block scopes in human terms', () => {
    expect(answerScopeTag({ ...base, answerScope: 'GENERAL' })).toBe('通用知识')
    expect(answerScopeTag({ ...base, answerScope: 'PORTFOLIO' })).toBe('作品集资料')
    expect(answerScopeTag({ ...base, answerScope: 'MIXED' })).toBe('混合回答')
    expect(answerScopeTag({ ...base, answerScope: 'GLOBAL' })).toBe('通用对话')
    expect(answerScopeTag(base)).toBe('')
    expect(blockScopeTag('GENERAL')).toBe('通用知识')
    expect(blockScopeTag('PORTFOLIO')).toBe('作品集资料')
  })

  it('tags intent sources and evidence states in human terms', () => {
    expect(answerSourceTag({ ...base, intentSource: 'PRESET' })).toBe('预设问题')
    expect(answerSourceTag({ ...base, intentSource: 'REFERENCE' })).toBe('引用追问')
    expect(answerSourceTag({ ...base, intentSource: 'RULE' })).toBe('规则识别')
    expect(answerVerificationTag(base)).toBe('已验证证据')
    expect(answerVerificationTag({ ...base, evidenceState: 'INSUFFICIENT' }))
      .toBe('证据不足')
    expect(answerVerificationTag({ ...base, evidenceState: 'NOT_REQUIRED' })).toBe('')
  })

  it('keeps the technical tail and degraded notice honest', () => {
    expect(answerTechTail(base)).toBe('ANSWERED · EVIDENCE_COMPOSITION')
    expect(answerTechTail(null)).toBe('')
    expect(degradedNotice({ ...base, degraded: true })).toBe('已切换到基础回答')
    expect(degradedNotice(base)).toBe('')
    expect(degradedNotice(null)).toBe('')
  })

  it('distinguishes construction modes and refusal states', () => {
    expect(answerGenerationTag(base)).toBe('确定性组装')
    expect(answerGenerationTag({ ...base, constructionMode: 'MODEL_GROUNDED' }))
      .toBe('基于证据表达')
    expect(answerGenerationTag({ ...base, constructionMode: 'GENERAL_MODEL' })).toBe('模型回答')
    expect(answerGenerationTag({ ...base, constructionMode: 'TEMPLATE' })).toBe('确定性模板')
    expect(answerGenerationTag({ ...base, degraded: true })).toBe('降级回答')
    expect(answerGenerationTag({ ...base, resolution: 'REJECTED' })).toBe('拒答')
  })
})
