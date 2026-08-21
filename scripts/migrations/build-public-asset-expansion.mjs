import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'

const EXISTING_PUBLIC_ASSET_IDS = new Set([
  'L-01', 'T-01', 'T-02', 'T-03', 'T-04', 'T-05', 'T-06', 'T-17', 'K-01',
])

const MAINLINES = {
  'L-02': {
    id: 'image-audit-project', code: 'P-02', slug: 'image-upload-audit',
    technologies: ['Java', 'Spring Boot', '审计日志'],
  },
  'L-03': {
    id: 'activity-engineering-project', code: 'P-03', slug: 'activity-engineering',
    technologies: ['Java', '配置管理', '缓存'],
  },
  'L-04': {
    id: 'personal-agent-platform-project', code: 'P-04', slug: 'personal-agent-platform',
    technologies: ['RAG', 'MCP', 'Agent', 'Vue'],
  },
  'L-05': {
    id: 'context-engineering-project', code: 'P-05', slug: 'context-engineering-evaluation',
    technologies: ['CodeGraph', 'RAG', '检索评测'],
  },
  'L-06': {
    id: 'technical-writing-project', code: 'P-06', slug: 'technical-writing',
    technologies: ['Markdown', '技术写作'],
  },
  'L-07': {
    id: 'engineering-delivery-learning-project', code: 'P-07', slug: 'engineering-delivery-learning',
    technologies: ['Maven', 'Docker', '持续集成'],
  },
}

const CHILD_MAINLINE = {
  'L-02': ['T-07'],
  'L-03': [
    'T-16',
    ...Array.from({ length: 21 }, (_, index) => `A-${String(index + 1).padStart(2, '0')}`),
    'K-10', 'K-13',
  ],
  'L-04': ['T-08', 'T-09', 'T-10', 'T-11'],
  'L-05': ['T-12', 'T-13', 'K-02', 'K-04', 'K-05'],
  'L-06': ['T-18', 'K-16', 'K-17'],
  'L-07': ['T-14', 'T-15', 'T-19', 'K-06', 'K-09', 'K-11', 'K-12', 'K-14', 'K-15'],
}

const STATUS = {
  DELIVERED: 'DELIVERED',
  IMPLEMENTED_TESTED: 'IMPLEMENTED_TESTED',
  VALIDATED_PROTOTYPE: 'PROTOTYPE',
  INVESTIGATED: 'LEARNING',
  DOCUMENTED_OUTPUT: 'DELIVERED',
  LEARNING_ONLY: 'LEARNING',
}

const PROJECT_STATUS = {
  DELIVERED: 'DELIVERED',
  IMPLEMENTED_TESTED: 'PROTOTYPE',
  VALIDATED_PROTOTYPE: 'PROTOTYPE',
  INVESTIGATED: 'IN_PROGRESS',
  DOCUMENTED_OUTPUT: 'DELIVERED',
  LEARNING_ONLY: 'LEARNING_ONLY',
}

const CONTRIBUTION = {
  INDEPENDENT: 'INDEPENDENT',
  PRIMARY: 'PRIMARY',
  COLLABORATIVE: 'COLLABORATIVE',
  ASSISTED: 'COLLABORATIVE',
  UNRESOLVED: 'COLLABORATIVE',
}

const args = Object.fromEntries(
  process.argv.slice(2).map((arg) => {
    const separator = arg.indexOf('=')
    if (separator < 0) throw new Error(`Invalid argument: ${arg}`)
    return [arg.slice(2, separator), arg.slice(separator + 1)]
  }),
)

for (const required of [
  'inventory', 'base-portfolio', 'base-routes', 'base-cases',
  'base-governance-benchmark', 'patch-output', 'routes-output',
  'benchmark-output', 'governance-benchmark-output',
]) {
  if (!args[required]) throw new Error(`Missing --${required}=...`)
}

const inventory = JSON.parse(await readFile(resolve(args.inventory), 'utf8'))
const portfolio = JSON.parse(await readFile(resolve(args['base-portfolio']), 'utf8'))
const baseRoutes = JSON.parse(await readFile(resolve(args['base-routes']), 'utf8'))
const baseCases = JSON.parse(await readFile(resolve(args['base-cases']), 'utf8'))
const baseGovernanceBenchmark = JSON.parse(
  await readFile(resolve(args['base-governance-benchmark']), 'utf8'),
)
const targetVersion = args['target-version'] ?? '2026-07-27.1'
const newAssets = inventory.assets.filter(
  (asset) => asset.reviewState !== 'EXCLUDE' && !EXISTING_PUBLIC_ASSET_IDS.has(asset.id),
)
const newById = new Map(newAssets.map((asset) => [asset.id, asset]))

