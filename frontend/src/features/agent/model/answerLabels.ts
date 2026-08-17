import type { AnswerSupportKind, FulfillmentRole, MappedAnswer, PublicDegradationKind, SemanticSourceDomain, SourceComposition } from './answerTypes'

type AnswerLabelInput = Pick<
  MappedAnswer,
  | 'intent'
  | 'answerScope'
  | 'resolution'
  | 'intentSource'
  | 'constructionMode'
  | 'evidenceState'
  | 'degraded'
>

export function answerStatusLabel(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.intent === 'TIME_SENSITIVE') return '暂时不可用'
  if (answer.intent === 'UNSUPPORTED_OR_UNSAFE' || answer.resolution === 'REJECTED') {
    return '无法处理该请求'
  }
  if (answer.resolution === 'NEEDS_CLARIFICATION' || answer.resolution === 'BOUNDARY') {
    return '需要补充信息'
  }
  if (answer.resolution === 'NOT_SUPPORTED') return '当前公开证据不足'
  if (answer.resolution === 'CAPABILITY_UNAVAILABLE') return '服务暂不可用'
  if (answer.resolution === 'INVALID_INPUT') return '请求的作品范围无效'
  if (answer.intent) return '回答'
  if (answer.evidenceState === 'VERIFIED') return '已验证回答'
  if (answer.evidenceState === 'INSUFFICIENT') return '当前公开证据不足'
  return '回答'
}

export function answerScopeTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.answerScope === 'GENERAL') return '通用知识'
  if (answer.answerScope === 'PORTFOLIO') return '作品集资料'
  if (answer.answerScope === 'MIXED' || answer.answerScope === 'HYBRID') return '混合回答'
  if (answer.answerScope === 'GLOBAL' || answer.answerScope === 'CONVERSATION') return '通用对话'
  return ''
}

export function blockScopeTag(scope: 'GENERAL' | 'PORTFOLIO'): string {
  return scope === 'GENERAL' ? '通用知识' : '作品集资料'
}

export function answerSourceTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.intentSource === 'PRESET') return '预设问题'
  if (answer.intentSource === 'REFERENCE') return '引用追问'
  if (answer.intentSource === 'RULE') return '规则识别'
  if (answer.intentSource === 'MODEL') return '模型识别'
  return ''
}

export function answerVerificationTag(
  answer: AnswerLabelInput | null | undefined,
): string | null {
  if (!answer) return ''
  if (answer.evidenceState === 'VERIFIED') return '已验证证据'
  // Insufficient evidence is expressed by the status label only; the
  // verification tag must never suggest any verification state.
  if (answer.evidenceState === 'INSUFFICIENT') return null
  return ''
}

export function answerGenerationTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.resolution === 'REJECTED') return '拒答'
  if (answer.degraded) return '降级回答'
  if (answer.constructionMode === 'EVIDENCE_COMPOSITION') return '已根据公开证据整理回答'
  if (answer.constructionMode === 'MODEL_GROUNDED') return '基于证据表达'
  if (answer.constructionMode === 'GENERAL_MODEL') return '模型回答'
  if (answer.constructionMode === 'TEMPLATE') return '按预设回答整理'
  return ''
}

// 交接规格 §5：技术尾注不再输出 ANSWERED · EVIDENCE_COMPOSITION 等协议枚举，
// 翻译为一条人类可读状态。
export function answerTechTail(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  switch (answer.constructionMode) {
    case 'EVIDENCE_COMPOSITION':
      return '已根据公开证据整理回答'
    case 'MODEL_GROUNDED':
      return '基于证据表达'
    case 'GENERAL_MODEL':
      return '模型通用回答'
    case 'MIXED_COMPOSITION':
      return '综合多来源整理'
    case 'TEMPLATE':
      return '按预设回答整理'
    default:
      return ''
  }
}

export function degradedNotice(answer: AnswerLabelInput | null | undefined): string {
  if (!answer?.degraded) return ''
  return '已切换到基础回答'
}

// P5 stp-v2 来源域文案（设计 §4.5）。未知值返回 null，调用方按 fail-closed 不渲染域标记。
export function sourceDomainLabel(domain: SemanticSourceDomain | undefined | null): string | null {
  if (domain === 'GENERAL') return '通用知识'
  if (domain === 'PORTFOLIO') return '作品集资料'
  if (domain === 'SYNTHESIS') return '跨域综合'
  return null
}

// P5 stp-v2 Block 支持类型文案（设计 §4.5）。未知值返回 null。
export function supportKindLabel(kind: AnswerSupportKind | undefined | null): string | null {
  if (kind === 'VERIFIED_PUBLIC_EVIDENCE') return '✓已验证证据'
  if (kind === 'GENERAL_KNOWLEDGE') return '通用知识'
  if (kind === 'DERIVED_FROM_TASKS') return '由通用+作品集推导'
  return null
}

// P5 stp-v2 降级类型文案（设计 §4.5）。未知值返回 null。
export function degradationKindLabel(kind: PublicDegradationKind | undefined | null): string | null {
  if (kind === 'RETRIEVAL_FALLBACK') return '检索回退'
  if (kind === 'EXPRESSION_FALLBACK') return '表达回退'
  if (kind === 'CROSS_DOMAIN_EXPRESSION_FALLBACK') return '跨域表达回退'
  if (kind === 'CONTENT_BACKEND_FALLBACK') return '内容后端回退'
  return null
}

// P5 来源组成文案（设计 §4.5/§9.5）。未知值返回 null。
export function sourceCompositionLabel(composition: SourceComposition | undefined | null): string | null {
  if (composition === 'GENERAL_ONLY') return '仅通用知识'
  if (composition === 'PORTFOLIO_ONLY') return '仅作品集资料'
  if (composition === 'MULTI_SOURCE') return '多来源'
  if (composition === 'CROSS_DOMAIN_DERIVED') return '跨域派生'
  return null
}

// P5 履约角色文案（仅信任层展示，设计 §4.5/§10.4）。未知值返回 null。
export function fulfillmentRoleLabel(role: FulfillmentRole | undefined | null): string | null {
  if (role === 'PRIMARY') return '主'
  if (role === 'SUPPORTING') return '辅'
  if (role === 'OPTIONAL') return '可选'
  return null
}
