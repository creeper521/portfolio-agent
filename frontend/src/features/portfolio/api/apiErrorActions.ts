export type ErrorAction =
  | 'NONE'
  | 'RETRY'
  | 'RETRY_AFTER'
  | 'CORRECT_INPUT'
  | 'NAVIGATE_BACK'

export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'INTERNAL_ERROR'
  | 'PROJECT_NOT_FOUND'
  | 'CASE_NOT_FOUND'
  | 'INVALID_ANSWER_CONTEXT'
  | 'ANSWER_RATE_LIMITED'
  | 'ANSWER_CONCURRENCY_LIMITED'
  | 'ANSWER_REQUEST_TIMEOUT'
  | 'CLIENT_REQUEST_TIMEOUT'
  | 'CLIENT_NETWORK_ERROR'
  | 'CLIENT_INVALID_RESPONSE'
  | 'REQUEST_CANCELLED'
  | 'UNKNOWN'

const API_ERROR_CODES = new Set<ApiErrorCode>([
  'VALIDATION_ERROR',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'UNSUPPORTED_MEDIA_TYPE',
  'INTERNAL_ERROR',
  'PROJECT_NOT_FOUND',
  'CASE_NOT_FOUND',
  'INVALID_ANSWER_CONTEXT',
  'ANSWER_RATE_LIMITED',
  'ANSWER_CONCURRENCY_LIMITED',
  'ANSWER_REQUEST_TIMEOUT',
  'CLIENT_REQUEST_TIMEOUT',
  'CLIENT_NETWORK_ERROR',
  'CLIENT_INVALID_RESPONSE',
  'REQUEST_CANCELLED',
  'UNKNOWN',
])

export function normalizeApiErrorCode(code: unknown): ApiErrorCode | undefined {
  if (code === undefined) return undefined
  return typeof code === 'string' && API_ERROR_CODES.has(code as ApiErrorCode)
    ? code as ApiErrorCode
    : 'UNKNOWN'
}

export function actionForApiError(code: string | undefined): ErrorAction {
  switch (normalizeApiErrorCode(code)) {
    case 'ANSWER_RATE_LIMITED':
      return 'RETRY_AFTER'
    case 'ANSWER_REQUEST_TIMEOUT':
      return 'RETRY'
    case 'VALIDATION_ERROR':
      return 'CORRECT_INPUT'
    case 'PROJECT_NOT_FOUND':
      return 'NAVIGATE_BACK'
    case 'REQUEST_CANCELLED':
      return 'NONE'
    default:
      return 'RETRY'
  }
}
