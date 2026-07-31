import { createClientRequestId, getClientSessionId } from './clientCorrelation'

export type FrontendDiagnosticEventName =
  | 'frontend.content.load.failed'
  | 'frontend.agent.request.failed'
  | 'frontend.agent.request.slow'
  | 'frontend.agent.request.cancelled'
  | 'frontend.response.invalid'
  | 'frontend.runtime.failed'

export type FrontendDurationBucket = 'LT_1000_MS' | 'FROM_1000_TO_4999_MS' | 'GE_5000_MS'

export type FrontendGuidanceStage = 'OPENING' | 'DEEPENING' | 'WRAP_UP' | 'EXPLORE_OTHERS'

export interface ReportableFrontendEvent {
  schemaVersion: 1
  eventName: FrontendDiagnosticEventName
  occurredAt: string
  clientSessionId: string
  clientRequestId: string
  serverRequestId?: string
  turnId?: string
  errorCode?: string
  errorKind?: string
  errorFingerprint?: string
  durationBucket?: FrontendDurationBucket
  recoveredCount?: number
  guidanceStage?: FrontendGuidanceStage
}

export type SafeFrontendEvent = ReportableFrontendEvent

const FRONTEND_EVENT_NAMES = new Set<FrontendDiagnosticEventName>([
  'frontend.content.load.failed',
  'frontend.agent.request.failed',
  'frontend.agent.request.slow',
  'frontend.agent.request.cancelled',
  'frontend.response.invalid',
  'frontend.runtime.failed',
])
const DURATION_BUCKETS = new Set<FrontendDurationBucket>([
  'LT_1000_MS',
  'FROM_1000_TO_4999_MS',
  'GE_5000_MS',
])
const GUIDANCE_STAGES = new Set<FrontendGuidanceStage>([
  'OPENING',
  'DEEPENING',
  'WRAP_UP',
  'EXPLORE_OTHERS',
])
const ERROR_KINDS = new Set([
  'HTTP',
  'TIMEOUT',
  'NETWORK',
  'INVALID_RESPONSE',
  'CANCELLED',
  'ERROR_EVENT',
  'UNHANDLED_REJECTION',
])
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,63}$/
const FINGERPRINT_PATTERN = /^[0-9a-f]{64}$/

export interface FrontendDiagnosticEventInput {
  eventName: FrontendDiagnosticEventName
  clientRequestId?: string
  serverRequestId?: string
  turnId?: string
  errorCode?: string
  errorKind?: string
  errorFingerprint?: string
  durationBucket?: FrontendDurationBucket
  recoveredCount?: number
  guidanceStage?: FrontendGuidanceStage
}

export function createFrontendDiagnosticEvent(input: FrontendDiagnosticEventInput): ReportableFrontendEvent {
  return {
    schemaVersion: 1,
    eventName: input.eventName,
    occurredAt: new Date().toISOString(),
    clientSessionId: getClientSessionId(),
    clientRequestId: input.clientRequestId ?? createClientRequestId(),
    ...(input.serverRequestId === undefined ? {} : { serverRequestId: input.serverRequestId }),
    ...(input.turnId === undefined ? {} : { turnId: input.turnId }),
    ...(input.errorCode === undefined ? {} : { errorCode: input.errorCode }),
    ...(input.errorKind === undefined ? {} : { errorKind: input.errorKind }),
    ...(input.errorFingerprint === undefined ? {} : { errorFingerprint: input.errorFingerprint }),
    ...(input.durationBucket === undefined ? {} : { durationBucket: input.durationBucket }),
    ...(input.recoveredCount === undefined ? {} : { recoveredCount: input.recoveredCount }),
    ...(input.guidanceStage === undefined ? {} : { guidanceStage: input.guidanceStage }),
  }
}

