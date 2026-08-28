import { expect, test, type Page } from '@playwright/test'
import {
  expectNonEmptyAnswer,
  type PublicTurnBody,
} from './support/public-turn-happy-path'

const TURNS = '**/api/agent/turns'
const OFFLINE_INJECTION_HEADER = 'x-portfolio-offline-turn-injection'

type InjectedTurn = PublicTurnBody & Record<string, unknown>

function validInjectedTurn(): InjectedTurn {
  return {
    requestId: '70000000-0000-4000-8000-000000000001',
    kind: 'ANSWER',
    modelExecution: {
      selectionKind: 'NONE',
      participation: 'NONE',
    },
    conversation: {
      conversationId: 'offline-negative-matrix-conversation',
      resumeToken: 'offline-negative-matrix-token',
      discussionRevision: 0,
    },
    answer: {
      resolution: 'COMPLETE',
      contentReleaseId: 'offline-negative-matrix',
      goalResults: [{
        goalId: 'offline-goal',
        label: '离线断言金样',
        coverage: 'FULL',
        notices: [],
        presentation: {
          kind: 'SECTIONED',
          sections: [{
            sectionId: 'offline-section',
            sectionKind: 'VERIFICATION',
            title: '验证正文',
            content: '该正文仅用于验证 Browser happy-path 断言，不是服务端回答证据。',
            support: {
              kind: 'VERIFIED_PUBLIC_EVIDENCE',
              publicSourceKeys: ['offline-source'],
            },
          }],
        },
      }],
      sourceCatalog: {
        sources: [{
          key: 'offline-source',
          code: 'OFFLINE-01',
          label: '离线断言来源',
          type: 'PUBLIC_EVIDENCE',
          route: '/evidence',
        }],
      },
      sourceComposition: ['VERIFIED_PUBLIC_EVIDENCE'],
    },
  }
}

function mutated(
  change: (body: InjectedTurn) => void,
): InjectedTurn {
  const body = structuredClone(validInjectedTurn())
  change(body)
  return body
}

const negativeVariants: Array<{
  name: string
  body: InjectedTurn
  error: RegExp
}> = [
  {
    name: '错误 kind',
    body: {
      requestId: '70000000-0000-4000-8000-000000000002',
      kind: 'CAPABILITY_UNAVAILABLE',
      code: 'SELECTED_MODEL_INVALID_RESPONSE',
      message: '离线错误终局',
      retryable: false,
      modelExecution: { selectionKind: 'NONE', participation: 'ATTEMPTED_UNAVAILABLE' },
      conversation: {
        conversationId: 'offline-negative-matrix-conversation',
        discussionRevision: 0,
      },
    },
    error: /期望 ANSWER/,
  },
  {
    name: '错误 resolution',
    body: mutated((body) => { body.answer!.resolution = 'PARTIAL' }),
    error: /resolution 必须为 COMPLETE/,
  },
  {
    name: '空 goalResults',
    body: mutated((body) => { body.answer!.goalResults = [] }),
    error: /goalResults 不得为空/,
  },
  {
    name: 'NONE coverage',
    body: mutated((body) => { body.answer!.goalResults![0]!.coverage = 'NONE' }),
    error: /coverage 不得为 NONE/,
  },
  {
    name: '空 SECTIONED sections',
    body: mutated((body) => {
      body.answer!.goalResults![0]!.presentation!.sections = []
    }),
    error: /必须包含 section/,
  },
  {
    name: '空白 SECTIONED content',
    body: mutated((body) => {
      body.answer!.goalResults![0]!.presentation!.sections![0]!.content = '   '
    }),
    error: /content 不得为空白/,
  },
  {
    name: '空 sourceCatalog',
    body: mutated((body) => { body.answer!.sourceCatalog = { sources: [] } }),
    error: /sourceCatalog\.sources/,
  },
  {
    name: '正文缺少已声明来源',
    body: mutated((body) => {
      body.answer!.sourceCatalog = { sources: [{ key: 'other-source' }] }
    }),
    error: /未声明来源 offline-source/,
  },
]

async function injectTurn(page: Page, body: InjectedTurn): Promise<PublicTurnBody> {
  await page.route(TURNS, async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json; charset=utf-8',
      headers: {
        [OFFLINE_INJECTION_HEADER]: 'true',
        'cache-control': 'no-store',
      },
      body: JSON.stringify(body),
    })
  })
  await page.goto('/agent')
  const submit = page.locator('.workspace-composer__suggestion').first()
  await expect(submit).toBeEnabled()
  const pending = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/agent/turns'
      && response.request().method() === 'POST')
  await submit.click()
  const response = await pending
  expect(response.status()).toBe(200)
  expect(response.headers()[OFFLINE_INJECTION_HEADER]).toBe('true')
  return await response.json() as PublicTurnBody
}

// 该 lane 的 route.fulfill 响应是离线断言注入，不证明 packaged 服务端产生了这些正文。
for (const variant of negativeVariants) {
  test(`OFFLINE_INJECTION 拒绝 ${variant.name}`, async ({ page }) => {
    const body = await injectTurn(page, variant.body)
    expect(() => expectNonEmptyAnswer(body)).toThrow(variant.error)
  })
}

test('OFFLINE_INJECTION 接受合法 happy-path 金样', async ({ page }) => {
  const body = await injectTurn(page, validInjectedTurn())
  expect(() => expectNonEmptyAnswer(body)).not.toThrow()
  await expect(page.getByTestId('answer-turn')).toBeVisible()
})
