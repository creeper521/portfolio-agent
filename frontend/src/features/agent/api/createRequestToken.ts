export function createRequestToken(): string {
  return crypto.randomUUID()
}
