import type { PortfolioHome, ProjectDetail } from '../model/portfolioTypes'
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'

interface ApiErrorBody {
  message?: string
}

const REQUEST_TIMEOUT_MS = 10_000

export class PortfolioApiError extends Error {
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'PortfolioApiError'
    this.status = status
  }
}

export async function request<T>(url: string, init: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  try {
    const response = await fetch(url, { ...init, signal: controller.signal })

    if (!response.ok) {
      let message = '作品集服务暂时无法处理这个请求'
      try {
        const body = (await response.json()) as ApiErrorBody
        if (body.message) {
          message = body.message
        }
      } catch (cause) {
        if (controller.signal.aborted) {
          throw cause
        }
        // Keep the stable public message when the server body is not JSON.
      }
      throw new PortfolioApiError(message, response.status)
    }

    return (await response.json()) as T
  } catch (cause) {
    if (cause instanceof PortfolioApiError) {
      throw cause
    }
    if (controller.signal.aborted) {
      throw new PortfolioApiError('作品集服务响应超时，请稍后重试')
    }
    throw new PortfolioApiError('暂时无法连接作品集服务，请稍后重试')
  } finally {
    clearTimeout(timeoutId)
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
