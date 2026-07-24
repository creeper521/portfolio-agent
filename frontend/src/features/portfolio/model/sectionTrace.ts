import type {
  PublicClaim,
  PublicClaimEvidenceLink,
} from '../../public-content/model/publicContentTypes'

/**
 * 详情页五段锚点 → claim.category 的映射。
 * 一个段落聚合一个或多个 category 的断言（如 how = TECHNICAL_DECISION + IMPLEMENTATION）。
 *
 * 映射依据：claim.category 的语义与详情页段落的叙事角色一一对应，
 * 已用 portfolio.json 真实数据验证每个 subject 在它"有内容的"段落上都有 claim。
 */
const SECTION_CATEGORIES: Record<SectionAnchor, PublicClaim['category'][]> = {
  why: ['BACKGROUND'],
  role: ['RESPONSIBILITY'],
  how: ['TECHNICAL_DECISION', 'IMPLEMENTATION'],
  proof: ['VERIFICATION'],
  status: ['OUTCOME', 'LIMITATION', 'LEARNING', 'REFLECTION'],
}

export type SectionAnchor = 'why' | 'role' | 'how' | 'proof' | 'status'

export interface SectionTrace {
  /** 该段落聚合的已核验断言数 */
  claims: number
  /** 这些断言直连的去重证据数 */
  evidence: number
}

export type SectionTraceMap = Record<SectionAnchor, SectionTrace | null>

/**
 * 为一个 subject（项目或案例）算出详情页每个段落的追溯信封。
 *
 * 返回值里 null 表示该段落没有对应 claim —— 调用方据此不渲染脚注
 * （而非显示"未覆盖"，按既定叙事取向：不强调缺失）。
 */
export function buildSectionTraces(
  subjectId: string,
  claims: ReadonlyArray<PublicClaim>,
  links: ReadonlyArray<PublicClaimEvidenceLink>,
): SectionTraceMap {
  const subjectClaims = claims.filter((claim) => claim.subjectId === subjectId)

  const result: SectionTraceMap = {
    why: null,
    role: null,
    how: null,
    proof: null,
    status: null,
  }

  for (const anchor of Object.keys(SECTION_CATEGORIES) as SectionAnchor[]) {
    const categories = SECTION_CATEGORIES[anchor]
    const matching = subjectClaims.filter((claim) => categories.includes(claim.category))
    if (matching.length === 0) continue

    // 收集这些 claim 直连的证据 id，去重计数
    const matchingIds = new Set(matching.map((claim) => claim.id))
    const evidenceIds = new Set<string>()
    for (const link of links) {
      if (matchingIds.has(link.claimId)) {
        evidenceIds.add(link.evidenceId)
      }
    }

    result[anchor] = { claims: matching.length, evidence: evidenceIds.size }
  }

  return result
}
