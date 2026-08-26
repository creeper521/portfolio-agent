// 客户端诊断关联 ID：为每次浏览器会话生成唯一 clientSessionId，
// 为每次请求生成唯一 requestId，用于日志关联与问题追踪。
const clientSessionId = globalThis.crypto.randomUUID()

export function getClientSessionId(): string {
  return clientSessionId
}

export function createClientRequestId(): string {
  return globalThis.crypto.randomUUID()
}
