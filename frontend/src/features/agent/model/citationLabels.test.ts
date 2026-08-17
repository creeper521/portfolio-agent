import { describe, expect, it } from 'vitest'

import { buildEvidenceLabeler } from './citationLabels'

const CATALOG = [
  { id: 'sql-audit-delivery-set', code: 'E-01', title: 'SQL 审计工具交付证据集' },
  { id: 'evidence-case-multilingual', code: 'E-03', title: '多语言图片保留实现与回归证据集' },
]

describe('buildEvidenceLabeler', () => {
  it('用公开编号和标题生成引用标签', () => {
    const label = buildEvidenceLabeler(CATALOG)
    expect(label('sql-audit-delivery-set')).toBe('E-01 · SQL 审计工具交付证据集')
    expect(label('evidence-case-multilingual')).toBe('E-03 · 多语言图片保留实现与回归证据集')
  })

  it('未知证据 ID 回退为通用文案，绝不显示内部 ID', () => {
    const label = buildEvidenceLabeler(CATALOG)
    expect(label('some-internal-id')).toBe('已审核公开证据')
    expect(label('some-internal-id')).not.toContain('some-internal-id')
  })

  it('空目录时全部回退', () => {
    const label = buildEvidenceLabeler([])
    expect(label('sql-audit-delivery-set')).toBe('已审核公开证据')
  })

  it('相同 ID 的后续条目不覆盖首个映射', () => {
    const label = buildEvidenceLabeler([
      ...CATALOG,
      { id: 'sql-audit-delivery-set', code: 'E-09', title: '重复条目' },
    ])
    expect(label('sql-audit-delivery-set')).toBe('E-01 · SQL 审计工具交付证据集')
  })
})
