// 生成 Turn 幂等键 requestId（API 层辅助）：后端合同只接受 UUID，
// 任何回退方案都不得产生时间戳等非 UUID 标识。
// 优先使用 crypto.randomUUID；不可用时用 getRandomValues 构造 RFC 4122 v4；
// 两者皆缺则直接抛错，宁可不发请求也不发送必然违反合同的标识。（A2-74）

/**
 * 生成符合后端合同的 UUID requestId（幂等重放键）。
 * 当前环境缺少加密随机源时抛错，绝不降级为弱随机或时间戳标识。
 */
export function newRequestId(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi?.randomUUID !== undefined) {
    return cryptoApi.randomUUID()
  }
  const getRandomValues = cryptoApi?.getRandomValues?.bind(cryptoApi)
  if (getRandomValues === undefined) {
    throw new Error('当前环境缺少加密随机源，无法生成请求标识')
  }
  const bytes = getRandomValues(new Uint8Array(16))
  // 按 RFC 4122 v4 固定 version（第 7 字节高 4 位）与 variant（第 9 字节高 2 位）。
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
