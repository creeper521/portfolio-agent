import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { buildDossierIndex, caseToIndexEntry, projectToIndexEntry } from './dossierIndexModel'

describe('dossierIndexModel', () => {
  const { projects, cases } = previewPublicContent

  describe('projectToIndexEntry / caseToIndexEntry', () => {
    it('puts a project in the MAINLINE group', () => {
      const entry = projectToIndexEntry(projects[0])
      expect(entry.group).toBe('MAINLINE')
      expect(entry.typeLabel).toBe('核心项目')
      expect(entry.code).toBe('P-01')
    })

    it('puts a FEATURE case in the TASK group', () => {
      const feature = cases.find((c) => c.slug === 'multilingual-image-preservation')!
      const entry = caseToIndexEntry(feature)
      expect(entry.group).toBe('TASK')
      expect(entry.typeLabel).toBe('功能任务')
    })

    it('puts an EVALUATION case in its own group', () => {
      const evaluation = cases.find((c) => c.slug === 'codegraph-evaluation')!
      const entry = caseToIndexEntry(evaluation)
      expect(entry.group).toBe('EVALUATION')
      expect(entry.typeLabel).toBe('工具评测')
    })

    it('puts an INCIDENT case in the INCIDENT group', () => {
      const incident = { ...cases[0], type: 'INCIDENT' as const }
      const entry = caseToIndexEntry(incident)
      expect(entry.group).toBe('INCIDENT')
      expect(entry.typeLabel).toBe('问题处理')
    })
  })

  describe('buildDossierIndex', () => {
    it('splits mainlines, tasks and knowledge assets into separate groups', () => {
      const groups = buildDossierIndex(projects, cases)
      expect(groups).toHaveLength(3)
      expect(groups[0].code).toBe('A / MAINLINE')
      expect(groups[0].entries).toHaveLength(1)
      expect(groups[1].code).toBe('B / TASK')
      expect(groups[1].entries).toHaveLength(1)
      expect(groups[2].code).toBe('D / KNOWLEDGE')
      expect(groups[2].entries).toHaveLength(1)
    })

    it('keeps projects and cases in their source order within each group', () => {
      const [mainlines, tasks] = buildDossierIndex(projects, cases)
      expect(mainlines.entries.map((e) => e.code)).toEqual(['P-01'])
      expect(tasks.entries.map((e) => e.code)).toEqual(['CASE-01'])
    })

    it('omits empty incident and knowledge groups', () => {
      const featureOnly = cases.filter((c) => c.type !== 'EVALUATION')
      const groups = buildDossierIndex(projects, featureOnly)
      expect(groups).toHaveLength(2)
      expect(groups.map((group) => group.code)).toEqual(['A / MAINLINE', 'B / TASK'])
    })
  })
})
