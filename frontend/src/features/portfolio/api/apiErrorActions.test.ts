import { describe, expect, it } from 'vitest'

import { actionForApiError, normalizeApiErrorCode } from './apiErrorActions'

describe('actionForApiError', () => {
  it('maps known backend codes and retries unknown codes', () => {
    expect(actionForApiError('ANSWER_RATE_LIMITED')).toBe('RETRY_AFTER')
    expect(actionForApiError('ANSWER_REQUEST_TIMEOUT')).toBe('RETRY')
    expect(actionForApiError('VALIDATION_ERROR')).toBe('CORRECT_INPUT')
    expect(actionForApiError('PROJECT_NOT_FOUND')).toBe('NAVIGATE_BACK')
    expect(actionForApiError('UNKNOWN_BACKEND_CODE')).toBe('RETRY')
  })

  // P3 idempotency + conversation-context error codes (handoff §14, §11, §12).
  it('maps P3 idempotency and resume-token error codes', () => {
    // 同一 requestToken 仍在执行：保持可重试，不得换 token 并发重发。
    expect(actionForApiError('REQUEST_IN_PROGRESS')).toBe('RETRY_AFTER')
    // 同 key 不同指纹：停止自动重试，进入受控错误状态。
    expect(actionForApiError('IDEMPOTENCY_KEY_CONFLICT')).toBe('CORRECT_INPUT')
    // 恢复 Token 格式非法：静默清除并新建会话，不向用户报错。
    expect(actionForApiError('INVALID_CONVERSATION_RESUME_TOKEN')).toBe('NONE')
  })

  it('recognizes P3 codes as known codes (not UNKNOWN)', () => {
    expect(normalizeApiErrorCode('REQUEST_IN_PROGRESS')).toBe('REQUEST_IN_PROGRESS')
    expect(normalizeApiErrorCode('IDEMPOTENCY_KEY_CONFLICT')).toBe('IDEMPOTENCY_KEY_CONFLICT')
    expect(normalizeApiErrorCode('INVALID_CONVERSATION_RESUME_TOKEN'))
      .toBe('INVALID_CONVERSATION_RESUME_TOKEN')
  })
})
