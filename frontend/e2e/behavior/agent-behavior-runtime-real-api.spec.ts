import { expect, test } from '@playwright/test'

const baseInput = {
  turnId: 'behavior-runtime-synthetic-turn',
  requestToken: '00000000-0000-4000-8000-000000000001',
  agentTurnContract: 'stp-v2',
  question: '112233',
  messages: [],
  context: {
    projectSlug: null,
    caseSlug: null,
    audienceRole: 'GUEST',
    source: 'AGENT_PAGE',
  },
}

test('runtime endpoint keeps public content cache-disabled and correlated', async ({ request, baseURL }) => {
  const response = await request.get(new URL('/api/v1/public-content', baseURL!).toString())
  expect(response.ok()).toBe(true)
  expect(response.headers()['cache-control']).toContain('no-store')
  expect(response.headers()['x-request-id']).toBeTruthy()
  expect(response.headers()['x-trace-id']).toBeTruthy()
})

test('runtime rejects an unknown short input without public evidence', async ({ request, baseURL }) => {
  const response = await request.post(new URL('/api/v2/answers', baseURL!).toString(), {
    data: baseInput,
  })
  expect(response.ok()).toBe(true)
  const body = await response.json() as {
    resolution?: string
    evidenceIds?: readonly string[]
    blocks?: readonly unknown[]
    publicSourceCatalog?: readonly unknown[]
  }
  expect(body.resolution).not.toBe('ANSWERED')
  expect(body.evidenceIds ?? []).toHaveLength(0)
  expect(body.blocks ?? []).toHaveLength(0)
  expect(body.publicSourceCatalog ?? []).toHaveLength(0)
})

test('runtime does not accept a resume token from the request body', async ({ request, baseURL }) => {
  const response = await request.post(new URL('/api/v2/answers', baseURL!).toString(), {
    headers: { 'X-Conversation-Resume-Token': 'synthetic-header-only-token' },
    data: { ...baseInput, resumeToken: 'synthetic-body-token' },
  })
  expect(response.status()).toBeLessThan(500)
  const serialized = JSON.stringify(await response.json())
  expect(serialized).not.toContain('synthetic-body-token')
})
