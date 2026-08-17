import { test } from '@playwright/test'
import { BEHAVIOR_SCENARIOS, V4_TARGET_SCENARIO_IDS } from './agentBehaviorCorpus'
import { assertBehavior } from './agentBehaviorOracle'
import { executeApiScenario } from './agentBehaviorApiDriver'

// P0 冻结的第四版目标行为（对当前实现为预期失败，待 P4–P7 交付后转绿）：
// 1. 首轮裸代词只允许澄清，不得静默绑定 pageHint 主体，也不得携带 Evidence；
// 2. 首版推荐结果主体域只允许 Project，不得混入 Case。
// 这些用例故意保持 RED 基线，不得为迁就现状改写期望。
const scenarios = BEHAVIOR_SCENARIOS.filter((scenario) =>
  V4_TARGET_SCENARIO_IDS.includes(scenario.id),
)

for (const scenario of scenarios) {
  test(`L0 v4 target behavior: ${scenario.id}`, async ({ request, baseURL }) => {
    const observations = await executeApiScenario(request, baseURL!, scenario)
    assertBehavior(scenario, observations)
  })
}
