import type {
  AnswerGoalResult,
  ClarificationChallenge,
  ClarificationChoice,
  ClarificationField,
  ContinuationReference,
  GoalNotice,
  GoalPresentation,
  LocalClarification,
  ModelExecutionProjection,
  PublicAnswer,
  PublicAgentTurn,
  PublicAgentTurnParseResult,
  PublicSection,
  PublicSectionKind,
  PublicSourceReference,
  PublicSupport,
  RecommendationItem,
  SingleChoiceField,
  SuggestedAction,
  SupportKind,
  TextField,
} from './publicAgentTurn'

// 冻结 PublicAgentTurn wire 合同的结构校验 mapper（领域模型层，合同入口）：
// 只做闭合 variant、必填字段、闭合枚举、结构不变量与公开引用解析；
// 未知附加字段按 additive evolution 忽略。
// 不推导业务语义（resolution/coverage/来源构成均以后端为权威），
// 不提供任何旧合同（旧协议版本、旧 disposition、任务快照、公共降级轴）回退。（D-38 / D-46）

const TURN_KINDS: readonly string[] = [
  'ANSWER',
  'CLARIFICATION',
  'CONVERSATIONAL',
  'BOUNDARY',
  'CAPABILITY_UNAVAILABLE',
]

const ANSWER_RESOLUTIONS: readonly string[] = ['COMPLETE', 'PARTIAL', 'NO_RESULT']

const GOAL_COVERAGES: readonly string[] = ['FULL', 'PARTIAL', 'NONE']

const PRESENTATION_KINDS: readonly string[] = ['SECTIONED', 'RECOMMENDATION']

// sectionKind 闭合枚举集合：与冻结合同逐字一致，新增取值必须先更新合同。（S5-01 / 前端交接 §16.1）
const SECTION_KINDS: readonly PublicSectionKind[] = [
  'BACKGROUND',
  'RESPONSIBILITY',
  'SOLUTION',
  'VERIFICATION',
  'STATUS',
  'BOUNDARY',
  'GENERAL_PRINCIPLE',
  'PORTFOLIO_EXAMPLE',
  'RELATION',
]

const SUPPORT_KINDS: readonly string[] = [
  'GENERAL_KNOWLEDGE',
  'VERIFIED_PUBLIC_EVIDENCE',
  'DERIVED',
]

const CLARIFICATION_FIELD_KINDS: readonly string[] = ['SINGLE_CHOICE', 'TEXT']

// 模型参与度闭合枚举：selectionKind 闭合，MODEL 必须携带 ref+version 而 NONE 不得携带；
// participation 按该轮实际成功采纳的阶段取值，前端不推导。（设计 §13）
const MODEL_PARTICIPATIONS: readonly string[] = [
  'NONE',
  'GOAL_INTERPRETATION_ONLY',
  'ANSWER_GENERATION',
  'GOAL_AND_ANSWER',
  'ATTEMPTED_UNAVAILABLE',
]

// 唯一不表达内容覆盖缺口、允许附着在 FULL Goal 上的通知码：
// 续读不可用属于交互能力缺失，而非内容没覆盖到。（D-38.4）
const NON_GAP_NOTICE_CODE = 'CONTINUATION_UNAVAILABLE'

// RFC 4122 v1—v5 UUID 形状；后端幂等键合同只接受 UUID。
const REQUEST_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

/** 违规明细收集器：一次解析累积全部违规再整体返回，而非首个错误即停。 */
class Violations {
  private readonly entries: string[] = []

  add(violation: string): void {
    this.entries.push(violation)
  }

  get empty(): boolean {
    return this.entries.length === 0
  }

