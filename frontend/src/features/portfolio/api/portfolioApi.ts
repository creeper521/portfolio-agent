import { EMPTY_MODEL_CATALOG, parseModelCatalogProjection } from '../../agent/model/modelSelection'
import { createClientRequestId, getClientSessionId } from '../../../shared/diagnostics/clientCorrelation'
import { frontendDiagnostics } from '../../../shared/diagnostics/frontendDiagnostics'
import { createFrontendDiagnosticEvent, durationBucketFor } from '../../../shared/diagnostics/frontendDiagnosticTypes'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'
import {
  actionForApiError,
  normalizeApiErrorCode,
  type ApiErrorCode,
  type ErrorAction,
} from './apiErrorActions'

/**
 * portfolio 前端 HTTP 访问层（api 层）。
 *
 * 数据流位置：位于 repository / composable 之下，是浏览器侧唯一的 fetch 出口，
 * 负责：注入 correlation 头（X-Client-Session-Id / X-Client-Request-Id）、
 * 超时与调用方取消、错误统一折叠为 PortfolioApiError（含错误码与 ErrorAction）、
 * 以及向前端诊断通道上报慢请求与失败事件。上层（public-content repository、Agent Workspace）
 * 通过 request/getPortfolioSnapshot 调用本模块，不直接使用 fetch。
 */

/** 后端错误响应体的最小结构：只消费 code 与 retryAfterSeconds，其余字段忽略。 */
interface ApiErrorBody {
  code?: string
  retryAfterSeconds?: number
}

/** 构造 PortfolioApiError 所需的全部错误细节（内部类型，经 apiError 包装后使用）。 */
interface PortfolioApiErrorDetails {
  /** 失败类别：HTTP 状态错误 / 超时 / 断网 / 响应非法 / 调用方取消 */
  kind: ApiFailureKind
  code?: ApiErrorCode
  status?: number
  /** 服务端回带的 X-Request-Id，用于跨端排查时与服务端日志对齐 */
  requestId?: string
  retryAfterSeconds?: number
  action: ErrorAction
  clientRequestId: string
}

/** 默认请求超时时间；调用方可在 RequestOptions.timeoutMs 覆盖。 */
const REQUEST_TIMEOUT_MS = 10_000

/** 请求失败类别，用于区分“服务端拒绝”与“客户端环境问题”。 */
export type ApiFailureKind = 'HTTP' | 'TIMEOUT' | 'NETWORK' | 'INVALID_RESPONSE' | 'CANCELLED'

/**
 * 请求操作类别：决定失败时上报哪个诊断事件。
 * PUBLIC_CONTENT 对应公开内容加载，ANSWER 对应 Agent 提问。
 */
export enum RequestOperation {
  PUBLIC_CONTENT = 'PUBLIC_CONTENT',
  ANSWER = 'ANSWER',
}

/** request() 的可选行为参数。 */
export interface RequestOptions {
  operation: RequestOperation
  signal?: AbortSignal
  timeoutMs?: number
  // 用于幂等 DELETE 等“成功无正文”的响应（204 No Content）：
  // 为真时 2xx 响应不解析 JSON 而是直接 resolve，避免空响应体被误判为 INVALID_RESPONSE。（P3）
  expectNoContent?: boolean
}

/**
 * portfolio API 的统一错误类型：所有请求失败最终都会折叠为该类的实例。
 *
 * 携带失败类别（kind）、归一化错误码（code）、HTTP 状态、服务端/客户端请求 id、
 * 建议等待秒数与界面处置动作（action），上层据此渲染错误态并决定是否可重试，
 * 无需再各自解析原始 Error。
 */
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

/** 工厂函数：用 details 构造 PortfolioApiError，便于各失败分支以紧凑形式抛出。 */
function apiError(
  message: string,
  details: PortfolioApiErrorDetails,
): PortfolioApiError {
  return new PortfolioApiError(message, details)
}

