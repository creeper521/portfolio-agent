import { describe, expect, it } from 'vitest'

import { previewPublicContentRepository } from './previewPublicContentRepository'

describe('previewPublicContentRepository', () => {
  it('returns only approved public evidence', async () => {
    const evidence = await previewPublicContentRepository.getEvidence()

    expect(evidence.length).toBeGreaterThan(0)
    expect(evidence.every((item) => item.publicStatus === 'APPROVED')).toBe(true)
  })

  it('does not invent a missing owner name', async () => {
    const portfolio = await previewPublicContentRepository.getPortfolio()

    expect(portfolio.owner.name).toBe('')
  })

  it('returns project details through the public slug', async () => {
    const project = await previewPublicContentRepository.getProject('sql-audit')

    expect(project?.title).toBe('SQL 审计与故障排查工具')
    expect(project?.evidenceIds).toEqual(['sql-audit-delivery-set'])
  })

  it('returns null for an unpublished project slug', async () => {
    await expect(previewPublicContentRepository.getProject('private-project')).resolves.toBeNull()
  })
})
