const clientSessionId = globalThis.crypto.randomUUID()

export function getClientSessionId(): string {
  return clientSessionId
}

export function createClientRequestId(): string {
  return globalThis.crypto.randomUUID()
}
