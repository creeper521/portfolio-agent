import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import type {
  PublicClaim,
  PublicClaimEvidenceLink,
} from '../../public-content/model/publicContentTypes'
import { buildSectionTraces } from './sectionTrace'

const { claims, claimEvidenceLinks } = previewPublicContent

function traceFor(subjectId: string) {
  return buildSectionTraces(subjectId, claims, claimEvidenceLinks)
}

describe('sectionTrace', () => {
  describe('project with full coverage (sql-audit)', () => {
    const traces = traceFor('sql-audit-project')

    it('counts claims and distinct evidence per section', () => {
      // preview data 里 sql-audit 只有 1 个 claim (claim-sql-audit-delivered, OUTCOME)
      // 它映射到 status 段
      expect(traces.status).toEqual({ claims: 1, evidence: 1 })
    })

    it('returns null for sections with no matching claims', () => {
      // preview fixture 的 sql-audit 只有 OUTCOME claim，why/role/how/proof 无断言
      expect(traces.why).toBeNull()
      expect(traces.role).toBeNull()
      expect(traces.how).toBeNull()
      expect(traces.proof).toBeNull()
    })
  })

  describe('case with partial coverage (multilingual)', () => {
    const traces = traceFor('case-multilingual-upload')

    it('covers only the implementation section (preview has 1 IMPLEMENTATION claim)', () => {
      expect(traces.how).toEqual({ claims: 1, evidence: 1 })
    })

    it('returns null for sections the case has no claims for', () => {
      expect(traces.why).toBeNull()
      expect(traces.role).toBeNull()
      expect(traces.proof).toBeNull()
      expect(traces.status).toBeNull()
    })
  })

  describe('evidence deduplication', () => {
    it('counts distinct evidence ids even when multiple claims share one', () => {
      // 构造：两条 claim 共享同一条证据，应只计 1 条证据
      const subjectId = 'dedup-test'
      const claims: PublicClaim[] = [
        makeClaim('c1', subjectId, 'IMPLEMENTATION'),
        makeClaim('c2', subjectId, 'IMPLEMENTATION'),
      ]
      const links: PublicClaimEvidenceLink[] = [
        makeLink('c1', 'ev-shared'),
        makeLink('c2', 'ev-shared'),
      ]
      const traces = buildSectionTraces(subjectId, claims, links)
      expect(traces.how).toEqual({ claims: 2, evidence: 1 })
    })
  })

  it('returns all-null for an unknown subject', () => {
    const traces = traceFor('does-not-exist')
    expect(traces.why).toBeNull()
    expect(traces.how).toBeNull()
    expect(traces.status).toBeNull()
  })
})

function makeClaim(id: string, subjectId: string, category: PublicClaim['category']): PublicClaim {
  return {
    id,
    subjectType: 'PROJECT',
    subjectId,
    category,
    statement: id,
    detail: '',
    achievementStatus: 'DELIVERED',
    contributionType: 'PRIMARY',
    verificationBasis: 'EVIDENCE_SUPPORTED',
    verificationStatus: 'VERIFIED',
    materiality: 'KEY',
    topics: [],
    audiencePriorities: {},
  }
}

function makeLink(claimId: string, evidenceId: string): PublicClaimEvidenceLink {
  return { id: `link-${claimId}-${evidenceId}`, claimId, evidenceId, supportType: 'DIRECT', scope: '' }
}
