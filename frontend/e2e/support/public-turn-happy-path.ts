export interface PublicSupportBody {
  kind?: string
  publicSourceKeys?: unknown[]
}

export interface PublicSectionBody {
  sectionId?: string
  sectionKind?: string
  title?: string
  content?: unknown
  support?: PublicSupportBody
}

export interface PublicPresentationBody {
  kind?: string
  items?: Array<{ support?: PublicSupportBody }>
  sections?: PublicSectionBody[]
  supportingSections?: PublicSectionBody[]
}

export interface PublicGoalResultBody {
  goalId?: string
  label?: string
  coverage?: string
  presentation?: PublicPresentationBody
  notices?: unknown[]
}

export interface PublicAnswerBody {
  resolution?: string
  contentReleaseId?: string
  goalResults?: PublicGoalResultBody[]
  sourceCatalog?: {
    sources?: Array<{
      key?: unknown
      code?: string
      label?: string
      type?: string
      route?: string
    }>
  }
  sourceComposition?: string[]
}

export interface PublicTurnBody {
  kind: string
  code?: string
  answer?: PublicAnswerBody
}

export interface NonEmptyAnswerOptions {
  requirePublicSources?: boolean
  allowPartial?: boolean
}

function fail(message: string): never {
  throw new Error(message)
}

function answerKindDescription(body: PublicTurnBody): string {
  return `${body.kind}${body.code === undefined ? '' : ':' + body.code}`
}

function nonBlankText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

/** 最小判别断言：用于只关心 PublicTurn 终局种类的场景。 */
export function expectAnswerKind<T extends PublicTurnBody>(
  body: T,
): asserts body is T & { kind: 'ANSWER' } {
  if (body.kind !== 'ANSWER') {
    fail(`期望 ANSWER 终局，实际 ${answerKindDescription(body)}`)
  }
}

/**
 * happy path 的 ANSWER 必须是 COMPLETE，并携带 answer 载荷。
 * PARTIAL/NO_RESULT 是合法公开合同，但不属于这里验证的成功场景。
 */
export function expectAnswer<T extends PublicTurnBody>(
  body: T,
): asserts body is T & { kind: 'ANSWER'; answer: PublicAnswerBody } {
  expectAnswerKind(body)
  if (body.answer === undefined) {
    fail('ANSWER 终局必须携带 answer')
  }
  if (body.answer.resolution !== 'COMPLETE') {
    fail(`happy path 的 answer.resolution 必须为 COMPLETE，实际 ${String(body.answer.resolution)}`)
  }
}

function presentationSupports(presentation: PublicPresentationBody): PublicSupportBody[] {
  const sectionSupports = [
    ...(presentation.sections ?? []),
    ...(presentation.supportingSections ?? []),
  ].flatMap((section) => section.support === undefined ? [] : [section.support])
  const itemSupports = (presentation.items ?? [])
    .flatMap((item) => item.support === undefined ? [] : [item.support])
  return [...sectionSupports, ...itemSupports]
}

/**
 * 对有正文的 Portfolio happy path 做最低语义完整性检查。
 * 来源使用当前公开合同的 answer.sourceCatalog.sources 与
 * presentation.*.support.publicSourceKeys，不引入额外 wire 字段。
 */
export function expectNonEmptyAnswer<T extends PublicTurnBody>(
  body: T,
  options: NonEmptyAnswerOptions = {},
): asserts body is T & { kind: 'ANSWER'; answer: PublicAnswerBody } {
  expectAnswerKind(body)
  if (body.answer === undefined) {
    fail('ANSWER 终局必须携带 answer')
  }
  const acceptedResolutions = options.allowPartial === true
    ? ['COMPLETE', 'PARTIAL']
    : ['COMPLETE']
  if (!acceptedResolutions.includes(String(body.answer.resolution))) {
    fail(`当前回答场景不接受 resolution ${String(body.answer.resolution)}`)
  }
  const goals = body.answer.goalResults ?? []
  if (goals.length === 0) {
    fail('goalResults 不得为空')
  }

  const supports: PublicSupportBody[] = []
  for (const [goalIndex, goal] of goals.entries()) {
    if (goal.coverage === 'NONE') {
      fail(`goalResults[${goalIndex}].coverage 不得为 NONE`)
    }
    if (goal.coverage !== 'FULL' && goal.coverage !== 'PARTIAL') {
      fail(`goalResults[${goalIndex}].coverage 必须为 FULL 或 PARTIAL`)
    }
    if (goal.presentation === undefined) {
      fail(`goalResults[${goalIndex}] 必须携带 presentation`)
    }
    const presentation = goal.presentation
    if (presentation.kind === 'SECTIONED') {
      const sections = presentation.sections ?? []
      if (sections.length === 0) {
        fail(`goalResults[${goalIndex}] 的 SECTIONED presentation 必须包含 section`)
      }
      for (const [sectionIndex, section] of sections.entries()) {
        if (!nonBlankText(section.content)) {
          fail(`goalResults[${goalIndex}].sections[${sectionIndex}].content 不得为空白`)
        }
      }
    }
    supports.push(...presentationSupports(presentation))
  }

  if (options.requirePublicSources === false) return

  const rawSources = body.answer.sourceCatalog?.sources ?? []
  if (rawSources.length === 0) {
    fail('Portfolio happy path 必须包含 answer.sourceCatalog.sources')
  }
  const sourceKeys = new Set(
    rawSources.flatMap((source) => nonBlankText(source.key) ? [source.key] : []),
  )
  if (sourceKeys.size !== rawSources.length) {
    fail('answer.sourceCatalog.sources 必须全部包含非空 key')
  }

  const referencedKeys = supports.flatMap((support) =>
    (support.publicSourceKeys ?? []).filter(nonBlankText))
  if (referencedKeys.length === 0) {
    fail('Portfolio happy path 正文必须通过 support.publicSourceKeys 引用公开来源')
  }
  const missingKey = referencedKeys.find((key) => !sourceKeys.has(key))
  if (missingKey !== undefined) {
    fail(`support.publicSourceKeys 引用了未声明来源 ${missingKey}`)
  }
}

export function expectRecommendationItems(
  body: PublicTurnBody,
  expected: number,
): void {
  expectNonEmptyAnswer(body)
  const presentation = body.answer.goalResults
    ?.find((goal) => goal.presentation?.kind === 'RECOMMENDATION')?.presentation
  const actual = presentation?.items?.length
  if (actual !== expected) {
    fail(`推荐 presentation 项数应为 ${expected}，实际 ${String(actual)}`)
  }
}
