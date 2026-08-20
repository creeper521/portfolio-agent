import { createClientRequestId, getClientSessionId } from '../../../shared/diagnostics/clientCorrelation'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import { createFrontendDiagnosticEvent, durationBucketFor } from '../../../shared/diagnostics/frontendDiagnosticTypes'
import type { PortfolioHome, ProjectDetail } from '../model/portfolioTypes'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import {
  actionForApiError,
  normalizeApiErrorCode,
  type ApiErrorCode,
  type ErrorAction,
} from './apiErrorActions'

interface ApiErrorBody {
  code?: string
  retryAfterSeconds?: number
}

interface PortfolioApiErrorDetails {
  kind: ApiFailureKind
  code?: ApiErrorCode
  status?: number
  requestId?: string
  retryAfterSeconds?: number
  action: ErrorAction
  clientRequestId: string
}

const REQUEST_TIMEOUT_MS = 10_000

export type ApiFailureKind = 'HTTP' | 'TIMEOUT' | 'NETWORK' | 'INVALID_RESPONSE' | 'CANCELLED'

export enum RequestOperation {
  PUBLIC_CONTENT = 'PUBLIC_CONTENT',
  PROJECT = 'PROJECT',
  ANSWER = 'ANSWER',
}

export interface RequestOptions {
  operation: RequestOperation
  signal?: AbortSignal
  timeoutMs?: number
  // P3：用于幂等 DELETE 等「成功无正文」的响应（204 No Content）。
  // 为真时，2xx 响应不解析 JSON，直接 resolve，避免空体被误判为 INVALID_RESPONSE。
  expectNoContent?: boolean
}

export class PortfolioApiError extends Error {
  readonly kind: ApiFailureKind
  readonly code?: ApiErrorCode
  readonly status?: number
  readonly requestId?: string
  readonly retryAfterSeconds?: number
  readonly action: ErrorAction
  readonly clientRequestId: string

  constructor(message: string, details: PortfolioApiErrorDetails)
  constructor(message: string, status?: number, code?: string, retryAfterSeconds?: number)
  constructor(
    message: string,
    detailsOrStatus?: PortfolioApiErrorDetails | number,
    code?: string,
    retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'PortfolioApiError'
    const details = typeof detailsOrStatus === 'number'
      ? {
          kind: 'HTTP' as const,
          status: detailsOrStatus,
          code: normalizeApiErrorCode(code),
          retryAfterSeconds,
          action: actionForApiError(code),
          clientRequestId: createClientRequestId(),
        }
      : detailsOrStatus ?? {
          kind: 'HTTP' as const,
          code: normalizeApiErrorCode(code),
          retryAfterSeconds,
          action: actionForApiError(code),
          clientRequestId: createClientRequestId(),
        }
    const normalizedCode = normalizeApiErrorCode(details.code)
    this.kind = details.kind
    this.code = normalizedCode
    this.status = details.status
    this.requestId = details.requestId
    this.retryAfterSeconds = details.retryAfterSeconds
    this.action = actionForApiError(normalizedCode)
    this.clientRequestId = details.clientRequestId
  }
}

function apiError(
  message: string,
  details: PortfolioApiErrorDetails,
): PortfolioApiError {
  return new PortfolioApiError(message, details)
}

export async function request<T>(
  url: string,
  init: RequestInit,
  options: RequestOptions,
): Promise<T> {
  if (!isRequestOperation(options.operation)) {
    throw new TypeError('Request operation is required')
  }
  const startedAt = performance.now()
  const clientRequestId = createClientRequestId()
  const operation = options.operation
  try {
    const result = await requestInternal<T>(url, init, options, clientRequestId)
    reportSlowAnswer(operation, clientRequestId, performance.now() - startedAt)
    return result
  } catch (error: unknown) {
    if (error instanceof PortfolioApiError) {
      reportRequestFailure(error, operation, performance.now() - startedAt)
    }
    throw error
  }
}