export function serializeFrontendEvent(event: unknown): ReportableFrontendEvent | undefined {
  try {
    if (!isPlainObject(event)) return undefined
    const schemaVersion = event.schemaVersion
    const eventName = event.eventName
    const occurredAt = event.occurredAt
    const clientSessionId = event.clientSessionId
    const clientRequestId = event.clientRequestId
    if (schemaVersion !== 1) return undefined
    if (typeof eventName !== 'string' || !FRONTEND_EVENT_NAMES.has(eventName as FrontendDiagnosticEventName)) return undefined
    if (typeof occurredAt !== 'string' || !isCanonicalInstant(occurredAt)) return undefined
    if (typeof clientSessionId !== 'string' || !UUID_PATTERN.test(clientSessionId)) return undefined
    if (typeof clientRequestId !== 'string' || !UUID_PATTERN.test(clientRequestId)) return undefined

    const sanitized: ReportableFrontendEvent = {
      schemaVersion: 1,
      eventName: eventName as FrontendDiagnosticEventName,
      occurredAt,
      clientSessionId,
      clientRequestId,
    }
    if (!copyOptionalString(event, sanitized, 'serverRequestId', (value) => UUID_PATTERN.test(value))) return undefined
    if (!copyOptionalString(event, sanitized, 'turnId', (value) => UUID_PATTERN.test(value))) return undefined
    if (!copyOptionalString(event, sanitized, 'errorCode', (value) => ERROR_CODE_PATTERN.test(value))) return undefined
    if (!copyOptionalString(event, sanitized, 'errorKind', (value) => ERROR_KINDS.has(value))) return undefined
    if (!copyOptionalString(event, sanitized, 'errorFingerprint', (value) => FINGERPRINT_PATTERN.test(value))) return undefined
    if (!copyOptionalString(event, sanitized, 'durationBucket', (value) => DURATION_BUCKETS.has(value as FrontendDurationBucket))) return undefined
    if (!copyOptionalNumber(event, sanitized, 'recoveredCount', (value) => Number.isInteger(value) && value >= 0)) return undefined
    if (!copyOptionalString(event, sanitized, 'guidanceStage', (value) => GUIDANCE_STAGES.has(value as FrontendGuidanceStage))) return undefined
    return sanitized
  } catch {
    return undefined
  }
}

export function durationBucketFor(elapsedMilliseconds: number): FrontendDurationBucket {
  if (elapsedMilliseconds < 1_000) return 'LT_1000_MS'
  if (elapsedMilliseconds < 5_000) return 'FROM_1000_TO_4999_MS'
  return 'GE_5000_MS'
}

export async function createFirstPartyStackFingerprint(stack: unknown): Promise<string | undefined> {
  try {
    if (typeof stack !== 'string' || stack.length === 0) return undefined
    const subtle = globalThis.crypto?.subtle
    const origin = globalThis.location?.origin
    if (!subtle || !origin) return undefined
    const frame = stack.split('\n').map((line) => line.trim()).find((line) => line.startsWith('at '))
    if (!frame) return undefined
    const match = /^at\s+(?:(?<functionName>[A-Za-z0-9_$.<>-]+)\s+\()?(?<url>https?:\/\/[^\s)]+|\/[^\s)]+):(?<line>\d+):(?<column>\d+)\)?$/.exec(frame)
    const functionName = match?.groups?.functionName
    const rawUrl = match?.groups?.url
    const line = match?.groups?.line
    const column = match?.groups?.column
    if (!rawUrl || !line || !column) return undefined
    const location = new URL(rawUrl, origin)
    if (location.origin !== origin || !/^\/assets\/[A-Za-z0-9._-]+\.js$/.test(location.pathname)) return undefined
    const normalized = `${functionName ?? 'anonymous'}@${location.pathname}:${line}:${column}`
    const digest = await subtle.digest('SHA-256', new TextEncoder().encode(normalized))
    return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  } catch {
    return undefined
  }
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null) return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

function isCanonicalInstant(value: string): boolean {
  const instant = new Date(value)
  return Number.isFinite(instant.getTime()) && instant.toISOString() === value
}

function copyOptionalString(
  source: Record<string, unknown>,
  target: ReportableFrontendEvent,
  key: 'serverRequestId' | 'turnId' | 'errorCode' | 'errorKind' | 'errorFingerprint' | 'durationBucket' | 'guidanceStage',
  accepts: (value: string) => boolean,
): boolean {
  if (!Object.prototype.hasOwnProperty.call(source, key)) return true
  const value = source[key]
  if (typeof value !== 'string' || !accepts(value)) return false
  Object.assign(target, { [key]: value })
  return true
}

function copyOptionalNumber(
  source: Record<string, unknown>,
  target: ReportableFrontendEvent,
  key: 'recoveredCount',
  accepts: (value: number) => boolean,
): boolean {
  if (!Object.prototype.hasOwnProperty.call(source, key)) return true
  const value = source[key]
  if (typeof value !== 'number' || !accepts(value)) return false
  Object.assign(target, { [key]: value })
  return true
}
