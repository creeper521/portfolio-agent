import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ClarificationFieldView } from '../model/semanticTurnView'
import ClarificationField from './ClarificationField.vue'

function field(overrides: Partial<ClarificationFieldView> = {}): ClarificationFieldView {
  return {
    fieldKey: 'comparisonSubject',
    inputMode: 'SINGLE_CHOICE',
    required: true,
    affectedGoalLabels: [],
    options: [
      {
        value: 'sql-audit',
        label: 'SQL 审计工具',
        subjectReference: { subjectType: 'PROJECT', subjectId: 'sql-audit' },
      },
      {
        value: 'abtest',
        label: 'ABTest 实验',
        subjectReference: { subjectType: 'PROJECT', subjectId: 'abtest' },
      },
    ],
    ...overrides,
  }
}

describe('ClarificationField', () => {
  it('renders numbered single-choice chips and submits the chosen option', async () => {
    const wrapper = mount(ClarificationField, { props: { field: field() } })

    expect(wrapper.text()).toContain('01')
    expect(wrapper.text()).toContain('02')
    await wrapper.get('[data-clarification-option="abtest"]').trigger('click')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      kind: 'CHOICE',
      fieldKey: 'comparisonSubject',
      option: {
        value: 'abtest',
        label: 'ABTest 实验',
        subjectReference: { subjectType: 'PROJECT', subjectId: 'abtest' },
      },
    })
  })

  it('shows an honest empty state when a choice field has no options', () => {
    const wrapper = mount(ClarificationField, {
      props: { field: field({ options: [] }) },
    })

    expect(wrapper.text()).toContain('暂无可选项')
    expect(wrapper.find('[data-clarification-option]').exists()).toBe(false)
  })

  it('keeps multi-choice submit disabled until every selection is controlled', async () => {
    const wrapper = mount(ClarificationField, {
      props: {
        field: field({
          inputMode: 'MULTI_CHOICE',
          options: [
            { value: 'a', label: '项目 A', subjectReference: { subjectType: 'PROJECT', subjectId: 'a' } },
            { value: 'b', label: '项目 B', subjectReference: null },
          ],
        }),
      },
    })

    const submit = wrapper.get('[data-clarification-submit]')
    expect(submit.attributes('disabled')).toBeDefined()
    await wrapper.get('[data-clarification-option="a"]').trigger('click')
    expect(wrapper.text()).toContain('已选 1 项')
    await wrapper.get('[data-clarification-option="b"]').trigger('click')
    // 项目 B 无受控引用 → 仍不可提交
    expect(wrapper.text()).toContain('包含暂不支持提交的选项')
    expect(submit.attributes('disabled')).toBeDefined()
    await wrapper.get('[data-clarification-option="b"]').trigger('click')
    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('click')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      kind: 'MULTI_CHOICE',
      options: [{ value: 'a' }],
    })
  })

  it('requires text before submitting a short-text field and preserves input on rerender', async () => {
    const wrapper = mount(ClarificationField, {
      props: { field: field({ inputMode: 'SHORT_TEXT', options: [] }) },
    })

    const submit = wrapper.get('[data-clarification-submit]')
    expect(submit.attributes('disabled')).toBeDefined()
    await wrapper.get('[data-clarification-text]').setValue('先只做前两步')
    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('click')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      kind: 'TEXT',
      fieldKey: 'comparisonSubject',
      text: '先只做前两步',
    })
  })

  it('aligns the short-text input with the 2000-character contract limit', async () => {
    const wrapper = mount(ClarificationField, {
      props: { field: field({ inputMode: 'SHORT_TEXT', options: [] }) },
    })

    const textarea = wrapper.get('[data-clarification-text]')
    expect(textarea.attributes('maxlength')).toBe('2000')

    // 2000 字可以提交
    await textarea.setValue('很'.repeat(2000))
    expect(wrapper.text()).toContain('2000/2000')
    const submit = wrapper.get('[data-clarification-submit]')
    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('click')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ kind: 'TEXT' })

    // 程序化写入 2001 字（绕过原生 maxlength）→ 禁止提交
    await textarea.setValue('很'.repeat(2001))
    expect(wrapper.text()).toContain('2001/2000')
    expect(wrapper.get('[data-clarification-submit]').attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('renders an honest fallback for unsupported input modes', () => {
    const wrapper = mount(ClarificationField, {
      props: { field: field({ inputMode: 'UNSUPPORTED' }) },
    })

    expect(wrapper.text()).toContain('暂不支持在此提交')
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
