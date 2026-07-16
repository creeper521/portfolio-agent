import type {
  PublicEvidence,
  PublicPortfolio,
  PublicProject,
  TimelineEvent,
} from '../model/publicContentTypes'
import { previewPublicContentRepository } from './previewPublicContentRepository'

export interface PublicContentRepository {
  getPortfolio(): Promise<PublicPortfolio>
  getProjects(): Promise<PublicProject[]>
  getProject(slug: string): Promise<PublicProject | null>
  getTimeline(): Promise<TimelineEvent[]>
  getEvidence(): Promise<PublicEvidence[]>
}

export const publicContentRepository: PublicContentRepository = previewPublicContentRepository