async function requestInternal<T>(
  url: string,
  init: RequestInit,
  options: RequestOptions,
  clientRequestId: string,
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('X-Client-Session-Id', getClientSessionId())
  headers.set('X-Client-Request-Id', clientRequestId)
  const controller = new AbortController()
  let timedOut = false
  let cancelledByCaller = options.signal?.aborted ?? false
  const abortFromCaller = () => {
    cancelledByCaller = true
    controller.abort()
  }
  if (cancelledByCaller) {
    controller.abort()
  } else {
    options.signal?.addEventListener('abort', abortFromCaller, { once: true })
  }
  const timeoutId = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, options.timeoutMs ?? REQUEST_TIMEOUT_MS)
  try {
    const response = await fetch(url, { ...init, headers, signal: controller.signal })
    const requestId = response.headers.get('X-Request-Id') ?? undefined

    if (!response.ok) {
      let code: ApiErrorCode | undefined
      let retryAfterSeconds: number | undefined
      try {
        const body = (await response.json()) as ApiErrorBody
        code = normalizeApiErrorCode(body.code)
        retryAfterSeconds = Number.isFinite(body.retryAfterSeconds)
          && Number(body.retryAfterSeconds) > 0
          ? Number(body.retryAfterSeconds)
          : undefined
      } catch {
        if (cancelledByCaller) {
          throw apiError('Request cancelled', {
            kind: 'CANCELLED',
            code: 'REQUEST_CANCELLED',
            requestId,
            action: 'NONE',
            clientRequestId,
          })
        }
        if (timedOut) {
          throw apiError('作品集服务响应超时，请稍后重试', {
            kind: 'TIMEOUT',
            code: 'CLIENT_REQUEST_TIMEOUT',
            requestId,
            action: 'RETRY',
            clientRequestId,
          })
        }
        // Keep the stable public message when the server body is not JSON.
      }
      throw apiError('作品集服务暂时无法处理这个请求', {
        kind: 'HTTP',
        status: response.status,
        code,
        requestId,
        retryAfterSeconds,
        action: actionForApiError(code),
        clientRequestId,
      })
    }

    try {
      // P3：幂等 DELETE 等成功响应可能无正文（204），跳过 JSON 解析。
      if (options.expectNoContent === true) return undefined as T
      return (await response.json()) as T
    } catch {
      if (cancelledByCaller) {
        throw apiError('Request cancelled', {
          kind: 'CANCELLED',
          code: 'REQUEST_CANCELLED',
          requestId,
          action: 'NONE',
          clientRequestId,
        })
      }
      if (timedOut) {
        throw apiError('作品集服务响应超时，请稍后重试', {
          kind: 'TIMEOUT',
          code: 'CLIENT_REQUEST_TIMEOUT',
          requestId,
          action: 'RETRY',
          clientRequestId,
        })
      }
      throw apiError('作品集服务返回了无效响应，请稍后重试', {
        kind: 'INVALID_RESPONSE',
        code: 'CLIENT_INVALID_RESPONSE',
        requestId,
        action: 'RETRY',
        clientRequestId,
      })
    }
  } catch (error: unknown) {
    if (error instanceof PortfolioApiError) {
      throw error
    }
    if (cancelledByCaller) {
      throw apiError('Request cancelled', {
        kind: 'CANCELLED',
        code: 'REQUEST_CANCELLED',
        action: 'NONE',
        clientRequestId,
      })
    }
    if (timedOut) {
      throw apiError('作品集服务响应超时，请稍后重试', {
        kind: 'TIMEOUT',
        code: 'CLIENT_REQUEST_TIMEOUT',
        action: 'RETRY',
        clientRequestId,
      })
    }
    throw apiError('暂时无法连接作品集服务，请稍后重试', {
      kind: 'NETWORK',
      code: 'CLIENT_NETWORK_ERROR',
      action: 'RETRY',
      clientRequestId,
    })
  } finally {
    clearTimeout(timeoutId)
    options.signal?.removeEventListener('abort', abortFromCaller)
  }
}

export function getPortfolio(): Promise<PortfolioHome> {
  return request<PortfolioHome>('/api/v1/portfolio', { method: 'GET' }, { operation: RequestOperation.PUBLIC_CONTENT })
}

export function getProject(slug: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/v1/projects/${encodeURIComponent(slug)}`, { method: 'GET' }, { operation: RequestOperation.PROJECT })
}

export async function getPublicContent(): Promise<PublicPortfolio> {
  const payload = await request<Record<string, unknown>>(
    '/api/v1/public-content',
    { method: 'GET' },
    { operation: RequestOperation.PUBLIC_CONTENT },
  )
  const availability = payload.agentAvailability
  const available = typeof availability === 'object'
    && availability !== null
    && !Array.isArray(availability)
    && (availability as Record<string, unknown>).status === 'AVAILABLE'
  const freeTextSemanticRouting = available
    && (availability as Record<string, unknown>).freeTextSemanticRouting === 'AVAILABLE'
      ? 'AVAILABLE'
      : 'DISABLED'
  // 旧服务或损坏响应缺少能力投影时，公开内容仍可浏览，
  // 但 Agent 提交入口必须 fail-closed，不能把“不知道”当成“可用”。
  return {
    ...payload,
    agentAvailability: {
      status: available ? 'AVAILABLE' : 'UNAVAILABLE',
      freeTextSemanticRouting,
    },
  } as unknown as PublicPortfolio
}

function reportSlowAnswer(
  operation: RequestOperation,
  clientRequestId: string,
  elapsedMilliseconds: number,
): void {
  if (operation !== RequestOperation.ANSWER || elapsedMilliseconds < 5_000) return
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName: 'frontend.agent.request.slow',
    clientRequestId,
    durationBucket: durationBucketFor(elapsedMilliseconds),
  }))
}

function reportRequestFailure(
  error: PortfolioApiError,
  operation: RequestOperation,
  elapsedMilliseconds: number,
): void {
  const eventName = diagnosticEventNameFor(error.kind, operation)
  if (!eventName) return
  frontendDiagnostics.report(createFrontendDiagnosticEvent({
    eventName,
    clientRequestId: error.clientRequestId,
    ...(error.requestId === undefined ? {} : { serverRequestId: error.requestId }),
    ...(error.code === undefined ? {} : { errorCode: error.code }),
    errorKind: error.kind,
    durationBucket: durationBucketFor(elapsedMilliseconds),
  }))
}

function diagnosticEventNameFor(
  errorKind: ApiFailureKind,
  operation: RequestOperation,
): 'frontend.content.load.failed' | 'frontend.agent.request.failed' | 'frontend.agent.request.cancelled' | 'frontend.response.invalid' | undefined {
  if (errorKind === 'INVALID_RESPONSE') return 'frontend.response.invalid'
  if (operation === RequestOperation.ANSWER) {
    return errorKind === 'CANCELLED' ? 'frontend.agent.request.cancelled' : 'frontend.agent.request.failed'
  }
  if (operation === RequestOperation.PUBLIC_CONTENT || operation === RequestOperation.PROJECT) {
    return 'frontend.content.load.failed'
  }
  return undefined
}

function isRequestOperation(value: unknown): value is RequestOperation {
  return value === RequestOperation.PUBLIC_CONTENT
    || value === RequestOperation.PROJECT
    || value === RequestOperation.ANSWER
}
