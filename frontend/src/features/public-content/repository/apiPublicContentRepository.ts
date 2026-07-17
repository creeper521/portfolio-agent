import { getPublicContent } from '../../portfolio/api/portfolioApi'
import type { PublicPortfolio } from '../model/publicContentTypes'
import type { PublicContentRepository } from './publicContentRepository'

export class ApiPublicContentRepository implements PublicContentRepository {
  private cached: Promise<PublicPortfolio> | null = null
  private readonly loader: () => Promise<PublicPortfolio>

  constructor(loader: () => Promise<PublicPortfolio> = getPublicContent) {
    this.loader = loader
  }

  invalidate(): void {
    this.cached = null
  }

  getPortfolio(): Promise<PublicPortfolio> {
    return this.cached ?? (this.cached = this.loader())
  }

  async getProjects() {
    return (await this.getPortfolio()).projects
  }

  async getProject(slug: string) {
    return (await this.getPortfolio()).projects.find((item) => item.slug === slug) ?? null
  }

  async getTimeline() {
    return (await this.getPortfolio()).timeline
  }

  async getEvidence() {
    return (await this.getPortfolio()).evidence.filter((item) => item.publicStatus === 'APPROVED')
  }
}

export const apiPublicContentRepository = new ApiPublicContentRepository()
