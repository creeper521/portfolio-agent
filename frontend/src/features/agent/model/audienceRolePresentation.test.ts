import { describe, expect, it } from 'vitest'

import { audienceRolePresentations, presentationOf } from './audienceRolePresentation'

// 四角色展示映射（audience-role UI 设计 §8/D-AR-8）：闭合四项、顺序与枚举
// 一致；文案为 agent feature 自有副本，不跨 feature 引用首页 audienceProfiles。

describe('audienceRolePresentation', () => {
  it('闭合四项且顺序与闭合枚举一致', () => {
    expect(audienceRolePresentations.map((item) => item.role))
      .toEqual(['INTERVIEWER', 'MENTOR', 'HR', 'GUEST'])
  })

  it('文案取自定稿：短标签、角色名与一句话侧重描述（原型 ROLES）', () => {
    expect(audienceRolePresentations).toEqual([
      {
        role: 'INTERVIEWER',
        shortLabel: '面试官',
        label: '技术面试官',
        description: '侧重技术方案、取舍和实现细节，每个结论标注状态与证据',
      },
      {
        role: 'MENTOR',
        shortLabel: '导师',
        label: '未来导师',
        description: '侧重工作过程、复盘质量和能力如何在连续迭代中形成',
      },
      {
        role: 'HR',
        shortLabel: 'HR',
        label: 'HR / 招聘者',
        description: '侧重经历概况、职责范围、交付状态和贡献边界',
      },
      {
        role: 'GUEST',
        shortLabel: '访客',
        label: '普通访客',
        description: '用更通俗的语言解释项目做了什么，同时保留事实边界',
      },
    ])
  })

  it('presentationOf 覆盖全部闭合角色并返回同一常量', () => {
    for (const item of audienceRolePresentations) {
      expect(presentationOf(item.role)).toBe(item)
    }
  })
})