/**
 * 统一请求入口：包装 fetch，注入 correlation 头、处理超时/取消/网络错误，
 * 并上报慢请求与失败诊断事件。
 * @param url 请求地址（站内相对路径）
 * @param init 原生 fetch 的 RequestInit（method、body 等）
 * @param options 操作类别、超时、取消信号、expectNoContent
 * @returns 解析后的 JSON 响应体；expectNoContent 为真且响应为 2xx 时返回 undefined
 * @throws PortfolioApiError 所有失败路径（HTTP 错误状态、超时、断网、响应非法、取消）
 *                           均抛出携带 kind/code/action 的统一错误
 */
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

/**
 * 请求的实际执行体：负责 abort 信号桥接（调用方 signal → 内部 controller）、
 * 超时定时器、错误响应体解析，以及把各类原生异常折叠为 PortfolioApiError。
 * 取消优先级：调用方主动取消 > 超时 > 其他网络异常，用于给错误选择正确的 kind。
 */
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
        // 服务端错误体不是合法 JSON 时（如网关返回 HTML）不在此报错，
        // 继续沿用下方面向用户的稳定文案，只保留状态码与 requestId。
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
      // 幂等 DELETE 等成功响应可能无正文（204），跳过 JSON 解析，避免空体被误判为无效响应。（P3）
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

/**
 * 拉取公开作品集快照（GET /api/portfolio），并对 Agent 能力投影做防御性校验。
 * @returns 校验补全后的 PublicPortfolio；agentAvailability 永远是完整对象
 */
export async function getPortfolioSnapshot(): Promise<PublicPortfolio> {
  const payload = await request<Record<string, unknown>>(
    '/api/portfolio',
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
  // A7 模型目录投影 fail-closed：损坏/缺失的目录规范化为空目录（显式 NONE），
  // 目录语义仍以后端为唯一权威，前端不静态复制。
  const modelCatalog = parseModelCatalogProjection(available ? availability : undefined)
    ?? EMPTY_MODEL_CATALOG
  // 旧服务或损坏响应缺少能力投影时，公开内容仍可浏览，
  // 但 Agent 提交入口必须 fail-closed：无法确认“可用”时一律按不可用处理。
  return {
    ...payload,
    agentAvailability: {
      status: available ? 'AVAILABLE' : 'UNAVAILABLE',
      freeTextSemanticRouting,
      modelCatalogVersion: modelCatalog.modelCatalogVersion,
      defaultModelSelection: modelCatalog.defaultModelSelection,
      selectableModels: modelCatalog.selectableModels,
    },
  } as unknown as PublicPortfolio
}

/**
 * 上报慢请求诊断事件：仅针对 ANSWER 操作，且耗时超过 5 秒才上报（内容加载不在此列），
 * 避免诊断通道被高频内容加载淹没。
 */
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

/** 上报请求失败诊断事件：按失败类别与操作类别选择事件名，可选字段仅在存在时携带。 */
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

/**
 * 失败类别 + 操作类别 → 诊断事件名的映射。
 * 取消与失败分開上报（cancelled 事件不计入普通失败）；
 * 无法识别的组合返回 undefined，表示不上报。
 */
function diagnosticEventNameFor(
  errorKind: ApiFailureKind,
  operation: RequestOperation,
): 'frontend.content.load.failed' | 'frontend.agent.request.failed' | 'frontend.agent.request.cancelled' | 'frontend.response.invalid' | undefined {
  if (errorKind === 'INVALID_RESPONSE') return 'frontend.response.invalid'
  if (operation === RequestOperation.ANSWER) {
    return errorKind === 'CANCELLED' ? 'frontend.agent.request.cancelled' : 'frontend.agent.request.failed'
  }
  if (operation === RequestOperation.PUBLIC_CONTENT) {
    return 'frontend.content.load.failed'
  }
  return undefined
}

function isRequestOperation(value: unknown): value is RequestOperation {
  return value === RequestOperation.PUBLIC_CONTENT
    || value === RequestOperation.ANSWER
}
