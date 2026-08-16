import { test } from '@playwright/test'
import { assertBehavior } from './agentBehaviorOracle'
import { BEHAVIOR_SCENARIOS, V4_TARGET_SCENARIO_IDS } from './agentBehaviorCorpus'
import { executeApiScenario } from './agentBehaviorApiDriver'

const scenarios = BEHAVIOR_SCENARIOS.filter(
  (scenario) => scenario.lane === 'L0_BUNDLE'
    && !V4_TARGET_SCENARIO_IDS.includes(scenario.id)
    && scenario.turns.some((turn) => turn.inputClass !== 'ACTIVE_PRESET' && turn.inputClass !== 'PRESET_VARIANT'),
)

for (const scenario of scenarios) {
  test(`L0 boundary and noise path: ${scenario.id}`, async ({ request, baseURL }) => {
    const observations = await executeApiScenario(request, baseURL!, scenario)
    assertBehavior(scenario, observations)
  })
}
