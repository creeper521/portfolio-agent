import { describe, expect, it } from 'vitest'

import { isNoiseQuestion, shortSessionTitle } from './sessionTitle'

describe('isNoiseQuestion', () => {
  it('纯数字、纯标点、纯表情、空白与过短输入判为噪声', () => {
    expect(isNoiseQuestion('1')).toBe(true)
    expect(isNoiseQuestion('112233')).toBe(true)
    expect(isNoiseQuestion('!!!')).toBe(true)
    expect(isNoiseQuestion('？？？')).toBe(true)
    expect(isNoiseQuestion('😀😀')).toBe(true)
    expect(isNoiseQuestion('   ')).toBe(true)
    expect(isNoiseQuestion('')).toBe(true)
    expect(isNoiseQuestion('a')).toBe(true)
  })

  it('包含汉字或字母的正常问题不是噪声', () => {
    expect(isNoiseQuestion('给我推荐两个项目')).toBe(false)
    expect(isNoiseQuestion('SQL')).toBe(false)
    expect(isNoiseQuestion('详细介绍 SQL 审计项目')).toBe(false)
    expect(isNoiseQuestion('第二个呢')).toBe(false)
  })
})

describe('shortSessionTitle', () => {
  it('正常问题原样保留（去除首尾空白）', () => {
    expect(shortSessionTitle('  给我推荐两个项目 ')).toBe('给我推荐两个项目')
  })

  it('超长问题截断为可扫描短标题并加省略号', () => {
    const long = '请详细介绍SQL审计与故障排查工具项目背景职责技术方案验证过程和最终状态分别是什么'
    const title = shortSessionTitle(long)
    if (title === null) throw new Error('长问题应能生成短标题')
    expect(title.length).toBeLessThanOrEqual(17)
    expect(title.endsWith('…')).toBe(true)
    expect(long.startsWith(title.slice(0, -1))).toBe(true)
  })

  it('合并连续空白字符', () => {
    expect(shortSessionTitle('介绍   这个   项目')).toBe('介绍 这个 项目')
  })

  it('噪声输入返回 null', () => {
    expect(shortSessionTitle('1')).toBeNull()
    expect(shortSessionTitle('!!!')).toBeNull()
  })
})
