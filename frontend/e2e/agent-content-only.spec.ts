import { expect, test } from '@playwright/test'

test('DISABLED 部署仍可浏览公开内容，但不显示 Agent 提交入口', async ({ page, request }) => {
  const content = await request.get('/api/portfolio')
  expect(content.status()).toBe(200)
  expect((await content.json()).agentAvailability).toEqual({ status: 'UNAVAILABLE' })

  await page.goto('/agent')
  await expect(page.getByTestId('agent-unavailable')).toContainText('仅提供作品集浏览')
  await expect(page.getByTestId('question-input')).toHaveCount(0)
  await expect(page.getByTestId('submit-question')).toHaveCount(0)

  const direct = await request.post('/api/agent/turns', {
    data: {
      requestId: crypto.randomUUID(),
      // DISABLED 部署目录为空：显式 NONE 是唯一闭合选择（A7）；503 状态失败语义不变。
      modelSelection: { kind: 'NONE' },
      command: { kind: 'ASK', input: { kind: 'FREE_TEXT', text: '你好' } },
      conversationWindow: [],
    },
  })
  expect(direct.status()).toBe(503)
  expect((await direct.json()).error?.code).toBe('AGENT_STATE_UNAVAILABLE')
})
