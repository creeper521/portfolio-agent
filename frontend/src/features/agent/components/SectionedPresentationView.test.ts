import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import { parseGoldenFixture } from '../model/publicAgentTurnFixtureLoader'
import type { PublicSourceCatalog, SectionedPresentation } from '../model/publicAgentTurn'
import SectionedPresentationView from './SectionedPresentationView.vue'

const ROUTER_LINK_STUB = { template: '<a :href="String($attrs.to)"><slot /></a>' }

function sectionedOf(fileName: string): {
  presentation: SectionedPresentation
  sourceCatalog: PublicSourceCatalog
} {
  const turn = parseGoldenFixture(fileName)
  if (turn.kind !== 'ANSWER') throw new Error('期望 ANSWER')
  const goal = turn.answer.goalResults[0]
  if (goal?.presentation === undefined || goal.presentation.kind !== 'SECTIONED') {
    throw new Error('期望 SECTIONED presentation')
  }
  return { presentation: goal.presentation, sourceCatalog: turn.answer.sourceCatalog }
}

describe('SectionedPresentationView', () => {
  it('按后端顺序渲染章节标题与正文，支持文本为克制中文标签', () => {
    const { presentation, sourceCatalog } = sectionedOf('answer-complete.json')
    const wrapper = mount(SectionedPresentationView, {
      props: { presentation, sourceCatalog },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    const sections = wrapper.findAll('.sectioned-presentation__section')
    expect(sections).toHaveLength(2)
    expect(sections[0]?.attributes('data-section-kind')).toBe('BACKGROUND')
    expect(sections[0]?.find('.sectioned-presentation__title')?.text()).toContain('项目背景')
    expect(sections[0]?.find('.sectioned-presentation__kind')?.text()).toBe('背景')
    expect(sections[0]?.find('.sectioned-presentation__content')?.text()).toContain(
      '该项目围绕公开的 SQL 审计与故障排查流程展开。',
    )
    expect(sections[0]?.find('.sectioned-presentation__support')?.text()).toContain('已审核公开证据')
  })

  it('来源 chip 由唯一 sourceCatalog 解析为公开编号+标题并链接站内 route', () => {
    const { presentation, sourceCatalog } = sectionedOf('answer-complete.json')
    const wrapper = mount(SectionedPresentationView, {
      props: { presentation, sourceCatalog },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    const chip = wrapper.find('.sectioned-presentation__source')
    expect(chip.attributes('data-source-key')).toBe('source-sql-audit')
    expect(chip.text()).toBe('E-01 · SQL 审计工具公开交付证据集')
    expect(chip.attributes('href')).toBe('/evidence')
  })

  it('GENERAL_KNOWLEDGE 章节显示通用知识且不渲染来源 chip', () => {
    const { presentation, sourceCatalog } = sectionedOf('answer-partial.json')
    const wrapper = mount(SectionedPresentationView, {
      props: { presentation, sourceCatalog },
      global: { stubs: { RouterLink: ROUTER_LINK_STUB } },
    })
    const support = wrapper.find('.sectioned-presentation__support')
    expect(support.attributes('data-support-kind')).toBe('GENERAL_KNOWLEDGE')
    expect(support.text()).toBe('通用知识')
    expect(wrapper.find('.sectioned-presentation__source').exists()).toBe(false)
  })
})