  get list(): readonly string[] {
    return this.entries
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown, violations: Violations, where: string): string | undefined {
  if (typeof value !== 'string' || value.trim().length === 0) {
    violations.add(`${where} 必须是非空字符串`)
    return undefined
  }
  return value
}

function optionalText(
  value: unknown,
  violations: Violations,
  where: string,
): string | undefined {
  if (value === undefined) {
    return undefined
  }
  return text(value, violations, where)
}

function optionalBoolean(
  value: unknown,
  violations: Violations,
  where: string,
): boolean | undefined {
  if (value === undefined) {
    return undefined
  }
  if (typeof value !== 'boolean') {
    violations.add(`${where} 必须是布尔值`)
    return undefined
  }
  return value
}

function arrayOf(
  value: unknown,
  violations: Violations,
  where: string,
): readonly unknown[] | undefined {
  if (!Array.isArray(value)) {
    violations.add(`${where} 必须是 JSON 数组`)
    return undefined
  }
  return value
}

/** 校验 requestId：非空且符合 UUID 形状，否则记违规并返回 undefined。 */
function parseRequestId(
  value: unknown,
  violations: Violations,
): string | undefined {
  const requestId = text(value, violations, 'requestId')
  if (requestId === undefined) {
    return undefined
  }
  if (!REQUEST_ID_PATTERN.test(requestId)) {
    violations.add(`requestId 必须是 UUID：${requestId}`)
    return undefined
  }
  return requestId
}

/** 校验并投影四种闭合 continuation 操作；operation 不在闭集内即记违规。 */
function parseContinuation(
  value: unknown,
  violations: Violations,
  where: string,
): ContinuationReference | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const operation = text(value.operation, violations, `${where}.operation`)
  if (operation === 'ENTER_RESULT') {
    const contextHandle = text(value.contextHandle, violations, `${where}.contextHandle`)
    const resultItemId = text(value.resultItemId, violations, `${where}.resultItemId`)
    return contextHandle === undefined || resultItemId === undefined
      ? undefined : { operation, contextHandle, resultItemId }
  }
  if (operation === 'ROUTE_IN_CONTEXT' || operation === 'EXIT_CONTEXT') {
    const contextHandle = text(value.contextHandle, violations, `${where}.contextHandle`)
    return contextHandle === undefined ? undefined : { operation, contextHandle }
  }
  if (operation === 'REENTER_SUBJECT') {
    if (!isRecord(value.subject) || value.subject.kind !== 'PROJECT') {
      violations.add(`${where}.subject 必须是 PROJECT subject`)
      return undefined
    }
    const reference = text(value.subject.reference, violations, `${where}.subject.reference`)
    return reference === undefined
      ? undefined : { operation, subject: { kind: 'PROJECT', reference } }
  }
  violations.add(`${where}.operation 不在闭合集合中`)
  return undefined
}

/** 校验建议动作数组；字段缺省视为空数组（可选字段）。单项不合法会记违规（整体仍判失败），但继续解析其余项以收集完整明细。 */
function parseSuggestedActions(
  value: unknown,
  violations: Violations,
  where: string,
): readonly SuggestedAction[] | undefined {
  if (value === undefined) {
    return []
  }
  const rawActions = arrayOf(value, violations, where)
  if (rawActions === undefined) {
    return undefined
  }
  const actions: SuggestedAction[] = []
  rawActions.forEach((rawAction, index) => {
    const actionWhere = `${where}[${index}]`
    if (!isRecord(rawAction)) {
      violations.add(`${actionWhere} 必须是 JSON 对象`)
      return
    }
    const actionId = text(rawAction.actionId, violations, `${actionWhere}.actionId`)
    const label = text(rawAction.label, violations, `${actionWhere}.label`)
    const inputText = optionalText(rawAction.inputText, violations, `${actionWhere}.inputText`)
    const continuation = rawAction.continuation === undefined
      ? undefined
      : parseContinuation(rawAction.continuation, violations, `${actionWhere}.continuation`)
    if (actionId === undefined || label === undefined) {
      return
    }
    actions.push({
      actionId,
      label,
      ...(inputText === undefined ? {} : { inputText }),
      ...(continuation === undefined ? {} : { continuation }),
    })
  })
  return actions
}

function parseChoice(
  value: unknown,
  violations: Violations,
  where: string,
): ClarificationChoice | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const choiceId = text(value.choiceId, violations, `${where}.choiceId`)
  const label = text(value.label, violations, `${where}.label`)
  if (choiceId === undefined || label === undefined) {
    return undefined
  }
  return { choiceId, label }
}

