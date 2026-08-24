import { afterEach, describe, expect, it, vi } from 'vitest'

import { newRequestId } from './requestId'

// A2-74：后端 requestId 合同只接受 UUID；任何回退都不得产生非 UUID 标识。

const UUID_V4
  = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('newRequestId', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('优先使用 crypto.randomUUID', () => {
    vi.stubGlobal('crypto', {
      randomUUID: () => '11111111-2222-4333-8444-555555555555',
    })
    expect(newRequestId()).toBe('11111111-2222-4333-8444-555555555555')
  })

  it('无 randomUUID 时用 getRandomValues 构造合法 v4 UUID，绝不退化为时间戳标识', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (array: Uint8Array) => {
        for (let index = 0; index < array.length; index += 1) array[index] = index + 1
        return array
      },
    })
    const requestId = newRequestId()
    expect(requestId).toMatch(UUID_V4)
    expect(requestId).not.toContain('turn-')
  })

  it('完全无加密随机源时抛出，阻止发送必然违约的请求', () => {
    vi.stubGlobal('crypto', {})
    expect(() => newRequestId()).toThrow()
  })
})
