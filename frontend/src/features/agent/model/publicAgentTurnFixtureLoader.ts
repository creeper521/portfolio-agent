import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import type { PublicAgentTurn } from './publicAgentTurn'
import { parsePublicAgentTurn } from './publicAgentTurnMapper'

// 测试专用合同 fixture loader（领域模型层的测试辅助）：
// 从仓库根 contracts/agent-turn 直读后端 Agent 冻结的共享 Golden Fixtures，
// 保证前后端校验同一份冻结样本，不在 frontend 复制数据。
// 仅被 *.test.ts 引用，不进入应用构建图。（S0-01 / 前端交接 §9）

/** 单个 fixture：文件名 + 尚未解析的原始 JSON。 */
export interface GoldenFixture {
  readonly fileName: string
  readonly turn: unknown
}

/** 冻结的 Turn 响应 fixtures 清单；目录文件集合必须与它完全一致，多删一个都视为漂移。 */
export const EXPECTED_FIXTURE_FILES: readonly string[] = [
  'answer-complete.json',
  'answer-local-clarification.json',
  'answer-no-result.json',
  'answer-partial.json',
  'boundary.json',
  'capability-unavailable.json',
  'clarification.json',
  'conversational.json',
  // 模型目录与 Turn 模型选择引入的五个模型终局场景 + stale 目录快照。（A7）
  'model-selection-stale.json',
  'selected-model-invalid-response.json',
  'selected-model-rate-limited.json',
  'selected-model-temporarily-unavailable.json',
  'selected-model-unavailable.json',
]

/** 冻结的 Turn 请求 fixtures 清单（MODEL/NONE/非法模型选择三种形态）。（A7） */
export const EXPECTED_REQUEST_FIXTURE_FILES: readonly string[] = [
  'turn-request-glm.json',
  'turn-request-invalid-model-selection.json',
  'turn-request-none.json',
  'turn-request-qwen.json',
]

function resolveContractsDirectory(...segments: string[]): string {
  // 运行入口可能是 frontend/（npm --prefix frontend test）或仓库根，
  // 从 cwd 逐级向上探测仓库根下的 contracts/agent-turn。
  let directory = process.cwd()
  for (let depth = 0; depth < 6; depth += 1) {
    const candidate = resolve(directory, 'contracts', 'agent-turn', ...segments)
    if (existsSync(candidate)) {
      return candidate
    }
    const parent = resolve(directory, '..')
    if (parent === directory) {
      break
    }
    directory = parent
  }
  throw new Error(`未找到 contracts/agent-turn/${segments.join('/')}（起始目录：${process.cwd()}）`)
}

/** 读取目录 JSON 并强制实际文件集合与冻结清单完全一致：fixture 意外增删立即抛错，防止测试样本静默漂移。 */
function readJsonFilesIn(directory: string, expected: readonly string[]): readonly GoldenFixture[] {
  const fileNames = readdirSync(directory)
    .filter((fileName) => fileName.endsWith('.json'))
    .sort()
  const sortedExpected = [...expected].sort()
  if (
    fileNames.length !== sortedExpected.length
    || fileNames.some((fileName, index) => fileName !== sortedExpected[index])
  ) {
    throw new Error(
      `共享 fixtures 文件集合与冻结清单不一致：实际 [${fileNames.join(', ')}]，预期 [${sortedExpected.join(', ')}]`,
    )
  }
  return fileNames.map((fileName) => ({
    fileName,
    turn: JSON.parse(readFileSync(resolve(directory, fileName), 'utf8')) as unknown,
  }))
}

/** 加载全部 Turn 响应 golden fixtures；目录与冻结清单不一致时抛错。 */
export function loadPublicAgentTurnGoldenFixtures(): readonly GoldenFixture[] {
  return readJsonFilesIn(resolveContractsDirectory('fixtures'), EXPECTED_FIXTURE_FILES)
}

/** 加载全部 Turn 请求 fixtures；目录与冻结清单不一致时抛错。 */
export function loadTurnRequestFixtures(): readonly GoldenFixture[] {
  return readJsonFilesIn(resolveContractsDirectory('request-fixtures'), EXPECTED_REQUEST_FIXTURE_FILES)
}

/** 加载冻结的 /api/portfolio agentAvailability 模型目录投影 fixture。（A7） */
export function loadPortfolioModelCatalogFixture(): Record<string, unknown> {
  const path = resolveContractsDirectory('portfolio-model-catalog.json')
  return JSON.parse(readFileSync(path, 'utf8')) as Record<string, unknown>
}

/** 测试便捷入口：按文件名读取并解析共享 golden fixture（解析失败直接抛错）。 */
export function parseGoldenFixture(fileName: string): PublicAgentTurn {
  const fixture = loadPublicAgentTurnGoldenFixtures().find(
    (candidate) => candidate.fileName === fileName,
  )
  if (fixture === undefined) {
    throw new Error(`缺少 fixture ${fileName}`)
  }
  const parsed = parsePublicAgentTurn(fixture.turn)
  if (!parsed.ok) {
    throw new Error(`${fileName} 解析失败：${parsed.error.violations.join('；')}`)
  }
  return parsed.turn
}