if (inventory.assets.length !== 68 || newAssets.length !== 52) {
  throw new Error(`Expected 68 inventory assets and 52 new public assets, got ${inventory.assets.length}/${newAssets.length}`)
}

const childToMainline = new Map()
for (const [mainlineId, childIds] of Object.entries(CHILD_MAINLINE)) {
  for (const childId of childIds) {
    if (childToMainline.has(childId)) throw new Error(`Duplicate child mapping: ${childId}`)
    childToMainline.set(childId, mainlineId)
  }
}
const expectedChildren = newAssets.filter((asset) => asset.contentType !== 'MAINLINE').map((asset) => asset.id)
if (expectedChildren.length !== 46 || expectedChildren.some((id) => !childToMainline.has(id))) {
  throw new Error('Every one of the 46 non-mainline assets must map to exactly one public mainline')
}

function contribution(asset) {
  const mapped = CONTRIBUTION[asset.contributionType]
  if (!mapped) throw new Error(`Unsupported contribution type for ${asset.id}`)
  return mapped
}

function caseStatus(asset) {
  const mapped = STATUS[asset.achievementStatus]
  if (!mapped) throw new Error(`Unsupported achievement status for ${asset.id}`)
  return mapped
}

function projectStatus(asset) {
  const mapped = PROJECT_STATUS[asset.achievementStatus]
  if (!mapped) throw new Error(`Unsupported project status for ${asset.id}`)
  return mapped
}

function publicId(assetId) {
  return assetId.toLowerCase().replace('-', '-')
}

function evidenceId(asset) {
  return `evidence-public-${publicId(asset.id)}`
}

function claimId(asset) {
  return `claim-public-${publicId(asset.id)}`
}

function caseId(asset) {
  return `case-public-${publicId(asset.id)}`
}

function timelineId(assetId) {
  return `timeline-public-${publicId(assetId)}`
}

function caseSlug(asset) {
  return `${publicId(asset.id)}-${asset.contentType.toLowerCase().replace('_asset', '')}`
}

function evidence(asset) {
  const sourceState = asset.reviewState === 'HOLD' ? '原处于保留审核状态' : '原处于待公开审核状态'
  return {
    id: evidenceId(asset),
    code: `E-${asset.id}`,
    title: `${asset.title}脱敏证据摘要`,
    type: 'COLLECTION',
    periodStart: '2026-07-27',
    periodEnd: '2026-07-27',
    sourceCount: 1,
    summary: `${sourceState}；本条仅公开资产库中的审核摘要、真实状态和贡献边界，原始截图、代码、内部路径与环境信息保持私有。`,
    publicStatus: 'APPROVED',
    rawContentPublic: false,
  }
}

function claim(asset, subjectType, subjectId) {
  const ownerConfirmed = asset.evidenceStatus === 'OWNER_CONFIRMED'
  const verified = asset.evidenceStatus === 'VERIFIED'
  return {
    id: claimId(asset),
    subjectType,
    subjectId,
    category: asset.achievementStatus === 'INVESTIGATED' ? 'LEARNING' : 'OUTCOME',
    statement: asset.summary,
    detail: `公开表述保持“${asset.achievementStatus} / ${asset.contributionType} / ${asset.evidenceStatus}”的来源边界，不推断上线、最终修复、生产效果或量化收益。`,
    achievementStatus: caseStatus(asset),
    contributionType: contribution(asset),
    verificationBasis: ownerConfirmed ? 'SELF_DECLARED' : verified ? 'EVIDENCE_SUPPORTED' : 'INFERRED',
    verificationStatus: verified ? 'VERIFIED' : 'PARTIALLY_VERIFIED',
    materiality: verified ? 'KEY' : 'SUPPORTING',
    topics: [asset.contentType, asset.achievementStatus, asset.contributionType],
    audiencePriorities: { INTERVIEWER: 100, MENTOR: 100, HR: 70, GUEST: 60 },
  }
}

const projects = Object.entries(MAINLINES).map(([assetId, identity]) => {
  const asset = newById.get(assetId)
  if (!asset) throw new Error(`Missing mainline asset: ${assetId}`)
  const childTitles = CHILD_MAINLINE[assetId].map((id) => newById.get(id)?.title)
  if (childTitles.some((title) => !title)) throw new Error(`Missing child asset for ${assetId}`)
  return {
    ...identity,
    title: asset.title,
    summary: asset.summary,
    background: `该主线由资产库中的已审核摘要汇总，公开内容不包含内部系统标识、环境信息或原始材料。`,
    responsibilities: [
      `围绕“${asset.title}”持续整理实现、排查或学习产出。`,
      `当前公开收录 ${childTitles.length} 个任务、案例或知识条目。`,
    ],
    solution: `以可追溯的资产条目组织内容，并按原始成熟度、贡献方式和证据状态展示。`,
    keyDecisions: [
      '原始证据保持私有，公开层只保留脱敏摘要。',
      '未完成最终验收的排查不表述为已修复，协助事项不表述为独立交付。',
    ],
    technologies: identity.technologies,
    verification: ['核对资产编号、公开摘要、状态、贡献方式和证据状态。'],
    outcome: asset.summary,
    handoff: `公开页面保留来源边界；详细证据仅在私有知识库中维护。`,
    status: projectStatus(asset),
    contributionType: contribution(asset),
    claimIds: [claimId(asset)],
    evidenceIds: [evidenceId(asset)],
    timelineEventIds: [timelineId(assetId)],
  }
})

