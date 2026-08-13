import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useConversationResume } from './useConversationResume'

// P3 会话级 ResumeToken 的唯一 sessionStorage 槽位（handoff §10.1）。
// 槽位只保存当前活跃会话的不透明 Token，绝不保存问题/答案/Context payload。
describe('useConversationResume', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('stores and reads back the active session resume token', () => {
    const resume = useConversationResume()

    resume.setActiveToken('opaque-token-alpha')

    expect(resume.getActiveToken()).toBe('opaque-token-alpha')
  })

  it('keeps only one token in the slot — a later token replaces the previous', () => {
    const resume = useConversationResume()

    resume.setActiveToken('opaque-token-alpha')
    resume.setActiveToken('opaque-token-beta')

    expect(resume.getActiveToken()).toBe('opaque-token-beta')
    // 槽位里只有一个键。
    expect(sessionStorage.length).toBe(1)
  })

  it('clears the active token slot', () => {
    const resume = useConversationResume()

    resume.setActiveToken('opaque-token-alpha')
    resume.clearActiveToken()

    expect(resume.getActiveToken()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('returns null when the slot is empty', () => {
    const resume = useConversationResume()

    expect(resume.getActiveToken()).toBeNull()
  })

  it('only ever stores the opaque token — never questions, answers, or payload', () => {
    const resume = useConversationResume()

    resume.setActiveToken('opaque-token-gamma')

    // 唯一键存在且值仅为不透明 Token。
    expect(sessionStorage.getItem('portfolio.agent.resume-token.v1')).toBe('opaque-token-gamma')
    // localStorage 保持空（禁止降级写入其他持久介质）。
    expect(localStorage.length).toBe(0)
    // Token 值不得包含问题/答案等业务内容（这里只验证存储的就是纯 token）。
    const allValues = Object.values(Object.fromEntries(Object.entries(sessionStorage)))
    expect(allValues).toEqual(['opaque-token-gamma'])
  })

  it('flags resume as unavailable and does not throw when sessionStorage is blocked', () => {
    // 模拟浏览器禁用/配额耗尽：setItem 抛错。
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })
    const resume = useConversationResume()

    expect(() => resume.setActiveToken('opaque-token-alpha')).not.toThrow()
    expect(resume.resumeUnavailable.value).toBe(true)
    // 不得降级写入 localStorage。
    expect(localStorage.length).toBe(0)
  })

  it('keeps running with no slot and a non-blocking unavailable state when storage is disabled', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    const resume = useConversationResume()

    expect(resume.isSessionStorageAvailable()).toBe(false)
    resume.setActiveToken('opaque-token-alpha')
    expect(resume.getActiveToken()).toBeNull()
    expect(resume.resumeUnavailable.value).toBe(true)
  })
})
