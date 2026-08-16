import { defineConfig, devices } from '@playwright/test'

const externalServer = process.env.PLAYWRIGHT_EXTERNAL_SERVER === '1'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:4173'

export default defineConfig({
  testDir: './e2e/behavior',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: 'line',
  use: { baseURL, trace: 'retain-on-failure' },
  projects: [
    { name: 'api-l0', testMatch: /agent-behavior-(presets|noise)-real-api\.spec\.ts/ },
    {
      name: 'behavior-desktop',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
      testMatch: /agent-behavior-context-real-api\.spec\.ts/,
    },
    {
      name: 'behavior-mobile',
      use: { ...devices['Pixel 7'], channel: 'chrome' },
      testMatch: /agent-behavior-context-real-api\.spec\.ts/,
    },
    { name: 'runtime', testMatch: /agent-behavior-runtime-real-api\.spec\.ts/ },
  ],
  testIgnore: ['**/*.test.ts'],
  webServer: externalServer
    ? undefined
    : {
        command: 'npm.cmd run dev -- --host 127.0.0.1 --port 4173',
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
