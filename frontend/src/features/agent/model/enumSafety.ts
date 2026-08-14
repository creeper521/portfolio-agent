// P5 stp-v2 契约消费：未知公共枚举 fail-closed 工具（设计 §2.7、§16.11）。
//
// 后端公共枚举是闭集；迁移期或异常情况下可能出现未知值。前端绝不向访客暴露原始
// 未知码：已知值原样返回，未知值（或非字符串）回落到调用方提供的安全默认。
// 本工具保持纯函数，便于测试；脱敏诊断上报由调用方按其上下文决定（不在本工具内做）。

/** 值在已知闭集内则返回该值，否则返回 fallback（fail-closed）。 */
export function safeEnum<T extends string>(
  value: unknown,
  known: ReadonlySet<T>,
  fallback: T,
): T {
  return typeof value === 'string' && known.has(value as T) ? (value as T) : fallback
}

/** 值在已知闭集内则返回该值，否则返回 undefined（用于可选字段，不强制默认）。 */
export function knownEnum<T extends string>(
  value: unknown,
  known: ReadonlySet<T>,
): T | undefined {
  return typeof value === 'string' && known.has(value as T) ? (value as T) : undefined
}