/** 校验单选/文本两种闭合字段；SINGLE_CHOICE 至少一个选项，TEXT 的 limit 必须为正数。 */
function parseChallengeField(
  value: unknown,
  violations: Violations,
  where: string,
): ClarificationField | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const fieldId = text(value.fieldId, violations, `${where}.fieldId`)
  const label = text(value.label, violations, `${where}.label`)
  const required = optionalBoolean(value.required, violations, `${where}.required`)
  if (fieldId === undefined || label === undefined) {
    return undefined
  }
  if (value.kind === 'SINGLE_CHOICE') {
    const rawChoices = arrayOf(value.choices, violations, `${where}.choices`)
    if (rawChoices === undefined) {
      return undefined
    }
    if (rawChoices.length === 0) {
      violations.add(`${where}: SINGLE_CHOICE 至少需要一个 choice`)
      return undefined
    }
    const choices: ClarificationChoice[] = []
    for (const rawChoice of rawChoices) {
      const choice = parseChoice(rawChoice, violations, `${where}.choices[]`)
      if (choice !== undefined) {
        choices.push(choice)
      }
    }
    const field: SingleChoiceField = {
      kind: 'SINGLE_CHOICE',
      fieldId,
      label,
      ...(required === undefined ? {} : { required }),
      choices,
    }
    return field
  }
  if (value.kind === 'TEXT') {
    if (value.limit !== undefined && (typeof value.limit !== 'number' || value.limit <= 0)) {
      violations.add(`${where}.limit 必须是正数`)
      return undefined
    }
    const field: TextField = {
      kind: 'TEXT',
      fieldId,
      label,
      ...(required === undefined ? {} : { required }),
      ...(value.limit === undefined ? {} : { limit: value.limit }),
    }
    return field
  }
  violations.add(
    `${where}.kind 必须是 ${CLARIFICATION_FIELD_KINDS.join('/')} 之一`,
  )
  return undefined
}

/** 校验澄清挑战：id + 提示语 + 至少一个字段。 */
function parseChallenge(
  value: unknown,
  violations: Violations,
  where: string,
): ClarificationChallenge | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const clarificationId = text(
    value.clarificationId,
    violations,
    `${where}.clarificationId`,
  )
  const prompt = text(value.prompt, violations, `${where}.prompt`)
  if (clarificationId === undefined || prompt === undefined) {
    return undefined
  }
  const rawFields = arrayOf(value.fields, violations, `${where}.fields`)
  if (rawFields === undefined) {
    return undefined
  }
  if (rawFields.length === 0) {
    violations.add(`${where}.fields 不能为空`)
    return undefined
  }
  const fields: ClarificationField[] = []
  rawFields.forEach((rawField, index) => {
    const field = parseChallengeField(rawField, violations, `${where}.fields[${index}]`)
    if (field !== undefined) {
      fields.push(field)
    }
  })
  return { clarificationId, prompt, fields }
}

/** 校验 ANSWER 内局部澄清；affectedGoalIds 必须全部能解析到同一 answer 的 goalResult。 */
function parseLocalClarification(
  value: unknown,
  violations: Violations,
  where: string,
  goalIds: ReadonlySet<string>,
): LocalClarification | undefined {
  const challenge = parseChallenge(value, violations, where)
  if (challenge === undefined || !isRecord(value)) {
    return undefined
  }
  const rawAffectedGoalIds = arrayOf(
    value.affectedGoalIds,
    violations,
    `${where}.affectedGoalIds`,
  )
  if (rawAffectedGoalIds === undefined) {
    return undefined
  }
  if (rawAffectedGoalIds.length === 0) {
    violations.add(`${where}.affectedGoalIds 不能为空`)
    return undefined
  }
  const affectedGoalIds: string[] = []
  for (const rawGoalId of rawAffectedGoalIds) {
    const affectedGoalId = text(
      rawGoalId,
      violations,
      `${where}.affectedGoalIds[]`,
    )
    if (affectedGoalId === undefined) {
      continue
    }
    if (!goalIds.has(affectedGoalId)) {
      violations.add(
        `${where}: affectedGoalId "${affectedGoalId}" 必须引用同一 answer 的 goalResult`,
      )
      continue
    }
    affectedGoalIds.push(affectedGoalId)
  }
  return { ...challenge, affectedGoalIds }
}

/** 校验支撑声明；publicSourceKeys 必须全部能在 answer.sourceCatalog 解析，否则记违规。 */
function parseSupport(
  value: unknown,
  violations: Violations,
  where: string,
  sourceKeys: ReadonlySet<string>,
): PublicSupport | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const kind = value.kind
  if (
    kind !== 'GENERAL_KNOWLEDGE'
    && kind !== 'VERIFIED_PUBLIC_EVIDENCE'
    && kind !== 'DERIVED'
  ) {
    violations.add(`${where}.kind 必须是 ${SUPPORT_KINDS.join('/')} 之一`)
    return undefined
  }
  const rawKeys = arrayOf(value.publicSourceKeys, violations, `${where}.publicSourceKeys`)
  if (rawKeys === undefined) {
    return undefined
  }
  const publicSourceKeys: string[] = []
  for (const rawKey of rawKeys) {
    const key = text(rawKey, violations, `${where}.publicSourceKeys[]`)
    if (key === undefined) {
      continue
    }
    if (!sourceKeys.has(key)) {
      violations.add(`${where}: publicSourceKey "${key}" 无法在 answer.sourceCatalog 中解析`)
      continue
    }
    publicSourceKeys.push(key)
  }
  return { kind, publicSourceKeys }
}

