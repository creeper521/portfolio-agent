import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import type { PublicSourceReference } from '../model/answerTypes'
import SourceReferenceList from './SourceReferenceList.vue'

// P3 公开来源引用（handoff §8/§17.19）。只渲染站内相对公开路由，不拼接对象存储地址；
// 保持后端顺序；展示层去重保序但不改变结论—来源绑定。
const REFERENCES: PublicSourceReference[] = [
  {
    referenceKey: 'SRC_SQL_AUDIT_DELIVERED',
    label: 'SQL 审计 · 交付证据',
    sourceType: 'DOCUMENT',
    subjectRoute: '/projects/sql-audit',
    evidenceRoute: '/evidence?evidence=evi-sql-audit',
    publishedVersion: 'public-2026-07-31',
  },
  {
    referenceKey: 'SRC_SQL_AUDIT_TEST',
    label: 'SQL 审计 · 测试结果',
    sourceType: 'TEST_RESULT',
    subjectRoute: '/projects/sql-audit',
    publishedVersion: 'public-2026-07-31',
  },
]

describe('SourceReferenceList', () => {
  it('renders each reference with its key, label and source-type label in order', () => {
    const wrapper = mount(SourceReferenceList, {
      props: { references: REFERENCES },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    const items = wrapper.findAll('[data-source-reference]')
    expect(items).toHaveLength(2)
    expect(items[0]?.attributes('data-source-reference')).toBe('SRC_SQL_AUDIT_DELIVERED')
    expect(items[0]?.text()).toContain('SQL 审计 · 交付证据')
    // sourceType 用受控中文标签表达。
    expect(items[1]?.attributes('data-source-type')).toBe('TEST_RESULT')
  })

  it('links only to in-site relative routes and surfaces the published version', () => {
    const wrapper = mount(SourceReferenceList, {
      props: { references: REFERENCES },
      global: { stubs: { RouterLink: { template: '<a :href="String($attrs.to)"><slot /></a>' } } },
    })

    const links = wrapper.findAll('a')
    expect(links[0]?.attributes('href')).toBe('/projects/sql-audit')
    // 不拼接对象存储/绝对地址。
    const html = wrapper.html()
    expect(html).not.toContain('https://')
    expect(html).not.toContain('s3:')
    expect(wrapper.text()).toContain('public-2026-07-31')
  })

  it('renders nothing when there are no references', () => {
    const wrapper = mount(SourceReferenceList, {
      props: { references: [] },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    expect(wrapper.find('[data-source-reference]').exists()).toBe(false)
  })
})
