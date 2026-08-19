export type ErrorAction =
  | 'NONE'
  | 'RETRY'
  | 'RETRY_AFTER'
  | 'CORRECT_INPUT'
  | 'NAVIGATE_BACK'
  | 'UPGRADE_REQUIRED'

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
  | 'REQUEST_IN_PROGRESS'
  | 'IDEMPOTENCY_KEY_CONFLICT'
  | 'INVALID_CONVERSATION_RESUME_TOKEN'
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
  'REQUEST_IN_PROGRESS',
  'IDEMPOTENCY_KEY_CONFLICT',
  'INVALID_CONVERSATION_RESUME_TOKEN',
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
    // P3：同一 requestToken 仍在执行——保持可重试态，由 Workspace 保证不换 token 重发。
    case 'REQUEST_IN_PROGRESS':
      return 'RETRY_AFTER'
    // P3：同 key 不同指纹——停止自动重试，进入受控错误状态。
    case 'IDEMPOTENCY_KEY_CONFLICT':
      return 'CORRECT_INPUT'
    // P3：恢复 Token 格式非法——静默处理（清除本地并新建会话），不向用户报错。
    case 'INVALID_CONVERSATION_RESUME_TOKEN':
      return 'NONE'
    default:
      return 'RETRY'
  }
}
