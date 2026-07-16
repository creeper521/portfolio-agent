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
      {
        path: '/projects/:slug',
        name: 'project',
        component: () => import('../pages/ProjectPage.vue'),
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
