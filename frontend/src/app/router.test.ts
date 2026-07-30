import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from './router'

describe('portfolio router', () => {
  it.each([
    ['/', 'home'],
    ['/projects', 'projects'],
    ['/projects/sql-audit', 'project'],
    ['/cases', 'cases'],
    ['/cases/multilingual-image-preservation', 'case'],
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

  it('把 case slug 注入详情页组件的 props', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/cases/multilingual-image-preservation')
    await router.isReady()

    expect(router.currentRoute.value.params.slug).toBe('multilingual-image-preservation')
    expect(router.currentRoute.value.matched[0].props.default).toBe(true)
  })

  it.each([
    ['/projects/context-engineering-evaluation', 'open-source-evaluation'],
    ['/projects/technical-writing', 'technical-writing'],
    ['/projects/engineering-delivery-learning', 'engineering-operations'],
  ])('把降为 Collection 的旧项目 %s 重定向到对应案例筛选', async (path, collection) => {
    const router = createAppRouter(createMemoryHistory())

    await router.push(path)
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('cases')
    expect(router.currentRoute.value.query).toEqual({ collection, status: 'all' })
  })
})
