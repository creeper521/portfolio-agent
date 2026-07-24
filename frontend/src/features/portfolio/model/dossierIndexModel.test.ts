import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { buildDossierIndex, caseToIndexEntry, projectToIndexEntry } from './dossierIndexModel'

describe('dossierIndexModel', () => {
  const { projects, cases } = previewPublicContent

  describe('projectToIndexEntry / caseToIndexEntry', () => {
    it('puts a project in the DELIVERED group', () => {
      const entry = projectToIndexEntry(projects[0])
      expect(entry.group).toBe('DELIVERED')
      expect(entry.typeLabel).toBe('核心项目')
      expect(entry.code).toBe('P-01')
    })

    it('puts a FEATURE case in the DELIVERED group', () => {
      const feature = cases.find((c) => c.slug === 'multilingual-image-preservation')!
      const entry = caseToIndexEntry(feature)
      expect(entry.group).toBe('DELIVERED')
      expect(entry.typeLabel).toBe('功能修复')
    })

    it('puts an EVALUATION case in its own group', () => {
      const evaluation = cases.find((c) => c.slug === 'codegraph-evaluation')!
      const entry = caseToIndexEntry(evaluation)
      expect(entry.group).toBe('EVALUATION')
      expect(entry.typeLabel).toBe('工具评测')
    })
  })

  describe('buildDossierIndex', () => {
    it('splits delivered work from evaluation into two groups', () => {
      const groups = buildDossierIndex(projects, cases)
      expect(groups).toHaveLength(2)
      expect(groups[0].code).toBe('A / DELIVERED')
      expect(groups[0].entries).toHaveLength(2) // project + multilingual case
      expect(groups[1].code).toBe('B / EVALUATION')
      expect(groups[1].entries).toHaveLength(1) // codegraph only
    })

    it('keeps projects before cases within the delivered group', () => {
      const [delivered] = buildDossierIndex(projects, cases)
      expect(delivered.entries.map((e) => e.code)).toEqual(['P-01', 'CASE-01'])
    })

    it('omits the evaluation group when there are no evaluation cases', () => {
      const featureOnly = cases.filter((c) => c.type !== 'EVALUATION')
      const groups = buildDossierIndex(projects, featureOnly)
      expect(groups).toHaveLength(1)
      expect(groups[0].code).toBe('A / DELIVERED')
    })
  })
})
