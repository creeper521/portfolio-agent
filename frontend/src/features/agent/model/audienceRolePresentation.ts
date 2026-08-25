import type { AudienceRole } from '../../public-content/model/publicContentTypes'

// 四角色展示映射（audience-role UI 设计 §8/D-AR-8）：agent feature 自有的
// 纯常量 + 查找函数，文案复制自首页定稿、不跨 feature 引用 audienceProfiles，
// 两处文案允许独立演化。不含颜色、图标或排序权重——四角色共用同一视觉
// 编码，角色区分只靠文字（D-AR-7）。

export interface AudienceRolePresentation {
  role: AudienceRole
  /** 会话列表短标签：面试官 / 导师 / HR / 访客 */
  shortLabel: string
  /** 角色行与浮层主名 */
  label: string
  /** 一句话侧重描述：角色行截断显示、浮层完整显示 */
  description: string
}

export const audienceRolePresentations: readonly AudienceRolePresentation[] = [
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
]

/** 按闭合角色取展示映射；角色集合编译期闭合，未知值直接失败。 */
export function presentationOf(role: AudienceRole): AudienceRolePresentation {
  const found = audienceRolePresentations.find((item) => item.role === role)
  if (found === undefined) throw new Error(`未知会话视角：${role}`)
  return found
}
