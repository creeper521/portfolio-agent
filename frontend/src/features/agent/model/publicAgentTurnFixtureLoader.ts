import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// 测试专用合同 fixture loader（S0-01 / 前端交接 §9）：
// 从仓库根 contracts/agent-turn/fixtures 直读主开发 Agent 冻结的共享 Golden Fixtures，
// 不复制数据到 frontend。仅被 *.test.ts 引用，不进入应用构建图。

export interface GoldenFixture {
  readonly fileName: string
  readonly turn: unknown
}

export const EXPECTED_FIXTURE_FILES: readonly string[] = [
  'answer-complete.json',
  'answer-local-clarification.json',
  'answer-no-result.json',
  'answer-partial.json',
  'boundary.json',
  'capability-unavailable.json',
  'clarification.json',
  'conversational.json',
]

function resolveFixturesDirectory(): string {
  // 运行入口可能是 frontend/（npm --prefix frontend test）或仓库根，
  // 从 cwd 逐级向上探测仓库根下的 contracts/agent-turn/fixtures。
  let directory = process.cwd()
  for (let depth = 0; depth < 6; depth += 1) {
    const candidate = resolve(directory, 'contracts', 'agent-turn', 'fixtures')
    if (existsSync(candidate)) {
      return candidate
    }
    const parent = resolve(directory, '..')
    if (parent === directory) {
      break
    }
    directory = parent
  }
  throw new Error(`未找到 contracts/agent-turn/fixtures（起始目录：${process.cwd()}）`)
}

export function loadPublicAgentTurnGoldenFixtures(): readonly GoldenFixture[] {
  const directory = resolveFixturesDirectory()
  const fileNames = readdirSync(directory)
    .filter((fileName) => fileName.endsWith('.json'))
    .sort()
  const expected = [...EXPECTED_FIXTURE_FILES].sort()
  if (
    fileNames.length !== expected.length
    || fileNames.some((fileName, index) => fileName !== expected[index])
  ) {
    throw new Error(
      `共享 fixtures 文件集合与冻结清单不一致：实际 [${fileNames.join(', ')}]，预期 [${expected.join(', ')}]`,
    )
  }
  return fileNames.map((fileName) => ({
    fileName,
    turn: JSON.parse(readFileSync(resolve(directory, fileName), 'utf8')) as unknown,
  }))
}