/** 校验单个段落：闭合 sectionKind + 必填标题/内容 + 支撑声明。 */
function parseSection(
  value: unknown,
  violations: Violations,
  where: string,
  sourceKeys: ReadonlySet<string>,
): PublicSection | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const sectionId = text(value.sectionId, violations, `${where}.sectionId`)
  const title = text(value.title, violations, `${where}.title`)
  const content = text(value.content, violations, `${where}.content`)
  const sectionKind = SECTION_KINDS.find((kind) => kind === value.sectionKind)
  if (sectionKind === undefined) {
    violations.add(`${where}.sectionKind 必须是 ${SECTION_KINDS.join('/')} 之一`)
    return undefined
  }
  const support = parseSupport(value.support, violations, `${where}.support`, sourceKeys)
  if (sectionId === undefined || title === undefined || content === undefined || support === undefined) {
    return undefined
  }
  return { sectionId, sectionKind, title, content, support }
}

function stringArrayOf(
  value: unknown,
  violations: Violations,
  where: string,
): readonly string[] | undefined {
  const rawValues = arrayOf(value, violations, where)
  if (rawValues === undefined) {
    return undefined
  }
  const values: string[] = []
  for (const rawValue of rawValues) {
    const item = text(rawValue, violations, `${where}[]`)
    if (item !== undefined) {
      values.push(item)
    }
  }
  return values
}

/** 按 kind 分派：SECTIONED 要求非空段落列表，其余进入 RECOMMENDATION 校验。 */
function parsePresentation(
  value: unknown,
  violations: Violations,
  where: string,
  sourceKeys: ReadonlySet<string>,
): GoalPresentation | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  if (!PRESENTATION_KINDS.includes(String(value.kind))) {
    violations.add(`${where}.kind 必须是 ${PRESENTATION_KINDS.join('/')} 之一`)
    return undefined
  }
  if (value.kind === 'SECTIONED') {
    const rawSections = arrayOf(value.sections, violations, `${where}.sections`)
    if (rawSections === undefined) {
      return undefined
    }
    if (rawSections.length === 0) {
      violations.add(`${where}.sections 不能为空`)
      return undefined
    }
    const sections: PublicSection[] = []
    rawSections.forEach((rawSection, index) => {
      const section = parseSection(
        rawSection,
        violations,
        `${where}.sections[${index}]`,
        sourceKeys,
      )
      if (section !== undefined) {
        sections.push(section)
      }
    })
    return { kind: 'SECTIONED', sections }
  }
  return parseRecommendation(value, violations, where, sourceKeys)
}

/**
 * 校验 RECOMMENDATION 呈现的结构不变量：结果项 1—5 个、actualSize 必须等于
 * 实际项数且不得超过 requestedSize；数量不足必须给出 incompleteReasons，
 * 数量完整则不得携带；route 必须站内相对路径；discussionAction 必须携带
 * ENTER_RESULT continuation。
 */
