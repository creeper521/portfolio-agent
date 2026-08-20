import { defineConfig, devices } from '@playwright/test'

const externalServer = process.env.PLAYWRIGHT_EXTERNAL_SERVER === '1'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:4173'
// slow-provider 是显式注册的慢 Provider 打包 lane（稳定化计划 Task 7 Step 4）：
// 由 run-jar-e2e 在启动了 Fake Provider body-stall 的打包 JAR 后，以
// PLAYWRIGHT_SLOW_PROVIDER=1 调用，只匹配专用的 agent-slow-provider spec；
// 默认 lane 不注册该项目，不受影响。
const slowProviderLane = process.env.PLAYWRIGHT_SLOW_PROVIDER === '1'
const admissionLane = process.env.PLAYWRIGHT_ADMISSION === '1'
const contentOnlyLane = process.env.PLAYWRIGHT_CONTENT_ONLY === '1'
const depthTwoLane = process.env.PLAYWRIGHT_DEPTH_TWO === '1'
const projectDiscussionLane = process.env.PLAYWRIGHT_PROJECT_DISCUSSION === '1'

export default defineConfig({
  testDir: './e2e',
  testMatch: projectDiscussionLane
    ? /agent-project-discussion\.spec\.ts/
    : contentOnlyLane
    ? /agent-content-only\.spec\.ts/
    : slowProviderLane
      ? /agent-slow-provider\.spec\.ts/
      : /agent-final-contract\.spec\.ts/,
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: 'line',
  grep: admissionLane
    ? /真实来源限流/
    : depthTwoLane
      ? /澄清挑战恢复推荐目标/
      : undefined,
  use: {
    baseURL,
    trace: projectDiscussionLane ? 'off' : 'retain-on-failure',
  },
  testIgnore: ['**/behavior/**/*.test.ts', '**/behavior/**/*.spec.ts'],
  projects: contentOnlyLane
    ? [{ name: 'content-only', use: { ...devices['Desktop Chrome'] } }]
    : slowProviderLane
      ? [{ name: 'slow-provider', use: { ...devices['Desktop Chrome'] } }]
      : admissionLane || depthTwoLane
        ? [{ name: admissionLane ? 'admission' : 'depth-two', use: { ...devices['Desktop Chrome'] } }]
        : [
            {
              name: 'chromium',
              use: { ...devices['Desktop Chrome'] },
            },
            {
              name: 'mobile-chromium',
              use: { ...devices['Pixel 7'] },
            },
          ],
  webServer: externalServer
    ? undefined
    : {
        command: 'java -jar ../backend/target/portfolio-agent.jar --spring.profiles.active=local --portfolio.conversation-context.mode=IN_MEMORY --server.port=4173',
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
