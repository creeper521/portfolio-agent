import { previewPublicContent } from '../data/previewPublicContent'
import type { PublicContentRepository } from './publicContentRepository'

export const previewPublicContentRepository: PublicContentRepository = {
  invalidate() {},
  async getPortfolio() {
    return previewPublicContent
  },
  async getProjects() {
    return previewPublicContent.projects
  },
  async getProject(slug) {
    return previewPublicContent.projects.find((project) => project.slug === slug) ?? null
  },
  async getTimeline() {
    return previewPublicContent.timeline
  },
  async getEvidence() {
    return previewPublicContent.evidence.filter((item) => item.publicStatus === 'APPROVED')
  },
}
