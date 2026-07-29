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
  answerSource: null,
  generationMode: 'DETERMINISTIC' as const,
  verification: 'VERIFIED' as const,
  degraded: false,
}

describe('answerLabels', () => {
  it('labels intent and resolution states in human terms', () => {
    expect(answerStatusLabel({ ...base, intent: 'TIME_SENSITIVE' })).toBe('暂时不可用')
    expect(answerStatusLabel({ ...base, intent: 'UNSUPPORTED_OR_UNSAFE' }))
      .toBe('无法处理该请求')
    expect(answerStatusLabel({ ...base, resolution: 'BOUNDARY' })).toBe('当前能力边界')
    expect(answerStatusLabel({ ...base, resolution: 'REJECTED' })).toBe('无法处理该请求')
    expect(answerStatusLabel({ ...base, intent: 'PORTFOLIO_GROUNDED' })).toBe('回答')
  })

  it('falls back to verification wording for v1 answers without intent', () => {
    expect(answerStatusLabel(base)).toBe('已核验回答')
    expect(answerStatusLabel({ ...base, verification: 'PARTIALLY_VERIFIED' }))
      .toBe('部分事实已核验')
    expect(answerStatusLabel({ ...base, verification: 'UNVERIFIED' })).toBe('尚未核验')
    expect(answerStatusLabel(null)).toBe('')
    expect(answerStatusLabel(undefined)).toBe('')
  })

  it('tags answer and block scopes in human terms', () => {
    expect(answerScopeTag({ ...base, answerScope: 'GENERAL' })).toBe('通用知识')
    expect(answerScopeTag({ ...base, answerScope: 'PORTFOLIO' })).toBe('作品集资料')
    expect(answerScopeTag({ ...base, answerScope: 'HYBRID' })).toBe('混合回答')
    expect(answerScopeTag(base)).toBe('')
    expect(blockScopeTag('GENERAL')).toBe('通用知识')
    expect(blockScopeTag('PORTFOLIO')).toBe('作品集资料')
  })

  it('tags sources and verification in human terms', () => {
    expect(answerSourceTag({ ...base, answerSource: 'RETRIEVAL' })).toBe('资料检索')
    expect(answerSourceTag({ ...base, answerSource: 'PRESET' })).toBe('预设问题')
    expect(answerSourceTag(base)).toBe('')
    expect(answerVerificationTag(base)).toBe('已核验')
    expect(answerVerificationTag({ ...base, verification: 'PARTIALLY_VERIFIED' }))
      .toBe('部分核验')
    expect(answerVerificationTag({ ...base, verification: 'UNVERIFIED' })).toBe('未核验')
    expect(answerVerificationTag({ ...base, verification: 'NOT_APPLICABLE' })).toBe('')
  })

  it('keeps the technical tail and degraded notice honest', () => {
    expect(answerTechTail(base)).toBe('ANSWERED · DETERMINISTIC')
    expect(answerTechTail({ ...base, generationMode: undefined })).toBe('ANSWERED')
    expect(answerTechTail(null)).toBe('')
    expect(degradedNotice({ ...base, generationMode: 'FALLBACK' })).toBe('已切换到基础回答')
    expect(degradedNotice({ ...base, degraded: true })).toBe('已切换到基础回答')
    expect(degradedNotice(base)).toBe('')
    expect(degradedNotice(null)).toBe('')
  })

  it('distinguishes deterministic, model, retrieval, fallback, and refusal states', () => {
    expect(answerGenerationTag(base)).toBe('确定性回答')
    expect(answerGenerationTag({ ...base, generationMode: 'MODEL' })).toBe('模型生成')
    expect(answerGenerationTag({ ...base, answerSource: 'RETRIEVAL' })).toBe('检索增强')
    expect(answerGenerationTag({ ...base, generationMode: 'FALLBACK' })).toBe('降级回答')
    expect(answerGenerationTag({ ...base, resolution: 'REJECTED' })).toBe('拒答')
  })
})
