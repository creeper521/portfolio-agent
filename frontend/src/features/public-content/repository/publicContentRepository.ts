import type {
  PublicEvidence,
  PublicPortfolio,
  PublicProject,
  TimelineEvent,
} from '../model/publicContentTypes'
import { apiPublicContentRepository } from './apiPublicContentRepository'

export interface PublicContentRepository {
  invalidate(): void
  getPortfolio(): Promise<PublicPortfolio>
  getProjects(): Promise<PublicProject[]>
  getProject(slug: string): Promise<PublicProject | null>
  getTimeline(): Promise<TimelineEvent[]>
  getEvidence(): Promise<PublicEvidence[]>
}

export const publicContentRepository: PublicContentRepository = apiPublicContentRepository
