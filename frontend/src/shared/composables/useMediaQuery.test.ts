import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useMediaQuery } from './useMediaQuery'

describe('useMediaQuery', () => {
  it('tracks the current matchMedia result', () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )

    const wrapper = mount(
      defineComponent({
        setup() {
          return { matches: useMediaQuery('(max-width: 980px)') }
        },
        template: '<span>{{ matches }}</span>',
      }),
    )

    expect(wrapper.text()).toBe('true')
  })
})