function parseRecommendation(
  value: Record<string, unknown>,
  violations: Violations,
  where: string,
  sourceKeys: ReadonlySet<string>,
): GoalPresentation | undefined {
  const requestedSize = value.requestedSize
  if (typeof requestedSize !== 'number' || !Number.isInteger(requestedSize) || requestedSize < 1) {
    violations.add(`${where}.requestedSize 必须是 >= 1 的整数`)
    return undefined
  }
  const actualSize = value.actualSize
  if (typeof actualSize !== 'number' || !Number.isInteger(actualSize) || actualSize < 0) {
    violations.add(`${where}.actualSize 必须是非负整数`)
    return undefined
  }
  const rawItems = arrayOf(value.items, violations, `${where}.items`)
  if (rawItems === undefined) {
    return undefined
  }
  if (rawItems.length < 1 || rawItems.length > 5) {
    violations.add(`${where}.items 数量必须在 1—5 之间`)
    return undefined
  }
  if (actualSize !== rawItems.length) {
    violations.add(`${where}.actualSize 必须等于 items 数量`)
    return undefined
  }
  if (rawItems.length > requestedSize) {
    violations.add(`${where}.items 数量不得超过 requestedSize`)
    return undefined
  }
  const incompleteReasons = stringArrayOf(
    value.incompleteReasons,
    violations,
    `${where}.incompleteReasons`,
  )
  const unsatisfiedConstraints = stringArrayOf(
    value.unsatisfiedConstraints,
    violations,
    `${where}.unsatisfiedConstraints`,
  )
  if (incompleteReasons === undefined || unsatisfiedConstraints === undefined) {
    return undefined
  }
  const countIncomplete = actualSize < requestedSize
  if (countIncomplete && incompleteReasons.length === 0) {
    violations.add(`${where}: 数量不足时必须提供 incompleteReasons`)
  }
  if (!countIncomplete && incompleteReasons.length > 0) {
    violations.add(`${where}: 数量完整时不得携带 incompleteReasons`)
  }

  const items: RecommendationItem[] = []
  rawItems.forEach((rawItem, index) => {
    const itemWhere = `${where}.items[${index}]`
    if (!isRecord(rawItem)) {
      violations.add(`${itemWhere} 必须是 JSON 对象`)
      return
    }
    const label = text(rawItem.label, violations, `${itemWhere}.label`)
    const summary = text(rawItem.summary, violations, `${itemWhere}.summary`)
    const route = text(rawItem.route, violations, `${itemWhere}.route`)
    const resultItemId = optionalText(rawItem.resultItemId, violations, `${itemWhere}.resultItemId`)
    const reasons = stringArrayOf(rawItem.reasons, violations, `${itemWhere}.reasons`)
    const support = parseSupport(rawItem.support, violations, `${itemWhere}.support`, sourceKeys)
    const discussionAction = rawItem.discussionAction === undefined
      ? undefined
      : parseSuggestedActions([rawItem.discussionAction], violations, `${itemWhere}.discussionAction`)?.[0]
    if (rawItem.discussionAction !== undefined
        && discussionAction?.continuation?.operation !== 'ENTER_RESULT') {
      violations.add(`${itemWhere}.discussionAction 必须携带 ENTER_RESULT continuation`)
      return
    }
    if (route !== undefined && !route.startsWith('/')) {
      violations.add(`${itemWhere}: route "${route}" 必须是站内相对路径`)
      return
    }
    if (
      label === undefined
      || summary === undefined
      || route === undefined
      || reasons === undefined
      || support === undefined
    ) {
      return
    }
    items.push({
      ...(resultItemId === undefined ? {} : { resultItemId }),
      label,
      summary,
      route,
      reasons,
      support,
      ...(discussionAction === undefined ? {} : { discussionAction }),
    })
  })

  const rawSupportingSections = arrayOf(
    value.supportingSections,
    violations,
    `${where}.supportingSections`,
  )
  if (rawSupportingSections === undefined) {
    return undefined
  }
  const supportingSections: PublicSection[] = []
  rawSupportingSections.forEach((rawSection, index) => {
    const section = parseSection(
      rawSection,
      violations,
      `${where}.supportingSections[${index}]`,
      sourceKeys,
    )
    if (section !== undefined) {
      supportingSections.push(section)
    }
  })

  return {
    kind: 'RECOMMENDATION',
    requestedSize,
    actualSize,
    items,
    unsatisfiedConstraints,
    incompleteReasons,
    supportingSections,
  }
}

/** 校验唯一来源目录：key 不得重复、route 必须站内相对路径；同时返回 key 集合供引用解析。 */
function parseSourceCatalog(
  value: unknown,
  violations: Violations,
  where: string,
): { sources: PublicSourceReference[]; keys: Set<string> } | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const rawSources = arrayOf(value.sources, violations, `${where}.sources`)
  if (rawSources === undefined) {
    return undefined
  }
  const sources: PublicSourceReference[] = []
  const keys = new Set<string>()
  rawSources.forEach((rawSource, index) => {
    const sourceWhere = `${where}.sources[${index}]`
    if (!isRecord(rawSource)) {
      violations.add(`${sourceWhere} 必须是 JSON 对象`)
      return
    }
    const key = text(rawSource.key, violations, `${sourceWhere}.key`)
    const label = text(rawSource.label, violations, `${sourceWhere}.label`)
    const route = text(rawSource.route, violations, `${sourceWhere}.route`)
    const code = optionalText(rawSource.code, violations, `${sourceWhere}.code`)
    const type = optionalText(rawSource.type, violations, `${sourceWhere}.type`)
    if (key === undefined || label === undefined || route === undefined) {
      return
    }
    if (keys.has(key)) {
      violations.add(`${sourceWhere}: key "${key}" 重复`)
      return
    }
    if (!route.startsWith('/')) {
      violations.add(`${sourceWhere}: route "${route}" 必须是站内相对路径`)
      return
    }
    keys.add(key)
    sources.push({
      key,
      label,
      route,
      ...(code === undefined ? {} : { code }),
      ...(type === undefined ? {} : { type }),
    })
  })
  return { sources, keys }
}

