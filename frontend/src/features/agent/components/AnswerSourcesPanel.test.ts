import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { PublicSourceReference } from '../model/publicAgentTurn'
import AnswerSourcesPanel from './AnswerSourcesPanel.vue'

// A2-06/B7：来源面板标题语义由外部给定；被当前会话正文引用的来源才出现定位入口。

const SOURCES: PublicSourceReference[] = [
  { key: 'source-sql-audit', label: 'SQL 审计证据', route: '/evidence', code: 'E-01' },
  { key: 'source-agent-mvp', label: 'Agent MVP 证据', route: '/evidence', code: 'E-02' },
]

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(AnswerSourcesPanel, {
    props: { sources: SOURCES, ...props },
    global: { stubs: { RouterLink: { template: '<a :href="String($attrs.to)"><slot /></a>' } } },
  })
}

describe('AnswerSourcesPanel', () => {
  it('默认标题为"当前回答来源"，无 stale 弱化', () => {
    const wrapper = mountPanel()
    expect(wrapper.get('.sources-panel__eyebrow').text()).toContain('当前回答来源')
    expect(wrapper.attributes('data-sources-stale')).toBeUndefined()
  })

  it('heading/stale 由外部语义控制（最近回答来源时弱化）', () => {
    const wrapper = mountPanel({ heading: '最近回答来源', stale: true })
    expect(wrapper.get('.sources-panel__eyebrow').text()).toContain('最近回答来源')
    expect(wrapper.attributes('data-sources-stale')).toBe('true')
  })

  it('只有被引用来源显示定位按钮并转发 locate', async () => {
    const wrapper = mountPanel({ citedSourceKeys: ['source-sql-audit'] })
    expect(wrapper.find('[data-locate-source-key="source-sql-audit"]').exists()).toBe(true)
    expect(wrapper.find('[data-locate-source-key="source-agent-mvp"]').exists()).toBe(false)

    await wrapper.get('[data-locate-source-key="source-sql-audit"]').trigger('click')
    expect(wrapper.emitted('locate')).toHaveLength(1)
    expect(wrapper.emitted('locate')?.[0]).toEqual(['source-sql-audit'])
  })
})
