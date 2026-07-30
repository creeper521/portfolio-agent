import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'

import HomePage from '../pages/HomePage.vue'
import NotFoundPage from '../pages/NotFoundPage.vue'

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({
    history,
    routes: [
      {
        path: '/',
        name: 'home',
        component: HomePage,
      },
      {
        path: '/projects',
        name: 'projects',
        component: () => import('../pages/ProjectsPage.vue'),
      },
      // 已降为 Collection 的旧项目地址，重定向到 /cases 的对应主题筛选（必须放在 /projects/:slug 之前）。
      {
        path: '/projects/context-engineering-evaluation',
        redirect: { name: 'cases', query: { collection: 'open-source-evaluation', status: 'all' } },
      },
      {
        path: '/projects/technical-writing',
        redirect: { name: 'cases', query: { collection: 'technical-writing', status: 'all' } },
      },
      {
        path: '/projects/engineering-delivery-learning',
        redirect: { name: 'cases', query: { collection: 'engineering-operations', status: 'all' } },
      },
      {
        path: '/projects/:slug',
        name: 'project',
        component: () => import('../pages/ProjectPage.vue'),
        props: true,
      },
      {
        path: '/cases',
        name: 'cases',
        component: () => import('../pages/CasesPage.vue'),
      },
      {
        path: '/cases/:slug',
        name: 'case',
        component: () => import('../pages/CasePage.vue'),
        props: true,
      },
      {
        path: '/timeline',
        name: 'timeline',
        component: () => import('../pages/TimelinePage.vue'),
      },
      {
        path: '/evidence',
        name: 'evidence',
        component: () => import('../pages/EvidencePage.vue'),
      },
      {
        path: '/agent',
        name: 'agent',
        component: () => import('../pages/AgentPage.vue'),
        meta: { workspace: true },
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFoundPage,
      },
    ],
    scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
  })
}
