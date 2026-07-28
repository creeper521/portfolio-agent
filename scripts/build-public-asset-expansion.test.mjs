import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const inventory = resolve(
  process.env.PORTFOLIO_ASSET_INVENTORY
    ?? 'C:/Users/WIN10/Documents/杂项/实习学习-Obsidian/agent_docs_staging/portfolio-governance/candidates/asset-library-2026-07-23.json',
)
const temporary = await mkdtemp(join(tmpdir(), 'public-asset-expansion-'))

try {
  const patchPath = join(temporary, 'patch.json')
  const routesPath = join(temporary, 'routes.json')
  const benchmarkPath = join(temporary, 'benchmark.json')
  const governanceBenchmarkPath = join(temporary, 'governance-benchmark.json')
  execFileSync(process.execPath, [
    resolve(root, 'scripts/build-public-asset-expansion.mjs'),
    `--inventory=${inventory}`,
    `--base-portfolio=${resolve(root, 'backend/src/main/resources/public-data/bundle/portfolio.json')}`,
    `--base-routes=${resolve(root, 'governance/portfolio-governance/candidates/wave-1-public-routes.json')}`,
    `--base-cases=${resolve(root, 'backend/src/test/resources/retrieval-benchmark/cases.json')}`,
    `--base-governance-benchmark=${resolve(root, 'governance/portfolio-governance/benchmark/wave-1-benchmarks.v1.json')}`,
    `--patch-output=${patchPath}`,
    `--routes-output=${routesPath}`,
    `--benchmark-output=${benchmarkPath}`,
    `--governance-benchmark-output=${governanceBenchmarkPath}`,
  ])

  const patch = JSON.parse(await readFile(patchPath, 'utf8'))
  const routes = JSON.parse(await readFile(routesPath, 'utf8'))
  const benchmark = JSON.parse(await readFile(benchmarkPath, 'utf8'))
  const governanceBenchmark = JSON.parse(
    await readFile(governanceBenchmarkPath, 'utf8'),
  )
  assert.equal(patch.projects.length, 6)
  assert.equal(patch.cases.length, 46)
  assert.equal(patch.timelineEvents.length, 6)
  assert.equal(patch.claims.length, 52)
  assert.equal(patch.evidence.length, 52)
  assert.equal(patch.links.length, 52)
  assert.equal(patch.presets.length, 1)
  assert.equal(patch.presets[0].projectIds.length, 6)
  assert.equal(patch.presets[0].caseIds.length, 46)
  assert.equal(patch.claims.some((item) => item.achievementStatus === 'IN_PROGRESS'), false)
  assert.equal(routes.publishRoutes.length, 61)
  assert.equal(new Set(routes.publishRoutes.map((route) => route.assetId)).size, 61)
  assert.equal(benchmark.cases.length, 89)
  assert.equal(
    benchmark.cases.filter((item) => item.caseId.startsWith('public-asset-')).length,
    52,
  )
  const publicAssetCases = benchmark.cases
    .filter((item) => item.caseId.startsWith('public-asset-'))
  assert.ok(publicAssetCases.some((item) => item.expectedDecision === 'SUFFICIENT'))
  assert.ok(publicAssetCases.some((item) => item.expectedDecision === 'AMBIGUOUS'))
  assert.equal(
    new Set(benchmark.cases.flatMap((item) => item.expectedClaimIds)).size >= 52,
    true,
  )
  assert.equal(
    governanceBenchmark.cases.length,
    JSON.parse(await readFile(
      resolve(root, 'governance/portfolio-governance/benchmark/wave-1-benchmarks.v1.json'),
      'utf8',
    )).cases.length + 5,
  )
  assert.equal(
    patch.cases.every((item) => item.timelineEventIds.length === 1),
    true,
  )

  const publicText = JSON.stringify({ patch, routes })
  for (const excludedId of ['A-22', 'A-23', 'A-24', 'A-25', 'K-03', 'K-07', 'K-08']) {
    assert.equal(publicText.includes(`"${excludedId}"`), false)
  }
  assert.equal(patch.evidence.every((item) => item.rawContentPublic === false), true)

  const investigatedCaseIds = new Set(
    patch.claims
      .filter((item) => item.topics.includes('INVESTIGATED'))
      .map((item) => item.subjectId),
  )
  const investigatedCases = patch.cases.filter((item) => investigatedCaseIds.has(item.id))
  assert.ok(investigatedCases.length > 0)
  assert.equal(investigatedCases.every((item) => item.outcome.includes('未') || item.limitations.some((text) => text.includes('不声明最终修复'))), true)

  const assistedClaims = patch.claims.filter((item) => item.topics.includes('ASSISTED'))
  assert.ok(assistedClaims.length > 0)
  assert.equal(assistedClaims.every((item) => item.contributionType === 'COLLABORATIVE'), true)

  const ownerConfirmed = patch.claims.find((item) => item.topics.includes('DOCUMENTED_OUTPUT')
    && item.verificationBasis === 'SELF_DECLARED')
  assert.ok(ownerConfirmed)
  assert.equal(ownerConfirmed.verificationStatus, 'PARTIALLY_VERIFIED')
  assert.equal(
    patch.claims
      .filter((item) => item.verificationStatus === 'PARTIALLY_VERIFIED'
        && item !== ownerConfirmed)
      .every((item) => item.verificationBasis === 'INFERRED'),
    true,
  )

  console.log('public asset expansion generator tests passed')
} finally {
  await rm(temporary, { recursive: true, force: true })
}
