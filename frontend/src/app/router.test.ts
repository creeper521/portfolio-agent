import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from './router'

describe('portfolio router', () => {
  it.each([
    ['/', 'home'],
    ['/projects', 'projects'],
    ['/projects/sql-audit', 'project'],
    ['/timeline', 'timeline'],
    ['/evidence', 'evidence'],
    ['/agent', 'agent'],
  ])('maps %s to %s', async (path, name) => {
    const router = createAppRouter(createMemoryHistory())

    await router.push(path)
    await router.isReady()

    expect(router.currentRoute.value.name).toBe(name)
  })

  it('shows an explicit not found route', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/unknown')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('not-found')
  })
})
