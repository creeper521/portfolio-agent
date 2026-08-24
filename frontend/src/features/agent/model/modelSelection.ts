// 配置化模型目录（2026-08-21 设计 §8/§9）的前端闭合合同消费者：
// ModelSelection(MODEL|NONE) 同时是 Turn 请求载荷与目录默认选择的 wire 形状，
// 字段名与枚举以 contracts/agent-turn 的 A7 冻结 fixtures 为准，前端不自造字段。
// 该模块是纯函数叶子模块：不持有状态、不做持久化、不依赖任何 feature 模块。

import type { ModelExecutionProjection } from './publicAgentTurn'

/** 每个 Turn 显式携带的闭合模型选择；NONE 不得携带 modelRef/selectionVersion。 */
export type ModelSelection =
  | { readonly kind: 'MODEL'; readonly modelRef: string; readonly selectionVersion: string }
  | { readonly kind: 'NONE' }

/** /api/portfolio agentAvailability 公开目录条目；只含后端投影的安全字段。 */
export interface SelectableModelReference {
  readonly modelRef: string
  readonly selectionVersion: string
  readonly displayName: string
}

/** /api/portfolio agentAvailability 的模型目录投影（fail-closed 解析后的规范形状）。 */
export interface ModelCatalogProjection {
  readonly modelCatalogVersion: string
  readonly defaultModelSelection: ModelSelection
  readonly selectableModels: readonly SelectableModelReference[]
}

/** 目录缺失/损坏时的规范化空目录：显式 NONE，确定性 Capability 仍可用（设计决策 9）。 */
export const EMPTY_MODEL_CATALOG: ModelCatalogProjection = {
  modelCatalogVersion: '',
  defaultModelSelection: { kind: 'NONE' },
  selectableModels: [],
}

// 设计 §9：modelRef 为小写 kebab-case，长度 1—64；selectionVersion 为有界公共版本，≤128。
const MODEL_REF_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

/** fail-closed 解析闭合 ModelSelection；结构不合法返回 null，不猜测默认。 */
export function parseModelSelection(value: unknown): ModelSelection | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null
  const record = value as Record<string, unknown>
  if (record.kind === 'NONE') {
    if (record.modelRef !== undefined || record.selectionVersion !== undefined) return null
    return { kind: 'NONE' }
  }
  if (record.kind !== 'MODEL') return null
  const modelRef = record.modelRef
  const selectionVersion = record.selectionVersion
  if (
    typeof modelRef !== 'string'
    || modelRef.length < 1
    || modelRef.length > 64
    || !MODEL_REF_PATTERN.test(modelRef)
  ) return null
  if (
    typeof selectionVersion !== 'string'
    || selectionVersion.length < 1
    || selectionVersion.length > 128
  ) return null
  return { kind: 'MODEL', modelRef, selectionVersion }
}

/** fail-closed 解析 /api/portfolio 模型目录投影；损坏投影规范化为空目录语义（返回 null 由调用方回退）。 */
export function parseModelCatalogProjection(value: unknown): ModelCatalogProjection | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null
  const record = value as Record<string, unknown>
  if (!isNonEmptyString(record.modelCatalogVersion)) return null
  const defaultModelSelection = parseModelSelection(record.defaultModelSelection)
  if (defaultModelSelection === null) return null
  if (!Array.isArray(record.selectableModels)) return null
  const seenRefs = new Set<string>()
  const selectableModels: SelectableModelReference[] = []
  for (const rawEntry of record.selectableModels) {
    if (typeof rawEntry !== 'object' || rawEntry === null || Array.isArray(rawEntry)) return null
    const entry = rawEntry as Record<string, unknown>
    const entrySelection = parseModelSelection({
      kind: 'MODEL',
      modelRef: entry.modelRef,
      selectionVersion: entry.selectionVersion,
    })
    if (entrySelection === null || entrySelection.kind !== 'MODEL') return null
    if (!isNonEmptyString(entry.displayName)) return null
    if (seenRefs.has(entrySelection.modelRef)) return null
    seenRefs.add(entrySelection.modelRef)
    selectableModels.push({
      modelRef: entrySelection.modelRef,
      selectionVersion: entrySelection.selectionVersion,
      displayName: entry.displayName,
    })
  }
  if (
    defaultModelSelection.kind === 'MODEL'
    && !seenRefs.has(defaultModelSelection.modelRef)
  ) return null
  return { modelCatalogVersion: record.modelCatalogVersion, defaultModelSelection, selectableModels }
}

export function sameModelSelection(a: ModelSelection, b: ModelSelection): boolean {
  if (a.kind !== b.kind) return false
  if (a.kind === 'NONE' || b.kind === 'NONE') return true
  return a.modelRef === b.modelRef && a.selectionVersion === b.selectionVersion
}

/** 解析选择的显示名；MODEL 条目按目录 modelRef 反查，目录无此条目时回退 modelRef 本身。 */
export function displayNameOfSelection(
  catalog: ModelCatalogProjection,
  selection: ModelSelection,
): string | null {
  if (selection.kind === 'NONE') return null
  return catalog.selectableModels.find(
    (model) => model.modelRef === selection.modelRef,
  )?.displayName ?? selection.modelRef
}

/** 目录条目选择（含 selectionVersion）反查；不在目录返回 null。 */
export function catalogEntryOfSelection(
  catalog: ModelCatalogProjection,
  selection: ModelSelection,
): SelectableModelReference | null {
  if (selection.kind === 'NONE') return null
  return catalog.selectableModels.find((model) => model.modelRef === selection.modelRef) ?? null
}

/**
 * 回答模型标识（UI spec §2.5）：只消费该轮 modelExecution 投影，前端不推断
 * "应该是谁"；selectionKind=NONE 或 participation=NONE 为确定性执行，
 * 显示固定 DETERMINISTIC，不自造第三种语义。
 */
export function modelTagOfExecution(
  execution: ModelExecutionProjection,
  catalog: ModelCatalogProjection,
): string {
  if (execution.selectionKind === 'NONE' || execution.participation === 'NONE') {
    return 'DETERMINISTIC'
  }
  const displayName = catalog.selectableModels.find(
    (model) => model.modelRef === execution.requestedModelRef,
  )?.displayName
  return (displayName ?? execution.requestedModelRef ?? '').toUpperCase()
}
