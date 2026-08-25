// 会话标题防噪声（领域模型层）：纯数字、纯标点、纯表情、空白与过短输入
// 不能直接固化为永久会话标题；噪声输入返回 null，由调用方落位「待补充问题」占位。
// （交接规格 2026-08-17 §8）

const MIN_TITLE_LENGTH = 2
const SHORT_TITLE_MAX_LENGTH = 16

/** 是否包含自然语言字母或汉字（数字、标点、表情不算）。 */
const HAS_LETTER = /\p{L}/u

/** 判定输入是否为噪声问题：去除首尾空白后长度不足，或不含任何字母/汉字。 */
export function isNoiseQuestion(content: string): boolean {
  const normalized = content.trim()
  if (normalized.length < MIN_TITLE_LENGTH) return true
  return !HAS_LETTER.test(normalized)
}

/** 生成可扫描短标题；噪声输入返回 null。 */
export function shortSessionTitle(content: string): string | null {
  const normalized = content.trim().replace(/\s+/g, ' ')
  if (isNoiseQuestion(normalized)) return null
  if (normalized.length > SHORT_TITLE_MAX_LENGTH) {
    return `${normalized.slice(0, SHORT_TITLE_MAX_LENGTH)}…`
  }
  return normalized
}
