import type { MappedAnswer } from './answerTypes'

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

export function answerVerificationTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.evidenceState === 'VERIFIED') return '已验证证据'
  if (answer.evidenceState === 'INSUFFICIENT') return '证据不足'
  return ''
}

export function answerGenerationTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.resolution === 'REJECTED') return '拒答'
  if (answer.degraded) return '降级回答'
  if (answer.constructionMode === 'EVIDENCE_COMPOSITION') return '确定性组装'
  if (answer.constructionMode === 'MODEL_GROUNDED') return '基于证据表达'
  if (answer.constructionMode === 'GENERAL_MODEL') return '模型回答'
  if (answer.constructionMode === 'TEMPLATE') return '确定性模板'
  return ''
}

export function answerTechTail(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  return [answer.resolution, answer.constructionMode].filter(Boolean).join(' · ')
}

export function degradedNotice(answer: AnswerLabelInput | null | undefined): string {
  if (!answer?.degraded) return ''
  return '已切换到基础回答'
}
