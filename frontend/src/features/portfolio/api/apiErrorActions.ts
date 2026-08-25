/**
 * portfolio API 错误码与错误处置动作的映射层（api 层）。
 *
 * 数据流位置：portfolioApi 发起请求后，将后端返回的错误码交给本模块归一化，
 * 再映射为面向界面的 ErrorAction（重试 / 等待后重试 / 修正输入 / 返回上一页 / 不处理），
 * 供 Workspace / 页面决定错误态展示与交互，本模块不发起任何请求。
 */

/**
 * 错误处置动作：界面对该错误应采取的恢复策略。
 * - NONE：静默处理，不向用户报错；
 * - RETRY：允许立即重试；
 * - RETRY_AFTER：需等待（或等待服务端指示的时间）后再重试；
 * - CORRECT_INPUT：需要用户修正输入或操作方式；
 * - NAVIGATE_BACK：目标资源不存在，引导用户返回上一级；
 * - UPGRADE_REQUIRED：客户端版本过低需升级（当前没有后端错误码映射到该动作，作为保留值）。
 */
export type ErrorAction =
  | 'NONE'
  | 'RETRY'
  | 'RETRY_AFTER'
  | 'CORRECT_INPUT'
  | 'NAVIGATE_BACK'
  | 'UPGRADE_REQUIRED'

/**
 * 后端公开错误码与前端本地合成错误码的联合类型。
 * CLIENT_ 前缀的码由前端在超时、断网、响应非法等场景自行合成，
 * 其余码与后端 `/api` 错误响应体中的 `code` 字段一一对应。
 */
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

// 合法错误码白名单：归一化时只放行集合内的码，防止后端新增/异常码直接流入界面逻辑。
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

/**
 * 将来源不明的错误码（通常是后端响应体的 `code` 字段）归一化为受信的 ApiErrorCode。
 * @param code 任意来源的错误码候选值
 * @returns 入参为 undefined 时返回 undefined（表示响应未携带错误码）；
 *          非法或未知字符串一律折叠为 'UNKNOWN'，保证调用方拿到的一定是联合类型成员
 */
export function normalizeApiErrorCode(code: unknown): ApiErrorCode | undefined {
  if (code === undefined) return undefined
  return typeof code === 'string' && API_ERROR_CODES.has(code as ApiErrorCode)
    ? code as ApiErrorCode
    : 'UNKNOWN'
}

/**
 * 将错误码映射为界面处置动作（ErrorAction）。
 * @param code 后端或本地合成的错误码（可能为空，为空时走 default）
 * @returns 该错误对应的恢复策略；未显式列出的码默认允许立即重试
 */
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
    // 同一 requestToken 的请求仍在执行：保持可重试态，由 Workspace 保证沿用同一 token 重发，避免产生新请求。（P3）
    case 'REQUEST_IN_PROGRESS':
      return 'RETRY_AFTER'
    // 幂等 key 相同但请求指纹不同：继续重试只会一直冲突，转为受控错误态让用户修正输入。（P3）
    case 'IDEMPOTENCY_KEY_CONFLICT':
      return 'CORRECT_INPUT'
    // 恢复 Token（ResumeToken）格式非法或已过期：静默丢弃本地 Token 并新建会话即可，不应作为错误打扰用户。（P3）
    case 'INVALID_CONVERSATION_RESUME_TOKEN':
      return 'NONE'
    default:
      return 'RETRY'
  }
}