/** 校验 Goal 通知数组；字段缺省视为空列表。 */
function parseNotices(
  value: unknown,
  violations: Violations,
  where: string,
): readonly GoalNotice[] {
  if (value === undefined) {
    return []
  }
  const rawNotices = arrayOf(value, violations, where)
  if (rawNotices === undefined) {
    return []
  }
  const notices: GoalNotice[] = []
  rawNotices.forEach((rawNotice, index) => {
    const noticeWhere = `${where}[${index}]`
    if (!isRecord(rawNotice)) {
      violations.add(`${noticeWhere} 必须是 JSON 对象`)
      return
    }
    const code = text(rawNotice.code, violations, `${noticeWhere}.code`)
    const message = text(rawNotice.message, violations, `${noticeWhere}.message`)
    if (code === undefined || message === undefined) {
      return
    }
    notices.push({ code, message })
  })
  return notices
}

/**
 * 校验 ANSWER 载荷。除字段级校验外还检查两组不变量：
 * 1) coverage × 呈现/通知：FULL、PARTIAL 必须携带 presentation，NONE 不得携带；
 *    PARTIAL 至少一个覆盖缺口 notice，FULL 不得有缺口 notice，NONE 必须有 notice。
 * 2) resolution × Goal：COMPLETE 要求全部 FULL；PARTIAL 要求至少一个 Goal 有产出
 *    且不得全为 FULL；NO_RESULT 要求全部 NONE。
 */
