import type { MappedAnswer } from './answerTypes'

// 回答元信息的人话标签：Agent 对话与首页轻回答共用同一套分层。
// 人话状态做视觉重点，技术枚举由 answerTechTail 降级为尾注。
// 所有函数接受空值并返回空串，由调用方决定缺省时是否渲染。

type AnswerLabelInput = Pick<
  MappedAnswer,
  | 'intent'
  | 'answerScope'
  | 'resolution'
  | 'answerSource'
  | 'generationMode'
  | 'verification'
  | 'degraded'
>

export function answerStatusLabel(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.intent === 'TIME_SENSITIVE') return '暂时不可用'
  if (answer.intent === 'UNSUPPORTED_OR_UNSAFE') return '无法处理该请求'
  if (answer.resolution === 'BOUNDARY') return '当前能力边界'
  if (answer.resolution === 'REJECTED') return '无法处理该请求'
  if (answer.intent) return '回答'
  if (answer.verification === 'VERIFIED') return '已核验回答'
  if (answer.verification === 'PARTIALLY_VERIFIED') return '部分事实已核验'
  return '尚未核验'
}

export function answerScopeTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.answerScope === 'GENERAL') return '通用知识'
  if (answer.answerScope === 'PORTFOLIO') return '作品集资料'
  if (answer.answerScope === 'HYBRID') return '混合回答'
  return ''
}

export function blockScopeTag(scope: 'GENERAL' | 'PORTFOLIO'): string {
  return scope === 'GENERAL' ? '通用知识' : '作品集资料'
}

export function answerSourceTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.answerSource === 'RETRIEVAL') return '资料检索'
  if (answer.answerSource === 'PRESET') return '预设问题'
  return ''
}

export function answerVerificationTag(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (answer.verification === 'VERIFIED') return '已核验'
  if (answer.verification === 'PARTIALLY_VERIFIED') return '部分核验'
  if (answer.verification === 'UNVERIFIED') return '未核验'
  return ''
}

// 技术枚举尾注：resolution + generationMode，价值低，降级展示
export function answerTechTail(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  return [answer.resolution, answer.generationMode].filter(Boolean).join(' · ')
}

export function degradedNotice(answer: AnswerLabelInput | null | undefined): string {
  if (!answer) return ''
  if (!answer.degraded && answer.generationMode !== 'FALLBACK') return ''
  return '已切换到基础回答'
}
