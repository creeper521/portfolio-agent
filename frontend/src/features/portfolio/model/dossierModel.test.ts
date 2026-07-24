import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import { caseToDossier, projectToDossier, resolveDossier } from './dossierModel'

describe('dossierModel', () => {
  const project = previewPublicContent.projects[0]
  const multilingualCase = previewPublicContent.cases.find(
    (item) => item.slug === 'multilingual-image-preservation',
  )!
  const codegraphCase = previewPublicContent.cases.find(
    (item) => item.slug === 'codegraph-evaluation',
  )!

  describe('projectToDossier', () => {
    it('maps project background→problem and keyDecisions→decisions', () => {
      const dossier = projectToDossier(project)
      expect(dossier.kind).toBe('PROJECT')
      expect(dossier.caseType).toBeNull()
      expect(dossier.problem).toBe(project.background)
      expect(dossier.responsibilities).toBe(project.responsibilities)
      expect(dossier.decisions).toBe(project.keyDecisions)
      expect(dossier.boundary).toBe(project.handoff)
      expect(dossier.technologies).toBe(project.technologies)
      expect(dossier.status.status).toBe('DELIVERED')
    })

    it('keeps the five shared sections in order', () => {
      const dossier = projectToDossier(project)
      expect(dossier.sections.map((s) => s.anchor)).toEqual([
        'why', 'role', 'how', 'proof', 'status',
      ])
    })
  })

  describe('caseToDossier', () => {
    it('maps a FEATURE case into the same dossier shape', () => {
      const dossier = caseToDossier(multilingualCase)
      expect(dossier.kind).toBe('CASE')
      expect(dossier.caseType).toBe('FEATURE')
      expect(dossier.problem).toBe(multilingualCase.problem)
      expect(dossier.responsibilities).toBe(multilingualCase.actions)
      expect(dossier.decisions).toBe(multilingualCase.decisions)
      expect(dossier.boundary).toContain('不公开内部模块')
      expect(dossier.evidenceIds).toEqual(['evidence-case-multilingual'])
      expect(dossier.technologies).toEqual([])
    })

    it('carries the prototype status for an evaluation case', () => {
      const dossier = caseToDossier(codegraphCase)
      expect(dossier.caseType).toBe('EVALUATION')
      expect(dossier.status.status).toBe('PROTOTYPE')
    })
  })

  describe('resolveDossier', () => {
    it('resolves a project slug first', () => {
      const dossier = resolveDossier(
        'sql-audit',
        previewPublicContent.projects,
        previewPublicContent.cases,
      )
      expect(dossier?.kind).toBe('PROJECT')
      expect(dossier?.title).toBe('SQL 审计与故障排查工具')
    })

    it('falls back to cases for a case slug', () => {
      const dossier = resolveDossier(
        'multilingual-image-preservation',
        previewPublicContent.projects,
        previewPublicContent.cases,
      )
      expect(dossier?.kind).toBe('CASE')
      expect(dossier?.code).toBe('CASE-01')
    })

    it('returns null for an unknown slug', () => {
      expect(
        resolveDossier('nope', previewPublicContent.projects, previewPublicContent.cases),
      ).toBeNull()
    })
  })
})