function parseAnswer(
  value: unknown,
  violations: Violations,
  where: string,
): PublicAnswer | undefined {
  if (!isRecord(value)) {
    violations.add(`${where} 必须是 JSON 对象`)
    return undefined
  }
  const resolution = value.resolution
  if (resolution !== 'COMPLETE' && resolution !== 'PARTIAL' && resolution !== 'NO_RESULT') {
    violations.add(`${where}.resolution 必须是 ${ANSWER_RESOLUTIONS.join('/')} 之一`)
    return undefined
  }
  const contentReleaseId = text(
    value.contentReleaseId,
    violations,
    `${where}.contentReleaseId`,
  )
  const catalog = parseSourceCatalog(
    value.sourceCatalog,
    violations,
    `${where}.sourceCatalog`,
  )
  const rawSourceComposition = arrayOf(
    value.sourceComposition,
    violations,
    `${where}.sourceComposition`,
  )
  const rawGoalResults = arrayOf(value.goalResults, violations, `${where}.goalResults`)
  if (
    contentReleaseId === undefined
    || catalog === undefined
    || rawSourceComposition === undefined
    || rawGoalResults === undefined
  ) {
    return undefined
  }
  if (rawGoalResults.length === 0) {
    violations.add(`${where}.goalResults 不能为空`)
    return undefined
  }

  const sourceComposition: SupportKind[] = []
  for (const rawComposition of rawSourceComposition) {
    const composition = text(
      rawComposition,
      violations,
      `${where}.sourceComposition[]`,
    )
    if (composition === undefined) {
      continue
    }
    if (
      composition !== 'GENERAL_KNOWLEDGE'
      && composition !== 'VERIFIED_PUBLIC_EVIDENCE'
      && composition !== 'DERIVED'
    ) {
      violations.add(
        `${where}.sourceComposition "${composition}" 必须是 ${SUPPORT_KINDS.join('/')} 之一`,
      )
      continue
    }
    sourceComposition.push(composition)
  }

  const goalIds = new Set<string>()
  const goalResults: AnswerGoalResult[] = []
  let producedGoals = 0
  let fullGoals = 0
  rawGoalResults.forEach((rawGoal, index) => {
    const goalWhere = `${where}.goalResults[${index}]`
    if (!isRecord(rawGoal)) {
      violations.add(`${goalWhere} 必须是 JSON 对象`)
      return
    }
    const goalId = text(rawGoal.goalId, violations, `${goalWhere}.goalId`)
    const label = text(rawGoal.label, violations, `${goalWhere}.label`)
    if (goalId === undefined || label === undefined) {
      return
    }
    if (goalIds.has(goalId)) {
      violations.add(`${goalWhere}: goalId "${goalId}" 重复`)
      return
    }
    goalIds.add(goalId)
    const coverage = rawGoal.coverage
    if (coverage !== 'FULL' && coverage !== 'PARTIAL' && coverage !== 'NONE') {
      violations.add(`${goalWhere}.coverage 必须是 ${GOAL_COVERAGES.join('/')} 之一`)
      return
    }
    const notices = parseNotices(rawGoal.notices, violations, `${goalWhere}.notices`)
    const gapNoticeCount = notices.filter(
      (notice) => notice.code !== NON_GAP_NOTICE_CODE,
    ).length
    const hasPresentation = rawGoal.presentation !== undefined

    if (coverage === 'FULL') {
      producedGoals += 1
      fullGoals += 1
      if (!hasPresentation) {
        violations.add(`${goalWhere}: FULL Goal 必须携带 presentation`)
      }
      if (gapNoticeCount > 0) {
        violations.add(`${goalWhere}: FULL Goal 不得携带覆盖缺口 notice`)
      }
    } else if (coverage === 'PARTIAL') {
      producedGoals += 1
      if (!hasPresentation) {
        violations.add(`${goalWhere}: PARTIAL Goal 必须携带 presentation`)
      }
      if (gapNoticeCount === 0) {
        violations.add(`${goalWhere}: PARTIAL Goal 至少需要一个覆盖缺口 notice`)
      }
    } else if (hasPresentation) {
      violations.add(`${goalWhere}: NONE Goal 不得携带 presentation`)
    } else if (notices.length === 0) {
      violations.add(`${goalWhere}: NONE Goal 必须携带 notice`)
    }

    const presentation = hasPresentation
      ? parsePresentation(
        rawGoal.presentation,
        violations,
        `${goalWhere}.presentation`,
        catalog.keys,
      )
      : undefined
    goalResults.push({
      goalId,
      label,
      coverage,
      ...(presentation === undefined ? {} : { presentation }),
      notices,
    })
  })

  if (resolution === 'COMPLETE') {
    if (fullGoals !== rawGoalResults.length) {
      violations.add(`${where}: COMPLETE 要求全部 Goal 为 FULL`)
    }
  } else if (resolution === 'PARTIAL') {
    if (producedGoals === 0) {
      violations.add(`${where}: PARTIAL 要求至少一个 Goal 有产出`)
    }
    if (fullGoals === rawGoalResults.length) {
      violations.add(`${where}: 全部 Goal 为 FULL 时不得为 PARTIAL`)
    }
  } else if (producedGoals !== 0) {
    violations.add(`${where}: NO_RESULT 要求全部 Goal 为 NONE`)
  }

  const suggestedActions = parseSuggestedActions(
    value.suggestedActions,
    violations,
    `${where}.suggestedActions`,
  )
  const localClarification = value.localClarification === undefined
    ? undefined
    : parseLocalClarification(
      value.localClarification,
      violations,
      `${where}.localClarification`,
      goalIds,
    )

  return {
    resolution,
    contentReleaseId,
    goalResults,
    sourceCatalog: { sources: catalog.sources },
    sourceComposition,
    ...(suggestedActions === undefined ? {} : { suggestedActions }),
    ...(localClarification === undefined ? {} : { localClarification }),
  }
}

/** 校验模型执行投影：participation 闭合；NONE 不得携带 ref/version，MODEL 必须携带。 */
function parseModelExecution(
  value: unknown,
  violations: Violations,
): ModelExecutionProjection | undefined {
  if (value === undefined) {
    return undefined
  }
  if (!isRecord(value)) {
    violations.add('modelExecution 必须是 JSON 对象')
    return undefined
  }
  if (!MODEL_PARTICIPATIONS.includes(String(value.participation))) {
    violations.add(`modelExecution.participation 必须是 ${MODEL_PARTICIPATIONS.join('/')} 之一`)
    return undefined
  }
  const participation = value.participation as ModelExecutionProjection['participation']
  if (value.selectionKind === 'NONE') {
    if (value.requestedModelRef !== undefined || value.selectionVersion !== undefined) {
      violations.add('modelExecution：selectionKind=NONE 不得携带 requestedModelRef/selectionVersion')
      return undefined
    }
    return { selectionKind: 'NONE', participation }
  }
  if (value.selectionKind !== 'MODEL') {
    violations.add('modelExecution.selectionKind 必须是 MODEL/NONE 之一')
    return undefined
  }
  const requestedModelRef = text(
    value.requestedModelRef,
    violations,
    'modelExecution.requestedModelRef',
  )
  const selectionVersion = text(
    value.selectionVersion,
    violations,
    'modelExecution.selectionVersion',
  )
  if (requestedModelRef === undefined || selectionVersion === undefined) {
    return undefined
  }
  return { selectionKind: 'MODEL', requestedModelRef, selectionVersion, participation }
}