const cases = newAssets
  .filter((asset) => asset.contentType !== 'MAINLINE')
  .map((asset, index) => {
    const parentId = childToMainline.get(asset.id)
    const parent = MAINLINES[parentId]
    const type = asset.contentType === 'INCIDENT'
      ? 'INCIDENT'
      : asset.contentType === 'KNOWLEDGE_ASSET' ? 'EVALUATION' : 'FEATURE'
    const investigated = asset.achievementStatus === 'INVESTIGATED'
    return {
      id: caseId(asset),
      code: `CASE-${String(index + 4).padStart(2, '0')}`,
      slug: caseSlug(asset),
      type,
      title: asset.title,
      summary: asset.summary,
      problem: investigated ? asset.summary : `该条目记录了“${asset.title}”的公开工作摘要。`,
      actions: ['按资产库审核结果提炼公开摘要。', '移除内部标识、原始代码、截图、地址和环境路径。'],
      decisions: ['保持原始成熟度与贡献边界，不把排查、协助或学习记录升级为独立交付。'],
      verification: [`来源证据状态：${asset.evidenceStatus}。`, `来源审核状态：${asset.reviewState}。`],
      outcome: asset.summary,
      limitations: [
        '公开内容只包含脱敏摘要，不能替代私有原始证据。',
        investigated ? '当前只确认已完成排查，不声明最终修复或上线验收。' : '不推断未在资产库中记录的生产效果或量化收益。',
      ],
      achievementStatus: caseStatus(asset),
      contributionType: contribution(asset),
      projectId: parent.id,
      claimIds: [claimId(asset)],
      evidenceIds: [evidenceId(asset)],
      timelineEventIds: [timelineId(parentId)],
      questionPresetIds: ['question-public-assets-overview'],
    }
  })

const claims = [
  ...projects.map((project) => claim(newById.get(
    Object.entries(MAINLINES).find(([, identity]) => identity.id === project.id)[0],
  ), 'PROJECT', project.id)),
  ...cases.map((caseStudy) => {
    const assetId = caseStudy.id.replace('case-public-', '').toUpperCase()
    return claim(newById.get(assetId), 'CASE', caseStudy.id)
  }),
]
const evidenceItems = newAssets.map(evidence)
const links = newAssets.map((asset) => ({
  id: `link-public-${publicId(asset.id)}`,
  claimId: claimId(asset),
  evidenceId: evidenceId(asset),
  supportType: 'DIRECT',
  scope: '脱敏资产摘要、状态与贡献边界',
  reviewStatus: 'APPROVED',
}))
const timelineEvents = Object.entries(MAINLINES).map(([assetId, identity]) => {
  const asset = newById.get(assetId)
  const childAssets = CHILD_MAINLINE[assetId].map((id) => newById.get(id))
  return {
    id: timelineId(assetId),
    dateLabel: '截至 2026.07',
    title: `${asset.title}公开资产整理`,
    problem: '原始工作记录分散在私有知识库中，公开范围、成熟度与贡献边界需要统一复核。',
    action: `将该主线及 ${childAssets.length} 个关联条目整理为脱敏 Claim、Evidence 摘要和可检索案例。`,
    impact: '形成可公开检索的资产入口，同时保留原始证据私有和未验收事项的限制说明。',
    projectIds: [identity.id],
    caseIds: childAssets.map((item) => caseId(item)),
    claimIds: [claimId(asset), ...childAssets.map(claimId)],
    evidenceIds: [evidenceId(asset), ...childAssets.map(evidenceId)],
  }
})
const presets = [{
  id: 'question-public-assets-overview',
  text: '除了核心项目，你还完成了哪些任务、问题排查、研究与知识资产？',
  aliases: ['介绍一下你的其他公开作品和实践。', '还有哪些可以公开检索的工作？'],
  audiences: ['INTERVIEWER', 'MENTOR', 'HR', 'GUEST'],
  projectIds: projects.map((project) => project.id),
  topics: ['PUBLIC_ASSET', 'PORTFOLIO_OVERVIEW'],
  preferredClaimCategories: ['OUTCOME', 'LEARNING'],
  placements: ['AGENT'],
  deterministicEntry: true,
  displayOrder: 190,
  caseIds: cases.map((caseStudy) => caseStudy.id),
}]

