import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { previewPublicContent } from '../../public-content/data/previewPublicContent'
import EvidenceDesk from './EvidenceDesk.vue'

const evidence = previewPublicContent.evidence
const project = previewPublicContent.projects[0]
const citation = {
  id: 'answer-1:VERIFICATION:sql-audit-delivery-set',
  messageId: 'answer-1',
  sectionType: 'VERIFICATION' as const,
  sectionTitle: '验证过程',
  excerpt: '通过交付物与测试结果进行核验。',
  evidenceId: 'sql-audit-delivery-set',
}

describe('EvidenceDesk', () => {
  it('renders evidence, citations, and sources tabs', () => {
    const wrapper = mount(EvidenceDesk, {
      props: {
        evidence,
        project,
        activeEvidenceId: evidence[0].id,
        focusEvidenceIds: [evidence[0].id],
        citations: [citation],
        tab: 'EVIDENCE',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3)
    expect(wrapper.findAll('.evidence-card').length).toBeGreaterThan(0)
    expect(wrapper.get('.evidence-card').classes()).toContain('evidence-card--focused')
  })

  it('requests a tab update without owning the active tab', async () => {
    const wrapper = mount(EvidenceDesk, {
      props: {
        evidence,
        project,
        activeEvidenceId: evidence[0].id,
        focusEvidenceIds: [evidence[0].id],
        citations: [citation],
        tab: 'EVIDENCE',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    await wrapper.findAll('[role="tab"]')[1].trigger('click')

    expect(wrapper.emitted('update:tab')).toEqual([['CITATIONS']])
    expect(wrapper.find('.citation-list').exists()).toBe(false)
  })

  it('locates an answer from a citation', async () => {
    const wrapper = mount(EvidenceDesk, {
      props: {
        evidence,
        project,
        activeEvidenceId: evidence[0].id,
        focusEvidenceIds: [evidence[0].id],
        citations: [citation],
        tab: 'CITATIONS',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    await wrapper.get('[data-citation-id]').trigger('click')
    expect(wrapper.emitted('locateAnswer')).toEqual([[
      { messageId: 'answer-1', sectionType: 'VERIFICATION' },
    ]])
  })

  it('reorders focused evidence first without mutating the input collection', () => {
    const secondary = {
      ...evidence[0]!,
      id: 'secondary-evidence',
      code: 'E-SECONDARY',
      title: 'Secondary evidence',
    }
    const input = [evidence[0]!, secondary]
    const originalIds = input.map((item) => item.id)
    const wrapper = mount(EvidenceDesk, {
      props: {
        evidence: input,
        project,
        activeEvidenceId: evidence[0]!.id,
        focusEvidenceIds: [secondary.id],
        citations: [citation],
        tab: 'EVIDENCE',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.findAll('.evidence-card').map((card) => card.attributes('data-evidence-id')))
      .toEqual([secondary.id, evidence[0]!.id])
    expect(input.map((item) => item.id)).toEqual(originalIds)
  })

  it('renders public source metadata on the Sources tab', () => {
    const wrapper = mount(EvidenceDesk, {
      props: {
        evidence,
        project,
        activeEvidenceId: evidence[0]!.id,
        focusEvidenceIds: [],
        citations: [],
        tab: 'SOURCES',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })

    expect(wrapper.findAll('.source-card')).toHaveLength(evidence.length)
    expect(wrapper.get('.source-card').text()).toContain(evidence[0]!.title)
    expect(wrapper.get('.source-card').text()).toContain(String(evidence[0]!.sourceCount))
  })
})