/** 校验 Turn 根节点并分派到各闭合变体；非 ANSWER 变体不得携带 answer。 */
function parseTurn(value: unknown, violations: Violations): PublicAgentTurn | undefined {
  if (!isRecord(value)) {
    violations.add('PublicAgentTurn 根节点必须是 JSON 对象')
    return undefined
  }
  const requestId = parseRequestId(value.requestId, violations)
  if (!TURN_KINDS.includes(String(value.kind))) {
    violations.add(`kind 必须是 ${TURN_KINDS.join('/')} 之一`)
    return undefined
  }
  if (requestId === undefined) {
    return undefined
  }
  const suggestedActions = parseSuggestedActions(
    value.suggestedActions,
    violations,
    'suggestedActions',
  )
  const modelExecution = parseModelExecution(value.modelExecution, violations)

  if (value.kind !== 'ANSWER') {
    if (value.answer !== undefined) {
      violations.add('非 ANSWER 变体不得携带 answer')
    }
    const message = text(value.message, violations, 'message')
    if (message === undefined) {
      return undefined
    }
    if (value.kind === 'CLARIFICATION') {
      const clarification = parseChallenge(
        value.clarification,
        violations,
        'clarification',
      )
      if (clarification === undefined) {
        return undefined
      }
      return {
        kind: 'CLARIFICATION',
        requestId,
        message,
        clarification,
        ...(suggestedActions === undefined ? {} : { suggestedActions }),
        ...(modelExecution === undefined ? {} : { modelExecution }),
      }
    }
    if (value.kind === 'BOUNDARY' || value.kind === 'CAPABILITY_UNAVAILABLE') {
      const code = text(value.code, violations, 'code')
      if (code === undefined) {
        return undefined
      }
      if (value.kind === 'CAPABILITY_UNAVAILABLE') {
        const retryable = optionalBoolean(value.retryable, violations, 'retryable')
        const retryAfterSeconds = value.retryAfterSeconds
        if (retryAfterSeconds !== undefined
            && (!Number.isSafeInteger(retryAfterSeconds)
              || Number(retryAfterSeconds) < 1
              || Number(retryAfterSeconds) > 300)) {
          violations.add('retryAfterSeconds 必须是 1—300 的整数')
        }
        return {
          kind: 'CAPABILITY_UNAVAILABLE',
          requestId,
          code,
          message,
          ...(retryable === undefined ? {} : { retryable }),
          ...(retryAfterSeconds === undefined
            || typeof retryAfterSeconds !== 'number'
            ? {} : { retryAfterSeconds }),
          ...(suggestedActions === undefined ? {} : { suggestedActions }),
          ...(modelExecution === undefined ? {} : { modelExecution }),
        }
      }
      return {
        kind: 'BOUNDARY',
        requestId,
        code,
        message,
        ...(suggestedActions === undefined ? {} : { suggestedActions }),
        ...(modelExecution === undefined ? {} : { modelExecution }),
      }
    }
    return {
      kind: 'CONVERSATIONAL',
      requestId,
      message,
      ...(suggestedActions === undefined ? {} : { suggestedActions }),
      ...(modelExecution === undefined ? {} : { modelExecution }),
    }
  }

  if (value.answer === undefined) {
    violations.add('ANSWER 变体必须携带 answer')
    return undefined
  }
  const answer = parseAnswer(value.answer, violations, 'answer')
  if (answer === undefined) {
    return undefined
  }
  return {
    kind: 'ANSWER',
    requestId,
    answer,
    ...(modelExecution === undefined ? {} : { modelExecution }),
  }
}

/**
 * fail-closed 解析未知来源的 PublicAgentTurn JSON。
 * 当且仅当全部闭合校验通过且无任何违规时返回 ok=true；
 * 任何违规都收进 violations 一次性返回，绝不猜测字段或回退旧格式。
 */
export function parsePublicAgentTurn(value: unknown): PublicAgentTurnParseResult {
  const violations = new Violations()
  const turn = parseTurn(value, violations)
  if (turn === undefined || !violations.empty) {
    return {
      ok: false,
      error: { reason: 'CONTRACT_INVALID', violations: violations.list },
    }
  }
  return { ok: true, turn }
}
