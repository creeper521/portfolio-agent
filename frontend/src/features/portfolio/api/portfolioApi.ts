import type { PortfolioHome, ProjectDetail } from '../model/portfolioTypes'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'

interface ApiErrorBody {
  code?: string
  message?: string
  retryAfterSeconds?: number
}

const REQUEST_TIMEOUT_MS = 10_000

export interface RequestOptions {
  signal?: AbortSignal
  timeoutMs?: number
}

export class PortfolioApiError extends Error {
  readonly status?: number
  readonly code?: string
  readonly retryAfterSeconds?: number

  constructor(message: string, status?: number, code?: string, retryAfterSeconds?: number) {
    super(message)
    this.name = 'PortfolioApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

export async function request<T>(
  url: string,
  init: RequestInit,
  options: RequestOptions = {},
): Promise<T> {
  const controller = new AbortController()
  let timedOut = false
  const abortFromCaller = () => controller.abort()
  if (options.signal?.aborted) {
    controller.abort()
  } else {
    options.signal?.addEventListener('abort', abortFromCaller, { once: true })
  }
  const timeoutId = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, options.timeoutMs ?? REQUEST_TIMEOUT_MS)
  try {
    const response = await fetch(url, { ...init, signal: controller.signal })

    if (!response.ok) {
      let message = '作品集服务暂时无法处理这个请求'
      let code: string | undefined
      let retryAfterSeconds: number | undefined
      try {
        const body = (await response.json()) as ApiErrorBody
        if (body.message) {
          message = body.message
        }
        code = typeof body.code === 'string' ? body.code : undefined
        retryAfterSeconds = Number.isFinite(body.retryAfterSeconds)
          && Number(body.retryAfterSeconds) > 0
          ? Number(body.retryAfterSeconds)
          : undefined
      } catch (cause) {
        if (controller.signal.aborted) {
          throw cause
        }
        // Keep the stable public message when the server body is not JSON.
      }
      throw new PortfolioApiError(message, response.status, code, retryAfterSeconds)
    }

    return (await response.json()) as T
  } catch (cause) {
    if (cause instanceof PortfolioApiError) {
      throw cause
    }
    if (options.signal?.aborted && !timedOut) {
      throw new PortfolioApiError('Request cancelled', undefined, 'REQUEST_CANCELLED')
    }
    if (timedOut) {
      throw new PortfolioApiError('作品集服务响应超时，请稍后重试')
    }
    throw new PortfolioApiError('暂时无法连接作品集服务，请稍后重试')
  } finally {
    clearTimeout(timeoutId)
    options.signal?.removeEventListener('abort', abortFromCaller)
  }
}

export function getPortfolio(): Promise<PortfolioHome> {
  return request<PortfolioHome>('/api/v1/portfolio', { method: 'GET' })
}

export function getProject(slug: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/v1/projects/${encodeURIComponent(slug)}`, { method: 'GET' })
}

export function getPublicContent(): Promise<PublicPortfolio> {
  return request<PublicPortfolio>('/api/v1/public-content', { method: 'GET' })
}
