import { describe, expect, it } from 'vitest'

import {
  answerScopeTag,
  answerGenerationTag,
  answerSourceTag,
  answerStatusLabel,
  answerTechTail,
  answerVerificationTag,
  blockScopeTag,
  degradationKindLabel,
  degradedNotice,
  fulfillmentRoleLabel,
  sourceCompositionLabel,
  sourceDomainLabel,
  supportKindLabel,
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
      .toBeNull()
    expect(answerVerificationTag({ ...base, evidenceState: 'NOT_REQUIRED' })).toBe('')
  })

  it('keeps the four authoritative answer semantics stable', () => {
    expect(answerSourceTag({ ...base, intentSource: 'RULE' })).toBe('规则识别')
    expect(answerGenerationTag({ ...base, constructionMode: 'MODEL_GROUNDED' }))
      .toBe('基于证据表达')
    expect(answerVerificationTag({ ...base, evidenceState: 'INSUFFICIENT' }))
      .toBeNull()
    const fallbackAnswer = {
      ...base,
      constructionMode: 'EVIDENCE_COMPOSITION' as const,
      generationMode: 'FALLBACK' as const,
    }
    expect(answerGenerationTag(fallbackAnswer)).not.toBe('模型回答')
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

  it('does not badge MIXED_COMPOSITION with any model/AI wording (P4)', () => {
    // 混合表达不展示徽标：fall-through 到空标签，且技术尾注不出现「模型/AI」字样。
    expect(answerGenerationTag({ ...base, constructionMode: 'MIXED_COMPOSITION' })).toBe('')
    const tail = answerTechTail({ ...base, constructionMode: 'MIXED_COMPOSITION' })
    expect(tail).not.toContain('模型')
    expect(tail).not.toContain('AI')
  })

  it('labels P5 source domains and support kinds from the closed whitelist (P5)', () => {
    // 来源域文案（设计 §4.5）
    expect(sourceDomainLabel('GENERAL')).toBe('通用知识')
    expect(sourceDomainLabel('PORTFOLIO')).toBe('作品集资料')
    expect(sourceDomainLabel('SYNTHESIS')).toBe('跨域综合')
    // 未知/缺失 fail-closed：返回 null，调用方不渲染域标记
    expect(sourceDomainLabel(undefined)).toBeNull()
    expect(sourceDomainLabel(null)).toBeNull()

    // 支持类型文案（设计 §4.5）
    expect(supportKindLabel('VERIFIED_PUBLIC_EVIDENCE')).toBe('✓已验证证据')
    expect(supportKindLabel('GENERAL_KNOWLEDGE')).toBe('通用知识')
    expect(supportKindLabel('DERIVED_FROM_TASKS')).toBe('由通用+作品集推导')
    expect(supportKindLabel(undefined)).toBeNull()
  })

  it('labels P5 degradation kinds from the closed whitelist (P5)', () => {
    expect(degradationKindLabel('RETRIEVAL_FALLBACK')).toBe('检索回退')
    expect(degradationKindLabel('EXPRESSION_FALLBACK')).toBe('表达回退')
    expect(degradationKindLabel('CROSS_DOMAIN_EXPRESSION_FALLBACK')).toBe('跨域表达回退')
    expect(degradationKindLabel('CONTENT_BACKEND_FALLBACK')).toBe('内容后端回退')
    expect(degradationKindLabel(undefined)).toBeNull()
  })

  it('labels P5 source composition and fulfillment roles from the closed whitelist (P5)', () => {
    expect(sourceCompositionLabel('GENERAL_ONLY')).toBe('仅通用知识')
    expect(sourceCompositionLabel('PORTFOLIO_ONLY')).toBe('仅作品集资料')
    expect(sourceCompositionLabel('MULTI_SOURCE')).toBe('多来源')
    expect(sourceCompositionLabel('CROSS_DOMAIN_DERIVED')).toBe('跨域派生')
    expect(sourceCompositionLabel(undefined)).toBeNull()

    expect(fulfillmentRoleLabel('PRIMARY')).toBe('主')
    expect(fulfillmentRoleLabel('SUPPORTING')).toBe('辅')
    expect(fulfillmentRoleLabel('OPTIONAL')).toBe('可选')
    expect(fulfillmentRoleLabel(undefined)).toBeNull()
  })
})
