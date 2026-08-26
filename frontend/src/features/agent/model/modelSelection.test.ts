import { describe, expect, it } from 'vitest'

import { loadPortfolioModelCatalogFixture, loadTurnRequestFixtures } from './publicAgentTurnFixtureLoader'
import {
  catalogEntryOfSelection,
  displayNameOfSelection,
  EMPTY_MODEL_CATALOG,
  parseModelCatalogProjection,
  parseModelSelection,
  sameModelSelection,
} from './modelSelection'

// 配置化模型目录前端合同消费者测试（A7）：字段名、枚举与序列化形状
// 只消费 contracts/agent-turn 冻结 fixtures，前端不发明第二合同源。

function requestFixture(fileName: string): Record<string, unknown> {
  const fixture = loadTurnRequestFixtures().find((candidate) => candidate.fileName === fileName)
  if (fixture === undefined) throw new Error(`缺少请求 fixture ${fileName}`)
  return fixture.turn as Record<string, unknown>
}

describe('ModelSelection（Turn 请求闭合联合）', () => {
  it('MODEL fixture 解析为 modelRef + selectionVersion', () => {
    const glm = requestFixture('turn-request-glm.json')
    expect(parseModelSelection(glm.modelSelection)).toEqual({
      kind: 'MODEL',
      modelRef: 'glm-4-7-flash',
      selectionVersion: 'glm-4-7-flash-v4',
    })
    const qwen = requestFixture('turn-request-qwen.json')
    const parsedQwen = parseModelSelection(qwen.modelSelection)
    expect(parsedQwen?.kind).toBe('MODEL')
    if (parsedQwen?.kind === 'MODEL') expect(parsedQwen.modelRef).toBe('qwen-3-7-flash')
  })

  it('NONE fixture 解析为显式无模型；NONE 携带多余字段解析失败（非法 fixture 佐证）', () => {
    expect(parseModelSelection(requestFixture('turn-request-none.json').modelSelection))
      .toEqual({ kind: 'NONE' })
    expect(parseModelSelection(requestFixture('turn-request-invalid-model-selection.json').modelSelection))
      .toBeNull()
  })

  it('结构非法输入 fail-closed：未知 kind、空字符串、非 kebab-case modelRef', () => {
    expect(parseModelSelection(undefined)).toBeNull()
    expect(parseModelSelection({ kind: 'AUTO' })).toBeNull()
    expect(parseModelSelection({ kind: 'MODEL', modelRef: '', selectionVersion: 'v1' })).toBeNull()
    expect(parseModelSelection({ kind: 'MODEL', modelRef: 'GLM_4', selectionVersion: 'v1' })).toBeNull()
    expect(parseModelSelection({ kind: 'MODEL', modelRef: 'a'.repeat(65), selectionVersion: 'v1' })).toBeNull()
    expect(parseModelSelection({ kind: 'MODEL', modelRef: 'glm', selectionVersion: 'x'.repeat(129) })).toBeNull()
  })

  it('sameModelSelection 区分 kind/ref/version；NONE 只与 NONE 相等', () => {
    const glm = { kind: 'MODEL' as const, modelRef: 'glm-4-7-flash', selectionVersion: 'glm-4-7-flash-v1' }
    expect(sameModelSelection(glm, { ...glm })).toBe(true)
    expect(sameModelSelection(glm, { ...glm, selectionVersion: 'glm-4-7-flash-v2' })).toBe(false)
    expect(sameModelSelection(glm, { kind: 'NONE' })).toBe(false)
    expect(sameModelSelection({ kind: 'NONE' }, { kind: 'NONE' })).toBe(true)
  })
})

describe('ModelCatalogProjection（/api/portfolio 目录投影）', () => {
  it('消费 A7 冻结目录 fixture：两个条目 + 默认 GLM', () => {
    const catalog = parseModelCatalogProjection(loadPortfolioModelCatalogFixture())
    expect(catalog).not.toBeNull()
    if (catalog === null) return
    expect(catalog.modelCatalogVersion).toBe('catalog-public-v4')
    expect(catalog.defaultModelSelection).toEqual({
      kind: 'MODEL',
      modelRef: 'glm-4-7-flash',
      selectionVersion: 'glm-4-7-flash-v4',
    })
    expect(catalog.selectableModels.map((model) => model.displayName)).toEqual([
      'GLM-4.7-Flash',
      'Qwen3.7-Flash',
    ])
  })

  it('损坏投影 fail-closed 为 null：缺版本、缺默认、重复条目、默认不在目录、NONE 默认却带字段', () => {
    expect(parseModelCatalogProjection(null)).toBeNull()
    expect(parseModelCatalogProjection({})).toBeNull()
    const base = loadPortfolioModelCatalogFixture()
    const baseModels = base.selectableModels as Array<Record<string, unknown>>
    expect(parseModelCatalogProjection({ ...base, modelCatalogVersion: '' })).toBeNull()
    expect(parseModelCatalogProjection({ ...base, defaultModelSelection: undefined })).toBeNull()
    expect(parseModelCatalogProjection({
      ...base,
      selectableModels: [...baseModels, baseModels[0]],
    })).toBeNull()
    expect(parseModelCatalogProjection({
      ...base,
      defaultModelSelection: { kind: 'MODEL', modelRef: 'not-in-catalog', selectionVersion: 'v1' },
    })).toBeNull()
    expect(parseModelCatalogProjection({
      ...base,
      defaultModelSelection: {
        kind: 'MODEL',
        modelRef: 'glm-4-7-flash',
        selectionVersion: 'stale-selection-version',
      },
    })).toBeNull()
    expect(parseModelCatalogProjection({
      ...base,
      defaultModelSelection: { kind: 'NONE', modelRef: 'glm-4-7-flash' },
    })).toBeNull()
  })

  it('空目录（selectableModels=[] + 默认 NONE）是合法投影', () => {
    expect(parseModelCatalogProjection({
      status: 'AVAILABLE',
      freeTextSemanticRouting: 'DISABLED',
      modelCatalogVersion: 'catalog-public-v2',
      defaultModelSelection: { kind: 'NONE' },
      selectableModels: [],
    })).toEqual({
      modelCatalogVersion: 'catalog-public-v2',
      defaultModelSelection: { kind: 'NONE' },
      selectableModels: [],
    })
  })

  it('displayNameOfSelection / catalogEntryOfSelection：目录内反查，目录外回退 modelRef 或 null', () => {
    const catalog = parseModelCatalogProjection(loadPortfolioModelCatalogFixture()) ?? EMPTY_MODEL_CATALOG
    expect(displayNameOfSelection(catalog, catalog.defaultModelSelection)).toBe('GLM-4.7-Flash')
    expect(
      displayNameOfSelection(catalog, { kind: 'MODEL', modelRef: 'gone-model', selectionVersion: 'v1' }),
    ).toBe('gone-model')
    expect(displayNameOfSelection(catalog, { kind: 'NONE' })).toBeNull()
    expect(catalogEntryOfSelection(catalog, catalog.defaultModelSelection)?.selectionVersion)
      .toBe('glm-4-7-flash-v4')
    expect(
      catalogEntryOfSelection(catalog, { kind: 'MODEL', modelRef: 'gone-model', selectionVersion: 'v1' }),
    ).toBeNull()
    expect(catalogEntryOfSelection(catalog, {
      kind: 'MODEL',
      modelRef: 'glm-4-7-flash',
      selectionVersion: 'stale-selection-version',
    })).toBeNull()
    expect(catalogEntryOfSelection(catalog, { kind: 'NONE' })).toBeNull()
  })
})
