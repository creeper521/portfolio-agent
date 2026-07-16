import type { AudienceProfile } from '../model/audienceTypes'

export const audienceProfiles: AudienceProfile[] = [
  {
    id: 'INTERVIEWER',
    code: 'DEFAULT',
    label: '技术面试官',
    description: '回答侧重技术方案、取舍和实现细节，每个结论标注状态与证据。',
    questions: [
      '介绍 SQL 审计工具的完整迭代。',
      '查询为什么需要设计成异步？',
      '这个项目中最关键的技术决策是什么？',
      '哪些是完成交付，哪些只是学习？',
    ],
  },
  {
    id: 'MENTOR',
    code: 'MENTOR',
    label: '未来导师',
    description: '回答侧重工作过程、复盘质量和能力如何在连续迭代中形成。',
    questions: [
      '你如何复盘这个项目？',
      '遇到问题时采用了怎样的排查方法？',
      '哪些工作是独立或主导完成的？',
      '如果重做一次会改变什么？',
    ],
  },
  {
    id: 'HR',
    code: 'HR',
    label: 'HR / 招聘者',
    description: '回答侧重经历概况、职责范围、交付状态和贡献边界。',
    questions: [
      '用一分钟介绍这段实习经历。',
      '完成了哪些核心工程产出？',
      '在项目中承担了什么职责？',
      '为什么适合服务端开发岗位？',
    ],
  },
  {
    id: 'GUEST',
    code: 'GUEST',
    label: '普通访客',
    description: '使用更通俗的语言解释项目做了什么，同时保留事实边界。',
    questions: [
      '这份作品集应该从哪里开始看？',
      'SQL 审计工具解决了什么问题？',
      '为什么强调证据和交付状态？',
      '完整 Agent 可以回答什么？',
    ],
  },
]
