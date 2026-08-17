import { test } from '@playwright/test'
import type { QuestionPreset } from '../../src/features/public-content/model/publicContentTypes'
import { assertBehavior } from './agentBehaviorOracle'
import { BEHAVIOR_SCENARIOS, V4_TARGET_SCENARIO_IDS, expandActivePresetScenarios } from './agentBehaviorCorpus'
import { executeApiScenario } from './agentBehaviorApiDriver'

const scenarios = BEHAVIOR_SCENARIOS.filter(
  (scenario) => scenario.lane === 'L0_BUNDLE' && !V4_TARGET_SCENARIO_IDS.includes(scenario.id),
)

for (const scenario of scenarios) {
  test(`L0 preset and explicit subject path: ${scenario.id}`, async ({ request, baseURL }) => {
    const observations = await executeApiScenario(request, baseURL!, scenario)
    assertBehavior(scenario, observations)
  })
}

test('L0 expands and executes every ACTIVE public preset', async ({ request, baseURL }) => {
  const response = await request.get(new URL('/api/v1/public-content', baseURL!).toString())
  test.expect(response.ok()).toBe(true)
  const content = await response.json() as { questionPresets?: readonly { id: string; text: string; availability: string }[] }
  const activePresets = (content.questionPresets ?? []).filter((preset) => preset.availability === 'ACTIVE')
  test.expect(activePresets.length).toBeGreaterThan(0)
  for (const scenario of expandActivePresetScenarios(activePresets as QuestionPreset[])) {
    const observations = await executeApiScenario(request, baseURL!, scenario)
    assertBehavior(scenario, observations)
  }
})