const patch = {
  schemaVersion: '2.0',
  baseContentVersion: portfolio.contentVersion,
  targetContentVersion: targetVersion,
  projects,
  cases,
  timelineEvents,
  claims,
  evidence: evidenceItems,
  links,
  presets,
  projectUpdates: [],
  caseUpdates: [],
}

const newRoutes = newAssets.map((asset) => {
  const isMainline = asset.contentType === 'MAINLINE'
  const identity = isMainline ? MAINLINES[asset.id] : null
  const caseStudy = isMainline ? null : cases.find((item) => item.id === caseId(asset))
  return {
    assetId: asset.id,
    finalRoute: isMainline ? 'PROJECT' : 'CASE',
    projectSlugs: isMainline ? [identity.slug] : [],
    caseSlugs: isMainline ? [] : [caseStudy.slug],
    evidenceIds: [evidenceId(asset)],
  }
})
const routes = {
  schemaVersion: '2.0',
  targetContentVersion: targetVersion,
  publishRoutes: [...baseRoutes.publishRoutes, ...newRoutes],
}
const benchmark = {
  suiteVersion: 'retrieval-benchmark-v5-full-public-assets',
  contentVersion: targetVersion,
  cases: [
    ...baseCases.cases,
    ...newAssets.map((asset) => {
      const isMainline = asset.contentType === 'MAINLINE'
      const identity = isMainline ? MAINLINES[asset.id] : null
      const caseStudy = isMainline ? null : cases.find((item) => item.id === caseId(asset))
      return {
        caseId: `public-asset-${publicId(asset.id)}-exact-01`,
        split: 'HOLDOUT',
        category: 'EXACT_TERM',
        subjectType: isMainline ? 'PROJECT' : 'CASE',
        subjectSlug: isMainline ? identity.slug : caseStudy.slug,
        query: `请介绍“${asset.title}”，并说明当前真实状态和贡献边界。`,
        expectedClaimIds: [claimId(asset)],
        expectedChunkIds: [`chunk-${claimId(asset)}`],
        expectedDecision: asset.evidenceStatus === 'VERIFIED'
          ? 'SUFFICIENT'
          : 'AMBIGUOUS',
      }
    }),
  ],
}
const governanceBenchmark = {
  schemaVersion: '1.0',
  cases: [
    ...baseGovernanceBenchmark.cases,
    {
      caseId: 'CONTRACT-SUPPORTED-PUBLIC-ASSETS-001',
      category: 'CONTRACT',
      caseType: 'SUPPORTED_QUESTION',
      questionPresetId: 'question-public-assets-overview',
      severity: 'ERROR',
    },
    {
      caseId: 'CONTRACT-ALIAS-PUBLIC-ASSETS-001',
      category: 'CONTRACT',
      caseType: 'ALIAS',
      questionPresetId: 'question-public-assets-overview',
      severity: 'ERROR',
    },
    {
      caseId: 'CONTRACT-BOUNDARY-PUBLIC-ASSETS-001',
      category: 'CONTRACT',
      caseType: 'BOUNDARY',
      questionPresetId: 'question-public-assets-overview',
      severity: 'ERROR',
    },
    {
      caseId: 'CONTENT-LINK-PUBLIC-ASSETS-001',
      category: 'CONTENT',
      caseType: 'CLAIM_EVIDENCE',
      questionPresetId: 'question-public-assets-overview',
      severity: 'ERROR',
      requiredClaimIds: ['claim-public-t-07'],
      requiredEvidenceIds: ['evidence-public-t-07'],
    },
    {
      caseId: 'SAFETY-PRIVACY-PUBLIC-ASSETS-001',
      category: 'SAFETY',
      caseType: 'SAFETY',
      questionPresetId: 'question-public-assets-overview',
      severity: 'BLOCKER',
    },
  ],
}

for (const [output, value] of [
  [args['patch-output'], patch],
  [args['routes-output'], routes],
  [args['benchmark-output'], benchmark],
  [args['governance-benchmark-output'], governanceBenchmark],
]) {
  const path = resolve(output)
  await mkdir(dirname(path), { recursive: true })
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

console.log(JSON.stringify({
  targetVersion,
  publicAssets: routes.publishRoutes.length,
  newProjects: projects.length,
  newCases: cases.length,
  newTimelineEvents: timelineEvents.length,
  newClaims: claims.length,
  newEvidence: evidenceItems.length,
  newPresets: presets.length,
  benchmarkCases: benchmark.cases.length,
  governanceBenchmarkCases: governanceBenchmark.cases.length,
  excludedAssets: inventory.assets.filter((asset) => asset.reviewState === 'EXCLUDE').length,
}))
