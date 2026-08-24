import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ModelSelector from './ModelSelector.vue'
import {
  EMPTY_MODEL_CATALOG,
  type ModelCatalogProjection,
  type ModelSelection,
} from '../model/modelSelection'

// UI spec §2/§8.1：触发钮、浮层、锁定、空目录 NONE、键盘可达全状态断言。
// 目录数据只来自 props 投影，组件不自带目录知识（D-MS-3/§9）。

const CATALOG: ModelCatalogProjection = {
  modelCatalogVersion: 'catalog-public-v1',
  defaultModelSelection: {
    kind: 'MODEL',
    modelRef: 'glm-4-7-flash',
    selectionVersion: 'glm-4-7-flash-v1',
  },
  selectableModels: [
    {
      modelRef: 'glm-4-7-flash',
      selectionVersion: 'glm-4-7-flash-v1',
      displayName: 'GLM-4.7-Flash',
    },
    {
      modelRef: 'qwen-3-7-flash',
      selectionVersion: 'qwen-3-7-flash-v1',
      displayName: 'Qwen3.7-Flash',
    },
  ],
}

const QWEN: ModelSelection = {
  kind: 'MODEL',
  modelRef: 'qwen-3-7-flash',
  selectionVersion: 'qwen-3-7-flash-v1',
}

function mountSelector(props: {
  catalog?: ModelCatalogProjection
  selection?: ModelSelection
  locked?: boolean
}) {
  return mount(ModelSelector, {
    props: {
      catalog: props.catalog ?? CATALOG,
      selection: props.selection ?? CATALOG.defaultModelSelection,
      ...(props.locked === undefined ? {} : { locked: props.locked }),
    },
    attachTo: document.body,
  })
}

describe('ModelSelector（布局 A：发送区内联）', () => {
  it('触发钮显示当前生效模型显示名，默认时无额外徽标', () => {
    const wrapper = mountSelector({})
    const trigger = wrapper.get('[data-testid="model-selector-trigger"]')
    expect(trigger.text()).toContain('GLM-4.7-Flash')
    expect(trigger.attributes('aria-haspopup')).toBe('listbox')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    wrapper.unmount()
  })

  it('打开浮层：条目含显示名/modelRef/目录默认徽标，当前条目标记选中', async () => {
    const wrapper = mountSelector({})
    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    expect(wrapper.get('[data-testid="model-selector-popover"]').attributes('data-open'))
      .toBe('true')
    const options = wrapper.findAll('[data-testid="model-selector-option"]')
    expect(options).toHaveLength(2)
    expect(options[0]?.text()).toContain('GLM-4.7-Flash')
    expect(options[0]?.text()).toContain('glm-4-7-flash')
    expect(options[0]?.text()).toContain('目录默认')
    expect(options[1]?.text()).not.toContain('目录默认')
    expect(options[0]?.attributes('aria-selected')).toBe('true')
    expect(options[1]?.attributes('aria-selected')).toBe('false')
    wrapper.unmount()
  })

  it('点击其他条目：emit select（含 selectionVersion）并关闭浮层；点击当前条目只关闭', async () => {
    const wrapper = mountSelector({})
    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[1]!.trigger('click')
    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual(QWEN)
    expect(wrapper.get('[data-testid="model-selector-trigger"]').attributes('aria-expanded'))
      .toBe('false')
    expect(wrapper.find('[data-testid="model-selector-popover"]').exists()).toBe(false)

    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    await wrapper.findAll('[data-testid="model-selector-option"]')[0]!.trigger('click')
    expect(wrapper.emitted('select')).toHaveLength(1)
    wrapper.unmount()
  })

  it('锁定（pending）：触发钮禁用、浮层不可打开、出现文字原因（不靠颜色单独表达）', async () => {
    const wrapper = mountSelector({ locked: true })
    const trigger = wrapper.get('[data-testid="model-selector-trigger"]')
    expect(trigger.attributes('disabled')).toBeDefined()
    expect(trigger.attributes('aria-disabled')).toBe('true')
    await trigger.trigger('click')
    expect(wrapper.find('[data-testid="model-selector-popover"][data-open="true"]').exists())
      .toBe(false)
    expect(wrapper.get('[data-testid="model-selector-lock-note"]').text())
      .toBe('回答生成中 · 本轮结束后可切换模型')
    wrapper.unmount()
  })

  it('键盘可达：Enter 打开、方向键移动、Enter 确认、Esc 关闭', async () => {
    const wrapper = mountSelector({})
    const trigger = wrapper.get('[data-testid="model-selector-trigger"]')
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(trigger.attributes('aria-expanded')).toBe('true')

    const options = wrapper.findAll('[data-testid="model-selector-option"]')
    const active = () => document.activeElement?.getAttribute('data-model-ref') ?? null
    // 打开后焦点落在当前选中条目；方向键在条目间循环移动。
    expect(active()).toBe('glm-4-7-flash')
    await options[0]!.trigger('keydown', { key: 'ArrowDown' })
    expect(active()).toBe('qwen-3-7-flash')
    await options[1]!.trigger('keydown', { key: 'ArrowUp' })
    expect(active()).toBe('glm-4-7-flash')

    await options[0]!.trigger('keydown', { key: 'ArrowDown' })
    await wrapper.findAll('[data-testid="model-selector-option"]')[1]!
      .trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual(QWEN)
    expect(trigger.attributes('aria-expanded')).toBe('false')

    await trigger.trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="model-selector-popover"]').trigger('keydown', { key: 'Escape' })
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)
    wrapper.unmount()
  })

  it('点击浮层外部关闭', async () => {
    const wrapper = mountSelector({})
    await wrapper.get('[data-testid="model-selector-trigger"]').trigger('click')
    document.body.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="model-selector-trigger"]').attributes('aria-expanded'))
      .toBe('false')
    wrapper.unmount()
  })

  it('空目录：只读 NONE 态无触发钮/浮层，常显确定性说明', () => {
    const wrapper = mountSelector({ catalog: EMPTY_MODEL_CATALOG, selection: { kind: 'NONE' } })
    expect(wrapper.find('[data-testid="model-selector-trigger"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="model-selector-popover"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="model-selector-none"]').text())
      .toContain('确定性回答 · 未配置模型')
    expect(wrapper.get('[data-testid="model-selector-none"]').text())
      .toContain('当前部署未配置可选模型，仅提供基于公开资料的确定性回答')
    wrapper.unmount()
  })

  it('会话选中非默认模型时触发钮显示该模型，不显示默认徽标信息', () => {
    const wrapper = mountSelector({ selection: QWEN })
    expect(wrapper.get('[data-testid="model-selector-trigger"]').text())
      .toContain('Qwen3.7-Flash')
    expect(wrapper.get('[data-testid="model-selector-trigger"]').text())
      .not.toContain('目录默认')
    wrapper.unmount()
  })
})
