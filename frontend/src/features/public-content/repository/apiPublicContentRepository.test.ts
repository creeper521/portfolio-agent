import { describe, expect, it, vi } from 'vitest'

import { previewPublicContent } from '../data/previewPublicContent'
import { ApiPublicContentRepository } from './apiPublicContentRepository'

describe('ApiPublicContentRepository', () => {
  it('shares one aggregate request across every selector', async () => {
    const loader = vi.fn().mockResolvedValue(previewPublicContent)
    const repository = new ApiPublicContentRepository(loader)

    const [portfolio, projects, project, timeline, evidence] = await Promise.all([
      repository.getPortfolio(),
      repository.getProjects(),
      repository.getProject('sql-audit'),
      repository.getTimeline(),
      repository.getEvidence(),
    ])

    expect(loader).toHaveBeenCalledTimes(1)
    expect(portfolio.contentVersion).toBe('2026-07-21.1')
    expect(projects).toHaveLength(1)
    expect(project?.code).toBe('P-01')
    expect(timeline).toHaveLength(previewPublicContent.timeline.length)
    expect(evidence.every((item) => item.publicStatus === 'APPROVED')).toBe(true)
  })

  it('loads again after invalidation', async () => {
    const loader = vi.fn().mockResolvedValue(previewPublicContent)
    const repository = new ApiPublicContentRepository(loader)

    await repository.getPortfolio()
    repository.invalidate()
    await repository.getPortfolio()

    expect(loader).toHaveBeenCalledTimes(2)
  })
})
