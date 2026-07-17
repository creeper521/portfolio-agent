import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { publicContentStateKey } from '../features/public-content/composables/usePublicContent'
import { readyPublicContentState } from '../test/publicContentStateFixture'
import AgentPage from './AgentPage.vue'

async function mountAgentPage(state = readyPublicContentState()) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent', component: AgentPage }],
  })
  await router.push('/agent')
  await router.isReady()

  return mount(AgentPage, {
    global: {
      plugins: [router],
      provide: { [publicContentStateKey as symbol]: state },
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

describe('AgentPage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: query.includes('1219'),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
  })

  it('mounts the Agent workspace once public content is ready', async () => {
    const wrapper = await mountAgentPage()
    await flushPromises()

    expect(wrapper.find('.agent-workspace').exists()).toBe(true)
  })

  it('does not mount the Agent workspace before public content is ready', async () => {
    const state = readyPublicContentState()
    state.portfolio.value = null
    state.status.value = 'loading'
    const wrapper = await mountAgentPage(state)

    expect(wrapper.text()).toContain('正在装订公开档案…')
    expect(wrapper.find('.agent-workspace').exists()).toBe(false)
  })
})
