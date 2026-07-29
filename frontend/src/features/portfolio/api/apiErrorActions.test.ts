import { describe, expect, it } from 'vitest'

import { actionForApiError } from './apiErrorActions'

describe('actionForApiError', () => {
  it('maps known backend codes and retries unknown codes', () => {
    expect(actionForApiError('ANSWER_RATE_LIMITED')).toBe('RETRY_AFTER')
    expect(actionForApiError('ANSWER_REQUEST_TIMEOUT')).toBe('RETRY')
    expect(actionForApiError('VALIDATION_ERROR')).toBe('CORRECT_INPUT')
    expect(actionForApiError('PROJECT_NOT_FOUND')).toBe('NAVIGATE_BACK')
    expect(actionForApiError('UNKNOWN_BACKEND_CODE')).toBe('RETRY')
  })
})
